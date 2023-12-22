package tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.storage.PumpStoragePlant;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.Investor;
import supply.invest.State;
import supply.invest.StateStrategic;
import supply.powerplant.Plant;
import supply.powerplant.PlantOption;
import tools.database.NameColumnsPowerPlant;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.FuelName;

/**
 * Operations on power plants
 */
public final class OperationsPowerPlants {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(OperationsPowerPlants.class.getName());

	// Needed to randomly assign initial storage levels
	private final static Random random = new Random(Settings.getRandomNumberSeed());

	public static PumpStoragePlant createNewStorageUnit(MarketArea marketArea, PlantOption myBlock,
			float unitSize, Investor owner, int timelag) {
		// Create new storage unit (new instance of StoragePlant object)
		return OperationsPowerPlants.convertInvestmentInStoragePlantObject(marketArea, myBlock,
				owner, timelag, unitSize);
	}

	/**
	 * Writes new generation unit (<code>myBlock</code>) of specified
	 * <code>owner</code> and with <code>timelag</code> in database.
	 *
	 * @param marketArea
	 * @param myBlock
	 *            : New generation unit
	 * @param owner
	 *            : Owner of new unit
	 * @param timelag
	 *            : This lag (in years) should reflect construction time and is
	 *            added to the current year which determines the value of the IN
	 *            column in the table.
	 * @param unitSize
	 *            : Define individual size of the generation unit.
	 * @return Plant object of new PowerPlant
	 *
	 */
	public static Plant createNewSupplyUnit(MarketArea marketArea, PlantOption myBlock,
			float unitSize, Investor owner, int timelag) {
		// Create new supply unit (new instance of Plant object)
		return OperationsPowerPlants.convertInvestmentInPlantObject(marketArea, myBlock, owner,
				timelag, unitSize);
	}

	/** Export power plants in Excel at the end of the simulation */
	public static void exportPowerPlants(MarketArea marketArea) {
		final String fileName = marketArea.getInitialsUnderscore() + "PowerPlants_All";
		final String description = "This file contains all power plants of the market area at the end of the Simulation";

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader(NameColumnsPowerPlant.UNIT_ID));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.LOCATION_NAME));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.UNIT_NAME));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.OWNER_ID));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.NET_INSTALLED_CAPACITY));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.EFFICIENCY));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.START_YEAR));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.FUEL_NAME_INDEX));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.TECHNOLOGY));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.OPERATING_LIFETIME));

		final int logID = LoggerXLSX.newLogObject(Folder.GENERATOR, fileName, description, columns,
				marketArea.getIdentityAndNameLong(), Frequency.SIMULATION);
		final Map<String, List<Plant>> allPowerPlants = marketArea.getSupplyData().getPowerPlants(
				Date.getYear(), Stream.of(StateStrategic.OPERATING, StateStrategic.DECOMMISSIONED,
						StateStrategic.MOTHBALLED).collect(Collectors.toSet()));

		for (final String ownerName : allPowerPlants.keySet()) {
			for (final Plant plant : allPowerPlants.get(ownerName)) {
				final List<Object> dataLine = new ArrayList<>();
				dataLine.add(plant.getUnitID());
				dataLine.add(plant.getLocName());
				dataLine.add(plant.getName());
				dataLine.add(plant.getOwnerID());
				dataLine.add(plant.getNetCapacity());
				dataLine.add(plant.getEfficiency());
				dataLine.add(plant.getAvailableYear());
				dataLine.add(FuelName.getFuelIndex(plant.getFuelName()));
				dataLine.add(plant.getEnergyConversionIndex());
				dataLine.add(plant.getOperatingLifetime());
				LoggerXLSX.writeLine(logID, dataLine);
			}
		}

		for (final PumpStoragePlant storagePlant : marketArea.getPumpStorage()
				.getAvailablePumpers()) {
			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(storagePlant.getUnitID());
			dataLine.add("");
			dataLine.add(storagePlant.getName());
			dataLine.add(storagePlant.getOwnerID());
			dataLine.add(storagePlant.getAvailableCapacity());
			dataLine.add(storagePlant.getEfficiency());
			dataLine.add(storagePlant.getAvailability());
			dataLine.add("");
			dataLine.add("");
			dataLine.add(storagePlant.getOperatingLifetime());
			LoggerXLSX.writeLine(logID, dataLine);
		}
		LoggerXLSX.close(logID);
	}

	private static Plant convertInvestmentInPlantObject(MarketArea marketArea, PlantOption myBlock,
			Investor owner, int timelag, float unitSize) {

		// "Convert" PlantOption into new Plant object
		final Plant newPlant = new Plant(marketArea);
		// Year of commissioning
		// Plus 1 because the available capacity options are taken from the
		// following year. Therefore, without all investments come one year too
		// early
		final int yearOfCommissioning = Date.getYear() + 1 + timelag
				+ myBlock.getConstructionTime();

		// Create new unique unit ID (Blocknummer)
		final int newUnitID = marketArea.getSupplyData().getNewUnitID();

		newPlant.setUnitID(newUnitID);
		// in order to track plant
		myBlock.setUnitID(newUnitID);
		newPlant.setOwnerID(owner.getID() + 1);
		newPlant.setOwnerName(owner.getName());
		newPlant.setNetCapacity(unitSize);
		newPlant.setEfficiency(myBlock.getEfficiency());
		newPlant.setAvailableDate(yearOfCommissioning);
		newPlant.setOperatingLifetime(myBlock.getOperatingLifetime());
		newPlant.setShutDownDate(newPlant.getAvailableYear() + newPlant.getOperatingLifetime());
		newPlant.setFuelName(myBlock.getFuelName());
		newPlant.setNetPresentValue(myBlock.getNetPresentValue());
		newPlant.setInvestmentPayment(myBlock.getInvestmentPayment());
		newPlant.setEnergyConversionIndex(myBlock.getEnergyConversionIndex());
		newPlant.setEnergyConversion(myBlock.getEnergyConversion());
		newPlant.setCostsOperationMaintenanceVar(myBlock.getCostsOperationMaintenanceVar());
		newPlant.setCostsOperationMaintenanceFixed(myBlock.getCostsOperationMaintenanceFixed());
		newPlant.setConstructionTime(myBlock.getConstructionTime());
		supply.powerplant.technique.Type.determinePowerPlantCategory(newPlant);

		newPlant.setLocationName("plant0" + newUnitID);
		newPlant.setUnitName(myBlock.getName());

		State.setStatesStrategicInitial(newPlant, StateStrategic.OPERATING);

		// Add new plant to map in SupplyData
		marketArea.getSupplyData().addNewPowerPlant(owner.getName(), newPlant);

		// Log investment
		marketArea.getInvestmentLogger().addNewInvestment(owner, yearOfCommissioning, myBlock,
				unitSize);
		logger.info("Build id " + newUnitID + ", fuel " + myBlock.getFuelName() + ", capacity "
				+ myBlock.getNetCapacity() + " by owner " + owner);
		return newPlant;
	}

	private static PumpStoragePlant convertInvestmentInStoragePlantObject(MarketArea marketArea,
			PlantOption myBlock, Investor owner, int timelag, float unitSize) {

		// "Convert" PlantOption into new Plant object
		final PumpStoragePlant newPlant = new PumpStoragePlant();
		// Create new unique unit ID (Blocknummer)
		final int newUnitID = marketArea.getSupplyData().getNewUnitID();

		final int yearOfCommissioning = Date.getYear() + 1 + timelag
				+ myBlock.getConstructionTime();
		newPlant.setUnitID(newUnitID);
		// in order to track plant
		myBlock.setUnitID(newUnitID);

		newPlant.setName(myBlock.getName());
		newPlant.setOwnerID(owner.getID() + 1);
		newPlant.setOwnerName(owner.getName());
		newPlant.setPumpCapacity(unitSize);
		newPlant.setGenerationCapacity(unitSize);
		newPlant.setAvailableCapacity(newPlant.getGenerationCapacity());
		newPlant.setEfficiency(myBlock.getEfficiency());
		newPlant.setChargeEfficiency((float) Math.sqrt(newPlant.getEfficiency()));
		newPlant.setGenerationEfficiency((float) Math.sqrt(newPlant.getEfficiency()));
		newPlant.setStorageVolume(
				(myBlock.getStorageVolume() * unitSize) / myBlock.getDischargeCapacity());

		newPlant.setStorageStatus(random.nextFloat() * newPlant.getStorageVolume());
		newPlant.setStorageInflow(0f);
		newPlant.setAvailableDate((yearOfCommissioning));
		newPlant.setOperatingLifetime(myBlock.getOperatingLifetime());
		newPlant.setShutDownDate(newPlant.getAvailableYear() + newPlant.getOperatingLifetime());
		newPlant.setFuelName(myBlock.getFuelName());
		newPlant.setNetPresentValue(myBlock.getNetPresentValue());
		newPlant.setInvestmentPayment(myBlock.getInvestmentPayment());

		newPlant.setEnergyConversionIndex(myBlock.getEnergyConversionIndex());
		newPlant.setEnergyConversion(myBlock.getEnergyConversion());

		newPlant.setCostsOperationMaintenanceFixed(myBlock.getCostsOperationMaintenanceFixed());
		newPlant.setConstructionTime(myBlock.getConstructionTime());

		// Add new plant to map in PumpStorage
		marketArea.getPumpStorage().addNewPumper(owner.getName(), newPlant);

		// Log investment
		marketArea.getInvestmentLogger().addNewInvestment(owner, yearOfCommissioning, myBlock,
				unitSize);
		logger.info("Build id " + newUnitID + ", fuel " + myBlock.getFuelName() + ", capacity "
				+ unitSize + " by owner " + owner);
		return newPlant;
	}
}