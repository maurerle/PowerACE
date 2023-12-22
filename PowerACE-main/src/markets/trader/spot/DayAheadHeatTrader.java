package markets.trader.spot;

import java.util.List;

import markets.bids.power.DayAheadHourlyBid;

/**
 * Interface that defines the communication between the auctioneer of the spot
 * market and the bidders on this market. Agents send their spot market bids
 * when the call for bids is invoked.
 *
 * @since 08.05.2015
 * @author 
 */
public interface DayAheadHeatTrader {

	List<DayAheadHourlyBid> callForBidsDayAheadHeat();

}