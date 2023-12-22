package markets.operator.spot.tools;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.power.DayAheadHourlyBid;
import markets.trader.spot.DayAheadTrader;

/**
 * A class to call hourly bids parallel and improve running time.
 * 
 * @author 
 * 
 */
public class CallHourlyDayAheadBids implements Callable<List<DayAheadHourlyBid>> {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(CallHourlyDayAheadBids.class.getName());
	private DayAheadTrader bidder = null;

	public CallHourlyDayAheadBids(DayAheadTrader bidder) {
		try {
			this.bidder = bidder;
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	@Override
	public List<DayAheadHourlyBid> call() {
		return bidder.callForBidsDayAheadHourly();
	}

}