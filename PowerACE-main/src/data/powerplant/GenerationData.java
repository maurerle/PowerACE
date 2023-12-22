package data.powerplant;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.carbon.CarbonPrices;
import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.Generator;
import supply.invest.StateStrategic;
import supply.powerplant.CostCap;
import supply.powerplant.Plant;
import supply.powerplant.PlantOption;
import supply.powerplant.technique.EnergyConversion;
import supply.powerplant.technique.Type;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.database.NameTable;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.math.Statistics;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.Unit;

/**
 * This class manages power plant data on an aggregated level for the respective
 * market area, i.e. there is no differentiation by agents. Data is amongst
 * other things required for determining daily available units, price forecast
 * and investment decisions.
 * <p>
 * <b>Merit order:</b> Initial data for the merit order is obtained from the
 * market area's SupplyData object ( {@link #loadMeritOrderUnitsDataAll()}). The
 * merit order is determined for all necessary years. Any investments
 * (construction of new plant, mothballing, ...) trigger an adjustment of all
 * affected merit order lists ( {@link #adjustMeritOrder(Plant)}).
 * <p>
 * <b>Investment options</b> are read at the beginning of the simulation from
 * the database ( {@link #loadCapacityOptionsDataAll()}).
 * <p>
 * <b>Nuclear availability</b>: Daily/monthly factors for the availability of
 * nuclear power plants are read at the beginning of the simulation from the
 * database ( {@link #loadNukeFactor()}).
 */
public final class GenerationData extends Agent implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(GenerationData.class.getName());

	/** That no difference between options and O&M Costs occur */
	private static final boolean useGenericOMCosts = true;

	/**
	 * Cumulate net capacity for a list of merit order units.
	 *
	 * @param meritOrder
	 *            List of merit order units
	 */
	public static void setCumulatedNetCapacity(List<CostCap> meritOrder) {
		if (meritOrder.isEmpty()) {
			return;
		}
		Collections.sort(meritOrder);
		meritOrder.get(0).setCumulatedNetCapacity(meritOrder.get(0).getNetCapacity());
		for (int meritOrderUnitIndex = 1; meritOrderUnitIndex < meritOrder
				.size(); meritOrderUnitIndex++) {
			meritOrder.get(meritOrderUnitIndex)
					.setCumulatedNetCapacity(meritOrder.get(meritOrderUnitIndex).getNetCapacity()
							+ meritOrder.get(meritOrderUnitIndex - 1).getCumulatedNetCapacity());
		}
	}

	/** List of all available power plants */
	private List<CostCap> actualUnits = new ArrayList<>();
	/** List of all capacity options */
	private List<PlantOption> capacityOptionsAll = new ArrayList<>();
	/** Map of capacity options grouped by year of availability */
	private final Map<Integer, List<PlantOption>> capacityOptionsByYear = new ConcurrentHashMap<>();
	/** Map of capacity options grouped by FuelType */
	private final Map<FuelType, List<PlantOption>> capacityOptionsForInvestmentPayment = new HashMap<>();
	private boolean log;
	/** Map of aggregated merit order units grouped by year of availability */
	private final Map<Integer, List<CostCap>> meritOrderAggrByYear = new HashMap<>();
	/** List containing all merit order units from the database */
	private List<CostCap> meritOrderUnitsAll = new ArrayList<>();
	/** Map of merit order units grouped by year of availability */
	private final Map<Integer, List<CostCap>> meritOrderUnitsByYear = new HashMap<>();

	/**
	 * Monthly historical availability of nuclear power plants for all available
	 * years. Is read from database in
	 * {@link data.powerplant.GenerationData#loadNukeFactor()}.
	 */
	private final Map<Integer, Float> nukeMonthlyFactor = new HashMap<>();

	/**
	 * Contains all FuelType, EnergyConvesrion combinations for which a waring
	 * is done
	 */
	private Set<String> warnings = new HashSet<>();

	public GenerationData(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setGenerationData(this);
	}

	/**
	 * Adjust merit order by new plant
	 *
	 * @param plant
	 *            New plant installed
	 */
	public void adjustMeritOrder(Plant plant) {
		// Add new plant to overall merit order list
		final CostCap meritOrderUnit = new CostCap(plant);

		// Costs are set initially to the costs in the current year. In the
		// method setMeritORderByYear(year) the actual yearly costs are updated.
		meritOrderUnit.determineCostsVar(Date.getYear(), marketArea);

		// Add to overall merit order list
		meritOrderUnitsAll.add(meritOrderUnit);

		// Reset merit order for all necessary years
		for (int year = Date.getYear(); year <= Date.getLastDetailedForecastYear(); year++) {
			setMeritOrderByYear(year);
		}
	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	private void checkCapacityOptionInitialization(int year) {
		if (!capacityOptionsByYear.containsKey(year)) {
			setCapacityOptionsByYear(year);
		}
	}

	/**
	 * Create aggregated, monotonously increasing list (only costs and volumes
	 * are stored)
	 */
	private void constructAggrMeritOrder(int year) {

		meritOrderAggrByYear.put(year, new ArrayList<CostCap>());

		float tempTotalNetCapacity = 0;

		// Loop all units
		if (!meritOrderUnitsByYear.get(year).isEmpty()) {
			if (meritOrderUnitsByYear.get(year).size() == 1) {
				meritOrderAggrByYear.get(year).add(meritOrderUnitsByYear.get(year).get(0));
			} else {
				final Comparator<CostCap> compVarCosts = (CostCap c1, CostCap c2) -> Float.compare(
						c1.getCostsVar(year, marketArea), c2.getCostsVar(year, marketArea));
				final List<CostCap> meritOrderSortedByCosts = new ArrayList<>(
						meritOrderUnitsByYear.get(year));
				Collections.sort(meritOrderSortedByCosts, compVarCosts);

				for (int meritOrderUnitIndex = 1; meritOrderUnitIndex < meritOrderSortedByCosts
						.size(); meritOrderUnitIndex++) {
					final CostCap previousMeritOrderUnit = meritOrderSortedByCosts
							.get(meritOrderUnitIndex - 1);
					final CostCap currentMeritOrderUnit = meritOrderSortedByCosts
							.get(meritOrderUnitIndex);

					// Store cumulated net capacity temporarily

					tempTotalNetCapacity += previousMeritOrderUnit.getNetCapacity();

					// Add new step of merit order when costs are different
					if (previousMeritOrderUnit.getCostsVar() != currentMeritOrderUnit
							.getCostsVar()) {
						final CostCap myCostCapTemp = new CostCap();
						myCostCapTemp.setRealPowerPlant(true);
						myCostCapTemp.setVarCostsTotal(previousMeritOrderUnit.getCostsVar());
						myCostCapTemp.setCumulatedNetCapacity(tempTotalNetCapacity);
						meritOrderAggrByYear.get(year).add(myCostCapTemp);
					}

					// Add last unit in merit order
					if (meritOrderUnitIndex == (meritOrderSortedByCosts.size() - 1)) {

						tempTotalNetCapacity += currentMeritOrderUnit.getNetCapacity();

						if (!meritOrderAggrByYear.get(year).isEmpty()) {
							if (previousMeritOrderUnit.getCostsVar() != currentMeritOrderUnit
									.getCostsVar()) {
								final CostCap myCostCapTemp = new CostCap();
								myCostCapTemp.setVarCostsTotal(currentMeritOrderUnit.getCostsVar());
								myCostCapTemp.setCumulatedNetCapacity(tempTotalNetCapacity);
								meritOrderAggrByYear.get(year).add(myCostCapTemp);
							} else {
								meritOrderAggrByYear.get(year)
										.get(meritOrderAggrByYear.get(year).size() - 1)
										.setCumulatedNetCapacity(tempTotalNetCapacity);
							}
						}
					}
				}

				if (!meritOrderAggrByYear.get(year).isEmpty()) {
					if (Math.abs(meritOrderAggrByYear.get(year)
							.get(meritOrderAggrByYear.get(year).size() - 1)
							.getCumulatedNetCapacity()
							- meritOrderUnitsByYear.get(year)
									.get(meritOrderUnitsByYear.get(year).size() - 1)
									.getCumulatedNetCapacity()) > 0.1f) {
						logger.error(
								"Mismatch between total capacity figures in merit order lists! "
										+ meritOrderAggrByYear.get(year)
												.get(meritOrderAggrByYear.get(year).size() - 1)
												.getCumulatedNetCapacity()
										+ ", "
										+ meritOrderUnitsByYear.get(year)
												.get(meritOrderUnitsByYear.get(year).size() - 1)
												.getCumulatedNetCapacity());
					}
				}
			}
		}

	}

	public List<CostCap> getActualUnits() {
		return actualUnits;
	}

	public List<PlantOption> getCapacityOptions(int year) {

		checkCapacityOptionInitialization(year);

		final List<PlantOption> plantOptions = new ArrayList<>();
		// Make deep copy of plant option object
		for (final PlantOption plantOption : capacityOptionsByYear.get(year)) {
			final PlantOption plantOptionCopy = new PlantOption(plantOption);
			// Determine shut down year as if plant would start operation after
			// construction phase (subtract 1 because first and last year are
			// included, respectively)
			plantOptionCopy.setShutDownDate((year + plantOptionCopy.getConstructionTime()
					+ plantOptionCopy.getOperatingLifetime()) - 1);
			plantOptionCopy.initialize(marketArea, year);
			plantOptions.add(plantOptionCopy);
		}
		return plantOptions;
	}

	public List<PlantOption> getCopyOfCapacityOptions(int year) {

		checkCapacityOptionInitialization(year);

		final List<PlantOption> copyCapOpt = new ArrayList<>();
		for (final PlantOption plantOption : capacityOptionsByYear.get(year)) {
			// Copy PlantOption object
			final PlantOption capacityOptionNew = new PlantOption(plantOption);
			capacityOptionNew.initialize(marketArea, year);
			copyCapOpt.add(capacityOptionNew);
		}
		return copyCapOpt;
	}

	public float getInvestmentPayment(int yearOfPlantAvailability, FuelType fuelType,
			EnergyConversion energyConversion) {
		// No warning when fuel type RENEWABLE because not modelled endogenously
		if (fuelType == FuelType.RENEWABLE) {
			return 0f;
		}
		float investmentPayment = -1;
		// Set Investmentcosts
		for (final PlantOption plantOption : capacityOptionsForInvestmentPayment.get(fuelType)) {
			if ((plantOption.getEnergyConversion() == energyConversion)
					&& (yearOfPlantAvailability >= plantOption.getAvailableYear())) {
				investmentPayment = plantOption.getInvestmentPayment();
			}
		}
		// if earlier built use the first option
		if (investmentPayment < 0) {
			for (final PlantOption plantOption : capacityOptionsForInvestmentPayment
					.get(fuelType)) {
				if (plantOption.getEnergyConversion() == energyConversion) {
					return plantOption.getInvestmentPayment();
				}
			}
		}
		// TODO adjust values to own scenario
		if (investmentPayment < 0) {
			final Map<FuelType, Float> investmentPaymentMap = new HashMap<>();
			investmentPaymentMap.put(FuelType.URANIUM, 1270f);
			investmentPaymentMap.put(FuelType.COAL, 1200f);
			investmentPaymentMap.put(FuelType.LIGNITE, 1600f);
			investmentPaymentMap.put(FuelType.OIL, 450f);
			investmentPaymentMap.put(FuelType.GAS, 450f);
			investmentPaymentMap.put(FuelType.CLEAN_COAL, 2600f);
			investmentPaymentMap.put(FuelType.CLEAN_LIGNITE, 2600f);
			investmentPaymentMap.put(FuelType.CLEAN_GAS, 450f);
			final String identifier = fuelType.name() + energyConversion.name();
			// just warn for new FuelType, EnergyConversion combinations
			if (!warnings.contains(identifier)) {
				logger.warn("No Investment payment in Database available for: " + fuelType + ", "
						+ energyConversion);
				warnings.add(identifier);
			}

			if (investmentPaymentMap.keySet().contains(fuelType)) {
				return investmentPaymentMap.get(fuelType);
			} else {
				// if sill not available
				return 0f;
			}
		}

		return investmentPayment;
	}

	/**
	 * Get generation units for specified year
	 *
	 * @param year
	 *            Year for which generation untis are to be returned
	 */
	public List<CostCap> getMeritOrderUnits(int year) {
		return meritOrderUnitsByYear.get(year);
	}

	public float getNukeMonthlyFactor(int year, int month) {
		final int keyMonthly = Date.getKeyMonthly(year, month);
		return nukeMonthlyFactor.get(keyMonthly);
	}

	public List<CostCap> getUnitsAggr() {
		int year = Date.getYear();
		if (Date.isLastDayOfYear()) {
			year++;
		}
		return meritOrderAggrByYear.get(year);
	}

	public List<CostCap> getUnitsAggr(int year) {
		return meritOrderAggrByYear.get(year);
	}

	/**
	 * Initialize generator
	 */
	@Override
	public void initialize() {
		try {
			logger.info(marketArea.getInitialsBrackets() + "Initialize "
					+ GenerationData.class.getSimpleName());

			// Load all necessary data
			loadInitialData();
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

	/**
	 * Set capacity options for specified <code>FuelType</code>, sorted by year
	 * of availability
	 */
	private void initializeCapacityOptionsForInvestmentPayment() {
		for (final FuelType fuelType : FuelType.values()) {
			capacityOptionsForInvestmentPayment.put(fuelType, new ArrayList<>());
			for (final PlantOption capacityOption : capacityOptionsAll) {
				if (fuelType == capacityOption.getFuelType()) {
					capacityOptionsForInvestmentPayment.get(fuelType).add(capacityOption);
				}
			}
			// Sort by year of availability
			Collections.sort(capacityOptionsForInvestmentPayment.get(fuelType),
					(option1, option2) -> Integer.compare(option1.getAvailableYear(),
							option2.getAvailableYear()));
		}
	}

	public boolean isLog() {
		return log;
	}

	/** Load all capacity options from database */
	private List<PlantOption> loadCapacityOptionsDataAll() {
		final List<PlantOption> capacityOptionList = new ArrayList<>();

		// Construct SQL query
		final String tableName = Settings.getTechnologyOptions();
		final String sqlQuery = "SELECT * FROM `" + tableName + "` WHERE `activated`=true";

		// Connect to database and execute query
		// TODO set Database name
		try (final ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED);) {
			conn.execute(sqlQuery);
			// Parse result set
			while (conn.getResultSet().next()) {

				// New CapacityOption object
				final PlantOption capacityOption = new PlantOption();

				// Save data in new object
				capacityOption.setUnitID(conn.getResultSet().getInt("ID"));

				if (conn.resultSetIncludesColumn("tecnology_name")) {
					capacityOption.setName(conn.getResultSet().getString("tecnology_name"));
				} else {
					capacityOption.setName(conn.getResultSet().getString("option_name"));
				}

				capacityOption.setAvailableDate(conn.getResultSet().getInt("availability_year"));
				capacityOption.setShutDownDate(conn.getResultSet().getInt("expire_year"));
				capacityOption.setNetCapacity(conn.getResultSet().getFloat("block_size"));

				if (conn.resultSetIncludesColumn("spec_invest")) {
					capacityOption
							.setInvestmentPayment(conn.getResultSet().getFloat("spec_invest"));
				} else {
					capacityOption.setInvestmentPayment(
							conn.getResultSet().getFloat("investment_specific"));
				}

				if (conn.resultSetIncludesColumn("fixed_costs")) {
					capacityOption.setCostsOperationMaintenanceFixed(
							conn.getResultSet().getFloat("fixed_costs"));
				} else {
					capacityOption.setCostsOperationMaintenanceFixed(
							conn.getResultSet().getFloat("fixed_om_costs"));
				}

				if (conn.resultSetIncludesColumn("var_costs")) {
					capacityOption.setCostsOperationMaintenanceVar(
							conn.getResultSet().getFloat("var_costs"));
				} else {
					capacityOption.setCostsOperationMaintenanceVar(
							conn.getResultSet().getFloat("var_om_costs"));
				}

				if (conn.resultSetIncludesColumn("efficiency")) {
					capacityOption.setEfficiency(conn.getResultSet().getFloat("efficiency") / 100);
				} else {
					capacityOption
							.setEfficiency(conn.getResultSet().getFloat("electrical_efficiency"));
				}

				capacityOption.setOperatingLifetime(conn.getResultSet().getInt("lifetime"));

				if (conn.resultSetIncludesColumn("constructionTime")) {
					capacityOption
							.setConstructionTime(conn.getResultSet().getInt("constructionTime"));
				} else {
					capacityOption
							.setConstructionTime(conn.getResultSet().getInt("construction_time"));
				}

				capacityOption.setStorage(conn.getResultSet().getBoolean("storage"));

				if (conn.resultSetIncludesColumn("fuel")) {
					capacityOption
							.setFuelName(FuelName.getFuelName(conn.getResultSet().getInt("fuel")));
				} else {
					capacityOption.setFuelName(
							FuelName.getFuelName(conn.getResultSet().getInt("fuel_name_index")));
				}

				capacityOption.setOperatingLifetime(conn.getResultSet().getInt("lifetime"));

				if (conn.resultSetIncludesColumn("technologyIndex")) {
					capacityOption.setEnergyConversionIndex(
							conn.getResultSet().getInt("technologyIndex"));
					capacityOption
							.setEnergyConversion(EnergyConversion.getEnergyConversionFromIndex(
									capacityOption.getEnergyConversionIndex()));
				} else {
					capacityOption.setEnergyConversionIndex(
							conn.getResultSet().getInt("technology_Index"));
					capacityOption
							.setEnergyConversion(EnergyConversion.getEnergyConversionFromIndex(
									capacityOption.getEnergyConversionIndex()));
				}
				if (capacityOption.isStorage()) {
					capacityOption
							.setStorageVolume(conn.getResultSet().getFloat("storage_capacity"));
				}
				if (conn.resultSetIncludesColumn("constructionTime")) {
					capacityOption
							.setConstructionTime(conn.getResultSet().getInt("constructionTime"));
				} else {
					capacityOption
							.setConstructionTime(conn.getResultSet().getInt("construction_time"));
				}

				Type.determinePowerPlantCategory(capacityOption);
				// set after plant category is determined
				if (useGenericOMCosts) {
					capacityOption.setCostsOperationMaintenanceFixed(
							marketArea.getOperationMaintenanceCosts().getCostFixed(capacityOption));
				} else if (conn.resultSetIncludesColumn("fixed_costs")) {
					capacityOption.setCostsOperationMaintenanceFixed(
							conn.getResultSet().getFloat("fixed_costs"));
				} else {
					capacityOption.setCostsOperationMaintenanceFixed(
							conn.getResultSet().getFloat("fixed_om_costs"));
				}
				if (useGenericOMCosts) {
					capacityOption.setCostsOperationMaintenanceVar(
							marketArea.getOperationMaintenanceCosts().getCostVar(capacityOption));
				} else if (conn.resultSetIncludesColumn("var_costs")) {
					capacityOption.setCostsOperationMaintenanceVar(
							conn.getResultSet().getFloat("var_costs"));
				} else {
					capacityOption.setCostsOperationMaintenanceVar(
							conn.getResultSet().getFloat("var_om_costs"));
				}

				// Add new object to list
				capacityOptionList.add(capacityOption);
			}
		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		}
		return capacityOptionList;
	}

	/**
	 * Loads all merit order units and capacity options at the beginning of the
	 * simulation
	 */
	private void loadInitialData() {

		// Load capacity options
		capacityOptionsAll = loadCapacityOptionsDataAll();
		logger.info(marketArea.getInitialsBrackets() + "Load investment options finished");

		// Initialize map with all FuelTypes
		initializeCapacityOptionsForInvestmentPayment();

		/** Load power plant data */
		loadMeritOrderUnitsDataAll();

		// Set merit order for all necessary years
		final int startYear = Date.getStartYear();
		for (int year = startYear; year <= Date.getLastDetailedForecastYear(); year++) {
			setMeritOrderByYear(year);
		}
		logger.debug(marketArea.getInitialsBrackets()
				+ "Import merit order units from SupplyData finished");

		// Load availability factor of nuclear plants if available
		if (!"NA".equals(marketArea.getNukeAvailFactor())) {
			loadNukeFactor();
			logger.debug(marketArea.getInitialsBrackets()
					+ "Load availability factor of nuclear plants finished");
		}

		logger.info(
				marketArea.getInitialsBrackets() + "Load capacity options from database finished");

	}

	/** Get all merit order units from SupplyData */
	public void loadMeritOrderUnitsDataAll() {
		// Get power plant data from SupplyData object of current market area
		final Map<String, List<Plant>> allPowerPlants = marketArea.getSupplyData().getPowerPlants(
				Date.getYear(), new HashSet<>(Arrays.asList(StateStrategic.values())));

		meritOrderUnitsAll = new ArrayList<>();

		for (final String ownerName : allPowerPlants.keySet()) {
			for (final Plant plant : allPowerPlants.get(ownerName)) {

				// Set investment payment
				plant.setInvestmentPayment(marketArea.getGenerationData().getInvestmentPayment(
						plant.getAvailableYear(), plant.getFuelType(),
						plant.getEnergyConversion()));

				// Create new merit order unit from plant
				final CostCap myCostCap = new CostCap(plant);

				// Costs are set initially to the costs in the start
				// year.
				// In the method setMeritORderByYear(year) the actual
				// yearly costs are updated.
				myCostCap.determineCostsVar(Date.getYear(), marketArea);

				// Add to overall merit order list
				meritOrderUnitsAll.add(myCostCap);
			}

		}
		Collections.sort(meritOrderUnitsAll);
	}

	/** Load nuclear availabilitiy */
	private void loadNukeFactor() {
		final String sqlQuery;

		if ("".equals(marketArea.getNukeAvailFactor())) {
			return;
		}

		try {
			logger.debug(marketArea.getInitialsBrackets()
					+ "Reading monthly availability of nuclear power plants");
			ConnectionSQL conn;
			String tableName;

			tableName = NameTable.EXAMPLE.getTableName();
			conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED, marketArea);

			sqlQuery = "SELECT * FROM `" + tableName + "`";
			conn.setResultSet(sqlQuery);

			// Number of columns
			final int columnCount = conn.getResultSet().getMetaData().getColumnCount();

			// Loop all columns
			for (int columnCounter = 2; columnCounter <= columnCount; columnCounter++) {
				final String columnName = conn.getResultSet().getMetaData()
						.getColumnName(columnCounter);
				// Catch non-parsable integer
				try {
					if ((Date.getStartYear() <= Integer.parseInt(columnName))) {
						final int year = Integer.parseInt(columnName);
						while (conn.getResultSet().next()) {
							nukeMonthlyFactor.put(
									Date.getKeyMonthly(year, conn.getResultSet().getInt("month")),
									conn.getResultSet().getFloat(columnName));
						}
						// Reset cursor
						conn.getResultSet().beforeFirst();

					}
				} catch (final NumberFormatException e) {
					continue;
				}
			}
			conn.close();
		} catch (final SQLException e) {
			logger.error("SQLException", e);
		}
	}

	public void logMeritOrder() {

		// Initialize log file
		final String fileName = marketArea.getInitialsUnderscore() + "MeritOrder_" + Date.getYear();
		final String description = "Logging of Merit Order";
		final List<ColumnHeader> titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("block_number", Unit.NONE));
		titleLine.add(new ColumnHeader("cap", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("cost", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("unit_name", Unit.NONE));
		titleLine.add(new ColumnHeader("owner", Unit.NONE));
		titleLine.add(new ColumnHeader("unit_capacity", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("efficiency", Unit.NONE));
		titleLine.add(new ColumnHeader("fuel", Unit.NONE));
		titleLine.add(new ColumnHeader("fuel_price", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("fuel_costs_var", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("co2_price", Unit.NONE));
		titleLine.add(new ColumnHeader("co2_emissionfactor", Unit.EMISSION_FACTOR));
		titleLine.add(new ColumnHeader("co2_costs_var", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("other_var_costs", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("startup_costs_cold", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("startup_costs_warm", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("startup_costs_hot", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("startup_fuel_factor", Unit.NONE));
		titleLine.add(new ColumnHeader("startup_fuel_cost", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("startup_deprecation_cost", Unit.CAPACITY_PRICE));
		final Folder logFolder = Folder.GENERATOR;
		// Frequency is not really hourly but doing it this way provides enough
		// rows
		final Frequency frequency = Frequency.HOURLY;
		final int logIDMeritOrderXLSX = LoggerXLSX.newLogObject(logFolder, fileName, description,
				titleLine, marketArea.getIdentityAndNameLong(), frequency);

		// Get units
		final List<Plant> units = new ArrayList<>();
		for (final Generator generator : marketArea.getGenerators()) {
			units.addAll(generator.getPowerPlantsList());
		}
		Collections.sort(units);

		// Loop all merit order units
		float totalCapacity = 0f;
		for (final Plant unit : units) {
			final List<Object> values = new ArrayList<>();
			totalCapacity += unit.getNetCapacity();

			// Add log values to list
			values.add(unit.getUnitID());
			values.add(totalCapacity);
			values.add(unit.getCostsVar());
			values.add(unit.getName());
			values.add(unit.getOwnerName());
			values.add(unit.getNetCapacity());
			values.add(unit.getEfficiency());
			values.add(unit.getFuelName());

			values.add(unit.getCostsFuelVar());
			values.add(CarbonPrices.getPricesDaily(marketArea));
			values.add(unit.getPlantEmissionFactor(true));
			values.add(unit.getCostsCarbonVar());
			values.add(unit.getCostsOperationMaintenanceVar());
			values.add(marketArea.getStartUpCosts().getMarginalStartupCostsCold(unit));
			values.add(marketArea.getStartUpCosts().getMarginalStartupCostsWarm(unit));
			values.add(marketArea.getStartUpCosts().getMarginalStartupCostsHot(unit));
			values.add(marketArea.getStartUpCosts().getFuelFactor(unit));
			values.add(marketArea.getStartUpCosts().getFuelFactor(unit)
					* (marketArea.getFuelPrices().getPricesDaily(unit.getFuelName())
							+ (CarbonPrices.getPricesDaily(marketArea)
									* unit.getPlantEmissionFactor(false))));
			values.add(marketArea.getStartUpCosts().getDepreciationCosts(unit));

			// Write values to log object
			LoggerXLSX.writeLine(logIDMeritOrderXLSX, values);
		}
		// Write data to file (output stream)
		LoggerXLSX.close(logIDMeritOrderXLSX);
	}

	/**
	 * Adjust merit order by removing plant.
	 *
	 * @param plant
	 *            plant that is removed
	 */
	public void removeCapacity(Plant plant, int startYear, int endYear) {

		for (int index = 0; index < meritOrderUnitsAll.size(); index++) {
			if (meritOrderUnitsAll.get(index).getUnitID() == plant.getUnitID()) {
				meritOrderUnitsAll.remove(index);
				break;
			}
		}

		// Reset merit order for all necessary years
		for (int year = startYear; year <= endYear; year++) {
			setMeritOrderByYear(year, false);
		}
	}

	/**
	 * Daily update of power plant data (e.g. daily fuel prices)<br>
	 * <br>
	 * Used only for price forecast!
	 *
	 * @param day
	 */
	public void setActualUnits(int day) {

		final int year = Date.getYear();
		actualUnits = new ArrayList<>();

		for (final CostCap meritOrderUnitOrg : meritOrderUnitsByYear.get(year)) {
			final CostCap meritOrderUnit = new CostCap(meritOrderUnitOrg);

			if (meritOrderUnitOrg.getFuelName() == FuelName.URANIUM) {

				meritOrderUnit.setNetCapacity(meritOrderUnit.getNetCapacity()
						* getNukeMonthlyFactor(year, Date.getMonth()));

			}
			// other fuels

			actualUnits.add(meritOrderUnit);
		}

		// Sort units
		Collections.sort(actualUnits);

		// Cumulate net capacity
		setCumulatedNetCapacity(actualUnits);

	}

	/** Set capacity options for specified <code>year</code> */
	private void setCapacityOptionsByYear(int year) {
		capacityOptionsByYear.put(year, new ArrayList<PlantOption>());
		for (final PlantOption capacityOption : capacityOptionsAll) {
			final int waitingTime = capacityOption.getConstructionTime();
			// Add plant option if after accounting for construction time year
			// of availability is reached
			if (capacityOption.isAvailableTechnically(year + waitingTime)) {
				capacityOptionsByYear.get(year).add(capacityOption);
			}
		}
	}

	public void setLog(boolean log) {
		this.log = log;
	}

	private void setMeritOrderByYear(int year) {
		setMeritOrderByYear(year, true);
	}

	/** Set merit order units for specified <code>year</code> */
	private void setMeritOrderByYear(int year, boolean verbose) {

		// Create and fill list of merit order units
		meritOrderUnitsByYear.put(year, new ArrayList<CostCap>());
		for (final CostCap meritOrderUnit : meritOrderUnitsAll) {
			if (meritOrderUnit.isAvailableTechnically(year) && (meritOrderUnit.getState(year)
					.getAttributeStateStrategic() != StateStrategic.DECOMMISSIONED)) {
				// Make (deep) copy of merit order unit
				// Deep copying is currently made in order to have
				// own objects per year which allows sorting the lists and
				// set the total capacity. However, it comes at the cost of
				// instantiating new objects.
				meritOrderUnitsByYear.get(year).add(new CostCap(meritOrderUnit));
			}
		}

		// Determine different Cost parameters
		// Only for year, when merit Order is not empty
		if (!meritOrderUnitsByYear.get(year).isEmpty()) {
			for (final CostCap meritOrderUnit : meritOrderUnitsByYear.get(year)) {
				meritOrderUnit.determineCostsVar(year, marketArea);
			}

			// Sort (ascending by variable costs)
			Collections.sort(meritOrderUnitsByYear.get(year));

			// Cumulate net capacity
			setCumulatedNetCapacity(meritOrderUnitsByYear.get(year));

			if (verbose) {
				// Log total net capacity for current year
				final float totalNetCapacity = meritOrderUnitsByYear.get(year)
						.get(meritOrderUnitsByYear.get(year).size() - 1).getCumulatedNetCapacity();
				if (Date.isFirstDay()) {
					logger.debug(marketArea.getInitialsBrackets() + "Total net capacity for year "
							+ year + ": " + Statistics.round(totalNetCapacity, 2) + " MW");
				}

				final float diffTotalNetCapacity = totalNetCapacity
						- marketArea.getSupplyData().getCapacity(year);
				if (Math.abs(diffTotalNetCapacity) > 1) {
					logger.error("Difference (" + diffTotalNetCapacity
							+ " MW) in total net capacity between SupplyData and GenerationData in year "
							+ year);

					// Check if there is a plant missing
					final List<Plant> plantSupplyData = marketArea.getSupplyData()
							.getPowerPlantsAsList(year,
									new HashSet<>(Arrays.asList(StateStrategic.values())));
					for (final Plant plant : plantSupplyData) {
						boolean matchFound = false;
						for (final CostCap meritOrderUnit : meritOrderUnitsByYear.get(year)) {
							if ((meritOrderUnit.getUnitID() == plant.getUnitID())
									&& (Math.abs(meritOrderUnit.getNetCapacity()
											- plant.getNetCapacity()) < 0.01f)) {
								matchFound = true;
								break;
							}
						}

						if (!matchFound) {
							logger.error(year + " no match found for " + plant
									+ " based on unit id and net capacity.");
						}
					}

					for (final CostCap meritOrderUnit : meritOrderUnitsByYear.get(year)) {
						boolean matchFound = false;
						for (final Plant plant : plantSupplyData) {
							if ((meritOrderUnit.getUnitID() == plant.getUnitID())
									&& (Math.abs(meritOrderUnit.getNetCapacity()
											- plant.getNetCapacity()) < 0.01f)) {
								matchFound = true;
								break;
							}

						}
						if (!matchFound) {
							logger.error("No match found for " + meritOrderUnit
									+ " based on unit id and net capacity.");
						}
					}
				}
			}

			// Construct aggregated merit order
			constructAggrMeritOrder(year);
		}

	}

}
