package results.investment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.math.Statistics;
import tools.types.Unit;

/**
 * 
 *
 */
public class FuturePrices extends Agent {

	private static final float EPSILON = 0.1f;

	/**
	 * Max forecast
	 */
	private final int maxFutureForecast = 20;

	/**
	 * Prices by year, yearForecast, agent sorted by hour of year
	 */
	Map<Integer, Map<Integer, String>> agentLastForecast = new ConcurrentHashMap<>();

	/**
	 * Prices by year, yearForecast, agent sorted by hour of year
	 */
	Map<Integer, Map<Integer, Map<String, Map<Integer, Float>>>> prices = new ConcurrentHashMap<>();

	/**
	 * Prices by year, yearForecast, agent but sorted by price
	 */
	Map<Integer, Map<Integer, Map<String, List<Float>>>> pricesSorted = new ConcurrentHashMap<>();

	public FuturePrices(MarketArea marketArea) {
		super(marketArea);
		initialize();
	}

	/**
	 * Add forecast for one agent to logged prices.
	 *
	 * @param yearOfForecast
	 * @param agent
	 * @param yearForecasted
	 * @param pricesForecasted
	 */
	public void addForecast(int yearOfForecast, String agent, int yearForecasted,
			Map<Integer, Float> pricesForecasted) {
		final Map<String, Map<Integer, Float>> forecastOfAgents = prices.get(yearOfForecast)
				.get(yearForecasted);

		// lazy initialization
		if (!forecastOfAgents.containsKey(agent)) {

			if (forecastOfAgents.isEmpty()) {
				forecastOfAgents.put(agent, pricesForecasted);
				agentLastForecast.get(yearOfForecast).put(yearForecasted, agent);
				final List<Float> pricesForecastedSorted = new ArrayList<>(
						pricesForecasted.values());
				Collections.sort(pricesForecastedSorted);
				pricesSorted.get(yearOfForecast).get(yearForecasted).put(agent,
						pricesForecastedSorted);
			} else {
				boolean different = false;

				for (final Map<Integer, Float> forecastOfOtherAgent : forecastOfAgents.values()) {
					for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
						if (Math.abs(forecastOfOtherAgent.get(hourOfYear)
								- pricesForecasted.get(hourOfYear)) > EPSILON) {
							different = true;
							break;
						}
					}
				}
				// Only add prices that are different
				if (different) {
					forecastOfAgents.put(agent, pricesForecasted);
					agentLastForecast.get(yearOfForecast).put(yearForecasted, agent);

					final List<Float> pricesForecastedSorted = new ArrayList<>(
							pricesForecasted.values());
					Collections.sort(pricesForecastedSorted);
					pricesSorted.get(yearOfForecast).get(yearForecasted).put(agent,
							pricesForecastedSorted);
				}
			}

		}
	}

	/**
	 *
	 * @param year
	 *            e.g. 2012
	 * @param offset
	 *            e.g. 2
	 *
	 * @return the average price forecast for a specific year with a specific
	 *         offset.
	 */
	public Map<String, Map<Integer, Float>> getFuturePrice(int year, int offset) {
		final int yearInWhichForecastWasMade = year - offset;
		if (prices.containsKey(yearInWhichForecastWasMade)) {
			return prices.get(yearInWhichForecastWasMade).get(year);
		}

		return null;
	}

	/**
	 *
	 *
	 * @param year
	 *            e.g. 2012
	 * @param offset
	 *            e.g. 2
	 *
	 * @return the average price forecast for a specific year with a specific
	 *         offset.
	 */
	public Map<Integer, Float> getFuturePriceLastForecast(int year, int offset) {
		final int yearInWhichForecastWasMade = year - offset;
		// forecast only exist if conventional investor agents are active
		if (prices.containsKey(yearInWhichForecastWasMade)
				&& prices.get(yearInWhichForecastWasMade).containsKey(year)
				&& (marketArea.getInvestorsConventionalGeneration().size() > 0)) {
			final String agent = agentLastForecast.get(yearInWhichForecastWasMade).get(year);
			if ((agent != null)
					&& prices.get(yearInWhichForecastWasMade).get(year).containsKey(agent)) {
				return prices.get(yearInWhichForecastWasMade).get(year).get(agent);
			}
		}
		return null;
	}

	/**
	 *
	 *
	 * @param year
	 *            e.g. 2012
	 * @param offset
	 *            e.g. 2
	 *
	 * @return the average price forecast for a specific year with a specific
	 *         offset.
	 */
	public Float getFuturePricesAverages(int year, int offset) {
		final int yearInWhichForecastWasMade = year - offset;

		Float average = null;
		if (prices == null) {
			return average;
		}
		if (prices.containsKey(yearInWhichForecastWasMade)
				&& prices.get(yearInWhichForecastWasMade).containsKey(year)) {
			average = 0f;
			for (final String agentForecast : prices.get(yearInWhichForecastWasMade).get(year)
					.keySet()) {
				average += Statistics.calcAvg(prices.get(yearInWhichForecastWasMade).get(year)
						.get(agentForecast).values());
			}
			average /= prices.get(yearInWhichForecastWasMade).get(year).size();

		}
		return average;
	}

	/**
	 *
	 *
	 * @param year
	 *            e.g. 2012
	 * @param offset
	 *            e.g. 2
	 *
	 * @return the average price forecast for a specific year with a specific
	 *         offset and specific hour of that year.
	 */
	public Float getFuturePricesAverages(int year, int offset, int hourOfYear) {
		final int yearInWhichForecastWasMade = year - offset;

		Float average = null;
		if (prices.containsKey(yearInWhichForecastWasMade)
				&& prices.get(yearInWhichForecastWasMade).containsKey(year)) {
			average = 0f;
			for (final String agentForecast : prices.get(yearInWhichForecastWasMade).get(year)
					.keySet()) {
				average += prices.get(yearInWhichForecastWasMade).get(year).get(agentForecast)
						.get(hourOfYear);
			}
			average /= prices.get(yearInWhichForecastWasMade).get(year).size();

		}
		return average;
	}

	@Override
	public void initialize() {
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			prices.put(year, new ConcurrentHashMap<>());
			pricesSorted.put(year, new ConcurrentHashMap<>());
			agentLastForecast.put(year, new ConcurrentHashMap<>());
			for (int yearOffSet = year; yearOffSet <= (year + maxFutureForecast); yearOffSet++) {
				prices.get(year).put(yearOffSet, new ConcurrentHashMap<>());
				pricesSorted.get(year).put(yearOffSet, new ConcurrentHashMap<>());
			}
		}
	}

	public void logPrices() {

		// Write yearly?
		if (!Date.isLastYear()) {

		}

		final int currentYear = Date.getYear();
		final String fileName = marketArea.getInitialsUnderscore() + "PriceForecasts" + currentYear;
		final String description = "The plants that have been in the strategic reserve";

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("hour of year", Unit.HOUR));
		columns.add(new ColumnHeader("day ahead price", Unit.ENERGY_PRICE));

		// year IN which forecast was made
		for (final Integer yearOfForecast : prices.keySet()) {
			final Map<Integer, Map<String, Map<Integer, Float>>> forecastInYear = prices
					.get(yearOfForecast);
			// year FOR which forecast was made
			for (final int yearForecasted : forecastInYear.keySet()) {
				if (yearForecasted == currentYear) {
					final Map<String, Map<Integer, Float>> forecastsOfAgents = forecastInYear
							.get(yearForecasted);
					// Agent who made forecast, since forecasts can differ
					for (final String agent : forecastsOfAgents.keySet()) {
						columns.add(new ColumnHeader(
								"YearOfForecast " + yearOfForecast + " offSet "
										+ (yearForecasted - yearOfForecast) + " agent " + agent,
								Unit.NONE));
					}
				}
			}
		}

		final int logFile = LoggerXLSX.newLogObject(Folder.INVESTMENT, fileName, description,
				columns, marketArea.getIdentityAndNameLong(), Frequency.HOURLY);

		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(hourOfYear);
			dataLine.add(
					marketArea.getElectricityResultsDayAhead().getHourlyPriceOfYear(hourOfYear));
			// year IN which forecast was made
			for (final Integer year : prices.keySet()) {
				final Map<Integer, Map<String, Map<Integer, Float>>> forecastInYear = prices
						.get(year);
				// year FOR which forecast was made
				for (final int yearForecasted : forecastInYear.keySet()) {
					if (yearForecasted == currentYear) {
						final Map<String, Map<Integer, Float>> forecastsOfAgents = forecastInYear
								.get(yearForecasted);
						// Agent who made forecast, since forecasts can differ
						for (final String agent : forecastsOfAgents.keySet()) {
							dataLine.add(forecastsOfAgents.get(agent).get(hourOfYear));
						}
					}
				}
			}
			LoggerXLSX.writeLine(logFile, dataLine);
		}

		LoggerXLSX.close(logFile);
	}

	public void logPricesSorted() {

		final int currentYear = Date.getYear();
		final String fileName = marketArea.getInitialsUnderscore() + "PriceForecastsSorted"
				+ currentYear;
		final String description = "The plants that have been in the strategic reserve";

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("hour of year", Unit.HOUR));
		columns.add(new ColumnHeader("day ahead price", Unit.ENERGY_PRICE));

		// year IN which forecast was made
		for (final Integer yearOfForecast : pricesSorted.keySet()) {
			final Map<Integer, Map<String, List<Float>>> forecastInYear = pricesSorted
					.get(yearOfForecast);
			// year FOR which forecast was made
			for (final int yearForecasted : forecastInYear.keySet()) {
				if (yearForecasted == currentYear) {
					final Map<String, List<Float>> forecastsOfAgents = forecastInYear
							.get(yearForecasted);
					// Agent who made forecast, since forecasts can differ
					for (final String agent : forecastsOfAgents.keySet()) {
						columns.add(new ColumnHeader(
								"YearOfForecast " + yearOfForecast + " offSet "
										+ (yearForecasted - yearOfForecast) + " agent " + agent,
								Unit.NONE));
					}
				}
			}
		}

		final int logFile = LoggerXLSX.newLogObject(Folder.INVESTMENT, fileName, description,
				columns, marketArea.getIdentityAndNameLong(), Frequency.HOURLY);

		final List<Float> pricesDayAhead = marketArea.getElectricityResultsDayAhead()
				.getYearlyPrices();
		Collections.sort(pricesDayAhead);

		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(hourOfYear);
			dataLine.add(pricesDayAhead.get(hourOfYear));
			// year IN which forecast was made
			for (final Integer year : pricesSorted.keySet()) {
				final Map<Integer, Map<String, List<Float>>> forecastInYear = pricesSorted
						.get(year);
				// year FOR which forecast was made
				for (final int yearForecasted : forecastInYear.keySet()) {
					if (yearForecasted == currentYear) {
						final Map<String, List<Float>> forecastsOfAgents = forecastInYear
								.get(yearForecasted);
						// Agent who made forecast, since forecasts can differ
						for (final String agent : forecastsOfAgents.keySet()) {
							dataLine.add(forecastsOfAgents.get(agent).get(hourOfYear));
						}
					}
				}
			}
			LoggerXLSX.writeLine(logFile, dataLine);
		}

		LoggerXLSX.close(logFile);
	}

}