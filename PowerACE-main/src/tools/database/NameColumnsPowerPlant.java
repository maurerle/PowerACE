package tools.database;

import tools.types.Unit;

/**
 * Lists the names of columns in the tables of the power plant database
 * <p>
 * Using directly the name of the enum members in the database queries is
 * possible because SQL is case insensitive.
 */
public enum NameColumnsPowerPlant {
	AVAILABILITY(
			"availability",
			null,
			Unit.NONE),
	AVAILABLE_DATE(
			"AvailableDate",
			"construction_year",
			Unit.NONE),
	COUNTRY_REF(
			null,
			"country_ref",
			Unit.NONE),
	CHP(
			null,
			"chp",
			Unit.NONE),
	MUSTRUN_CHP(
			null,
			"mustrun_chp",
			Unit.NONE),
	MUSTRUN(
			null,
			"mustrun",
			Unit.NONE),
	DEACTIVATED(
			"deactivated",
			"deactivated",
			Unit.NONE),
	EFFICIENCY(
			"Wirkungsgrad",
			"efficiency",
			Unit.NONE),
	FUEL_COSTS(
			"fuelCosts",
			null,
			Unit.ENERGY_PRICE),
	FUEL_NAME_INDEX(
			"fuel",
			"fuel_ref",
			Unit.NONE),
	GROSS_INSTALLED_CAPACITY(
			null,
			"power_gross",
			Unit.CAPACITY),
	LOCATION_NAME(
			"StandortName",
			"location",
			Unit.NONE),

	LATITUDE(
			"lat",
			"lat",
			Unit.NONE),
	LONGITUDE(
			"lon",
			"lon",
			Unit.NONE),
	NET_INSTALLED_CAPACITY(
			"Nettoleistung",
			"power_net",
			Unit.CAPACITY),
	OPERATING_LIFETIME(
			"tech_use_nuclearPhaseOut",
			"lifetime",
			Unit.YEAR),
	OPERATING_LIFETIME_NO_NUCLEAR_PHASEOUT(
			"tech_use",
			"lifetime",
			Unit.YEAR),
	OWNER_ID(
			"owner1",
			"owner_ref",
			Unit.NONE),
	PRODUCTION_MINIMUM(
			null,
			"power_min",
			Unit.CAPACITY),
	SHUT_DOWN_DATE(
			"ShutDownDate",
			"shut_down",
			Unit.NONE),
	START_YEAR(
			"In",
			"construction_year",
			Unit.YEAR),
	SUM_NET_INSTALLED_CAPACITY(
			"SumNet",
			null,
			Unit.CAPACITY),
	TECHNOLOGY(
			"tecnologyIndex",
			"technology_ref",
			Unit.NONE),
	UNIT_ID(
			"Blocknummer",
			"wepp_unit_id",
			Unit.NONE),
	UNIT_NAME(
			"EinheitName",
			"block_name",
			Unit.NONE),
	UNIT_ZIP_CODE(
			"plz",
			"postal_code",
			Unit.NONE);

	/** Name of column in database table */
	private String columnName;

	/** Unit of column in database table */
	private Unit columnUnit;

	/** Constructor */
	private NameColumnsPowerPlant(String columnName, String columnNameInstitute, Unit columnUnit) {
		this.columnName = columnName;
		this.columnUnit = columnUnit;
	}

	public String getColumnName() {
		return columnName;
	}

	public Unit getColumnUnit() {
		return columnUnit;
	}
}