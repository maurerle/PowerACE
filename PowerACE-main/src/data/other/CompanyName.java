package data.other;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.database.NameTable;

/**
 * In this class the names of the supply bidders are read from the database.
 * 
 * @author Massimo Genoese
 */
public final class CompanyName implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(CompanyName.class.getName());

	private final MarketArea marketArea;
	private Map<Integer, String> names;

	public CompanyName(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setCompanyName(this);
	}

	@Override
	public Void call() {
		try {
			initialize();
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * Returns the name corresponding to the number. The numbering in the map
	 * starts with 1. If the <code>number</code> does not exist, return null.
	 * 
	 * @param number
	 * @return
	 */
	public String getCompanyName(int number) {
		if (names.containsKey(number)) {
			return names.get(number);
		}
		logger.warn("Number " + number + " not specified.");
		return null;
	}

	public Map<Integer, String> getCompanyNames() {
		return names;
	}

	/**
	 * Returns the number corresponding to the name. If the <code>name</code>
	 * does not exist, return null.
	 * 
	 * @param name
	 * @return
	 */
	public Integer getNumber(String name) {
		for (final Entry<Integer, String> entry : names.entrySet()) {
			if (name.equals(entry.getValue())) {
				return entry.getKey();
			}
		}
		logger.error("Name " + name + " not specified.");
		return null;
	}

	public void loadCompanyNames() throws Exception {
		// TODO set database name
		try (ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED)) {
			String tableName = "";
			String query = "";
			tableName = NameTable.EXAMPLE.getTableName();
			query = "SELECT * FROM `" + tableName + "` final ORDER BY `id`";
			conn.setResultSet(query);

			while (conn.getResultSet().next()) {
				final String name = conn.getResultSet().getString("owner_name");
				final Integer number = conn.getResultSet().getInt("id");
				names.put(number, name);
			}
		}
	}

	private void initialize() throws Exception {
		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ CompanyName.class.getSimpleName());
		names = new LinkedHashMap<>();
		loadCompanyNames();
	}

}