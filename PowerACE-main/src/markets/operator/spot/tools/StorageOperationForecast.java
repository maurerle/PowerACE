package markets.operator.spot.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.operator.spot.MarketCouplingOperator;
import simulations.MarketArea;
import simulations.scheduling.Date;
import tools.math.Statistics;

/**
 * Estimation of hourly storage operation using multiple linear regression
 * 
 * @author original version by PR (PriceEffectMarketCoupling), adopted with
 *         changes by CF
 */
public class StorageOperationForecast {

	/** Simple wrapper for independent variable in regresion model */
	private class IndependentVariable {

		/** Current regression coefficient (updated after each estimation) */
		private double coefficient;
		/**
		 * Indicates whether variable is excluded for current estimation
		 * (because all elements of input data are equal)
		 */
		private boolean excluded;
		/** (Hourly) Forecast data for next prediction */
		private List<Float> forecastData;
		/** Index in regression model */
		private int index;
		/** (Hourly) Input data for next model estimation */
		private final List<Float> inputDataAll = new ArrayList<>();
		/** (Hourly) Input data for model estimation on current day */
		private List<Float> inputDataUpdateDaily;
		/** Reference to market area (e.g. relevant for scarcity indicator) */
		private MarketArea marketArea;
		/** Type of independent variable */
		private IndependentVariableTypes type;

		/** Constructor */
		protected IndependentVariable(int index, IndependentVariableTypes type) {
			this.index = index;
			this.type = type;
		}

		/** Constructor */
		protected IndependentVariable(int index, IndependentVariableTypes type,
				MarketArea marketArea) {
			this(index, type);
			this.marketArea = marketArea;
		}

		@Override
		public String toString() {
			return type + (marketArea == null ? "" : ";" + marketArea.getName());
		}
	}

	private enum IndependentVariableTypes {
		DAILY_AVG_RESIDUAL_DEMAND,
		RESIDUAL_DEMAND,
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(StorageOperationForecast.class.getName());

	/** Interconnected market area */
	private final Set<MarketArea> allMarketAreas;
	/** Current estimate of constant in regression model */
	private double constant;

	/** Independent variables */
	private final List<IndependentVariable> independentVariables = new ArrayList<>();

	/** Current market area */
	private final MarketArea marketArea;
	private final MarketCouplingOperator marketCouplingOperator;
	/** Recursive least squares model */
	private final RecursiveLeastSquaresModel recursiveLeastSquaresModel;
	private List<Float> storageOperationForecast = new ArrayList<>();
	/** Maximum value of simulated storage operation */
	private float storageOperationMaximum = Float.MIN_VALUE;
	/** Minimum value of simulated storage operation */
	private float storageOperationMinimum = Float.MAX_VALUE;
	/** Indicates whether full regression model is estimated */
	private final boolean testRegressionFull;

	/**
	 * Residual load with Key, value pair. One map in order to safe computation
	 * time
	 */
	private final Map<Integer, Float> residualLoad = new TreeMap<>();
	/** Public constructor */
	public StorageOperationForecast(MarketCouplingOperator marketCouplingOperator,
			MarketArea marketArea) {
		this.marketCouplingOperator = marketCouplingOperator;
		this.marketArea = marketArea;
		testRegressionFull = false;

		/* Initialize fields */
		allMarketAreas = this.marketCouplingOperator.getMarketAreas();
		calculateResdiualLoad();
		/* Set up regression model */
		setUpModel();

		/* Initialize recursive least squares model */
		// Forgetting factor of 1 behaves much more stable than e.g. 0.95d
		final double forgettingFactor = 1d;
		recursiveLeastSquaresModel = new RecursiveLeastSquaresModel(independentVariables.size(),
				forgettingFactor);
		recursiveLeastSquaresModel.initializeModel();
	}

	/** Estimate regression model with most recent data */
	public void estimateModel() {

		try {
			Thread.currentThread()
					.setName("Calculat storage operation forecast " + marketArea.getInitials());
			int year = Date.getYear();
			int dayOfYear = Date.getDayOfYear() - 1;
			if (dayOfYear == 0) {
				year--;
				dayOfYear = Date.DAYS_PER_YEAR;
			}

			final List<Float> storageOperationForecastUpdateDaily = new ArrayList<>();

			for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
				final float hourlyStorageOperation = marketArea.getElectricityProduction()
						.getElectricityPumpedStorage(year,
								Date.getFirstHourOfDay(dayOfYear) + hour);
				storageOperationForecastUpdateDaily.add(hourlyStorageOperation);
			}

			storageOperationForecast.addAll(storageOperationForecastUpdateDaily);

			// Independent variables (-> regressors)
			final List<List<Float>> inputDataAllVariables = collectDataIndependentVariables(year,
					dayOfYear, dayOfYear);
			for (final IndependentVariable independentVariable : independentVariables) {
				independentVariable.inputDataUpdateDaily = inputDataAllVariables
						.get(independentVariable.index);
				if (testRegressionFull) {
					independentVariable.inputDataAll
							.addAll(inputDataAllVariables.get(independentVariable.index));
				}
			}

			/* Perform iterations in recursive least squares filter */
			for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
				try {

					// Get hourly sample update
					final double[] sampleUpdate = new double[independentVariables.size() + 1];
					// Set data value of constant to 1d
					sampleUpdate[0] = 1d;
					int indexVar = 0;
					for (final IndependentVariable independentVariable : independentVariables) {
						sampleUpdate[indexVar + 1] = independentVariable.inputDataUpdateDaily
								.get(hourOfDay);
						indexVar++;
					}
					final RealMatrix sampleUpdateMatrix = MatrixUtils
							.createColumnRealMatrix(sampleUpdate);

					// Perform iteration
					recursiveLeastSquaresModel.performIteration(
							storageOperationForecastUpdateDaily.get(hourOfDay), sampleUpdateMatrix,
							hourOfDay);

					// Set updated coefficients of independent variables
					final double[] coefficientsRecursive = recursiveLeastSquaresModel
							.getCoefficients();
					constant = coefficientsRecursive[0];
					int index = 1;
					for (final IndependentVariable independentVariable : independentVariables) {
						independentVariable.coefficient = coefficientsRecursive[index];
						index++;
					}

					// Cache maximum and minimum values
					storageOperationMaximum = Math.max(
							storageOperationForecastUpdateDaily.get(hourOfDay),
							storageOperationMaximum);
					storageOperationMinimum = Math.min(
							storageOperationForecastUpdateDaily.get(hourOfDay),
							storageOperationMinimum);
				} catch (final Exception e) {
					logger.error(e.getMessage(), e);
				}
			}

			if (testRegressionFull) {
				/* Set up regression model */
				final OLSMultipleLinearRegression regressionModel = new OLSMultipleLinearRegression();

				/* Set input data for model */
				// Check whether any input data is constant for time horizon (in
				// such a case the regression model would not be solved)
				int numberOfIndependentVars = independentVariables.size();
				for (final IndependentVariable independentVariable : independentVariables) {
					if (Statistics.areAllElementsEqual(independentVariable.inputDataUpdateDaily)) {
						numberOfIndependentVars--;
						independentVariable.excluded = true;
					} else {
						independentVariable.excluded = false;
					}
				}

				// Reformat input data
				final int sampleSize = storageOperationForecast.size();
				final double[] dependentVar = new double[sampleSize];
				final double[][] independentVars = new double[sampleSize][numberOfIndependentVars];
				for (int indexSample = 0; indexSample < sampleSize; indexSample++) {
					dependentVar[indexSample] = storageOperationForecast.get(indexSample);
					int indexIndependentVariables = 0;
					for (final IndependentVariable independentVariable : independentVariables) {
						if (!independentVariable.excluded) {
							independentVars[indexSample][indexIndependentVariables++] = independentVariable.inputDataAll
									.get(indexSample);
						}
					}
				}

				regressionModel.newSampleData(dependentVar, independentVars);
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Estimate hourly storage operation
	 */

	public List<Float> getStorageOperationForecast(int forecastLengthInHours) {
		return getStorageOperationForecast(forecastLengthInHours, Date.getYear());
	}

	/**
	 * Estimate hourly storage operation
	 */
	public List<Float> getStorageOperationForecast(int forecastLengthInHours, int year) {
		final List<Float> storageOperationForecast = new ArrayList<>();

		/* Define forecast period */
		int firstDayOfYear;
		int lastDayOfYear;
		if (forecastLengthInHours == Date.HOURS_PER_DAY) {
			firstDayOfYear = Date.getDayOfYear();
			lastDayOfYear = Date.getDayOfYear();
		} else if (forecastLengthInHours == Date.HOURS_PER_YEAR) {
			firstDayOfYear = 1;
			lastDayOfYear = Date.DAYS_PER_YEAR;
		} else {

			firstDayOfYear = Date.getDayOfYear();
			lastDayOfYear = Date.getDayOfYear()
					+ (int) Math.floor((forecastLengthInHours - 1) / Date.HOURS_PER_DAY);
		}

		/* Get forecasts for all independent variable */
		final List<List<Float>> inputDataAllVariables = collectDataIndependentVariables(year,
				firstDayOfYear, lastDayOfYear);
		for (final IndependentVariable independentVariable : independentVariables) {
			try {
				independentVariable.forecastData = inputDataAllVariables
						.get(independentVariable.index);
			} catch (final Exception e) {
				logger.error(e.getMessage(), e);
			}
		}

		/* Determine expected storage operation for each hour of day */
		for (int hourOfForecastPeriod = 0; hourOfForecastPeriod < forecastLengthInHours; hourOfForecastPeriod++) {
			double predictionRecursiveCurrentHour = getStorageOperationForecastHourly(
					hourOfForecastPeriod);

			int hoursOfForecastPeriodAsHourOfYear = Date.getFirstHourOfToday()
					+ hourOfForecastPeriod;
			if (hoursOfForecastPeriodAsHourOfYear > Date.HOURS_PER_YEAR) {
				hoursOfForecastPeriodAsHourOfYear -= Date.HOURS_PER_YEAR;
			}

			// Limit the forecast depending on maximum/minimum simulated values
			if (predictionRecursiveCurrentHour > storageOperationMaximum) {
				predictionRecursiveCurrentHour = storageOperationMaximum;
			} else if (predictionRecursiveCurrentHour < storageOperationMinimum) {
				predictionRecursiveCurrentHour = storageOperationMinimum;
			}
			storageOperationForecast.add((float) predictionRecursiveCurrentHour);

		}

		return storageOperationForecast;
	}

	private void calculateResdiualLoad() {
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
				final float residualLoadHourly = marketArea.getManagerRenewables()
						.getRemainingLoad(year).get(hourOfYear)
						+ marketArea.getExchange().getHourlyFlowForecast(year, hourOfYear);
				residualLoad.put(key, residualLoadHourly);
			}
		}
	}

	private List<Float> collectDataIndependentVariables(IndependentVariableTypes type, int year,
			int dayOfYearStart, int dayOfYearEnd) {
		List<Float> values = new ArrayList<>();
		List<Float> valuesCurrentVariable;

		switch (type) {
			case RESIDUAL_DEMAND:
				valuesCurrentVariable = new ArrayList<>();
				for (int dayOfYear = dayOfYearStart; dayOfYear <= dayOfYearEnd; dayOfYear++) {
					// If last day of the year is exceeded within forecast
					// period, corresponding values at the beginning of the
					// current year will be used
					if (dayOfYear > Date.DAYS_PER_YEAR) {
						valuesCurrentVariable.addAll(getResidualLoadWithoutMarketCouplingDaily(
								marketArea, year, dayOfYear - Date.DAYS_PER_YEAR));
					} else {
						valuesCurrentVariable.addAll(getResidualLoadWithoutMarketCouplingDaily(
								marketArea, year, dayOfYear));
					}
				}
				values = valuesCurrentVariable;
				break;

			case DAILY_AVG_RESIDUAL_DEMAND:
				valuesCurrentVariable = collectDataIndependentVariables(
						IndependentVariableTypes.RESIDUAL_DEMAND, year, dayOfYearStart,
						dayOfYearEnd);
				for (int dayOfYear = dayOfYearStart; dayOfYear <= dayOfYearEnd; dayOfYear++) {
					float avgLoadBeforeStorageOperation = 0f;
					int length = 0;
					for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
						if ((((dayOfYear - dayOfYearStart) * Date.HOURS_PER_DAY)
								+ hourOfDay) < valuesCurrentVariable.size()) {
							avgLoadBeforeStorageOperation += valuesCurrentVariable
									.get(((dayOfYear - dayOfYearStart) * Date.HOURS_PER_DAY)
											+ hourOfDay);
							length++;
						} else {
							break;
						}
					}
					avgLoadBeforeStorageOperation /= length;

					for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
						if ((((dayOfYear - dayOfYearStart) * Date.HOURS_PER_DAY)
								+ hourOfDay) < valuesCurrentVariable.size()) {
							valuesCurrentVariable.set(
									((dayOfYear - dayOfYearStart) * Date.HOURS_PER_DAY) + hourOfDay,
									avgLoadBeforeStorageOperation);
						} else {
							break;
						}
					}
				}
				values = valuesCurrentVariable;
				break;
			default:
				logger.warn("Type of independet variable undefined!");
				break;
		}
		return values;
	}

	/**
	 * Collect data for all independent variables (can be used for both input
	 * data of regression model and forecast data)
	 * <p>
	 * The order of the independent variables is equal to the one initially
	 * defined (see {@link#independentVariables}).
	 * 
	 * @param year
	 * @param dayOfYearStart
	 *            first day (including)
	 * @param dayOfYearEnd
	 *            last day (including)
	 */
	private List<List<Float>> collectDataIndependentVariables(int year, int dayOfYearStart,
			int dayOfYearEnd) {

		/* Initialize list */
		final List<List<Float>> values = new ArrayList<>();

		/* Get data */
		for (final IndependentVariable independentVariable : independentVariables) {
			try {
				values.add(collectDataIndependentVariables(independentVariable.type, year,
						dayOfYearStart, dayOfYearEnd));
			} catch (final Exception e) {
				logger.error(e.getMessage(), e);
			}
		}

		return values;
	}

	private List<Float> getResidualLoadWithoutMarketCouplingDaily(MarketArea marketArea, int year,
			int dayOfYear) {
		final List<Float> residualLoadDaily = new ArrayList<>();
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(dayOfYear, 0);
		final int keyStart = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
		for (int key = keyStart; key < (keyStart + Date.HOURS_PER_DAY); key++) {
			final Float residualLoadHourly = residualLoad.get(key);
			residualLoadDaily.add(residualLoadHourly);
		}

		return residualLoadDaily;
	}

	/** Get expected hourly exchange from market coupling */
	private double getStorageOperationForecastHourly(int hourOfForecastPeriod) {
		double prediction = constant;
		for (final IndependentVariable independentVariable : independentVariables) {
			prediction += independentVariable.forecastData.get(hourOfForecastPeriod)
					* independentVariable.coefficient;
		}
		return prediction;
	}

	/** Set up general regression model */
	private void setUpModel() {

		int index = 0;

		for (final MarketArea currentMarketArea : allMarketAreas) {
			// Residual demand
			independentVariables.add(new IndependentVariable(index++,
					IndependentVariableTypes.RESIDUAL_DEMAND, currentMarketArea));
			// Daily avg of residual demand
			if (currentMarketArea.getMarketAreaType() == marketArea.getMarketAreaType()) {
				independentVariables.add(new IndependentVariable(index++,
						IndependentVariableTypes.DAILY_AVG_RESIDUAL_DEMAND, currentMarketArea));
			}
		}
	}
}