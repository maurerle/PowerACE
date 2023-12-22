package markets.trader.spot;

import java.util.List;

import markets.bids.power.DayAheadHourlyBid;

/**
 * Interface that defines the communication between the auctioneer of the spot
 * market and the bidders on this market. Agents send their spot market bids
 * when the call for bids is invoked.
 * 
 * @since 08.11.2004
 * @author Petr Nenutil
 */
public interface DayAheadTrader {

	List<DayAheadHourlyBid> callForBidsDayAheadHourly();

	void evaluateResultsDayAhead();

}