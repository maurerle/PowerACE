package data.exchange;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import tools.math.Interpolation;

/**
 * Interconnector between two market areas whose utilisation is modeled
 * endogenously
 * <p>
 * The interconnector is defined in a unilateral in a way (fromMarketArea ->
 * toMarketArea). The corresponding data (e.g. available capacity, exchange
 * flow, congestion revenue) are defined and stored in instance of respective
 * classes.
 *
 */
public class Interconnector {

	/**
	 * Bi-directional implementation of an interconnector
	 * <p>
	 * <b><u>In general, it is</b></u><br>
	 * - flow with positive sign -> flow from <code>marketAreaHere</code> to
	 * <code>marketAreaThere</code><br>
	 * - flow with negative sign -> vice versa
	 */
	public static class InterconnectorBidirectional {

		private final MarketArea marketAreaHere;
		private final MarketArea marketAreaThere;

		public InterconnectorBidirectional(MarketArea marketAreaHere, MarketArea marketAreaThere) {
			this.marketAreaHere = marketAreaHere;
			this.marketAreaThere = marketAreaThere;
		}

		@Override
		public boolean equals(Object thatObject) {
			if (this == thatObject) {
				return true;
			}
			if (thatObject == null) {
				return false;
			}
			if (getClass() != thatObject.getClass()) {
				return false;
			}
			// Cast to InterconnectorBidirectional
			final InterconnectorBidirectional thatInterconnectorBidirectional = (InterconnectorBidirectional) thatObject;

			if (marketAreaHere.isEqualMarketArea(
					thatInterconnectorBidirectional.marketAreaHere.getMarketAreaType())
					&& (marketAreaThere.isEqualMarketArea(
							thatInterconnectorBidirectional.marketAreaThere.getMarketAreaType()))) {
				return true;
			}
			if (marketAreaThere.isEqualMarketArea(
					thatInterconnectorBidirectional.marketAreaHere.getMarketAreaType())
					&& (marketAreaHere.isEqualMarketArea(
							thatInterconnectorBidirectional.marketAreaThere.getMarketAreaType()))) {
				return true;
			}
			return false;
		}

		public MarketArea getMarketAreaHere() {
			return marketAreaHere;
		}

		public MarketArea getMarketAreaThere() {
			return marketAreaThere;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = (prime * result) + ((marketAreaHere == null) ? 0 : marketAreaHere.hashCode())
					+ ((marketAreaThere == null) ? 0 : marketAreaThere.hashCode());
			return result;
		}

		@Override
		public String toString() {
			return marketAreaHere.getInitials() + "-" + marketAreaThere.getInitials();
		}
	}

	public enum SeasonNTC {

		SUMMER(
				2161,
				6552),
		WINTER(
				6553,
				2160);

		/** Get season of specified hour of year */
		private static SeasonNTC getSeason(int hourOfYear) {
			if ((hourOfYear >= SUMMER.hourOfYearStart) && (hourOfYear <= SUMMER.hourOfYearEnd)) {
				return SUMMER;
			} else {
				return WINTER;
			}
		}

		/** Last (including) hour of season */
		private int hourOfYearEnd; // NOPMD
		/** First (including) hour of season */
		private int hourOfYearStart; // NOPMD

		private SeasonNTC(int hourOfYearStart, int hourOfYearEnd) {
			this.hourOfYearStart = hourOfYearStart;
			this.hourOfYearEnd = hourOfYearEnd;
		}
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Interconnector.class.getName());

	/**
	 * Warning requested is before first data year of interconnector already
	 * appeared
	 */
	private boolean warningAppearedRequestedYearBeforeFirst = false;

	/** First year with available capacity data */
	private int firstYearAvailable = Integer.MAX_VALUE;

	/** Last year with available capacity data */
	private int lastYearAvailable = Integer.MIN_VALUE;

	/** Starting point of interconnector */
	private final MarketArea fromMarketArea;
	/** Hourly interconnection capacity mapped by year */
	private final Map<Integer, List<Double>> interconnectionCapacityHourly = new HashMap<>();
	/** Yearly interconnection capacity */
	private final Map<Integer, Map<SeasonNTC, Double>> interconnectionCapacitySeason = new HashMap<>();
	/** End point of interconnector */
	private final MarketArea toMarketArea;

	/**
	 * Public constructor to define new interconnector between the two specified
	 * market areas
	 *
	 * @param fromMarketArea
	 * @param toMarketArea
	 */
	protected Interconnector(MarketArea fromMarketArea, MarketArea toMarketArea) {
		this.fromMarketArea = fromMarketArea;
		this.toMarketArea = toMarketArea;
	}

	/**
	 * Get {@link#interconnectionCapacitySeason} for the specified year and
	 * season
	 */
	public float getCapacitySeason(int year, SeasonNTC season) {

		/* Check yearly value (recursively) */
		if (interconnectionCapacitySeason.containsKey(year)) {
			return interconnectionCapacitySeason.get(year).get(season).floatValue();
		}

		// Use first available data or just one value is available
		if ((year < firstYearAvailable) || (firstYearAvailable == lastYearAvailable)) {
			if (!warningAppearedRequestedYearBeforeFirst) {
				logger.info("Year (" + year
						+ ") requested is before first data year of interconnector (" + toString()
						+ ")");
				warningAppearedRequestedYearBeforeFirst = true;
			}
			return interconnectionCapacitySeason.get(firstYearAvailable).get(season).floatValue();
		}
		// Linear Interpolate
		if (year > lastYearAvailable) {
			// Point (x2,y2)
			final int yearSecondPoint = getYearFirstAvailable(year, false);
			final float capacitySecondPoint = getCapacitySeason(yearSecondPoint, season);

			// Point(x1,y1)
			final int yearFirstPoint = getYearFirstAvailable(yearSecondPoint - 1, false);
			final float capacityFirstPoint = getCapacitySeason(yearSecondPoint - 1, season);
			// However, its unlikely that the exchange capacity shrinks more
			// after the last year, therefore use constant values of the last
			// year instead
			return Math.max(capacitySecondPoint, Interpolation.linear(yearFirstPoint,
					yearSecondPoint, capacityFirstPoint, capacitySecondPoint, year));
		}

		// Years between two points
		// Point (x2,y2)
		final int yearSecondPoint = getYearFirstAvailable(year, true);
		final float capacitySecondPoint = getCapacitySeason(yearSecondPoint, season);
		// Point(x1,y1)

		final int yearFirstPoint = getYearFirstAvailable(year, false);
		final float capacityFirstPoint = getCapacitySeason(yearFirstPoint, season);

		return Interpolation.linear(yearFirstPoint, yearSecondPoint, capacityFirstPoint,
				capacitySecondPoint, year);
	}

	/** Get {@link#firstYearAvailable} */
	public int getFirstYearAvailable() {
		return firstYearAvailable;
	}

	/** Get {@link#fromMarketArea} */
	public MarketArea getFromMarketArea() {
		return fromMarketArea;
	}

	/** Get {@link#lastYearAvailable} */
	public int getLastYearAvailable() {
		return lastYearAvailable;
	}

	/** Get {@link#toMarketArea} */
	public MarketArea getToMarketArea() {
		return toMarketArea;
	}

	@Override
	public String toString() {
		return fromMarketArea.getInitials() + ">" + toMarketArea.getInitials();
	}

	private float getCapacitySeason(int year, int hourOfYear) {
		return getCapacitySeason(year, SeasonNTC.getSeason(hourOfYear));
	}

	/**
	 * Get the first year above/under the given year that has available
	 * interconnection data
	 */
	private int getYearFirstAvailable(int year, boolean increase) {
		if (interconnectionCapacitySeason.containsKey(year)) {
			return year;
		}
		if (increase) {
			return getYearFirstAvailable(year + 1, increase);
		} else {
			return getYearFirstAvailable(year - 1, increase);
		}
	}

	/** Add new time series of hourly interconnection capacities */
	protected void addCapacityHourly(int year, List<Double> values) {
		interconnectionCapacityHourly.put(year, values);
	}

	/** Add new yearly interconnection capacity */
	protected void addCapacitySeason(int year, SeasonNTC seasonNTC, double value) {

		// Add new year
		if (!interconnectionCapacitySeason.containsKey(year)) {
			interconnectionCapacitySeason.put(year, new HashMap<>());
		}

		if (!interconnectionCapacitySeason.get(year).containsKey(seasonNTC)) {
			interconnectionCapacitySeason.get(year).put(seasonNTC, value);
		} else {
			interconnectionCapacitySeason.get(year).put(seasonNTC,
					interconnectionCapacitySeason.get(year).get(seasonNTC) + value);
		}

		// Set new first data year
		if (year < firstYearAvailable) {
			firstYearAvailable = year;
		}

		// Set new last data Year
		if (year > lastYearAvailable) {
			lastYearAvailable = year;
		}
	}

	/**
	 * Get hourly interconnection capacity for specified date
	 * <p>
	 * First, hourly values are checked. If not available, the yearly value for
	 * specified year is checked.
	 */
	protected float getCapacity(int year, int hourOfYear) {

		/* Check hourly value */
		if (interconnectionCapacityHourly.containsKey(year)) {
			if ((interconnectionCapacityHourly.get(year).get(hourOfYear) == null)
					|| Double.isNaN(interconnectionCapacityHourly.get(year).get(hourOfYear))) {
				// Get seasonal capacity
				return getCapacitySeason(year, hourOfYear);
			}
			return interconnectionCapacityHourly.get(year).get(hourOfYear).floatValue();
		}

		// Get seasonal capacity
		return getCapacitySeason(year, hourOfYear);
	}
}
