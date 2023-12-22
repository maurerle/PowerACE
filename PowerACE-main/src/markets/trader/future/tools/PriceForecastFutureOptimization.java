package markets.trader.future.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.exchange.Capacities;
import data.storage.PumpStoragePlant;
import gurobi.GRBException;
import markets.bids.Bid;
import markets.trader.TraderType;
import markets.trader.spot.hydro.SeasonalStorageTrader;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.Investment;
import supply.invest.Investor;
import supply.powerplant.CostCap;
import supply.powerplant.PlantOption;
import tools.logging.LoggerXLSX;
import tools.types.FuelName;

/**
 * @author Florian Zimmermann
 */
public final class PriceForecastFutureOptimization extends PriceForecastFuture {

	// One thread is enough for logging
	private static final ExecutorService exec = Executors.newSingleThreadExecutor();

	private static Map<MarketArea, Map<Integer, Map<Integer, Float>>> forecastWithAdditionalPlants;
	/**
	 * calcMarketCouplingForecast() and recalculate() is possible to be called
	 * parallel
	 */
	private static ReentrantLock lock = new ReentrantLock();

	private static final boolean logFutureMeritOrder = false;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(PriceForecastFutureOptimization.class.getName());

	private static final OptimizationPeriodType optimizationPeriodType = OptimizationPeriodType.WEEKLY;
	private static Queue<Map<MarketArea, Map<MarketArea, Map<Integer, Float>>>> resultsFlows = new LinkedBlockingQueue<>();
	private static Queue<Map<MarketArea, Map<Integer, Map<Integer, Float>>>> resultsQueue = new LinkedBlockingQueue<>();

	private static Map<MarketArea, Map<Integer, Float>> surplusStartup = new ConcurrentHashMap<>();

	private static final boolean useSurplusStartup = false;

	/**
	 * Only for seasonal forecast
	 * 
	 * @param results
	 */
	protected static void addFlows(Map<MarketArea, Map<MarketArea, Map<Integer, Float>>> results) {
		resultsFlows.add(results);
	}

	protected static void addToQueue(Map<MarketArea, Map<Integer, Map<Integer, Float>>> results,
			boolean withAdditioalPlants) {
		resultsQueue.add(results);
	}

	/**
	 * Calculate forecast without plants
	 */
	protected static Map<MarketArea, Map<Integer, Map<Integer, Float>>> calcMarketCouplingForecastOptimization(
			Set<MarketArea> marketAreas) {
		return calcMarketCouplingForecastOptimizationWithPlants(marketAreas, new ArrayList<>());
	}

	protected static Map<MarketArea, Map<Integer, Map<Integer, Float>>> calcMarketCouplingForecastOptimizationWithPlants(
			Set<MarketArea> marketAreas, List<Investment> additionalPlants) {
		try {
			// Log settings
			logger.info("Get long-term price forecast with optimization.");
			final long timeStart = System.currentTimeMillis() / 1000;
			// could
			lock.lock();
			if (!additionalPlants.isEmpty()) {
				logger.info(
						"Calculate long-term price forecast with optimization and capactiy option.");
				forecastWithAdditionalPlants = new ConcurrentHashMap<>();
				marketCouplingForecastOptimization(marketAreas, additionalPlants);
				final long timeEnd = System.currentTimeMillis() / 1000;
				logger.info("End long-term price focecast with additional plants. Time: "
						+ (timeEnd - timeStart) + " s.");
				return forecastWithAdditionalPlants;
			}

			if (!forwardPrices.containsKey(Date.getYear())
					|| forwardPrices.get(Date.getYear()).isEmpty()) {
				logger.info("Calculate long-term price forecast with optimization.");
				marketCouplingForecastOptimization(marketAreas, additionalPlants);

			}
			final long timeEnd = System.currentTimeMillis() / 1000;
			logger.info("End long-term price focecast. Time: " + (timeEnd - timeStart) + " s.");
			return forwardPrices.get(Date.getYear());
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			lock.unlock();
		}
		return null;
	}

	private static Callable<Void> calculateForecastPricesHourly(Set<MarketArea> marketAreas,
			int year, int hourOfYear, Map<MarketArea, Float> futureDemand,
			Map<MarketArea, Float> futureRenewableLoad,
			Map<MarketArea, List<CostCap>> futureMeritOrder,
			Map<MarketArea, List<PumpStoragePlant>> futureStorageUnits,
			Map<MarketArea, Map<PlantOption, Integer>> newPlants,
			Map<MarketArea, Map<PlantOption, Integer>> newStorages,
			Map<MarketArea, Float> startupSurplus) throws GRBException {
		return () -> {
			try {
				final String threadName = "Priceforecast for year " + year + ", hour " + hourOfYear;

				Thread.currentThread().setName(threadName);
				Capacities exchangeCapacity = null;
				final Map<MarketArea, List<Bid>> bidPoints = new HashMap<>();
				for (final MarketArea marketArea : marketAreas) {
					bidPoints.put(marketArea, getHourlyForecastBids(marketArea, year, hourOfYear,
							futureMeritOrder.get(marketArea), futureDemand.get(marketArea),
							futureRenewableLoad.get(marketArea), startupSurplus.get(marketArea),
							futureStorageUnits.get(marketArea), newPlants.get(marketArea),
							newStorages.get(marketArea)));
					exchangeCapacity = marketArea.getMarketCouplingOperator().getCapacitiesData();
				}

				final boolean withAdditioalPlants = !newStorages.isEmpty() || !newPlants.isEmpty();
				// Market coupling
				MarketCouplingForecast.marketCouplingAlgorithmHourly(year, hourOfYear, marketAreas,
						Collections.unmodifiableMap(bidPoints), exchangeCapacity,
						withAdditioalPlants);

			} catch (final Exception e) {
				logger.error("Clear market problems hourly", e);
			}
			return null;
		};
	}

	private static Callable<Void> calculateForecastPricesWithStorageMonthly(
			Set<MarketArea> marketAreas, int year, int monthOfYear,
			Map<MarketArea, Map<Integer, Float>> futureDemand,
			Map<MarketArea, Map<Integer, Float>> futureRenewableLoad,
			Map<MarketArea, List<CostCap>> futureMeritOrder,
			Map<MarketArea, Map<Integer, Float>> seasonalStorage,
			Map<MarketArea, List<PumpStoragePlant>> futureStorageUnits,
			Map<MarketArea, Map<PlantOption, Integer>> newPlants,
			Map<MarketArea, Map<PlantOption, Integer>> newStorages,
			Map<MarketArea, Float> startupSurplus) throws GRBException {
		return () -> {
			try {
				final String threadName = "Priceforecast for year " + year + ", month "
						+ monthOfYear;

				Thread.currentThread().setName(threadName);
				Capacities exchangeCapacity = null;
				for (final MarketArea marketArea : marketAreas) {
					exchangeCapacity = marketArea.getMarketCouplingOperator().getCapacitiesData();
					break;
				}

				// Market coupling
				MarketCouplingForecast.marketCouplingForecastStorage(OptimizationPeriodType.MONTHLY,
						year, monthOfYear, marketAreas, futureDemand, futureRenewableLoad,
						futureMeritOrder, seasonalStorage, futureStorageUnits, newPlants,
						newStorages, exchangeCapacity, startupSurplus);

			} catch (final Exception e) {
				logger.error("Clear market problems monthly", e);
			}
			return null;
		};
	}

	private static Callable<Void> calculateForecastPricesWithStorageWeekly(
			Set<MarketArea> marketAreas, int year, int weekOfYear,
			Map<MarketArea, Map<Integer, Float>> futureDemand,
			Map<MarketArea, Map<Integer, Float>> futureRenewableLoad,
			Map<MarketArea, List<CostCap>> futureMeritOrder,
			Map<MarketArea, Map<Integer, Float>> seasonalStorage,
			Map<MarketArea, List<PumpStoragePlant>> futureStorageUnits,
			Map<MarketArea, Map<PlantOption, Integer>> newPlants,
			Map<MarketArea, Map<PlantOption, Integer>> newStorages,
			Map<MarketArea, Float> startupSurplus) throws GRBException {
		return () -> {
			try {
				final String threadName = "Priceforecast for year " + year + ", week " + weekOfYear;

				Thread.currentThread().setName(threadName);
				Capacities exchangeCapacity = null;
				for (final MarketArea marketArea : marketAreas) {
					exchangeCapacity = marketArea.getMarketCouplingOperator().getCapacitiesData();
					break;
				}

				// Market coupling
				MarketCouplingForecast.marketCouplingForecastStorage(OptimizationPeriodType.WEEKLY,
						year, weekOfYear, marketAreas, futureDemand, futureRenewableLoad,
						futureMeritOrder, seasonalStorage, futureStorageUnits, newPlants,
						newStorages, exchangeCapacity, startupSurplus);

			} catch (final Exception e) {
				logger.error("Clear market problems weekly", e);
			}
			return null;
		};
	}

	private static Callable<Void> calculateForecastPricesWithStorageYearly(
			final Set<MarketArea> marketAreas, final int year,
			final Map<MarketArea, Map<Integer, Float>> futureDemand,
			final Map<MarketArea, Map<Integer, Float>> futureRenewableLoad,
			final Map<MarketArea, List<CostCap>> futureMeritOrder,
			final Map<MarketArea, Map<Integer, Float>> seasonalStorage,
			final Map<MarketArea, List<PumpStoragePlant>> futureStorageUnits,
			final Map<MarketArea, Map<PlantOption, Integer>> newPlants,
			final Map<MarketArea, Map<PlantOption, Integer>> newStorages,
			final Map<MarketArea, Float> startupSurplus) throws GRBException {
		return () -> {
			try {
				final String threadName = "Priceforecast for year " + year;

				Thread.currentThread().setName(threadName);
				Capacities exchangeCapacity = null;
				for (final MarketArea marketArea : marketAreas) {

					exchangeCapacity = marketArea.getMarketCouplingOperator().getCapacitiesData();
					break;
				}

				// Market coupling
				MarketCouplingForecast.marketCouplingForecastStorage(OptimizationPeriodType.YEARLY,
						year, null, Collections.unmodifiableSet(marketAreas),
						Collections.unmodifiableMap(futureDemand),
						Collections.unmodifiableMap(futureRenewableLoad),
						Collections.unmodifiableMap(futureMeritOrder),
						Collections.unmodifiableMap(seasonalStorage),
						Collections.unmodifiableMap(futureStorageUnits),
						Collections.unmodifiableMap(newPlants),
						Collections.unmodifiableMap(newStorages), exchangeCapacity,
						Collections.unmodifiableMap(startupSurplus));

			} catch (final Exception e) {
				logger.error("Clear market problems yearly", e);
			}
			return null;
		};
	}

	/**
	 * Set market clearing price for all market areas
	 * 
	 * @param marketClearingPricesDailyArray
	 *            optimization result
	 * @param hourOfDay
	 *            [0...23]
	 */
	private static void calculateQueue(boolean withAdditioalPlants) {
		try {
			logger.info("Priceforecast combine results");
			// wait until end and until queue is processed
			while (!(resultsQueue.isEmpty())) {
				final Map<MarketArea, Map<Integer, Map<Integer, Float>>> result = resultsQueue
						.poll();
				for (final MarketArea marketArea : result.keySet()) {
					for (final Integer year : result.get(marketArea).keySet()) {
						for (final Integer hourOfYear : result.get(marketArea).get(year).keySet()) {

							// Check whether price is one of the extreme
							// prices; if
							// so, set
							// price determined by solver to the constant
							// value (max
							// or min
							// price allowed) in order to reduce rounding
							// errors
							float marketClearingPrice = result.get(marketArea).get(year)
									.get(hourOfYear);
							final float minPriceAllowed = marketArea.getDayAheadMarketOperator()
									.getMinPriceAllowed();
							final float maxPriceAllowed = marketArea.getDayAheadMarketOperator()
									.getMaxPriceAllowed();
							if (Math.round(marketClearingPrice) < minPriceAllowed) {
								marketClearingPrice = minPriceAllowed;
							} else if (Math.round(marketClearingPrice) > maxPriceAllowed) {
								marketClearingPrice = maxPriceAllowed;
							}

							if (withAdditioalPlants) {
								if (!forecastWithAdditionalPlants.containsKey(marketArea)) {
									forecastWithAdditionalPlants.put(marketArea,
											new ConcurrentHashMap<>());
								}
								if (!forecastWithAdditionalPlants.get(marketArea)
										.containsKey(year)) {
									forecastWithAdditionalPlants.get(marketArea).put(year,
											new ConcurrentHashMap<>());
								}
								if (!forecastWithAdditionalPlants.get(marketArea)
										.containsKey(year + 1)) {
									forecastWithAdditionalPlants.get(marketArea).put(year + 1,
											new ConcurrentHashMap<>());
								}
								forecastWithAdditionalPlants.get(marketArea).get(year)
										.put(hourOfYear, marketClearingPrice);
								// Two year steps
								forecastWithAdditionalPlants.get(marketArea).get(year + 1)
										.put(hourOfYear, marketClearingPrice);

							} else {
								if (!forwardPrices.containsKey(Date.getYear())) {
									forwardPrices.put(Date.getYear(), new ConcurrentHashMap<>());
								}
								if (!forwardPrices.get(Date.getYear()).containsKey(marketArea)) {
									forwardPrices.get(Date.getYear()).put(marketArea,
											new ConcurrentHashMap<>());
								}
								if (!forwardPrices.get(Date.getYear()).get(marketArea)
										.containsKey(year)) {
									forwardPrices.get(Date.getYear()).get(marketArea).put(year,
											new ConcurrentHashMap<>());
								}
								if (!forwardPrices.get(Date.getYear()).get(marketArea)
										.containsKey(year + 1)) {
									forwardPrices.get(Date.getYear()).get(marketArea).put(year + 1,
											new ConcurrentHashMap<>());

								}
								// normal way
								forwardPrices.get(Date.getYear()).get(marketArea).get(year)
										.put(hourOfYear, marketClearingPrice);
								// Two year steps
								forwardPrices.get(Date.getYear()).get(marketArea).get(year + 1)
										.put(hourOfYear, marketClearingPrice);
							}
						}
					}
				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
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
	private static void calculateQueueExchange() {
		try {
			logger.info("Priceforecast storage combine exchange results");
			// wait until end and until queue is processed
			while (!(resultsFlows.isEmpty())) {
				final Map<MarketArea, Map<MarketArea, Map<Integer, Float>>> result = resultsFlows
						.poll();
				for (final MarketArea marketAreaFrom : result.keySet()) {
					for (final MarketArea marketAreaTo : result.get(marketAreaFrom).keySet()) {
						for (final Integer hourOfYear : result.get(marketAreaFrom).get(marketAreaTo)
								.keySet()) {

							final float flow = result.get(marketAreaFrom).get(marketAreaTo)
									.get(hourOfYear);
							if (!exchangeFlowForecast.containsKey(marketAreaFrom)) {
								exchangeFlowForecast.put(marketAreaFrom, new ConcurrentHashMap<>());
							}
							if (!exchangeFlowForecast.get(marketAreaFrom)
									.containsKey(marketAreaTo)) {
								exchangeFlowForecast.get(marketAreaFrom).put(marketAreaTo,
										new ConcurrentHashMap<>());
							}

							exchangeFlowForecast.get(marketAreaFrom).get(marketAreaTo)
									.put(hourOfYear, flow);
						}
					}
				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
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
	private static void calculateQueueStorage() {
		try {
			logger.info("Priceforecast storage combine price results");
			// wait until end and until queue is processed
			while (!(resultsQueue.isEmpty())) {
				final Map<MarketArea, Map<Integer, Map<Integer, Float>>> result = resultsQueue
						.poll();
				for (final MarketArea marketArea : result.keySet()) {
					for (final Integer year : result.get(marketArea).keySet()) {
						for (final Integer hourOfYear : result.get(marketArea).get(year).keySet()) {

							// Check whether price is one of the extreme
							// prices; if so, set price determined by solver to
							// the constant value (max or min price allowed)
							// in order to reduce rounding errors
							float marketClearingPrice = result.get(marketArea).get(year)
									.get(hourOfYear);
							final float minPriceAllowed = marketArea.getDayAheadMarketOperator()
									.getMinPriceAllowed();
							final float maxPriceAllowed = marketArea.getDayAheadMarketOperator()
									.getMaxPriceAllowed();
							if (Math.round(marketClearingPrice) < minPriceAllowed) {
								marketClearingPrice = minPriceAllowed;
							} else if (Math.round(marketClearingPrice) > maxPriceAllowed) {
								marketClearingPrice = maxPriceAllowed;
							}

							if (!forwardPricesStorage.containsKey(marketArea)) {
								forwardPricesStorage.put(marketArea, new ConcurrentHashMap<>());
							}

							forwardPricesStorage.get(marketArea).put(hourOfYear,
									marketClearingPrice);
						}
					}
				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Write all log files and wait for shutdown. Create a new instance of the
	 * Executor in case of multiruns.
	 */
	public static void closeFinal() {
		try {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private static void forecastPrice(final Set<MarketArea> marketAreas,
			List<Investment> additionalPlants, final ExecutorService clearing, int yearOffset,
			final int year, int iteration) throws GRBException {
		// initialize yealy values
		final Map<MarketArea, List<CostCap>> futureMeritOrder = new LinkedHashMap<>();
		final Map<MarketArea, Map<Integer, Float>> futureDemand = new LinkedHashMap<>();
		final Map<MarketArea, Map<Integer, Float>> futureRenewableLoad = new LinkedHashMap<>();
		final Map<MarketArea, Float> startupSurplus = new LinkedHashMap<>();

		final Map<MarketArea, List<PumpStoragePlant>> futureStorageUnits = new LinkedHashMap<>();

		final Map<MarketArea, Map<PlantOption, Integer>> newPlants = new LinkedHashMap<>();
		final Map<MarketArea, Map<PlantOption, Integer>> newStorages = new LinkedHashMap<>();

		// For every market Area
		for (final MarketArea area : marketAreas) {

			futureMeritOrder.put(area,
					Collections.unmodifiableList(getFutureMeritOrder(area, yearOffset, iteration)));

			futureDemand.put(area, Collections.unmodifiableMap(getFutureDemand(area, year)));
			// Renewables load
			futureRenewableLoad.put(area, Collections.unmodifiableMap(
					area.getManagerRenewables().getRenewableLoadHourlyTotalMap(year)));

			startupSurplus.put(area, getSurplusStartup(area, Date.getYear()));

			futureStorageUnits.put(area, Collections
					.unmodifiableList(getFutureStorageUnits(area, yearOffset, new ArrayList<>())));

			// Map with number of planned new plants structured by market area
			// and technology
			newPlants.put(area, new LinkedHashMap<>());
			newStorages.put(area, new LinkedHashMap<>());
			for (final PlantOption plantOption : area.getGenerationData()
					.getCopyOfCapacityOptions(Date.getYear())) {
				int numberOfNewPlants = 0;
				for (final Investment newPlant : additionalPlants) {
					if (newPlant.getMarketArea().isEqualMarketArea(area.getMarketAreaType())
							&& newPlant.getInvestmentOption().isSameInvestmentOption(plantOption)
							&& (yearOffset >= newPlant.getInvestmentOption()
									.getConstructionTime())) {
						numberOfNewPlants += 1;
					}
				}
				if (plantOption.isStorage()) {
					newStorages.get(area).put(plantOption, numberOfNewPlants);
				} else {
					newPlants.get(area).put(plantOption, numberOfNewPlants);
				}
			}

		}

		if (optimizationPeriodType.equals(OptimizationPeriodType.HOURLY)) {
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				final Map<MarketArea, Float> demand = getValuesPerMarketAreaHourly(futureDemand,
						hourOfYear);
				final Map<MarketArea, Float> renewableLoad = getValuesPerMarketAreaHourly(
						futureRenewableLoad, hourOfYear);

				clearing.submit(calculateForecastPricesHourly(marketAreas, year, hourOfYear, demand,
						renewableLoad, futureMeritOrder, futureStorageUnits, newPlants, newStorages,

						Collections.unmodifiableMap(startupSurplus)));
			}

		} else if (optimizationPeriodType.equals(OptimizationPeriodType.YEARLY)) {
			final Map<MarketArea, Map<Integer, Float>> demand = getValuesPerMarketAreaYearly(
					futureDemand);
			final Map<MarketArea, Map<Integer, Float>> renewableLoad = getValuesPerMarketAreaYearly(
					futureRenewableLoad);
			final Map<MarketArea, Map<Integer, Float>> seasonalStorage = getValuesPerMarketAreaYearly(
					operationSeasonalStorage);

			clearing.submit(calculateForecastPricesWithStorageYearly(marketAreas, year, demand,
					renewableLoad, futureMeritOrder, Collections.unmodifiableMap(seasonalStorage),
					futureStorageUnits, newPlants, newStorages,

					Collections.unmodifiableMap(startupSurplus)));
		} else if (optimizationPeriodType.equals(OptimizationPeriodType.MONTHLY)) {
			for (int monthOfYear = 1; monthOfYear <= Date.MONTH_PER_YEAR; monthOfYear++) {
				final Map<MarketArea, Map<Integer, Float>> demand = getValuesPerMarketAreaMonthly(
						futureDemand, monthOfYear);
				final Map<MarketArea, Map<Integer, Float>> renewableLoad = getValuesPerMarketAreaMonthly(
						futureRenewableLoad, monthOfYear);
				final Map<MarketArea, Map<Integer, Float>> seasonalStorage = getValuesPerMarketAreaMonthly(
						operationSeasonalStorage, monthOfYear);

				clearing.submit(calculateForecastPricesWithStorageMonthly(marketAreas, year,
						monthOfYear, demand, renewableLoad, futureMeritOrder,
						Collections.unmodifiableMap(seasonalStorage), futureStorageUnits, newPlants,
						newStorages, Collections.unmodifiableMap(startupSurplus)));
			}
		} else if (optimizationPeriodType.equals(OptimizationPeriodType.WEEKLY)) {
			for (int weekOfYear = 1; weekOfYear <= Date.WEEKS_PER_YEAR; weekOfYear++) {
				final Map<MarketArea, Map<Integer, Float>> demand = getValuesPerMarketAreaWeekly(
						futureDemand, weekOfYear);
				final Map<MarketArea, Map<Integer, Float>> renewableLoad = getValuesPerMarketAreaWeekly(
						futureRenewableLoad, weekOfYear);
				final Map<MarketArea, Map<Integer, Float>> seasonalStorage = getValuesPerMarketAreaWeekly(
						operationSeasonalStorage, weekOfYear);

				clearing.submit(calculateForecastPricesWithStorageWeekly(marketAreas, year,
						weekOfYear, demand, renewableLoad, futureMeritOrder,
						Collections.unmodifiableMap(seasonalStorage), futureStorageUnits, newPlants,
						newStorages, Collections.unmodifiableMap(startupSurplus)));
			}
		} else {
			logger.error(
					"Error in long-term price forecast: Type of optimization period (weekly, monthly, yearly) has not been defined!");
		}
	}

	/**
	 * 
	 * @param marketArea
	 * @param year
	 * @param futureDemand
	 *            Demand load (including losses)
	 * @param newPowerPlants
	 * @param PART_STRATEGIC_COSTS
	 * @return
	 */
	private static List<Bid> getHourlyForecastBids(MarketArea marketArea, int year, int hourOfYear,
			final List<CostCap> futureMeritOrder, float futureDemand, float futureRenewableLoad,
			float startupSurplus, List<PumpStoragePlant> futureStorageUnits,
			Map<PlantOption, Integer> newPlants, Map<PlantOption, Integer> newStorages) {

		final float hourlyPriceMax = marketArea.getDayAheadMarketOperator().getMaxPriceAllowed();
		final float hourlyPriceMin = marketArea.getDayAheadMarketOperator().getMinPriceAllowed();

		/** Get relevant data */

		List<Float> pumpProfile = new ArrayList<>();
		if (marketArea.getPumpStorageActiveTrading() == 3) {
			pumpProfile = marketArea.getPumpStorage().getDynamicPumpStorageProfile(year);
		} else if (marketArea.getPumpStorageActiveTrading() == 1) {
			pumpProfile = marketArea.getPumpStorageStaticProfile();
		}

		final Map<FuelName, Float> availability = setAvailabilities(marketArea, futureMeritOrder);

		// Initialize hourly bids

		float futureScarcity = FACTOR_SCARCITY_MAX;
		final float residualDemand = futureDemand - futureRenewableLoad;
		// Set future scarcity
		float futureCumulatedCapacity = 0;
		if (futureMeritOrder.size() > 0) {
			futureCumulatedCapacity = futureMeritOrder.get(futureMeritOrder.size() - 1)
					.getCumulatedNetCapacity();
		}
		futureScarcity = (futureCumulatedCapacity) / residualDemand;

		final List<DayAheadHourlyBidForecast> hourlyDayAheadPowerBids = new ArrayList<>();
		// Add demand for hour
		hourlyDayAheadPowerBids.add(new DayAheadHourlyBidForecast(hourOfYear, hourlyPriceMax,
				futureDemand, TraderType.DEMAND));

		// Only values not equal zero
		if (futureRenewableLoad > 0) {
			hourlyDayAheadPowerBids.add(new DayAheadHourlyBidForecast(hourOfYear,
					marketArea.getRenewableTrader().getDayAheadBiddingPrice(), -futureRenewableLoad,
					TraderType.RENEWABLE));
		}

		// PumpStorageProfile
		float pumpedStorage = 0;

		if ((marketArea.getPumpStorageActiveTrading() == 3) && !pumpProfile.isEmpty()) {
			pumpedStorage = pumpProfile.get(hourOfYear);
		} else if (marketArea.getPumpStorageActiveTrading() == 1) {
			pumpedStorage = pumpProfile.get(hourOfYear % Date.HOURS_PER_DAY);

		} else if ((marketArea.getPumpStorageActiveTrading() == 4) && !Date.isFirstYear()) {
			pumpedStorage = -marketArea.getElectricityProduction().getElectricityPumpedStorage(year,
					hourOfYear);
		}
		// Only values not equal zero
		if (Math.abs(pumpedStorage) > Settings.FLOATING_POINT_TOLERANCE) {
			hourlyDayAheadPowerBids.add(new DayAheadHourlyBidForecast(hourOfYear,
					PRICE_PUMPED_STORAGE, pumpedStorage, TraderType.PUMPED_STORAGE));
		}

		// known operation of seasonal storage plants
		// Only values not equal zero
		float seasonalStorage = 0;
		if (operationSeasonalStorage.containsKey(marketArea)) {
			seasonalStorage = operationSeasonalStorage.get(marketArea).get(hourOfYear);

		}
		if (seasonalStorage > 0) {
			seasonalStorage /= SeasonalStorageTrader.getIterations();
			for (int iteration = 1; iteration <= SeasonalStorageTrader
					.getIterations(); iteration++) {
				hourlyDayAheadPowerBids.add(new DayAheadHourlyBidForecast(hourOfYear,
						SeasonalStorageTrader.getBidPrice(marketArea, iteration), -seasonalStorage,
						TraderType.SEASONAL_STORAGE));
			}
		}

		final int dayOfYear = Date.getDayFromHourOfYear(hourOfYear);
		for (final CostCap costCap : futureMeritOrder) {

			float availabilityFactor = availability.get(costCap.getFuelName());
			availabilityFactor += getVariationFactorSeasonal(hourOfYear);
			final float bidCapacity = -availabilityFactor * costCap.getNetCapacity();
			final float strategicFactor = 1 + startupSurplus;

			final float varCosts = costCap.getCostsVar(year, marketArea);
			hourlyDayAheadPowerBids.add(new DayAheadHourlyBidForecast(hourOfYear,
					Math.min(varCosts * strategicFactor, hourlyPriceMax), bidCapacity,
					TraderType.SUPPLY));

		}
		for (final PlantOption costCap : newPlants.keySet()) {
			float availabilityFactor = availability.get(costCap.getFuelName());
			availabilityFactor += getVariationFactorSeasonal(hourOfYear);
			final float bidCapacity = -availabilityFactor * costCap.getNetCapacity();

			final float varCosts = costCap.getCostsVar(year, marketArea);
			for (int numberOfPlants = 0; numberOfPlants < newPlants
					.get(costCap); numberOfPlants++) {
				hourlyDayAheadPowerBids.add(new DayAheadHourlyBidForecast(hourOfYear,
						Math.min((varCosts), hourlyPriceMax), bidCapacity, TraderType.SUPPLY));
			}
		}

		for (final PlantOption option : newStorages.keySet()) {
			final float storagesNewCapacity = newStorages.get(option) * option.getNetCapacity();
			hourlyDayAheadPowerBids.add(new DayAheadHourlyBidForecast(hourOfYear,
					Math.min((PRICE_PUMPED_STORAGE), hourlyPriceMax), storagesNewCapacity,
					TraderType.PUMPED_STORAGE));
		}

		final List<Bid> bidPoints = new ArrayList<>();

		// set bidpoints of hourly bids
		for (final DayAheadHourlyBidForecast bid : hourlyDayAheadPowerBids) {
			bid.setAssignedVolume(0);
			bidPoints.addAll(bid.getBidPoints());
		}

		// Sort bids
		// 1. by lowest price
		final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
				b2.getPrice());
		// 2. by highest volume
		final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
				* Float.compare(b1.getVolume(), b2.getVolume());
		Collections.sort(bidPoints, compPrice.thenComparing(compVolume));
		return Collections.unmodifiableList(bidPoints);
	}

	/** determines surplus that is added to the bids */
	private static float getSurplusStartup(MarketArea marketArea, int year) {
		if (!surplusStartup.containsKey(marketArea)) {
			surplusStartup.put(marketArea, new ConcurrentHashMap<>());
		}
		if (!useSurplusStartup) {
			return 0;
		}
		// Average of var costs
		final float avgVarCosts = marketArea.getElectricityResultsDayAhead().getYearlyVarCostsAvg();
		// average startup costs
		final float avgStartUp = marketArea.getElectricityResultsDayAhead()
				.getYearlyStartupCostsAvg();
		// calculate the share of startup costs compared to var costs
		float surplusStartupValue = 0;
		if ((avgVarCosts > 0) && (avgStartUp > 0)) {
			surplusStartupValue = avgStartUp / avgVarCosts;
		}
		// add to map
		surplusStartup.get(marketArea).put(year, surplusStartupValue);
		return surplusStartup.get(marketArea).get(year);
	}

	private static Map<MarketArea, Float> getValuesPerMarketAreaHourly(
			Map<MarketArea, Map<Integer, Float>> valuesPerMarketArea, int hourOfYear) {
		final Map<MarketArea, Float> values = new LinkedHashMap<>();
		for (final MarketArea marketArea : valuesPerMarketArea.keySet()) {
			values.put(marketArea, valuesPerMarketArea.get(marketArea).get(hourOfYear));
		}
		return Collections.unmodifiableMap(values);
	}

	private static Map<MarketArea, Map<Integer, Float>> getValuesPerMarketAreaMonthly(
			Map<MarketArea, Map<Integer, Float>> valuesPerMarketArea, int monthOfYear) {
		final Map<MarketArea, Map<Integer, Float>> values = new LinkedHashMap<>();
		for (final MarketArea marketArea : valuesPerMarketArea.keySet()) {
			values.put(marketArea, new LinkedHashMap<>());
			// Adjust if leap years are used
			final int totalHoursOfMonth = Date.HOURS_PER_DAY
					* java.time.Month.of(monthOfYear).length(false);

			for (int hourOfMonth = 0; hourOfMonth < totalHoursOfMonth; hourOfMonth++) {
				final int hourOfYear;
				// Adjust if leap years are used
				hourOfYear = Date.getFirstYearlyHourOfMonth(monthOfYear, 2015) + hourOfMonth;
				values.get(marketArea).put(hourOfMonth,
						valuesPerMarketArea.get(marketArea).get(hourOfYear));
			}
		}
		return Collections.unmodifiableMap(values);
	}

	private static Map<MarketArea, Map<Integer, Float>> getValuesPerMarketAreaWeekly(
			Map<MarketArea, Map<Integer, Float>> valuesPerMarketArea, int weekOfYear) {
		final Map<MarketArea, Map<Integer, Float>> values = new LinkedHashMap<>();
		for (final MarketArea marketArea : valuesPerMarketArea.keySet()) {
			values.put(marketArea, new HashMap<>());
			final int totalHoursOfWeek;
			if (weekOfYear < Date.WEEKS_PER_YEAR) {
				totalHoursOfWeek = Date.HOURS_PER_WEEK;
			} else {
				totalHoursOfWeek = Date.HOURS_PER_DAY;
			}

			for (int hourOfWeek = 0; hourOfWeek < totalHoursOfWeek; hourOfWeek++) {
				final int hourOfYear = ((weekOfYear - 1) * Date.HOURS_PER_WEEK) + hourOfWeek;
				values.get(marketArea).put(hourOfWeek,
						valuesPerMarketArea.get(marketArea).get(hourOfYear));
			}

		}
		return Collections.unmodifiableMap(values);
	}

	private static Map<MarketArea, Map<Integer, Float>> getValuesPerMarketAreaYearly(
			Map<MarketArea, Map<Integer, Float>> valuesPerMarketArea) {
		final Map<MarketArea, Map<Integer, Float>> values = new LinkedHashMap<>();
		for (final MarketArea marketArea : valuesPerMarketArea.keySet()) {
			values.put(marketArea, new HashMap<>());
			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				values.get(marketArea).put(hourOfYear,
						valuesPerMarketArea.get(marketArea).get(hourOfYear));
			}
		}
		return Collections.unmodifiableMap(values);
	}

	private static void logging(final Set<MarketArea> marketAreas,
			final Map<MarketArea, Map<Integer, Map<Integer, Float>>> forwardPrices) {
		if (logFutureMeritOrder) {
			marketAreas.parallelStream()
					.forEach((marketArea) -> marketArea.getFutureMeritOrders().logMeritOrder());

		}
		logViaStreams(marketAreas, forwardPrices);
	}

	private static void logViaStreams(final Set<MarketArea> marketAreas,
			final Map<MarketArea, Map<Integer, Map<Integer, Float>>> forwardPricesCopy) {
		// copy reference
		final Map<MarketArea, Map<Integer, Map<Integer, Float>>> forwardPrices = forwardPricesCopy;
		for (final MarketArea marketArea : marketAreas) {
			exec.execute(() -> {
				final String threadName = "Log price forecast";
				Thread.currentThread().setName(threadName);
				for (final Integer year : forwardPrices.get(marketArea).keySet()) {
					if (forwardPrices.get(marketArea).get(year).size() != (Date.HOURS_PER_YEAR)) {
						logger.error(marketArea.getInitialsBrackets()
								+ "Forecast not complete. Year " + year + ", size "
								+ forwardPrices.get(marketArea).get(year).size());
					}

					logForecast(marketArea, 0, 0, 0, year, forwardPrices.get(marketArea).get(year),
							null, null, null, null, 0f, null, null, null, null, null, null);
				}
				LoggerXLSX.close(logIDPriceForecastXLSX.get(marketArea), compress);
				logIDPriceForecastXLSX.put(marketArea, -1);
			});
		}
	}

	private static void marketCouplingForecastOptimization(final Set<MarketArea> marketAreas,
			List<Investment> additionalPlants) {
		try {
			// delete possible old values
			resultsFlows.clear();
			removeOldValues();
			logger.info("Start forecast long-term");
			final ExecutorService clearing = Executors
					.newFixedThreadPool(Settings.getNumberOfCores());
			final int yearOffsetStart = Investor.getYearsLongTermPriceForecastStart() + 1;
			final int yearOffsetEnd = Investor.getYearsLongTermPriceForecastEnd() + 1;
			final int yearLastDetailedForecast = Date.getLastRegularForecastYear()
					+ PriceForecastFuture.getYearOffsetMax();
			// new map
			if (additionalPlants.isEmpty() || !forwardPrices.containsKey(Date.getYear())) {
				forwardPrices.put(Date.getYear(), new ConcurrentHashMap<>());
			}
			for (final MarketArea area : marketAreas) {
				if (!forwardPrices.get(Date.getYear()).containsKey(area)) {
					forwardPrices.get(Date.getYear()).put(area, new ConcurrentHashMap<>());
				}
			}

			// Take two year steps
			for (int yearOffset = yearOffsetStart; yearOffset <= (yearOffsetEnd); yearOffset++) {
				final int year = setYear(yearOffset);
				if (year > yearLastDetailedForecast) {
					break;
				}
				// For every market Area
				for (final MarketArea area : marketAreas) {
					// Add marketArea

					if (!forwardPrices.get(Date.getYear()).get(area).containsKey(year)) {
						forwardPrices.get(Date.getYear()).get(area).put(year,
								new ConcurrentHashMap<>());
					}
					// Two year steps
					if (!forwardPrices.get(Date.getYear()).get(area).containsKey(year + 1)) {
						forwardPrices.get(Date.getYear()).get(area).put(year + 1,
								new ConcurrentHashMap<>());
					}
				}
				forecastPrice(marketAreas, additionalPlants, clearing, yearOffset, year, 0);
				yearOffset++;
			}
			clearing.shutdown();
			clearing.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
			calculateQueue(!additionalPlants.isEmpty());
			logger.info("End forecast threads");
			if (additionalPlants.isEmpty()) {
				logging(marketAreas,
						Collections.unmodifiableMap(forwardPrices.get(Date.getYear())));
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	protected static Map<MarketArea, Map<Integer, Float>> priceForecastSeasonalStorage(
			Set<MarketArea> marketAreas, int iteration) {
		try {
			// Log settings
			logger.info("Get long-term price forecast with optimization.");
			// delete possible old values
			resultsFlows.clear();
			final long timeStart = System.currentTimeMillis() / 1000;
			// could
			lock.lock();

			logger.info("Start forecast seasonalStorage");
			final ExecutorService clearing = Executors
					.newFixedThreadPool(Settings.getNumberOfCores());

			for (final MarketArea area : marketAreas) {
				forwardPricesStorage.put(area, new ConcurrentHashMap<>());
			}

			final int year = Date.getYear();

			forecastPrice(marketAreas, new ArrayList<>(), clearing, 0, year, iteration);
			clearing.shutdown();
			clearing.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
			calculateQueueStorage();
			calculateQueueExchange();
			final long timeEnd = System.currentTimeMillis() / 1000;
			logger.info("End seasonal forecast. Time: " + (timeEnd - timeStart) + " s.");

			return forwardPricesStorage;
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			lock.unlock();
		}
		return null;
	}
	public static void recalculate(Set<MarketArea> marketAreas) {
		try {
			// Logger for settings
			logger.info("Recalculate long-term price forecast with optimization.");
			final long timeStart = System.currentTimeMillis() / 1000;
			// because if recalculate is called first an additional calculation
			// could be call otherwise
			lock.lock();
			if (!forwardPrices.containsKey(Date.getYear())) {
				forwardPrices.put(Date.getYear(), new ConcurrentHashMap<>());
			}
			marketCouplingForecastOptimization(marketAreas, new ArrayList<>());
			final long timeEnd = System.currentTimeMillis() / 1000;
			logger.info("End recalculate long-term price forecast. Time: " + (timeEnd - timeStart)
					+ " s.");
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			lock.unlock();
		}
	}

	private static void removeOldValues() {
		final int yearCurrent = Date.getYear();
		final Iterator<Integer> iterator = forwardPrices.keySet().iterator();
		while (iterator.hasNext()) {
			final int yearList = iterator.next();
			if (yearList < yearCurrent) {
				iterator.remove();
			}
		}
	}

}