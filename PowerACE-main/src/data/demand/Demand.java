package data.demand;

import static simulations.scheduling.Date.HOURS_PER_DAY;
import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.scheduling.Date;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.math.Interpolation;
import tools.math.Statistics;

/**
 * Reads the demand data from the SQL database. If the DemandSupplierBidder is
 * active the demand is not calculated from several demand agents e.g. industry,
 * consumer, as it is done in SupplierBidder, but directly taken from the
 * database.
 * 
 * 
 * 
 */
public final class Demand implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Demand.class.getName());

	/** Current database */
	private NameDatabase databaseName;

	/**
	 * Contains the total electricity demand excluding grid losses in the form
	 * <code>{dateKey[year,hourOfYear],{hourlyDemand}}</code>
	 */
	private Map<Integer, Float> demandData = new TreeMap<>();
	private Map<Integer, Float> demandMax = new HashMap<>();
	private Map<Integer, Float> demandMin = new HashMap<>();
	private int firstYearAvail;
	private int firstYearNeeded;
	private Map<Integer, Map<Integer, Float>> hourlyProfile = new HashMap<>();
	private int lastYearAvail;
	private int lastYearNeeded;
	private final MarketArea marketArea;
	/** Name of the Scenario Table that contains all TotalDemand scenarios */

	public Demand(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setDemand(this);
	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	/**
	 * Returns the daily electricity demand for the current day including grid
	 * losses. If the requested year is <b>not</b> available, last available
	 * year will be used instead.
	 * 
	 * @param day
	 *            [1,365]
	 * 
	 * @return The hourly demand in MWh for the requested day.
	 * 
	 */
	public float[] getDailyDemand() {
		return getRange(Date.getDayOfYear(), HOURS_PER_DAY);
	}

	/**
	 * Returns the maximum demand in the specified year
	 * 
	 * @param year
	 *            year of simulation
	 */
	public float getDemandMax(int year) {
		if (!demandMax.containsKey(year)) {
			logger.error("Maximal demand of the year " + year + " not available.");
		}
		return demandMax.get(year);
	}

	/**
	 * Returns the electricity demand summed up for the specified year.
	 * 
	 * @param year
	 * @return yearlyDemand
	 */
	public float getDemandYearlySum(int year) {

		final int startIndex = Date.getKeyHourlyWithHourOfYear(year, 0);
		final int endIndex = Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear() - 1);

		final List<Float> yearlyDemand = new ArrayList<>();
		for (int index = startIndex; index <= endIndex; index++) {
			yearlyDemand.add(demandData.get(index));
		}

		return Statistics.calcSum(yearlyDemand);
	}

	public int getFirstYearDemand() {
		return firstYearAvail;
	}

	/**
	 * Returns the daily electricity demand for the current day. If the
	 * requested year is <b>not</b> available, last available year will be used
	 * instead.
	 * 
	 * @param hourOfDay
	 *            [0, HOURS_PER_DAY]
	 * @return The hourly demand in MWh for the current day
	 * 
	 */
	public float getHourlyDemand(int hourOfDay) {
		return getRange(Date.getDayOfYear(), hourOfDay, hourOfDay)[0];
	}

	/**
	 * Returns the electricity demand for the specified <code>year</code> in
	 * <code>hourOfYear</code>
	 * 
	 * @param year
	 * @param hourOfYear
	 * @return The hourly demand in MWh
	 * 
	 */
	public float getHourlyDemand(int year, int hourOfYear) {
		return demandData.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * Returns the daily electricity demand for the hour including grid losses.
	 * If the requested year is <b>not</b> available, last available year will
	 * be used instead.
	 * 
	 * @param hour
	 *            [0, HOURS_PER_YEAR]
	 * @return The hourly demand in MWh for the current day
	 */
	public float getHourlyDemandOfYear(int hour) {
		final int dayStart = 1 + (hour / HOURS_PER_DAY);
		final int dayHour = hour % HOURS_PER_DAY;

		return getRange(dayStart, dayHour, dayHour)[0];
	}

	public int getLastYearDemand() {
		return lastYearAvail;
	}

	/**
	 * Returns the daily electricity demand for the range, starting from
	 * <code>day</code> at hour 0 and finishing at range. If the requested data
	 * is <b>not</b> available, the data from the last available year will be
	 * used instead.
	 * 
	 * @param day
	 *            [1,365]
	 * @param range
	 *            length in hours
	 * @return The hourly demand in MWh for the requested range.
	 * 
	 */
	public List<Float> getRangeList(int day, int range) {
		return getRangeList(day, 0, range - 1);
	}

	/**
	 * Returns the electricity demand for the current year including grid
	 * losses. If the requested year is <b>not</b> available, last available
	 * year will be used instead.
	 * 
	 */
	public float[] getYearlyDemand() {
		return getYearlyDemand(Date.getYear());
	}

	/**
	 * Returns the electricity demand for the corresponding year. If the
	 * requested year is <b>not</b> available, last available year will be used
	 * instead.
	 * 
	 * @param year
	 * @return yearly demand
	 */
	public float[] getYearlyDemand(int year) {

		final float[] demand = new float[Date
				.getLastHourOfYear(Math.min(year, Date.getLastYear()))];

		for (int hourOfYear = 0; hourOfYear < demand.length; hourOfYear++) {
			try {
				final Float value = demandData
						.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
				demand[hourOfYear] = value == null ? Float.NaN : value;
			} catch (final IndexOutOfBoundsException e) {
				logger.error(e.getMessage(), e);
				return demand;
			}
		}

		return demand;
	}

	/**
	 * Returns the electricity demand for the corresponding yearIf the requested
	 * year is <b>not</b> available, last available year will be used instead.
	 * 
	 * @param year
	 *            in 4-digits form, e.g., 2010
	 */
	public List<Float> getYearlyDemandList(int year) {
		final List<Float> values = new ArrayList<>();
		year = Math.min(year, Date.getLastYear());

		for (int hourOfDay = 0; hourOfDay < Date.getLastHourOfYear(year); hourOfDay++) {
			values.add(demandData.get(Date.getKeyHourlyWithHourOfYear(year, hourOfDay)));
		}

		return values;
	}

	/**
	 * Returns the electricity demand for the corresponding year including grid
	 * losses. If the requested year is <b>not</b> available, last available
	 * year will be used instead.
	 * 
	 * @param year
	 * @return map of hourly demand
	 */
	public Map<Integer, Float> getYearlyDemandMap(int year) {
		final Map<Integer, Float> values = new HashMap<>();
		year = Math.min(year, Date.getLastYear());

		for (int hourOfYear = 0; hourOfYear < Date.getLastHourOfYear(year); hourOfYear++) {
			values.put(hourOfYear,
					demandData.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear)));
		}

		return values;
	}

	private void calculateDemand() {
		try {

			for (int year = firstYearAvail; year <= lastYearAvail; year++) {
				if (!hourlyProfile.containsKey(year)) {
					logger.debug("year: " + year + " in demand Scenario not available!");
					continue;
				}
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

					final int dateKey = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);

					final float demand = hourlyProfile.get(year).get(hourOfYear);

					if (!demandData.containsKey(dateKey)) {
						demandData.put(dateKey, demand);
					}

				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/** Finds the maximal and minimal yearly demand */
	private void findExtrema() {

		for (int year = firstYearNeeded; year <= lastYearNeeded; year++) {

			float maximalYearlyDemand = Float.NEGATIVE_INFINITY;
			float minimalYearlyDemand = Float.POSITIVE_INFINITY;

			for (int hour = 0; hour < HOURS_PER_YEAR; hour++) {
				final int dateKey = Date.getKeyHourlyWithHourOfYear(year, hour);

				final Float value = demandData.get(dateKey);
				// Check if value for current exists after all
				if (value == null) {
					continue;
				}

				if (value > maximalYearlyDemand) {
					maximalYearlyDemand = value;
				}

				if (value < minimalYearlyDemand) {
					minimalYearlyDemand = value;
				}
			}

			demandMax.put(year, maximalYearlyDemand);
			demandMin.put(year, minimalYearlyDemand);
		}
	}

	/**
	 * Returns the daily electricity demand for the range, starting from
	 * <code>day</code> at hour 0 and finishing at range. If the requested data
	 * is <b>not</b> available, the data from the last available year will be
	 * used instead.
	 * 
	 * @param day
	 *            [1,365]
	 * @param range
	 *            length in hours
	 * @return The hourly demand in MWh for the requested range.
	 * 
	 */
	private float[] getRange(int day, int range) {
		return getRange(day, 0, range - 1);
	}

	/**
	 * Returns the daily electricity demand for the range, starting from
	 * <code>day</code> at hour 0 and finishing at range. If the requested data
	 * is <b>not</b> available, the data from the last available year will be
	 * used instead.
	 * 
	 * @param day
	 *            [1,365]
	 * @param startHour
	 * 
	 * @param endHour
	 * 
	 * @return The hourly demand in MWh for the requested range [startHour,
	 *         endHour].
	 * 
	 */
	private float[] getRange(int day, int startHour, int endHour) {

		final float[] dailyDemand = new float[(endHour - startHour) + 1];

		// Hours in year and counting for next year
		final int start = ((day - 1) * HOURS_PER_DAY) + startHour;
		final int end = ((day - 1) * HOURS_PER_DAY) + endHour;

		// If data from next year is required different limits are required
		final int lastHourCurrentYear;
		if (end > HOURS_PER_YEAR) {
			lastHourCurrentYear = HOURS_PER_YEAR - 1;
		} else {
			lastHourCurrentYear = end;
		}

		// Write values for current year
		final int year = Date.getYear();

		for (int hour = 0; (start + hour) <= lastHourCurrentYear; hour++) {
			dailyDemand[hour] = demandData.get(Date.getKeyHourlyWithHourOfYear(year, start + hour));
		}

		// Write values for next year
		if (end > HOURS_PER_YEAR) {
			for (int hour = 0; hour <= (end - HOURS_PER_YEAR); hour++) {
				dailyDemand[(hour + HOURS_PER_YEAR) - start] = demandData
						.get(Date.getKeyHourlyWithHourOfYear(year + 1, hour));
			}
		}

		return dailyDemand;
	}

	/**
	 * Returns the daily electricity demand for the range, starting from
	 * <code>day</code> at hour 0 and finishing at range. If the requested data
	 * is <b>not</b> available, the data from the last available year will be
	 * used instead.
	 * 
	 * @param day
	 *            [1,365]
	 * @param startHour
	 * 
	 * @param endHour
	 * 
	 * @return The hourly demand in MWh for the requested range [startHour,
	 *         endHour].
	 * 
	 */
	private List<Float> getRangeList(int day, int startHour, int endHour) {

		final List<Float> dailyDemand = new ArrayList<>((endHour - startHour) + 1);

		// Hours in year and counting for next year
		final int start = ((day - 1) * HOURS_PER_DAY) + startHour;
		final int end = ((day - 1) * HOURS_PER_DAY) + endHour;

		// If data from next year is required different limits are required
		final int lastHourCurrentYear;
		if (end > HOURS_PER_YEAR) {
			lastHourCurrentYear = HOURS_PER_YEAR - 1;
		} else {
			lastHourCurrentYear = end;
		}

		// Write values for current year
		final int year = Date.getYear();
		for (int hour = 0; (start + hour) <= lastHourCurrentYear; hour++) {
			dailyDemand.add(demandData.get(Date.getKeyHourlyWithHourOfYear(year, start + hour)));
		}

		// Write values for next year
		if (end > HOURS_PER_YEAR) {
			for (int hour = 0; hour <= (end - HOURS_PER_YEAR); hour++) {
				dailyDemand.add(
						demandData.get(Date.getKeyHourlyWithHourOfYear(year + 1, start + hour)));
			}
		}

		return dailyDemand;
	}

	/**
	 * Returns the next available year in the map. True searches in positive
	 * direction, false in negative.
	 */
	private int getYearAvailable(int startYear, boolean add) {
		if (hourlyProfile.containsKey(startYear)) {
			return startYear;
		}
		if (add) {
			return getYearAvailable(startYear + 1, true);
		}
		return getYearAvailable(startYear - 1, false);
	}

	private void initialize() {
		try {
			logger.info(marketArea.getInitialsBrackets() + "Initialize "
					+ Demand.class.getSimpleName());
			// TODO set database name
			databaseName = NameDatabase.NAME_OF_DATABASED;

			// Calculate first year
			firstYearNeeded = Date.getStartYear();
			// Set needed and available years
			lastYearNeeded = Date.getLastDetailedForecastYear();

			readProfileData();
			interpolateProfileData();
			calculateDemand();

			// Process data
			writeMissingValues();
			findExtrema();

			// Make sure no changes are made!
			demandData = Collections.unmodifiableMap(demandData);
			demandData = Collections.unmodifiableMap(demandData);
			demandMax = Collections.unmodifiableMap(demandMax);
			demandMin = Collections.unmodifiableMap(demandMin);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private Map<Integer, Float> interpolateHourlyProfile(int year) {
		firstYearAvail = Collections.min(hourlyProfile.keySet());
		lastYearAvail = Collections.max(hourlyProfile.keySet());
		// Use first available data or just one value is available
		if ((year < firstYearAvail) || (firstYearAvail == lastYearAvail)) {
			return hourlyProfile.get(firstYearAvail);
		}
		if (year > lastYearAvail) {
			return hourlyProfile.get(lastYearAvail);
		}
		// Linear Interpolate the hours
		final int yearSecondPoint = getYearAvailable(year, true);
		final int yearFirstPoint = getYearAvailable(yearSecondPoint - 1, false);
		final Map<Integer, Float> valueMap = new HashMap<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			final float capacitySecondPoint = hourlyProfile.get(yearSecondPoint).get(hourOfYear);

			// Point(x1,y1)
			final float capacityFirstPoint = hourlyProfile.get(yearFirstPoint).get(hourOfYear);
			valueMap.put(hourOfYear, Interpolation.linear(yearFirstPoint, yearSecondPoint,
					capacityFirstPoint, capacitySecondPoint, year));
		}
		return valueMap;
	}

	private void interpolateProfileData() {

		for (int year = Date.getStartYear(); year <= Date.getLastDetailedForecastYear(); year++) {

			if (hourlyProfile.containsKey(year)) {
				continue;
			}
			hourlyProfile.put(year, interpolateHourlyProfile(year));
		}
	}

	/**
	 * 
	 * @throws SQLException
	 */
	private void readProfileData() throws SQLException {
		final String columnHourOfYear = "hour_of_year";
		final String tableName = marketArea.getTotalDemandScenario();
		final String sqlQuery = "SELECT FROM `" + tableName + "` AND `area` LIKE '"
				+ marketArea.getInitials() + "' ORDER BY `" + columnHourOfYear + "`;";
		try (ConnectionSQL conn = new ConnectionSQL(databaseName)) {
			conn.setResultSet(sqlQuery);

			// Check number of available profile years by counting columns
			// of result set (first column of table is for the hour of year)
			final int numberOfColumns = conn.getResultSetMetaData().getColumnCount();
			// First Column is area, second hourOfYear, so start with third
			// column
			for (int columnIndex = 3; columnIndex <= numberOfColumns; columnIndex++) {
				final Integer profileYear = Integer
						.parseInt(conn.getResultSetMetaData().getColumnName(columnIndex));
				if (!hourlyProfile.containsKey(profileYear)) {
					hourlyProfile.put(profileYear, new HashMap<>());
				}
				// Read values from database
				while (conn.getResultSet().next()) {
					final int hourOfYear = conn.getResultSet().getInt(columnHourOfYear);
					final float value = conn.getResultSet().getFloat(profileYear.toString());
					hourlyProfile.get(profileYear).put(hourOfYear, value);
				}
				// Reset cursor
				conn.getResultSet().beforeFirst();
			}
		}
	}

	private void writeMissingValues() {

		boolean valuesMissingStart = false;
		// extrapolating: write missing values start by taking the values from
		// the first year that is available
		for (int year = firstYearAvail - 1; year >= firstYearNeeded; year--) {

			if (!valuesMissingStart) {
				logger.debug("Some demand data at beginning are missing! Starting with " + year);
				valuesMissingStart = true;
			}

			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

				final int keyAvailable = Date.getKeyHourlyWithHourOfYear(firstYearAvail,
						hourOfYear);
				final int keyNeeded = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);

				demandData.put(keyNeeded, demandData.get(keyAvailable));
				demandData.put(keyNeeded, demandData.get(keyAvailable));
			}
		}

		boolean valuesMissingEnd = false;
		// extrapolating: write missing values start by taking the values from
		// the last year that is available
		for (int year = lastYearAvail; year <= lastYearNeeded; year++) {

			if (!valuesMissingEnd) {
				logger.debug("Some demand data at end are missing! Starting with " + year);
				valuesMissingEnd = true;
			}

			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				final int keyAvailable = Date.getKeyHourlyWithHourOfYear(lastYearAvail, hourOfYear);
				final int keyNeeded = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);

				demandData.put(keyNeeded, demandData.get(keyAvailable));
				demandData.put(keyNeeded, demandData.get(keyAvailable));
			}
		}
	}
}