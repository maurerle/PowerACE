package simulations.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.initialization.Settings;
import tools.other.Tuple;

/**
 * Manages all date and time operations
 * <p>
 * Currently, there is no explicit consideration of leap years. All years
 * feature 365 days
 *
 * @author
 *
 */
public class Date implements Callable<Void> {

	public static final int DAYS_PER_WEEK = 7;
	public static final int DAYS_PER_YEAR = 365;
	public static final int HOT_STARTUP_LENGTH = 8;
	public static final int HOURS_PER_DAY = 24;
	public static final int HOURS_PER_WEEK = DAYS_PER_WEEK * HOURS_PER_DAY;
	public static final int HOURS_PER_YEAR = DAYS_PER_YEAR * HOURS_PER_DAY;
	public static Map<Integer, Integer> lastHourOfYearMap = new HashMap<>();
	public static final int MONTH_PER_YEAR = 12;
	public static final int WARM_STARTUP_LENGTH = 48;
	public static final int WEEKS_PER_YEAR = 53;
	private static final int START_PEAK_TIME = 8;
	private static final int END_PEAK_TIME = 20;

	private static final int LAST_REGULAR_FORECAST_YEAR = 2050;
	/** Instance of DateTime with current date */
	private static LocalDateTime currentDate;
	private static int dayOfMonth;
	private static int dayOfTotal;
	private static int dayOfYear;
	private static int firstHourOfToday;
	private static Integer keyDaily;
	private static int lastDayOfYear;
	/** Last year for which detailed long-term price forecast is made */
	private static int lastDetailedForecastYear;
	/** Last simulated year which is fully based on historical input data */
	private static int lastHistoricalYear = 2014;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(Date.class.getName());
	private static int month;
	private static Map<Integer, LocalDateTime> referenceDays = new HashMap<>(DAYS_PER_YEAR);
	/** Reference year for scenario data (set via Settings class in xml) */
	private static int referenceYear;
	private static LocalDate referenceYearDate = LocalDate.of(2012, 01, 01);
	/** Start time of simulation */
	private static LocalDateTime startTime;
	/** Start year of simulation (set via Settings class in xml) */
	private static int startYear;
	/** Start year for plots (set via Settings class in xml) */
	private static int startYearPlots;
	/** Total days of simulation (set via Settings class in xml) */
	private static int totalDays;
	private static int weekOfWeekyear;
	private static int year;
	private static int yearIndex;
	/** Get DateTime instance of currentDate */
	public static LocalDateTime getCurrentDateTime() {
		return currentDate;
	}

	/**
	 * First year for investment valuation (typically current year incremented
	 * by 1 since investment decisions are called at the end of the year)
	 */
	public static int getCurrentInvestmentStartYear() {
		return Date.isLastDayOfYear() ? Date.getYear() + 1 : Date.getYear();
	}

	public static int getDayFromHourOfYear(int hourOfYear) {
		return 1 + (hourOfYear / 24);
	}

	/**
	 * Get day of month
	 *
	 * @return dayOfMonth [1...]
	 */
	public static int getDayOfMonth() {
		return dayOfMonth;
	}

	/**
	 * Get day index in simulation
	 *
	 * @return dayOfTotal [1...totalDays]
	 */
	public static int getDayOfTotal() {
		return dayOfTotal;
	}

	/**
	 * Get current day index of week
	 *
	 * @return dayOfWeek [1 Monday...7 Sunday]
	 */
	public static int getDayOfWeek() {
		return Date.checkReferenceDate().getDayOfWeek().getValue();
	}

	/** Returns the current day of week as String */
	public static String getDayOfWeekAsString() {
		final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
		return Date.checkReferenceDate().format(dtf);
	}

	/**
	 * Get current day of year
	 *
	 * @return dayOfYear [1...DAYS_PER_YEAR]
	 */
	public static int getDayOfYear() {
		return dayOfYear;
	}

	/**
	 * Get day of year
	 *
	 * CAUTION: Checks for leap year
	 *
	 * @param date
	 *            yyyy-mm-dd
	 *
	 * @return dayOfYear [1...dayOfYear]
	 */
	public static int getDayOfYearFromDate(String date) {

		final int year = Integer.parseInt(date.substring(0, 4));
		final int month = Integer.parseInt(date.substring(5, 7));
		final int day = Integer.parseInt(date.substring(8, 10));

		int dayOfYear = 0;

		// Programming style is not simplistic but more readable
		if (month == 1) {
			dayOfYear = day;
		} else if (month == 2) {
			dayOfYear = 31 + day;
		} else if (month == 3) {
			dayOfYear = 31 + 28 + day;
		} else if (month == 4) {
			dayOfYear = 31 + 28 + 31 + day;
		} else if (month == 5) {
			dayOfYear = 31 + 28 + 31 + 30 + day;
		} else if (month == 6) {
			dayOfYear = 31 + 28 + 31 + 30 + 31 + day;
		} else if (month == 7) {
			dayOfYear = 31 + 28 + 31 + 30 + 31 + 30 + day;
		} else if (month == 8) {
			dayOfYear = 31 + 28 + 31 + 30 + 31 + 30 + 31 + day;
		} else if (month == 9) {
			dayOfYear = 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + day;
		} else if (month == 10) {
			dayOfYear = 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + day;
		} else if (month == 11) {
			dayOfYear = 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31 + day;
		} else if (month == 12) {
			dayOfYear = 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31 + 30 + day;
		}

		// Check for leap year (only valid from 2001<2099
		if ((month > 2) && ((year % 4) == 0)) {
			dayOfYear++;
		}

		return dayOfYear;
	}

	/**
	 * Get number of days in current month.
	 * <p>
	 * If leap year, return only 30 days for December
	 */
	public static int getDaysInMonth() {
		if (currentDate.toLocalDate().isLeapYear() && (currentDate.getMonthValue() == 12)) {
			return currentDate.getMonth().maxLength() - 1;
		} else {
			return currentDate.getMonth().maxLength();
		}
	}

	/** Returns the number of days left in the current year */
	public static int getDaysUntilEndOfYear() {
		return (DAYS_PER_YEAR - Date.getDayOfYear()) + 1;
	}

	public static int getFirstHourOfDay(int day) {
		return (day - 1) * HOURS_PER_DAY;
	}

	/**
	 * @return the yearly number of the first hour of today. 2 Day, 1 hour would
	 *         be 24 (0-23+1)
	 */
	public static int getFirstHourOfToday() {
		return firstHourOfToday;
	}

	public static int getFirstYearlyHourOfMonth(int month) {
		return Date.getFirstYearlyHourOfMonth(month, year);
	}

	public static int getFirstYearlyHourOfMonth(int month, int year) {
		final LocalDateTime date = LocalDate.of(year, month, 1).atStartOfDay();
		final int day = date.getDayOfYear();
		return (day - 1) * HOURS_PER_DAY;
	}

	/** Determines hourOfDay from hourOfYear */
	public static int getHourOfDayFromHourOfYear(int hourOfYear) {
		return hourOfYear % HOURS_PER_DAY;
	}

	/**
	 *
	 *
	 * @param date
	 * @return Hour of year
	 */
	public static int getHourOfYear(LocalDateTime date) {
		return ((date.getDayOfYear() - 1) * HOURS_PER_DAY) + date.getHour();
	}

	/** Get hourOfYear from hourOfDay on current day */
	public static int getHourOfYearFromHourOfDay(int hourOfDay) {
		return Date.getHourOfYearFromHourOfDay(Date.getDayOfYear(), hourOfDay);
	}

	/**
	 * Get hourOfYear from hourOfDay on current day
	 *
	 * @param dayOfYear
	 *            [1...DAYS_PER_YEAR]
	 * @param hourOfDay
	 *            [0...(HOURS_PER_DAY-1)]
	 * @return
	 */
	public static int getHourOfYearFromHourOfDay(int dayOfYear, int hourOfDay) {
		return ((dayOfYear - 1) * HOURS_PER_DAY) + hourOfDay;
	}

	/**
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyDaily() {
		return keyDaily;
	}

	/**
	 * @param day
	 *            [1,DAYS_PER_YEAR]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyDaily(int day) {
		return getKeyDaily(year, day);
	}

	/**
	 * @param year
	 *            e.g. 2010
	 * @param day
	 *            [1,DAYS_PER_YEAR]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyDaily(int year, int day) {
		return ((1000 * year) + day) - 1;
	}

	/**
	 * Returns a key that can used for Maps based on the given parameter.
	 *
	 * @param year
	 *            e.g. 2010
	 * @param day
	 *            [1,DAYS_PER_YEAR]
	 * @param hour
	 *            [0,23]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyHourly(int year, int day, int hour) {
		return (10000 * year) + ((day - 1) * HOURS_PER_DAY) + hour;
	}

	/**
	 * @param day
	 *            [1,DAYS_PER_YEAR]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyHourlyWithDay(int day) {
		return (10000 * year) + day;
	}

	/**
	 * @param year
	 *            e.g. 2010
	 * @param day
	 *            [1,DAYS_PER_YEAR]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyHourlyWithDay(int year, int day) {
		return (10000 * year) + day;
	}

	/**
	 * Returns a key that can used for Maps based on the given parameter.
	 *
	 * @param hourOfDay
	 *            [0,23]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyHourlyWithHourOfDay(int hourOfDay) {
		return (10000 * year) + ((dayOfYear - 1) * HOURS_PER_DAY) + hourOfDay;
	}

	/**
	 * Returns a key that can used for Maps based on the given parameter.
	 *
	 * @param day
	 *            [1,DAYS_PER_YEAR]
	 * @param hourOfDay
	 *            [0,23]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyHourlyWithHourOfDay(int day, int hourOfDay) {
		return (10000 * year) + ((day - 1) * HOURS_PER_DAY) + hourOfDay;
	}

	/**
	 * @param hourOfYear
	 *            [0,HOURS_PER_YEAR]
	 * @return A key that can used for maps based on the current year and the
	 *         given hour.
	 */
	public static Integer getKeyHourlyWithHourOfYear(int hourOfYear) {
		return (10000 * year) + hourOfYear;
	}

	/**
	 * @param year
	 *            e.g. 2010
	 * @param hour
	 *            [0,HOURS_PER_YEAR]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyHourlyWithHourOfYear(int year, int hourOfYear) {
		return (10000 * year) + hourOfYear;
	}

	/**
	 * @param year
	 *            e.g. 2010
	 * @param month
	 *            [1,MONTHS_PER_YEAR]
	 * @return A key that can used for Maps based on the parameters.
	 */
	public static Integer getKeyMonthly(int year, int month) {
		return (100 * year) + month;
	}

	/**
	 * @param year
	 * @return The number of hours in the requested year, e.g. HOURS_PER_YEAR.
	 */
	public static int getLastCompleteMonthOfYear() {

		// Either december is almost complete (online looking at 8760 hours or
		// last hour is really last hour of month)
		if ((Date.getLastHourOfYear() == HOURS_PER_YEAR)
				|| (currentDate.withDayOfYear(Date.getLastDayOfYear()) == currentDate
						.withDayOfYear(Date.getLastDayOfYear())
						.with(TemporalAdjusters.lastDayOfMonth()))) {
			return currentDate.withDayOfYear(Date.getLastDayOfYear()).getMonthValue();
		}
		// get last month minus 1
		else {
			return currentDate.withDayOfYear(Date.getLastDayOfYear()).getMonthValue() - 1;
		}
	}

	/** Set to DAYS_PER_YEAR instead of 0, when a whole year is requested */
	public static int getLastDayOfLastYear() {
		return (Date.getTotalDays() % DAYS_PER_YEAR) == 0
				? DAYS_PER_YEAR
				: Date.getTotalDays() % DAYS_PER_YEAR;
	}

	/**
	 * @return The last day of the current year [1, DAYS_PER_YEAR]
	 */
	public static int getLastDayOfYear() {
		return lastDayOfYear;
	}

	/**
	 * @param year
	 * @return The last day of the requested year [1, DAYS_PER_YEAR]
	 */
	public static int getLastDayOfYear(int year) {
		int day;

		if (year == Date.getLastYear()) {
			day = Date.getLastDayOfLastYear();
		} else {
			day = DAYS_PER_YEAR;
		}

		return day;
	}

	/** {@link Date#lastDetailedForecastYear} */
	public static int getLastDetailedForecastYear() {
		return lastDetailedForecastYear;
	}

	/** Year index of {@link Date#lastDetailedForecastYear} */
	public static int getLastDetailedForecastYearIndex() {
		return Date.getYearIndex(lastDetailedForecastYear);
	}

	/**
	 * @param year
	 * @return The number of hours in the requested year, e.g. HOURS_PER_YEAR.
	 */
	public static int getLastHourOfYear() {
		return lastHourOfYearMap.get(year);
	}

	/**
	 * @param year
	 *            e.g. 2010
	 * @return The number of hours in the requested year, e.g. HOURS_PER_YEAR.
	 */
	public static int getLastHourOfYear(int year) {
		if (!lastHourOfYearMap.containsKey(year)) {
			return -1;
		}

		return lastHourOfYearMap.get(year);
	}

	/**
	 * @param year
	 * @return The number of hours in the requested year, e.g. HOURS_PER_YEAR.
	 */
	public static int getLastMonthOfYear() {
		return currentDate.withDayOfYear(Date.getLastDayOfYear()).getMonthValue();
	}

	public static int getLastRegularForecastYear() {
		return LAST_REGULAR_FORECAST_YEAR;
	}

	/** Get last year of simulation */
	public static int getLastYear() {
		return startYear + (int) Math.floor((totalDays - 1) / 365.0);
	}

	public static int getLastYearlyHourOfMonth(int month) {
		return Date.getLastYearlyHourOfMonth(month, year);
	}

	public static int getLastYearlyHourOfMonth(int month, int year) {
		final LocalDate date = LocalDate.of(year, month, 1)
				.with(TemporalAdjusters.lastDayOfMonth());

		// Correct if leap year
		final int day = Math.min(date.getDayOfYear(), DAYS_PER_YEAR);
		// hour of year starts with 0 therefore minus 1
		return (day * HOURS_PER_DAY) - 1;
	}

	/** Returns the month of the current date */
	public static int getMonth() {
		return month;
	}

	/** Returns the month of the day of the reference year. */
	public static int getMonth(int day) {
		return referenceYearDate.withDayOfYear(day).getMonthValue();
	}

	/**
	 * The number of years, the model is running. If run stops in the middle of
	 * year, the year is also taken into account.
	 *
	 * @return numberOfYears
	 */
	public static int getNumberOfYears() {
		return (int) Math.ceil(totalDays / 365.0);
	}

	public static Integer getReferenceYear() {
		return referenceYear;
	}

	/** Get the start time of simulation in the specified format */
	public static LocalDateTime getStartTime() {
		return startTime;
	}

	/** Get the start time of simulation in the specified format */
	public static String getStartTimeFormatted(String format) {
		return startTime.format(DateTimeFormatter.ofPattern(format));
	}

	public static int getStartYear() {
		return startYear;
	}

	public static int getStartYearPlots() {
		return Math.max(startYearPlots, startYear);
	}

	public static int getTotalDays() {
		return totalDays;
	}

	public static int getTotalHours() {
		return totalDays * HOURS_PER_DAY;
	}

	public static int getWeekOfWeekyear() {
		return weekOfWeekyear;
	}

	public static int getWeekOfWeekyearFromHourOfYear(int hourOfYear) {
		return 1 + (hourOfYear / Date.HOURS_PER_WEEK);
	}

	public static int getWeekOfYear() {
		return (getDayOfYear() - 1) / DAYS_PER_WEEK;
	}

	public static int getYear() {
		return year;
	}

	public static String getYearDayDate() {
		return Integer.toString(Date.getYear()) + "/" + Date.getDayOfYear();
	}

	/**
	 * Get the index of current year
	 *
	 * @return yearIndex, e.g. 2010 - 2008 (startYear) = 2
	 */
	public static int getYearIndex() {
		return yearIndex;
	}

	/**
	 * Get index of specified year
	 *
	 * @return yearIndex (year - startYear)
	 * @param year
	 */
	public static int getYearIndex(int year) {
		return year - startYear;
	}

	/**
	 * This method increments the date by one day. It is called by the
	 * StepManager at the end of each day.
	 * <p>
	 * In leap years the last day of the year is skipped.
	 */
	public static void incrementDay() {
		// Increment currentDate by 1 day (except for leap days)
		if ((currentDate.getDayOfYear() == 365) && currentDate.toLocalDate().isLeapYear()) {
			currentDate = currentDate.plusDays(2);
		} else {
			currentDate = currentDate.plusDays(1);
		}

		if (currentDate.getDayOfYear() >= 366) {
			logger.error("Error when incrementing day (day count higher than 365)");
		}

		Date.updateFields();
	}

	/** Check whether current day is first day of simulation */
	public static boolean isFirstDay() {
		return (year == startYear) && (dayOfYear == 1);
	}

	/** Check whether current day is first day of current year */
	public static boolean isFirstDayOfYear() {
		return dayOfYear == 1;
	}

	/** Check whether current year is first year of simulation */
	public static boolean isFirstYear() {
		return year == startYear;
	}

	/** Check whether current day is last day of simulation */
	public static boolean isLastDay() {
		return Date.getDayOfTotal() == totalDays;
	}

	/** Check whether current day is last simulated day of current year */
	public static boolean isLastDayOfYear() {
		return ((Date.getDayOfYear() % DAYS_PER_YEAR) == 0) || Date.isLastDay();
	}

	/** Check whether current day is last day of simulation */
	public static boolean isLastYear() {
		return Date.getLastYear() == year;
	}

	/** Check whether hour of year is in peak time */
	public static boolean isPeakTime(int hourOfYear) {
		final int hourOfDay = getHourOfDayFromHourOfYear(hourOfYear);
		return ((hourOfDay >= START_PEAK_TIME) && (hourOfDay < END_PEAK_TIME));
	}

	/** Returns true if current day is a week day, false if it is weekend day */
	public static boolean isWeekDay() {
		final DayOfWeek daytype = Date.checkReferenceDate().getDayOfWeek();
		if ((daytype == DayOfWeek.SATURDAY) || (daytype == DayOfWeek.SUNDAY)) {
			return false;
		}
		return true;
	}

	/**
	 * Returns true if the given day is a week day in the referenceYear
	 *
	 * @param day
	 * @return
	 */
	public static boolean isWeekDay(int day) {
		final DayOfWeek daytype = referenceYearDate.withDayOfYear(day).getDayOfWeek();
		if ((daytype == DayOfWeek.SATURDAY) || (daytype == DayOfWeek.SUNDAY)) {
			return false;
		}
		return true;
	}

	/**
	 * Prints the total time needed for the simulation
	 *
	 * @param timeForecast
	 * @param numberOfRepetitions
	 * @return Returns a tuple object containing in the first position the
	 *         formatted total length and in the second position the formatted
	 *         average length per run (in case of multiruns)
	 */
	public static Tuple<String, String> printTotalTime(LocalDateTime timeForecast,
			int numberOfRepetitions) {
		final LocalDateTime end = LocalDateTime.now();
		final long length = ChronoUnit.MILLIS.between(timeForecast, end);
		final long averageLength = (long) (length
				/ ((numberOfRepetitions * Date.getTotalDays()) / 365.0));

		// Format output
		final String lengthFormatted = String.format("%d min, %d sec",
				TimeUnit.MILLISECONDS.toMinutes(length), TimeUnit.MILLISECONDS.toSeconds(length)
						- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(length)));
		final String averageLengthFormatted = String.format("%d min, %d sec",
				TimeUnit.MILLISECONDS.toMinutes(averageLength),
				TimeUnit.MILLISECONDS.toSeconds(averageLength) - TimeUnit.MINUTES
						.toSeconds(TimeUnit.MILLISECONDS.toMinutes(averageLength)));

		// Write output
		logger.info("Time for all tics " + lengthFormatted);
		logger.info("Average time for one year " + averageLengthFormatted);

		return new Tuple<>(lengthFormatted, averageLengthFormatted);
	}

	/** Reset date to the end of the previous year */
	public static void resetDateToEndOfLastYear() {
		currentDate = currentDate.withYear(Date.getYear() - 1).withDayOfYear(Date.DAYS_PER_YEAR);
		Date.updateFields();
	}

	/**
	 * Set the initial date (first day in first year of simulation) at the
	 * beginning of the simulation.
	 *
	 * @param year
	 *            start year of simulation
	 */
	public static void setInitialDate(int startYear, int startYearPlots, Integer referenceYear,
			int totalDays) {
		currentDate = LocalDateTime.of(startYear, 1, 1, 0, 0);
		Date.totalDays = totalDays;
		Date.startYear = startYear;
		Date.startYearPlots = startYearPlots;

		// Set reference year only if parameter is set in xml file (if it is not
		// set explicitly the int parameter is initialized in Settings with 0)
		if (referenceYear == null) {
			Date.referenceYear = 2012;
		} else {
			Date.referenceYear = referenceYear;
		}

		// Add maximal construction time of new plants, really difficult to
		// determine therefore add some more years which can represent overhead
		// values
		final int additionalBackup = 15;
		lastDetailedForecastYear = startYear + Date.getNumberOfYears()
				+ (2 * Settings.getInvestmentHorizonMax()) + additionalBackup;

		lastHourOfYearMap = new HashMap<>();
		final int lastYear = Date.getLastYear();
		for (int year = Date.startYear; year <= lastYear; year++) {
			if (year < lastYear) {
				lastHourOfYearMap.put(year, HOURS_PER_YEAR);
			} else {
				lastHourOfYearMap.put(year, Date.getLastDayOfYear(year) * HOURS_PER_DAY);
			}
		}

		// Add reference year map
		final LocalDateTime firstDayOfYear = LocalDateTime.of(Date.referenceYear, 1, 1, 0, 0);
		for (int dayOffset = 0; dayOffset < DAYS_PER_YEAR; dayOffset++) {
			// Attention day starts with 1
			final int key = Date.getKeyDaily(Date.referenceYear, 1 + dayOffset);
			referenceDays.put(key, firstDayOfYear.plusDays(dayOffset));
		}

		Date.updateFields();
	}

	public static void setStartTime(LocalDateTime startTime) {
		Date.startTime = startTime;
	}

	/**
	 * Checks if current date is still within "historical" period. If not return
	 * the adjusted date in the reference year.
	 */
	private static LocalDateTime checkReferenceDate() {
		if (currentDate.getYear() <= lastHistoricalYear) {
			return currentDate;
		} else {
			return Date.getReferenceDate();
		}
	}

	/** Returns the adjusted date in the reference year */
	private static LocalDateTime getReferenceDate() {
		final int key = Date.getKeyDaily(referenceYear, dayOfYear);
		return referenceDays.get(key);
	}

	private static void updateFields() {
		year = currentDate.getYear();
		dayOfYear = currentDate.getDayOfYear();
		dayOfMonth = currentDate.getDayOfMonth();

		weekOfWeekyear = currentDate.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());
		month = currentDate.getMonthValue();
		yearIndex = Date.getYearIndex(Date.getYear());
		dayOfTotal = Date.getDayOfYear() + (Date.getYearIndex() * DAYS_PER_YEAR);
		firstHourOfToday = (Date.getDayOfYear() - 1) * HOURS_PER_DAY;
		lastDayOfYear = Date.getLastDayOfYear(Date.getYear());
		keyDaily = ((1000 * year) + dayOfYear) - 1;
	}

	@Override
	public Void call() {
		return null;
	}
}