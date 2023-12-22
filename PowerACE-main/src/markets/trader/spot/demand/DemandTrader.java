package markets.trader.spot.demand;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.trader.Trader;
import markets.trader.TraderType;
import markets.trader.spot.DayAheadTrader;
import simulations.initialization.Settings;
import simulations.scheduling.Date;

/**
 * Reads the demand data from the SQL database. If the DemandSupplierBidder is
 * active the demand is not calculated from several demand agents e.g. industry,
 * consumer, as it is done in SupplierBidder, but directly taken from the
 * database.
 *
 * 
 *
 */

public class DemandTrader extends Trader implements DayAheadTrader {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(DemandTrader.class.getName());
	/**
	 * The additive difference of the demand. Set via reflection taking values
	 * from xml-file.
	 */
	private float demandDistanceAdditive;

	/**
	 * The difference of the demand. Set via reflection taking values from
	 * xml-file.
	 */
	private float demandDistanceMultiplicative;

	/** If true bids elast */
	private boolean elasticDemand;

	/** If demand bidding is multiplicative or not. */
	private boolean multiplicative = false;

	/** Bids the hourly demand for the current day. */
	@Override
	public List<DayAheadHourlyBid> callForBidsDayAheadHourly() {
		try {
			hourlyDayAheadPowerBids.clear();
			staticDemand();

		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return hourlyDayAheadPowerBids;
	}

	/**
	 * The amount the demand can be lowered (assume mostly due to less exchange
	 * flows).
	 *
	 * @param year
	 * @param hourOfYear
	 * @return demand reduction potential
	 */
	public float demandChangingPotential(int year, int hourOfYear) {

		float demandChangingPotential = 0f;

		if (elasticDemand) {
			if (multiplicative) {
				final float totalDemand = marketArea.getDemandData().getHourlyDemand(year,
						hourOfYear);
				demandChangingPotential = totalDemand * demandDistanceMultiplicative;
			} else {
				demandChangingPotential = demandDistanceAdditive;
			}

		}

		return demandChangingPotential;
	}

	@Override
	public void evaluateResultsDayAhead() {

		for (final DayAheadHourlyBid bid : hourlyDayAheadPowerBids) {
			for (final HourlyBidPower hourlyBid : bid.getBidPoints()) {
				// if price sensitive demand may not be fully cleared and this
				// does not pose a problem
				if ((hourlyBid.getVolumeRemaining() > 0)
						&& !Settings.isMarketClearingPriceSensitive()) {
					logger.warn("[" + marketArea.getInitials() + "] Year " + Date.getYear()
							+ ", day " + Date.getDayOfYear() + ", hour " + (hourlyBid.getHour() + 1)
							+ " Demand not fully accepted! demandLoad, " + hourlyBid.getVolume()
							+ ", demandAccepted " + hourlyBid.getVolumeAccepted() + ", diff "
							+ hourlyBid.getVolumeRemaining());
				}
			}
		}
	}

	/** Called via the xml file and loads the needed demand data */
	@Override
	public void initialize() {
		logger.info(marketArea.getInitialsBrackets() + " Initialize " + getName());
	}

	public boolean isElasticDemand() {
		return elasticDemand;
	}

	/**
	 * Bid a static demand with a negative price.
	 */
	private void staticDemand() {
		final float[] volumes = marketArea.getDemandData().getDailyDemand();
		float price;
		if (Settings.isMarketClearingPriceSensitive()) {
			price = marketArea.getDayAheadMarketOperator().getMaxPriceAllowed();
		} else {
			price = marketArea.getDayAheadMarketOperator().getMinPriceAllowed();
		}

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			hourlyDayAheadPowerBids.add(new DayAheadHourlyBid(hour, price, volumes[hour],
					TraderType.DEMAND, marketArea));
		}
	}

}