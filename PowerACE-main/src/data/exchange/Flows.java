package data.exchange;

import static simulations.scheduling.Date.HOURS_PER_DAY;
import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.math.Interpolation;
import tools.math.Statistics;

/**
 * Utility class that loads the "static" exchange scenerio data from the
 * database. No endogenous market coupling is considered.
 * 
 * @since 05.02.2013
 * 
 * 
 */
public final class Flows implements Callable<Void> {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Flows.class.getName());

	/** Use this year to compare price effect of demand. */
	private final String exchangeScenarioName;
	private int firstYearNeeded;
	private int lastAvailableYear;
	private final MarketArea marketArea;

	/** Markets, year, hourOfYear, Value */
	private final Map<String, Map<Integer, Map<Integer, Float>>> powerExchangeLong = new LinkedHashMap<>();
	private Set<String> tableNamesExchangeNotCoupled = new LinkedHashSet<>();

	public Flows(MarketArea marketArea) {
		this.marketArea = marketArea;
		exchangeScenarioName = marketArea.getExchangeScenario();
		marketArea.setExchange(this);
	}

	public float calculateMaxExport(int year) {
		float maxExport = 0;
		for (int hour = 0; hour < Date.HOURS_PER_YEAR; hour++) {
			final float actualExchange = getHourlyFlow(year, hour);
			if ((actualExchange > 0) && (actualExchange > maxExport)) {
				maxExport = actualExchange;
			}
		}
		return maxExport;
	}

	public float calculateYearlyAverageAbsImport(int year) {
		float averageImport = 0;
		float importSum = 0;
		int counterImport = 0;
		for (int hour = 0; hour < Date.HOURS_PER_YEAR; hour++) {
			final float actualExchange = getHourlyFlow(year, hour);
			if (actualExchange < 0) {
				importSum += actualExchange;
				counterImport++;
			}

		}
		averageImport = importSum / counterImport;
		return Math.abs(averageImport);
	}

	public float calculateYearlyAverageExport(int year) {
		float averageExport = 0;
		float ExportSum = 0;
		int counterExport = 0;
		for (int hour = 0; hour < Date.HOURS_PER_YEAR; hour++) {
			final float actualExchange = getHourlyFlow(year, hour);
			if (actualExchange > 0) {
				ExportSum += actualExchange;
				counterExport++;
			}

		}
		averageExport = ExportSum / counterExport;
		return averageExport;
	}

	public float calculateYearlyExchangeSum(int year) {
		final int startIndex = 0;
		final int endIndex = Date.getLastHourOfYear() - 1;
		float sum = 0;
		for (final String key : powerExchangeLong.keySet()) {
			sum = Statistics.calcSumMap(powerExchangeLong.get(key).get(year), startIndex, endIndex);
		}

		return sum;
	}

	@Override
	public Void call() {
		try {
			initialize();
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	public float getHourlyFlow(int hourOfYear) {
		return getHourlyFlow(Date.getYear(), hourOfYear);
	}

	public float getHourlyFlow(int year, int hourOfYear) {
		if (powerExchangeLong.isEmpty()) {
			return 0;
		}
		float value = 0;
		for (final String key : powerExchangeLong.keySet()) {
			if (powerExchangeLong.get(key).containsKey(year)) {
				if (powerExchangeLong.get(key).get(year).containsKey(hourOfYear)) {
					if (powerExchangeLong.get(key).get(year).get(hourOfYear) != null) {
						value += powerExchangeLong.get(key).get(year).get(hourOfYear);
					}
				}
			}
		}
		return value;
	}

	public float getHourlyFlowForecast(int year, int hourOfYear) {
		if (powerExchangeLong.isEmpty()) {
			return 0;
		}
		float value = 0;
		for (final String key : powerExchangeLong.keySet()) {
			value += getHourlyFlowForecastByKey(key, year, hourOfYear);
		}
		return value;
	}

	private float getHourlyFlowForecastByKey(String key, int year, int hourOfYear) {
		float value = 0;
		if (powerExchangeLong.get(key).containsKey(year)) {
			if (powerExchangeLong.get(key).get(year).containsKey(hourOfYear)) {
				if (powerExchangeLong.get(key).get(year).get(hourOfYear) != null) {
					value += powerExchangeLong.get(key).get(year).get(hourOfYear);
				}
			}
		} else {
			// Only occurs when year > last simulation year
			if (year < Date.getStartYear()) {
				logger.error("Exchange flows not available for key " + key);
				return value;
			}
			value += getHourlyFlowForecastByKey(key, year - 1, hourOfYear);
		}
		return value;
	}

	/**
	 * 
	 * @param day
	 *            from 1 to 365 (powerMarkets.dayoftheyear)
	 * @return exchange load for next day
	 */
	public float[] getHourlyFlowsOfDay(int day) {
		final float[] exchange = new float[HOURS_PER_DAY];
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			exchange[i] = getHourlyFlow(Date.getYear(), ((day - 1) * HOURS_PER_DAY) + i);
		}
		return exchange;
	}

	public float[] getHourlyFlowsOfPeriod(int day, int lengthOfForecastInDays) {
		final float[] exchange = new float[lengthOfForecastInDays];
		for (int i = 0; i < lengthOfForecastInDays; i++) {
			exchange[i] = getHourlyFlow(Date.getYear(), ((day - 1) * HOURS_PER_DAY) + i);
		}
		return exchange;
	}

	/**
	 * @return Returns the powerExchangePERSEUS.
	 */
	public List<Float> getHourlyFlowsOfYear() {
		return getPowerExchangeYearly(Date.getYear());
	}

	/**
	 * @return Returns the powerExchangePERSEUS.
	 */
	public List<Float> getHourlyFlowsOfYear(int year) {
		return getPowerExchangeYearly(year);
	}

	public void loadExchangeDataMarketCoupling(PowerMarkets model) {
		if ("noexchange".equals(exchangeScenarioName)) {
			return;
		}

		try {
			// Get all table name of a MarketArea for that exchange flows are
			// available
			loadAvailableExchangeTablesForMarketAreas(model);

		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private List<Float> getPowerExchangeYearly(int year) {
		final List<Float> exchange = new ArrayList<>();
		for (int hour = 0; hour < Date.getLastHourOfYear(year); hour++) {
			exchange.add(getHourlyFlow(hour));
		}
		return exchange;
	}

	/**
	 * Returns the next available year in the map. True searches in positive
	 * direction, false in negative.
	 */
	private int getYearAvailable(String scenario, int startYear, boolean add) {
		if (powerExchangeLong.get(scenario).containsKey(startYear)) {
			return startYear;
		}
		if (add) {
			return getYearAvailable(scenario, startYear + 1, true);
		}
		return getYearAvailable(scenario, startYear - 1, false);
	}

	private void initialize() throws SQLException {
		firstYearNeeded = Date.getStartYear();

		// No exchange
		if ("noexchange".equals(exchangeScenarioName)) {
			logger.info(
					"No structure for exchange data provided. Exchange set to zero in all hours.");
			setNoExchange();
		}
		// If market coupling, exchange data is loaded after all market areas
		// have been initialized and information about endogenously modeled
		// interconnections are available
		else if (marketArea.isMarketCoupling()) {
			logger.info(
					"Market area is coupled with other area(s). Exchange is read later in order to adjust exogenous exchange data accordingly.");
		} else {

			// Read data
			loadHourly(exchangeScenarioName, lastAvailableYear);

			// Process data
			writeMissingValues();
		}
	}

	private Map<Integer, Float> interpolateHourlyProfile(String scenario, int year) {
		final Map<Integer, Float> values = new LinkedHashMap<>();
		try {

			final int firstYearAvailScenario = Collections
					.min(powerExchangeLong.get(scenario).keySet());
			final int lastYearAvailScenario = Collections
					.max(powerExchangeLong.get(scenario).keySet());

			// Use first available data or just one value is available
			if ((year < firstYearAvailScenario)
					|| (firstYearAvailScenario == lastYearAvailScenario)) {
				return powerExchangeLong.get(scenario).get(firstYearAvailScenario);
			}
			if (year > lastYearAvailScenario) {
				return powerExchangeLong.get(scenario).get(lastYearAvailScenario);
			}
			// Linear Interpolate the hours
			final int yearSecondPoint = getYearAvailable(scenario, year, true);
			final int yearFirstPoint = getYearAvailable(scenario, yearSecondPoint - 1, false);

			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				final float valueSecondPoint = powerExchangeLong.get(scenario).get(yearSecondPoint)
						.get(hourOfYear);

				// Point(x1,y1)
				final float valueFirstPoint = powerExchangeLong.get(scenario).get(yearFirstPoint)
						.get(hourOfYear);
				values.put(hourOfYear, Interpolation.linear(yearFirstPoint, yearSecondPoint,
						valueFirstPoint, valueSecondPoint, year));
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return values;
	}

	private void interpolateProfileData(String scenario) {
		// Interpolate profile
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			if (powerExchangeLong.get(scenario).containsKey(year)) {
				continue;
			}
			powerExchangeLong.get(scenario).put(year, interpolateHourlyProfile(scenario, year));
		}

	}

	private void loadAvailableExchangeTablesForMarketAreas(PowerMarkets model) throws SQLException {
		final String patternSQLExchangeMarketArea = Settings.getStaticExchange()
				+ marketArea.getInitials() + "_from_";
		final String query = "SHOW TABLES like '" + patternSQLExchangeMarketArea + "%';";
		// For Italy there are long substrings like IT_NORTH, there use just the
		// first two characters
		final Set<String> marketAreasSubstring = model.getMarketAreasMappedInitials().keySet()
				.parallelStream().map(marketAreaInitial -> marketAreaInitial.substring(0, 2))
				.collect(Collectors.toSet());
		try (ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED, marketArea)) {
			conn.setResultSet(query);

			while (conn.getResultSet().next()) {
				final String tableNameFull = conn.getResultSet().getString(1);
				final String coupledMarket = tableNameFull.replace(patternSQLExchangeMarketArea, "")
						.substring(0, 2);

				if (!marketAreasSubstring.contains(coupledMarket)) {
					tableNamesExchangeNotCoupled.add(tableNameFull);
				}
			}

		}

	}

	/**
	 * Load hourly structured exchange data
	 * 
	 * @throws SQLException
	 */
	private void loadHourly(String scenario, int lastYear) throws SQLException {
		if (lastYear < Date.getStartYear()) {
			lastYear = Date.getStartYear();
		}
		for (int year = firstYearNeeded; year <= Date.getLastDetailedForecastYear(); year++) {
			if (year <= lastYear) {
				final String sqlQuery = "SELECT `" + year + "` FROM `" + scenario
						+ "` WHERE `area` LIKE '" + marketArea.getInitials()
						+ "' ORDER BY `hourOfTheYear`";
				// TODO set Database Name
				try (ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED)) {

					conn.setResultSet(sqlQuery);

					int hour = 0;
					while (conn.getResultSet().next()) {
						if (!powerExchangeLong.containsKey(marketArea.getInitials())) {
							powerExchangeLong.put(marketArea.getInitials(), new LinkedHashMap<>());
						}
						if (!powerExchangeLong.get(marketArea.getInitials()).containsKey(year)) {
							powerExchangeLong.get(marketArea.getInitials()).put(year,
									new LinkedHashMap<>());
						}
						powerExchangeLong.get(marketArea.getInitials()).get(year).put(hour,
								conn.getResultSet().getFloat(String.valueOf(year)));
						hour++;

					}
				}
			} else {
				for (int hour = 0; hour < Date.HOURS_PER_YEAR; hour++) {
					if (!powerExchangeLong.containsKey(marketArea.getInitials())) {
						powerExchangeLong.put(marketArea.getInitials(), new LinkedHashMap<>());
					}
					if (!powerExchangeLong.get(marketArea.getInitials()).containsKey(year)) {
						powerExchangeLong.get(marketArea.getInitials()).put(year,
								new LinkedHashMap<>());
					}
					powerExchangeLong.get(marketArea.getInitials()).get(year).put(hour,
							powerExchangeLong.get(marketArea.getInitials()).get(year).get(hour));
				}
			}
		}
	}

	/** Set exchange in all hours equal to zero */
	private void setNoExchange() {
		if (powerExchangeLong.isEmpty()) {
			powerExchangeLong.put(marketArea.getInitials(), new LinkedHashMap<>());
		}
		for (final String key : powerExchangeLong.keySet()) {
			for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
				if (!powerExchangeLong.get(key).containsKey(year)) {
					powerExchangeLong.get(key).put(year, new LinkedHashMap<>());
				}
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

					powerExchangeLong.get(key).get(year).put(hourOfYear, 0f);
				}
			}
		}
	}

	/** Write missing data for each year */
	private void writeMissingValues() {
		for (int year = firstYearNeeded; year <= Date.getLastYear(); year++) {
			for (int hourOfYear = 0; hourOfYear < HOURS_PER_YEAR; hourOfYear++) {
				float value;
				if (lastAvailableYear == 0) {
					return;
				}
				for (final String key : powerExchangeLong.keySet()) {
					// Values after last available year are missing
					if (year > lastAvailableYear) {
						// Set respective value from reference year
						if (!powerExchangeLong.get(key).containsKey(year)) {
							powerExchangeLong.get(key).put(year, new LinkedHashMap<>());
						}
						if (powerExchangeLong.get(key)
								.get(marketArea.getExchangeFlowReferenceYear())
								.containsKey(hourOfYear)) {
							value = powerExchangeLong.get(key)
									.get(marketArea.getExchangeFlowReferenceYear()).get(hourOfYear);
							powerExchangeLong.get(key).get(year).put(hourOfYear, value);
						} else {
							powerExchangeLong.get(key).get(year).put(hourOfYear, 0f);
						}

					}

					// Values within available years are missing (e.g.
					// because year is not yet finished)
					else if (!powerExchangeLong.get(key).get(year).containsKey(hourOfYear)) {
						if (powerExchangeLong.get(key).get(year).isEmpty()) {
							return;
						}
						// Set respective from year preceding last available
						// year

						value = powerExchangeLong.get(key).get(lastAvailableYear - 1)
								.get(hourOfYear);
						powerExchangeLong.get(key).get(year).put(hourOfYear, value);
					}
				}
			}

		}
	}
}