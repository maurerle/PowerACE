package results.spot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.OpenMapRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.operator.spot.MarketCouplingOperator;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.scheduling.Date;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/**
 * Writes hourly demand based emission results of all market areas in log files
 * <p>
 * 
 * @since 10/2019
 * @author Florian Zimmermann
 * 
 */
public class EmissionsCarbonMarketArea {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(EmissionsCarbonMarketArea.class.getName());

	/** Log ID */
	private int logIDCarbonXLSX;
	/** List of all market areas */
	private final List<MarketArea> marketAreasAll;
	/** Market coupling operator of model */
	private final MarketCouplingOperator marketCouplingOperator;
	/**
	 * Map with the values that have to be written in the log files. Needed to
	 * sort it hourly.
	 */
	private Map<Integer, List<Object>> valuesLogging;

	public EmissionsCarbonMarketArea(PowerMarkets model) {
		marketCouplingOperator = model.getMarketScheduler().getMarketCouplingOperator();;
		marketAreasAll = model.getMarketAreas().stream().collect(Collectors.toList());
	}

	/** Write log file at the end of a year */
	public void logResults() {
		logInitializeEmissions();
		logEmissions();
		LoggerXLSX.close(logIDCarbonXLSX);
	}

	private void logDemand(List<Object> values, int year, int hourOfYear) {
		for (final MarketArea marketArea : marketAreasAll) {
			values.add(marketArea.getDemandData().getHourlyDemand(year, hourOfYear));
		}
	}

	/**
	 * Writes the results in the corresponding xslx file.
	 */
	private void logEmissions() {
		Stream.iterate(0, hourOfYear -> hourOfYear + 1).limit(Date.HOURS_PER_YEAR).parallel()
				.forEach(hourOfYear -> logEmissionsParallel(hourOfYear));
		valuesLogging.keySet().parallelStream().sorted(Comparator.naturalOrder()).forEachOrdered(
				hourOfYear -> LoggerXLSX.writeLine(logIDCarbonXLSX, valuesLogging.get(hourOfYear)));

	}

	private void logEmissionsCarbonFactorsConsumption(List<Object> values, int year,
			int hourOfYear) {
		/*
		 * Solving matrix in the form of A*x=B Matrix A (Demand +
		 * export)*x_local - import*x_import = total emissions
		 */
		final RealMatrix coefficients = new OpenMapRealMatrix(marketAreasAll.size(),
				marketAreasAll.size());
		for (int row = 0; row < marketAreasAll.size(); row++) {
			final MarketArea localMarketArea = marketAreasAll.get(row);
			double export = 0;

			// Export flows have to be considered as additional demand
			for (final MarketArea foreignMarketArea : marketAreasAll) {
				if (!localMarketArea.equals(foreignMarketArea)) {
					export += marketCouplingOperator.getExchangeFlows()
							.getHourlyFlow(localMarketArea, foreignMarketArea, year, hourOfYear);
				}
			}

			for (int column = 0; column < marketAreasAll.size(); column++) {
				final MarketArea foreignMarketArea = marketAreasAll.get(column);
				if (row == column) {
					// add demand
					coefficients.addToEntry(row, column,
							localMarketArea.getDemandData().getHourlyDemand(year, hourOfYear)
									+ export);
				} else {
					// Add import flows
					// Import is negative and reduces the local production
					coefficients.addToEntry(row, column, -marketCouplingOperator.getExchangeFlows()
							.getHourlyFlow(foreignMarketArea, localMarketArea, year, hourOfYear));
				}
			}
		}
		final DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();

		/*
		 * Next create a RealVector array to represent the constant vector B and
		 * use solve(RealVector) to solve the system. These are the carbon
		 * emission per market area
		 */
		final RealVector constants = new OpenMapRealVector(marketAreasAll.size());
		for (int row = 0; row < marketAreasAll.size(); row++) {
			constants.setEntry(row, marketAreasAll.get(row).getCarbonEmissions()
					.getEmissionsHourlyProduction(year, hourOfYear));
		}

		// Solve the linear equation system
		final RealVector solution = solver.solve(constants);

		// write values into logfile
		for (int row = 0; row < marketAreasAll.size(); row++) {
			final double value = solution.getEntry(row);
			values.add(solution.getEntry(row));
			marketAreasAll.get(row).getCarbonEmissions().addCarbonFactorDemandBased(hourOfYear,
					(float) value);
		}
	}

	private void logEmissionsCarbonFactorsProduction(List<Object> values, int year,
			int hourOfYear) {
		for (final MarketArea marketArea : marketAreasAll) {
			final float value = marketArea.getCarbonEmissions().getEmissionsHourlyProduction(year,
					hourOfYear)
					/ (marketArea.getElectricityProduction().getElectricityConventionalHourly(year,
							hourOfYear)
							+ marketArea.getManagerRenewables().getTotalRenewableLoad(hourOfYear));
			values.add(value);
			marketArea.getCarbonEmissions().addCarbonFactorProductionBased(hourOfYear, value);
		}
	}

	private void logEmissionsCarbonProduction(List<Object> values, int year, int hourOfYear) {
		for (final MarketArea marketArea : marketAreasAll) {
			values.add(
					marketArea.getCarbonEmissions().getEmissionsHourlyProduction(year, hourOfYear));
		}
	}

	private void logEmissionsParallel(int hourOfYear) {
		try {
			final int year = Date.getYear();
			final List<Object> values = new ArrayList<>();
			// Hour of year, day and hour
			values.add(hourOfYear);
			values.add(Date.getDayFromHourOfYear(hourOfYear));
			values.add(Date.getHourOfDayFromHourOfYear(hourOfYear));

			// Demand
			logDemand(values, year, hourOfYear);

			// Carbon emissions total
			logEmissionsCarbonProduction(values, year, hourOfYear);

			if (marketAreasAll.size() > 1) {
				// Flows
				logFlows(values, year, hourOfYear);
			}

			// Consumption based emission factors
			logEmissionsCarbonFactorsConsumption(values, year, hourOfYear);

			// Production based emission factors
			logEmissionsCarbonFactorsProduction(values, year, hourOfYear);

			valuesLogging.put(hourOfYear, values);
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void logFlows(List<Object> values, int year, int hourOfYear) {
		for (final MarketArea fromMarketArea : marketAreasAll) {
			for (final MarketArea toMarketArea : marketAreasAll) {
				if (!fromMarketArea.equals(toMarketArea)) {
					values.add(marketCouplingOperator.getExchangeFlows()
							.getHourlyFlow(fromMarketArea, toMarketArea, year, hourOfYear));
				}
			}
		}
	}

	private void logInitializeDemand(List<ColumnHeader> titleLine) {
		for (final MarketArea marketArea : marketAreasAll) {
			titleLine.add(new ColumnHeader("Demand " + marketArea.getIdentityAndNameLong(),
					Unit.ENERGY_VOLUME));
		}
	}

	/**
	 * Initializes the file at the beginning of each year by setting the file
	 * name, title line and unit line in the xslx file.
	 */
	private void logInitializeEmissions() {

		final String fileName = "0_Emissons_Carbon" + Date.getYear();

		final Folder folder = Folder.MARKET_COUPLING;

		List<ColumnHeader> titleLine;
		final String description = "Demand based carbon emissions";

		titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("Hour_Of_Year", Unit.NONE));
		titleLine.add(new ColumnHeader("Day", Unit.NONE));
		titleLine.add(new ColumnHeader("Hour", Unit.HOUR));

		// Demand
		logInitializeDemand(titleLine);

		// Carbon emissions total
		logInitializeEmissionsCarbonTotal(titleLine);

		if (marketAreasAll.size() > 1) {
			// Flows
			logInitializeFlows(titleLine);
		}
		// Consumption based Emission factors
		logInitializeEmissionsCarbonFactorsConsumption(titleLine);

		// Production based emission factors
		logInitializeEmissionsCarbonFactorsProduction(titleLine);

		// Initialize map
		valuesLogging = new ConcurrentHashMap<>();

		logIDCarbonXLSX = LoggerXLSX.newLogObject(folder, fileName, description, titleLine, "",
				Frequency.HOURLY, "#,##0.00");
	}

	private void logInitializeEmissionsCarbonFactorsConsumption(List<ColumnHeader> titleLine) {
		for (final MarketArea marketArea : marketAreasAll) {
			titleLine.add(new ColumnHeader("Consumption based carbon emissions factors for "
					+ marketArea.getIdentityAndNameLong(), Unit.EMISSION_FACTOR));
		}
	}

	private void logInitializeEmissionsCarbonFactorsProduction(List<ColumnHeader> titleLine) {
		for (final MarketArea marketArea : marketAreasAll) {
			titleLine.add(new ColumnHeader("Production based carbon emissions factors for "
					+ marketArea.getIdentityAndNameLong(), Unit.EMISSION_FACTOR));
		}
	}

	private void logInitializeEmissionsCarbonTotal(List<ColumnHeader> titleLine) {
		for (final MarketArea marketArea : marketAreasAll) {
			titleLine.add(new ColumnHeader(
					"Carbon Emissions " + marketArea.getIdentityAndNameLong(), Unit.TONS_CO2));
		}
	}

	private void logInitializeFlows(List<ColumnHeader> titleLine) {
		for (final MarketArea fromMarketArea : marketAreasAll) {
			for (final MarketArea toMarketArea : marketAreasAll) {
				if (!fromMarketArea.equals(toMarketArea)) {
					titleLine.add(new ColumnHeader("Flow_" + fromMarketArea.getInitials() + "_"
							+ toMarketArea.getInitials(), Unit.ENERGY_VOLUME));
				}
			}
		}
	}

}
