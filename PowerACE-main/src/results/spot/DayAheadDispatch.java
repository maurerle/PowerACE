package results.spot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.storage.PumpStoragePlant;
import markets.trader.spot.hydro.SeasonalStorageTrader;
import results.powerplant.PlotCapacities;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.StateStrategic;
import supply.powerplant.Plant;
import supply.powerplant.capacity.CapacityType;
import tools.logging.Folder;
import tools.logging.LoggerCSV;
import tools.types.FuelName;

/**
 * Logging of day ahead dispatch on a power plant level or aggregated by
 * technology used in the ELMOD model (TU Dresden). Dispatch of all market areas
 * is written in the same file. Different files exist for (1) dispatch of all
 * power plants incl. storages (discharging), (2) dispatch of storages
 * (charging), (3) storage levels
 * 
 * @author CF
 */
public class DayAheadDispatch {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(PlotCapacities.class.getName());
	private int logIDDispatch;
	private int logIDDispatchAggregated;
	private int logIDDispatchProfiles;
	private int logIDDispatchPump;
	private int logIDDispatchPumpAggregated;
	private int logIDDispatchPumpReservoir;
	private int logIDDispatchPumpReservoirAggregated;
	private int logIDDispatchStoredHydro;
	private int logIDDispatchStoredHydroAggregated;
	private int logIDDispatchStoredHydroReservoir;
	private int logIDDispatchStoredHydroReservoirAggregated;

	private String subfolerName = "Dispatch_logfiles";

	private int year;

	private void closeAllFiles() {
		LoggerCSV.close(logIDDispatch);
		LoggerCSV.close(logIDDispatchProfiles);
		LoggerCSV.close(logIDDispatchPump);
		LoggerCSV.close(logIDDispatchPumpReservoir);
		LoggerCSV.close(logIDDispatchStoredHydro);
		LoggerCSV.close(logIDDispatchStoredHydroAggregated);
		LoggerCSV.close(logIDDispatchStoredHydroReservoir);
		LoggerCSV.close(logIDDispatchStoredHydroReservoirAggregated);
	}

	public void logDispatch(PowerMarkets model, int year) {
		try {
			this.year = year;
			logInitializeLog();
			for (final MarketArea marketArea : model.getMarketAreas()) {
				logDispatchDetailed(marketArea);
				logDispatchProfiles(marketArea);
			}
			closeAllFiles();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void logDispatchDetailed(MarketArea marketArea) {

		final List<Plant> units = marketArea.getSupplyData().getPowerPlantsAsList(year,
				Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()));
		final List<PumpStoragePlant> storageUnits = marketArea.getPumpStorage()
				.getAvailablePumpers(year);
		final int hoursOfCurrentYear = Date.getLastHourOfYear(year);

		for (final Plant plant : units) {
			String dataLine = "";
			dataLine = dataLine + plant.getUnitName() + ";";
			if (plant.getUnitName().startsWith("plant")) {
				dataLine += plant.getUnitName() + ";";
			} else {
				dataLine += "Plant_conv_" + plant.getUnitID() + ";";
			}
			dataLine += plant.getFuelName() + ";" + plant.getEnergyConversion() + ";"
					+ marketArea.getInitials() + ";"
					+ String.valueOf(plant.getNetCapacity()
							- plant.getCapacity(CapacityType.NON_USABILITY_EXPECTED, 0)
							- plant.getCapacity(CapacityType.NON_USABILITY_UNEXPECTED, 0))
					+ ";";
			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				dataLine += String.valueOf(plant.getElectricityProductionHourOfYear(hour)) + ";";
			}
			LoggerCSV.writeLine(logIDDispatch, dataLine);
		}

		for (final PumpStoragePlant plant : storageUnits) {
			String dataLine = "";
			dataLine = dataLine + "PSP_" + plant.getName() + ";" + "PSP_" + plant.getUnitID() + ";"
					+ plant.getFuelName() + ";Turbining;" + marketArea.getInitials() + ";"
					+ String.valueOf(plant.getGenerationCapacity()) + ";";
			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				dataLine += String.valueOf(Math.max(-plant.getLongOperation()[hour], 0)) + ";";
			}
			LoggerCSV.writeLine(logIDDispatch, dataLine);

			String dataLinePump = "";
			dataLinePump = dataLinePump + "PSP_" + plant.getName() + ";" + "PSP_"
					+ plant.getUnitID() + ";" + plant.getFuelName() + ";Pumping;"
					+ marketArea.getInitials() + ";" + String.valueOf(plant.getPumpCapacity())
					+ ";";
			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				dataLinePump += String.valueOf(Math.max(plant.getLongOperation()[hour], 0)) + ";";
			}
			LoggerCSV.writeLine(logIDDispatchPump, dataLinePump);

			String dataLinePumpReservoir = "";
			dataLinePumpReservoir = dataLinePumpReservoir + "PSP_Reservoir_" + plant.getName() + ";"
					+ "PSP_Reservoir_" + plant.getName() + ";-;" + "ReservoirLevel;"
					+ marketArea.getInitials() + ";" + String.valueOf(plant.getStorageVolume())
					+ ";";
			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				dataLinePumpReservoir += String
						.valueOf(Math.max(plant.getLongStorageStatus(hour), 0)) + ";";
			}
			LoggerCSV.writeLine(logIDDispatchPumpReservoir, dataLinePumpReservoir);
		}

		if (!marketArea.getSeasonalStorageTraders().isEmpty()) {

			final List<Float> aggregatedHourlyOperation = new ArrayList<>();
			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				aggregatedHourlyOperation.add(0f);
			}
			int numberTrader = 0;
			for (final SeasonalStorageTrader trader : marketArea.getSeasonalStorageTraders()) {
				final float capacity = trader.getAvailableTurbineCapacity();

				for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
					aggregatedHourlyOperation.set(hour, marketArea.getElectricityProduction()
							.getElectricityGeneration(FuelName.HYDRO_SEASONAL_STORAGE, year, hour));
				}

				String dataLine = "";
				dataLine += marketArea.getInitialsUnderscore() + "StoredHydro_" + numberTrader + ";"
						+ marketArea.getInitialsUnderscore() + "StoredHydro_" + numberTrader + ";"
						+ FuelName.HYDRO_SEASONAL_STORAGE + ";" + "Turbine" + ";"
						+ marketArea.getInitials() + ";" + String.valueOf(capacity) + ";";
				for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
					dataLine += String.valueOf(aggregatedHourlyOperation.get(hour)) + ";";
				}
				LoggerCSV.writeLine(logIDDispatch, dataLine);
				numberTrader++;
			}
		}
		/* **************************
		 * RES Curtailment
		 *****************************/

		String dataLineCurtailment = "";
		dataLineCurtailment += marketArea.getInitialsUnderscore() + "RES_Curtailment" + ";"
				+ marketArea.getInitialsUnderscore() + "RES_Curtailment" + ";" + "RES_Curtailment"
				+ ";" + "RES_Curtailment" + ";" + marketArea.getInitials() + ";" + "-;";
		for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
			dataLineCurtailment += String.valueOf(
					marketArea.getBalanceDayAhead().getHourlyCurtailmentRenewables(year, hour))
					+ ";";
		}
		LoggerCSV.writeLine(logIDDispatch, dataLineCurtailment);

	}
	private void logDispatchProfiles(MarketArea marketArea) {
		// RES
		final int hoursOfCurrentYear = Date.getLastHourOfYear(year);
		for (final FuelName renewableType : marketArea.getManagerRenewables().getRenewableTypes()) {
			final Map<Integer, Float> hourlyProfile = marketArea.getManagerRenewables()
					.getRenewableLoadOfYear(renewableType, year);
			String dataLine = "";
			dataLine += renewableType + ";" + marketArea.getInitials() + "_" + renewableType + ";"
					+ renewableType + ";-;" + marketArea.getInitials() + ";";
			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				dataLine += String.valueOf(hourlyProfile.get(hour)) + ";";
			}
			LoggerCSV.writeLine(logIDDispatchProfiles, dataLine);
		}
		// Demand
		final List<Float> hourlyProfile = marketArea.getDemandData().getYearlyDemandList(year);
		String dataLine = "";
		dataLine += "DEMAND;" + marketArea.getInitials() + "_DEMAND" + ";-;-;"
				+ marketArea.getInitials() + ";";
		for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
			dataLine += String.valueOf(hourlyProfile.get(hour)) + ";";
		}
		LoggerCSV.writeLine(logIDDispatchProfiles, dataLine);

		// Reservoir
		String dataLineReservoir = "";
		dataLineReservoir += "HYDRO_RESERVOIR;" + marketArea.getInitials() + "_HYDRO_RESERVOIR"
				+ ";" + FuelName.HYDRO_SEASONAL_STORAGE + ";" + "Turbine;"
				+ marketArea.getInitials() + ";";
		for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
			dataLineReservoir += String
					.valueOf(marketArea.getElectricityProduction()
							.getElectricityGeneration(FuelName.HYDRO_SEASONAL_STORAGE, year, hour))
					+ ";";
		}
		LoggerCSV.writeLine(logIDDispatchProfiles, dataLineReservoir);

		// Exchange Static
		String dataLineExchangeStatic = "";
		dataLineExchangeStatic += "EXCHANGE_STATIC;" + marketArea.getInitials() + "_EXCHANGE_STATIC"
				+ ";" + "EXCHANGE;" + "-;" + marketArea.getInitials() + ";";
		for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
			dataLineExchangeStatic += String
					.valueOf(marketArea.getExchange().getHourlyFlow(year, hour)) + ";";
		}
		LoggerCSV.writeLine(logIDDispatchProfiles, dataLineExchangeStatic);

		// Exchange market coupling
		for (final MarketArea toMarketArea : marketArea.getMarketCouplingOperator()
				.getMarketAreas()) {
			if (!marketArea.equals(toMarketArea)) {
				String dataLineExchangeMarketCoupling = "";
				dataLineExchangeMarketCoupling += "EXCHANGE_MARKET_COUPLING;"
						+ marketArea.getInitials() + "_EXCHANGE_MARKET_COUPLING_TO_"
						+ toMarketArea.getInitials() + ";" + "EXCHANGE;" + "-;"
						+ marketArea.getInitials() + ";";
				for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
					dataLineExchangeMarketCoupling += String
							.valueOf(marketArea.getMarketCouplingOperator().getExchangeFlows()
									.getHourlyFlow(marketArea, toMarketArea, year, hour))
							+ ";";
				}
				LoggerCSV.writeLine(logIDDispatchProfiles, dataLineExchangeMarketCoupling);
			}
		}
	}

	private void logInitializeDispatch() {
		final String fileName = "All_Dispatch_" + year + Settings.LOG_FILE_SUFFIX_CSV;
		final String fileNameAggregated = "All_Dispatch_Aggregated_" + year
				+ Settings.LOG_FILE_SUFFIX_CSV;
		String titleLine = "ID;unique_ID;fuel;technology;c;p_max;";
		String unitLine = "-;-;-;-;-;MWh;";

		for (int i = 0; i < Date.getLastHourOfYear(year); i++) {
			titleLine += "t" + String.valueOf(i + 1) + ";";
			unitLine += "MWh" + ";";
		}

		final String description = "";
		logIDDispatch = LoggerCSV.newLogObject(Folder.MAIN, fileName, description, titleLine,
				unitLine, subfolerName);

		logIDDispatchAggregated = LoggerCSV.newLogObject(Folder.MAIN, fileNameAggregated,
				description, titleLine, unitLine, subfolerName);

	}

	private void logInitializeDispatchProfiles() {
		final String fileName = "All_Profiles_" + year + Settings.LOG_FILE_SUFFIX_CSV;
		String titleLine = "type;unique_ID;fuel;technology;Country;";
		String unitLine = "-;-;-;-;-;";

		for (int i = 0; i < Date.getLastHourOfYear(year); i++) {
			titleLine += "t" + String.valueOf(i + 1) + ";";
			unitLine += "MWh" + ";";
		}

		final String description = "#";
		logIDDispatchProfiles = LoggerCSV.newLogObject(Folder.MAIN, fileName, description,
				titleLine, unitLine, subfolerName);
	}

	private void logInitializeDispatchPump() {
		final String fileName = "All_Pump_" + year + Settings.LOG_FILE_SUFFIX_CSV;
		final String fileNameAggregated = "All_Pump_Aggregated_" + year
				+ Settings.LOG_FILE_SUFFIX_CSV;
		String titleLine = "ID;unique_ID;fuel;technology;c;p_max;";
		String unitLine = "-;-;-;-;-;MW;";

		for (int i = 0; i < Date.getLastHourOfYear(year); i++) {
			titleLine += "t" + String.valueOf(i + 1) + ";";
			unitLine += "MWh" + ";";
		}

		final String description = "#";
		logIDDispatchPump = LoggerCSV.newLogObject(Folder.MAIN, fileName, description, titleLine,
				unitLine, subfolerName);
		logIDDispatchPumpAggregated = LoggerCSV.newLogObject(Folder.MAIN, fileNameAggregated,
				description, titleLine, unitLine, subfolerName);
	}

	private void logInitializeDispatchPumpReservoir() {
		final String fileName = "All_Pump_Reservoir_" + year + Settings.LOG_FILE_SUFFIX_CSV;
		final String fileNameAggregated = "All_Pump_Reservoir_Aggregated_" + year
				+ Settings.LOG_FILE_SUFFIX_CSV;
		String titleLine = "ID;unique_ID;fuel;technology;c;reservoir volume;";
		String unitLine = "-;-;-;-;-;MWh;";

		for (int i = 0; i < Date.getLastHourOfYear(year); i++) {
			titleLine += "t" + String.valueOf(i + 1) + ";";
			unitLine += "MWh" + ";";
		}

		final String description = "#";
		logIDDispatchPumpReservoir = LoggerCSV.newLogObject(Folder.MAIN, fileName, description,
				titleLine, unitLine, subfolerName);
		logIDDispatchPumpReservoirAggregated = LoggerCSV.newLogObject(Folder.MAIN,
				fileNameAggregated, description, titleLine, unitLine, subfolerName);
	}

	private void logInitializeDispatchStoredHydro() {
		final String fileName = "All_Stored_Hydro_" + year + Settings.LOG_FILE_SUFFIX_CSV;
		final String fileNameAggregated = "All_Stored_Hydro_Aggregated_" + year
				+ Settings.LOG_FILE_SUFFIX_CSV;
		String titleLine = "ID;unique_ID;fuel;technolgoy;c;p_max;";
		String unitLine = "-;-;-;-;-;MWh;";
		for (int i = 0; i < Date.getLastHourOfYear(year); i++) {
			titleLine += "t" + String.valueOf(i + 1) + ";";
			unitLine += "MWh" + ";";
		}

		final String description = "#";
		logIDDispatchStoredHydro = LoggerCSV.newLogObject(Folder.MAIN, fileName, description,
				titleLine, unitLine, subfolerName);
		logIDDispatchStoredHydroAggregated = LoggerCSV.newLogObject(Folder.MAIN, fileNameAggregated,
				description, titleLine, unitLine, subfolerName);
	}

	private void logInitializeDispatchStoredHydroReservoir() {
		final String fileName = "All_Stored_Hydro_Reservoir_" + year + Settings.LOG_FILE_SUFFIX_CSV;
		final String fileNameAggregated = "All_Stored_Hydro_Reservoir_Aggregated_" + year
				+ Settings.LOG_FILE_SUFFIX_CSV;
		String titleLine = "ID;unique_ID;fuel;technology;c;reservoir volume;";
		String unitLine = "-;-;-;-;-;MWh;";

		for (int i = 0; i < Date.getLastHourOfYear(year); i++) {
			titleLine += "t" + String.valueOf(i + 1) + ";";
			unitLine += "MWh" + ";";
		}

		final String description = "#";
		logIDDispatchStoredHydroReservoir = LoggerCSV.newLogObject(Folder.MAIN, fileName,
				description, titleLine, unitLine, subfolerName);
		logIDDispatchStoredHydroReservoirAggregated = LoggerCSV.newLogObject(Folder.MAIN,
				fileNameAggregated, description, titleLine, unitLine, subfolerName);
	}

	private void logInitializeLog() {

		logInitializeDispatch();
		logInitializeDispatchPump();
		logInitializeDispatchPumpReservoir();
		logInitializeDispatchStoredHydro();
		logInitializeDispatchStoredHydroReservoir();
		logInitializeDispatchProfiles();
	}
}