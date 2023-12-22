package simulations.scheduling;

import java.util.HashMap;
import java.util.Map;
/**
 * Manages all season operations and represents the meteorolocial seasons.
 * <p>
 * 
 * Spring: March to May
 * <p>
 * Summer: June to August
 * <p>
 * Autumn: September to November
 * <p>
 * Winter: December to February
 * 
 *
 * @author Florian
 *
 */
public enum SeasonMeteorolocial {

	// Meteorological
	AUTUMN(
			244,
			5833),
	SPRING(
			60,
			1417),
	SUMMER(
			152,
			3625),
	WINTER(
			335,
			8017);

	static Map<Integer, SeasonMeteorolocial> seasonByHour = new HashMap<>(Date.HOURS_PER_YEAR);

	static {
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			seasonByHour.put(hourOfYear, SeasonMeteorolocial.getSeasonForHour(hourOfYear));
		}
	}

	public static int getFirstHour(SeasonMeteorolocial season) {
		return season.startSeasonHourOfYear;
	}

	public static int getLastHour(SeasonMeteorolocial season) {
		int hour = Integer.MAX_VALUE;
		if (season == WINTER) {
			hour = SPRING.startSeasonHourOfYear - 1;
		} else if (season == SPRING) {
			hour = SUMMER.startSeasonHourOfYear - 1;
		} else if (season == SUMMER) {
			hour = AUTUMN.startSeasonHourOfYear - 1;
		} else {
			hour = WINTER.startSeasonHourOfYear - 1;
		}
		return hour;
	}

	public static SeasonMeteorolocial getSeason(int hourOfYear) {
		return seasonByHour.get(hourOfYear);
	}

	private static SeasonMeteorolocial getSeasonForHour(int hourOfYear) {
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

	private SeasonMeteorolocial(int firstDayInYear, int firstHourInYear) {
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
