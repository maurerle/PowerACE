package data.powerplant.costs;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.initialization.Settings;
import supply.powerplant.PlantAbstract;
import supply.powerplant.technique.Type;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.database.NameTable;

/**
 * Utility class that loads the operation and maintenance costs.
 * 
 * 
 */
public final class OperationMaintenanceCost implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(OperationMaintenanceCost.class.getName());

	/** Operation and maintenance costs in Euro/MW */
	private Map<Type, Float> fixedCosts;
	private final MarketArea marketArea;
	/** Operation and maintenance costs in Euro/MWh */
	private Map<Type, Float> varCosts;

	public OperationMaintenanceCost(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setOperationMaintenanceCosts(this);
	}

	@Override
	public Void call() {
		initialize();
		return null;
	}

	/**
	 * @param plant
	 * @return fixedCosts
	 */
	public float getCostFixed(PlantAbstract plant) {
		if (fixedCosts.containsKey(plant.getCategory())) {
			return fixedCosts.get(plant.getCategory());
		}
		// No warning when fuel type RENEWABLE because not modelled endogenously
		else if (plant.getCategory() == Type.OTHER) {
			return 0f;
		} else {
			logger.warn("No fixed costs defined for fuel type " + plant);
			return 0f;
		}
	}

	public float getCostFixed(Type type) {
		if (fixedCosts.containsKey(type)) {
			return fixedCosts.get(type);
		}
		return 0f;
	}

	public float getCostVar(PlantAbstract plant) {
		return varCosts.get(plant.getCategory());
	}

	/** Loads startup costs */
	private void initialize() {
		logger.info("Initialize " + OperationMaintenanceCost.class.getSimpleName());
		loadCostsFixed();
		loadCostsVar();
	}

	private void loadCostsFixed() {

		fixedCosts = new HashMap<>(Type.values().length);

		// TODO set table name
		final String tableName = NameTable.EXAMPLE.getTableName();
		// TODO Set database name
		final NameDatabase nameDatabase = NameDatabase.NAME_OF_DATABASED;

		try (ConnectionSQL connector = new ConnectionSQL(nameDatabase)) {
			final String sqlQuery = "SELECT * FROM `" + tableName + "` WHERE `scenario`='"
					+ Settings.getOperationMaintenanceScenarioFixed() + "'";
			connector.setResultSet(sqlQuery);
			connector.getResultSet().next();

			for (final Type powerPlantCategory : Type.values()) {
				// set fixed costs
				fixedCosts.put(powerPlantCategory,
						connector.getResultSet().getFloat(powerPlantCategory.toString()));
			}
		} catch (final SQLException e) {
			logger.error("SQLException", e);
		}
	}

	private void loadCostsVar() {

		varCosts = new HashMap<>(Type.values().length);

		// TODO set table name
		final String tableName = NameTable.EXAMPLE.getTableName();
		// TODO Set database name
		final NameDatabase nameDatabase = NameDatabase.NAME_OF_DATABASED;
		try (ConnectionSQL connector = new ConnectionSQL(nameDatabase)) {
			final String sqlQuery = "SELECT * FROM `" + tableName + "` WHERE `scenario`='"
					+ Settings.getOperationMaintenanceScenarioVar() + "'";
			connector.setResultSet(sqlQuery);
			connector.getResultSet().next();

			for (final Type powerPlantCategory : Type.values()) {
				// set fixed costs
				varCosts.put(powerPlantCategory,
						connector.getResultSet().getFloat(powerPlantCategory.toString()));
			}
		} catch (final SQLException e) {
			logger.error("SQLException", e);
		}
	}

}