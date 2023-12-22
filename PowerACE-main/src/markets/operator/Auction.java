package markets.operator;

import java.util.Set;

import markets.trader.Trader;

/**
 * Super interface for all auction interfaces.
 * 
 * @since 21.06.2005
 * @author Anke Weidlich
 */

public interface Auction {

	void evaluate();

	void execute();

	int getHourCallForBids();

	int getHourResults();

	void register(Set<Trader> Bidders);

}