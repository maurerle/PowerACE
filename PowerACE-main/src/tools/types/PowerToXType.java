package tools.types;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists the different types of Power to X (Heat, Gas, etc.) demand sources.
 * Each type has a generic name, a corresponding table name in the data base,
 * and an index.
 * 
 * @author Florian Zimmermann
 * @since 2015/02/13
 */
public enum PowerToXType {

	POWER_TO_HEAT_INDUSTRY_COAL(
			"PowerToHeatIndustryCoal",
			1,
			FuelName.COAL),
	POWER_TO_HEAT_HOUSEHOLD_COAL(
			"PowerToHeatHouseholdCoal",
			2,
			FuelName.COAL),
	POWER_TO_HEAT_INDUSTRY_GAS(
			"PowerToHeatIndustryGas",
			1,
			FuelName.GAS),
	POWER_TO_HEAT_HOUSEHOLD_GAS(
			"PowerToHeatHouseholdGas",
			2,
			FuelName.GAS);

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(PowerToXType.class.getName());
	/**
	 * Map to provide reference to Power to X type from Power to X column name
	 */
	private static Map<String, PowerToXType> powerToXColumnNameMapping;

	/** Name of corresponding table in the data base of RenewableType */
	private String tableName;

	/** Index of RenewableType */
	private int index;
	/** FuelType that is substituted by this technology */
	private FuelName fuelName;

	/** Constructor */
	private PowerToXType(String tableName, int index, FuelName fuelName) {
		this.tableName = tableName;
		this.index = index;
		this.fuelName = fuelName;
	}

	private static void initMappingPowerToXTypeColumnName() {
		powerToXColumnNameMapping = new HashMap<>();
		for (final PowerToXType powerToXType : values()) {
			powerToXColumnNameMapping.put(powerToXType.tableName, powerToXType);
		}
	}

	/** Get renewable type from column name */
	public static PowerToXType getRenewableType(String columnName) {
		if (powerToXColumnNameMapping == null) {
			initMappingPowerToXTypeColumnName();
		}
		if (powerToXColumnNameMapping.containsKey(columnName)) {
			return powerToXColumnNameMapping.get(columnName);
		} else {
			logger.warn("Power to X column name " + columnName + " not matched to Power to X type");
			return null;
		}
	}

	/**
	 * @return The index that is assigned to this PowerToXType.
	 */
	public Integer getIndex() {
		return index;
	}

	/**
	 * @return The table name that is used in the database for this
	 *         PowerToXType.
	 */
	public String getTableName() {
		return tableName;
	}

	public FuelName getFuelName() {
		return fuelName;
	}

}
