package markets.trader.future.tools;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.exchange.Capacities;
import data.storage.PumpStoragePlant;
import gurobi.GRB;
import gurobi.GRBConstr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import markets.bids.Bid;
import markets.bids.Bid.BidType;
import markets.trader.TraderType;
import markets.trader.spot.hydro.SeasonalStorageTrader;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.CostCap;
import supply.powerplant.PlantOption;

/** Market coupling algorithm for one hour */
public class MarketCouplingForecast {
	// Create Gurobi environment object
	private static GRBEnv env;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(MarketCouplingForecast.class.getName());
	/**
	 * Set a small penalty that no peaker will produce for another market area.
	 * With Gurobi 8.0 penalty value has to be greater than 1e-5 otherwise it
	 * doesn't seem to has an effect. Margin seems advisable, so we choose 1e-3.
	 */
	private static double penaltyInterconnectorFlows = -0.001;
	private static String priceForecastPath = Settings.getLogPathName()
			+ "MarketCouplingPriceForecast";

	static {
		try {
			env = new GRBEnv();
		} catch (final GRBException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
	public static void disposeMarketCouplingForecast() {
		try {
			// dispose environment
			env.dispose();
			env = new GRBEnv();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Implements the market coupling algorithm. The method (following the
	 * COSMOS algorithm) maximizes social welfare based on bids from all market
	 * areas and interconnection capacities.
	 * 
	 * @throws GRBException
	 */
	public static void marketCouplingAlgorithmHourly(final int year, final int hourOfYear,
			Set<MarketArea> marketAreas, Map<MarketArea, List<Bid>> simpleBids,
			Capacities capacitiesData, boolean withAdditionalPlants) throws GRBException {
		try {
			// Create Gurobi model object
			final GRBModel model = new GRBModel(env);
			/* Objective function */
			final GRBLinExpr objective = new GRBLinExpr();

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
			// not at all), you may want to try MIPFocus=3 to focus on the
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

			// Controls the presolve level. A value of -1 corresponds to an
			// automatic setting. Other options are off (0), conservative (1),
			// or aggressive (2). More aggressive application of presolve takes
			// more time, but can sometimes lead to a significantly tighter
			// model.

			/* Define solving method */
			model.set(GRB.IntParam.Method, GRB.METHOD_DUAL);
			/* Definition of variables */
			// - Set dimension of array
			final GRBVar[][] accept = new GRBVar[marketAreas.size()][];
			for (final MarketArea marketArea : marketAreas) {
				accept[marketArea.getIdMarketCoupling() - 1] = new GRBVar[simpleBids.get(marketArea)
						.size()];
			}

			// - Add variables to model
			for (final MarketArea marketArea : marketAreas) {
				// Add only bids to be optimized
				for (int bid = 0; bid < simpleBids.get(marketArea).size(); bid++) {

					final Bid simpleBid = simpleBids.get(marketArea).get(bid);

					final String variableName = "accept_" + marketArea + "_Trader_"
							+ simpleBid.getTraderType() + "_price_" + simpleBid.getPrice()
							+ "_volume_" + simpleBid.getVolume();

					accept[marketArea.getIdMarketCoupling() - 1][bid] = model.addVar(0, 1, 0.0,
							GRB.CONTINUOUS, variableName);
				}
			}

			// Flow variables
			// Set dimension of array
			final GRBVar[][] flowMatrix = new GRBVar[marketAreas.size()][marketAreas.size()];
			for (final MarketArea fromMarketArea : marketAreas) {
				for (final MarketArea toMarketArea : marketAreas) {
					final int idMarketCouplingFromMarketArea = fromMarketArea.getIdMarketCoupling()
							- 1;
					final int idMarketCouplingToMarketArea = toMarketArea.getIdMarketCoupling() - 1;

					// Set name of variable
					final String name = "flow_from_" + fromMarketArea + "_to_" + toMarketArea;

					if (fromMarketArea.equals(toMarketArea)) {
						flowMatrix[idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea] = model
								.addVar(0, 0, 0.0, GRB.CONTINUOUS, name);
					} else {
						// Set upper bound equal to the available capacity
						// between
						// the two market areas
						double upperBound = 0;

						upperBound = capacitiesData.getInterconnectionCapacityHour(fromMarketArea,
								toMarketArea, year, hourOfYear);

						// Add variable
						flowMatrix[idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea] = model
								.addVar(0, upperBound, 0.0, GRB.CONTINUOUS, name);

						objective.addTerm(penaltyInterconnectorFlows,
								flowMatrix[idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea]);

					}
				}
			}

			// Needed for constraints
			final GRBLinExpr expr1 = new GRBLinExpr();

			/* Boundaries for acceptance variables */
			// Demand bids have to be accepted
			// On the one hand this is necessary since demand bids have no price
			// limit (i.e. price = 0). Consequently, these bids do not appear in
			// the objective function. In order to maximize the objective
			// function
			// all supply bids (which have a negative sign) will be declined and
			// there is no market clearing.
			// On the other hand setting accept = 1 for demand bids could result
			// in infeasible models when supply is insufficient. There would be
			// no market clearing either and error handling is necessary.

			// Accept = 1
			for (final MarketArea marketArea : marketAreas) {
				for (int bidNumber = 0; bidNumber < simpleBids.get(marketArea)
						.size(); bidNumber++) {
					if (simpleBids.get(marketArea).get(bidNumber).getBidType() == BidType.ASK) {
						if (simpleBids.get(marketArea).get(bidNumber)
								.getTraderType() == TraderType.POWER_TO_HYDROGEN) {
							continue;
						}
						final Bid bid = simpleBids.get(marketArea).get(bidNumber);
						expr1.addTerm(1, accept[marketArea.getIdMarketCoupling() - 1][bidNumber]);
						model.addConstr(expr1, GRB.EQUAL, 1, "accept_demand_" + marketArea
								+ "_BidID_" + bid.getIdentifier() + "_" + bid.getComment());
						expr1.clear();
					}
				}
			}

			/* Market area balance */
			// Sum(Accept * Volume) = 0
			final GRBConstr[] marketAreaBalance = new GRBConstr[marketAreas.size()];

			for (final MarketArea marketArea : marketAreas) {
				final int marketAreaId = marketArea.getIdMarketCoupling() - 1;
				// Add demand and supply bids
				for (int bid = 0; bid < simpleBids.get(marketArea).size(); bid++) {

					final Bid bidPoint = simpleBids.get(marketArea).get(bid);
					float bidVolume = Float.NaN;
					if (bidPoint.getType() == BidType.ASK) {
						bidVolume = bidPoint.getVolume();
					} else if (bidPoint.getType() == BidType.SELL) {
						bidVolume = -bidPoint.getVolume();
					}

					expr1.addTerm(bidVolume, accept[marketAreaId][bid]);
				}

				// Add flows
				for (int i = 0; i < flowMatrix.length; i++) {
					// Exports
					expr1.addTerm(1, flowMatrix[marketAreaId][i]);
					// Imports
					expr1.addTerm(-1, flowMatrix[i][marketAreaId]);
				}

				marketAreaBalance[marketAreaId] = model.addConstr(expr1, GRB.EQUAL, 0,
						"market_area_balance" + marketArea);
				expr1.clear();
			}

			// add to objective function: -q * p * Acceptance
			for (final MarketArea marketArea : marketAreas) {
				for (int bid = 0; bid < simpleBids.get(marketArea).size(); bid++) {

					final Bid bidPoint = simpleBids.get(marketArea).get(bid);
					float bidVolume = Float.NaN;
					if (bidPoint.getType() == BidType.ASK) {
						bidVolume = bidPoint.getVolume();
					} else if (bidPoint.getType() == BidType.SELL) {
						bidVolume = -bidPoint.getVolume();
					}

					objective.addTerm(bidVolume * bidPoint.getPrice(),
							accept[marketArea.getIdMarketCoupling() - 1][bid]);
				}
			}

			model.setObjective(objective, GRB.MAXIMIZE);

			// Write LP in file (each year the files are overwritten)
			if (hourOfYear == 0) {
				// Create log folder
				final File folder = new File(priceForecastPath);
				if (!folder.exists()) {
					folder.mkdirs();
				}
				model.write(priceForecastPath + File.separator + "MarketForecastGurobi_" + year
						+ "_" + hourOfYear + ".lp");
			}

			/* Solve model */

			model.optimize();

			if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
				logger.error(
						"Market clearing is not optimal! Please check the bid lists for Bugs.");
				// Create log folder
				final File folder = new File(priceForecastPath);
				if (!folder.exists()) {
					folder.mkdirs();
				}
				model.write(priceForecastPath + File.separator + "MarketForecastGurobi_" + year
						+ "_" + hourOfYear + ".lp");
				// compute IIS to find restrictive constraints
				model.computeIIS();
				for (int constrIndex = 0; constrIndex < model.getConstrs().length; constrIndex++) {
					if (model.getConstr(constrIndex).get(GRB.IntAttr.IISConstr) > 0) {
						logger.error("IIS-constraint (h" + hourOfYear + "): "
								+ model.getConstr(constrIndex).get(GRB.StringAttr.ConstrName));
					}
				}
			}

			/* Results */
			// Get flows and calculate net flows by market area
			setFlowForecast(marketAreas, model.get(GRB.DoubleAttr.X, flowMatrix), hourOfYear);

			// Get shadow prices (equal to market area prices)
			final double[] prices = model.get(GRB.DoubleAttr.Pi, marketAreaBalance);

			final Map<MarketArea, Map<Integer, Map<Integer, Float>>> results = new ConcurrentHashMap<>();
			for (final MarketArea marketArea : marketAreas) {

				float marketClearingPrice = (float) prices[marketArea.getIdMarketCoupling() - 1];
				final float minPriceAllowed = marketArea.getDayAheadMarketOperator()
						.getMinPriceAllowed();
				final float maxPriceAllowed = marketArea.getDayAheadMarketOperator()
						.getMaxPriceAllowed();
				if (Math.round(marketClearingPrice) < minPriceAllowed) {
					marketClearingPrice = minPriceAllowed;
				} else if (Math.round(marketClearingPrice) > maxPriceAllowed) {
					marketClearingPrice = maxPriceAllowed;
				}
				results.put(marketArea, new ConcurrentHashMap<>());
				results.get(marketArea).put(year, new ConcurrentHashMap<>());
				results.get(marketArea).get(year).put(hourOfYear, marketClearingPrice);

			}
			PriceForecastFutureOptimization.addToQueue(results, withAdditionalPlants);

			// Dispose everything
			model.dispose();
		} catch (final GRBException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Implements the market coupling algorithm. The method (following the
	 * COSMOS algorithm) maximizes social welfare based on bids from all market
	 * areas and interconnection capacities.
	 * 
	 * @throws GRBException
	 */
	private static void marketCouplingAlgorithmStorage(
			final OptimizationPeriodType optimizationPeriodType, final Integer year,
			final Integer timeIncrement, final Set<MarketArea> marketAreas,
			final Map<MarketArea, Map<Integer, Float>> futureDemand,
			final Map<MarketArea, Map<Integer, Float>> futureRenewableLoad,
			final Map<MarketArea, List<CostCap>> powerPlants,
			final Map<MarketArea, Map<Integer, Float>> seasonalStorage,
			final Map<MarketArea, List<PumpStoragePlant>> storageUnits,
			final Map<MarketArea, Map<PlantOption, Integer>> newPlants,
			final Map<MarketArea, Map<PlantOption, Integer>> newStorages,
			final Capacities capacitiesData, final Map<MarketArea, Float> startupSurplus)
			throws GRBException {

		// Define optimization period in hours based on type of price forecast
		// (weekly, monthly, yearly)
		final int OPTIMIZATION_PERIOD;

		// Create Gurobi model object
		if (optimizationPeriodType.equals(OptimizationPeriodType.YEARLY)) {
			OPTIMIZATION_PERIOD = Date.HOURS_PER_YEAR;

		} else if (optimizationPeriodType.equals(OptimizationPeriodType.MONTHLY)) {
			// Adjust if leap years are used
			OPTIMIZATION_PERIOD = Date.HOURS_PER_DAY
					* java.time.Month.of(timeIncrement).length(false);

		} else if (optimizationPeriodType.equals(OptimizationPeriodType.WEEKLY)) {
			if (timeIncrement < Date.WEEKS_PER_YEAR) {
				OPTIMIZATION_PERIOD = Date.HOURS_PER_WEEK;
			} else {
				OPTIMIZATION_PERIOD = Date.HOURS_PER_DAY;
			}

		} else {
			OPTIMIZATION_PERIOD = 0;
			logger.error(
					"Error in long-term price forecast: Type of optimization period (weekly, monthly, yearly) has not been defined!");
		}

		// Create Gurobi model object
		final GRBModel model = new GRBModel(env);
		/* Objective function */
		final GRBLinExpr objective = new GRBLinExpr();

		// Set Gurobi model parameters

		// Enables (1) or disables (0) console logging.
		final int LogToConsole = 0;
		model.getEnv().set(GRB.IntParam.LogToConsole, LogToConsole);

		// Set number of threads per model
		model.set(GRB.IntParam.Threads, 1);

		// Choose appropriate solver method: primal simplex (0), dual simplex
		// (1),
		// barrier (2)
		if (optimizationPeriodType.equals(OptimizationPeriodType.YEARLY)
				|| (optimizationPeriodType.equals(OptimizationPeriodType.MONTHLY))) {
			model.set(GRB.IntParam.Method, 2);
			model.set(GRB.IntParam.Crossover, 0);

		} else if (optimizationPeriodType.equals(OptimizationPeriodType.WEEKLY)) {
			model.set(GRB.IntParam.Method, 1);

		}

		// Set tolerances
		model.set(GRB.DoubleParam.OptimalityTol, 1e-5);

		// Controls the presolve level. A value of -1 corresponds to an
		// automatic setting. Other options are off (0), conservative (1),
		// or aggressive (2). More aggressive application of presolve takes
		// more time, but can sometimes lead to a significantly tighter
		// model.
		int presolveLevel;
		presolveLevel = -1;
		model.getEnv().set(GRB.IntParam.Presolve, presolveLevel);

		/* Definition of variables */

		// Acceptance variables for each power plant and storage unit
		// (charge/discharge)
		// - Set dimension of array
		final GRBVar[][][] accept = new GRBVar[OPTIMIZATION_PERIOD][marketAreas.size()][];
		final GRBVar[][][] acceptNewPowerPlants = new GRBVar[OPTIMIZATION_PERIOD][marketAreas
				.size()][];
		final GRBVar[][][] acceptNewStorageUnits = new GRBVar[OPTIMIZATION_PERIOD][marketAreas
				.size()][];
		final GRBVar[][][] acceptSeasonalStorage = new GRBVar[OPTIMIZATION_PERIOD][marketAreas
				.size()][];
		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			for (final MarketArea marketArea : marketAreas) {
				accept[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBVar[powerPlants.get(marketArea).size()
								+ (2 * storageUnits.get(marketArea).size()) + 1];
				acceptNewPowerPlants[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBVar[newPlants.get(marketArea).size()];
				acceptNewStorageUnits[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBVar[2 * newStorages.get(marketArea).size()];
				acceptSeasonalStorage[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBVar[SeasonalStorageTrader.getIterations()];
			}
		}

		// Storage level variables
		// - Set dimension of array
		final GRBVar[][][] storageLevels = new GRBVar[OPTIMIZATION_PERIOD][marketAreas.size()][];
		final GRBVar[][][] newStorageLevels = new GRBVar[OPTIMIZATION_PERIOD][marketAreas.size()][];
		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			for (final MarketArea marketArea : marketAreas) {
				storageLevels[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBVar[storageUnits.get(marketArea).size()];
				newStorageLevels[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBVar[newStorages.get(marketArea).size()];
			}
		}

		// Flow variables
		// - Set dimension of array
		final GRBVar[][][] flowMatrix = new GRBVar[OPTIMIZATION_PERIOD][marketAreas
				.size()][marketAreas.size()];

		// Acceptance variables for each power plant and storage unit
		// (charge/discharge)
		// - Add variables to model
		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			final int hourOfYear;

			if (optimizationPeriodType.equals(OptimizationPeriodType.YEARLY)) {
				hourOfYear = hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.MONTHLY)) {
				// Adjust if leap years are used
				hourOfYear = Date.getFirstYearlyHourOfMonth(timeIncrement, 2015)
						+ hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.WEEKLY)) {
				hourOfYear = ((timeIncrement - 1) * Date.HOURS_PER_WEEK) + hourOfOptimizationPeriod;
			} else {
				hourOfYear = 0;
				logger.error(
						"Error in long-term price forecast: Type of optimization period (weekly, monthly, yearly) has not been defined!");
			}

			for (final MarketArea marketArea : marketAreas) {
				int offsetID = 0;
				// Power plants, storage units charge, storage units discharge,
				// peaker
				final double[] acceptVariablesLowerBounds = new double[powerPlants.get(marketArea)
						.size() + (2 * storageUnits.get(marketArea).size()) + 1];
				final double[] acceptVariablesUpperBounds = new double[powerPlants.get(marketArea)
						.size() + (2 * storageUnits.get(marketArea).size()) + 1];
				final String[] acceptVariablesNames = new String[powerPlants.get(marketArea).size()
						+ (2 * storageUnits.get(marketArea).size()) + 1];

				// Power plants
				for (int powerPlantID = 0; powerPlantID < powerPlants.get(marketArea)
						.size(); powerPlantID++) {
					final CostCap powerPlant = powerPlants.get(marketArea).get(powerPlantID);
					float availabilityFactor;

					// Get availability factor
					availabilityFactor = marketArea.getAvailabilityFactors().getAvailabilityFactors(
							powerPlant.getFuelName(),
							Date.isWeekDay(Date.getDayFromHourOfYear(hourOfYear)));

					final String variableName = "accept_hour_" + hourOfOptimizationPeriod + "_Area_"
							+ marketArea.getInitials() + "_Plant_ID_" + powerPlantID;

					final float bidVolume = powerPlant.getNetCapacity() * availabilityFactor;

					acceptVariablesLowerBounds[powerPlantID] = 0f;
					acceptVariablesUpperBounds[powerPlantID] = bidVolume;
					acceptVariablesNames[powerPlantID] = variableName;

				}

				// Storage units

				// Discharge mode
				offsetID += powerPlants.get(marketArea).size();
				for (int storageUnitID = 0; storageUnitID < storageUnits.get(marketArea)
						.size(); storageUnitID++) {
					final PumpStoragePlant storageUnit = storageUnits.get(marketArea)
							.get(storageUnitID);
					final String variableNameDischarge = "accept_hour_" + hourOfOptimizationPeriod
							+ "_Area_" + marketArea.getInitials() + "_Storage_ID_" + storageUnitID
							+ "_discharge";
					acceptVariablesLowerBounds[offsetID + storageUnitID] = 0;
					acceptVariablesUpperBounds[offsetID + storageUnitID] = storageUnit
							.getGenerationCapacity();
					acceptVariablesNames[offsetID + storageUnitID] = variableNameDischarge;

				}

				// Charge mode
				offsetID += storageUnits.get(marketArea).size();
				for (int storageUnitID = 0; storageUnitID < storageUnits.get(marketArea)
						.size(); storageUnitID++) {
					final PumpStoragePlant storageUnit = storageUnits.get(marketArea)
							.get(storageUnitID);
					final String variableNameCharge = "accept_hour_" + hourOfOptimizationPeriod
							+ "_Area_" + marketArea.getInitials() + "_Storage_ID_" + storageUnitID
							+ "_charge";
					acceptVariablesLowerBounds[offsetID + storageUnitID] = 0;
					acceptVariablesUpperBounds[offsetID + storageUnitID] = storageUnit
							.getPumpCapacity();
					acceptVariablesNames[offsetID + storageUnitID] = variableNameCharge;

				}

				// Peaker
				offsetID += storageUnits.get(marketArea).size();
				final String variableName = "accept_hour_" + hourOfOptimizationPeriod + "_Area_"
						+ marketArea.getInitials() + "_Peaker";
				final float bidVolume = marketArea.getDemandData().getHourlyDemand(year,
						hourOfYear);
				acceptVariablesLowerBounds[offsetID] = 0;
				acceptVariablesUpperBounds[offsetID] = bidVolume * 10;
				acceptVariablesNames[offsetID] = variableName;

				// Add all accept variables to model
				accept[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling() - 1] = model
						.addVars(acceptVariablesLowerBounds, acceptVariablesUpperBounds, null, null,
								acceptVariablesNames);

				// New power plants
				int newPowerPlantID = 0;
				final double[] acceptNewPowerPlantsVariablesUpperBounds = new double[newPlants
						.get(marketArea).size()];
				final String[] acceptNewPowerPlantsVariablesNames = new String[newPlants
						.get(marketArea).size()];

				for (final PlantOption newPowerPlant : newPlants.get(marketArea).keySet()) {
					final int numberOfUnits = newPlants.get(marketArea).get(newPowerPlant);
					float availabilityFactor;
					// Get availability factor

					availabilityFactor = marketArea.getAvailabilityFactors().getAvailabilityFactors(
							newPowerPlant.getFuelName(),
							Date.isWeekDay(Date.getDayFromHourOfYear(hourOfYear)));

					final String variableNameNewPowerPlant = "accept_hour_"
							+ hourOfOptimizationPeriod + "_Area_" + marketArea.getInitials()
							+ "_New_Plant_ID_" + newPowerPlantID;

					final float bidVolumeNewPowerPlant = numberOfUnits
							* newPowerPlant.getNetCapacity() * availabilityFactor;

					acceptNewPowerPlantsVariablesUpperBounds[newPowerPlantID] = bidVolumeNewPowerPlant;
					acceptNewPowerPlantsVariablesNames[newPowerPlantID] = variableNameNewPowerPlant;

					newPowerPlantID += 1;
				}

				// Add all acceptNewPowerPlants variables to model
				acceptNewPowerPlants[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = model.addVars(null, acceptNewPowerPlantsVariablesUpperBounds, null,
								null, acceptNewPowerPlantsVariablesNames);

				// New storage units
				// Discharge mode
				offsetID = 0;
				int newStorageUnitID = 0;
				final double[] acceptNewStorageUnitsVariablesUpperBounds = new double[2
						* newStorages.get(marketArea).size()];
				final String[] acceptNewStorageUnitsVariablesNames = new String[2
						* newStorages.get(marketArea).size()];

				for (final PlantOption newStorageUnit : newStorages.get(marketArea).keySet()) {
					final int numberOfUnits = newStorages.get(marketArea).get(newStorageUnit);
					final String variableNameDischarge = "accept_hour_" + hourOfOptimizationPeriod
							+ "_Area_" + marketArea.getInitials() + "_New_Storage_ID_"
							+ newStorageUnitID + "_discharge";
					acceptNewStorageUnitsVariablesUpperBounds[offsetID
							+ newStorageUnitID] = (double) numberOfUnits
									* newStorageUnit.getDischargeCapacity();
					acceptNewStorageUnitsVariablesNames[offsetID
							+ newStorageUnitID] = variableNameDischarge;

					newStorageUnitID += 1;
				}

				// Charge mode
				offsetID += newStorages.get(marketArea).size();
				newStorageUnitID = 0;
				for (final PlantOption newStorageUnit : newStorages.get(marketArea).keySet()) {
					final int numberOfUnits = newStorages.get(marketArea).get(newStorageUnit);
					final String variableNameCharge = "accept_hour_" + hourOfOptimizationPeriod
							+ "_Area_" + marketArea.getInitials() + "_New_Storage_ID_"
							+ newStorageUnitID + "_charge";
					acceptNewStorageUnitsVariablesUpperBounds[offsetID
							+ newStorageUnitID] = (double) numberOfUnits
									* newStorageUnit.getChargeCapacity();
					acceptNewStorageUnitsVariablesNames[offsetID
							+ newStorageUnitID] = variableNameCharge;

					newStorageUnitID += 1;
				}

				// Add all acceptNewStorageUnits variables to model
				acceptNewStorageUnits[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = model.addVars(null, acceptNewStorageUnitsVariablesUpperBounds, null,
								null, acceptNewStorageUnitsVariablesNames);

				// Seasonal Storage
				final int iterations = SeasonalStorageTrader.getIterations();
				final double[] acceptSeasonalStorageUpperBounds = new double[iterations];
				final String[] acceptSeasonalStorageNames = new String[iterations];

				for (int iterationSeasonalStorage = 0; iterationSeasonalStorage < iterations; iterationSeasonalStorage++) {
					final String variableNameSeasonal = "accept_hour_" + hourOfOptimizationPeriod
							+ "_Area_" + marketArea.getInitials() + "_SeasonalStorage_Iteration_"
							+ iterationSeasonalStorage;
					acceptSeasonalStorageNames[iterationSeasonalStorage] = variableNameSeasonal;
					acceptSeasonalStorageUpperBounds[iterationSeasonalStorage] = seasonalStorage
							.get(marketArea).get(hourOfOptimizationPeriod) / iterations;
				}

				// Add all accept variables to model
				acceptSeasonalStorage[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = model.addVars(null, acceptSeasonalStorageUpperBounds, null, null,
								acceptSeasonalStorageNames);

			}
		}

		// Storage level variables
		// - Add variables to model
		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			for (final MarketArea marketArea : marketAreas) {
				final double[] storageLevelsVariablesUpperBounds = new double[storageUnits
						.get(marketArea).size()];
				final String[] storageLevelsVariablesNames = new String[storageUnits.get(marketArea)
						.size()];

				for (int storageUnitID = 0; storageUnitID < storageUnits.get(marketArea)
						.size(); storageUnitID++) {
					final PumpStoragePlant storageUnit = storageUnits.get(marketArea)
							.get(storageUnitID);
					final String variableName = "storageLevel_hour_" + hourOfOptimizationPeriod
							+ "_Area_" + marketArea.getInitials() + "_Storage_ID_" + storageUnitID;
					storageLevelsVariablesUpperBounds[storageUnitID] = storageUnit
							.getStorageVolume();
					storageLevelsVariablesNames[storageUnitID] = variableName;

				}

				// Add all storageLevels variables to model
				storageLevels[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = model.addVars(null, storageLevelsVariablesUpperBounds, null, null,
								storageLevelsVariablesNames);

				int newStorageUnitID = 0;
				final double[] newStorageLevelsVariablesUpperBounds = new double[newStorages
						.get(marketArea).size()];
				final String[] newStorageLevelsVariablesNames = new String[newStorages
						.get(marketArea).size()];

				for (final PlantOption newStorageUnit : newStorages.get(marketArea).keySet()) {
					final int numberOfUnits = newStorages.get(marketArea).get(newStorageUnit);
					final String variableName = "storageLevel_hour_" + hourOfOptimizationPeriod
							+ "_Area_" + marketArea.getInitials() + "_New_Storage_ID_"
							+ newStorageUnitID;
					newStorageLevelsVariablesUpperBounds[newStorageUnitID] = (double) numberOfUnits
							* newStorageUnit.getStorageVolume();
					newStorageLevelsVariablesNames[newStorageUnitID] = variableName;

					newStorageUnitID += 1;
				}

				// Add all newStorageLevels variables to model
				newStorageLevels[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = model.addVars(null, newStorageLevelsVariablesUpperBounds, null, null,
								newStorageLevelsVariablesNames);
			}
		}

		// Flow variables
		// - Add variables to model
		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			final int hourOfYear;

			if (optimizationPeriodType.equals(OptimizationPeriodType.YEARLY)) {
				hourOfYear = hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.MONTHLY)) {
				// Adjust if leap years are used
				hourOfYear = Date.getFirstYearlyHourOfMonth(timeIncrement, 2015)
						+ hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.WEEKLY)) {
				hourOfYear = ((timeIncrement - 1) * Date.HOURS_PER_WEEK) + hourOfOptimizationPeriod;
			} else {
				hourOfYear = 0;
				logger.error(
						"Error in long-term price forecast: Type of optimization period (weekly, monthly, yearly) has not been defined!");
			}

			for (final MarketArea fromMarketArea : marketAreas) {
				for (final MarketArea toMarketArea : marketAreas) {
					final int idMarketCouplingFromMarketArea = fromMarketArea.getIdMarketCoupling()
							- 1;
					final int idMarketCouplingToMarketArea = toMarketArea.getIdMarketCoupling() - 1;

					// Set name of variable
					final String name = "hour_" + hourOfOptimizationPeriod + "_flow_from_"
							+ fromMarketArea.getInitials() + "_to_" + toMarketArea.getInitials();

					if (fromMarketArea.equals(toMarketArea)) {
						flowMatrix[hourOfOptimizationPeriod][idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea] = model
								.addVar(0, 0, 0.0, GRB.CONTINUOUS, name);
					} else {
						// Set upper bound equal to the available capacity
						// between
						// the two market areas
						double upperBound = 0;

						upperBound = capacitiesData.getInterconnectionCapacityHour(fromMarketArea,
								toMarketArea, year, hourOfYear);

						// Add variable
						flowMatrix[hourOfOptimizationPeriod][idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea] = model
								.addVar(0, upperBound, 0.0, GRB.CONTINUOUS, name);

						objective.addTerm(penaltyInterconnectorFlows,
								flowMatrix[hourOfOptimizationPeriod][idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea]);

					}
				}
			}
		}

		// Needed for constraints
		final GRBLinExpr expr1 = new GRBLinExpr();

		/* Market area balance */
		// Sum(Accept * Volume) = 0
		final GRBConstr[][] marketAreaBalance = new GRBConstr[OPTIMIZATION_PERIOD][marketAreas
				.size()];

		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			for (final MarketArea marketArea : marketAreas) {
				double[] marketAreaBalanceCoefficients = new double[accept[hourOfOptimizationPeriod][marketArea
						.getIdMarketCoupling() - 1].length];

				final int marketAreaId = marketArea.getIdMarketCoupling() - 1;
				// Add power plants
				for (int powerPlantID = 0; powerPlantID < powerPlants.get(marketArea)
						.size(); powerPlantID++) {
					marketAreaBalanceCoefficients[powerPlantID] = -1;

				}

				// Add storage units (discharge mode)
				int offsetID = powerPlants.get(marketArea).size();

				for (int storageUnitID = 0; storageUnitID < storageUnits.get(marketArea)
						.size(); storageUnitID++) {
					marketAreaBalanceCoefficients[offsetID + storageUnitID] = -1;

				}

				// Add storage units (charge mode)
				offsetID += storageUnits.get(marketArea).size();

				for (int storageUnitID = 0; storageUnitID < storageUnits.get(marketArea)
						.size(); storageUnitID++) {
					marketAreaBalanceCoefficients[offsetID + storageUnitID] = 1;

				}

				// Add peaker
				offsetID += storageUnits.get(marketArea).size();
				marketAreaBalanceCoefficients[offsetID] = -1;

				// Add all accept expressions to model
				expr1.addTerms(marketAreaBalanceCoefficients,
						accept[hourOfOptimizationPeriod][marketAreaId]);

				// Add new power plants
				marketAreaBalanceCoefficients = new double[acceptNewPowerPlants[hourOfOptimizationPeriod][marketArea
						.getIdMarketCoupling() - 1].length];

				for (int newPowerPlantID = 0; newPowerPlantID < newPlants.get(marketArea)
						.size(); newPowerPlantID++) {
					marketAreaBalanceCoefficients[newPowerPlantID] = -1;

				}
				// Add all acceptNewPowerPlants expressions to model
				expr1.addTerms(marketAreaBalanceCoefficients,
						acceptNewPowerPlants[hourOfOptimizationPeriod][marketAreaId]);

				// Add storage units (discharge mode)
				marketAreaBalanceCoefficients = new double[acceptNewStorageUnits[hourOfOptimizationPeriod][marketArea
						.getIdMarketCoupling() - 1].length];

				offsetID = 0;
				for (int newStorageUnitID = 0; newStorageUnitID < newStorages.get(marketArea)
						.size(); newStorageUnitID++) {
					marketAreaBalanceCoefficients[offsetID + newStorageUnitID] = -1;

				}

				// Add storage units (charge mode)
				offsetID += newStorages.get(marketArea).size();
				for (int newStorageUnitID = 0; newStorageUnitID < newStorages.get(marketArea)
						.size(); newStorageUnitID++) {
					marketAreaBalanceCoefficients[offsetID + newStorageUnitID] = 1;

				}

				// Add all acceptNewStorageUnits expressions to model
				expr1.addTerms(marketAreaBalanceCoefficients,
						acceptNewStorageUnits[hourOfOptimizationPeriod][marketAreaId]);

				// Add Seasonal storages
				marketAreaBalanceCoefficients = new double[acceptSeasonalStorage[hourOfOptimizationPeriod][marketArea
						.getIdMarketCoupling() - 1].length];

				for (int index = 0; index < marketAreaBalanceCoefficients.length; index++) {
					// Due to production
					marketAreaBalanceCoefficients[index] = -1;
				}
				// Add all acceptSeasonalStorage expressions to model
				expr1.addTerms(marketAreaBalanceCoefficients,
						acceptSeasonalStorage[hourOfOptimizationPeriod][marketAreaId]);

				// Add residual demand
				expr1.addConstant(futureDemand.get(marketArea).get(hourOfOptimizationPeriod)
						- futureRenewableLoad.get(marketArea).get(hourOfOptimizationPeriod));

				// Add flows
				for (int i = 0; i < flowMatrix[hourOfOptimizationPeriod].length; i++) {
					// Exports
					expr1.addTerm(1, flowMatrix[hourOfOptimizationPeriod][marketAreaId][i]);
					// Imports
					expr1.addTerm(-1, flowMatrix[hourOfOptimizationPeriod][i][marketAreaId]);
				}

				marketAreaBalance[hourOfOptimizationPeriod][marketAreaId] = model.addConstr(expr1,
						GRB.LESS_EQUAL, 0, "hour_" + hourOfOptimizationPeriod
								+ "_market_area_balance" + marketArea.getInitials());
				expr1.clear();
			}
		}

		// Needed for constraints
		final GRBLinExpr expr2 = new GRBLinExpr();

		// Storage level constraints
		// One constraint for each hour of the day plus a constraint for the end
		// of the
		// day
		final GRBConstr[][][] storageBalance = new GRBConstr[OPTIMIZATION_PERIOD + 1][marketAreas
				.size()][];
		final GRBConstr[][][] newStorageBalance = new GRBConstr[OPTIMIZATION_PERIOD + 1][marketAreas
				.size()][];

		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < (OPTIMIZATION_PERIOD
				+ 1); hourOfOptimizationPeriod++) {
			for (final MarketArea marketArea : marketAreas) {
				storageBalance[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBConstr[storageUnits.get(marketArea).size()];
				newStorageBalance[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
						- 1] = new GRBConstr[newStorages.get(marketArea).size()];
			}
		}

		for (final MarketArea marketArea : marketAreas) {
			for (int storageUnitID = 0; storageUnitID < storageUnits.get(marketArea)
					.size(); storageUnitID++) {
				final PumpStoragePlant storageUnit = storageUnits.get(marketArea)
						.get(storageUnitID);
				// All hours
				for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
					// current hour
					expr2.addTerm(1,
							storageLevels[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
									- 1][storageUnitID]);
					// previous hour
					if (hourOfOptimizationPeriod == 0) {
						expr2.addConstant(-0.25 * storageUnit.getStorageVolume());
					} else {
						expr2.addTerm(-1, storageLevels[hourOfOptimizationPeriod - 1][marketArea
								.getIdMarketCoupling() - 1][storageUnitID]);
					}

					// discharge
					int offsetID = powerPlants.get(marketArea).size();
					expr2.addTerm(1 / storageUnit.getGenerationEfficiency(),
							accept[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
									- 1][offsetID + storageUnitID]);

					// charge
					offsetID += storageUnits.get(marketArea).size();
					expr2.addTerm(-storageUnit.getChargeEfficiency(),
							accept[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
									- 1][offsetID + storageUnitID]);

					// balance
					storageBalance[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
							- 1][storageUnitID] = model.addConstr(
									expr2, GRB.EQUAL, 0,
									"storageBalance_hour_" + hourOfOptimizationPeriod + "_Area_"
											+ marketArea.getInitials() + "_Storage_ID_"
											+ storageUnitID);
					expr2.clear();
				}

				// End hour
				expr2.addTerm(1,
						storageLevels[OPTIMIZATION_PERIOD - 1][marketArea.getIdMarketCoupling()
								- 1][storageUnitID]);
				// One hour later in the list of constraints, since two
				// constraints are required
				// for the last hour of the day
				storageBalance[OPTIMIZATION_PERIOD][marketArea.getIdMarketCoupling()
						- 1][storageUnitID] = model.addConstr(expr2, GRB.EQUAL,
								0.25 * storageUnit.getStorageVolume(),
								"storageBalance_endOfDay" + "_Area_" + marketArea.getInitials()
										+ "_Storage_ID_" + storageUnitID);
				expr2.clear();
			}

			// New storage units
			int newStorageUnitID = 0;
			for (final PlantOption newStorageUnit : newStorages.get(marketArea).keySet()) {
				final int numberOfUnits = newStorages.get(marketArea).get(newStorageUnit);
				// All hours
				for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
					// current hour
					expr2.addTerm(1, newStorageLevels[hourOfOptimizationPeriod][marketArea
							.getIdMarketCoupling() - 1][newStorageUnitID]);
					// previous hour
					if (hourOfOptimizationPeriod == 0) {
						expr2.addConstant(
								-0.25 * numberOfUnits * newStorageUnit.getStorageVolume());
					} else {
						expr2.addTerm(-1, newStorageLevels[hourOfOptimizationPeriod - 1][marketArea
								.getIdMarketCoupling() - 1][newStorageUnitID]);
					}

					// discharge
					int offsetID = 0;
					expr2.addTerm(1 / newStorageUnit.getDischargeEfficiency(),
							acceptNewStorageUnits[hourOfOptimizationPeriod][marketArea
									.getIdMarketCoupling() - 1][offsetID + newStorageUnitID]);

					// charge
					offsetID += newStorages.get(marketArea).size();
					expr2.addTerm(-newStorageUnit.getChargeEfficiency(),
							acceptNewStorageUnits[hourOfOptimizationPeriod][marketArea
									.getIdMarketCoupling() - 1][offsetID + newStorageUnitID]);

					// balance
					newStorageBalance[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
							- 1][newStorageUnitID] = model.addConstr(
									expr2, GRB.EQUAL, 0,
									"storageBalance_hour_" + hourOfOptimizationPeriod + "_Area_"
											+ marketArea.getInitials() + "_New_Storage_ID_"
											+ newStorageUnitID);
					expr2.clear();
				}

				// End hour
				expr2.addTerm(1,
						newStorageLevels[OPTIMIZATION_PERIOD - 1][marketArea.getIdMarketCoupling()
								- 1][newStorageUnitID]);
				// One hour later in the list of constraints, since two
				// constraints are required
				// for the last hour of the day
				newStorageBalance[OPTIMIZATION_PERIOD][marketArea.getIdMarketCoupling()
						- 1][newStorageUnitID] = model.addConstr(expr2, GRB.EQUAL,
								0.25 * numberOfUnits * newStorageUnit.getStorageVolume(),
								"storageBalance_endOfDay" + "_Area_" + marketArea.getInitials()
										+ "_New_Storage_ID_" + newStorageUnitID);
				expr2.clear();
				newStorageUnitID += 1;
			}
		}

		// add to objective function: Acceptance of power plant bids
		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			final int hourOfYear;
			final int dayOfYear;

			if (optimizationPeriodType.equals(OptimizationPeriodType.YEARLY)) {
				hourOfYear = hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.MONTHLY)) {
				// Adjust if leap years are used
				hourOfYear = Date.getFirstYearlyHourOfMonth(timeIncrement, 2015)
						+ hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.WEEKLY)) {
				hourOfYear = ((timeIncrement - 1) * Date.HOURS_PER_WEEK) + hourOfOptimizationPeriod;
			} else {
				hourOfYear = 0;
				logger.error(
						"Error in long-term price forecast: Type of optimization period (weekly, monthly, yearly) has not been defined!");
			}

			dayOfYear = Date.getDayFromHourOfYear(hourOfYear);

			for (final MarketArea marketArea : marketAreas) {
				double[] objectiveCoefficients = new double[powerPlants.get(marketArea).size()];

				for (int powerPlantID = 0; powerPlantID < powerPlants.get(marketArea)
						.size(); powerPlantID++) {
					final CostCap powerPlant = powerPlants.get(marketArea).get(powerPlantID);
					float bidPrice = powerPlant.getCostsVar(year, marketArea);
					bidPrice += startupSurplus.get(marketArea);
					objectiveCoefficients[powerPlantID] = -bidPrice;

				}

				objective.addTerms(objectiveCoefficients,
						accept[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling() - 1], 0,
						powerPlants.get(marketArea).size());

				// peaker
				final int offsetID = powerPlants.get(marketArea).size()
						+ (2 * storageUnits.get(marketArea).size());
				final float bidPrice = marketArea.getPriceForwardMaximum();
				objective.addTerm(-bidPrice,
						accept[hourOfOptimizationPeriod][marketArea.getIdMarketCoupling()
								- 1][offsetID]);

				// new power plants
				objectiveCoefficients = new double[newPlants.get(marketArea).size()];

				int newPowerPlantID = 0;
				for (final PlantOption newPowerPlant : newPlants.get(marketArea).keySet()) {
					float newBidPrice = newPowerPlant.getCostsVar(year, marketArea);
					newBidPrice += startupSurplus.get(marketArea);
					objectiveCoefficients[newPowerPlantID] = -newBidPrice;
					newPowerPlantID += 1;
				}

				objective.addTerms(objectiveCoefficients,
						acceptNewPowerPlants[hourOfOptimizationPeriod][marketArea
								.getIdMarketCoupling() - 1]);

				// Seasonal storage
				// known operation of seasonal storage plants
				// Only values not equal zero
				objectiveCoefficients = new double[SeasonalStorageTrader.getIterations()];
				float seasonalStorageOperation = seasonalStorage.get(marketArea)
						.get(hourOfOptimizationPeriod);
				if (seasonalStorageOperation > 0) {
					seasonalStorageOperation /= SeasonalStorageTrader.getIterations();
					for (int iteration = 1; iteration <= SeasonalStorageTrader
							.getIterations(); iteration++) {
						objectiveCoefficients[iteration - 1] = -SeasonalStorageTrader
								.getBidPrice(marketArea, iteration);
					}
				}
				objective.addTerms(objectiveCoefficients,
						acceptSeasonalStorage[hourOfOptimizationPeriod][marketArea
								.getIdMarketCoupling() - 1]);
			}
		}

		model.setObjective(objective, GRB.MAXIMIZE);

		/* Solve model */
		model.optimize();
		if (model.get(GRB.IntAttr.Status) == GRB.Status.INFEASIBLE) {
			logger.error("Market clearing infeasible! Please check the bid lists for Bugs.");
		}
		if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
			logger.error("Market clearing not optimal! Please check the bid lists for Bugs.");

			// Create log folder
			final File folder = new File(priceForecastPath);
			if (!folder.exists()) {
				folder.mkdirs();
			}
			model.write(priceForecastPath + File.separator + "MarketForecastGurobi_" + year + "_"
					+ timeIncrement + ".lp");
			// compute IIS to find restrictive constraints
			model.computeIIS();
			for (int constrIndex = 0; constrIndex < model.getConstrs().length; constrIndex++) {
				if (model.getConstr(constrIndex).get(GRB.IntAttr.IISConstr) > 0) {
					logger.error("IIS-constraint (h" + timeIncrement + "): "
							+ model.getConstr(constrIndex).get(GRB.StringAttr.ConstrName));
				}
			}
		}
		/* Results */

		// Get shadow prices (equal to market area prices)
		final double[][] prices = model.get(GRB.DoubleAttr.Pi, marketAreaBalance);

		final Map<MarketArea, Map<Integer, Map<Integer, Float>>> results = new ConcurrentHashMap<>();
		for (int hourOfOptimizationPeriod = 0; hourOfOptimizationPeriod < OPTIMIZATION_PERIOD; hourOfOptimizationPeriod++) {
			final int hourOfYear;

			if (optimizationPeriodType.equals(OptimizationPeriodType.YEARLY)) {
				hourOfYear = hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.MONTHLY)) {
				// Adjust if leap years are used
				hourOfYear = Date.getFirstYearlyHourOfMonth(timeIncrement, 2015)
						+ hourOfOptimizationPeriod;
			} else if (optimizationPeriodType.equals(OptimizationPeriodType.WEEKLY)) {
				hourOfYear = ((timeIncrement - 1) * Date.HOURS_PER_WEEK) + hourOfOptimizationPeriod;
			} else {
				hourOfYear = 0;
				logger.error(
						"Error in long-term price forecast: Type of optimization period (weekly, monthly, yearly) has not been defined!");
			}
			// Get flows and calculate net flows by market area
			setFlowForecast(marketAreas,
					model.get(GRB.DoubleAttr.X, flowMatrix[hourOfOptimizationPeriod]), hourOfYear);
			for (final MarketArea marketArea : marketAreas) {

				float marketClearingPrice = (float) prices[hourOfOptimizationPeriod][marketArea
						.getIdMarketCoupling() - 1];

				final float minPriceAllowed = marketArea.getDayAheadMarketOperator()
						.getMinPriceAllowed();
				final float maxPriceAllowed = marketArea.getDayAheadMarketOperator()
						.getMaxPriceAllowed();

				marketClearingPrice = Math.max(minPriceAllowed,
						Math.min(marketClearingPrice, maxPriceAllowed));

				if (results.get(marketArea) == null) {
					results.put(marketArea, new ConcurrentHashMap<>());
				}
				if (results.get(marketArea).get(year) == null) {
					results.get(marketArea).put(year, new ConcurrentHashMap<>());
				}
				results.get(marketArea).get(year).put(hourOfYear, marketClearingPrice);
			}
		}
		PriceForecastFutureOptimization.addToQueue(results, false);

		// Dispose everything
		model.dispose();

	}

	/**
	 * Not yet fully implemented since best solver configuration could not be
	 * determined on current hardware
	 **/
	public static void marketCouplingForecastStorage(OptimizationPeriodType optimizationPeriodType,
			int year, final Integer timeIncrement, final Set<MarketArea> marketAreas,
			final Map<MarketArea, Map<Integer, Float>> futureDemand,
			final Map<MarketArea, Map<Integer, Float>> futureRenewableLoad,
			final Map<MarketArea, List<CostCap>> powerPlants,
			final Map<MarketArea, Map<Integer, Float>> seasonalStorage,
			final Map<MarketArea, List<PumpStoragePlant>> storageUnits,
			final Map<MarketArea, Map<PlantOption, Integer>> newPlants,
			final Map<MarketArea, Map<PlantOption, Integer>> newStorages,
			final Capacities capacitiesData, final Map<MarketArea, Float> startupSurplus)
			throws GRBException {
		if (optimizationPeriodType == OptimizationPeriodType.YEARLY) {
			marketCouplingAlgorithmStorage(OptimizationPeriodType.YEARLY, year, null, marketAreas,
					futureDemand, futureRenewableLoad, powerPlants, seasonalStorage, storageUnits,
					newPlants, newStorages, capacitiesData, startupSurplus);
		} else if (optimizationPeriodType == OptimizationPeriodType.MONTHLY) {
			marketCouplingAlgorithmStorage(OptimizationPeriodType.MONTHLY, year, timeIncrement,
					marketAreas, futureDemand, futureRenewableLoad, powerPlants, seasonalStorage,
					storageUnits, newPlants, newStorages, capacitiesData, startupSurplus);
		} else if (optimizationPeriodType == OptimizationPeriodType.WEEKLY) {
			marketCouplingAlgorithmStorage(OptimizationPeriodType.WEEKLY, year, timeIncrement,
					marketAreas, futureDemand, futureRenewableLoad, powerPlants, seasonalStorage,
					storageUnits, newPlants, newStorages, capacitiesData, startupSurplus);
		}
	}

	private static void setFlowForecast(Set<MarketArea> marketAreas,
			double[][] flowsBetweenMarketAreas, final int hourOfYear) throws GRBException {
		final Map<MarketArea, Map<MarketArea, Map<Integer, Float>>> results = new LinkedHashMap<>();
		for (final MarketArea fromMarketArea : marketAreas) {

			results.put(fromMarketArea, new LinkedHashMap<>());
			for (final MarketArea toMarketArea : marketAreas) {
				results.get(fromMarketArea).put(toMarketArea, new LinkedHashMap<>());
				results.get(fromMarketArea).get(toMarketArea).put(hourOfYear, 0f);
			}
		}
		final double[][] flowsBetweenMarketAreasTemp = flowsBetweenMarketAreas;
		// Get fromMarketArea
		for (final MarketArea fromMarketArea : marketAreas) {

			// Get toMarketArea
			for (final MarketArea toMarketArea : marketAreas) {
				final int idFromMarketArea = fromMarketArea.getIdMarketCoupling() - 1;
				final int idToMarketArea = toMarketArea.getIdMarketCoupling() - 1;

				if (fromMarketArea.equals(toMarketArea)) {
					// No "internal" flow
					if (flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] != 0) {
						logger.error(
								"Result shows direct flow from market area into same market are ("
										+ fromMarketArea + ") in hour " + hourOfYear + " on day "
										+ Date.getDayOfYear() + ". Market coupling cancelled.");
						throw new GRBException();
					} else {
						continue;
					}
				}

				// Netting of flows between fromMarketArea and toMarketArea
				if (flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] < flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea]) {
					flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea] -= flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea];
					flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] = 0;
				} else {
					flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] -= flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea];
					flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea] = 0;
				}
				final float flow = (float) flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea];

				results.get(fromMarketArea).get(toMarketArea).put(hourOfYear, flow);
			}

		}
		// Set flow between market areas
		PriceForecastFutureOptimization.addFlows(results);
	}
}
