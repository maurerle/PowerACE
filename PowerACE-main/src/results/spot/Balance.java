package results.spot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import tools.math.Statistics;

public final class Balance extends Agent {

	/** Map with hourly balance of day-ahead market */
	private final Map<Integer, Float> hourlyBalance = new ConcurrentHashMap<>();
	/** Map with hourly curtailment of renewables after day-ahead market */
	private final Map<Integer, Float> hourlyCurtailmentRenewables = new ConcurrentHashMap<>();

	public Balance(MarketArea marketArea) {
		super(marketArea);
	}

	/** Get summed hourly balance for current year */
	public float getBalanceYearlySum(int year) {
		final int startIndex = Date.getKeyHourlyWithHourOfYear(year, 0);
		final int endIndex = Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear(year));
		return Statistics.calcSumMap(hourlyBalance, startIndex, endIndex);
	}

	/** Get summed hourly curtailment for current year */
	public int getCurtailmentYearlyHours(int year) {
		final int startIndex = Date.getKeyHourlyWithHourOfYear(year, 0);
		final int endIndex = Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear(year));
		return Statistics.countNotZeroMap(hourlyCurtailmentRenewables, startIndex, endIndex);
	}

	/** Get number of hour with a hourly curtailment for current year */
	public float getCurtailmentYearlySum(int year) {
		final int startIndex = Date.getKeyHourlyWithHourOfYear(year, 0);
		final int endIndex = Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear(year));
		return Statistics.calcSumMap(hourlyCurtailmentRenewables, startIndex, endIndex);
	}

	/** Get balance for <code>hourOfDay</code> on current day */
	public float getHourlyBalance(int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		return getHourlyBalance(Date.getYear(), hourOfYear);
	}

	/** Get balance for <code>hourOfDay</code> on current day */
	public float getHourlyBalance(int year, int hourOfYear) {
		final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
		if (!hourlyBalance.containsKey(key)) {
			return 0;
		}
		return hourlyBalance.get(key);
	}

	/**
	 * Get curtailment of renewables for <code>hourOfDay</code> on current day
	 */
	public float getHourlyCurtailmentRenewables(int year, int hourOfYear) {
		final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
		if (!hourlyCurtailmentRenewables.containsKey(key)) {
			return 0;
		}
		return hourlyCurtailmentRenewables.get(key);
	}

	@Override
	public void initialize() {
	}

	/** Set balance for <code>hourOfDay</code> on current day */
	public void setHourlyBalance(int hourOfDay, float balance) {
		hourlyBalance.put(Date.getKeyHourlyWithHourOfDay(hourOfDay), balance);
	}

	/**
	 * Set curtailment of renewables for <code>hourOfDay</code> on current day
	 */
	public void setHourlyCurtailmentRenewables(int hourOfDay, float balance) {
		hourlyCurtailmentRenewables.put(Date.getKeyHourlyWithHourOfDay(hourOfDay), balance);
	}
}