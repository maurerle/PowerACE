package tools.math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some math tools to perform operations on lists.
 *
 * @author
 *
 */
public final class Statistics {

	public enum PriceFigureType {
		AVG,
		AVG_BOTTOM_100,
		AVG_TOP_100,
		COUNTER_MAX_PRICE,
		MAX,
		MEDIAN,
		MIN,
		TOP_20;
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Statistics.class.getName());

	/** Check whether all elements in specified list are equal */
	public static boolean areAllElementsEqual(List<Float> values) {

		float valuePrevious = values.get(0);
		for (int index = 1; index < values.size(); index++) {
			final float valueCurrent = values.get(index);
			if (compareFloats(valueCurrent, valuePrevious)) {
				valuePrevious = valueCurrent;
			} else {
				// Different value found
				return false;
			}
		}
		return true;
	}

	/**
	 * Calculate the <b>arithmetic average</b> of the list. Can handle empty
	 * lists and lists with <code>Infinite</code>, <code>NaN</code> or
	 * <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Arithmetic average</b> or <code>NaN</code> if list is empty or
	 *         has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcAvg(Collection<?> values) {
		float sum = 0f;
		int validValues = 0;
		if ((values != null) && !values.isEmpty()) {
			for (final Object value : values) {
				final Float valueFloat = Statistics.getFloatValue(value);
				if ((valueFloat == null) || valueFloat.isNaN() || valueFloat.isInfinite()) {
					return Float.NaN;
				} else {
					validValues++;
					sum += valueFloat;
				}
			}
		}

		if (validValues > 0) {
			return sum / (validValues);
		}

		return null;
	}

	/**
	 * Calculate the <b>arithmetic average</b> of the list. Can handle empty
	 * lists and lists with <code>Infinite</code>, <code>NaN</code> or
	 * <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Arithmetic average</b> or <code>NaN</code> if list is empty or
	 *         has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcAvgWithValidValues(Collection<?> values) {
		float sum = 0f;
		int validValues = 0;
		if ((values != null) && !values.isEmpty()) {
			for (final Object value : values) {
				final Float valueFloat = Statistics.getFloatValue(value);
				if ((valueFloat != null) && !valueFloat.isNaN()) {
					validValues++;
					sum += valueFloat;
				}
			}
		}

		if (validValues > 0) {
			return sum / (validValues);
		}

		return 0f;
	}

	/**
	 * Calculate the <b>coefficient of variation</b> of the list. Can handle
	 * empty lists and lists with <code>Infinite</code>, <code>NaN</code> or
	 * <code>null</code> values.
	 *
	 * @param values
	 * @return The <b>coefficient of variation</b> or <code>NaN</code> if list
	 *         is empty or has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcCoeffOfVar(List<Float> values) {
		final Float average = Statistics.calcAvg(values);
		final Float stDev = Statistics.calcStDev(values);

		return stDev / average;
	}

	/**
	 * Get compund annual growth rate (CAGR) of time series data between the
	 * specified years
	 *
	 * @param yearStart
	 * @param yearEnd
	 * @param valueStart
	 * @param valueEnd
	 * @return
	 */
	public static float calcCompundAnnualGrowthRate(int yearStart, int yearEnd, float valueStart,
			float valueEnd) {
		return (float) Math.pow((valueEnd / valueStart), (1f / (yearEnd - yearStart))) - 1;
	}

	/**
	 * Calculate the <b>linear correlation</b> of the lists. Can handle empty
	 * lists and lists with <code>Infinite</code>, <code>NaN</code> or
	 * <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Linear correlation</b> or <code>NaN</code> if list is empty or
	 *         has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcCorr(List<Float> valuesA, List<Float> valuesB) {

		// Check equal size
		if (valuesA.size() != valuesB.size()) {
			return Float.NaN;
		}

		// Check lists for integrity
		for (final Float valueA : valuesA) {
			if ((valueA == null) || valueA.isNaN() || valueA.isInfinite()) {
				return Float.NaN;
			}
		}
		for (final Float valueB : valuesB) {
			if ((valueB == null) || valueB.isNaN() || valueB.isInfinite()) {
				return Float.NaN;
			}
		}

		final Float avgA = Statistics.calcAvg(valuesA);
		final Float avgB = Statistics.calcAvg(valuesB);
		final Float nStdA = (float) Math.pow(valuesA.size(), 0.5) * Statistics.calcStDev(valuesA);
		final Float nStdB = (float) Math.pow(valuesB.size(), 0.5) * Statistics.calcStDev(valuesB);

		float sum = 0f;
		for (int index = 0; index < valuesA.size(); index++) {
			sum += (valuesA.get(index) - avgA) * (valuesB.get(index) - avgB);
		}

		return sum / (nStdA * nStdB);
	}

	/**
	 * Calculates different statistical figures for prices.
	 *
	 * @param prices
	 * @return
	 */
	public static Map<PriceFigureType, Object> calcFigures(List<Float> prices) {
		final Map<PriceFigureType, Object> pricesExpectedYear = new HashMap<>();
		final List<Float> pricesSorted = new ArrayList<>(prices);
		Collections.sort(pricesSorted);
		final int lastIndex = pricesSorted.size() - 1;
		pricesExpectedYear.put(PriceFigureType.AVG, Statistics.calcAvg(prices));
		pricesExpectedYear.put(PriceFigureType.MAX, pricesSorted.get(lastIndex));
		int counter = 1;

		// Count occurences of max price
		for (int index = 1; index < pricesSorted.size(); index++) {
			if (pricesSorted.get(lastIndex) <= pricesSorted.get(lastIndex - index)) {
				counter++;
			} else {
				break;
			}
		}
		pricesExpectedYear.put(PriceFigureType.COUNTER_MAX_PRICE, (float) counter);
		pricesExpectedYear.put(PriceFigureType.MIN, pricesSorted.get(0));
		pricesExpectedYear.put(PriceFigureType.AVG_BOTTOM_100,
				Statistics.calcAvg(pricesSorted.subList(0, 99)));
		pricesExpectedYear.put(PriceFigureType.AVG_TOP_100,
				Statistics.calcAvg(pricesSorted.subList(pricesSorted.size() - 101, lastIndex)));
		pricesExpectedYear.put(PriceFigureType.MEDIAN, Statistics.calcMedian(pricesSorted));

		final StringBuffer sb = new StringBuffer();
		for (int index = 0; index < 20; index++) {
			sb.append(pricesSorted.get(lastIndex - index) + ",");
		}
		pricesExpectedYear.put(PriceFigureType.TOP_20, sb.toString());

		return pricesExpectedYear;
	}

	/**
	 * Removes all values that are not part of the interval [lowerBound,
	 * upperBound] from valuesA and removes the same amount of lower and upper
	 * values from valuesB.
	 *
	 * @param values
	 */
	public static void calcFilteredLists(List<Float> valuesA, List<Float> valuesB, float lowerBound,
			float upperBound) {
		int removedLower = 0;
		int removedUpper = 0;

		// make copy so all values can safely be removed
		final List<Float> valuesAcopy = new ArrayList<>(valuesA);

		for (final Float value : valuesAcopy) {
			if (value > upperBound) {
				removedUpper++;
				valuesA.remove(value);
			} else if (value < lowerBound) {
				removedLower++;
				valuesA.remove(value);
			}
		}

		final List<Float> valuesBcopy = new ArrayList<>(valuesB);
		Collections.sort(valuesBcopy);

		// remove lower
		for (int index = 0; index < removedLower; index++) {
			valuesB.remove(valuesBcopy.get(index));
		}

		// remove upper
		for (int index = valuesBcopy.size() - 1; index > (valuesBcopy.size() - 1
				- removedUpper); index--) {
			valuesB.remove(valuesBcopy.get(index));
		}

	}

	/**
	 * Removes all values that are not part of the interval [lowerBound,
	 * upperBound] from valuesA and removes the same values with same index for
	 * valuesB.
	 *
	 * @param values
	 */
	public static void calcFilteredListsIndex(List<Float> valuesA, List<Float> valuesB,
			float lowerBound, float upperBound) {

		final Iterator<Float> iter = valuesA.iterator();
		int index = 0;
		while (iter.hasNext()) {
			final float value = iter.next();
			if (value > upperBound) {
				iter.remove();
				valuesB.remove(index);
				index--;
			} else if (value < lowerBound) {
				iter.remove();
				valuesB.remove(index);
				index--;
			}
			index++;
		}

	}

	/**
	 * Calculate the <b>maximum value</b> of the list. Can handle empty lists
	 * and lists with <code>Infinite</code>, <code>NaN</code> or
	 * <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Maximum value</b> or <code>NaN</code> if list is empty or has
	 *         <code>NaN</code> values.
	 */
	public static Float calcMax(Collection<Float> values) {
		return values.parallelStream().filter(s -> s != null).filter(s -> !s.isNaN())
				.max(Comparator.naturalOrder()).get();
	}

	/**
	 * Calculate the <b>mean absolute error</b> of the forecast values. Can
	 * handle empty lists and lists with <code>Infinite</code>, <code>NaN</code>
	 * or <code>null</code> values.
	 *
	 * @param forecast
	 *            Forecast values
	 * @param actual
	 *            Actual values
	 * @return <b>Mean absolute error</b> or <code>NaN</code> if list is empty
	 *         or has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcMeanAbsoluteError(List<Float> forecast, List<Float> actual) {

		// Check equal size
		if (forecast.size() != actual.size()) {
			return Float.NaN;
		}

		// Check lists for integrity
		for (final Float value : forecast) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}
		for (final Float value : actual) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}

		float sum = 0f;
		for (int index = 0; index < forecast.size(); index++) {
			sum += Math.abs(forecast.get(index) - actual.get(index));
		}
		sum /= forecast.size();

		return sum;
	}

	/**
	 * Calculate the <b>mean absolute percentage error</b> of the forecast
	 * values. Can handle empty lists and lists with <code>Infinite</code>,
	 * <code>NaN</code> or <code>null</code> values.
	 *
	 * @param forecast
	 *            Forecast values
	 * @param actual
	 *            Actual values
	 * @return <b>Mean average percentage error</b> or <code>NaN</code> if list
	 *         is empty or has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcMeanAbsolutePercentageError(List<Float> forecast, List<Float> actual) {

		// Check equal size
		if (forecast.size() != actual.size()) {
			return Float.NaN;
		}

		// Check lists for integrity
		for (final Float value : forecast) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}
		for (final Float value : actual) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}

		// Calculate mean absolute percentage error (MAPE)
		float sum = 0f;
		for (int index = 0; index < forecast.size(); index++) {
			sum += Math.abs(actual.get(index) - ((forecast.get(index)) / actual.get(index)));
		}
		sum /= forecast.size();

		return sum;
	}

	/**
	 * Calculate the <b>median</b> of the list. Can handle empty lists and lists
	 * with <code>Infinite</code>, <code>NaN</code> or <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Median</b> or <code>NaN</code> if list is empty or has special
	 *         values such as <code>NaN</code> or <code>Infinity</code>.
	 */
	public static Float calcMedian(List<Float> values) {
		final List<Float> sortedList = new ArrayList<>();
		if (!values.isEmpty()) {
			for (final Float value : values) {
				if ((value == null) || value.isNaN()) {
					return Float.NaN;
				} else {
					sortedList.add(value);
				}
			}
		}

		Collections.sort(sortedList);

		Float median = null;
		if ((sortedList.size() % 2) == 0) {
			final int midIndex = (int) Math.floor(sortedList.size() / 2) - 1;
			median = (sortedList.get(midIndex) + sortedList.get(midIndex + 1)) / 2;
		} else {
			median = sortedList.get(sortedList.size() / 2);
		}

		return median;
	}

	/**
	 * Calculate the <b>minimum value</b> of the list. Can handle empty lists
	 * and lists with <code>Infinite</code>, <code>NaN</code> or
	 * <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Minimum value</b> or <code>NaN</code> if list is empty or has
	 *         <code>NaN</code> values.
	 */
	public static Float calcMin(Collection<Float> values) {
		return values.parallelStream().filter(s -> s != null).filter(s -> !s.isNaN())
				.min(Comparator.naturalOrder()).get();
	}

	public static Integer calcMinInt(Collection<Integer> values) {
		Integer min = Integer.MAX_VALUE;
		boolean validValues = false;
		if ((values != null) && !values.isEmpty()) {
			for (final Integer value : values) {
				if ((value != null) && (min > value)) {
					validValues = true;
					min = value;
				}
			}
		}
		if (!validValues) {
			return null;
		}

		return min;
	}

	/**
	 * Calculate the <b>root mean squared error</b> of the forecast values. Can
	 * handle empty lists and lists with <code>Infinite</code>, <code>NaN</code>
	 * or <code>null</code> values.
	 *
	 * @param forecast
	 *            Forecast values
	 * @param actual
	 *            Actual values
	 * @return <b>Root mean squared error</b> or <code>NaN</code> if list is
	 *         empty or has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcRootMeanSquaredError(List<Float> forecast, List<Float> actual) {

		// Check equal size
		if (forecast.size() != actual.size()) {
			return Float.NaN;
		}

		// Check lists for integrity
		for (final Float value : forecast) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}
		for (final Float value : actual) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}

		float sum = 0f;
		for (int index = 0; index < forecast.size(); index++) {
			sum += Math.pow(forecast.get(index) - actual.get(index), 2);
		}
		sum = (float) Math.pow(sum / forecast.size(), 0.5);

		return sum;
	}

	/**
	 * Calculate the <b>standard deviation</b> the of list. Can handle empty
	 * lists and lists with <code>Infinite</code>, <code>NaN</code> or
	 * <code>null</code> values.
	 *
	 * @param values
	 * @return The <b>standard deviation</b> or <code>NaN</code> if list is
	 *         empty or has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcStDev(Collection<Float> values) {
		final Float variance = Statistics.calcVar(values);

		if (variance.isNaN()) {
			return Float.NaN;
		}

		return (float) Math.pow(variance, 0.5);

	}

	/**
	 * Calculate the sum of a list. Can handle empty lists and lists with
	 * <code>Infinite</code>, <code>NaN</code> or <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Sum</b> or <code>NaN</code> if list is empty or has special
	 *         values such as <code>NaN</code> or <code>Infinity</code>.
	 */
	public static Float calcSum(Collection<?> values) {
		return Statistics.calcSum(values, 0, values.size() - 1);
	}

	/**
	 * Calculate the sum of a list. Can handle empty lists and lists with
	 * <code>Infinite</code>, <code>NaN</code> or <code>null</code> values.
	 *
	 * @param values
	 * @return <b>Sum</b> or <code>NaN</code> if list is empty or has special
	 *         values such as <code>NaN</code> or <code>Infinity</code>.
	 */
	public static Float calcSum(Collection<?> values, int start, int end) {
		float sum = 0f;
		int validValues = 0;
		if (!values.isEmpty()) {
			final Iterator<?> iter = values.iterator();
			int counter = 0;
			while (iter.hasNext()) {
				final Object value = iter.next();
				if ((start <= counter) && (counter <= end)) {
					final Float valueFloat = Statistics.getFloatValue(value);
					if ((valueFloat == null) || valueFloat.isNaN() || valueFloat.isInfinite()) {
						return Float.NaN;
					} else {
						validValues++;
						sum += valueFloat;
					}
				}
				counter++;
			}
		}

		if (validValues > 0) {
			return sum;
		}

		return null;
	}

	/**
	 * Calculate the <b>sum</b> over the values of the <code>Map</code> for the
	 * given index range.
	 * <p>
	 * Can handle empty lists and lists with <code>Infinite</code>,
	 * <code>NaN</code> or <code>null</code> values.
	 * 
	 * @param map
	 * @param startIndex
	 * @param endIndex
	 *            Last index <u>included</u> in summation
	 * @return <b>Sum</b> or <code>NaN</code> if list is empty or has special
	 *         values such as <code>NaN</code> or <code>Infinity</code>.
	 */
	public static Float calcSumMap(Map<Integer, Float> map, int startIndex, int endIndex) {
		float sum = 0;
		if (!map.isEmpty()) {
			for (int index = startIndex; index <= endIndex; index++) {
				if (!map.containsKey(index)) {
					continue;
				}
				final Float value = map.get(index);
				if ((value == null) || value.isNaN() || value.isInfinite()) {
					return Float.NaN;
				} else {
					sum += value;
				}
			}
		}
		return sum;
	}

	/**
	 * Calculate the <b>symmetric mean absolute percentage error</b> of the
	 * forecast values. Can handle empty lists and lists with
	 * <code>Infinite</code>, <code>NaN</code> or <code>null</code> values.
	 *
	 * Not the standard version that suffers some problems such as
	 *
	 * There is a third version of SMAPE, which allows to measure the direction
	 * of the bias in the data by generating a positive and a negative error on
	 * line item level. Furthermore it is better protected against outliers and
	 * the bias effect mentioned in the previous paragraph than the two other
	 * formulas. http://en.wikipedia.org/wiki/SMAPE
	 *
	 * @param forecast
	 *            Forecast values
	 * @param actual
	 *            Actual values
	 * @return <b>Mean average percentage error</b> [0,1] or <code>NaN</code> if
	 *         list is empty or has special values such as <code>NaN</code> or
	 *         <code>Infinity</code>.
	 */
	public static Float calcSymmetricMeanAbsolutePercentageError(List<Float> forecast,
			List<Float> actual) {

		// Check equal size
		if (forecast.size() != actual.size()) {
			return Float.NaN;
		}

		// Check lists for integrity
		for (final Float value : forecast) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}
		for (final Float value : actual) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}

		// Calculate mean absolute percentage error (MAPE)
		// \frac{\sum_{t=1}^n \left|F_t-A_t\right|}{\sum_{t=1}^n (A_t+F_t)}
		float sumDenominator = 0f;
		float sumNumerator = 0f;
		for (int index = 0; index < forecast.size(); index++) {
			sumNumerator += Math.abs(actual.get(index) - forecast.get(index));
			sumDenominator += actual.get(index) + forecast.get(index);
		}
		return sumNumerator / sumDenominator;

	}

	/**
	 * Calculate the <b>symmetric mean absolute percentage error</b> of the
	 * forecast values. Can handle empty lists and lists with
	 * <code>Infinite</code>, <code>NaN</code> or <code>null</code> values.
	 *
	 * Standard version that suffers some problems such as under and over
	 * forecasting.
	 *
	 *
	 * @param forecast
	 *            Forecast values
	 * @param actual
	 *            Actual values
	 * @return <b>Symmetric Mean average percentage error</b> [0,1] or
	 *         <code>NaN</code> if list is empty or has special values such as
	 *         <code>NaN</code> or <code>Infinity</code>.
	 */
	public static Float calcSymmetricMeanAbsolutePercentageErrorOriginal(List<Float> forecast,
			List<Float> actual) {

		// Check equal size
		if (forecast.size() != actual.size()) {
			return Float.NaN;
		}

		// Check lists for integrity
		for (final Float value : forecast) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}
		for (final Float value : actual) {
			if ((value == null) || value.isNaN() || value.isInfinite()) {
				return Float.NaN;
			}
		}

		// Calculate mean absolute percentage error (MAPE)
		float sum = 0f;
		for (int index = 0; index < forecast.size(); index++) {
			sum += Math.abs(actual.get(index) - forecast.get(index))
					/ ((actual.get(index) + forecast.get(index)));
		}
		return sum / forecast.size();

	}

	/**
	 * Calculate the <b>variance</b> of the list. Can handle empty lists and
	 * lists with <code>Infinite</code>, <code>NaN</code> or <code>null</code>
	 * values.
	 *
	 * @param values
	 * @return The <b>variance</b> or <code>NaN</code> if list is empty or has
	 *         special values such as <code>NaN</code> or <code>Infinity</code>.
	 */
	public static Float calcVar(Collection<Float> values) {
		final Float average = Statistics.calcAvg(values);

		if ((average == null) || average.isNaN()) {
			return Float.NaN;
		}

		float sum = 0f;
		int validValues = 0;
		for (final Float value : values) {
			if ((value != null) && !value.isNaN() && !value.isInfinite()) {
				validValues++;
				sum += Math.pow(value - average, 2);
			}
		}

		return sum / validValues;
	}

	/** Check two floating-point numbers for 'equality' */
	public static boolean compareFloats(float float1, float float2) {
		return compareFloats(float1, float2, 0.001f);
	}

	/** Check two float for "equality" with specified epsilon */
	public static boolean compareFloats(float float1, float float2, float epsilon) {
		if (Math.abs(float1 - float2) < epsilon) {
			return true;
		}
		return false;
	}

	/**
	 * Counts the values not zero of the <code>Map</code> for the given index
	 * range.
	 * <p>
	 * Can handle empty maps and maps with <code>Infinite</code>,
	 * <code>NaN</code> or <code>null</code> values.
	 * 
	 * @param map
	 * @param startIndex
	 * @param endIndex
	 *            Last index <u>included</u> in summation
	 * @return <b>Count</b> or <code>null</code> if list is empty or has special
	 *         values such as <code>NaN</code> or <code>Infinity</code>.
	 */
	public static Integer countNotZeroMap(Map<Integer, Float> map, int startIndex, int endIndex) {
		int sum = 0;
		if (!map.isEmpty()) {
			for (int index = startIndex; index <= endIndex; index++) {
				if (!map.containsKey(index)) {
					continue;
				}
				final Float value = map.get(index);
				if ((value == null) || value.isNaN() || value.isInfinite()) {
					continue;
				} else if (Math.abs(value) > 0.001) {
					sum++;
				}
			}
		}
		return sum;
	}

	private static Float getFloatValue(Object value) {
		Float valueFloat = null;
		try {
			if (value instanceof Float) {
				valueFloat = (Float) value;
			} else if (value instanceof Double) {
				valueFloat = ((Double) value).floatValue();
			} else if (value instanceof Integer) {
				valueFloat = ((Integer) value).floatValue();
			}

		} catch (final ClassCastException e) {
			logger.warn(e.getMessage());
		}
		return valueFloat;
	}

	/**
	 * Inverse logit transformation of single raw data point
	 *
	 * @param rawData
	 * @return Re-transformed data
	 */
	private static double logitInverseTransformation(double logitData) {
		return 1 / (1 + Math.exp(-logitData));
	}

	/**
	 * Logit transformation of single raw data point
	 *
	 * @param rawData
	 * @return Transformed data
	 */
	private static double logitTransformation(double rawData) {
		return Math.log(rawData / (1 - rawData));
	}

	/**
	 * Logit transformation of raw data by looping each value and applying
	 * transformation formula
	 *
	 * @param rawData
	 * @param forward
	 *            True for forward, false for inverse logit transformation
	 * @return List of transformed data
	 */
	public static double[] logitTransformation(double[] rawData, boolean forward) {
		final double[] transformedData = new double[rawData.length];
		for (int index = 0; index < rawData.length; index++) {
			if (forward) {
				transformedData[index] = Statistics.logitTransformation(rawData[index]);
			} else {
				transformedData[index] = Statistics.logitInverseTransformation(rawData[index]);
			}
		}
		return transformedData;
	}

	public static void main(String[] args) {
		final List<Float> values = new ArrayList<>();
		values.add(-1f);
		values.add(-0f);
		values.add(-0.1f);
		values.add(3f);
		values.add(-33f);

		final List<Float> valuesB = new ArrayList<>();
		valuesB.add(-1f);
		valuesB.add(-0f);
		valuesB.add(2f);
		valuesB.add(3f);
		valuesB.add(3f);

		Statistics.calcFilteredLists(values, valuesB, -2, 2);

		logger.info(Statistics.calcAvg(values).toString());
		logger.info(Statistics.calcMedian(values).toString());
		logger.info(Statistics.calcMax(values).toString());
		logger.info(Statistics.calcMin(values).toString());
		logger.info(Statistics.calcVar(values).toString());
		logger.info(Statistics.calcStDev(values).toString());
		logger.info(Statistics.calcCoeffOfVar(values).toString().toString());
		logger.info(Statistics.calcMeanAbsoluteError(values, values).toString());
		logger.info(Statistics.calcMeanAbsolutePercentageError(values, valuesB).toString());
		logger.info(
				Statistics.calcSymmetricMeanAbsolutePercentageError(values, valuesB).toString());
		logger.info(Statistics.calcSymmetricMeanAbsolutePercentageErrorOriginal(values, valuesB)
				.toString());
		logger.info(Statistics.calcCorr(values, valuesB).toString());
		logger.info(Statistics.calcRootMeanSquaredError(values, valuesB).toString());
	}

	/**
	 * Print some statistics for a given distribution
	 *
	 * @param input
	 */
	public static void printStatistics(double[] input) {
		// Get new instance of DescriptiveStatistics
		final DescriptiveStatistics stats = new DescriptiveStatistics();

		// Add data from the array
		for (final double element : input) {
			stats.addValue(element);
		}

		// Print statistics
		logger.info("mean: " + stats.getMean());
		logger.info("median: " + stats.getPercentile(50));
		logger.info("standard deviation: " + stats.getStandardDeviation());
		logger.info("skewness: " + stats.getSkewness());
		logger.info("excess: " + stats.getKurtosis());
		logger.info("minimum: " + stats.getMin());
		logger.info("maximum: " + stats.getMax());
	}

	/** Round a double number to the specified number of decimals */
	public static float round(double value, int numberOfDecimals) {
		return Statistics.round((float) value, numberOfDecimals);
	}

	/** Round a floating number to the specified number of decimals */
	public static float round(float value, int numberOfDecimals) {
		return Math.round((value * Math.pow(10, numberOfDecimals)))
				/ ((float) (Math.pow(10, numberOfDecimals)));
	}

}