package results.powerplant;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.PowerMarkets;
import supply.powerplant.Plant;
import tools.database.NameColumnsPowerPlant;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.FuelName;
import tools.types.Unit;

/**
 * Contains data for all market Areas.
 * 
 * @author Florian Zimmermann
 *
 */
public class WritePowerPlantData {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(WritePowerPlantData.class.getName());

	private PowerMarkets model;
	private int logIDCapa;
	public WritePowerPlantData(PowerMarkets model) {
		this.model = model;
	}
	private void logInitialize() {
		final String fileName = "PowerPlantsEnd";
		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader(NameColumnsPowerPlant.UNIT_ID.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader("bna_number", Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.UNIT_NAME.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.LOCATION_NAME.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(
				NameColumnsPowerPlant.NET_INSTALLED_CAPACITY.getColumnName(), Unit.NONE));
		columns.add(new ColumnHeader(
				NameColumnsPowerPlant.GROSS_INSTALLED_CAPACITY.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.OPERATING_LIFETIME_NO_NUCLEAR_PHASEOUT
				.getColumnName(), Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.OWNER_ID.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.FUEL_NAME_INDEX.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.TECHNOLOGY.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.EFFICIENCY.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.MUSTRUN.getColumnName(),
				Unit.NONE));
		columns.add(
				new ColumnHeader(NameColumnsPowerPlant.CHP.getColumnName(), Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.MUSTRUN_CHP.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.UNIT_ZIP_CODE.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader("power_thermal", Unit.NONE));
		columns.add(new ColumnHeader(
				NameColumnsPowerPlant.PRODUCTION_MINIMUM.getColumnName(), Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.AVAILABLE_DATE.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.COUNTRY_REF.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.SHUT_DOWN_DATE.getColumnName(),
				Unit.NONE));

		columns.add(new ColumnHeader(NameColumnsPowerPlant.LATITUDE.getColumnName(),
				Unit.NONE));
		columns.add(new ColumnHeader(NameColumnsPowerPlant.LONGITUDE.getColumnName(),
				Unit.NONE));

		final String description = "#";
		logIDCapa = LoggerXLSX.newLogObject(Folder.MARKET_COUPLING, fileName, description, columns,
				"", Frequency.YEARLY);
	}

	public void write() {
		try {
			logInitialize();
			writePowerPlants();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void writePowerPlants() {
		for (final MarketArea marketArea : model.getMarketAreas()) {
			for (final Plant plant : marketArea.getSupplyData().getPowerPlantsAsList()) {
				final List<Object> dataLine = new ArrayList<>();
				dataLine.add(plant.getUnitID());
				dataLine.add(plant.getBNANumber());
				dataLine.add(plant.getUnitName());
				dataLine.add(plant.getLocName());
				dataLine.add(plant.getNetCapacity());
				dataLine.add(plant.getGrossCapacity());
				dataLine.add(plant.getOperatingLifetime());
				dataLine.add(plant.getOwnerID());
				dataLine.add(FuelName.getFuelIndex(plant.getFuelName()));
				dataLine.add(plant.getEnergyConversionIndex());
				dataLine.add(plant.getEfficiency());
				dataLine.add(plant.isMustrun() ? 1 : 0);
				dataLine.add(plant.isChp() ? 1 : 0);
				dataLine.add(plant.isMustrunChp() ? 1 : 0);
				dataLine.add(plant.getZipCode());
				dataLine.add(plant.getHeatCapacity());
				dataLine.add(plant.getMinProduction());
				dataLine.add(plant.getAvailableDate());
				dataLine.add(marketArea.getInitials());
				// Shutdown at the beginning of the next year
				dataLine.add(
						plant.getShutDownDate().plusYears(1).format(DateTimeFormatter.ISO_DATE));
				dataLine.add(plant.getLatitude());
				dataLine.add(plant.getLongitude());

				LoggerXLSX.writeLine(logIDCapa, dataLine);
			}
		}
		LoggerXLSX.close(logIDCapa);
	}
}