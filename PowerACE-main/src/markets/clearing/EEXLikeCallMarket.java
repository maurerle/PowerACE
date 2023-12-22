package markets.clearing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.Bid;
import markets.operator.Operator;
import markets.operator.spot.DayAheadMarketOperator;

/**
 * Carries out a call market with bids of type markets.bids.BidPoint and
 * linearly interchanges between prices that are not explicitly given.
 *
 * @since 03.07.2005
 * @author Anke Weidlich
 */

public class EEXLikeCallMarket {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(EEXLikeCallMarket.class.getName());
	/** the MCP of this call market */
	public float resultingPrice;
	/** total traded volume in the call market */
	public float resultingVolume;
	/** all bids submitted by buyers and sellers */
	private List<Bid> bidPoints = new ArrayList<Bid>();
	/** Maximum price */
	private final float maxPrice;
	/** Minimum price */
	private final float minPrice;

	/** Status of MCP calculation */
	private int status = DayAheadMarketOperator.INITIAL;

	/**
	 * @param bidList
	 *            contains all bids and asks for this auction
	 */
	public EEXLikeCallMarket(float pMin, float pMax) {
		minPrice = pMin;
		maxPrice = pMax;
	}

	/**
	 * Executes the call market
	 *
	 * @return bids The list of bids; the method clear market has added
	 *         information about results to each bid
	 */
	public void clearMarket(List<Bid> bidList) {
		double sumVolume = 0, diffVolume = 0, price = 0, lastPrice = minPrice;

		bidPoints = bidList;
		sortBids();
		resultingPrice = -1;
		resultingVolume = 0;
		/*
		 * all diffs for the same price are summed before zero crossing is
		 * tested; (bids are sorted from lowest to highest price and highest to
		 * lowest volume)
		 */
		for (int i = 0; i < bidPoints.size(); i++) {
			final Bid bidPoint = bidPoints.get(i);
			price = bidPoint.getPrice();
			diffVolume = bidPoint.getVolume();
			// test whether zero is crossed
			if ((Math.signum(sumVolume) != Math.signum(sumVolume + diffVolume))
					&& (sumVolume != 0)) {
				resultingPrice = (float) (price
						- (((price - lastPrice) * (sumVolume + diffVolume)) / diffVolume));
				if (resultingPrice > 1000) {
					logger.warn("Should not happen");
				}

				bidPoint.setVolumeAccepted((float) -sumVolume);
				resultingVolume += Math.abs(sumVolume);
				break;
			}
			bidPoint.setVolumeAccepted(bidPoint.getVolume());
			sumVolume += diffVolume;
			lastPrice = price;
			if (diffVolume < 0) {
				resultingVolume += Math.abs(diffVolume);
			}
		}
		// no equilibrium found
		if (resultingPrice == -1) {
			if (sumVolume == 0) { // no bids for this hour
				resultingPrice = 0;
				status = Operator.SUCCESSFUL;
			}
			if (sumVolume > 0) {
				status = Operator.DEMAND_OVERHANG;
			} else {
				status = Operator.OFFER_OVERHANG;
			}
		} else {
			status = Operator.SUCCESSFUL;
		}
	}

	public int getStatus() {
		return status;
	}

	/**
	 * Sorts supply and demand bids according to their bidding price (ascending)
	 * and volume where price is identical
	 *
	 * @return tempBids List of sorted bids
	 */
	private void sortBids() {
		final ListIterator<Bid> iterator = bidPoints.listIterator();
		Bid bidPoint;
		while (iterator.hasNext()) {
			bidPoint = iterator.next();
			if (!bidPoint.isValid(minPrice, maxPrice)) {
				iterator.remove();
				logger.warn("EEXLikeCallMarket Warning: there has been an invalid bid point! "
						+ bidPoint.toString());
			}
		}

		// First: Sort by lowest price
		final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
				b2.getPrice());
		// Second: Sort by highest volume
		final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
				* Float.compare(b1.getVolume(), b2.getVolume());
		Collections.sort(bidPoints, compPrice.thenComparing(compVolume));

	}

}