package markets.bids.reserve;

import java.text.DecimalFormat;

import markets.bids.Bid;

/**
 * Class representing a bid at the balancing power market.
 * 
 * @since 11.04.2005
 * @author Anke Weidlich
 */
public class BalanceBid extends Bid implements Comparable<BalanceBid> {
	/** Number of bloc that the bid is valid for. */
	public int blocN;
	/** This variable helps to keep reference from one power plant to one bid */
	public int plantReference;
	/** Offered capacity price for the block in EUR/MWh */
	public float priceLimitCapacity;
	/** Offered electricity price for the block in EUR/MWh */
	public float priceLimitWork;
	/** Resulting sold capacity in MW */
	public float resCap;
	/** Resulting market clearing price for balancing power capacity in EUR */
	public float resPriceCapacity;
	/** Resulting market clearing for balancing power output price in EUR */
	public float resPriceWork;
	/** Resulting sold work in MWh */
	public float resWork;

	private final DecimalFormat decimalFormat = new DecimalFormat("0.00");

	/**
	 * Constructs a new empty block bid
	 * 
	 * @param biddingBlocNumber
	 *            Number of the bidding bloc that this bid is valid for
	 */
	public BalanceBid(int biddingBlocNumber) {
		blocN = biddingBlocNumber;
	}

	/**
	 * Constructs a new block bid
	 * 
	 * @param biddingBlocNumber
	 *            Number of the bidding bloc that this bid is valid for
	 * @param biddingVolume
	 *            Bidding volume in MW
	 * @param biddingPriceWork
	 *            Bidding price for work in EUR/MWh
	 * @param biddingPriceCapacity
	 *            Bidding price for capacity in EUR/MW
	 */
	public BalanceBid(int biddingBlocNumber, float biddingVolume, float biddingPriceWork,
			float biddingPriceCapacity) {
		blocN = biddingBlocNumber;
		volume = biddingVolume;
		priceLimitWork = biddingPriceWork;
		priceLimitCapacity = biddingPriceCapacity;
	}

	/**
	 * Constructs a new block bid
	 * 
	 * @param biddingBlocNumber
	 *            Number of the bidding bloc that this bid is valid for
	 * @param plant
	 *            Reference to the plant that this bid is allocated to
	 * @param biddingVolume
	 *            Bidding volume in MW
	 * @param biddingPriceWork
	 *            Bidding price for work in EUR/MWh
	 * @param biddingPriceCapacity
	 *            Bidding price for capacity in EUR/MW
	 */
	public BalanceBid(int biddingBlocNumber, int plant, float biddingVolume, float biddingPriceWork,
			float biddingPriceCapacity) {
		blocN = biddingBlocNumber;
		plantReference = plant;
		volume = biddingVolume;
		priceLimitWork = biddingPriceWork;
		priceLimitCapacity = biddingPriceCapacity;
	}

	/**
	 * Compares this bid to another bid; comparison criterion: work price limit
	 * Returns -1 if this bid is lower ranked than the other bid 0 if this bid
	 * is equally ranked than the other bid 1 if this bid is higher ranked than
	 * the other bid
	 * 
	 * @param otherBid
	 *            The bid to compare to this bid
	 */
	@Override
	public int compareTo(BalanceBid otherBid) {
		if ((priceLimitWork == otherBid.priceLimitWork) && (volume == otherBid.volume)) {
			return 0; // equal
		}
		if ((priceLimitWork < otherBid.priceLimitWork)
				|| ((priceLimitWork == otherBid.priceLimitWork)
						&& (Math.abs(volume) > Math.abs(otherBid.volume)))) {
			return 1; // higher ranked
		} else {
			return -1; // lower ranked
		}
	}

	/** Returns sold capacity in MW */
	public float getResCap() {
		return resCap;
	}

	/** Returns MCP in EUR */
	public float getResPriceCapacity() {
		return resPriceCapacity;
	}

	/** Returns MCP in EUR */
	public float getResPriceWork() {
		return resPriceWork;
	}

	/** Returns sold work in MWh */
	public float getResWork() {
		return resWork;
	}

	@Override
	public boolean isValid(float pMinWork, float pMaxWork) {
		return (priceLimitWork >= pMinWork) && (priceLimitWork <= pMaxWork) && (volume != 0);
	}

	public boolean isValid(float pMinWork, float pMaxWork, float pMinCapacity, float pMaxCapacity) {
		return (priceLimitWork >= pMinWork) && (priceLimitWork <= pMaxWork)
				&& (priceLimitCapacity >= pMinCapacity) && (priceLimitCapacity <= pMaxCapacity)
				&& (volume != 0);
	}

	/**
	 * Sets the capacity bid price of an existing BalanceBid
	 * 
	 * @param price
	 *            The bid price in EUR/MW
	 */
	public void setBidPriceCapacity(float price) {
		priceLimitCapacity = price;
	}

	/**
	 * Sets the bid price of an existing BalanceBid
	 * 
	 * @param price
	 *            The bid price in EUR/MW
	 */
	public void setBidPriceWork(float price) {
		priceLimitWork = price;
	}

	/**
	 * Sets the bid price of an existing BalanceBid
	 * 
	 * @param price
	 *            The bid price in EUR/MW
	 */
	public void setBidVolume(float vol) {
		volume = vol;
	}

	/**
	 * Constructs a new hourly price independent bid for specified hour h and
	 * volume vol
	 */
	@Override
	public String toString() {
		return (blocN + 1) + "," + decimalFormat.format(priceLimitCapacity) + ","
				+ decimalFormat.format(priceLimitWork) + "," + decimalFormat.format(volume) + ","
				+ decimalFormat.format(resPriceCapacity) + "," + decimalFormat.format(resCap) + ","
				+ decimalFormat.format(resPriceWork) + "," + decimalFormat.format(resWork);
	}
}