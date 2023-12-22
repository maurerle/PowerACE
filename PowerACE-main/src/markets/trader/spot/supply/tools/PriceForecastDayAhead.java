package markets.trader.spot.supply.tools;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.fuel.FuelPrices;
import data.powerplant.costs.StartupCost;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.Plant;
import supply.powerplant.technique.Type;
import supply.scenarios.ScenarioList;
import tools.Sorting;
import tools.logging.Folder;
import tools.logging.LoggerCSV;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * Based on the intermittent feed-in from wind/pv, the demand, a small
 * Monte-Carlo-simulation for the day-ahead electricity price is carried out
 * here.
 *
 * Price is determined by power plant with highest variable costs that is still
 * required to run to satisfy demand. Price is then equal to variable costs and
 * startup costs/length of running time of that plant.
 *
 * 
 */

public class PriceForecastDayAhead {

	/**
	 * Logs the outcome of the forecast in one class.
	 *
	 * 
	 *
	 */
	public class ForecastOutcome {

		/**
		 * The total demand for each hour.
		 */
		private final Map<Integer, Float> demand = new HashMap<>();
		/**
		 * The production for each fuel and each hour of the day.
		 */
		private final Map<Integer, Map<FuelType, Float>> fuel = new HashMap<>();
		/**
		 * The index of the last power plant in the plant list that is producing
		 * for each each.
		 */
		private final Map<Integer, Integer> lastIndices = new HashMap<>();
		/**
		 * The name of the scenario.
		 */
		private String name;
		/**
		 * The plants that satisfy the demand.
		 */
		private List<Plant> plants = new ArrayList<>();
		/**
		 * The price for each hour.
		 */
		private final Map<Integer, Float> price = new HashMap<>();
		/**
		 * The total renewable load for each hour.
		 */
		private final Map<Integer, Float> renewableTotal = new HashMap<>();
		/**
		 * The total demand for each hour.
		 */
		private final Map<Integer, Float> resLoad = new HashMap<>();

		/**
		 * The startUpCosts for each hour.
		 */
		private final Map<Integer, Float> startUpCosts = new HashMap<>();

		public ForecastOutcome(String name) {
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				fuel.put(hour, new HashMap<FuelType, Float>());
			}
			this.name = name;
		}

		public Map<Integer, Float> getDemand() {
			return demand;
		}

		/**
		 * @param hour
		 *            [0,HOURS_PER_DAY]
		 * @return
		 */
		public Float getDemand(int hour) {
			return demand.get(hour);
		}

		public Map<Integer, Map<FuelType, Float>> getFuel() {
			return fuel;
		}

		/**
		 * @param hour
		 *            [0,HOURS_PER_DAY]
		 * @return
		 */
		public Integer getLastIndex(int hour) {
			return lastIndices.get(hour);
		}

		/**
		 *
		 * @return Index gives the last plant that was running based on var
		 *         costs.
		 */
		public Map<Integer, Integer> getLastIndices() {
			return lastIndices;
		}

		public String getName() {
			return name;
		}

		public List<Plant> getPlants() {
			return plants;
		}

		public Map<Integer, Float> getPrice() {
			return price;
		}

		/**
		 * @param hour
		 *            [0,HOURS_PER_DAY]
		 * @return
		 */
		public Float getPrice(int hour) {
			return price.get(hour);
		}

		public Map<Integer, Float> getRenewableTotal() {
			return renewableTotal;
		}

		/**
		 * @param hour
		 *            [0,HOURS_PER_DAY]
		 * @return
		 */
		public Float getRenewableTotal(int hour) {
			return renewableTotal.get(hour);
		}

		public Map<Integer, Float> getResLoad() {
			return resLoad;
		}

		public Map<Integer, Float> getStartUpCosts() {
			return startUpCosts;
		}

		/**
		 * @param hour
		 *            [0,HOURS_PER_DAY]
		 * @return
		 */
		public Float getStartUpCosts(int hour) {
			return startUpCosts.get(hour);
		}

	}

	public static List<Float> capacityList = new ArrayList<>();
	private static final int DEMAND_SCENARIO_NUMBER = 3;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(PriceForecastDayAhead.class.getName());
	private static Integer logId;
	private static Integer logIdThermal;
	/**
	 * The maximal length (number of continuous hours) for which start-up costs
	 * are regarded.
	 */
	private static final int MAX_STARTUP_PERIOD_LENGTH = 10;

	/**
	 * Little test case.
	 *
	 * @param args
	 */
	public static void main(String[] args) {

		Date.setInitialDate(2010, 2010, null, 365);

		final MarketArea area = new MarketArea();

		try {
			Field field = MarketArea.class.getDeclaredField("dataBasePrefix");
			field.setAccessible(true);
			field.set(area, "");
			field = MarketArea.class.getDeclaredField("fuelPriceScenarioDaily");
			field.setAccessible(true);
			field.set(area, "historical");
			field = MarketArea.class.getDeclaredField("fuelPriceScenarioYearly");
			field.setAccessible(true);
			field.set(area, "EU_EnergyRoadmap_RS");
			field = MarketArea.class.getDeclaredField("lastYearlyFuelPriceYear");
			field.setAccessible(true);
			field.set(area, 2010);
			field = MarketArea.class.getDeclaredField("lastDailyFuelPriceYear");
			field.setAccessible(true);
			field.set(area, 2010);

			field = Settings.class.getDeclaredField("powerPlantDbName");
			field.setAccessible(true);
			field.set(null, "powerplant");
			field = Settings.class.getDeclaredField("startupCostsScenario");
			field.setAccessible(true);
			field.set(null, "ThureTraber");
			field = Settings.class.getDeclaredField("fuelsDBName");
			field.setAccessible(true);
			field.set(null, "Fuels");
			field = Settings.class.getDeclaredField("fuelsDBName");
			field.setAccessible(true);
			field.set(null, "Fuels");

		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException
				| IllegalAccessException e) {
			logger.error(e.getMessage());
		}

		final StartupCost startCosts = new StartupCost(area);
		startCosts.call();

		final FuelPrices fuelPrices = new FuelPrices(area);
		fuelPrices.call();

		final Plant plant1 = new Plant(new MarketArea());
		plant1.setUnitID(1);
		plant1.setUnitID(1);
		plant1.setFuelName(FuelName.URANIUM);
		plant1.setVarCostsTotal(10);
		plant1.setNetCapacity(1.5f);
		plant1.setCategory(Type.NUC_GEN_2);

		final Plant plant2 = new Plant(new MarketArea());
		plant2.setUnitID(2);
		plant1.setUnitID(2);
		plant2.setFuelName(FuelName.GAS);
		plant2.setVarCostsTotal(11);
		plant2.setNetCapacity(1.5f);
		plant2.setCategory(Type.GAS_CC_NEW);

		final Plant plant3 = new Plant(new MarketArea());
		plant3.setUnitID(3);
		plant1.setUnitID(3);
		plant3.setFuelName(FuelName.GAS);
		plant3.setVarCostsTotal(11);
		plant3.setNetCapacity(1.5f);
		plant3.setCategory(Type.GAS_CC_NEW);

		final List<Plant> powerPlants = new ArrayList<>();
		powerPlants.add(plant1);
		powerPlants.add(plant2);
		powerPlants.add(plant3);

		final List<Float> demand = Arrays.asList(4.6f, 4f, 1f, 2f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
				1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f);
		final List<Float> renewableCertain = Arrays.asList(0f, 0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
				0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
		final List<Float> pumpedStorage = Arrays.asList(0f, 0f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
				0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

		final PriceForecastDayAhead forecast = new PriceForecastDayAhead(demand, renewableCertain,
				pumpedStorage, powerPlants, null);

		forecast.forecastMarketPricesFast(area);

		final List<ScenarioList<Float>> scenarios = forecast.getPriceForecastDaily();
		for (final ScenarioList<Float> scenario : scenarios) {
			logger.info(Arrays.toString(scenario.getValues().toArray()));
		}

	}

	/**
	 * The expected feed-in from intermittent renewables in MWh for each hour.
	 */
	private List<Float> availCapacity;

	/** The demand in MWh for each hour. */
	private List<Float> demand;
	/** The demand forecasts, each scenario is in a new list. */
	private List<ScenarioList<Float>> demandForecastDaily;
	/** The number of hours for which forecast is made, e.g. 30 hours. */
	private final int forecastLength;
	/** Number of total scenarios */
	private int numberOfScenarios;
	private final List<ForecastOutcome> outcomes = new ArrayList<>();
	/** The daily market prices, each scenario is in a new list. */
	private Map<Integer, Map<Integer, Float>> powerPlantCapacityUnused;

	/** All power plants sorted by their variable costs. */
	private final List<Plant> powerPlants;
	/** The daily market prices, each scenario is in a new list. */
	private List<ScenarioList<Float>> priceForecastDaily;
	private final float probabilityMedium = 0.5f;
	/**
	 * Forecasted hourly production of all plants for all scenarios.
	 */
	private List<Map<Plant, List<Float>>> productionForecastDaily = null;
	/**
	 * The expected feed-in from intermittent renewables in MWh for each hour.
	 */
	private final List<Float> pumpedStorage;
	/** The feed-in from certain renewables in MWh for each hour. */
	private final List<Float> renewableCertain;

	private List<ScenarioList<Float>> startupCostsDaily;
	/** The residual load for each scenario. */
	private List<ScenarioList<Float>> thermalResLoad;
	private final int time = 1;
	/** Available capacity for each hour. */
	private List<ScenarioList<Float>> varCostsDaily;

	/**
	 * Create a new PriceForecastDayAheadDayAhead object that can be used to
	 * forecast the market prices via {@link #forecastMarketPrices()}.
	 *
	 * @param demand
	 * @param renewable
	 * @param powerPlants
	 */
	public PriceForecastDayAhead(List<Float> demand, List<Float> renewableCertain,
			List<Float> pumpedStorage, List<Plant> powerPlants, Random random) {

		this.demand = new ArrayList<>(demand);
		this.renewableCertain = new ArrayList<>(renewableCertain);
		this.pumpedStorage = new ArrayList<>(pumpedStorage);
		this.powerPlants = new ArrayList<>(powerPlants);
		forecastLength = demand.size();

		numberOfScenarios = 1;

	}

	/**
	 * Forecast market prices for different scenarios. Start-up costs are
	 * included in forecast.
	 *
	 * Not really used anymore, see new method that also uses min-run time and
	 * other things.
	 *
	 *
	 * @return Market prices
	 */
	public void forecastMarketPricesFast(MarketArea marketArea) {

		// sort can lead to problems with threads if the costs are set at
		// the same time
		try {
			Collections.sort(powerPlants);
		} catch (final java.lang.IllegalArgumentException e) {
			logger.error(
					"Maybe this has to to do with an concurrency error where determineDailyPlantCosts sets the cost of power plants at the same time?",
					e);
		}

		try {
			forecastDemand();
		} catch (final Exception e) {
			logger.error(e.getMessage());
		}

		try {
			calcCapacityUnused();
		} catch (final Exception e) {
			logger.error(e.getMessage());
		}
		try {
			calcMarketOutcomes(marketArea);
		} catch (final Exception e) {
			logger.error(e.getMessage());
		}

		for (final ScenarioList<Float> scenario : priceForecastDaily) {
			for (final Float price : scenario.getValues()) {
				if ((price > 500) || (price < -100)) {
					logger.error("Error forecast is too high/low. " + price);
				}
			}
		}

	}

	/**
	 * Forecast market prices for different scenarios. Start-up costs are
	 * included in forecast.
	 *
	 * Includes min-run time and min-shut down time.
	 *
	 * @return Market prices
	 */
	public void forecastMarketPricesNew(MarketArea marketArea) {

		// sort can lead to problems with threads if the costs are set at
		// the same time
		try {
			Collections.sort(powerPlants);
		} catch (final java.lang.IllegalArgumentException e) {
			logger.error(
					"Maybe this has to to do with an concurrency error where determineDailyPlantCosts sets the cost of power plants at the same time?",
					e);
		}

		forecastDemand();
		calcCapacityUnused();
		calcMarketOutcomesAssign(marketArea);
	}

	public List<ForecastOutcome> getOutcomes() {
		return outcomes;
	}

	public List<ScenarioList<Float>> getPriceForecastDaily() {
		return priceForecastDaily;
	}

	/**
	 * Returns the forecasted hourly production of all plants for all scenarios.
	 */
	public List<Map<Plant, List<Float>>> getProductionForecastDaily() {
		return productionForecastDaily;
	}

	public List<ScenarioList<Float>> getResidualLoadDaily() {
		return thermalResLoad;
	}

	public synchronized void logPriceForecast(MarketArea marketArea) {
		if ((logId == null) || Date.isFirstDayOfYear()) {
			final String logFile = marketArea.getInitialsUnderscore() + "PriceForecast_"
					+ Date.getYear() + Settings.LOG_FILE_SUFFIX_CSV;
			final String description = "All trades";
			final String unitLine = "[-];Euro/MWh;MWh";
			final String titleLine = "hourOfYear;" + "dayAheadPriceForecast;dayAheadVolumeForecast;"
					+ "tradePrice;tradeVolume;"
					+ "sellerType;sellerTrader;sellplantId;sellNonAvailVolume;sellTotalCapacity;sellDayAheadVolume;sellMarginalCosts;sellPrice;sellVolume;sellStartCosts;sellMarkup;"
					+ "buyer;buyerTrader;buyerPlantId;buyerNonAvailVolume;buyerTotalVolume;buyerDayAheadVolume;buyerMarginalCosts;buyerPrice;buyerVolume;buyerStartCosts";
			logId = LoggerCSV.newLogObject(Folder.DAY_AHEAD_PRICES, logFile, description, titleLine,
					unitLine, marketArea.getIdentityAndNameLong());
		}

		final int startHour = (Date.getDayOfYear() - 1) * HOURS_PER_DAY;
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			final StringBuffer sb = new StringBuffer(Integer.toString(startHour + hour));

			for (int index = 0; index < priceForecastDaily.size(); index++) {
				sb.append(";" + demand.get(hour) + ";" + renewableCertain.get(hour) + ";"
						+ pumpedStorage.get(hour) + ";" + thermalResLoad.get(index).get(hour) + ";"
						+ availCapacity.get(hour) + ";" + priceForecastDaily.get(index).get(hour)
						+ ";" + varCostsDaily.get(index).get(hour) + ";"
						+ startupCostsDaily.get(index).get(hour));

			}
			LoggerCSV.writeLine(logId, sb.toString());
		}

		logThermalUnits(marketArea);

	}

	/**
	 * Access to powerPlant.getCapacityUnused is rather slow. Therefore store
	 * values here.
	 */
	private void calcCapacityUnused() {

		powerPlantCapacityUnused = new HashMap<>(powerPlants.size());

		final int firstHourOfToday = Date.getFirstHourOfToday();
		for (final Plant powerPlant : powerPlants) {

			final int plantIndex = powerPlant.getUnitID();
			final HashMap<Integer, Float> plantCapacityUnused = new HashMap<>(forecastLength);
			for (int hour = 0; hour < forecastLength; hour++) {
				plantCapacityUnused.put(hour,
						powerPlant.getCapacityUnusedExpected(firstHourOfToday + hour));

			}
			powerPlantCapacityUnused.put(plantIndex, plantCapacityUnused);
		}

	}

	/**
	 * Calculate the market price for a given combination of the demand, the
	 * renewable feed-in and available power plants.
	 *
	 * @param demand
	 * @param renewable
	 * @param powerPlants
	 * @return A list with the hourly market prices.
	 */
	private ScenarioList<Float> calcDailyMarketPrice(ScenarioList<Float> demand,
			List<Plant> powerPlants, MarketArea marketArea) {

		final String name = demand.getName();
		final float probability = demand.getProbability();
		final List<Float> prices = new ArrayList<>(demand.getSize());
		try {

			final List<Float> thermalResLoadLocal = calcThermalResLoad(demand, marketArea)
					.getValues();
			thermalResLoad.add(new ScenarioList<>(time, name, thermalResLoadLocal));
			final List<Integer> lastIndex = new ArrayList<>(
					Collections.nCopies(forecastLength, Integer.MIN_VALUE));

			determineLastPwrPlant(powerPlants, thermalResLoadLocal, lastIndex);
			final List<Float> varCosts = new ArrayList<>();
			final List<Float> startupCosts = new ArrayList<>();

			for (int hour = 0; hour < forecastLength; hour++) {
				final Plant powerPlant = powerPlants.get(lastIndex.get(hour));
				final int length = determineContinuousHours(lastIndex, hour, powerPlant);
				Float varCostsHourly;
				Float startupCostsHourly = 0f;

				if ((powerPlant.getFuelType() == FuelType.URANIUM)
						|| (powerPlant.getFuelType() == FuelType.LIGNITE)) {

					float startup;

					if (length < powerPlant.getMinDownTime()) {
						startup = marketArea.getStartUpCosts()
								.getMarginalStartupCostsHot(powerPlant);
					} else {
						startup = marketArea.getStartUpCosts()
								.getMarginalStartupCostsHot(powerPlant);
					}
					varCostsHourly = powerPlant.getCostsVar() - (1.5f * startup);

				} else {
					varCostsHourly = powerPlant.getCostsVar();

					if (length < MAX_STARTUP_PERIOD_LENGTH) {

						startupCostsHourly = marketArea.getStartUpCosts()
								.getMarginalStartupCostsHot(powerPlant) / length;
					}
				}

				prices.add(varCostsHourly + startupCostsHourly);
				varCosts.add(varCostsHourly);
				startupCosts.add(startupCostsHourly);
			}
			varCostsDaily.add(new ScenarioList<>(time, name, varCosts, probability));
			startupCostsDaily.add(new ScenarioList<>(time, name, startupCosts, probability));
			if (Settings.isLogDayAheadForecast()) {
				logOutcome(prices, startupCosts, demand.getValues(), thermalResLoadLocal,
						powerPlants, lastIndex, name);
			}

		} catch (final Exception e) {
			logger.error("What happened", e);
		}

		return new ScenarioList<>(time, name, prices, probability);
	}

	/**
	 * Calculate the market outcomes for all scenario combinations via
	 * {@link #priceForecastDaily}
	 */
	private void calcMarketOutcomes(MarketArea marketArea) {

		priceForecastDaily = new ArrayList<>(numberOfScenarios);
		thermalResLoad = new ArrayList<>(numberOfScenarios);
		varCostsDaily = new ArrayList<>(numberOfScenarios);
		startupCostsDaily = new ArrayList<>(numberOfScenarios);
		availCapacity = new ArrayList<>();

		for (final ScenarioList<Float> demand : demandForecastDaily) {
			priceForecastDaily.add(calcDailyMarketPrice(demand, powerPlants, marketArea));

		}
	}

	private void calcMarketOutcomesAssign(MarketArea marketArea) {

		thermalResLoad = new ArrayList<>();

		for (final ScenarioList<Float> demand : demandForecastDaily) {
			// Get res load
			thermalResLoad.add(calcThermalResLoad(demand, marketArea));

		}

		final AssignPowerPlantsForecast app = new AssignPowerPlantsForecast(powerPlants,
				thermalResLoad, marketArea);
		app.assignPlants();
		priceForecastDaily = app.getMarginalCosts();
		productionForecastDaily = app.getProduction();

	}

	/**
	 * Calculate the residual load for a given demand by subtracting the total
	 * renewable feed-in and the expected pump-storage profile as well as the
	 * electricity which is produced in cogeneration from the demand.
	 *
	 * @param demand
	 *
	 * @return residual load
	 */
	private ScenarioList<Float> calcThermalResLoad(ScenarioList<Float> demand,
			MarketArea marketArea) {
		final List<Float> thermalResLoad = new ArrayList<>(demand.getSize());
		for (int hour = 0; hour < forecastLength; hour++) {
			final int hourOfYear = Date.getFirstHourOfToday() + hour;
			float definitedReduction = 0f;
			float bothWaysReduction = 0f;
			float resLoad = 0f;
			try {
				definitedReduction = -renewableCertain.get(hour);
				bothWaysReduction = pumpedStorage.get(hour) + marketArea.getExchange()
						.getHourlyFlowForecast(Date.getYear(), hourOfYear);
				resLoad = demand.get(hour) + definitedReduction + bothWaysReduction;
				thermalResLoad.add(resLoad);
			} catch (final NullPointerException e) {
				logger.error(hour + ": definitedReduction " + definitedReduction
						+ ": bothWaysReduction " + bothWaysReduction + ": resLoad " + resLoad + ", "
						+ e.getMessage());
			}

		}
		return new ScenarioList<>(time, demand.getName(), thermalResLoad, demand.getProbability());
	}

	/**
	 * Determine the number of continuous hours the power plant with highest
	 * variable costs that is still in the market at <code>hour</code> is
	 * running. For example, the power plant is two hours in the market before
	 * <code>hour</code> and three after, thus six will be returned.
	 *
	 * @param lastIndex
	 *            The index of the power plants that setting the price in the
	 *            market.
	 * @param hour
	 *            The hour for which the length should be determined.
	 * @param powerPlant
	 * @return Number of continuous hours last power plant is running.
	 */
	private int determineContinuousHours(List<Integer> lastIndex, int hour, Plant powerPlant) {

		int length = 1;
		final int currentIndex = lastIndex.get(hour);

		// Check how plant is in the market before current hour
		// If longer than first hour of today, take last hour of today as an
		// approximate value
		// Could have take real value too, but this seems to work better, cause
		// forecast does not regard
		// min-runtime or outages on day before, still a better approach could
		// be used
		for (int beforeHour = hour - 1; beforeHour >= -HOURS_PER_DAY; beforeHour--) {

			if (beforeHour < 0) {
				// If goes further back then first hour of day, use real data
				if (currentIndex <= lastIndex.get(beforeHour + HOURS_PER_DAY)) {
					length++;
				} else {
					break;
				}
			} else {
				if (currentIndex <= lastIndex.get(beforeHour)) {
					length++;
				} else {
					break;
				}
			}
		}

		// Check how plant is in the market after current hour
		for (int afterHour = hour + 1; afterHour < forecastLength; afterHour++) {
			if (currentIndex <= lastIndex.get(afterHour)) {
				length++;
			} else {
				break;
			}
		}

		return length;
	}

	/**
	 * Determine the last power plant for each hour that runs in order to
	 * satisfy the demand. <BR>
	 * Add the moment only works if power plants are out of the market for a
	 * whole day and not less! This code was optimized for performance reasons
	 * since the old method took too much time. (compare with Revision 728)
	 *
	 * @param plants
	 *            All power plants, available and non-available. Plants need to
	 *            be sorted.
	 * @param resLoad
	 *            The residual load,
	 * @param lastIndex
	 *            For each hour the index of the last (most expensive) power
	 *            plant that is running.
	 */
	private void determineLastPwrPlant(List<Plant> plants, List<Float> resLoad,
			List<Integer> lastIndex) {

		// First day, since real outages can differ from day to day, create
		// list of demand
		final Map<Integer, Float> resLoadFirstDay = new HashMap<>(HOURS_PER_DAY);
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			resLoadFirstDay.put(hour, resLoad.get(hour));
		}
		determineLastPwrPlantFast(plants, resLoad, lastIndex, resLoadFirstDay);

		// Second day
		final Map<Integer, Float> resLoadSecondDay = new HashMap<>(forecastLength - HOURS_PER_DAY);
		for (int hour = HOURS_PER_DAY; hour < forecastLength; hour++) {
			resLoadSecondDay.put(hour, resLoad.get(hour));
		}
		determineLastPwrPlantFast(plants, resLoad, lastIndex, resLoadSecondDay);

	}

	/**
	 * Determines the last power plant for each value <code>resLoad</code>.
	 * Starts with hour with lowest residual demand and goes to the hour with
	 * next higher demand.
	 *
	 * @param plants
	 *            All power plants, available and non-available. Plants need to
	 *            be in a sorted order!
	 * @param thermalResLoad
	 *            The residual load
	 * @param lastIndex
	 *            The index of the last power plant that is running in each hour
	 * @param resLoad
	 *            The hourly values for which the last running power plants are
	 *            determined.
	 *
	 */
	private void determineLastPwrPlantFast(List<Plant> plants, List<Float> thermalResLoad,
			List<Integer> lastIndex, Map<Integer, Float> resLoad) {

		// Sort Map by value
		final Map<Integer, Float> resLoadMap = Sorting.sortByValueIncreasing(resLoad);

		float supply = 0;
		int plantIndex = 0;

		// start with lowest demand on list
		for (final Integer hour : resLoadMap.keySet()) {

			// check if demand is already satisfied
			if (supply >= thermalResLoad.get(hour)) {
				// plantIndex is incremented when supply is met, so current
				// plantIndex is equal to last power plant + 1 and price setting
				// plant is power plant -1, but if plant is the first plant that
				// satisfies the demand has no been incremented so plant index
				// would be 0
				lastIndex.set(hour, Math.max(0, plantIndex - 1));
				continue;
			}

			// get last power plant that needs to be running and that is also
			// available on the next day due to maintenance (index starts with
			// 0, day with 1) or assumed to be out of the market in a scenario
			for (; plantIndex < plants.size(); plantIndex++) {
				final Plant powerPlant = plants.get(plantIndex);

				supply += powerPlantCapacityUnused.get(powerPlant.getUnitID()).get(hour);
				if (supply >= thermalResLoad.get(hour)) {
					// This plant does need to be counted again, therefore
					// increment index
					lastIndex.set(hour, plantIndex++);
					break;
				}

			}

			// If demand cannot be met, set last index to most expensive plant
			if (lastIndex.get(hour) == Integer.MIN_VALUE) {

				lastIndex.set(hour, lastAvailablePlant(plants));
			}
		}
	}

	/**
	 * Forecast the demand by calculating scenarios, where one of them is actual
	 * demand.
	 */
	private void forecastDemand() {

		demandForecastDaily = new ArrayList<>(DEMAND_SCENARIO_NUMBER);

		demandForecastDaily.add(new ScenarioList<>(time, "DemandMid", demand, probabilityMedium));

	}

	/**
	 *
	 * Attention if plants can be out of the market for less than a day this
	 * method has to be adapted.
	 *
	 * @param plants
	 *            (that are sorted by costs)
	 *
	 * @return The plantIndex (index in powerPlants list) of the last available
	 *         plant. Last available plant means the plant with the highest
	 *         costs that is not in revision or expected to be not working in
	 *         the scenario. If no plant is available returns Integer.MIN_VALUE;
	 */
	private Integer lastAvailablePlant(List<Plant> plants) {
		int plantIndex = Integer.MIN_VALUE;
		for (int index = plants.size() - 1; index >= 0; index--) {
			plants.get(index);

			plantIndex = index;
			break;

		}
		return plantIndex;
	}

	/**
	 * Logs information about the daily forecast outcome.
	 *
	 * @param prices
	 *            the hourly prices forecast
	 * @param demand
	 *            the total demand that needed to be fulfilled
	 * @param thermalResLoadLocal
	 * @param plants
	 *            the plants that
	 * @param outages
	 *
	 * @param lastIndex
	 */
	private void logOutcome(List<Float> prices, List<Float> startUpCosts, List<Float> demand,
			List<Float> thermalResLoadLocal, List<Plant> plants, List<Integer> lastIndex,
			String name) {
		final ForecastOutcome outcome = new ForecastOutcome(name);
		outcome.plants = new ArrayList<>(plants);

		// For each hour of the current day (disregard forecast for next days,
		// since they are not as relevant for comparison purposes with real
		// market results)
		for (int hour = 0; hour < prices.size(); hour++) {
			outcome.price.put(hour, prices.get(hour));
			outcome.demand.put(hour, demand.get(hour));
			outcome.resLoad.put(hour, thermalResLoadLocal.get(hour));
			outcome.renewableTotal.put(hour, demand.get(hour));
			outcome.startUpCosts.put(hour, startUpCosts.get(hour));
			outcome.lastIndices.put(hour, lastIndex.get(hour));
		}
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			float demandRemaining = demand.get(hour);
			for (int plantIndex = 0; plantIndex <= lastIndex.get(hour); plantIndex++) {

				float volume = plants.get(plantIndex).getNetCapacity();
				demandRemaining -= volume;
				volume = Math.min(demandRemaining, volume);
				final FuelType fuelType = plants.get(plantIndex).getFuelType();
				final Float oldVolume = outcome.fuel.get(hour).get(fuelType);
				if (oldVolume == null) {
					outcome.fuel.get(hour).put(fuelType, volume);
				} else {
					outcome.fuel.get(hour).put(fuelType, oldVolume + volume);
				}
			}
		}
		outcomes.add(outcome);
	}

	private synchronized void logThermalUnits(MarketArea marketArea) {

		if (!(Date.getDayOfYear() == 2)) {
			return;
		}

		if ((logIdThermal == null) || (Date.getDayOfYear() == 2)) {
			final String logFile = marketArea.getInitialsUnderscore() + "Capacity_" + Date.getYear()
					+ Settings.LOG_FILE_SUFFIX_CSV;
			final String description = "All trades";
			final String unitLine = "[-];Euro/MWh;MWh";
			final String titleLine = "hourOfYear;" + "dayAheadPriceForecast;dayAheadVolumeForecast;"
					+ "tradePrice;tradeVolume;"
					+ "sellerType;sellerTrader;sellplantId;sellNonAvailVolume;sellTotalCapacity;sellDayAheadVolume;sellMarginalCosts;sellPrice;sellVolume;sellStartCosts;sellMarkup;"
					+ "buyer;buyerTrader;buyerPlantId;buyerNonAvailVolume;buyerTotalVolume;buyerDayAheadVolume;buyerMarginalCosts;buyerPrice;buyerVolume;buyerStartCosts";
			logIdThermal = LoggerCSV.newLogObject(Folder.DAY_AHEAD_PRICES, logFile, description,
					titleLine, unitLine, marketArea.getIdentityAndNameLong());
		}

		for (int index = 0; index < powerPlants.size(); index++) {
			final StringBuffer sb = new StringBuffer(Integer.toString(index));
			final Plant plant = powerPlants.get(index);
			sb.append(";" + plant.getUnitID() + ";" + ";" + plant.getCostsVar() + ";"
					+ plant.getNetCapacity() + ";" + plant.getOwnerID() + ";"
					+ plant.getShutDownDate());

			LoggerCSV.writeLine(logIdThermal, sb.toString());
		}
	}

	/**
	 * Utility method to show merit order in console output.
	 *
	 * @param lastIndex
	 * @param marketArea
	 */
	@SuppressWarnings("unused")
	private void printMeritOrder(int lastIndex, MarketArea marketArea) {
		float startCap = 0f;
		for (int index = 0; index <= lastIndex; index++) {
			final Plant plant = powerPlants.get(index);
			logger.info("Cap " + String.format("%7.1f", startCap) + " | "
					+ String.format("%7.1f", plant.getNetCapacity()) + " | fuel "
					+ String.format("%10s", plant.getFuelType().toString()) + " | varCosts "
					+ String.format("%7.1f", plant.getCostsVar()) + " | startcosts "
					+ String.format("%7.1f",
							marketArea.getStartUpCosts().getMarginalStartUpCosts(plant)));
			startCap += plant.getNetCapacity();
		}
	}

}