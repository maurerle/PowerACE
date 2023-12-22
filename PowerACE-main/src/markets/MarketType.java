package markets;

/**
 * @author
 *
 */
public enum MarketType {

	CAPACITY_MARKET,
	DAY_AHEAD_MARKET,
	FORWARD_MARKET;

	public static boolean isSpotMarket(MarketType marketType) {
		return (marketType == DAY_AHEAD_MARKET);
	}

}