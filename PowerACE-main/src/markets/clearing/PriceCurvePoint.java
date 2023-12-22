package markets.clearing;

import java.util.HashSet;
import java.util.Set;

/**
 * A price curve point that is part of price curve function. For a price it
 * contains the corresponding ask and sell volume as well as the relevant bids.
 *
 */
public class PriceCurvePoint {
	/**
	 * Contains the identifier of all ask bid points that have the same price as
	 * the current price.
	 */
	private Set<Integer> askPoints = new HashSet<Integer>();
	private float askVolumeMaximum;
	private float askVolumeMinimum;
	private final float price;
	/**
	 * Contains the identifier of all sell bid points that have the same price
	 * as the current price.
	 */
	private Set<Integer> sellPoints;
	private float sellVolumeMaximum;
	private float sellVolumeMinimum;

	PriceCurvePoint(float price) {
		this.price = price;
	}

	public Set<Integer> getAskPoints() {
		return askPoints;
	}

	public float getAskVolumeMaximum() {
		return askVolumeMaximum;
	}

	public float getAskVolumeMinimum() {
		return askVolumeMinimum;
	}

	public float getPrice() {
		return price;
	}

	public Set<Integer> getSellPoints() {
		return sellPoints;
	}

	public float getSellVolumeMaximum() {
		return sellVolumeMaximum;
	}

	public float getSellVolumeMinimum() {
		return sellVolumeMinimum;
	}

	@Override
	public String toString() {
		return "price " + getPrice() + ", askMax " + getAskVolumeMaximum() + ", askMin "
				+ getAskVolumeMinimum() + ", sellMin " + getSellVolumeMinimum() + ", sellMax "
				+ getSellVolumeMaximum();
	}

	protected void setAskPoints(Set<Integer> askPoints) {
		this.askPoints = askPoints;
	}

	protected void setAskVolumeMaximum(float askVolumeMaximum) {
		this.askVolumeMaximum = askVolumeMaximum;
	}

	protected void setAskVolumeMinimum(float askVolumeMinimum) {
		this.askVolumeMinimum = askVolumeMinimum;
	}

	protected void setSellPoints(Set<Integer> sellPoints) {
		this.sellPoints = sellPoints;
	}

	protected void setSellVolumeMaximum(float sellVolumeMaximum) {
		this.sellVolumeMaximum = sellVolumeMaximum;
	}

	protected void setSellVolumeMinimum(float sellVolumeMinimum) {
		this.sellVolumeMinimum = sellVolumeMinimum;
	}

}