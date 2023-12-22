package data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.demand.Demand;
import data.exchange.Flows;
import data.fuel.FuelPrices;
import data.other.CompanyName;
import data.powerplant.Availability;
import data.powerplant.costs.OperationMaintenanceCost;
import data.powerplant.costs.StartupCost;
import data.renewable.RenewableManager;
import simulations.MarketArea;
import simulations.scheduling.Date;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.other.Concurrency;

/**
 * Reading of scenario settings for respective market area from SQL database
 *
 * @author PR
 * @since 06/2014
 *
 */
public class MarketAreaData {

	/** Instance of logger */
	private static final Logger logger = LoggerFactory.getLogger(MarketAreaData.class.getName());
	/** Current market area */
	private final MarketArea marketArea;

	/** Public constructor */
	public MarketAreaData(MarketArea marketArea) {
		this.marketArea = marketArea;
	}

	/** Load market area data */
	public void loadMarketAreaData() {

		// Load data that has almost no dependencies
		Collection<Callable<Void>> tasks = new ArrayList<>();

		tasks.add(new Availability(marketArea));
		tasks.add(new CompanyName(marketArea));
		tasks.add(new Demand(marketArea));
		tasks.add(new Flows(marketArea));
		tasks.add(new FuelPrices(marketArea));
		tasks.add(new OperationMaintenanceCost(marketArea));
		tasks.add(new RenewableManager(marketArea));
		tasks.add(new StartupCost(marketArea));

		Concurrency.executeConcurrently(tasks);

		// Load data that has dependencies
		tasks = new ArrayList<>();

		Concurrency.executeConcurrently(tasks);

	}

	private void readLastYearlyFuelPriceYear() throws SQLException {
		ConnectionSQL conn;

		String tableName = marketArea.getFuelPriceScenario();
		// TODO Set database Name
		conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED);

		final String sqlQuery = "SELECT `start_year`, `end_year` FROM `"
				+ "Fuel_Scenarios` WHERE `Table_Name`='" + tableName + "'";
		conn.setResultSet(sqlQuery);
		if (conn.getResultSet().next()) {
			final int lastYearlyFuelPriceYear = conn.getResultSet().getInt("end_year");
			marketArea.setLastYearlyFuelPriceYear(lastYearlyFuelPriceYear);

			final int firstYearlyFuelPriceYear = conn.getResultSet().getInt("start_year");
			if (conn.getResultSet().wasNull()) {
				logger.warn(
						"No first available year for fuel specified! Assume that data is available from start year! Check scenario table!");
				marketArea.setFirstYearlyFuelPriceYear(Date.getStartYear());
			} else {
				marketArea.setFirstYearlyFuelPriceYear(firstYearlyFuelPriceYear);
			}

		} else {
			logger.warn("Last fuel price year was not set! Check scenario table!");
		}

		conn.close();
	}

	/** Read different scenario settings required before loading data */
	public void readScenarioSettings() {
		// Read last yearly fuel price year
		try {
			readLastYearlyFuelPriceYear();
		} catch (final SQLException e) {
			logger.error("Error while reading last yearly fuel price year", e);
		}

	}
}