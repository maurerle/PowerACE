package markets.trader.spot.renewable;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.Bid.BidType;
import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.operator.spot.tools.MarginalBid;
import markets.trader.Trader;
import markets.trader.TraderType;
import markets.trader.spot.DayAheadTrader;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import simulations.scheduling.MarketScheduler;
import tools.math.Statistics;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * Trader Renewable capacity
 *
 * @since 13.04.2005
 * @author Frank Sensfuss
 */
public class RenewableTrader extends Trader implements DayAheadTrader {

	private final static float EPSILON = 0.15f;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(RenewableTrader.class.getName());
	/**
	 * Price that is used for the dayAheadMarket, value is set via agent_XX.xml
	 */
	private float dayAheadBiddingPrice;

	/**
	 * Calculate energy balance by market area after market clearing
	 * <p>
	 * The grid operator checks whether supply and demand of electrical energy
	 * is balanced in each hour of the day.
	 */
	public void calculateEnergyBalance(MarketScheduler marketScheduler) {

		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {

			final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);

			// Get data to calculate balance
			// Demand
			float demandLoad = marketArea.getDemandData().getHourlyDemand(hourOfDay);

			final float demandAccepted = marketArea.getElectricityResultsDayAhead()
					.getDemandAcceptedHourOfDay(hourOfDay);

			// Renewables production planned (as calculated from database)
			float renewablesProductionPlanned = marketArea.getManagerRenewables()
					.getTotalRenewableLoad(hourOfYear);

			final float renewablesProductionAccepted = marketArea.getElectricityResultsDayAhead()
					.getRenewablesAcceptedHourOfDay(hourOfDay);

			// Electricity production of conventional power plants
			float elecProductionPowerPlants = 0f;
			for (final FuelName fuelName : FuelName.values()) {
				elecProductionPowerPlants += marketArea.getElectricityProduction()
						.getElectricityDaily(fuelName, hourOfDay);
			}

			final float elecProductionPumpedStorage = marketArea.getElectricityProduction()
					.getElectricityPumpedStorageDaily(hourOfDay);

			// Exchange (exogenous)
			final float exchangeExogenous = marketArea.getExchange()
					.getHourlyFlow((HOURS_PER_DAY * (Date.getDayOfYear() - 1)) + hourOfDay);
			final float exchangeExogenousAccepted = marketArea.getElectricityResultsDayAhead()
					.getExchangeAcceptedHourOfDay(hourOfDay);

			// Exchange (market coupling)
			final float exchangeMarketCoupling = marketArea.isMarketCoupling()
					? (float) marketArea.getMarketCouplingOperator().getExchangeFlows()
							.getHourlyFlow(marketArea, hourOfDay)
					: 0f;

			// Calculate hourly balance
			float balance = ((demandLoad) - (renewablesProductionPlanned + elecProductionPowerPlants
					+ elecProductionPumpedStorage)) + (exchangeExogenous + exchangeMarketCoupling);
			float curtailment = 0f;

			// Unbalance (consider floating numbers imprecision)
			if (Math.abs(balance) > EPSILON) {

				// Planned renewables production exceeds demand
				if ((renewablesProductionPlanned + elecProductionPumpedStorage) > (demandLoad
						+ exchangeExogenous + exchangeMarketCoupling)) {
					logger.debug(marketArea.getInitialsBrackets() + "On day " + Date.getDayOfYear()
							+ " in hour " + (hourOfDay + 1) + "/"
							+ (Date.getFirstHourOfToday() + hourOfDay)
							+ " renewables production exceeds demand (balance: "
							+ Statistics.round(balance, 2) + " / load: "
							+ Statistics.round(demandLoad, 2) + " / renewables: "
							+ renewablesProductionPlanned
							+ "). Renewables are curtailed by the imbalance.");

					// Set balance equal to zero since renewables could be
					// curtailed by the amount of the imbalance
					curtailment = balance;
					balance = 0f;
				}
				// Other case is more problematic. Check why they occur.
				else {

					final float demandDiff = demandLoad - demandAccepted;
					final float renewableDiff = renewablesProductionPlanned
							- renewablesProductionAccepted;
					final float exchangeDiff = Math.abs(exchangeExogenous)
							- exchangeExogenousAccepted;
					// if RES market price is higher as must run bids,
					// curtailment is ok
					if (((Math.abs(balance) - renewableDiff) < EPSILON) && (Math
							.abs(marketArea.getElectricityResultsDayAhead().getHourlyPriceOfDay(
									hourOfDay) - dayAheadBiddingPrice) < EPSILON)) {
						logger.warn(marketArea.getInitialsBrackets() + "Year " + Date.getYear()
								+ ", day " + Date.getDayOfYear() + ", hour " + (hourOfDay + 1)
								+ " Renewables not fully accepted due to mustrun conditons! Therefore curtailed. RenewablesProductionPlanned, "
								+ renewablesProductionPlanned + ", renewablesProductionAccepted "
								+ renewablesProductionAccepted + ", diff " + renewableDiff
								+ ", price " + marketArea.getElectricityResultsDayAhead()
										.getHourlyPriceOfDay(hourOfDay));
						curtailment = balance;
						balance = 0f;
					} else if ((Math.abs(balance) - renewableDiff) < EPSILON) {
						logger.warn(marketArea.getInitialsBrackets() + "Year " + Date.getYear()
								+ ", day " + Date.getDayOfYear() + ", hour " + (hourOfDay + 1)
								+ " Renewables not fully accepted! renewablesProductionPlanned, "
								+ renewablesProductionPlanned + ", renewablesProductionAccepted "
								+ renewablesProductionAccepted + ", diff " + renewableDiff
								+ ", price " + marketArea.getElectricityResultsDayAhead()
										.getHourlyPriceOfDay(hourOfDay));
					} else if ((Math.abs(balance) - exchangeDiff) < EPSILON) {
						logger.warn(marketArea.getInitialsBrackets() + "Year " + Date.getYear()
								+ ", day " + Date.getDayOfYear() + ", hour " + (hourOfDay + 1)
								+ " Exchange not fully accepted! exchangeExogenous, "
								+ exchangeExogenous + ", exchangeExogenousAccepted "
								+ exchangeExogenousAccepted + ", diff " + exchangeDiff);
					} else if ((Math.abs(balance) - exchangeDiff - renewableDiff) < EPSILON) {
						logger.warn(marketArea.getInitialsBrackets() + "Year " + Date.getYear()
								+ ", day " + Date.getDayOfYear() + ", hour " + (hourOfDay + 1)
								+ " Exchange and renewables not fully accepted! exchangeExogenous, "
								+ exchangeExogenous + ", exchangeExogenousAccepted "
								+ exchangeExogenousAccepted + ", diffExchange " + exchangeDiff
								+ ", renewablesProductionPlanned " + renewablesProductionPlanned
								+ ", renewablesProductionAccepted " + renewablesProductionAccepted
								+ ", diffRenewable " + renewableDiff);
					} else {
						logger.error(marketArea.getInitialsBrackets() + "Year " + Date.getYear()
								+ ", day " + Date.getDayOfYear() + ", hour " + (hourOfDay + 1)
								+ " logged demand/supply is not balanced (balance: "
								+ Statistics.round(balance, 2) + " / elecProd: "
								+ Statistics.round(
										elecProductionPowerPlants + renewablesProductionPlanned, 2)
								+ "/ demand: "
								+ ((demandLoad - elecProductionPumpedStorage)
										+ (exchangeExogenous + exchangeMarketCoupling))
								+ "). Market price "
								+ marketArea.getElectricityResultsDayAhead()
										.getHourlyPriceOfDay(hourOfDay)
								+ ", demandUnaccepted " + demandDiff + ", exchangeUnaccepted "
								+ exchangeDiff + ", renewableUnaccepted " + renewableDiff
								+ ", elecProductionPowerPlants " + elecProductionPowerPlants);
					}
				}
			} else {
				balance = 0f;
			}

			// Set balance in results object
			marketArea.getBalanceDayAhead().setHourlyBalance(hourOfDay, balance);
			// Make simple curtailment
			marketArea.getBalanceDayAhead().setHourlyCurtailmentRenewables(hourOfDay, curtailment);
		}
	}
	/** bids for the EEX are sent to the Auctioneer */
	@Override
	public List<DayAheadHourlyBid> callForBidsDayAheadHourly() {
		try {
			hourlyDayAheadPowerBids.clear();
			for (int i = 0; i < HOURS_PER_DAY; i++) {
				final DayAheadHourlyBid bid = generateHourlyDayAheadBid(i);
				if ((bid != null) && !bid.getBidPoints().isEmpty()) {
					hourlyDayAheadPowerBids.add(bid);
				}
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return hourlyDayAheadPowerBids;
	}

	/**
	 * Determine whether the current bid (plantIndex) is in the current
	 * hourOfDay the marginal bid. If it is save the marginal bid in the
	 * corresponding data object.
	 */
	private void determineMarginalBid(int hourOfDay, float marketClearingPrice,
			HourlyBidPower bidPoint) {

		// Set actual marginal bid which is determined by comparing
		// the market clearing price and bid price
		final float priceDifference = Math
				.abs(Math.round((bidPoint.getPrice() - marketClearingPrice) * 100)) / 100f;
		if (priceDifference <= 0.001f) {
			// Create new instance of MarginalBid
			final MarginalBid marginalBid = new MarginalBid(bidPoint);
			// Set in results object
			marketArea.getElectricityResultsDayAhead().setMarginalBidHourOfDay(hourOfDay,
					marginalBid);
		}
	}

	@Override
	public void evaluateResultsDayAhead() {
		// determine marginal bid
		for (final DayAheadHourlyBid dayAheadHourlyBid : hourlyDayAheadPowerBids) {
			final int hourOfDay = dayAheadHourlyBid.getHour();
			final float marketClearingPrice = hourlyDayAheadPowerBids.get(hourOfDay)
					.getMarketClearingPrice();

			for (final HourlyBidPower bidPoint : dayAheadHourlyBid.getBidPoints()) {
				// If bid is (partially) accepted
				if (Math.abs(bidPoint.getVolumeAccepted()) != 0) {
					/* Determine marginal bid */
					determineMarginalBid(hourOfDay, marketClearingPrice, bidPoint);
				}
			}
		}
	}

	/** Creates an hourly bid */
	private DayAheadHourlyBid generateHourlyDayAheadBid(int hour) {
		final DayAheadHourlyBid bid = new DayAheadHourlyBid(hour, TraderType.GRID_OPERATOR);

		for (final FuelName type : marketArea.getManagerRenewables().getRenewableTypes()) {

			final float volume = marketArea.getManagerRenewables().getRenewableLoad(type,
					Date.getFirstHourOfToday() + hour);

			// Check volumes
			if (volume == 0) {
				continue;
			}
			float price = dayAheadBiddingPrice;
			// Without mustrun no negative bids for RES is expectable
			if ((Settings.getMustrunYearEnd() < Date.getYear()) && (price < 0)) {
				price = 0;
				dayAheadBiddingPrice = 0;
			}
			if ((price < marketArea.getDayAheadMarketOperator().getMinPriceAllowed())
					|| (price > marketArea.getDayAheadMarketOperator().getMaxPriceAllowed())) {
				logger.error("Price for renewables is " + dayAheadBiddingPrice
						+ " however, min prices allowed for day ahead market is "
						+ marketArea.getDayAheadMarketOperator().getMinPriceAllowed()
						+ " and max price "
						+ marketArea.getDayAheadMarketOperator().getMaxPriceAllowed()
						+ ". Therefore out of range. Set 0 instead.");
				price = 0;
			}
			bid.addBidPoint(
					new HourlyBidPower.Builder(volume, price, hour, BidType.SELL, marketArea)
							.fuelType(FuelType.RENEWABLE).renewableType(type)
							.traderType(TraderType.GRID_OPERATOR).comment(type.name()).fuelCosts(0f)
							.build());
		}
		return bid;
	}

	public float getDayAheadBiddingPrice() {
		return dayAheadBiddingPrice;
	}

	@Override
	public void initialize() {
		logger.info(marketArea.getInitialsBrackets() + "Initialize " + getName());
		marketArea.setRenewablerTrader(this);
	}
}