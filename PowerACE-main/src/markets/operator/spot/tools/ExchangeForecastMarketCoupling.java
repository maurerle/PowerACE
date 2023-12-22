package markets.operator.spot.tools;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.operator.spot.MarketCouplingOperator;
import simulations.MarketArea;
import simulations.scheduling.Date;
import supply.Generator;
import supply.powerplant.Plant;
import tools.math.Statistics;

/**
 * Estimation of hourly electricity exchange from market coupling using multiple
 * linear regression
 * 
 * @author original version by PR (PriceEffectMarketCoupling), adopted with
 *         changes by CF
 */
public class ExchangeForecastMarketCoupling {

	/** Simple wrapper for independent variable in regresion model */
	private class IndependentVariable {

		/** Current regression coefficient (updated after each estimation) */
		private double coefficient;
		/** Index for dummy variable, e.g. hourOfDay or monthOfYear */
		private Integer dummyIndex;
		/**
		 * Indicates whether variable is excluded for current estimation
		 * (because all elements of input data are equal)
		 */
		private boolean excluded;
		private Float exponent;
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
		AVAIL_EXPORT_CAPACITY,
		AVAIL_GENERATION_CAPACITY,
		AVAIL_IMPORT_CAPACITY,
		DEMAND,
		DUMMY_HOUR_OF_DAY,
		DUMMY_MONTH_OF_YEAR,
		DUMMY_SCARCITY,
		PV_GENERATION,
		RESIDUAL_DEMAND,
		RESIDUAL_DEMAND_DIFFERENCES,
		RESIDUAL_DEMAND_EXPONENTIATED,
		SCARCITY_INTERCONNECTED_MARKET_AREA,
		WIND_GENERATION;
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(ExchangeForecastMarketCoupling.class.getName());

	/** Interconnected market area */
	private final List<MarketArea> allMarketAreas;
	/** Current coefficients of full regression model */
	private double[] coefficientsRegressionFull;
	/** Current estimate of constant in regression model */
	private double constant;
	private List<Double> exchangeForecast = new ArrayList<>();
	/** Maximum value of simulated exchange */
	private double exchangeMaximum = Double.MIN_VALUE;
	/** Minimum value of simulated exchange */
	private double exchangeMinimum = Double.MAX_VALUE;
	/** Independent variables */
	private final List<IndependentVariable> independentVariables = new ArrayList<>();

	/** Current market area */
	private final MarketArea marketAreaFrom;
	private final MarketArea marketAreaTo;
	private final MarketCouplingOperator marketCouplingOperator;

	/** Recursive least squares model */
	private final RecursiveLeastSquaresModel recursiveLeastSquaresModel;
	/** Day of year when first data is available */
	private final int startDayOfYear;
	/** Indicates whether full regression model is estimated */
	private final boolean testRegressionFull;

	/** Public constructor */
	public ExchangeForecastMarketCoupling(MarketCouplingOperator marketCouplingOperator,
			MarketArea marketAreaFrom, MarketArea marketAreaTo, int startDayOfYear) {
		this.marketCouplingOperator = marketCouplingOperator;
		this.marketAreaFrom = marketAreaFrom;
		this.marketAreaTo = marketAreaTo;
		this.startDayOfYear = startDayOfYear;
		testRegressionFull = false;

		/* Initialize fields */
		allMarketAreas = marketCouplingOperator.getMarketAreasList();

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
			/* Set period */
			int year = Date.getYear();
			int dayOfYear = Date.getDayOfYear() - 1;
			if (dayOfYear == 0) {
				year--;
				dayOfYear = Date.DAYS_PER_YEAR;
			}

			final List<Double> exchangeForecastUpdateDaily = new ArrayList<>();

			for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
				final int hourOfYear = Date.getFirstHourOfDay(dayOfYear) + hour;
				final double hourlyInterconnectorBalance = (marketCouplingOperator
						.getExchangeFlows()
						.getHourlyFlow(marketAreaFrom, marketAreaTo, year, hourOfYear)
						- marketCouplingOperator.getExchangeFlows().getHourlyFlow(marketAreaTo,
								marketAreaFrom, year, hourOfYear));
				exchangeForecastUpdateDaily.add(hourlyInterconnectorBalance);
			}

			exchangeForecast.addAll(exchangeForecastUpdateDaily);

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
							exchangeForecastUpdateDaily.get(hourOfDay), sampleUpdateMatrix,
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
					exchangeMaximum = Math.max(exchangeForecastUpdateDaily.get(hourOfDay),
							exchangeMaximum);
					exchangeMinimum = Math.min(exchangeForecastUpdateDaily.get(hourOfDay),
							exchangeMinimum);
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
				final int sampleSize = exchangeForecast.size();
				final double[] dependentVar = new double[sampleSize];
				final double[][] independentVars = new double[sampleSize][numberOfIndependentVars];
				for (int indexSample = 0; indexSample < sampleSize; indexSample++) {
					dependentVar[indexSample] = exchangeForecast.get(indexSample);
					int indexIndependentVariables = 0;
					for (final IndependentVariable independentVariable : independentVariables) {
						if (!independentVariable.excluded) {
							independentVars[indexSample][indexIndependentVariables++] = independentVariable.inputDataAll
									.get(indexSample);
						}
					}
				}

				regressionModel.newSampleData(dependentVar, independentVars);

				/* Get model coefficients */
				coefficientsRegressionFull = regressionModel.estimateRegressionParameters();
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Estimate hourly exchange over interconnector
	 */
	public List<Float> getExchangeForecast(int forecastLengthInHours) {
		return getExchangeForecast(forecastLengthInHours, Date.getYear());
	}

	/**
	 * Estimate hourly exchange over interconnector
	 */
	public List<Float> getExchangeForecast(int forecastLengthInHours, int year) {
		final List<Float> exchangeForecast = new ArrayList<>();

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
		final boolean isDayAheadForecast = (forecastLengthInHours == Date.HOURS_PER_DAY);

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

		/* Determine expected exchange for each hour of day */
		for (int hourOfForecastPeriod = 0; hourOfForecastPeriod < forecastLengthInHours; hourOfForecastPeriod++) {
			double predictionRecursiveCurrentHour = getExchangeForecastHourly(hourOfForecastPeriod);

			if (isDayAheadForecast && testRegressionFull) {
				double predictionRegressionCurrentHour = coefficientsRegressionFull[0];
				for (final IndependentVariable independentVariable : independentVariables) {
					predictionRegressionCurrentHour += independentVariable.forecastData
							.get(hourOfForecastPeriod)
							* coefficientsRegressionFull[independentVariable.index + 1];
				}

			}

			int hoursOfForecastPeriodAsHourOfYear = Date.getFirstHourOfToday()
					+ hourOfForecastPeriod;
			if (hoursOfForecastPeriodAsHourOfYear >= Date.HOURS_PER_YEAR) {
				hoursOfForecastPeriodAsHourOfYear -= Date.HOURS_PER_YEAR;
			}

			if ((predictionRecursiveCurrentHour > 0)
					&& (predictionRecursiveCurrentHour > (marketCouplingOperator.getCapacitiesData()
							.getInterconnectionCapacityHour(marketAreaFrom, marketAreaTo, year,
									hoursOfForecastPeriodAsHourOfYear)))) {
				predictionRecursiveCurrentHour = marketCouplingOperator.getCapacitiesData()
						.getInterconnectionCapacityHour(marketAreaFrom, marketAreaTo, year,
								hoursOfForecastPeriodAsHourOfYear);
			} else if ((predictionRecursiveCurrentHour < 0) && (exchangeMinimum != 0)
					&& (predictionRecursiveCurrentHour < (-marketCouplingOperator
							.getCapacitiesData().getInterconnectionCapacityHour(marketAreaTo,
									marketAreaFrom, year, hoursOfForecastPeriodAsHourOfYear)))) {
				predictionRecursiveCurrentHour = -marketCouplingOperator.getCapacitiesData()
						.getInterconnectionCapacityHour(marketAreaTo, marketAreaFrom, year,
								hoursOfForecastPeriodAsHourOfYear);
			}

			exchangeForecast.add((float) predictionRecursiveCurrentHour);

		}

		return exchangeForecast;
	}

	private List<Float> collectDataIndependentVariables(IndependentVariableTypes type,
			MarketArea interconnectedMarketArea, Integer dummyIndex, Float exponent, int year,
			int dayOfYearStart, int dayOfYearEnd) {
		List<Float> values = new ArrayList<>();
		List<Float> valuesCurrentVariable;

		switch (type) {

			case DUMMY_HOUR_OF_DAY:
				valuesCurrentVariable = new ArrayList<>();
				for (int dayOfYear = dayOfYearStart; dayOfYear <= dayOfYearEnd; dayOfYear++) {
					for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
						if (hourOfDay == dummyIndex) {
							valuesCurrentVariable.add(1f);
						} else {
							valuesCurrentVariable.add(0f);
						}
					}
				}
				values = valuesCurrentVariable;
				break;

			case DUMMY_MONTH_OF_YEAR:
				valuesCurrentVariable = new ArrayList<>();
				for (int dayOfYear = dayOfYearStart; dayOfYear <= dayOfYearEnd; dayOfYear++) {
					int tempDayOfYear = dayOfYear;
					if (dayOfYear > Date.DAYS_PER_YEAR) {
						tempDayOfYear -= Date.DAYS_PER_YEAR;
					}
					if (Date.getMonth(tempDayOfYear) == dummyIndex) {
						for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
							valuesCurrentVariable.add(1f);
						}
					} else {
						for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
							valuesCurrentVariable.add(0f);
						}
					}
				}
				values = valuesCurrentVariable;
				break;

			case DUMMY_SCARCITY:
				valuesCurrentVariable = collectDataIndependentVariables(
						IndependentVariableTypes.SCARCITY_INTERCONNECTED_MARKET_AREA,
						interconnectedMarketArea, null, null, year, dayOfYearStart, dayOfYearEnd);
				for (int hour = 0; hour < valuesCurrentVariable.size(); hour++) {
					if (valuesCurrentVariable.get(hour) > 1f) {
						valuesCurrentVariable.set(hour, 0f);
					} else {
						valuesCurrentVariable.set(hour, 1f);
					}
				}
				values = valuesCurrentVariable;
				break;

			case RESIDUAL_DEMAND:
				valuesCurrentVariable = new ArrayList<>();
				for (int dayOfYear = dayOfYearStart; dayOfYear <= dayOfYearEnd; dayOfYear++) {

					if (dayOfYear > Date.DAYS_PER_YEAR) {
						valuesCurrentVariable.addAll(getResidualLoadWithoutMarketCouplingDaily(
								interconnectedMarketArea, year, dayOfYear - Date.DAYS_PER_YEAR));
					} else {
						valuesCurrentVariable.addAll(getResidualLoadWithoutMarketCouplingDaily(
								interconnectedMarketArea, year, dayOfYear));
					}
				}
				values = valuesCurrentVariable;
				break;

			case RESIDUAL_DEMAND_DIFFERENCES:
				valuesCurrentVariable = collectDataIndependentVariables(
						IndependentVariableTypes.RESIDUAL_DEMAND, interconnectedMarketArea, null,
						null, year, dayOfYearStart, dayOfYearEnd);
				// Calculation of differences, first hour is handled separately
				for (int hour = valuesCurrentVariable.size() - 1; hour > 0; hour--) {
					valuesCurrentVariable.set(hour,
							valuesCurrentVariable.get(hour) - valuesCurrentVariable.get(hour - 1));
				}

				final int yearYesterday;
				final int dayOfYearYesterday;

				if (dayOfYearStart == 1) {
					yearYesterday = year - 1;
					dayOfYearYesterday = Date.DAYS_PER_YEAR;
				} else {
					yearYesterday = year;
					dayOfYearYesterday = dayOfYearStart - 1;
				}

				valuesCurrentVariable.set(0,
						valuesCurrentVariable.get(0) - getResidualLoadWithoutMarketCouplingHourly(
								interconnectedMarketArea, yearYesterday, dayOfYearYesterday,
								Date.HOURS_PER_DAY - 1));

				values = valuesCurrentVariable;
				break;

			case RESIDUAL_DEMAND_EXPONENTIATED:
				valuesCurrentVariable = collectDataIndependentVariables(
						IndependentVariableTypes.RESIDUAL_DEMAND, interconnectedMarketArea, null,
						null, year, dayOfYearStart, dayOfYearEnd);
				for (int hour = 0; hour < valuesCurrentVariable.size(); hour++) {
					// If negative value and exponent<1, calculate absolute
					// value, exponentiate,correct sign to minus.
					if ((valuesCurrentVariable.get(hour) < 0f) && (exponent < 1f)) {
						valuesCurrentVariable.set(hour, (float) -Math
								.pow(Math.abs(valuesCurrentVariable.get(hour)), exponent));
					} else {
						valuesCurrentVariable.set(hour,
								(float) Math.pow(valuesCurrentVariable.get(hour), exponent));
					}
				}
				values = valuesCurrentVariable;
				break;

			case SCARCITY_INTERCONNECTED_MARKET_AREA:
				valuesCurrentVariable = new ArrayList<>();
				for (int dayOfYear = dayOfYearStart; dayOfYear <= dayOfYearEnd; dayOfYear++) {
					// If last day of the year is exceeded within forecast
					// period, corresponding values at the beginning of the
					// current year will be used
					if (dayOfYear > Date.DAYS_PER_YEAR) {
						valuesCurrentVariable.addAll(determineScarcityInterconnectedMarketArea(
								interconnectedMarketArea, year, dayOfYear - Date.DAYS_PER_YEAR));
					} else {
						valuesCurrentVariable.addAll(determineScarcityInterconnectedMarketArea(
								interconnectedMarketArea, year, dayOfYear));
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
				values.add(collectDataIndependentVariables(independentVariable.type,
						independentVariable.marketArea, independentVariable.dummyIndex,
						independentVariable.exponent, year, dayOfYearStart, dayOfYearEnd));
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		}

		return values;
	}

	/**
	 * Determine hourly scarcity in interconnected market area
	 * <p>
	 * Scarcity = available capacity [MW] / residual load [MW]
	 */
	private List<Float> determineScarcityInterconnectedMarketArea(
			MarketArea interconnectedMarketArea, int year, int dayOfYear) {
		final List<Float> scarcity = new ArrayList<>();

		final List<Float> residualLoad = getResidualLoadWithoutMarketCouplingDaily(
				interconnectedMarketArea, year, dayOfYear);

		final List<Float> availableCapacity = getAvailableCapacityDaily(interconnectedMarketArea,
				dayOfYear);

		// Set hourly scarcity
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			float scarcityCurrentHourOfDay;

			// No negative values for residual load should be considered here
			scarcityCurrentHourOfDay = availableCapacity.get(hourOfDay)
					/ Math.max(residualLoad.get(hourOfDay), 0.1f);
			// Limit value of scarcity
			final float maxValue = 2f;
			final float minValue = 0.5f;
			if (scarcityCurrentHourOfDay > maxValue) {
				scarcityCurrentHourOfDay = maxValue;
			}
			if (scarcityCurrentHourOfDay < minValue) {
				scarcityCurrentHourOfDay = minValue;
			}

			scarcity.add(scarcityCurrentHourOfDay);
		}
		return scarcity;
	}

	private List<Float> getAvailableCapacityDaily(MarketArea marketArea, int dayOfYear) {
		final List<Float> availableCapacityDaily = new ArrayList<>();
		for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
			Float availableCapacityHourly = 0f;
			for (final Generator generator : marketArea.getGenerators()) {
				for (final Plant plant : generator.getAvailablePlants()) {
					availableCapacityHourly += plant.getCapacityUnusedExpected(
							((dayOfYear - 1) * Date.HOURS_PER_DAY) + hour);
				}
			}
			availableCapacityDaily.add(availableCapacityHourly);
		}
		return availableCapacityDaily;
	}

	/** Get expected hourly exchange from market coupling */
	private double getExchangeForecastHourly(int hourOfForecastPeriod) {
		double prediction = constant;
		for (final IndependentVariable independentVariable : independentVariables) {
			prediction += independentVariable.forecastData.get(hourOfForecastPeriod)
					* independentVariable.coefficient;
		}
		return prediction;
	}

	private List<Float> getResidualLoadWithoutMarketCouplingDaily(MarketArea marketArea, int year,
			int dayOfYear) {
		final List<Float> residualLoadDaily = new ArrayList<>();
		for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
			final Float residualLoadHourly = getResidualLoadWithoutMarketCouplingHourly(marketArea,
					year, dayOfYear, hour);
			residualLoadDaily.add(residualLoadHourly);
		}
		return residualLoadDaily;
	}

	private float getResidualLoadWithoutMarketCouplingHourly(MarketArea marketArea, int year,
			int dayOfYear, int hourOfDay) {
		final float residualLoadHourly = marketArea.getDemandData().getHourlyDemand(year,
				Date.getHourOfYearFromHourOfDay(dayOfYear, hourOfDay))
				- marketArea.getManagerRenewables().getTotalRenewableLoad(year,
						Date.getHourOfYearFromHourOfDay(dayOfYear, hourOfDay))
				+ marketArea.getExchange().getHourlyFlowForecast(year,
						Date.getHourOfYearFromHourOfDay(dayOfYear, hourOfDay));
		return residualLoadHourly;
	}

	/** Set up general regression model */
	private void setUpModel() {

		int index = 0;

		for (final MarketArea interconnectedMarketArea : allMarketAreas) {

			independentVariables.add(new IndependentVariable(index++,
					IndependentVariableTypes.RESIDUAL_DEMAND, interconnectedMarketArea));

		}

		independentVariables.add(new IndependentVariable(index++,
				IndependentVariableTypes.DUMMY_SCARCITY, marketAreaFrom));
		independentVariables.add(new IndependentVariable(index++,
				IndependentVariableTypes.DUMMY_SCARCITY, marketAreaTo));
	}
}
