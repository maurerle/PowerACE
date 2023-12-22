package tools.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import simulations.MarketArea;

/**
 * This class provides an easy method to connect to the MySQL Server. It uses a
 * connection pool (Hikari) in order to speed up the connections.
 *
 * @author
 * @version 1.2
 * @since 2011-10-20 .
 *
 */
public final class ConnectionSQL implements AutoCloseable {

	/** The time out for the connection [ms]. */
	private static final long CONNECTION_TIMEOUT = 60000;
	/** The data source (new). */
	private static HikariDataSource dataSource;

	/** The Constant HOST_NAME_DB. */
	private static final String HOST_NAME = "DatabaseHostName";
	/** The time for the leak detection [ms]. */
	private static final long LEAK_DETECTION_TIMEOUT = 30000;
	/**
	 * Instance of logger to give out warnings, errors to console and or files.
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(ConnectionSQL.class.getName());
	/** The password for the server. */
	private static final String PASSWORD = "password";
	/** Maximum size of the connection pool */
	private static final int POOL_SIZE_MAXIMUM = 4;
	/** The connection port. */
	private static final String PORT = "0000";

	/** The Constant USER. */
	private static final String USER = "Username";

	public static void closeDataSource() {
		logger.info("Try to close datasource.");
		if (dataSource != null) {
			dataSource.close();
		}
		logger.info("Pool closed");
	}

	/**
	 * If no connection is available wait and request timeout.
	 * 
	 * @param accessInstituteDatabase
	 * @return
	 * @throws SQLException
	 */
	private static Connection getConnectionWorking() throws SQLException {

		ConnectionSQL.initialize();

		return dataSource.getConnection();

	}

	/**
	 * Initialize the connection pools.
	 *
	 * @param accessInstituteDatabase
	 */
	// Must be sychronized, because if threads call parallel initialize more
	// connections pools will be open than necessary
	private synchronized static void initialize() {

		// Pool for Database
		if ((dataSource == null) || dataSource.isClosed()) {
			final HikariConfig config = new HikariConfig();
			config.setConnectionTimeout(CONNECTION_TIMEOUT);
			config.setLeakDetectionThreshold(LEAK_DETECTION_TIMEOUT);
			config.setJdbcUrl("jdbc:mysql://" + HOST_NAME);
			config.setDriverClassName("com.mysql.cj.jdbc.Driver");
			config.addDataSourceProperty("serverTimezone", "Europe/Paris");
			config.addDataSourceProperty("serverName", HOST_NAME);
			config.addDataSourceProperty("port", PORT);
			config.setUsername(USER);
			config.setMaximumPoolSize(POOL_SIZE_MAXIMUM);
			config.setPassword(PASSWORD);

			// MySQL optimizations, see
			// https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
			config.addDataSourceProperty("cachePrepStmts", true);
			config.addDataSourceProperty("prepStmtCacheSize", 25);
			config.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
			config.addDataSourceProperty("useServerPrepStmts", true);
			config.addDataSourceProperty("useLocalSessionState", true);
			config.addDataSourceProperty("useLocalTransactionState", true);
			config.addDataSourceProperty("rewriteBatchedStatements", true);
			config.addDataSourceProperty("cacheResultSetMetadata", true);
			config.addDataSourceProperty("cacheServerConfiguration", true);
			config.addDataSourceProperty("elideSetAutoCommits", true);
			config.addDataSourceProperty("maintainTimeStats", false);
			config.addDataSourceProperty("sslMode", "DISABLED");
			config.addDataSourceProperty("allowPublicKeyRetrieval", false);
			dataSource = new HikariDataSource(config);
		}

	}

	/**
	 * Checks whether specified column exists in given table.
	 *
	 * @param nameDatabase
	 *            the name database
	 * @param tableName
	 *            Name of table
	 * @param columnName
	 *            Name of column
	 * @param marketArea
	 *            the market area
	 * @param accessInstituteDatabase
	 *            the access institute database
	 * @return @return <code>True</code> whether table with specified name
	 *         exists, <code>false</code> if not.
	 */
	public static boolean isColumnExisting(NameDatabase nameDatabase, String tableName,
			String columnName, MarketArea marketArea) {
		boolean tableExists = false;
		String database;
		database = NameDatabase.getDatabaseName(nameDatabase);

		final String query = "SELECT * FROM COLUMNS WHERE TABLE_SCHEMA = '" + database
				+ "' AND TABLE_NAME = '" + tableName + "' AND COLUMN_NAME = '" + columnName + "'";
		try (final ConnectionSQL conn = new ConnectionSQL(NameDatabase.INFORMATION_SCHEMA)) {
			conn.setResultSet(query);
			if (conn.getResultSet().first()) {
				tableExists = true;
			} else {
				tableExists = false;
			}
		} catch (final SQLException e) {
			logger.error(query, e);
		}
		return tableExists;
	}

	/**
	 * Checks whether specified table exists. Only for Institute DB
	 *
	 * @param conn
	 *            SQL connection
	 * @param nameDatabase
	 *            the name database
	 * @param tableName
	 *            Name of table
	 * @param marketArea
	 *            the market area
	 * @param accessInstituteDatabase
	 *            the access institute database
	 * @return <code>True</code> whether table with specified name exists,
	 *         <code>false</code> if not.
	 * @throws SQLException
	 */
	public static boolean isTableExisting(NameDatabase nameDatabase, String tableName) {
		try (ConnectionSQL conn = new ConnectionSQL(nameDatabase)) {
			final String database = NameDatabase.getDatabaseName(nameDatabase);
			final String query = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '"
					+ database + "' AND TABLE_NAME = '" + tableName + "'";
			conn.setResultSet(query);
			if (conn.resultSet.first()) {
				return true;
			} else {
				return false;
			}
		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		}
		return false;
	}

	/** The connection. */
	private final Connection connection;

	/**
	 * The result set that is visible for easier access.
	 */
	private ResultSet resultSet;

	/** The statement. */
	private final Statement statement;

	/**
	 * Creates a new object with access to the given <code>database</code> on
	 * the Institute or old Database.
	 *
	 * @param database
	 *            the database
	 * @param accessInstituteDatabase
	 *            the access institute database
	 * @throws SQLException
	 *             the SQL exception
	 */
	public ConnectionSQL(NameDatabase database) throws SQLException {

		connection = getConnectionWorking();

		statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_UPDATABLE);
		// Database information_schema in Institute Database has no prefix,
		// so use just the name for reading
		if (database == NameDatabase.INFORMATION_SCHEMA) {
			statement.execute("USE `" + database + "`");
		} else {
			statement.execute(
					"USE `" + String.valueOf(NameDatabase.getDatabaseName(database)) + "`");
		}

	}

	/**
	 * Creates a new object with access to the given <code>database</code> on
	 * the Institute or old Database.
	 *
	 * @param database
	 *            the database
	 * @param marketArea
	 *            the market area
	 * @param accessInstituteDatabase
	 *            the access institute database
	 * @throws SQLException
	 *             the SQL exception
	 */
	public ConnectionSQL(NameDatabase database, MarketArea marketArea) throws SQLException {
		connection = getConnectionWorking();

		statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_UPDATABLE);
		// Database information_schema in Institute Database has no prefix,
		// so use just the name for reading
		if (database == NameDatabase.INFORMATION_SCHEMA) {
			statement.execute("USE `" + database + "`");
		} else {
			statement.execute("USE `" + NameDatabase.getDatabaseName(database) + "`");
		}
	}

	/**
	 * Creates a new object with access to the given <code>database</code> on
	 * the Institute or old Database and executes the <code>query</code>.
	 *
	 * @param database
	 *            the database
	 * @param query
	 *            the query
	 * @param accessInstituteDatabase
	 *            the access institute database
	 * @throws SQLException
	 *             the SQL exception
	 */
	public ConnectionSQL(NameDatabase database, String query) throws SQLException {
		this(database);
		setResultSet(query);
	}

	/**
	 * Closes all open connections, i.e.
	 * <code>ResultSet, Statement, Connection</code>.
	 *
	 * @throws SQLException
	 *             the SQL exception
	 */
	@Override
	public void close() throws SQLException {
		logger.debug("Close connection");
		if (resultSet != null) {
			resultSet.close();
		}
		if (statement != null) {
			statement.close();
		}
		if (connection != null) {
			connection.close();
		}
		logger.debug("Connection closed");
	}

	/**
	 * Execute the <code>query</code>. Attention! Do not set the
	 * <code>ResultSet</code>. Use setResultset instead
	 *
	 * @param query
	 *            The query to execute
	 * @throws SQLException
	 *             the SQL exception
	 */
	public void execute(String query) throws SQLException {
		// if a connection is used for several results, it is better to close
		// the result set
		if (resultSet != null) {
			resultSet.close();
		}
		statement.execute(query);
	}

	/**
	 * Execute the <code>query</code>.
	 *
	 * @param query
	 *            The query to execute
	 * @throws SQLException
	 *             the SQL exception
	 */
	public void executeUpdate(String query) throws SQLException {
		// if a connection is used for several results, it is better to close
		// the result set
		if (resultSet != null) {
			resultSet.close();
		}
		statement.executeUpdate(query);
	}

	/**
	 * Gets the connection.
	 *
	 * @return the connection
	 */
	public Connection getConnection() {
		return connection;
	}

	/**
	 * 
	 * @return ResultSet of previous execute(-Update) application
	 */
	public ResultSet getResultSet() {
		return resultSet;
	}

	/**
	 * Get meta data of current result set.
	 *
	 * @return the result set meta data
	 * @throws SQLException
	 *             the SQL exception
	 */
	public ResultSetMetaData getResultSetMetaData() throws SQLException {
		// if a connection is used for several results, it is better to close
		// the result set
		if (resultSet != null) {
			return resultSet.getMetaData();
		} else {
			return null;
		}
	}

	/**
	 * Checks for the current result set whether the specified column exists.
	 *
	 * @param columnName
	 *            the column name
	 * @return true if column exists
	 * @throws SQLException
	 *             the SQL exception
	 */
	public boolean resultSetIncludesColumn(String columnName) throws SQLException {
		final ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
		for (int columnIndex = 1; columnIndex <= resultSetMetaData
				.getColumnCount(); columnIndex++) {
			if (columnName.equals(resultSetMetaData.getColumnName(columnIndex))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Execute the <code>query</code> and get to the first result line.
	 *
	 * @param query
	 *            The query to execute
	 * @throws SQLException
	 *             the SQL exception
	 */
	public void setResultSet(String query) throws SQLException {
		// if a connection is used for several results, it is better to close
		// the result set
		if (resultSet != null) {
			resultSet.close();
		}
		resultSet = statement.executeQuery(query);
	}
}