package markets.trader.spot.supply;

import static simulations.scheduling.Date.DAYS_PER_YEAR;
import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.power.BlockBidPower;
import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.operator.spot.tools.MarginalBid;
import markets.trader.Trader;
import markets.trader.TraderType;
import markets.trader.future.tools.PriceForecastFuture;
import markets.trader.spot.DayAheadBlockTrader;
import markets.trader.spot.DayAheadTrader;
import markets.trader.spot.supply.tools.AssignPowerPlants;
import markets.trader.spot.supply.tools.BiddingAlgorithm;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.Plant;
import supply.scenarios.ScenarioList;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerCSV;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/**
 * @since 31.03.2005
 * @author Massimo Genoese
 */
public class SupplyTrader extends Trader implements DayAheadTrader, DayAheadBlockTrader {

	private List<Float> dayAheadPriceForecast;

	public List<Float> getDayAheadPriceForecast() {
		return dayAheadPriceForecast;
	}

	/** Threshold for rounding errors */
	private static final float EPSILON = 0.00001f;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(SupplyTrader.class.getName());

	/**
	 * Contains the available plants from generator that can be accessed of
	 * their unit identifier.
	 */
	private Map<Integer, Plant> availablePlantsMap = new HashMap<>();

	private List<Plant> availablePlantsOwn = new ArrayList<>();

	private Map<Integer, Map<Integer, String>> bidComments = new HashMap<>();
	private List<Float> bidPrices = new ArrayList<>();
	private List<Float> bidVolumes = new ArrayList<>();
	private List<BlockBidPower> blockBids = new ArrayList<>();
	private List<Float> dayAheadDemand;
	/**
	 * The demand that could not be satisfied by own plants.
	 */
	private Map<Integer, Float> demandRemaining;
	private Map<Integer, Float> exchangeForecast = new HashMap<>();

	private float[] indivTotalHourlyAvailCap = new float[HOURS_PER_DAY];
	private int logIdBids = -1;
	private boolean logInitialized;

	private float[] myprice;
	private float[] myvolume;
	private float[] profits = new float[24 * DAYS_PER_YEAR];
	/**
	 * Random number generator. Each agent needs its own, in order to safely
	 * work with threads, since otherwise not the same outcome could be expected
	 * if the order of calls to random differs due to the thread calling.
	 */
	private Random random;

	protected boolean fixedCostsMarkUp = false;
	/**
	 * The id number of this bidder, should be one less than the field "owner"
	 * in the DB.
	 */
	protected int identity;
	protected float minCost = 0;
	protected float threshold = 0;
	protected float thresholdUp = 0;
	protected int totalcap = 0;
	/** Price Forecast for determine DayAheadBidsHourly */
	float[] priceForecastDayAhead;

	/**
	 * Constructs a SupplyBidder without passing name and using name from
	 * Database.
	 */
	public SupplyTrader() {
		super();
	}

	/**
	 * Constructs a SupplyBidder with the given <code>name</code>.
	 */
	public SupplyTrader(String name) {
		super(name);
	}

	public SupplyTrader(String name, int identity) {
		super(name);
		// for Reference supply bidder counter has stay the same
		this.identity = identity;
	}

	/**
	 * Calculate hourly and block bids based on a price forecast. Add a mark-up
	 * factor all bids.
	 */
	private void calculateDayAheadBidsNew() {

		// Initialize log file yearly
		if (Settings.isLogBids() && ((logIdBids == -1) || Date.isFirstDayOfYear())) {
			final String logFile = marketArea.getInitialsUnderscore() + "Bids_" + Date.getYear()
					+ "_" + getName();
			final String description = "All trades";
			final List<ColumnHeader> titleLine = new ArrayList<>();
			titleLine.add(new ColumnHeader("hourOfYear", Unit.HOUR));

			final List<Plant> powerPlants = marketArea.getGenerator(getName()).getPowerPlantsList();

			// Sort list by unit id
			Collections.sort(powerPlants,
					(Plant o1, Plant o2) -> Integer.compare(o1.getUnitID(), o2.getUnitID()));
			powerPlants.forEach(powerPlant -> {
				titleLine.add(new ColumnHeader("" + powerPlant.getUnitID(), Unit.NONE));
				titleLine.add(new ColumnHeader("startCosts", Unit.ENERGY_PRICE));
			});
			logIdBids = LoggerXLSX.newLogObject(Folder.DAY_AHEAD_PRICES, logFile, description,
					titleLine, marketArea.getIdentityAndNameLong(), Frequency.HOURLY);
		}

		// Initialize hourly bids
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			hourlyDayAheadPowerBids.add(new DayAheadHourlyBid(hour, TraderType.SUPPLY));
		}

		final BiddingAlgorithm bidding;
		final List<Plant> plantsAll = marketArea.getGenerator(getName()).getPowerPlantsList();

		final List<ScenarioList<Float>> forecastPrices = calculateDayAheadPriceForecast();
		dayAheadPriceForecast = forecastPrices.get(0).getValues();

		bidding = new BiddingAlgorithm(forecastPrices, availablePlantsOwn, marketArea, logIdBids,
				plantsAll, random);
		bidding.makeBids();

		// add hourly bids to bid lists
		final Map<Integer, List<HourlyBidPower>> bidPoints = bidding.getHourlyBids();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			for (final HourlyBidPower bidPoint : bidPoints.get(hourOfDay)) {

				hourlyDayAheadPowerBids.get(bidPoint.getHour()).addBidPoint(bidPoint);
			}
		}

		// Remove hourly bids if empty
		final Iterator<DayAheadHourlyBid> iterator = hourlyDayAheadPowerBids.iterator();
		while (iterator.hasNext()) {
			final DayAheadHourlyBid bid = iterator.next();
			if (bid.getBidPoints().size() == 0) {
				iterator.remove();
			}
		}

		// add blocks bids to bid lists
		for (final BlockBidPower blockBidsPlant : bidding.getBlockBids()) {
			blockBids.add(blockBidsPlant);
		}
	}

	private List<ScenarioList<Float>> calculateDayAheadPriceForecast() {
		List<Float> forecastPricesAsList = new ArrayList<>();
		List<ScenarioList<Float>> forecastPrices = new ArrayList<>();
		final int forecastLength = Date.isLastDayOfYear() ? 24 : 36;

		for (int hourOfForecast = 0; hourOfForecast < forecastLength; hourOfForecast++) {
			int dayOfYear = (hourOfForecast < Date.HOURS_PER_DAY)
					? Date.getDayOfYear()
					: (Date.getDayOfYear() + 1);
			int hourOfDay = (hourOfForecast < Date.HOURS_PER_DAY)
					? hourOfForecast
					: (hourOfForecast - Date.HOURS_PER_DAY);
			forecastPricesAsList.add(PriceForecastFuture.getForwardPriceListCurrentYear()
					.get(marketArea).get(Date.getHourOfYearFromHourOfDay(dayOfYear, hourOfDay)));
		}

		forecastPrices.add(new ScenarioList<>(1, "", forecastPricesAsList, 1f));

		return forecastPrices;
	}

	/**
	 * Calculate the total electricity produced by the power plants
	 * <P>
	 */
	private void calculateElectricityProduced() {
		for (final Plant plant : availablePlantsOwn) {
			for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
				marketArea.getElectricityProduction().addElectricityDaily(hourOfDay,
						plant.getElectricityProductionToday(hourOfDay), plant.getFuelName());
			}
		}
	}

	/**
	 * Calculate the daily carbon emissions and add amount to plant and total
	 * statistics.
	 * <P>
	 */
	private void calculateEmissions() {
		for (final Plant plant : availablePlantsOwn) {
			plant.increaseCarbonEmissions();
		}
	}

	@Override
	public List<BlockBidPower> callForBidsDayAheadBlockBids() {
		return blockBids;
	}

	/** calculates bids for the spot power market */
	@Override
	public List<DayAheadHourlyBid> callForBidsDayAheadHourly() {

		try {
			// actualize plant data from generator and clear bids
			getPlantDataFromGenerator();
			hourlyDayAheadPowerBids.clear();
			bidComments.clear();
			blockBids.clear();

			// Initialize hourly bids
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

				bidComments.put(hour, new HashMap<>());
			}

			// Create cumulated bids
			if (Settings.isCumulatedBids()) {
				for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {
					hourlyDayAheadPowerBids.add(createCumulatedHBid(hourOfDay));
				}
			}
			// Make single bid per generation unit
			else {

				calculateDayAheadBidsNew();
			}

		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}

		return hourlyDayAheadPowerBids;
	}

	/**
	 * If a new day begins, runnings hours of today become the values for
	 * yesterday.
	 */
	private void countStartUps() {
		for (final Plant powerPlant : availablePlantsOwn) {
			powerPlant.countStartUps();
		}
	}

	/**
	 *
	 * generates one (cumulated) hourly bid from plant data. generate a bid
	 * curve with variable costs
	 *
	 * @return Generated hourly bid.
	 */
	private DayAheadHourlyBid createCumulatedHBid(int hour) {

		bidPrices.clear();
		bidVolumes.clear();
		bidPrices.add(Float.valueOf(getMinimumDayAheadPrice()));
		bidVolumes.add(Float.valueOf(0));
		bidPrices.add(Float.valueOf(myprice[0]));
		bidVolumes.add(Float.valueOf(myvolume[0]));
		int temp = 0;
		for (int i = 1; i < myprice.length; i++) {
			if (myprice[i - 1] == myprice[i]) {
				// price is equal to last, increase temp. sum
				temp += myvolume[i - 1];
			} else {
				// higher price found, add an element to the list
				bidPrices.add(Float.valueOf(myprice[i]));
				temp += myvolume[i - 1];
				bidVolumes.add(bidVolumes.size() - 1, Float.valueOf(temp));
				temp = 0;
			}
		}

		// generate Hbid
		return new DayAheadHourlyBid(hour, bidPrices, bidVolumes, TraderType.SUPPLY, marketArea);
	}

	/**
	 * Determine whether the current bid (plantIndex) is in the current
	 * hourOfDay the marginal bid. If it is save the marginal bid in the
	 * corresponding data object.
	 */
	private void determineMarginalBid(int hourOfDay, float marketClearingPrice,
			HourlyBidPower bidPoint) {

		// Set actual marginal bid which is determined by comparing
		// the market clearing price and bid price
		final float priceDifference = Math.abs((bidPoint.getPrice() - marketClearingPrice));
		if (priceDifference < EPSILON) {
			// Create new instance of MarginalBid
			final MarginalBid marginalBid = new MarginalBid(bidPoint);
			// Set in results object
			marketArea.getElectricityResultsDayAhead().setMarginalBidHourOfDay(hourOfDay,
					marginalBid);
		}
	}

	/**
	 * the result of the auctions is available here this method is called after
	 * the auctions
	 */
	@Override
	public void evaluateResultsDayAhead() {
		try {
			// Calculate assigned volumes from bids into hourly demand
			dayAheadDemand = new ArrayList<>(Collections.nCopies(HOURS_PER_DAY, 0f));
			final Iterator<DayAheadHourlyBid> iterator = hourlyDayAheadPowerBids.iterator();
			while (iterator.hasNext()) {
				final DayAheadHourlyBid bid = iterator.next();
				dayAheadDemand.set(bid.getHour(), Math.abs(bid.getAssignedVolume()));

			}
			evaluateResultsDayAheadNew();

		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void evaluateResultsDayAheadNew() {
		try {
			for (final BlockBidPower blockBid : blockBids) {
				if (blockBid.isAccepted()) {
					for (int hour = blockBid.getStart(); hour < (blockBid.getStart()
							+ blockBid.getLength()); hour++) {
						dayAheadDemand.set(hour,
								dayAheadDemand.get(hour) + Math.abs(blockBid.getVolume()));

					}
				}
			}

			// Learn from profits
			final AssignPowerPlants assignment = new AssignPowerPlants(
					new ArrayList<>(availablePlantsOwn), dayAheadDemand, marketArea);
			assignment.assignPlants();
			demandRemaining = assignment.getDemandRemaining();

			// count start ups
			countStartUps();

			// determine marginal bid
			for (final DayAheadHourlyBid dayAheadHourlyBid : hourlyDayAheadPowerBids) {
				final int hourOfDay = dayAheadHourlyBid.getHour();
				Collections.sort(dayAheadHourlyBid.getBidPoints());
				for (final HourlyBidPower bidPoint : dayAheadHourlyBid.getBidPoints()) {
					// If bid is (partially) accepted
					if (Math.abs(bidPoint.getVolumeAccepted()) > 0) {
						final float marketClearingPrice = dayAheadHourlyBid
								.getMarketClearingPrice();
						/* Determine marginal bid */
						determineMarginalBid(hourOfDay, marketClearingPrice, bidPoint);

					}
				}
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Method called in SupplyData by getAllPlantsInActualOrder(int hourOfDay)
	 * which is called in TransportLoadManager to determine marginal emissions
	 * EV specific emissions.
	 */
	public Map<Plant, Float> getActivePlantsInclCosts(int hourOfDay) {
		final Map<Plant, Float> activePlants = new HashMap<>();

		for (final DayAheadHourlyBid dayAheadHourlyBid : hourlyDayAheadPowerBids) {
			for (final HourlyBidPower bidPoint : dayAheadHourlyBid.getBidPoints()) {
				if ((dayAheadHourlyBid.getHour() == hourOfDay)
						&& (bidPoint.getVolumeAccepted() != 0)) {
					final int plantID = bidPoint.getPlant().getUnitID();
					activePlants.put(availablePlantsMap.get(plantID), bidPoint.getPrice());
				}
			}
		}
		return activePlants;
	}

	/** @return the identification of this bidder */
	public int getID() {
		return identity;
	}

	public float[] getIndivTotalhourlyavailcap() {
		return indivTotalHourlyAvailCap;
	}

	/**
	 * Get power plants data from generator
	 */
	private void getPlantDataFromGenerator() {

		marketArea.getGenerator(getName()).priceForecastNextDay();
		myprice = new float[marketArea.getGenerator(getName()).getAvailablePlants().size() + 1];
		myvolume = new float[marketArea.getGenerator(getName()).getAvailablePlants().size() + 1];

		// get plants from generator
		myprice = marketArea.getGenerator(getName()).getVarcosts();
		myvolume = marketArea.getGenerator(getName()).getVolumes();

		// put a dummy point at the end
		myprice[marketArea.getGenerator(getName()).getAvailablePlants().size()] = -1;
		myvolume[marketArea.getGenerator(getName()).getAvailablePlants().size()] = 0;

		availablePlantsOwn = marketArea.getGenerator(getName()).getAvailablePlants();

		for (final Plant powerPlant : availablePlantsOwn) {
			availablePlantsMap.put(powerPlant.getUnitID(), powerPlant);
		}

	}

	public float[] getProfits() {
		return profits;
	}

	// this function will be called by PowerMarkets when building agents
	@Override
	public void initialize() {
		// Id +1 = index
		identity = marketArea.getCompanyName().getNumber(getName()) - 1;

		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ SupplyTrader.class.getSimpleName() + " " + getName() + " (" + identity + ")");

		marketArea.addSupplyTrader(this);

		if (logInitialized) {
			logInitialized = true;
			logInitialize();
			logInitialize2();
		}
	}

	private void logInitialize() {
		final String fileName = marketArea.getInitialsUnderscore() + "SupplyBidder" + Date.getYear()
				+ Settings.LOG_FILE_SUFFIX_CSV;

		final String description = "Describes the operations of SpotBidder";
		final String titleLine = "hour;" + "price;" + "volume;" + "income;";
		final String unitLine = "h;" + "Euro/MWh;" + "MWh;" + "Euro;";
		LoggerCSV.newLogObject(Folder.GENERATOR, fileName, description, titleLine, unitLine,
				marketArea.getIdentityAndNameLong());
	}

	private int logInitialize2() {

		final String fileName = marketArea.getInitialsUnderscore() + "CO2Bidder" + Date.getYear()
				+ Settings.LOG_FILE_SUFFIX_CSV;

		final String description = "Describes the operations of CO2Trader";
		final String titleLine = "day;" + "price;" + "volume;" + "income;emissions;emissionsgross";
		final String unitLine = "day;" + "Euro/t;" + "t;" + "Euro;";
		return LoggerCSV.newLogObject(Folder.GENERATOR, fileName, description, titleLine, unitLine,
				marketArea.getIdentityAndNameLong());
	}

	public void logProduction() {
		calculateElectricityProduced();
		countStartUps();
		calculateEmissions();
	}

	/**
	 * the result of the auctions is available here this method is called after
	 * the auctions
	 */
	public void performOutput() {

		/*
		 * in this case, variable costs make a bid curve
		 */
		if (Settings.isCumulatedBids()) {

			final float[] myBid = new float[hourlyDayAheadPowerBids.size()];
			for (int bidIndex = 0; bidIndex < hourlyDayAheadPowerBids.size(); bidIndex++) {
				myBid[bidIndex] = -hourlyDayAheadPowerBids.get(bidIndex).getAssignedVolume();
			}
			marketArea.getGenerator(getName()).schedulePlants(myBid,
					hourlyDayAheadPowerBids.get(0).getMarketClearingPrice());
		}

		/*
		 * plants are bidden, possible startUp and fixed costs markup bid points can be
		 * aggregated
		 */
		else {

			if (Settings.isAggregatePlantBid()) {
				/*
				 * plantBids have been aggregated compute profits and CO2-emissions the
				 * calculations here have been transferred to the evaluateResultsSpot methods
				 */
				// is done in afterAuctions
			}
			/*
			 * not aggregated
			 */
			else {

				int j = 0;

				for (final DayAheadHourlyBid hourlyDayAheadPowerBid : hourlyDayAheadPowerBids) {
					/**
					 * find which plant belongs to the bid
					 */
					if ((j < availablePlantsOwn.size()) && (hourlyDayAheadPowerBid
							.getPlantReference() == availablePlantsOwn.get(j).getUnitID())) {
						availablePlantsOwn.get(j).setThermalState(3);
					} else {
						j++;
					}
				}

			}
		}
	}

	public void setExchangeForecast(Map<Integer, Float> exchangeForecast) {
		this.exchangeForecast = exchangeForecast;
	}

}