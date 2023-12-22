package results.investment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import supply.powerplant.PlantAbstract;
import supply.powerplant.PlantOption;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/**
 * 
 *
 */
public class NetValueExtremePrices extends Agent {
	private class LogValue {

		Float maxPrice;
		Float percentageHighPrices;
		Float percentagePerYear;

		public LogValue(Float percentageHighPrices, Float percentagePerYear, Float maxPrice) {
			super();
			this.percentageHighPrices = percentageHighPrices;
			this.percentagePerYear = percentagePerYear;
			this.maxPrice = maxPrice;
		}

	}
	private static final Logger logger = LoggerFactory
			.getLogger(NetValueExtremePrices.class.getName());

	/** Per year, Per agent, per investment option, per offset */
	Map<Integer, Map<String, Map<PlantOption, Map<Integer, LogValue>>>> percentages = new HashMap<>();

	public NetValueExtremePrices(MarketArea marketArea) {
		super(marketArea);
		initialize();
	}

	public void addValue(String agent, PlantOption plant, Integer offset,
			Float percentageHighPrices, Float percentageYear, Float maxPrice) {

		final int year = Date.getYear();

		// create maps
		if (!percentages.get(year).containsKey(agent)) {
			percentages.get(year).put(agent, new HashMap<>());
		}
		if (!percentages.get(year).get(agent).containsKey(plant)) {
			percentages.get(year).get(agent).put(plant, new HashMap<>());
		}
		percentages.get(year).get(agent).get(plant).put(offset,
				new LogValue(percentageHighPrices, percentageYear, maxPrice));
	}

	@Override
	public void initialize() {
		for (int year = Date.getYear(); year <= Date.getLastYear(); year++) {
			percentages.put(year, new HashMap<>());
		}
	}

	public void writeLogFile() {
		try {
			// Check if values exist
			final int year = Date.getYear();
			if (percentages.get(year).isEmpty()) {
				return;
			}

			// Initialize log file
			final String fileName = marketArea.getInitialsUnderscore() + "NetValueExtremePrices"
					+ year;
			final String description = "Describes the results of the investment planner";

			final List<ColumnHeader> columns = new ArrayList<>();
			columns.add(new ColumnHeader("Year", Unit.YEAR));
			columns.add(new ColumnHeader("Agent name", Unit.NONE));
			columns.add(new ColumnHeader("Plant option", Unit.NONE));
			columns.add(new ColumnHeader("Plant net present value", Unit.CAPACITY_INVESTMENT));
			final Set<Integer> yearOffSetMax = getMaxOffSet();
			for (final Integer yearOffset : yearOffSetMax) {
				columns.add(
						new ColumnHeader("Percentage on Profit " + yearOffset, Unit.PERCENTAGE));
			}
			for (final Integer yearOffset : yearOffSetMax) {
				columns.add(new ColumnHeader("Percentage of High Prices on Profit" + yearOffset,
						Unit.PERCENTAGE));
			}
			for (final Integer yearOffset : yearOffSetMax) {
				columns.add(new ColumnHeader("Max prices of years" + yearOffset, Unit.NONE));
			}

			final int logFileOverview = LoggerXLSX.newLogObject(Folder.INVESTMENT, fileName,
					description, columns, marketArea.getIdentityAndNameLong(),
					Frequency.SIMULATION);

			for (final String agent : percentages.get(year).keySet()) {
				for (final PlantOption plant : percentages.get(year).get(agent).keySet()) {
					final List<Object> data = new ArrayList<>();
					data.add(year);
					data.add(agent);
					data.add(plant.getName());
					data.add(plant.getNetPresentValue());

					for (final Integer yearOffset : yearOffSetMax) {
						if (percentages.get(year).get(agent).get(plant).containsKey(yearOffset)) {
							data.add(percentages.get(year).get(agent).get(plant)
									.get(yearOffset).percentagePerYear * 100);
						} else {
							data.add(null);
						}
					}

					for (final Integer yearOffset : yearOffSetMax) {
						if (percentages.get(year).get(agent).get(plant).containsKey(yearOffset)) {
							data.add(percentages.get(year).get(agent).get(plant)
									.get(yearOffset).percentageHighPrices * 100);
						} else {
							data.add(null);
						}
					}

					for (final Integer yearOffset : yearOffSetMax) {
						if (percentages.get(year).get(agent).get(plant).containsKey(yearOffset)) {
							data.add(percentages.get(year).get(agent).get(plant)
									.get(yearOffset).maxPrice);
						} else {
							data.add(null);
						}

					}

					LoggerXLSX.writeLine(logFileOverview, data);
				}
			}
			LoggerXLSX.close(logFileOverview);
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * @return the maximum offset of regarded years of investment option for
	 *         current year
	 */
	private Set<Integer> getMaxOffSet() {
		final Set<Integer> offsetYears = new TreeSet<>();
		final int year = Date.getYear();
		for (final String agent : percentages.get(year).keySet()) {
			for (final PlantAbstract plant : percentages.get(year).get(agent).keySet()) {
				for (final Integer yearOffSet : percentages.get(year).get(agent).get(plant)
						.keySet()) {
					offsetYears.add(yearOffSet);
				}
			}
		}
		return offsetYears;
	}

}