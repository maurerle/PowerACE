package markets.trader.future.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.Bid.BidType;
import markets.trader.TraderType;
import simulations.PowerMarkets;
import simulations.scheduling.Date;
import supply.powerplant.Plant;

/**
 * Class represents an hourly bid that contains BidPoints for that hour.
 *
 * @since 12.10.2004
 * @author Petr Nenutil et al
 */
public class DayAheadHourlyBidForecast {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(DayAheadHourlyBidForecast.class.getName());
	/** Resulting sold/purchased volume in MW */
	private float assignedVolume;
	private final TraderType bidder;
	/** ID of this bid; is positive for hourly bids */
	private int bidID = 0;
	/** Array of bidPoints, each containing one price volume pair and bid ID */
	private final List<HourlyBidPowerForecast> bidPoints = Collections
			.synchronizedList(new ArrayList<HourlyBidPowerForecast>());
	/** Number of hour the bid is valid for. Must be from range [0, 23]. */
	private final int hour;
	/** Resulting market clearing price in EUR */
	private float marketClearingPrice;
	/** This variable helps to keep reference from one power plant to one bid */
	private int plantReference;
	/**
	 * Bid status (SUCCESSFUL, DEMAND_OVERHANG, OFFER_OVERHANG or INVALID BID)
	 */
	private int status;

	/**
	 * Constructs a new hourly price independent bid for specified hour and
	 * volume
	 *
	 * @param hour
	 *            Hour for which the bid is valid
	 * @param volume
	 *            The volume of the price independent bid in MW
	 * @param sourceType
	 *            The type of the source e.g. exchange or renewable
	 */
	public DayAheadHourlyBidForecast(int hour, float price, float volume, TraderType bidder) {
		checkHour(hour);
		this.hour = hour;
		this.bidder = bidder;
		final BidType bidType = volume < 0 ? BidType.SELL : BidType.ASK;
		bidPoints.add(
				new HourlyBidPowerForecast(price, Math.abs(volume), hour, bidID, bidType, bidder));
	}

	/**
	 * Constructs a new bid with for given hour h and two arrays.
	 *
	 * @param hour
	 *            Hour for which the bid is valid
	 * @param prices
	 *            Array containing prices in EUR/MWh
	 * @param volumes
	 *            Array containing volumes in MW
	 */
	public DayAheadHourlyBidForecast(int hour, float[] prices, float[] volumes, TraderType bidder) {
		checkHour(hour);
		this.hour = hour;
		this.bidder = bidder;

		for (int i = 0; i < prices.length; i++) {
			final BidType bidType = volumes[i] < 0 ? BidType.SELL : BidType.ASK;
			bidPoints.add(new HourlyBidPowerForecast(prices[i], Math.abs(volumes[i]), hour, bidID,
					bidType, bidder));
		}
	}

	/**
	 * Constructs a new bid with for a given hour h and a two dimensional array
	 * of price volume pairs.
	 *
	 * @param hour
	 *            Hour for which the bid is valid
	 * @param plant
	 *            Reference to the plant to which this bid belongs to
	 * @param steps
	 *            Array containing [0][] prices in EUR/MWh and [1][] volumes in
	 *            MW
	 */
	public DayAheadHourlyBidForecast(int hour, float[][] steps, TraderType bidder) {
		checkHour(hour);
		this.hour = hour;
		float price, volume;
		this.bidder = bidder;

		for (int i = 0; i < steps[0].length; i++) {
			final BidType bidType = steps[1][i] <= 0 ? BidType.SELL : BidType.ASK;
			price = steps[0][i];
			volume = steps[1][i];
			bidPoints.add(new HourlyBidPowerForecast(price, Math.abs(volume), hour, bidID, bidType,
					bidder));
		}
	}

	/**
	 * Constructs a new bid with for given hour h and two arrays.
	 *
	 * @param hour
	 *            Hour for which the bid is valid
	 * @param prices
	 *            ArrayList containing prices in EUR/MWh as floats
	 * @param volumes
	 *            ArrayList containing volumes in MW as floats
	 */
	public DayAheadHourlyBidForecast(int hour, List<Float> prices, List<Float> volumes,
			TraderType bidder) {
		checkHour(hour);
		this.hour = hour;
		this.bidder = bidder;
		float priceBids, volumeBids;

		if (prices.size() == volumes.size()) {
			for (int i = 0; i < prices.size(); i++) {
				final BidType bidType = volumes.get(i) <= 0 ? BidType.SELL : BidType.ASK;
				priceBids = prices.get(i).floatValue();
				volumeBids = volumes.get(i).floatValue();
				bidPoints.add(new HourlyBidPowerForecast(priceBids, Math.abs(volumeBids), hour,
						bidID, bidType, bidder));
			}
		} else {
			logger.error(
					"Error while constructing a new SpotHourlyBid: prices and volumes must have the same length.");
		}
	}

	/**
	 * Constructs a new bid with for given hour h and two arrays.
	 *
	 * @param hour
	 *            Hour for which the bid is valid
	 * @param prices
	 *            ArrayList containing prices in EUR/MWh as floats
	 * @param volumes
	 *            ArrayList containing volumes in MW as floats
	 * @param plants
	 *            ArryList containing plants submitting the bids
	 */
	public DayAheadHourlyBidForecast(int hour, List<Float> prices, List<Float> volumes,
			TraderType bidder, List<Plant> plants) {
		checkHour(hour);
		this.hour = hour;
		this.bidder = bidder;
		float priceBids, volumeBids;
		Plant plant;
		if ((prices.size() == volumes.size()) & (prices.size() == plants.size())) {
			for (int i = 0; i < prices.size(); i++) {
				final BidType bidType = volumes.get(i) <= 0 ? BidType.SELL : BidType.ASK;
				priceBids = prices.get(i).floatValue();
				volumeBids = volumes.get(i).floatValue();
				plant = plants.get(i);

				bidPoints.add(new HourlyBidPowerForecast(priceBids, Math.abs(volumeBids), hour,
						bidID, bidType, bidder, plant));
			}
		} else {
			logger.error(
					"Error while constructing a new SpotHourlyBid: prices and volumes must have the same length.");
		}
	}

	/**
	 * Constructs a new hourly price independent bid for specified hour.
	 *
	 * @param hour
	 *            Hour for which the bid is valid [0,23]
	 */
	public DayAheadHourlyBidForecast(int hour, TraderType bidder) {
		checkHour(hour);
		this.hour = hour;
		this.bidder = bidder;
	}

	/** Adds an BidPoint to the list of BidPoints */
	public void addBidPoint(HourlyBidPowerForecast bidPoint) {
		bidPoints.add(bidPoint);
	}

	public float getAssignedVolume() {
		return assignedVolume;
	}

	public TraderType getBidder() {
		return bidder;
	}

	/** Returns Array of BidPrices */
	public List<HourlyBidPowerForecast> getBidPoints() {
		return bidPoints;
	}

	/** Returns the bid volume as a sum of all bid point volumes */
	public float getBidVolume() {
		float vol = 0;
		for (int index = 0; index < bidPoints.size(); index++) {
			vol += bidPoints.get(index).getVolume();
		}
		return vol;
	}

	public int getHour() {
		return hour;
	}

	public float getMarketClearingPrice() {
		return marketClearingPrice;
	}

	public int getPlantReference() {
		return plantReference;
	}

	public int getStatus() {
		return status;
	}

	/**
	 * Checks if this hourly bid is valid. If only a bid point of the hourly bid
	 * is invalid, true is returned and only the bid point is removed.
	 */
	public boolean isValid(float priceMinimum, float priceMaximum) {

		// Check if hour is between 0-23
		boolean valid = (hour >= 0) && (hour <= 23);

		// Check bid points
		final Iterator<HourlyBidPowerForecast> iterator = bidPoints.iterator();
		while (iterator.hasNext()) {
			final HourlyBidPowerForecast bidPoint = iterator.next();
			if (!bidPoint.isValid(priceMinimum, priceMaximum)) {
				iterator.remove();
			}
		}

		// Check if bid is non empty, after removing invalid BidPoints
		valid = valid && (bidPoints.size() >= 1);

		return valid;
	}

	public void setAssignedVolume(float assignedVolume) {
		this.assignedVolume = assignedVolume;
	}

	/** Sets the bidID for this bid and its corresponding bid points */
	public void setBidID(int identifier) {
		bidID = identifier;
		for (int i = 0; i < bidPoints.size(); i++) {
			bidPoints.get(i).setIdentfier(identifier);
		}
	}

	public void setMarketClearingPrice(float marketClearingPrice) {
		this.marketClearingPrice = marketClearingPrice;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	@Override
	public String toString() {
		final StringBuffer pointString = new StringBuffer();
		for (int i = 0; i < bidPoints.size(); i++) {
			pointString.append(bidPoints.get(i).toString());
		}
		return "bidID " + bidID + ", hour " + hour + ", MCP "
				+ PowerMarkets.getDecimalFormat().format(getMarketClearingPrice()) + ", resVol "
				+ PowerMarkets.getDecimalFormat().format(getAssignedVolume()) + ", status "
				+ getStatus() + ", " + pointString;
	}

	private void checkHour(int hour) {
		if ((hour < 0) || (hour >= Date.HOURS_PER_YEAR)) {
			logger.warn("Hour " + hour + " is out of boundaries!");
		}
	}

}