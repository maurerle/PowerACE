package supply.tools.periods;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import supply.powerplant.Plant;
import supply.powerplant.state.Running;

/**
 * Methods for determining running periods of power plants.
 * 
 * 
 *
 */
public final class PeriodTools {

	/**
	 * @param plant
	 * @param hourlyProduction
	 *            The current hourly production for today.
	 * @return The possible running periods for the plant based on the given
	 *         hourly income.
	 */
	public static List<ProfitablePeriod> determinePossibleProfitablePeriods(Plant plant,
			Map<Integer, Float> hourlyIncome) {

		// get first and last hour
		final TreeSet<Integer> set = new TreeSet<>(hourlyIncome.keySet());
		boolean wasRunning = plant.isRunningHour(set.first() - 1);
		int periodStartHour = set.first();

		// Init list for periods
		final List<ProfitablePeriod> periods = new ArrayList<>();

		// For all relevant hours
		for (int hour = set.first(); hour <= set.last(); hour++) {

			// plant will only be running if min production is not underrun
			final boolean isProfitable = hourlyIncome.get(hour) > 0f ? true : false;

			// Start of new period
			if (wasRunning != isProfitable) {
				if (hour != set.first()) {
					// If change occurs from yesterday today, yesterday's period
					// is irrelevant
					periods.add(new ProfitablePeriod(periodStartHour, hour - 1, wasRunning));
				}
				periodStartHour = hour;
				wasRunning = isProfitable;
				// check if change occurs only for last hour
				if (hour == set.last()) {
					periods.add(new ProfitablePeriod(periodStartHour, hour, wasRunning));
					break;
				}
				continue;
			}

			// End of day is reached
			if (hour == set.last()) {
				periods.add(new ProfitablePeriod(periodStartHour, hour, wasRunning));
				break;
			}

			// Extend current period
			if (wasRunning == isProfitable) {
				continue;
			}

		}

		return periods;
	}

	/**
	 * @param plant
	 * @param hourlyProduction
	 *            The current hourly production for today.
	 * @return The possible running periods for the plant based on the given
	 *         hourly production.
	 */
	public static List<RunningPeriod> determinePossibleRunningPeriods(Plant plant,
			Map<Integer, Float> hourlyProduction) {

		// get first and last hour
		final TreeSet<Integer> set = new TreeSet<>(hourlyProduction.keySet());
		boolean wasRunning = plant.isRunningHour(set.first() - 1);
		int periodStartHour = set.first();

		// Init list for periods
		final List<RunningPeriod> periods = new ArrayList<>();

		// For all relevant hours
		for (int hour = set.first(); hour <= set.last(); hour++) {

			// plant will only be running if min production is not underrun
			final boolean isRunning = hourlyProduction.get(hour) >= plant.getMinProduction()
					? true
					: false;

			// Start of new period
			if (wasRunning != isRunning) {
				if (hour != set.first()) {
					// If change occurs from yesterday today, yesterday's period
					// is irrelevant
					periods.add(new RunningPeriod(periodStartHour, hour - 1, wasRunning));
				}
				periodStartHour = hour;
				wasRunning = isRunning;
				// check if change occurs only for last hour
				if (hour == set.last()) {
					periods.add(new RunningPeriod(periodStartHour, hour, wasRunning));
					break;
				}
				continue;
			}

			// End of day is reached
			if (hour == set.last()) {
				periods.add(new RunningPeriod(periodStartHour, hour, wasRunning));
				break;
			}

			// Extend current period
			if (wasRunning == isRunning) {
				continue;
			}

		}

		return periods;
	}

	/**
	 * 
	 * @param hourlyMargin
	 *            The net margin.
	 * @return The possible running periods for the plant, i.e. periods where
	 *         margin is positive.
	 */
	public static List<RunningPeriodSimple> determineProfitableMarginPeriods(
			List<Float> hourlyMargin) {

		// Initialize list for periods
		final List<RunningPeriodSimple> periods = new ArrayList<>(hourlyMargin.size());

		// Assume same start value for first hour of period as first hour before
		// period
		boolean wasRunning = hourlyMargin.get(0) > 0 ? true : false;

		// First period starts with first value of margin and no hour of
		// yesterday!
		int periodStartHour = 0;

		// Last hour that is regarded
		final int lastHour = hourlyMargin.size() - 1;

		// For all relevant hours find periods
		for (int hour = 0; hour <= lastHour; hour++) {

			// Plant will only be running for positive margin
			final boolean isRunning = hourlyMargin.get(hour) > 0 ? true : false;

			// Start of new period either running or not running
			if (wasRunning != isRunning) {
				if (wasRunning) {
					// only add periods where plant will be running
					periods.add(new RunningPeriodSimple(periodStartHour, hour - 1, wasRunning));
				}
				periodStartHour = hour;
				wasRunning = isRunning;
			}

		}

		// Add last period where plant was running (assume that plant will be
		// running for another hour)
		if (wasRunning) {
			periods.add(new RunningPeriodSimple(periodStartHour, lastHour, wasRunning));
		}

		return periods;
	}

	/**
	 * @param plant
	 * @return The running periods for the current day the plant, meaning the
	 *         periods where a plants is currently running or not running.
	 */
	public static List<RunningPeriod> determineRunningPeriods(Plant plant) {

		final List<RunningPeriod> periods = new ArrayList<>();

		Running wasRunning = plant.isRunning(-1);
		int periodStartHour = 0;

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			final Running isRunning = plant.isRunning(hour);

			// Start of new period
			if (wasRunning != isRunning) {

				if (hour != 0) {
					// If change occurs from yesterday today, yesterday's period
					// is irrelevant
					periods.add(new RunningPeriod(periodStartHour, hour - 1, wasRunning));
				}
				periodStartHour = hour;
				wasRunning = isRunning;

				// End of day is reached
				if ((hour + 1) == HOURS_PER_DAY) {
					periods.add(new RunningPeriod(periodStartHour, hour, wasRunning));
				}

				continue;
			}

			// End of day is reached
			if ((hour + 1) == HOURS_PER_DAY) {
				periods.add(new RunningPeriod(periodStartHour, hour, wasRunning));
				break;
			}

			// Extend current period
			if (wasRunning == isRunning) {
				continue;
			}

		}

		return periods;
	}

	/**
	 * @param plant
	 * @return The running periods for the current day the plant, meaning the
	 *         periods where a plants is currently running or not running.
	 */
	public static List<RunningPeriod> determineRunningPeriodsOnlyOff(Plant plant) {

		final List<RunningPeriod> periods = new ArrayList<>();

		boolean wasRunning = plant.isRunningHour(-1);
		int periodStartHour = 0;

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			final boolean isRunning = plant.isRunningHour(hour);

			// Start of new period
			if (wasRunning != isRunning) {
				if (hour != 0) {
					// If change occurs from yesterday today, yesterday's period
					// is irrelevant
					periods.add(new RunningPeriod(periodStartHour, hour - 1, wasRunning));
				}
				periodStartHour = hour;
				wasRunning = isRunning;

				// check for end of day and switch of periods
				if ((hour + 1) == HOURS_PER_DAY) {
					periods.add(new RunningPeriod(periodStartHour, hour, wasRunning));
					break;
				}
				continue;
			}

			// End of day is reached
			if ((hour + 1) == HOURS_PER_DAY) {
				periods.add(new RunningPeriod(periodStartHour, hour, wasRunning));
				break;
			}

			// Extend current period
			if (wasRunning == isRunning) {
				continue;
			}

		}

		return periods;
	}

	private PeriodTools() {
	}

}