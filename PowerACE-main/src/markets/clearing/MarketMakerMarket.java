package markets.clearing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.Bid.BidType;
import markets.bids.Trade;
import markets.bids.power.HourlyBidPower;
import markets.operator.spot.DayAheadMarketOperator;
import markets.trader.TraderType;
import tools.types.FuelType;

/**
 * Carries out a market market that matches bids.
 *
 * @since 07.12.2012
 * @author
 */

public class MarketMakerMarket {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(DayAheadMarketOperator.class.getName());

	public static void main(String[] args) {
		final ArrayList<HourlyBidPower> customBidList = new ArrayList<>();
		customBidList.add(new HourlyBidPower(0, 10, 0, 0, BidType.SELL, TraderType.UNKNOWN, null));
		customBidList.add(new HourlyBidPower(2, 5, 0, 0, BidType.SELL, TraderType.UNKNOWN, null));
		customBidList.add(new HourlyBidPower(1, 15, 0, 0, BidType.ASK, TraderType.UNKNOWN, null));
		final MarketMakerMarket market = new MarketMakerMarket("name", 0, 0, 1);
		market.clearMarket(customBidList);
		logger.info(Double.toString(market.getAverageMarketPrice()));
	}

	/** Array list that contains all ask bids. */
	private final List<HourlyBidPower> askBidPoints = new ArrayList<>();
	/** Weighted price by volume */
	private double averageMarketPrice;

	private final float maxPrice;
	private final float minPrice;
	/** Name of the market */
	private final String name;
	private float renewableVolume = 0f;
	/** total traded volume in the call market */
	private double resultingVolume;
	/** Array list that contains all sell bids. */
	private final TreeSet<HourlyBidPower> sellBidPoints = new TreeSet<>();
	/** List containing all made trades */
	private final List<Trade> trades = new ArrayList<>();

	/** Map bids and trades */
	private final Map<HourlyBidPower, List<Trade>> tradesMap = new HashMap<>();

	/**
	 * @param bidList
	 *            contains all bids and asks for this auction
	 */
	public MarketMakerMarket(String name, float maxPrice, float minPrice, int logId) {
		this.name = name;
		this.maxPrice = maxPrice;
		this.minPrice = minPrice;
	}

	/**
	 * Executes the market. Matches all the bids with a negative spread. Price
	 * is the arithmetic mean while volume is the minimum of the two offers.
	 * Resulting volumes and prices are written into a list that is part of each
	 * bid.
	 *
	 * @return bids The list of bids; the method clear market has added
	 *         information about results to each bid
	 */
	public void clearMarket(List<HourlyBidPower> bidPoints) {
		initialize(bidPoints);
		matchBids();
		calcBidPrices();
		calcStats();
	}

	public double getAverageMarketPrice() {
		return averageMarketPrice;
	}

	public String getName() {
		return name;
	}

	/** Returns the resulting market volume [MWh] */
	public double getResultingVolume() {
		return resultingVolume;
	}

	public List<Trade> getTrades() {
		return trades;
	}

	private void calcBidPrices() {
		for (final HourlyBidPower bidPoint : tradesMap.keySet()) {

			// Get average volume weighted average price
			float price = 0f;
			for (final Trade trade : tradesMap.get(bidPoint)) {
				price += trade.price * trade.volume;
			}

			// Write result into bid
			if (bidPoint.getVolumeAccepted() > 0) {
				price /= bidPoint.getVolumeAccepted();
				bidPoint.setPriceAccepted(price);
			}

		}
	}

	/**
	 * Calculates the average market price weighted by the volume of each trade.
	 */
	private void calcStats() {
		float temp = 0;
		resultingVolume = 0;
		for (final Trade trade : trades) {
			temp += trade.price * trade.volume;
			resultingVolume += trade.volume;
		}
		averageMarketPrice = temp / resultingVolume;
	}

	private void checkPrice(float price) {
		if (price > 1000) {
			logger.error("why price so high");
		}

	}

	/**
	 * Find the next ask offer that is not renewable.
	 *
	 * @param askBidPoint
	 * @return
	 */
	private HourlyBidPower findNextNonRenewableAskOffer(HourlyBidPower sellBidPoint) {
		// Get cheapest offer non-renewable
		HourlyBidPower askBidPoint = null;
		float totalVolume = 0f;
		for (final HourlyBidPower bidPoint : askBidPoints) {
			if (bidPoint.getFuelType() != FuelType.RENEWABLE) {
				totalVolume += bidPoint.getVolume();
				if (totalVolume > renewableVolume) {
					askBidPoint = bidPoint;
					renewableVolume += sellBidPoint.getVolume();
					break;
				}
			}
		}
		return askBidPoint;
	}

	/**
	 * Find the next sell offer that is not renewable and that has not already
	 * been used in the trades.
	 *
	 * @param askBidPoint
	 * @return
	 */
	private HourlyBidPower findNextNonRenewableSellOffer(HourlyBidPower askBidPoint) {
		// Get cheapest offer non-renewable
		final Iterator<HourlyBidPower> iterator = sellBidPoints.iterator();
		HourlyBidPower sellBidPoint = null;
		float totalVolume = 0f;
		while (iterator.hasNext()) {
			final HourlyBidPower bidPoint = iterator.next();
			if (bidPoint.getFuelType() != FuelType.RENEWABLE) {
				totalVolume += bidPoint.getVolume();
				if (totalVolume > renewableVolume) {
					sellBidPoint = bidPoint;
					renewableVolume += askBidPoint.getVolume();
					break;
				}
			}
		}

		return sellBidPoint;
	}

	private Trade findSellOffer(HourlyBidPower askBidPoint) {

		// Check if sell bids are left
		if (sellBidPoints.isEmpty()) {
			return null;
		}

		// Get cheapest offer
		final HourlyBidPower sellBidPoint = sellBidPoints.first();

		// See if match can be made
		if (sellBidPoint.getPrice() >= askBidPoint.getPrice()) {
			return null;
		}

		// Make trade with average price
		// Check for max prices, max price is for grid operator bidder that bids
		// max price but does not pay that
		float price;
		try {
			if (sellBidPoint.getPrice() == minPrice) {
				final HourlyBidPower nextSell = findNextNonRenewableSellOffer(askBidPoint);
				if (nextSell != null) {
					price = (askBidPoint.getPrice() + nextSell.getPrice()) / 2;
					checkPrice(price);
				} else {
					price = askBidPoint.getPrice();
					checkPrice(price);
				}
			} else if (askBidPoint.getPrice() == maxPrice) {
				final HourlyBidPower nextAsk = findNextNonRenewableAskOffer(askBidPoint);
				if (nextAsk != null) {
					price = (sellBidPoint.getPrice() + nextAsk.getPrice()) / 2;
					checkPrice(price);
				} else {
					price = sellBidPoint.getPrice();
					checkPrice(price);
				}

			} else {
				price = (sellBidPoint.getPrice() + askBidPoint.getPrice()) / 2;
				checkPrice(price);
			}
		} catch (final NullPointerException e) {
			price = 0f;
		}

		final float volume = Math.min(sellBidPoint.getVolume() - sellBidPoint.getVolumeAccepted(),
				askBidPoint.getVolume() - askBidPoint.getVolumeAccepted());
		sellBidPoint.setVolumeAccepted(sellBidPoint.getVolumeAccepted() + volume);
		askBidPoint.setVolumeAccepted(askBidPoint.getVolumeAccepted() + volume);

		// Write trades, so that afterwards prices for bids can be calculated
		final Trade trade = new Trade(volume, price, sellBidPoint.getHour());
		tradesMap.get(sellBidPoint).add(trade);
		tradesMap.get(askBidPoint).add(trade);
		trades.add(trade);

		// Remove bid if volume is entirely sold
		if (sellBidPoint.getVolumeAccepted() == sellBidPoint.getVolume()) {
			sellBidPoints.remove(sellBidPoint);
		}

		// Return trade
		return trade;

	}

	private void initialize(List<HourlyBidPower> bidPoints) {
		for (final HourlyBidPower bidPoint : bidPoints) {
			if (bidPoint.getBidType() == BidType.ASK) {
				askBidPoints.add(bidPoint);
			} else {
				sellBidPoints.add(bidPoint);
			}

			tradesMap.put(bidPoint, new ArrayList<Trade>());
		}
		Collections.sort(askBidPoints);

	}

	/** Matches the bids */
	private void matchBids() {

		// Start with highest ask offer
		for (int index = askBidPoints.size() - 1; index >= 0; index--) {
			final HourlyBidPower askBidPoint = askBidPoints.get(index);

			// Find sell offer for ask offer
			final Trade trade = findSellOffer(askBidPoint);

			// No more trades can be made
			if (trade == null) {
				return;
			}

			// Only advance to next bid if entire volume of current bid is
			// matched
			if (askBidPoint.getVolumeAccepted() < askBidPoint.getVolume()) {
				index++;
			}
		}
	}

}