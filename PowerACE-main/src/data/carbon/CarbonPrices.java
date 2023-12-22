package data.carbon;

import static simulations.scheduling.Date.DAYS_PER_YEAR;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;

public final class CarbonPrices implements Callable<Void> {

	private static Map<Integer, Float> carbonPricesDaily;
	private static Map<Integer, Float> carbonPricesYearlyAvg;
	/**
	 * Use this year to analyze the carbon price change effects on the
	 * electricity market.
	 */
	private static float currentDayPrice = Float.NaN;
	private static int day = Integer.MIN_VALUE;
	private static Integer firstYearNeeded;
	private static Integer lastYearNeeded;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(CarbonPrices.class.getName());
	private static final int START_OF_EU_CARBON_MARKET = 2005;

	/**
	 * Return the carbon prices for specified key
	 *
	 * @param keyYearDay,
	 *            marketArea
	 * @return caronPricesDaily
	 */
	public static float getPricesDaily(int keyYearDay, MarketArea marketArea) {
		return carbonPricesDaily.get(keyYearDay);
	}

	/**
	 * Return the carbon prices for today
	 *
	 * @param marketArea
	 * @return caronPricesDaily
	 */
	public static float getPricesDaily(MarketArea marketArea) {

		if (Date.getDayOfTotal() != day) {
			day = Date.getDayOfTotal();
			currentDayPrice = carbonPricesDaily
					.get(Date.getKeyDaily(Date.getYear(), Date.getDayOfYear()));
		}
		return currentDayPrice;

	}

	/**
	 * Returns the yearly average CO2 price for specified year.
	 */
	public static float getPricesYearlyAverage(int year, MarketArea marketArea) {

		return carbonPricesYearlyAvg.get(year);
	}

	public static void initialize() {
		final String threadName = "Initialize " + CarbonPrices.class.getSimpleName();
		Thread.currentThread().setName(threadName);
		logger.info(threadName);

		carbonPricesDaily = new HashMap<>();
		carbonPricesYearlyAvg = new HashMap<>();

		final String scenario = Settings.getCarbonPriceScenario();

		// Set first/last year needed for calculations
		firstYearNeeded = Date.getStartYear();
		lastYearNeeded = Date.getLastDetailedForecastYear();

		CarbonPrices.loadDailyCO2Prices(scenario);

		CarbonPrices.writeAvgValues();
		carbonPricesDaily = Collections.unmodifiableMap(carbonPricesDaily);
		carbonPricesYearlyAvg = Collections.unmodifiableMap(carbonPricesYearlyAvg);
	}

	private static void loadDailyCO2Prices(String scenario) {
		// TODO set databasename
		try (final ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED)) {
			final String query = "SELECT * FROM `" + scenario + "` ORDER BY `dayoftheyear`";
			conn.setResultSet(query);

			for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
				conn.getResultSet().beforeFirst();
				// Setting counter
				int day = 1;
				// Reading daily CO2 prices
				while (conn.getResultSet().next()) {

					final float price = conn.getResultSet().getFloat(Integer.toString(year));
					// SQL returns 0 for null
					if (!conn.getResultSet().wasNull()) {
						carbonPricesDaily.put(Date.getKeyDaily(year, day), price);
					}
					day++;
				}
			}
		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private static void writeAvgValues() {

		for (int year = firstYearNeeded; year <= lastYearNeeded; year++) {
			float sum = 0f;
			for (int day = 1; day <= DAYS_PER_YEAR; day++) {
				sum += carbonPricesDaily.get(Date.getKeyDaily(year, day));
			}
			final float avg = sum / DAYS_PER_YEAR;
			carbonPricesYearlyAvg.put(year, avg);
		}

	}

	/**
	 * Write the missing data for each day of each year.
	 */
	private static void writeMissingValues(int firstYearAvailable, int lastYearAvailable) {

		boolean valuesMissingStart = false;

		// write missing values start
		for (int year = firstYearAvailable; year >= firstYearNeeded; year--) {

			for (int day = Date.getLastDayOfYear(year); day >= 1; day--) {
				if (!carbonPricesDaily.containsKey(Date.getKeyDaily(year, day))) {

					if (!valuesMissingStart) {
						logger.warn("Some carbon prices at beginning are missing! Starting with "
								+ year + "/" + day);
						valuesMissingStart = true;
					}

					float value;
					if (year < START_OF_EU_CARBON_MARKET) {
						value = 0;
					} else if ((year == firstYearAvailable) && !carbonPricesDaily
							.containsKey(Date.getKeyDaily(firstYearAvailable, day))) {
						// Get value of day before
						value = carbonPricesDaily
								.get(Date.getKeyDaily(firstYearAvailable, day + 1));
					} else {
						// Get value of year that is available
						value = carbonPricesDaily.get(Date.getKeyDaily(firstYearAvailable, day));
					}
					// set missing value
					carbonPricesDaily.put(Date.getKeyDaily(year, day), value);

				}
			}
		}

		boolean valuesMissingEnd = false;
		for (int year = lastYearAvailable; year <= lastYearNeeded; year++) {
			// write missing values end
			for (int day = 1; day <= Date.getLastDayOfYear(year); day++) {
				if (!carbonPricesDaily.containsKey(Date.getKeyDaily(year, day))) {

					if (!valuesMissingEnd) {
						logger.warn("Some carbon prices at end missing! Starting with " + year + "/"
								+ day);
						valuesMissingEnd = true;
					}

					float value;
					// year may not be fully available
					if ((year == lastYearAvailable) && !carbonPricesDaily
							.containsKey(Date.getKeyDaily(lastYearAvailable, day))) {
						// Get value of day before
						value = carbonPricesDaily.get(Date.getKeyDaily(lastYearAvailable, day - 1));
					} else {
						// Get value of year that is available
						value = carbonPricesDaily.get(Date.getKeyDaily(lastYearAvailable, day));
					}

					// set missing value
					carbonPricesDaily.put(Date.getKeyDaily(year, day), value);

				}
			}

		}
	}

	@Override
	public Void call() {
		CarbonPrices.initialize();
		return null;
	}

}