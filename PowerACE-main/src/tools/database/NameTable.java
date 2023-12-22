package tools.database;

import tools.types.Unit;

/**
 * Lists names of used tables<br>
 * <br>
 * Using directly the name of the enum members in the database queries is
 * possible because SQL is case insensitive.
 */
public enum NameTable {
	EXAMPLE(
			"example_table",
			Unit.NONE), 
	EURO_EXCHANGE_RATES(
			"example_table",
			Unit.NONE);

	/** Generic name of table */
	private String tableName;

	/** Generic name of table */
	private Unit unit;

	/** Constructor */
	private NameTable(String tableName, Unit tableUnit) {
		this.tableName = tableName;

		unit = tableUnit;
	}

	public String getTableName() {
		return NameDatabase.getPrefixTable() + tableName;
	}


	public Unit getUnit() {
		return unit;
	}
}
