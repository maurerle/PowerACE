package results.investment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import supply.powerplant.CostCap;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/**
 * 
 *
 */
public class FutureMeritOrders extends Agent {

	private final int loadIncrement = 25;
	private final int loadMax = 100000;

	/**
	 * Max forecast
	 */
	private final int maxFutureForecast = 20;

	/**
	 * Prices by year, yearForecast, agent sorted by hour of year
	 */
	Map<Integer, Map<Integer, Map<String, Map<Integer, Float>>>> meritOrder = new TreeMap<>();

	public FutureMeritOrders(MarketArea marketArea) {
		super(marketArea);
		initialize();
	}

	/**
	 * Add forecast for one agent to logged prices.
	 *
	 * @param yearOfForecast
	 * @param agent
	 * @param yearForecasted
	 * @param meritOrderForecasted
	 */
	public void addMeritOrder(int yearOfForecast, String agent, int yearForecasted,
			List<CostCap> meritOrderForecasted) {
		final Map<String, Map<Integer, Float>> forecastOfAgents = meritOrder.get(yearOfForecast)
				.get(yearForecasted);

		final Map<Integer, Float> meritOrderForecastedCalculated = calcNormalizedMeritOrder(
				meritOrderForecasted);

		// lazy initialization

		if (forecastOfAgents.isEmpty() || !forecastOfAgents.containsKey(agent)) {
			forecastOfAgents.put(agent, meritOrderForecastedCalculated);
		} else {
			boolean different = false;

			for (final Integer load : forecastOfAgents.get(agent).keySet()) {
				if (Math.abs(forecastOfAgents.get(agent).get(load)
						- meritOrderForecastedCalculated.get(load)) > 0.01) {
					different = true;
					break;
				}
			}

			// Only add prices that are different
			if (different) {
				forecastOfAgents.put(agent, meritOrderForecastedCalculated);
				meritOrder.get(yearOfForecast).put(yearForecasted, forecastOfAgents);
			}
		}

	}

	@Override
	public void initialize() {
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			meritOrder.put(year, new TreeMap<>());
			for (int yearOffSet = year; yearOffSet <= (year + maxFutureForecast); yearOffSet++) {
				meritOrder.get(year).put(yearOffSet, new TreeMap<>());
			}
		}
	}

	public void logMeritOrder() {

		final int currentYear = Date.getYear();
		final String fileName = marketArea.getInitialsUnderscore() + "MeritOrder" + currentYear;
		final String description = "The plants that have been in the strategic reserve";

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("load", Unit.CAPACITY));
		columns.add(new ColumnHeader("current merit order", Unit.NONE));

		// year IN which forecast was made
		for (final Integer yearOfForecast : meritOrder.keySet()) {
			final Map<Integer, Map<String, Map<Integer, Float>>> forecastInYear = meritOrder
					.get(yearOfForecast);
			// year FOR which forecast was made
			for (final int yearForecasted : forecastInYear.keySet()) {

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

		final int logID = LoggerXLSX.newLogObject(Folder.INVESTMENT, fileName, description, columns,
				marketArea.getIdentityAndNameLong(), Frequency.HOURLY);

		final Map<Integer, Float> meritOrderForecastedCalculated = calcNormalizedMeritOrder(
				marketArea.getGenerationData().getMeritOrderUnits(Date.getYear()));

		for (int load = 0; load <= loadMax; load += loadIncrement) {
			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(load);
			dataLine.add(meritOrderForecastedCalculated.get(load));
			// year IN which forecast was made
			for (final Integer year : meritOrder.keySet()) {
				final Map<Integer, Map<String, Map<Integer, Float>>> forecastInYear = meritOrder
						.get(year);
				// year FOR which forecast was made
				for (final int yearForecasted : forecastInYear.keySet()) {
					final Map<String, Map<Integer, Float>> forecastsOfAgents = forecastInYear
							.get(yearForecasted);
					// Agent who made forecast, since forecasts can differ

					for (final String agent : forecastsOfAgents.keySet()) {
						dataLine.add(forecastsOfAgents.get(agent).get(load));
					}

				}
			}
			LoggerXLSX.writeLine(logID, dataLine);
		}

		LoggerXLSX.close(logID);
	}

	private Map<Integer, Float> calcNormalizedMeritOrder(List<CostCap> meritOrderForecasted) {

		final Map<Integer, Float> meritOrderForecastedCalculated = new TreeMap<>();

		int capacity = 0;
		for (final CostCap costCap : meritOrderForecasted) {
			for (; capacity <= loadMax; capacity += loadIncrement) {
				if (capacity <= costCap.getCumulatedNetCapacity()) {
					meritOrderForecastedCalculated.put(capacity, costCap.getCostsVar());
				} else {
					break;
				}
			}
		}
		for (; capacity <= loadMax; capacity += loadIncrement) {
			meritOrderForecastedCalculated.put(capacity, 3000f);
		}

		return meritOrderForecastedCalculated;
	}

}