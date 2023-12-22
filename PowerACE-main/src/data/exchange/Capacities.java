package data.exchange;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.exchange.Interconnector.InterconnectorBidirectional;
import data.exchange.Interconnector.SeasonNTC;
import markets.operator.spot.MarketCouplingOperator;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.types.InterconnectionScenario;

/**
 * Class to store interconnector capacities between actual market area and
 * interconnected areas for the current year <br>
 * <br>
 * Data in the first year is read after all market areas have been initialized
 * because the data variables contain a reference to the instance of the
 * interconnected market area.
 * 
 * @author PR
 * @since 03/2013
 * 
 */
public class Capacities {

	/** Instance of logger */
	private static final Logger logger = LoggerFactory.getLogger(Capacities.class.getName());

	/**
	 * Hourly availability of interconnectors for current year (for all
	 * deterministic runs full availability is assumed; adjusted for each Monte
	 * Carlo run) [1 : available; 0 : unavailable]<br>
	 * <br>
	 * [fromMarketArea[toMarketArea[DateKey[Capacity]]]]
	 */
	private Map<MarketArea, Map<MarketArea, List<Integer>>> interconnectionAvailabilities;
	/** Mapping between each market area and respective interconnections */
	private final Map<MarketArea, List<MarketArea>> interconnectionsByMarketArea = new LinkedHashMap<>();
	/** List of interconnectors in model */
	private final List<Interconnector> interconnectors = new ArrayList<>();
	/** List of (bi-directional) interconnectors in model */
	private final List<InterconnectorBidirectional> interconnectorsBidirectional = new ArrayList<>();
	/** Interconnectors mapped to market areas */
	private final Map<MarketArea, Map<MarketArea, Interconnector>> interconnectorsMappedtoMarketAreas = new LinkedHashMap<>();

	/** Create bi-directional interconnectors */
	private void createInterconnectorsBidirectional() {
		for (final Interconnector interconnector : getInterconnectors()) {
			final InterconnectorBidirectional interconnectorBidirectional = new InterconnectorBidirectional(
					interconnector.getFromMarketArea(), interconnector.getToMarketArea());
			if (interconnectorsBidirectional.contains(interconnectorBidirectional)) {
				continue;
			} else {
				interconnectorsBidirectional.add(interconnectorBidirectional);
			}
		}
	}

	/**
	 * Get the total export capacity for the specified market area and hourOfDay
	 * in the current year.
	 */
	public double getExportCapacityTotal(MarketArea fromMarketArea, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		final int year = Date.getYear();

		double exportCapacity = 0;
		// In case of just one Market area
		if (interconnectorsMappedtoMarketAreas.isEmpty()) {
			return exportCapacity;
		}
		for (final MarketArea toMarketArea : interconnectorsMappedtoMarketAreas.get(fromMarketArea)
				.keySet()) {
			if (!fromMarketArea.equals(toMarketArea)) {
				exportCapacity += getInterconnectionCapacityHour(fromMarketArea, toMarketArea, year,
						hourOfYear);
			}
		}
		return exportCapacity;
	}

	/**
	 * Count the number of forced outages of all interconnectors in current year
	 */
	public Float getHoursOfUnavailability(MarketArea marketArea) {
		if (interconnectionAvailabilities == null) {
			return 0f;
		}

		int forcedOutages = 0;
		// Count number of hours with failure for all interconnectors
		for (final MarketArea fromMarketArea : interconnectionAvailabilities.keySet()) {
			for (final MarketArea toMarketArea : interconnectionAvailabilities.get(fromMarketArea)
					.keySet()) {
				if (fromMarketArea.equals(marketArea) || toMarketArea.equals(marketArea)) {
					forcedOutages += interconnectionAvailabilities.get(fromMarketArea)
							.get(toMarketArea).stream().filter(available -> available == 0).count();
				}
			}
		}

		// Return half the number of outages because all interconnectors are
		// counted twice
		return forcedOutages / 2f;
	}

	/**
	 * Get the total import capacity for the specified market area and hourOfDay
	 * in the current year.
	 */
	public double getImportCapacityTotal(MarketArea toMarketArea, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		final int year = Date.getYear();

		double importCapacity = 0;
		// In case of just one MarketArea and no interconnectors
		if (interconnectorsMappedtoMarketAreas.isEmpty()) {
			return importCapacity;
		}
		for (final MarketArea fromMarketArea : interconnectorsMappedtoMarketAreas.keySet()) {
			if (!interconnectorsMappedtoMarketAreas.get(fromMarketArea).containsKey(toMarketArea)) {
				continue;
			}
			if (!toMarketArea.equals(fromMarketArea)) {
				importCapacity += getInterconnectionCapacityHour(fromMarketArea, toMarketArea, year,
						hourOfYear);
			}
		}
		return importCapacity;
	}

	/**
	 * Get hourly interconnection capacity for the specified interconnector and
	 * time
	 */
	public float getInterconnectionCapacityHour(MarketArea fromMarketArea, MarketArea toMarketArea,
			int year, int hourOfYear) {
		if (interconnectorsMappedtoMarketAreas.get(fromMarketArea).containsKey(toMarketArea)) {
			return interconnectorsMappedtoMarketAreas.get(fromMarketArea).get(toMarketArea)
					.getCapacity(year, hourOfYear);
		}
		return 0f;
	}

	/**
	 * Get hourly interconnection capacity for specified interconnector
	 * 
	 * @param interconnectedMarketArea
	 * @param marketArea
	 * @param year
	 * @param dayOfYear
	 * @return
	 */
	public List<Float> getInterconnectionCapacityHourlyDay(MarketArea fromMarketArea,
			MarketArea toMarketArea, int year, int dayOfYear) {
		final List<Float> values = new ArrayList<>();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			final int hourOfYear = Date.getHourOfYearFromHourOfDay(dayOfYear, hourOfDay);
			values.add(
					getInterconnectionCapacityHour(fromMarketArea, toMarketArea, year, hourOfYear));
		}
		return values;
	}

	/** Get interconnector */
	private Interconnector getInterconnector(MarketArea fromMarketArea, MarketArea toMarketArea) {

		Interconnector interconnector;
		// Interconnector already initialized
		if (interconnectorsMappedtoMarketAreas.containsKey(fromMarketArea)
				&& interconnectorsMappedtoMarketAreas.get(fromMarketArea)
						.containsKey(toMarketArea)) {
			interconnector = interconnectorsMappedtoMarketAreas.get(fromMarketArea)
					.get(toMarketArea);
		}
		// Create new one
		else {
			interconnector = new Interconnector(fromMarketArea, toMarketArea);

			if (!interconnectorsMappedtoMarketAreas.containsKey(fromMarketArea)) {
				interconnectorsMappedtoMarketAreas.put(fromMarketArea, new LinkedHashMap<>());
			}
			interconnectorsMappedtoMarketAreas.get(fromMarketArea).put(toMarketArea,
					interconnector);
			interconnectors.add(interconnector);
		}

		return interconnector;
	}

	/** Get {@link#interconnectors} */
	public List<Interconnector> getInterconnectors() {
		return interconnectors;
	}

	/** Get {@link#interconnectorsBidirectional} */
	public List<InterconnectorBidirectional> getInterconnectorsBidirectional() {
		return interconnectorsBidirectional;
	}

	/**
	 * Returns all market areas with an interconnection to specified market area
	 */
	public List<MarketArea> getMarketAreasInterconnected(MarketArea marketArea) {
		final List<MarketArea> marketAreaInterconnected = new ArrayList<>();

		/* Return cached lists */
		if (interconnectionsByMarketArea.containsKey(marketArea)) {
			return interconnectionsByMarketArea.get(marketArea);
		}

		/* Set interconnections for specified market area */
		if (interconnectorsMappedtoMarketAreas.keySet().contains(marketArea)) {
			for (final Interconnector interconnector : interconnectorsMappedtoMarketAreas
					.get(marketArea).values()) {
				final MarketArea toMarketArea = interconnector.getToMarketArea();
				if (!marketAreaInterconnected.contains(toMarketArea)) {
					marketAreaInterconnected.add(toMarketArea);
				}
			}
		}
		interconnectionsByMarketArea.put(marketArea, marketAreaInterconnected);

		return marketAreaInterconnected;
	}

	/**
	 * Check whether between the two specified market areas a direct
	 * interconnection exists
	 * 
	 * @param fromMarketArea
	 * @param toMarketArea
	 * @return <code>false</code> if no interconnection exists or if one of
	 *         specified market areas is null, else <code>true</code>
	 */
	boolean hasInterconnection(MarketArea fromMarketArea, MarketArea toMarketArea) {
		if ((fromMarketArea == null) || (toMarketArea == null)) {
			return false;
		}
		if (interconnectorsMappedtoMarketAreas.containsKey(fromMarketArea)
				&& interconnectorsMappedtoMarketAreas.get(fromMarketArea)
						.containsKey(toMarketArea)) {

			// Check whether interconnector is already available in current year
			final Interconnector interconnector = interconnectorsMappedtoMarketAreas
					.get(fromMarketArea).get(toMarketArea);
			if (Date.getYear() >= interconnector.getFirstYearAvailable()) {
				return true;
			}
		}
		return false;
	}

	public boolean isDataReferenceYearAvailable() {
		// Loop all interconnectors
		for (final Interconnector interconnector : interconnectors) {
			if (Date.getReferenceYear() < interconnector.getFirstYearAvailable()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Load input data from databas
	 * 
	 * @param marketCouplingOperator
	 * @param model
	 */
	public void loadData(MarketCouplingOperator marketCouplingOperator, PowerMarkets model) {
		loadInterconnectionData(marketCouplingOperator, model);
		isDataReferenceYearAvailable();
	}

	/**
	 * Load interconnection data
	 * 
	 * @param marketCouplingOperator
	 *            Provides information about which market areas are using market
	 *            coupling
	 * @param model
	 *            Provides mapping between market area initials and respective
	 *            type
	 */
	private void loadInterconnectionData(MarketCouplingOperator marketCouplingOperator,
			PowerMarkets model) {
		logger.info("Load interconnector capacities");

		try {
			/* Load yearly interconnector values */
			loadInterconnectionDataYearly(model, marketCouplingOperator);

			/* Create bi-directional interconnectors */
			createInterconnectorsBidirectional();
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/** Load yearly interconnection data */
	private void loadInterconnectionDataYearly(PowerMarkets model,
			MarketCouplingOperator marketCouplingOperator) throws SQLException {

		// Create string of market area initials which are coupled. This is used
		// in order to limit the SQL query only to relevant market areas.
		final StringBuffer marketAreasCoupledFrom = new StringBuffer();
		final StringBuffer marketAreasCoupledTo = new StringBuffer();
		for (final MarketArea marketArea : marketCouplingOperator.getMarketAreas()) {
			if (marketAreasCoupledFrom.length() > 0) {
				marketAreasCoupledFrom.append(" OR ");
			}
			if (marketAreasCoupledTo.length() > 0) {
				marketAreasCoupledTo.append(" OR ");
			}
			marketAreasCoupledFrom
					.append(" `from` LIKE '" + marketArea.getInitials().toUpperCase() + "%'");
			marketAreasCoupledTo
					.append("`to` LIKE '" + marketArea.getInitials().toUpperCase() + "%'");
		}

		final String tableName;

		tableName = Settings.getInterconnectionDataScenario();

		final String sqlQuery = "SELECT `year`,`from`,`to`,`winter`,`summer` FROM `" + tableName
				+ "` WHERE   ((" + marketAreasCoupledFrom + ") AND (" + marketAreasCoupledTo
				+ ")) AND `from`<>`to` ORDER BY `year`";
		// TODO set Database name
		try (final ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED);) {
			conn.setResultSet(sqlQuery);

			// Check whether result is empty
			if (!conn.getResultSet().isBeforeFirst()) {
				logger.warn("No NTC data in SQL result set!");
			}

			// Loop all rows
			while (conn.getResultSet().next()) {

				// Get market areas
				// From
				final String fromMarketAreaInitials = conn.getResultSet().getString("from");
				final MarketArea fromMarketArea;
				if (model.getMarketAreasMappedInitials().containsKey(fromMarketAreaInitials)) {
					fromMarketArea = model.getMarketAreasMappedInitials()
							.get(fromMarketAreaInitials);
				} else {
					fromMarketArea = model.getMarketAreasMappedInitials()
							.get(fromMarketAreaInitials.substring(0, 2));
				}

				// To
				final String toMarketAreaInitials = conn.getResultSet().getString("to");
				final MarketArea toMarketArea;
				if (model.getMarketAreasMappedInitials().containsKey(toMarketAreaInitials)) {
					toMarketArea = model.getMarketAreasMappedInitials().get(toMarketAreaInitials);
				} else {
					toMarketArea = model.getMarketAreasMappedInitials()
							.get(toMarketAreaInitials.substring(0, 2));
				}

				// In case in and out is the same after using the substrings
				if (fromMarketArea.equals(toMarketArea)) {
					continue;
				}

				// Get interconnector
				final Interconnector interconnector = getInterconnector(fromMarketArea,
						toMarketArea);

				// Get year
				final int year = conn.getResultSet().getInt("year");

				// Read seasonal values
				double capacityWinter;
				double capacitySummer;
				if (Settings.getInterconnectionDataScenario()
						.equals(InterconnectionScenario.NO_INTERCONNECTION.name())) {
					capacityWinter = 0d;
					capacitySummer = 0d;
				} else if (Settings.getInterconnectionDataScenario()
						.equals(InterconnectionScenario.UNLIMITED.name())) {
					capacityWinter = Double.MAX_VALUE;
					capacitySummer = Double.MAX_VALUE;
				} else {
					capacityWinter = conn.getResultSet().getDouble("winter");
					capacitySummer = conn.getResultSet().getDouble("summer");
				}

				interconnector.addCapacitySeason(year, SeasonNTC.WINTER, capacityWinter);
				interconnector.addCapacitySeason(year, SeasonNTC.SUMMER, capacitySummer);
			}
		}
	}
}
