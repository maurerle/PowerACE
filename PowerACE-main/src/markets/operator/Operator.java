package markets.operator;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.trader.Trader;
import simulations.agent.Agent;

/**
 * Abstract super class for any agent that implements one of the defined auction
 * protocols.
 *
 * @since 11.04.2005
 * @author Anke Weidlich
 */
public abstract class Operator extends Agent implements Auction, Callable<Void> {

	public static final int BLOCKS = 0;
	public static final int DEMAND_OVERHANG = -1;
	public static final int INITIAL = 1;
	public static final int INVALID_BID = -3;
	public static final int OFFER_OVERHANG = -2;
	public static final int SUCCESSFUL = 0;

	private static final Logger logger = LoggerFactory.getLogger(Operator.class.getName());

	/**
	 * Used for better output format. Make it tread safe via ThreadLocal since
	 * DecimalFormat is not.
	 */
	private static final ThreadLocal<NumberFormat> numberFormat = new ThreadLocal<NumberFormat>() {
		@Override
		public NumberFormat initialValue() {
			return new DecimalFormat(",##0.00", new DecimalFormatSymbols(new Locale("en")));
		}
	};

	/**
	 * Hour in which the call for bids is sent. For scheduling purposes. Is set
	 * via XML file
	 */
	private int hourCallForBids = 1;

	/**
	 * Hour in which market results are sent to the participants. For scheduling
	 * purposes. Is set via XML file
	 */
	private int hourResults = 1;
	/*** LogID */
	private int logID;
	/** Upper bound of the price scale. Is set via XML file. */
	protected float maximumPriceAllowed;
	/** Lower bound of the price scale. Is set via XML file. */
	protected float minimumPriceAllowed;
	/** Title for output files and graphics */
	private String title;
	/** References to all bidders taking part in the auction. */
	protected final List<Trader> traders;

	/** Constructor of instance */
	public Operator() {
		this(new ArrayList<Trader>());
	}

	/**
	 * Constructor of instance.
	 *
	 * @param traders
	 *            List of all bidders that can bid in the auction.
	 */
	public Operator(List<Trader> traders) {
		super();
		this.traders = traders;
	}

	@Override
	public Void call() {
		try {
			execute();
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * Takes of a single bidder that quits the market off the bidders list [this
	 * function is not yet used]
	 */
	public void deleteBidder(String name) {
		String temp;
		Agent agent;
		final ListIterator<Trader> iterator = traders.listIterator();
		while (iterator.hasNext()) {
			agent = iterator.next();
			temp = agent.getName();
			if (name.equals(temp)) {
				iterator.remove();
				break;
			}
		}
	}

	@Override
	public abstract void evaluate();

	@Override
	public abstract void execute();

	public NumberFormat getDecimalFormat() {
		return numberFormat.get();
	}

	@Override
	public int getHourCallForBids() {
		return hourCallForBids;
	}

	@Override
	public int getHourResults() {
		return hourResults;
	}

	/** Upper bound of the price scale. Is set via XML file. */
	public float getMaxPriceAllowed() {
		return maximumPriceAllowed;
	}

	/** Lower bound of the price scale. Is set via XML file. */
	public float getMinPriceAllowed() {
		return minimumPriceAllowed;
	}

	public String getTitle() {
		return title;
	}

	@Override
	public void initialize() {
		logger.info(marketArea.getInitialsBrackets() + "Initialize Operator " + getName());
		/*
		 * verifies that the hour of call for bids is before the hour of results
		 * and that both are within the range [1,24]
		 */
		checkTiming(hourCallForBids, hourResults);
		title = this.getClass().getSimpleName();
		logInitialize();
	}

	public abstract void logInitialize();

	/** Registers traders */
	@Override
	public void register(Set<Trader> traders) {
		this.traders.addAll(traders);
	}

	public void setHourCallForBids(int hourCallForBids) {
		this.hourCallForBids = hourCallForBids;
	}

	public void setHourResults(int hourResults) {
		this.hourResults = hourResults;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@Override
	public abstract String toString();

	private void checkTiming(int hour1, int hour2) {
		if ((hour1 < 1) || (hour1 > 24)) {
			logger.error(
					"Error in Auctioneer: no valid number for hourCallForBids. Must lie between 0 and 23. Fix the error in the XML file");
		}
		if ((hour2 < 1) || (hour2 > 24)) {
			logger.error(
					"Error in Auctioneer: no valid number for hourResults. Must lie between 0 and 23. Fix the error in the XML file");
		}
		if (hour1 > hour2) {
			logger.error(
					"Error in Auctioneer: hourCallForBids must be lower than hourResults. Fix the error in the XML file");
		}
	}

	protected abstract void clearMarket() throws Exception;

	protected abstract void getBids() throws Exception;

	protected int getLogId() {
		return logID;
	}

	protected void setLogId(int logID) {
		this.logID = logID;
	}

}