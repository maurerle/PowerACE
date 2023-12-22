package simulations.scheduling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.clearing.MarketCouplingHourly;
import markets.operator.spot.DayAheadMarketOperator;
import markets.trader.future.tools.ExchangeForecastFuture;
import markets.trader.future.tools.MarketCouplingForecast;
import markets.trader.future.tools.PriceForecastFuture;
import markets.trader.future.tools.StorageOperationForecastFutureRegression;
import markets.trader.spot.hydro.PumpStorageTrader;
import markets.trader.spot.hydro.SeasonalStorageTrader;
import markets.trader.spot.supply.SupplyTrader;
import results.Validation;
import results.powerplant.PlotCapacities;
import results.powerplant.WritePowerPlantData;
import results.spot.DayAheadDispatch;
import results.spot.EmissionsCarbonAnalysis;
import results.spot.EmissionsCarbonMarketArea;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import supply.Generator;
import supply.invest.DecommissionPlants;
import supply.invest.InvestmentPlannerMarketCoupling;
import supply.invest.StateStrategic;
import supply.invest.YearlyProfitStorage;
import supply.powerplant.Plant;
import tools.OperationsPowerPlants;
import tools.database.ConnectionSQL;
import tools.file.Operations;
import tools.logging.Folder;
import tools.logging.LogFile;
import tools.logging.LogFile.Frequency;
import tools.other.Concurrency;
import tools.other.Mail;
import tools.other.SpeedTest;
import tools.other.Tuple;

/**
 * This class handles the execution of methods regularly called by
 * {@link PowerMarkets#step()}.
 * 
 */
public class Steps {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Steps.class.getName());

	private final ExecutorService executorLogFiles = Executors.newSingleThreadExecutor();

	private final PowerMarkets model;

	ExchangeForecastFuture exchangeForecastFuture;
	StorageOperationForecastFutureRegression storageOperationForecastFuture;

	public Steps(PowerMarkets model) {
		this.model = model;
	}

	/** Perform operations at the begin of each day */
	private void performOperationsBeginDay() throws Exception {

		logger.debug("Perform daily operations before market clearing");
		final int day = Date.getDayOfYear();
		final int year = Date.getYear();

		final Collection<Callable<Void>> tasks = new ArrayList<>();

		// model.getMarketAreas().parallelStream().forEach(marketArea -> {
		for (final MarketArea marketArea : model.getMarketAreas()) {
			// executorLogFiles.execute(() -> {
			final Callable<Void> wrapper = () -> {
				try {

					// Perform output in SupplyBidder (profits, emissions, ...)
					for (final SupplyTrader supplyBidder : marketArea.getSupplyTrader()) {
						supplyBidder.performOutput();
					}

					// Get current units of each generator
					marketArea.getGenerationData().setActualUnits(day);

					// Update daily plants with costs and availability
					for (final Generator generator : marketArea.getGenerators()) {
						generator.determineDailyAvailablePlants();
					}

					marketArea.getAvailabilitiesPlants().calculateCapacities(marketArea, year, day);
				} catch (final Exception e) {
					logger.error(e.getLocalizedMessage(), e);
				}
				return null;
			};
			tasks.add(wrapper);
		}
		// }
		Concurrency.executeConcurrently(tasks);
	}

	/** Perform operations at the begin of the simulation */
	private void performOperationsBeginSim() {
		logger.info("Perform start operations");
		logger.debug("randomNumberSeed " + Settings.getRandomNumberSeed());

		// Copy files here to make sure that files do not get changed afterwards
		Operations.exportXMLFiles(model.getMarketAreas());
		Operations.rarSrc();

		// Initialize MarketCouplingOperator (if necessary)
		if (model.getMarketScheduler().getMarketCouplingOperator() != null) {
			model.getMarketScheduler().getMarketCouplingOperator().initialize(model);
		}

		// Initialize long-term exchange and storage operation forecasts
		exchangeForecastFuture = new ExchangeForecastFuture(model);
		storageOperationForecastFuture = new StorageOperationForecastFutureRegression(model);

		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				marketArea.getManagerRenewables().calculateRemainingSystemLoad();
				// Initialize the logger for new capacities and gap
				marketArea.getInvestmentLogger().initializeInvestmentsMap();
				marketArea.getInvestmentLogger().logGapInitialize();

			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});
		// Close conneciton pool
		ConnectionSQL.closeDataSource();
	}

	/** Perform operations at the begin of each year */
	private void performOperationsBeginYear() {
		logger.info("Perform new year operations");

		/**
		 * Perform operations at the begin of each year (excluding start year)
		 */
		if (!Date.isFirstDay()) {
			// Exchange by market coupling and storage operation are forecasted,
			// which is then used for several long-term price predictions
			exchangeForecastFuture.updateExchangeForecastFutureForAllMarketAreas();
			storageOperationForecastFuture.updateStorageOperationForecastFutureForAllMarketAreas();
		}

		/**
		 * Perform operations at the begin of each year (including start year)
		 */
		// Loop market areas
		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				// reset the electricity production to zero
				marketArea.getSupplyData().setPlantDataCurrentYear();

				for (final Generator generator : marketArea.getGenerators()) {
					generator.initializeGenerator();
				}

				// reset
				for (final Plant powerplant : marketArea.getSupplyData().getPowerPlantsAsList(
						Date.getYear(),
						Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()))) {
					powerplant.resetYearlyRunningHours();
					powerplant.initializeProfit();
				}

				// Initialize plants and make revision plan
				final List<Plant> powerPlants = new ArrayList<>();
				for (final Generator generator : marketArea.getGenerators()) {
					// calculate the total variable costs first
					generator.determinePlantCosts();
					// get all plants
					powerPlants.addAll(generator.getPowerPlantsList());
				}

				// Initialize logging of InvestmentPlanner agents
				marketArea.getInvestmentLogger().logCapOptInitialize();

				// Dispatch logging for pumped storage plants only every five
				// years
				if (!marketArea.getPumpStorageTraders().isEmpty()
						&& marketArea.getPumpStorageTraders().get(0).isLogPumpedStorageDispatch()
						&& ((Date.getYear() % 5) == 0)) {
					marketArea.getPumpStorageTraders().get(0).logInitializePumpedStorageDispatch();
				}

				// Get current units of each generator
				marketArea.getGenerationData().setActualUnits(Date.getDayOfYear());
				for (final Generator generator : marketArea.getGenerators()) {
					generator.getMeritOrder();
				}

			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});

		final Map<MarketArea, Map<Integer, Float>> operationPlannedSeasonalStorage = new ConcurrentHashMap<>();
		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				operationPlannedSeasonalStorage.put(marketArea, new HashMap<>());
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {

					operationPlannedSeasonalStorage.get(marketArea).put(hourOfYear, 0f);
				}
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});
		for (int run = 0; run < SeasonalStorageTrader.getIterations(); run++) {

			final Map<MarketArea, Map<Integer, Float>> priceForecast = PriceForecastFuture
					.getForwardPriceListStorage(model.getMarketAreas(),
							operationPlannedSeasonalStorage, run + 1);
			// need for Stream an paralellization
			final int iteration = run;
			model.getMarketAreas().parallelStream().forEach(marketArea -> {
				try {
					// Seasonal storage trader
					// calculate operation
					for (final SeasonalStorageTrader trader : marketArea
							.getSeasonalStorageTraders()) {
						operationPlannedSeasonalStorage.put(marketArea, trader
								.storageOptimization(iteration, priceForecast.get(marketArea)));
					}

				} catch (final Exception e) {
					logger.error(e.getLocalizedMessage(), e);
				}
			});

		}

		// Yearly markets

		final Collection<Callable<Void>> currentTasks = new ArrayList<>();
		/** Strategic reserve market */
		final List<MarketArea> marketAreas = model.getMarketAreas().stream()
				.collect(Collectors.toList());
		for (final MarketArea marketArea : marketAreas) {

			// Plants are either decommissioned after strategic reserve
			// auction or if not present here, since data from forward
			// market is needed
			if (marketArea.isDecommissionActive()) {
				// Make a copy, in case if plants get removed to
				// avoid ConcurrentModificationException
				final List<Plant> plants = new ArrayList<>(
						marketArea.getSupplyData().getPowerPlantsAsList(Date.getYear(),
								Collections.singleton(StateStrategic.OPERATING)));
				final Comparator<Plant> compAge = (Plant p1, Plant p2) -> Float
						.compare(p1.getShutDownYear(), p2.getShutDownYear());
				// make sure sorting is identical each time for
				// different runs
				final Comparator<Plant> compId = (Plant p1, Plant p2) -> Float
						.compare(p1.getUnitID(), p2.getUnitID());

				// First check old plants, then new ones
				plants.sort(compAge.thenComparing(compId));
				for (final Plant plant : plants) {
					DecommissionPlants.checkDecommissionOfPlant(plant, marketArea,
							marketArea.getDecommissionsYearsOfNegativeProfit(),
							marketArea.getDecommissionsYearsToShutDown());
				}
				marketArea.getGenerationData().loadMeritOrderUnitsDataAll();
			}

		}
		Concurrency.executeConcurrently(currentTasks);

	}

	/** Perform operations at the end of each day */
	private void performOperationsEndDay() {

		logger.debug("Perform daily operations after market clearing");

		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				for (final SupplyTrader supplyTrader : marketArea.getSupplyTrader()) {
					supplyTrader.logProduction();
				}

				final int firstHourOfToday = Date.getFirstHourOfToday();
				for (final Generator generator : marketArea.getGenerators()) {
					for (final Plant plant : generator.getPowerPlantsList()) {
						plant.calculateDailyRunningHours(firstHourOfToday);
					}
				}

				if (Date.isFirstDayOfYear()) {
					marketArea.getGenerationData().logMeritOrder();
				}

				marketArea.getRenewableTrader().calculateEnergyBalance(model.getMarketScheduler());

				// logging of PumpStorage Dayahead-results
				if (Settings.isLogPumpStorage()) {
					marketArea.getPumpStorage().logBlockOperations();
				}
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});
		// Wait until everything is written in the maps than write logfiles
		writeLogFiles(Frequency.DAILY);
		writeLogFiles(Frequency.HOURLY);

	}

	/**
	 * Perform operations at the end of the simulation
	 * 
	 * @throws InterruptedException
	 */
	private void performOperationsEndSim() throws InterruptedException {
		logger.info("Last tick count is reached");

		for (final MarketArea marketArea : model.getMarketAreas()) {

			// Export of Power Plants
			OperationsPowerPlants.exportPowerPlants(marketArea);

			// Log statistics
			marketArea.getInvestmentLogger().closeLogFiles();

			marketArea.getSupplyData().logPlantProfitabilityData();
			marketArea.getSupplyData().logPlantProfitabilityDataVerbose();

		}

		// Log exchange flows between coupled market areas
		if (!(model.getMarketScheduler().getMarketCouplingOperator() == null)) {
			model.getMarketScheduler().getMarketCouplingOperator().getExchangeFlows()
					.logExchangeFlows("Exchange_Flows_Market_Coupling", Folder.MARKET_COUPLING);
		}
		executorLogFiles.execute(() -> {
			new WritePowerPlantData(model).write();
		});
		final Tuple<String, String> total = Date.printTotalTime(Date.getStartTime(),
				PowerMarkets.getMultiRunsTotal());
		Mail.mailSimEnd(total);

		// Reset all lists for Multiruns
		DayAheadMarketOperator.shutdown();
		Concurrency.close();
		executorLogFiles.shutdown();
		executorLogFiles.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

		for (final MarketArea marketArea : model.getMarketAreas()) {
			marketArea.endOfSimulation();
		}
		PriceForecastFuture.endOfSimulation();

	}

	/** Perform operations at the end of each year */
	private void performOperationsEndYear() {

		logger.info("Perform operations at the end of the year");
		// Final year, particularly for threads
		final int year = Date.getYear();
		// for (final MarketArea marketArea : model.getMarketAreas()) {
		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				logger.debug("CO2 in year: " + year + ", "
						+ marketArea.getCarbonEmissions().getEmissionsYearly(year));
				marketArea.getManagerRenewables().removeOldValues(year);
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});

		/** Logging object market area carbon emissions */
		new EmissionsCarbonMarketArea(model).logResults();
		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			new EmissionsCarbonAnalysis(marketArea).logResults();
		});

		// Exchange by market coupling and storage operation are forecasted,
		// which is then used for several long-term price predictions
		exchangeForecastFuture.updateExchangeForecastFutureForAllMarketAreas();
		storageOperationForecastFuture.updateStorageOperationForecastFutureForAllMarketAreas();

		// Write log files
		writeLogFiles(Frequency.YEARLY);
		// for (final MarketArea marketArea : model.getMarketAreas()) {
		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				for (final Generator generator : marketArea.getGenerators()) {
					generator.operationsEndYear();
				}
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});

		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {

				final List<Plant> plants = new ArrayList<>();
				for (final Generator generator : marketArea.getGenerators()) {
					plants.addAll(generator.getPowerPlantsList());
				}

				// Reset yearly full load hours
				for (final Plant powerplant : plants) {
					powerplant.resetYearlyRunningHours();
				}

				marketArea.logPricesHigh();
				marketArea.logSecurityOfSupplyWithExchange();
				marketArea.logSecurityOfSupplyWithoutExchange();
				marketArea.getFutureMeritOrders().logMeritOrder();
				marketArea.getFuturePrices().logPrices();
				marketArea.getFuturePrices().logPricesSorted();

				if (marketArea.isDecommissionActive()) {
					marketArea.getPlantsDecommissioned().logPlants();
					marketArea.getPlantsDecommissioned().logPlantsDecisions();
				}

			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});

		// Dispatch logging for pumped storage plants only every five years
		// for (final MarketArea marketArea : model.getMarketAreas()) {
		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				if (!marketArea.getPumpStorageTraders().isEmpty()
						&& marketArea.getPumpStorageTraders().get(0).isLogPumpedStorageDispatch()
						&& ((year % 5) == 0)) {
					marketArea.getPumpStorageTraders().get(0).logPumpedStorageDispatch();
				}
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});

		// for (final MarketArea marketArea : model.getMarketAreas()) {
		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				final List<Generator> generators = marketArea.getGenerators();
				marketArea.getElectricityProduction().summarize(generators);
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});

		// Investments
		new InvestmentPlannerMarketCoupling(model).startInvestments();

		model.getMarketAreas().parallelStream().forEach(marketArea -> {
			try {
				// Log investments of InvestmentPlanner agents
				marketArea.getInvestmentLogger().logNewInvestments();
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		});
		executorLogFiles.execute(() -> new Validation(model, year));

		// dispose env
		MarketCouplingForecast.disposeMarketCouplingForecast();
		MarketCouplingHourly.dispose();
		PumpStorageTrader.dispose();
		SeasonalStorageTrader.dispose();
		YearlyProfitStorage.dispose();
		// Log duration of simulation run at the end of each year
		SpeedTest.speedtest(Date.getStartTime());

		// Keep heap space down, suggest garbage collection
		System.gc();
	}

	/**
	 * This method is executed every tick. This method triggers all date events
	 * especially all the events sketched in the StepManager, such as daily
	 * operations monthly operations and annual operations.
	 */
	public void step() {
		try {
			logger.info("Next step, day " + Date.getDayOfYear() + " of the year " + Date.getYear()
					+ ", model day " + Date.getDayOfTotal() + " of " + Date.getTotalDays()
					+ " (remaining: " + (Date.getTotalDays() - Date.getDayOfTotal()) + ")");

			/** Perform operations at the begin of the simulation */
			if (Date.isFirstDay()) {
				performOperationsBeginSim();
			}

			/** Perform operations at the begin of each year */
			if (Date.isFirstDayOfYear()) {
				performOperationsBeginYear();
			}

			/** Perform operations at the begin of each day */
			performOperationsBeginDay();

			/** Schedule and execute markets */
			model.getMarketScheduler().executeMarkets();

			/** Perform operations at the end of each day */
			performOperationsEndDay();

			/** Perform operations at the end of each year */
			if (Date.isLastDayOfYear()) {
				performOperationsEndYear();
			}

			/** Perform operations at the end of simulation */
			if (Date.isLastDay()) {
				// Evaluate Monte Carlo runs and check for new one
				performOperationsEndSim();
			}

			// All operations have been executed -> next day
			Date.incrementDay();
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Initiates the writing of log files according to the specified update
	 * frequency
	 */
	private void writeLogFiles(Frequency updateFrequency) {
		try {
			final int year = Date.getYear();
			final int day = Date.getDayOfYear();
			// Loop all log files defined for market area
			executorLogFiles.execute(() -> {
				Thread.currentThread().setName("Central logfile writing thread.");
				for (final MarketArea marketArea : model.getMarketAreas()) {
					for (final LogFile logFile : marketArea.getLogFiles()) {
						// If update frequency match execute logging
						if (updateFrequency == logFile.getFrequency()) {
							executorLogFiles.execute(() -> logFile.executeLoggingDay(day));
						}
					}
				}
			});
			if (updateFrequency == Frequency.YEARLY) {
				executorLogFiles.execute(() -> {
					new PlotCapacities(model, year).log();
				});

				if ((Date.getYear() % 5) == 0) {
					new DayAheadDispatch().logDispatch(model, year);
				} ;

			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
}