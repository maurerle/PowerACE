package supply.invest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.trader.future.tools.PriceForecastFuture;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.PlantOption;
import tools.other.Concurrency;
import tools.types.MarketAreaType;

/**
 * In this class, the investment planning under consideration of market coupling
 * effects is executed. More details can be found in Fraunholz et al. (2019):
 * Agent-Based Generation and Storage Expansion Planning in Interconnected
 * Electricity Markets. 16th International Conference on the European Energy
 * Market (EEM).<br>
 * Basic methodology:<br>
 * (1) Execute capacity forward markets in all market areas where active.<br>
 * (1a) Calculate an initial price forecast for all market areas under
 * consideration of the current and future interconnector capacities.<br>
 * (1b) Get all investors from the market areas with active capacity forward
 * market and shuffle them within each market area.<br>
 * (1c) Create bids and clear capacity forward markets in all market areas where
 * active.<br>
 * (2) Start investment planning game across all market areas.<br>
 * (2a) Update price forecast to account for new power plants and storage units
 * built in the capacity forward markets.<br>
 * (2b) Get all investors from the coupled market areas and shuffle them within
 * each market area.<br>
 * (2c) For each market area determine annuities for the current investor and
 * all available investment options. The price effect of the respective
 * potential investment is neglected at this stage.<br>
 * (2d) Sort all investment options from all market areas and build the option
 * with the highest annuity, if any investment options with a positive annuity
 * are still available.<br>
 * (2e) Update the price forecast to account for the price effect of the
 * respective planned investment and add it to the list of planned
 * investments.<br>
 * (2f) If the planned investment or any other previously planned investments
 * have become unprofitable under the updated price forecast, then gradually
 * remove them and update the price forecast until all planned investments are
 * profitable again.<br>
 * (2g) If the new planned investment stayed profitable even under consideration
 * of its price impact, move on to the next investor in the respective market
 * area where the new investment was planned.<br>
 * (2h) Repeat the whole procedure until no more profitable investment options
 * are available.<br>
 * <br>
 * 
 * @author CF, FZ
 */
public class InvestmentPlannerMarketCoupling {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(InvestmentPlannerMarketCoupling.class.getName());

	private static final int BUILD_LIMIT_DEFAULT = 1000;
	private Map<MarketArea, List<InvestorNetValue>> allInvestors;

	private List<Investment> allNewPlants;

	private Map<MarketArea, List<Investment>> allOptions;
	private Map<MarketAreaType, Float> builtCapacities;

	private Investment capacityOptionBest;

	private boolean isBuilt;

	private boolean isProfitableOptionsRemaining;

	private Map<MarketAreaType, Float> maxBuildCapacitiesPerYear;
	private PowerMarkets model;
	private final Random random;

	private List<Investment> profitableOptionsAllMarketAreasAsList;
	private Map<MarketArea, Set<PlantOption>> unprofitableOptions;

	private Map<MarketArea, List<Investment>> optionsSimilarToBest;

	private Map<MarketArea, Map<Integer, Map<Integer, Float>>> allPriceForecastsWithNewPlants;

	public InvestmentPlannerMarketCoupling(PowerMarkets model) {
		this.model = model;
		random = new Random(Settings.getRandomNumberSeed());
		setMaxBuildCapacitiesPerYear();
	}
	private boolean allNewPlantsStillProfitable() {
		logger.info("Investment game in year " + Date.getYear()
				+ ", evaluate profitability of all planned investments with price effect of new planned investment");
		boolean allNewPlantsStillProfitable = true;
		// Calculcate capped priceforecast
		allPriceForecastsWithNewPlants = PriceForecastFuture
				.getForwardPriceListCapped(model.getMarketAreas(), allNewPlants);

		final Collection<Callable<Void>> evaluateProfitabilityOfAllNewPlants = new ArrayList<>();
		for (final Investment investment : allNewPlants) {
			final MarketArea marketArea = investment.getMarketArea();
			evaluateProfitabilityOfAllNewPlants
					.add(investment.getInvestor().evaluateProfitabilityPlantOption(
							allPriceForecastsWithNewPlants.get(marketArea),
							investment.getInvestmentOption()));

		}
		Concurrency.executeConcurrently(evaluateProfitabilityOfAllNewPlants);

		for (final Investment investment : allNewPlants) {
			if (investment.getNetPresentValue() < 0) {
				allNewPlantsStillProfitable = false;
				break;
			}
		}
		return allNewPlantsStillProfitable;
	}
	private final boolean buildBestInvestmentOption() {
		final MarketArea marketAreaBest = capacityOptionBest.getMarketArea();

		logger.info("Investment game in year " + Date.getYear() + ", add new planned investment: "
				+ marketAreaBest.getInitialsBrackets()
				+ capacityOptionBest.getInvestmentOption().getName());
		allNewPlants.add(capacityOptionBest);
		logger.info("Investment game in year " + Date.getYear()
				+ ", evaluate profitability of new planned investment with price effect");

		final boolean allNewPlantsProfitable = allNewPlantsStillProfitable();

		if (!allNewPlantsProfitable) {
			logger.info("Investment game in year " + Date.getYear()
					+ ", reset list of investment options since previously planned investments were removed");
			return false;
		}
		logger.info("Investment game in year " + Date.getYear()
				+ ", new planned investment are profitable: " + marketAreaBest.getInitialsBrackets()
				+ capacityOptionBest.getInvestmentOption().getName());
		setLastPriceForecastWithNewPlantProfitable();
		return capacityOptionBest.getInvestor()
				.buildOption(capacityOptionBest.getInvestmentOption());
	}
	private Investment chooseBestInvestmentOption() {
		if (profitableOptionsAllMarketAreasAsList.isEmpty()) {
			return null;
		} else {
			Collections.shuffle(profitableOptionsAllMarketAreasAsList, random);
			Collections.sort(profitableOptionsAllMarketAreasAsList, Collections.reverseOrder());
			return profitableOptionsAllMarketAreasAsList
					.get(profitableOptionsAllMarketAreasAsList.size() - 1);
		}
	}
	private synchronized void deleteInvestorsOfCurrentMarketArea(MarketArea marketArea) {
		allInvestors.get(marketArea).clear();
	}

	private void evaluateNPVs(MarketArea marketArea) {
		try {
			final String threadName = "Calculating NPV for " + marketArea.getInitials();
			Thread.currentThread().setName(threadName);
			// Check investor types and perform capacity
			// planning
			if (allInvestors.get(marketArea).isEmpty()) {
				allOptions.put(marketArea, new ArrayList<>());
				return;
			}

			final List<Investment> optionsPerMarketArea = Collections
					.synchronizedList(new ArrayList<>());
			allInvestors.get(marketArea).parallelStream().forEach(currentInvestor -> {
				Thread.currentThread().setName("Calculating NPV for " + marketArea.getInitials()
						+ " and investor " + currentInvestor.getName());

				final Map<Integer, Map<Integer, Float>> forwardPrices = marketArea
						.getLongTermPricePrediction(false);
				optionsPerMarketArea.addAll(currentInvestor.getPlantOptionsSortedByNPV(
						forwardPrices, unprofitableOptions.get(marketArea)));
			});

			Collections.sort(optionsPerMarketArea, Collections.reverseOrder());
			final List<Investment> optionsPerMarketAreaWithoutUnnecessary = removeUnnecessaryOptions(
					optionsPerMarketArea);

			allOptions.put(marketArea, optionsPerMarketAreaWithoutUnnecessary);

			final Investment bestOptionCurrentMarketArea;
			if (!allOptions.get(marketArea).isEmpty()) {
				bestOptionCurrentMarketArea = allOptions.get(marketArea)
						.get(allOptions.get(marketArea).size() - 1);
				if (bestOptionCurrentMarketArea.getNetPresentValue() > 0) {
					profitableOptionsAllMarketAreasAsList.add(bestOptionCurrentMarketArea);
				} else {
					deleteInvestorsOfCurrentMarketArea(marketArea);
				}
			} else {
				deleteInvestorsOfCurrentMarketArea(marketArea);
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void execute() {
		if (Date.getYear() < Settings.getInvestmentsStart()) {
			return;
		}
		try {
			initialize();
			getAndInitializeAllInvestors();
			shuffleInvestorsOfEachMarketArea();

			while (isBuilt || isProfitableOptionsRemaining) {
				profitableOptionsAllMarketAreasAsList = Collections
						.synchronizedList(new ArrayList<>());
				logger.info("Investment game in year " + Date.getYear()
						+ ", evaluate profitability of all remaining investment options");
				isBuilt = false;

				model.getMarketAreas().parallelStream()
						.forEach(marketArea -> evaluateNPVs(marketArea));

				capacityOptionBest = chooseBestInvestmentOption();

				if (capacityOptionBest == null) {
					isBuilt = false;
					isProfitableOptionsRemaining = false;
					continue;
				}

				if (isBuildLimitReached(capacityOptionBest.getMarketArea())) {
					deleteInvestorsOfCurrentMarketArea(capacityOptionBest.getMarketArea());
					isBuilt = false;
					isProfitableOptionsRemaining = true;
					continue;
				}

				isBuilt = buildBestInvestmentOption();

				if (isBuilt) {
					final MarketAreaType marketAreaTypeBest = capacityOptionBest.getMarketArea()
							.getMarketAreaType();
					builtCapacities.put(marketAreaTypeBest, builtCapacities.get(marketAreaTypeBest)
							+ capacityOptionBest.getInvestmentOption().getNetCapacity());

				} else {
					allNewPlants.remove(allNewPlants.size() - 1);
					profitableOptionsAllMarketAreasAsList
							.remove(profitableOptionsAllMarketAreasAsList.size() - 1);
					isProfitableOptionsRemaining = !profitableOptionsAllMarketAreasAsList.isEmpty();
					if (isProfitableOptionsRemaining) {
						removeSimilarInvestments();
					}
				}
			}

			logger.info("Investment game in year " + Date.getYear()
					+ ", finished since no more profitable investment options available");
			writeInvestmentResults();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void getAndInitializeAllInvestors() {
		for (final MarketArea marketArea : model.getMarketAreas()) {
			allInvestors.put(marketArea, new ArrayList<>());
			for (final Investor investor : marketArea.getInvestorsConventionalGeneration()) {
				if (investor instanceof InvestorNetValue) {
					allInvestors.get(marketArea).add((InvestorNetValue) investor);
					((InvestorNetValue) investor).setBuiltCapacity(0);
				}
			}
		}
	}

	private void initialize() {
		isBuilt = true;
		isProfitableOptionsRemaining = true;
		allOptions = new ConcurrentHashMap<>();
		profitableOptionsAllMarketAreasAsList = Collections.synchronizedList(new ArrayList<>());
		unprofitableOptions = new ConcurrentHashMap<>();
		allInvestors = new LinkedHashMap<>();
		allNewPlants = Collections.synchronizedList(new ArrayList<>());
		builtCapacities = new HashMap<>();
		optionsSimilarToBest = new HashMap<>();
		for (final MarketArea marketArea : model.getMarketAreas()) {
			builtCapacities.put(marketArea.getMarketAreaType(), 0f);
			unprofitableOptions.put(marketArea, new HashSet<>());
			optionsSimilarToBest.put(marketArea, new ArrayList<>());
		}
	}

	private boolean isBuildLimitReached(MarketArea marketArea) {
		if (!maxBuildCapacitiesPerYear.containsKey(marketArea.getMarketAreaType())) {
			logger.error(
					"Use default build capacity because no specific build limit is available!");
			return builtCapacities.get(marketArea.getMarketAreaType()) >= BUILD_LIMIT_DEFAULT;
		}
		return builtCapacities.get(marketArea.getMarketAreaType()) >= maxBuildCapacitiesPerYear
				.get(marketArea.getMarketAreaType());
	}

	private void removeSimilarInvestments() {
		// add all similar investments to unprofiable options
		final MarketArea areaBest = capacityOptionBest.getMarketArea();
		for (final Investment investment : optionsSimilarToBest.get(areaBest)) {
			unprofitableOptions.get(areaBest).add(investment.getInvestmentOption());
		}
		// create new list
		optionsSimilarToBest.put(areaBest, new ArrayList<>());
	}

	private List<Investment> removeUnnecessaryOptions(List<Investment> options) {
		final List<Investment> optionsProfitable = new ArrayList<>();
		// Remove unprofitable options
		for (final Investment option : options) {
			if (option.getNetPresentValue() > 0) {
				optionsProfitable.add(option);
			} else {
				unprofitableOptions.get(option.getMarketArea()).add(option.getInvestmentOption());
			}
		}
		final List<Investment> optionsClean = Collections.synchronizedList(new ArrayList<>());
		// Last element is most profitable
		if (optionsProfitable.isEmpty()) {
			return optionsProfitable;
		}
		final Investment best = optionsProfitable.get(optionsProfitable.size() - 1);
		optionsSimilarToBest.get(best.getMarketArea()).add(best);
		for (final Investment option : optionsProfitable) {
			// empty list
			if (optionsClean.isEmpty()) {
				optionsClean.add(option);
			}
			// add options that are not similar
			else {
				boolean isSimilar = false;
				for (final Investment optionSimilar : optionsClean) {
					if (option.isEqualInvestment(optionSimilar)) {
						isSimilar = true;
						break;
					}
				}
				if (best.isEqualInvestment(option)) {
					optionsSimilarToBest.get(best.getMarketArea()).add(option);
				}
				if (!isSimilar) {
					optionsClean.add(option);
				}
			}

		}

		return optionsClean;
	}

	/** In order so safe computation time */
	private void setLastPriceForecastWithNewPlantNotProfitable() {
		model.getMarketAreas().parallelStream()
				.forEach(marketArea -> marketArea.setForwardPricesLast(null));
	}

	/** In order so safe computation time */
	private void setLastPriceForecastWithNewPlantProfitable() {
		model.getMarketAreas().parallelStream().forEach(marketArea -> marketArea
				.setForwardPricesLast(allPriceForecastsWithNewPlants.get(marketArea)));
	}
	// TODO: set for different market areas
	private void setMaxBuildCapacitiesPerYear() {
		maxBuildCapacitiesPerYear = new HashMap<>();
		maxBuildCapacitiesPerYear.put(MarketAreaType.MARKET_AREA_1, 5000f);;
	}

	private void shuffleInvestors(MarketArea marketArea) {
		Collections.shuffle(allInvestors.get(marketArea), random);
	}

	private void shuffleInvestorsOfEachMarketArea() {
		model.getMarketAreas().parallelStream()
				.forEach((marketArea) -> shuffleInvestors(marketArea));

	}

	public void startInvestments() {
		try {
			if (!(Date.getNumberOfYears() > 1) || Date.isLastDay()) {
				logger.error("No Investments");
				return;
			}

			// Start Investments decisions
			logger.info("Start investment module at the end of the year " + Date.getYear());
			execute();
			// That after the investment planning the old priceforecast is reset
			setLastPriceForecastWithNewPlantNotProfitable();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void writeInvestmentResults() {
		// Log investment results
		model.getMarketAreas().parallelStream()
				.forEach((marketArea) -> marketArea.getInvestmentLogger().closeLogFiles());
		model.getMarketAreas().parallelStream()
				.forEach((marketArea) -> marketArea.getNetValueExtremePrices().writeLogFile());
	}

}