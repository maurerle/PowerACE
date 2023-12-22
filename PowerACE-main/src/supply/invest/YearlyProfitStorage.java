package supply.invest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.PlantOption;

public class YearlyProfitStorage {

	private static GRBEnv env;

	private static final Logger logger = LoggerFactory
			.getLogger(YearlyProfitStorage.class.getName());

	/**
	 * Amount of hours over which the day ahead operation is optimized (rolling
	 * horizon approach). Minimum value: 24 hours
	 */
	private static final int OPTIMIZATION_PERIOD = Date.HOURS_PER_DAY;

	private static boolean useOptimization = true;
	static {
		try {
			env = new GRBEnv();
		} catch (final GRBException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
	public static void dispose() {
		try {
			// dispose environment
			env.dispose();
			env = new GRBEnv();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Little test case for optimization
	 * 
	 * @throws FileNotFoundException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public static void main(String args[]) throws FileNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
		// Read test prices
		final List<Float> testPrices = new ArrayList<>();
		final Scanner inFile = new Scanner(
				new FileReader("C:\\Users\\gl2125\\Documents\\Prices_2050_DEN_Test.txt"));
		for (int hour = 0; hour < Date.HOURS_PER_YEAR; hour++) {
			testPrices.add(inFile.nextFloat());
		}
		inFile.close();

		// Create dummy storage plant
		final PlantOption testStoragePlant = new PlantOption();
		testStoragePlant.setNetCapacity(100f);
		testStoragePlant.setStorageVolume(400f);
		testStoragePlant.setEfficiency(0.8f);

		final Field field = Settings.class.getDeclaredField("numberOfCores");
		field.setAccessible(true);
		field.set(null, 4);

		final YearlyProfitStorage yearlyProfitStorage = new YearlyProfitStorage(testPrices,
				testStoragePlant);
		yearlyProfitStorage.determineStorageOperationOptimization();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			System.out
					.println(hourOfYear + ";" + yearlyProfitStorage.yearlyPriceForecast[hourOfYear]
							+ ";" + yearlyProfitStorage.yearlyOperation[hourOfYear]);
		}
	}

	private float initialStorageLevel;

	private float[] operation = new float[Date.HOURS_PER_DAY];
	private float[] plannedOperation = new float[OPTIMIZATION_PERIOD];
	private float[] plannedStorageLevel = new float[OPTIMIZATION_PERIOD];

	private float[] priceForecast = new float[OPTIMIZATION_PERIOD];
	private Queue<Map<Integer, Float>> resultsQueue = new LinkedBlockingQueue<>();
	private float[] storageLevel = new float[Date.HOURS_PER_DAY];
	private PlantOption storagePlant;
	/** The total profit for the year */
	private float totalProfit;
	/** The total profit for the year */
	private float totalProfitExcludingAvailability;
	/** The total profit for the year */
	private float totalProfitExcludingFixedCosts;
	private float[] yearlyOperation = new float[Date.HOURS_PER_YEAR];
	private Map<Integer, Float> yearlyOperationConcurrent = new HashMap<>();
	private float[] yearlyPriceForecast = new float[Date.HOURS_PER_YEAR];
	private float[] yearlyStorageLevel = new float[Date.HOURS_PER_YEAR];

	/** This is the constructor */
	public YearlyProfitStorage(List<Float> prices, PlantOption storagePlant) {
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			yearlyPriceForecast[hourOfYear] = prices.get(hourOfYear);
		}
		this.storagePlant = storagePlant;
	}

	/**
	 * The yearly profit of an investment in the storage device is calculated
	 * using the simulated hourly operation, the input time series of
	 * electricity prices and economic characteristics of the storage device.
	 */
	public void calcYearlyProfit() {
		if (useOptimization) {
			determineStorageOperationOptimization();
		} else {
			determineStorageOperationHeuristic();
		}

		totalProfit = 0f;
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			totalProfit += -yearlyOperation[hourOfYear] * yearlyPriceForecast[hourOfYear];
		}
		totalProfit /= storagePlant.getDischargeCapacity();

		totalProfitExcludingAvailability = totalProfit
				- storagePlant.getCostsOperationMaintenanceFixed(Date.getYear());

		totalProfitExcludingFixedCosts = totalProfit;
		totalProfit = totalProfit - storagePlant.getCostsOperationMaintenanceFixed(Date.getYear());
	}

	/**
	 * @return profit, which equals income - fixed costs in [EUR/MW] and
	 *         availability <br>
	 *         <b>(Net capacity is not yet regarded!)</b>
	 */
	public float getTotalProfit() {
		return totalProfit;
	}

	/**
	 * @return profit, which equals income - fixed costs in [EUR/MW]<br>
	 *         <b>(Net capacity is not yet regarded!)</b>
	 */
	public float getTotalProfitExcludingAvailability() {
		return totalProfitExcludingAvailability;
	}

	/**
	 * @return profit, which equals income - fixed costs in [EUR/MW]<br>
	 *         <b>(Net capacity is not yet regarded!)</b>
	 */
	public float getTotalProfitWithoutFixedCosts() {
		return totalProfitExcludingFixedCosts;
	}

	public float[] getYearlyOperation() {
		return yearlyOperation;
	}

	public List<Float> getYearlyOperationAsList() {
		final List<Float> yearlyOperationAsList = new ArrayList<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			yearlyOperationAsList.add(yearlyOperation[hourOfYear]);
		}
		return yearlyOperationAsList;
	}

	private void addToQueue(Map<Integer, Float> results) {
		resultsQueue.add(results);
	}

	private void calculateQueue() {
		try {
			// wait until end and until queue is processed
			while (!(resultsQueue.isEmpty())) {
				final Map<Integer, Float> result = resultsQueue.poll();
				for (final int hour : result.keySet()) {
					final float operationOfHour = result.get(hour);
					yearlyOperationConcurrent.put(hour, operationOfHour);
				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

	/**
	 * Corrects an overflow of the storage by reducing the amount of charging in
	 * all hours before the overflow
	 */
	private void correctOverflow() {
		final int hourWithWorstOverflow = findOverflow();

		// Find hour with min price before the overflow
		int hourWithMin = 0;
		for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
			if (priceForecast[hour] < priceForecast[hourWithMin]) {
				hourWithMin = hour;
			}
		}

		// Determine scaling factor
		float scalingFactor = 0;
		for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
			if (plannedOperation[hour] > 0) {
				scalingFactor += priceForecast[hour] - priceForecast[hourWithMin];
			}
		}

		// Check if prices in all problematic hours are the same
		boolean allHourlyValuesEqual = false;
		if (scalingFactor == 0) {
			allHourlyValuesEqual = true;

			int hoursWithOverflow = 0;
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (plannedOperation[hour] > 0) {
					hoursWithOverflow += 1;
				}
			}

			scalingFactor = (plannedStorageLevel[hourWithWorstOverflow]
					- storagePlant.getStorageVolume()) / storagePlant.getChargeEfficiency()
					/ hoursWithOverflow;
		} else {
			scalingFactor = 1 / scalingFactor;
			scalingFactor *= (plannedStorageLevel[hourWithWorstOverflow]
					- storagePlant.getStorageVolume()) / storagePlant.getChargeEfficiency();
		}

		// Correct charging operation
		if (allHourlyValuesEqual) {
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (plannedOperation[hour] > 0) {
					plannedOperation[hour] = Math.max(plannedOperation[hour] - scalingFactor, 0);
				}
			}
		} else {
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (plannedOperation[hour] > 0) {
					plannedOperation[hour] = Math.max(plannedOperation[hour]
							- ((priceForecast[hour] - priceForecast[hourWithMin]) * scalingFactor),
							0);
				}
			}
		}
	}

	/**
	 * Corrects an underflow of the storage by reducing the amount of turbining
	 * in all hours before the overflow
	 */
	private void correctUnderflow() {
		final int hourWithWorstUnderflow = findUnderflow();

		// Find hour with max price before the underflow
		int hourWithMax = 0;
		for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
			if (priceForecast[hour] > priceForecast[hourWithMax]) {
				hourWithMax = hour;
			}
		}

		// Determine scaling factor
		float scalingFactor = 0;
		for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
			if (plannedOperation[hour] < 0) {
				scalingFactor += priceForecast[hourWithMax] - priceForecast[hour];
			}
		}

		// Check if prices in all problematic hours are the same
		boolean allHourlyValuesEqual = false;
		if (scalingFactor == 0) {
			allHourlyValuesEqual = true;

			int hoursWithUnderflow = 0;
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (plannedOperation[hour] < 0) {
					hoursWithUnderflow += 1;
				}
			}

			scalingFactor = (plannedStorageLevel[hourWithWorstUnderflow]
					* storagePlant.getDischargeEfficiency()) / hoursWithUnderflow;
		} else {
			scalingFactor = 1 / scalingFactor;
			scalingFactor *= (plannedStorageLevel[hourWithWorstUnderflow]
					* storagePlant.getDischargeEfficiency());
		}

		// Correct discharging operation
		if (allHourlyValuesEqual) {
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (plannedOperation[hour] < 0) {
					plannedOperation[hour] = Math.min(plannedOperation[hour] - scalingFactor, 0);
				}
			}
		} else {
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (plannedOperation[hour] < 0) {
					plannedOperation[hour] = Math.min(plannedOperation[hour]
							- ((priceForecast[hourWithMax] - priceForecast[hour]) * scalingFactor),
							0);
				}
			}
		}
	}

	/**
	 * Determines a preliminary operation of the storage device for all hours of
	 * the optimization period. General idea: Price limits for a profitable
	 * operation are determined. In hours with high prices discharging operation
	 * at maximum capacity, in hours with low prices charging operation at
	 * maximum capacity. Constraints with regard to overflows and underflows of
	 * the storage are not being considered in this method.
	 */
	private void determinePrelimOperation() {
		// Initialize price limits. If even for the highest and lowest price of
		// the optimization period no profitable operation is possible, then
		// price limits must be chosen very high / low to avoid operation of the
		// storage device.
		float priceLimitCharge = -500000f;
		float priceLimitDischarge = 500000f;

		// Sort price forecast ascending
		final float[] sortedPriceForecast = Arrays.copyOf(priceForecast, priceForecast.length);
		Arrays.sort(sortedPriceForecast);

		// Get price limits for profitable operation
		for (int i = 0; i < Math.floorDiv(sortedPriceForecast.length, 2); i++) {
			// Check price pair for profitability
			if ((sortedPriceForecast[i]
					/ sortedPriceForecast[sortedPriceForecast.length - 1 - i]) <= storagePlant
							.getEfficiency()) {
				// Update price limits
				priceLimitCharge = sortedPriceForecast[i];
				priceLimitDischarge = sortedPriceForecast[(sortedPriceForecast.length - 1 - i)];
			} else {
				break;
			}
		}

		for (int hour = 0; hour < OPTIMIZATION_PERIOD; hour++) {
			if (priceForecast[hour] >= priceLimitDischarge) {
				plannedOperation[hour] = -storagePlant.getDischargeCapacity();
			} else if (priceForecast[hour] <= priceLimitCharge) {
				plannedOperation[hour] = storagePlant.getChargeCapacity();
			} else {
				plannedOperation[hour] = 0f;
			}
		}
	}

	/**
	 * The profit-maximizing operation of the storage device is simulated over a
	 * period of one year using the input time series of electricity prices and
	 * the techno-economic characteristics of the storage device.
	 */
	private void determineStorageOperationHeuristic() {
		initialStorageLevel = 0.25f * storagePlant.getStorageVolume();

		// Loop over all days of the year. If an optimization period longer than
		// 24 hours is chosen (rolling horizon), the simulation will stop the
		// corresponding amount of days earlier.
		for (int dayOfYear = 0; dayOfYear < ((Date.DAYS_PER_YEAR
				- (int) Math.ceil((double) OPTIMIZATION_PERIOD / Date.HOURS_PER_DAY))
				+ 1); dayOfYear++) {

			// Reset variables
			int counter = 0;
			Arrays.fill(operation, 0);
			Arrays.fill(plannedOperation, 0);
			Arrays.fill(plannedStorageLevel, 0);
			Arrays.fill(storageLevel, 0);

			// Update electricity price forecast
			for (int hour = 0; hour < OPTIMIZATION_PERIOD; hour++) {
				priceForecast[hour] = yearlyPriceForecast[(dayOfYear * Date.HOURS_PER_DAY) + hour];
			}

			// Determine preliminary operation and update storage level
			determinePrelimOperation();
			setStorageLevel();

			// Correct overflows and underflows of the storage
			while (isOverflow() || isUnderflow()) {
				if (isOverflow()) {
					correctOverflow();
					setStorageLevel();
				}
				if (isUnderflow()) {
					correctUnderflow();
					setStorageLevel();
				}
				counter += 1;
				if (counter > 100) {
					System.out.println("StorageOperation could not be solved for day " + dayOfYear
							+ 1 + ". Overflow: " + isOverflow() + ", Underflow: " + isUnderflow());
					break;
				}
			}

			// Store final operation and storage levels before moving on to the
			// next simulation day
			System.arraycopy(plannedOperation, 0, operation, 0, Date.HOURS_PER_DAY);
			System.arraycopy(plannedStorageLevel, 0, storageLevel, 0, Date.HOURS_PER_DAY);
			initialStorageLevel = storageLevel[Date.HOURS_PER_DAY - 1];
			for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
				yearlyOperation[(dayOfYear * Date.HOURS_PER_DAY) + hour] = operation[hour];
				yearlyStorageLevel[(dayOfYear * Date.HOURS_PER_DAY) + hour] = storageLevel[hour];
			}
		}
	}

	/**
	 * The profit-maximizing operation of the storage device is simulated over a
	 * period of one year using the input time series of electricity prices and
	 * the techno-economic characteristics of the storage device.
	 */
	private void determineStorageOperationOptimization() {
		initialStorageLevel = 0.25f * storagePlant.getStorageVolume();

		// Loop over all days of the year. If an optimization period longer than
		// 24 hours is chosen (rolling horizon), the simulation will stop the
		// corresponding amount of days earlier.

		try {

			for (int weekOfYear = 1; weekOfYear <= Date.WEEKS_PER_YEAR; weekOfYear++) {
				optimizeOperationWeekly(weekOfYear);
			}

		} catch (final GRBException e) {
			logger.error("Error determining storage profit:" + e);
		}
		calculateQueue();

		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

			yearlyOperation[hourOfYear] = yearlyOperationConcurrent.get(hourOfYear);
		}
	}

	/**
	 * Returns the hour of the optimization period with the worst overflow of
	 * the storage
	 */
	private int findOverflow() {
		int maxOverflow = 0;
		for (int hour = 0; hour < OPTIMIZATION_PERIOD; hour++) {
			if (plannedStorageLevel[hour] >= plannedStorageLevel[maxOverflow]) {
				maxOverflow = hour;
			}
		}
		return maxOverflow;
	}

	/**
	 * Returns the hour of the optimization period with the worst underflow of
	 * the storage
	 */
	private int findUnderflow() {
		int maxUnderflow = 0;
		for (int hour = 0; hour < OPTIMIZATION_PERIOD; hour++) {
			if (plannedStorageLevel[hour] <= plannedStorageLevel[maxUnderflow]) {
				maxUnderflow = hour;
			}
		}
		return maxUnderflow;
	}

	/**
	 * Checks if an overflow of the storage occurs considering a given operation
	 * per hour of the optimization period. A certain tolerance (0.01% of
	 * maxStorageLevel) is defined to reach faster convergence.
	 */
	private boolean isOverflow() {
		for (int hour = 0; hour < OPTIMIZATION_PERIOD; hour++) {
			if (plannedStorageLevel[hour] > (1.0001f * storagePlant.getStorageVolume())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if an underflow of the storage occurs considering a given
	 * operation per hour of the optimization period. A certain tolerance (0.01%
	 * of maxStorageLevel) is defined to reach faster convergence.
	 */
	private boolean isUnderflow() {
		for (int hour = 0; hour < OPTIMIZATION_PERIOD; hour++) {
			if (plannedStorageLevel[hour] < (-0.0001f * storagePlant.getStorageVolume())) {
				return true;
			}
		}
		return false;
	}

	private void optimizeOperationWeekly(int weekOfYear) throws GRBException {
		final int totalHoursOfWeek;
		if (weekOfYear < Date.WEEKS_PER_YEAR) {
			totalHoursOfWeek = Date.HOURS_PER_WEEK;
		} else {
			totalHoursOfWeek = Date.HOURS_PER_DAY;
		}

		// create environment and model
		final GRBModel model = new GRBModel(env);
		model.getEnv().set("OutputFlag", "0");

		// Only use one thread per model
		model.set(GRB.IntParam.Threads, 1);

		// Use dual simplex
		model.set(GRB.IntParam.Method, 1);

		// Enables (1) or disables (0) console logging.
		int LogToConsole;
		LogToConsole = 0;
		model.getEnv().set(GRB.IntParam.LogToConsole, LogToConsole);

		// Controls the presolve level. A value of -1 corresponds to an
		// automatic setting. Other options are off (0), conservative (1),
		// or aggressive (2). More aggressive application of presolve takes
		// more time, but can sometimes lead to a significantly tighter
		// model.
		int presolveLevel;
		presolveLevel = 0;
		model.getEnv().set(GRB.IntParam.Presolve, presolveLevel);

		// storage level
		final GRBVar[] storageLevel = new GRBVar[totalHoursOfWeek];
		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			storageLevel[hour] = model.addVar(0, storagePlant.getStorageVolume(), 0, GRB.CONTINUOUS,
					"storageLevel" + hour);
		}

		// pump operation
		final GRBVar[] pumpOperation = new GRBVar[totalHoursOfWeek];
		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			pumpOperation[hour] = model.addVar(0, storagePlant.getChargeCapacity(), 0,
					GRB.CONTINUOUS, "pumpOperation" + hour);
		}

		// turbine operation
		final GRBVar[] turbineOperation = new GRBVar[totalHoursOfWeek];
		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			turbineOperation[hour] = model.addVar(-storagePlant.getDischargeCapacity(), 0, 0,
					GRB.CONTINUOUS, "turbineOperation" + hour);
		}

		// operation
		final GRBVar[] optimalOperation = new GRBVar[totalHoursOfWeek];
		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			optimalOperation[hour] = model.addVar(-storagePlant.getDischargeCapacity(),
					storagePlant.getChargeCapacity(), 0, GRB.CONTINUOUS, "operation" + hour);
		}

		// setting objective function
		final GRBLinExpr linexpr = new GRBLinExpr();
		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			linexpr.addTerm(-yearlyPriceForecast[((weekOfYear - 1) * Date.HOURS_PER_WEEK) + hour],
					optimalOperation[hour]);
		}
		model.setObjective(linexpr, GRB.MAXIMIZE);
		linexpr.clear();

		// add constraint for operation
		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			linexpr.addTerm(1, pumpOperation[hour]);
			linexpr.addTerm(1, turbineOperation[hour]);
			model.addConstr(linexpr, GRB.EQUAL, optimalOperation[hour],
					"constraintOperation" + hour);
			linexpr.clear();
		}

		// add constraint for storage level
		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			// previous hour
			if (hour == 0) {
				linexpr.addConstant(initialStorageLevel);
			} else {
				linexpr.addTerm(1, storageLevel[hour - 1]);
			}
			linexpr.addTerm(storagePlant.getChargeEfficiency(), pumpOperation[hour]);
			linexpr.addTerm(1 / storagePlant.getDischargeEfficiency(), turbineOperation[hour]);
			model.addConstr(linexpr, GRB.EQUAL, storageLevel[hour], "constraintLevel" + hour);
			linexpr.clear();
		}

		// Last hour of optimization period
		model.addConstr(storageLevel[totalHoursOfWeek - 1], GRB.EQUAL, initialStorageLevel,
				"constraintLevelEndOfYear");

		// update model
		model.update();

		// perform optimization
		model.optimize();

		// transfer operation
		final Map<Integer, Float> yearlyOperation = new HashMap<>();

		for (int hour = 0; hour < totalHoursOfWeek; hour++) {
			yearlyOperation.put(((weekOfYear - 1) * Date.HOURS_PER_WEEK) + hour,
					(float) optimalOperation[hour].get(GRB.DoubleAttr.X));
		}

		addToQueue(yearlyOperation);

		// dispose of model and environment
		model.dispose();
	}

	/**
	 * Sets the storage level for every hour of the optimization period
	 * resulting from the planned operation.
	 */
	private void setStorageLevel() {
		if (plannedOperation[0] > 0) {
			plannedStorageLevel[0] = initialStorageLevel
					+ (plannedOperation[0] * storagePlant.getChargeEfficiency());
		} else if (plannedOperation[0] < 0) {
			plannedStorageLevel[0] = initialStorageLevel
					+ (plannedOperation[0] / storagePlant.getDischargeEfficiency());
		} else {
			plannedStorageLevel[0] = initialStorageLevel;
		}

		for (int hour = 1; hour < OPTIMIZATION_PERIOD; hour++) {
			if (plannedOperation[hour] > 0) {
				plannedStorageLevel[hour] = plannedStorageLevel[hour - 1]
						+ (plannedOperation[hour] * storagePlant.getChargeEfficiency());
			} else if (plannedOperation[hour] < 0) {
				plannedStorageLevel[hour] = plannedStorageLevel[hour - 1]
						+ (plannedOperation[hour] / storagePlant.getDischargeEfficiency());
			} else {
				plannedStorageLevel[hour] = plannedStorageLevel[hour - 1];
			}
		}
	}
}