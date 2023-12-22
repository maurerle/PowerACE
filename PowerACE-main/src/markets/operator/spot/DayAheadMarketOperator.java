package markets.operator.spot;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.Bid;
import markets.bids.Bid.BidType;
import markets.bids.power.BlockBidPower;
import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.clearing.RegularCallMarket;
import markets.operator.Operator;
import markets.operator.spot.tools.CallHourlyDayAheadBids;
import markets.operator.spot.tools.EvaluateDayAheadBids;
import markets.trader.Trader;
import markets.trader.TraderType;
import markets.trader.spot.DayAheadBlockTrader;
import markets.trader.spot.DayAheadTrader;
import markets.trader.spot.hydro.PumpStorageTrader;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.other.Concurrency;

/**
 * Agent that implements the EEX auction mechanism for hourly and bloc bids. For
 * more detailed description see "EEX auction protocol.pdf".
 *
 * @since 11.04.2005
 * @author Anke Weidlich, mods by Massimo
 */

public class DayAheadMarketOperator extends Operator {

	private static final int CONV_DURATION = 100;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(DayAheadMarketOperator.class.getName());
	private static ExecutorService exec;

	public static void shutdown() throws InterruptedException {
		exec.shutdown();
		exec.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
	}

	public long t1;
	public long t2;
	public long t3;
	public long t4;
	public long t5;
	public long t6;
	/** List containing hourly bids from all bidders. */
	private final List<DayAheadHourlyBid> allBids = Collections.synchronizedList(new ArrayList<>());
	/** Hourly weighted average of all MCPs per day. */
	private float averagePrice;
	/** List containing single price volume pairs for every hour */
	private Map<Integer, List<Bid>> bidPoints = new ConcurrentHashMap<>();
	/** List containing all day-ahead block bids from all bidders. */
	private List<BlockBidPower> blockBids = Collections.synchronizedList(new ArrayList<>());
	private boolean converged;
	/** Indicates whether callForBids is already over */
	private boolean dayAheadMarketBidOver;
	/** Indicates whether market clearing is already over */
	private boolean dayAheadMarketCleared;
	private final float[] lastPrices = new float[CONV_DURATION];
	/** Array with clearing prices for every hour. */
	private List<Float> marketClearingPrice = new ArrayList<>(
			Collections.nCopies(HOURS_PER_DAY, Float.NaN));
	/** max price, daily. */
	private float maxPriceOfCurrentDay;
	/** min price, daily. */
	private float minPriceOfCurrentDay;
	/** Random number generator */
	private final Random random = new Random(Settings.getRandomNumberSeed());
	/** List with included startup costs in each hourly price. */
	private List<Float> startupCosts = new ArrayList<>(
			Collections.nCopies(HOURS_PER_DAY, Float.NaN));

	/** Array with status of MCP calculation. */
	private final int[] status = new int[HOURS_PER_DAY];

	/** Summed total traded volume. */
	private float totalVolume;

	/** Total traded volumes for every hour. */
	private List<Float> totalVolumes = new ArrayList<>(
			Collections.nCopies(HOURS_PER_DAY, Float.NaN));

	public DayAheadMarketOperator() {
		if ((exec == null) || exec.isTerminated()) {
			exec = Executors.newFixedThreadPool(Settings.getNumberOfCores());
		}
	}

	private void checkBidPointsAllocation() {
		for (final Integer hourOfDay : bidPoints.keySet()) {
			for (final Bid bid : bidPoints.get(hourOfDay)) {
				final HourlyBidPower hourlyBid = (HourlyBidPower) bid;
				if (!(hourlyBid.getHour() == hourOfDay)) {
					logger.error("Wrong alloction of a power bid to an hour in BidPoints");
				}
			}
		}
	}

	/**
	 * Clears the market and sets volumes and prices.
	 */
	@Override
	public void clearMarket() {

		final RegularCallMarket callMarket = new RegularCallMarket(getTitle(), getMaxPriceAllowed(),
				getMaxPriceAllowed(), marketArea);

		// executes the market
		callMarket.clearBlockHourlyMarket(bidPoints, blockBids);

		// write results into fields
		blockBids = callMarket.getBlockBidsDay();
		bidPoints = callMarket.getBidPointsDay();
		marketClearingPrice = callMarket.getClearingPrices();
		startupCosts = callMarket.getClearingStartupCosts();
		totalVolumes = callMarket.getClearingVolumes();

		// put resulting price info into the hourly bids
		for (final DayAheadHourlyBid bid : allBids) {
			bid.setMarketClearingPrice(marketClearingPrice.get(bid.getHour()));
			bid.setStatus(status[bid.getHour()]);
		}

		// put resulting volume info into the hourly bids
		for (final DayAheadHourlyBid bid : allBids) {
			for (final HourlyBidPower bidPoint : bid.getBidPoints()) {
				bid.setAssignedVolume(bid.getAssignedVolume() + bidPoint.getVolumeAccepted());
			}
		}
	}

	/** Computes auction statistics. */
	private void compStatistics() {
		dayAheadMarketCleared = true;

		// Write results into statistics
		marketArea.getElectricityResultsDayAhead().setDailyPrices(marketClearingPrice);
		marketArea.getElectricityResultsDayAhead().setDailyStartupCosts(startupCosts);
		marketArea.getElectricityResultsDayAhead().setDailyVolumes(totalVolumes);

		final List<Float> renewablesAccepted = new ArrayList<>(
				Collections.nCopies(HOURS_PER_DAY, 0f));
		final List<Float> exchangeAccepted = new ArrayList<>(
				Collections.nCopies(HOURS_PER_DAY, 0f));
		final List<Float> demandAccepted = new ArrayList<>(Collections.nCopies(HOURS_PER_DAY, 0f));
		final List<Float> sheddableAccepted = new ArrayList<>(
				Collections.nCopies(HOURS_PER_DAY, 0f));
		final List<Float> shiftableAcceptedSell = new ArrayList<>(
				Collections.nCopies(HOURS_PER_DAY, 0f));
		final List<Float> shiftableAcceptedAsk = new ArrayList<>(
				Collections.nCopies(HOURS_PER_DAY, 0f));
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			for (final Bid bidPoint : bidPoints.get(hour)) {
				if (bidPoint.getTraderType() == TraderType.DEMAND) {
					demandAccepted.set(hour,
							demandAccepted.get(hour) + bidPoint.getVolumeAccepted());
				} else if ((bidPoint.getTraderType() == TraderType.RENEWABLE)
						|| (bidPoint.getTraderType() == TraderType.GRID_OPERATOR)) {
					renewablesAccepted.set(hour,
							renewablesAccepted.get(hour) + bidPoint.getVolumeAccepted());
				} else if (bidPoint.getTraderType() == TraderType.EXCHANGE) {
					exchangeAccepted.set(hour,
							exchangeAccepted.get(hour) + bidPoint.getVolumeAccepted());
				} else if (bidPoint
						.getTraderType() == TraderType.DEMAND_SIDE_MANAGEMENT_SHIFTABLE) {
					if (bidPoint.getBidType() == BidType.SELL) {
						shiftableAcceptedSell.set(hour,
								shiftableAcceptedSell.get(hour) + bidPoint.getVolumeAccepted());
					} else {
						shiftableAcceptedAsk.set(hour,
								shiftableAcceptedAsk.get(hour) + bidPoint.getVolumeAccepted());
					}
				}
			}
		}
		marketArea.getElectricityResultsDayAhead().setDailyDemandAccepted(demandAccepted);
		marketArea.getElectricityResultsDayAhead().setDailyExchangeAccepted(exchangeAccepted);
		marketArea.getElectricityResultsDayAhead().setDailyRenewablesAccepted(renewablesAccepted);
		marketArea.getElectricityResultsDayAhead()
				.setDailySheddableLoadsAccepted(sheddableAccepted);
		marketArea.getElectricityResultsDayAhead()
				.setDailyShiftableLoadsAcceptedAsk(shiftableAcceptedAsk);
		marketArea.getElectricityResultsDayAhead()
				.setDailyShiftableLoadsAcceptedSell(shiftableAcceptedSell);

		averagePrice = 0;
		totalVolume = 0;
		maxPriceOfCurrentDay = getMinPriceAllowed();
		minPriceOfCurrentDay = getMaxPriceAllowed();
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			totalVolume += totalVolumes.get(hour);
			averagePrice += marketClearingPrice.get(hour);
			if (marketClearingPrice.get(hour) > maxPriceOfCurrentDay) {
				maxPriceOfCurrentDay = marketClearingPrice.get(hour);
			}
			if (marketClearingPrice.get(hour) < minPriceOfCurrentDay) {
				minPriceOfCurrentDay = marketClearingPrice.get(hour);
			}
		}
		averagePrice = averagePrice / HOURS_PER_DAY;

		// write security of supply statistics
		// Consolidate with regular call market
		if (true) {
			final Map<Integer, List<Bid>> bidPoints = marketArea.getDayAheadMarketOperator()
					.getBidPoints();
			final HashMap<TraderType, Map<Integer, Map<BidType, Float>>> volumeRequested = findTypeVolumes(
					new HashSet<>(Arrays.asList(TraderType.values())), bidPoints);
			logSecurityOfSupplyMarketCoupling(bidPoints, volumeRequested);
		}
	}

	/**
	 * For each trader type, find the hourly bids. Useful e.g. when the
	 * requested volume for each trader needs to be determined.
	 *
	 * @param bidPointsDay
	 * @return bidPointsDayTraderType
	 */
	private Map<TraderType, Map<Integer, List<Bid>>> determineTraderType(
			Map<Integer, List<Bid>> bidPointsDay) {

		final Map<TraderType, Map<Integer, List<Bid>>> bidPointsDayTraderType = new HashMap<>();

		for (final TraderType traderType : TraderType.values()) {
			bidPointsDayTraderType.put(traderType, new HashMap<Integer, List<Bid>>());
			for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
				bidPointsDayTraderType.get(traderType).put(hourOfDay, new ArrayList<>());
			}
		}

		for (final Integer hourOfDay : bidPointsDay.keySet()) {
			for (final Bid bid : bidPointsDay.get(hourOfDay)) {
				final TraderType traderType = bid.getTraderType();
				bidPointsDayTraderType.get(traderType).get(hourOfDay).add(bid);
			}
		}

		return bidPointsDayTraderType;
	}

	/** Makes the agents evaluate the results of the market */
	@Override
	public void evaluate() {

		if (Settings.getNumberOfCores() > 1) {
			evaluateConcurrent();
		} else {
			evaluateSuccessively();
		}

	}

	/**
	 * Evaluate bids for each bidder one after the other.
	 */
	private void evaluateConcurrent() {

		// Relevant for price learning, traders want to know real pumped storage
		// production
		Collection<Callable<Void>> tasks = new ArrayList<>();
		// write tasks for each bidder
		for (final Trader trader : traders) {
			final DayAheadTrader dayAheadTrader = (DayAheadTrader) trader;
			final EvaluateDayAheadBids thread = new EvaluateDayAheadBids(dayAheadTrader);
			tasks.add(thread);
		}
		Concurrency.executeConcurrently(tasks);
	}

	/**
	 * Evaluate bids for each bidder one after the other.
	 */
	private void evaluateSuccessively() {

		// Relevant for price learning, traders want to know real pumped storage
		// production
		final ListIterator<Trader> iterator = traders.listIterator();
		while (iterator.hasNext()) {
			final Trader trader = iterator.next();
			if (trader instanceof PumpStorageTrader) {
				((DayAheadTrader) trader).evaluateResultsDayAhead();
			}
		}
	}

	/** Executes the auction. */
	@Override
	public void execute() {
		try {
			logger.info("[" + marketArea.getInitials() + "] Execute Day-ahead market operator");
			final long time1 = System.currentTimeMillis() / 1000;
			initAuction();
			final long time2 = System.currentTimeMillis() / 1000;
			t1 += time2 - time1;
			getBids();
			final long time3 = System.currentTimeMillis() / 1000;
			t2 += time3 - time2;
			processBids();
			final long time4 = System.currentTimeMillis() / 1000;
			t3 += time4 - time3;
			clearMarket();

			final long time5 = System.currentTimeMillis() / 1000;
			t4 += time5 - time4;
			compStatistics();
			final long time6 = System.currentTimeMillis() / 1000;
			t5 += time6 - time5;
			evaluate();
			final long time7 = System.currentTimeMillis() / 1000;
			t6 += time7 - time6;
			logger.info("initAuction " + t1 + ", getBids " + t2 + ", processBids/strategicReserve "
					+ t3 + ", clearMarket " + t4 + ", compStatistics " + t5 + ", evaluate " + t6);

			logger.info("Day-ahead market auction over");
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 *
	 * Find the accepted volume of bids for each trader type. This is needed to
	 * check if bids from certain traders are accepted e.g. renewables and if
	 * they are not, more block bids can be deactivated.
	 *
	 * @param traderTypes
	 *
	 */
	private HashMap<TraderType, Map<Integer, Map<BidType, Float>>> findTypeVolumes(
			Set<TraderType> traderTypes, Map<Integer, List<Bid>> bidPointsDay) {

		final HashMap<TraderType, Map<Integer, Map<BidType, Float>>> volumeRequested = new HashMap<>();

		for (final TraderType traderType : traderTypes) {
			volumeRequested.put(traderType, new HashMap<>());
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				volumeRequested.get(traderType).put(hour, new HashMap<>());
				for (final BidType bidType : BidType.values()) {
					volumeRequested.get(traderType).get(hour).put(bidType, 0f);

				}
			}
		}

		for (final TraderType traderType : traderTypes) {
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				for (final BidType bidType : BidType.values()) {
					volumeRequested.get(traderType).get(hour).put(bidType, 0f);

				}
			}
		}
		final Map<TraderType, Map<Integer, List<Bid>>> bidPointsDayTraderType = determineTraderType(
				bidPointsDay);

		for (final TraderType traderType : traderTypes) {
			for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
				for (final Bid bidPoint : bidPointsDayTraderType.get(traderType).get(hourOfDay)) {

					final float volumeRequestedBidPoint = bidPoint.getVolume();

					final BidType bidType = bidPoint.getBidType();

					volumeRequested.get(traderType).get(hourOfDay).put(bidType,
							volumeRequested.get(traderType).get(hourOfDay).get(bidType)
									+ volumeRequestedBidPoint);
				}
			}

		}
		return volumeRequested;

	}

	public double getAveragePrice() {
		return averagePrice;
	}

	public Map<Integer, List<Bid>> getBidPoints() {
		return bidPoints;
	}

	/** Get bids from all bidders. */
	@Override
	protected void getBids() throws Exception {
		if (Settings.getNumberOfCores() > 1) {
			getBidsConcurrent();
		} else {
			getBidsSuccessively();
		}

		Collections.shuffle(allBids, random);
		dayAheadMarketBidOver = true;
	}

	/**
	 * Get bids from all bidders, in order to fasten calculation threads are
	 * carried out.
	 */
	private void getBidsConcurrent() {

		try {
			final Collection<CallHourlyDayAheadBids> tasks = new ArrayList<>();
			// write tasks for hourly bids
			for (final Trader trader : traders) {
				if (trader instanceof DayAheadTrader) {
					final DayAheadTrader dayAheadTrader = (DayAheadTrader) trader;
					final CallHourlyDayAheadBids thread = new CallHourlyDayAheadBids(
							dayAheadTrader);
					tasks.add(thread);
				}
			}

			List<Future<List<DayAheadHourlyBid>>> results;

			results = exec.invokeAll(tasks);
			// put results from each task into list
			for (final Future<List<DayAheadHourlyBid>> result : results) {
				if (result.get() != null) {
					allBids.addAll(result.get());
				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}

		for (final Trader trader : traders) {
			if (trader instanceof DayAheadBlockTrader) {
				final DayAheadBlockTrader dayAheadBlockBidder = (DayAheadBlockTrader) trader;
				final List<BlockBidPower> blockBids = dayAheadBlockBidder
						.callForBidsDayAheadBlockBids();
				if (blockBids != null) {
					this.blockBids.addAll(blockBids);
				}
			}
		}
	}

	/**
	 * Get bids from all bidders one after the other, without threads. Helpful
	 * for profiling.
	 */
	private void getBidsSuccessively() {

		for (final Trader trader : traders) {
			// get hourly bids
			if (trader instanceof DayAheadTrader) {
				final DayAheadTrader dayAheadTrader = (DayAheadTrader) trader;
				final List<DayAheadHourlyBid> hourlyBids = dayAheadTrader
						.callForBidsDayAheadHourly();
				if (hourlyBids != null) {
					allBids.addAll(hourlyBids);
				}
			}

			// get block bids
			if (trader instanceof DayAheadBlockTrader) {
				final DayAheadBlockTrader dayAheadBlockBidder = (DayAheadBlockTrader) trader;
				final List<BlockBidPower> blockBids = dayAheadBlockBidder
						.callForBidsDayAheadBlockBids();
				if (blockBids != null) {
					this.blockBids.addAll(blockBids);
				}
			}
		}
	}

	public List<Float> getMarketClearingPrice() {
		return marketClearingPrice;
	}

	public float getMaxDailyPrice() {
		return maxPriceOfCurrentDay;
	}

	public float[] getMCPs() {
		final float[] prices = new float[marketClearingPrice.size()];
		for (int index = 0; index < marketClearingPrice.size(); index++) {
			final Float price = marketClearingPrice.get(index);
			prices[index] = price == null ? Float.NaN : price;
		}
		return prices;
	}

	public float getMinDailyPrice() {
		return minPriceOfCurrentDay;
	}

	public int[] getStatus() {
		final int[] statusCopy = new int[status.length];
		System.arraycopy(status, 0, statusCopy, 0, status.length);
		return statusCopy;
	}

	public String getTotalResults() {
		double convPrice = 0, convDev = 0;
		for (int i = 0; i < CONV_DURATION; i++) {
			convPrice += lastPrices[i];
		}
		convPrice /= CONV_DURATION;
		for (int i = 0; i < CONV_DURATION; i++) {
			convDev += Math.pow(lastPrices[i] - convPrice, 2);
		}
		convDev = Math.sqrt(convDev / CONV_DURATION);
		if (converged) {
			final String temp = ",av price," + convPrice + ",SD," + convDev + ",last-first price,"
					+ (lastPrices[CONV_DURATION - 1] - lastPrices[0]);
			return temp;
		} else {
			return "no convergence";
		}
	}

	public double getTotalTradedVolume() {
		return totalVolume;
	}

	public float[] getTradedVolumes() {
		final float[] volumes = new float[totalVolumes.size()];
		for (int index = 0; index < totalVolumes.size(); index++) {
			final Float volume = totalVolumes.get(index);
			volumes[index] = volume == null ? Float.NaN : volume;
		}
		return volumes;
	}

	/** Restores default states of instance variables. */
	private void initAuction() throws Exception {
		dayAheadMarketBidOver = false;
		dayAheadMarketCleared = false;

		// in the first auction round...
		if (Date.getDayOfTotal() == 1) {
			setUpMarket();
			converged = false;
		}

		Collections.fill(marketClearingPrice, 0f);
		Collections.fill(totalVolumes, 0f);

		for (int i = 0; i < HOURS_PER_DAY; i++) {
			bidPoints.get(i).clear();
			status[i] = INITIAL;
		}
		allBids.clear();
		blockBids.clear();
	}

	public boolean isDayAheadMarketBidOver() {
		return dayAheadMarketBidOver;
	}

	public boolean isDayAheadMarketCleared() {
		return dayAheadMarketCleared;
	}

	@Override
	public void logInitialize() {
	}

	public void logSecurityOfSupplyMarketCoupling(Map<Integer, List<Bid>> bidPointsDay,
			HashMap<TraderType, Map<Integer, Map<BidType, Float>>> volumeRequested) {

		for (final Integer hour : bidPointsDay.keySet()) {

			final float exchangeAsk = volumeRequested.get(TraderType.EXCHANGE).get(hour)
					.get(BidType.ASK);
			final float exchangeSell = volumeRequested.get(TraderType.EXCHANGE).get(hour)
					.get(BidType.SELL);
			final List<Bid> bidHourly = bidPointsDay.get(hour);

			final Float exchangeVolumeAskHour = exchangeAsk;
			final float exchangeVolumeSellHour = exchangeSell;
			float sellVolumeMaximum = 0;
			float askVolumeMaximum = 0;
			for (int bidPointIndex = 0; bidPointIndex < bidHourly.size(); bidPointIndex++) {
				if (bidPointsDay.get(hour).get(bidPointIndex).getBidType() == BidType.SELL) {
					sellVolumeMaximum += bidHourly.get(bidPointIndex).getVolume();
				} else {
					askVolumeMaximum += bidHourly.get(bidPointIndex).getVolume();
				}
			}

			final float askWithoutExchange = askVolumeMaximum - exchangeVolumeAskHour;
			final float askWithExchange = askVolumeMaximum;

			final float sellWithExchange = sellVolumeMaximum;
			final float sellWithoutExchange = sellVolumeMaximum - exchangeVolumeSellHour;

			final float surPlusWithExchange = sellWithExchange - askWithExchange;
			final float surPlusWithoutExchange = sellWithoutExchange - askWithoutExchange;

			final float levelWithExchange = (surPlusWithExchange / askWithExchange) + 1;
			final float levelWithoutExchange = (surPlusWithoutExchange / askWithoutExchange) + 1;

			marketArea.getSecurityOfSupply().addVolume(Date.getFirstHourOfToday() + hour,
					surPlusWithExchange, surPlusWithoutExchange, levelWithExchange,
					levelWithoutExchange

			);
		}
	}

	public Callable<Void> postMarketClearingOperations() {
		return () -> {
			try {
				final String threadName = "PostMarketClearingOperations";
				Thread.currentThread().setName(threadName);

				processResults();
				compStatistics();
				evaluate();
			} catch (final Exception e) {
				logger.error(e.getMessage(), e);
			}
			return null;
		};
	}

	public Callable<Void> preMarketClearingOperations() {
		return () -> {
			try {
				final String threadName = "PreMarketClearingOperations";
				Thread.currentThread().setName(threadName);
				initAuction();
				getBids();
				processBids();
				checkBidPointsAllocation();
			} catch (final Exception e) {
				logger.error(e.getMessage(), e);
			}
			return null;
		};
	}

	/**
	 * Go through all hourly and block bids bids. Put hourly points into the
	 * same hour. Check validity of block and hourly bids.
	 */
	private void processBids() {

		// hourly bids
		int numberOfBids = allBids.size();
		for (int i = 0; i < numberOfBids; i++) {
			final DayAheadHourlyBid hBid = allBids.get(i);
			if (hBid == null) {
				logger.warn("There has been a null bid.");
			} else if (hBid.isValid(getMinPriceAllowed(), getMaxPriceAllowed())) {
				hBid.setAssignedVolume(0);
				hBid.setBidID(i);
				bidPoints.get(hBid.getHour()).addAll(hBid.getBidPoints());
			} else {
				// remove invalid hourly bids
				allBids.remove(i);
				hBid.setStatus(INVALID_BID);
				i--;
				numberOfBids--;
				// Seasonal Storage bidder bids sometimes 0, no warning is
				// needed
				if ((hBid.getBidder() != TraderType.SEASONAL_STORAGE)
						&& (hBid.getBidder() != TraderType.POWER_TO_HYDROGEN)) {
					logger.warn("There is an invalid " + DayAheadHourlyBid.class.getName() + ": "
							+ hBid.getBidder() + ", " + hBid.toString());
				}
			}
		}

		// block bids
		final ListIterator<BlockBidPower> blockBidIterator = blockBids.listIterator();
		BlockBidPower blockBid;
		while (blockBidIterator.hasNext()) {
			blockBid = blockBidIterator.next();
			if (!blockBid.isValid(getMinPriceAllowed(), getMaxPriceAllowed())) {
				blockBidIterator.remove();
				logger.warn("There has been an invalid block bid! " + blockBid.toString());
			}
		}
	}

	private void processResults() {
		// put resulting volume info into the hourly bids
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			for (final Bid bidPoint : bidPoints.get(hour)) {
				allBids.get(bidPoint.getIdentifier())
						.setAssignedVolume(allBids.get(bidPoint.getIdentifier()).getAssignedVolume()
								+ bidPoint.getVolumeAccepted());
			}
		}

		// put resulting price info into the hourly bids
		for (final DayAheadHourlyBid bid : allBids) {
			bid.setMarketClearingPrice(marketClearingPrice.get(bid.getHour()));
			bid.setStatus(status[bid.getHour()]);
		}
	}

	public void setMarketClearingPrice(List<Float> marketClearingPrice) {
		this.marketClearingPrice = marketClearingPrice;
	}

	public void setMarketClearingVolumes(List<Float> totalVolumes) {
		this.totalVolumes = totalVolumes;
	}

	/**
	 * Prepares auction before the first auction round (creates array lists for
	 * aggrBalBids
	 */
	private void setUpMarket() {
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			bidPoints.put(i, new ArrayList<Bid>());
		}
	}

	@Override
	public String toString() {
		final StringBuffer stringBuffer = new StringBuffer(
				",average,,hour 1,,hour 2,,hour 3,,hour 4,,hour 5,,hour 6,,hour 7,,hour 8,,hour 9,,hour 10,,hour 11,,hour 12,,hour 13,,hour 14,,hour 15,,hour 16,,hour 17,,hour 18,,hour 19,,hour 20,,hour 21,,hour 22,,hour 23,,hour 24\n");
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			stringBuffer.append("prices [EUR/MWh],volumes [MW],");
		}
		return stringBuffer.toString();
	}
}