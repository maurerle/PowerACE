package data.powerplant;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.trader.spot.supply.SupplyTrader;
import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import supply.invest.State;
import supply.invest.StateStrategic;
import supply.powerplant.Plant;
import supply.powerplant.technique.EnergyConversion;
import supply.powerplant.technique.Type;
import tools.Sorting;
import tools.database.ConnectionSQL;
import tools.database.NameColumnsPowerPlant;
import tools.database.NameDatabase;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.math.Statistics;
import tools.types.FuelName;
import tools.types.Unit;

/**
 * In this class power plant data of the supply agents in the current market
 * area is managed.
 * <p>
 * At the beginning of the simulation all data is read from the database (
 * {@link #loadPlantDataAll()}).
 * <p>
 * For all necessary years (this is effectively the simulation's start year
 * until the last forecast year of the investors) the net capacity of all
 * capacity owners is summed (also called market share here) (
 * {@link #setMarketShare(int)}).
 * <p>
 * Any investments (construction of new plant, mothballing, ...) trigger an
 * adjustment of the relevant fields ({@link #addNewPowerPlant(String, Plant)}).
 * 
 */
public class SupplyData extends Agent implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(SupplyData.class.getName());
	/** Map containing all power plants from the database */
	private final Map<String, List<Plant>> allPowerPlants = new HashMap<>();
	/** Map containing all power plants available in the current year */
	private Map<String, List<Plant>> allPowerPlantsAvailableInCurrentYear;
	/** Map containing all power plants from the database */
	private final Map<Integer, Plant> allPowerPlantsById = new HashMap<>();
	/** Installed capacity by supply trader and year */
	private final Map<String, Map<Integer, Float>> capacityPerAgent = new HashMap<>();
	/** Maximum unit ID currently in the power plants map */
	private AtomicInteger maxUnitID = new AtomicInteger();
	/** Map containing all power plants available for agent in a year */
	private final Map<String, Map<Integer, List<Plant>>> plantsPerAgent = new HashMap<>();

	public SupplyData(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setSupplyData(this);
	}

	/**
	 * Add new power plant to the map containing all units and update market
	 * shares
	 * 
	 * @param ownerName
	 *            Name of plant owner
	 * @param plant
	 *            New power plant
	 */
	public void addNewPowerPlant(String ownerName, Plant plant) {
		// Add new plant to map with all power plants
		allPowerPlants.get(ownerName).add(plant);
		allPowerPlantsById.put(plant.getUnitID(), plant);

		// Adjust market shares
		adjustMarketShare(ownerName, plant);

		// Adjust merit order (in GenerationData)
		marketArea.getGenerationData().adjustMeritOrder(plant);
	}

	/**
	 * Adjust market shares by new plant
	 * 
	 * @param ownerName
	 *            Name of capacity owner
	 * @param plant
	 *            New plant installed
	 */
	private void adjustMarketShare(String ownerName, Plant plant) {
		final int lastYear = Math.min(Date.getLastDetailedForecastYear(), plant.getShutDownYear());
		// Increase market share of owner for all years starting with first year
		// of availability
		for (int year = plant.getAvailableYear(); year <= lastYear; year++) {
			final float capacityBefore = capacityPerAgent.get(ownerName).get(year);
			final float capacityNew = capacityBefore + plant.getNetCapacity();
			capacityPerAgent.get(ownerName).put(year, capacityNew);
			plantsPerAgent.get(ownerName).get(year).add(plant);
		}

		logger.info("Market share of " + ownerName + " adjusted by " + plant.getNetCapacity()
				+ " from " + plant.getAvailableYear() + " until " + lastYear + " ("
				+ (lastYear == Date.getLastDetailedForecastYear()
						? "last forecast year"
						: "last simulated year")
				+ ")");
	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	public List<Plant> getAllPlantsInActualOrder(int hourOfDay) {
		final Map<Plant, Float> allPlantsInclCost = new HashMap<>();

		for (final SupplyTrader supplyTrader : marketArea.getSupplyTrader()) {
			allPlantsInclCost.putAll(supplyTrader.getActivePlantsInclCosts(hourOfDay));
		}
		Sorting.sortByValueIncreasing(allPlantsInclCost);
		float lig = 0;
		float gas = 0;
		float coal = 0;
		float uran = 0;
		final List<Plant> allPlants = new ArrayList<>(allPlantsInclCost.size());
		for (final Plant plant : allPlantsInclCost.keySet()) {
			allPlants.add(plant);

			switch (plant.getFuelType()) {
				case URANIUM:
					uran += plant.getNetCapacity();
				case LIGNITE:
					lig += plant.getNetCapacity();
				case GAS:
					gas += plant.getNetCapacity();
				case COAL:
					coal += plant.getNetCapacity();
				case OIL:

				case CLEAN_COAL:

				case CLEAN_GAS:

				case CLEAN_LIGNITE:

				case OTHER:

				case RENEWABLE:

				default:
			}

		}

		return allPlants;
	}

	public Plant getAllPowerPlantsById(int id) {
		return allPowerPlantsById.get(id);
	}

	/**
	 * Get market share sum (equal to total installed capacity)
	 * 
	 * @param yearIndex
	 *            Year index for which market share data is required
	 */
	public float getCapacity(int year) {
		float sumCapacity = 0f;

		for (final String ownerName : capacityPerAgent.keySet()) {
			if (!capacityPerAgent.get(ownerName).containsKey(year)) {
				return sumCapacity;
			}
			sumCapacity += capacityPerAgent.get(ownerName).get(year);
		}
		return sumCapacity;
	}

	/**
	 * Get capacity in <code>year</code> and of <code>fuelName</code>.
	 * 
	 * @param yearIndex
	 *            Year index for which market share data is required
	 * @param fuelName
	 */
	public float getCapacity(int year, FuelName fuelName) {
		float sumInstalledCapacity = 0f;
		for (final String ownerName : allPowerPlants.keySet()) {
			for (final Plant plant : allPowerPlants.get(ownerName)) {
				if (plant.isOperating(year) && plant.getFuelName().equals(fuelName)) {
					sumInstalledCapacity += plant.getNetCapacity();
				}
			}
		}

		return sumInstalledCapacity;
	}

	public int getMaxUnitID() {
		return maxUnitID.get();
	}

	/**
	 * Get new unit ID (needs to be unique) which will be used later when
	 * writing in database as primary key of table
	 */
	public int getNewUnitID() {
		return maxUnitID.incrementAndGet();
	}

	/** Determine number of power plants in system */
	private int getNumberOfPlants() {
		int numberOfPlants = 0;
		for (final String owner : allPowerPlants.keySet()) {
			numberOfPlants += allPowerPlants.get(owner).size();
		}
		return numberOfPlants;
	}

	public Plant getPowerPlant(int plantIdentity) {
		for (final String owner : allPowerPlants.keySet()) {
			for (final Plant plant : allPowerPlants.get(owner)) {
				if (plant.getUnitID() == plantIdentity) {
					return plant;
				}
			}
		}
		return null;
	}

	public Map<String, List<Plant>> getPowerPlants(int year, Set<StateStrategic> strategicStates) {
		return getPowerPlants(year, null, strategicStates);
	}

	/**
	 * Get power plants available in current year
	 */
	public Map<String, List<Plant>> getPowerPlants(int year, String owner,
			Set<StateStrategic> strategicStates) {

		final Map<String, List<Plant>> plants = new LinkedHashMap<>();
		for (final String ownerIter : allPowerPlants.keySet()) {

			// Only take plants from owner or if undefined take plants from all
			// owners
			if ((owner != null) && !owner.equals(ownerIter)) {
				continue;
			}

			final List<Plant> plantsOfOwner = allPowerPlants.get(ownerIter);
			final List<Plant> plantsOfOwnerSelected = new ArrayList<>();

			for (final Plant plant : plantsOfOwner) {
				for (final StateStrategic strategicState : strategicStates) {
					switch (strategicState) {
						case DECOMMISSIONED:
							if (plant.getStateStrategic(year) == StateStrategic.DECOMMISSIONED) {
								plantsOfOwnerSelected.add(plant);
							}
							break;
						case NEWBUILD_OPPORTUNITY:
							if (plant.getStateStrategic(
									year) == StateStrategic.NEWBUILD_OPPORTUNITY) {
								plantsOfOwnerSelected.add(plant);
							}
							break;
						case MOTHBALLED:
							if (plant.getStateStrategic(year) == StateStrategic.MOTHBALLED) {
								plantsOfOwnerSelected.add(plant);
							}
							break;
						case OPERATING:
							if (plant.getStateStrategic(year) == StateStrategic.OPERATING) {
								plantsOfOwnerSelected.add(plant);
							}
							break;

						case UNDER_CONSTRUCTION:
							if (plant
									.getStateStrategic(year) == StateStrategic.UNDER_CONSTRUCTION) {
								plantsOfOwnerSelected.add(plant);
							}
							break;
						default:
							logger.error("State is not defined!");
							break;
					}
				}
			}
			plants.put(ownerIter, plantsOfOwnerSelected);
		}

		return plants;
	}

	/**
	 * Get all power plants
	 */
	public List<Plant> getPowerPlantsAsList() {
		final List<Plant> allPowerPlantsWithOutOwner = new ArrayList<>();
		for (final String owner : allPowerPlants.keySet()) {
			allPowerPlantsWithOutOwner.addAll(allPowerPlants.get(owner));
		}
		return allPowerPlantsWithOutOwner;
	}

	public List<Plant> getPowerPlantsAsList(int year, Set<StateStrategic> strategicStates) {
		return getPowerPlantsAsList(year, null, strategicStates);
	}

	/**
	 * Get power plants
	 */
	public List<Plant> getPowerPlantsAsList(int year, String owner,
			Set<StateStrategic> strategicStates) {

		final List<Plant> plants = new ArrayList<>();
		for (final String ownerIter : allPowerPlants.keySet()) {

			// Only take plants from owner or if undefined take plants from all
			// owners
			if ((owner != null) && !owner.equals(ownerIter)) {
				continue;
			}

			final List<Plant> plantsOfOwner = allPowerPlants.get(ownerIter);

			for (final Plant plant : plantsOfOwner) {
				for (final StateStrategic strategicState : strategicStates) {
					switch (strategicState) {
						case DECOMMISSIONED:
							if (plant.getStateStrategic(year) == StateStrategic.DECOMMISSIONED) {
								plants.add(plant);
							}
							break;
						case NEWBUILD_OPPORTUNITY:
							if (plant.getStateStrategic(
									year) == StateStrategic.NEWBUILD_OPPORTUNITY) {
								plants.add(plant);
							}
							break;
						case MOTHBALLED:
							if (plant.getStateStrategic(year) == StateStrategic.MOTHBALLED) {
								plants.add(plant);
							}
							break;
						case OPERATING:
							if (plant.getStateStrategic(year) == StateStrategic.OPERATING) {
								plants.add(plant);
							}
							break;
						case UNDER_CONSTRUCTION:
							if (plant
									.getStateStrategic(year) == StateStrategic.UNDER_CONSTRUCTION) {
								plants.add(plant);
							}
							break;
						default:
							logger.error("State is not defined!");
							break;
					}
				}
			}
		}

		return plants;
	}

	@Override
	public void initialize() {
		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ SupplyData.class.getSimpleName());
		try {
			// Load all plant data at the beginning of the simulation

			// Initialize plant map
			initializePlantMap();

			// Load power plant data
			loadPlantDataAll();

			// Set market shares for all years of simulation
			final int startYear = Date.getStartYear();
			for (int year = startYear; year <= Date.getLastDetailedForecastYear(); year++) {
				setMarketShare(year);
			}

			// Set plant data for the current year
			setPlantDataCurrentYear();
		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Initialize the map containing all power plants while parsing the names of
	 * all supply traders
	 */
	private void initializePlantMap() {
		// Add regular supply trader
		for (final String companyName : marketArea.getCompanyName().getCompanyNames().values()) {
			allPowerPlants.put(companyName, new ArrayList<Plant>());
			capacityPerAgent.put(companyName, new HashMap<Integer, Float>());
			plantsPerAgent.put(companyName, new HashMap<Integer, List<Plant>>());
		}

		// Initialize new unit ID
		maxUnitID.set(0);
	}

	/** Load power plant data from database */

	private void loadPlantDataAll() throws SQLException {

		// Connection to PowerPlant Database
		// TODO Set Database name
		final NameDatabase databaseName = NameDatabase.NAME_OF_DATABASED;

		try (final ConnectionSQL conn = new ConnectionSQL(databaseName);) {

			// Check if plant is has been shut down before start year of
			// simulation (shutdown plants are not deactivated)
			final int year = Date.getYear();

			String sqlQuery;
			final String shortagePowerPlant = "powerplant";

			final String tablePowerPlant = marketArea.getPowerPlantTableName() + " AS "
					+ shortagePowerPlant + " ";

			final String sqlShutDownDate = "(" + shortagePowerPlant + ".shut_down IS NULL OR "
					+ shortagePowerPlant + "."
					+ NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName() + " >= " + year + " OR "
					+ shortagePowerPlant + "."
					+ NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName() + " ='')";

			String initials = marketArea.getInitials();

			final String sqlColumns = shortagePowerPlant + "."
					+ NameColumnsPowerPlant.UNIT_ID.getColumnName() + ","
					+ NameColumnsPowerPlant.UNIT_NAME.getColumnName() + ","
					+ NameColumnsPowerPlant.LOCATION_NAME.getColumnName() + ","
					+ NameColumnsPowerPlant.NET_INSTALLED_CAPACITY.getColumnName() + ","
					+ NameColumnsPowerPlant.GROSS_INSTALLED_CAPACITY.getColumnName() + ","
					+ NameColumnsPowerPlant.OPERATING_LIFETIME_NO_NUCLEAR_PHASEOUT.getColumnName()
					+ "," + NameColumnsPowerPlant.OWNER_ID.getColumnName() + ","
					+ NameColumnsPowerPlant.FUEL_NAME_INDEX.getColumnName() + ","
					+ NameColumnsPowerPlant.TECHNOLOGY.getColumnName() + ","
					+ NameColumnsPowerPlant.EFFICIENCY.getColumnName() + ","
					+ NameColumnsPowerPlant.CHP.getColumnName() + ","
					+ NameColumnsPowerPlant.MUSTRUN.getColumnName() + ","
					+ NameColumnsPowerPlant.MUSTRUN_CHP.getColumnName() + ","
					+ NameColumnsPowerPlant.UNIT_ZIP_CODE.getColumnName() + "," + "power_thermal"
					+ "," + NameColumnsPowerPlant.PRODUCTION_MINIMUM.getColumnName() + ","
					+ "bna_number" + "," + NameColumnsPowerPlant.AVAILABLE_DATE.getColumnName()
					+ "," + shortagePowerPlant + "."
					+ NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName() + ","
					+ NameColumnsPowerPlant.UNIT_NAME.getColumnName() + ","
					+ NameColumnsPowerPlant.LATITUDE.getColumnName() + ","
					+ NameColumnsPowerPlant.LONGITUDE.getColumnName() + "," + "shut_down_table."
					+ NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName()
					+ " AS shut_down_replace";

			sqlQuery = "SELECT " + sqlColumns + " FROM " + tablePowerPlant + " WHERE area" + "='"
					+ initials + "' AND " + sqlShutDownDate
					+ " AND fuel_ref in (1,2,3,4,5,22) AND NOT status_ref=5 AND efficiency>0.1 AND "
					+ NameColumnsPowerPlant.DEACTIVATED + " = 0";

			sqlQuery += " ORDER BY " + NameColumnsPowerPlant.NET_INSTALLED_CAPACITY.getColumnName()
					+ " DESC;";

			// Execute SQL query
			conn.setResultSet(sqlQuery);
			conn.getResultSet().beforeFirst();

			final Set<String> bnaNumbers = new HashSet<>();

			while (conn.getResultSet().next()) {
				final Plant plant = new Plant(marketArea);

				// Store the data from powerplant database

				final int unitID = conn.getResultSet()
						.getInt(NameColumnsPowerPlant.UNIT_ID.getColumnName());
				plant.setUnitID(unitID);
				// Store max unit ID in object field to be used for new
				// plants
				if (unitID > maxUnitID.get()) {
					maxUnitID.set(unitID);
				}
				plant.setUnitName(conn.getResultSet()
						.getString(NameColumnsPowerPlant.UNIT_NAME.getColumnName()));
				plant.setLocationName(conn.getResultSet()
						.getString(NameColumnsPowerPlant.LOCATION_NAME.getColumnName()));
				plant.setNetCapacity(conn.getResultSet()
						.getFloat(NameColumnsPowerPlant.NET_INSTALLED_CAPACITY.getColumnName()));
				plant.setGrossCapacity(conn.getResultSet()
						.getFloat(NameColumnsPowerPlant.GROSS_INSTALLED_CAPACITY.getColumnName()));

				plant.setOperatingLifetime(conn.getResultSet()
						.getInt(NameColumnsPowerPlant.OPERATING_LIFETIME_NO_NUCLEAR_PHASEOUT
								.getColumnName()));

				plant.setOwnerID(
						conn.getResultSet().getInt(NameColumnsPowerPlant.OWNER_ID.getColumnName()));

				plant.setOwnerName(marketArea.getCompanyName().getCompanyName(plant.getOwnerID()));

				plant.setFuelName(FuelName.getFuelName(conn.getResultSet()
						.getInt(NameColumnsPowerPlant.FUEL_NAME_INDEX.getColumnName())));
				plant.setEnergyConversionIndex(conn.getResultSet()
						.getInt(NameColumnsPowerPlant.TECHNOLOGY.getColumnName()));
				plant.setEnergyConversion(EnergyConversion
						.getEnergyConversionFromIndex(plant.getEnergyConversionIndex()));
				plant.setEfficiency(conn.getResultSet()
						.getFloat(NameColumnsPowerPlant.EFFICIENCY.getColumnName()));
				plant.setChp((conn.getResultSet()
						.getInt(NameColumnsPowerPlant.CHP.getColumnName()) != 0));
				plant.setMustrunChp((conn.getResultSet()
						.getInt(NameColumnsPowerPlant.MUSTRUN_CHP.getColumnName()) != 0));
				plant.setMustrun((conn.getResultSet()
						.getInt(NameColumnsPowerPlant.MUSTRUN.getColumnName()) != 0));

				// if minimum capacity/Productions exists
				try {
					plant.setMinProduction(conn.getResultSet()
							.getFloat(NameColumnsPowerPlant.PRODUCTION_MINIMUM.getColumnName()));
				} catch (final SQLException e) {

				}

				// Check if AvailabilityDate exists otherwise set it first
				// day of the year
				final String availableDate = conn.getResultSet()
						.getString(NameColumnsPowerPlant.AVAILABLE_DATE.getColumnName());
				if (availableDate.length() > 4) {
					plant.setAvailableDate(LocalDate.parse(availableDate));
				} else {
					plant.setAvailableDate(Integer.parseInt(availableDate));
				}

				// Check if ShutDownDate exists otherwise set it to last day
				// of the year
				String shutDownDate = null;
				try {
					shutDownDate = conn.getResultSet().getString("shut_down_replace");
				} catch (final SQLException e) {

				}
				if (shutDownDate != null) {
					if (shutDownDate.length() > 4) {
						plant.setShutDownDate(LocalDate.parse(shutDownDate));
					} else {
						plant.setShutDownDate(Integer.parseInt(shutDownDate));
					}
				} else if (conn.getResultSet()
						.getString(NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName()) == null
						|| conn.getResultSet()
								.getString(NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName())
								.equals("0")) {
					// Substract one year because both start and end year
					// are counted

					// If age restriction of power plants, is in near future
					// a shut down should be know and hence plant should
					// feature a shutdown date.
					int shutDownYear = (plant.getAvailableYear() + plant.getOperatingLifetime())
							- 1;
					final Integer shutDownYearNotAllowedStart = marketArea
							.getYearFromShutDownPowerPlantsNotAllowedStart();
					final Integer shutDownYearNotAllowedEnd = marketArea
							.getYearFromShutDownPowerPlantsNotAllowedEnd();
					if ((shutDownYearNotAllowedStart != null) && (shutDownYearNotAllowedEnd != null)
							&& (shutDownYearNotAllowedStart < shutDownYear)
							&& (shutDownYear < shutDownYearNotAllowedEnd)) {

						final Integer yearDifference = (shutDownYearNotAllowedEnd
								- shutDownYearNotAllowedStart) + 1;
						shutDownYear += yearDifference;
					}

					plant.setShutDownDate(shutDownYear);
				} else {
					shutDownDate = conn.getResultSet()
							.getString(NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName());

					if (shutDownDate.length() > 4) {
						plant.setShutDownDate(
								LocalDate.parse(shutDownDate).minus(1, ChronoUnit.YEARS));
					} else {
						plant.setShutDownDate(Integer.parseInt(shutDownDate) - 1);
					}
				}
				// Coal phase-out
				if (((plant.getFuelName() == FuelName.COAL)
						|| (plant.getFuelName() == FuelName.LIGNITE))) {
					if (plant.getShutDownYear() > marketArea.getShutDownYearCoal()) {
						plant.setShutDownDate(marketArea.getShutDownYearCoal());
					}
				}

				// Determine power plant category
				Type.determinePowerPlantCategory(plant);

				// Determine variable O&M Costs
				plant.setCostsOperationMaintenanceVar(
						marketArea.getOperationMaintenanceCosts().getCostVar(plant));

				// Set strategic states initially
				State.setStatesStrategicInitial(plant, StateStrategic.OPERATING);

				if ((plant.getBNANumber() != null) && plant.getBNANumber().startsWith("BNA")) {
					if (bnaNumbers.contains(plant.getBNANumber())) {
						logger.error("BNANumber should be unique! " + plant.getBNANumber());
					}
					bnaNumbers.add(plant.getBNANumber());
				}

				// Add power plant to map
				final String ownerName = marketArea.getCompanyName()
						.getCompanyName(plant.getOwnerID());

				allPowerPlants.get(ownerName).add(plant);
				allPowerPlantsById.put(plant.getUnitID(), plant);
			}
		}

		logger.info(marketArea.getInitialsBrackets() + "..." + getNumberOfPlants()
				+ " plants read from database.");
	}

	/**
	 * Log the profit of all power plants in market area.
	 */
	public void logPlantProfitabilityData() {

		final String fileName = marketArea.getInitialsUnderscore() + "PlantProfitability";
		final String description = "This file contains the profitability of all power plants of the market area";

		final int yearsFirstCut = 15;
		final int yearsSecondCut = 20;

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("Owner", Unit.NONE));
		columns.add(new ColumnHeader("Plant Id", Unit.NONE));
		columns.add(new ColumnHeader("Plant Name", Unit.NONE));
		columns.add(new ColumnHeader("Capacity", Unit.CAPACITY));
		columns.add(new ColumnHeader("Efficiency", Unit.NONE));
		columns.add(new ColumnHeader("FuelType", Unit.NONE));
		columns.add(new ColumnHeader("Start Year", Unit.YEAR));
		columns.add(new ColumnHeader("Shut Down Year", Unit.YEAR));
		columns.add(new ColumnHeader("Invest", Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Net present value at investment decision",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit Technical lifetime undiscounted",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit Technical lifetime discounted",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit " + yearsFirstCut + " years discounted",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit " + yearsSecondCut + " years discounted",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader(
				"Total Profit " + yearsSecondCut
						+ " years discounted (Extrapolated with average if shorter)",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader(
				"Profit " + yearsSecondCut
						+ " years discounted (Extrapolated with last year if shorter)",
				Unit.CURRENCY_MILLION));

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Income " + year, Unit.CURRENCY_MILLION));
		}
		columns.add(new ColumnHeader("Cumulated capacity payments", Unit.CURRENCY_MILLION));

		final int logCounter = LoggerXLSX.newLogObject(Folder.GENERATOR, fileName, description,
				columns, marketArea.getIdentityAndNameLong(), Frequency.HOURLY);

		// Calculate map to save values and speed up overall calculation
		final Map<Integer, Float> discountFactor = new HashMap<>();
		float interestRate = 1.0f;
		if (marketArea.getInvestorsConventionalGeneration().size() > 0) {
			interestRate += marketArea.getInvestorsConventionalGeneration().get(0)
					.getInterestRate();
		}

		// speed up calculations and store results of pow
		for (int year = 0; year <= 150; year++) {
			discountFactor.put(year, 1 / (float) Math.pow(interestRate, year));
		}

		final float million = 1_000_000;

		for (final String agentName : allPowerPlants.keySet()) {
			for (final Plant plant : allPowerPlants.get(agentName)) {

				final List<Object> dataLine = new ArrayList<>();
				dataLine.add(agentName);
				dataLine.add(plant.getUnitID());
				dataLine.add(plant.getName());
				dataLine.add(plant.getNetCapacity());
				dataLine.add(plant.getEfficiency());
				dataLine.add(plant.getFuelType());
				dataLine.add(plant.getAvailableYear());
				dataLine.add(plant.getShutDownYear());

				// Make calculations
				final float investmentPayments = (plant.getInvestmentPayment()
						* plant.getNetCapacity() * 1000) / million;

				float netPresentValueFirstTimeSpan = -investmentPayments;
				float netPresentValueSecondTimeSpan = -investmentPayments;
				float netPresentValueTechnicalLifeTime = -investmentPayments;
				float netPresentValueTechnicalLifeTimeUndiscounted = -investmentPayments;

				final List<Object> yearlyIncome = new ArrayList<>();

				float yearlyIncomeValueTotal = 0f;
				float yearlyIncomeLast = 0f;
				int yearlyIncomeValueTotalCounter = 0;

				for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {

					final int yearsDiff = year - plant.getAvailableYear();

					if ((yearsDiff < 0) || (plant.getProfitYearly(year) == null)) {
						yearlyIncome.add("-");
					} else {
						final float yearlyIncomeValue = plant.getProfitYearly(year) / million;
						yearlyIncomeLast = yearlyIncomeValue;
						yearlyIncomeValueTotal += yearlyIncomeValue;
						yearlyIncomeValueTotalCounter++;
						final float yearlyIncomeValueDiscounted = yearlyIncomeValue
								* discountFactor.get(yearsDiff);

						yearlyIncome.add(yearlyIncomeValue);
						netPresentValueTechnicalLifeTimeUndiscounted += yearlyIncomeValue;
						netPresentValueTechnicalLifeTime += yearlyIncomeValueDiscounted;

						if (yearsDiff < yearsFirstCut) {
							netPresentValueFirstTimeSpan += yearlyIncomeValueDiscounted;
						}

						if (yearsDiff < yearsSecondCut) {
							netPresentValueSecondTimeSpan += yearlyIncomeValueDiscounted;
						}

					}

				}

				float netPresentValueSecondTimeSpanExtrapolatedAvgIfShorter = netPresentValueSecondTimeSpan;
				float netPresentValueSecondTimeSpanExtrapolatedLastYearIfShorter = netPresentValueSecondTimeSpan;

				// Extrapolate plants
				if (yearlyIncomeValueTotalCounter > 0) {
					final float yearlyIncomeValueAvg = yearlyIncomeValueTotal
							/ yearlyIncomeValueTotalCounter;

					for (int year = Date.getLastYear() + 1; year < (plant.getAvailableYear()
							+ yearsSecondCut); year++) {
						final int yearsDiff = year - plant.getAvailableYear();
						final float yearlyIncomeValueAvgDiscounted = yearlyIncomeValueAvg
								* discountFactor.get(yearsDiff);
						final float yearlyIncomeValueLastDiscounted = yearlyIncomeLast
								* discountFactor.get(yearsDiff);

						netPresentValueSecondTimeSpanExtrapolatedAvgIfShorter += yearlyIncomeValueAvgDiscounted;
						netPresentValueSecondTimeSpanExtrapolatedLastYearIfShorter += yearlyIncomeValueLastDiscounted;
					}
				}

				dataLine.add(investmentPayments);
				dataLine.add(plant.getNetPresentValue());
				dataLine.add(netPresentValueTechnicalLifeTimeUndiscounted);
				dataLine.add(netPresentValueTechnicalLifeTime);
				dataLine.add(netPresentValueFirstTimeSpan);
				dataLine.add(netPresentValueSecondTimeSpan);
				dataLine.add(netPresentValueSecondTimeSpanExtrapolatedAvgIfShorter);
				dataLine.add(netPresentValueSecondTimeSpanExtrapolatedLastYearIfShorter);

				for (final Object income : yearlyIncome) {
					dataLine.add(income);
				}
				LoggerXLSX.writeLine(logCounter, dataLine);
			}
		}

		LoggerXLSX.close(logCounter);

	}

	/**
	 * Log the profit of all power plants in market area.
	 */
	public void logPlantProfitabilityDataVerbose() {

		final String fileName = marketArea.getInitialsUnderscore() + "PlantProfitabilityVerbose";
		final String description = "This file contains the profitability of all power plants of the market area";

		final int yearsFirstCut = 15;
		final int yearsSecondCut = 20;

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("Owner", Unit.NONE));
		columns.add(new ColumnHeader("Plant Id", Unit.NONE));
		columns.add(new ColumnHeader("Plant Name", Unit.NONE));
		columns.add(new ColumnHeader("Capacity", Unit.CAPACITY));
		columns.add(new ColumnHeader("Efficiency", Unit.NONE));
		columns.add(new ColumnHeader("FuelType", Unit.NONE));
		columns.add(new ColumnHeader("Start Year", Unit.YEAR));
		columns.add(new ColumnHeader("Shut Down Year", Unit.YEAR));
		columns.add(new ColumnHeader("Invest", Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Fixed Costs", Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Net present value at investment decision",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit Technical lifetime undiscounted",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit Technical lifetime discounted",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit " + yearsFirstCut + " years discounted",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader("Total Profit " + yearsSecondCut + " years discounted",
				Unit.CURRENCY));
		columns.add(new ColumnHeader(
				"Total Profit " + yearsSecondCut + " years discounted (Extrapolated with avg)",
				Unit.CURRENCY_MILLION));
		columns.add(new ColumnHeader(
				"Total Profit " + yearsSecondCut + " years discounted (Extrapolated with lastYear)",
				Unit.CURRENCY_MILLION));

		columns.add(new ColumnHeader("Total Income Day-Ahead", Unit.CAPACITY_PRICE_MILLION));
		columns.add(new ColumnHeader("Total Costs Day-Ahead", Unit.CAPACITY_PRICE_MILLION));
		columns.add(new ColumnHeader("Total Income Capacity Market", Unit.CAPACITY_PRICE_MILLION));
		columns.add(new ColumnHeader("Total Income Balancing Market", Unit.CAPACITY_PRICE_MILLION));

		columns.add(new ColumnHeader("Cumulated capacity payments", Unit.CAPACITY_PRICE));

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Profit " + year, Unit.CAPACITY_PRICE_MILLION));
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Income Day-Ahead Market " + year,
					Unit.CAPACITY_PRICE_MILLION));
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Fuel and carbons costs Day-Ahead Market " + year,
					Unit.CAPACITY_PRICE_MILLION));
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Income Capacity Market " + year,
					Unit.CAPACITY_PRICE_MILLION));
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(
					new ColumnHeader("Income Balancing Markets " + year, Unit.CURRENCY_MILLION));
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Running Hours " + year, Unit.CURRENCY_MILLION));
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Electricity Production " + year, Unit.ENERGY_VOLUME));
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Carbon Emissions " + year, Unit.TONS_CO2));
		}

		final int logCounter = LoggerXLSX.newLogObject(Folder.GENERATOR, fileName, description,
				columns, marketArea.getIdentityAndNameLong(), Frequency.HOURLY);

		// Calculate map to save values and speed up overall calculation
		final Map<Integer, Float> discountFactor = new HashMap<>();
		float interestRate = 1.0f;
		if (marketArea.getInvestorsConventionalGeneration().size() > 0) {
			interestRate += marketArea.getInvestorsConventionalGeneration().get(0)
					.getInterestRate();
		}

		// speed up calculations and store results of pow
		for (int year = 0; year <= 150; year++) {
			discountFactor.put(year, 1 / (float) Math.pow(interestRate, year));
		}

		final float million = 1_000_000;

		for (final String agentName : allPowerPlants.keySet()) {
			for (final Plant plant : allPowerPlants.get(agentName)) {
				final List<Object> dataLine = new ArrayList<>();
				dataLine.add(agentName);
				dataLine.add(plant.getUnitID());
				dataLine.add(plant.getName());
				dataLine.add(plant.getNetCapacity());
				dataLine.add(plant.getEfficiency());
				dataLine.add(plant.getFuelType());
				dataLine.add(plant.getAvailableYear());
				dataLine.add(plant.getShutDownYear());

				// Make calculations
				final float investmentPayments = (plant.getInvestmentPayment() * 1000) / million;

				float netPresentValueFirstTimeSpan = -investmentPayments;
				float netPresentValueSecondTimeSpan = -investmentPayments;
				float netPresentValueTechnicalLifeTime = -investmentPayments;
				float netPresentValueTechnicalLifeTimeUndiscounted = -investmentPayments;
				float totalCostsDayAhead = 0f;
				float totalIncomeDayAhead = 0f;

				final List<Object> yearlyProfit = new ArrayList<>();
				final List<Object> operatingHours = new ArrayList<>();
				final List<Object> electricityProduced = new ArrayList<>();
				final List<Object> carbonEmissions = new ArrayList<>();
				final List<Object> incomeDayAhead = new ArrayList<>();
				final List<Object> costsDayAhead = new ArrayList<>();

				float yearlyIncomeValueTotal = 0f;
				float yearlyIncomeLast = 0f;
				int yearlyIncomeValueTotalCounter = 0;

				for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {

					final int yearsDiff = year - plant.getAvailableYear();
					operatingHours.add(plant.getRunningHours(year));
					electricityProduced.add(plant.getElectricityProductionYearly(year));
					carbonEmissions.add(plant.getCarbonEmissionsYearly(year));

					try {
						final float costsDayAheadYearly = plant.getCostYearlyDayAheadMarket(year)
								/ (plant.getNetCapacity() * million);
						totalCostsDayAhead += costsDayAheadYearly;
						costsDayAhead.add(costsDayAheadYearly);
					} catch (final NullPointerException e) {
						costsDayAhead.add("-");
					}

					try {
						final float incomeDayAheadYearly = plant.getIncomeYearlyDayAheadMarket(year)
								/ (plant.getNetCapacity() * million);
						totalIncomeDayAhead += incomeDayAheadYearly;
						incomeDayAhead.add(incomeDayAheadYearly);
					} catch (final NullPointerException e) {
						incomeDayAhead.add("-");
					}

					try {

						final float yearlyIncomeValue = plant.getProfitYearly(year)
								/ (plant.getNetCapacity() * million);
						yearlyIncomeLast = yearlyIncomeValue;
						yearlyIncomeValueTotal += yearlyIncomeValue;
						yearlyIncomeValueTotalCounter++;
						final float yearlyIncomeValueDiscounted = yearlyIncomeValue
								* discountFactor.get(yearsDiff);

						yearlyProfit.add(yearlyIncomeValue);
						netPresentValueTechnicalLifeTimeUndiscounted += yearlyIncomeValue;
						netPresentValueTechnicalLifeTime += yearlyIncomeValueDiscounted;

						if (yearsDiff < yearsFirstCut) {
							netPresentValueFirstTimeSpan += yearlyIncomeValueDiscounted;
						}

						if (yearsDiff < yearsSecondCut) {
							netPresentValueSecondTimeSpan += yearlyIncomeValueDiscounted;
						}

					} catch (final NullPointerException e) {
						yearlyProfit.add("-");
					}
				}

				float netPresentValueSecondTimeSpanExtrapolatedAvgIfShorter = netPresentValueSecondTimeSpan;
				float netPresentValueSecondTimeSpanExtrapolatedLastYearIfShorter = netPresentValueSecondTimeSpan;

				// Extrapolate plants profit if running less than 20 years
				if (yearlyIncomeValueTotalCounter > 0) {
					final float yearlyIncomeValueAvg = yearlyIncomeValueTotal
							/ yearlyIncomeValueTotalCounter;

					for (int year = Date.getLastYear() + 1; year < (plant.getAvailableYear()
							+ yearsSecondCut); year++) {
						final int yearsDiff = year - plant.getAvailableYear();
						if (!discountFactor.containsKey(yearsDiff)) {
							continue;
						}
						final float yearlyIncomeValueAvgDiscounted = yearlyIncomeValueAvg
								* discountFactor.get(yearsDiff);
						final float yearlyIncomeValueLastDiscounted = yearlyIncomeLast
								* discountFactor.get(yearsDiff);

						netPresentValueSecondTimeSpanExtrapolatedAvgIfShorter += yearlyIncomeValueAvgDiscounted;
						netPresentValueSecondTimeSpanExtrapolatedLastYearIfShorter += yearlyIncomeValueLastDiscounted;
					}
				}

				dataLine.add(investmentPayments);
				dataLine.add(plant.getCostsOperationMaintenanceFixed() / million);
				dataLine.add(plant.getNetPresentValue());
				dataLine.add(netPresentValueTechnicalLifeTimeUndiscounted);
				dataLine.add(netPresentValueTechnicalLifeTime);
				dataLine.add(netPresentValueFirstTimeSpan);
				dataLine.add(netPresentValueSecondTimeSpan);
				dataLine.add(netPresentValueSecondTimeSpanExtrapolatedAvgIfShorter);
				dataLine.add(netPresentValueSecondTimeSpanExtrapolatedLastYearIfShorter);
				dataLine.add(totalIncomeDayAhead);
				dataLine.add(totalCostsDayAhead);

				for (final Object income : yearlyProfit) {
					dataLine.add(income);
				}

				for (final Object income : incomeDayAhead) {
					dataLine.add(income);
				}
				for (final Object costs : costsDayAhead) {
					dataLine.add(costs);
				}

				for (final Object hours : operatingHours) {
					dataLine.add(hours);
				}

				for (final Object electricity : electricityProduced) {
					dataLine.add(electricity);
				}

				for (final Object carbon : carbonEmissions) {
					dataLine.add(carbon);
				}

				LoggerXLSX.writeLine(logCounter, dataLine);
			}
		}

		float totalExchange = 0f;
		float totalRenewable = 0f;
		float totalPumpedStorage = 0f;
		float totalPeakerVoLL = 0f;
		final Map<Integer, Float> exchange = new HashMap<>();
		final Map<Integer, Float> renewable = new HashMap<>();
		final Map<Integer, Float> peakerVoLL = new HashMap<>();
		final Map<Integer, Float> pumpedStorage = new HashMap<>();

		// calculate income values
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			float yearlyExchange = 0f;
			float yearlyRenewable = 0f;
			float yearlyPumpedStorage = 0f;
			float yearlyPeakerVoLL = 0f;
			final int lastHourOfYear = Date.getLastHourOfYear(year);
			for (int hourOfYear = 0; hourOfYear < lastHourOfYear; hourOfYear++) {
				final float price = marketArea.getElectricityResultsDayAhead()
						.getHourlyPriceOfYear(year, hourOfYear);
				// regard exchange as a powerplant meaning production is import
				// which is negative in flow map so switch sign
				yearlyExchange += price * -1
						* marketArea.getExchange().getHourlyFlow(year, hourOfYear);
				yearlyRenewable += price
						* marketArea.getManagerRenewables().getTotalRenewableLoad(year, hourOfYear);

				yearlyPumpedStorage += price * marketArea.getElectricityProduction()
						.getElectricityPumpedStorage(year, hourOfYear);

			}
			yearlyExchange /= million;
			yearlyRenewable /= million;
			yearlyPumpedStorage /= million;
			yearlyPeakerVoLL /= million;

			totalExchange += yearlyExchange;
			totalRenewable += yearlyRenewable;
			totalPumpedStorage += yearlyPumpedStorage;
			totalPeakerVoLL += yearlyPeakerVoLL;

			exchange.put(year, yearlyExchange);
			renewable.put(year, yearlyRenewable);
			pumpedStorage.put(year, yearlyPumpedStorage);
			peakerVoLL.put(year, yearlyPeakerVoLL);
		}

		final List<Object> dataLineExchange = new ArrayList<>();
		final List<Object> dataLineRenewable = new ArrayList<>();
		final List<Object> dataLinePumpedStorage = new ArrayList<>();
		final List<Object> dataLinePeakerVoLL = new ArrayList<>();

		// Enter names
		dataLineExchange.add("Exchange");
		dataLineRenewable.add("Renewable");
		dataLinePumpedStorage.add("PumpedStoraged");

		for (int counter = 1; counter < 15; counter++) {
			dataLineExchange.add("-");
			dataLineRenewable.add("-");
			dataLinePumpedStorage.add("-");
			dataLinePeakerVoLL.add("-");
		}

		dataLineExchange.add(totalExchange);
		dataLineRenewable.add(totalRenewable);
		dataLinePumpedStorage.add(totalPumpedStorage);
		dataLinePeakerVoLL.add(totalPeakerVoLL);

		// fill profits before with dummy values
		for (int counter = 16; counter < 22; counter++) {
			dataLineExchange.add("-");
			dataLineRenewable.add("-");
			dataLinePumpedStorage.add("-");
			dataLinePeakerVoLL.add("-");
		}
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			dataLineExchange.add("-");
			dataLineRenewable.add("-");
			dataLinePumpedStorage.add("-");
			dataLinePeakerVoLL.add("-");
		}

		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			dataLineExchange.add(exchange.get(year));
			dataLineRenewable.add(renewable.get(year));
			dataLinePeakerVoLL.add(peakerVoLL.get(year));
			dataLinePumpedStorage.add(pumpedStorage.get(year));
		}

		// write values
		LoggerXLSX.writeLine(logCounter, dataLineExchange);
		LoggerXLSX.writeLine(logCounter, dataLineRenewable);
		LoggerXLSX.writeLine(logCounter, dataLinePumpedStorage);

		LoggerXLSX.writeLine(logCounter, dataLinePeakerVoLL);

		LoggerXLSX.close(logCounter);

	}

	/**
	 * Adjust capacities
	 * 
	 * @param ownerName
	 *            Name of capacity owner
	 * @param plant
	 * 
	 */
	public void removeCapacity(String ownerName, Plant plant, int startYear, int endYear) {

		for (int year = startYear; year <= endYear; year++) {
			final float capacityBefore = capacityPerAgent.get(ownerName).get(year);
			final float capacityNew = capacityBefore - plant.getNetCapacity();
			capacityPerAgent.get(ownerName).put(year, capacityNew);

			boolean removed = false;
			final Iterator<Plant> iter = plantsPerAgent.get(ownerName).get(year).iterator();
			while (iter.hasNext()) {
				final Plant plantList = iter.next();
				if (plant.getUnitID() == plantList.getUnitID()) {
					iter.remove();
					removed = true;
				}
			}
			if (!removed) {
				logger.error(
						"Plant " + plant + " is not available anymore and could not be removed!");
			}

			// if plant is removed from current year, than it cannot run anymore
			if (year == Date.getYear()) {
				allPowerPlantsAvailableInCurrentYear.get(ownerName).remove(plant);
			}
		}

	}

	private void setMarketShare(int year) {

		for (final String ownerName : allPowerPlants.keySet()) {
			float sumInstalledCapacity = 0f;
			plantsPerAgent.get(ownerName).put(year, new ArrayList<>());

			for (final Plant plant : allPowerPlants.get(ownerName)) {
				if (plant.isOperating(year)) {
					sumInstalledCapacity += plant.getNetCapacity();
					plantsPerAgent.get(ownerName).get(year).add(plant);
				}
			}
			capacityPerAgent.get(ownerName).put(year, sumInstalledCapacity);
		}

		// Log market shares in console
		final StringBuffer consoleOutput = new StringBuffer(55);
		consoleOutput.append(marketArea.getInitialsBrackets() + "Market shares (year: " + year
				+ " / yearIndex: " + Date.getYearIndex(year) + ") - ");
		consoleOutput.append("Total capacity: ");
		consoleOutput.append(Statistics.round(getCapacity(year), 2));

		for (final String ownerName : capacityPerAgent.keySet()) {
			consoleOutput.append(" / ");
			consoleOutput.append(ownerName + ": ");
			consoleOutput.append(Statistics.round(capacityPerAgent.get(ownerName).get(year), 2));
		}
		logger.debug(consoleOutput.toString());
	}

	public void setMaxUnitID(int maxUnitID) {
		this.maxUnitID.set(maxUnitID);
	}

	/**
	 * Stores plants which are available in current year in map
	 */
	public void setPlantDataCurrentYear() {

		// Set data for current year (when called at the beginning the
		// simulation) or next year (when called at the beginning of the year)
		int year = Date.getYear();
		if (Date.isLastDayOfYear()) {
			year++;
		}

		// Set available plants
		allPowerPlantsAvailableInCurrentYear = new HashMap<>();
		for (final String ownerName : allPowerPlants.keySet()) {
			allPowerPlantsAvailableInCurrentYear.put(ownerName, new ArrayList<Plant>());
			for (final Plant plant : allPowerPlants.get(ownerName)) {
				if (plant.isOperating(year)) {
					allPowerPlantsAvailableInCurrentYear.get(ownerName).add(plant);
				}
			}
		}
	}

}