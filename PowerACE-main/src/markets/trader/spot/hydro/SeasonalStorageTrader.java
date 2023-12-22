package markets.trader.spot.hydro;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import markets.bids.Bid.BidType;
import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.operator.spot.tools.MarginalBid;
import markets.trader.Trader;
import markets.trader.TraderType;
import markets.trader.spot.DayAheadTrader;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.other.Concurrency;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.Unit;
/**
 * @author Florian Zimmermann
 */
public class SeasonalStorageTrader extends Trader implements DayAheadTrader {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(SeasonalStorageTrader.class.getName());
	private static final ExecutorService executorLogFiles = Executors.newSingleThreadExecutor();
	/**
	 * Create Gurobi environment object. Just use one environment for runtime
	 * reasons
	 */
	private static GRBEnv env;
	static {
		try {
			env = new GRBEnv();
		} catch (final GRBException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private static int iterations = 20;
	private static final long PENALTY = 1_000_000_000l;

	/**
	 * Write all log files and wait for shutdown. Create a new instance of the
	 * Executor in case of multiruns.
	 */
	public static void closeFinal() {
		try {
			executorLogFiles.shutdown();
			executorLogFiles.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
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
	 * Bidding algorithm for Seasonal storage trader
	 * 
	 * @param marketArea
	 * @param iteration
	 * @return
	 */
	public static float getBidPrice(MarketArea marketArea, int iteration) {

		final float costsVarCone = 30f;
		float priceBid = 6;
		priceBid += (6 * (costsVarCone / iteration));
		return priceBid;
	}
	public static int getIterations() {
		return iterations;
	}
	/** In MW */
	private float capacityMax;

	private float storageVolumeMax = Float.MAX_VALUE;

	private float efficiency;
	private float minimumProduction;

	private int weatherYear = 2018;

	private float deviation = 0.2f;

	private double availableTurbineCapacity;

	private Map<Integer, Map<Integer, Float>> productionWeeklyHistorical = new HashMap<>();
	private Map<String, Map<Integer, Float>> storageVolumeWeeklyHistorical = new HashMap<>();

	List<Object> data = null;
	Map<Integer, Float> productionRegression = new HashMap<>();

	Map<Integer, Float> productionMinimum = new HashMap<>();

	Map<Integer, Float> productionCONE = new HashMap<>();
	Map<Integer, Float> productionMax = new HashMap<>();

	private Map<Integer, Float> operationPlanned;
	private Map<Integer, Map<Integer, Float>> storageLevelPreMarket = new HashMap<>();
	private Map<Integer, Map<Integer, Float>> storageLevelPostMarket = new HashMap<>();
	private final List<Double> listStorageLevel = new ArrayList<>();
	/**
	 * Define upper and lower limit of bidding energy for the bids of the agent
	 */
	private void calculatePotentials() {
		productionWeeklyHistorical = marketArea.getSeasonalProductionHistorical();
		storageVolumeWeeklyHistorical = marketArea.getStorageValuesHistorical();

	}

	/** bids are sent to the Auctioneer */
	@Override
	public List<DayAheadHourlyBid> callForBidsDayAheadHourly() {
		try {
			hourlyDayAheadPowerBids.clear();

			// generate Bids
			for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
				final DayAheadHourlyBid bid = generateHBid(hour);
				if (bid != null) {
					hourlyDayAheadPowerBids.add(bid);
				}
			}
		} catch (final Exception e) {
			logger.debug(getName());
			logger.error(e.getMessage(), e);
			logger.debug("Day of the year = " + Date.getDayOfYear());
		}

		return hourlyDayAheadPowerBids;
	}

	/**
	 * Determine whether the current bid (plantIndex) is in the current
	 * hourOfDay the marginal bid. If it is save the marginal bid in the
	 * corresponding data object.
	 */
	private void determineMarginalBid(int hourOfDay, float marketClearingPrice,
			HourlyBidPower bidPoint) {

		// Set actual marginal bid which is determined by comparing
		// the market clearing price and bid price
		final float priceDifference = Math
				.abs(Math.round((bidPoint.getPrice() - marketClearingPrice) * 100)) / 100f;
		if (priceDifference <= .0001) {
			// Create new instance of MarginalBid
			final MarginalBid marginalBid = new MarginalBid(bidPoint);
			// Set in results object
			marketArea.getElectricityResultsDayAhead().setMarginalBidHourOfDay(hourOfDay,
					marginalBid);
		}
	}

	@Override
	public void evaluateResultsDayAhead() {
		try {
			// add generated electricity by Seasonal Storage to SupplyBidder
			final Map<Integer, Float> dailyVolumes = new HashMap<>();
			IntStream.range(0, Date.HOURS_PER_DAY).forEach(i -> dailyVolumes.put(i, 0f));
			for (final DayAheadHourlyBid hourlyBid : hourlyDayAheadPowerBids) {
				for (final HourlyBidPower bidPoint : hourlyBid.getBidPoints()) {
					final float volume = bidPoint.getBidType() == BidType.ASK
							? -bidPoint.getVolumeAccepted()
							: bidPoint.getVolumeAccepted();
					marketArea.getElectricityProduction().addElectricityDaily(bidPoint.getHour(),
							volume, FuelName.HYDRO_SEASONAL_STORAGE);
					dailyVolumes.put(bidPoint.getHour(),
							dailyVolumes.get(bidPoint.getHour()) + bidPoint.getVolumeAccepted());
				}

			}
			// For logging
			final int year = Date.getYear();
			if (!storageLevelPostMarket.containsKey(year)) {
				storageLevelPostMarket.put(year, new HashMap<>());
			}
			float correctionFactor = 0;
			if (!storageLevelPostMarket.get(year).isEmpty()) {
				correctionFactor = storageLevelPostMarket.get(year)
						.get(Date.getFirstHourOfToday() - 1)
						- storageLevelPreMarket.get(year).get(Date.getFirstHourOfToday() - 1);
			}
			for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
				final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);

				correctionFactor += operationPlanned.get(hourOfYear) - dailyVolumes.get(hourOfDay);
				storageLevelPostMarket.get(year).put(hourOfYear,
						storageLevelPreMarket.get(year).get(hourOfYear) + correctionFactor);

			}
			// determine marginal bid
			for (final DayAheadHourlyBid dayAheadHourlyBid : hourlyDayAheadPowerBids) {
				final int hourOfDay = dayAheadHourlyBid.getHour();

				final float marketClearingPrice = hourlyDayAheadPowerBids.get(hourOfDay)
						.getMarketClearingPrice();

				for (final HourlyBidPower bidPoint : dayAheadHourlyBid.getBidPoints()) {
					// If bid is (partially) accepted
					if (Math.abs(bidPoint.getVolumeAccepted()) != 0) {
						/* Determine marginal bid */
						determineMarginalBid(hourOfDay, marketClearingPrice, bidPoint);
					}

				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/** Creates an hourly bid */
	private DayAheadHourlyBid generateHBid(int hourOfDay) {

		final DayAheadHourlyBid bid = new DayAheadHourlyBid(hourOfDay, TraderType.SEASONAL_STORAGE);
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		double volume = operationPlanned.get(hourOfYear);

		final BidType bidType = BidType.SELL;
		// For sell, it accepts any price even the lowest, for buying any price
		// even the highest
		// 100 because its in between of peak load and middle load plants.
		// Bid price of zero, because rather spilling than paying money
		if (minimumProduction > 0) {
			bid.addBidPoint(
					new HourlyBidPower.Builder(minimumProduction, 1, hourOfDay, bidType, marketArea)
							.fuelType(FuelType.RENEWABLE).traderType(TraderType.SEASONAL_STORAGE)
							.comment("Seasonal storage minimum production").build());
		}
		final float capacityWithoutMinimumProduction = (float) (volume - minimumProduction);
		if (capacityWithoutMinimumProduction <= 0) {
			return bid;
		}

		for (int iteration = 1; iteration <= iterations; iteration++) {

			final float priceBid = getBidPrice(marketArea, iteration);
			final float volumeBid = capacityWithoutMinimumProduction / iterations;
			bid.addBidPoint(
					new HourlyBidPower.Builder(volumeBid, priceBid, hourOfDay, bidType, marketArea)
							.fuelType(FuelType.RENEWABLE).traderType(TraderType.SEASONAL_STORAGE)
							.comment("Seasonal storage optimization production iteration "
									+ iteration)
							.build());
			volume -= volumeBid;
		}

		// Offer remaining capacity to maximum price
		final float remainingCapacity = (float) (availableTurbineCapacity - volume);
		// 20% to 99% of max price
		if (remainingCapacity > 0) {

			bid.addBidPoint(new HourlyBidPower.Builder((remainingCapacity * 0.05f),
					(getMaximumDayAheadPrice() * 0.99f), hourOfDay, bidType, marketArea)
							.fuelType(FuelType.RENEWABLE).traderType(TraderType.SEASONAL_STORAGE)
							.comment(
									"Seasonal storage CONE bids of remaining volume for extreme situations")
							.build());
		}

		return bid;
	}

	public float getAvailableTurbineCapacity() {
		return (float) availableTurbineCapacity;
	}

	/** Planned operation for PSP forecast */
	public float getOperationPlanned(int hourOfYear) {
		return operationPlanned.get(hourOfYear);
	}

	private float getProductionHistorical(int weatherYear, int weekOfYear) {
		// loops normally start with zero, week numbers in fields with 1
		weekOfYear++;

		// Last available week of storage level
		final int weekLastStorageLevel = Collections
				.max(productionWeeklyHistorical.get(weatherYear).keySet());
		final int week = Math.min(weekOfYear, weekLastStorageLevel);
		return productionWeeklyHistorical.get(weatherYear).get(week);
	}
	public float getStorageLevel(int year, int hourOfYear) {
		if (storageLevelPostMarket.containsKey(year)) {
			return storageLevelPostMarket.get(year).get(hourOfYear);
		}
		return 0f;
	}
	private float getStorageLevelHistorical(int weatherYear, int weekOfYear) {
		// loops normally start with zero, week numbers in fields with 1
		weekOfYear++;

		// Last available week of storage level
		final int weekLastStorageLevel = Collections
				.max(storageVolumeWeeklyHistorical.get("" + weatherYear).keySet());

		final int week = Math.min(weekOfYear, weekLastStorageLevel);

		return storageVolumeWeeklyHistorical.get("" + weatherYear).get(week);
	}
	public float getStorageVolumeMax() {
		return storageVolumeMax;
	}

	@Override
	public void initialize() {
		logger.info(marketArea.getInitialsBrackets() + "Initialize " + getName());

		marketArea.addSeasonalTrader(this);
		// downloadData
		final Collection<Callable<Void>> tasks = new ArrayList<>();
		Concurrency.executeConcurrently(tasks);
		availableTurbineCapacity = capacityMax;
		calculatePotentials();
	}
	private void logDataOptimization(final Map<Integer, Float> forecastprice,
			final List<Double> storageLevel, final int weatherYear,
			final Map<Integer, Float> operationPlanned) throws GRBException {
		final int year = Date.getYear();
		final int logFileOptimization = logInitializeOptimization(year);
		executorLogFiles.execute(() -> {

			try {
				final String threadName = "Logging " + SeasonalStorageTrader.class.getName();
				Thread.currentThread().setName(threadName);
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
					final List<Object> data = new ArrayList<>();
					data.add(hourOfYear);
					data.add(marketArea.getDemandData().getHourlyDemand(year, hourOfYear));

					data.add(forecastprice.get(hourOfYear));
					data.add(availableTurbineCapacity);
					data.add(operationPlanned.get(hourOfYear));

					// weekly values
					if (hourOfYear < storageLevel.size()) {
						data.add(storageLevel.get(hourOfYear));
					} else {
						data.add("-");
					}

					if (hourOfYear < storageLevel.size()) {
						data.add((getProductionHistorical(weatherYear, hourOfYear) / efficiency));
					} else {
						data.add("-");
					}

					if (hourOfYear < storageLevel.size()) {
						data.add(getStorageLevelHistorical(weatherYear, hourOfYear));
					} else {
						data.add("-");
					}

					if (hourOfYear < storageLevel.size()) {
						final float inflowStorageLevelDiff = getStorageLevelHistorical(weatherYear,
								hourOfYear + 1)
								- getStorageLevelHistorical(weatherYear, hourOfYear);
						data.add(inflowStorageLevelDiff);
					} else {
						data.add("-");
					}

					LoggerXLSX.writeLine(logFileOptimization, data);
				}

				LoggerXLSX.close(logFileOptimization);
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});
	}
	private int logInitializeOptimization(int year) {
		// Initialize log file
		final String fileName = marketArea.getInitialsUnderscore()
				+ "SeasonalHydroStorageTrader Optimization" + year;
		final String description = "Results of Seasonal hydro storage trader of "
				+ marketArea.getName();
		final List<ColumnHeader> titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("hourOfYear_weekOfYear", Unit.HOUR));
		titleLine.add(new ColumnHeader("Demand", Unit.ENERGY_VOLUME));

		titleLine.add(new ColumnHeader("Price_forecast", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("Maximum_Capacity", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("Operation", Unit.ENERGY_VOLUME));

		titleLine.add(new ColumnHeader("Storagelevel_realized", Unit.ENERGY_VOLUME));

		titleLine.add(new ColumnHeader("Production_Historical", Unit.ENERGY_VOLUME));

		titleLine.add(new ColumnHeader("StorageLevel_Historical", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("Storagelevel_diff_Historical", Unit.ENERGY_VOLUME));

		return LoggerXLSX.newLogObject(Folder.HYDROPOWER, fileName, description, titleLine,
				marketArea.getIdentityAndNameLong(), Frequency.YEARLY);

	}
	private void logStorageLevelPreMarket(List<Double> storageLevel,
			Map<Integer, Float> operationPlanned) {
		final int year = Date.getYear();
		if (!storageLevelPreMarket.containsKey(year)) {
			storageLevelPreMarket.put(year, new HashMap<>());
		}
		int week = 0;
		double levelFirst = storageLevel.get(week);
		double add = 0;

		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

			if ((week == 0) || (0 == (hourOfYear % Date.HOURS_PER_WEEK))) {
				double levelSecond = 0;
				if (storageLevel.size() > (week + 1)) {
					levelSecond = storageLevel.get(week + 1);
				} else {
					levelSecond = storageLevel.get(week);
				}

				add = (levelSecond - levelFirst) / Date.HOURS_PER_WEEK;

				levelFirst = storageLevel.get(week);
				week++;
			}

			levelFirst += add;

			storageLevelPreMarket.get(year).put(hourOfYear, (float) levelFirst);

		}

	}
	private void setOperation(GRBVar[] operation) throws GRBException {
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			operationPlanned.put(hourOfYear, (float) operation[hourOfYear].get(GRB.DoubleAttr.X)
					+ operationPlanned.get(hourOfYear));
		}
	}
	public Map<Integer, Float> storageOptimization(int iteration,
			Map<Integer, Float> priceForecast) {
		try {
			// Load data for historical inflow and storage volume
			// Price forecast for the whole year
			// for the whole year
			if (iteration == 0) {
				operationPlanned = new HashMap<>();
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

					operationPlanned.put(hourOfYear, 0f);
				}
			}
			// Create Gurobi model object
			final GRBModel model = new GRBModel(env);

			// Set Gurobi model parameters

			// The MIPFocus parameter allows you to modify your high-level
			// solution strategy, depending on your goals. By default
			// (MIPFocus=0), the Gurobi MIP solver strikes a balance between
			// finding new feasible solutions and proving that the current
			// solution is optimal. If you are more interested in good quality
			// feasible solutions, you can select MIPFocus=1. If you believe the
			// solver is having no trouble finding the optimal solution, and
			// wish to focus more attention on proving optimality, select
			// MIPFocus=2. If the best objective bound is moving very slowly (or
			// bound.
			int MIPFocus;
			MIPFocus = 0;
			model.getEnv().set(GRB.IntParam.MIPFocus, MIPFocus);

			// Limits the total time expended (in seconds). Optimization returns
			// with a TIME_LIMIT status if the limit is exceeded.
			// Default value: Infinity
			// Range [0, infinity]
			// Gurobi was designed to be deterministic, meaning that it will
			// produce
			// the same results so long as you don't change the computer, Gurobi
			// version, matrix, or parameters. One of the known exception is
			// setting
			// a time limit

			// The MIP solver will terminate (with an optimal result) when the
			// relative gap between the lower and upper objective bound is less
			// than MIPGap times the upper bound.
			// Default value: 1e-4
			// Range [0, infinity]
			double MIPGap;
			MIPGap = 1E-4;
			model.getEnv().set(GRB.DoubleParam.MIPGap, MIPGap);

			// Enables (1) or disables (0) console logging.
			int LogToConsole;
			LogToConsole = 0;
			model.getEnv().set(GRB.IntParam.LogToConsole, LogToConsole);

			/* Define solving method */
			model.set(GRB.IntParam.Method, GRB.METHOD_DUAL);

			// WeatherYear
			int storageLevelYear = weatherYear;
			if (storageVolumeWeeklyHistorical.containsKey("" + Date.getYear())) {
				storageLevelYear = Date.getYear();
			}

			/*********************
			 * Decision variables *
			 *********************/
			/* Definition of variables */

			///////////////////////////
			// Hourly
			//////////////////////////
			// hourly operation
			// - Set dimension of array
			final GRBVar[] operation = new GRBVar[Date.HOURS_PER_YEAR];
			// Penalty for not producing the minimum
			final GRBVar[] penaltyMinProd = new GRBVar[Date.HOURS_PER_YEAR];
			float operationMin = 0;
			if (iteration == (iterations - 1)) {
				operationMin = minimumProduction;
			}
			final float share = (float) (iteration + 1) / iterations;
			// - Add variables to model
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

				// Operation
				String variableName = "Operation_hour_" + hourOfYear + "_priceForecast_"
						+ priceForecast.get(hourOfYear);
				// Gurobi only alows Names with a maximal length of 255
				// characters
				if (variableName.length() > 254) {
					variableName = variableName.substring(0, 254);
				}
				// maximum Production
				operation[hourOfYear] = model.addVar(0, availableTurbineCapacity / iterations, 0.0,
						GRB.CONTINUOUS, variableName);

				// Penalty
				variableName = "Penalty_hour_" + hourOfYear + "_min_prod" + minimumProduction;
				// Gurobi only alows Names with a maximal length of 255
				// characters
				if (variableName.length() > 254) {
					variableName = variableName.substring(0, 254);
				}

				// maximum Production
				penaltyMinProd[hourOfYear] = model.addVar(0, operationMin, 0.0, GRB.CONTINUOUS,
						variableName);
			}

			////////////////////////////
			// Weekly
			////////////////////////////
			// operation in week

			final GRBVar[] storageLevel = new GRBVar[Date.WEEKS_PER_YEAR + 1];
			final GRBVar[] storageLevelPenaltyPos = new GRBVar[Date.WEEKS_PER_YEAR + 1];
			final GRBVar[] storageLevelPenaltyNeg = new GRBVar[Date.WEEKS_PER_YEAR + 1];

			// - Add variables to model
			for (int weekOfYear = 0; weekOfYear < (Date.WEEKS_PER_YEAR + 1); weekOfYear++) {

				final float storageVolumeUpper = Math.min(
						getStorageLevelHistorical(storageLevelYear, weekOfYear) * (1 + deviation),
						storageVolumeMax);
				// not negative
				final float storageVolumeLower = Math.min(Math.max(0,
						getStorageLevelHistorical(storageLevelYear, weekOfYear) * (1 - deviation)),
						storageVolumeMax);

				String variableName = "Storage_Level_week_" + weekOfYear + "_minVol_"
						+ storageVolumeLower + "_maxVol_" + storageVolumeUpper;
				// Gurobi only alows Names with a maximal length of 255
				// characters
				if (variableName.length() > 254) {
					variableName = variableName.substring(0, 254);
				}

				storageLevel[weekOfYear] = model.addVar(storageVolumeLower, storageVolumeUpper, 0.0,
						GRB.CONTINUOUS, variableName);
				storageLevelPenaltyPos[weekOfYear] = model.addVar(0, 10000000, 0.0, GRB.CONTINUOUS,
						"Penalty_for_storage_level_Pos_deviation");
				storageLevelPenaltyNeg[weekOfYear] = model.addVar(0, 10000000, 0.0, GRB.CONTINUOUS,
						"Penalty_for_storage_level_Neg_deviation");
			}

			// Update model
			model.update();

			/*********************
			 * Target function *
			 *********************/
			/* Objective function */
			final GRBLinExpr objective = new GRBLinExpr();
			// Maximize revenues

			// add to objective function: price * operation
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				objective.addTerm(priceForecast.get(hourOfYear), operation[hourOfYear]);
				// Plus penalties
				// For min Production
				objective.addTerm(-1, penaltyMinProd[hourOfYear]);

			}

			// add to objective function: slag
			for (int hourOfWeek = 0; hourOfWeek < (Date.WEEKS_PER_YEAR + 1); hourOfWeek++) {

				// weekly deviation
				objective.addTerm(-PENALTY * share, storageLevelPenaltyPos[hourOfWeek]);
				objective.addTerm(-PENALTY * share, storageLevelPenaltyNeg[hourOfWeek]);

			}

			model.setObjective(objective, GRB.MAXIMIZE);

			// Update model
			model.update();

			/*********************
			 * Constraints *
			 *********************/
			// Needed for constraints
			final GRBLinExpr expr1 = new GRBLinExpr();

			/* Boundaries for operation variables */
			// Minimum Production with penalty if negative prices occure
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				expr1.addTerm(1, operation[hourOfYear]);
				expr1.addTerm(1, penaltyMinProd[hourOfYear]);
				model.addConstr(expr1, GRB.GREATER_EQUAL, operationMin, "operation_minimum_"
						+ minimumProduction + "_max_" + availableTurbineCapacity);
				expr1.clear();
			}

			// maximum storage level

			// minimum storage level

			// Add initial storage volume
			final float initalStorageLevel = getStorageLevelHistorical(storageLevelYear, 0);
			expr1.addTerm(1, storageLevel[0]);
			expr1.addConstant(-initalStorageLevel);
			model.addConstr(expr1, GRB.EQUAL, 0, "Initial_Storage_level_" + initalStorageLevel);
			expr1.clear();
			// combine operation in hours per week to weekly storage volume
			// Volume_t+1=operation_week_t+inflow+Volume_t
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				// Consider efficiency
				// Operation in week
				expr1.addTerm(-1 / efficiency, operation[hourOfYear]);

				// already allocated operation

				expr1.addConstant((-1 / efficiency) * operationPlanned.get(hourOfYear));

				final int weekOfYear = Date.getWeekOfWeekyearFromHourOfYear(hourOfYear) - 1;

				if ((weekOfYear < (Date.getWeekOfWeekyearFromHourOfYear(hourOfYear + 1) - 1))
						|| (hourOfYear == (Date.HOURS_PER_YEAR - 1))) {

					// Storage level_t, Field starts with 0
					expr1.addTerm(1, storageLevel[weekOfYear]);

					// net inflow = historical production
					final float outflowHistProduction = (getProductionHistorical(storageLevelYear,
							weekOfYear) / efficiency);

					final float inflowStorageLevelDiff = getStorageLevelHistorical(storageLevelYear,
							weekOfYear + 1)
							- getStorageLevelHistorical(storageLevelYear, weekOfYear);
					if (((outflowHistProduction + inflowStorageLevelDiff) < 0)) {
						logger.error(marketArea.getInitialsBrackets()
								+ " Net outflow without production weekly seasonal storage operation.Not Possile! Check input data");
					}
					expr1.addConstant(share * outflowHistProduction);
					expr1.addConstant(Math.max(0, share * inflowStorageLevelDiff));
					// Storagelevel next week
					// what about week 53
					expr1.addTerm(-1, storageLevel[weekOfYear + 1]);
					expr1.addTerm(-1, storageLevelPenaltyPos[weekOfYear + 1]);
					expr1.addTerm(1, storageLevelPenaltyNeg[weekOfYear + 1]);
					model.addConstr(expr1, GRB.EQUAL, 0, "Storage_Level_weekly_" + weekOfYear);
					expr1.clear();
				}
			}

			// Storagelevel end of week after operation +/-x%

			// stroagelevel hist(beginn next week)- storagelevel(end last week)
			// + production hist (week) -operation

			// Storagelevel after operation (end of week) = storagelevel (beginn
			// week) + (inflow: level beginn next week-level beinn week + hist
			// operation)

			// Write model
			final String path = Settings.getLogPathName(marketArea.getIdentityAndNameLong(),
					Folder.HYDROPOWER);
			model.write(path + File.separator + marketArea.getInitials() + "_SeasonalStorage_"
					+ Date.getYear() + "_iteration_" + iteration + ".lp");

			/* Solve model */
			model.optimize();
			if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
				logger.error("Seasonal Storage infeasible! Please check . Status: "
						+ model.get(GRB.IntAttr.Status));
				// compute IIS to find restrictive constraints
				model.computeIIS();
				for (int constrIndex = 0; constrIndex < model.getConstrs().length; constrIndex++) {
					if (model.getConstr(constrIndex).get(GRB.IntAttr.IISConstr) > 0) {
						logger.error("IIS-constraint (y" + Date.getYear() + "): "
								+ model.getConstr(constrIndex).get(GRB.StringAttr.ConstrName));
					}
				}
			}

			setOperation(operation);
			// for logging
			listStorageLevel.clear();
			for (final GRBVar element : storageLevel) {
				listStorageLevel.add(element.get(GRB.DoubleAttr.X));
			}
			model.dispose();

			logDataOptimization(Collections.unmodifiableMap(priceForecast),
					Collections.unmodifiableList(listStorageLevel), storageLevelYear,
					Collections.unmodifiableMap(operationPlanned));
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		logStorageLevelPreMarket(Collections.unmodifiableList(listStorageLevel),
				Collections.unmodifiableMap(operationPlanned));
		return operationPlanned;
	}
}