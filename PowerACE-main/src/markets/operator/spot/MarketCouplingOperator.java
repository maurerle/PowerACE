package markets.operator.spot;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.exchange.Capacities;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBModel;
import gurobi.GRBVar;
import markets.bids.Bid;
import markets.bids.Bid.BidType;
import markets.clearing.MarketCouplingHourly;
import markets.operator.spot.tools.ExchangeForecastMarketCoupling;
import markets.operator.spot.tools.MarginalBid;
import markets.operator.spot.tools.StorageOperationForecast;
import markets.trader.future.tools.PriceForecastFuture;
import markets.trader.spot.supply.SupplyTrader;
import markets.trader.spot.supply.tools.ForecastTypeDayAhead;
import results.spot.CongestionRevenue;
import results.spot.ExchangeFlows;
import results.spot.PricesMarketArea;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.logging.Folder;
import tools.logging.LoggerCSV;
import tools.math.Statistics;
import tools.other.Concurrency;

/**
 * This day ahead market coupling operator is called by the market scheduler
 * after each market area's call for bids in order to clear the coupled markets.
 * <p>
 * The market coupling operator uses a specific optimization algorithm (e.g.
 * COSMOS) in order to maximize social welfare from the market area bids
 * considering interconnection capacities.
 * <p>
 * Does not extend/implement any super classes/interfaces because operator is
 * not assigned to a specific market area and the process of the operation is
 * quite different from other implemented markets (e.g. receiving bids from
 * local market operator not bidders directly)
 * 
 * @author PR
 * @since 02/2013
 * 
 */
public final class MarketCouplingOperator {

	private static final int FORECAST_LENGTH_LONG = 3 * Date.HOURS_PER_DAY;
	private static final int FORECAST_LENGTH_SHORT = 3 * Date.HOURS_PER_DAY;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(MarketCouplingOperator.class.getName());

	private static long t1; // NOPMD
	private static long t2; // NOPMD
	private static long t3; // NOPMD
	private static long t4; // NOPMD
	public static int getForecastLengthShort() {
		return FORECAST_LENGTH_SHORT;
	}
	private Capacities capacitiesData;

	private CongestionRevenue congestionRevenue;
	/** Map storing the EV charging schedule forecasts for all market areas */
	private ExchangeFlows exchangeFlows;
	/** Map storing the exchange forecasts for all interconnectors */
	private Map<MarketArea, Map<MarketArea, ExchangeForecastMarketCoupling>> exchangeForecastMarketCoupling = new LinkedHashMap<>();
	/** Map storing the EV scheduling forecasts for all market areas */

	/** Map storing the exchange forecasts for all market areas */
	private Map<MarketArea, Map<Integer, Float>> hourlyExchangeForecastAllMarketAreas = new LinkedHashMap<>();

	private Map<MarketArea, List<Float>> hourlyStorageOperationForecastAllMarketAreas = new LinkedHashMap<>();
	private int logIDPriceForecast;
	private int logIDStorageForecast;
	/** List of market areas to be coupled */
	private final List<MarketArea> marketAreas = new ArrayList<>();

	/**
	 * Contains the hourly market clearing prices for each market area after
	 * market coupling.<br>
	 * <br>
	 * <code>[marketArea[hour[MCP]]]</code>
	 */
	private final Map<MarketArea, Map<Integer, Float>> marketClearingPricesDaily = new ConcurrentHashMap<>();

	/** Daily total cleared volumes for each market area and hour */
	private final Map<MarketArea, Map<Integer, Float>> marketClearingVolumeDaily = new ConcurrentHashMap<>();
	/** Name of folder where mathematical program is saved */
	private final String marketCouplingFolderPath = Settings.getLogPathName()
			+ Folder.MARKET_COUPLING.toString();
	/** Logging object market area prices */
	private PricesMarketArea pricesMarketArea;

	/**
	 * Map of simple bids for current day<br>
	 * <br>
	 * <code>[market area[hour of day[list of bid points]]]</code>
	 */
	private final Map<MarketArea, Map<Integer, List<Bid>>> simpleBids = new ConcurrentHashMap<>();

	/** Map storing the storage operation forecasts for all market areas */
	private Map<MarketArea, StorageOperationForecast> storageOperationForecast = new LinkedHashMap<>();

	/** Add new market area which should be part of market coupling */
	public void addMarketArea(MarketArea marketArea) {
		marketAreas.add(marketArea);
		Collections.sort(marketAreas, (m1, m2) -> Integer.compare(m1.getId(), m2.getId()));
	}

	private void calculateExchangeFlowForecast() {
		final Map<MarketArea, Map<MarketArea, List<Float>>> hourlyExchangeForecast = new LinkedHashMap<>();
		final int startDayOfYear = 3;
		if (Settings.getDayAheadPriceForecastType() == ForecastTypeDayAhead.OPTIMIZATION) {

			final Map<MarketArea, Map<MarketArea, Map<Integer, Float>>> flowForecast = PriceForecastFuture
					.getExchangeFlowForecast();
			// use original market area set for deterministic results
			for (final MarketArea marketAreaFrom : marketAreas) {
				if (hourlyExchangeForecast.get(marketAreaFrom) == null) {
					hourlyExchangeForecast.put(marketAreaFrom, new LinkedHashMap<>());
				}
				for (final MarketArea marketAreaTo : marketAreas) {

					// Get exchange forecast
					hourlyExchangeForecast.get(marketAreaFrom).put(marketAreaTo, new ArrayList<>());
					final int lastForecastHour = (Date.getFirstHourOfToday()
							+ FORECAST_LENGTH_LONG);
					for (int hourOfYear = Date
							.getFirstHourOfToday(); hourOfYear < lastForecastHour; hourOfYear++) {
						if (hourOfYear >= Date.HOURS_PER_YEAR) {
							hourlyExchangeForecast.get(marketAreaFrom).get(marketAreaTo)
									.add(flowForecast.get(marketAreaFrom).get(marketAreaTo)
											.get(hourOfYear - FORECAST_LENGTH_LONG));
						} else {
							hourlyExchangeForecast.get(marketAreaFrom).get(marketAreaTo)
									.add(flowForecast.get(marketAreaFrom).get(marketAreaTo)
											.get(hourOfYear));
						}
					}
					if (hourlyExchangeForecast.get(marketAreaTo) == null) {
						hourlyExchangeForecast.put(marketAreaTo, new LinkedHashMap<>());
					}

					hourlyExchangeForecast.get(marketAreaTo).put(marketAreaFrom, new ArrayList<>());

					for (int hourOfYear = Date
							.getFirstHourOfToday(); hourOfYear < lastForecastHour; hourOfYear++) {
						if (hourOfYear >= Date.HOURS_PER_YEAR) {
							hourlyExchangeForecast.get(marketAreaTo).get(marketAreaFrom)
									.add(-flowForecast.get(marketAreaFrom).get(marketAreaTo)
											.get(hourOfYear - FORECAST_LENGTH_LONG));
						} else {
							hourlyExchangeForecast.get(marketAreaTo).get(marketAreaFrom)
									.add(-flowForecast.get(marketAreaFrom).get(marketAreaTo)
											.get(hourOfYear));
						}
					}

				}
			}
		} else {
			// Initialize module for market coupling exchange forecast
			if (Date.isFirstYear() && (Date.getDayOfYear() == startDayOfYear)) {
				// List of already considered market areas, since each
				// interconnector only needs to be forecasted in one direction
				final List<MarketArea> marketAreasAlreadyConsidered = new ArrayList<>();
				for (final MarketArea marketAreaFrom : marketAreas) {
					marketAreasAlreadyConsidered.add(marketAreaFrom);
					for (final MarketArea marketAreaTo : getCapacitiesData()
							.getMarketAreasInterconnected(marketAreaFrom)) {
						if (!marketAreasAlreadyConsidered.contains(marketAreaTo)) {
							if (exchangeForecastMarketCoupling.get(marketAreaFrom) == null) {
								exchangeForecastMarketCoupling.put(marketAreaFrom,
										new LinkedHashMap<>());
							}
							exchangeForecastMarketCoupling.get(marketAreaFrom).put(marketAreaTo,
									new ExchangeForecastMarketCoupling(this, marketAreaFrom,
											marketAreaTo, startDayOfYear));
						}
					}
				}
			}

			// Module needs some data for calibration
			if (!Date.isFirstYear()
					|| (Date.isFirstYear() && (Date.getDayOfYear() >= startDayOfYear))) {
				for (final MarketArea marketAreaFrom : exchangeForecastMarketCoupling.keySet()) {
					if (hourlyExchangeForecast.get(marketAreaFrom) == null) {
						hourlyExchangeForecast.put(marketAreaFrom, new LinkedHashMap<>());
					}
					for (final MarketArea marketAreaTo : exchangeForecastMarketCoupling
							.get(marketAreaFrom).keySet()) {
						// New estimate of model
						exchangeForecastMarketCoupling.get(marketAreaFrom).get(marketAreaTo)
								.estimateModel();
						// Get exchange forecast
						hourlyExchangeForecast.get(marketAreaFrom).put(marketAreaTo,
								new ArrayList<>());
						hourlyExchangeForecast.get(marketAreaFrom).get(marketAreaTo)
								.addAll(exchangeForecastMarketCoupling.get(marketAreaFrom)
										.get(marketAreaTo)
										.getExchangeForecast(FORECAST_LENGTH_LONG));
						if (hourlyExchangeForecast.get(marketAreaTo) == null) {
							hourlyExchangeForecast.put(marketAreaTo, new LinkedHashMap<>());
						}
						hourlyExchangeForecast.get(marketAreaTo).put(marketAreaFrom,
								new ArrayList<>());
						for (int hour = 0; hour < FORECAST_LENGTH_LONG; hour++) {
							hourlyExchangeForecast.get(marketAreaTo).get(marketAreaFrom)
									.add(-hourlyExchangeForecast.get(marketAreaFrom)
											.get(marketAreaTo).get(hour));
						}
					}
				}

			} else {

				// Initialize module for market coupling exchange forecast
				if ((Date.isFirstYear() && (Date.getDayOfYear() == startDayOfYear))) {
					// List of already considered market areas, since each
					// interconnector only needs to be forecasted in one
					// direction
					final List<MarketArea> marketAreasAlreadyConsidered = new ArrayList<>();

					for (final MarketArea marketAreaFrom : marketAreas) {
						marketAreasAlreadyConsidered.add(marketAreaFrom);
						for (final MarketArea marketAreaTo : getCapacitiesData()
								.getMarketAreasInterconnected(marketAreaFrom)) {
							if (!marketAreasAlreadyConsidered.contains(marketAreaTo)) {
								if (exchangeForecastMarketCoupling.get(marketAreaFrom) == null) {
									exchangeForecastMarketCoupling.put(marketAreaFrom,
											new LinkedHashMap<>());
								}
								exchangeForecastMarketCoupling.get(marketAreaFrom).put(marketAreaTo,
										new ExchangeForecastMarketCoupling(this, marketAreaFrom,
												marketAreaTo, startDayOfYear));
							}
						}
					}
				}

				// Module needs some data for calibration
				if (!Date.isFirstYear()
						|| (Date.isFirstYear() && (Date.getDayOfYear() >= startDayOfYear))) {

					for (final MarketArea marketAreaFrom : exchangeForecastMarketCoupling
							.keySet()) {
						if (hourlyExchangeForecast.get(marketAreaFrom) == null) {
							hourlyExchangeForecast.put(marketAreaFrom, new LinkedHashMap<>());
						}
						for (final MarketArea marketAreaTo : exchangeForecastMarketCoupling
								.get(marketAreaFrom).keySet()) {

							// New estimate of model
							exchangeForecastMarketCoupling.get(marketAreaFrom).get(marketAreaTo)
									.estimateModel();

							// Get exchange forecast
							hourlyExchangeForecast.get(marketAreaFrom).put(marketAreaTo,
									new ArrayList<>());
							hourlyExchangeForecast.get(marketAreaFrom).get(marketAreaTo)
									.addAll(exchangeForecastMarketCoupling.get(marketAreaFrom)
											.get(marketAreaTo)
											.getExchangeForecast(FORECAST_LENGTH_LONG));

							if (hourlyExchangeForecast.get(marketAreaTo) == null) {
								hourlyExchangeForecast.put(marketAreaTo, new LinkedHashMap<>());
							}

							hourlyExchangeForecast.get(marketAreaTo).put(marketAreaFrom,
									new ArrayList<>());

							for (int hour = 0; hour < FORECAST_LENGTH_LONG; hour++) {
								hourlyExchangeForecast.get(marketAreaTo).get(marketAreaFrom)
										.add(-hourlyExchangeForecast.get(marketAreaFrom)
												.get(marketAreaTo).get(hour));
							}

						}
					}
				}
			}
		}
		// Calculate total exchange forecast for each market area based on
		// forecast of the different interconnectors
		for (final MarketArea marketArea : marketAreas) {
			final Map<Integer, Float> hourlyExchangeForecastMarketArea = new LinkedHashMap<>();
			// Check if exchange forecast module has already been
			// initialized
			if (!Date.isFirstYear()
					|| (Date.isFirstYear() && (Date.getDayOfYear() >= startDayOfYear)) || (Settings
							.getDayAheadPriceForecastType() == ForecastTypeDayAhead.OPTIMIZATION)) {
				for (int hour = 0; hour < FORECAST_LENGTH_LONG; hour++) {
					final int hourOfYear = Date.getHourOfYearFromHourOfDay(hour);
					float tempExchangeForecast = 0f;
					if (hourlyExchangeForecast.containsKey(marketArea)) {
						// Static demand of not coupled market areas
						final float exchangeStatic = marketArea.getExchange()
								.getHourlyFlowForecast(Date.getYear(), hourOfYear);
						tempExchangeForecast += exchangeStatic;
						for (final MarketArea marketAreaConnected : hourlyExchangeForecast
								.get(marketArea).keySet()) {
							tempExchangeForecast += hourlyExchangeForecast.get(marketArea)
									.get(marketAreaConnected).get(hour);
						}
					}
					hourlyExchangeForecastMarketArea.put(hour, tempExchangeForecast);
				}
			} else {
				for (int hour = 0; hour < FORECAST_LENGTH_LONG; hour++) {
					hourlyExchangeForecastMarketArea.put(hour, 0f);
				}
			}
			// Store exchange forecast for all market areas centrally
			hourlyExchangeForecastAllMarketAreas.put(marketArea, hourlyExchangeForecastMarketArea);

			for (final SupplyTrader supplyTrader : marketArea.getSupplyTrader()) {
				supplyTrader.setExchangeForecast(hourlyExchangeForecastMarketArea);
			}
		}

	}

	private void calculateStorageOperationForecast() {
		// Initialize module for storage operation forecast
		// Regression model for first year
		final int startDayOfYear = 3;
		if (Date.isFirstYear() && (Date.getDayOfYear() == startDayOfYear)) {
			for (final MarketArea marketArea : getMarketAreas()) {
				storageOperationForecast.put(marketArea,
						new StorageOperationForecast(this, marketArea));
			}
		}

		// Module needs some data for calibration
		if (!Date.isFirstYear()
				|| (Date.isFirstYear() && (Date.getDayOfYear() >= startDayOfYear))) {

			getMarketAreas().parallelStream().forEach(marketArea -> {
				storageOperationForecast.get(marketArea).estimateModel();
			});
			for (final MarketArea marketArea : getMarketAreas()) {
				// New estimate of model

				int dayOfYear = Date.getDayOfYear() - 1;
				if (dayOfYear == 0) {

					dayOfYear = Date.DAYS_PER_YEAR;
				}

				// Get storage operation forecast
				hourlyStorageOperationForecastAllMarketAreas.put(marketArea, new ArrayList<>());

				final List<Float> tempHourlyStorageOperationForecast = storageOperationForecast
						.get(marketArea).getStorageOperationForecast(FORECAST_LENGTH_LONG);
				for (int hour = 0; hour < FORECAST_LENGTH_LONG; hour++) {
					hourlyStorageOperationForecastAllMarketAreas.get(marketArea)
							.add(-tempHourlyStorageOperationForecast.get(hour));
				}

			}
		} else {
			for (final MarketArea marketArea : getMarketAreas()) {
				hourlyStorageOperationForecastAllMarketAreas.put(marketArea, new ArrayList<>());
				for (int hour = 0; hour < FORECAST_LENGTH_LONG; hour++) {
					hourlyStorageOperationForecastAllMarketAreas.get(marketArea).add(0f);
				}

			}
		}
	}

	private Callable<Void> clearMarket(int hourOfDay) throws GRBException {
		return () -> {
			try {
				final String threadName = "Clear Markets for hour of day: " + hourOfDay;
				Thread.currentThread().setName(threadName);
				/* 2. Get bids from market areas */
				getBidsSimple(hourOfDay);
				final Map<MarketArea, List<Bid>> simpleBidsTemp = new LinkedHashMap<>();
				for (final MarketArea marketArea : marketAreas) {
					simpleBidsTemp.put(marketArea, Collections
							.unmodifiableList(simpleBids.get(marketArea).get(hourOfDay)));
				}
				/* 3. Clear market */

				// Market coupling
				MarketCouplingHourly.marketCouplingAlgorithmHourly(hourOfDay, marketAreas,
						Collections.unmodifiableMap(simpleBidsTemp), this, capacitiesData);
			} catch (final Exception e) {
				logger.error("Clear market problems", e);
			}
			return null;
		};
	}

	private Callable<Void> determineMarginalBids(MarketArea marketArea) {
		return () -> {
			try {
				// Add marginal bid for hour, if nothing exists
				for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
					if (marketArea.getElectricityResultsDayAhead()
							.getMarginalBidHourOfDay(hourOfDay) != null) {
						continue;
					}
					final MarginalBid marginalBid = getMarginalBidForMarketArea(marketArea,
							hourOfDay);
					marketArea.getElectricityResultsDayAhead().setMarginalBidHourOfDay(hourOfDay,
							marginalBid);
				}
			} catch (final Exception e) {
				logger.error("Problems determine maginal bids", e);
			}
			return null;
		};
	}

	/**
	 * Execute market coupling for registered market areas. Method is called
	 * daily in PowerMarkets.
	 */
	public void execute() {
		try {
			logger.info("Day-ahead market coupling " + marketAreas);

			/* 0a. Market areas need to register */
			// Market areas are registered during their initialization

			/* 0b. Initialize market coupling operator */
			// initialize() is called at the beginning of the simulation

			/* 0c. Initialize logging at the beginning of each year */
			if (Date.isFirstDayOfYear()) {
				pricesMarketArea.logInitializePrices();
			}
			final long time1 = System.currentTimeMillis() / 1000;
			initializeMarketCouplingDaily();
			final long time2 = System.currentTimeMillis() / 1000;
			t1 += time2 - time1;

			/* 0d. Forecast exchange flows between market area */
			calculateExchangeFlowForecast();

			/* 0e. Forecast storage operation in all market areas */
			calculateStorageOperationForecast();

			// 1. Pre-market coupling operations (initialize auction, get
			// bids, process bids) in each market area
			final Collection<Callable<Void>> tasksPreCouplingOperations = new ArrayList<>();
			for (final MarketArea marketArea : marketAreas) {
				tasksPreCouplingOperations
						.add(marketArea.getDayAheadMarketOperator().preMarketClearingOperations());
			}
			Concurrency.executeConcurrently(tasksPreCouplingOperations);

			final long time3 = System.currentTimeMillis() / 1000;
			t2 += time3 - time2;

			try {

				final Collection<Callable<Void>> tasksClearing = new ArrayList<>();
				for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
					tasksClearing.add(clearMarket(hourOfDay));
				}
				Concurrency.executeConcurrently(tasksClearing);
				final long time4 = System.currentTimeMillis() / 1000;
				final long timeGurobiSolver = time4 - time3;
				t3 += timeGurobiSolver;

				/* 4. Process results */
				processResults();

				/* 5. Post-market coupling operations in market areas */
				final Collection<Callable<Void>> tasksPostCouplingOperations = new ArrayList<>();
				final Collection<Callable<Void>> tasksDetermineMarginalBids = new ArrayList<>();
				for (final MarketArea marketArea : marketAreas) {
					tasksPostCouplingOperations.add(
							marketArea.getDayAheadMarketOperator().postMarketClearingOperations());
					tasksDetermineMarginalBids.add(determineMarginalBids(marketArea));
				}
				Concurrency.executeConcurrently(tasksPostCouplingOperations);
				Concurrency.executeConcurrently(tasksDetermineMarginalBids);

				if (Date.isFirstDayOfYear()) {
					logInitializePriceForecast();
					logInitializeStorageForecast();
				}
				logPriceForecast();
				logStorageForecast();
				if (Date.isLastDayOfYear()) {
					LoggerCSV.close(logIDPriceForecast);
					LoggerCSV.close(logIDStorageForecast);
				}

				final long time5 = System.currentTimeMillis() / 1000;
				t4 += time5 - time4;

				logger.info("Market coupling: initMarketCoupling " + t1
						+ ", preMarketCouplingOperations " + t2 + ", marketCoupling " + t3
						+ ", postMarketCouplingOperations " + t4 + ", timeGurobiSolver "
						+ timeGurobiSolver);
				final StringBuffer marketClearingPrices = new StringBuffer(
						"Market clearing prices (hour 0): ");
				int index = 0;
				for (final MarketArea marketArea : marketAreas) {
					marketClearingPrices.append(marketArea.getInitials() + " " + Statistics
							.round(marketClearingPricesDaily.get(marketArea).get(0), 2));
					if (index < (marketAreas.size() - 1)) {
						marketClearingPrices.append(" / ");
					}
					index++;
				}
				logger.info(marketClearingPrices.toString());

			}
			// Catch exception from optimization
			catch (final GRBException e) {
				logger.error("Market coupling operator could not clear the market: " + e, e);
				logger.error(
						"Fall-back mechanism takes effect: market areas are cleared as if there is no market coupling on day "
								+ Date.getDayOfYear() + " in year " + Date.getYear() + ".");

				/*
				 * #3. Reset accepted bids and flows (in previous hours before failure)
				 */
				resetAcceptedBidsAndFlows();

				for (final MarketArea marketArea : marketAreas) {

					// Fall-back mechanism could also be implemented on an
					// hourly basis instead on a daily basis as it is currently
					// done

					/* #4. Fall-back solution when optimization fails */
					// Clearing of all market areas as if there is no market
					// coupling
					marketArea.getDayAheadMarketOperator().execute();
				}
			}

			/* 6. Logging */
			// Add data in logger
			pricesMarketArea.logMarketPrices();
			// Write log file at the end of each year
			if (Date.isLastDayOfYear()) {
				pricesMarketArea.writeFile();
			}
		} catch (final Exception e) {
			logger.error("Error executing marketcoupling", e);
		}
	}

	/**
	 * Get bids from local market operators
	 * 
	 * @param hourOfDay
	 *            [0..23]
	 */
	private void getBidsSimple(int hourOfDay) {
		// Loop all coupled market areas
		for (final MarketArea marketArea : marketAreas) {
			if (marketArea.getDayAheadMarketOperator().getBidPoints().get(hourOfDay).isEmpty()) {
				logger.warn("No day ahead bids in market area" + marketArea.getName());
			} else {
				simpleBids.get(marketArea).get(hourOfDay).addAll(
						marketArea.getDayAheadMarketOperator().getBidPoints().get(hourOfDay));
				// Sort bids
				// 1. by lowest price
				final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
						b2.getPrice());
				// 2. by highest volume
				final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
						* Float.compare(b1.getVolume(), b2.getVolume());
				// 4. by UnitID
				final Comparator<Bid> compUnit = (Bid b1, Bid b2) -> Integer.compare(b1.getUnitID(),
						b2.getUnitID());
				// 4. by identifier
				final Comparator<Bid> compIdentifier = (Bid b1, Bid b2) -> Integer
						.compare(b1.getIdentifier(), b2.getIdentifier());
				Collections.sort(simpleBids.get(marketArea).get(hourOfDay),
						compPrice.thenComparing(compVolume).thenComparing(compUnit)
								.thenComparing(compIdentifier));
			}

		}
	}

	/** Get capacity data object */
	public Capacities getCapacitiesData() {
		return capacitiesData;
	}

	/** Get congestion revenue result object */
	public CongestionRevenue getCongestionRevenue() {
		return congestionRevenue;
	}

	/** Get market coupling operator */
	public ExchangeFlows getExchangeFlows() {
		return exchangeFlows;
	}

	public Map<MarketArea, Map<MarketArea, ExchangeForecastMarketCoupling>> getExchangeForecastMarketCoupling() {
		return exchangeForecastMarketCoupling;
	}

	public ExchangeForecastMarketCoupling getExchangeForecastMarketCoupling(
			MarketArea marketAreaFrom, MarketArea marketAreaTo) {
		return exchangeForecastMarketCoupling.get(marketAreaFrom).get(marketAreaTo);
	}

	public Map<MarketArea, Map<Integer, Float>> getHourlyExchangeForecastAllMarketAreas() {
		return hourlyExchangeForecastAllMarketAreas;
	}

	public Map<MarketArea, List<Float>> getHourlyStorageOperationForecastAllMarketAreas() {
		return hourlyStorageOperationForecastAllMarketAreas;
	}

	private MarginalBid getMarginalBidForMarketArea(MarketArea marketAreaForMarginalBid,
			int hourOfDay) {

		MarginalBid marginalBid = new MarginalBid();
		for (final MarketArea marketArea : marketAreas) {
			if (marketArea.getElectricityResultsDayAhead()
					.getMarginalBidHourOfDay(hourOfDay) == null) {
				continue;
			}
			final float marginalBidPrice = marketArea.getElectricityResultsDayAhead()
					.getMarginalBidHourOfDay(hourOfDay).getPrice();
			final float marketClearingPrice = marketAreaForMarginalBid
					.getElectricityResultsDayAhead().getHourlyPriceOfDay(hourOfDay);
			final float priceDifference = Math.abs(marginalBidPrice - marketClearingPrice);
			if (priceDifference < .001) {
				// Return corresponding MarginalBid
				marginalBid = marketArea.getElectricityResultsDayAhead()
						.getMarginalBidHourOfDay(hourOfDay);
			}
		}
		return marginalBid;
	}

	/** Get list of all coupled market areas */
	public Set<MarketArea> getMarketAreas() {
		return new LinkedHashSet<>(marketAreas);
	}

	/** Get list of all coupled market areas */
	public List<MarketArea> getMarketAreasList() {
		return marketAreas;
	}

	/**
	 * Get hourly market clearing prices for current day and specified market
	 * area
	 * 
	 * @param marketArea
	 * @return list of hourly market clearing prices [EUR/MWh]
	 */
	private List<Float> getMarketClearingPricesDaily(MarketArea marketArea) {
		final List<Float> marketClearingPrices = new ArrayList<>();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			marketClearingPrices.add(marketClearingPricesDaily.get(marketArea).get(hourOfDay));
		}
		return marketClearingPrices;
	}

	/**
	 * Get hourly market clearing volumes for current day and specified market
	 * area
	 * 
	 * @param marketArea
	 * @return list of hourly market clearing volumes [MWh]
	 */
	private List<Float> getMarketClearingVolumesDaily(MarketArea marketArea) {
		final List<Float> marketClearingVolumes = new ArrayList<>();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			marketClearingVolumes.add(marketClearingVolumeDaily.get(marketArea).get(hourOfDay));
		}
		return marketClearingVolumes;
	}

	/** Get path name of market coupling folder */
	public String getMarketCouplingFolderPath() {
		return marketCouplingFolderPath;
	}

	/** Get market prices result object */
	public PricesMarketArea getPricesMarketArea() {
		return pricesMarketArea;
	}

	public Map<MarketArea, StorageOperationForecast> getStorageOperationForecast() {
		return storageOperationForecast;
	}

	/**
	 * Initialize relevant fields and load data for market coupling operations
	 */
	public void initialize(PowerMarkets model) {
		logger.info("Initialize " + MarketCouplingOperator.class.getSimpleName());
		initializeFields(model);
		loadData(model);
	}

	/**
	 * Initialize fields necessary for market coupling algorithm and results
	 * 
	 * @param model
	 */
	private void initializeFields(PowerMarkets model) {
		// Initialize result and logging objects
		congestionRevenue = new CongestionRevenue(marketAreas);
		exchangeFlows = new ExchangeFlows(marketAreas);
		pricesMarketArea = new PricesMarketArea(model);

		// Create log folder
		final File folder = new File(marketCouplingFolderPath);
		folder.mkdirs();
	}

	/** Initialize daily relevant fields for market coupling operations */
	private void initializeMarketCouplingDaily() {
		// Loop all coupled market areas
		for (final MarketArea marketArea : marketAreas) {

			// Map for daily prices
			marketClearingPricesDaily.put(marketArea, new ConcurrentHashMap<>());

			// Map for bid points
			simpleBids.put(marketArea, new LinkedHashMap<>());

			for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
				// Simple bids
				simpleBids.get(marketArea).put(hourOfDay, new ArrayList<>());
			}
			// Map for daily clearing volume
			marketClearingVolumeDaily.put(marketArea, new ConcurrentHashMap<>());
		}
	}

	/**
	 * Load data for market coupling (interconnection capacities, exogenous
	 * exchange)
	 * 
	 * @param model
	 */
	private void loadData(PowerMarkets model) {
		// Load interconnection capacities from database
		if (capacitiesData == null) {
			capacitiesData = new Capacities();
		}
		capacitiesData.loadData(this, model);

		// Load exogenous exchange data for coupled market areas
		for (final MarketArea marketArea : marketAreas) {
			marketArea.getExchange().loadExchangeDataMarketCoupling(model);
		}
	}

	private void logInitializePriceForecast() {
		final String fileName = "Price_forecast_" + Date.getYear() + Settings.LOG_FILE_SUFFIX_CSV;
		final String unitLine = "marketArea;year;hourOfYear;opt;"
				+ Settings.getDayAheadPriceForecastType() + ";sim";
		final String titleLine = "#";
		final String description = "#";
		logIDPriceForecast = LoggerCSV.newLogObject(Folder.MAIN, fileName, description, titleLine,
				unitLine, "Price_forecast");
	}

	private void logInitializeStorageForecast() {
		final String fileName = "Storage_forecast_" + Date.getYear() + Settings.LOG_FILE_SUFFIX_CSV;
		final String unitLine = "marketArea;year;hourOfYear;forecast;sim";
		final String titleLine = "#";
		final String description = "#";
		logIDStorageForecast = LoggerCSV.newLogObject(Folder.MAIN, fileName, description, titleLine,
				unitLine, "Storage_forecast");
	}

	private void logPriceForecast() {
		for (final MarketArea marketArea : marketAreas) {
			List<Float> priceForecasts = new ArrayList<>();
			for (final SupplyTrader supplyTrader : marketArea.getSupplyTrader()) {

				priceForecasts = supplyTrader.getDayAheadPriceForecast();
				break;

			}

			for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
				String dataLine = "";
				dataLine = dataLine + marketArea + ";" + Date.getYear() + ";"
						+ Date.getHourOfYearFromHourOfDay(hourOfDay) + ";" + ";"
						+ priceForecasts.get(hourOfDay) + ";"
						+ marketClearingPricesDaily.get(marketArea).get(hourOfDay) + ";";
				LoggerCSV.writeLine(logIDPriceForecast, dataLine);
			}
		}
	}

	private void logStorageForecast() {
		for (final MarketArea marketArea : marketAreas) {
			for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
				String dataLine = "";
				dataLine = dataLine + marketArea + ";" + Date.getYear() + ";"
						+ Date.getHourOfYearFromHourOfDay(hourOfDay) + ";"
						+ hourlyStorageOperationForecastAllMarketAreas.get(marketArea)
								.get(hourOfDay)
						+ ";" + (-marketArea.getElectricityProduction().getElectricityPumpedStorage(
								Date.getYear(), Date.getFirstHourOfToday() + hourOfDay))
						+ ";";
				LoggerCSV.writeLine(logIDStorageForecast, dataLine);
			}
		}
	}

	/** Process market clearing results (prices, volumes, congestion revenue) */
	private void processResults() {
		// Send prices and volumes to market areas
		for (final MarketArea marketArea : marketAreas) {
			marketArea.getDayAheadMarketOperator()
					.setMarketClearingPrice(getMarketClearingPricesDaily(marketArea));
			marketArea.getDayAheadMarketOperator()
					.setMarketClearingVolumes(getMarketClearingVolumesDaily(marketArea));
		}
		// Set congestion revenue
		congestionRevenue.setCongestionRevenue(marketClearingPricesDaily, exchangeFlows);
	}

	/**
	 * Reset all those bids which have been accepted when the bid list was cut
	 * and determined flows in hours before market clearing fails
	 * 
	 * @return
	 */
	public void resetAcceptedBidsAndFlows() {
		// Loop hours
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			// Reset wrongly accepted bids
			for (final MarketArea marketArea : marketAreas) {
				for (final Bid bidPoint : simpleBids.get(marketArea).get(hourOfDay)) {
					bidPoint.setVolumeAccepted(0);
				}
			}
			// Reset wrongly modeled flows between coupled market areas
			final double[][] noFlows = new double[marketAreas.size()][marketAreas.size()];
			try {
				setFlows(noFlows, hourOfDay);
			} catch (final GRBException e) {
				logger.error("While resetting accepted bids and flows", e);
			}
		}
	}

	/**
	 * Set accepted volume for each optimized bid
	 * 
	 * @param hourOfDay
	 * @param acceptResults
	 * @param model
	 * @param accept
	 * @throws GRBException
	 */
	public void setAcceptedVolume(int hourOfDay, GRBModel model, GRBVar[][] accept)
			throws GRBException {

		for (final MarketArea marketArea : marketAreas) {

			/* Set accepted volume for each optimized bid */
			// Loop all optimized bids and set accepted volume
			for (int bid = 0; bid < simpleBids.get(marketArea).get(hourOfDay).size(); bid++) {
				final Bid simpleBid = simpleBids.get(marketArea).get(hourOfDay).get(bid);
				// Save cleared volume: accept * volume
				final double acceptanceRate = model.get(GRB.DoubleAttr.X,
						accept[marketArea.getIdMarketCoupling() - 1])[bid];
				final double volume = acceptanceRate * simpleBid.getVolume();
				simpleBid.setVolumeAccepted((float) volume);
			}

			/* Check balance between sell and ask bids */
			// Calculate total market clearing volume for current hour and
			// market area
			double marketClearingVolumeSellTemp = 0f;
			double marketClearingVolumeAskTemp = 0f;
			for (final Bid bidPoint : simpleBids.get(marketArea).get(hourOfDay)) {
				if (bidPoint.getBidType() == BidType.SELL) {
					marketClearingVolumeSellTemp += bidPoint.getVolumeAccepted();
				} else {
					marketClearingVolumeAskTemp += bidPoint.getVolumeAccepted();
				}
			}
			// Take flows from market coupling into account
			final double marketAreaFlow = exchangeFlows.getHourlyFlow(marketArea, hourOfDay);
			if (marketAreaFlow < 0) {
				marketClearingVolumeAskTemp -= Math.abs(marketAreaFlow);
			} else {
				marketClearingVolumeSellTemp -= Math.abs(marketAreaFlow);
			}
			// Check balance
			if (Math.abs(marketClearingVolumeSellTemp - marketClearingVolumeAskTemp) > 1) {
				logger.error("Ask and sell volume are not balanced in hour " + hourOfDay
						+ " on day " + Date.getDayOfTotal() + " in market area "
						+ marketArea.getName() + "! (Difference: "
						+ (marketClearingVolumeSellTemp - marketClearingVolumeAskTemp) + ")");
			}
			marketClearingVolumeDaily.get(marketArea).put(hourOfDay,
					(float) marketClearingVolumeSellTemp);
		}
	}

	public void setFlows(double[][] flowsBetweenMarketAreas, final int hourOfDay)
			throws GRBException {
		final double[][] flowsBetweenMarketAreasTemp = flowsBetweenMarketAreas;
		// Get fromMarketArea
		for (final MarketArea fromMarketArea : marketAreas) {
			// Get toMarketArea
			for (final MarketArea toMarketArea : marketAreas) {
				final int idFromMarketArea = fromMarketArea.getIdMarketCoupling() - 1;
				final int idToMarketArea = toMarketArea.getIdMarketCoupling() - 1;

				if (fromMarketArea.equals(toMarketArea)) {
					// No "internal" flow
					if (flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] != 0) {
						logger.error(
								"Result shows direct flow from market area into same market are ("
										+ fromMarketArea + ") in hour " + hourOfDay + " on day "
										+ Date.getDayOfYear() + ". Market coupling cancelled.");
						throw new GRBException();
					} else {
						continue;
					}
				}

				// Netting of flows between fromMarketArea and toMarketArea
				if (flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] < flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea]) {
					flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea] -= flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea];
					flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] = 0;
				} else {
					flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] -= flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea];
					flowsBetweenMarketAreasTemp[idToMarketArea][idFromMarketArea] = 0;
				}

				/* Check consistency of flows */
				// Compare with available capacity (throw exception if net flow
				// larger than capacity)
				final float availableCapacity = capacitiesData.getInterconnectionCapacityHour(
						fromMarketArea, toMarketArea, Date.getYear(),
						Date.getHourOfYearFromHourOfDay(hourOfDay));
				if (!fromMarketArea.equals(toMarketArea)
						&& (flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea] > availableCapacity)) {
					logger.error("Resulting flow from " + fromMarketArea.toString() + " to "
							+ toMarketArea.toString() + " exceeds available capacity in hour "
							+ hourOfDay + " on day " + Date.getDayOfYear()
							+ ". Market coupling cancelled.");
					throw new GRBException();
				}

				// Set flow between market areas
				exchangeFlows.setHourlyFlow(fromMarketArea, toMarketArea, hourOfDay,
						flowsBetweenMarketAreasTemp[idFromMarketArea][idToMarketArea]);
			}
		}
	}

	/**
	 * Set market clearing price for all market areas
	 * 
	 * @param marketClearingPricesDailyArray
	 *            optimization result
	 * @param hourOfDay
	 *            [0...23]
	 */
	public void setMarketClearingPricesDaily(double[] marketClearingPricesDailyArray,
			int hourOfDay) {
		for (final MarketArea marketArea : marketAreas) {

			// Check whether price is one of the extreme prices; if so, set
			// price determined by solver to the constant value (max or min
			// price allowed) in order to reduce rounding errors
			float marketClearingPrice = (float) marketClearingPricesDailyArray[marketArea
					.getIdMarketCoupling() - 1];
			final float minPriceAllowed = marketArea.getDayAheadMarketOperator()
					.getMinPriceAllowed();
			final float maxPriceAllowed = marketArea.getDayAheadMarketOperator()
					.getMaxPriceAllowed();

			if (Math.round(marketClearingPrice) <= minPriceAllowed) {
				marketClearingPrice = minPriceAllowed;
			} else if (Math.round(marketClearingPrice) >= maxPriceAllowed) {
				marketClearingPrice = maxPriceAllowed;
			}

			marketClearingPricesDaily.get(marketArea).put(hourOfDay, marketClearingPrice);
		}
	}

	/** Write bid points to console */
	public void writeBidPoints(int analyzedDay, int currentHour, int analyzedHour) {
		if ((Date.getDayOfTotal() == analyzedDay) && (currentHour == analyzedHour)) {
			logger.debug("Writing bid points on day" + analyzedDay + " in hour " + analyzedHour);
			logger.info(";" + "Type;" + "Bidder;" + "Price;" + "Volume;" + "AcceptedVolume;");
			for (final MarketArea marketArea : marketAreas) {
				logger.debug(marketArea.getName());
				for (final Bid bidPoint : simpleBids.get(marketArea).get(currentHour)) {
					logger.debug(";" + bidPoint.getType() + ";" + bidPoint.getTraderType() + ";"
							+ bidPoint.getPrice() + ";" + bidPoint.getVolume() + ";"
							+ bidPoint.getVolumeAccepted() + ";");
				}
			}
			logger.debug("Writing bid points completed");
		}
	}
}