package markets.operator.reserve;

import markets.operator.Auction;

/**
 * Interface for auctioneers that implement the auction protocol used at the the
 * balancing power market.
 * 
 * @since 11.04.2005
 * @author Anke Weidlich
 * 
 */
public interface BalancingTrading extends Auction {

	float getAveragePriceCapacity();

	float getAveragePriceCapacity(int blocN);

	float getAveragePriceWork();

	float getAveragePriceWork(int blocN);

	float getCommittedCapacity(int blocN);

	float getTotalCommittedCapacity();

	float getTotalUsedWork();

	float getUsedWork(int blocN);

}