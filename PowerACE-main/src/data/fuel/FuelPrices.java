package data.fuel;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.scheduling.Date;
import tools.database.ConnectionSQL;
import tools.database.NameDatabase;
import tools.math.Statistics;
import tools.types.FuelName;

/**
 * Utility class that reads and contains all fuel prices.
 * 
 * 
 * 
 * 
 */
public final class FuelPrices implements Callable<Void> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(FuelPrices.class.getName());

	private int firstAvailYearScenarioYearly;
	private int firstYearNeeded;
	/**
	 * Contains the daily fuel prices in Euro/MWh for each fuel from
	 * <code>dailyFuels</code> for each year and each day.
	 * 
	 */
	private Map<FuelName, Map<Integer, Map<Integer, Float>>> fuelPricesDaily;
	/**
	 * Contains the daily fuel prices in Euro/MWh for each fuel from
	 * <code>dailyFuels</code>.
	 */
	private Map<FuelName, Float> fuelPricesToday;
	/**
	 * Contains the yearly fuel prices in Euro/MWh for each fuel from
	 * {@link FuelName}.
	 */
	private Map<FuelName, Map<Integer, Float>> fuelPricesYearly;
	/**
	 * Contains the avg yearly fuel prices in Euro/MWh for each fuel from
	 * {@link FuelName}.
	 */
	private Map<FuelName, Map<Integer, Float>> fuelPricesYearlyAvg;
	/**
	 * Contains the transport costs for each fuel in Euro/MWh where they are
	 * relevant.
	 */
	private int lastAvailYearScenarioYearly;
	/** Last day for which daily prices where called. */
	private int lastDay = Integer.MIN_VALUE;
	private int lastYearNeeded;
	private final MarketArea marketArea;

	public FuelPrices(MarketArea marketArea) {
		this.marketArea = marketArea;
		marketArea.setFuelPrices(this);
	}

	private void calcYearlyAveragesFromDailyPrices() {
		fuelPricesYearlyAvg = new HashMap<>();

		for (final FuelName fuelName : fuelPricesDaily.keySet()) {
			fuelPricesYearlyAvg.put(fuelName, new HashMap<Integer, Float>());
			for (final Integer year : fuelPricesDaily.get(fuelName).keySet()) {
				final Float average = Statistics
						.calcAvg(fuelPricesDaily.get(fuelName).get(year).values());
				fuelPricesYearlyAvg.get(fuelName).put(year, average);
			}
		}
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
	 * Return a copy of the daily fuel prices.
	 */
	public Map<FuelName, Map<Integer, Map<Integer, Float>>> getFuelsDaily() {
		return new HashMap<>(fuelPricesDaily);
	}

	/**
	 * Returns the fuel price of a specific fuel, day and year. Uses daily
	 * prices for coal, oil and gas, yearly prices elsewise.
	 * 
	 * @param fuel
	 *            the fuel name
	 * @param year
	 *            in yyyy format, e.g. 2014
	 * @param day
	 *            in day of the year format, e.g. 1-365
	 */
	public Float getPricesDaily(FuelName fuel, int year, int day) {

		Float price;

		FuelName requestedFuel;
		switch (fuel) {
			case CLEAN_COAL:
			case COAL_AT_COAST:
			case COAL_FAR_COAST:
			case COAL:
				requestedFuel = FuelName.COAL;
				break;
			case CLEAN_GAS:
			case BIOGAS:
			case LANDFILLGAS:
			case MINEGAS:
			case BLASTFURNACEGAS:
				requestedFuel = FuelName.GAS;
				break;
			case HEATING_OIL:
			case HEATING_OIL_HEAVY:
				requestedFuel = FuelName.OIL;
				break;
			// Skip some fuels that are not relevant
			case HYDROLARGESCALE:
			case HYDROSMALLSCALE:
			case HYDRO_PUMPED_STORAGE:
			case HYDRO_SEASONAL_STORAGE:
			case OTHER:
			case INTERCONNECTOR:
			case TIDAL:
				return 0f;
			default:
				requestedFuel = fuel;
		}
		price = getPricesYearly(fuel, year);

		return price;
	}

	/**
	 * Return the fuel price [Euro/MWh] for the requested day of the current
	 * year.
	 * 
	 * @param fuel
	 *            the fuel name
	 * @param day
	 *            in day of the year format, e.g. 1-365
	 */
	public Float getPricesDaily(FuelName fuel, int day) {
		return getPricesDaily(fuel, Date.getYear(), day);
	}

	/**
	 * Return the fuel price [Euro/MWh] for the current day of the current year.
	 * 
	 * @param fuel
	 *            the fuel name
	 */
	public Float getPricesDaily(FuelName fuel) {
		// in order to speed up access load prices for current day and save them
		// in an custom map
		final int today = Date.getDayOfYear();
		if (lastDay != today) {
			initializefuelPricesToday(today);
			lastDay = today;

		}
		return fuelPricesToday.get(fuel);
	}

	/**
	 * Return the fuel price of the current year.
	 */
	public Float getPricesYearly(FuelName fuel) {
		return getPricesYearly(fuel, Date.getYear());
	}

	/**
	 * Return the fuel price of the requested year.
	 */
	public Float getPricesYearly(FuelName fuel, int year) {
		if (fuel == FuelName.CLEAN_COAL) {
			fuel = FuelName.COAL;
		}
		if (fuel == FuelName.BLASTFURNACEGAS) {
			fuel = FuelName.GAS;
		}
		if (fuel == FuelName.CLEAN_GAS) {
			fuel = FuelName.GAS;
		}
		if (fuel == FuelName.CLEAN_LIGNITE) {
			fuel = FuelName.LIGNITE;
		}
		if (fuel == FuelName.OTHER) {
			return 0f;
		}
		if (fuel == FuelName.WASTE) {
			return 0f;
		}
		if (fuel == FuelName.INTERCONNECTOR) {
			return 0f;
		}
		if (fuel == FuelName.HYDRO_SEASONAL_STORAGE) {
			return 0f;
		}
		if (fuel == FuelName.HYDRO_PUMPED_STORAGE) {
			return 0f;
		}
		if (fuel == FuelName.STORAGE) {
			return 0f;
		}
		if (fuel.isRenewableType()) {
			return 0f;
		}
		if (!fuelPricesYearly.get(fuel).containsKey(year)) {
			return 0f;
		}
		if (fuelPricesYearly.get(fuel).get(year) == null) {
			return 0f;
		}
		return fuelPricesYearly.get(fuel).get(year);
	}

	/**
	 * Return the fuel price of the current year.
	 */
	public Float getPricesYearlyAvg(FuelName fuel) {
		return getPricesYearlyAvg(fuel, Date.getYear());
	}

	/**
	 * Return the fuel price of the requested year.
	 */
	public Float getPricesYearlyAvg(FuelName fuel, int year) {
		if (fuel == FuelName.CLEAN_COAL) {
			fuel = FuelName.COAL;
		}
		if (fuel == FuelName.CLEAN_GAS) {
			fuel = FuelName.GAS;
		}
		if (fuel == FuelName.CLEAN_LIGNITE) {
			fuel = FuelName.LIGNITE;
		}
		if (fuelPricesYearlyAvg.containsKey(fuel)
				&& fuelPricesYearlyAvg.get(fuel).containsKey(year)) {
			return fuelPricesYearlyAvg.get(fuel).get(year);
		}
		return fuelPricesYearly.get(fuel).get(year);
	}

	/** loads FuelPriceScenario */
	private void initialize() throws Exception {
		logger.info(marketArea.getInitialsBrackets() + "Load fuel price data");

		// load yearly prices
		firstYearNeeded = Date.getStartYear();
		lastYearNeeded = Date.getLastDetailedForecastYear();

		firstAvailYearScenarioYearly = Math.max(firstYearNeeded,
				marketArea.getFirstYearlyFuelPriceYear());
		lastAvailYearScenarioYearly = Math.min(lastYearNeeded,
				marketArea.getLastYearlyFuelPriceYear());

		loadYearlyPrices();

		writeMissingValues();
		calcYearlyAveragesFromDailyPrices();
	}

	/**
	 * synchronized if threads access parallel to initialize the fuelPricesToday
	 */
	private synchronized void initializefuelPricesToday(int today) {
		for (final FuelName fuelName : FuelName.values()) {
			fuelPricesToday.put(fuelName, getPricesDaily(fuelName, today));
		}

	}

	private void loadYearlyPrices() {

		// Initialize Prices
		fuelPricesYearly = new HashMap<>(FuelName.values().length);
		for (final FuelName fuelName : FuelName.values()) {
			fuelPricesYearly.put(fuelName, new HashMap<>());
		}

		try {

			// Connection to Database

			final String tableName = marketArea.getFuelPriceScenario();
			// TODO set database name
			final ConnectionSQL conn = new ConnectionSQL(NameDatabase.NAME_OF_DATABASED);
			final String query = "SELECT * FROM  `" + tableName + "`";
			conn.setResultSet(query);

			// each fuel
			while (conn.getResultSet().next()) {

				final FuelName fuel = FuelName.getFuelName(conn.getResultSet().getInt("Nr"));

				for (int year = firstAvailYearScenarioYearly; year <= lastAvailYearScenarioYearly; year++) {
					final Float price = conn.getResultSet().getFloat(year);
					fuelPricesYearly.get(fuel).put(year, price);
				}

			}

			conn.close();

		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void writeMissingValues() {

		boolean valuesMissingStart = false;
		// extrapolating: write missing values start by taking the values from
		// the first year that is available
		for (int year = firstAvailYearScenarioYearly - 1; year >= firstYearNeeded; year--) {

			if (!valuesMissingStart) {
				logger.warn(
						"Some yearly fuel data at beginning are missing! Starting from " + year);
				valuesMissingStart = true;
			}

			for (final FuelName fuelName : fuelPricesYearly.keySet()) {
				if (fuelPricesYearly.isEmpty()) {
					continue;
				}
				fuelPricesYearly.get(fuelName).put(year,
						fuelPricesYearly.get(fuelName).get(firstAvailYearScenarioYearly));
			}

		}

		boolean valuesMissingEnd = false;
		// extrapolating: write missing values end by taking the values from
		// the last year that is available
		for (int year = lastAvailYearScenarioYearly + 1; year <= lastYearNeeded; year++) {

			if (!valuesMissingEnd) {
				logger.warn("Some yearly fuel data at end are missing! Starting from " + year);
				valuesMissingEnd = true;
			}

			for (final FuelName fuelName : fuelPricesYearly.keySet()) {
				if (fuelPricesYearly.isEmpty()) {
					continue;
				}
				fuelPricesYearly.get(fuelName).put(year,
						fuelPricesYearly.get(fuelName).get(lastAvailYearScenarioYearly));
			}
		}

	}

}