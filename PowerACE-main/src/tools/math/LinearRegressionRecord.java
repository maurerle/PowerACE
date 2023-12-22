package tools.math;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.scheduling.Date;

/**
 * Represents one record for the regression used for the price forecast
 * <p>
 * 
 * Includes the dependent variable (mcp) and the different independent variables
 * (e.g. dummies for time of day, forecast of demand).
 * 
 * @author PR
 * @since 08/2013
 * 
 */
public final class LinearRegressionRecord {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(LinearRegressionRecord.class.getName());

	/** Static factory method to create new instance of object */
	public static LinearRegressionRecord createLinearRegressionRecord(int hourOfSim,
			double dependentVariable, double renewablesForecast, double demandForecast,
			double pumpStorageForecast) {
		return new LinearRegressionRecord(hourOfSim, dependentVariable, renewablesForecast,
				demandForecast, pumpStorageForecast);
	}

	/**
	 * Checks whether the specified hour is on a weekend or not.
	 * 
	 * @param hourOfSim
	 *            [1,8760*numberOfYears]
	 * @return true if hour on weekend, false if not
	 */
	private static boolean hourOfSimOnWeekend(int hourOfSim) {
		final int LAST_HISTORICAL_YEAR = 2012;
		final int BASE_YEAR = 2008;
		final int year = (Date.getYear() <= LAST_HISTORICAL_YEAR) ? Date.getYear() : BASE_YEAR;

		final Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.YEAR, year);
		calendar.set(Calendar.DAY_OF_YEAR, (((hourOfSim - 1) / 24) + 1));

		// Check whether day is a saturday (7) or sunday (1)
		return ((calendar.get(Calendar.DAY_OF_WEEK) == 1)
				|| (calendar.get(Calendar.DAY_OF_WEEK) == 7));
	}

	/** Map of continuous dependent variable */
	private final Map<RegressionVariableName, Double> dependentVariable = new HashMap<RegressionVariableName, Double>();

	/** Map of binary independent variables */
	private final Map<RegressionVariableName, List<Integer>> independentBinVariables = new HashMap<RegressionVariableName, List<Integer>>();

	/** Map of continuous independent variables */
	private final Map<RegressionVariableName, Double> independentContVariables = new HashMap<RegressionVariableName, Double>();

	/** Public constructor */
	public LinearRegressionRecord() {
	}

	/** Private constructor */
	private LinearRegressionRecord(int hourOfSim, double dependentVariable,
			double renewablesForecast, double demandForecast, double pumpStorageForecast) {
		setDummyHourOfDay(hourOfSim);
		setDummyWeekend(hourOfSim);
		setDependentVariables(dependentVariable);
		setIndependentVariables(renewablesForecast, demandForecast, pumpStorageForecast);
	}

	/** Add new continuous variable */
	public void addNewContVariable(RegressionVariableName regressionVariableName, Float value) {
		try {
			if (regressionVariableName.getRegressionVariableType()
					.equals(RegressionVariableType.CONTINUOUS)) {
				independentContVariables.put(regressionVariableName, Double.valueOf(value));
			}
		} catch (final Exception e) {
			logger.error("Exception", e);
		}

	}

	/** Add new hourly dummy variables */
	public void addNewHourlyDummyVariables(int hourOfDay) {
		setDummyHourOfDay(hourOfDay);
		setDummyWeekend(hourOfDay);
	}

	public Map<RegressionVariableName, Double> getDependentVariable() {
		return dependentVariable;
	}

	public Map<RegressionVariableName, List<Integer>> getIndependentBinVariables() {
		return independentBinVariables;
	}

	public Map<RegressionVariableName, Double> getIndependentContVariables() {
		return independentContVariables;
	}

	public double getMeanMCP() {
		return independentContVariables.get(RegressionVariableName.MEAN_MCP);
	}

	public void setDependentVariables(double dependentVariable) {
		this.dependentVariable.put(RegressionVariableName.MCP, dependentVariable);
	}

	public void setDummyHourOfDay(int hourOfSim) {
		final List<Integer> dummyHourOfDay = new ArrayList<Integer>(
				Collections.nCopies(Date.HOURS_PER_DAY, 0));
		final int hourOfDay = hourOfSim % 24;
		dummyHourOfDay.set(hourOfDay, 1);
		independentBinVariables.put(RegressionVariableName.DUMMY_HOUR, dummyHourOfDay);
	}

	public void setDummyWeekend(int hourOfSim) {
		final List<Integer> dummyWeekend = new ArrayList<Integer>(Collections.nCopies(1, 0));
		dummyWeekend.set(0, (hourOfSimOnWeekend(hourOfSim)) ? 1 : 0);
		independentBinVariables.put(RegressionVariableName.DUMMY_WEEKEND, dummyWeekend);
	}

	public void setMCP(int day, double[] MCP) {
		for (int n = 0; n < 24; n++) {

		}
	}

	/**
	 * Sets the mean day-ahead MCP of all preceding hours and of the n
	 * precending hours.
	 */
	public void setMCPLags(List<Double> priceLags, int hourOfSim) {

		if (priceLags.isEmpty()) {
			// Set new sum of all prices
			independentContVariables.put(RegressionVariableName.SUM_MCP,
					dependentVariable.get(RegressionVariableName.MCP));
		} else {
			final List<Double> priceLagsTemp = priceLags;

			// Set new sum of all prices
			independentContVariables.put(RegressionVariableName.SUM_MCP,
					priceLagsTemp.get(0) + dependentVariable.get(RegressionVariableName.MCP));

			// Set mean day-ahead MCP of all preceding hours
			final int numberOfPreviousPrices = hourOfSim / Date.HOURS_PER_DAY;
			independentContVariables.put(RegressionVariableName.MEAN_MCP,
					(priceLagsTemp.get(0) / numberOfPreviousPrices));
			priceLagsTemp.remove(0);

			// Set lagged day-ahead MCPs
			double meanMCPlagged = 0;
			int numberOfPriceLags = 0;
			for (final Double priceLag : priceLagsTemp) {
				meanMCPlagged += priceLag;
				numberOfPriceLags++;
			}
			if (numberOfPriceLags != 0) {
				meanMCPlagged /= numberOfPriceLags;
			}
			independentContVariables.put(RegressionVariableName.MEAN_MCP_LAGGED, meanMCPlagged);
		}
	}

	@Override
	public String toString() {
		final StringBuffer stringOfLinearRegressionRecord = new StringBuffer();

		for (final Map.Entry<RegressionVariableName, Double> entry : dependentVariable.entrySet()) {
			stringOfLinearRegressionRecord.append(entry.getKey());
			stringOfLinearRegressionRecord.append(": ");
			stringOfLinearRegressionRecord.append(entry.getValue());
			stringOfLinearRegressionRecord.append("; ");
		}
		for (final Map.Entry<RegressionVariableName, Double> entry : independentContVariables
				.entrySet()) {
			stringOfLinearRegressionRecord.append(entry.getKey());
			stringOfLinearRegressionRecord.append(": ");
			stringOfLinearRegressionRecord.append(entry.getValue());
			stringOfLinearRegressionRecord.append("; ");
		}
		for (final Map.Entry<RegressionVariableName, List<Integer>> entry : independentBinVariables
				.entrySet()) {
			stringOfLinearRegressionRecord.append(entry.getKey());
			stringOfLinearRegressionRecord.append(": ");
			stringOfLinearRegressionRecord.append(entry.getValue());
			stringOfLinearRegressionRecord.append("; ");
		}

		stringOfLinearRegressionRecord.deleteCharAt(stringOfLinearRegressionRecord.length() - 2);

		return String.valueOf(stringOfLinearRegressionRecord);
	}

	private void setIndependentVariables(double renewablesForecast, double demandForecast,
			double pumpStorageForecast) {
		independentContVariables.put(RegressionVariableName.DEMAND, demandForecast);
		independentContVariables.put(RegressionVariableName.RENEWABLES, renewablesForecast);
		independentContVariables.put(RegressionVariableName.PUMPSTORAGE, pumpStorageForecast);
	}
}
