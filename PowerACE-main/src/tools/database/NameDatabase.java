package tools.database;

/**
 * Lists the database names used.<br>
 * <br>
 * Market area-specific data of all market areas except Germany is stored in
 * merged databases grouped by category. These categories (e.g. exchange, power
 * plants) are listed in this enumeration.<br>
 * <br>
 * Using directly the name of the enum members in the database queries is
 * possible because SQL is case insensitive.
 */
public enum NameDatabase {
	NAME_OF_DATABASED(
			"name_of_database"),
	INFORMATION_SCHEMA(
			"information_schema");

	/** Prefix for accessing the databases */
	private final static String PREFIX_DATABASE = "prefix_";

	/** Prefix for tables in the databases */
	private final static String PREFIX_TABLE = "tbl_";

	/**
	 * Get the database name (prefix + category) for data.
	 * 
	 */
	public static String getDatabaseName(NameDatabase database) {
		return (PREFIX_DATABASE + database.databaseName);
	}

	public static String getPrefixDatabase() {
		return PREFIX_DATABASE;
	}
	public static String getPrefixTable() {
		return PREFIX_TABLE;
	}

	/** Generic name of database for server */
	private String databaseName;

	/** Constructor */
	private NameDatabase(String databaseName) {

		this.databaseName = databaseName;
	}

}