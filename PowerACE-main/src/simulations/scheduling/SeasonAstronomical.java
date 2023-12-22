package simulations.scheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all season operations and represents the astronomical/Julian calendar
 * seasons.
 * <p>
 *
 * @author Andreas, Florian
 *
 */
public enum SeasonAstronomical {

	AUTUMN(
			266,
			6361),
	SPRING(
			79,
			1873),
	SUMMER(
			172,
			4105),
	WINTER(
			355,
			8449);

	static Map<Integer, SeasonAstronomical> seasonByHour = new HashMap<>(Date.HOURS_PER_YEAR);

	static {
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			seasonByHour.put(hourOfYear, SeasonAstronomical.getSeasonForHour(hourOfYear));
		}
	}

	public static int getFirstHour(SeasonAstronomical season) {
		return season.startSeasonHourOfYear;
	}

	public static int getLastHour(SeasonAstronomical season) {
		int hour = Integer.MAX_VALUE;
		if (season == SeasonAstronomical.WINTER) {
			hour = SeasonAstronomical.SPRING.startSeasonHourOfYear - 1;
		} else if (season == SeasonAstronomical.SPRING) {
			hour = SeasonAstronomical.SUMMER.startSeasonHourOfYear - 1;
		} else if (season == SeasonAstronomical.SUMMER) {
			hour = SeasonAstronomical.AUTUMN.startSeasonHourOfYear - 1;
		} else {
			hour = SeasonAstronomical.WINTER.startSeasonHourOfYear - 1;
		}
		return hour;
	}

	public static SeasonAstronomical getSeason(int hourOfYear) {
		return seasonByHour.get(hourOfYear);
	}

	private static SeasonAstronomical getSeasonForHour(int hourOfYear) {
		if ((hourOfYear < SPRING.startSeasonHourOfYear)
				|| (hourOfYear >= WINTER.startSeasonHourOfYear)) {
			return WINTER;
		} else if (hourOfYear < SUMMER.startSeasonHourOfYear) {
			return SPRING;
		} else if (hourOfYear < AUTUMN.startSeasonHourOfYear) {
			return SUMMER;
		} else {
			return AUTUMN;
		}
	}

	private final int startSeasonDayOfYear;
	private final int startSeasonHourOfYear;

	private SeasonAstronomical(int firstDayInYear, int firstHourInYear) {
		startSeasonDayOfYear = firstDayInYear;
		startSeasonHourOfYear = firstHourInYear;
	}

	public int getFirstDayOfYear() {
		return startSeasonDayOfYear;
	}

	public int getFirstHourOfYear() {
		return startSeasonHourOfYear;
	}

}
