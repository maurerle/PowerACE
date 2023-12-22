package results.spot;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.carbon.CarbonPrices;
import markets.bids.Bid;
import markets.bids.Bid.BidType;
import markets.bids.power.BlockBidPower;
import markets.bids.power.HourlyBidPower;
import markets.bids.power.PowerBid;
import markets.clearing.PriceCurvePoint;
import markets.trader.TraderType;
import markets.trader.spot.supply.tools.BiddingAlgorithm;
import simulations.MarketArea;
import simulations.scheduling.Date;
import simulations.scheduling.SeasonAstronomical;
import supply.powerplant.Plant;
import supply.powerplant.capacity.CapacityType;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.Unit;

/**
 *
 *
 * 
 */
public class RegularCallMarketLog {

	/** Testing purposes. */
	private static Map<Integer, Integer> counterOfDay = new HashMap<>();

	/** Testing purposes. */
	private static Map<Integer, Integer> counterOfLength = new HashMap<>();
	/** Testing purposes. */
	private static int counterStartNegative = 0;
	/** Testing purposes. */
	private static int counterStartZero = 0;
	/** Testing purposes. */
	private static Map<FuelType, Map<Integer, Integer>> counterStartZeroFuelType = new HashMap<>();
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(RegularCallMarketLog.class.getName());
	static {
		for (final FuelType fuelType : FuelType.values()) {
			counterStartZeroFuelType.put(fuelType, new HashMap<Integer, Integer>());
			for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
				counterStartZeroFuelType.get(fuelType).put(hourOfDay, 0);
			}
		}
	}

	private static void addZeroStart(FuelType fuelType, int hour) {
		if (fuelType != null) {
			counterStartZeroFuelType.get(fuelType).put(hour,
					counterStartZeroFuelType.get(fuelType).get(hour) + 1);
		}
	}

	private int logPrices;
	private final MarketArea marketArea;

	public RegularCallMarketLog(MarketArea marketArea) {
		super();
		this.marketArea = marketArea;
	}

	/**
	 * Logs the hourly prices
	 */
	public void logPrices(Map<Integer, List<Bid>> bidPointsDay, List<BlockBidPower> blockBidsDay,
			List<Float> clearingPrices, List<Float> clearingVolumes,
			final List<List<PriceCurvePoint>> temporaryPriceFunction,
			Map<Integer, PowerBid> bidsLastAcceptedSupply) {

		// Initialize values and file
		final List<FuelType> fuels = Arrays.asList(FuelType.COAL, FuelType.GAS, FuelType.LIGNITE,
				FuelType.OIL, FuelType.OTHER, FuelType.RENEWABLE, FuelType.URANIUM,
				FuelType.CLEAN_COAL, FuelType.CLEAN_GAS, FuelType.CLEAN_LIGNITE);

		if (Date.isFirstDayOfYear()) {
			logPricesInit(fuels);
		}

		// Get demand, pumpedstorage
		final Map<Integer, Float> demandAccepted = new HashMap<>();
		final Map<Integer, Float> exchangeAccepted = new HashMap<>();
		final float[] demand = marketArea.getDemandData().getDailyDemand();
		final Map<Integer, Float> pumpedStorage = new HashMap<>();
		for (final Integer hourOfDay : bidPointsDay.keySet()) {
			demandAccepted.put(hourOfDay, 0f);
			exchangeAccepted.put(hourOfDay, 0f);
			pumpedStorage.put(hourOfDay, 0f);
			for (final Bid bidPoint : bidPointsDay.get(hourOfDay)) {
				if ((bidPoint.getType() == BidType.ASK)
						&& (bidPoint.getTraderType() == TraderType.DEMAND)) {
					demandAccepted.put(hourOfDay,
							demandAccepted.get(hourOfDay) + bidPoint.getVolumeAccepted());
				}

				if (bidPoint.getTraderType() == TraderType.PUMPED_STORAGE) {
					float value = 0f;
					if (bidPoint.getBidType() == BidType.ASK) {
						value = bidPoint.getVolumeAccepted();
					} else {
						value = -bidPoint.getVolumeAccepted();
					}
					pumpedStorage.put(hourOfDay, value);
				}

				if (bidPoint.getTraderType() == TraderType.EXCHANGE) {
					float value = 0f;
					if (bidPoint.getBidType() == BidType.ASK) {
						value = bidPoint.getVolumeAccepted();
					} else {
						value = -bidPoint.getVolumeAccepted();
					}
					exchangeAccepted.put(hourOfDay, value);
				}
			}
		}

		final List<FuelName> renewableTypesCertain = new ArrayList<>();
		for (final FuelName type : marketArea.getManagerRenewables().getRenewableTypes()) {

			renewableTypesCertain.add(type);

		}

		final List<Float> renewableCertain = new ArrayList<>();
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			renewableCertain.add(marketArea.getManagerRenewables().getRenewableLoadProfile(
					renewableTypesCertain, Date.getFirstHourOfToday() + hour));

		}
		// Get exchange
		final float[] exchange = marketArea.getExchange().getHourlyFlowsOfDay(Date.getDayOfYear());
		// Calculate residual load
		final List<Float> resLoad = new ArrayList<>();
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			resLoad.add((demandAccepted.get(hour) + exchangeAccepted.get(hour)
					+ pumpedStorage.get(hour)) - renewableCertain.get(hour));

		}

		for (final Integer hour : bidPointsDay.keySet()) {

			// Make list only out of sell bids
			final List<Bid> hourlySellBids = new ArrayList<>();
			final List<Bid> hourlyAskBids = new ArrayList<>();
			for (final Bid bidPoint : bidPointsDay.get(hour)) {
				if (bidPoint.getType() == BidType.SELL) {
					hourlySellBids.add(bidPoint);
				} else {
					hourlyAskBids.add(bidPoint);
				}
			}

			// First: Sort by lowest price
			final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
					b2.getPrice());
			// Second: Sort by highest volume
			final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
					* Float.compare(b1.getVolume(), b2.getVolume());
			Collections.sort(hourlySellBids, compPrice.thenComparing(compVolume));

			HourlyBidPower bidPoint = null;
			for (int index = 0; index < hourlySellBids.size(); index++) {
				// Find last bid that has an accepted volume, if last bid is
				// reached take it
				if (((index + 1) < hourlySellBids.size())
						&& (((HourlyBidPower) hourlySellBids.get(index + 1))
								.getVolumeAccepted() > 0)) {
					continue;
				}
				bidPoint = (HourlyBidPower) hourlySellBids.get(index);
				// Only take last bid
				break;
			}

			// Find last accepted block bid
			// Add only accepted block bids for current hour
			final List<BlockBidPower> currentHourBlockBids = new ArrayList<>();
			for (final BlockBidPower blockBid : blockBidsDay) {
				if ((blockBid.getStart() <= hour) && (hour <= blockBid.getEnd())
						&& blockBid.isAccepted()) {
					currentHourBlockBids.add(blockBid);
				}
			}

			BlockBidPower blockBid;
			// Write block bid to String
			if (currentHourBlockBids.isEmpty()) {
				blockBid = null;
			} else {
				blockBid = currentHourBlockBids.get(currentHourBlockBids.size() - 1);
			}

			if ((blockBid != null) && (blockBid.getPrice() > bidPoint.getPrice())) {
				bidsLastAcceptedSupply.put(Date.getFirstHourOfToday() + hour, blockBid);
			} else if (bidPoint != null) {
				bidsLastAcceptedSupply.put(Date.getFirstHourOfToday() + hour, bidPoint);
			} else {
				// This case should not occur
				logger.warn("This is not supposed to happen!");

			}

		}
		// Remember the hours plant was running
		logRunningHours(bidsLastAcceptedSupply, bidPointsDay, blockBidsDay);

		final int firstHourOfToday = Date.getFirstHourOfToday();
		final int day = Date.getDayOfYear();
		// Print price function for each hour of the day
		for (final Integer hour : bidPointsDay.keySet()) {

			// General information for hour
			final float price = clearingPrices.get(hour);
			final List<Object> lines = new ArrayList<>();
			lines.add(Date.getFirstHourOfToday() + hour);
			lines.add(Date.getDayOfYear());
			lines.add(hour);
			lines.add(SeasonAstronomical.getSeason(firstHourOfToday).name());
			lines.add(marketArea.getDemandData().getHourlyDemand(hour));
			lines.add(marketArea.getDemandData().getHourlyDemand(hour) + exchange[hour]);
			lines.add(price);
			lines.add(clearingVolumes.get(hour));
			lines.add(renewableCertain.get(hour));
			lines.add(pumpedStorage.get(hour));
			for (final FuelName renewableType : marketArea.getManagerRenewables()
					.getRenewableTypes()) {
				final List<FuelName> renewable = new ArrayList<>();
				renewable.add(renewableType);
				lines.add(marketArea.getManagerRenewables().getRenewableLoadProfile(renewable,
						Date.getFirstHourOfToday() + hour));
			}

			lines.add(exchange[hour]);
			lines.add(resLoad.get(hour));
			lines.add(demandAccepted.get(hour));
			lines.add(demand[hour]);

			// Find out if supply was setting the price(
			boolean supplyPriceSetting = false;
			for (final Bid bidPoint : bidPointsDay.get(hour)) {
				if ((price == bidPoint.getPrice()) && (bidPoint.getType() == BidType.SELL)) {
					supplyPriceSetting = true;
					break;
				}
			}

			// Check if demand or supply was setting price
			if (!supplyPriceSetting) {
				lines.add("DemandPriceSetting");
			} else {
				lines.add("SupplyPriceSetting");
			}

			// Make list only out of sell bids
			final List<Bid> hourlySellBids = new ArrayList<>();
			final List<Bid> hourlyAskBids = new ArrayList<>();
			for (final Bid bidPoint : bidPointsDay.get(hour)) {
				if (bidPoint.getType() == BidType.SELL) {
					hourlySellBids.add(bidPoint);
				} else {
					hourlyAskBids.add(bidPoint);
				}
			}

			// First: Sort by lowest price
			final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
					b2.getPrice());
			// Second: Sort by highest volume
			final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
					* Float.compare(b1.getVolume(), b2.getVolume());
			Collections.sort(hourlySellBids, compPrice.thenComparing(compVolume));

			HourlyBidPower bidPoint = null;
			for (int index = 0; index < hourlySellBids.size(); index++) {
				// Find last bid that has an accepted volume, if last bid is
				// reached take it
				if (((index + 1) < hourlySellBids.size())
						&& (((HourlyBidPower) hourlySellBids.get(index + 1))
								.getVolumeAccepted() > 0)) {
					continue;
				}
				bidPoint = (HourlyBidPower) hourlySellBids.get(index);
				// Only take last bid
				break;
			}

			// Find last accepted block bid
			// Add only accepted block bids for current hour
			final List<BlockBidPower> currentHourBlockBids = new ArrayList<>();
			for (final BlockBidPower blockBid : blockBidsDay) {
				if ((blockBid.getStart() <= hour) && (hour <= blockBid.getEnd())
						&& blockBid.isAccepted()) {
					currentHourBlockBids.add(blockBid);
				}
			}

			// Sort list with accepted block bids for current hour
			final Comparator<BlockBidPower> byPrice = (BlockBidPower o1, BlockBidPower o2) -> Float
					.compare(o1.getPrice(), o2.getPrice());
			final Comparator<BlockBidPower> byVolume = (BlockBidPower o1, BlockBidPower o2) -> Float
					.compare(o1.getVolume(), o2.getVolume());
			Collections.sort(currentHourBlockBids, byPrice.thenComparing(byVolume));

			BlockBidPower blockBid;
			// Write block bid to String
			if (currentHourBlockBids.isEmpty()) {
				blockBid = null;
			} else {
				blockBid = currentHourBlockBids.get(currentHourBlockBids.size() - 1);
			}

			// Block bids or hourly bids can set the price, what has value
			// closest to market price should be price setting
			if ((blockBid != null) && (blockBid.getPrice() > bidPoint.getPrice())) {
				lines.add("BlockBid");
				lines.add(blockBid.getPrice());
				lines.add(blockBid.getStartupCosts());
				lines.add(marketArea.getElectricityResultsDayAhead()
						.getMarginalBidRunningHours(firstHourOfToday + hour));
				lines.add(blockBid.getLength());
				lines.add(blockBid.getFuelCosts());
				lines.add(blockBid.getEmissionCosts());
				lines.add(blockBid.getOperAndMainCosts());

				lines.add(marketArea.getStartUpCosts()
						.getMarginalStartupCostsHot(blockBid.getPlant(), day));
				lines.add(marketArea.getStartUpCosts()
						.getMarginalStartupCostsWarm(blockBid.getPlant(), day));
				lines.add(marketArea.getStartUpCosts()
						.getMarginalStartupCostsCold(blockBid.getPlant(), day));
				lines.add(marketArea.getStartUpCosts().getDepreciationCosts(blockBid.getPlant()));

				lines.add(blockBid.getVolume());
				lines.add(blockBid.getVolume());
				lines.add(blockBid.getComment());
				lines.add(blockBid.getFuelType());
				lines.add(blockBid.getPlant().getUnitID());
				lines.add(blockBid.getPlant().getEfficiency());
				lines.add(blockBid.getPlant().getEnergyConversion().name());

				lines.add(blockBid.getBidType());

			} else if (bidPoint != null) {

				lines.add("HourlyBid");
				lines.add(bidPoint.getPrice());
				lines.add(bidPoint.getStartupCosts());
				lines.add(marketArea.getElectricityResultsDayAhead()
						.getMarginalBidRunningHours(firstHourOfToday + hour));
				lines.add(bidPoint.getRunningHoursExpected());
				lines.add(bidPoint.getFuelCosts());
				lines.add(bidPoint.getEmissionCosts());
				lines.add(bidPoint.getOperAndMainCosts());
				if (bidPoint.getPlant() != null) {
					lines.add(marketArea.getStartUpCosts()
							.getMarginalStartupCostsHot(bidPoint.getPlant(), day));
					lines.add(marketArea.getStartUpCosts()
							.getMarginalStartupCostsWarm(bidPoint.getPlant(), day));
					lines.add(marketArea.getStartUpCosts()
							.getMarginalStartupCostsCold(bidPoint.getPlant(), day));
					lines.add(
							marketArea.getStartUpCosts().getDepreciationCosts(bidPoint.getPlant()));
				} else {
					lines.add(null);
					lines.add(null);
					lines.add(null);
					lines.add(null);
				}
				lines.add(bidPoint.getVolume());
				lines.add(bidPoint.getVolumeAccepted());
				lines.add(bidPoint.getComment());
				lines.add(bidPoint.getFuelType());
				if (bidPoint.getPlant() != null) {
					lines.add(bidPoint.getPlant().getUnitID());
					lines.add(bidPoint.getPlant().getEfficiency());
					lines.add(bidPoint.getPlant().getEnergyConversion().name());
				} else {
					lines.add(null);
					lines.add(null);
					lines.add(null);
				}
				lines.add(bidPoint.getBidType());

				bidsLastAcceptedSupply.put(Date.getFirstHourOfToday() + hour, bidPoint);
			} else {
				// This case should not occur
				logger.warn("This is not supposed to happen!");

				for (int index = 0; index < 11; index++) {
					lines.add(null);
				}
			}

			final Map<FuelType, Float> cogenerationVolumeUtility = new HashMap<>();
			final Map<FuelType, Float> cogenerationVolumeIndustry = new HashMap<>();
			for (final FuelType fuelType : fuels) {
				cogenerationVolumeUtility.put(fuelType, 0f);
				cogenerationVolumeIndustry.put(fuelType, 0f);
			}

			for (final Bid hourlySellBid : hourlySellBids) {
				bidPoint = (HourlyBidPower) hourlySellBid;
				if ((bidPoint.getComment() != null)
						&& bidPoint.getComment().startsWith("Cogeneration bid")) {
					final FuelType fuelType = bidPoint.getPlant().getFuelType();
					if (bidPoint.getTrader().getName().equals("Industrie")) {
						cogenerationVolumeIndustry.put(fuelType,
								cogenerationVolumeIndustry.get(fuelType)
										+ bidPoint.getVolumeAccepted());
					} else {
						cogenerationVolumeUtility.put(fuelType,
								cogenerationVolumeUtility.get(fuelType)
										+ bidPoint.getVolumeAccepted());
					}

				}
			}

			for (final FuelType fuelType : fuels) {
				lines.add(cogenerationVolumeUtility.get(fuelType));
			}
			for (final FuelType fuelType : fuels) {
				lines.add(cogenerationVolumeIndustry.get(fuelType));
			}

			final Map<FuelType, Float> cogenerationVolumeAdditionalUtility = new HashMap<>();
			final Map<FuelType, Float> cogenerationVolumeAdditionalIndustry = new HashMap<>();
			for (final FuelType fuelType : fuels) {
				cogenerationVolumeAdditionalUtility.put(fuelType, 0f);
				cogenerationVolumeAdditionalIndustry.put(fuelType, 0f);
			}

			for (final FuelType fuelType : fuels) {
				lines.add(cogenerationVolumeAdditionalUtility.get(fuelType));
			}
			for (final FuelType fuelType : fuels) {
				lines.add(cogenerationVolumeAdditionalIndustry.get(fuelType));
			}

			// Add production values
			logPricesProduction(hour, hourlySellBids, hourlyAskBids, fuels, lines, blockBidsDay,
					marketArea);

			lines.add(CarbonPrices.getPricesDaily(marketArea));
			for (final FuelName fuelName : FuelName.values()) {
				lines.add(marketArea.getFuelPrices().getPricesYearly(fuelName));
			}

			// Write to file
			LoggerXLSX.writeLine(logPrices, lines);
		}

		if (Date.isLastDayOfYear()) {
			LoggerXLSX.close(logPrices);
		}
	}

	/**
	 * Initialize log file
	 *
	 * @param fuels
	 *            - All fuels that will be stored.
	 */
	public void logPricesInit(List<FuelType> fuels) {

		final List<ColumnHeader> titleLine = new ArrayList<>();

		titleLine.add(new ColumnHeader("hour_Of_Year", Unit.HOUR));
		titleLine.add(new ColumnHeader("day", Unit.NONE));
		titleLine.add(new ColumnHeader("hour_Of_Day", Unit.HOUR));
		titleLine.add(new ColumnHeader("season", Unit.NONE));
		titleLine.add(new ColumnHeader("Historical price", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("Historical Volume", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("historic Volume Incl Exchange", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("market Price", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("market Volume", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("avg Forecast", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("renewable Certain", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("renewable Uncertain", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("pumped_Storage_Demand", Unit.ENERGY_VOLUME));

		for (final FuelName renewableType : marketArea.getManagerRenewables().getRenewableTypes()) {
			titleLine.add(new ColumnHeader(renewableType.toString(), Unit.ENERGY_VOLUME));
		}

		titleLine.add(new ColumnHeader("export", Unit.NONE));
		titleLine.add(new ColumnHeader("residual Load", Unit.NONE));
		titleLine.add(new ColumnHeader("demand accepted", Unit.NONE));
		titleLine.add(new ColumnHeader("demand Historical", Unit.NONE));
		titleLine.add(new ColumnHeader("type", Unit.NONE));
		titleLine.add(new ColumnHeader("hourly_or_block", Unit.NONE));
		titleLine.add(new ColumnHeader("bid Price", Unit.NONE));
		titleLine.add(new ColumnHeader("startup", Unit.NONE));
		titleLine.add(new ColumnHeader("runningHours", Unit.NONE));
		titleLine.add(new ColumnHeader("runningHoursExpected", Unit.NONE));
		titleLine.add(new ColumnHeader("fuel Costs;", Unit.NONE));
		titleLine.add(new ColumnHeader("emission Costs", Unit.NONE));
		titleLine.add(new ColumnHeader("O&M_Costs", Unit.NONE));
		titleLine.add(new ColumnHeader("startup_Costs_Hot", Unit.NONE));
		titleLine.add(new ColumnHeader("startup_Costs_Warm", Unit.NONE));
		titleLine.add(new ColumnHeader("startup_Costs_Cold", Unit.NONE));
		titleLine.add(new ColumnHeader("startup_Costs_Deprecation", Unit.NONE));
		titleLine.add(new ColumnHeader("markup", Unit.NONE));
		titleLine.add(new ColumnHeader("volume", Unit.NONE));
		titleLine.add(new ColumnHeader("accepted Volume", Unit.NONE));
		titleLine.add(new ColumnHeader("comment", Unit.NONE));
		titleLine.add(new ColumnHeader("source", Unit.NONE));
		titleLine.add(new ColumnHeader("plant id", Unit.NONE));
		titleLine.add(new ColumnHeader("efficiency", Unit.NONE));
		titleLine.add(new ColumnHeader("energy conversion", Unit.NONE));
		titleLine.add(new ColumnHeader("bid Type", Unit.NONE));

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader("CogenerationUtility " + fuelType, Unit.ENERGY_VOLUME));
		}

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader("CogenerationIndustry " + fuelType, Unit.ENERGY_VOLUME));
		}

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader("CogenerationUtility Additional " + fuelType,
					Unit.ENERGY_VOLUME));
		}

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader("CogenerationIndustry Additional " + fuelType,
					Unit.ENERGY_VOLUME));
		}

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader(fuelType.toString(), Unit.ENERGY_VOLUME));
		}

		for (final FuelName renewableType : marketArea.getManagerRenewables().getRenewableTypes()) {
			titleLine.add(new ColumnHeader(renewableType.toString(), Unit.ENERGY_VOLUME));
		}

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader(
					fuelType + " Capacity Avail After Expected Outages Incl Reserves ",
					Unit.CAPACITY));
		}

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader(
					fuelType + " Capacity Avail After Expected+Unexpected Outages Incl Reserves ",
					Unit.CAPACITY));
		}

		for (final FuelType fuelType : fuels) {
			titleLine.add(new ColumnHeader(
					fuelType + "  Capacity Avail After Expected Outages Exl Reserves ",
					Unit.CAPACITY));
		}

		titleLine.add(new ColumnHeader("demand Accepted", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("exchange", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("carbonPrice", Unit.ENERGY_VOLUME));

		for (final FuelName fuelType : FuelName.values()) {
			titleLine.add(new ColumnHeader(fuelType + "_Price", Unit.ENERGY_PRICE));
		}

		logPrices = LoggerXLSX.newLogObject(Folder.DAY_AHEAD_PRICES,
				marketArea.getInitialsUnderscore() + "ClearingPrices" + Date.getYear(), "",
				titleLine, marketArea.getIdentityAndNameLong(), Frequency.HOURLY);

	}

	/**
	 * Log production in prices file.
	 *
	 * @param hour
	 * @param hourlySellBids
	 * @param hourlyAskBids
	 * @param fuels
	 * @param hourString
	 */
	public void logPricesProduction(int hour, List<Bid> hourlySellBids, List<Bid> hourlyAskBids,
			List<FuelType> fuels, List<Object> hourString, List<BlockBidPower> blockBidsDay,
			MarketArea marketArea) {
		final int hourOfYear = Date.getFirstHourOfToday() + hour;
		// Add up values from bids
		// Hourly bids
		final Map<FuelType, Float> production = new HashMap<>();
		final Map<FuelName, Float> productionRenewable = new HashMap<>();
		for (final Bid bidPointOr : hourlySellBids) {

			final HourlyBidPower bidPoint = (HourlyBidPower) bidPointOr;

			final FuelType fuelType = bidPoint.getFuelType();
			final TraderType traderType = bidPoint.getTraderType();

			// Sometimes plant has the fueltype renewable but is not part of the
			// exogenous renewables
			if ((fuelType == FuelType.RENEWABLE) && (traderType == TraderType.GRID_OPERATOR)) {
				final FuelName renewableType = bidPoint.getRenewableType();

				if (bidPoint.getVolumeAccepted() < bidPoint.getVolume()) {
					logger.warn(Date.getYear() + "/" + Date.getDayOfYear()
							+ " Renewable offer is not totally accepted! " + bidPoint + ", "
							+ renewableType + ", " + bidPoint.getVolumeAccepted() + ", id "
							+ bidPoint.getIdentifier());
				}

				if (productionRenewable.containsKey(renewableType)) {
					productionRenewable.put(renewableType,
							productionRenewable.get(renewableType) + bidPoint.getVolumeAccepted());
				} else {
					productionRenewable.put(renewableType, bidPoint.getVolumeAccepted());
				}
			}

			if (production.containsKey(fuelType)) {
				production.put(fuelType, production.get(fuelType) + bidPoint.getVolumeAccepted());
			} else {
				production.put(fuelType, bidPoint.getVolumeAccepted());
			}
		}
		// Block bids
		for (final BlockBidPower blockBid : blockBidsDay) {
			if (blockBid.isAccepted() && (blockBid.getStart() <= hour)
					&& (hour <= blockBid.getEnd())) {
				final FuelType source = blockBid.getFuelType();
				if (production.containsKey(source)) {
					production.put(source, production.get(source) + blockBid.getVolume());
				} else {
					production.put(source, blockBid.getVolume());
				}
			}
		}

		// Write values to string
		for (final FuelType fuelType : fuels) {
			hourString.add(production.get(fuelType));
		}
		for (final FuelName renewableType : marketArea.getManagerRenewables().getRenewableTypes()) {
			hourString.add(productionRenewable.get(renewableType));
		}

		for (final FuelType fuelType : fuels) {
			hourString.add(marketArea.getAvailabilitiesPlants().getCapacity(Date.getYear(),
					hourOfYear, fuelType,
					Stream.of(CapacityType.NON_USABILITY_EXPECTED).collect(Collectors.toSet())));

		}

		for (final FuelType fuelType : fuels) {
			hourString.add(marketArea.getAvailabilitiesPlants().getCapacity(Date.getYear(),
					hourOfYear, fuelType, null));
		}

		for (final FuelType fuelType : fuels) {
			hourString.add(marketArea.getAvailabilitiesPlants().getCapacity(Date.getYear(),
					hourOfYear, fuelType,
					Stream.of(CapacityType.NON_USABILITY_EXPECTED).collect(Collectors.toSet())));
		}

		final Map<TraderType, Float> demand = new TreeMap<>();
		boolean exchangeDemand = false;
		for (final Bid bidPointOr : hourlyAskBids) {

			final HourlyBidPower bidPoint = (HourlyBidPower) bidPointOr;

			final TraderType trader = bidPoint.getTraderType();

			if (trader == TraderType.EXCHANGE) {
				exchangeDemand = true;
				// initialize value if necessary
				if (!demand.containsKey(trader)) {
					demand.put(trader, 0f);
				}
				demand.put(trader, demand.get(trader) + bidPoint.getVolumeAccepted());
			}

			if (trader == TraderType.DEMAND) {
				// initialize value if necessary
				if (!demand.containsKey(trader)) {
					demand.put(trader, 0f);
				}
				demand.put(trader, demand.get(trader) + bidPoint.getVolumeAccepted());
			}
		}

		for (final TraderType trader : demand.keySet()) {
			hourString.add(demand.get(trader));
		}

		if (!exchangeDemand) {
			hourString.add("NoDemand");
		}

	}

	public void logRunningHours(Map<Integer, PowerBid> bidsLastAcceptedSupply,
			Map<Integer, List<Bid>> bidPointsDay, List<BlockBidPower> blockBidsDay) {

		// Find all plants that were setting price
		final int firstHourOfToday = Date.getFirstHourOfToday();
		final Set<Plant> plants = new HashSet<>();
		for (final Bid bid : bidsLastAcceptedSupply.values()) {
			plants.add(((PowerBid) bid).getPlant());
		}

		// Initialize list that contains for each plant for each hour the bid
		final Map<Plant, Map<Integer, PowerBid>> bids = new HashMap<>();
		for (final Plant plant : plants) {
			bids.put(plant, new HashMap<Integer, PowerBid>());
		}

		// Add all bids for plant
		// hourly bids
		for (final Integer hourOfDay : bidPointsDay.keySet()) {
			for (final Bid bidPoint : bidPointsDay.get(hourOfDay)) {
				if (plants.contains(((HourlyBidPower) bidPoint).getPlant())) {
					bids.get(((HourlyBidPower) bidPoint).getPlant()).put(hourOfDay,
							(HourlyBidPower) bidPoint);
				}
			}
		}
		// block bids, are added for each hour
		for (final BlockBidPower blockBid : blockBidsDay) {
			if (plants.contains(blockBid.getPlant())) {
				for (int hourOfDay = blockBid.getStart(); hourOfDay <= blockBid
						.getEnd(); hourOfDay++) {
					bids.get(blockBid.getPlant()).put(hourOfDay, blockBid);
				}
			}
		}

		final Map<Integer, Integer> marginalBidsHoursDaily = new HashMap<>();

		// get running time for plants
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			final Plant plant = bidsLastAcceptedSupply.get(firstHourOfToday + hour).getPlant();

			int hoursBefore = 0;
			for (int hoursBeforeCounter = hour - 1; hoursBeforeCounter > -1; hoursBeforeCounter--) {
				if ((bids.get(plant).get(hoursBeforeCounter) != null)
						&& bids.get(plant).get(hoursBeforeCounter).isAccepted()) {
					hoursBefore++;
				}
			}

			int hoursAfter = 0;
			for (int hoursAfterCounter = hour
					+ 1; hoursAfterCounter < HOURS_PER_DAY; hoursAfterCounter++) {
				if ((bids.get(plant).get(hoursAfterCounter) != null)
						&& bids.get(plant).get(hoursAfterCounter).isAccepted()) {
					hoursAfter++;
				}
			}

			final int totalHours = hoursBefore + 1 + hoursAfter;

			final boolean loggingActive = false;

			if ((totalHours < 8) && (bidsLastAcceptedSupply.get(firstHourOfToday + hour)
					.getStartupCosts() <= 0f)) {

				if (bidsLastAcceptedSupply.get(firstHourOfToday + hour)
						.getStartupCosts() > -0.0001) {
					counterStartZero++;
					addZeroStart(bidsLastAcceptedSupply.get(firstHourOfToday + hour).getFuelType(),
							hour);
				} else {
					counterStartNegative++;

				}

				if (loggingActive) {
					logger.error("counterStartZero " + counterStartZero);
					logger.error("counterStartNegative " + counterStartNegative);
				}

				if (!counterOfLength.containsKey(totalHours)) {
					counterOfLength.put(totalHours, 0);
				}
				counterOfLength.put(totalHours, counterOfLength.get(totalHours) + 1);

				if (loggingActive) {
					for (final FuelType fuelType : FuelType.values()) {
						final StringBuffer sb = new StringBuffer(fuelType + " ");
						for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
							sb.append("h" + hourOfDay + " "
									+ counterStartZeroFuelType.get(fuelType).get(hourOfDay) + " ");
						}
						logger.error(sb.toString());
					}
				}

				if (!counterOfDay.containsKey(hour)) {
					counterOfDay.put(hour, 0);
				}
				counterOfDay.put(hour, counterOfDay.get(hour) + 1);

				if (loggingActive) {
					StringBuilder sb = new StringBuilder();
					for (final int hourOfLength : counterOfLength.keySet()) {
						if (sb.length() > 0) {
							sb.append(", ");
						}
						sb.append("hourOfLength: " + hourOfLength + " #"
								+ counterOfLength.get(hourOfLength));
					}
					logger.error(sb.toString());

					sb = new StringBuilder();
					for (final int hourOfDay : counterOfDay.keySet()) {
						if (sb.length() > 0) {
							sb.append(", ");
						}
						sb.append("hourOfDay: " + hourOfDay + " #" + counterOfDay.get(hourOfDay));
					}
					logger.error(sb.toString());

					final StringBuilder sbAvoidTotal = new StringBuilder();
					final StringBuilder sbAvoidReal = new StringBuilder();
					final StringBuilder sbNotInMarket = new StringBuilder();
					for (final FuelType fuelType : FuelType.values()) {
						sbAvoidTotal.append(fuelType + " "
								+ BiddingAlgorithm.getCounterAvoidShutdownTotal().get(fuelType)
								+ " ");
						sbAvoidReal.append(fuelType + " "
								+ BiddingAlgorithm.getCounterAvoidShutdownReal().get(fuelType)
								+ " ");
						sbNotInMarket.append(fuelType + " "
								+ BiddingAlgorithm.getCounterNotInMarket().get(fuelType) + " ");
					}
					logger.error("CounterAvoidedShutdownTotal " + sbAvoidTotal.toString());
					logger.error("CounterAvoidedShutdownReal " + sbAvoidReal.toString());
					logger.error("CounterNotInMarket " + sbNotInMarket.toString());
				}

			}

			marginalBidsHoursDaily.put(firstHourOfToday + hour, totalHours);
		}

		marketArea.getElectricityResultsDayAhead().addMarginalRunningHours(marginalBidsHoursDaily);
	}

	public void logSecurityOfSupply(Map<Integer, List<Bid>> bidPointsDay,
			final List<List<PriceCurvePoint>> temporaryPriceFunction,
			HashMap<TraderType, Map<Integer, Map<BidType, Float>>> volumeRequested) {

		for (final Integer hour : bidPointsDay.keySet()) {

			final float exchangeAsk = volumeRequested.get(TraderType.EXCHANGE).get(hour)
					.get(BidType.ASK);
			final float exchangeSell = volumeRequested.get(TraderType.EXCHANGE).get(hour)
					.get(BidType.SELL);

			final int lastBid = temporaryPriceFunction.get(hour).size() - 1;
			final PriceCurvePoint priceCurvePoint = temporaryPriceFunction.get(hour).get(lastBid);

			final Float exchangeVolumeAskHour = exchangeAsk;
			final float exchangeVolumeSellHour = exchangeSell;

			final float askWithoutExchange = priceCurvePoint.getAskVolumeMaximum()
					- exchangeVolumeAskHour;
			final float askWithExchange = priceCurvePoint.getAskVolumeMaximum();

			// Strategic reserve only bids when needed therefore adjustments
			// have to made to get full capacity of strategic reserve at all
			// times
			final float sellWithExchange = priceCurvePoint.getSellVolumeMaximum();

			final float sellWithoutExchange = priceCurvePoint.getSellVolumeMaximum()
					- exchangeVolumeSellHour;

			final float surPlusWithExchange = sellWithExchange - askWithExchange;
			final float surPlusWithoutExchange = sellWithoutExchange - askWithoutExchange;

			final float levelWithExchange = (surPlusWithExchange / askWithExchange) + 1;
			final float levelWithoutExchange = (surPlusWithoutExchange / askWithoutExchange) + 1;

			marketArea.getSecurityOfSupply().addVolume(Date.getFirstHourOfToday() + hour,
					surPlusWithExchange, surPlusWithoutExchange, levelWithExchange,
					levelWithoutExchange);
		}
	}

}
