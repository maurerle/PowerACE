package markets.clearing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.Bid;
import markets.bids.Bid.BidType;
import markets.bids.power.HourlyBidPower;
import markets.operator.spot.DayAheadMarketOperator;
import markets.trader.TraderType;
import simulations.MarketArea;
import simulations.scheduling.Date;

/**
 * Carries out a clearing algorithm where intersection of demand and supply
 * curve is found. No block bids are allowed.
 * 
 * @author
 */

public class SimpleClearing {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(DayAheadMarketOperator.class.getName());

	public static void main(String[] args) {
		final ArrayList<Bid> customBidList = new ArrayList<>();
		customBidList.add(new HourlyBidPower(0, 10, 0, 0, BidType.SELL, TraderType.UNKNOWN, null));
		customBidList.add(new HourlyBidPower(2, 5, 0, 0, BidType.SELL, TraderType.UNKNOWN, null));
		customBidList.add(new HourlyBidPower(1, 15, 0, 0, BidType.ASK, TraderType.UNKNOWN, null));
		final SimpleClearing market = new SimpleClearing("name", 0, null);
		market.clearMarket(customBidList);

		logger.info("Price " + market.getClearingPrice());
		logger.info("Volume " + market.getClearingVolume());
	}

	/** Array list that contains all ask bids. */
	private final List<Bid> askBidPoints = new ArrayList<>();
	/** the MCP of this call market */
	private float clearingPrice;
	private float clearingVolume;
	private final float maximumPrice;
	/** Name of the market */
	private final String name;
	private PriceCurvePoint outcome;
	private float remainingAskVolume;
	private float remainingSellVolume;
	/** Array list that contains all sell bids. */
	private final List<Bid> sellBidPoints = new ArrayList<>();
	/** All bids submitted */
	private final List<Bid> temporaryBids = new ArrayList<>();
	/** the MCP of this call market */
	private float temporaryPrice;
	/** All price volume combination for temporaryBids */
	private final List<PriceCurvePoint> temporaryPriceFunction = new ArrayList<>();
	/** the MCP of this call market */
	private float temporaryVolume;

	/**
	 * @param bidList
	 *            contains all bids and asks for this auction
	 */
	public SimpleClearing(String name, float maximumPrice, MarketArea marketArea) {
		this.name = name;
		this.maximumPrice = maximumPrice;
	}

	/**
	 * Executes the market. Matches all the bids with a negative spread. Price
	 * is the arithmetic mean while volume is the minimum of the two offers.
	 * Resulting volumes and prices are written into a list that is part of each
	 * bid.
	 * 
	 * @return bids The list of bids; the method clear market has added
	 *         information about results to each bid
	 */
	public void clearMarket(List<? extends Bid> bids) {
		checkBids(bids);
		initBidList(bids);
		calculateBidCurves();
		findMarketOutcomes(0, temporaryPriceFunction.size() - 1);
		clearingPrice = temporaryPrice;
		clearingVolume = temporaryVolume;
		setBidVolumes(bids);
	}

	public float getClearingPrice() {
		return clearingPrice;
	}

	public float getClearingVolume() {
		return clearingVolume;
	}

	public String getName() {
		return name;
	}

	public PriceCurvePoint getOutcome() {
		return outcome;
	}

	/**
	 * Calculate the bid curves from block bids and hourly bids.
	 * <p>
	 * Writes hourly lists into {@link #temporaryPriceFunction} that contain for
	 * each price the corresponding <b>ask/sell volumes</b>. Each volume has a
	 * maximal and minimal value.
	 */
	private void calculateBidCurves() {

		// Find unique list of prices
		final ListIterator<Bid> bidIterator = temporaryBids.listIterator();
		final TreeSet<Float> prices = new TreeSet<>();
		while (bidIterator.hasNext()) {
			prices.add(bidIterator.next().getPrice());
		}

		// Write unique sorted prices into hourly price curve
		for (final float price : prices) {
			temporaryPriceFunction.add(new PriceCurvePoint(price));
		}

		// Write sell and ask volumes for each price
		writeSellVolumes(prices);
		writeAskVolumes(prices);
	}

	/**
	 * Set accepted volumes for hourly bids.
	 * 
	 * @param bids
	 */
	private void calculateHourlyBidVolumes(List<? extends Bid> bids) {

		for (final Bid bid : askBidPoints) {
			if ((bid.getPrice() >= clearingPrice) && (remainingAskVolume > 0)) {
				bid.setVolumeAccepted(Math.min(bid.getVolume(), remainingAskVolume));
				remainingAskVolume -= bid.getVolumeAccepted();
			}
		}

		for (final Bid bid : sellBidPoints) {
			if ((bid.getPrice() <= clearingPrice) && (remainingSellVolume > 0)) {
				bid.setVolumeAccepted(Math.min(bid.getVolume(), remainingSellVolume));
				remainingSellVolume -= bid.getVolumeAccepted();
			}
		}

	}

	private void checkBids(List<? extends Bid> bids) {
		for (final Bid bid : bids) {
			temporaryBids.add(bid);
		}
	}

	/**
	 * Calculate the resulting prices for the priceCurvePoint. If several
	 * solutions exist
	 * <li>for the price, take average price from upper and lower bound.</li>
	 * <li>for the volume, maximize the traded volume.</li> <br>
	 * <br>
	 * 
	 * @param index
	 *            the index of the priceCurvePoint in the temporaryPriceFunction
	 * @param priceCurvePoint
	 * 
	 * @return <code>true</code> if intersection has be found or no intersection
	 *         exists
	 */
	private boolean findMarketIntersection(int index, PriceCurvePoint priceCurvePoint) {

		// No equilibrium at this point
		if ((Math.abs(priceCurvePoint.getSellVolumeMaximum()) < Math
				.abs(priceCurvePoint.getAskVolumeMinimum()))) {
			// but also not a problem since it is not the last point
			if ((index + 1) < temporaryPriceFunction.size()) {
				return false;
			}
			// which is problem since it is the last point in the
			// list, meaning demand cannot be satisfied
			else if ((index + 1) == temporaryPriceFunction.size()) {
				logger.error("No market equilibrium found.");
				return true;
			}
		}

		// Ambiguous Volume, i.e. ask and supply step intersect
		// Rule: Maximize traded volume
		if ((priceCurvePoint.getAskVolumeMinimum() != priceCurvePoint.getAskVolumeMaximum())
				&& (priceCurvePoint.getSellVolumeMinimum() != priceCurvePoint
						.getSellVolumeMaximum())
				&& (priceCurvePoint.getAskVolumeMinimum() <= priceCurvePoint.getSellVolumeMaximum())
				&& (priceCurvePoint.getSellVolumeMinimum() <= priceCurvePoint
						.getAskVolumeMaximum())) {
			temporaryPrice = priceCurvePoint.getPrice();
			temporaryVolume = Math.min(priceCurvePoint.getAskVolumeMaximum(),
					priceCurvePoint.getSellVolumeMaximum());
			outcome = priceCurvePoint;
			return true;
		}

		// Ambiguous Price, i.e. ask or supply steps lands directly on
		// supply/ask curve
		// Rule: Set price to the middle of price interval
		if (priceCurvePoint.getAskVolumeMinimum() == priceCurvePoint.getSellVolumeMaximum()) {
			final float lowerBound = priceCurvePoint.getPrice();
			final float upperBound;
			if (!((index + 1) == temporaryPriceFunction.size())) {
				upperBound = temporaryPriceFunction.get(index + 1).getPrice();
			} else {
				upperBound = lowerBound;
			}
			temporaryVolume = priceCurvePoint.getAskVolumeMinimum();
			temporaryPrice = (lowerBound + upperBound) / 2;
			outcome = priceCurvePoint;
			return true;
		}

		// Unambiguous price and volume
		// Either a) Sell step intersects ask curve
		if ((priceCurvePoint.getAskVolumeMinimum() == priceCurvePoint.getAskVolumeMaximum())
				&& (priceCurvePoint.getSellVolumeMinimum() < priceCurvePoint.getAskVolumeMinimum())
				&& (priceCurvePoint.getAskVolumeMinimum() < priceCurvePoint
						.getSellVolumeMaximum())) {
			temporaryPrice = priceCurvePoint.getPrice();
			temporaryVolume = priceCurvePoint.getAskVolumeMinimum();
			outcome = priceCurvePoint;
			return true;
		}
		// or b) Ask step intersects sell curve
		if ((priceCurvePoint.getSellVolumeMinimum() == priceCurvePoint.getSellVolumeMaximum())
				&& (priceCurvePoint.getAskVolumeMinimum() < priceCurvePoint.getSellVolumeMinimum())
				&& (priceCurvePoint.getSellVolumeMinimum() < priceCurvePoint
						.getAskVolumeMaximum())) {
			temporaryPrice = priceCurvePoint.getPrice();
			temporaryVolume = priceCurvePoint.getSellVolumeMinimum();
			outcome = priceCurvePoint;
			return true;
		}

		return false;
	}

	/**
	 * Find the market outcomes.
	 * 
	 * Calculate the resulting prices from {@link #temporaryPriceFunction} via
	 * {@link #findMarketIntersection(hour, index, PriceCurvePoint)}
	 * 
	 * In order to save time, first, the start of the list, then end and then
	 * the last point of the price function is checked. If none of these points
	 * is the intersection of supply and demand, look at <i>the interval where
	 * the intersection is located</i> either [start, middle] or [middle, end].
	 * 
	 * @param start
	 *            start point of temporaryPriceFunction for this hour
	 *            <code>[0,size-2]</code>
	 * @param end
	 *            end point of temporaryPriceFunction for this hour
	 *            <code>[1,size-1]</code>
	 * 
	 */
	private void findMarketOutcomes(int start, int end) {
		final Map<Integer, PriceCurvePoint> priceCurvePoints = new HashMap<>(3);
		priceCurvePoints.put(start, temporaryPriceFunction.get(start));
		priceCurvePoints.put(end, temporaryPriceFunction.get(end));

		// Check if market can be cleared! If not set price to maximal price
		// that demand is willing to pay and volume to maximal volume supply is
		// willing to produce. This is volume is less than what demand ask for!
		if (end < 1) {

			// Last volume
			temporaryVolume = Math.min(priceCurvePoints.get(end).getAskVolumeMaximum(),
					priceCurvePoints.get(end).getSellVolumeMaximum());

			// No price, if nothing is traded
			if (temporaryVolume > 0) {
				temporaryPrice = temporaryPriceFunction.get(end).getPrice();
			} else {
				temporaryPrice = Float.NaN;
			}

			return;
		}

		if ((priceCurvePoints.get(end).getPrice() >= maximumPrice) && (priceCurvePoints.get(end)
				.getSellVolumeMaximum() < priceCurvePoints.get(end).getAskVolumeMaximum())) {

			// Only print warning once
			logger.warn("Market could not be properly cleared in " + Date.getYearDayDate() + "/"
					+ ". Demand " + priceCurvePoints.get(end).getAskVolumeMaximum() + " Supply "
					+ priceCurvePoints.get(end).getSellVolumeMaximum());

			temporaryPrice = temporaryPriceFunction.get(end - 1).getPrice();
			// Last volume
			temporaryVolume = priceCurvePoints.get(end).getAskVolumeMaximum();

			return;
		}

		// Find middle x value, assume that curves are to straight lines with
		// one line made out of (x1,y1)-(x2,y2) and the other (x3,y3)-(x4,y4).
		// Intersection can be calculated via
		// \frac{(x1 y2-y1 x2)(x3-x4)-(x1-x2)(x3 y4-y3
		// x4)}{(x1-x2)(y3-y4)-(y1-y2)(x3-x4)}
		// , but since x1=x3 and x2=x4 equation can be simplified to
		// (x1(y4-y2)+x2y1-x2y3)/(y1-y2-y3+y4)
		final double x1 = start;
		final double x2 = end;
		final double y1 = priceCurvePoints.get(start).getSellVolumeMaximum();
		final double y2 = priceCurvePoints.get(end).getSellVolumeMinimum();
		final double y3 = priceCurvePoints.get(start).getAskVolumeMinimum();
		final double y4 = priceCurvePoints.get(end).getAskVolumeMaximum();
		int middle = (int) ((((x1 * (y4 - y2)) + (x2 * y1)) - (x2 * y3)) / ((y1 - y2 - y3) + y4));

		// Check if value of middle is correct, if only two values are left
		// middle is a
		// negative number
		if (middle == start) {
			middle++;
		} else if (middle == end) {
			middle--;
		} else if ((middle < start) || (middle > end)) {
			middle = (start + end) / 2;
		}

		priceCurvePoints.put(middle, temporaryPriceFunction.get(middle));

		boolean intersectionFound = false;
		for (final int index : priceCurvePoints.keySet()) {
			final PriceCurvePoint priceCurvePoint = priceCurvePoints.get(index);
			intersectionFound = findMarketIntersection(index, priceCurvePoint);
			if (intersectionFound) {
				break;
			}
		}

		// Check interval where intersection lies. First
		// interval if in the start there is more demand than offer and
		// in the middle there is less demand than offer else second interval.
		if (!intersectionFound) {
			final boolean firstIntervall = (priceCurvePoints.get(start)
					.getSellVolumeMaximum() <= priceCurvePoints.get(start).getAskVolumeMinimum())
					&& (priceCurvePoints.get(middle).getSellVolumeMaximum() >= priceCurvePoints
							.get(middle).getAskVolumeMinimum());
			if (firstIntervall) {
				// start, middle have already been checked
				findMarketOutcomes(start + 1, middle - 1);
			} else {
				// middle, end have already been checked
				findMarketOutcomes(middle + 1, end - 1);
			}
		}
	}

	private void initBidList(List<? extends Bid> bids) {
		for (final Bid bid : bids) {
			if (bid.getBidType() == BidType.ASK) {
				askBidPoints.add(bid);
			} else {
				sellBidPoints.add(bid);
			}
		}

		// Sort by lowest price
		final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
				b2.getPrice());
		// Sort by highest volume
		final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
				* Float.compare(b1.getVolume(), b2.getVolume());

		Collections.sort(sellBidPoints, compPrice.thenComparing(compVolume));

		Collections.sort(askBidPoints, compPrice.thenComparing(compVolume));

	}

	/**
	 * Set the volume for each bid and calculates the total market volume. Bids
	 * that come first in order are considered first. If the bid list are
	 * ordered via {@link #sortDailyBids()} .
	 */
	private void setBidVolumes(List<? extends Bid> bids) {
		remainingAskVolume = clearingVolume;
		remainingSellVolume = clearingVolume;
		calculateHourlyBidVolumes(bids);
	}

	/**
	 * Write ask volumes for each price in <code>prices</code> based on
	 * <code>temporaryBids</code>
	 */
	private void writeAskVolumes(Set<Float> prices) {

		float askVolumeCurrent = 0;
		float askVolumeLast = 0;
		final Set<Integer> askSet = new HashSet<>();
		int priceIndex = 0;
		int bidCounter = askBidPoints.size();

		// Copy unique prices to an array in order to reversely iterate through
		// that array
		final Float[] pricesArray = prices.toArray(new Float[prices.size()]);

		for (priceIndex = pricesArray.length - 1; priceIndex >= 0; priceIndex--) {
			final float currentPrice = pricesArray[priceIndex];
			for (final ListIterator<Bid> bidIterator = askBidPoints
					.listIterator(bidCounter); bidIterator.hasPrevious();) {
				final Bid bidPoint = bidIterator.previous();
				bidCounter--;

				// price add volume until ask prices are higher than
				// current market price
				if (bidPoint.getPrice() >= currentPrice) {
					askVolumeCurrent += bidPoint.getVolume();
					askSet.add(bidPoint.getIdentifier());
				}
				// consider bid for next price
				else {
					bidCounter++;
					break;
				}
			}
			// Update ask value for current price
			final PriceCurvePoint temp = temporaryPriceFunction.get(priceIndex);
			temp.setAskPoints(askSet);
			temp.setAskVolumeMinimum(askVolumeLast);
			temp.setAskVolumeMaximum(askVolumeCurrent);
			askVolumeLast = askVolumeCurrent;

		}
	}

	/**
	 * Write sell volumes for each price in <code>prices</code> based on
	 * <code>temporaryBids</code>
	 */
	private void writeSellVolumes(Set<Float> prices) {

		float sellVolumeLast = 0;
		float sellVolumeCurrent = 0;
		int hourlyPriceIndex = 0;
		int bidCounter = 0;
		ListIterator<Bid> bidIterator = sellBidPoints.listIterator();

		// For all prices
		for (final float price : prices) {
			final float currentPrice = price;
			final Set<Integer> sellSet = new HashSet<>();
			while (bidIterator.hasNext()) {
				final Bid bidPoint = bidIterator.next();
				bidCounter++;

				// price add volume until bid prices are smaller than
				// current market
				if (bidPoint.getPrice() <= currentPrice) {
					sellVolumeCurrent += bidPoint.getVolume();
					sellSet.add(bidPoint.getIdentifier());
				} else {
					// consider bid for next price
					bidIterator = sellBidPoints.listIterator(--bidCounter);
					break;
				}
			}
			// Update sell value for current price
			final PriceCurvePoint temp = temporaryPriceFunction.get(hourlyPriceIndex);
			temp.setSellPoints(sellSet);
			temp.setSellVolumeMinimum(Math.abs(sellVolumeLast));
			temp.setSellVolumeMaximum(Math.abs(sellVolumeCurrent));
			sellVolumeLast = sellVolumeCurrent;
			hourlyPriceIndex++;
		}
	}

}