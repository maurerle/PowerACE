package markets.trader.reserve;

import java.util.List;

import markets.bids.reserve.BalanceBid;

/**
 * Interface that defines the communication between the auctioneer of the
 * balancing power market and the bidders on this market. Agents send their
 * balance bids when the call for bids is invoked.
 *
 * @since 11.04.2005
 * @author Anke Weidlich
 */

public interface BalancingTrader {

	List<BalanceBid> callForBidsBalance();

	void evaluateResultsBalance();

}