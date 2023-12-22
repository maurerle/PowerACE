package results.spot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.scheduling.Date;

/**
 * Cache hourly congestion revenue generated from market coupling
 * <p>
 * Congestion revenue from coupling of two market areas is defined as the
 * product of the price differences times the exchange volume.
 * 
 * @author PR
 * @since 08/2013
 * 
 */
public class CongestionRevenue {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(CongestionRevenue.class.getName());

	/**
	 * Hourly congestion revenue for modeled interconnectors [EUR]
	 * <p>
	 * {fromMarketArea{toMarketArea{dateKey(year,hourOfYear){value}}}}
	 */
	private Map<MarketArea, Map<MarketArea, Map<Integer, Double>>> congestionRevenueHourly = new ConcurrentHashMap<>();
	/** Coupled market areas */
	private final List<MarketArea> marketAreas;

	public CongestionRevenue(List<MarketArea> marketAreas) {
		this.marketAreas = marketAreas;
	}

	/**
	 * Return congestion revenue for the specified hour of the current day
	 */
	public double getCongestionRevenue(int hourOfDay, MarketArea fromMarketArea,
			MarketArea toMarketArea) {
		final int year = Date.getYear();
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		final int dateKey = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);

		if (congestionRevenueHourly.containsKey(fromMarketArea)) {
			if (congestionRevenueHourly.get(fromMarketArea).containsKey(toMarketArea)) {
				if (congestionRevenueHourly.get(fromMarketArea).get(toMarketArea)
						.containsKey(dateKey)) {
					return congestionRevenueHourly.get(fromMarketArea).get(toMarketArea)
							.get(dateKey);
				} else {
					// If current hour is not available (e.g. because market
					// coupling was not successful) return 0 which is ok for
					// sums (though, pay attention when using the return value
					// e.g. for calculation of an average)
					logger.warn("Congestion revenue between " + fromMarketArea + " and "
							+ toMarketArea + " in hour " + hourOfYear + " in year " + year
							+ " not available. 0 returned, but check!");
				}
			}
		}
		// Return 0 if interconnector does not exist
		return 0.0;
	}

	/**
	 * Return total congestion revenue over all interconnectors for the
	 * specified hour of the current day of the current year
	 * 
	 * @param hourOfDay
	 * @return
	 */
	public double getCongestionRevenueTotalHour(int hourOfDay) {
		double congestionRevenue = 0;
		// Loop all interconnectors and sum up congestion revenue
		for (final MarketArea fromMarketArea : congestionRevenueHourly.keySet()) {
			for (final MarketArea toMarketArea : congestionRevenueHourly.get(fromMarketArea)
					.keySet()) {
				congestionRevenue += getCongestionRevenue(hourOfDay, fromMarketArea, toMarketArea);
			}
		}
		return congestionRevenue;
	}

	private void initialize() {
		congestionRevenueHourly = new HashMap<>();
		// Initialize map of congestion revenue
		for (final MarketArea fromMarketArea : marketAreas) {
			congestionRevenueHourly.put(fromMarketArea, new HashMap<>());
			for (final MarketArea toMarketArea : marketAreas) {
				if (!fromMarketArea.equals(toMarketArea)) {
					congestionRevenueHourly.get(fromMarketArea).put(toMarketArea, new HashMap<>());
				}
			}
		}
	}

	/**
	 * Calculates congestion revenue after market coupling for all hours of the
	 * current day
	 */
	public void setCongestionRevenue(Map<MarketArea, Map<Integer, Float>> marketClearingPricesDaily,
			ExchangeFlows exchangeFlows) {

		// Reset map on first day of year
		if (Date.isFirstDayOfYear()) {
			initialize();
		}

		// Loop hours
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
			// Loop all market areas
			for (final MarketArea fromMarketArea : marketClearingPricesDaily.keySet()) {
				for (final MarketArea toMarketArea : marketClearingPricesDaily.keySet()) {

					if (fromMarketArea != toMarketArea) {
						// Determine congestion revenue:
						// price difference * exchange volume
						final float fromMarketAreaPrice = marketClearingPricesDaily
								.get(fromMarketArea).get(hourOfDay);
						final float toMarketAreaPrice = marketClearingPricesDaily.get(toMarketArea)
								.get(hourOfDay);
						final double exchangeVolume = exchangeFlows.getHourlyFlow(fromMarketArea,
								toMarketArea, hourOfDay);
						final double congestionRevenue = (toMarketAreaPrice - fromMarketAreaPrice)
								* exchangeVolume;

						// Add to map
						final int dateKey = Date.getKeyHourlyWithHourOfYear(Date.getYear(),
								hourOfYear);
						congestionRevenueHourly.get(fromMarketArea).get(toMarketArea).put(dateKey,
								congestionRevenue);
					}
				}
			}
		}
	}
}