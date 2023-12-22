package results.spot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.logging.Folder;
import tools.logging.LoggerCSV;

/**
 * Cache hourly (endogenous) exchange between coupled market areas
 * <p>
 * 
 * @author PR, Christian Will
 * @since 08/2013
 *
 */
public final class ExchangeFlows {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(ExchangeFlows.class.getName());
	/**
	 * Hourly flow data for modeled interconnectors [MW]
	 * <p>
	 * {fromMarketArea{toMarketArea{dateKey(year,hourOfYear){value}}}}
	 */
	private final Map<MarketArea, Map<MarketArea, Map<Integer, Double>>> exchangeFlowsHourly = new HashMap<>();
	/** Coupled market areas */
	private final List<MarketArea> marketAreas;

	public ExchangeFlows(List<MarketArea> marketAreas) {
		this.marketAreas = marketAreas;
		initialize();
	}

	/**
	 * Get a list of the hourly flows for the specified market area on the
	 * respective day and year
	 */
	public Map<Integer, Float> getDailyFlow(MarketArea marketArea, int year, int dayOfYear) {
		final Map<Integer, Float> dailyFlow = new LinkedHashMap<>();
		for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
			dailyFlow.put(hour, (float) getHourlyFlowByMarketArea(marketArea, year,
					Date.getFirstHourOfDay(dayOfYear) + hour));
		}
		return dailyFlow;
	}

	public double getFlowSumYear(MarketArea fromMarketArea, MarketArea toMarketArea, int year) {
		double flowSum = 0;
		if (exchangeFlowsHourly.containsKey(fromMarketArea)) {
			if (exchangeFlowsHourly.get(fromMarketArea).containsKey(toMarketArea)) {
				if (!exchangeFlowsHourly.get(fromMarketArea).get(toMarketArea).isEmpty()) {

					for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
						final int dateKey = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
						if (exchangeFlowsHourly.get(fromMarketArea).get(toMarketArea)
								.containsKey(dateKey)) {
							flowSum += exchangeFlowsHourly.get(fromMarketArea).get(toMarketArea)
									.get(dateKey);
						}
					}
				}
			}
		}
		// Return 0 if interconnector does not exist
		return flowSum;
	}

	/**
	 * Get the hourly flow for the market area and hourOfDay on current day and
	 * year. The algebraic sign defines the net direction: (+) exports / (-)
	 * imports
	 */
	public double getHourlyFlow(MarketArea marketArea, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		return getHourlyFlowByMarketArea(marketArea, Date.getYear(), hourOfYear);
	}

	/**
	 * Get the hourly flow for the specified interconnector and hourOfDay on
	 * current day and year
	 */
	public double getHourlyFlow(MarketArea fromMarketArea, MarketArea toMarketArea, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		final int year = Date.getYear();
		return getHourlyFlow(fromMarketArea, toMarketArea, year, hourOfYear);
	}

	public double getHourlyFlow(MarketArea fromMarketArea, MarketArea toMarketArea, int year,
			int hourOfYear) {
		final int dateKey = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
		if (exchangeFlowsHourly.containsKey(fromMarketArea)) {
			if (exchangeFlowsHourly.get(fromMarketArea).containsKey(toMarketArea)) {
				if (exchangeFlowsHourly.get(fromMarketArea).get(toMarketArea)
						.containsKey(dateKey)) {
					return exchangeFlowsHourly.get(fromMarketArea).get(toMarketArea).get(dateKey);
				}
			}
		}
		// Return 0 if interconnector does not exist
		return 0;
	}

	public double getHourlyFlowByMarketArea(MarketArea marketArea, int year, int hourOfYear) {
		final double imports = getHourlyFlowImportsByMarketArea(marketArea, year, hourOfYear);
		final double exports = getHourlyFlowExportsByMarketArea(marketArea, year, hourOfYear);
		return exports - imports;
	}

	private double getHourlyFlowExportsByMarketArea(MarketArea marketArea, int year,
			int hourOfYear) {
		double exports = 0;
		for (final MarketArea toMarketArea : exchangeFlowsHourly.get(marketArea).keySet()) {
			if (!marketArea.equals(toMarketArea)) {
				exports += getHourlyFlow(marketArea, toMarketArea, year, hourOfYear);
			}
		}
		return exports;
	}

	/**
	 * Get the sum of hourly export flows from the market area and hourOfDay on
	 * current day and year. According to convention, exports have a positive
	 * (+) algebraic sign.
	 * 
	 */
	public double getHourlyFlowExportsFromMarketArea(MarketArea marketArea, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		return getHourlyFlowExportsByMarketArea(marketArea, Date.getYear(), hourOfYear);
	}

	private double getHourlyFlowImportsByMarketArea(MarketArea marketArea, int year,
			int hourOfYear) {
		double imports = 0;
		for (final MarketArea fromMarketArea : exchangeFlowsHourly.keySet()) {
			if (!marketArea.equals(fromMarketArea)) {
				imports += getHourlyFlow(fromMarketArea, marketArea, year, hourOfYear);
			}
		}
		return imports;
	}

	/**
	 * Get the sum of hourly import flows to the market area and hourOfDay on
	 * current day and year. According to convention, exports get a negative (-)
	 * algebraic sign.
	 * 
	 */
	public double getHourlyFlowImportsToMarketArea(MarketArea marketArea, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		final double result = getHourlyFlowImportsByMarketArea(marketArea, Date.getYear(),
				hourOfYear);
		return result * (-1);
	}

	/**
	 * Get the yearly sum of hourly net flows for the specified interconnector
	 * between <code>marketArea1</code> and <code>marketArea2</code> in the
	 * current year
	 * 
	 * @param
	 */
	public double getYearlyFlowByInterconnector(MarketArea marketArea1, MarketArea marketArea2) {
		final int year = Date.getYear();
		return getYearlyFlowByInterconnector(marketArea1, marketArea2, year);
	}

	/**
	 * Get the yearly sum of hourly net flows for the specified interconnector
	 * between <code>marketArea1</code> and <code>marketArea2</code>
	 * 
	 * @param
	 */
	public double getYearlyFlowByInterconnector(MarketArea marketArea1, MarketArea marketArea2,
			int year) {
		double yearlyFlow = 0;

		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			yearlyFlow += getHourlyFlow(marketArea1, marketArea2, year, hourOfYear);

		}
		return yearlyFlow;
	}

	/**
	 * Get the yearly sum of hourly net flows for the specified market area in
	 * the current year
	 * 
	 * @param
	 */
	public double getYearlyFlowByMarketArea(MarketArea marketArea, int year) {
		double sumFlows = 0f;
		for (int hourOfYear = 0; hourOfYear < Date.getLastHourOfYear(); hourOfYear++) {
			sumFlows += getHourlyFlowByMarketArea(marketArea, year, hourOfYear);
		}
		return sumFlows;

	}

	private void initialize() {
		// Initialize map of exchange flows
		for (final MarketArea fromMarketArea : marketAreas) {
			exchangeFlowsHourly.put(fromMarketArea, new HashMap<>());
			for (final MarketArea toMarketArea : marketAreas) {
				if (!fromMarketArea.equals(toMarketArea)) {
					exchangeFlowsHourly.get(fromMarketArea).put(toMarketArea,
							new ConcurrentHashMap<>());
				}
			}
		}
	}

	/**
	 * Logs the exchange flows between coupled market areas for all simulated
	 * years
	 */
	public void logExchangeFlows(String fileName, Folder logFolder) {

		final String fileNameDayAheadStatistics = fileName + Settings.LOG_FILE_SUFFIX_CSV;
		final String description = "Yearly sums of exchange flows";
		final StringBuffer titleLine = new StringBuffer("Year;");
		final StringBuffer unitLine = new StringBuffer(";");

		for (final MarketArea marketArea1 : marketAreas) {
			for (final MarketArea marketArea2 : marketAreas) {
				if (!marketArea1.equals(marketArea2)) {
					titleLine.append(marketArea1.getName());
					titleLine.append('_');
					titleLine.append(marketArea2.getName());
					titleLine.append(';');
					unitLine.append("GWh;");
				}
			}
		}

		// ID for logging exchange flows between coupled market areas
		final int logIDExchangeFlows = LoggerCSV.newLogObject(logFolder, fileNameDayAheadStatistics,
				description, String.valueOf(titleLine), String.valueOf(unitLine), "");

		for (int year = Date.getStartYear(); year < Date.getLastYear(); year++) {
			final StringBuffer dataLine = new StringBuffer();

			// Year
			dataLine.append(year);
			dataLine.append(';');
			for (final MarketArea marketArea1 : marketAreas) {
				for (final MarketArea marketArea2 : marketAreas) {
					if (!marketArea1.equals(marketArea2)) {
						// Flow
						final double yearlyFlow = getYearlyFlowByInterconnector(marketArea1,
								marketArea2, year) / 1000;
						dataLine.append(String.valueOf(yearlyFlow));
						dataLine.append(';');
					}
				}
			}
			// Write data line for current year
			LoggerCSV.writeLine(logIDExchangeFlows, dataLine.toString());
		}

		LoggerCSV.close(logIDExchangeFlows);
	}

	/**
	 * Set hourly flow for the specified interconnector and hourOfDay on current
	 * day and year
	 * 
	 * @param fromMarketArea
	 * @param toMarketArea
	 * @param hourOfDay
	 * @param flow
	 */
	public void setHourlyFlow(MarketArea fromMarketArea, MarketArea toMarketArea, int hourOfDay,
			double flow) {
		if (flow < 0) {
			logger.error("Exchange flow negative (" + fromMarketArea + ">" + toMarketArea
					+ "; hour " + hourOfDay + "; " + flow + " MW)");
			return;
		}
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		final int year = Date.getYear();
		setHourlyFlow(fromMarketArea, toMarketArea, year, hourOfYear, Math.abs(flow));
	}

	private void setHourlyFlow(MarketArea fromMarketArea, MarketArea toMarketArea, int year,
			int hourOfYear, double flow) {
		final int dateKey = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
		exchangeFlowsHourly.get(fromMarketArea).get(toMarketArea).put(dateKey, flow);
	}

}