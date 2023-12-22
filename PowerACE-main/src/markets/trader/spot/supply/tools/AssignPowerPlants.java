package markets.trader.spot.supply.tools;

import static simulations.scheduling.Date.HOT_STARTUP_LENGTH;
import static simulations.scheduling.Date.HOURS_PER_DAY;
import static simulations.scheduling.Date.WARM_STARTUP_LENGTH;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.carbon.CarbonPrices;
import data.fuel.FuelPrices;
import data.powerplant.costs.OperationMaintenanceCost;
import data.powerplant.costs.StartupCost;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.Plant;
import supply.powerplant.capacity.CapacityType;
import supply.powerplant.technique.EnergyConversion;
import supply.powerplant.technique.Type;
import tools.other.Tuple;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * Assign power plants based on their variable, start-up costs, minimal running
 * time and minimal running load to a given demand. No optimal solution is
 * guaranteed though. For each length of production the cost for a power plant
 * to produce during that time is calculated. In some cases demand cannot be met
 * exactly, e.g. if not enough plants are available or demand is lower than must
 * run production of power plants.
 * <p>
 * This is done for the realized market volumes. For forecast see
 * {@link AssignPowerPlantsForecast}.
 *
 * 
 */
public class AssignPowerPlants {

	/**
	 * A object that contains the costs for a power plant to produce electricity
	 * for a given length of hours.
	 */
	private class CommitmentPoint implements Comparable<CommitmentPoint> {

		private final float capacity;
		private final float costs;
		private final int index;
		private final int length;
		private final Plant plant;
		private boolean technicallyPossible;

		CommitmentPoint(float capacity, float costs, int length, int identifier, Plant plant,
				boolean technicallyPossible, int index) {
			this.capacity = capacity;
			this.costs = costs;
			this.length = length;
			this.plant = plant;
			this.technicallyPossible = technicallyPossible;
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
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final CommitmentPoint other = (CommitmentPoint) obj;
			return (plant.getUnitID() == other.plant.getUnitID()) && (length == other.length)
					&& (index == other.index);
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = (890 * hash) + (plant.getUnitID() * 100000) + (length * 10) + index;
			return hash;
		}

	}

	/**
	 * Key for cold startup costs in startup costs map.
	 */
	private static final int COLD_STARTUP_KEY = 100;
	/** Tolerance for floating point variable volume checks. */
	private static final float FLOATING_POINT_TOLERANCE = 0.05f;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(AssignPowerPlants.class.getName());
	/**
	 * Maximal Period length for which period costs are calculated. At least as
	 * long as max minimum running time (10 for nuclear). If not this technology
	 * is not regarded at all.
	 */
	private static final int MAX_PERIOD_LENGTH = 12;

	public static void main(String args[]) {

		Date.setInitialDate(2010, 2010, null, 365);

		final MarketArea area = new MarketArea();

		try {
			Field field = MarketArea.class.getDeclaredField("name");
			field.setAccessible(true);
			field.set(area, "Germany");
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
			field = MarketArea.class.getDeclaredField("powerPlantData");
			field.setAccessible(true);
			field.set(area, "powerplant");
			field = Settings.class.getDeclaredField("startupCostsScenario");
			field.setAccessible(true);
			field.set(null, "Thure");
			field = Settings.class.getDeclaredField("carbonPriceScenario");
			field.setAccessible(true);
			field.set(null, "eu_roadmap_rs_isi2");
			field = Settings.class.getDeclaredField("operationMaintenanceScenarioFixed");
			field.setAccessible(true);
			field.set(null, "base");
			field = Settings.class.getDeclaredField("operationMaintenanceScenarioVar");
			field.setAccessible(true);
			field.set(null, "traber");

		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException
				| IllegalAccessException e) {
			logger.error(e.getMessage());
		}

		final StartupCost startCosts = new StartupCost(area);
		startCosts.call();

		final FuelPrices fuelPrices = new FuelPrices(area);
		fuelPrices.call();

		final CarbonPrices carbonPrices = new CarbonPrices();
		carbonPrices.call();

		final OperationMaintenanceCost omCosts = new OperationMaintenanceCost(area);
		omCosts.call();

		final Plant plant1 = new Plant(new MarketArea());
		plant1.setNetCapacity(10);
		plant1.setEnergyConversion(EnergyConversion.STEAM_TURBINE);
		plant1.setFuelName(FuelName.OIL);
		plant1.setCategory(Type.OIL_STEAM);
		plant1.initializePowerPlant(area);
		plant1.setUnitID(1);
		plant1.setVarCostsTotal(10);

		final Plant plant2 = new Plant(new MarketArea());
		plant2.setNetCapacity(100);
		plant2.setEnergyConversion(EnergyConversion.STEAM_TURBINE);
		plant2.setUnitID(2);
		plant2.setFuelName(FuelName.OIL);
		plant2.setCategory(Type.OIL_STEAM);
		plant2.initializePowerPlant(area);
		plant2.setVarCostsTotal(11);

		final Plant plant3 = new Plant(new MarketArea());
		plant3.setNetCapacity(10);
		plant3.setEnergyConversion(EnergyConversion.STEAM_TURBINE);
		plant3.setUnitID(3);
		plant3.setFuelName(FuelName.OIL);
		plant3.setCategory(Type.OIL_STEAM);
		plant3.initializePowerPlant(area);
		plant3.setVarCostsTotal(11 + 1);

		final List<Plant> plants = new ArrayList<>();
		plants.add(plant1);
		plants.add(plant2);
		plants.add(plant3);

		List<Float> demand = Arrays.asList(150f, 125f, 0f, 20f, 0f, 20f, 20f, 20f, 0f, 20f, 20f,
				20f, 20f, 20f, 20f, 20f, 20f, 20f, 20f, 20f, 20f, 20f, 00f, 20f);
		demand = Arrays.asList(0f, 13f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
				0f, 0f, 0f, 0f, 0f, 0f, 0f);

		AssignPowerPlants app = new AssignPowerPlants(plants, demand, area);
		app.assignPlants();

		final List<Float> cost = app.getMarginalCosts();
		logger.info(Arrays.toString(cost.toArray()));

		app.determineRunningTime(0);

		app.showOutcome();

		app = new AssignPowerPlants(plants, demand, area);
		app.assignPlants();

		app.showOutcome();
	}

	/**
	 * Costs for each hour of the day and each interval, first entry for 1 hour
	 * length, second entry for 2 hour length, ... sorted by costs
	 */
	private Map<Integer, Map<Integer, List<CommitmentPoint>>> costs;
	/** The total costs for each hour. */
	private List<Float> costsFromRunning;
	/** The demand for each hour that needs to be met. */
	private final List<Float> demand;
	/** The original demand for each hour that needs to be met. */
	private final List<Float> demandOriginal;
	/**
	 * Do not allow excess production regardless of technical restrictions,
	 * useful if total numbers are compared e.g. emissions
	 */
	private boolean exactProduction = true;
	/** The total profit for each hour. */
	private Map<Integer, Integer> lastRunningHour;
	/** The marginal costs for each hour. */
	private Map<Integer, Float> marginalCosts;
	/** The market area in which the power plants are located. */
	private final MarketArea marketArea;
	/** The plants that can meet the demand. */
	private final List<Plant> plants;
	/** The total profit for each hour. */
	private List<Float> profit;
	/** The startup costs for each hour. */
	private Map<Integer, Map<Integer, Float>> startupCosts;

	/**
	 * Constructor that automatically assign the plants.
	 *
	 * @param plants
	 * @param demand
	 * @param marketArea
	 */
	public AssignPowerPlants(List<Plant> plants, List<Float> demand, MarketArea marketArea) {

		this.plants = plants;
		this.demand = new ArrayList<>(demand);
		this.marketArea = marketArea;
		demandOriginal = new ArrayList<>(demand);
		startupCosts = new HashMap<>(plants.size());
		try {
			initialize();
			checkDemand();
			preSortPlants();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/** main method that assigns plant and calculate results */
	public void assignPlants() {
		try {
			calculateStartupCosts();

			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				// Costs can vary for each hour, since start-up costs depend
				// upon if a plant is running in the hour before the current and
				// if not how many hours plant was not running
				calculateCosts(hour);
				determineRunningTime(hour);

			}

			checkProductionMatch();

			calculateProfit();

		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Calculate costs for each period starting with 1 until
	 * {@link #MAX_PERIOD_LENGTH} of time. Costs are made out of
	 * {@link Plant#startUpCostsHot} and variable costs
	 * {@link Plant#totalVariableCosts}. Costs are only calculated if plant is
	 * able to run for the period of time.
	 */
	private void calculateCosts(int hour) {
		// Initialize costs

		costs.put(hour, new HashMap<>());
		for (int length = 0; length <= MAX_PERIOD_LENGTH; length++) {
			costs.get(hour).put(length, new ArrayList<CommitmentPoint>());
		}

		final int hourOfYear = Date.getFirstHourOfToday() + hour;
		for (final Plant plant : plants) {
			final float varCosts = plant.getCostsVar();
			final int unitID = plant.getUnitID();

			// assume that available capacity stays the same over the day, can
			// of course change, but is checked later on
			final float capacityAvailable = plant.getCapacityUnusedUnexpected(hourOfYear);

			// Simplification, if available capacity is smaller than min
			// production
			final float capacityMinimum = Math.min(capacityAvailable, plant.getMinProduction());

			final int lastRunningHour = this.lastRunningHour.get(unitID);
			final int hoursNotRunning = hour - lastRunningHour;

			final float startUpCosts;
			if (hoursNotRunning <= 0) {
				startUpCosts = 0f;
			} else if (hoursNotRunning <= HOT_STARTUP_LENGTH) {
				startUpCosts = startupCosts.get(unitID).get(HOT_STARTUP_LENGTH);
			} else if (hoursNotRunning <= WARM_STARTUP_LENGTH) {
				startUpCosts = startupCosts.get(unitID).get(WARM_STARTUP_LENGTH);
			} else {
				startUpCosts = startupCosts.get(unitID).get(COLD_STARTUP_KEY);
			}

			final int minRunTime = plant.getMinRunTime();
			// For MAX_PERIOD_LENGTH, base load power plant offers minimum
			// capacity cheaper to avoid future start-up costs. Assumption: Hot
			// start-up can be avoided.
			if (((plant.getFuelType() == FuelType.URANIUM)
					|| (plant.getFuelType() == FuelType.LIGNITE))
					&& (plant.getElectricityProductionToday(hour) == 0f)) {
				for (int length = 1; length <= (MAX_PERIOD_LENGTH - 1); length++) {
					costs.get(hour).get(length - 1)
							.add(new CommitmentPoint(capacityMinimum,
									varCosts + (startUpCosts / length), length, unitID, plant,
									length >= minRunTime, 1));
					costs.get(hour).get(length - 1)
							.add(new CommitmentPoint(capacityAvailable - capacityMinimum,
									varCosts + (startUpCosts / length), length, unitID, plant,
									length >= minRunTime, 2));
				}

				// Minimum capacity at variable costs reduced by avoided hot
				// start-up
				costs.get(hour).get(MAX_PERIOD_LENGTH - 1)
						.add(new CommitmentPoint(capacityMinimum,
								varCosts - (startupCosts.get(unitID).get(HOT_STARTUP_LENGTH)
										/ MAX_PERIOD_LENGTH),
								MAX_PERIOD_LENGTH, unitID, plant, MAX_PERIOD_LENGTH >= minRunTime,
								1));

				// Additional capacity always at variable costs
				costs.get(hour).get(MAX_PERIOD_LENGTH - 1)
						.add(new CommitmentPoint(capacityAvailable - capacityMinimum, varCosts,
								MAX_PERIOD_LENGTH, unitID, plant, MAX_PERIOD_LENGTH >= minRunTime,
								2));
			} else {
				// If no base load power plant or already running in hour, full
				// available capacity at variable costs plus start-up costs.
				for (int length = 1; length <= MAX_PERIOD_LENGTH; length++) {
					costs.get(hour).get(length - 1)
							.add(new CommitmentPoint(capacityAvailable,
									varCosts + (startUpCosts / length), length, unitID, plant,
									length >= minRunTime, 0));
				}
			}
		}

		for (int length = 0; length <= MAX_PERIOD_LENGTH; length++) {
			Collections.sort(costs.get(hour).get(length));
		}
	}

	/**
	 * Find out how much capacity has to be lowered in order for a plant to not
	 * underrun minimal capacity.
	 */
	private List<Float> calculateLowerProduction(int startHour, int endHour, Plant plant,
			float minimumProduction) {
		final List<Float> lowerValues = new ArrayList<>((endHour - startHour) + 1);

		for (int hourIndex = startHour; hourIndex <= endHour; hourIndex++) {
			if (minimumProduction > demand.get(hourIndex)) {
				lowerValues.add(minimumProduction - demand.get(hourIndex));
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
		marginalCosts = new TreeMap<>();
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			setMarginalCost(hour, Float.POSITIVE_INFINITY);
		}

		for (final Plant plant : plants) {
			boolean wasRunning = false;
			int hourStart = 0;

			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

				// was not running and is not running
				if (!wasRunning && !plant.isRunningHour(hour)) {
					continue;
				}

				// was not running but is running now
				if (!wasRunning && plant.isRunningHour(hour)) {
					// Only runs for last hour of this day
					if ((hour + 1) == HOURS_PER_DAY) {
						final int extraLength = 3;
						final int length = 1 + extraLength;
						float startUpCosts = 0f;
						if (length < MAX_PERIOD_LENGTH) {
							startUpCosts = marketArea.getStartUpCosts()
									.getMarginalStartUpCosts(plant, hourStart) / length;
						}
						final float tempCosts = plant.getCostsVar() + startUpCosts;
						setMarginalCost(hour, tempCosts);
					} else {
						hourStart = hour;
						wasRunning = true;
					}
					continue;

				}

				// was running and is running
				if (wasRunning && plant.isRunningHour(hour)) {
					if ((hour + 1) == HOURS_PER_DAY) {
						// Add some factor for plant that runs until midnight
						final int extraLength = 3;
						final int length = (hour - hourStart) + extraLength;
						float startUpCosts = 0f;
						if (length < MAX_PERIOD_LENGTH) {
							startUpCosts = marketArea.getStartUpCosts()
									.getMarginalStartUpCosts(plant, hourStart) / length;
						}
						final float tempCosts = plant.getCostsVar() + startUpCosts;

						for (int tempHour = hourStart; tempHour <= hour; tempHour++) {
							setMarginalCost(tempHour, tempCosts);
						}
					}
					continue;
				}

				// was running and but not anymore
				if (wasRunning && !plant.isRunningHour(hour)) {
					final int length = hour - hourStart;
					float startUpCosts = 0f;
					if (length < MAX_PERIOD_LENGTH) {
						startUpCosts = marketArea.getStartUpCosts().getMarginalStartUpCosts(plant,
								hourStart) / length;
					}
					final float tempCosts = plant.getCostsVar() + startUpCosts;

					for (int tempHour = hourStart; tempHour < hour; tempHour++) {
						setMarginalCost(tempHour, tempCosts);
					}

					wasRunning = false;
				}
			}
		}

		calculateMarginalCostsOutOfMarket();
		calculateMarginalCostsDemandLeft();
	}

	private void calculateMarginalCostsDemandLeft() {
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			if (demand.get(hour) > 0) {
				setMarginalCost(hour, 1000);
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
	private void calculateMarginalCostsOutOfMarket() {

		int numberOfContinousHours = 0;
		boolean lastHourNoDemand = false;
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			// no out of market hour
			if (demandOriginal.get(hour) > 0) {
				// out of market period end
				if (lastHourNoDemand) {
					for (int outOfMarketHour = hour
							- numberOfContinousHours; outOfMarketHour < hour; outOfMarketHour++) {
						// write costs
						final Plant plant;
						float varCosts;

						// Get plant and then get start-up costs
						// If plants runs before out of market period
						// start-up costs are lower maybe even zero, if
						// plants runs after currently this is not regarded.
						if (costs.get(hour)
								.get(Math.min(numberOfContinousHours, MAX_PERIOD_LENGTH) - 1)
								.isEmpty()) {
							varCosts = Float.POSITIVE_INFINITY;
						} else {
							plant = costs.get(hour)
									.get(Math.min(numberOfContinousHours, MAX_PERIOD_LENGTH) - 1)
									.get(0).plant;
							// Get costs, startup costs depend on if plant ran
							// before, but currently not on if plant runs after
							varCosts = plant.getCostsVar()
									+ (marketArea.getStartUpCosts().getMarginalStartUpCosts(plant,
											hour - 1) / numberOfContinousHours);

						}

						setMarginalCost(outOfMarketHour, varCosts);

					}
					lastHourNoDemand = false;
					numberOfContinousHours = 0;
				}
			}

			// out of market hour
			else if (demandOriginal.get(hour) == 0) {
				// check for last hour of day
				if ((hour + 1) == HOURS_PER_DAY) {

					float varCosts;
					// Assume that plant will also not be running tomorrow, if
					// not costs for the period may be very high and
					// sometimes infeasible if plants have min run time >1
					final int numberOfHoursRunningTomorrow = 3;
					for (int outOfMarketHour = hour
							- numberOfContinousHours; outOfMarketHour <= hour; outOfMarketHour++) {

						if (costs.get(hour)
								.get(Math.min(numberOfContinousHours + numberOfHoursRunningTomorrow,
										MAX_PERIOD_LENGTH) - 1)
								.isEmpty()) {
							varCosts = Float.POSITIVE_INFINITY;
						} else {
							varCosts = costs.get(hour)
									.get(Math.min(
											numberOfContinousHours + numberOfHoursRunningTomorrow,
											MAX_PERIOD_LENGTH) - 1)
									.get(0).costs;
						}
						setMarginalCost(outOfMarketHour, varCosts);
					}
					lastHourNoDemand = true;
					numberOfContinousHours++;

				}
			}
		}

	}

	/**
	 * Calculate the hourly profit based on variable costs of all power power
	 * plants and market prices. Start-up costs occur in hour where plant is
	 * started.
	 */
	private void calculateProfit() {
		profit = new ArrayList<>(Collections.nCopies(HOURS_PER_DAY, 0f));
		costsFromRunning = new ArrayList<>(Collections.nCopies(HOURS_PER_DAY, 0f));

		for (final Plant plant : plants) {
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

				final float production = plant.getElectricityProductionToday(hour);
				final float startupCosts;
				// Add startupCosts if the do occur
				if (!plant.isRunningHour(hour - 1) && plant.isRunningHour(hour)) {
					startupCosts = production
							* marketArea.getStartUpCosts().getMarginalStartUpCosts(plant, hour);
				} else {
					startupCosts = 0;
				}

				final float marketPrice = marketArea.getElectricityResultsDayAhead()
						.getHourlyPriceOfDay(hour);
				final float varCosts = plant.getCostsVar();
				final float costsCarbon = plant.getCostsCarbonVar();
				final float costsFuel = plant.getCostsFuelVar();
				final float costsOther = plant.getCostsOperationMaintenanceVar();

				final float margin = marketPrice - varCosts;
				final float hourlyPlantProfit = (production * margin) - startupCosts;
				final float hourlyPlantCosts = (production * varCosts) + startupCosts;
				final float hourlyPlantCostsCarbon = (production * costsCarbon);
				final float hourlyPlantCostsFuel = (production * costsFuel);
				final float hourlyPlantCostsOther = (production * costsOther);

				costsFromRunning.set(hour, costsFromRunning.get(hour) + hourlyPlantCosts);
				profit.set(hour, profit.get(hour) + hourlyPlantProfit);
				plant.increaseProfit(hourlyPlantProfit);
				plant.increaseCostsDayAheadMarket(hourlyPlantCosts);
				plant.increaseCostsStartUp(startupCosts);
				plant.increaseCostsDayAheadMarketFuel(hourlyPlantCostsFuel);
				plant.increaseCostsDayAheadMarketCarbon(hourlyPlantCostsCarbon);
				plant.increaseCostsDayAheadMarketOther(hourlyPlantCostsOther);
				plant.increaseIncomeDayAheadMarket(production * marketPrice);
			}
		}
	}

	private void calculateStartupCosts() {
		for (final Plant plant : plants) {
			final int id = plant.getUnitID();
			final Map<Integer, Float> costs = new HashMap<>();
			costs.put(HOT_STARTUP_LENGTH,
					marketArea.getStartUpCosts().getMarginalStartupCostsHot(plant));
			costs.put(WARM_STARTUP_LENGTH,
					marketArea.getStartUpCosts().getMarginalStartupCostsWarm(plant));
			costs.put(COLD_STARTUP_KEY,
					marketArea.getStartUpCosts().getMarginalStartupCostsCold(plant));
			startupCosts.put(id, costs);
		}
	}

	private void checkDemand() {
		for (final Float demandHourly : demand) {
			if (demandHourly < 0) {
				logger.error("Demand is negative " + demandHourly);
			}
		}
	}

	/**
	 * Test function to see if production equals demand.
	 */
	private void checkProductionMatch() {

		matchExactDemand();

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
		for (int hourIndex = hour; hourIndex < HOURS_PER_DAY; hourIndex++) {
			final float demandLeft = demand.get(hourIndex);
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

		if (runningHours <= 0) {
			return;
		}

		int hourEnd = (hour + runningHours) - 1;
		float minCapacity = values.getY();

		Iterator<CommitmentPoint> iter = costs.get(hour)
				.get(Math.min(runningHours, MAX_PERIOD_LENGTH) - 1).iterator();

		// until no more demand is left or no more plant is available for
		// this increase production
		while ((runningHours > 0) && iter.hasNext() && (demand.get(hour) > 0)) {

			final CommitmentPoint commitmentPoint = iter.next();

			// Get cheapest plant
			final Plant plant = commitmentPoint.plant;
			final float minimumProduction = Math.min(plant.getMinProduction(),
					plant.getCapacityUnusedExpected(Date.getFirstHourOfToday() + hour));
			// check if plant is already running in current hour and can
			// increase production
			if (plant.isRunningRange(hour, hour)) {
				increaseProduction(hour, hour, plant, commitmentPoint);
			}
			// plant is not yet running but minimal production is underrun with
			// current demand lower production of other plants
			else if (commitmentPoint.technicallyPossible) {
				if ((minCapacity < minimumProduction)) {

					// Amount production has to be lowered in order for plant to
					// run above min running level

					final List<Float> lowerVolume = calculateLowerProduction(hour, hourEnd, plant,
							minimumProduction);

					// Check that production of plants can be lowered and
					// current plant can run, without minimal capacity under run
					final boolean lowerPossible = lowerProductionPossible(hour, plant.getUnitID(),
							lowerVolume);

					if (lowerPossible) {
						lowerProduction(hour, plant.getUnitID(), lowerVolume);
						increaseProduction(hour, hourEnd, plant, commitmentPoint);
					} else {
						continue;
					}
				} else if (commitmentPoint.technicallyPossible) {
					increaseProduction(hour, hourEnd, plant, commitmentPoint);
				}

			} else if (!commitmentPoint.technicallyPossible) {

				final List<Float> lowerVolumeNextHours = new ArrayList<>();
				final int lastHour = (hour + plant.getMinRunTime()) - 1;
				for (int hourLowering = hour + 1; hourLowering <= lastHour; hourLowering++) {
					lowerVolumeNextHours.add(minimumProduction);
				}

				// Check that production of plants can be lowered and current
				// plant can run, without minimal capacity under run
				final boolean lowerPossibleFuture = lowerProductionPossible(hour + 1,
						plant.getUnitID(), lowerVolumeNextHours);

				if (lowerPossibleFuture) {
					if (minCapacity < minimumProduction) {
						final List<Float> lowerVolumeCurrentHour = calculateLowerProduction(hour,
								hour, plant, minimumProduction);
						final boolean lowerPossibleCurrentHour = lowerProductionPossible(hour,
								plant.getUnitID(), lowerVolumeCurrentHour);
						if (lowerPossibleCurrentHour) {
							lowerProduction(hour + 1, plant.getUnitID(), lowerVolumeNextHours);
							lowerProduction(hour, plant.getUnitID(), lowerVolumeCurrentHour);
							increaseProduction(hour, lastHour, plant, commitmentPoint);
						}

					} else {
						lowerProduction(hour + 1, plant.getUnitID(), lowerVolumeNextHours);
						increaseProduction(hour, lastHour, plant, commitmentPoint);
					}

				}
			}

			// reset runningHours and minCapacity
			values = determineContinuousHours(hour);
			// If runningHours change, meaning the period where a demand is
			// left, the costIndex has to be reseted
			// since costs order can differ for a different period!

			if (values.getX() == 0) {
				return;
			} else if (runningHours != values.getX()) {
				iter = costs.get(hour).get(Math.min(values.getX(), MAX_PERIOD_LENGTH) - 1)
						.iterator();
				runningHours = values.getX();
				hourEnd = (hour + runningHours) - 1;
			}

			minCapacity = values.getY();

		}

	}

	public List<Float> getCosts() {
		if (costsFromRunning == null) {
			calculateProfit();
		}

		return costsFromRunning;
	}

	/**
	 * @return The demand that could not be satisfied by plants.
	 */
	public Map<Integer, Float> getDemandRemaining() {
		final Map<Integer, Float> remainingDemand = new HashMap<>();

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			remainingDemand.put(hour, demand.get(hour));
		}

		return remainingDemand;
	}

	/**
	 *
	 * @return marginalCosts
	 */
	public List<Float> getMarginalCosts() {
		if (marginalCosts == null) {
			calculateMarginalCosts();
		}

		// Transform map to list
		final List<Float> costs = new ArrayList<>();
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			costs.add(marginalCosts.get(hour));
		}

		return costs;
	}

	public List<Float> getProfit() {
		if (profit == null) {
			calculateProfit();
		}

		return profit;
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
		demand.set(hour, demand.get(hour) + increase);
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
	private void increaseProduction(int hourStart, int hourEnd, Plant plant,
			CommitmentPoint commitmentPoint) {
		for (int hour = hourStart; (hour <= hourEnd) && (hour < HOURS_PER_DAY); hour++) {

			final int hourOfYear = Date.getFirstHourOfToday() + hour;
			float capacityUnused;

			if (commitmentPoint.index == 1) {
				capacityUnused = Math.max(
						plant.getMinProduction() - plant.getElectricityProductionToday(hour), 0f);
				capacityUnused = Math.min(capacityUnused,
						plant.getCapacityUnusedUnexpected(hourOfYear));
			} else {
				capacityUnused = plant.getCapacityUnusedUnexpected(hourOfYear);
			}

			final float increasePossibleForPlant = capacityUnused;

			// At least minimum capacity has to be running, this can lead to
			// a production that is too high
			final float increaseNeededForPlant = capacityUnused
					- plant.getElectricityProductionToday(hour);

			// if increase needed or possible
			if (increaseNeededForPlant <= increasePossibleForPlant) {

				// Theoretically min production can be greater than production
				final float increase = Math.min(demand.get(hour), increasePossibleForPlant);

				plant.increaseProduction(hour, increase);
				plant.setCapacity(CapacityType.CONTRACTED_DAY_AHEAD, hourOfYear, increase
						+ plant.getCapacity(CapacityType.CONTRACTED_DAY_AHEAD, hourOfYear));
				lowerDemand(hour, increase);

				lastRunningHour.put(plant.getUnitID(), hour);
			}
		}

	}

	/**
	 * Initialize power plant map and reset today's running hours, dailyCarbon
	 * and profits.
	 */
	private void initialize() {
		costs = new HashMap<>();
		lastRunningHour = new HashMap<>();
		for (final Plant plant : plants) {
			plant.resetCarbonEmissionsDaily();
			lastRunningHour.put(plant.getUnitID(), plant.getLastRunningHour());
		}
	}

	/**
	 * Lowers the demand in <code>hour</code> by the amount of
	 * <code>decrease</code>.
	 *
	 * @param hour
	 *            [0,23]
	 * @param decrease
	 *            [0, infinity] in MWh
	 */
	private void lowerDemand(int hour, float decrease) {
		demand.set(hour, demand.get(hour) - decrease);
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
	private void lowerProduction(int hourStart, int plantIdentifier, List<Float> lowerProduction) {
		for (int hour = hourStart, hourIndex = 0; (hour < HOURS_PER_DAY)
				&& (hour < (hourStart + lowerProduction.size())); hour++, hourIndex++) {
			while (lowerProduction.get(hourIndex) > 0) {
				for (final Plant plant : plants) {

					final int hourOfYear = Date.getFirstHourOfToday() + hour;
					final float capacityContractedDayAhead = plant
							.getCapacity(CapacityType.CONTRACTED_DAY_AHEAD, hourOfYear);
					// Make sure that production of plant that is supposed to be
					// increased is not lowered, since this can result in an
					// infinite loop
					if (plant.isRunningTodayRange(hour)
							// Assume that heat cannot be shifted, but better
							// improve and look at
							// ratio of generated electricity due to heat and
							// current electricity production
							&& (plant.getHeatProductionHourOfYear(hourOfYear) <= 0)
							&& (plantIdentifier != plant.getUnitID())) {
						final float decrease = Math.min(
								// Needed cause of heat can run below 0
								Math.max(0, capacityContractedDayAhead - plant.getMinProduction()),
								lowerProduction.get(hourIndex));

						plant.decreaseProduction(hour, decrease);
						plant.setCapacity(CapacityType.CONTRACTED_DAY_AHEAD, hourOfYear,
								capacityContractedDayAhead - decrease);
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
	 * Check that the lowering of the production can be fulfilled.
	 *
	 * @param hourStart
	 * @param plantIdentifier
	 * @param lowerProductionOriginal
	 * @return true if lowering is possible
	 */
	private boolean lowerProductionPossible(int hourStart, int plantIdentifier,
			List<Float> lowerProductionOriginal) {

		final ArrayList<Float> lowerProduction = new ArrayList<>(lowerProductionOriginal);

		final int firstHourOfToday = Date.getFirstHourOfToday();
		for (int hour = hourStart, hourIndex = 0; (hour < (hourStart + lowerProduction.size()))
				&& (hour < HOURS_PER_DAY); hour++, hourIndex++) {

			final int hourOfYear = firstHourOfToday + hour;
			while (lowerProduction.get(hourIndex) > 0) {
				for (final Plant plant : plants) {
					// Make sure that production of plant that is supposed to be
					// increased is not lowered, since this can result in an
					// infinite loop
					if (plant.isRunningTodayRange(hour) && (plantIdentifier != plant.getUnitID())
					// Assume that heat cannot be shifted, but better improve
					// and look at ratio of generated electricity due to heat
					// and current electricity production
							&& (plant.getHeatProductionHourOfYear(hourOfYear) <= 0)) {
						final float minimumProduction = Math.min(plant.getMinProduction(),
								plant.getCapacityUnusedExpected(hourOfYear));
						final float decrease = Math.min(
								plant.getElectricityProductionToday(hour) - minimumProduction,
								lowerProduction.get(hourIndex));
						lowerProduction.set(hourIndex, lowerProduction.get(hourIndex) - decrease);
					}
				}

				// Production lowering cannot be fulfilled
				if (lowerProduction.get(hourIndex) > 0) {
					return false;
				}

			}
		}

		return true;
	}

	/**
	 * Do not allow missing production regardless of technical restrictions,
	 * useful if total numbers are compared e.g. emissions
	 */
	private void matchExactDemand() {

		if (exactProduction) {
			final int firstHourOfToday = Date.getFirstHourOfToday();
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				final int hourOfYear = firstHourOfToday + hour;
				float demandUnfullfilled = demand.get(hour);
				if (demandUnfullfilled > 0) {
					float totalCapacity = 0;
					float allocatedPrimaryReserve = 0;
					float allocatedSecondaryReserve = 0;
					float allocatedtertiaryReserve = 0;
					float nonUsedExpected = 0;
					float nonUsedUnexpected = 0;
					float unusedUnexpected = 0;
					float unusedExpected = 0;
					float totalContracted = 0;
					float totalDayAhead = 0;

					for (final Plant plant : plants) {
						final float capacityAvailable = plant
								.getCapacityUnusedUnexpected(hourOfYear);
						final float increase = Math.min(capacityAvailable, demandUnfullfilled);
						totalCapacity += plant.getNetCapacity();

						nonUsedExpected += plant.getCapacity(CapacityType.NON_USABILITY_EXPECTED,
								hourOfYear);
						nonUsedUnexpected += plant
								.getCapacity(CapacityType.NON_USABILITY_UNEXPECTED, hourOfYear);
						unusedExpected += plant.getCapacity(CapacityType.CAPACITY_UNUSED_EXPECTED,
								hourOfYear);
						unusedUnexpected += plant
								.getCapacity(CapacityType.CAPACITY_UNUSED_UNEXPECTED, hourOfYear);
						totalContracted += plant.getCapacity(CapacityType.CONTRACTED_TOTAL,
								hourOfYear);
						totalDayAhead += plant.getCapacity(CapacityType.CONTRACTED_DAY_AHEAD,
								hourOfYear);

						if (increase > 0) {
							plant.increaseProduction(hour, increase);
							demandUnfullfilled -= increase;
							demand.set(hour, demand.get(hour) - increase);
							if (demandUnfullfilled <= 0) {
								break;
							}
						}
					}
					if (demandUnfullfilled > FLOATING_POINT_TOLERANCE) {
						logger.error("Demand unfullfilled in hour:" + hour + " amount: "
								+ demandUnfullfilled + ", total net capacity of power plants: "
								+ totalCapacity + ", total contracted " + totalContracted
								+ ", allocated primary: " + allocatedPrimaryReserve
								+ ", allocated secondary: " + allocatedSecondaryReserve
								+ ", allocated tertiary: " + allocatedtertiaryReserve
								+ ", non used expected: " + nonUsedExpected
								+ ", non used unexpected: " + nonUsedUnexpected
								+ ", unused expected: " + unusedExpected + ", unused unexpected: "
								+ unusedUnexpected + ", contracted day-ahead: " + totalDayAhead);
					}
				} else if (demandUnfullfilled < 0) {
					logger.error(hour + " " + demandUnfullfilled);
				}
			}
		}

	}

	private void preSortPlants() {
		// Do some presorting to improve run time
		final Comparator<Plant> compStartUpCosts = (Plant p1, Plant p2) -> -1 * Float.compare(
				p1.getCostsVar()
						+ (marketArea.getStartUpCosts().getMarginalStartupCostsWarm(p1) / 5),
				p2.getCostsVar()
						+ (marketArea.getStartUpCosts().getMarginalStartupCostsWarm(p2) / 5));
		// Second variable costs
		final Comparator<Plant> compPrice = (Plant p1, Plant p2) -> -1
				* Float.compare(p1.getCostsVar(), p2.getCostsVar());
		// Second larger plants
		final Comparator<Plant> compCapacity = (Plant p1, Plant p2) -> -1
				* Float.compare(p1.getNetCapacity(), p2.getNetCapacity());
		// 3. by UnitID
		final Comparator<Plant> compUnit = (Plant p1, Plant p2) -> Integer.compare(p1.getUnitID(),
				p2.getUnitID());
		Collections.sort(plants, compStartUpCosts.thenComparing(compPrice)
				.thenComparing(compCapacity).thenComparing(compUnit));

	}

	private void setMarginalCost(int hour, float costs) {
		if (marginalCosts.get(hour) == null) {
			marginalCosts.put(hour, costs);
		} else if (marginalCosts.get(hour) < costs) {
			marginalCosts.put(hour, costs);
		}
	}

	/**
	 * Method to print out the algorithm into logger.
	 */
	public void showOutcome() {

		logger.error("Print information for agent " + plants.get(0).getOwnerID());

		// Print demand for agent
		final DecimalFormat dfShort = new DecimalFormat("00");
		StringBuffer sb = new StringBuffer(String.format("%1$" + 86 + "s", "DemandOriginal: "));
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			sb.append("(hour " + dfShort.format(hour) + ", "
					+ String.format("%7.1f", demandOriginal.get(hour)) + "), ");
		}
		logger.error(sb.toString());

		sb = new StringBuffer(String.format("%1$" + 86 + "s", "DemandLeft: "));
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			sb.append("(hour " + dfShort.format(hour) + ", "
					+ String.format("%7.1f", demand.get(hour)) + "), ");
		}
		logger.error(sb.toString());

		final Map<Integer, Float> capacity = new HashMap<>();
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			capacity.put(hour, 0f);
		}
		final int hourOfYearFirst = Date.getFirstHourOfToday();
		for (final Plant plant : plants) {
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				final float capacityPlant = plant.getNetCapacity()
						- plant.getCapacity(CapacityType.NON_USABILITY_EXPECTED,
								hourOfYearFirst + hour)
						- plant.getCapacity(CapacityType.NON_USABILITY_UNEXPECTED,
								hourOfYearFirst + hour);
				capacity.put(hour, capacity.get(hour) + capacityPlant);
			}
		}

		sb = new StringBuffer(String.format("%1$" + 86 + "s", "CapacityAvailable: "));
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			sb.append("(hour " + dfShort.format(hour) + ", "
					+ String.format("%7.1f", capacity.get(hour)) + "), ");
		}
		logger.error(sb.toString());

		// print out plants
		Collections.sort(plants);
		for (final Plant plant : plants) {

			sb.setLength(0);
			sb.append(
					"ID " + String.format("%5d", plant.getUnitID()) + ", "
							+ String.format("%1$" + 10 + "s", plant.getNetCapacity()) + ", "
							+ String.format("%1$" + 10 + "s", plant.getFuelName().toString()) + ", "
							+ String.format("%1$" + 15 + "s",
									plant.getEnergyConversion().toString())
							+ ", varCost " + String.format("%6.1f" + 3, plant.getCostsVar())
							+ ", startCost "
							+ String.format("%6.1f",
									marketArea.getStartUpCosts().getMarginalStartupCostsHot(plant))
							+ ": ");
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				sb.append("(hour " + dfShort.format(hour) + ", "
						+ String.format("%7.1f", plant.getElectricityProductionToday(hour))
						+ "), ");
			}
			logger.error(sb.toString());
		}

		logger.error("printed information for agent " + plants.get(0).getOwnerID());

	}

}