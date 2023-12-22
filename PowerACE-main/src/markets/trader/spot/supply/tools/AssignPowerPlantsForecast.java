package markets.trader.spot.supply.tools;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import tools.other.Tuple;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * Assign power plants based on their variable, start-up costs, minimal running
 * time and minimal running load to a given demand. No optimal solution is
 * guaranteed though. For each length the cost for a power plant to produce
 * during that time is calculated. In some cases demand cannot be met exactly,
 * e.g. if not enough plants are available or demand is lower than must run
 * production of power plants.
 *
 * 
 */
public class AssignPowerPlantsForecast {

	/**
	 * A object that contains the costs for a power plant to produce electricity
	 * for a given length of hours.
	 */
	private class CommitmentPoint implements Comparable<CommitmentPoint> {

		private final float capacity;
		private final float costs;
		private final Plant plant;
		private final int index;

		CommitmentPoint(float capacity, float costs, float length, int identifier, Plant plant,
				int index) {
			this.capacity = capacity;
			this.costs = costs;
			this.plant = plant;
			this.index = index;
		}

		/**
		 * Compare via costs (lower considered first) and capacity (higher
		 * considered first) and index (lower considered first).
		 */
		@Override
		public int compareTo(CommitmentPoint other) {

			// lower prices are considered first
			if (costs > other.costs) {
				return 1;
			} else if (costs < other.costs) {
				return -1;
			}

			// higher volumes are considered first
			if (capacity > other.capacity) {
				return -1;
			} else if (capacity < other.capacity) {
				return 1;
			}

			// minimum running capacity (index 1) is considered first
			if (index > other.index) {
				return -1;
			} else if (index < other.index) {
				return 1;
			}

			return 0;
		}

		@Override
		public String toString() {
			return "Capacity " + capacity + ", Plant " + plant + ", Costs " + costs;
		}
	}

	/**
	 * Set of technical restrictions for a specific power plant. Needed to use
	 * different restrictions than defined for the power plant in the class
	 * "Plant", i.e. mainly if no technical restrictions should be considered
	 * for the price forecast.
	 */
	private class TechnicalRestrictions {
		private float minProduction = 0f;
		private int minRunTime = 1;

		private TechnicalRestrictions(float minProduction, int minRunTime) {
			this.minProduction = minProduction;
			this.minRunTime = minRunTime;
		}

		private float getMinProduction() {
			return minProduction;
		}

		private int getMinRunTime() {
			return minRunTime;
		}
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(AssignPowerPlantsForecast.class.getName());

	/**
	 * Maximal Period length for which period costs are calculated. At least as
	 * long as max minimum running time (10 for nuclear). If not this technology
	 * is not regarded at all.
	 */
	private static final int MAX_PERIOD_LENGTH = 12;
	private static final int MAX_PRICE_FORECAST = 1000;

	public static void main(String args[]) {

		Date.setInitialDate(2010, 2010, null, 365);

		final MarketArea area = new MarketArea();

		try {
			Field field = MarketArea.class.getDeclaredField("dataBasePrefix");
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

		final Plant plant1 = new Plant(new MarketArea());
		plant1.setUnitID(1);
		plant1.setFuelName(FuelName.URANIUM);
		plant1.setVarCostsTotal(10);
		plant1.setNetCapacity(100);
		plant1.setCategory(Type.NUC_GEN_2);

		final Plant plant2 = new Plant(new MarketArea());
		plant2.setUnitID(2);
		plant2.setFuelName(FuelName.GAS);
		plant2.setVarCostsTotal(11);
		plant2.setNetCapacity(100);
		plant2.setCategory(Type.NUC_GEN_2);

		final List<Plant> plants = new ArrayList<>();
		plants.add(plant1);
		plants.add(plant2);

		final StartupCost startCosts = new StartupCost(area);
		startCosts.call();

		final FuelPrices fuelPrices = new FuelPrices(area);
		fuelPrices.call();
	}

	/**
	 * Costs for each interval, first entry for 1 hour length, second entry for
	 * 2 hour length, ...
	 */
	private List<List<CommitmentPoint>> costs;

	/** The demand for each hour that needs to be met. */
	private List<List<Float>> demand;

	/** The original demand for each hour that needs to be met. */
	private List<List<Float>> demandOriginal;
	/** The length of the forecast */
	private final int length;
	/** The marginal costs for each hour. */
	private List<Map<Integer, Float>> marginalCosts;
	/** The market area in which the power plants are located. */
	private final MarketArea marketArea;
	/** The plants that can meet the demand. */
	private Map<Integer, List<Plant>> plants;
	private final List<Plant> plantsAll;
	/** The production for each plant */
	private List<Map<Plant, List<Float>>> production;
	private int scenarioIndex;
	private Map<Integer, String> scenarioNames;
	private final int scenarioNumber;
	private Map<Integer, Float> scenarioProbabilities;
	private final int time = 1;
	/** Turn technical restrictions on/off for use in the price forecast */
	private boolean useTechnicalRestrictions = false;
	/** Map storing technical restrictions for all plants */
	Map<Integer, TechnicalRestrictions> plantsTechnicalRestrictions = new LinkedHashMap<>();

	/** Constructor that automatically assign the plants. */
	public AssignPowerPlantsForecast(List<Plant> plants, List<ScenarioList<Float>> demand,
			MarketArea marketArea) {
		plantsAll = plants;
		Collections.sort(plantsAll);
		length = demand.get(0).getSize();
		this.marketArea = marketArea;
		scenarioIndex = 0;
		scenarioNumber = demand.size();

		initialize(demand);

		checkDemand();
	}

	/** main method that assigns plant and calculate results */
	public void assignPlants() {

		// Costs can vary for each hour, since start-up costs depend upon if a
		// plant is running in the hour before the current and if not how many
		// hours plant was not running
		for (int index = 0; index < scenarioNumber; index++) {
			for (int hour = 0; hour < length; hour++) {
				calculateCosts(hour);
				sortCosts();
				determineRunningTime(hour);
			}
			scenarioIndex++;
		}

	}

	public List<ScenarioList<Float>> getMarginalCosts() {
		if (marginalCosts == null) {
			calculateMarginalCosts();
		}

		// Transform map to list
		final List<ScenarioList<Float>> costs = new ArrayList<>();

		for (int index = 0; index < scenarioNumber; index++) {
			final List<Float> cost = new ArrayList<>();
			for (int hour = 0; hour < marginalCosts.get(index).size(); hour++) {
				cost.add(marginalCosts.get(index).get(hour));
			}
			costs.add(new ScenarioList<>(time, scenarioNames.get(index), cost,
					scenarioProbabilities.get(index)));
		}

		return costs;
	}

	/**
	 * Returns the forecasted hourly production of all plants for all scenarios.
	 */
	public List<Map<Plant, List<Float>>> getProduction() {
		return production;
	}

	/**
	 * Calculate costs for each period starting with 1 until
	 * {@link #MAX_PERIOD_LENGTH} of time. Costs are made out of
	 * {@link Plant#startUpCostsHot} and variable costs
	 * {@link Plant#totalVariableCosts}. Costs are only calculated if plant is
	 * able to run for the period of time.
	 */
	private void calculateCosts(int hour) {
		costs = new ArrayList<>();
		for (int length = 1; length <= MAX_PERIOD_LENGTH; length++) {
			costs.add(new ArrayList<CommitmentPoint>());
			for (final Plant plant : plantsAll) {
				if (length >= plantsTechnicalRestrictions.get(plant.getUnitID()).getMinRunTime()) {
					// For MAX_PERIOD_LENGTH, base load power plant offers
					// minimum
					// capacity cheaper to avoid future start-up costs.
					// Assumption: Hot
					// start-up can be avoided.
					if ((plant.getFuelType() == FuelType.URANIUM)
							|| (plant.getFuelType() == FuelType.LIGNITE)) {
						// Simplification, if available capacity is smaller than
						// min production
						final float capacityAvailable = plant
								.getCapacityUnusedUnexpected(Date.getFirstHourOfToday() + hour);
						final float capacityMinimum = Math.min(capacityAvailable,
								plant.getMinProduction());

						if (length == MAX_PERIOD_LENGTH) {
							costs.get(length - 1).add(new CommitmentPoint(capacityMinimum,
									plant.getCostsVar() - (marketArea.getStartUpCosts()
											.getMarginalStartupCostsHot(plant) / MAX_PERIOD_LENGTH),
									length, plant.getUnitID(), plant, 1));
							costs.get(length - 1)
									.add(new CommitmentPoint(capacityAvailable - capacityMinimum,
											plant.getCostsVar(), length, plant.getUnitID(), plant,
											2));
						} else {
							costs.get(length - 1).add(new CommitmentPoint(capacityMinimum, plant
									.getCostsVar()
									+ (calculateMarginalStartUpCosts(scenarioIndex, plant, hour)
											/ length),
									length, plant.getUnitID(), plant, 1));
							costs.get(length - 1)
									.add(new CommitmentPoint(capacityAvailable - capacityMinimum,
											plant.getCostsVar()
													+ (calculateMarginalStartUpCosts(scenarioIndex,
															plant, hour) / length),
											length, plant.getUnitID(), plant, 2));
						}
					} else {
						costs.get(length - 1).add(new CommitmentPoint(
								plant.getCapacityUnusedExpected(Date.getFirstHourOfToday() + hour),
								plant.getCostsVar()
										+ (calculateMarginalStartUpCosts(scenarioIndex, plant, hour)
												/ length),
								length, plant.getUnitID(), plant, 0));
					}
				}
			}
		}
	}

	/**
	 * Find out how much capacity has to be lowered in order to not overproduce.
	 */
	private List<Float> calculateLowerProduction(int startHour, int endHour) {
		final List<Float> lowerValues = new ArrayList<>((endHour - startHour) + 1);
		for (int hourIndex = startHour; hourIndex <= endHour; hourIndex++) {
			if (demand.get(scenarioIndex).get(hourIndex) < 0) {
				lowerValues.add(-demand.get(scenarioIndex).get(hourIndex));
			} else {
				lowerValues.add(0f);
			}
		}
		return lowerValues;
	}

	/**
	 * Find out how much capacity has to be lowered in order for a plant to not
	 * underrun minimal capacity.
	 */
	private List<Float> calculateLowerProduction(int startHour, int endHour, Plant plant) {
		final List<Float> lowerValues = new ArrayList<>((endHour - startHour) + 1);
		for (int hourIndex = startHour; hourIndex <= endHour; hourIndex++) {
			if (plantsTechnicalRestrictions.get(plant.getUnitID()).getMinProduction() > demand
					.get(scenarioIndex).get(hourIndex)) {
				lowerValues
						.add(plantsTechnicalRestrictions.get(plant.getUnitID()).getMinProduction()
								- demand.get(scenarioIndex).get(hourIndex));
			} else {
				lowerValues.add(0f);
			}
		}
		return lowerValues;
	}

	/**
	 * Calculate prices that occur if plants were setting the price.
	 */
	private void calculateMarginalCosts() {

		marginalCosts = new ArrayList<>();
		for (int index = 0; index < scenarioNumber; index++) {
			marginalCosts.add(new TreeMap<Integer, Float>());

			for (final Plant plant : plants.get(index)) {
				boolean wasRunning = plant.isRunningHour(-1);
				int hourStart = 0;

				for (int hour = 0; hour < length; hour++) {

					// was not running and is not running
					if (!wasRunning && !isRunningHour(index, plant, hour)) {
						continue;
					}

					// was not running but is running now
					if (!wasRunning && isRunningHour(index, plant, hour)) {
						// Only runs for last hour of this day
						if ((hour + 1) == length) {
							final int extraLength = 0;
							final int length = 1 + extraLength;
							float startUpCosts = 0f;
							if (length < MAX_PERIOD_LENGTH) {
								startUpCosts = calculateMarginalStartUpCosts(index, plant,
										hourStart) / length;
							}
							final float tempCosts = plant.getCostsVar() + startUpCosts;
							setMarginalCost(index, hour, tempCosts);
						} else {
							hourStart = hour;
							wasRunning = true;
						}
						continue;

					}

					// was running and is running
					if (wasRunning && isRunningHour(index, plant, hour)) {
						if ((hour + 1) == length) {
							// Add some factor for plant that runs until
							// midnight
							final int extraLength = 0;
							final int length = (hour - hourStart) + 1 + extraLength;
							float startUpCosts = 0f;
							if (length < MAX_PERIOD_LENGTH) {
								startUpCosts = calculateMarginalStartUpCosts(index, plant,
										hourStart) / length;
							} else if (((plant.getFuelType() == FuelType.URANIUM)
									|| (plant.getFuelType() == FuelType.LIGNITE))
									&& (production.get(index).get(plant).get(hour) <= plant
											.getMinProduction())) {
								// Base load power plants bid minimal load below
								// variable costs in order to avoid future
								// start-up costs
								startUpCosts = -(marketArea.getStartUpCosts()
										.getMarginalStartupCostsHot(plant) / MAX_PERIOD_LENGTH);
							}
							final float tempCosts = plant.getCostsVar() + startUpCosts;
							for (int tempHour = hourStart; tempHour <= hour; tempHour++) {
								setMarginalCost(index, tempHour, tempCosts);
							}
						}
						continue;
					}

					// was running and but not anymore
					if (wasRunning && !isRunningHour(index, plant, hour)) {
						final int length = hour - hourStart;
						float startUpCosts = 0f;
						if (length < MAX_PERIOD_LENGTH) {
							startUpCosts = calculateMarginalStartUpCosts(index, plant, hourStart)
									/ length;
						} else if (((plant.getFuelType() == FuelType.URANIUM)
								|| (plant.getFuelType() == FuelType.LIGNITE))
								&& (production.get(index).get(plant).get(hour) <= plant
										.getMinProduction())) {
							// Base load power plants bid minimal load below
							// variable costs in order to avoid future start-up
							// costs
							startUpCosts = -(marketArea.getStartUpCosts()
									.getMarginalStartupCostsHot(plant) / MAX_PERIOD_LENGTH);
						}
						final float tempCosts = plant.getCostsVar() + startUpCosts;
						for (int tempHour = hourStart; tempHour < hour; tempHour++) {
							setMarginalCost(index, tempHour, tempCosts);
						}

						wasRunning = false;
					}
				}
			}

			calculateMarginalCostsOutOfMarket(index);
			calculateMarginalCostsDemandLeft(index);
		}
	}

	private void calculateMarginalCostsDemandLeft(int index) {
		for (int hour = 0; hour < length; hour++) {
			if (demand.get(index).get(hour) > 0) {
				setMarginalCost(index, hour, MAX_PRICE_FORECAST);
			}
		}
	}

	/**
	 * Calculate a cheap option
	 *
	 * What about a plant that could run longer? Couldn't that be cheaper than
	 * to plant that runs only for this period?
	 *
	 *
	 */
	private void calculateMarginalCostsOutOfMarket(int index) {

		int numberOfContinousHours = 0;
		boolean lastHourNoDemand = false;
		for (int hour = 0; hour < length; hour++) {

			// no out of market hour
			if (demandOriginal.get(index).get(hour) > 0) {
				// out of market period ends
				if (lastHourNoDemand) {
					for (int outOfMarketHour = hour
							- numberOfContinousHours; outOfMarketHour < hour; outOfMarketHour++) {
						// write costs
						final Plant plant;

						// Get plant and then get start-up costs
						// If plants runs before out of market period
						// start-up costs are lower maybe even zero, if
						// plants runs after currently this is not regarded.
						if (costs.get(Math.min(numberOfContinousHours, MAX_PERIOD_LENGTH) - 1)
								.isEmpty()) {
							// If no plant is available for this period (cause
							// plants have larger min running time take next
							// period
							plant = costs.get(
									Math.min(numberOfContinousHours + 1, MAX_PERIOD_LENGTH) - 1)
									.get(0).plant;
						} else {
							plant = costs
									.get(Math.min(numberOfContinousHours, MAX_PERIOD_LENGTH) - 1)
									.get(0).plant;

						}
						// Get costs, startup costs depend on if plant ran
						// before, but currently not on if plant runs after
						// no
						final float costs = plant.getCostsVar() + (marketArea.getStartUpCosts()
								.getMarginalStartUpCosts(plant, hour - 1) / numberOfContinousHours);

						setMarginalCosts(index, outOfMarketHour, costs);
					}
					lastHourNoDemand = false;
					numberOfContinousHours = 0;
				}
			}
			// out of market hour
			else if (demandOriginal.get(index).get(hour) == 0) {
				// check for last hour of period
				if ((hour + 1) == length) {
					// Assume that plant will also not be running tomorrow, if
					// not costs for the period may be very high and
					// sometimes infeasible if plants have min run time >1
					final int numberOfHoursRunningTomorrow = 3;
					for (int outOfMarketHour = hour
							- numberOfContinousHours; outOfMarketHour <= hour; outOfMarketHour++) {
						setMarginalCosts(index, outOfMarketHour,
								costs.get(Math.min(
										numberOfContinousHours + numberOfHoursRunningTomorrow,
										MAX_PERIOD_LENGTH) - 1).get(0).costs);
					}
				}
				lastHourNoDemand = true;
				numberOfContinousHours++;

			}
		}

	}

	private float calculateMarginalStartUpCosts(int index, Plant plant, int hour) {
		float costs;
		// no start-up costs for base load power plants
		if (isRunningHour(index, plant, hour - 1) || (plant.getFuelType() == FuelType.URANIUM)
				|| (plant.getFuelType() == FuelType.LIGNITE)) {
			costs = 0;
		} else {
			if (isRunningRange(index, plant, hour - Date.HOT_STARTUP_LENGTH, hour - 1)) {
				costs = marketArea.getStartUpCosts().getMarginalStartupCostsHot(plant);
			} else if (isRunningRange(index, plant, hour - Date.WARM_STARTUP_LENGTH,
					hour - (Date.HOT_STARTUP_LENGTH + 1))) {
				costs = marketArea.getStartUpCosts().getMarginalStartupCostsWarm(plant);
			} else {
				costs = marketArea.getStartUpCosts().getMarginalStartupCostsCold(plant);
			}
		}

		return costs;
	}

	private void checkDemand() {
		for (final List<Float> demandScenario : demand) {
			for (final Float demandHourly : demandScenario) {
				if (demandHourly < 0) {
					logger.error("Demand cannot be less than zero!");
				}
			}
		}
	}

	/**
	 * Lowers the production of the running plants by the values in
	 * <code>lowerProduction</code>.
	 *
	 * @param hourStart
	 *            hour in which the demand is supposed to be lowered [0,23]
	 * @param lowerProduction
	 *            The amount by which the current production has to be reduced.
	 *
	 */
	private void decreaseProduction(int hourStart, int plantIdentifier,
			List<Float> lowerProduction) {
		for (int hour = hourStart,
				hourIndex = 0; hour < (hourStart + lowerProduction.size()); hour++, hourIndex++) {
			while (lowerProduction.get(hourIndex) > 0) {
				for (final Plant plant : plants.get(scenarioIndex)) {
					// Make sure that production of plant that is supposed to be
					// increased is not lowered, since this can result in an
					// infinite loop
					if (isRunningHour(scenarioIndex, plant, hour)
							&& (plantIdentifier != plant.getUnitID())) {
						final float decrease = Math.min(
								production.get(scenarioIndex).get(plant).get(hour)
										- plantsTechnicalRestrictions.get(plant.getUnitID())
												.getMinProduction(),
								lowerProduction.get(hourIndex));
						lowerProduction(plant, hour, decrease);
						increaseDemand(hour, decrease);
						lowerProduction.set(hourIndex, lowerProduction.get(hourIndex) - decrease);
					}
				}
				// Unable to lower production
				break;
			}
		}
	}

	/**
	 * Determine the number of continuous hours with a positive demand starting
	 * with <code>hour</code>.
	 *
	 * @param hour
	 *            [0,23]
	 * @return Tuple(hours, minDemand) The number of continuous hours and the
	 *         minimal additional demand during that time period.
	 */
	private Tuple<Integer, Float> determineContinuousHours(int hour) {
		int demandHours = 0;
		float minCapacity = Float.MAX_VALUE;
		for (int hourIndex = hour; hourIndex < length; hourIndex++) {
			final float demandLeft = demand.get(scenarioIndex).get(hourIndex);
			if (demandLeft > 0) {
				demandHours++;
			} else {
				break;
			}
			if (demandLeft < minCapacity) {
				minCapacity = demandLeft;
			}
		}
		return new Tuple<>(demandHours, minCapacity);
	}

	/** Determine the running time and values for each power plant. */
	private void determineRunningTime(int hour) {

		// Set runningHours and minCapacity
		Tuple<Integer, Float> values = determineContinuousHours(hour);
		int runningHours = values.getX();
		int hourEnd = (hour + runningHours) - 1;
		float minCapacity = values.getY();
		int costIndex = 0;
		int additionalRunningHours = 0;

		// until no more demand is left or no more plant is available for
		// this increase production
		while ((runningHours > 0) && (costIndex < costs.get(MAX_PERIOD_LENGTH - 1).size())
				&& (demand.get(scenarioIndex).get(hour) > 0)) {

			// If no plant is available for the required runtime due to min
			// runtime constraints, find plant with lowest additional runtime
			// requirements. This results automatically in minCapacity=0,
			// because the plant would then have to be running in at least one
			// hour where it is not needed, i.e. demand=0.
			while (((runningHours + additionalRunningHours) < MAX_PERIOD_LENGTH)
					&& (costIndex == costs.get((runningHours + additionalRunningHours) - 1)
							.size())) {
				additionalRunningHours++;
				costIndex = 0;
			}

			if (additionalRunningHours > 0) {
				minCapacity = 0;
			}

			// Get cheapest plant
			final Plant plant = costs
					.get(Math.min(runningHours + additionalRunningHours, MAX_PERIOD_LENGTH) - 1)
					.get(costIndex++).plant;

			// additional running hours may require to regard hours after
			// the forecast length; if so, run additional hours before
			// current hour instead
			final int hourOverhang = Math.max(
					(hourEnd + additionalRunningHours + 1) - demand.get(scenarioIndex).size(), 0);

			// check if plant is already running and can increase production
			if (isRunningRange(scenarioIndex, plant, hour - hourOverhang,
					(hourEnd + additionalRunningHours) - hourOverhang)) {
				increaseProduction(hour - hourOverhang,
						(hourEnd + additionalRunningHours) - hourOverhang, plant);
				decreaseProduction(hour - hourOverhang, plant.getUnitID(), calculateLowerProduction(
						hour - hourOverhang, (hourEnd + additionalRunningHours) - hourOverhang));
			}
			// plant is not yet running but minimal production is underrun
			// with current demand lower production of other plants
			else if (minCapacity < plantsTechnicalRestrictions.get(plant.getUnitID())
					.getMinProduction()) {
				decreaseProduction(hour - hourOverhang, plant.getUnitID(),
						calculateLowerProduction(hour - hourOverhang,
								(hourEnd + additionalRunningHours) - hourOverhang, plant));
				increaseProduction(hour - hourOverhang,
						(hourEnd + additionalRunningHours) - hourOverhang, plant);
			}
			// plant is not yet running and minimal production is not
			// underrun
			else {
				increaseProduction(hour, hourEnd + additionalRunningHours, plant);
			}
			// reset runningHours and minCapacity
			values = determineContinuousHours(hour);
			// If runningHours change, meaning the period where a demand is
			// left, the costIndex has to be reseted
			// since costs order can differ for a different period!
			if (runningHours != values.getX()) {
				costIndex = 0;
				additionalRunningHours = 0;
			}
			runningHours = values.getX();
			hourEnd = (hour + runningHours) - 1;
			minCapacity = values.getY();

		}

		if (hour == (length - 1)) {
			for (int i = 0; i < length; i++) {
				if (demand.get(scenarioIndex).get(i) < -0.001f) {
					logger.error(marketArea.getInitialsBrackets()
							+ "Production overhang in forecast hour;" + i + ";"
							+ demand.get(scenarioIndex).get(i));
				}
			}
		}
	}

	/**
	 * Increase the demand in <code>hour</code> by the amount of
	 * <code>increase</code>.
	 *
	 * @param hour
	 *            [0,23]
	 * @param increase
	 *            [0, infinity] in MWh
	 */
	private void increaseDemand(int hour, float increase) {
		demand.get(scenarioIndex).set(hour, demand.get(scenarioIndex).get(hour) + increase);
	}

	/**
	 * Increase the production for the power plant and lowers the value of
	 * demand by the same amount.
	 *
	 * @param hourStart
	 *            [0,23]
	 * @param hourEnd
	 *            [0,23]
	 * @param plant
	 *            The plant which production should increase.
	 */
	private void increaseProduction(int hour, float increase, Plant plant) {
		production.get(scenarioIndex).get(plant).set(hour,
				production.get(scenarioIndex).get(plant).get(hour) + increase);
	}

	/**
	 * Initialize power plant map and reset today's running hours, dailyCarbon
	 * and profits.
	 *
	 * @param demand
	 */
	private void initialize(List<ScenarioList<Float>> demand) {

		// initalize probabilities and names
		scenarioNames = new LinkedHashMap<>();
		scenarioProbabilities = new LinkedHashMap<>();
		for (int index = 0; index < scenarioNumber; index++) {
			scenarioNames.put(index, demand.get(index).getName());
			scenarioProbabilities.put(index, demand.get(index).getProbability());
		}

		plants = new LinkedHashMap<>();
		for (int index = 0; index < scenarioNumber; index++) {
			plants.put(index, new ArrayList<Plant>());
			for (final Plant plant : plantsAll) {
				plants.get(index).add(plant);
			}
			Collections.sort(plants.get(index));
		}

		// Technical restrictions of power plants
		if (useTechnicalRestrictions) {
			for (final Plant plant : plantsAll) {
				plantsTechnicalRestrictions.put(plant.getUnitID(),
						new TechnicalRestrictions(plant.getMinProduction(), plant.getMinRunTime()));
			}
		} else {
			for (final Plant plant : plantsAll) {
				plantsTechnicalRestrictions.put(plant.getUnitID(),
						new TechnicalRestrictions(0f, 1));
			}
		}

		production = new ArrayList<>();
		for (int index = 0; index < scenarioNumber; index++) {
			production.add(new LinkedHashMap<Plant, List<Float>>());
			for (final Plant plant : plants.get(index)) {
				final List<Float> tempProduction = new ArrayList<>();
				for (int hour = 0; hour < length; hour++) {
					tempProduction.add(0f);
				}
				production.get(index).put(plant, tempProduction);
			}
		}

		demandOriginal = new ArrayList<>();
		this.demand = new ArrayList<>();
		for (int index = 0; index < scenarioNumber; index++) {
			// Price forecast calculates which power plants are needed to cover
			// the residual load in every hour. Therefore, negative residual
			// loads don't need to be considered but can be regarded as a
			// residual load of zero.
			final List<Float> demandOnlyPositive = new ArrayList<>();
			for (int hour = 0; hour < length; hour++) {
				demandOnlyPositive.add(Math.max(0, demand.get(index).get(hour)));
			}
			demandOriginal.add(new ArrayList<>(demandOnlyPositive));
			this.demand.add(new ArrayList<>(demandOnlyPositive));
		}
	}

	/**
	 * Checks if power plant is running for requested hour. Negative values are
	 * used for yesterday or the day before yesterday.
	 *
	 * @param hour
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 * @return
	 */
	private boolean isRunningHour(int index, Plant plant, int hour) {
		if (hour < 0) {
			return plant.isRunningHour(hour);
		} else {
			return production.get(index).get(plant).get(hour) > 0 ? true : false;
		}
	}

	/**
	 * Checks if power plant is running for requested period.
	 *
	 * @param hourStart
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 *
	 * @param hourEnd
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 *
	 * @return <code>true</code> if plant is running during that period
	 */
	private boolean isRunningRange(int index, Plant plant, int hourStart, int hourEnd) {
		boolean isRunning = false;
		for (int hourIndex = hourStart; hourIndex <= hourEnd; hourIndex++) {
			if (isRunningHour(index, plant, hourIndex)) {
				isRunning = true;
				break;
			}
		}
		return isRunning;
	}

	private void lowerProduction(Plant plant, int hour, float decrease) {
		production.get(scenarioIndex).get(plant).set(hour,
				production.get(scenarioIndex).get(plant).get(hour) - decrease);
	}

	private void setMarginalCost(int index, int hour, float costs) {
		if (marginalCosts.get(index).get(hour) == null) {
			marginalCosts.get(index).put(hour, costs);
		} else if (marginalCosts.get(index).get(hour) < costs) {
			marginalCosts.get(index).put(hour, costs);
		}
	}

	private void setMarginalCosts(int index, int hour, Float costs) {
		// Even if there is no demand in a certain hour, marginal costs might
		// still be set already because of minimum runtime constraints. These
		// may lead to an overproduction in that hour.
		if (marginalCosts.get(index).get(hour) == null) {
			marginalCosts.get(index).put(hour, costs);
		}
	}

	/** Sort all costs interval for each hour. */
	private void sortCosts() {
		for (final List<CommitmentPoint> cost : costs) {
			Collections.sort(cost);
		}
	}

}