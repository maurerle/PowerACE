package data.powerplant.costs;

import static simulations.scheduling.Date.HOT_STARTUP_LENGTH;
import static simulations.scheduling.Date.WARM_STARTUP_LENGTH;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.carbon.CarbonPrices;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.Plant;
import supply.powerplant.PlantAbstract;
import supply.powerplant.PlantOption;
import supply.powerplant.technique.Type;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.database.NameTable;
import tools.types.Startup;

/**
 * Utility class that loads and calculates the startup costs for power plants.
 * 
 * 
 * 
 */
public final class StartupCost implements Callable<Void> {

	/** Factor to convert from cold to hot start-up costs, see Grimm (2007) */
	private static float coldToHotFactor = 0.3f;
	/** Factor to convert from cold to warm start-up costs, see Grimm (2007) */
	private static float coldToWarmFactor = 0.5f;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(StartupCost.class.getName());

	public static float getColdToHotFactor() {
		return coldToHotFactor;
	}

	public static float getColdToWarmFactor() {
		return coldToWarmFactor;
	}

	/** Deprecation costs in Euro/MW */
	private Map<Type, Float> depreciationCosts;
	/** Fuel factor in MWh_th/MW */
	private Map<Type, Float> fuelFactor;

	private final MarketArea marketArea;

	public StartupCost(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setStartupCosts(this);
	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	/**
	 * Calculates the total costs from the marginal costs.
	 * 
	 * @param plant
	 * @param costs
	 * @return
	 */
	private float convertMarginalToTotalCosts(PlantAbstract plant, float costs) {
		return plant.getNetCapacity() * costs;
	}

	/**
	 * @param plant
	 * @param startup
	 * @return The carbon emissions do to start-up.
	 */

	public float getCarbonEmissions(Plant plant, Startup startup) {

		final Type category = plant.getCategory();
		// For the carbon emissions of the startup include sequestration but not
		// efficiency since for heating generator efficiency does not matter
		final float emissionFactor = plant.getPlantEmissionFactor(false);
		final float emissions = fuelFactor.get(category) * emissionFactor;

		switch (startup) {
			case HOT:
				return emissions * coldToHotFactor;
			case WARM:
				return emissions * coldToWarmFactor;
			case COLD:
				return emissions;
			default:
				logger.error("Startup type undefined");
				return 0f;
		}
	}

	public float getDepreciationCosts(PlantAbstract plant) {
		return depreciationCosts.get(plant.getCategory());
	}

	public float getFuelFactor(PlantAbstract plant) {
		return fuelFactor.get(plant.getCategory());
	}

	/**
	 * Based on the running hours of the last days the startup costs for the
	 * first hour of today are calculated.
	 * <P>
	 * <i>No </i>startup costs if plant was running in the last hour of
	 * yesterday. <i>Hot </i>startup if plant was running in the last
	 * HOT_STARTUP_LENGTH hours of yesterday. <i>Warm</i> startup if the plant
	 * was running not running in the last HOT_STARTUP_LENGTH hours of
	 * yesterday, but in the last WARM_STARTUP_LENGTH hours. <i>Cold</i> startup
	 * elsewise.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * 
	 * @return The startup costs in Euro/MW.
	 */
	public float getMarginalStartUpCosts(Plant plant) {
		return getMarginalStartUpCosts(plant, 0);
	}

	/**
	 * Based on the running hours of the last days and today the startup costs
	 * for today are calculated.
	 * <P>
	 * <i>No </i>startup costs if plant was running in the last hour of
	 * yesterday. <i>Hot </i>startup if plant was running in the last
	 * HOT_STARTUP_LENGTH hours of yesterday. <i>Warm</i> startup if the plant
	 * was running not running in the last HOT_STARTUP_LENGTH hours of
	 * yesterday, but in the last WARM_STARTUP_LENGTH hours. <i>Cold</i> startup
	 * elsewise.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @param hourOfDay
	 *            The hour of the current day. [0, 23]
	 * 
	 * @return The startup costs in Euro/MW.
	 */
	public float getMarginalStartUpCosts(Plant plant, int hourOfDay) {
		final float costs;
		if (plant.isRunningRange(-1 + hourOfDay, -1 + hourOfDay)) {
			costs = 0f;
		} else if (plant.isRunningRange(-HOT_STARTUP_LENGTH + hourOfDay, -2 + hourOfDay)) {
			costs = getMarginalStartupCostsHot(plant);
		} else if (plant.isRunningRange(-WARM_STARTUP_LENGTH + hourOfDay,
				-(HOT_STARTUP_LENGTH + 1) + hourOfDay)) {
			costs = getMarginalStartupCostsWarm(plant);
		} else {
			costs = getMarginalStartupCostsCold(plant);
		}

		return costs;
	}

	/**
	 * Returns the <i>cold</i> marginal startup costs of a power plant for the
	 * <i>current</i> day of the current year. Use daily prices for coal, oil
	 * and gas, yearly prices elsewise. Startup costs are made out of two
	 * components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 */
	public float getMarginalStartupCostsCold(PlantAbstract plant) {

		// Same as getMarginalStartupCostsCold(PlantAbstract plant, int year,
		// int day) but quicker acess to

		// deprecation costs
		final Type category = plant.getCategory();
		final float deprecationCost = depreciationCosts.get(category);

		// fuel costs
		final float fuelPrice = marketArea.getFuelPrices().getPricesDaily(plant.getFuelName());
		final float emissionPrice = CarbonPrices.getPricesDaily(marketArea);
		// For the carbon emissions of the startup include sequestration but not
		// efficiency since for heating generator efficiency does not matter
		final float emissionFactor = plant.getPlantEmissionFactor(false);
		final float fuelCost = fuelFactor.get(category)
				* (fuelPrice + (emissionPrice * emissionFactor));

		return deprecationCost + fuelCost;
	}

	/**
	 * Returns the <i>cold</i> marginal startup costs of a power plant for the
	 * <i>requested</i> day of the current year. Use daily prices for coal, oil
	 * and gas, yearly prices elsewise. Startup costs are made out of two
	 * components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @param day
	 *            in day of the year format, e.g. 1-365
	 */
	public float getMarginalStartupCostsCold(PlantAbstract plant, int day) {
		return getMarginalStartupCostsCold(plant, Date.getYear(), day);
	}

	/**
	 * Returns the <i>cold</i> marginal startup costs of a power plant for the
	 * <i>requested</i> day and year. Use daily prices for coal, oil and gas,
	 * yearly prices elsewise. Startup costs are made out of two components,
	 * additional fuel costs and deprecation costs.
	 * 
	 * <p>
	 * Fuel costs are stated in <code>Euro/Î”MW_el</code>, deprecation costs are
	 * stated in <code>Euro/Î”MW_el</code>. In order to receive the the
	 * <b>total</b> costs for a full start, costs have to <b>multiplied with
	 * capacity</b>.
	 * 
	 * <p>
	 * The parameter fuel costs and deprecation costs are given in MWh/MW and
	 * Euro/MW respectively. *
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @param year
	 *            in yyyy format, e.g. 2014
	 * @param day
	 *            in day of the year format, e.g. 1-365
	 * 
	 * @return costs in Euro/MW
	 */
	public float getMarginalStartupCostsCold(PlantAbstract plant, int year, int day) {

		// deprecation costs
		final Type category = plant.getCategory();
		final float deprecationCost = depreciationCosts.get(category);

		// fuel costs
		final float fuelPrice = marketArea.getFuelPrices().getPricesDaily(plant.getFuelName(), year,
				day);
		final float emissionPrice = CarbonPrices.getPricesDaily(marketArea);
		// For the carbon emissions of the startup include sequestration but not
		// efficiency since for heating generator efficiency does not matter
		final float emissionFactor = plant.getPlantEmissionFactor(false);
		final float fuelCost = fuelFactor.get(category)
				* (fuelPrice + (emissionPrice * emissionFactor));

		return deprecationCost + fuelCost;
	}

	/**
	 * Returns the <i>cold</i> marginal startup costs of a Tecnology Option for
	 * the <i>requested</i> year. Use yearly prices for coal, oil and gas, and
	 * CO2. Startup costs are made out of two components, additional fuel costs
	 * and deprecation costs.
	 * 
	 * <p>
	 * Fuel costs are stated in <code>Euro/Î”MW_el</code>, deprecation costs are
	 * stated in <code>Euro/Î”MW_el</code>. In order to receive the the
	 * <b>total</b> costs for a full start, costs have to <b>multiplied with
	 * capacity</b>.
	 * 
	 * <p>
	 * The parameter fuel costs and deprecation costs are given in MWh/MW and
	 * Euro/MW respectively. *
	 * 
	 * @param plantOption
	 *            The tecnology Option for which the startup costs are
	 *            calculated.
	 * @param year
	 *            in yyyy format, e.g. 2014
	 * 
	 * @return costs in Euro/MW
	 */

	public float getMarginalStartupCostsCold(PlantOption plantOption, int year) {
		// deprecation costs
		final Type category = plantOption.getCategory();
		final float deprecationCost = depreciationCosts.get(category);

		// fuel costs
		final float fuelPrice = marketArea.getFuelPrices()
				.getPricesYearly(plantOption.getFuelName());
		final float emissionPrice = CarbonPrices.getPricesDaily(marketArea);
		// For the carbon emissions of the startup include sequestration but not
		// efficiency since for heating generator efficiency does not matter
		final float emissionFactor = plantOption.getPlantEmissionFactor(false);
		final float fuelCost = fuelFactor.get(category)
				* (fuelPrice + (emissionPrice * emissionFactor));

		return deprecationCost + fuelCost;
	}

	public float getMarginalStartUpCostsFromHoursNotRunning(Plant plant, int hoursNotRunning) {
		final float costs;
		if (hoursNotRunning <= 0) {
			costs = 0f;
		} else if (hoursNotRunning <= HOT_STARTUP_LENGTH) {
			costs = getMarginalStartupCostsHot(plant);
		} else if (hoursNotRunning <= WARM_STARTUP_LENGTH) {
			costs = getMarginalStartupCostsWarm(plant);
		} else {
			costs = getMarginalStartupCostsCold(plant);
		}

		return costs;
	}

	/**
	 * Returns the <i>hot</i> marginal startup costs of a power plant for the
	 * <i>current</i> day and <i>current</i> year. Use daily prices for coal,
	 * oil and gas, yearly prices elsewise. Startup costs are made out of two
	 * components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @return costs in Euro/MW
	 */
	public float getMarginalStartupCostsHot(PlantAbstract plant) {
		return getMarginalStartupCostsCold(plant) * coldToHotFactor;
	}

	/**
	 * Returns the <i>hot</i> marginal startup costs of a power plant for the
	 * <i>requested</i> day of the <i>current</i> year. Use daily prices for
	 * coal, oil and gas, yearly prices elsewise. Startup costs are made out of
	 * two components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @param day
	 *            in day of the year format, e.g. 1-365
	 * @return costs in Euro/MW
	 */
	public float getMarginalStartupCostsHot(PlantAbstract plant, int day) {
		return getMarginalStartupCostsCold(plant, day) * coldToHotFactor;
	}

	/**
	 * Returns the <i>hot</i> marginal startup costs of a Tecnology Option for
	 * the <i>requested</i> year. Use yearly prices for coal, oil and gas and
	 * CO2. Startup costs are made out of two components, additional fuel costs
	 * and deprecation costs.
	 * 
	 * @param plantOption
	 *            The Tecnology Option for which the startup costs are
	 *            calculated.
	 * @param year
	 *            for the requested year in yyyy format, e.g. 2014
	 * @return costs in Euro/MW
	 */

	public float getMarginalStartupCostsHot(PlantOption plantOption, int year) {
		return getMarginalStartupCostsCold(plantOption, year) * coldToHotFactor;
	}

	/**
	 * Returns the <i>warm</i> marginal startup costs of a power plant for the
	 * <i>current</i> day of the <i>current</i> year. Use daily prices for coal,
	 * oil and gas, yearly prices elsewise. Startup costs are made out of two
	 * components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @return costs in Euro/MW
	 */
	public float getMarginalStartupCostsWarm(PlantAbstract plant) {
		return getMarginalStartupCostsCold(plant) * coldToWarmFactor;
	}

	/**
	 * Returns the <i>warm</i> marginal startup costs of a power plant for the
	 * <i>requested</i> day of the <i>current</i> year. Use daily prices for
	 * coal, oil and gas, yearly prices elsewise. Startup costs are made out of
	 * two components, additional fuel costs and deprecation costs.
	 * 
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @param day
	 *            in day of the year format, e.g. 1-365
	 * @return costs in Euro/MW
	 */
	public float getMarginalStartupCostsWarm(PlantAbstract plant, int day) {
		return getMarginalStartupCostsCold(plant, day) * coldToWarmFactor;
	}

	/**
	 * Returns the <i>warm</i> marginal startup costs of a tecnology option for
	 * the <i>requested</i> year. Use yearly prices for coal, oil and gas and
	 * CO2. Startup costs are made out of two components, additional fuel costs
	 * and deprecation costs.
	 * 
	 * 
	 * @param plantOption
	 *            The tecnology option for which the startup costs are
	 *            calculated.
	 * @param day
	 *            in requested year in yyyy format, e.g. 2014
	 * @return costs in Euro/MW
	 */

	public float getMarginalStartupCostsWarm(PlantOption plantOption, int year) {
		return getMarginalStartupCostsCold(plantOption, year) * coldToWarmFactor;
	}

	/**
	 * Based on the running hours of the last days the startup costs for today
	 * are calculated.
	 * <P>
	 * <i>No </i>startup costs if plant was running in the last hour of
	 * yesterday. <i>Hot </i>startup if plant was running in the last
	 * HOT_STARTUP_LENGTH hours of yesterday. <i>Warm</i> startup if the plant
	 * was running not running in the last HOT_STARTUP_LENGTH hours of
	 * yesterday, but in the last WARM_STARTUP_LENGTH hours. <i>Cold</i> startup
	 * elsewise.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * 
	 * @return The startup costs in Euro/MW.
	 */
	public float getTotalStartUpCosts(Plant plant) {
		return convertMarginalToTotalCosts(plant, getMarginalStartUpCosts(plant));
	}

	/**
	 * Based on the running hours of the last days and today the total startup
	 * costs for the parameter hour are calculated.
	 * <P>
	 * <i>No </i>startup costs if plant was running in the last hour of
	 * yesterday. <i>Hot </i>startup if plant was running in the last
	 * HOT_STARTUP_LENGTH hours of yesterday. <i>Warm</i> startup if the plant
	 * was running not running in the last HOT_STARTUP_LENGTH hours of
	 * yesterday, but in the last WARM_STARTUP_LENGTH hours. <i>Cold</i> startup
	 * elsewise.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @param hourOfDay
	 *            The hour of the current day. [0, 23]
	 * 
	 * @return The total startup costs in Euro/MW.
	 */
	public float getTotalStartUpCosts(Plant plant, int hourOfDay) {
		final float costs;
		if (plant.isRunningRange(-1 + hourOfDay, -1 + hourOfDay)) {
			costs = 0f;
		} else if (plant.isRunningRange(-HOT_STARTUP_LENGTH + hourOfDay, -2 + hourOfDay)) {
			costs = getTotalStartupCostsHot(plant);
		} else if (plant.isRunningRange(-WARM_STARTUP_LENGTH + hourOfDay,
				-(HOT_STARTUP_LENGTH + 1) + hourOfDay)) {
			costs = getTotalStartupCostsWarm(plant);
		} else {
			costs = getTotalStartupCostsCold(plant);
		}

		return costs;
	}

	/**
	 * Based on then given length total startup costs for plant are returned.
	 * <P>
	 * <i>No </i>startup costs if plant was running in the last hour of
	 * yesterday. <i>Hot </i>startup if plant was running in the last
	 * HOT_STARTUP_LENGTH hours of yesterday. <i>Warm</i> startup if the plant
	 * was running not running in the last HOT_STARTUP_LENGTH hours of
	 * yesterday, but in the last WARM_STARTUP_LENGTH hours. <i>Cold</i> startup
	 * elsewise.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * @param length
	 *            The length in hours the plant is out of the market.
	 * 
	 * @return The total startup costs in Euro/MW.
	 */
	public float getTotalStartUpCostsByLength(PlantAbstract plant, int length) {
		final float costs;

		if (length <= 0) {
			costs = 0f;
		} else if (length <= HOT_STARTUP_LENGTH) {
			costs = getTotalStartupCostsHot(plant);
		} else if (length <= WARM_STARTUP_LENGTH) {
			costs = getTotalStartupCostsWarm(plant);
		} else {
			costs = getTotalStartupCostsCold(plant);
		}

		return costs;
	}

	/**
	 * Returns the <i>cold</i> total startup costs of a power plant for the
	 * <i>current</i> day of the current year. Use daily prices for coal, oil
	 * and gas, yearly prices elsewise. Startup costs are made out of two
	 * components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * 
	 * @return costs in Euro
	 */
	public float getTotalStartupCostsCold(PlantAbstract plant) {
		return convertMarginalToTotalCosts(plant, getMarginalStartupCostsCold(plant));
	}

	/**
	 * Returns the <i>hot</i> total startup costs of a power plant for the
	 * <i>current</i> day and <i>current</i> year. Use daily prices for coal,
	 * oil and gas, yearly prices elsewise. Startup costs are made out of two
	 * components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * 
	 * @return costs in Euro
	 */
	public float getTotalStartupCostsHot(PlantAbstract plant) {
		return convertMarginalToTotalCosts(plant, getMarginalStartupCostsHot(plant));
	}

	/**
	 * Returns the <i>warm</i> total startup costs of a power plant for the
	 * <i>current</i> day of the <i>current</i> year. Use daily prices for coal,
	 * oil and gas, yearly prices elsewise. Startup costs are made out of two
	 * components, additional fuel costs and deprecation costs.
	 * 
	 * @param plant
	 *            The power plant for which the startup costs are calculated.
	 * 
	 * @return costs in Euro
	 * 
	 */
	public float getTotalStartupCostsWarm(PlantAbstract plant) {
		return convertMarginalToTotalCosts(plant, getMarginalStartupCostsWarm(plant));
	}

	/** Loads startup costs */
	private void initialize() {

		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ StartupCost.class.getSimpleName());

		try {
			// Initialize lists
			depreciationCosts = new HashMap<>(Type.values().length);
			fuelFactor = new HashMap<>(Type.values().length);
			ConnectionSQL connector;
			String tableName;

			// Set Name of Database and Tablename
			tableName = NameTable.EXAMPLE.getTableName();
			connector = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED);

			final String sqlQuery = "SELECT * FROM `" + tableName + "` WHERE `scenario`='"
					+ Settings.getStartupCostsScenario() + "'";
			connector.setResultSet(sqlQuery);
			connector.getResultSet().next();

			for (final Type powerPlantCategory : Type.values()) {

				// set deprecation
				depreciationCosts.put(powerPlantCategory,
						connector.getResultSet().getFloat(powerPlantCategory + "_Depreciation"));

				// set fuel factor
				fuelFactor.put(powerPlantCategory,
						connector.getResultSet().getFloat(powerPlantCategory + "_FuelFactor"));

			}

			connector.close();

		} catch (final SQLException e) {
			logger.error("SQLException", e);
		}
	}

}