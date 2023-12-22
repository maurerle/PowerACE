package markets.bids;

import markets.trader.Trader;
import markets.trader.TraderType;
import simulations.MarketArea;
import supply.powerplant.Plant;

/**
 * General abstract superclass for all bids.
 *
 */

public abstract class Bid {

	public enum BidType {
		ASK,
		SELL;
	}

	/** Shows if the bid is accepted. */
	private boolean accepted;
	/** Accepted price that bid will receive or have to pay */
	private float priceAccepted;
	/** The accepted volume of the bid. */
	private float volumeAccepted;
	/** Buying or selling bid. */
	protected BidType bidType;
	/** Any comment e.g. avoided startup for 2 hours. */
	protected String comment;
	/** identifier helps to keep reference from the bidder to his bid point. */
	protected int identifier;
	/**
	 * UnitID helps to sort the bids in order to guarantee deterministic
	 * behavior.
	 */
	protected int unitID = 0;
	/** Identifier of power plant */
	protected Plant plant;

	/** Bid price [Euro/MWh] */
	protected float price;
	/** Trader that made the bid */
	protected Trader trader;
	/** The bidder that made the bid. */
	protected TraderType traderType;
	/** Bid volume; only positive */
	protected float volume;
	/** MarketArea in which bid was made */
	protected MarketArea marketArea;

	public BidType getBidType() {
		return bidType;
	}

	public String getComment() {
		return comment;
	}

	public int getIdentifier() {
		return identifier;
	}

	public MarketArea getMarketAreaOfBid() {
		return marketArea;
	}

	public Plant getPlant() {
		return plant;
	}

	public float getPrice() {
		return price;
	}

	public float getPriceAccepted() {
		return priceAccepted;
	}

	public Trader getTrader() {
		return trader;
	}

	public TraderType getTraderType() {
		return traderType;
	}

	public BidType getType() {
		return bidType;
	}

	public int getUnitID() {
		return unitID;
	}

	public float getVolume() {
		return volume;
	}

	public float getVolumeAccepted() {
		return volumeAccepted;
	}

	public float getVolumeRemaining() {
		return volume - volumeAccepted;
	}

	@Override
	public int hashCode() {
		return identifier;
	}

	public boolean isAccepted() {
		return accepted;
	}

	/**
	 * checks if bid is in <code>[pMin, pMax]</code> and <code>volume</code> of
	 * the bid is unequal to zero
	 *
	 * @param pMin
	 *            the <i>minimal</i> accepted price, e.g. 0
	 * @param pMax
	 *            the <i>maximal</i> accepted price, e.g. 3000
	 */
	public abstract boolean isValid(float pMin, float pMax);

	public void setComment(String comment) {
		this.comment = comment;
	}

	public void setIdentfier(int identifier) {
		this.identifier = identifier;
	}

	public void setPrice(float price) {
		this.price = price;
	}

	public void setPriceAccepted(float priceAccepted) {
		this.priceAccepted = priceAccepted;
	}

	public void setUnitID(int unitID) {
		this.unitID = unitID;
	}

	public void setVolumeAccepted(float volumeAccepted) {
		this.volumeAccepted = volumeAccepted;
		if (volumeAccepted > 0) {
			accepted = true;
		} else {
			accepted = false;
		}
	}

}