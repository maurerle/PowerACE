package results.powerplant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.storage.PumpStoragePlant;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.StateStrategic;
import supply.powerplant.Plant;
import supply.powerplant.technique.EnergyConversion;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.Unit;

/**
 * Contains data for all market Areas.
 * 
 * @author Florian Zimmermann, Thorsten Weiskopf
 *
 */
public class PlotCapacities {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(PlotCapacities.class.getName());

	private final Map<FuelType, Map<EnergyConversion, Map<Integer, Float>>> capacityByEnergyConversion = new HashMap<>();
	// Tree Map for equal order in all capacity plots
	private final Map<MarketArea, Map<Integer, Float>> capacityByCountry = new TreeMap<>();
	private final Map<MarketArea, Map<Integer, Float>> capacityByCountryWithRES = new TreeMap<>();
	private final Map<Integer, Float> residualLoad = new TreeMap<>();
	private final Map<FuelName, Map<Integer, Float>> renewableCapacity = new HashMap<>();
	private int logIDCapa = -1;
	private PowerMarkets model;
	private int yearFinal;

	private int logIDCapaPerMarketArea = -1;

	private int logIDCapaInclReserve = -1;

	private int logIDCapaSimple = -1;

	public PlotCapacities(PowerMarkets model, int year) {
		this.model = model;
		yearFinal = year;
	}

	public void log() {
		try {
			logCapaInitialize();
			logSupplyCapaFlexible();
			for (final MarketArea marketArea : model.getMarketAreas()) {
				logSupplyInitializeCapaPerCountry(marketArea);
				logSupplyInitializeCapaSimplePerCountry(marketArea);
				logSupplyCapaPerCountry(marketArea);
				logSupplyCapaSimplePerCountry(marketArea);
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void logCapaInitialize() {
		if (logIDCapa == -1) {
			final int year = Date.getYear();
			final String fileName = "CapacityFuelTotal_" + year;
			final List<ColumnHeader> columns = new ArrayList<>();
			columns.add(new ColumnHeader("Year", Unit.YEAR));
			for (final FuelType fuelType : FuelType.values()) {
				for (final EnergyConversion energyConversion : EnergyConversion.values()) {
					columns.add(new ColumnHeader("" + fuelType + " " + energyConversion,
							Unit.CAPACITY));
				}
			}
			final String description = "#";
			logIDCapa = LoggerXLSX.newLogObject(Folder.MARKET_COUPLING, fileName, description,
					columns, "", Frequency.YEARLY);
		}
	}
	private void logSupplyCapaFlexible() {
		for (int year = Date.getStartYearPlots(); year <= yearFinal; year++) {
			// Add Capacity to Map from Powerplants excl Reserve
			for (final MarketArea marketArea : model.getMarketAreas()) {
				for (final FuelName fuelName : marketArea.getManagerRenewables()
						.getRenewableCapacity().keySet()) {

					if (!renewableCapacity.containsKey(fuelName)) {
						renewableCapacity.put(fuelName, new TreeMap<>());
					}

					if (!renewableCapacity.get(fuelName).containsKey(year)) {
						renewableCapacity.get(fuelName).put(year, 0f);
					}
					renewableCapacity.get(fuelName).put(year,
							renewableCapacity.get(fuelName).get(year)
									+ marketArea.getManagerRenewables().getRenewableCapacity()
											.get(fuelName).get(year));
					if (!capacityByCountryWithRES.containsKey(marketArea)) {
						capacityByCountryWithRES.put(marketArea, new TreeMap<>());
					}
					if (!capacityByCountryWithRES.get(marketArea).containsKey(year)) {
						capacityByCountryWithRES.get(marketArea).put(year, 0f);
					}
					capacityByCountryWithRES.get(marketArea).put(year,
							capacityByCountryWithRES.get(marketArea).get(year)
									+ marketArea.getManagerRenewables().getRenewableCapacity()
											.get(fuelName).get(year));

				}
				for (final Plant plant : marketArea.getSupplyData().getPowerPlantsAsList(year,
						Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()))) {
					if (!capacityByEnergyConversion.containsKey(plant.getFuelType())) {
						capacityByEnergyConversion.put(plant.getFuelType(),
								new HashMap<EnergyConversion, Map<Integer, Float>>());
					}

					if (!capacityByEnergyConversion.get(plant.getFuelType())
							.containsKey(plant.getEnergyConversion())) {
						capacityByEnergyConversion.get(plant.getFuelType())
								.put(plant.getEnergyConversion(), new TreeMap<>());
					}

					if (!capacityByEnergyConversion.get(plant.getFuelType())
							.get(plant.getEnergyConversion()).containsKey(year)) {
						capacityByEnergyConversion.get(plant.getFuelType())
								.get(plant.getEnergyConversion()).put(year, 0f);
					}

					capacityByEnergyConversion.get(plant.getFuelType())
							.get(plant.getEnergyConversion()).put(year,
									capacityByEnergyConversion.get(plant.getFuelType())
											.get(plant.getEnergyConversion()).get(year)
											+ plant.getNetCapacity());
				}

				// Add storage capacity to Map from Powerplants excl Reserve
				for (final PumpStoragePlant storagePlant : marketArea.getPumpStorage()
						.getAvailablePumpers(year)) {
					if (!capacityByEnergyConversion.containsKey(storagePlant.getFuelType())) {
						capacityByEnergyConversion.put(storagePlant.getFuelType(), new HashMap<>());
					}

					if (!capacityByEnergyConversion.get(storagePlant.getFuelType())
							.containsKey(storagePlant.getEnergyConversion())) {
						capacityByEnergyConversion.get(storagePlant.getFuelType())
								.put(storagePlant.getEnergyConversion(), new TreeMap<>());
					}

					if (!capacityByEnergyConversion.get(storagePlant.getFuelType())
							.get(storagePlant.getEnergyConversion()).containsKey(year)) {
						capacityByEnergyConversion.get(storagePlant.getFuelType())
								.get(storagePlant.getEnergyConversion()).put(year, 0f);
					}

					capacityByEnergyConversion.get(storagePlant.getFuelType())
							.get(storagePlant.getEnergyConversion()).put(year,
									capacityByEnergyConversion.get(storagePlant.getFuelType())
											.get(storagePlant.getEnergyConversion()).get(year)
											+ storagePlant.getGenerationCapacity());
				}

				// Add Capacity to Map from Powerplants incl Reserve
				for (final Plant plant : marketArea.getSupplyData().getPowerPlantsAsList(year,
						Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()))) {
					if (!capacityByCountry.containsKey(marketArea)) {
						capacityByCountry.put(marketArea, new TreeMap<>());
					}
					if (!capacityByCountry.get(marketArea).containsKey(year)) {
						capacityByCountry.get(marketArea).put(year, 0f);
					}
					capacityByCountry.get(marketArea).put(year,
							capacityByCountry.get(marketArea).get(year) + plant.getNetCapacity());
				}
				// Add storage capacity to Map from Powerplants excl Reserve
				for (final PumpStoragePlant storagePlant : marketArea.getPumpStorage()
						.getAvailablePumpers(year)) {

					if (!capacityByCountry.containsKey(marketArea)) {
						capacityByCountry.put(marketArea, new TreeMap<>());
					}
					if (!capacityByCountry.get(marketArea).containsKey(year)) {
						capacityByCountry.get(marketArea).put(year, 0f);
					}
					capacityByCountry.get(marketArea).put(year,
							capacityByCountry.get(marketArea).get(year)
									+ storagePlant.getGenerationCapacity());
				}
				if (!residualLoad.containsKey(year)) {
					residualLoad.put(year, 0f);
				}
				residualLoad.put(year, residualLoad.get(year)
						+ marketArea.getManagerRenewables().getRemainingLoadMax(year));

			}
			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(year);
			for (final FuelType fuelType : FuelType.values()) {
				for (final EnergyConversion energyConversion : EnergyConversion.values()) {
					if (capacityByEnergyConversion.containsKey(fuelType)
							&& capacityByEnergyConversion.get(fuelType)
									.containsKey(energyConversion)
							&& capacityByEnergyConversion.get(fuelType).get(energyConversion)
									.containsKey(year)) {
						dataLine.add(capacityByEnergyConversion.get(fuelType).get(energyConversion)
								.get(year));
					} else {
						dataLine.add("0");
					}
				}
			}

			LoggerXLSX.writeLine(logIDCapa, dataLine);
		}
		LoggerXLSX.close(logIDCapa);

	}
	private void logSupplyCapaPerCountry(MarketArea marketArea) {
		final Map<FuelType, Map<EnergyConversion, Map<Integer, Float>>> capacityByEnergyConversion = new HashMap<>();

		final Map<Integer, Float> residualLoad = new HashMap<>();

		final Map<FuelType, Map<EnergyConversion, Map<Integer, Float>>> capacityByEnergyConversionInclReserve = new HashMap<>();

		for (int year = Date.getStartYearPlots(); year <= yearFinal; year++) {
			// Add Capacity to Map from Powerplants excl Reserve
			for (final Plant plant : marketArea.getSupplyData().getPowerPlantsAsList(year,
					Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()))) {
				if (!capacityByEnergyConversion.containsKey(plant.getFuelType())) {
					capacityByEnergyConversion.put(plant.getFuelType(),
							new HashMap<EnergyConversion, Map<Integer, Float>>());
				}

				if (!capacityByEnergyConversion.get(plant.getFuelType())
						.containsKey(plant.getEnergyConversion())) {
					capacityByEnergyConversion.get(plant.getFuelType())
							.put(plant.getEnergyConversion(), new TreeMap<>());
				}

				if (!capacityByEnergyConversion.get(plant.getFuelType())
						.get(plant.getEnergyConversion()).containsKey(year)) {
					capacityByEnergyConversion.get(plant.getFuelType())
							.get(plant.getEnergyConversion()).put(year, 0f);
				}

				capacityByEnergyConversion.get(plant.getFuelType()).get(plant.getEnergyConversion())
						.put(year,
								capacityByEnergyConversion.get(plant.getFuelType())
										.get(plant.getEnergyConversion()).get(year)
										+ plant.getNetCapacity());
			}

			// Add storage capacity to Map from Powerplants incl Reserve
			for (final PumpStoragePlant storagePlant : marketArea.getPumpStorage()
					.getAvailablePumpers(year)) {
				if (!capacityByEnergyConversion.containsKey(storagePlant.getFuelType())) {
					capacityByEnergyConversion.put(storagePlant.getFuelType(),
							new HashMap<EnergyConversion, Map<Integer, Float>>());
				}

				if (!capacityByEnergyConversion.get(storagePlant.getFuelType())
						.containsKey(storagePlant.getEnergyConversion())) {
					capacityByEnergyConversion.get(storagePlant.getFuelType())
							.put(storagePlant.getEnergyConversion(), new TreeMap<>());
				}

				if (!capacityByEnergyConversion.get(storagePlant.getFuelType())
						.get(storagePlant.getEnergyConversion()).containsKey(year)) {
					capacityByEnergyConversion.get(storagePlant.getFuelType())
							.get(storagePlant.getEnergyConversion()).put(year, 0f);
				}

				capacityByEnergyConversion.get(storagePlant.getFuelType())
						.get(storagePlant.getEnergyConversion()).put(year,
								capacityByEnergyConversion.get(storagePlant.getFuelType())
										.get(storagePlant.getEnergyConversion()).get(year)
										+ storagePlant.getGenerationCapacity());
			}

			// Add Capacity to Map from Powerplants incl Reserve
			for (final Plant plant : marketArea.getSupplyData().getPowerPlantsAsList(year,
					Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()))) {
				if (!capacityByEnergyConversionInclReserve.containsKey(plant.getFuelType())) {
					capacityByEnergyConversionInclReserve.put(plant.getFuelType(),
							new HashMap<EnergyConversion, Map<Integer, Float>>());
				}

				if (!capacityByEnergyConversionInclReserve.get(plant.getFuelType())
						.containsKey(plant.getEnergyConversion())) {
					capacityByEnergyConversionInclReserve.get(plant.getFuelType())
							.put(plant.getEnergyConversion(), new TreeMap<>());
				}

				if (!capacityByEnergyConversionInclReserve.get(plant.getFuelType())
						.get(plant.getEnergyConversion()).containsKey(year)) {
					capacityByEnergyConversionInclReserve.get(plant.getFuelType())
							.get(plant.getEnergyConversion()).put(year, 0f);
				}

				capacityByEnergyConversionInclReserve.get(plant.getFuelType())
						.get(plant.getEnergyConversion()).put(year,
								capacityByEnergyConversionInclReserve.get(plant.getFuelType())
										.get(plant.getEnergyConversion()).get(year)
										+ plant.getNetCapacity());
			}

			// Add storage capacity to Map from Powerplants incl Reserve
			for (final PumpStoragePlant storagePlant : marketArea.getPumpStorage()
					.getAvailablePumpers(year)) {
				if (!capacityByEnergyConversionInclReserve
						.containsKey(storagePlant.getFuelType())) {
					capacityByEnergyConversionInclReserve.put(storagePlant.getFuelType(),
							new HashMap<EnergyConversion, Map<Integer, Float>>());
				}

				if (!capacityByEnergyConversionInclReserve.get(storagePlant.getFuelType())
						.containsKey(storagePlant.getEnergyConversion())) {
					capacityByEnergyConversionInclReserve.get(storagePlant.getFuelType())
							.put(storagePlant.getEnergyConversion(), new TreeMap<>());
				}

				if (!capacityByEnergyConversionInclReserve.get(storagePlant.getFuelType())
						.get(storagePlant.getEnergyConversion()).containsKey(year)) {
					capacityByEnergyConversionInclReserve.get(storagePlant.getFuelType())
							.get(storagePlant.getEnergyConversion()).put(year, 0f);
				}

				capacityByEnergyConversionInclReserve.get(storagePlant.getFuelType())
						.get(storagePlant.getEnergyConversion()).put(year,
								capacityByEnergyConversionInclReserve
										.get(storagePlant.getFuelType())
										.get(storagePlant.getEnergyConversion()).get(year)
										+ storagePlant.getGenerationCapacity());
			}

			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(year);

			if (residualLoad.isEmpty()) {
				for (int yearResidualLoad = Date.getStartYear(); yearResidualLoad <= Date
						.getLastYear(); yearResidualLoad++) {
					residualLoad.put(year, marketArea.getManagerRenewables()
							.getRemainingLoadMax(yearResidualLoad));
				}
			}
			for (final FuelType fuelType : FuelType.values()) {
				for (final EnergyConversion energyConversion : EnergyConversion.values()) {
					if (capacityByEnergyConversion.containsKey(fuelType)
							&& capacityByEnergyConversion.get(fuelType)
									.containsKey(energyConversion)
							&& capacityByEnergyConversion.get(fuelType).get(energyConversion)
									.containsKey(year)) {
						dataLine.add(capacityByEnergyConversion.get(fuelType).get(energyConversion)
								.get(year));
					} else {
						dataLine.add("0");
					}
				}
			}

			LoggerXLSX.writeLine(logIDCapaPerMarketArea, dataLine);

			// Log Capacity incl. Reserves
			final List<Object> dataLineInclReserve = new ArrayList<>();
			dataLineInclReserve.add(year);

			for (final FuelType fuelType : FuelType.values()) {
				for (final EnergyConversion energyConversion : EnergyConversion.values()) {
					if (capacityByEnergyConversion.containsKey(fuelType)
							&& capacityByEnergyConversion.get(fuelType)
									.containsKey(energyConversion)
							&& capacityByEnergyConversion.get(fuelType).get(energyConversion)
									.containsKey(year)) {
						dataLineInclReserve.add(capacityByEnergyConversion.get(fuelType)
								.get(energyConversion).get(year));
					} else {
						dataLineInclReserve.add("0");
					}
				}
			}

			LoggerXLSX.writeLine(logIDCapaInclReserve, dataLineInclReserve);
		}

		LoggerXLSX.close(logIDCapaPerMarketArea);
		logIDCapaPerMarketArea = -1;
		LoggerXLSX.close(logIDCapaInclReserve);
		logIDCapaInclReserve = -1;

	}

	private void logSupplyCapaSimplePerCountry(MarketArea marketArea) {
		for (int year = Date.getStartYearPlots(); year <= yearFinal; year++) {
			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(year);
			// Market Area
			dataLine.add(marketArea);
			// scenario
			dataLine.add(Settings.getMultiRunName());
			final Map<FuelType, Map<Integer, Float>> capacityByFuelType = new HashMap<>();
			// Add Capacity to Map from Powerplants excl Reserve
			for (final Plant plant : marketArea.getSupplyData().getPowerPlantsAsList(year,
					Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()))) {
				if (plant.getFuelType() == FuelType.WATER) {
					plant.getEnergyConversion();
				}

				if (!capacityByFuelType.containsKey(plant.getFuelType())) {
					capacityByFuelType.put(plant.getFuelType(), new TreeMap<>());

				}

				if (!capacityByFuelType.get(plant.getFuelType()).containsKey(year)) {
					capacityByFuelType.get(plant.getFuelType()).put(year, 0f);
				}

				capacityByFuelType.get(plant.getFuelType()).put(year,
						capacityByFuelType.get(plant.getFuelType()).get(year)
								+ plant.getNetCapacity());
			}
			// Add Renewables
			for (final FuelName fuelName : marketArea.getManagerRenewables().getRenewableCapacity()
					.keySet()) {
				final FuelType fueltype = fuelName.getFuelType();

				if (!capacityByFuelType.containsKey(fueltype)) {
					capacityByFuelType.put(fueltype, new TreeMap<>());
				}

				if (!capacityByFuelType.get(fueltype).containsKey(year)) {
					capacityByFuelType.get(fueltype).put(year, 0f);
				}
				capacityByFuelType.get(fueltype).put(year,
						capacityByFuelType.get(fueltype).get(year)
								+ marketArea.getManagerRenewables().getRenewableCapacity()
										.get(fuelName).get(year));

			}
			// Add Storages
			for (final PumpStoragePlant storagePlant : marketArea.getPumpStorage()
					.getAvailablePumpers(year)) {
				if (storagePlant.getFuelType() == FuelType.WATER) {
					storagePlant.getEnergyConversion();
				}

				if (!capacityByFuelType.containsKey(storagePlant.getFuelType())) {
					capacityByFuelType.put(storagePlant.getFuelType(), new TreeMap<>());
				}

				if (!capacityByFuelType.get(storagePlant.getFuelType()).containsKey(year)) {
					capacityByFuelType.get(storagePlant.getFuelType()).put(year, 0f);
				}
				capacityByFuelType.get(storagePlant.getFuelType()).put(year,
						capacityByFuelType.get(storagePlant.getFuelType()).get(year)
								+ storagePlant.getGenerationCapacity());

			}

			for (final FuelType fuelType : FuelType.values()) {
				if (capacityByFuelType.containsKey(fuelType)
						&& capacityByFuelType.get(fuelType).containsKey(year)) {
					dataLine.add(capacityByFuelType.get(fuelType).get(year));
				} else {
					dataLine.add("0");
				}

			}

			LoggerXLSX.writeLine(logIDCapaSimple, dataLine);
		}
		LoggerXLSX.close(logIDCapaSimple);
		logIDCapaSimple = -1;
	}

	private void logSupplyInitializeCapaPerCountry(MarketArea marketArea) {
		final int year = Date.getYear();
		if (logIDCapaPerMarketArea == -1) {

			final String fileName = marketArea.getInitialsUnderscore() + "CapacityFuel_" + year
					+ "_";
			final List<ColumnHeader> columns = new ArrayList<>();
			columns.add(new ColumnHeader("Year", Unit.YEAR));
			for (final FuelType fuelType : FuelType.values()) {
				for (final EnergyConversion energyConversion : EnergyConversion.values()) {
					columns.add(new ColumnHeader("" + fuelType + " " + energyConversion,
							Unit.CAPACITY));
				}
			}
			final String description = "#";
			logIDCapaPerMarketArea = LoggerXLSX.newLogObject(Folder.SUPPLY, fileName, description,
					columns, marketArea.getIdentityAndNameLong(), Frequency.YEARLY);
		}

		if (logIDCapaInclReserve == -1) {

			final String fileName = marketArea.getInitialsUnderscore() + "CapacityFuelInclReserves_"
					+ year + "_";
			final List<ColumnHeader> columns = new ArrayList<>();
			columns.add(new ColumnHeader("Year", Unit.YEAR));
			for (final FuelType fuelType : FuelType.values()) {
				for (final EnergyConversion energyConversion : EnergyConversion.values()) {
					columns.add(new ColumnHeader("" + fuelType + " " + energyConversion,
							Unit.CAPACITY));
				}
			}
			final String description = "# Capacity with strategic reserve";
			logIDCapaInclReserve = LoggerXLSX.newLogObject(Folder.SUPPLY, fileName, description,
					columns, marketArea.getIdentityAndNameLong(), Frequency.YEARLY);
		}

	}

	private void logSupplyInitializeCapaSimplePerCountry(MarketArea marketArea) {
		if (logIDCapaSimple == -1) {
			final int year = Date.getYear();
			final String fileName = marketArea.getInitialsUnderscore() + "CapacityFuelSimple_"
					+ year + "_";
			final List<ColumnHeader> columns = new ArrayList<>();
			columns.add(new ColumnHeader("Year", Unit.YEAR));
			columns.add(new ColumnHeader("Market Area", Unit.NONE));
			columns.add(new ColumnHeader("Scenario", Unit.NONE));
			for (final FuelType fuelType : FuelType.values()) {
				columns.add(new ColumnHeader("" + fuelType, Unit.CAPACITY));
			}
			final String description = "# Generation capacities by fueltype incl. storages and renewables, excl. Reserves";
			logIDCapaSimple = LoggerXLSX.newLogObject(Folder.SUPPLY, fileName, description, columns,
					marketArea.getIdentityAndNameLong(), Frequency.YEARLY);
		}
	}

}