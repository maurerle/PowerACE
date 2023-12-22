package supply.invest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.carbon.CarbonPrices;
import simulations.scheduling.Date;
import supply.powerplant.PlantOption;
import tools.OperationsPowerPlants;
import tools.logging.LoggerXLSX;
import tools.math.Finance;
import tools.math.Statistics;
import tools.other.Concurrency;

/**
 * The InvestmentPlanner agent assumes all operations and decisions regarding
 * the (long-term) strategic operation of generation units.<br>
 * <br>
 * This includes amongst others<br>
 * - generating a long-term price forecast<br>
 * - assessing different types of remuneration schemes (for energy and capacity)
 * <br>
 * - evaluating the available investment options (in particular the construction
 * of new power plants) *
 * 
 * @since 31.03.2005
 * @author Massimo Genoese et al
 */
public class InvestorNetValue extends Investor {

	private class InvestmentMetrics {
		private float emissionCosts;
		private float fuelCosts;
		private float powerProfit;
		private float profit;

		private float getEmissionCosts() {
			return emissionCosts;
		}

		private float getFuelCosts() {
			return fuelCosts;
		}

		private float getPowerProfit() {
			return powerProfit;
		}

		private float getProfit() {
			return profit;
		}

		private void setEmissionCosts(float emissionCosts) {
			this.emissionCosts = emissionCosts;
		}

		private void setFuelCosts(float fuelCosts) {
			this.fuelCosts = fuelCosts;
		}

		private void setPowerProfit(float powerProfit) {
			this.powerProfit = powerProfit;
		}

		private void setProfit(float profit) {
			this.profit = profit;
		}
	}

	/**
	 * Number of hour from currentHour when short-term price forecast is
	 * generated
	 */
	private static int HOURS_SHORT_TERM_PRICE_FORECAST = 24;
	/**
	 * learning factor for capacity certificates, should equal learning factor
	 * of neutral/risk taker Capacity demand trader
	 */
	public static float learningfactor = 0.3f;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static Logger logger = LoggerFactory // NOPMD
			.getLogger(InvestorNetValue.class.getName());

	/** Min Unit Size that can be built */
	private static int MIN_UNIT_SIZE = 100;

	/**
	 * Use one thread only since this performance is determined by writing to
	 * hard disk
	 */
	// One thread is enough. Just at the end it will take some time
	private static final ExecutorService exec = Executors.newSingleThreadExecutor();

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

	public static int getHoursShortTermPriceForecast() {
		return HOURS_SHORT_TERM_PRICE_FORECAST;
	}

	private int builtCapacity;

	private Set<PlantOption> unprofitablePlantOptionOfCurrentAgent;

	private boolean useTechnicalRestrictions = false;

	public boolean buildOption(PlantOption capacityOptionBest) {
		// Design problem should be build anyway even if not
		boolean isBuilt = false;

		// set capacity within upper and lower limit Max net
		// Capacity
		final int netCapacity = (int) capacityOptionBest.getNetCapacity();
		final int unitSize = netCapacity;

		// Build just plants bigger than limitLower
		if (unitSize >= MIN_UNIT_SIZE) {
			final int timeLag = 0;

			if (!capacityOptionBest.isStorage()) {
				OperationsPowerPlants.createNewSupplyUnit(marketArea, capacityOptionBest, unitSize,
						this, timeLag);
			} else {
				OperationsPowerPlants.createNewStorageUnit(marketArea, capacityOptionBest, unitSize,
						this, timeLag);
			}

			builtCapacity += unitSize;
			isBuilt = true;
			logger.info(marketArea.getInitialsBrackets() + "New investment: "
					+ capacityOptionBest.getName() + " (capacity: " + unitSize + ")");
		}

		return isBuilt;
	}

	public boolean evaluateOptionNotProfitable() {
		boolean isBuilt = false;

		logger.info(marketArea.getInitialsBrackets()
				+ "No capacity forward market or not active yet in year " + Date.getYear());

		return isBuilt;
	}

	public Callable<Void> evaluateProfitabilityPlantOption(
			Map<Integer, Map<Integer, Float>> forwardPricesMap, PlantOption capacityOption) {
		final Map<Integer, Map<Integer, Float>> forwardPrices = forwardPricesMap;
		return () -> {
			try {
				int YEAR_OFFSET = capacityOption.getConstructionTime();

				final int regardedLifeTime = capacityOption.getOperatingLifetime() / 2;
				capacityOption.setNetPresentValue(0);
				capacityOption.setAnnuity(0);

				int forwardYearCountMax = Collections.max(forwardPrices.keySet()) - Date.getYear();
				// Calculate and store undiscounted metrics for every year
				// year, parameter, value
				final Map<Integer, InvestmentMetrics> yearlyMetricsUndiscounted = new LinkedHashMap<>();
				for (int yearCount = 0
						+ YEAR_OFFSET; yearCount <= forwardYearCountMax; yearCount++) {
					yearlyMetricsUndiscounted.put(yearCount, calculateYearlyMetricsUndiscounted(
							forwardPrices, capacityOption, yearCount, null));

				}
				// Calculate total metrics over regarded lifetime
				final InvestmentMetrics aggregatedMetrics = calculateAggregatedMetrics(
						capacityOption, regardedLifeTime, yearlyMetricsUndiscounted);
				// Profit in EUR pro MWh! Fixkosten in EUR pro kWh, daher Faktor
				// 1000 variables that store yearly profit and costs
				/** variables that store the total discounted costs/profit */
				final float profitBeforeInvestmentAndCRM = aggregatedMetrics.getProfit();
				final float totalPowerProfit = aggregatedMetrics.getPowerProfit();
				final float totalEmissionCosts = aggregatedMetrics.getEmissionCosts();
				final float totalFuelCosts = aggregatedMetrics.getFuelCosts();

				if (!capacityOption.isStorage()) {
					capacityOption.setEmissionCosts(totalEmissionCosts);
					capacityOption.setPowerProfit(totalPowerProfit);
					capacityOption.setFuelCosts(totalFuelCosts);
				}

				// in EUR/kW!
				// Simplified calculation assuming that all payments for the
				// construction occur in the middle of the construction period
				final float netPresentValue = (profitBeforeInvestmentAndCRM / 1000)
						- (capacityOption.getInvestmentPayment()
								* Finance.getDiscountFactor(interestRate, YEAR_OFFSET / 2));
				final float annuityFactor = 1
						/ Finance.getAnnuityFactor(interestRate, regardedLifeTime);
				final float annuity = annuityFactor * netPresentValue;

				final float netPresentValueMin = netPresentValue;
				final float netPresentValueMax = netPresentValue;
				final float valueAtRisk = netPresentValue;
				final float conditionalValueAtRisk = netPresentValue;
				final float netPresentValueRiskAdjusted;
				final float annuityMin = annuityFactor * netPresentValueMin;
				final float annuityMax = annuityFactor * netPresentValueMax;
				final float annuityValueAtRisk = annuityFactor * valueAtRisk;
				final float annuityConditionalValueAtRisk = annuityFactor * conditionalValueAtRisk;
				final float annuityRiskAdjusted;

				capacityOption.setNetPresentValue(netPresentValue);

				netPresentValueRiskAdjusted = netPresentValue;
				annuityRiskAdjusted = annuity;

				capacityOption.setNetPresentValue(netPresentValueRiskAdjusted);
				capacityOption.setAnnuity(annuityRiskAdjusted);

				logger.debug(marketArea.getInitialsBrackets()
						+ "Annuity of plant option (risk-adjusted) " + capacityOption.getName()
						+ " (" + capacityOption.getNetCapacity() + " MW): " + annuityRiskAdjusted
						+ " kEUR/(MW*a) (Min: " + annuityMin + ", CVaR: "
						+ annuityConditionalValueAtRisk + ", VaR: " + annuityValueAtRisk + ", Exp: "
						+ annuity + ", Max: " + annuityMax + ")");

				if ((conditionalValueAtRisk - valueAtRisk) > 0.01f) {
					logger.error(marketArea.getInitialsBrackets()
							+ "Error evaluating profitability of plant option: "
							+ capacityOption.getName() + ". CVaR=" + conditionalValueAtRisk
							+ " is larger than VaR=" + valueAtRisk);
				}

			} catch (final Exception e) {
				logger.error(marketArea.getInitialsBrackets()
						+ "Error evaluating profitability of plant option: "
						+ capacityOption.getName(), e);
			}
			return null;
		};
	}

	public int getBuiltCapacity() {
		return builtCapacity;
	}

	public List<PlantOption> getPlantOptionsSortedByAnnuity() {
		// Sort the plant options according to its calculated annuities in
		// reverse
		// order (so that most profitable is at the end)
		Collections.sort(capacityOptions, Collections.reverseOrder());
		return capacityOptions;
	}

	public List<Investment> getPlantOptionsSortedByNPV(
			Map<Integer, Map<Integer, Float>> forecastPrices,
			Set<PlantOption> unprofitablePlantOptionsOtherAgents) {
		// Determine available investment options
		capacityOptions = determineInvestmentOptions(unprofitablePlantOptionsOtherAgents);

		final Collection<Callable<Void>> evaluateProfitabilityOfAllPlantOptions = new ArrayList<>();
		for (final PlantOption capacityOption : capacityOptions) {
			evaluateProfitabilityOfAllPlantOptions
					.add(evaluateProfitabilityPlantOption(forecastPrices, capacityOption));
		}
		Concurrency.executeConcurrently(evaluateProfitabilityOfAllPlantOptions);

		logCapacityOptions(capacityOptions);
		// Sort the plant options according to its calculated NPVs in reverse
		// order (so that most profitable is at the end)
		Collections.sort(capacityOptions, Collections.reverseOrder());
		final List<Investment> investmentLists = new ArrayList<>();
		for (final PlantOption capacityOption : capacityOptions) {
			investmentLists.add(new Investment(this, capacityOption));
		}
		return investmentLists;
	}

	public Set<PlantOption> getUnprofitablePlantOptions() {
		return unprofitablePlantOptionOfCurrentAgent;
	}

	@Override
	public void initialize() {
		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ InvestorNetValue.class.getSimpleName() + " " + getName());
		super.initialize();
	}

	public void setBuiltCapacity(int builtCapacity) {
		this.builtCapacity = builtCapacity;
	}

	// This method has to be overritten for the initalization via reflection
	// from
	// the settings file.
	// Reflection does not find inherited methods.
	@Override
	public void setName(String name) { // NOPMD
		super.setName(name);
	}

	private InvestmentMetrics calculateAggregatedMetrics(PlantOption capacityOption,
			int regardedLifeTime, Map<Integer, InvestmentMetrics> yearlyMetricsUndiscounted) {
		final InvestmentMetrics aggregatedMetrics = new InvestmentMetrics();

		float profitBeforeInvestmentAndCRM = 0;
		float totalPowerProfit = 0;
		float totalEmissionCosts = 0;
		float totalFuelCosts = 0;
		int metricsLastYear = Collections.max(yearlyMetricsUndiscounted.keySet());
		int YEAR_OFFSET = capacityOption.getConstructionTime();

		for (int yearCount = 0 + YEAR_OFFSET; yearCount < (regardedLifeTime
				+ YEAR_OFFSET); yearCount++) {
			final int yearIndexWithoutOffSet = yearCount - YEAR_OFFSET;
			// If detailed price forecast is not available, use last available
			// year
			final int yearCountPriceForecast = Math.min(yearCount, metricsLastYear);

			final float factor = Finance.getDiscountFactor(interestRate, yearCount);
			if (!capacityOption.isStorage()) {
				totalPowerProfit += yearlyMetricsUndiscounted.get(yearCountPriceForecast)
						.getPowerProfit() * factor;
				final float cashFlow = capacityOption.getCashFlow(yearIndexWithoutOffSet)
						+ yearlyMetricsUndiscounted.get(yearCountPriceForecast).getPowerProfit()
								* factor;

				capacityOption.setCashFlow(yearIndexWithoutOffSet, cashFlow);

				totalEmissionCosts += yearlyMetricsUndiscounted.get(yearCountPriceForecast)
						.getEmissionCosts() * factor;
				totalFuelCosts += yearlyMetricsUndiscounted.get(yearCountPriceForecast)
						.getFuelCosts() * factor;
			}
			profitBeforeInvestmentAndCRM += yearlyMetricsUndiscounted.get(yearCountPriceForecast)
					.getProfit() * factor;
		}
		capacityOption.setProfitBeforeInvestment(profitBeforeInvestmentAndCRM / 1000);

		aggregatedMetrics.setProfit(profitBeforeInvestmentAndCRM);
		aggregatedMetrics.setPowerProfit(totalPowerProfit);
		aggregatedMetrics.setEmissionCosts(totalEmissionCosts);
		aggregatedMetrics.setFuelCosts(totalFuelCosts);

		return aggregatedMetrics;
	}

	private InvestmentMetrics calculateYearlyMetricsUndiscounted(
			Map<Integer, Map<Integer, Float>> forwardPrices, PlantOption capacityOption,
			Integer yearCount, Integer weatherYear) {
		final InvestmentMetrics metricsUndiscounted = new InvestmentMetrics();

		// Reset offCounter
		capacityOption.setUtilisation(0);
		float profit = 0;
		float powerProfit = 0;
		float emissionCosts = 0;
		float fuelCosts = 0;
		int forwardYearCountMax = Collections.max(forwardPrices.keySet()) - Date.getYear();

		// in Euro/MWh
		final int yearRegarded = Date.getYear() + Math.min(yearCount, forwardYearCountMax);
		float tempFuelCosts = 0f;
		float tempEmissionCosts = 0f;
		if (!capacityOption.isStorage()) {
			tempFuelCosts = marketArea.getFuelPrices().getPricesYearly(capacityOption.getFuelName(),
					yearRegarded) / capacityOption.getEfficiency();
			tempEmissionCosts = capacityOption.getCostsCarbonVar(yearRegarded,
					CarbonPrices.getPricesYearlyAverage(yearRegarded, marketArea));
		}
		// Add yearly fixed costs in [EUR/MW], Plant availability is
		// not relevant here
		profit -= capacityOption.getCostsOperationMaintenanceFixed();

		// List with temporary profits
		final List<Float> margins = new ArrayList<>();
		final List<Float> prices = new ArrayList<>();

		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			float currentForwardPrice = getForwardPriceMap(forwardPrices, yearRegarded)
					.get(hourOfYear);
			// set maximum price to upper limit
			if (currentForwardPrice >= marketArea.getPriceForwardMaximum()) {
				currentForwardPrice = marketArea.getPriceForwardMaximum();
			}

			if (!capacityOption.isStorage()) {
				// profit from selling electricity
				final float tempProfit = currentForwardPrice - tempFuelCosts - tempEmissionCosts
						- capacityOption.getCostsOperationMaintenanceVar();

				if (useTechnicalRestrictions) {
					margins.add(tempProfit);
					prices.add(currentForwardPrice);
				} else {
					// Plant is not available all the time assume
					// constant
					// lower availability
					final float availabilityFactorTechnical = marketArea.getAvailabilityFactors()
							.getAvailabilityFactors(capacityOption.getFuelName());
					if (tempProfit > 0) {
						profit += tempProfit * availabilityFactorTechnical;
						powerProfit += currentForwardPrice * availabilityFactorTechnical;
						fuelCosts += tempFuelCosts * availabilityFactorTechnical;
						emissionCosts += tempEmissionCosts * availabilityFactorTechnical;

					}
				}
			} else {
				prices.add(currentForwardPrice);
			}
		}

		if (!capacityOption.isStorage() && useTechnicalRestrictions) {
			final YearlyProfit yearlyProfit = new YearlyProfit(margins, capacityOption, marketArea);
			yearlyProfit.calcYearlyProfit();

			final float availabilityFactorTechnical = marketArea.getAvailabilityFactors()
					.getAvailabilityFactors(capacityOption.getFuelName());

			// Fixed O&M costs are already subtracted before
			profit += yearlyProfit.getTotalProfitWithoutFixedCosts();

			for (int hour = 0; hour < Date.HOURS_PER_YEAR; hour++) {
				if (yearlyProfit.isRunning(hour)) {
					fuelCosts += tempFuelCosts * availabilityFactorTechnical;
					emissionCosts += tempEmissionCosts * availabilityFactorTechnical;
					powerProfit += prices.get(hour) * availabilityFactorTechnical;
				}
			}
		} else if (capacityOption.isStorage()) {
			final YearlyProfitStorage yearlyProfitStorage = new YearlyProfitStorage(prices,
					capacityOption);
			yearlyProfitStorage.calcYearlyProfit();
			capacityOption.setYearlyOperation(yearRegarded,
					yearlyProfitStorage.getYearlyOperationAsList());

			// Fixed O&M costs are already subtracted before
			profit += yearlyProfitStorage.getTotalProfitWithoutFixedCosts();
		}

		metricsUndiscounted.setProfit(profit);
		metricsUndiscounted.setPowerProfit(powerProfit);
		metricsUndiscounted.setEmissionCosts(emissionCosts);
		metricsUndiscounted.setFuelCosts(fuelCosts);

		return metricsUndiscounted;
	}
	private Map<Integer, Float> getForwardPriceMap(Map<Integer, Map<Integer, Float>> forewardPrices,
			int year) {
		final int yearOffsetMax = Collections.max(forewardPrices.keySet());
		final int yearOffsetMin = Collections.min(forewardPrices.keySet());
		if (year < yearOffsetMin) {
			return forewardPrices.get(yearOffsetMin);
		}
		if (year > yearOffsetMax) {
			return forewardPrices.get(yearOffsetMax);
		}
		if (forewardPrices.containsKey(year)) {
			return forewardPrices.get(year);
		}
		return getForwardPriceMap(forewardPrices, year + 1);
	}

	private void logCapacityOptions(final List<PlantOption> capOpts) {
		final List<PlantOption> capOpt = new ArrayList<>(capOpts);

		exec.execute(() -> {
			try {
				for (final PlantOption capacityOption : capOpt) {
					logger.info(marketArea.getInitialsBrackets() + "Name "
							+ capacityOption.getName() + ", profit "
							+ Statistics.round(capacityOption.getNetPresentValue(), 2));

					final ArrayList<Object> dataLine = new ArrayList<>();

					final Map<Integer, Float> cashFlow = capacityOption.getCashFlow();
					final int[] util = capacityOption.getYearlyUtilisationHours();
					dataLine.add(Date.getYear());
					dataLine.add(identity);
					dataLine.add(getName());
					dataLine.add(capacityOption.getName());
					dataLine.add(capacityOption.getNetCapacity());
					dataLine.add(capacityOption.getEfficiency());
					dataLine.add(capacityOption.getPlantEmissionFactor(true));
					dataLine.add(capacityOption.getAvailableYear());
					dataLine.add(capacityOption.getInvestmentPayment());
					dataLine.add(capacityOption.getOperatingLifetime());
					dataLine.add(capacityOption.getCostsOperationMaintenanceFixed());
					dataLine.add(capacityOption.getCostsOperationMaintenanceVar());
					dataLine.add(capacityOption.getNetPresentValue());
					dataLine.add(capacityOption.getPowerProfit());
					dataLine.add(capacityOption.getFuelCosts());
					dataLine.add(capacityOption.getCertificateCosts());
					dataLine.add(capacityOption.getCostsOperationMaintenanceVar());
					dataLine.add(capacityOption.getInvestmentAdd());

					for (int j = 0; j < cashFlow.size(); j++) {
						dataLine.add(cashFlow.get(j));
					}
					for (final int element : util) {
						dataLine.add(element);
					}
					dataLine.add(capacityOption.getFuelName());
					dataLine.add(capacityOption.getUtilisation());
					dataLine.add(capacityOption.getConstructionTime());
					dataLine.add(capacityOption.getEnergyConversion());

					LoggerXLSX.writeLine(
							marketArea.getInvestmentLogger().getLogIDInvestmentPlannerCapOpt(),
							dataLine);
				}
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}

		});
	}

}