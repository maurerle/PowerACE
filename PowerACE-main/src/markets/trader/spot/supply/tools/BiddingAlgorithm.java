package markets.trader.spot.supply.tools;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.fuel.FuelPrices;
import data.powerplant.costs.StartupCost;
import markets.bids.Bid.BidType;
import markets.bids.power.BlockBidPower;
import markets.bids.power.HourlyBidPower;
import markets.trader.TraderType;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.Plant;
import supply.powerplant.technique.Type;
import supply.scenarios.ScenarioList;
import tools.logging.LoggerXLSX;
import tools.math.Statistics;
import tools.other.Tuple;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * 
 *
 *         Important note, bids in model for day-ahead-market are made on the
 *         same day. So for start-up costs hour of yesterday and day before
 *         yesterday are relevant.
 *
 */
public class BiddingAlgorithm {

	private enum HOURLY_BID_TYPE {
		DIFF,
		NORMAL;
	}

	/**
	 * Set of technical restrictions for a specific power plant. Needed to use
	 * different restrictions than defined for the power plant in the class
	 * "Plant", i.e. mainly if no technical restrictions should be considered
	 * for the bidding algorithm.
	 */
	private class TechnicalRestrictions {
		private float minProduction;
		private int minRunTime;

		private TechnicalRestrictions(float minProduction, int minRunTime) {
			this.minProduction = minProduction;
			this.minRunTime = minRunTime;
		}

		private float getMinProduction() {
			return minProduction;
		}

		@SuppressWarnings("unused")
		private int getMinRunTime() {
			return minRunTime;
		}
	}

	/** For test purposes */
	private static Map<FuelType, Integer> counterAvoidShutdownReal = new LinkedHashMap<>();

	/** For test purposes */
	private static Map<FuelType, Integer> counterAvoidShutdownTotal = new LinkedHashMap<>();

	/** For test purposes */
	private static Map<FuelType, Integer> counterNotInMarket = new LinkedHashMap<>();

	private static final float EPSILON = 0.0001f;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(BiddingAlgorithm.class.getName());
	private static final int MAX_PERIOD_LENGTH = 12;

	/**
	 * Used for better output format. Make it tread safe via ThreadLocal since
	 * DecimalFormat is not.
	 */
	private static final ThreadLocal<NumberFormat> numberFormat = new ThreadLocal<>() {
		@Override
		public NumberFormat initialValue() {
			return new DecimalFormat("##.#", new DecimalFormatSymbols(new Locale("en")));
		}
	};
	/** Price increase to make sure price is within boundaries. */
	private static final float priceIncrease = 0.1f;
	/** Probability for finding period where plant will be out of the market. */
	private static final double PROBABILITY_AVOID_TURN_OFF_NOT_RUNNING = 0.1;
	/** Probability for finding period where plant will be out of the market. */
	private static final double PROBABILITY_AVOID_TURN_OFF_RUNNING = 0.9;
	/** If not using of price step algorithm */
	private static boolean steppingAlgorithm = true;
	private static float URANIUM_MIN_PRODUCTION_INCREASE = 1.3f;
	/** Assume if power plant will be running after end of forecast period. */
	private static final boolean WAS_RUNNING = false;
	/** Assume if power plant will be running after end of forecast period. */
	private static final boolean WILL_BE_RUNNING = false;

	public static void addAvoidShutdownReal(FuelType fueltype, int length) {

		if (!counterAvoidShutdownReal.containsKey(fueltype)) {
			counterAvoidShutdownReal.put(fueltype, 0);
		}
		counterAvoidShutdownReal.put(fueltype, counterAvoidShutdownReal.get(fueltype) + length);

	}

	public static void addAvoidShutdownTotal(FuelType fueltype, int length) {

		if (!counterAvoidShutdownTotal.containsKey(fueltype)) {
			counterAvoidShutdownTotal.put(fueltype, 0);
		}
		counterAvoidShutdownTotal.put(fueltype, counterAvoidShutdownTotal.get(fueltype) + length);

	}

	public static void addNotInMarket(FuelType fueltype) {

		if (!counterNotInMarket.containsKey(fueltype)) {
			counterNotInMarket.put(fueltype, 0);
		}
		counterNotInMarket.put(fueltype, counterNotInMarket.get(fueltype) + HOURS_PER_DAY);

	}

	/**
	 * Quick test method.
	 */
	public static void main(String[] args) {

		Date.setInitialDate(2010, 2010, null, 365);

		final MarketArea area = new MarketArea();

		try {
			Field field = MarketArea.class.getDeclaredField("name");
			field.setAccessible(true);
			field.set(area, "Germany");
			field = MarketArea.class.getDeclaredField("dataBasePrefix");
			field.setAccessible(true);
			field.set(area, "");
			field = MarketArea.class.getDeclaredField("fuelPriceScenarioDaily");
			field.setAccessible(true);
			field.set(area, "_prices_historical_transformed");
			field = MarketArea.class.getDeclaredField("fuelPriceScenarioYearly");
			field.setAccessible(true);
			field.set(area, "EU_EnergyRoadmap_RS");
			field = MarketArea.class.getDeclaredField("lastYearlyFuelPriceYear");
			field.setAccessible(true);
			field.set(area, 2010);
			field = MarketArea.class.getDeclaredField("lastDailyFuelPriceYear");
			field.setAccessible(true);
			field.set(area, 2010);
			field = Settings.class.getDeclaredField("startupCostsScenario");
			field.setAccessible(true);
			field.set(null, "ThureTraber");
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
		plant1.setFuelName(FuelName.URANIUM);
		plant1.setVarCostsTotal(10);
		plant1.setNetCapacity(1000);
		plant1.setCategory(Type.NUC_GEN_2);
		plant1.initializePowerPlant(area);
		plant1.setAvailableDate(1990);

		final Plant plant2 = new Plant(new MarketArea());
		plant2.setUnitID(2);
		plant2.setFuelName(FuelName.COAL);
		plant2.setVarCostsTotal(10);
		plant2.setNetCapacity(2000);
		plant2.setCategory(Type.GAS_COMB_NEW);
		plant2.setAvailableDate(1990);
		plant2.initializePowerPlant(area);

		final List<Plant> plants = new ArrayList<>();
		plants.add(plant1);
		plants.add(plant2);

		final ScenarioList<Float> prices1 = new ScenarioList<>(1, "",
				Arrays.asList(50f, 0f, 50f, 55f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
						0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
				0.5f);
		final ScenarioList<Float> prices2 = new ScenarioList<>(1, "",
				Arrays.asList(50f, 0f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
						0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
				0.5f);

		final List<ScenarioList<Float>> prices = new ArrayList<>();
		prices.add(prices1);
		prices.add(prices2);
		final BiddingAlgorithm bidAlg = new BiddingAlgorithm(prices, plants, area, 0, plants, null);
		bidAlg.makeBids();
		final Map<Integer, Map<Integer, Map<HOURLY_BID_TYPE, List<HourlyBidPower>>>> bids = bidAlg.bids;
		final List<BlockBidPower> blockBidPowers = bidAlg.getBlockBids();

		for (final Map<Integer, Map<HOURLY_BID_TYPE, List<HourlyBidPower>>> bid : bids.values()) {
			final StringBuilder sb = new StringBuilder();
			for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
				sb.append(bid.get(hourOfDay));
				for (final HOURLY_BID_TYPE type : bids.get(0).get(0).keySet()) {
					for (final HourlyBidPower powerBid : bid.get(0).get(type)) {
						logger.info(powerBid.getPlant() + " " + sb.toString());
					}
				}
			}
		}
		logger.info(Arrays.toString(blockBidPowers.toArray()));

	}

	/**
	 * For each power plant the hourly bids for the next day.
	 * [PlantIndex][HourOfDay][HOURLY_BID_TYPE][Bid]
	 */
	private Map<Integer, Map<Integer, Map<HOURLY_BID_TYPE, List<HourlyBidPower>>>> bids;

	/** For each power plant the block bids for the next day. */
	private Map<Integer, List<BlockBidPower>> blockBids;

	/** For each power plant the Capacity in MWh. */
	private Map<Integer, Map<Integer, Float>> capacity;
	/** List of all power plants included in the bidding algorithm. */
	private Map<Integer, Integer> firstContinuousNotRunningHour;
	/** List of all power plants included in the bidding algorithm. */
	private Map<Integer, Integer> firstContinuousRunningHour;
	/**
	 * Number of hours a forecast is made. This is not equal to the number of
	 * hours bids are made, but in order to make bids, the expected profit of
	 * the next day has to be taken in account too.
	 */
	private final int forecastLength;
	/** For each power plant the fuel type. */
	private Map<Integer, FuelType> fuelType;
	public static Map<FuelType, Integer> getCounterAvoidShutdownReal() {
		return counterAvoidShutdownReal;
	}

	public static Map<FuelType, Integer> getCounterAvoidShutdownTotal() {
		return counterAvoidShutdownTotal;
	}

	public static Map<FuelType, Integer> getCounterNotInMarket() {
		return counterNotInMarket;
	}

	/**
	 * Status for each plant and hour if plants is running on day before
	 * bidding.
	 */
	private Map<Integer, Map<Integer, Boolean>> inMarketBefore;
	/**
	 * For each power plant the probability to be in the market for each hour,
	 * e.g. first element equals probability for the first power plant for hour
	 * <i>1</i>.
	 */
	private Map<Integer, Map<Integer, Float>> inMarketProbBasedOnBids;
	/**
	 * For each power plant the probability to be in the market for each hour,
	 * e.g. first element equals probability for the first power plant for hour
	 * <i>1</i>.
	 */
	private Map<Integer, Map<Integer, Float>> inMarketProbBasedOnVarCosts;
	/**
	 * For each power plant the probability to be in the market for each hour
	 * and length, e.g. first element equals probability for the first power
	 * plant for hour <i>1</i> and length of <i>0</i>.
	 */
	private List<List<List<Float>>> inMarketProbPeriods;
	/** Logid for bids */
	private final int logId;
	private final MarketArea marketArea;
	/** Currently only 100 block bids per bidder. */
	private int numberOfBlockBidsCurrently;
	/** Currently only 100 block bids per bidder. */
	private final int numberOfBlockBidsMaximum = 100;
	/** Total number of scenarios which are regarded. */
	private final int numberOfScenarios;
	/** Current plant index */
	private int plantIndex;
	/** Map storing technical restrictions for all plants */
	Map<Integer, TechnicalRestrictions> plantsTechnicalRestrictions = new LinkedHashMap<>();
	/** List of all power plants included in the bidding algorithm. */
	private final List<Plant> powerPlantsAll;
	/** List of all power plants included in the bidding algorithm. */
	private final List<Plant> powerPlantsAvail;
	/** Prices for each scenario and each hour. */
	private final List<? extends ScenarioList<Float>> prices;
	private Map<Integer, Float> production;

	/**
	 * Expected hourly production of all plants according to the price forecast
	 * for all scenarios.
	 */
	private List<Map<Plant, List<Float>>> productionForecast;
	/** For each power plant the expected profit for each scenario. */
	private Map<Integer, Map<Integer, Float>> profit;
	/**
	 * For each scenario and each hour, true if last checked power plant was
	 * running continuously, elsewise false.
	 */
	private List<List<Boolean>> runningAllTime;
	/**
	 * For each scenario, true if last checked power plant was running
	 * continuously, elsewise false.
	 */
	private List<Boolean> runningAllTimeScenario;
	private List<List<List<Integer>>> runningHours;
	/** For each power plant the average startup costs for each hour. */
	private List<List<Float>> runningHoursExpectedAvg;
	/** Logid for bids */
	private boolean speedUp = true;
	/**
	 * For each power plant the startup costs for each scenario and each hour.
	 * First value equals startup costs that occur in scenario 1 if plant starts
	 * in first hour.
	 */
	private List<List<List<Float>>> startUpCosts;
	/** For each power plant the average startup costs for each hour. */
	private List<List<Float>> startUpCostsAvg;
	/** For each power plant the startup costs. */
	private List<Float> startUpCostsCold;
	/** For each power plant the startup costs. */
	private List<Float> startUpCostsHot;
	/**
	 * For each power plant the startup costs for each scenario and each hour,
	 * assuming that plant is not in the market and has to start.
	 */
	private List<List<List<Float>>> startUpCostsNotRunning;
	/**
	 * For each power plant for each hour, the number of times plant is not in
	 * the market.
	 */
	private List<List<Integer>> startUpCostsNotRunningCounter;
	/** For each power plant the startup costs. */
	private List<Float> startUpCostsWarm;
	/**
	 * For each scenario and hour, the length a power plant is supposed to be in
	 * the market.
	 */
	private List<List<Integer>> tempLength;
	/**
	 * Outcome of price forecast can be used to estimate the running hours of
	 * every plant.
	 */
	private boolean useProductionForecast = false;

	/** Turn technical restrictions on/off for use in the bidding algorithm */
	private boolean useTechnicalRestrictions = true;

	/** For each power plant the variable costs. */
	private List<Float> variableCosts;

	/**
	 * Alternative constructor if bidding should be based on expected production
	 * of each power plant as resulting from the price forecast instead of the
	 * actual price forecast
	 */
	public BiddingAlgorithm(List<? extends ScenarioList<Float>> prices,
			List<Map<Plant, List<Float>>> productionForecast, List<Plant> powerPlantsAvail,
			MarketArea marketArea, int logId, List<Plant> powerPlantsAll, Random random) {
		useProductionForecast = true;
		this.productionForecast = productionForecast;
		this.logId = logId;
		this.marketArea = marketArea;
		this.prices = prices;
		numberOfScenarios = prices.size();
		forecastLength = prices.get(0).getValues().size();

		this.powerPlantsAvail = powerPlantsAvail;
		this.powerPlantsAll = powerPlantsAll;
		// Sort plants by variable costs
		// This is important for a lot of methods do not change that
		Collections.sort(this.powerPlantsAvail);

		initialize();
	}

	/**
	 * Standard constructor if bidding should be based on the difference between
	 * the hourly price forecast and the variable costs of each power plant
	 */
	public BiddingAlgorithm(List<? extends ScenarioList<Float>> prices,
			List<Plant> powerPlantsAvail, MarketArea marketArea, int logId,
			List<Plant> powerPlantsAll, Random random) {
		this.logId = logId;
		this.marketArea = marketArea;
		this.prices = prices;
		numberOfScenarios = prices.size();
		forecastLength = prices.get(0).getValues().size();

		this.powerPlantsAvail = powerPlantsAvail;
		this.powerPlantsAll = powerPlantsAll;
		// Sort plants by variable costs
		// This is important for a lot of methods do not change that
		Collections.sort(this.powerPlantsAvail);

		initialize();
	}

	/** @return Return the block bids bids. */
	public List<BlockBidPower> getBlockBids() {
		final List<BlockBidPower> blockBidsList = new ArrayList<>();
		for (final List<BlockBidPower> blockBidsPlant : blockBids.values()) {
			blockBidsList.addAll(blockBidsPlant);
		}
		Collections.sort(blockBidsList);
		return blockBidsList;
	}

	/** @return Return the hourly bids. */
	public Map<Integer, List<HourlyBidPower>> getHourlyBids() {
		final Map<Integer, List<HourlyBidPower>> bidsHourlyList = new LinkedHashMap<>();

		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			final List<HourlyBidPower> hourlyBids = new ArrayList<>();
			for (final Map<Integer, Map<HOURLY_BID_TYPE, List<HourlyBidPower>>> bidPointsPlant : bids
					.values()) {

				final Map<HOURLY_BID_TYPE, List<HourlyBidPower>> bidPoints = bidPointsPlant
						.get(hourOfDay);
				for (final List<HourlyBidPower> bidType : bidPoints.values()) {
					for (final HourlyBidPower bidPoint : bidType) {

						if (bidPoints != null) {
							hourlyBids.add(bidPoint);
						}
					}
				}

			}
			Collections.sort(hourlyBids);
			bidsHourlyList.put(hourOfDay, hourlyBids);
		}

		return bidsHourlyList;
	}

	/**
	 * Method that calls all other methods that are needed for the calculation
	 * of the bids.
	 */
	public void makeBids() {
		try {
			numberOfBlockBidsCurrently = 0;

			// code that needs to run from most expensive to cheapest plant
			// here sorting is important speed up
			for (plantIndex = powerPlantsAvail.size() - 1; plantIndex >= 0; plantIndex--) {
				if (useProductionForecast) {
					calculateProbabilitiesBasedOnProductionForecast();
				} else {
					calculateProbabilitiesBasedOnVarCosts();
				}
				calculateStartUpCosts();
				calculateStartUpCostsAvg();
			}

			// Make bids
			for (plantIndex = 0; plantIndex < powerPlantsAvail.size(); plantIndex++) {

				try {
					makeHourlyBids();
					calculateProbabilitiesBasedOnBids();
					makeAvoidedTurnOffBids();
					removeAuxBids();
					checkBids();

				} catch (final Exception e) {
					logger.error("Error while making bids!", e);
				}
			}

			// log bids if wanted
			if (Settings.isLogBids()) {
				logBids();
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Calculate probability that power plant needs to be turned off at least
	 * once for period <code>[start, end]</code> if plant is running at
	 * <code>start-1</code> and <code>end+1</code>. This is path specific and it
	 * is assumed that plant is turned off, if bid price is higher than expected
	 * market price.
	 *
	 * @param start
	 *            [0,23]
	 * @param end
	 *            [0,23]
	 * @return The probability that that power plant needs to be turned off for
	 *         period.
	 */
	private float calcNotRunningProbBasedOnBids(int start, int end) {

		float probability = 0f;
		for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
			if (isInMarketBasedOnBids(scenarioIndex, start - 1)
					&& isInMarketBasedOnBids(scenarioIndex, end + 1)) {
				for (int hour = start; hour <= end; hour++) {
					if (!isInMarketBasedOnBids(scenarioIndex, hour)) {
						if (numberOfScenarios == 1) {
							probability = 1f;
						} else {
							probability += prices.get(scenarioIndex).getProbability();
						}
						break;
					}
				}
			}
		}
		return probability;
	}

	/**
	 * Find first hour where plant is not running, assuming that for the current
	 * hour the plant is also not running. Method looks at the last hour until
	 * plant is running, then the <code>index + 1</code> is returned.
	 *
	 * @param scenarioIndex
	 * @param hour
	 * @return
	 */
	private Integer calculateFirstContinuousNotRunningHour(int scenarioIndex, int hour) {

		Integer firstNotRunningHour = null;
		int firstNotRunningHourIndex = hour - 1;
		final int maxLength = Date.WARM_STARTUP_LENGTH;

		// Stop running when no more data is available. At the moment
		// WARM_STARTUP_LENGTH, because start-up cost afterwards do not change
		// anymore.
		while ((firstNotRunningHour == null) && ((hour - firstNotRunningHourIndex) <= maxLength)) {
			// If value has already been determined for the past, use it since
			// it does not modify for scenarios
			// Only do this for hour < -1, because change from 0 to -1 is
			// depending on scenario and therefore cannot be set for all
			// scenarios
			if ((firstNotRunningHourIndex < -1)
					&& (firstContinuousNotRunningHour.get(plantIndex) != null)) {
				firstNotRunningHour = firstContinuousNotRunningHour.get(plantIndex);
				break;
			}

			// If out of market, last hour was the last hour in-the-market
			if (isInMarketBasedOnVarCosts(scenarioIndex, firstNotRunningHourIndex)) {
				firstNotRunningHour = firstNotRunningHourIndex + 1;
				// Set value, so it does not have to be checked again for
				// another scenario
				// Only do this for hour < -1, because change from 0 to -1 is
				// depending on scenario and therefore cannot be set for all
				// scenarios
				if (firstNotRunningHourIndex < -1) {
					firstContinuousNotRunningHour.put(plantIndex, firstNotRunningHour);
				}
			}
			firstNotRunningHourIndex--;
		}

		// Check if last date where plant is not running is not regarded.
		if (firstNotRunningHour == null) {
			firstNotRunningHour = -maxLength;
		}

		return firstNotRunningHour;
	}

	/**
	 * Find the first hour starting from <code>hour</code> from current
	 * scenario, where the plant is continuously running.
	 *
	 * E.g. if <code>hour</code> is 4 and plant is running from 2-6 hours, 2 is
	 * returned.
	 *
	 * Only last DateManager.WARM_STARTUP_LENGTH hours are regarded since
	 * afterwards start-up costs do not change anymore.
	 *
	 * @param scenarioIndex
	 * @param hour
	 * @return
	 */
	private Integer calculateFirstContinuousRunningHour(int scenarioIndex, int hour) {

		Integer firstRunningHour = null;
		int firstRunningHourIndex = hour - 1;

		// Run until hour is found or length is not relevant anymore since
		// maximal start-up cost occur
		while ((firstRunningHour == null)
				&& ((hour - firstRunningHourIndex) < Date.WARM_STARTUP_LENGTH)) {

			// If value has already been determined for the past, use it since
			// it does not modify for scenarios
			// Only do this for hour < -1, because change from 0 to -1 is
			// depending on scenario and therefore cannot be set for all
			// scenarios
			if ((firstContinuousRunningHour.get(plantIndex) != null)
					&& (firstRunningHourIndex < -1)) {
				firstRunningHour = firstContinuousRunningHour.get(plantIndex);
			}

			// If out of market, last hour was the last hour in the market
			if (!isInMarketBasedOnVarCosts(scenarioIndex, firstRunningHourIndex)) {
				firstRunningHour = firstRunningHourIndex + 1;

				// Set value, so it does not have to be checked again for
				// another scenario
				// Only do this for hour < -1, because change from 0 to -1 is
				// depending on scenario and therefore cannot be set for all
				// scenarios
				if (firstRunningHourIndex < -1) {
					firstContinuousRunningHour.put(plantIndex, firstRunningHour);
				}
			}

			firstRunningHourIndex--;
		}

		if (firstRunningHour == null) {
			firstRunningHour = -Date.WARM_STARTUP_LENGTH;
		}

		return firstRunningHour;
	}

	/**
	 * Find last hour where plant is not running assuming plant is not running
	 * that current hour is running.
	 *
	 * @param scenarioIndex
	 * @param hour
	 * @return
	 */
	private int calculateLastContinuousNotRunningHour(int scenarioIndex, int hour) {

		Integer lastNotRunningHour = null;
		int lastNotRunningHourIndex = hour + 1;
		final int maxLength = forecastLength;

		while ((lastNotRunningHour == null) && (lastNotRunningHourIndex <= forecastLength)) {

			// if out of market, last hour was the last hour in-the-market
			if (isInMarketBasedOnVarCosts(scenarioIndex, lastNotRunningHourIndex)) {
				lastNotRunningHour = lastNotRunningHourIndex - 1;
			}
			lastNotRunningHourIndex++;
		}

		if (lastNotRunningHour == null) {
			lastNotRunningHour = maxLength;
		}

		return lastNotRunningHour;
	}

	/**
	 * Find the last hour starting from <code>hour</code> from current scenario,
	 * where the plant is continuously running.
	 *
	 * E.g. if <code>hour</code> is 6 and plant is running from 2-6 hours, 6 is
	 * returned.
	 *
	 * Only forecast length hours are regarded, afterwards WILL_BE_RUNNING is
	 * regarded.
	 *
	 * @param scenarioIndex
	 * @param hour
	 * @return
	 */
	private int calculateLastContinuousRunningHour(int scenarioIndex, int hour) {

		Integer lastRunningHour = null;
		int lastRunningHourIndex = hour + 1;

		while ((lastRunningHour == null) && (lastRunningHourIndex <= forecastLength)) {
			// if out of market, last hour was the last hour in-the-market
			if (!isInMarketBasedOnVarCosts(scenarioIndex, lastRunningHourIndex)) {
				lastRunningHour = lastRunningHourIndex - 1;
			}
			lastRunningHourIndex++;
		}

		return lastRunningHour;
	}

	/**
	 * Calculate the probability that a power plant is in the market. The
	 * probability is equal to the number of times a power plant has a lower bid
	 * price than the current market price divided by number of scenarios.
	 *
	 * This <b>cannot</b> be taken for the probability that power plant is in
	 * the market for <i>x</i> hours, cause this probability is path specific.
	 * This is done in calculateProbabilitiesPeriods and stored in
	 * inMarketProbPeriods.
	 *
	 */
	private void calculateProbabilitiesBasedOnBids() {
		for (int hour = 0; hour < forecastLength; hour++) {
			float probability = 0f;
			if (!bids.get(plantIndex).get(hour).isEmpty()) {
				for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
					// See if power plant is in the market at given price
					if ((prices.get(scenarioIndex).get(hour) + EPSILON) >= bids.get(plantIndex)
							.get(hour).get(HOURLY_BID_TYPE.NORMAL).get(0).getPrice()) {
						if (numberOfScenarios == 1) {
							probability = 1f;
						} else {
							probability += prices.get(scenarioIndex).getProbability();
						}
					}
				}
			}
			inMarketProbBasedOnBids.get(plantIndex).put(hour, probability);
		}
	}

	/**
	 * Calculate the probability that a power plant is in the market. The
	 * probability is equal to the number of times a power plant is running
	 * according to the outcome of the price forecast divided by number of
	 * scenarios.
	 */
	private void calculateProbabilitiesBasedOnProductionForecast() {

		for (int hour = 0; hour < forecastLength; hour++) {
			float probability = 0;
			for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
				// See if power plant is in the market according to the outcome
				// of the price forecast
				if (productionForecast.get(scenarioIndex).get(powerPlantsAvail.get(plantIndex))
						.get(hour) > 0) {
					if (numberOfScenarios == 1) {
						probability = 1f;
					} else {
						probability += prices.get(scenarioIndex).getProbability();
					}
				}
			}
			inMarketProbBasedOnVarCosts.get(plantIndex).put(hour, probability);
		}
	}

	/**
	 * Calculate the probability that a power plant is in the market. The
	 * probability is equal to the number of times a power plant has a lower bid
	 * price than the current market price divided by number of scenarios.
	 *
	 * This <b>cannot</b> be taken used for the probability that power plant is
	 * in the market for <i>x</i> hours, cause this probability is path
	 * specific. This is done in calculateProbabilitiesPeriods and stored in
	 * inMarketProbPeriods.
	 *
	 */
	private void calculateProbabilitiesBasedOnVarCosts() {

		for (int hour = 0; hour < forecastLength; hour++) {
			float probability = 0;
			for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
				// See if power plant is in the market at given price
				if ((prices.get(scenarioIndex).get(hour) + EPSILON) >= variableCosts
						.get(plantIndex)) {
					if (numberOfScenarios == 1) {
						probability = 1f;
					} else {
						probability += prices.get(scenarioIndex).getProbability();
					}
				}
			}
			inMarketProbBasedOnVarCosts.get(plantIndex).put(hour, probability);
		}
	}

	/**
	 * Calculate the start-up costs for an hour for a specific scenario, if the
	 * plant would start in that hour.
	 */
	private void calculateStartUpCosts() {
		for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {

			// Check if last power plant was already running all the time, then
			// this will run all the time too
			if (!useProductionForecast && runningAllTimeScenario.get(scenarioIndex)) {
				for (int continuousHour = 0; continuousHour < forecastLength; continuousHour++) {
					startUpCosts.get(plantIndex).get(scenarioIndex).set(continuousHour, 0f);
					runningHours.get(plantIndex).get(scenarioIndex).set(continuousHour, 0);
				}
				continue;
			}

			int hour = 0;
			while (hour < forecastLength) {

				// Set costs for all hours of period where plant is continuously
				// running
				if (isInMarketBasedOnVarCosts(scenarioIndex, hour)) {

					// Find first continuous hour where plant was running
					final Integer firstRunningHour = calculateFirstContinuousRunningHour(
							scenarioIndex, hour);

					// Find last continuous hour where plant will be running
					final Integer lastRunningHour = calculateLastContinuousRunningHour(
							scenarioIndex, hour);

					// Bids are made only for forecast period - not for the past
					// or beyond forecast length
					final int startHour = Math.max(0, firstRunningHour);
					final int endHour = Math.min(forecastLength - 1, lastRunningHour);

					if ((startHour == 0) && (endHour == (forecastLength - 1))) {
						runningAllTimeScenario.set(scenarioIndex, true);
					}

					// check costs for next period
					hour = lastRunningHour + 1;

					// Check if costs have to be regarded, then distribute costs
					// evenly over period
					if (((lastRunningHour - firstRunningHour) + 1) < MAX_PERIOD_LENGTH) {

						// Find last hour where plant was running before current
						// continuous running period
						final int firstContinuousNotRunning = calculateFirstContinuousNotRunningHour(
								scenarioIndex, firstRunningHour);

						// Get startup costs
						float startCosts = calculateStartUpCostsViaLength(
								firstRunningHour - firstContinuousNotRunning);

						// If plant is running yesterday than no start-up cost
						// occur
						if ((startHour == 0)
								&& powerPlantsAvail.get(plantIndex).isRunningHour(-1)) {
							startCosts = 0f;
						}

						// Calculate how long costs have to be distributed.
						final int length = (lastRunningHour - firstRunningHour) + 1;

						// Set start-up costs for whole period
						for (int continuousHour = startHour; continuousHour <= endHour; continuousHour++) {
							startUpCosts.get(plantIndex).get(scenarioIndex).set(continuousHour,
									startCosts / length);
							runningHours.get(plantIndex).get(scenarioIndex).set(continuousHour,
									length);

						}

					} else {
						// plants runs longer than MAX_PERIOD_LENGTH no start-up
						// costs are regarded
						for (int continuousHour = startHour; continuousHour <= endHour; continuousHour++) {
							startUpCosts.get(plantIndex).get(scenarioIndex).set(continuousHour, 0f);
						}
					}

				} else {

					// Set costs for period where plant is not running
					final Integer firstContinuousNotRunning = calculateFirstContinuousNotRunningHour(
							scenarioIndex, hour);

					final Integer lastContinuousNotRunning = calculateLastContinuousNotRunningHour(
							scenarioIndex, hour);

					// Bids are made only for forecast period - not for the past
					// or beyond forecast length
					final int startHour = Math.max(0, firstContinuousNotRunning);
					final int endHour = Math.min(forecastLength - 1, lastContinuousNotRunning);
					hour = lastContinuousNotRunning + 1;

					for (int continuousHour = startHour; continuousHour <= endHour; continuousHour++) {
						// e.g. startHour = 5, continuousHour = 6 ->
						// lastRunningHour = 4 = (startHour-1), length = (6-4)-1
						// = 1
						final int length = continuousHour - (firstContinuousNotRunning - 1) - 1;
						// Elsewise length would be 0 and no start-up cost
						// will be added
						float startCosts = calculateStartUpCostsViaLength(length);

						// If plant was running yesterday no start-up costs
						// occur in first hour
						if ((continuousHour == 0)
								&& powerPlantsAvail.get(plantIndex).isRunningHour(-1)) {
							startCosts = 0f;
						}

						// Costs if plant is not running is saved in different
						// list
						startUpCostsNotRunning.get(plantIndex).get(scenarioIndex)
								.set(continuousHour, startCosts);
						startUpCostsNotRunningCounter.get(plantIndex).set(continuousHour,
								startUpCostsNotRunningCounter.get(plantIndex).get(continuousHour)
										+ 1);
					}
				}
			}
		}
	}

	/**
	 * Calculate the average start-up costs for each hours for current power
	 * plant. The average start-up costs are equal to the arithmetic average
	 * over all scenarios.
	 */
	private void calculateStartUpCostsAvg() {

		for (int hour = 0; hour < forecastLength; hour++) {

			float startCosts = 0f;
			float runningHoursCurrentHour = 0f;

			if (startUpCostsNotRunningCounter.get(plantIndex).get(hour) == numberOfScenarios) {
				// Plant is not supposed to be in the market in all scenarios
				for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
					if (numberOfScenarios == 1) {
						startCosts = startUpCostsNotRunning.get(plantIndex).get(scenarioIndex)
								.get(hour);
					} else {
						startCosts += startUpCostsNotRunning.get(plantIndex).get(scenarioIndex)
								.get(hour) * prices.get(scenarioIndex).getProbability();
					}
				}
			} else {

				// Plant is supposed to be in the market in at least one
				// scenario
				float propTotalWithCosts = 0f;
				for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
					final float startUpCostScenario = startUpCosts.get(plantIndex)
							.get(scenarioIndex).get(hour);
					// if (startUpCostScenario > 0) {
					if (isInMarketBasedOnVarCosts(scenarioIndex, hour)) {
						if (numberOfScenarios == 1) {
							startCosts = startUpCostScenario;
							propTotalWithCosts = 1f;
							runningHoursCurrentHour = runningHours.get(plantIndex)
									.get(scenarioIndex).get(hour);
						} else {
							startCosts += startUpCostScenario
									* prices.get(scenarioIndex).getProbability();
							propTotalWithCosts += prices.get(scenarioIndex).getProbability();
							runningHoursCurrentHour += runningHours.get(plantIndex)
									.get(scenarioIndex).get(hour)
									* prices.get(scenarioIndex).getProbability();
						}
					}

				}

				// calculate expected profitability, meaning if plant gets in
				// market only once than but else not, than it should bid it
				// total starting costs and not just costs * probability of
				// scenario
				if ((startCosts > 0) && (propTotalWithCosts > 0)) {
					startCosts /= propTotalWithCosts;
					runningHoursCurrentHour /= propTotalWithCosts;
				}

			}

			if (startCosts > 1000) {
				logger.error("Start-up costs are too high!");
			}

			startUpCostsAvg.get(plantIndex).set(hour, startCosts);
			runningHoursExpectedAvg.get(plantIndex).set(hour, runningHoursCurrentHour);

		}
	}

	/**
	 * @param hours
	 *            the number of hours a plant is not running [0,infinity]
	 * @return The marginal start-up costs based on the number of hours the
	 *         plant is not running before the start.
	 */
	private float calculateStartUpCostsViaLength(int hoursNotRunning) {
		final float costs;
		int hours = hoursNotRunning;

		// If hours are negative, use positive value since length cannot be
		// negative
		if (hoursNotRunning < 0) {
			logger.error("A time length cannot be negative. Assuming positive value is meant.");
			hours = Math.abs(hoursNotRunning);
		}

		if (hours < 1) {
			costs = 0;
		} else if (hours < Date.HOT_STARTUP_LENGTH) {
			costs = startUpCostsHot.get(plantIndex);
		} else if (hours < Date.WARM_STARTUP_LENGTH) {
			costs = startUpCostsWarm.get(plantIndex);
		} else {
			costs = startUpCostsCold.get(plantIndex);
		}

		return costs;
	}

	// This method that only serves for testing
	private void checkBids() {
		final Set<Integer> hours = new TreeSet<>();

		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			hours.add(hourOfDay);
		}
		// Check max and min price
		final float maxPrice = marketArea.getDayAheadMarketOperator().getMaxPriceAllowed();
		final float minPrice = marketArea.getDayAheadMarketOperator().getMinPriceAllowed();
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			if (bids.get(plantIndex).get(hourOfDay) != null) {
				for (final List<HourlyBidPower> bidList : bids.get(plantIndex).get(hourOfDay)
						.values()) {
					for (final HourlyBidPower bidPoint : bidList) {
						if (!bidPoint.isValid(minPrice, maxPrice)) {
							final float price = bidPoint.getPrice();
							if (price < minPrice) {
								bidPoint.setPrice(minPrice);
							}
							if (price > maxPrice) {
								bidPoint.setPrice(maxPrice * 0.95f);
							}
						}
					}
				}

			}
		}
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			if (bids.get(plantIndex).get(hourOfDay) != null) {
				for (final List<HourlyBidPower> bidList : bids.get(plantIndex).get(hourOfDay)
						.values()) {
					for (final HourlyBidPower bidPoint : bidList) {
						production.put(hourOfDay, production.get(hourOfDay) + bidPoint.getVolume());
						hours.remove(hourOfDay);
					}
				}

			}

		}

		// Check nuclear
		if ((powerPlantsAvail.get(plantIndex).getFuelType() == FuelType.URANIUM)
				&& (isNeverInMarketBasedOnBids())) {
			logger.warn(marketArea.getInitialsBrackets()
					+ "Should usually not occur! URANIUM should run through! Start-up costs for power plant "
					+ powerPlantsAll.get(plantIndex).getUnitID());
		}

		final List<BlockBidPower> blockBidList = blockBids.get(plantIndex);
		for (final BlockBidPower blockBid : blockBidList) {
			for (int hourOfDay = blockBid.getStart(); hourOfDay <= blockBid.getEnd(); hourOfDay++) {

				production.put(hourOfDay, production.get(hourOfDay) + blockBid.getVolume());
				hours.remove(hourOfDay);
			}
		}

		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			if (capacity.get(plantIndex).get(hourOfDay) <= 0) {
				hours.remove(hourOfDay);
			}
		}

		if (!hours.isEmpty()) {
			logger.error("Why is the set still not empty?");
		}

	}

	/**
	 * Calculate the periods where a power plant could be out of the market
	 * based on the bids.
	 *
	 * @return A list with tuples where the first value equals the hour where a
	 *         power plant could be out of market, e.g. [1,2] means in hour 0
	 *         power plant is definitely in the market, in hour 1 or 2 maybe,
	 *         but in hour 3 definitely again in the market.
	 */

	private List<Tuple<Integer, Integer>> determinePossibleOutOfMarketPeriods() {

		final List<Tuple<Integer, Integer>> startEndPoints = new ArrayList<>();

		boolean wasRunning = inMarketBefore.get(plantIndex).get(-1);
		int startRunningHour = wasRunning ? -1 : Integer.MIN_VALUE;

		for (int hour = 0; hour < forecastLength; hour++) {

			// Only hours where power plant is almost definitely in the market
			// are relevant.
			if (inMarketProbBasedOnBids.get(plantIndex)
					.get(hour) < PROBABILITY_AVOID_TURN_OFF_RUNNING) {
				continue;
			}

			// Extend current period
			if (wasRunning && ((hour - startRunningHour) <= 1)) {
				startRunningHour = hour;
				continue;
			}

			// Start of new period
			if (wasRunning && ((hour - startRunningHour) > 1)) {
				startEndPoints.add(new Tuple<>(startRunningHour + 1, hour - 1));
				startRunningHour = hour;
				continue;
			}

			// First start
			if (!wasRunning) {
				startRunningHour = hour;
				wasRunning = true;
				continue;
			}
		}

		return startEndPoints;

	}

	private void initialize() {

		// Initialize once for all power plants
		bids = new LinkedHashMap<>(powerPlantsAvail.size());
		blockBids = new LinkedHashMap<>(powerPlantsAvail.size());
		capacity = new LinkedHashMap<>(powerPlantsAvail.size());
		fuelType = new LinkedHashMap<>(powerPlantsAvail.size());
		inMarketProbBasedOnBids = new LinkedHashMap<>(powerPlantsAvail.size());
		inMarketProbBasedOnVarCosts = new LinkedHashMap<>(powerPlantsAvail.size());
		inMarketBefore = new LinkedHashMap<>(powerPlantsAvail.size());
		inMarketProbPeriods = new ArrayList<>(powerPlantsAvail.size());
		profit = new LinkedHashMap<>(powerPlantsAvail.size());

		startUpCosts = new ArrayList<>(powerPlantsAvail.size());
		runningHours = new ArrayList<>(powerPlantsAvail.size());
		startUpCostsNotRunning = new ArrayList<>(powerPlantsAvail.size());
		startUpCostsNotRunningCounter = new ArrayList<>(powerPlantsAvail.size());
		startUpCostsAvg = new ArrayList<>(powerPlantsAvail.size());
		runningHoursExpectedAvg = new ArrayList<>(powerPlantsAvail.size());

		startUpCostsCold = new ArrayList<>(powerPlantsAvail.size());
		startUpCostsHot = new ArrayList<>(powerPlantsAvail.size());
		startUpCostsWarm = new ArrayList<>(powerPlantsAvail.size());
		variableCosts = new ArrayList<>(powerPlantsAvail.size());

		firstContinuousNotRunningHour = new LinkedHashMap<>(powerPlantsAvail.size());
		firstContinuousRunningHour = new LinkedHashMap<>(powerPlantsAvail.size());

		tempLength = new ArrayList<>(numberOfScenarios);
		runningAllTimeScenario = new ArrayList<>(Collections.nCopies(numberOfScenarios, false));

		runningAllTime = new ArrayList<>(numberOfScenarios);
		for (int scenario = 0; scenario < numberOfScenarios; scenario++) {
			tempLength.add(new ArrayList<>(Collections.nCopies(forecastLength, 1)));
			runningAllTime.add(new ArrayList<>(Collections.nCopies(forecastLength, false)));
		}

		// Initialize once per plant
		for (int index = 0; index < powerPlantsAvail.size(); index++) {
			initializePlantValues(index);
		}

		production = new LinkedHashMap<>();
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			production.put(hourOfDay, 0f);
		}

	}

	/** Initialize all lists and values for each power plant. */
	private void initializePlantValues(int index) {

		// Initialize values
		final Plant plant = powerPlantsAvail.get(index);
		capacity.put(index, new LinkedHashMap<>());

		final int firstHourOfToday = Date.getFirstHourOfToday();
		for (int hourOfDay = 0; hourOfDay < forecastLength; hourOfDay++) {
			capacity.get(index).put(hourOfDay,
					plant.getCapacityUnusedExpected(firstHourOfToday + hourOfDay));
		}

		fuelType.put(index, plant.getFuelType());

		// Technical restrictions of power plants
		if (useTechnicalRestrictions) {
			plantsTechnicalRestrictions.put(plant.getUnitID(),
					new TechnicalRestrictions(plant.getMinProduction(), plant.getMinRunTime()));
		} else {
			plantsTechnicalRestrictions.put(plant.getUnitID(), new TechnicalRestrictions(0f, 1));
		}

		// Add running hours so that
		final Map<Integer, Boolean> pastRunningHours = new LinkedHashMap<>(2 * HOURS_PER_DAY);
		for (int hourOfDay = -(2 * HOURS_PER_DAY); hourOfDay < 0; hourOfDay++) {
			pastRunningHours.put(hourOfDay, plant.isRunningHour(hourOfDay));
		}
		inMarketBefore.put(index, pastRunningHours);

		startUpCostsCold.add(marketArea.getStartUpCosts().getMarginalStartupCostsCold(plant));
		startUpCostsWarm.add(marketArea.getStartUpCosts().getMarginalStartupCostsWarm(plant));
		startUpCostsHot.add(marketArea.getStartUpCosts().getMarginalStartupCostsHot(plant));
		variableCosts.add(plant.getCostsVar());

		// initialize lists
		// should be one more than HOURS_PER_DAY since one extra bid is needed
		// to determine the profit for the last interval of the next day to see
		// if plant is running in HOURS_PER_DAY+1
		bids.put(index, new LinkedHashMap<Integer, Map<HOURLY_BID_TYPE, List<HourlyBidPower>>>(
				HOURS_PER_DAY + 1));
		// Initialize map as well
		for (int hour = 0; hour < forecastLength; hour++) {
			bids.get(index).put(hour, new LinkedHashMap<>(2));
		}

		blockBids.put(index, new ArrayList<BlockBidPower>(HOURS_PER_DAY + 1));
		inMarketProbBasedOnBids.put(index, new LinkedHashMap<Integer, Float>(forecastLength));
		inMarketProbBasedOnVarCosts.put(index, new LinkedHashMap<Integer, Float>(forecastLength));
		profit.put(index, new LinkedHashMap<Integer, Float>(numberOfScenarios));
		inMarketProbPeriods.add(new ArrayList<List<Float>>(forecastLength));

		// Initialize probability with zeros
		for (int hour = 0; hour < forecastLength; hour++) {
			inMarketProbPeriods.get(index)
					.add(new ArrayList<>(Collections.nCopies(MAX_PERIOD_LENGTH + 1, 0f)));
		}

		startUpCosts.add(new ArrayList<List<Float>>(numberOfScenarios));
		// Initialize values with zeros
		for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
			startUpCosts.get(index).add(new ArrayList<>(Collections.nCopies(forecastLength, 0f)));
		}

		startUpCostsNotRunning.add(new ArrayList<List<Float>>(numberOfScenarios));
		// Initialize values with zeros
		for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
			startUpCostsNotRunning.get(index)
					.add(new ArrayList<>(Collections.nCopies(forecastLength, 0f)));
		}
		startUpCostsNotRunningCounter.add(new ArrayList<>(Collections.nCopies(forecastLength, 0)));

		startUpCostsAvg.add(new ArrayList<Float>(numberOfScenarios));
		for (int hour = 0; hour < forecastLength; hour++) {
			startUpCostsAvg.get(index).add(0f);
		}

		runningHours.add(new ArrayList<List<Integer>>(numberOfScenarios));
		// Initialize values with zeros
		for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios; scenarioIndex++) {
			runningHours.get(index).add(new ArrayList<>(Collections.nCopies(forecastLength, 0)));
		}

		runningHoursExpectedAvg.add(new ArrayList<Float>(numberOfScenarios));
		for (int hour = 0; hour < forecastLength; hour++) {
			runningHoursExpectedAvg.get(index).add(0f);
		}

	}

	/**
	 * Return true if plant is running for current hour and false elsewise.
	 *
	 * @param scenario
	 *            The requested scenario.
	 * @param hour
	 *            The requested hour of the day.
	 *
	 * @return Status of power plant in hour for given scenario. Either
	 *         running(true)/not running(false)
	 */
	private boolean isInMarketBasedOnBids(int scenario, int hour) {

		boolean indexElement = false;
		try {
			// If plant is longer out of market than warm start-up it has to be
			// a cold start-up
			if (hour < -(Date.WARM_STARTUP_LENGTH + 1)) {
				indexElement = WAS_RUNNING;
			} else if (hour < 0) {
				// at the moment inMarketBefore contains hours for two days,
				// could be added more time
				indexElement = inMarketBefore.get(plantIndex).get(hour);
			} else if (hour >= forecastLength) {
				indexElement = WILL_BE_RUNNING;
			} else if ((prices.get(scenario).get(hour) >= bids.get(plantIndex).get(hour)
					.get(HOURLY_BID_TYPE.NORMAL).get(0).getPrice())) {
				indexElement = true;
			} else {
				indexElement = false;
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return indexElement;
	}

	/**
	 * Return true if plant is running for current hour and false elsewise.
	 *
	 * @param scenario
	 *            The requested scenario.
	 * @param hour
	 *            The requested hour of the day.
	 *
	 * @return Status of power plant in hour for given scenario. Either
	 *         running(true)/not running(false)
	 */
	private boolean isInMarketBasedOnVarCosts(int scenario, int hour) {

		final boolean indexElement;

		if (hour < -(2 * HOURS_PER_DAY)) {
			indexElement = WAS_RUNNING;
		} else if (hour < 0) {
			// at the moment inMarketBefore contains hours for two days, could
			// be added more time
			indexElement = inMarketBefore.get(plantIndex).get(hour);
		} else if (hour >= forecastLength) {
			indexElement = WILL_BE_RUNNING;
		} else if (!useProductionForecast
				&& (prices.get(scenario).get(hour) > variableCosts.get(plantIndex))) {
			indexElement = true;
		} else if (useProductionForecast && (productionForecast.get(scenario)
				.get(powerPlantsAvail.get(plantIndex)).get(hour) > 0)) {
			indexElement = true;
		} else {
			indexElement = false;
		}

		return indexElement;
	}

	/**
	 * @return True, if plant is never in market based on bids and forecasted
	 *         prices.
	 */
	private boolean isNeverInMarketBasedOnBids() {

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			if (inMarketProbBasedOnBids.get(plantIndex).get(hour) > 0) {
				return false;
			}
		}
		return true;
	}

	private void logBids() {

		final Map<Integer, Map<Integer, String>> prices = new LinkedHashMap<>();
		final Map<Integer, Map<Integer, String>> startCosts = new LinkedHashMap<>();
		final Map<Integer, Map<Integer, String>> varCosts = new LinkedHashMap<>();

		// Write information about hourly bids into maps

		for (final Map<Integer, Map<HOURLY_BID_TYPE, List<HourlyBidPower>>> bidsHourlyByType : bids
				.values()) {
			for (final Map<HOURLY_BID_TYPE, List<HourlyBidPower>> bidsHourByType : bidsHourlyByType
					.values()) {
				for (final List<HourlyBidPower> bidList : bidsHourByType.values()) {
					for (final HourlyBidPower bid : bidList) {
						final Integer id = bid.getPlant().getUnitID();
						final int hour = bid.getHour();
						final float price = bid.getPrice();
						final float startCost = bid.getStartupCosts();
						final float varCost = bid.getStartupCosts();

						if (!prices.containsKey(id)) {
							prices.put(id, new LinkedHashMap<Integer, String>());
							startCosts.put(id, new LinkedHashMap<Integer, String>());
							varCosts.put(id, new LinkedHashMap<Integer, String>());
						}

						prices.get(id).put(hour, Float.toString(price));
						startCosts.get(id).put(hour, Float.toString(startCost));
						varCosts.get(id).put(hour, Float.toString(varCost));
					}
				}
			}
		}

		// Write information about block bids into maps
		for (final List<BlockBidPower> bids : blockBids.values()) {
			for (final BlockBidPower bid : bids) {
				final Integer id = bid.getPlant().getUnitID();
				final int startHour = bid.getStart();
				final int endHour = bid.getEnd();
				final int length = bid.getLength();

				final float price = bid.getPrice();
				final float startCost = bid.getStartupCosts() / length;
				final float varCost = bid.getFuelCosts() + bid.getEmissionCosts()
						+ bid.getOperAndMainCosts();

				if (!prices.containsKey(id)) {
					prices.put(id, new LinkedHashMap<Integer, String>());
					startCosts.put(id, new LinkedHashMap<Integer, String>());
					varCosts.put(id, new LinkedHashMap<Integer, String>());
				}

				for (int hour = startHour; hour <= endHour; hour++) {
					prices.get(id).put(hour, Float.toString(price) + "b");
					startCosts.get(id).put(hour, Float.toString(startCost) + "b");
					varCosts.get(id).put(hour, Float.toString(varCost));
				}
			}
		}

		// Sort by id so that everything has the same order for each call
		// Maybe easier to just give list
		Collections.sort(powerPlantsAll,
				(Plant o1, Plant o2) -> Integer.compare(o1.getUnitID(), o2.getUnitID()));

		// Write everything into file
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			final List<Object> line = new ArrayList<>();

			final int hourOfYear = Date.getFirstHourOfToday() + hour;
			line.add(Integer.toString(hourOfYear));
			// Write
			for (final Plant plant : powerPlantsAll) {
				if (prices.containsKey(plant.getUnitID())) {
					line.add(prices.get(plant.getUnitID()).get(hour));
					line.add(startCosts.get(plant.getUnitID()).get(hour));
				} else {
					line.add(null);
					line.add(null);
				}
			}

			for (final ScenarioList<Float> scenario : this.prices) {
				line.add(scenario.getValues().get(hour));
			}

			// Write line
			LoggerXLSX.writeLine(logId, line);
		}

	}

	/** Lower bid to avoid turning off a power plant. */
	private void makeAvoidedTurnOffBids() {

		final Plant plant = powerPlantsAvail.get(plantIndex);
		// Only lower bids for base load power plants
		if (!(plant.isMustrun() && (Settings.getMustrunYearEnd() <= Date.getYear()))
				&& (plant.getFuelType() != FuelType.URANIUM)
				&& (plant.getFuelType() != FuelType.LIGNITE)
				&& (plant.getFuelType() != FuelType.CLEAN_LIGNITE)) {
			return;
		}
		// If plant has an outage don't lower the bids
		for (int hour = 0; hour < forecastLength; hour++) {
			if (capacity.get(plantIndex).get(hour) <= 0) {
				replaceHourlyBid(0, HOURS_PER_DAY - 1, 0f);
				return;
			}
		}
		// If power plant is never in the market today, but has been running in
		// the last hour of yesterday: Assume that warm start-up on the day
		// after can be avoided and reduce bid for every hour of today. Two bids
		// will be created: Minimum running capacity at variable costs minus
		// avoided start-up costs, additional capacity at variable costs.
		if (isNeverInMarketBasedOnBids() && plant.isRunningHour(-1)) {
			final Map<Integer, Float> volumeNew = new LinkedHashMap<>();
			final float expStartupCosts = startUpCostsWarm.get(plantIndex);
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				if ((plant.getFuelName() == FuelName.URANIUM)
						|| (plant.getFuelName() == FuelName.LIGNITE)) {
					// Increase minimum capacity for Uranium and Lignite due to
					// balancing markets
					volumeNew.put(hour, Math.min(
							plantsTechnicalRestrictions.get(plant.getUnitID()).getMinProduction()
									* URANIUM_MIN_PRODUCTION_INCREASE,
							plant.getCapacityUnusedUnexpected(Date.getFirstHourOfToday() + hour)));
				} else {
					volumeNew.put(hour, Math.min(
							plantsTechnicalRestrictions.get(plant.getUnitID()).getMinProduction(),
							plant.getCapacityUnusedUnexpected(Date.getFirstHourOfToday() + hour)));
				}
			}
			replaceHourlyBidPartial(0, HOURS_PER_DAY - 1, expStartupCosts, volumeNew);
			return;
		}

		if (!isNeverInMarketBasedOnBids() && plant.isRunningHour(-1)) {
			final boolean[] alreadyReplaced = new boolean[forecastLength];
			final List<Tuple<Integer, Integer>> startEndPoints = determinePossibleOutOfMarketPeriods();

			for (final Tuple<Integer, Integer> startEnd : startEndPoints) {
				final int start = startEnd.getX();

				// Only block bids for today are needed
				if (start >= HOURS_PER_DAY) {
					continue;
				}

				final int end = startEnd.getY();
				final int length = (end - start) + 1;

				BiddingAlgorithm.addAvoidShutdownTotal(
						powerPlantsAvail.get(plantIndex).getFuelType(), length);

				final float countNotRunningProb = calcNotRunningProbBasedOnBids(start, end);
				if (countNotRunningProb <= PROBABILITY_AVOID_TURN_OFF_NOT_RUNNING) {
					continue;
				}

				final float expStartupCosts = calculateStartUpCostsViaLength((end - start) + 1)
						* countNotRunningProb;
				BiddingAlgorithm.addAvoidShutdownReal(
						powerPlantsAvail.get(plantIndex).getFuelType(), length);

				final Map<Integer, Float> volumeNew = new LinkedHashMap<>();
				for (int hour = start; hour <= end; hour++) {
					volumeNew.put(hour, Math.min(
							plantsTechnicalRestrictions.get(plant.getUnitID()).getMinProduction(),
							plant.getCapacityUnusedUnexpected(Date.getFirstHourOfToday() + hour)));
					alreadyReplaced[hour] = true;
				}
				replaceHourlyBidPartial(start, end, expStartupCosts, volumeNew);
			}

			// Lower bids in hours with formerly expected start-ups to only
			// variable costs, since bidding below marginal costs in
			// out-of-market periods likely leads to these start-up costs being
			// avoided.
			int start = -1;
			int end = -1;

			for (int hour = 0; hour < forecastLength; hour++) {
				if (!alreadyReplaced[hour] && (start == -1)) {
					start = hour;
					end = hour;
				} else if (!alreadyReplaced[hour]) {
					end = hour;
				}

				if (((start != -1) && alreadyReplaced[hour]) || (hour == (forecastLength - 1))) {
					replaceHourlyBid(start, end, 0f);
					start = -1;
					end = -1;
				}
			}
			return;
		}
		// Bid for minimum Production
		for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
			bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.NORMAL, new ArrayList<>());
			float volume = 0;
			// if stepping algorithm is of, just use full capacity
			if (steppingAlgorithm) {
				volume = Math.min(
						plantsTechnicalRestrictions.get(plant.getUnitID()).getMinProduction(),
						plant.getCapacityUnusedUnexpected(Date.getFirstHourOfToday() + hour));
			} else {
				volume = capacity.get(plantIndex).get(hour);
			}

			float price = Math.max(
					marketArea.getDayAheadMarketOperator().getMinPriceAllowed() + priceIncrease,
					variableCosts.get(plantIndex));
			bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.NORMAL)
					.add(new HourlyBidPower.Builder(volume, price, hour, BidType.SELL, marketArea)
							.traderType(TraderType.SUPPLY)
							.comment("MinimumPriceBid Avoid turnOff length. UnitID: "
									+ powerPlantsAvail.get(plantIndex).getUnitID())
							.emissionCosts(powerPlantsAvail.get(plantIndex).getCostsCarbonVar())
							.fuelCosts(powerPlantsAvail.get(plantIndex).getCostsFuelVar())
							.operAndMainCosts(powerPlantsAvail.get(plantIndex)
									.getCostsOperationMaintenanceVar())
							.fuelType(fuelType.get(plantIndex))
							.plant(powerPlantsAvail.get(plantIndex))
							.startupCosts(price - variableCosts.get(plantIndex)).build());

			final float productionMinMax = capacity.get(plantIndex).get(hour) - volume;

			price = Math.max(
					marketArea.getDayAheadMarketOperator().getMinPriceAllowed() + priceIncrease,
					variableCosts.get(plantIndex));
			final float startUp = (price - variableCosts.get(plantIndex));
			if ((productionMinMax) == 0) {
				break;
			}

			if (!bids.get(plantIndex).get(hour).containsKey(HOURLY_BID_TYPE.DIFF)) {
				bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.DIFF, new ArrayList<>());
			}
			bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.DIFF)
					.add(new HourlyBidPower.Builder(productionMinMax, price, hour, BidType.SELL,
							marketArea)
									.traderType(TraderType.SUPPLY)
									.comment("Stepping price bid for nuclear and lignite. UnitID:"
											+ powerPlantsAvail.get(plantIndex).getUnitID())
									.emissionCosts(
											powerPlantsAvail.get(plantIndex).getCostsCarbonVar())
									.fuelCosts(powerPlantsAvail.get(plantIndex).getCostsFuelVar())
									.operAndMainCosts(powerPlantsAvail.get(plantIndex)
											.getCostsOperationMaintenanceVar())
									.fuelType(fuelType.get(plantIndex)).startupCosts(startUp)
									.plant(powerPlantsAvail.get(plantIndex)).build());

		}
	}

	/**
	 * Calculate the bids for each hour via adding the expected startup costs to
	 * the variable costs for a power plant.
	 */
	private void makeHourlyBids() {

		for (int hour = 0; hour < forecastLength; hour++) {

			if (capacity.get(plantIndex).get(hour) <= 0) {
				continue;
			}

			final float startUpCosts = startUpCostsAvg.get(plantIndex).get(hour);

			final StringBuffer probability = new StringBuffer();
			if (!speedUp) {
				if (hour > 1) {
					probability.append("hour " + (hour - 2) + ", prob " + numberFormat.get()
							.format(inMarketProbBasedOnVarCosts.get(plantIndex).get(hour - 2)));
				}
				if (hour > 0) {
					probability.append(", hour " + (hour - 1) + ", prob " + numberFormat.get()
							.format(inMarketProbBasedOnVarCosts.get(plantIndex).get(hour - 1)));
				}
				probability.append(", hour " + hour + ", prob " + numberFormat.get()
						.format(inMarketProbBasedOnVarCosts.get(plantIndex).get(hour)));
				if ((hour + 1) < forecastLength) {
					probability.append(", hour " + (hour + 1) + ", prob " + numberFormat.get()
							.format(inMarketProbBasedOnVarCosts.get(plantIndex).get(hour + 1)));
				}
				if ((hour + 2) < forecastLength) {
					probability.append(", hour " + (hour + 2) + ", prob " + numberFormat.get()
							.format(inMarketProbBasedOnVarCosts.get(plantIndex).get(hour + 2)));
				}
				probability.append("\\n Startup Costs: Hot "
						+ numberFormat.get().format(startUpCostsHot.get(plantIndex)) + ", Warm "
						+ numberFormat.get().format(startUpCostsWarm.get(plantIndex)) + ", Cold "
						+ numberFormat.get().format(startUpCostsCold.get(plantIndex)));
				probability.append("\\nHour: " + hour);
			}

			final float totalCosts = variableCosts.get(plantIndex) + startUpCosts;

			final Plant plant = powerPlantsAvail.get(plantIndex);

			// all the remaining market areas and plants
			// Bid for minimum Production
			final float minimumVolume = Math.min(
					plantsTechnicalRestrictions.get(plant.getUnitID()).getMinProduction(),
					plant.getCapacityUnusedUnexpected(Date.getFirstHourOfToday() + hour));
			if (minimumVolume > 0) {
				// Avoid zero bid
				// To avoid arrayindex out of bounds only inside the if else
				// clause

				float bidPrice = Math.max(
						marketArea.getDayAheadMarketOperator().getMinPriceAllowed() + priceIncrease,
						totalCosts);
				if ((plant.getFuelType() == FuelType.URANIUM)
						|| (plant.getFuelType() == FuelType.LIGNITE)) {
					bidPrice = (marketArea.getDayAheadMarketOperator().getMinPriceAllowed() / 2);
				}
				// No mustrun conditions, no plant will bid negative prices
				if ((Settings.getMustrunYearEnd() < Date.getYear())) {
					bidPrice = Math.max(0.001f, bidPrice);
				}
				bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.NORMAL, new ArrayList<>());
				bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.NORMAL)
						.add(new HourlyBidPower.Builder(minimumVolume, bidPrice, hour, BidType.SELL,
								marketArea)
										.traderType(TraderType.SUPPLY)
										.comment("MinimumPriceBid. UnitID: "
												+ powerPlantsAvail.get(plantIndex).getUnitID())
										.emissionCosts(powerPlantsAvail
												.get(plantIndex).getCostsCarbonVar())
										.startupCosts(startUpCosts)
										.fuelCosts(
												powerPlantsAvail.get(plantIndex).getCostsFuelVar())
										.operAndMainCosts(powerPlantsAvail.get(plantIndex)
												.getCostsOperationMaintenanceVar())
										.fuelType(fuelType.get(plantIndex))
										.plant(powerPlantsAvail.get(plantIndex)).build());
			}
			final float productionMinMax = capacity.get(plantIndex).get(hour) - minimumVolume;
			float price = Math.max(
					marketArea.getDayAheadMarketOperator().getMinPriceAllowed() + priceIncrease,
					totalCosts);

			final float volume = productionMinMax;

			if (volume <= 0) {
				break;
			}
			if (!bids.get(plantIndex).get(hour).containsKey(HOURLY_BID_TYPE.NORMAL)) {
				bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.NORMAL, new ArrayList<>());
			}
			bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.NORMAL)
					.add(new HourlyBidPower.Builder(volume, price, hour, BidType.SELL, marketArea)
							.traderType(TraderType.SUPPLY)
							.comment("Stepping price bid. UnitID: "
									+ powerPlantsAvail.get(plantIndex).getUnitID())
							.emissionCosts(powerPlantsAvail.get(plantIndex).getCostsCarbonVar())
							.fuelCosts(powerPlantsAvail.get(plantIndex).getCostsFuelVar())
							.operAndMainCosts(powerPlantsAvail.get(plantIndex)
									.getCostsOperationMaintenanceVar())
							.startupCosts(startUpCosts).fuelType(fuelType.get(plantIndex))
							.plant(powerPlantsAvail.get(plantIndex)).build());

		}
	}

	/**
	 * Remove all the bids that were made for the day after tomorrow. These bids
	 * were needed for the making the day-ahead bids, since for the start-up
	 * costs it has to checked if a power plant is producing on the day after
	 * tomorrow or not.
	 */
	private void removeAuxBids() {
		bids.get(plantIndex).entrySet().removeIf(e -> e.getKey() > HOURS_PER_DAY);
	}

	/**
	 * Remove hourly bid(s) in current interval and depending on the size of the
	 * interval create either new hourly bid(s) or a block bid.
	 *
	 * @param start
	 *            [0, 23]
	 * @param end
	 *            [0, 23]
	 * @param expStartupCosts
	 *            - the avoided startup costs by which the variable costs are
	 *            lowered [0, infinity)
	 *
	 */
	private void replaceHourlyBid(int start, int end, float expStartupCosts) {
		replaceHourlyBid(start, end, expStartupCosts, capacity.get(plantIndex));
	}

	/**
	 * Remove hourly bid(s) in current interval and depending on the size of the
	 * interval create either new hourly bid(s) or a block bid.
	 *
	 * @param start
	 *            [0, 23]
	 * @param end
	 *            [0, 23]
	 * @param expStartupCosts
	 *            - the avoided startup costs by which the variable costs are
	 *            lowered [0, infinity)
	 *
	 */
	private void replaceHourlyBid(int start, int end, float expStartupCosts,
			Map<Integer, Float> volumeNew) {

		// Take total avoid costs but only include part of them in todays bid
		// The part lenghtToday/lengthTotal
		final int endToday = Math.min(HOURS_PER_DAY - 1, end);

		final int lengthTotal = (end - start) + 1;
		final int lengthToday = (endToday - start) + 1;

		if (expStartupCosts < 0) {
			logger.error("Startup costs need to be positive!");
		}

		if (Float.isInfinite(expStartupCosts)) {
			logger.error("Startup costs are to high!");
		}

		// market coupling does not support block bids yet
		if ((lengthToday == 1) || (numberOfBlockBidsCurrently >= numberOfBlockBidsMaximum)
				|| marketArea.isMarketCoupling()) {

			for (int hour = start; hour <= endToday; hour++) {
				bids.get(plantIndex).get(hour).remove(HOURLY_BID_TYPE.NORMAL);
			}

			for (int hour = start; hour <= endToday; hour++) {

				float volume = 0;
				if (steppingAlgorithm) {
					volume = Math.min(plantsTechnicalRestrictions
							.get(powerPlantsAvail.get(plantIndex).getUnitID()).getMinProduction(),
							powerPlantsAvail.get(plantIndex).getCapacityUnusedUnexpected(
									Date.getFirstHourOfToday() + hour));
				} else {
					// if no stepping algorithm is active use full capacity
					volume = capacity.get(plantIndex).get(hour);
				}

				// Make hourly bid with lowered price
				final float startUpInBid = (expStartupCosts / lengthTotal);
				float bidPrice = Math.max(
						marketArea.getDayAheadMarketOperator().getMinPriceAllowed() + priceIncrease,
						variableCosts.get(plantIndex) - startUpInBid);

				// No mustrun conditions, no plant will bid negative prices
				if ((Settings.getMustrunYearEnd() < Date.getYear())) {
					bidPrice = Math.max(0.001f, bidPrice);
				}
				bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.NORMAL, new ArrayList<>());
				bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.NORMAL).add(
						new HourlyBidPower.Builder(volume, bidPrice, hour, BidType.SELL, marketArea)
								.traderType(TraderType.SUPPLY)
								.comment("replace hourly bid: make minimum bid. UnitID: "
										+ powerPlantsAvail.get(plantIndex).getUnitID())
								.emissionCosts(powerPlantsAvail.get(plantIndex).getCostsCarbonVar())
								.fuelCosts(powerPlantsAvail.get(plantIndex).getCostsFuelVar())
								.operAndMainCosts(powerPlantsAvail.get(plantIndex)
										.getCostsOperationMaintenanceVar())
								.startupCosts(-startUpInBid).fuelType(fuelType.get(plantIndex))
								.plant(powerPlantsAvail.get(plantIndex)).build());

			}
		} else {
			// Make block bid

			// get minimum offered volume in time period
			final float minimumVolume = Statistics.calcMin(volumeNew.values());

			// Remove bids that should be replaced by hourly or block bid with
			// lowered price

			for (int hour = start; hour <= endToday; hour++) {

				final float diff = bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.NORMAL).get(0)
						.getVolume() - minimumVolume;

				if (diff > 0) {
					final float startUpInBid = (expStartupCosts / lengthTotal);
					final float price = Math
							.max(marketArea.getDayAheadMarketOperator().getMinPriceAllowed()
									+ priceIncrease, variableCosts.get(plantIndex) - startUpInBid);
					bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.DIFF, new ArrayList<>());
					bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.DIFF).add(
							new HourlyBidPower.Builder(diff, price, hour, BidType.SELL, marketArea)
									.traderType(TraderType.SUPPLY)
									.comment("AvoidTurnOff lengthToday " + lengthToday + ", total "
											+ lengthTotal + ", additional. UnitID: "
											+ powerPlantsAvail.get(plantIndex).getUnitID())
									.emissionCosts(
											powerPlantsAvail.get(plantIndex).getCostsCarbonVar())
									.fuelCosts(powerPlantsAvail.get(plantIndex).getCostsFuelVar())
									.operAndMainCosts(powerPlantsAvail.get(plantIndex)
											.getCostsOperationMaintenanceVar())
									.startupCosts(-startUpInBid).fuelType(fuelType.get(plantIndex))
									.plant(powerPlantsAvail.get(plantIndex)).build());

				}

				// remove the old hourly bid
				bids.get(plantIndex).get(hour).remove(HOURLY_BID_TYPE.NORMAL);
			}

			numberOfBlockBidsCurrently++;
			final float startUpInBid = (expStartupCosts / lengthTotal);
			final float price = Math.max(
					marketArea.getDayAheadMarketOperator().getMinPriceAllowed() + priceIncrease,
					variableCosts.get(plantIndex) - startUpInBid);
			blockBids.get(plantIndex)
					.add(new BlockBidPower.Builder(minimumVolume, price, start, lengthToday,
							BidType.SELL, marketArea)
									.traderType(TraderType.SUPPLY)
									.comment("AvoidTurnOff lengthToday "
											+ lengthToday + ", total " + lengthTotal + ". UnitID: "
											+ powerPlantsAvail.get(plantIndex).getUnitID())
									.emissionCosts(
											powerPlantsAvail.get(plantIndex).getCostsCarbonVar())
									.fuelCosts(powerPlantsAvail.get(plantIndex).getCostsFuelVar())
									.fuelType(fuelType.get(plantIndex))
									.operAndMainCosts(powerPlantsAvail.get(plantIndex)
											.getCostsOperationMaintenanceVar())
									.plant(powerPlantsAvail.get(plantIndex))
									.startupCosts(-startUpInBid).build());
		}
	}

	/**
	 * Remove hourly bid(s) in current interval and create new hourly bid(s),
	 * one with min production and avoided startup costs and one with additional
	 * production and normal variable costs..
	 *
	 * @param start
	 *            [0, 23]
	 * @param end
	 *            [0, 23]
	 * @param expStartupCosts
	 *            - the avoided startup costs by which the variable costs are
	 *            lowered [0, infinity)
	 *
	 */
	private void replaceHourlyBidPartial(int start, int end, float expStartupCosts,
			Map<Integer, Float> volumeNew) {

		// Take total avoid costs but only include part of them in todays bid
		// The part lenghtToday/lengthTotal
		final int endToday = Math.min(HOURS_PER_DAY - 1, end);

		final int lengthTotal = (end - start) + 1;

		if (expStartupCosts < 0) {
			logger.error("Startup costs need to be positive!");
		}

		if (Float.isInfinite(expStartupCosts)) {
			logger.error("Startup costs are to high!");
		}

		final Map<Integer, Float> startUpInBid = new HashMap<>();

		// Check whether lowering bids by avoided start-up cost is sufficient to
		// get into the market
		// Currently only implemented for single price forecast
		if (numberOfScenarios == 1) {
			final Map<Integer, Float> requiredBidReductions = new HashMap<>();
			float totalRequiredBidReductions = 0;

			for (int hour = start; hour <= endToday; hour++) {
				requiredBidReductions.put(hour,
						Math.max(variableCosts.get(plantIndex) - prices.get(0).get(hour), 0));
				totalRequiredBidReductions += requiredBidReductions.get(hour);
			}

			if (totalRequiredBidReductions > expStartupCosts) {
				return;
			}

			for (int hour = start; hour <= endToday; hour++) {
				startUpInBid.put(hour, (expStartupCosts * requiredBidReductions.get(hour))
						/ totalRequiredBidReductions);
			}
		} else {
			for (int hour = start; hour <= endToday; hour++) {
				startUpInBid.put(hour, expStartupCosts / lengthTotal);
			}
		}

		for (int hour = start; hour <= endToday; hour++) {
			bids.get(plantIndex).get(hour).remove(HOURLY_BID_TYPE.NORMAL);
		}

		for (int hour = start; hour <= endToday; hour++) {
			// Make hourly bid with lowered price
			bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.NORMAL, new ArrayList<>());
			bids.get(plantIndex).get(hour).put(HOURLY_BID_TYPE.DIFF, new ArrayList<>());

			// Bid for minimum Production
			final float minimumProduction = volumeNew.get(hour);
			float price = Math.max(
					marketArea.getDayAheadMarketOperator().getMinPriceAllowed() + priceIncrease,
					variableCosts.get(plantIndex) - startUpInBid.get(hour));
			price = (marketArea.getDayAheadMarketOperator().getMinPriceAllowed() / 2)
					+ priceIncrease;

			if (minimumProduction > 0) {
				bids.get(plantIndex).get(hour).get(HOURLY_BID_TYPE.NORMAL)
						.add(new HourlyBidPower.Builder(minimumProduction, price, hour,
								BidType.SELL, marketArea)
										.traderType(TraderType.SUPPLY)
										.comment("MinimumPriceBid Avoid turnOff length. UnitID: "
												+ powerPlantsAvail.get(plantIndex).getUnitID())
										.emissionCosts(powerPlantsAvail.get(plantIndex)
												.getCostsCarbonVar())
										.fuelCosts(
												powerPlantsAvail.get(plantIndex).getCostsFuelVar())
										.operAndMainCosts(powerPlantsAvail.get(plantIndex)
												.getCostsOperationMaintenanceVar())
										.startupCosts(-startUpInBid.get(hour))
										.fuelType(fuelType.get(plantIndex))
										.plant(powerPlantsAvail.get(plantIndex)).build());
			}
		}
	}
}