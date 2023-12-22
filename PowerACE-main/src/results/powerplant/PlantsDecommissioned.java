package results.powerplant;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.Plant;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/**
 * Contains the actively decommissioned plants.
 *
 * 
 *
 */
public final class PlantsDecommissioned extends Agent {

	private class PlantDecision {

		float capacityDecommissionedCumulated;
		boolean capacityLeft;
		String comment;
		boolean decommissionDenied;
		boolean decommissioned;
		float expectedFutureProfit;
		Plant plant;
		int yearsOfNegativeProfit;
		int yearsToRun;

		public PlantDecision(String comment, Plant plant, boolean decommissioned,
				boolean decommissionDenied, boolean capacityLeft, int yearsOfNegativeProfit,
				int yearsToRun, float expectedFutureProfit, float capacityDecommissionedCumulated) {
			super();
			this.comment = comment;
			this.plant = plant;
			this.decommissioned = decommissioned;
			this.decommissionDenied = decommissionDenied;
			this.capacityLeft = capacityLeft;
			this.yearsOfNegativeProfit = yearsOfNegativeProfit;
			this.yearsToRun = yearsToRun;
			this.expectedFutureProfit = expectedFutureProfit;
			this.capacityDecommissionedCumulated = capacityDecommissionedCumulated;
		}

	}

	private class PlantDecommissioned {

		String comment;
		Plant plant;

		public PlantDecommissioned(String comment, Plant plant) {
			super();
			this.comment = comment;
			this.plant = plant;
		}

	}

	/**
	 * List with the yearly decisions by year and plant identity.
	 */
	private final Map<Integer, Map<Integer, PlantDecision>> plantDecisions = new TreeMap<>();

	/**
	 * List with a set of all decommissioned plants before or in requested year.
	 */
	private final Map<Integer, Set<Integer>> plantsDecommisionedCumulated = new TreeMap<>();

	/**
	 * List that for each year contains the decommissioned plant.
	 */
	private final Set<Integer> plantsDecommissioned = new HashSet<>();

	/**
	 * List with the yearly decommissioned plants order by year and plant
	 * identity.
	 */
	private final Map<Integer, Map<Integer, PlantDecommissioned>> plantsDecommissionsByYear = new TreeMap<>();

	public PlantsDecommissioned(MarketArea marketArea) {
		super(marketArea);
		initialize();
	}

	public void addDecommissionedPlant(int year, Plant plant, String comment) {
		plantsDecommissionsByYear.get(year).put(plant.getUnitID(),
				new PlantDecommissioned(comment, plant));
		plantsDecommissioned.add(plant.getUnitID());

		for (int yearCounter = year; yearCounter <= Date.getLastYear(); yearCounter++) {
			plantsDecommisionedCumulated.get(yearCounter).add(plant.getUnitID());
		}
	}

	public void addPlantDecision(int year, Plant plant, String comment, boolean decommissioned,
			boolean capacityLeft, boolean decommissionDenied, float expectedFutureProfit,
			int yearsOfNegativeProfit, int yearsToRun, float capacityDecommissionedCumulated) {
		plantDecisions.get(year).put(plant.getUnitID(),
				new PlantDecision(comment, plant, decommissioned, decommissionDenied, capacityLeft,
						yearsOfNegativeProfit, yearsToRun, expectedFutureProfit,
						capacityDecommissionedCumulated));
	}

	/**
	 * @param plant
	 * @return the year the plant was decommissioned, [yearAvailable,
	 *         yearShutdown], if plant was not decommissioned before end of
	 *         technical life time, return null.
	 */
	public Integer getYearOfDecommission(Plant plant) {
		Integer year = null;

		if (isDecommissioned(plant)) {
			for (int yearCounter = plant.getAvailableYear(); yearCounter <= plant
					.getShutDownYear(); yearCounter++) {
				if (isDecommissioned(plant, yearCounter)) {
					year = yearCounter;
					break;
				}
			}
		}

		return year;
	}

	@Override
	public void initialize() {
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			plantsDecommissionsByYear.put(year, new HashMap<>(1000));
			plantDecisions.put(year, new HashMap<>(1000));
			plantsDecommisionedCumulated.put(year, new HashSet<>(100));
		}
	}

	public boolean isDecommissioned(Plant plant) {
		return plantsDecommissioned.contains(plant.getUnitID());
	}

	public boolean isDecommissioned(Plant plant, int year) {
		// Can't be decomissioned after simulation end
		if ((year > Date.getLastYear()) || (year < Date.getStartYear())) {
			return false;
		}
		return plantsDecommisionedCumulated.get(year).contains(plant.getUnitID());
	}

	/**
	 * Log plants that have been actively decommissioned.
	 */
	public void logPlants() {

		final String fileName = marketArea.getInitialsUnderscore() + "PlantsDecommissioned"
				+ Date.getYear();
		final String description = "The plants that have been in the strategic reserve";

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("Shutdown Year", Unit.YEAR));
		columns.add(new ColumnHeader("Plant Identity", Unit.NONE));
		columns.add(new ColumnHeader("Name", Unit.NONE));
		columns.add(new ColumnHeader("Comment", Unit.NONE));
		columns.add(new ColumnHeader("Fuel Type", Unit.NONE));
		columns.add(new ColumnHeader("Efficiency", Unit.NONE));
		columns.add(new ColumnHeader("Energy Conversion", Unit.NONE));
		columns.add(new ColumnHeader("Fixed Costs", Unit.CURRENCY));
		columns.add(new ColumnHeader("Net Capacity", Unit.CAPACITY));
		columns.add(new ColumnHeader("Years Shutdown Earlier", Unit.YEAR));
		columns.add(new ColumnHeader("Technical Life Time Start", Unit.YEAR));
		columns.add(new ColumnHeader("Technical Life Time End", Unit.YEAR));
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Plant Profit " + year, Unit.YEAR));
		}
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Running Hours " + year, Unit.YEAR));
		}

		final int logFilePlants = LoggerXLSX.newLogObject(Folder.SUPPLY, fileName, description,
				columns, marketArea.getIdentityAndNameLong(), Frequency.SIMULATION);

		for (final Integer year : plantsDecommissionsByYear.keySet()) {
			for (final Integer plantIdentity : plantsDecommissionsByYear.get(year).keySet()) {

				final Plant plant = plantsDecommissionsByYear.get(year).get(plantIdentity).plant;
				final String comment = plantsDecommissionsByYear.get(year)
						.get(plantIdentity).comment;

				final List<Object> dataLine = new ArrayList<>();
				dataLine.add(year);
				dataLine.add(plantIdentity);
				dataLine.add(plant.getName());
				dataLine.add(comment);
				dataLine.add(plant.getFuelType());
				dataLine.add(plant.getEfficiency());
				dataLine.add(plant.getEnergyConversion());
				dataLine.add(
						plant.getCostsOperationMaintenanceFixed(year) * plant.getNetCapacity());
				dataLine.add(plant.getNetCapacity());
				dataLine.add(plant.getShutDownYear() - year);
				dataLine.add(plant.getAvailableYear());
				dataLine.add(plant.getShutDownYear());
				for (int yearCounter = Date.getStartYear(); yearCounter <= Date
						.getLastYear(); yearCounter++) {
					dataLine.add(plant.getProfitYearly(yearCounter));
				}
				for (int yearCounter = Date.getStartYear(); yearCounter <= Date
						.getLastYear(); yearCounter++) {
					dataLine.add(plant.getRunningHours(yearCounter));
				}

				LoggerXLSX.writeLine(logFilePlants, dataLine);
			}
		}

		LoggerXLSX.close(logFilePlants);

	}

	/**
	 * Log plants that have been actively decommissioned.
	 */
	public void logPlantsDecisions() {

		// Delete older files which contain information which is also in current
		// file
		final String fileNameOld = marketArea.getInitialsUnderscore() + "PlantsDecisions"
				+ (Date.getYear() - 1) + Settings.LOG_FILE_SUFFIX_EXCEL;
		final File fileOld = new File(// Create log folder
				Settings.getLogPathName(marketArea.getIdentityAndNameLong(), Folder.SUPPLY)
						+ fileNameOld);
		if (fileOld.exists()) {
			fileOld.delete();
		}

		final String fileName = marketArea.getInitialsUnderscore() + "PlantsDecisions"
				+ Date.getYear();
		final String description = "The plants that were checked for decommission";

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("Year", Unit.YEAR));
		columns.add(new ColumnHeader("Plant Identity", Unit.NONE));
		columns.add(new ColumnHeader("Name", Unit.NONE));
		columns.add(new ColumnHeader("Decommisioned", Unit.NONE));
		columns.add(new ColumnHeader("Decommision Denied", Unit.NONE));
		columns.add(new ColumnHeader("Capacity Left", Unit.CAPACITY));
		columns.add(new ColumnHeader("Capacity Cumulated Yearly", Unit.CAPACITY));
		columns.add(new ColumnHeader("Years of negative profit", Unit.YEAR));
		columns.add(new ColumnHeader("expected future profit", Unit.CURRENCY));
		columns.add(new ColumnHeader("years to run", Unit.YEAR));
		columns.add(new ColumnHeader("Comment", Unit.NONE));
		columns.add(new ColumnHeader("Fuel Type", Unit.NONE));
		columns.add(new ColumnHeader("Efficiency", Unit.NONE));
		columns.add(new ColumnHeader("Energy Conversion", Unit.NONE));
		columns.add(new ColumnHeader("Fixed Costs", Unit.CURRENCY));
		columns.add(new ColumnHeader("Net Capacity", Unit.CAPACITY));
		columns.add(new ColumnHeader("Years Shutdown Earlier", Unit.YEAR));
		columns.add(new ColumnHeader("Technical Life Time Start", Unit.YEAR));
		columns.add(new ColumnHeader("Technical Life Time End", Unit.YEAR));
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Plant Profit " + year, Unit.CURRENCY));
		}
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			columns.add(new ColumnHeader("Running Hours " + year, Unit.HOUR));
		}

		final int logFilePlants = LoggerXLSX.newLogObject(Folder.SUPPLY, fileName, description,
				columns, marketArea.getIdentityAndNameLong(), Frequency.SIMULATION);

		for (final Integer year : plantDecisions.keySet()) {
			for (final Integer plantIdentity : plantDecisions.get(year).keySet()) {

				final PlantDecision plantDecision = plantDecisions.get(year).get(plantIdentity);
				final Plant plant = plantDecision.plant;
				final String comment = plantDecision.comment;
				final float expectedFutureIncome = plantDecision.expectedFutureProfit;
				final int yearsOfNegativeProfit = plantDecision.yearsOfNegativeProfit;
				// plus 1 because decision is at the beginning of the year
				final int yearsToRun = plantDecision.yearsToRun + 1;
				final boolean decommissioned = plantDecision.decommissioned;
				final boolean decommissionDenied = plantDecision.decommissionDenied;
				final boolean capacityLeft = plantDecision.capacityLeft;
				final float capacityCumulated = plantDecision.capacityDecommissionedCumulated;

				final List<Object> dataLine = new ArrayList<>();
				dataLine.add(year);
				dataLine.add(plantIdentity);
				dataLine.add(plant.getName());
				dataLine.add(decommissioned);
				dataLine.add(decommissionDenied);
				dataLine.add(capacityLeft);
				dataLine.add(capacityCumulated);
				dataLine.add(yearsOfNegativeProfit);
				dataLine.add(expectedFutureIncome);
				dataLine.add(yearsToRun);
				dataLine.add(comment);
				dataLine.add(plant.getFuelType());
				dataLine.add(plant.getEfficiency());
				dataLine.add(plant.getEnergyConversion());
				dataLine.add(
						plant.getCostsOperationMaintenanceFixed(year) * plant.getNetCapacity());
				dataLine.add(plant.getNetCapacity());
				dataLine.add(plant.getShutDownYear() - year);
				dataLine.add(plant.getAvailableYear());
				dataLine.add(plant.getShutDownYear());
				for (int yearCounter = Date.getStartYear(); yearCounter <= Date
						.getLastYear(); yearCounter++) {
					dataLine.add(plant.getProfitYearly(yearCounter));
				}
				for (int yearCounter = Date.getStartYear(); yearCounter <= Date
						.getLastYear(); yearCounter++) {
					dataLine.add(plant.getRunningHours(yearCounter));
				}

				LoggerXLSX.writeLine(logFilePlants, dataLine);
			}
		}

		LoggerXLSX.close(logFilePlants);

	}

}