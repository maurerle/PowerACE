package results.spot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.scheduling.Date;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/**
 * Writes hourly demand-based emission results to market-specific log files,
 * incl. emission factors for Green Charging paper
 * <p>
 * (based on {@link EmissionsCarbonMarketArea}
 * 
 * @since 11/2019
 * @author Christian Will
 * 
 */
public class EmissionsCarbonAnalysis {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(EmissionsCarbonAnalysis.class.getName());

	/** Default value for {@link #marketRECFactor} */
	private final static float marketRECFactorDEFAULT = 1.0f;

	/** Log ID */
	private int logIDCarbonXLSX;
	private Map<Integer, Float> mapConsEVFromREC;
	private Map<Integer, Float> mapConsTotal;
	private Map<Integer, Float> mapEmFactorAllocCons;
	private Map<Integer, Float> mapEmFactorAllocProd;
	private Map<Integer, Float> mapEmFactorClassicCons;
	private Map<Integer, Float> mapEmFactorClassicProd;
	private Map<Integer, Float> mapEmissionsCons;
	private Map<Integer, Float> mapEmissionsProd;
	private Map<Integer, Float> mapProdRenewables;
	private Map<Integer, Float> mapProdTotal;
	private Map<Integer, Float> mapRECAvailableForEV;
	private Map<Integer, Float> mapShareGreenEV;

	private final MarketArea marketArea;

	private final int year;

	/**
	 * Percentage of renewable production (i.e. renewable electricity credits,
	 * REC) available for EV charging
	 */
	private final float marketRECFactor;

	/**
	 * Map with the values that have to be written in the log files. Needed to
	 * sort it hourly.
	 */
	private Map<Integer, List<Object>> valuesLogging;

	public EmissionsCarbonAnalysis(MarketArea marketArea) {
		this.marketArea = marketArea;
		year = Date.getYear();
		// limitingFactorProduction is = 0.1 unless we set it in xml for
		// renewables-optimization in TLM. The default value here is set for
		// code correctness.

		marketRECFactor = marketRECFactorDEFAULT;
	}

	/** Write log file for this marketArea at the end of a year */
	public void logResults() {
		logger.info(marketArea.getInitialsBrackets() + "Logging emission analysis results");

		mapsInitialize();
		logInitialize();
		logAnalysis();

		LoggerXLSX.close(logIDCarbonXLSX);
		mapsReset();
	}

	/**
	 * Writes the results in the corresponding xslx file.
	 */
	private void logAnalysis() {
		Stream.iterate(0, hourOfYear -> hourOfYear + 1).limit(Date.HOURS_PER_YEAR).parallel()
				.forEach(hourOfYear -> logAnalysisParallel(hourOfYear));
		valuesLogging.keySet().parallelStream().sorted(Comparator.naturalOrder()).forEachOrdered(
				hourOfYear -> LoggerXLSX.writeLine(logIDCarbonXLSX, valuesLogging.get(hourOfYear)));
	}

	private void logAnalysisParallel(int hourOfYear) {
		try {
			final List<Object> values = new ArrayList<>();
			// Hour of year, day and hour
			values.add(hourOfYear);
			values.add(Date.getDayFromHourOfYear(hourOfYear));
			values.add(Date.getHourOfDayFromHourOfYear(hourOfYear));

			// Production (3 columns)
			logProduction(values, hourOfYear);

			// Consumption (3 columns)
			logConsumption(values, hourOfYear);

			// Carbon emissions total (2 columns)
			logEmissionsCarbon(values, hourOfYear);

			// Average mix emission factors (2 columns)
			logEmissionFactorsClassic(values, hourOfYear);

			// Allocated (EV-specific) emission factors (2 columns)
			logEmissionFactorsAllocated(values, hourOfYear);

			// residual load emission factors (2 columns)
			logEmissionFactorsResidualLoad(values, hourOfYear);

			valuesLogging.put(hourOfYear, values);

		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private synchronized void logConsumption(List<Object> values, int hourOfYear) {
		try {
			final float demandSystem = marketArea.getDemandData().getHourlyDemand(Date.getYear(),
					hourOfYear);
			values.add(demandSystem);
			mapConsTotal.put(hourOfYear, demandSystem);
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private synchronized void logEmissionFactorsAllocated(List<Object> values, int hourOfYear) {
		final float allocEmFactCons = mapEmFactorClassicCons.get(hourOfYear)
				* (1 - mapShareGreenEV.get(hourOfYear)) * (mapConsTotal.get(hourOfYear)
						/ (mapConsTotal.get(hourOfYear) - mapConsEVFromREC.get(hourOfYear)));
		values.add(allocEmFactCons);
		mapEmFactorAllocCons.put(hourOfYear, allocEmFactCons);

		final float allocEmFactProd = mapEmFactorClassicProd.get(hourOfYear)
				* (1 - mapShareGreenEV.get(hourOfYear)) * (mapProdTotal.get(hourOfYear)
						/ (mapProdTotal.get(hourOfYear) - mapConsEVFromREC.get(hourOfYear)));
		values.add(allocEmFactProd);
		mapEmFactorAllocProd.put(hourOfYear, allocEmFactProd);
	}

	private synchronized void logEmissionFactorsClassic(List<Object> values, int hourOfYear) {
		final float emFactCons = marketArea.getCarbonEmissions()
				.getEmissionsFactorDemandBasedHourly(year, hourOfYear);
		values.add(emFactCons);
		mapEmFactorClassicCons.put(hourOfYear, emFactCons);

		final float emFactProd = marketArea.getCarbonEmissions()
				.getEmissionsFactorProductionBasedHourly(year, hourOfYear);
		values.add(emFactProd);
		mapEmFactorClassicProd.put(hourOfYear, emFactProd);
	}

	private synchronized void logEmissionFactorsResidualLoad(List<Object> values, int hourOfYear) {
		final float emFactResidCons = mapEmissionsCons.get(hourOfYear);
		values.add(emFactResidCons);

		final float emFactResidProd = mapEmissionsProd.get(hourOfYear);
		values.add(emFactResidProd);
	}

	private synchronized void logEmissionsCarbon(List<Object> values, int hourOfYear) {
		final float emissionsCons = mapConsTotal.get(hourOfYear) * marketArea.getCarbonEmissions()
				.getEmissionsFactorDemandBasedHourly(year, hourOfYear);
		values.add(emissionsCons);
		mapEmissionsCons.put(hourOfYear, emissionsCons);

		final float emissionsProd = marketArea.getCarbonEmissions()
				.getEmissionsHourlyProduction(year, hourOfYear);
		values.add(emissionsProd);
		mapEmissionsProd.put(hourOfYear, emissionsProd);
	}

	/**
	 * Initializes the file at the beginning of each year by setting the file
	 * name, title line and unit line in the xslx file.
	 */
	private void logInitialize() {

		final String fileName = marketArea.getInitialsUnderscore() + "AnalysisEmissionFactors_"
				+ year;
		final Folder folder = Folder.EMISSIONS;

		List<ColumnHeader> titleLine;
		final String description = "Hourly emission factors and other data for analysis";

		titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("Hour_Of_Year", Unit.NONE));
		titleLine.add(new ColumnHeader("Day", Unit.NONE));
		titleLine.add(new ColumnHeader("Hour", Unit.HOUR));

		// Production
		titleLine.add(
				new ColumnHeader("Electricity_Production_Total_Renewables", Unit.ENERGY_VOLUME));
		titleLine.add(
				new ColumnHeader("Electricity_Production_Total_Conventional", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("Production_Total", Unit.ENERGY_VOLUME)); // PROD

		// Consumption
		titleLine.add(new ColumnHeader("Demand", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("Consumption_Electric_Vehicles", Unit.ENERGY_VOLUME)); // CONS_PEV
		titleLine.add(new ColumnHeader("Consumption_Total", Unit.ENERGY_VOLUME)); // CONS

		// Carbon emissions total
		titleLine.add(new ColumnHeader("Emissions_Consumption-based", Unit.TONS_CO2)); // E_cons
		titleLine.add(new ColumnHeader("Emissions_Production-based", Unit.TONS_CO2)); // E_prod

		// Electric vehicle information
		titleLine.add(new ColumnHeader("REC_Available_for_EV", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("Consumption_EV_from_REC", Unit.ENERGY_VOLUME));
		titleLine.add(new ColumnHeader("Share_Green_EV", Unit.NONE));

		// Average mix emission factors
		titleLine.add(new ColumnHeader("Consumption-based carbon emissions factor",
				Unit.EMISSION_FACTOR));
		titleLine.add(
				new ColumnHeader("Production-based carbon emissions factor", Unit.EMISSION_FACTOR));

		// Allocated (EV-specific) emission factors
		titleLine.add(new ColumnHeader("allocated EV emission factor, consumption-based",
				Unit.EMISSION_FACTOR));
		titleLine.add(new ColumnHeader("allocated EV emission factor, production-based",
				Unit.EMISSION_FACTOR));
		// Residual load emission factors
		titleLine.add(new ColumnHeader("residual load emission factor, consumption-based",
				Unit.EMISSION_FACTOR));
		titleLine.add(new ColumnHeader("residual load emission factor, production-based",
				Unit.EMISSION_FACTOR));

		// Initialize map
		valuesLogging = new ConcurrentHashMap<>();

		logIDCarbonXLSX = LoggerXLSX.newLogObject(folder, fileName, description, titleLine,
				marketArea.getIdentityAndNameLong(), Frequency.HOURLY, "#,##0.00");
	}

	private synchronized void logProduction(List<Object> values, int hourOfYear) {
		final float prodRenewable = marketArea.getManagerRenewables()
				.getTotalRenewableLoad(hourOfYear);
		values.add(prodRenewable);
		mapProdRenewables.put(hourOfYear, prodRenewable);

		final float prodConventional = marketArea.getElectricityProduction()
				.getElectricityConventionalHourly(year, hourOfYear);
		values.add(prodConventional);
		values.add(prodConventional + prodRenewable);
		mapProdTotal.put(hourOfYear, prodConventional + prodRenewable);
	}

	private void mapsInitialize() {
		mapEmFactorAllocCons = new HashMap<>();
		mapEmFactorAllocProd = new HashMap<>();
		mapConsEVFromREC = new HashMap<>();
		mapConsTotal = new HashMap<>();
		mapEmFactorClassicCons = new HashMap<>();
		mapEmFactorClassicProd = new HashMap<>();
		mapEmissionsCons = new HashMap<>();
		mapEmissionsProd = new HashMap<>();
		mapProdRenewables = new HashMap<>();
		mapProdTotal = new HashMap<>();
		mapRECAvailableForEV = new HashMap<>();
		mapShareGreenEV = new HashMap<>();
	}

	private void mapsReset() {
		mapEmFactorAllocCons = null;
		mapEmFactorAllocProd = null;
		mapConsEVFromREC = null;
		mapConsTotal = null;
		mapEmFactorClassicCons = null;
		mapEmFactorClassicProd = null;
		mapEmissionsCons = null;
		mapEmissionsProd = null;
		mapProdRenewables = null;
		mapProdTotal = null;
		mapRECAvailableForEV = null;
		mapShareGreenEV = null;
	}
}
