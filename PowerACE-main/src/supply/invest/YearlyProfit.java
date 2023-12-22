package supply.invest;

import static simulations.scheduling.Date.HOT_STARTUP_LENGTH;
import static simulations.scheduling.Date.WARM_STARTUP_LENGTH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.scheduling.Date;
import supply.powerplant.PlantAbstract;
import supply.tools.periods.PeriodTools;
import supply.tools.periods.RunningPeriodSimple;
import tools.math.Statistics;

/**
 * Calculate the yearly profit based on the margin, start-up costs and minimal
 * run as well as down time. No optimal solution guaranteed though, since
 * problem is NP-hard.
 *
 * Currently only look at current period and period before. Looking at period
 * after current period is possible too, but quite complicated and not really
 * giving better results.
 *
 * <p>
 * <code>
 * <b>Algorithm:</b>
 * <p>
 * <b>A)</b> First find periods where margin is positive, i.e. plant would operate
 * profitably.
 * <p>
 * <b>B)</b> Check for technical/economical constraints <br>
 * 1. Minimal Down Time Before <br>
 * 2. Minimal Run Time <br>
 * 3. Minimal Down Time After
 * <p>
 * <b>C)</b> Check for pure economical constraints <br>
 * 1. Running additional hours to avoid/reduce start-up costs. <br>
 * 2. Start-up costs are earned.
 * </code>
 * <p>
 *
 * For further speed improvement all counters could be removed.
 *
 * 
 *
 */
public class YearlyProfit {

	private class Extending extends Period {

		int hourEnd;
		int hourStart;

		public Extending(float costs) {
			super(costs);
		}

		public Extending(float costs, int hourStart, int hourEnd) {
			super(costs);
			this.hourStart = hourStart;
			this.hourEnd = hourEnd;
		}

	}

	private class NoRunAfter extends Period {

		@SuppressWarnings("unused")
		int periodIndex;

		public NoRunAfter(float costs) {
			super(costs);
		}

		public NoRunAfter(float costs, int periodIndex) {
			super(costs);
			this.periodIndex = periodIndex;
		}

	}

	private class Period {

		float costs;

		public Period(float costs) {
			this.costs = costs;
		}
	}

	private class PostPone extends Period {

		int hourEnd;
		int hourStart;

		public PostPone(float costs) {
			super(costs);
		}

		public PostPone(float costs, int hourStart, int hourEnd) {
			super(costs);
			this.hourStart = hourStart;
			this.hourEnd = hourEnd;
		}
	}

	private class PrePoneEnd extends Period {

		int hourEnd;

		public PrePoneEnd(float costs) {
			super(costs);
		}

		public PrePoneEnd(float costs, int hourEnd) {
			super(costs);
			this.hourEnd = hourEnd;
		}
	}

	private class RunAfter extends Period {

		int hourEnd;
		int periodIndex;

		public RunAfter(float costs) {
			super(costs);
		}

		public RunAfter(float costs, int hourEnd, int periodIndex) {
			super(costs);
			this.hourEnd = hourEnd;
			this.periodIndex = periodIndex;
		}

	}

	private class RunBefore extends Period {

		int hourStart;

		public RunBefore(float costs) {
			super(costs);
		}

		public RunBefore(float costs, int hourStart) {
			super(costs);
			this.hourStart = hourStart;
		}

	}

	private static final boolean CHECK_NEXT_PERIOD = false;
	private static int counterMinDownAfterDoNotRun = 0;
	private static int counterMinDownAfterDoNotRunTemp = 0;
	private static int counterMinDownAfterPrePoneEnd = 0;
	private static int counterMinDownAfterPrePoneEndTemp = 0;
	private static int counterMinDownAfterRunThrough = 0;
	private static int counterMinDownAfterRunThroughTemp = 0;
	private static int counterMinDownBeforePostPoneStart = 0;
	private static int counterMinDownBeforePostPoneStartTemp = 0;
	private static int counterMinDownBeforeRunThrough = 0;
	private static int counterMinDownBeforeRunThroughTemp = 0;
	private static int counterMinRunAfter = 0;
	private static int counterMinRunAfterTemp = 0;
	private static int counterMinRunExtend = 0;
	private static int counterMinRunExtendTemp = 0;
	private static int counterMinRunThroughBefore = 0;
	private static int counterMinRunThroughBeforeTemp = 0;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static Logger logger = LoggerFactory.getLogger(YearlyProfit.class.getName());

	/** End hour of current period. */
	private int currentHourEnd;
	/** Start hour of current period. */
	private int currentHourStart;
	/** Last hour where plant is running currently */
	private int lastRunHour;
	/** The hourly margins */
	private final List<Float> margins;
	/** The hourly margins */
	private float marginsAvg;
	/** Market area */
	private final MarketArea marketArea;
	/** The minimum shut-down time of the plant. */
	private final int minDownTime;
	/** The minimum run time of the plant. */
	private final int minRunTime;
	/** The plant for which the margin will be calculated. */
	private final PlantAbstract plant;
	/** The hourly prices */
	private List<Float> prices;
	/** If running in hour, contains true */
	private Map<Integer, Boolean> running;
	/** Periods where plant will be running. */
	private List<RunningPeriodSimple> runPeriods;
	/** Periods where margin is positive sorted by time. */
	private List<RunningPeriodSimple> runPeriodsPossible;
	/** The current index of possibleRunPeriods */
	private int runPeriodsPossibleIndex;
	/** The start-up costs for each length */
	private Map<Integer, Float> startCostsByLength;
	/** The total profit for the year */
	private float totalProfit;
	/** The total profit for the year */
	private float totalProfitExcludingAvailability;
	/** The total profit for the year */
	private float totalProfitExcludingFixedCosts;
	/** The total running hours for the year */
	private int totalRunningHours;
	/** The total start-up costs for the year */
	private float totalStartupCosts;
	/** If true print warnings */
	private boolean warningsActive = true;

	public YearlyProfit(List<Float> margins, List<Float> prices, PlantAbstract plant,
			MarketArea marketArea) {
		this(margins, prices, plant, marketArea, true);
	}

	public YearlyProfit(List<Float> margins, List<Float> prices, PlantAbstract plant,
			MarketArea marketArea, boolean warningsActive) {
		this.margins = margins;
		this.prices = prices;
		marginsAvg = Statistics.calcAvg(margins);
		this.plant = plant;
		this.marketArea = marketArea;
		minRunTime = plant.getMinRunTime();
		minDownTime = plant.getMinDownTime();
		setStartCosts();
		this.warningsActive = warningsActive;
	}

	public YearlyProfit(List<Float> margins, PlantAbstract plant, MarketArea marketArea) {
		this(margins, null, plant, marketArea, true);
	}

	/**
	 * Calculate the yearly profit under consideration of start-up costs,
	 * minimum run time and minimum shut-down time.
	 */
	public void calcYearlyProfit() {

		runPeriodsPossible = PeriodTools.determineProfitableMarginPeriods(margins);
		runPeriods = calcRunningPeriods();

		calcProfitYearly();

	}

	public float getMargin(int hour) {
		return margins.get(hour);
	}

	public float getMarginsAvg() {
		return marginsAvg;
	}

	public List<Float> getPrices() {
		return prices;
	}

	/**
	 * @return profit, which equals income - fixed costs in [EUR/MW] and
	 *         availability <br>
	 *         <b>(Net capacity is not yet regarded!)</b>
	 */
	public float getTotalProfit() {
		return totalProfit;
	}

	/**
	 * @return profit, which equals income - fixed costs in [EUR/MW]<br>
	 *         <b>(Net capacity is not yet regarded!)</b>
	 */
	public float getTotalProfitExcludingAvailability() {
		return totalProfitExcludingAvailability;
	}

	/**
	 * @return profit, which equals income - fixed costs in [EUR/MW]<br>
	 *         <b>(Net capacity is not yet regarded!)</b>
	 */
	public float getTotalProfitWithoutFixedCosts() {
		return totalProfitExcludingFixedCosts;
	}

	public int getTotalRunningHours() {
		return totalRunningHours;
	}

	public float getTotalStartupCosts() {
		return totalStartupCosts;
	}

	/**
	 * @return true, if running in hour
	 */
	public boolean isRunning(int hour) {
		return running.containsKey(hour);
	}

	/**
	 * Extend current run time
	 *
	 * @param period
	 *            Periods that needs to be extended.
	 * @param lengthNeeded
	 *            Number of hours periods need to be extended by.
	 * @param periodIndex
	 * @return The costs for extending the period.
	 */
	private Extending calcCostsExtending(int periodIndex) {

		/* Min hour before without violating minDownTime
		   lastTimeRunning (incl): 3,  minShutDownTime = 2,
		   ->  3+2+1=6 */
		final int minHourBefore = lastRunHour + (minDownTime + 1);

		int maxHourAfter;
		if ((periodIndex + 1) == runPeriodsPossible.size()) {
			// If last period only dependent on margin number
			maxHourAfter = margins.size() - 1;
		} else {
			/* Max amount after
			periodStart (incl): 10,  minShutDownTime = 2
			-> 10-2-1=7 */
			maxHourAfter = runPeriodsPossible.get(periodIndex + 1).getStartHour()
					- (minDownTime + 1);
			// If last period
			maxHourAfter = Math.max(maxHourAfter, currentHourEnd);
		}

		// Check if is possible to extend period by the needed length
		if (((maxHourAfter - minHourBefore) + 1) < minRunTime) {
			return new Extending(Float.POSITIVE_INFINITY);
		}

		int hourExtendingPeriodStart = currentHourStart;
		int hourExtendingPeriodEnd = currentHourEnd;
		float costs = 0f;

		// Check if period can be made any longer without violating
		// other restrictions
		if ((minHourBefore <= hourExtendingPeriodStart)
				&& (hourExtendingPeriodEnd <= maxHourAfter)) {
			costs = 0f;
		} else {
			costs = Float.POSITIVE_INFINITY;
		}

		// hoursRemaining
		final int currentLength = (hourExtendingPeriodEnd - hourExtendingPeriodStart) + 1;
		int hoursRemaining = minRunTime - currentLength;
		while (!Float.isInfinite(costs) && (hoursRemaining > 0)) {

			// Check if it possible to extend period (subtracting one)
			float costBefore = 0f;
			if (minHourBefore <= (hourExtendingPeriodStart - 1)) {
				// negative margin -> positive costs
				costBefore = -margins.get(hourExtendingPeriodStart - 1);
			} else {
				costBefore = Float.POSITIVE_INFINITY;
			}

			float costAfter = 0f;
			if (((hourExtendingPeriodEnd + 1) <= maxHourAfter)
					&& ((hourExtendingPeriodEnd + 1) < margins.size())) {
				// negative margin -> positive costs
				costAfter = -margins.get(hourExtendingPeriodEnd + 1);
			} else {
				costAfter = Float.POSITIVE_INFINITY;
			}

			// Check if period can be made any longer without violating
			// other restrictions
			if (Float.isInfinite(costAfter) && Float.isInfinite(costBefore)) {
				costs = Float.POSITIVE_INFINITY;
				break;
			}

			if (costBefore < costAfter) {
				costs += costBefore;
				hourExtendingPeriodStart--;
			} else {
				costs += costAfter;
				hourExtendingPeriodEnd++;
			}
			hoursRemaining--;

		}
		return new Extending(costs, hourExtendingPeriodStart, hourExtendingPeriodEnd);
	}

	/**
	 * Only look at one period.
	 *
	 * @param nextPeriodIndex
	 * @param period
	 * @return
	 */
	private NoRunAfter calcCostsNotRunningRunAfter(int nextPeriodIndex) {

		final float costs = calcMarginSum(runPeriodsPossible.get(nextPeriodIndex));

		// Only look at one period
		final int periodIndex = nextPeriodIndex;

		return new NoRunAfter(costs, periodIndex);
	}

	/**
	 * Calculate costs for postpone start, i.e. costs from not running the
	 * negative margin for these hours.
	 *
	 * First postpone start and if minRunTime is not held, postpone end too. If
	 * end is too close to next period.
	 *
	 * @param period
	 * @return Costs <code>[0, infinity]</code>
	 */
	private PostPone calcCostsPostPoneStart() {

		// Get start hour of next period
		int nextHourStart;
		if ((runPeriodsPossibleIndex + 1) < runPeriodsPossible.size()) {
			nextHourStart = runPeriodsPossible.get(runPeriodsPossibleIndex + 1).getStartHour();
		} else {
			// assume plant is not running in hours after the regarded time span
			nextHourStart = Integer.MAX_VALUE;
		}

		// lastRunHour (incl) = 10, minShutDownTime 1 -> startHourNew (incl) 12
		final int newHourStart = lastRunHour + minDownTime + 1;

		// Find end hour that does not violate minimal run time of current
		// period and minimal down time of current and next period
		// startHourNew (incl) = 10, minRunTime 1 -> endHourNew (incl) 10
		int newHourEnd = (newHourStart + minRunTime) - 1;
		// Only lengthen period because of minRunTime, do not shorten period
		newHourEnd = Math.max(newHourEnd, currentHourEnd);
		// Check if minimum down time is okay between current and next period,
		// if not try to shorten current period
		// nextHourStart (incl) = 10, minShutDownTime 1 -> newHourEndNeeded
		// (incl) 8
		final int newHourEndNeeded = nextHourStart - (minDownTime + 1);
		newHourEnd = Math.min(Math.min(newHourEnd, newHourEndNeeded), margins.size() - 1);

		final int newLength = (newHourEnd - newHourStart) + 1;
		if (newLength < minRunTime) {
			// not possible to find and hour where plant is not in the market
			// without changing
			return new PostPone(Float.POSITIVE_INFINITY);
		} else {

			// Start is only postponed, so costs equal opportunity costs
			final float costsStart = calcMarginSum(currentHourStart, newHourStart - 1);
			float costsEnd = 0;
			if (newHourEnd < currentHourEnd) {
				// opportunity costs, e.g. runs until hour 10 before now only
				// until 9, so money lost in hour 10 where margin was positive
				costsEnd = calcMarginSum(newHourEnd + 1, currentHourEnd);
			} else if (newHourEnd > currentHourEnd) {
				// lost money, e.g. runs until hour 10 before now until 11 so
				// money lost in hour 10 cause margin is negative
				costsEnd = -calcMarginSum(currentHourEnd + 1, newHourEnd);
			}

			final float costs = costsStart + costsEnd;

			return new PostPone(costs, newHourStart, newHourEnd);
		}

	}

	/**
	 * Prepone end of period so that minDownTime between current and next period
	 * is not violated.
	 *
	 *
	 * @param period
	 * @return
	 */
	private PrePoneEnd calcCostsPrePoneEnd() {

		final int nextPeriodStart = runPeriodsPossible.get(runPeriodsPossibleIndex + 1)
				.getStartHour();
		// e.g. end 10, minDown 1 -> start 8
		final int endHourNeeded = nextPeriodStart - (minDownTime + 1);
		// e.g. start 1, minRun 2 -> end 2
		final int endHourMinimalPossible = currentHourStart + (minRunTime - 1);

		if (endHourMinimalPossible <= endHourNeeded) {
			// Prepone is possible
			final float costs = calcMarginSum(endHourNeeded + 1, currentHourEnd);
			return new PrePoneEnd(costs, endHourNeeded);
		} else {
			// Prepone would violate minimal run time
			return new PrePoneEnd(Float.POSITIVE_INFINITY);
		}
	}

	private RunAfter calcCostsRunAfter(int index) {

		// Get next end hour
		int newHourEnd;
		if ((index + 1) >= runPeriodsPossible.size()) {
			newHourEnd = Math.min(currentHourStart + minRunTime, margins.size() - 1);
		} else {
			newHourEnd = runPeriodsPossible.get(index + 1).getEndHour();
		}

		// see if problem is caused, at the end year it assumed that this does
		// not occur
		if ((newHourEnd < (margins.size() - 1)) && (newHourEnd <= (currentHourEnd + 1))) {
			logger.error("What is going on here? NewHourEnd " + newHourEnd + ", CurrentEnd+1"
					+ (currentHourEnd + 1) + ", minDownTime " + minDownTime + ", minRunTime"
					+ minRunTime);
		}

		// See if minRunTime is now okay
		if (((newHourEnd - currentHourStart) + 1) < minRunTime) {
			// one hour is not enough to achieve minRunTime
			return new RunAfter(Float.POSITIVE_INFINITY);
		}

		return new RunAfter(Float.POSITIVE_INFINITY, newHourEnd, runPeriodsPossibleIndex + 1);

	}

	/**
	 *
	 * @param period
	 *
	 * @return Costs for running for the whole period.
	 *         <code>[0, infinity]</code>
	 */
	private RunBefore calcCostsRunBefore() {
		// -> only one hour in between = 6-4-1 */
		final int timeBefore = currentHourStart - lastRunHour - 1;

		if (timeBefore == 0) {
			return new RunBefore(Float.POSITIVE_INFINITY);
		} else {
			return new RunBefore(getCostsRunThroughBefore(lastRunHour + 1, currentHourStart - 1),
					lastRunHour + 1);
		}
	}

	/**
	 *
	 *
	 * @param startHour
	 * @param endHour
	 * @return
	 */
	private float calcMarginSum(int startHour, int endHour) {

		float marginSum = 0f;
		for (int hour = startHour; hour <= endHour; hour++) {
			marginSum += margins.get(hour);
		}
		return marginSum;
	}

	/**
	 *
	 *
	 * @return margin for period
	 */
	private float calcMarginSum(RunningPeriodSimple period) {
		float marginSum = 0f;
		for (int hour = period.getStartHour(); hour <= period.getEndHour(); hour++) {
			marginSum += margins.get(hour);
		}
		return marginSum;
	}

	/**
	 * Calculate the total profit based for all running periods based on the the
	 * start-up costs and hourly margins.
	 *
	 * @param RunPeriods
	 *            Periods where plant is running.
	 * @return Total profit for all periods in RunPeriods.
	 */
	private void calcProfitYearly() {

		totalProfit = 0f;
		totalStartupCosts = 0f;
		totalRunningHours = 0;

		// Assume plant was running before periods.
		int lastRunningHour = -1;

		for (final RunningPeriodSimple period : runPeriods) {

			// Get startup costs
			final int outOfMarketLength = period.getStartHour() - lastRunningHour - 1;
			final float startCosts = getStartupCostsByLength(outOfMarketLength);
			totalStartupCosts += startCosts;
			totalRunningHours += period.getLength();

			// Get hourly profit from period
			final float periodIncome = calcMarginSum(period);

			totalProfit += periodIncome - startCosts;
			lastRunningHour = period.getEndHour();
		}

		totalProfitExcludingAvailability = totalProfit
				- plant.getCostsOperationMaintenanceFixed(Date.getYear());

		totalProfitExcludingFixedCosts = totalProfit
				* marketArea.getAvailabilityFactors().getAvailabilityFactors(plant.getFuelName());

		totalProfit = (totalProfit
				* marketArea.getAvailabilityFactors().getAvailabilityFactors(plant.getFuelName()))
				- plant.getCostsOperationMaintenanceFixed(Date.getYear());

		// Check for extreme events
		if ((totalProfit > 1_000_000) && warningsActive) {
			final List<Float> marginsSorted = new ArrayList<>(margins);
			Collections.sort(marginsSorted);
			final int size = marginsSorted.size();
			final List<Float> marginsSortedHighest = marginsSorted.subList(size - 101, size - 1);
			logger.warn("Seems unrealistic, profit too high " + totalProfit + " of plant " + plant);
			logger.warn("Avg margin " + Statistics.calcAvg(margins) + ", avg 100 highest values "
					+ Statistics.calcAvg(marginsSortedHighest));
		}

	}

	/**
	 * Calculate the periods where plant would be running regarding start-up
	 * costs, min-up time and min-down time.
	 *
	 * @return
	 */
	private List<RunningPeriodSimple> calcRunningPeriods() {

		runPeriods = new ArrayList<>(runPeriodsPossible.size());
		// Assume plant was running before period
		lastRunHour = -1;

		// Calculate running hours
		for (runPeriodsPossibleIndex = 0; runPeriodsPossibleIndex < runPeriodsPossible
				.size(); runPeriodsPossibleIndex++) {

			final RunningPeriodSimple period = runPeriodsPossible.get(runPeriodsPossibleIndex);

			currentHourStart = period.getStartHour();
			currentHourEnd = period.getEndHour();

			// Check technical restrictions
			checkMinShutDownTimeBefore();
			final int lastPeriodIndex = checkMinRunTime();
			if (CHECK_NEXT_PERIOD) {
				checkMinShutDownTimeAfter(lastPeriodIndex);
			}

			// Check economical condition
			checkProfitabilityAvoidedStartUp();
			if (isProfitablePeriod()) {
				// Join periods if they follow directly
				if (((currentHourStart - lastRunHour) == 1) && (runPeriods.size() > 0)) {
					currentHourStart = runPeriods.get(runPeriods.size() - 1).getStartHour();
					runPeriods.remove(runPeriods.size() - 1);
				}

				runPeriods.add(new RunningPeriodSimple(currentHourStart, currentHourEnd, true));
				// only set lastRunHour if period is profitable
				lastRunHour = currentHourEnd;

				// check which period has to be regarded next, e.g. when plant
				// runs for several hours afterwards of next period
				runPeriodsPossibleIndex = determineNextPeriod();

				counterMinDownAfterDoNotRun += counterMinDownAfterDoNotRunTemp;
				counterMinDownAfterPrePoneEnd += counterMinDownAfterPrePoneEndTemp;
				counterMinDownAfterRunThrough += counterMinDownAfterRunThroughTemp;
				counterMinRunAfter += counterMinRunAfterTemp;
				counterMinRunThroughBefore += counterMinRunThroughBeforeTemp;
				counterMinRunExtend += counterMinRunExtendTemp;
				counterMinDownBeforePostPoneStart += counterMinDownBeforePostPoneStartTemp;
				counterMinDownBeforeRunThrough += counterMinDownBeforeRunThroughTemp;

			}

			counterMinDownAfterDoNotRunTemp = 0;
			counterMinDownAfterPrePoneEndTemp = 0;
			counterMinDownAfterRunThroughTemp = 0;
			counterMinRunAfterTemp = 0;
			counterMinRunThroughBeforeTemp = 0;
			counterMinRunExtendTemp = 0;
			counterMinDownBeforePostPoneStartTemp = 0;
			counterMinDownBeforeRunThroughTemp = 0;

		}

		running = new HashMap<>();
		for (final RunningPeriodSimple runPeriod : runPeriods) {
			for (int hour = runPeriod.getStartHour(); hour <= runPeriod.getEndHour(); hour++) {
				running.put(hour, true);
			}
		}

		return runPeriods;
	}

	private int checkMinRunTime() {

		int lastPeriodIndex = runPeriodsPossibleIndex;

		// Only if plant is not running through, cause period before is already
		// long enough to make sure that minimal run time is taken care of
		if ((lastRunHour + 1) != currentHourStart) {

			// Get correct length
			final int currentLength = (currentHourEnd - currentHourStart) + 1;

			// If minimal runtime is not hold
			if (minRunTime > currentLength) {

				final Extending extending = calcCostsExtending(runPeriodsPossibleIndex);
				final RunBefore before = calcCostsRunBefore();
				final RunAfter longer = calcCostsRunAfter(runPeriodsPossibleIndex);

				if ((extending.costs < before.costs) && (extending.costs < longer.costs)) {
					// Extending is the cheapest
					currentHourStart = extending.hourStart;
					currentHourEnd = extending.hourEnd;
					counterMinRunExtendTemp++;
				} else if (before.costs < longer.costs) {
					// Run through before is the cheapest
					currentHourStart = before.hourStart;
					counterMinRunThroughBeforeTemp++;
				} else {
					// Run through after is the cheapest
					currentHourEnd = longer.hourEnd;
					lastPeriodIndex = longer.periodIndex;
					counterMinRunAfterTemp++;
				}
			}
		}

		return lastPeriodIndex;
	}

	/**
	 * Check if minimal down time has to be checked between current and next
	 * period
	 *
	 * @param index
	 * @param period
	 */
	private void checkMinShutDownTimeAfter(int indexCurrent) {

		final int indexNext = indexCurrent + 1;

		if (indexNext >= runPeriodsPossible.size()) {
			// Nothing to do anymore. Assume plant is not running afterwards, so
			// no restriction is violated.
			return;
		}

		final int nextHourStart = runPeriodsPossible.get(indexNext).getStartHour();
		// e.g. nextHourStart 10 currentHourEnd 9 = 0 ->
		final int outOfMarketTime = (nextHourStart - currentHourEnd) - 1;
		// Check if minimal down time is violated

		if ((outOfMarketTime < minDownTime) && (outOfMarketTime != 0)) {
			final PrePoneEnd prePoneEnd = calcCostsPrePoneEnd();
			final RunAfter runAfter = calcCostsRunAfter(indexNext);
			final NoRunAfter noRunAfter = calcCostsNotRunningRunAfter(indexNext);

			if ((prePoneEnd.costs < runAfter.costs) && (prePoneEnd.costs < noRunAfter.costs)) {
				currentHourEnd = prePoneEnd.hourEnd;
				counterMinDownAfterPrePoneEndTemp++;
			} else if (runAfter.costs < noRunAfter.costs) {
				currentHourEnd = runAfter.hourEnd;
				counterMinDownAfterRunThroughTemp++;
			} else {
				// Cannot already skip next period here, since first it has to
				// be determined if plant runs this period. If not next period
				// may again be profitable
				counterMinDownAfterDoNotRunTemp++;
			}
		}

	}

	/**
	 * Check if plant is not too short out of market. If so, try to postpone
	 * start while regarding minimum run time or running through.
	 *
	 * @param period
	 *            Current period which is checked.
	 */
	private void checkMinShutDownTimeBefore() {

		/* Check for minimum shutdown time before
		   periodStart (incl): 6,  lastTimeRunning (incl): 4
		   -> only one hour in between = 6-4-1 */
		final int timeBefore = currentHourStart - lastRunHour - 1;

		// If minimum shutdown time is not hold
		// either postpone start or run through
		if ((timeBefore != 0) && (timeBefore < minDownTime)) {

			final PostPone postPone = calcCostsPostPoneStart();
			final float costsRunningThrough = getCostsRunThroughBefore(lastRunHour + 1,
					currentHourStart - 1);

			if (postPone.costs < costsRunningThrough) {
				// Postpone start
				currentHourStart = postPone.hourStart;
				currentHourEnd = postPone.hourEnd;
				counterMinDownBeforePostPoneStartTemp++;
			} else {
				// Run through
				currentHourStart = lastRunHour + 1;
				counterMinDownBeforeRunThroughTemp++;
			}

		}
	}

	/**
	 * Check if running additional hours before period is profitable due to
	 * avoided/reduced start-up costs. Cold-start, warm-start, hot-start and
	 * run-through are compared.
	 *
	 * @param period
	 *            Current period which is checked.
	 */
	private void checkProfitabilityAvoidedStartUp() {
		int downTimeBefore = currentHourStart - lastRunHour - 1;

		if (downTimeBefore == 0) {
			return;
		}

		int updatedCurrentHourStart;
		// Warm-start better than cold-start?
		if ((downTimeBefore > Date.WARM_STARTUP_LENGTH) && (minDownTime > WARM_STARTUP_LENGTH)) {
			updatedCurrentHourStart = lastRunHour + 1 + WARM_STARTUP_LENGTH;
			if (getCostsRunThroughBefore(updatedCurrentHourStart, currentHourStart - 1) < 0f) {
				currentHourStart = updatedCurrentHourStart;
				downTimeBefore = currentHourStart - lastRunHour - 1;
			}
		}
		// Hot-start better than warm-start?
		if ((downTimeBefore > Date.HOT_STARTUP_LENGTH) && (minDownTime > HOT_STARTUP_LENGTH)) {
			updatedCurrentHourStart = lastRunHour + 1 + HOT_STARTUP_LENGTH;
			if (getCostsRunThroughBefore(updatedCurrentHourStart, currentHourStart - 1) < 0f) {
				currentHourStart = updatedCurrentHourStart;
				downTimeBefore = currentHourStart - lastRunHour - 1;
			}
		}
		// Run-through better than hot-start?
		updatedCurrentHourStart = lastRunHour + 1;
		if (getCostsRunThroughBefore(updatedCurrentHourStart, currentHourStart - 1) < 0f) {
			currentHourStart = updatedCurrentHourStart;
		}
	}

	/**
	 * Check if other periods have to be regarded since plant is already running
	 * for next periods, i.e. index of period when start hour of next possible
	 * period is greater than current end hour.
	 *
	 * @return Index of next period that has to be regarded.
	 */
	private int determineNextPeriod() {

		int newPossiblePeriodIndex = runPeriodsPossibleIndex;
		while (((newPossiblePeriodIndex + 1) < runPeriodsPossible.size()) && (runPeriodsPossible
				.get(newPossiblePeriodIndex + 1).getStartHour() <= lastRunHour)) {
			newPossiblePeriodIndex++;
		}
		return newPossiblePeriodIndex;

	}

	/**
	 * Calculate the costs for running before and not shutting down.
	 *
	 * @param startHour
	 *            First hour where plant will be running additionally.
	 * @param endHour
	 *            Last hour where plant will be running additionally.
	 *
	 * @return Costs from running minus avoided start-up costs.
	 *         <code>[-infinity, infinity]</code>
	 */
	private float getCostsRunThroughBefore(int startHour, int endHour) {

		// Remove later
		if (endHour < startHour) {
			logger.error("Hours are wrong. End: " + endHour + ", Start: " + startHour);
		}

		// Variable costs, plant is running so negative margin is positive cost
		final float varCost = -calcMarginSum(startHour, endHour);

		// Avoided start-up costs
		// first term: start-up costs w/o running additional hours, second term:
		// start-up costs if running additional hours (zero, if complete
		// run-through)
		final float avoidedStartCosts = getStartupCostsByLength(currentHourStart - lastRunHour - 1)
				- getStartupCostsByLength(startHour - lastRunHour - 1);

		return varCost - avoidedStartCosts;
	}

	/**
	 * Get the startup costs based on the length of out-of-market-time. Costs
	 * are stored in a map for faster access.
	 *
	 * @param length
	 * @param startCostsByLength
	 * @return start-up costs
	 */
	private float getStartupCostsByLength(int length) {

		float costs;

		if (length <= 0) {
			costs = 0f;
		} else if (length <= HOT_STARTUP_LENGTH) {
			costs = startCostsByLength.get(HOT_STARTUP_LENGTH);
		} else if (length <= WARM_STARTUP_LENGTH) {
			costs = startCostsByLength.get(WARM_STARTUP_LENGTH);
		} else {
			costs = startCostsByLength.get(Integer.MAX_VALUE);
		}

		return costs;
	}

	/**
	 * Check if period is profitable considering margin and start-up costs.
	 */
	private boolean isProfitablePeriod() {

		// Calculate start-up costs
		final int timeBefore = (currentHourStart - lastRunHour) - 1;
		final float startUpCostsCurrent = getStartupCostsByLength(timeBefore);

		// Calculate hourly margins
		final float marginSum = calcMarginSum(currentHourStart, currentHourEnd);

		// Check if it would be profitable to run for period
		final float periodProfit = marginSum - startUpCostsCurrent;

		boolean profitable = false;
		if (periodProfit > 0) {
			profitable = true;
		}
		return profitable;

	}

	/**
	 * Set the start-up costs.
	 *
	 * @param plant
	 */
	private void setStartCosts() {

		startCostsByLength = new HashMap<>();

		startCostsByLength.put(Date.HOT_STARTUP_LENGTH,
				marketArea.getStartUpCosts().getMarginalStartupCostsHot(plant));
		startCostsByLength.put(Date.WARM_STARTUP_LENGTH,
				marketArea.getStartUpCosts().getMarginalStartupCostsWarm(plant));
		startCostsByLength.put(Integer.MAX_VALUE,
				marketArea.getStartUpCosts().getMarginalStartupCostsCold(plant));

	}

}