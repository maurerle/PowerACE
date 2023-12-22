package supply;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.trader.spot.hydro.SeasonalStorageTrader;
import simulations.agent.Agent;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.StateStrategic;
import supply.powerplant.CostCap;
import supply.powerplant.Plant;

/**
 *
 * @since 31.03.2005
 * @author Massimo Genoese
 * 
 *
 */
public class Generator extends Agent {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Generator.class.getName());

	private float[] costs = null;
	private float gambleMargin = 0.3f;

	/**
	 * The id number of this bidder, should be one less than the field "owner"
	 * in the DB.
	 */
	protected int identity;
	protected float[] loadResidual = new float[24];
	private List<CostCap> longMeritOrder = new ArrayList<>();

	protected List<CostCap> meritOrder = new ArrayList<>();

	/** List of all power plants (before checking for availability) */
	private List<Plant> powerPlantsAll = new ArrayList<>();
	private List<Plant> powerPlantsAllSorted = new ArrayList<>();
	/** List of available plants */
	private List<Plant> powerPlantsAvailable = new ArrayList<>();
	private float[] priceForecastDayAhead = new float[24];
	// this is given to the Bidders..
	private float[] varcosts = null;
	private float[] volumes = null;

	private void calculateFullLoadHours() {
		for (final Plant powerPlant : powerPlantsAll) {
			powerPlant.setUtilisation(
					powerPlant.getElectricityProductionCurrentYear() / powerPlant.getNetCapacity());
		}
	}

	/**
	 * In this method a list of daily available power plants for the agent
	 * supplyBidder is provided
	 */
	public void determineDailyAvailablePlants() {

		determinePlantCosts();
		powerPlantsAvailable.clear();

		// needed for calculating with dates and in order to avoid to many
		// object constructions, initiate it only once here
		final LocalDate today = Date.getCurrentDateTime().toLocalDate();
		final int firstHourOfToday = Date.getFirstHourOfToday();

		// if Blackout, it is checked whether plants are available
		if (Settings.isCheckBlackout()) {

			for (final Plant powerPlant : powerPlantsAll) {
				/**
				 * If power plant is in operation and already available
				 * regarding its date, add to the available list of power plants
				 */
				if (powerPlant.isStillRunning(today)) {

					// Only available if some capacity is left to sell
					if (powerPlant.getCapacityUnusedExpected(firstHourOfToday) <= 0) {
						logger.error("Why is no more capacity available!");
						continue;
					}

					// Plant is available if no expected outage occurs and no
					// unexpected outage. But when no intraday market is running
					// unexpected outages are not regarded.
					powerPlantsAvailable.add(powerPlant);

				}
			}
		} else {
			for (final Plant powerPlant : powerPlantsAll) {
				if (powerPlant.isStillRunning(today)) {
					powerPlantsAvailable.add(powerPlant);
				}
			}
		}

		// Blackouts are on daily basis, so for each hour of the day fill with
		// current value
		varcosts = new float[powerPlantsAvailable.size() + 1];
		volumes = new float[powerPlantsAvailable.size() + 1];
		for (int i = 0; i < powerPlantsAvailable.size(); i++) {
			varcosts[i] = powerPlantsAvailable.get(i).getCostsVar();
			volumes[i] = powerPlantsAvailable.get(i).getNetCapacity();
		}
	}

	/**
	 * Calculate the variable costs for each power plant.
	 */
	public void determinePlantCosts() {
		determinePlantCosts(Date.getDayOfYear());
	}

	/**
	 * Calculate the variable costs for each power plant.
	 */
	public void determinePlantCosts(int day) {
		powerPlantsAll.parallelStream().forEach((plant) -> plant.determineCostsVar(Date.getYear()));
		Collections.sort(powerPlantsAllSorted);
	}

	/**
	 * @return Returns the availablePlants.
	 */
	public List<Plant> getAvailablePlants() {
		return new ArrayList<>(powerPlantsAvailable);
	}

	/** Get merit order from GenerationData object */
	public void getMeritOrder() {
		meritOrder = marketArea.getGenerationData().getUnitsAggr();
		longMeritOrder = marketArea.getGenerationData().getActualUnits();
	}

	public List<Plant> getPowerPlantsList() {
		return powerPlantsAll;
	}

	public float[] getPriceForecastDayAhead() {
		return priceForecastDayAhead;
	}

	/**
	 * Determines the residual load to be met by thermal power plants based on
	 * "forecasts" for demand, exchange, renewables, pump storage and CHP
	 * electricity
	 */
	private void getThermalRestLoad() {
		final float[] pumpstorageLoad = new float[Date.HOURS_PER_DAY];

		float seasonalStorageCapacity = 0;
		for (final SeasonalStorageTrader trader : marketArea.getSeasonalStorageTraders()) {
			seasonalStorageCapacity += trader.getAvailableTurbineCapacity();

		}
		final int year = Date.getYear();
		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {

			/* Load data */
			final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
			// Demand load (including losses)
			final float demandLoadForecast = marketArea.getDemandData()
					.getHourlyDemandOfYear(hourOfYear);
			// Exchange
			final float exchangeForecast = marketArea.getExchange().getHourlyFlowForecast(year,
					hourOfYear);
			// Renewables load
			final float renewablesForecast = marketArea.getManagerRenewables()
					.getTotalRenewableLoad(hourOfYear);
			// PumpstorageLoad
			if (marketArea.getPumpStorageActiveTrading() == 3) {
				pumpstorageLoad[hourOfDay] = marketArea.getPumpStorage()
						.getDynamicPumpStorageProfile(year, hourOfYear);
			}

			/* Determine residual load */
			loadResidual[hourOfDay] = (((demandLoadForecast + exchangeForecast) - renewablesForecast
					- seasonalStorageCapacity) + pumpstorageLoad[hourOfDay]);
		}

	}

	public float[] getVarcosts() {
		return varcosts;
	}

	public float[] getVolumes() {
		return volumes;
	}

	@Override
	public void initialize() {
		// id from database - 1 = index
		identity = marketArea.getCompanyName().getNumber(getName()) - 1;

		logger.info(marketArea.getInitialsBrackets() + "Initialize "
				+ Generator.class.getSimpleName() + " " + getName() + " (" + identity + ")");

		/**
		 * clear arrayLists
		 */
		meritOrder.clear();
		longMeritOrder.clear();

	}

	private void initializeFixedCosts() {
		for (final Plant powerPlant : powerPlantsAll) {
			powerPlant.setCostsOperationMaintenanceFixed();
		}
	}

	public void initializeGenerator() {
		// Get power plants from SupplyData
		powerPlantsAll = marketArea.getSupplyData().getPowerPlantsAsList(Date.getYear(), getName(),
				Stream.of(StateStrategic.OPERATING).collect(Collectors.toSet()));
		powerPlantsAllSorted = new ArrayList<>(powerPlantsAll);
		initializeInvestmentPayment();
		initializeFixedCosts();
		initializePowerPlants();
	}

	private void initializeInvestmentPayment() {
		for (final Plant powerPlant : powerPlantsAll) {
			if (powerPlant.getInvestmentPayment() == 0) {
				powerPlant.setInvestmentPayment(marketArea.getGenerationData().getInvestmentPayment(
						powerPlant.getAvailableYear(), powerPlant.getFuelType(),
						powerPlant.getEnergyConversion()));
			}
		}
	}

	/** Initialize available capacity of all power plants */
	private void initializePowerPlants() {
		for (final Plant powerPlant : powerPlantsAll) {
			powerPlant.initializePowerPlant(marketArea);
		}
	}

	public void operationsEndYear() {
		calculateFullLoadHours();
	}

	public void priceForecastNextDay() {

		final float[] scarcity = new float[24];

		getMeritOrder();
		getThermalRestLoad();

		final float[] capacityAvailable = new float[24];
		final float[] capacityAvailableNet = new float[24];
		final float[] capacityAvailableReserve = new float[24];
		Arrays.fill(capacityAvailable, 0f);
		Arrays.fill(capacityAvailableNet, 0f);
		Arrays.fill(capacityAvailableReserve, 0f);
		final int firstHourOfToday = Date.getFirstHourOfToday();
		for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
			for (final Plant plant : marketArea.getSupplyData().getPowerPlantsAsList(Date.getYear(),
					Collections.singleton(StateStrategic.OPERATING))) {
				capacityAvailable[hour] += plant.getCapacityUnusedExpected(firstHourOfToday + hour);
			}
		}

		if (Date.getYear() >= 2010) {
			gambleMargin = 0.0f;
		}

		final float resReserveFactor = 0.4f;

		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			int indexLongMeritOrder = 0;

			// Loop merit order in order to determine expected marginal
			// plant
			while ((indexLongMeritOrder < (longMeritOrder.size() - 1))
					&& ((longMeritOrder.get(indexLongMeritOrder).getCumulatedNetCapacity()
							- loadResidual[hourOfDay]) <= 0)) {
				indexLongMeritOrder++;
			}

			if (longMeritOrder.isEmpty()) {
				logger.warn(marketArea.getInitialsBrackets() + "Error. j=" + indexLongMeritOrder
						+ ", longMeritOrder.size()-1=" + (longMeritOrder.size() - 1) + ".");
			}

			// Load exceeds expected available capacities
			if (longMeritOrder.isEmpty()
					|| (longMeritOrder.get(indexLongMeritOrder).getCumulatedNetCapacity()
							- loadResidual[hourOfDay]) <= 0) {

				if (loadResidual[hourOfDay] <= 0) {
					priceForecastDayAhead[hourOfDay] = marketArea.getRenewableTrader()
							.getDayAheadBiddingPrice();
				} else {
					priceForecastDayAhead[hourOfDay] = marketArea.getPriceForwardMaximum();
				}
				continue;
			}

			// Calculate scarcity for each hour, based on expected
			// availabilities
			scarcity[hourOfDay] = (capacityAvailable[hourOfDay] * resReserveFactor)
					/ loadResidual[hourOfDay];

			if (scarcity[hourOfDay] < 0) {
				scarcity[hourOfDay] = 10;
			}

			float lastprice = 0;
			float thisprice;
			float thisvol;
			float lastvol;

			if ((longMeritOrder.get(indexLongMeritOrder).getCumulatedNetCapacity()
					- loadResidual[hourOfDay]) == 0) {
				if (longMeritOrder.size() < indexLongMeritOrder) {
					priceForecastDayAhead[hourOfDay] = longMeritOrder.get(indexLongMeritOrder + 1)
							.getCostsVar();
				} else {
					priceForecastDayAhead[hourOfDay] = longMeritOrder.get(indexLongMeritOrder)
							.getCostsVar();
				}
			} else {
				if (Settings.isEexLike()) {
					lastprice = longMeritOrder.get(Math.max(0, indexLongMeritOrder - 1))
							.getCostsVar();
					lastvol = longMeritOrder.get(Math.max(0, indexLongMeritOrder - 1))
							.getCumulatedNetCapacity();
					thisvol = longMeritOrder.get(indexLongMeritOrder).getCumulatedNetCapacity();
					thisprice = longMeritOrder.get(indexLongMeritOrder).getCostsVar();
					priceForecastDayAhead[hourOfDay] = lastprice
							+ (((thisprice - lastprice) * (loadResidual[hourOfDay] - lastvol))
									/ (thisvol - lastvol));
				} else {
					priceForecastDayAhead[hourOfDay] = longMeritOrder.get(indexLongMeritOrder)
							.getCostsVar();
				}
			}
		}

	}

	public void schedulePlants(float[] myBid, float mcp) {
		final int[] sum = new int[24];
		final float[][] loadplant = new float[powerPlantsAllSorted.size()][24];
		costs = new float[24];

		for (int bidIndex = 0; bidIndex < myBid.length; bidIndex++) {
			for (final Plant powerPlant : powerPlantsAllSorted) {

				if ((sum[bidIndex] + powerPlant.getNetCapacity()) <= myBid[bidIndex]) {
					sum[bidIndex] += powerPlant.getNetCapacity();

					powerPlant.setThermalState(3); // hot
					loadplant[powerPlantsAllSorted.indexOf(powerPlant)][bidIndex] = powerPlant
							.getNetCapacity();
					costs[bidIndex] += powerPlant.getCostsVar() * powerPlant.getNetCapacity();
				} else if (sum[bidIndex] < myBid[bidIndex]) {
					// partly executed bid
					sum[bidIndex] += (int) myBid[bidIndex] - sum[bidIndex];
					powerPlant.setThermalState(3); // hot
					loadplant[powerPlantsAllSorted
							.indexOf(powerPlant)][bidIndex] = (int) myBid[bidIndex] - sum[bidIndex];
					costs[bidIndex] += powerPlant.getCostsVar()
							* loadplant[powerPlantsAll.indexOf(powerPlant)][bidIndex];
				} else {

					powerPlant.setThermalState(1); // cold

					break;
				}
			}
		}
	}

}