package markets.trader;

import java.util.ArrayList;
import java.util.List;

import markets.bids.power.DayAheadHourlyBid;
import markets.bids.reserve.BalanceBid;
import simulations.agent.Agent;

/**
 * Abstract superclass for all bidders that take part in the auction. Instances
 * can be created, but the bids must be given explicitly.
 *
 * @since 14.10.2004
 * @author Petr Nenutil
 */
public abstract class Trader extends Agent {

	/** Maximum price in intraday auction. */
	private float maximumCarbonPrice;
	/** Maximum price in day-ahead auction. */
	private float maximumDayAheadPrice;
	/** Minimum price in intraday auction. */
	private float minimumCarbonPrice;
	/** Minimum price in day-ahead auction. */
	private float minimumDayAheadPrice;

	/** List with balancing bids */
	protected List<BalanceBid> balBids;
	/** List with hourly day-ahead bids */
	protected List<DayAheadHourlyBid> hourlyDayAheadPowerBids;

	/** Constructor that initializes all bid lists without a name */
	public Trader() {
		this(null);
	}

	/** Constructor that initializes all bid lists */
	public Trader(String name) {
		super(name, null);
		hourlyDayAheadPowerBids = new ArrayList<>();
		balBids = new ArrayList<>();
	}

	public List<DayAheadHourlyBid> getHourlySpotPowerBids() {
		return hourlyDayAheadPowerBids;
	}

	public float getMaximumCarbonPrice() {
		return maximumCarbonPrice;
	}

	public float getMaximumDayAheadPrice() {
		return maximumDayAheadPrice;
	}

	public float getMinimumCarbonPrice() {
		return minimumCarbonPrice;
	}

	public float getMinimumDayAheadPrice() {
		return minimumDayAheadPrice;
	}

	public void setMaximumCarbonPrice(float maximumCarbonPrice) {
		this.maximumCarbonPrice = maximumCarbonPrice;
	}

	public void setMaximumDayAheadPrice(float maximumDayAheadPrice) {
		this.maximumDayAheadPrice = maximumDayAheadPrice;
	}

	public void setMinimumCarbonPrice(float minimumCarbonPrice) {
		this.minimumCarbonPrice = minimumCarbonPrice;
	}

	public void setMinimumDayAheadPrice(float minimumDayAheadPrice) {
		this.minimumDayAheadPrice = minimumDayAheadPrice;
	}

}