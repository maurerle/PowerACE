package data.powerplant;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import supply.powerplant.PlantAbstract;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.types.FuelName;

/**
 * Load availability factors of power plants
 *
 * @author PR
 * @since 06/2013
 *
 */
public final class Availability implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Availability.class.getName());

	/** Availability of the coal technology in percent, e.g. 0.85 */
	private float availabilityCoal;
	/** Availability factor per fuel */
	private final Map<FuelName, Float> availabilityFactorsWeekday = new HashMap<>();
	/** Availability factor on weekends per fuel */
	private final Map<FuelName, Float> availabilityFactorsWeekend = new HashMap<>();
	/** Availability of the gas technology in percent, e.g. 0.85 */
	private float availabilityGas;
	/** Availability of the lignite technology in percent, e.g. 0.85 */
	private float availabilityLignite;
	/** Availability of the nuclear technology in percent, e.g. 0.85 */
	private float availabilityNuclear;
	/** Availability of the oil technology in percent, e.g. 0.85 */
	private float availabilityOil;
	/** Availability of other technologies in percent, e.g. 0.85 */
	private float availabilityOther;

	/** Current marketArea */
	private final MarketArea marketArea;

	public Availability(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setAvailability(this);
	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	/**
	 * @param powerPlant
	 *            The power plant for which the availability is determined.
	 * @return The availability of the <code>powerPlant</code>, e.g. 0.85, based
	 *         on the availability scenario.
	 */
	public float determineAvailability(PlantAbstract powerPlant) {
		float availability;

		if (powerPlant.getFuelType() == null) {
			availability = availabilityOther;
		} else {

			switch (powerPlant.getFuelType()) {

				case COAL:
				case CLEAN_COAL:
					availability = availabilityCoal;
					break;

				case GAS:
				case CLEAN_GAS:
					availability = availabilityGas;
					break;

				case LIGNITE:
				case CLEAN_LIGNITE:
					availability = availabilityLignite;
					break;

				case OIL:
					availability = availabilityOil;
					break;

				case OTHER:
					availability = availabilityOther;
					break;

				case URANIUM:
					availability = availabilityNuclear;
					break;

				default:
					availability = availabilityOther;
					logger.trace("Availability for fuel type not defined. Other is assumed.");
					break;
			}
		}
		return availability;
	}

	public float getAvailabilityFactors(FuelName fuelName) {
		return 1 - (((availabilityFactorsWeekday.get(fuelName) * 5f) / 7f)
				+ ((availabilityFactorsWeekend.get(fuelName) * 2f) / 7f));
	}

	public float getAvailabilityFactors(FuelName fuelName, boolean isWeekday) {
		if (isWeekday) {
			return 1 - availabilityFactorsWeekday.get(fuelName);
		} else {
			return 1 - availabilityFactorsWeekend.get(fuelName);
		}
	}

	public float getAvailabilityOther() {
		return availabilityOther;
	}

	/** Loads data */
	private void initialize() {

		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ Availability.class.getSimpleName());

		// Load average plant availabilities
		loadAvailability();
	}

	/** Loads availability factors from database */
	private void loadAvailability() {

		try {
			// Connect to database and execute query

			String tableName = ""; // to be set
			// Set DatabaseName
			ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED);

			final String sqlQuery = "SELECT * FROM `" + tableName + "` WHERE `scenario_id`="
					+ marketArea.getAvailabilityScenarioId();

			// Set result set
			conn.setResultSet(sqlQuery);

			// Get meta data for column names
			final ResultSetMetaData rsmd = conn.getResultSet().getMetaData();

			// Parse result set
			while (conn.getResultSet().next()) {
				conn.getResultSet().getString("Scenario_Name");

				for (int column = 3; column <= rsmd.getColumnCount(); column++) {
					// Set default factor for all fuels (works only when default
					// columns are at the beginning)

					if (rsmd.getColumnName(column).contains("Weekend")) {
						for (final FuelName fuelName : FuelName.values()) {
							availabilityFactorsWeekend.put(fuelName,
									conn.getResultSet().getFloat(rsmd.getColumnName(column)));
						}
					} else {
						for (final FuelName fuelName : FuelName.values()) {
							availabilityFactorsWeekday.put(fuelName,
									conn.getResultSet().getFloat(rsmd.getColumnName(column)));
						}
					}
				}
			}

			// Close connection
			conn.close();

		} catch (final SQLException e) {
			logger.warn(
					"Error while reading availability factors. All values set to 1 (full availability).");
			for (final FuelName fuelName : FuelName.values()) {
				availabilityFactorsWeekend.put(fuelName, 1f);
				availabilityFactorsWeekday.put(fuelName, 1f);
			}
			logger.error("SQLException", e);
		}
	}

}