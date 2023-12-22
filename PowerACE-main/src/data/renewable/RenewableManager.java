package data.renewable;

import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.math.Interpolation;
import tools.math.Statistics;
import tools.types.FuelName;

/**
 * Reads renewable data (capacity, full load hours, profiles) from the database.
 * 
 * , Frank Sensfuss
 * @since 29.03.2005
 */

public final class RenewableManager implements Callable<Void> {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(RenewableManager.class.getName());

	/** Last year for which detailed long-term price forecast is made */
	private int endYear;
	private int firstAvailableYear = Integer.MAX_VALUE;

	/** Current market area */
	private final MarketArea marketArea;
	// TODO set Database name
	private final NameDatabase nameDatabase = NameDatabase.NAME_OF_DATABASED;
	private Map<Integer, Map<Integer, Float>> remainingSystemLoad;
	/**
	 * Renewable capacity in MW for each technology and year.
	 */
	private final Map<FuelName, Map<Integer, Float>> renewableCapacity = new HashMap<>();

	/**
	 * The hourly load for each hour of the year [MWh] based on the load
	 * profiles (not actual feed-in!)
	 */
	private Map<FuelName, Map<Integer, Map<Integer, Float>>> renewableLoad = new HashMap<>();
	private final Map<Integer, Float> renewableLoadHourlyTotal = new TreeMap<>();
	/**
	 * The generation profile for each hour of the year normalized by the total
	 * energy produced [MWh/MWh]. If only one yearly profile is available the
	 * year (key in second map) is set to zero.
	 */
	private final Map<FuelName, Map<Integer, List<Float>>> renewableLoadProfile = new HashMap<>();
	/**
	 * List of relevant renewable types in current market area. Only these types
	 * are considered when reading input data.
	 */
	private final Set<FuelName> renewableTypes = new TreeSet<>();
	/** Start year of simulation (set via Settings class in xml) */
	private int startYear;
	/**
	 * Used adjust the utilization of plants to weather conditions (natural
	 * energy supply)
	 */
	private final Map<FuelName, Map<Integer, Float>> utilisationfactor = new HashMap<>();

	/** Yearly full load hours for each technology */
	private final Map<FuelName, Map<Integer, Float>> yearlyFullLoadHours = new HashMap<>();

	public RenewableManager(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setManagerRenewables(this);
	}

	private void calculateDataFromHourlyValues() throws Exception, SQLException {

		// Read data
		readCapacityData();
		interpolateCapacityData();
		readProfileDataOneTable(marketArea.getRenewableScenario());
		// Adjust start year by first available year
		startYear = Math.max(startYear, firstAvailableYear);

		interpolateProfileData();
		calculateRenewableLoadHourlyTotalNew();
	}

	/** Calculates the load to be covered by non-res plants */
	public void calculateRemainingSystemLoad() {
		remainingSystemLoad = new HashMap<>();
		for (int year = Date.getStartYear(); year < Date.getLastDetailedForecastYear(); year++) {
			remainingSystemLoad.put(year, new HashMap<>());

			for (int hour = 0; hour < Date.HOURS_PER_YEAR; hour++) {
				final float totalRenewable = getTotalRenewableLoad(year, hour);
				final float totalDemand = marketArea.getDemandData().getHourlyDemand(year, hour);

				remainingSystemLoad.get(year).put(hour, totalDemand - totalRenewable);
			}
		}
	}

	/**
	 * Calculate renewable load for each relevant type and hour of simulation
	 */
	public void calculateRenewableLoad() {
		for (final FuelName type : renewableTypes) {
			for (int year = startYear; year <= endYear; year++) {
				if (renewableLoad.get(type).containsKey(year)) {
					continue;
				}
				renewableLoad.get(type).put(year, new HashMap<>());
				for (int hourOfYear = 0; hourOfYear < HOURS_PER_YEAR; hourOfYear++) {
					renewableLoad.get(type).get(year).put(hourOfYear,
							getRenewableLoadUtilisationBased(type, year, hourOfYear));
				}
			}
		}
	}

	/** Calculate total renewable load for each hour of simulation */
	public void calculateRenewableLoadHourlyTotalNew() throws Exception {
		for (int year = startYear; year <= endYear; year++) {

			for (int hour = 0; hour < HOURS_PER_YEAR; hour++) {
				float value = 0f;
				for (final FuelName type : renewableTypes) {
					if ((renewableLoad.get(type).get(year).get(hour) == Float.NaN)
							|| (renewableLoad.get(type).get(year).get(hour) == null)) {
						logger.error("Not a number. Problems while reading renewable values");
					}
					value += renewableLoad.get(type).get(year).get(hour);
				}
				renewableLoadHourlyTotal.put(Date.getKeyHourlyWithHourOfYear(year, hour), value);
			}
		}
	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	private boolean checkSize(Map<Integer, Float> values) {
		if (values.entrySet().size() == Date.HOURS_PER_YEAR) {
			return true;
		}
		return false;
	}

	public Map<Integer, Float> getRemainingLoad(int year) {
		return remainingSystemLoad.get(year);
	}

	public Float getRemainingLoadMax(int year) {
		return Collections.max(remainingSystemLoad.get(year).values());
	}

	public Map<Integer, Float> getRemainingSystemLoad() {
		return getRemainingLoad(Date.getYear());
	}

	public float getRemainingSystemLoadOfDay(int hourOfDay) {
		return getRemainingLoad(Date.getYear()).get(Date.getFirstHourOfToday() + hourOfDay);
	}

	/**
	 * Get renewable capacity
	 * 
	 */
	public Map<FuelName, Map<Integer, Float>> getRenewableCapacity() {
		return renewableCapacity;
	}

	/**
	 * Get renewable capacity
	 * 
	 * @param renewableType
	 *            Type for which capacity is required
	 * @param year
	 *            Year (<b>not</b> year index)
	 */
	public float getRenewableCapacity(FuelName renewableType, int year) {
		if (!renewableCapacity.containsKey(renewableType)) {
			return 0;
		}
		if (renewableCapacity.get(renewableType).containsKey(year)) {
			return renewableCapacity.get(renewableType).get(year);
		} else {
			return getRenewableCapacity(renewableType, year - 1);
		}
	}

	/**
	 * @param type
	 * @param hourOfYear
	 *            [0,HOURS_PER_YEAR)
	 * @return
	 */
	public float getRenewableLoad(FuelName type, int hourOfYear) {
		return getRenewableLoad(type, Date.getYear(), hourOfYear);
	}

	public float getRenewableLoad(FuelName type, int year, int hourOfYear) {
		if (!renewableLoad.containsKey(type)) {
			return 0f;
		}
		if (!renewableLoad.get(type).containsKey(year)) {
			return 0f;
		}
		return renewableLoad.get(type).get(year).get(hourOfYear);

	}

	/**
	 * Get hourly load profile of specified renewables types and year
	 * 
	 * @param types
	 * @param year
	 * @return
	 */
	public List<Float> getRenewableLoad(List<FuelName> types, int year) {
		final List<Float> loadProfile = new ArrayList<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			float value = 0f;
			for (final FuelName type : types) {
				value += renewableLoad.get(type).get(year).get(hourOfYear);
			}
			loadProfile.add(value);
		}
		return loadProfile;
	}

	public float getRenewableLoadHourlyTotal(int year, int hourOfYear) {
		return renewableLoadHourlyTotal.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	public Map<Integer, Float> getRenewableLoadHourlyTotalMap(int year) {

		if (year > endYear) {
			year = endYear;
		}

		final Map<Integer, Float> values = new HashMap<>();
		try {
			for (int hourOfYear = 0; hourOfYear < HOURS_PER_YEAR; hourOfYear++) {
				values.put(hourOfYear, getRenewableLoadHourlyTotal(year, hourOfYear));
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return values;
	}

	public Map<Integer, Float> getRenewableLoadOfYear(FuelName type, int year) {
		return renewableLoad.get(type).get(year);
	}

	/**
	 * Return normalized generation for specified renewable type and date
	 * 
	 * @param type
	 * @param year
	 *            If profile year is not available, the corresponding value from
	 *            the reference year is taken.
	 * @param hourOfYear
	 *            [1..8760]
	 * @return Normalized generation [MWh/MWh]
	 */
	private Float getRenewableLoadProfile(FuelName type, int year, int hourOfYear) {

		float value;

		if (renewableLoadProfile.get(type).containsKey(year)) {
			value = renewableLoadProfile.get(type).get(year).get(hourOfYear);
		} else {
			// Take last available if reference does not exist
			if (renewableLoadProfile.get(type).get(Date.getReferenceYear()) == null) {
				final TreeSet<Integer> years = new TreeSet<>(
						renewableLoadProfile.get(type).keySet());
				value = renewableLoadProfile.get(type).get(years.last()).get(hourOfYear);
			} else {
				value = renewableLoadProfile.get(type).get(Date.getReferenceYear()).get(hourOfYear);
			}
		}

		return value;
	}

	public float getRenewableLoadProfile(List<FuelName> type, int hour) {
		return getTotalRenewableLoad(type, Date.getYear(), hour);
	}

	/** Calculate renewable load for specified type and hour */
	private float getRenewableLoadUtilisationBased(FuelName type, int year, int hourOfYear) {
		float value = 0;
		try {
			// Checks if data is available for year, if not print warning and
			// use last available year
			int lastRenewableYear = Collections.max(renewableCapacity.get(type).keySet());
			if (year > lastRenewableYear) {

				year = lastRenewableYear;
			}
			value = renewableCapacity.get(type).get(year) * getYearlyFullLoadHours(type, year)
					* utilisationfactor.get(type).get(year)
					* getRenewableLoadProfile(type, year, hourOfYear);

		} catch (final Exception e) {
			logger.error(year + ", " + hourOfYear + ", " + type, e);
		}
		return value;
	}

	/**
	 * Get yearly sum of renewable type for specified year
	 * 
	 * @param type
	 * @return
	 */
	public float getRenewableLoadYearlySum(FuelName type, int year) {
		if (!renewableLoad.containsKey(type)) {
			return 0f;
		}
		return Statistics.calcSum(renewableLoad.get(type).get(year).values());
	}

	/** Get relevant renewables types for current market area */
	public Set<FuelName> getRenewableTypes() {
		return renewableTypes;
	}

	public float getTotalRenewableLoad(int hourOfYear) {
		return getTotalRenewableLoad(Date.getYear(), hourOfYear);
	}

	public float getTotalRenewableLoad(int year, int hourOfYear) {
		float value = 0f;
		for (final FuelName type : renewableTypes) {
			value += renewableLoad.get(type).get(year).get(hourOfYear);
		}
		return value;
	}

	private float getTotalRenewableLoad(List<FuelName> types, int year, int hour) {
		float value = 0f;
		for (final FuelName type : types) {
			value += getRenewableLoad(type, year, hour);
		}

		return value;
	}

	/**
	 * Returns the next available year in the map. True searches in positive
	 * direction, false in negative.
	 */
	private int getYearAvailable(FuelName type, int startYear, boolean add) {
		if (renewableLoad.get(type).containsKey(startYear)) {
			return startYear;
		}
		if (add) {
			return getYearAvailable(type, startYear + 1, true);
		}
		return getYearAvailable(type, startYear - 1, false);
	}

	/**
	 * Returns the next available year in the map. True searches in positive
	 * direction, false in negative.
	 */
	private int getYearAvailableCapacity(FuelName type, int startYear, boolean add) {
		if (renewableCapacity.get(type).containsKey(startYear)) {
			return startYear;
		}
		if (add) {
			return getYearAvailableCapacity(type, startYear + 1, true);
		}
		return getYearAvailableCapacity(type, startYear - 1, false);
	}

	/** Get full load hours for specified type and year */
	public float getYearlyFullLoadHours(FuelName type, int year) {
		if (yearlyFullLoadHours.get(type).containsKey(year)) {
			return yearlyFullLoadHours.get(type).get(year);
		}
		final TreeSet<Integer> years = new TreeSet<>(yearlyFullLoadHours.get(type).keySet());

		return yearlyFullLoadHours.get(type).get(years.last());

	}

	/**
	 * Reads renewables data from database and calculates values for each hour
	 * of simulation
	 */

	private void initialize() {
		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ RenewableManager.class.getSimpleName());

		try {
			startYear = Date.getStartYear();
			endYear = Date.getLastDetailedForecastYear();

			calculateDataFromHourlyValues();

		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private Float interpolateCapacity(FuelName type, int year) {

		final int firstYearAvailScenario = Collections.min(renewableCapacity.get(type).keySet());
		final int lastYearAvailScenario = Collections.max(renewableCapacity.get(type).keySet());
		// Use first available data or just one value is available
		if ((year < firstYearAvailScenario) || (firstYearAvailScenario == lastYearAvailScenario)) {
			return renewableCapacity.get(type).get(firstYearAvailScenario);
		}
		if (year > lastYearAvailScenario) {
			return renewableCapacity.get(type).get(lastYearAvailScenario);
		}
		// Linear Interpolate the hours
		final int yearSecondPoint = getYearAvailableCapacity(type, year, true);
		final int yearFirstPoint = getYearAvailableCapacity(type, yearSecondPoint - 1, false);

		final float valueSecondPoint = renewableCapacity.get(type).get(yearSecondPoint);

		// Point(x1,y1)
		final float valueFirstPoint = renewableCapacity.get(type).get(yearFirstPoint);
		return (Interpolation.linear(yearFirstPoint, yearSecondPoint, valueFirstPoint,
				valueSecondPoint, year));
	}

	private void interpolateCapacityData() {
		for (final FuelName type : FuelName.getRenewableTypes()) {
			if (!renewableCapacity.containsKey(type)) {
				continue;
			}
			// Interpolate profile
			for (int year = startYear; year <= endYear; year++) {
				if (renewableCapacity.get(type).containsKey(year)) {
					continue;
				}
				renewableCapacity.get(type).put(year, interpolateCapacity(type, year));
			}
		}
	}

	private Map<Integer, Float> interpolateHourlyProfile(FuelName type, int year) {

		final int firstYearAvailScenario = Collections.min(renewableLoad.get(type).keySet());
		final int lastYearAvailScenario = Collections.max(renewableLoad.get(type).keySet());
		// Use first available data or just one value is available
		if ((year < firstYearAvailScenario) || (firstYearAvailScenario == lastYearAvailScenario)) {
			return renewableLoad.get(type).get(firstYearAvailScenario);
		}
		if (year > lastYearAvailScenario) {
			return renewableLoad.get(type).get(lastYearAvailScenario);
		}
		// Linear Interpolate the hours
		final int yearSecondPoint = getYearAvailable(type, year, true);
		final int yearFirstPoint = getYearAvailable(type, yearSecondPoint - 1, false);
		final Map<Integer, Float> values = new HashMap<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			final float valueSecondPoint = renewableLoad.get(type).get(yearSecondPoint)
					.get(hourOfYear);

			// Point(x1,y1)
			final float valueFirstPoint = renewableLoad.get(type).get(yearFirstPoint)
					.get(hourOfYear);
			values.put(hourOfYear, Interpolation.linear(yearFirstPoint, yearSecondPoint,
					valueFirstPoint, valueSecondPoint, year));
		}
		return values;
	}
	private void interpolateProfileData() {
		for (final FuelName type : FuelName.getRenewableTypes()) {
			if (!renewableLoad.containsKey(type)) {
				continue;
			}
			// Interpolate profile
			for (int year = startYear; year <= endYear; year++) {
				if (renewableLoad.get(type).containsKey(year)) {
					continue;
				}
				renewableLoad.get(type).put(year, interpolateHourlyProfile(type, year));
			}
		}
	}

	private void readCapacityData() {
		try (ConnectionSQL conn = new ConnectionSQL(nameDatabase, marketArea)) {
			// Scenario data
			final String tableName = Settings.getResCapacityScenario();
			final String query = "SELECT * FROM `tbl_" + tableName + "` WHERE `area_code`='"
					+ marketArea.getInitials() + "';";
			conn.setResultSet(query);

			// Read values from database for each year and renewable type
			while (conn.getResultSet().next()) {
				for (final FuelName type : FuelName.getRenewableTypes()) {

					if (conn.getResultSet().getString("res_type")
							.equals(type.toString().toLowerCase())) {
						renewableTypes.add(type);
						renewableCapacity.put(type, new HashMap<Integer, Float>());
						int year;
						for (int column = 1; column <= conn.getResultSetMetaData()
								.getColumnCount(); column++) {

							if (conn.getResultSetMetaData().getColumnTypeName(column)
									.equals("FLOAT")) {
								year = Integer.parseInt(
										conn.getResultSetMetaData().getColumnName(column));
								final float capacity = conn.getResultSet().getFloat("" + year);
								renewableCapacity.get(type).put(year, capacity);
							}
						}
						break;
					}
				}
			}

			// Initialize fields
			for (final FuelName type : renewableTypes) {
				utilisationfactor.put(type, new HashMap<>());
				yearlyFullLoadHours.put(type, new HashMap<>());
				renewableLoad.put(type, new HashMap<>());
				renewableLoadProfile.put(type, new HashMap<>());
			}

		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * @throws SQLException
	 * @param renewable
	 *            Scenario
	 */
	private void readProfileDataOneTable(String renewableScenario) throws SQLException {
		final String columnHourOfYear = "hour_of_year";
		for (final FuelName type : renewableTypes) {
			final String tableName = "tbl_" + renewableScenario;
			final String sqlQuery = "SELECT * FROM `" + tableName + "` WHERE type='"
					+ type.toString().toLowerCase() + "' AND area='" + marketArea.getInitials()
					+ "' ORDER BY `" + columnHourOfYear + "`;";

			try (ConnectionSQL conn = new ConnectionSQL(nameDatabase)) {
				conn.setResultSet(sqlQuery);
				final HashSet<Integer> profileYears = new HashSet<>();
				while (conn.getResultSet().next()) {
					// Read values from database
					final Integer profileYear = conn.getResultSet().getInt("year");
					profileYears.add(profileYear);
					if (!renewableLoad.get(type).containsKey(profileYear)) {
						renewableLoad.get(type).put(profileYear, new HashMap<>());
					}

					final int hourOfYear = conn.getResultSet().getInt(columnHourOfYear);
					final float value = conn.getResultSet().getFloat("value");
					renewableLoad.get(type).get(profileYear).put(hourOfYear, value);

					// Set first year
					if (profileYear < firstAvailableYear) {
						firstAvailableYear = profileYear;
					}
				}
				// Check size
				for (final Integer profileYear : profileYears) {
					if (!checkSize(renewableLoad.get(type).get(profileYear))) {
						logger.error("Renewable values of type " + type.toString() + " in area "
								+ marketArea.getInitials() + " for profile year" + profileYear
								+ " has not 8760 but "
								+ renewableLoad.get(type).get(profileYear).size() + " enties.");
					}
				}
			}
		}

		// Adjust start year by first available year
		startYear = Math.max(startYear, firstAvailableYear);
	}

	/**
	 * In order to safe resources regarding RAM delete values that will not
	 * needed for the future simulation years
	 */
	public void removeOldValues(int year) {
		final int yearOlderThan = 1;
		if (year < (Date.getStartYear() + yearOlderThan)) {
			return;
		}

		for (int yearDelete = Date.getStartYear(); yearDelete < (year
				- yearOlderThan); yearDelete++) {
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				final int key = Date.getKeyHourlyWithHourOfYear(yearDelete, hourOfYear);
				renewableLoadHourlyTotal.remove(key);
			}
		}
	}

	/**
	 * Set hourly load profile of specified renewables types and year
	 * 
	 * @param loadProfile
	 * @param type
	 * @param year
	 */
	public void setRenewableLoadProfile(List<Float> loadProfile, FuelName type, int year) {
		if ((loadProfile == null) || (loadProfile.size() != HOURS_PER_YEAR)) {
			logger.error("Profile incorrect!");
		}
		if (Math.abs(1 - Statistics.calcSum(loadProfile)) > 0.01f) {
			logger.error("Profile sum must be equal to one!");
		}
		renewableLoadProfile.get(type).put(year, loadProfile);
	}

}