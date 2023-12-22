package markets.clearing;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import markets.bids.Bid;
import markets.bids.Bid.BidType;
import markets.bids.power.BlockBidPower;
import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.bids.power.PowerBid;
import markets.operator.Operator;
import markets.operator.spot.DayAheadMarketOperator;
import markets.trader.TraderType;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.other.AccessFields;

/**
 * Carries out a call market with bids of type markets.bids.BidPoint and forms a
 * stepwise constant function between prices that are not explicitly given.
 *
 * @since 29.12.2005
 * @author Anke Weidlich
 */
public class RegularCallMarket {

	// Class that is used to compare the BlockBid profitability
	class BlockProfit implements Comparable<BlockProfit> {

		int blockBidIndex;
		float profit;
		float volume;

		BlockProfit(float profit, float volume, int blockBidIndex) {
			this.profit = profit;
			this.volume = volume;
			this.blockBidIndex = blockBidIndex;
		}

		// First consider profit, than volume
		// higher profit is better and higher volume is better
		@Override
		public int compareTo(BlockProfit other) {

			// profit
			if (profit < other.profit) {
				return -1;
			}
			if (profit > other.profit) {
				return 1;
			}

			// volume
			if (volume < other.volume) {
				return -1;
			}
			if (volume > other.volume) {
				return 1;
			}

			return 0;
		}
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(RegularCallMarket.class.getName());

	public static void main(String[] args) {

		AccessFields.setStaticFieldviaReflection(Settings.class, "logPathName",
				"C:/Users/andreas/Documents/simulations/Multiruns");

		final MarketArea marketArea = new MarketArea();
		marketArea.setName("GERMANY");
		final RegularCallMarket market = new RegularCallMarket("test", -3000f, 3000f, marketArea);

		//
		boolean unambiguousPrice1 = false;
		boolean unambiguousPrice2 = false;
		boolean ambiguousVolume = false;
		boolean ambiguousPrice = false;

		final Map<Integer, List<Bid>> customBidList = new HashMap<>();
		List<BlockBidPower> customBlockBidList = new ArrayList<>(200);

		// First check: all ask bids receive their total volume
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			customBidList.put(i, new ArrayList<>());
			customBidList.get(i).add(
					new HourlyBidPower(1, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(2, 10, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(3, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(4, 8, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
		}
		customBidList.get(1)
				.add(new HourlyBidPower(6, 10, 1, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));

		market.clearBlockHourlyMarket(customBidList, customBlockBidList);
		if ((market.clearingPrices.get(0) == 2) && (market.clearingVolumes.get(0) == 10)) {
			unambiguousPrice1 = true;
		}

		// First check: all ask bids receive their total volume
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			customBidList.put(i, new ArrayList<>());
			customBidList.get(i).add(
					new HourlyBidPower(1, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(2, 10, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(3, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(4, 8, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
		}
		customBlockBidList
				.add(new BlockBidPower.Builder(8, 2, 0, 2, BidType.SELL, marketArea).build());
		customBlockBidList
				.add(new BlockBidPower.Builder(2, 5, 0, 2, BidType.SELL, marketArea).build());

		market.clearBlockHourlyMarket(customBidList, customBlockBidList);
		if ((market.clearingPrices.get(0) == 2) && (market.clearingVolumes.get(0) == 18)
				&& unambiguousPrice1) {
			unambiguousPrice1 = true;
		} else {
			unambiguousPrice1 = false;
		}

		// Second check: all supply bids receive their total volume
		customBlockBidList = new ArrayList<>(200);
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			customBidList.put(i, new ArrayList<>());
			customBidList.get(i).add(
					new HourlyBidPower(1, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(2, 10, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(3, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(4, 15, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
		}
		market.clearBlockHourlyMarket(customBidList, customBlockBidList);
		if ((market.clearingPrices.get(0) == 3) && (market.clearingVolumes.get(0) == 15)) {
			unambiguousPrice2 = true;
		}

		// Third check: ambiguous Price
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			customBidList.put(i, new ArrayList<>());
			customBidList.get(i).add(
					new HourlyBidPower(1, 10, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(2, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(3, 10, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(4, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
		}
		market.clearBlockHourlyMarket(customBidList, customBlockBidList);
		if ((Math.abs(market.clearingPrices.get(0) - 2.5) < 0.0001)
				&& (market.clearingVolumes.get(0) == 10)) {
			ambiguousPrice = true;
		}

		// Fourth check: ambiguous Volume
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			customBidList.put(i, new ArrayList<>());
			customBidList.get(i).add(
					new HourlyBidPower(1, 5, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(2, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(2, 10, i, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
			customBidList.get(i).add(
					new HourlyBidPower(4, 10, i, 0, BidType.SELL, TraderType.UNKNOWN, marketArea));
		}
		market.clearBlockHourlyMarket(customBidList, customBlockBidList);
		if ((Math.abs(market.clearingPrices.get(0) - 2) < 0.0001)
				&& (market.clearingVolumes.get(0) == 10)) {
			ambiguousVolume = true;
		}

		market.clearBlockHourlyMarket(customBidList, customBlockBidList);

		logger.info("All test passed: "
				+ (unambiguousPrice1 & unambiguousPrice2 & ambiguousVolume & ambiguousPrice));

		final List<BlockBidPower> blockBidsNew = new ArrayList<>();
		BidTest.createBlockBids(blockBidsNew);

		final DayAheadHourlyBid hourlyBid0 = new DayAheadHourlyBid(0, null);
		BidTest.createBids0(hourlyBid0);
		final DayAheadHourlyBid hourlyBid1 = new DayAheadHourlyBid(1, null);
		BidTest.createBids1(hourlyBid1);
		final DayAheadHourlyBid hourlyBid2 = new DayAheadHourlyBid(2, null);
		BidTest.createBids2(hourlyBid2);
		final DayAheadHourlyBid hourlyBid3 = new DayAheadHourlyBid(3, null);
		BidTest.createBids3(hourlyBid3);
		final DayAheadHourlyBid hourlyBid4 = new DayAheadHourlyBid(4, null);
		BidTest.createBids4(hourlyBid4);
		final DayAheadHourlyBid hourlyBid5 = new DayAheadHourlyBid(5, null);
		BidTest.createBids5(hourlyBid5);
		final DayAheadHourlyBid hourlyBid6 = new DayAheadHourlyBid(6, null);
		BidTest.createBids6(hourlyBid6);
		final DayAheadHourlyBid hourlyBid7 = new DayAheadHourlyBid(7, null);
		BidTest.createBids7(hourlyBid7);
		final DayAheadHourlyBid hourlyBid8 = new DayAheadHourlyBid(8, null);
		BidTest.createBids8(hourlyBid8);
		final DayAheadHourlyBid hourlyBid9 = new DayAheadHourlyBid(9, null);
		BidTest.createBids9(hourlyBid9);
		final DayAheadHourlyBid hourlyBid10 = new DayAheadHourlyBid(10, null);
		BidTest.createBids10(hourlyBid10);
		final DayAheadHourlyBid hourlyBid11 = new DayAheadHourlyBid(11, null);
		BidTest.createBids11(hourlyBid11);
		final DayAheadHourlyBid hourlyBid12 = new DayAheadHourlyBid(12, null);
		BidTest.createBids12(hourlyBid12);
		final DayAheadHourlyBid hourlyBid13 = new DayAheadHourlyBid(13, null);
		BidTest.createBids13(hourlyBid13);
		final DayAheadHourlyBid hourlyBid14 = new DayAheadHourlyBid(14, null);
		BidTest.createBids14(hourlyBid14);
		final DayAheadHourlyBid hourlyBid15 = new DayAheadHourlyBid(15, null);
		BidTest.createBids15(hourlyBid15);
		final DayAheadHourlyBid hourlyBid16 = new DayAheadHourlyBid(16, null);
		BidTest.createBids16(hourlyBid16);
		final DayAheadHourlyBid hourlyBid17 = new DayAheadHourlyBid(17, null);
		BidTest.createBids17(hourlyBid17);
		final DayAheadHourlyBid hourlyBid18 = new DayAheadHourlyBid(18, null);
		BidTest.createBids18(hourlyBid18);
		final DayAheadHourlyBid hourlyBid19 = new DayAheadHourlyBid(19, null);
		BidTest.createBids19(hourlyBid19);
		final DayAheadHourlyBid hourlyBid20 = new DayAheadHourlyBid(20, null);
		BidTest.createBids20(hourlyBid20);
		final DayAheadHourlyBid hourlyBid21 = new DayAheadHourlyBid(21, null);
		BidTest.createBids21(hourlyBid21);
		final DayAheadHourlyBid hourlyBid22 = new DayAheadHourlyBid(22, null);
		BidTest.createBids22(hourlyBid22);
		final DayAheadHourlyBid hourlyBid23 = new DayAheadHourlyBid(23, null);
		BidTest.createBids23(hourlyBid23);

		final Map<Integer, List<Bid>> customBidListNew = new HashMap<>();
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			customBidListNew.put(hourOfDay, new ArrayList<>());
		}

		customBidListNew.get(0).addAll(hourlyBid0.getBidPoints());
		customBidListNew.get(1).addAll(hourlyBid1.getBidPoints());
		customBidListNew.get(2).addAll(hourlyBid2.getBidPoints());
		customBidListNew.get(3).addAll(hourlyBid3.getBidPoints());
		customBidListNew.get(4).addAll(hourlyBid4.getBidPoints());
		customBidListNew.get(5).addAll(hourlyBid5.getBidPoints());
		customBidListNew.get(6).addAll(hourlyBid6.getBidPoints());
		customBidListNew.get(7).addAll(hourlyBid7.getBidPoints());
		customBidListNew.get(8).addAll(hourlyBid8.getBidPoints());
		customBidListNew.get(9).addAll(hourlyBid9.getBidPoints());
		customBidListNew.get(10).addAll(hourlyBid10.getBidPoints());
		customBidListNew.get(11).addAll(hourlyBid11.getBidPoints());
		customBidListNew.get(12).addAll(hourlyBid12.getBidPoints());
		customBidListNew.get(13).addAll(hourlyBid13.getBidPoints());
		customBidListNew.get(14).addAll(hourlyBid14.getBidPoints());
		customBidListNew.get(15).addAll(hourlyBid15.getBidPoints());
		customBidListNew.get(16).addAll(hourlyBid16.getBidPoints());
		customBidListNew.get(17).addAll(hourlyBid17.getBidPoints());
		customBidListNew.get(18).addAll(hourlyBid18.getBidPoints());
		customBidListNew.get(19).addAll(hourlyBid19.getBidPoints());
		customBidListNew.get(20).addAll(hourlyBid20.getBidPoints());
		customBidListNew.get(21).addAll(hourlyBid21.getBidPoints());
		customBidListNew.get(22).addAll(hourlyBid22.getBidPoints());
		customBidListNew.get(23).addAll(hourlyBid23.getBidPoints());

		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			for (final Bid bid : customBidListNew.get(hourOfDay)) {
				final HourlyBidPower hbp = (HourlyBidPower) bid;
				AccessFields.setFieldViaReflection(hbp.getClass(), hbp, hourOfDay, "hour");
			}
		}

		market.clearBlockHourlyMarket(customBidListNew, blockBidsNew);

	}

	/** All bids submitted */
	private final Map<Integer, Map<Integer, Bid>> allBidsMap = new HashMap<>();
	/** All bids submitted by buyers and sellers for the whole day */
	private Map<Integer, List<Bid>> bidPointsDay;
	private Map<TraderType, Map<Integer, List<Bid>>> bidPointsDayTraderType;
	private Map<Integer, Map<BidType, List<Bid>>> bidPointsDayType = new HashMap<>();
	private final Map<Integer, PowerBid> bidsLastAcceptedSupply = new HashMap<>();
	/** All block bids submitted by buyers and sellers for the whole day */
	private List<BlockBidPower> blockBidsDay = new ArrayList<>();
	/** the MCP of this call market */
	private float clearingPrice;
	/** the MCP of this call market */
	private List<Float> clearingPrices = new ArrayList<>(HOURS_PER_DAY);

	/** the startup costs of hourly prices */
	private final List<Float> clearingStartupCosts = new ArrayList<>(HOURS_PER_DAY);
	/** total traded volume in the call market */
	private float clearingVolume;
	/** the volumes of this call market */
	private List<Float> clearingVolumes = new ArrayList<>(HOURS_PER_DAY);
	/**
	 * If true block bids are only accepted when exogenous factors (renewable,
	 * exchange, pumped-storage) are accepted as well .
	 */
	private final boolean exogenousAcceptAll = true;
	/**
	 * True, if
	 */
	private Map<Integer, Boolean> exogenousAccepted;
	private Map<Integer, Float> exogenousUnaccepted;
	private final MarketArea marketArea;
	/** Upper bound of the price scale in EUR/MWh. */
	private final float maximumPrice;
	/** Lower bound of the price scale in EUR/MWh. */
	private final float minimumPrice;
	/** Name of the market */
	private final String name;
	/** Temporary outcomes */
	private final Map<Integer, PriceCurvePoint> outcomes = new HashMap<>();
	/** The supply volume of the peaker unit. */
	/** Status of MCP calculation */
	private int status = DayAheadMarketOperator.INITIAL;
	/** All temporary accepted volumes for all bids submitted */
	private Map<Integer, Float> temporaryAcceptedVolumes;
	/**
	 * List of all hourly bids submitted and block bids transformed into hourly
	 * bids.
	 */
	private List<List<Bid>> temporaryBids;
	/** All price volume combination for temporaryBids */
	private final List<List<PriceCurvePoint>> temporaryPriceFunction = new ArrayList<>(
			HOURS_PER_DAY);
	/** the MCP of this call market */
	private final List<Float> temporaryPrices = new ArrayList<>(
			Collections.nCopies(HOURS_PER_DAY, Float.NaN));
	/** the MCP of this call market */
	private final List<Float> temporaryVolumes = new ArrayList<>(
			Collections.nCopies(HOURS_PER_DAY, Float.NaN));
	private Set<TraderType> traderTypesAll = new HashSet<>(Arrays.asList(TraderType.values()));
	/** Find types to check only for some exogenous factors */
	private final Set<TraderType> traderTypesExogenous = Stream
			.of(TraderType.EXCHANGE, TraderType.PUMPED_STORAGE, TraderType.GRID_OPERATOR)
			.collect(Collectors.toSet());
	/** The hours where the market could not be cleared properly. */
	private final List<Integer> unclearedHours = new ArrayList<>();
	/**
	 * Volume requested for each TraderType,HourOfDay,BidType
	 */
	private HashMap<TraderType, Map<Integer, Map<BidType, Float>>> volumeRequested;
	/**
	 * Volume unaccepted for each TraderType,HourOfDay,BidType
	 */
	private HashMap<TraderType, Map<Integer, Map<BidType, Float>>> volumeUnaccepted;

	/**
	 * @param name
	 *            Name of the market.
	 * @param minimumPrice
	 *            Lower bound of the price scale in EUR/MWh.
	 * @param maximumPrice
	 *            Upper bound of the price scale in EUR/MWh.
	 */
	public RegularCallMarket(String name, float minimumPrice, float maximumPrice,
			MarketArea marketArea) {
		this.name = name;
		this.minimumPrice = minimumPrice;
		this.maximumPrice = maximumPrice;
		this.marketArea = marketArea;
	}

	/**
	 * Clears the market while considering <b>block bids</b>.
	 * <p>
	 * Old market rules as described in <i>Ockenfells (2008)</i>:
	 * <p>
	 * All bids are aggregated into supply and demand functions in accordance
	 * with art. 24 of the trading conditions as outlined herein after. As a
	 * first step, all bids for all individual hours and for all blocks are
	 * converted into linearly interpolated sell or buy curves. In this context,
	 * the price bid for the individual hours is taken into account, while block
	 * bids are assumed to be unlimited initially. The market price of this
	 * first step is established on the basis of the intersection of the
	 * resulting supply and demand functions.
	 * <p>
	 * As a second step, the block bid which would incur the highest losses at
	 * the hourly prices of the first step – i.e. the block bid which displays
	 * the biggest gap to the market prices established in the first step - is
	 * then excluded (irreversible). In cases where two bids have exactly the
	 * same gap, the bid with the lower volume is excluded. In accordance with
	 * this principle, individual blocks are excluded in further iterations
	 * until all blocks which have not been excluded yet can at least realize
	 * their total demand.
	 * <p>
	 * Adaptions: Block bids can be excluded if they are not profitable anymore
	 * or if they can realize their demand at all times, meaning being
	 * profitable at all times. This can lead to problemns, e.g. if only
	 * blockbids are available for one hour than blockbids have to
	 *
	 *
	 * @param bidPoints
	 *            all hourly bids for the day
	 * @param blockBids
	 *            all block bids for the day
	 *
	 */
	public void clearBlockHourlyMarket(Map<Integer, List<Bid>> bidPoints,
			List<BlockBidPower> blockBids) {

		// Initialize hourly bids lists
		bidPointsDay = bidPoints;
		preSortHourlyBids();

		// Init block bids
		blockBidsDay = blockBids;
		setAllBlockBidsInMarket();

		// Set unique identifiers for all bids
		setIdentifier();
		setBidsMap();

		// Create hourly bids, sort them and calculate bid curves
		makeTemporaryHourlyBids();
		sortTemporaryBids();
		calculateBidCurves();

		// Find market prices, until all block bids are profitable
		final int noRemovedBids = 1;
		final float noRemovedBidsPercentage = 0.10f;
		boolean notFullySatisfiedBlockBids = true;
		while (notFullySatisfiedBlockBids) {
			findMarketOutcomes();
			notFullySatisfiedBlockBids ^= checkBlockBids(noRemovedBids, noRemovedBidsPercentage,
					false, null);
		}

		// Find market prices, until all exogenous (e.g. renewable) bids defined
		// in {#link traderTypesExogenous} are fully accepted, if desired
		if (exogenousAcceptAll) {
			notFullySatisfiedBlockBids = true;
			while (notFullySatisfiedBlockBids) {
				findMarketOutcomes();
				setBidVolumes(false);
				findTypeVolumes(traderTypesExogenous);
				notFullySatisfiedBlockBids ^= checkBlockBids(noRemovedBids, noRemovedBidsPercentage,
						false, traderTypesExogenous);
			}
		}

		clearingPrices = temporaryPrices;
		clearingVolumes = temporaryVolumes;

		findTypeVolumes(traderTypesAll);
		setStartCosts();
		setBidVolumes(true);
		checkFinalResults();
		marketArea.getRegularCallMarketLog().logSecurityOfSupply(bidPoints, temporaryPriceFunction,
				volumeRequested);
		marketArea.getRegularCallMarketLog().logPrices(bidPoints, blockBids, clearingPrices,
				clearingVolumes, temporaryPriceFunction, bidsLastAcceptedSupply);

	}

	/**
	 *
	 * @param bidPoints
	 *            all CapacityCertificate bids for the Year
	 * @author Florian Zimmermann
	 */
	public void clearCertificateMarket(List<Bid> bidPoints) {
		try {
			bidPointsDay = new HashMap<>();
			bidPointsDay.put(0, bidPoints);
			sortDailyBids(); // after sorting, bigger bids are considered first
			setIdentifier();
			setBidsMap();
			makeTemporaryHourlyBids();
			sortTemporaryBids();
			calculateBidCurves();
			findMarketOutcomes();

			clearingPrices = temporaryPrices;
			clearingVolumes = temporaryVolumes;
			setVolumesCapacityCertificateMarket(bidPoints);

			checkFinalResultsCertificates();
		} catch (final Exception e) {
			logger.error(e.getMessage());
		}
	}

	/**
	 * Executes the call market
	 *
	 * @return bids The list of bids; the method clear market has added
	 *         information about results to each bid
	 */
	@Deprecated
	public void clearMarket(List<Bid> bidPoints, int hour) {

		sortBids(bidPoints);

		double sumVolume = 0;
		final int numberOfValidPoints = bidPoints.size();
		clearingPrice = -1;
		clearingVolume = 0;

		/*
		 * all diffs for the same price are summed before zero crossing is
		 * tested; (bids are sorted from lowest to highest price and highest to
		 * lowest volume)
		 */
		for (int bidPointIndex = 0; bidPointIndex < numberOfValidPoints; bidPointIndex++) {

			final Bid bidPoint = bidPoints.get(bidPointIndex);

			float bidVolume = Float.NaN;
			if (bidPoint.getType() == BidType.ASK) {
				bidVolume = bidPoint.getVolume();
			} else if (bidPoint.getType() == BidType.SELL) {
				bidVolume = -bidPoint.getVolume();
			}

			// test whether zero is crossed
			if ((Math.signum(sumVolume) == Math.signum(sumVolume + bidVolume))
					|| (sumVolume == 0)) {

				// entire bid volume has been sold
				bidPoint.setVolumeAccepted(bidPoint.getVolume());

				// add volume only for supply
				if (bidPoint.getType() == BidType.SELL) {
					clearingVolume += Math.abs(bidVolume);
				}

				sumVolume += bidVolume;

			} else {

				if (((sumVolume + bidPoint.getVolume()) == 0)
						&& ((bidPointIndex + 1) < bidPoints.size())) {
					clearingPrice = (bidPoint.getPrice()
							+ bidPoints.get(bidPointIndex + 1).getPrice()) / 2;
				} else {
					clearingPrice = bidPoint.getPrice();
				}

				bidPoint.setVolumeAccepted((float) Math.abs(sumVolume));

				// add volume only for supply
				if (bidPoint.getType() == BidType.SELL) {
					clearingVolume += Math.abs(sumVolume);
				}
				break;
			}
		}

		// Check accepted volume
		checkAcceptedVolume(bidPoints);

		// no equilibrium found
		if (clearingPrice == -1) {
			// no bids for this hour
			if (sumVolume == 0) {
				clearingPrice = 0;
				status = Operator.SUCCESSFUL;
			} else {

				if (sumVolume > 0) {
					clearingPrice = maximumPrice;
					status = Operator.DEMAND_OVERHANG;
				} else {
					clearingPrice = minimumPrice;
					status = Operator.OFFER_OVERHANG;
				}
				for (final Bid bidPoint : bidPoints) {
					bidPoint.setVolumeAccepted(0);
				}
				clearingVolume = 0;
			}
		} else {
			status = Operator.SUCCESSFUL;
			if (clearingPrice > maximumPrice) {
				logger.error(name + ": MCP higher than " + maximumPrice + " : " + clearingPrice
						+ ". Year " + Date.getYear() + ", day " + Date.getDayOfYear() + "hour"
						+ hour + "sumVolume:" + sumVolume + "status" + status);
			}
		}
	}

	/**
	 * Clears the market via a simplified <code>COSMOS</code>-algorithm
	 *
	 * The objective of the algorithm is to maximize the total welfare: The
	 * welfare is defined as the difference between the cumulative amount that
	 * the buyers are ready to pay and the cumulative amount that the sellers
	 * want to be paid (quantities are signed) over all hours. <br>
	 * <br>
	 * <b>Simplifications</b>: <br>
	 * No bid curves => p0=p1=p.<br>
	 * No areas => m=1, ATC_PRICE, from, to not regarded.<br>
	 * <br>
	 * If-else-constraints are handled via
	 * <code>http://www.yzuda.org/Useful_Links/optimization/if-then-else-Main.html</code>
	 *
	 *
	 * @param bidList
	 *            All bids for auction
	 */
	public void clearMarketCosmos(Map<Integer, List<Bid>> bidList) {

		bidPointsDay = bidList;
		for (int i = 0; i < bidPointsDay.get(0).size(); i++) {
			if (bidPointsDay.get(0).get(i).getType() == BidType.ASK) {
				bidPointsDay.get(0).remove(i);
			}
		}
		bidPointsDay.get(0).add(
				new HourlyBidPower(50, 20000, 1, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
		bidPointsDay.get(0).add(
				new HourlyBidPower(30, 10000, 1, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
		bidPointsDay.get(0).add(
				new HourlyBidPower(25, 10000, 1, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));
		bidPointsDay.get(0).add(
				new HourlyBidPower(20, 10000, 1, 0, BidType.ASK, TraderType.UNKNOWN, marketArea));

		final RegularCallMarket rcm = new RegularCallMarket(name, 0, 3000, null);
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			rcm.clearMarket(bidPointsDay.get(i), i);
		}

		try {

			final GRBEnv env = new GRBEnv();
			final GRBModel model = new GRBModel(env);

			// The MIPFocus parameter allows you to modify your high-level
			// solution strategy, depending on your goals. By default
			// (MIPFocus=0), the Gurobi MIP solver strikes a balance between
			// finding new feasible solutions and proving that the current
			// solution is optimal. If you are more interested in good quality
			// feasible solutions, you can select MIPFocus=1. If you believe the
			// solver is having no trouble finding the optimal solution, and
			// wish to focus more attention on proving optimality, select
			// MIPFocus=2. If the best objective bound is moving very slowly (or
			// not at all), you may want to try MIPFocus=3 to focus on the
			// bound.
			final int MIPFocus = 0;
			model.getEnv().set(GRB.IntParam.MIPFocus, MIPFocus);

			// Limits the total time expended (in seconds). Optimization returns
			// with a TIME_LIMIT status if the limit is exceeded.
			// Default value: Infinity
			// Range [0, infinity]
			final double TimeLimit = 30;
			model.getEnv().set(GRB.DoubleParam.TimeLimit, TimeLimit);

			// The MIP solver will terminate (with an optimal result) when the
			// relative gap between the lower and upper objective bound is less
			// than MIPGap times the upper bound.
			// Default value: 1e-4
			// Range [0, infinity]
			final double MIPGap = 1E-10;
			model.getEnv().set(GRB.DoubleParam.MIPGap, MIPGap);

			// Enables (1) or disables (0) console logging.
			final int LogToConsole = 1;
			model.getEnv().set(GRB.IntParam.LogToConsole, LogToConsole);

			// Market Clearing Price
			final GRBVar[] marketClearingPrice = new GRBVar[HOURS_PER_DAY];

			// Acceptance variables
			final GRBVar[][] accept = new GRBVar[HOURS_PER_DAY][];

			// Dummy variables
			final GRBVar[][][] dummyBinary = new GRBVar[HOURS_PER_DAY][][];
			final int binaryDummies = 6;

			final GRBVar[][][] dummyContinuous = new GRBVar[HOURS_PER_DAY][][];
			final int continuousDummies = 2;

			// Initialize prices
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				marketClearingPrice[hour] = model.addVar(-3000.0, 3000.0, 0.0, GRB.CONTINUOUS,
						"price");
			}

			// Initialize acceptance variables
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				accept[hour] = new GRBVar[bidPointsDay.get(hour).size()];
				for (int bid = 0; bid < bidPointsDay.get(hour).size(); bid++) {
					accept[hour][bid] = model.addVar(0, 1, 0.0, GRB.CONTINUOUS,
							"acceptance_" + hour + "_" + bid);
				}
			}

			// Initialize binary dummy variables
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				dummyBinary[hour] = new GRBVar[bidPointsDay.get(hour).size()][];
				for (int bid = 0; bid < bidPointsDay.get(hour).size(); bid++) {
					dummyBinary[hour][bid] = new GRBVar[binaryDummies];
					for (int dummy = 0; dummy < binaryDummies; dummy++) {
						dummyBinary[hour][bid][dummy] = model.addVar(0, 1, 0.0, GRB.BINARY,
								"acceptance_" + hour + "_" + bid);
					}
				}
			}

			// Initialize continuous dummy variables
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				dummyContinuous[hour] = new GRBVar[bidPointsDay.get(hour).size()][];
				for (int bid = 0; bid < bidPointsDay.get(hour).size(); bid++) {
					dummyContinuous[hour][bid] = new GRBVar[binaryDummies];
					for (int dummy = 0; dummy < continuousDummies; dummy++) {
						dummyContinuous[hour][bid][dummy] = model.addVar(-10E10, 10E10, 0.0,
								GRB.CONTINUOUS, "acceptance_" + hour + "_" + bid);
					}
				}
			}

			// Update model to integrate new variables
			model.update();

			// Objective function
			final GRBLinExpr objective = new GRBLinExpr();
			// Needed for constraints
			final double bigConstant = 10E10;
			final GRBLinExpr expr1 = new GRBLinExpr();
			final GRBLinExpr expr2 = new GRBLinExpr();

			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				final ListIterator<Bid> iterator = bidPointsDay.get(hour).listIterator();
				int bid = 0;
				while (iterator.hasNext()) {

					final Bid bidPoint = iterator.next();
					int dummyBinaryCounter = 0;
					int dummyContinuousCounter = 0;

					// volume it is considered positive for supply orders and
					// negative for demand orders in COSMOS
					// in bid Point negative value: sell bid, positive value:
					// buy bid
					// Therefore volume has to be flipped
					// That does seem to work strangely!
					final float volume = -1 * bidPoint.getVolume();

					/*
					 * 1. Market Constraint
					 * --------------------------------------------
					 */
					// An hourly order o must be fully accepted if it is
					// in-the-money
					// q(p - MCP) < 0 => Accept = 1
					// can be written as
					// q(p - MCP) - dummyContinuous = 0 and
					// -dummyContinuous <= bigConstant * dummyBinary and
					// Accept >= dummyBinary

					// q(p - MCP) - dummyContinuous = 0
					expr1.addConstant(volume * bidPoint.getPrice());
					expr1.addTerm(-volume, marketClearingPrice[hour]);
					expr1.addTerm(-1, dummyContinuous[hour][bid][dummyContinuousCounter]);
					model.addConstr(expr1, GRB.EQUAL, 0, "market_constraint_1a");
					expr1.clear();

					// dummyContinuous >= -bigConstant * dummyBinary and
					expr1.addTerm(1, dummyContinuous[hour][bid][dummyContinuousCounter]);
					expr2.addTerm(-bigConstant, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.GREATER_EQUAL, expr2, "market_constraint_1b");
					expr1.clear();
					expr2.clear();

					// Accept >= dummyBinary
					expr1.addTerm(1, accept[hour][bid]);
					expr2.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.GREATER_EQUAL, expr2, "market_constraint_1c");
					expr1.clear();
					expr2.clear();

					dummyBinaryCounter++;
					dummyContinuousCounter++;

					/*
					 * 2. Market Constraint
					 * --------------------------------------------
					 */
					// An hourly order o must be refused if it is
					// out-of-the-money:
					// q(p - MCP) > 0 => Accept = 0
					// can be written as
					// q(p - MCP) - dummyContinuous = 0 and
					// dummyContinuous <= bigConstant * (1 - dummyBinary) and
					// accept <= dummyBinary

					// q(p - MCP) - dummyContinuous = 0 and
					expr1.addConstant(volume * bidPoint.getPrice());
					expr1.addTerm(-volume, marketClearingPrice[hour]);
					expr1.addTerm(-1, dummyContinuous[hour][bid][dummyContinuousCounter]);
					model.addConstr(expr1, GRB.EQUAL, 0, "market_constraint_2a");
					expr1.clear();

					// dummyContinuous <= bigConstant * (1 - dummyBinary) and
					expr1.addTerm(1, dummyContinuous[hour][bid][dummyContinuousCounter]);
					expr2.addConstant(bigConstant);
					expr2.addTerm(-bigConstant, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.LESS_EQUAL, expr2, "market_constraint_2b");
					expr1.clear();
					expr2.clear();

					// accept <= dummyBinary
					expr1.addTerm(1, accept[hour][bid]);
					expr2.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.LESS_EQUAL, expr2, "market_constraint_2c");
					expr1.clear();
					expr2.clear();

					dummyBinaryCounter++;
					dummyContinuousCounter++;

					/*
					 * 3. Market Constraint
					 * --------------------------------------------
					 */
					// An hourly order o may be partially rejected only if
					// it is at-the-money:
					// 0 < Accept < 1 => MCP=p
					// can be written as
					// Accept <= dummyBinary1 and
					// 1-Accept <= dummyBinary2 and
					// (2-dummyBinary1-dummyBinary2) < dummyBinary3 and
					// MCP <= p + M * (1- dummyBinary3) and
					// MCP >= p * dummyBinary3

					// Accept <= dummyBinary1
					expr1.addTerm(1, accept[hour][bid]);
					expr2.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.LESS_EQUAL, expr2, "market_constraint_3a");
					expr1.clear();
					expr2.clear();
					dummyBinaryCounter++;

					// (1-Accept) <= dummyBinary2
					expr1.addConstant(1);
					expr1.addTerm(-1, accept[hour][bid]);
					expr2.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.LESS_EQUAL, expr2, "market_constraint_3b");
					expr1.clear();
					expr2.clear();
					dummyBinaryCounter++;

					// (dummyBinary1 + dummyBinary2 - 1.5) < dummyBinary3
					expr1.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter - 2]);
					expr1.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter - 1]);
					expr1.addConstant(-1.5);
					expr2.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.LESS_EQUAL, expr2, "market_constraint_3c");
					expr1.clear();
					expr2.clear();

					// MCP <= p + M * (1 - dummyBinary3)
					expr1.addTerm(1, marketClearingPrice[hour]);
					expr2.addConstant(bidPoint.getPrice());
					expr2.addConstant(bigConstant);
					expr2.addTerm(-bigConstant, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.LESS_EQUAL, expr2, "market_constraint_3d");
					expr1.clear();
					expr2.clear();

					// MCP >= p * dummyBinary3
					expr1.addTerm(1, marketClearingPrice[hour]);
					expr2.addTerm(bidPoint.getPrice(), dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.GREATER_EQUAL, expr2, "market_constraint_3e");
					dummyBinaryCounter++;
					expr1.clear();
					expr2.clear();

					/*
					 * 4. Market Constraint
					 * --------------------------------------------
					 */
					// An hourly order o may be accepted only if it is at-
					// or in-the-money:
					// Accept > 0 => q(MCP-p) = 0
					// can be written as
					// accept <= dummyBinary
					// volume(MCP - p) >= - bigConstant * (1 - dummyBinary)

					// accept <= dummyBinary
					expr1.addTerm(1.0, accept[hour][bid]);
					expr2.addTerm(1, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.LESS_EQUAL, expr2, "market_constraint_4a");
					expr1.clear();
					expr2.clear();

					// volume(MCP-p) >= - bigConstant * (1 - dummyBinary)
					expr1.addTerm(volume, marketClearingPrice[hour]);
					expr1.addConstant(volume * -bidPoint.getPrice());
					expr2.addConstant(-1 * bigConstant);
					expr2.addTerm(bigConstant, dummyBinary[hour][bid][dummyBinaryCounter]);
					model.addConstr(expr1, GRB.GREATER_EQUAL, expr2, "market_constraint_4b");
					expr1.clear();
					expr2.clear();

					/*
					 * Objective function
					 * --------------------------------------------
					 */
					// add to objective: -q * p * Acceptance
					objective.addTerm(-1 * volume * bidPoint.getPrice(), accept[hour][bid]);

					bid++;

				}
			}

			/*
			 * 1. Network Constraint
			 * --------------------------------------------
			 */

			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				final ListIterator<Bid> iterator = bidPointsDay.get(hour).listIterator();
				int counter = 0;
				while (iterator.hasNext()) {
					expr1.addTerm(iterator.next().getVolume(), accept[hour][counter]);
					counter++;
				}
				model.addConstr(expr1, GRB.EQUAL, 0, "network_constraint_1");
				expr1.clear();
			}

			// Solve model
			model.setObjective(objective);
			model.optimize();

			// Show results
			int hour = 0;
			for (final GRBVar mcp : marketClearingPrice) {
				logger.info("hour " + ++hour + " " + mcp.get(GRB.DoubleAttr.X));
			}

			// Dispose everything
			model.dispose();
			env.dispose();

		} catch (final GRBException e) {
			logger.error("Error code: " + e.getErrorCode() + ". " + e.getMessage(), e);
		}
	}

	public Map<Integer, List<Bid>> getBidPointsDay() {
		return bidPointsDay;
	}

	public Map<Integer, PowerBid> getBidsLastAcceptedSupply() {
		return bidsLastAcceptedSupply;
	}

	public List<BlockBidPower> getBlockBidsDay() {
		return blockBidsDay;
	}

	public float getClearingPrice() {
		return clearingPrice;
	}

	/**
	 * @return a copy of the market prices in Euro/MWh
	 */
	public List<Float> getClearingPrices() {
		return new ArrayList<>(clearingPrices);
	}

	public List<Float> getClearingStartupCosts() {
		return clearingStartupCosts;
	}

	public float getClearingVolume() {
		return clearingVolume;
	}

	/**
	 * @return a copy of the market volumes in MWh
	 */
	public List<Float> getClearingVolumes() {
		return new ArrayList<>(clearingVolumes);
	}

	public int getStatus() {
		return status;
	}

	/**
	 * Adapting bid curves by removing the volume from block bid which
	 * <code> index</code> equals<code> worstProfitIndex</code>.
	 *
	 * @param worstProfitIndex
	 *            The index block bid which volume will be removed.
	 */
	private void adaptBidCurves(int worstProfitIndex) {

		final BlockBidPower blockBid = blockBidsDay.get(worstProfitIndex);
		final int start = blockBid.getStart();
		final int end = blockBid.getEnd();
		final float volume = blockBid.getVolume();
		final float price = blockBid.getPrice();

		if (blockBid.getBidType() == BidType.SELL) {
			for (int hour = start; hour <= end; hour++) {
				final ListIterator<PriceCurvePoint> iterator = temporaryPriceFunction.get(hour)
						.listIterator(temporaryPriceFunction.get(hour).size());

				while (iterator.hasPrevious()) {
					final PriceCurvePoint priceCurvePoint = iterator.previous();
					if (priceCurvePoint.getPrice() > price) {
						priceCurvePoint.setSellVolumeMaximum(
								priceCurvePoint.getSellVolumeMaximum() - volume);
						priceCurvePoint.setSellVolumeMinimum(
								priceCurvePoint.getSellVolumeMinimum() - volume);
					}
					// Attention here only the maximum value has to changed.
					else if (priceCurvePoint.getPrice() == price) {
						priceCurvePoint.setSellVolumeMaximum(
								priceCurvePoint.getSellVolumeMaximum() - volume);

						// Remove point if does not represent a change anymore
						if ((priceCurvePoint.getSellVolumeMaximum() == priceCurvePoint
								.getSellVolumeMinimum())
								&& (priceCurvePoint.getAskVolumeMaximum() == priceCurvePoint
										.getAskVolumeMinimum())) {
							iterator.remove();
						}

					} else {
						break;
					}
				}
			}
		} else {
			for (int hour = start; hour <= end; hour++) {
				final Iterator<PriceCurvePoint> iterator = temporaryPriceFunction.get(hour)
						.iterator();
				while (iterator.hasNext()) {
					final PriceCurvePoint priceCurvePoint = iterator.next();
					if (priceCurvePoint.getPrice() < price) {
						priceCurvePoint.setAskVolumeMaximum(
								priceCurvePoint.getAskVolumeMaximum() - volume);
						priceCurvePoint.setAskVolumeMinimum(
								priceCurvePoint.getAskVolumeMinimum() - volume);
					}
					// Attention here only the maximum value has to changed.
					else if (priceCurvePoint.getPrice() == price) {
						priceCurvePoint.setAskVolumeMaximum(
								priceCurvePoint.getAskVolumeMaximum() - volume);

						// Remove point if does not represent a change anymore
						if ((priceCurvePoint.getSellVolumeMaximum() == priceCurvePoint
								.getSellVolumeMinimum())
								&& (priceCurvePoint.getAskVolumeMaximum() == priceCurvePoint
										.getAskVolumeMinimum())) {
							iterator.remove();
						}

					} else {
						break;
					}
				}
			}
		}
	}

	/**
	 * Calculate the bid curves from block bids and hourly bids.
	 * <p>
	 * Writes hourly lists into {@link #temporaryPriceFunction} that contain for
	 * each price the corresponding <b>ask/sell volumes</b>. Each volume has a
	 * maximal and minimal value.
	 */
	private void calculateBidCurves() {

		initializePriceFunction();

		for (int hour = 0; hour < temporaryBids.size(); hour++) {

			final ListIterator<Bid> bidIterator = temporaryBids.get(hour).listIterator();

			final TreeSet<Float> hourlyPrices = new TreeSet<>();
			// Find unique list of prices for each hour
			while (bidIterator.hasNext()) {
				hourlyPrices.add(bidIterator.next().getPrice());
			}
			// Write unique prices into hourly price set
			for (final float price : hourlyPrices) {
				temporaryPriceFunction.get(hour).add(new PriceCurvePoint(price));
			}

			// Write sell and ask volumes for each price
			writeTemporarySellVolumes(hour, hourlyPrices);
			writeTemporaryAskVolumes(hour, hourlyPrices);
		}

	}

	/**
	 * Calculate the total market volumes for all the block bids.
	 */
	private void calculateBlockBidVolumes(List<Float> remainingAskVolume,
			List<Float> remainingSellVolume, boolean finalResults) {

		for (final BlockBidPower blockBid : blockBidsDay) {
			if (blockBid.isAccepted()) {
				temporaryAcceptedVolumes.put(blockBid.getIdentifier(), blockBid.getVolume());
				for (int hour = blockBid.getStart(); hour <= blockBid.getEnd(); hour++) {
					if (blockBid.getBidType() == BidType.ASK) {
						final float remainingAskVolumeNew = remainingAskVolume.get(hour)
								- blockBid.getVolume();
						remainingAskVolume.set(hour, remainingAskVolumeNew);
					} else if (blockBid.getBidType() == BidType.SELL) {
						final float remainingSellVolumeNew = remainingSellVolume.get(hour)
								- blockBid.getVolume();
						remainingSellVolume.set(hour, remainingSellVolumeNew);
					}
				}
			}
		}

	}

	/**
	 * Set accepted volumes for hourly bids and calculate the total market
	 * volumes.
	 */
	private void calculateHourlyBidVolumes(List<Float> remainingAskVolume,
			List<Float> remainingSellVolume, boolean finalResults) {

		if (exogenousAccepted == null) {
			exogenousAccepted = new HashMap<>();
			exogenousUnaccepted = new HashMap<>();
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				exogenousAccepted.put(hour, false);
			}
		}

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			if (!finalResults && exogenousAccepted.get(hour)) {
				continue;
			}

			for (final Bid bidPoint : bidPointsDayType.get(hour).get(BidType.SELL)) {

				if (remainingSellVolume.get(hour) > 0) {

					final float volume = Math.min(bidPoint.getVolume(),
							remainingSellVolume.get(hour));

					if ((volume > 0.1) && (bidPoint.getPrice() > temporaryPrices.get(hour))) {
						logger.error(
								"This should not happen! Clearing price should be higher than bid price!");
					}

					remainingSellVolume.set(hour, remainingSellVolume.get(hour) - volume);
					temporaryAcceptedVolumes.put(bidPoint.getIdentifier(), volume);

					if (finalResults) {
						bidPoint.setVolumeAccepted(volume);
					}

				} else {
					temporaryAcceptedVolumes.put(bidPoint.getIdentifier(), 0f);
				}
			}

			for (final Bid bidPoint : bidPointsDayType.get(hour).get(BidType.ASK)) {
				if (remainingAskVolume.get(hour) > 0) {
					final float volume = Math.min(bidPoint.getVolume(),
							remainingAskVolume.get(hour));
					remainingAskVolume.set(hour, remainingAskVolume.get(hour) - volume);
					temporaryAcceptedVolumes.put(bidPoint.getIdentifier(), volume);

					if (finalResults) {
						bidPoint.setVolumeAccepted(volume);
					}
				} else {
					temporaryAcceptedVolumes.put(bidPoint.getIdentifier(), 0f);
				}
			}
		}

	}

	/**
	 * Checks whether the accepted volume of all ASK bids and all SELL bids is
	 * equal.
	 */
	private void checkAcceptedVolume(List<Bid> bidPoints) {
		float checkAcceptedVolumeASK = 0;
		float checkAcceptedVolumeSELL = 0;
		for (final Bid bidPoint : bidPoints) {
			if (bidPoint.getVolumeAccepted() > bidPoint.getVolume()) {
				logger.warn("Accepted volume larger than bid volume!");
			}
			if (bidPoint.getType() == BidType.ASK) {
				checkAcceptedVolumeASK += bidPoint.getVolumeAccepted();
			} else if (bidPoint.getType() == BidType.SELL) {
				checkAcceptedVolumeSELL += bidPoint.getVolumeAccepted();
			} else {
				logger.warn("Wrong BidType!");
			}
		}
		if (Math.abs(checkAcceptedVolumeASK - checkAcceptedVolumeSELL) > 0.5) {
			logger.warn("RegularCallMarket did not clear properly! Clearing Volume "
					+ clearingVolume + ", Ask " + checkAcceptedVolumeASK + ", Sell "
					+ checkAcceptedVolumeSELL);
		}
	}

	/**
	 * Checks if all block bids, that are not yet set to unaccepted, can realize
	 * their total demand in each hour or are profitable for all hours. If not
	 * it the #removeLimit block bid with the lowest profit.
	 *
	 * @param removeLimit
	 *            the maximum number of negative block bids that are removed.
	 *            <code>1</code> is the most exact and slowest value.
	 * @param alwaysInMarket
	 *            if true, all block bids have to in the market for all relevant
	 *            hours, if false, block bids only have to be profitable.
	 *
	 * @return true, if all block bids can realize their total demand in all
	 *         hours
	 */
	private boolean checkBlockBids(int removeLimit, float removedPercentage, boolean alwaysInMarket,
			Set<TraderType> traderTypes) {

		// If no block bids are submitted
		if (blockBidsDay.isEmpty()) {
			return true;
		}

		// Determine additional sell capacity at current price
		final Map<Integer, Float> remainingSellCapacityAtPrice = new HashMap<>();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			final PriceCurvePoint pricePoint = outcomes.get(hourOfDay);
			remainingSellCapacityAtPrice.put(hourOfDay,
					pricePoint.getSellVolumeMaximum() - pricePoint.getAskVolumeMinimum());
		}

		// Determine additional sell capacity at second highest price (i.e.
		// without peaker)
		final Map<Integer, Float> surplusTotal = new HashMap<>();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			surplusTotal.put(hourOfDay, Float.MAX_VALUE);
		}

		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {

			for (int index = temporaryPriceFunction.get(hourOfDay).size()
					- 1; index >= 0; index--) {
				final PriceCurvePoint pricePointCurrent = temporaryPriceFunction.get(hourOfDay)
						.get(index);

			}
		}

		// Initialize lists
		final Map<Integer, Float> blockBidsSellVolume = new HashMap<>();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			blockBidsSellVolume.put(hourOfDay, 0f);
		}

		// Set that contains negative profits
		final List<BlockProfit> worstProfits = new ArrayList<>();
		// Map that contains positive profits for each hour.
		final Map<Integer, List<BlockProfit>> positiveProfits = new HashMap<>();

		// Check all block bids
		int listIndex = 0;
		final ListIterator<BlockBidPower> iterator = blockBidsDay.listIterator();
		while (iterator.hasNext()) {

			final BlockBidPower blockBid = iterator.next();

			// Only consider blocks that are still in the market
			if (!blockBid.isAccepted()) {
				listIndex++;
				continue;
			}

			final int start = blockBid.getStart();
			final int end = blockBid.getEnd();
			final float bidPrice = blockBid.getPrice();
			float blockProfitTotal = 0;
			boolean bidTemporarlyOutOfMarket = false;

			// Calculate profit for blocks that are in the market
			if (blockBid.getBidType() == BidType.SELL) {
				for (int hour = start; hour <= end; hour++) {

					final float profitHourly = temporaryPrices.get(hour) - bidPrice;
					blockProfitTotal += profitHourly;
					blockBidsSellVolume.put(hour,
							blockBidsSellVolume.get(hour) + blockBid.getVolume());

					// Check if bid is unprofitable
					if (profitHourly < 0) {
						bidTemporarlyOutOfMarket = true;
					}
					// If profitable, check if full demand is satisfied
					// First: Is price setting?
					else if (Math.abs(profitHourly) < 0.00001) {
						final float volume = remainingSellCapacityAtPrice.get(hour)
								- blockBid.getVolume();
						remainingSellCapacityAtPrice.put(hour, volume);
						// Second: Full volume of block bid is satisfied
						if (volume < 0) {
							bidTemporarlyOutOfMarket = true;
						}
					}
				}
			} else {
				for (int hour = start; hour <= end; hour++) {
					final float profitHourly = bidPrice - temporaryPrices.get(hour);
					blockProfitTotal += profitHourly;
					if (profitHourly < 0) {
						bidTemporarlyOutOfMarket = true;
					}
				}
			}

			if ((alwaysInMarket && bidTemporarlyOutOfMarket)
					|| (!alwaysInMarket && (blockProfitTotal < 0))) {
				final BlockProfit currentProfit = new BlockProfit(blockProfitTotal,
						blockBid.getVolume(), listIndex);
				worstProfits.add(currentProfit);
			}

			// Log positive block bids
			if (blockProfitTotal >= 0) {
				for (int hour = start; hour <= end; hour++) {
					final BlockProfit currentProfit = new BlockProfit(blockProfitTotal,
							blockBid.getVolume(), listIndex);
					if (!positiveProfits.containsKey(hour)) {
						positiveProfits.put(hour, new ArrayList<>());
					}
					positiveProfits.get(hour).add(currentProfit);
				}
			}

			listIndex++;
		}

		// Remove block bids by setting their accepted
		// status to false and adapt bid curves.
		final int maxNumber = Math.max((int) (worstProfits.size() * removedPercentage),
				removeLimit);
		boolean allBidsAccepted = deactivateBlockBids(surplusTotal, worstProfits, maxNumber, false);

		// Check if some exogenous supply is unsatisfied even though all block
		// bids have a positive value, then deactivate more block bids
		if (worstProfits.isEmpty() && (traderTypes != null)) {

			final Map<Integer, Float> blockBidsSurplus = new HashMap<>();
			Integer surplusHourMax = null;
			float surplusVolumeMax = Float.NEGATIVE_INFINITY;

			for (final int hourOfDay : blockBidsSellVolume.keySet()) {

				// Check if block bids are still selling
				if (blockBidsSellVolume.get(hourOfDay) <= 0) {
					continue;
				}

				float sellVolumeExogenousUnaccepted = 0f;
				for (final TraderType traderType : traderTypes) {
					sellVolumeExogenousUnaccepted += volumeUnaccepted.get(traderType).get(hourOfDay)
							.get(BidType.SELL);
				}

				blockBidsSurplus.put(hourOfDay, sellVolumeExogenousUnaccepted);
				if (sellVolumeExogenousUnaccepted > surplusVolumeMax) {
					surplusVolumeMax = sellVolumeExogenousUnaccepted;
					surplusHourMax = hourOfDay;
				}

				if (sellVolumeExogenousUnaccepted > 0) {
					exogenousAccepted.put(hourOfDay, false);
				} else {
					exogenousAccepted.put(hourOfDay, true);
				}

				exogenousUnaccepted.put(hourOfDay, sellVolumeExogenousUnaccepted);
			}

			// Deactive first block bids in hour with highest surplus
			if (surplusVolumeMax > 0) {
				allBidsAccepted = allBidsAccepted && deactivateBlockBids(surplusTotal,
						positiveProfits.get(surplusHourMax), maxNumber, true);
			}

		}

		return allBidsAccepted;
	}

	/**
	 * Checks the result of market clearing
	 */
	private void checkFinalResults() {
		for (final float price : clearingPrices) {
			if (Float.isNaN(price)) {
				logger.error("Price was not determined correctly!");
			}
		}
		for (final float volume : clearingVolumes) {
			if (Float.isNaN(volume)) {
				logger.error("Volume was not determined correctly!");
			}
		}
	}

	/**
	 * Checks the result of market clearing
	 */
	private void checkFinalResultsCertificates() {

		if (Float.isNaN(clearingPrices.get(0))) {
			logger.error("Price was not determined correctly!");
		}

		if (Float.isNaN(clearingVolumes.get(0))) {
			logger.error("Volume was not determined correctly!");
		}
	}

	@SuppressWarnings("unused")
	private void clearMarketSteps(List<Bid> bidPointsNew) {

		// Create to separate list of ask and sell bids which are to be matched
		final List<Bid> bidPointsAsk = new ArrayList<>();
		final List<Bid> bidPointsSell = new ArrayList<>();
		for (final Bid bidPoint : bidPointsNew) {
			if (bidPoint.getBidType() == BidType.ASK) {
				bidPointsAsk.add(bidPoint);
			} else if (bidPoint.getBidType() == BidType.SELL) {
				bidPointsSell.add(bidPoint);
			}
		}

		// Sort ascendingly by price
		sortBids(bidPointsAsk);
		sortBids(bidPointsSell);

		// Variables to store current clearing volume and clearing price
		float tempClearingVolume = 0f;
		float tempClearingPrice = -1f;

		// Index of current sell bid
		int bidPointSellIndex = 0;

		// Variables to store remaining volume of bids which are not fully
		// matched in each iteration
		float remainingAskVolume = 0f;
		float remainingSellVolume = 0f;

		// Loop all ask bids
		for (int bidPointAskIndex = bidPointsAsk.size()
				- 1; bidPointAskIndex >= 0; bidPointAskIndex--) {
			// Set current ask and sell bids
			final Bid bidPointAsk = bidPointsAsk.get(bidPointAskIndex);
			Bid bidPointSell = bidPointsSell.get(bidPointSellIndex);
			remainingAskVolume = bidPointAsk.getVolume();

			// If last sell bid is not yet fully matched, check current ask bid
			if ((remainingSellVolume < 0) && (bidPointAsk.getPrice() >= bidPointSell.getPrice())) {
				// Set current price
				tempClearingPrice = bidPointAsk.getPrice();

				// Set unmatched volume (could come from open ask or sell
				// volume)
				final float unmatchedVolume = remainingSellVolume + remainingAskVolume;

				// Case 1
				if (unmatchedVolume <= 0) {
					// Remaining sell volume still not matched
					remainingSellVolume = unmatchedVolume;

					// Ask bid fully accepted
					tempClearingVolume += bidPointAsk.getVolume();
					remainingAskVolume = 0;

					// Set accepted volumes
					bidPointAsk.setVolumeAccepted(bidPointAsk.getVolume());
					bidPointSell.setVolumeAccepted(
							bidPointSell.getVolumeAccepted() + bidPointAsk.getVolume());
				}
				// Case 2
				else {
					// Ask partially accepted
					remainingAskVolume = unmatchedVolume;
					tempClearingVolume += -remainingSellVolume;

					// Set accepted volumes
					bidPointAsk.setVolumeAccepted(
							bidPointAsk.getVolumeAccepted() + -remainingSellVolume);
					bidPointSell.setVolumeAccepted(bidPointSell.getVolume());

					// Remaining sell volume matched. Go to next sell bid
					remainingSellVolume = 0;
					bidPointSellIndex++;
					bidPointSell = bidPointsSell.get(bidPointSellIndex);
				}
			}

			while ((bidPointAsk.getPrice() >= bidPointSell.getPrice()) && (remainingAskVolume > 0)
					&& (remainingSellVolume >= 0) && (bidPointSellIndex < bidPointsSell.size())) {
				// Set current price
				tempClearingPrice = bidPointSell.getPrice();

				// Set unmatched volume (could come from open ask or sell
				// volume)
				final float unmatchedVolume = remainingAskVolume - bidPointSell.getVolume();

				// Case 1
				if (unmatchedVolume > 0) {
					// Remaining ask bid still not matched
					remainingAskVolume = unmatchedVolume;

					// Sell bid fully accepted
					tempClearingVolume += bidPointSell.getVolume();

					// Set accepted volumes
					bidPointAsk.setVolumeAccepted(
							bidPointAsk.getVolumeAccepted() + bidPointSell.getVolume());
					bidPointSell.setVolumeAccepted(bidPointSell.getVolume());

					// Go to next sell bid
					bidPointSellIndex++;
					bidPointSell = bidPointsSell.get(bidPointSellIndex);
				}
				// Case 2
				else {
					// Sell bid partially accepted
					remainingSellVolume = unmatchedVolume;

					// Set accepted volumes
					bidPointAsk.setVolumeAccepted(bidPointAsk.getVolume());
					bidPointSell.setVolumeAccepted(
							bidPointSell.getVolumeAccepted() + remainingAskVolume);

					// Remaining ask volume matched
					tempClearingVolume += remainingAskVolume;
					remainingAskVolume = 0;
				}
			}
		} // End loop all ask bids

		// Check accepted volume
		checkAcceptedVolume(bidPointsNew);

		// Check deviation from old clearing mechanism
		if ((Math.abs(tempClearingPrice - clearingPrice) > .1)
				|| (Math.abs(tempClearingVolume - clearingVolume) > .1)) {
			logger.error("Clearing different!");
		}
	}

	private boolean deactivateBlockBids(Map<Integer, Float> surplusTotal,
			List<BlockProfit> worstProfits, int numberOfRemovedBidsMax, boolean exogenous) {

		Collections.sort(worstProfits);

		boolean allBidsAccepted = true;
		for (int blockProfitIndex = 0; (blockProfitIndex < numberOfRemovedBidsMax)
				&& (blockProfitIndex < worstProfits.size()); blockProfitIndex++) {

			final BlockBidPower blockBid = blockBidsDay
					.get(worstProfits.get(blockProfitIndex).blockBidIndex);

			// Find minimal surplus
			float surplusMinimal = Float.POSITIVE_INFINITY;
			for (int hour = blockBid.getStart(); hour <= blockBid.getEnd(); hour++) {
				final float surplusHourly = surplusTotal.get(hour);
				if (surplusHourly < surplusMinimal) {
					surplusMinimal = surplusHourly;
				}
			}

			// See if market still clears!
			if ((surplusMinimal - blockBid.getVolume()) > 0) {
				blockBid.setVolumeAccepted(0);
				adaptBidCurves(worstProfits.get(blockProfitIndex).blockBidIndex);
				for (int hour = blockBid.getStart(); hour <= blockBid.getEnd(); hour++) {
					final float surplusHourlyNew = surplusTotal.get(hour) - blockBid.getVolume();
					surplusTotal.put(hour, surplusHourlyNew);
				}
				allBidsAccepted = false;
			} else {
				if (!exogenous) {
					logger.warn(Date.getYear() + "/" + Date.getDayOfYear()
							+ ": No deactivation possible even though only profit ist regarded?");
				} else {
					logger.warn(Date.getYear() + "/" + Date.getDayOfYear()
							+ ": No further deactivation of block bids possible as this would lead to market failure!");
				}
			}

		}

		return allBidsAccepted;
	}

	/**
	 * For each trader type, find the hourly bids. Useful e.g. when the
	 * requested volume for each trader needs to be determined.
	 *
	 * @param bidPointsDay
	 * @return
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

	/**
	 * Calculate the resulting prices for the priceCurvePoint. If several
	 * solutions exist
	 * <li>for the price, take average price from upper and lower bound.</li>
	 * <li>for the volume, maximize the traded volume.</li> <br>
	 * <br>
	 *
	 * @param hour
	 *            <code>[0,23]</code>
	 * @param index
	 *            the index of the priceCurvePoint in the temporaryPriceFunction
	 * @param priceCurvePoint
	 *
	 * @return <code>true</code> if intersection has be found or no intersection
	 *         exists
	 */
	private boolean findMarketIntersection(int hour, int index, PriceCurvePoint priceCurvePoint) {

		// No equilibrium at this point
		if (Math.abs(priceCurvePoint.getSellVolumeMaximum()) < Math
				.abs(priceCurvePoint.getAskVolumeMinimum())) {
			// but also not a problem since it is not the last point
			if ((index + 1) < temporaryPriceFunction.get(hour).size()) {
				return false;
			}
			// which is problem since it is the last point in the
			// list, meaning demand cannot be satisfied
			else if ((index + 1) == temporaryPriceFunction.get(hour).size()) {
				logger.error("No market equilibrium found.");
				return true;
			}
		}

		// Ambiguous Volume, i.e. ask and supply step intersect
		// Rule: Maximize traded volume
		if ((priceCurvePoint.getAskVolumeMinimum() != priceCurvePoint.getAskVolumeMaximum())
				&& (priceCurvePoint.getSellVolumeMinimum() != priceCurvePoint
						.getSellVolumeMaximum())
				&& (priceCurvePoint.getAskVolumeMinimum() <= priceCurvePoint.getSellVolumeMaximum())
				&& (priceCurvePoint.getSellVolumeMinimum() <= priceCurvePoint
						.getAskVolumeMaximum())) {
			temporaryPrices.set(hour, priceCurvePoint.getPrice());
			temporaryVolumes.set(hour, Math.min(priceCurvePoint.getAskVolumeMaximum(),
					priceCurvePoint.getSellVolumeMaximum()));
			outcomes.put(hour, priceCurvePoint);
			return true;
		}

		// Ambiguous Price, i.e. ask or supply steps lands directly on
		// supply/ask curve
		// Rule: Set price to the middle of price interval
		if (priceCurvePoint.getAskVolumeMinimum() == priceCurvePoint.getSellVolumeMaximum()) {
			final float lowerBound = priceCurvePoint.getPrice();
			final float upperBound;
			if (!((index + 1) == temporaryPriceFunction.get(hour).size())) {
				upperBound = temporaryPriceFunction.get(hour).get(index + 1).getPrice();
			} else {
				upperBound = lowerBound;
			}
			temporaryVolumes.set(hour, priceCurvePoint.getAskVolumeMinimum());
			temporaryPrices.set(hour, (lowerBound + upperBound) / 2);
			outcomes.put(hour, priceCurvePoint);
			return true;
		}

		// Unambiguous price and volume
		// Either a) Sell step intersects ask curve
		if ((priceCurvePoint.getAskVolumeMinimum() == priceCurvePoint.getAskVolumeMaximum())
				&& (priceCurvePoint.getSellVolumeMinimum() < priceCurvePoint.getAskVolumeMinimum())
				&& (priceCurvePoint.getAskVolumeMinimum() < priceCurvePoint
						.getSellVolumeMaximum())) {
			temporaryPrices.set(hour, priceCurvePoint.getPrice());
			temporaryVolumes.set(hour, priceCurvePoint.getAskVolumeMinimum());
			outcomes.put(hour, priceCurvePoint);
			return true;
		}
		// or b) Ask step intersects sell curve
		if ((priceCurvePoint.getSellVolumeMinimum() == priceCurvePoint.getSellVolumeMaximum())
				&& (priceCurvePoint.getAskVolumeMinimum() < priceCurvePoint.getSellVolumeMinimum())
				&& (priceCurvePoint.getSellVolumeMinimum() < priceCurvePoint
						.getAskVolumeMaximum())) {
			temporaryPrices.set(hour, priceCurvePoint.getPrice());
			temporaryVolumes.set(hour, priceCurvePoint.getSellVolumeMinimum());
			outcomes.put(hour, priceCurvePoint);
			return true;
		}

		return false;
	}

	/**
	 * Find the market outcomes for each hour of the day.
	 */
	private void findMarketOutcomes() {
		for (int hour = 0; hour < temporaryPriceFunction.size(); hour++) {
			findMarketOutcomesHourly(hour, 0, temporaryPriceFunction.get(hour).size() - 1);
		}
	}

	/**
	 * Calculate the resulting prices from {@link #temporaryPriceFunction} via
	 * {@link #findMarketIntersection(hour, index, PriceCurvePoint)}
	 *
	 * In order to save time, first, the start of the list, then end and then
	 * the last point of the price function is checked. If none of these points
	 * is the intersection of supply and demand, look at <i>the interval where
	 * the intersection is located</i> either [start, middle] or [middle, end].
	 *
	 * @param hour
	 *            <code>[0,23]</code>
	 * @param start
	 *            start point of temporaryPriceFunction for this hour
	 *            <code>[0,size-2]</code>
	 * @param end
	 *            end point of temporaryPriceFunction for this hour
	 *            <code>[1,size-1]</code>
	 *
	 */
	private void findMarketOutcomesHourly(int hour, int start, int end) {

		final ConcurrentMap<Integer, PriceCurvePoint> priceCurvePoints = new ConcurrentHashMap<>(3);
		priceCurvePoints.put(start, temporaryPriceFunction.get(hour).get(start));
		priceCurvePoints.put(end, temporaryPriceFunction.get(hour).get(end));

		// Check if market can be cleared! If not set price to maximal price
		// that demand is willing to pay and volume to maximal volume supply is
		// willing to produce. This is volume is less than what demand ask for!
		if ((priceCurvePoints.get(end).getPrice() >= maximumPrice) && (priceCurvePoints.get(end)
				.getSellVolumeMaximum() < priceCurvePoints.get(end).getAskVolumeMaximum())) {

			// Only print warning once
			if (!unclearedHours.contains(hour)) {
				logger.warn("Market could not be properly cleared in " + Date.getYearDayDate() + "/"
						+ hour + ". Demand " + priceCurvePoints.get(end).getAskVolumeMaximum()
						+ " Supply " + priceCurvePoints.get(end).getSellVolumeMaximum());
			}

			// Last price is 3000 so take last point -1
			temporaryPrices.set(hour, temporaryPriceFunction.get(hour).get(end - 1).getPrice());
			// Last volume
			temporaryVolumes.set(hour, priceCurvePoints.get(end).getAskVolumeMaximum());

			unclearedHours.add(hour);

			return;
		}

		// Find middle x value, assume that curves are to straight lines with
		// one line made out of (x1,y1)-(x2,y2) and the other (x3,y3)-(x4,y4).
		// Intersection can be calculated via
		// \frac{(x1 y2-y1 x2)(x3-x4)-(x1-x2)(x3 y4-y3
		// x4)}{(x1-x2)(y3-y4)-(y1-y2)(x3-x4)}
		// , but since x1=x3 and x2=x4 equation can be simplified to
		// (x1(y4-y2)+x2y1-x2y3)/(y1-y2-y3+y4)
		final double x1 = start;
		final double x2 = end;
		final double y1 = priceCurvePoints.get(start).getSellVolumeMaximum();
		final double y2 = priceCurvePoints.get(end).getSellVolumeMinimum();
		final double y3 = priceCurvePoints.get(start).getAskVolumeMinimum();
		final double y4 = priceCurvePoints.get(end).getAskVolumeMaximum();

		int middle = (int) ((((x1 * (y4 - y2)) + (x2 * y1)) - (x2 * y3)) / ((y1 - y2 - y3) + y4));

		// check if value of middle is correct, if only two values are left
		// middle is a
		// negative number
		// start==end if only one number is left
		if (start == end) {
		} else if (middle == start) {
			middle++;
		} else if (middle == end) {
			middle--;
		} else if ((middle < start) || (middle > end)) {
			middle = (start + end) / 2;
		}
		if (middle < 0) {
			logger.error("Market can not be cleared.");
			return;
		}
		priceCurvePoints.put(middle, temporaryPriceFunction.get(hour).get(middle));

		boolean intersectionFound = false;
		for (final int index : priceCurvePoints.keySet()) {
			final PriceCurvePoint priceCurvePoint = priceCurvePoints.get(index);
			intersectionFound = findMarketIntersection(hour, index, priceCurvePoint);
			if (intersectionFound) {
				break;
			}
		}

		// Check interval where intersection lies. First
		// interval if in the start there is more demand than offer and
		// in the middle there is less demand than offer else second interval.
		if (!intersectionFound) {
			final boolean firstIntervall = (priceCurvePoints.get(start)
					.getSellVolumeMaximum() <= priceCurvePoints.get(start).getAskVolumeMinimum())
					&& (priceCurvePoints.get(middle).getSellVolumeMaximum() >= priceCurvePoints
							.get(middle).getAskVolumeMinimum());
			if (firstIntervall) {
				// start, middle have already been checked
				findMarketOutcomesHourly(hour, start + 1, middle - 1);
			} else {
				// middle, end have already been checked
				findMarketOutcomesHourly(hour, middle + 1, end - 1);
			}
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
	private void findTypeVolumes(Set<TraderType> traderTypes) {

		// Init
		volumeRequested = new HashMap<>();
		volumeUnaccepted = new HashMap<>();

		for (final TraderType traderType : traderTypes) {
			volumeRequested.put(traderType, new HashMap<>());
			volumeUnaccepted.put(traderType, new HashMap<>());
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				volumeRequested.get(traderType).put(hour, new HashMap<>());
				volumeUnaccepted.get(traderType).put(hour, new HashMap<>());
				for (final BidType bidType : BidType.values()) {
					volumeRequested.get(traderType).get(hour).put(bidType, 0f);
					volumeUnaccepted.get(traderType).get(hour).put(bidType, 0f);
				}
			}
		}

		for (final TraderType traderType : traderTypes) {
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				for (final BidType bidType : BidType.values()) {
					volumeRequested.get(traderType).get(hour).put(bidType, 0f);
					volumeUnaccepted.get(traderType).get(hour).put(bidType, 0f);
				}
			}
		}

		if (bidPointsDayTraderType == null) {
			bidPointsDayTraderType = determineTraderType(bidPointsDay);
		}

		for (final TraderType traderType : traderTypes) {
			for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
				for (final Bid bidPoint : bidPointsDayTraderType.get(traderType).get(hourOfDay)) {

					final float volumeRequestedBidPoint = bidPoint.getVolume();
					final float volumeUnacceptedBidPoint = volumeRequestedBidPoint
							- temporaryAcceptedVolumes.get(bidPoint.getIdentifier());

					final BidType bidType = bidPoint.getBidType();

					volumeRequested.get(traderType).get(hourOfDay).put(bidType,
							volumeRequested.get(traderType).get(hourOfDay).get(bidType)
									+ volumeRequestedBidPoint);
					volumeUnaccepted.get(traderType).get(hourOfDay).put(bidType,
							volumeUnaccepted.get(traderType).get(hourOfDay).get(bidType)
									+ volumeUnacceptedBidPoint);

				}
			}

		}

	}

	/** Set temporary prices for the whole day */
	private void initializePriceFunction() {
		temporaryPriceFunction.clear();
		for (int hour = 0; hour < temporaryBids.size(); hour++) {
			temporaryPriceFunction.add(new ArrayList<PriceCurvePoint>());
		}
	}

	/**
	 * Writes all bids from {@link #bidPointsDay} into {@link #temporaryBids}
	 * (shallow copy). Also, make hourly bids out of {@link #blockBidsDay} and
	 * writes them into {@link #temporaryBids}
	 */
	private void makeTemporaryHourlyBids() {

		final List<List<Bid>> copybidPointList = new ArrayList<>(bidPointsDay.size());
		for (final Integer hour : bidPointsDay.keySet()) {
			copybidPointList.add(new ArrayList<Bid>(bidPointsDay.get(hour).size()));
			for (final Bid bidPoint : bidPointsDay.get(hour)) {
				copybidPointList.get(hour).add(bidPoint);
			}
		}
		temporaryBids = copybidPointList;

		if (blockBidsDay != null) {
			final ListIterator<BlockBidPower> iterator = blockBidsDay.listIterator();
			while (iterator.hasNext()) {
				final BlockBidPower blockBid = iterator.next();
				// If BlockBid is still in the market, add to ask or sell list
				if (blockBid.isAccepted()) {
					for (int hour = blockBid.getStart(); hour <= blockBid.getEnd(); hour++) {
						temporaryBids.get(hour)
								.add(new HourlyBidPower.Builder(blockBid.getVolume(),
										blockBid.getPrice(), hour, blockBid.getBidType(),
										marketArea).traderType(blockBid.getTraderType())
												.identifier(blockBid.getIdentifier()).build());
					}
				}
			}
		}
	}

	/**
	 * Pre sort hourly bids so that different bids are considered first when
	 * determining volume e.g. first regular
	 */
	private void preSortHourlyBids() {

		// SELL bids
		// Lowest price first
		final Comparator<Bid> compPriceSell = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
				b2.getPrice());
		// Prefer other sellers than prefer
		// renewable/exchange over or supply
		final Comparator<Bid> compTypeSell = (Bid b1, Bid b2) -> {

			// supply has lower priority than renewables/exchange ...
			if ((b1.getTraderType() == TraderType.SUPPLY)
					&& (b2.getTraderType() != TraderType.SUPPLY)) {
				return 1;
			} else if ((b1.getTraderType() != TraderType.SUPPLY)
					&& (b2.getTraderType() == TraderType.SUPPLY)) {
				return -1;
			} else {
				return 0;
			}
		};
		// Highest volume first
		final Comparator<Bid> compVolumeSell = (Bid b1, Bid b2) -> -1
				* Float.compare(b1.getVolume(), b2.getVolume());
		for (final Integer hourOfDay : bidPointsDay.keySet()) {
			Collections.sort(bidPointsDay.get(hourOfDay),
					compPriceSell.thenComparing(compTypeSell).thenComparing(compVolumeSell));
		}

		// Initialize
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			bidPointsDayType.put(hourOfDay, new HashMap<>());
			bidPointsDayType.get(hourOfDay).put(BidType.ASK, new ArrayList<>());
			bidPointsDayType.get(hourOfDay).put(BidType.SELL, new ArrayList<>());
			for (final Bid bidPoint : bidPointsDay.get(hourOfDay)) {
				bidPointsDayType.get(hourOfDay).get(bidPoint.getBidType()).add(bidPoint);
			}
		}
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			Collections.sort(bidPointsDayType.get(hourOfDay).get(BidType.SELL),
					compPriceSell.thenComparing(compTypeSell).thenComparing(compVolumeSell));
		}

		// ASK bids
		// Highest price first
		final Comparator<Bid> compPriceAsk = (Bid b1, Bid b2) -> -1
				* Float.compare(b1.getPrice(), b2.getPrice());
		// Highest volume first
		final Comparator<Bid> compVolumeAsk = (Bid b1, Bid b2) -> -1
				* Float.compare(b1.getVolume(), b2.getVolume());
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
			Collections.sort(bidPointsDayType.get(hourOfDay).get(BidType.ASK),
					compPriceAsk.thenComparing(compVolumeAsk));
		}

	}

	/**
	 * Sets <code>true</code> for the acceptance of all bids in
	 * {@link #blockBidsDay}.
	 */
	private void setAllBlockBidsInMarket() {
		for (final BlockBidPower blockBid : blockBidsDay) {

			if (blockBid.getBidType() == BidType.ASK) {
				logger.error("Not yet fully implemented!");
			}

			blockBid.setVolumeAccepted(blockBid.getVolume());
		}
	}

	/**
	 * To easily access bids via identifier
	 */
	private void setBidsMap() {

		// add hour bids
		for (final Integer hourOfDay : bidPointsDay.keySet()) {
			allBidsMap.put(hourOfDay, new HashMap<Integer, Bid>());
			for (final Bid bidPoint : bidPointsDay.get(hourOfDay)) {
				allBidsMap.get(hourOfDay).put(bidPoint.getIdentifier(), bidPoint);
			}
		}

		// add hour bids
		for (final BlockBidPower blockBid : blockBidsDay) {
			for (int blockBidHour = blockBid.getStart(); blockBidHour <= blockBid
					.getEnd(); blockBidHour++) {
				allBidsMap.get(blockBidHour).put(blockBid.getIdentifier(), blockBid);
			}
		}
	}

	/**
	 * Set the volume for each bid and calculates the total market volume. Bids
	 * that come first in order are considered first. If the bid list are
	 * ordered via {@link #sortDailyBids()} .
	 */
	private void setBidVolumes(boolean finalResults) {

		if (temporaryAcceptedVolumes == null) {
			temporaryAcceptedVolumes = new HashMap<>();
		}

		final List<Float> remainingAskVolume = new ArrayList<>(temporaryVolumes);
		final List<Float> remainingSellVolume = new ArrayList<>(temporaryVolumes);
		calculateBlockBidVolumes(remainingAskVolume, remainingSellVolume, finalResults);
		calculateHourlyBidVolumes(remainingAskVolume, remainingSellVolume, finalResults);
	}

	/**
	 * Set a unique identifier for each hourly and block bid.
	 */
	private void setIdentifier() {
		int identifier = 0;

		for (final List<Bid> bidList : bidPointsDay.values()) {
			for (final Bid bidPoint : bidList) {
				bidPoint.setIdentfier(identifier++);
			}
			// So that hourly bids are more easily identified start with next
			// thousand
			identifier = (identifier + 1000) - (identifier % 1000);
		}

		// So that hourly bids are more easily identified start with next
		// thousand
		identifier = (identifier + 100000) - (identifier % 100000);
		for (final BlockBidPower blockBid : blockBidsDay) {
			blockBid.setIdentfier(identifier++);
		}
	}

	private void setStartCosts() {
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			final PriceCurvePoint point = outcomes.get(hour);

			// check for empty values
			final Set<Integer> sell;
			if (point != null) {
				sell = point.getSellPoints();
			} else {
				sell = new HashSet<>();
			}

			// set costs
			if (sell.isEmpty()) {
				// If sell is empty, meaning that ask Bids are setting the price
				// or market could not be cleared
				clearingStartupCosts.add(Float.NaN);
			} else {
				// Theoretically several bids can set the price (but only first
				// is regarded here) (it is pretty unlikely that bids with
				// different start-up costs will have the exact same
				// price)
				final int identifier = sell.iterator().next();
				final float startupCost = ((PowerBid) allBidsMap.get(hour).get(identifier))
						.getStartupCosts();
				clearingStartupCosts.add(startupCost);
			}
		}

	}

	private void setVolumesCapacityCertificateMarket(List<Bid> bidPoints) {
		final List<Float> remainingVolumeAsk = new ArrayList<>(temporaryVolumes);
		final List<Float> remainingVolumeSell = new ArrayList<>(temporaryVolumes);
		for (final Bid bidPoint : bidPoints) {

			if (remainingVolumeSell.get(0) > 0 && bidPoint.getBidType() == BidType.SELL) {

				final float volume = Math.min(bidPoint.getVolume(), remainingVolumeSell.get(0));

				if ((volume > 0.1) && (bidPoint.getPrice() > temporaryPrices.get(0))) {
					logger.error(
							"This should not happen! Clearing price should be higher than bid price!");
				}
				remainingVolumeSell.set(0, remainingVolumeSell.get(0) - volume);
				bidPoint.setVolumeAccepted(volume);
			}
			if (remainingVolumeAsk.get(0) > 0 && bidPoint.getBidType() == BidType.ASK) {

				final float volume = Math.min(bidPoint.getVolume(), remainingVolumeAsk.get(0));

				if ((volume > 0.1) && (bidPoint.getPrice() > temporaryPrices.get(0))) {
					logger.error(
							"This should not happen! Clearing price should be higher than bid price!");
				}
				remainingVolumeAsk.set(0, remainingVolumeAsk.get(0) - volume);
				bidPoint.setVolumeAccepted(volume);
			}

		}
	}

	/**
	 * Sorts supply and demand bids according to their bidding price (ascending)
	 * and volume where price is identical.
	 *
	 * @see markets.bids.power.HourlyBidPower#compareTo(Object) compareTo
	 */
	private void sortBids(List<Bid> list) {
		// First: Sort by lowest price
		final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
				b2.getPrice());
		// Second: Sort by highest volume
		final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
				* Float.compare(b1.getVolume(), b2.getVolume());
		Collections.sort(list, compPrice.thenComparing(compVolume));
	}

	/**
	 * Sorts all supply and demand bids in {@link #bidPointsDay} according to
	 * their bidding price (ascending) and volume where price is identical.
	 *
	 * @see markets.bids.power.HourlyBidPower#compareTo(Object) compareTo
	 */
	private void sortDailyBids() {
		for (final List<Bid> bidList : bidPointsDay.values()) {
			sortBids(bidList);
		}
	}

	/**
	 * Sorts supply and demand bids according to their bidding price (ascending)
	 * and volume where price is identical.
	 *
	 * @see markets.bids.power.HourlyBidPower#compareTo(Object) compareTo
	 */
	private void sortTemporaryBids() {
		for (final List<Bid> temporaryBid : temporaryBids) {
			// First: Sort by lowest price
			final Comparator<Bid> compPrice = (Bid b1, Bid b2) -> Float.compare(b1.getPrice(),
					b2.getPrice());
			// Second: Sort by highest volume
			final Comparator<Bid> compVolume = (Bid b1, Bid b2) -> -1
					* Float.compare(b1.getVolume(), b2.getVolume());
			Collections.sort(temporaryBid, compPrice.thenComparing(compVolume));
		}
	}

	/**
	 * Write ask volumes for each price in <code>hourlyPrices</code> based on
	 * <code>temporaryBids</code>
	 */
	private void writeTemporaryAskVolumes(int hour, Set<Float> hourlyPrices) {

		float askVolumeCurrent = 0;
		float askVolumeLast = 0;
		final Set<Integer> askSet = new HashSet<>();
		int hourlyPriceIndex = 0;
		int bidCounter = temporaryBids.get(hour).size();

		// Copy unique prices to an array in order to reversely iterate through
		// that array
		final Float[] hourlyPricesArray = hourlyPrices.toArray(new Float[hourlyPrices.size()]);

		for (hourlyPriceIndex = hourlyPricesArray.length
				- 1; hourlyPriceIndex >= 0; hourlyPriceIndex--) {
			final float currentPrice = hourlyPricesArray[hourlyPriceIndex];
			for (final ListIterator<Bid> bidIterator = temporaryBids.get(hour)
					.listIterator(bidCounter); bidIterator.hasPrevious();) {
				final Bid bidPoint = bidIterator.previous();
				bidCounter--;

				// only asking bids
				if (bidPoint.getType() != BidType.ASK) {
					continue;
				}

				// price add volume until ask prices are higher than
				// current market price
				if (bidPoint.getPrice() >= currentPrice) {
					askVolumeCurrent += bidPoint.getVolume();
					askSet.add(bidPoint.getIdentifier());
				}
				// consider bid for next price
				else {
					bidCounter++;
					break;
				}
			}
			// Update ask value for current price
			final PriceCurvePoint temp = temporaryPriceFunction.get(hour).get(hourlyPriceIndex);
			temp.setAskPoints(askSet);
			temp.setAskVolumeMinimum(askVolumeLast);
			temp.setAskVolumeMaximum(askVolumeCurrent);
			askVolumeLast = askVolumeCurrent;

		}
	}

	/**
	 * Write sell volumes for each price in <code>hourlyPrices</code> based on
	 * <code>temporaryBids</code>
	 */
	private void writeTemporarySellVolumes(int hour, Set<Float> hourlyPrices) {
		float sellVolumeLast = 0;
		float sellVolumeCurrent = 0;
		int hourlyPriceIndex = 0;
		int bidCounter = 0;
		ListIterator<Bid> bidIterator = temporaryBids.get(hour).listIterator();
		// For all prices
		for (final float price : hourlyPrices) {
			final float currentPrice = price;
			final Set<Integer> sellSet = new HashSet<>();
			while (bidIterator.hasNext()) {
				final Bid bidPoint = bidIterator.next();
				bidCounter++;
				// only selling bids
				if (bidPoint.getType() != BidType.SELL) {
					continue;
				}
				// price add volume until bid prices are smaller than
				// current market
				if (bidPoint.getPrice() <= currentPrice) {
					sellVolumeCurrent += bidPoint.getVolume();
					sellSet.add(bidPoint.getIdentifier());
				} else {
					// consider bid for next price
					bidIterator = temporaryBids.get(hour).listIterator(--bidCounter);
					break;
				}
			}
			// Update sell value for current price
			final PriceCurvePoint temp = temporaryPriceFunction.get(hour).get(hourlyPriceIndex);
			temp.setSellPoints(sellSet);
			temp.setSellVolumeMinimum(Math.abs(sellVolumeLast));
			temp.setSellVolumeMaximum(Math.abs(sellVolumeCurrent));
			sellVolumeLast = sellVolumeCurrent;
			hourlyPriceIndex++;
		}
	}

}
