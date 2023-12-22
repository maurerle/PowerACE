package markets.operator.spot.tools;

import java.util.concurrent.Callable;

import markets.trader.spot.DayAheadTrader;

/**
 * A class to call hourly bids parallel and improve running time.
 * 
 * 
 * 
 */
public class EvaluateDayAheadBids implements Callable<Void> {

	private final DayAheadTrader bidder;

	public EvaluateDayAheadBids(DayAheadTrader bidder) {
		this.bidder = bidder;
	}

	@Override
	public Void call() {
		final String threadName = "Evalate day-ahead bids";
		Thread.currentThread().setName(threadName);

		bidder.evaluateResultsDayAhead();
		return null;
	}

}