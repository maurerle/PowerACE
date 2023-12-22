package data.storage;

import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.trader.spot.hydro.PumpStorageTrader;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.technique.EnergyConversion;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.logging.Folder;
import tools.logging.LoggerCSV;
import tools.types.FuelName;

/**
 * in this class the data of the pump storage units are stored
 * 
 * @author Genoese
 * 
 */
public final class PumpStorage implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(PumpStorage.class.getName());

	private final Map<String, List<PumpStoragePlant>> allPumpers = new LinkedHashMap<>();

	private final float[] capacityAllPlants = new float[24];
	// TODO Set Database name
	private final NameDatabase databaseName = NameDatabase.NAME_OF_DATABASED;
	private final Map<Integer, List<Float>> dynamicPumpStorageProfile = new LinkedHashMap<>();

	private int lastProfileYear;

	private int logid2;

	private final MarketArea marketArea;
	private final float[] operationAllPlants = new float[24];

	private final float[] prices = new float[24];

	private final float[] storageStatusAllPlants = new float[24];
	private final float[] totalIncomeAllPlants = new float[24];

	public PumpStorage(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setPumpStorage(this);
	}

	public void addNewPumper(String ownerName, PumpStoragePlant plant) {
		allPumpers.get(ownerName).add(plant);
	}

	private void calculatePumpStorageDayAheadResults() {
		Arrays.fill(capacityAllPlants, 0);
		Arrays.fill(operationAllPlants, 0);
		Arrays.fill(totalIncomeAllPlants, 0);
		Arrays.fill(storageStatusAllPlants, 0);
		Arrays.fill(prices, 0);

		for (final PumpStorageTrader trader : marketArea.getPumpStorageTraders()) {
			for (int hour = 0; hour < 24; hour++) {
				capacityAllPlants[hour] += trader.getSummedCapacity()[hour];
				operationAllPlants[hour] += trader.getSummedOperation()[hour];
				storageStatusAllPlants[hour] += trader.getSummedStorageStatus()[hour];
				totalIncomeAllPlants[hour] += trader.getTotalIncome()[hour];
				prices[hour] = trader.getPrices()[hour];
			}
		}

	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	public float getAllPumpersAvailableCapacity() {
		int allPumpersAvailableCapacity = 0;
		for (final PumpStoragePlant pumper : getAvailablePumpers()) {
			allPumpersAvailableCapacity += pumper.getAvailableCapacity();
		}
		return allPumpersAvailableCapacity;
	}

	public List<PumpStoragePlant> getAvailablePumpers() {
		final LocalDate today = Date.getCurrentDateTime().toLocalDate();
		return getAvailablePumpers(today);
	}

	public List<PumpStoragePlant> getAvailablePumpers(int year) {
		final LocalDate date = LocalDate.of(year, 1, 1);
		return getAvailablePumpers(date);
	}

	private List<PumpStoragePlant> getAvailablePumpers(LocalDate date) {
		final List<PumpStoragePlant> allAvailablePumpersOfAllOwners = new ArrayList<>();
		for (final String companyName : marketArea.getCompanyName().getCompanyNames().values()) {
			allAvailablePumpersOfAllOwners.addAll(getAvailablePumpers(companyName, date));
		}
		Collections.sort(allAvailablePumpersOfAllOwners, Collections.reverseOrder());
		return allAvailablePumpersOfAllOwners;
	}

	private List<PumpStoragePlant> getAvailablePumpers(String ownerName, LocalDate date) {
		final List<PumpStoragePlant> allAvailablePumpersOfOwner = new ArrayList<>();
		if (allPumpers.isEmpty()) {
			return allAvailablePumpersOfOwner;
		}
		for (final PumpStoragePlant pumper : allPumpers.get(ownerName)) {
			if ((pumper.getAvailableDate().isBefore(date)
					|| pumper.getAvailableDate().isEqual(date))
					&& (pumper.getShutDownDate().isAfter(date)
							|| pumper.getShutDownDate().isEqual(date))) {
				allAvailablePumpersOfOwner.add(pumper);
			}
		}
		Collections.sort(allAvailablePumpersOfOwner, Collections.reverseOrder());
		return allAvailablePumpersOfOwner;
	}

	/**
	 * Equals ask type, meaning - is production + is consumption.
	 * 
	 * @param year
	 * @return
	 */
	public List<Float> getDynamicPumpStorageProfile(int year) {
		if (!dynamicPumpStorageProfile.containsKey(year)) {
			return new ArrayList<>();
		}
		return dynamicPumpStorageProfile.get(year);
	}

	/**
	 * Equals ask type, meaning - is production + is consumption.
	 * 
	 * @param year
	 * @param hour
	 * @return
	 */
	public float getDynamicPumpStorageProfile(int year, int hour) {
		return dynamicPumpStorageProfile.get(year).get(hour);
	}

	public void initialize() {
		try {
			logger.info(marketArea.getInitialsBrackets() + "Initialize DataManagerPumpStorage");
			// Initialize map of all pumped storage plants
			for (final String companyName : marketArea.getCompanyName().getCompanyNames()
					.values()) {
				allPumpers.put(companyName, new ArrayList<>());
			}
			loadData();
			for (final List<PumpStoragePlant> list : allPumpers.values()) {
				Collections.sort(list);
			}
			// when activetrading == 3, then dynamic pumpstorage profile is
			// used
			if ((marketArea.getPumpStorageProfileData() != null)
					&& !marketArea.getPumpStorageProfileData().isEmpty()) {
				readLastTotalProfileYear();
				loadPumpStorageProfile();
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void loadData() {

		final String marketAreaInitials = marketArea.getInitials();

		final String tableName = marketArea.getPumpStorageData();

		final String query = "SELECT * FROM `" + tableName + "` WHERE `area`='" + marketAreaInitials
				+ "' " + "AND reservoir_volume>0 "
				+ "ORDER BY plant_ID AND commissioning_year DESC";

		try (final ConnectionSQL conn = new ConnectionSQL(databaseName)) {
			conn.setResultSet(query);
			conn.getResultSet().beforeFirst();

			while (conn.getResultSet().next()) {
				final PumpStoragePlant plant = new PumpStoragePlant();

				plant.setUnitID(conn.getResultSet().getInt("plant_ID"));
				plant.setName("Plant_" + conn.getResultSet().getString("plant_ID"));
				plant.setGenerationCapacity(conn.getResultSet().getFloat("net_power_generator"));
				plant.setAvailableCapacity(plant.getGenerationCapacity());
				plant.setPumpCapacity(conn.getResultSet().getFloat("net_power_pump"));
				plant.setStorageVolume(conn.getResultSet().getFloat("reservoir_volume"));
				// Start with half of the total storage volume
				plant.setStorageStatus(plant.getStorageVolume() * 0.5f);
				plant.setStorageInflow(conn.getResultSet().getFloat("inflow") / HOURS_PER_YEAR);
				plant.setAvailableDate(conn.getResultSet().getInt("commissioning_year"));
				// Pumped storage plants assumed to never be shut down, but
				// rather refurbished
				plant.setShutDownDate(LocalDate.MAX);

				plant.setFuelName(FuelName.HYDRO_PUMPED_STORAGE);
				plant.setEnergyConversionIndex(15);
				plant.setEnergyConversion(EnergyConversion
						.getEnergyConversionFromIndex(plant.getEnergyConversionIndex()));
				// If no owner defined in database, set to owner "Others"
				if (conn.resultSetIncludesColumn("owner_ref")) {
					plant.setOwnerID(conn.getResultSet().getInt("owner_ref"));
				} else {
					plant.setOwnerID(100);
				}
				plant.setOwnerName(marketArea.getCompanyName().getCompanyName(plant.getOwnerID()));

				plant.setName(conn.getResultSet().getString("name"));
				plant.setChargeEfficiency(conn.getResultSet().getFloat("charge_efficiency"));
				plant.setGenerationEfficiency(
						conn.getResultSet().getFloat("generation_efficiency"));
				plant.setEfficiency(plant.getChargeEfficiency() * plant.getGenerationEfficiency());

				try {
					allPumpers.get(plant.getOwnerName()).add(plant);
				} catch (final Exception e) {
					logger.error(e.getMessage(), e);
					allPumpers.get("Other").add(plant);
				}
			}
		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void loadPumpStorageProfile() {
		try (final ConnectionSQL conn = new ConnectionSQL(databaseName);) {

			final String tableName = marketArea.getPumpStorageProfileData();

			final String sqlQuery = "SELECT * FROM `" + tableName + "` WHERE `area`='"
					+ marketArea.getInitials() + "' ORDER BY `year`, `hour_of_year`";
			conn.setResultSet(sqlQuery);
			conn.getResultSet().beforeFirst();

			// Read hourly profile for each required year from database
			while (conn.getResultSet().next()) {
				final int year = conn.getResultSet().getInt("year");
				final float value = -conn.getResultSet().getFloat("pumped_storage");

				if (!dynamicPumpStorageProfile.containsKey(year)) {
					dynamicPumpStorageProfile.put(year, new ArrayList<Float>());
				}

				dynamicPumpStorageProfile.get(year).add(value);
			}
		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		}

		for (int year = lastProfileYear + 1; year <= Date.getLastDetailedForecastYear(); year++) {
			dynamicPumpStorageProfile.put(year, new ArrayList<Float>());
			dynamicPumpStorageProfile.get(year)
					.addAll(dynamicPumpStorageProfile.get(lastProfileYear));
		}

	}

	public void logBlockOperations() {
		if (Date.getDayOfYear() == 1) {
			logid2 = logInitialize("TotalPumpStorage", Date.getYear());
		}

		calculatePumpStorageDayAheadResults();

		final int day = Date.getDayOfYear() - 1;
		final int hourTracker = day * 24;
		for (int h = 0; h < 24; h++) {
			final String dataLine = h + 1 + ";" + (hourTracker + h + 1) + ";" + prices[h] + ";"
					+ capacityAllPlants[h] + ";" + operationAllPlants[h] + ";"
					+ storageStatusAllPlants[h] + ";" + totalIncomeAllPlants[h] + ";"
					+ totalIncomeAllPlants[h] + ";" + 0 + ";" + 0;
			LoggerCSV.writeLine(logid2, dataLine);
		}
	}

	public int logInitialize(String name, double capacity) {
		int logID;
		final String fileName = marketArea.getInitialsUnderscore() + name + Date.getYear()
				+ Settings.LOG_FILE_SUFFIX_CSV;
		final String description = "Describes the hourly operation of the" + name
				+ "PumpStoragePlant in 1 year with capacity of" + capacity;
		final String titleLine = "Hour;" + "Hour of the year;" + "Prices;" + "GenCapacity;"
				+ "Operation;" + "Storagestatus;" + "hrincome;" + "dayincome;" + "MarketStatus;";
		final String unitLine = "hour;" + "hour;" + "Euro/MWh;" + "MWh;" + "MWh;" + "Euros;"
				+ "Euros;" + "Euros/MW;" + "Market;";
		logID = LoggerCSV.newLogObject(Folder.HYDROPOWER, fileName, description, titleLine,
				unitLine, marketArea.getIdentityAndNameLong());
		return logID;
	}

	private void readLastTotalProfileYear() {
		// Establish connection
		try (final ConnectionSQL conn = new ConnectionSQL(databaseName);) {

			final String tableName = marketArea.getPumpStorageProfileData();

			// Set maximum demand year by choosing maximum between range of
			// scenario data and historical data
			final String sqlQueryMaxProfileYear = "SELECT MAX(`year`) AS `lastProfileYear` FROM `"
					+ tableName + "` WHERE `area`='" + marketArea.getInitials() + "'";
			conn.setResultSet(sqlQueryMaxProfileYear);
			conn.getResultSet().next();
			lastProfileYear = conn.getResultSet().getInt("lastProfileYear");
		} catch (final SQLException e) {
			logger.error(marketArea.getInitialsBrackets()
					+ "Error while reading last profile year for pumped storage!", e);
		}
	}
}