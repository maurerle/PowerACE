package markets.clearing;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.exchange.Capacities;
import gurobi.GRB;
import gurobi.GRBConstr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import markets.bids.Bid;
import markets.bids.Bid.BidType;
import markets.operator.spot.MarketCouplingOperator;
import markets.trader.TraderType;
import simulations.MarketArea;
import simulations.scheduling.Date;

/** Market coupling algorithm for one hour */
public class MarketCouplingHourly {
	/**
	 * Create Gurobi environment object. Just use one environment for runtime
	 * reasons
	 */
	private static GRBEnv env;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(MarketCouplingHourly.class.getName());

	/**
	 * Set a small penalty that no peaker will produce for another market area.
	 * With Gurobi 8.0 penalty value has to be greater than 1e-5 otherwise it
	 * doesn't seem to has an effect. Margin seems advisable, so we choose 1e-3.
	 */
	private static double penaltyInterconnectorFlows = -0.001;

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
	 * Implements the market coupling algorithm. The method (following the
	 * COSMOS algorithm) maximizes social welfare based on bids from all market
	 * areas and interconnection capacities.
	 * 
	 * @throws GRBException
	 */
	public static void marketCouplingAlgorithmHourly(final int hourOfDay,
			List<MarketArea> marketAreas, Map<MarketArea, List<Bid>> simpleBids,
			MarketCouplingOperator marketCouplingOperator, Capacities capacitiesData)
			throws GRBException {

		// Create Gurobi model object
		final GRBModel model = new GRBModel(env);

		// Enables (1) or disables (0) console logging.
		int LogToConsole;
		LogToConsole = 0;
		model.getEnv().set(GRB.IntParam.LogToConsole, LogToConsole);

		/* Define solving method */
		model.set(GRB.IntParam.Method, GRB.METHOD_DUAL);

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
		// Gurobi was designed to be deterministic, meaning that it will produce
		// the same results so long as you don't change the computer, Gurobi
		// version, matrix, or parameters. One of the known exception is setting
		// a time limit

		// The MIP solver will terminate (with an optimal result) when the
		// relative gap between the lower and upper objective bound is less
		// than MIPGap times the upper bound.
		// Default value: 1e-4
		// Range [0, infinity]
		double MIPGap;
		MIPGap = 1E-4;
		model.getEnv().set(GRB.DoubleParam.MIPGap, MIPGap);

		// Needed for constraints
		final GRBLinExpr expr1 = new GRBLinExpr();

		// Controls the presolve level. A value of -1 corresponds to an
		// automatic setting. Other options are off (0), conservative (1),
		// or aggressive (2). More aggressive application of presolve takes
		// more time, but can sometimes lead to a significantly tighter
		// model.

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

				String name = "";
				if (simpleBid.getBidType() == BidType.ASK) {
					name = "demand";
				} else {
					// Avoid null pointer for plant, e.g., for renewable bids
					if (simpleBid.getPlant() != null) {
						name = simpleBid.getPlant().getName();
					}
				}

				String variableName = "accept_" + marketArea + "_Trader_"
						+ simpleBid.getTraderType() + "_name_" + name + "_" + simpleBid.getComment()
						+ "_price_" + simpleBid.getPrice() + "_volume_" + simpleBid.getVolume();
				// Gurobi only alows Names with a maximal length of 255
				// characters
				if (variableName.length() > 254) {
					variableName = variableName.substring(0, 254);
				}
				accept[marketArea.getIdMarketCoupling() - 1][bid] = model.addVar(0, 1, 0.0,
						GRB.CONTINUOUS, variableName);
			}
		}

		// Flow variables
		// Set dimension of array
		final GRBVar[][] flowMatrix = new GRBVar[marketAreas.size()][marketAreas.size()];
		for (final MarketArea fromMarketArea : marketAreas) {
			for (final MarketArea toMarketArea : marketAreas) {
				final int idMarketCouplingFromMarketArea = fromMarketArea.getIdMarketCoupling() - 1;
				final int idMarketCouplingToMarketArea = toMarketArea.getIdMarketCoupling() - 1;

				// Set name of variable
				final String name = "flow_from_" + fromMarketArea + "_to_" + toMarketArea;

				if (fromMarketArea.equals(toMarketArea)) {
					flowMatrix[idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea] = model
							.addVar(0, 0, 0.0, GRB.CONTINUOUS, name);
				} else {
					// Set upper bound equal to the available capacity between
					// the two market areas
					double upperBound = 0;

					upperBound = capacitiesData.getInterconnectionCapacityHour(fromMarketArea,
							toMarketArea, Date.getYear(),
							Date.getHourOfYearFromHourOfDay(hourOfDay));

					// Add variable
					flowMatrix[idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea] = model
							.addVar(0, upperBound, 0.0, GRB.CONTINUOUS, name);
					// Add penalty for exchange flows
					objective.addTerm(penaltyInterconnectorFlows,
							flowMatrix[idMarketCouplingFromMarketArea][idMarketCouplingToMarketArea]);

				}
			}
		}

		// Update model to integrate new variables
		model.update();

		/* Boundaries for acceptance variables */
		// Demand bids have to be accepted
		// On the one hand this is necessary since demand bids have no price
		// limit (i.e. price = 0). Consequently, these bids do not appear in the
		// objective function. In order to maximize the objective function all
		// supply bids (which have a negative sign) will be declined and there
		// is no market clearing.
		// On the other hand setting accept = 1 for demand bids could result in
		// infeasible models when supply is insufficient. There would be no
		// market clearing either and error handling is necessary.

		// Accept = 1
		for (final MarketArea marketArea : marketAreas) {
			for (int bid = 0; bid < simpleBids.get(marketArea).size(); bid++) {

				final Bid simpleBid = simpleBids.get(marketArea).get(bid);
				if (simpleBid.getBidType() == BidType.ASK) {
					// Skip pumped storage trader because this bids should be
					// price dependent
					if (simpleBid.getTraderType() == TraderType.PUMPED_STORAGE) {
						continue;
					}
					// Skip hydrogen trader because this bids should be
					// price dependent
					if (simpleBid.getTraderType() == TraderType.POWER_TO_HYDROGEN) {
						continue;
					}
					expr1.addTerm(1, accept[marketArea.getIdMarketCoupling() - 1][bid]);
					model.addConstr(expr1, GRB.EQUAL, 1, "accept_demand_" + marketArea + "_BidID_"
							+ simpleBid.getIdentifier() + "_" + simpleBid.getComment());
					expr1.clear();
				}
			}
		}

		// Update model
		model.update();

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

		// Update model
		model.update();

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

				objective.addTerm((bidVolume * bidPoint.getPrice()),
						accept[marketArea.getIdMarketCoupling() - 1][bid]);
			}
		}

		// Update model
		model.update();

		model.setObjective(objective, GRB.MAXIMIZE);

		// Update model
		model.update();

		// Write LP in file (each year the files are overwritten)
		if (Date.isFirstDayOfYear() && (hourOfDay == 0)) {
			model.write(marketCouplingOperator.getMarketCouplingFolderPath() + File.separator
					+ "MarketCouplingGurobi_" + Date.getYear() + "_" + Date.getDayOfYear() + "_"
					+ hourOfDay + ".lp");
		}

		/* Solve model */
		model.optimize();
		if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
			logger.error("Market clearing infeasible! Please check the bid lists for Bugs.");
			model.write(marketCouplingOperator.getMarketCouplingFolderPath() + File.separator
					+ "MarketCouplingGurobi_" + Date.getYear() + "_" + Date.getDayOfYear() + "_"
					+ hourOfDay + "_Status_" + model.get(GRB.IntAttr.Status) + ".lp");
			// compute IIS to find restrictive constraints
			model.computeIIS();
			for (int constrIndex = 0; constrIndex < model.getConstrs().length; constrIndex++) {
				if (model.getConstr(constrIndex).get(GRB.IntAttr.IISConstr) > 0) {
					logger.error("IIS-constraint (y" + Date.getYear() + "_d" + Date.getDayOfYear()
							+ "_h" + hourOfDay + "): "
							+ model.getConstr(constrIndex).get(GRB.StringAttr.ConstrName));
				}
			}
		}

		/* Results */
		// Get flows and calculate net flows by market area
		marketCouplingOperator.setFlows(model.get(GRB.DoubleAttr.X, flowMatrix), hourOfDay);

		// Get shadow prices (equal to market area prices)
		final double[] prices = model.get(GRB.DoubleAttr.Pi, marketAreaBalance);
		marketCouplingOperator.setMarketClearingPricesDaily(prices, hourOfDay);

		// Set accepted volume for each bid
		marketCouplingOperator.setAcceptedVolume(hourOfDay, model, accept);

		// Log optimization model permanently if maximum prices occur in any
		// market area
		for (final MarketArea marketArea : marketAreas) {

			// Check whether price is one of the extreme prices; if so, set
			// price determined by solver to the constant value (max or min
			// price allowed) in order to reduce rounding errors
			final float marketClearingPrice = (float) prices[marketArea.getIdMarketCoupling() - 1];
			final float maxPriceAllowed = marketArea.getDayAheadMarketOperator()
					.getMaxPriceAllowed();
			if (Math.round(marketClearingPrice) == maxPriceAllowed) {
				model.write(marketCouplingOperator.getMarketCouplingFolderPath() + File.separator
						+ "MarketCouplingGurobi_" + Date.getYear() + "_"
						+ Date.getHourOfYearFromHourOfDay(hourOfDay) + ".lp");
				break;
			}
		}

		// Dispose everything
		model.dispose();
	}
}
