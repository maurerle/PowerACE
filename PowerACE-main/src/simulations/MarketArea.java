package simulations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.MarketAreaData;
import data.demand.Demand;
import data.exchange.Capacities;
import data.exchange.Flows;
import data.fuel.FuelPrices;
import data.other.CompanyName;
import data.powerplant.Availability;
import data.powerplant.GenerationData;
import data.powerplant.SupplyData;
import data.powerplant.costs.OperationMaintenanceCost;
import data.powerplant.costs.StartupCost;
import data.renewable.RenewableManager;
import data.storage.PumpStorage;
import markets.operator.Auction;
import markets.operator.spot.DayAheadMarketOperator;
import markets.operator.spot.MarketCouplingOperator;
import markets.trader.Trader;
import markets.trader.future.tools.ExchangeForecastFuture;
import markets.trader.future.tools.PriceForecastFuture;
import markets.trader.future.tools.StorageOperationForecastFutureRegression;
import markets.trader.spot.DayAheadTrader;
import markets.trader.spot.demand.DemandTrader;
import markets.trader.spot.hydro.PumpStorageTrader;
import markets.trader.spot.hydro.SeasonalStorageTrader;
import markets.trader.spot.other.ExchangeTrader;
import markets.trader.spot.renewable.RenewableTrader;
import markets.trader.spot.supply.SupplyTrader;
import results.investment.FutureMeritOrders;
import results.investment.FuturePrices;
import results.investment.NetValue;
import results.investment.NetValueExtremePrices;
import results.powerplant.AvailabilitiesPlants;
import results.powerplant.EmissionsCarbon;
import results.powerplant.PlantsDecommissioned;
import results.powerplant.ProductionElectricity;
import results.spot.Balance;
import results.spot.DayAhead;
import results.spot.RegularCallMarketLog;
import results.spot.SecurityOfSupply;
import results.spot.SecurityOfSupply.SecurityOfSupplyValue;
import simulations.agent.Agent;
import simulations.initialization.FileParser;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.Generator;
import supply.invest.DecommissionPlants;
import supply.invest.Investor;
import supply.invest.InvestorNetValue;
import supply.powerplant.PlantOption;
import tools.logging.Folder;
import tools.logging.LogData;
import tools.logging.LogFile;
import tools.logging.LogFile.Frequency;
import tools.logging.LogFile.LogDataColumn;
import tools.logging.LogFile.LogDataColumnSeveral;
import tools.math.Finance;
import tools.math.Finance.Payment;
import tools.math.Statistics;
import tools.types.MarketAreaType;

/**
 * Class to describe each market area. This class is called during model
 * building where market areas are constructed. The market area constructor in
 * turn calls to build market areas from file. Each market area instance
 * includes information about markets and agents as well as market area specific
 * data.
 *
 * @author PR
 * @since 02/2013
 *
 */
public class MarketArea implements Comparable<MarketArea> {

	/** Counter for market areas */
	private static int count;

	/** Counter for market areas in market coupling */
	private static int countMarketCoupling;

	/** Instance of logger */
	private static final Logger logger = LoggerFactory.getLogger(MarketArea.class.getName());

	protected static void resetCounter() {
		count = 1;
		countMarketCoupling = 1;
	}

	private FileParser afp;

	/**
	 * Instance of CapacitiesPlants class which calculates capacities of plants
	 */
	private AvailabilitiesPlants availabilitiesPlants;

	private Availability availabilityFactors;

	/**
	 * Name of availability scenario which is selected from a "availability"
	 * table in a power plant database
	 */
	private String availabilityScenario;

	/**
	 * (old) ID of availability scenario which is selected from a "availability"
	 * table in the power plants database
	 */
	private int availabilityScenarioId;
	private String availabilityScenarioSecuredPower;
	private Balance balanceDayAhead;

	private Map<Integer, List<Float>> calculatedSecurityOfSupplyNiveausWithBalancing = new TreeMap<>();
	private Map<Integer, List<Float>> calculatedSecurityOfSupplyNiveausWithoutBalancing = new TreeMap<>();
	private EmissionsCarbon carbonEmissions;
	private CompanyName companyName;
	private Map<Integer, Float> CONE = new HashMap<>();
	private Map<Integer, PlantOption> costOfNewEntryPlant = new HashMap<>();

	/**
	 * All dayAheadElectricityTraders. Set is recommended so that no each Trader
	 * is unique i.e. has a different name and Traders are sorted automatically.
	 * Due to threads the order in these lists can be of relevance.
	 */
	private Set<Trader> dayAheadElectricityTraders = new TreeSet<>();
	/** Implementor of interface EEXClosedAuction. */
	private DayAheadMarketOperator dayAheadMarketOperator;
	/** True, if commission via {@link DecommissionPlants} is active. */
	private boolean decommissionActive;
	/**
	 * Years of continuous negative profit before shut down due to economic
	 * reasons
	 */
	private int decommissionsYearsOfNegativeProfit;
	/** Remaining plant life time before shut down due to economic reasons */
	private int decommissionsYearsToShutDown;
	/** Demand object of market area */
	private Demand demandData;
	private DemandTrader demandTrader;
	/** Yearly Hydrogen demand <b>year</b> <b>value</b> */

	private ProductionElectricity electricityProduction;
	private DayAhead electricityResultsDayAhead;

	/**
	 * Name of demand (aggregated) Scenario in the Table
	 * #electricityTotalDemandTable, set via XML
	 */
	private String electricityTotalDemandScenario = "demand";
	/** Name of demand (aggregated) table */
	private String electricityTotalDemandTable;
	private Flows exchange;
	/**
	 * Name of exchange scenario which is selected from the "scenario" table in
	 * the exchange database
	 */
	private int exchangeFlowReferenceYear;
	/** Long-term exchange forecasts */
	private ExchangeForecastFuture exchangeForecastFuture;
	/**
	 * Name of exchange scenario which is selected from the "scenario" table in
	 * the exchange database
	 */
	private String exchangeScenario;

	/**
	 * All exchange Traders. Set is recommended so that no each Trader is unique
	 * i.e. has a different name and Traders are sorted automatically. Due to
	 * threads the order in these lists can be of relevance.
	 */
	private Set<ExchangeTrader> exchangeTraders = new TreeSet<>();

	private int firstYearlyFuelPriceYear;
	// Map with last forecast prices
	private Map<Integer, Map<Integer, Float>> forwardPricesLast = null;
	/**
	 * All forward Traders. Set is recommended so that no each Trader is unique
	 * i.e. has a different name and Traders are sorted automatically. Due to
	 * threads the order in these lists can be of relevance.
	 */
	private FuelPrices fuelPrices;
	/** Name of daily fuel price scenario */
	private String fuelPriceScenarioDaily;
	/** Name of yearly fuel price scenario */
	private String fuelPriceScenarioYearly;
	/** Name of fuel load hours scenario for renewable capacities */
	private String fullLoadHoursHistorical;
	/** Name of fuel load hours scenario for renewable capacities */
	private String fullLoadHoursScenario;
	private FutureMeritOrders futureMeritOrders;
	private FuturePrices futurePrices;
	private String gasHub;
	private GenerationData generationData;
	/**
	 * All generators. Set is recommended so that no each Trader is unique i.e.
	 * has a different name and Traders are sorted automatically. Due to threads
	 * the order in these lists can be of relevance.
	 */
	private Map<String, Generator> generators = new TreeMap<>();
	/** Grid losses in percentage of demand */
	private int id;

	private int idMarketCoupling = 0;

	/**
	 * Interest Rate for MarketArea. Could also be set different for each
	 * generator
	 */
	private float interestRate;

	/** Logger of investments */
	private NetValue investmentLogger;
	/** Logger of investments dynamic */
	private Map<String, Investor> investorsConventionalGeneration = new TreeMap<>();
	@Deprecated
	private int lastDailyFuelPriceYear;
	@Deprecated
	private int lastRenewableCapacityYearHistorical;

	private int lastYearlyFuelPriceYear;
	private LogFile logFilePricesHigh;
	private Set<LogFile> logFiles = new HashSet<>();

	private boolean logHourlyDispatch;
	private LogFile logSecurityOfSupplyWithExchange;
	private LogFile logSecurityOfSupplyWithoutExchange;
	private RenewableManager managerRenewables;
	private MarketAreaType marketAreaType;
	/** Market clearing through market coupling */
	private boolean marketCoupling = false;
	/** List containing all market operator agents (e.g. auctioneers) */
	private List<Auction> marketOperators = new ArrayList<>();

	/** Reference to PowerMarkets object */
	private PowerMarkets model;
	/** Name of market area */
	private String name;
	private NetValueExtremePrices netValueExtremePrices;
	/**
	 * Name of scenario for non fluctuating renewable profiles
	 */
	private String nonFluctuatingProfileScenario = "";
	private String nonFluctuatingProfileScenarioHistorical = "";

	private String nukeAvailFactor = "NA";
	private OperationMaintenanceCost operationMaintenanceCosts;
	private PlantsDecommissioned plantsDecommissioned;
	private String powerPlantData;
	/**
	 * Name of power plant table (as given in xml). Name is not changed when
	 * power plant table is cloned, i.e. NEVER use this variable when
	 * changing/deleting the table.
	 */
	private String powerPlantTableNameOrg = null;

	/** Set via xml, equals maximum for price in net value calculations. */
	private float priceForwardMaximum = Float.POSITIVE_INFINITY;

	private PumpStorage pumpStorage;
	private int pumpStorageActiveTrading;

	private String pumpStorageData;
	private String pumpStorageProfileData;
	private float[] pumpStorageStaticProfile;
	private Set<PumpStorageTrader> pumpStorageTraders = new TreeSet<>();
	private Random random;
	private RegularCallMarketLog regularCallMarketLog;
	private String renewableHistorical;
	/** Name of renewable scenario */
	private String renewableScenario;
	private int renewableScenarioYear;
	private RenewableTrader renewableTrader;

	/** Hourly historical seasonal storage production */
	private Map<Integer, Float> seasonalProduction;

	private Map<Integer, Map<Integer, Float>> seasonalProductionHistorical;
	private Set<SeasonalStorageTrader> seasonalStorageTraders = new TreeSet<>();

	private SecurityOfSupply securityOfSupply;
	/** Name of XML file for general simulation settings */
	private String settingsFileName;
	/**
	 * Year, when a regulatory coal-phase out is scheduled. No value (MAX_VALUE)
	 * means that the shut down date is based on the database and technical
	 * lifetime.
	 */
	private int shutDownYearCoal = Integer.MAX_VALUE;

	private StartupCost startupCosts;
	/** Long-term storage operation forecasts */
	private StorageOperationForecastFutureRegression storageOperationForecastFuture;
	private Map<Integer, Float> storageValues;
	/** weekly historical storage volumes */
	private Map<String, Map<Integer, Float>> storageValuesHistorical;

	private SupplyData supplyData;

	/**
	 * All supply Traders. Set is recommended so that no each Trader is unique
	 * i.e. has a different name and Traders are sorted automatically. Due to
	 * threads the order in these lists can be of relevance.
	 */
	private Set<SupplyTrader> supplyTraders = new TreeSet<>();

	private String totalDemandScenarioHistorical;

	/** Profile dataset for RES */
	private String weatherDataset;

	/** Year of WeatherDataset */
	private int weatherYearDemand;

	private int yearDecommissionsStart;

	/**
	 * Year until which additional shutdown is not allowed for power plants.
	 * Shutdown of plants should be handled by official information for the near
	 * future. If <code>null</code> has no effect.
	 */
	private Integer yearFromShutDownPowerPlantsNotAllowedEnd;
	/**
	 * Year from additional shutdown is not allowed for power plants. Shutdown
	 * of plants should be handled by official information for the near future.
	 * If <code>null</code> has no effect.
	 */
	private Integer yearFromShutDownPowerPlantsNotAllowedStart;

	/** Constructor new market area */
	public MarketArea() {
		id = count;
		count++;
		random = new Random(Settings.getRandomNumberSeed());
	}
	/** Constructor new market area */
	public MarketArea(MarketAreaType marketAreaType) {
		this();
		this.marketAreaType = marketAreaType;
	}
	public void addAllDayAheadElectricityTraders(Set<? extends Trader> dayAheadElectricityTraders) {
		this.dayAheadElectricityTraders.addAll(dayAheadElectricityTraders);
	}
	private void addCONE(int year) {

		// Reuse already calculated values
		if (CONE.get(year) == null) {

			final List<PlantOption> capacityOptions = getGenerationData()
					.getCopyOfCapacityOptions(year);

			// CONE depends one what assumptions are made
			final float amortisationTime = 15;

			// find CONE for current year
			float CONECheapest = Float.POSITIVE_INFINITY;
			for (final PlantOption plantOption : capacityOptions) {

				// determine costs for current plant
				final float investment = plantOption.getInvestmentPayment() * 1000;
				final float fixedCosts = plantOption.getCostsOperationMaintenanceFixed(year);

				// calculate annuity
				final float interestRate = getInterestRate();
				if (interestRate == 0) {
					logger.error("Interestrate = 0. Check Problem ");
				}
				final float annuity = Finance.getInverseAnnuityFactor(interestRate,
						amortisationTime, Payment.IN_ARREAR);

				final float CONEOfCurrentPlantOption = (annuity * investment) + fixedCosts;

				if (CONEOfCurrentPlantOption < CONECheapest) {
					CONECheapest = CONEOfCurrentPlantOption;
					costOfNewEntryPlant.put(year, plantOption);
				}
			}

			// Store value
			if (Float.isNaN(CONECheapest)) {
				logger.error("Cone is not a number!");
			}
			CONE.put(year, CONECheapest);
		}
	}

	public void addPumpStorageTrader(PumpStorageTrader pumpStorageTrader) {
		pumpStorageTraders.add(pumpStorageTrader);
	}

	public void addSeasonalTrader(SeasonalStorageTrader seasonalTraders) {
		seasonalStorageTraders.add(seasonalTraders);
	}

	public void addSupplyTrader(SupplyTrader supplyTrader) {
		supplyTraders.add(supplyTrader);
	}

	/** Builds agents from agents_ XML file. */
	private void buildAgentsFromFile(final String settingsFileName) {
		initializeValues(settingsFileName);
		registerAgents(afp.getAgents());
		registerTradersAtOperators();
	}

	@Override
	public int compareTo(MarketArea marketArea) {
		return getInitials().compareTo(marketArea.getInitials());
	}

	public void endOfSimulation() {
		afp = null;
		availabilitiesPlants = null;
		availabilityFactors = null;
		availabilityScenarioSecuredPower = null;
		balanceDayAhead = null;
		calculatedSecurityOfSupplyNiveausWithBalancing = null;
		calculatedSecurityOfSupplyNiveausWithoutBalancing = null;
		carbonEmissions = null;
		companyName = null;
		CONE = null;
		costOfNewEntryPlant = null;
		dayAheadElectricityTraders = null;
		dayAheadMarketOperator = null;
		demandData = null;
		electricityProduction = null;
		electricityResultsDayAhead = null;
		exchange = null;
		exchangeTraders = null;
		fuelPrices = null;
		futureMeritOrders = null;
		futurePrices = null;
		generationData = null;
		generators = null;
		renewableTrader = null;
		investmentLogger = null;
		investorsConventionalGeneration = null;
		logFilePricesHigh = null;
		logFiles = null;
		logSecurityOfSupplyWithExchange = null;
		logSecurityOfSupplyWithoutExchange = null;
		managerRenewables = null;
		marketAreaType = null;
		marketOperators = null;
		model = null;
		netValueExtremePrices = null;
		operationMaintenanceCosts = null;
		plantsDecommissioned = null;
		powerPlantTableNameOrg = null;
		pumpStorage = null;
		pumpStorageData = null;
		pumpStorageProfileData = null;
		pumpStorageStaticProfile = null;
		pumpStorageTraders = null;
		seasonalStorageTraders = null;
		seasonalProduction = null;
		seasonalProductionHistorical = null;
		seasonalStorageTraders = null;
		securityOfSupply = null;
		startupCosts = null;
		supplyData = null;
		supplyTraders = null;
		storageValues = null;
		storageValuesHistorical = null;
		seasonalProduction = null;
	}

	public boolean equals(MarketAreaType marketAreaType) {
		return name.equalsIgnoreCase(marketAreaType.name());
	}

	public AvailabilitiesPlants getAvailabilitiesPlants() {
		return availabilitiesPlants;
	}

	public Availability getAvailabilityFactors() {
		return availabilityFactors;
	}

	public String getAvailabilityScenario() {
		return availabilityScenario;
	}

	public int getAvailabilityScenarioId() {
		return availabilityScenarioId;
	}

	public String getAvailabilityScenarioSecuredPower() {
		return availabilityScenarioSecuredPower;
	}

	public Balance getBalanceDayAhead() {
		return balanceDayAhead;
	}

	public EmissionsCarbon getCarbonEmissions() {
		return carbonEmissions;
	}

	public CompanyName getCompanyName() {
		return companyName;
	}

	public float getCostOfNewEntryMW() {
		if (!CONE.containsKey(Date.getYear())) {
			addCONE(Date.getYear());
		}
		return CONE.get(Date.getYear());
	}

	public synchronized PlantOption getCostOfNewEntryPlant() {
		if (!costOfNewEntryPlant.containsKey(Date.getYear())) {
			addCONE(Date.getYear());
		}
		return costOfNewEntryPlant.get(Date.getYear());
	}

	public DayAheadMarketOperator getDayAheadMarketOperator() {
		return dayAheadMarketOperator;
	}

	public int getDecommissionsYearsOfNegativeProfit() {
		return decommissionsYearsOfNegativeProfit;
	}

	public int getDecommissionsYearsToShutDown() {
		return decommissionsYearsToShutDown;
	}

	public Demand getDemandData() {
		return demandData;
	}

	public DemandTrader getDemandTrader() {
		return demandTrader;
	}

	public ProductionElectricity getElectricityProduction() {
		return electricityProduction;
	}

	public DayAhead getElectricityResultsDayAhead() {
		return electricityResultsDayAhead;
	}

	public String getElectricityTotalDemandScenario() {
		return electricityTotalDemandScenario;
	}

	public Flows getExchange() {
		return exchange;
	}

	public int getExchangeFlowReferenceYear() {
		return exchangeFlowReferenceYear;
	}

	public ExchangeForecastFuture getExchangeForecastFuture() {
		return exchangeForecastFuture;
	}

	public String getExchangeScenario() {
		return exchangeScenario;
	}

	public List<ExchangeTrader> getExchangeTraders() {
		return new ArrayList<>(exchangeTraders);
	}

	public int getFirstYearlyFuelPriceYear() {
		return firstYearlyFuelPriceYear;
	}

	private Map<Integer, Float> getForewardPriceListLast(int futureYear) {
		// max year
		final int yearMax = Collections.max(forwardPricesLast.keySet());
		if (futureYear > yearMax) {
			return forwardPricesLast.get(yearMax);
		}
		// min year (should actually not happen)
		final int yearMin = Collections.min(forwardPricesLast.keySet());
		if (futureYear < yearMin) {
			return forwardPricesLast.get(yearMin);
		}
		// if year matches
		if (forwardPricesLast.containsKey(futureYear)) {
			return forwardPricesLast.get(futureYear);
		}
		return getForewardPriceListLast(futureYear + 1);
	}

	public FuelPrices getFuelPrices() {
		return fuelPrices;
	}

	public String getFuelPriceScenarioDaily() {
		return fuelPriceScenarioDaily;
	}

	public String getFuelPriceScenario() {
		return fuelPriceScenarioYearly;
	}

	public String getFullLoadHoursHistorical() {
		return fullLoadHoursHistorical;
	}

	public String getFullLoadHoursScenario() {
		return fullLoadHoursScenario;
	}

	public FutureMeritOrders getFutureMeritOrders() {
		return futureMeritOrders;
	}

	public FuturePrices getFuturePrices() {
		return futurePrices;
	}

	public String getGasHub() {
		return gasHub;
	}

	public GenerationData getGenerationData() {
		return generationData;
	}

	public Generator getGenerator(String name) {
		return generators.get(name);
	}

	public List<Generator> getGenerators() {
		return new ArrayList<>(generators.values());
	}

	public int getId() {
		return id;
	}

	public String getIdentityAndNameLong() {
		return id + "_" + name;
	}

	public int getIdMarketCoupling() {
		return idMarketCoupling;
	}

	public String getInitials() {
		return getMarketAreaType().getInitials().toUpperCase();
	}

	public String getInitialsBrackets() {
		return "[" + getInitials() + "] ";
	}

	public String getInitialsUnderscore() {
		return getInitials() + "_";
	}

	public Capacities getInterconnectionCapacities() {
		return getMarketCouplingOperator().getCapacitiesData();
	}

	public float getInterestRate() {
		return interestRate;
	}

	public NetValue getInvestmentLogger() {
		return investmentLogger;
	}

	public List<Investor> getInvestorsConventionalGeneration() {
		return new ArrayList<>(investorsConventionalGeneration.values());
	}

	@Deprecated
	public int getLastRenewableCapacityYearHistorical() {
		return lastRenewableCapacityYearHistorical;
	}

	public int getLastYearlyFuelPriceYear() {
		return lastYearlyFuelPriceYear;
	}
	public Collection<LogFile> getLogFiles() {
		return logFiles;
	}

	/**
	 * Get price forecast according to specified approach Current approach: <br>
	 * - use simulated price of previous year for the
	 * {@link #getYearsLongTermPriceForecastStart()} following years <br>
	 * - use forward price (based on simplified hourly supply demand curves
	 * model) for the remaining investment period
	 */
	public Map<Integer, Map<Integer, Float>> getLongTermPricePrediction(boolean isIndex) {
		/** Get price forecast according to specified approach */
		/**
		 * Current approach: <br>
		 * - use simulated price of previous year for the 5 following years <br>
		 * - use forward price (based on simplified hourly supply demand curves
		 * model) for the remaining investment period
		 */
		// Map with forecast prices
		final Map<Integer, Map<Integer, Float>> forwardPrices = new LinkedHashMap<>();

		// sometimes its easier using only an index without the current year.
		// Therefore use boolean to set.
		// Investment planning is at the end of the year, therefore use next
		// year as starting point
		final int year = Date.getYear() + 1;

		// Set price forecast
		// the index is relative to the year in which forecast is made!
		for (int index = 0; index <= Investor.getYearsLongTermPriceForecastEnd(); index++) {
			Map<Integer, Float> prices;
			if (index < Investor.getYearsLongTermPriceForecastStart()) {
				// 1. Spot price current year
				prices = new LinkedHashMap<>(getElectricityResultsDayAhead().getYearlyPricesMap());
			} else {
				// 2. Forward price
				if (forwardPricesLast != null) {
					prices = new LinkedHashMap<>(getForewardPriceListLast(year + index));
				} else {
					prices = new LinkedHashMap<>(
							PriceForecastFuture.getForwardPriceListCapped(year + index, this));
				}
			}
			if (isIndex) {
				forwardPrices.put(index, prices);
			} else {
				forwardPrices.put(year + index, prices);

			}
			// log results
			getFuturePrices().addForecast(year, getName(), year + index, prices);
		}
		Map<Integer, Float> pricesLast;
		if (forwardPricesLast != null) {
			pricesLast = new LinkedHashMap<>(getForewardPriceListLast(
					year + Investor.getYearsLongTermPriceForecastEnd() + 1));
		} else {
			pricesLast = new LinkedHashMap<>(PriceForecastFuture.getForwardPriceListCapped(
					year + Investor.getYearsLongTermPriceForecastEnd() + 1, this));
		}
		if (isIndex) {
			// Use last available capped price
			forwardPrices.put(Investor.getYearsLongTermPriceForecastEnd() + 1, pricesLast);
		} else {
			// Use last available capped price
			forwardPrices.put(year + Investor.getYearsLongTermPriceForecastEnd() + 1, pricesLast);
		}
		getFuturePrices().addForecast(year, getName(),
				year + Investor.getYearsLongTermPriceForecastEnd() + 1, pricesLast);
		logPriceForecastYearlyAverages(forwardPrices);
		return new LinkedHashMap<>(forwardPrices);
	}

	public RenewableManager getManagerRenewables() {
		return managerRenewables;
	}

	public MarketAreaType getMarketAreaType() {
		if (marketAreaType == null) {
			marketAreaType = MarketAreaType.getMarketAreaTypeFromName(name);
		}
		return marketAreaType;
	}

	public MarketCouplingOperator getMarketCouplingOperator() {
		return model.getMarketScheduler().getMarketCouplingOperator();
	}

	public List<Auction> getMarketOperators() {
		return marketOperators;
	}

	/** Get reference to PowerMarkets object */
	public PowerMarkets getModel() {
		return model;
	}

	public String getName() {
		return name;
	}

	public NetValueExtremePrices getNetValueExtremePrices() {
		return netValueExtremePrices;
	}

	public String getNonFluctuatingProfileScenario() {
		return nonFluctuatingProfileScenario;
	}

	public String getNonFluctuatingProfileScenarioHistorical() {
		return nonFluctuatingProfileScenarioHistorical;
	}

	public String getNukeAvailFactor() {
		return nukeAvailFactor;
	}

	public OperationMaintenanceCost getOperationMaintenanceCosts() {
		return operationMaintenanceCosts;
	}

	public PlantsDecommissioned getPlantsDecommissioned() {
		return plantsDecommissioned;
	}

	public String getPowerPlantTableName() {
		return powerPlantData;
	}

	public String getPowerPlantTableNameOrg() {
		return powerPlantTableNameOrg;
	}

	public float getPriceForwardMaximum() {
		return priceForwardMaximum;
	}

	public PumpStorage getPumpStorage() {
		return pumpStorage;
	}

	public int getPumpStorageActiveTrading() {
		return pumpStorageActiveTrading;
	}

	public String getPumpStorageData() {
		return pumpStorageData;
	}

	public String getPumpStorageProfileData() {
		return pumpStorageProfileData;
	}

	public List<Float> getPumpStorageStaticProfile() {
		final List<Float> profile = new ArrayList<>(pumpStorageStaticProfile.length);
		for (final float value : pumpStorageStaticProfile) {
			profile.add(value);
		}
		return profile;
	}

	public List<PumpStorageTrader> getPumpStorageTraders() {
		return new ArrayList<>(pumpStorageTraders);
	}

	public RegularCallMarketLog getRegularCallMarketLog() {
		return regularCallMarketLog;
	}

	public String getRenewableHistorical() {
		return renewableHistorical;
	}

	public String getRenewableScenario() {
		return renewableScenario;
	}

	public int getRenewableScenarioYear() {
		return renewableScenarioYear;
	}

	public RenewableTrader getRenewableTrader() {
		return renewableTrader;
	}

	public Map<Integer, Float> getSeasonalProduction() {
		return seasonalProduction;
	}

	public Map<Integer, Map<Integer, Float>> getSeasonalProductionHistorical() {
		return seasonalProductionHistorical;
	}

	public List<SeasonalStorageTrader> getSeasonalStorageTraders() {
		return new ArrayList<>(seasonalStorageTraders);
	}

	public SecurityOfSupply getSecurityOfSupply() {
		return securityOfSupply;
	}

	public String getSettingsFileName() {
		return settingsFileName;
	}

	public int getShutDownYearCoal() {
		return shutDownYearCoal;
	}

	public StartupCost getStartUpCosts() {
		return startupCosts;
	}

	public StorageOperationForecastFutureRegression getStorageOperationForecastFuture() {
		return storageOperationForecastFuture;
	}

	/** Key week year, value */
	public Map<Integer, Float> getStorageValues() {
		return storageValues;
	}

	public Map<String, Map<Integer, Float>> getStorageValuesHistorical() {
		return storageValuesHistorical;
	}

	public SupplyData getSupplyData() {
		return supplyData;
	}

	public Investor getSupplyInvestorFromID(int id) {
		Investor investmentPlannerTemp = null;
		for (final Investor investmentPlanner : investorsConventionalGeneration.values()) {
			if (investmentPlanner.getID() == id) {
				investmentPlannerTemp = investmentPlanner;
			}
		}
		return investmentPlannerTemp;
	}

	public Investor getSupplyInvestorFromName(String name) {
		Investor investmentPlannerTemp = null;

		// check for conventional
		if (investorsConventionalGeneration.containsKey(name)) {
			investmentPlannerTemp = investorsConventionalGeneration.get(name);
		}

		return investmentPlannerTemp;
	}

	public List<SupplyTrader> getSupplyTrader() {
		return new ArrayList<>(supplyTraders);
	}

	public SupplyTrader getSupplyTraderByID(int ID) {

		for (final SupplyTrader supplyTrader : supplyTraders) {
			if (supplyTrader.getID() == (ID - 1)) {
				return supplyTrader;
			}
		}

		return null;
	}

	public SupplyTrader getSupplyTraderByName(String name) {

		for (final SupplyTrader supplyTrader : supplyTraders) {
			if (supplyTrader.getName().equals(name)) {
				return supplyTrader;
			}
		}

		return null;
	}

	public String getTotalDemandScenario() {
		return electricityTotalDemandTable;
	}

	public String getTotalDemandScenarioHistorical() {
		return totalDemandScenarioHistorical;
	}

	public String getWeatherDataset() {
		if (weatherDataset == null) {
			return "NA";
		}
		return weatherDataset;
	}

	public int getWeatherYearDemand() {
		return weatherYearDemand;
	}

	public int getYearDecommissionsStart() {
		return yearDecommissionsStart;
	}

	public Integer getYearFromShutDownPowerPlantsNotAllowedEnd() {
		return yearFromShutDownPowerPlantsNotAllowedEnd;
	}

	public Integer getYearFromShutDownPowerPlantsNotAllowedStart() {
		return yearFromShutDownPowerPlantsNotAllowedStart;
	}

	/**
	 * Initializes market areas. The method is called when market area objects
	 * are instantiated in {@link simulations.initialization.Agent}.
	 *
	 */
	public void initialize() {
		try {
			// Initialize market area for market coupling
			if (marketCoupling) {
				// Create new instance of MarketCouplingOperator
				if (model.getMarketScheduler().getMarketCouplingOperator() == null) {
					model.getMarketScheduler().createNewMarketCouplingOperator();
				}
				// Set market area for market coupling
				idMarketCoupling = countMarketCoupling;
				countMarketCoupling++;
				model.getMarketScheduler().getMarketCouplingOperator().addMarketArea(this);
			}

			long startTime = 0;
			startTime = System.currentTimeMillis();

			// Load market area data
			logger.info(getInitialsBrackets() + "Start data loading ");
			loadMarketAreaData();

			// Build agents through parsing xml file
			logger.info(getInitialsBrackets() + "Start agent building ");
			buildAgentsFromFile(settingsFileName);
			logger.info(getInitialsBrackets() + "Agent building completed ("
					+ (System.currentTimeMillis() - startTime) + "ms)");

			final int size = dayAheadElectricityTraders.size() + exchangeTraders.size()
					+ generators.size() + supplyTraders.size()
					+ investorsConventionalGeneration.size();

			logger.debug(getInitialsBrackets() + "Number of traders " + size);

			/** Logging objects */
			electricityResultsDayAhead = new DayAhead(this);
			electricityProduction = new ProductionElectricity();
			balanceDayAhead = new Balance(this);
			carbonEmissions = new EmissionsCarbon(this);
			availabilitiesPlants = new AvailabilitiesPlants();

			securityOfSupply = new SecurityOfSupply();
			netValueExtremePrices = new NetValueExtremePrices(this);
			plantsDecommissioned = new PlantsDecommissioned(this);
			futurePrices = new FuturePrices(this);
			futureMeritOrders = new FutureMeritOrders(this);

			// Create investment logger objects
			investmentLogger = new results.investment.NetValue(this);

			initializeLogFiles();

		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void initializeLogFiles() {
		// Add log files

		// Hourly EmissionsCarbon
		final List<LogData> columnsHourlyEmissions = new ArrayList<>();
		columnsHourlyEmissions.add(LogDataColumn.DAY);
		columnsHourlyEmissions.add(LogDataColumn.HOUR_OF_DAY);
		columnsHourlyEmissions.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);
		columnsHourlyEmissions.add(LogDataColumn.DEMAND);
		columnsHourlyEmissions.add(LogDataColumn.DEMAND_EXCL_LOSSES);
		columnsHourlyEmissions.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES);
		columnsHourlyEmissions.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_CONVENTIONAL);
		columnsHourlyEmissions.add(LogDataColumn.ELECTRICITY_PROD_TOTAL);
		columnsHourlyEmissions.add(LogDataColumn.PROD_TEST);
		columnsHourlyEmissions.add(LogDataColumn.EXCHANGE_MARKET_COUPLING);
		columnsHourlyEmissions.add(LogDataColumn.EXCHANGE_EXOGENOUS);
		columnsHourlyEmissions.add(LogDataColumn.EMISSIONS_CONSUMPTION_BASED_TOTAL);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_TOTAL_HOURLY_PRODUCTION);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_TOTAL_HOURLY_STARTUP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_LIGNITE);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_LIGNITE_START_UP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_CLEAN_LIGNITE);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_CLEAN_LIGNITE_START_UP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_COAL);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_COAL_START_UP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_GAS);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_GAS_START_UP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_CLEAN_COAL);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_CLEAN_COAL_START_UP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_CLEAN_GAS);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_CLEAN_GAS_START_UP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_OIL);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_OIL_START_UP);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_PEAKER);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_MINE);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_SEWAGEGAS);
		columnsHourlyEmissions.add(LogDataColumn.EMISSION_WASTE);
		columnsHourlyEmissions.add(LogDataColumnSeveral.CONVENTIONAL_PRODUCTION);
		columnsHourlyEmissions.add(LogDataColumnSeveral.RENEWABLE_PRODUCTION);

		// Hourly prices and other data
		final List<LogData> columnsHourlyMarketClearing = new ArrayList<>();
		columnsHourlyMarketClearing.add(LogDataColumn.DAY);
		columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_HISTORICAL);
		columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);
		columnsHourlyMarketClearing.add(LogDataColumnSeveral.MARGINAL_BID);
		columnsHourlyMarketClearing.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES);
		columnsHourlyMarketClearing.add(LogDataColumn.DEMAND);

		columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_MARKET_COUPLING);
		columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_EXOGENOUS);
		columnsHourlyMarketClearing.add(LogDataColumn.BALANCE);
		columnsHourlyMarketClearing.add(LogDataColumn.CURTAILMENT);
		columnsHourlyMarketClearing.add(LogDataColumnSeveral.CONVENTIONAL_PRODUCTION);
		columnsHourlyMarketClearing.add(LogDataColumnSeveral.RENEWABLE_PRODUCTION);
		columnsHourlyMarketClearing.add(LogDataColumn.CAPACITY_ALL_PLANTS);
		columnsHourlyMarketClearing
				.add(LogDataColumn.CAPACITY_ALL_PLANTS_EXCL_EXPECTED_AND_UNEXPECTED);

		// Yearly prices
		final List<LogData> columnsPrices = new ArrayList<>();
		columnsPrices.add(LogDataColumn.PRICE_DAY_AHEAD_HISTORICAL);
		columnsPrices.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);
		logFiles.add(new LogFile("PricesYearly", "Yearly prices and other data for market area",
				Folder.DAY_AHEAD_PRICES, columnsPrices, Frequency.YEARLY, this));

		// Daily prices and other data
		final List<LogData> columnsLogForecast = new ArrayList<>();
		columnsLogForecast.add(LogDataColumn.DAY);
		columnsLogForecast.add(LogDataColumn.HOUR_OF_DAY);
		columnsLogForecast.add(LogDataColumn.PRICE_DAY_AHEAD_HISTORICAL);
		columnsLogForecast.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);

		columnsLogForecast.add(LogDataColumnSeveral.CONVENTIONAL_PRODUCTION);
		columnsLogForecast.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_CONVENTIONAL);
		columnsLogForecast.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES);

		columnsLogForecast.add(LogDataColumn.DEMAND);

		columnsLogForecast.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);

		// Yearly energy balance
		final List<LogData> columnsYearlyEnergyBalance = new ArrayList<>();
		columnsYearlyEnergyBalance.add(LogDataColumnSeveral.CONVENTIONAL_PRODUCTION);
		columnsYearlyEnergyBalance.add(LogDataColumnSeveral.RENEWABLE_PRODUCTION);
		columnsYearlyEnergyBalance.add(LogDataColumn.DEMAND);
		columnsYearlyEnergyBalance.add(LogDataColumn.EXCHANGE_MARKET_COUPLING);
		columnsYearlyEnergyBalance.add(LogDataColumn.EXCHANGE_EXOGENOUS);
		columnsYearlyEnergyBalance.add(LogDataColumn.BALANCE);
		columnsYearlyEnergyBalance.add(LogDataColumn.CURTAILMENT);
		logFiles.add(new LogFile("EnergyBalanceYearly", "Yearly energy balance by market area",
				Folder.MAIN, columnsYearlyEnergyBalance, Frequency.YEARLY, this));
	}

	private void initializeValues(final String settingsFileName) {
		try {
			afp = new FileParser(model, PowerMarkets.getParamDirectory()
					+ PowerMarkets.getSettingsFolder() + settingsFileName, this);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	public boolean isDecommissionActive() {
		return decommissionActive;
	}

	/**
	 * Checks whether this market area is of specified {@link MarketAreaType}
	 */
	public boolean isEqualMarketArea(MarketAreaType marketAreaType) {
		return name.equalsIgnoreCase(marketAreaType.name());
	}

	public boolean isLogHourlyDispatch() {
		return logHourlyDispatch;
	}

	public boolean isMarketCoupling() {
		return marketCoupling;
	}

	/**
	 * Loads the data for the market area. In order to optimize the performance
	 * threads are used. If a utility class depends on another, then it is
	 * loaded after the other class has been initialized.
	 */
	private void loadMarketAreaData() {
		final MarketAreaData marketAreaScenarioSettings = new MarketAreaData(this);

		// Read different scenario settings
		marketAreaScenarioSettings.readScenarioSettings();

		// Load data
		marketAreaScenarioSettings.loadMarketAreaData();
	}

	/** Logs the yearly average price forecast */
	private void logPriceForecastYearlyAverages(Map<Integer, Map<Integer, Float>> forwardPrices) {
		// fire and forget, only for logfiles
		new Thread(() -> {
			try {
				/** Log yearly average price forecast */
				// List of the yearly average price forecast (for logging)
				final List<Float> priceForecastYearlyAverages = new ArrayList<>();
				for (final Map<Integer, Float> hourlyPricesForecast : forwardPrices.values()) {
					priceForecastYearlyAverages
							.add(Statistics.calcAvg(hourlyPricesForecast.values()));
				}

				final StringBuffer logYearlyAverage = new StringBuffer(200);
				logYearlyAverage.append(
						getInitialsBrackets() + "Price forecast (yearly averages) / first forecast "
								+ Investor.getYearsLongTermPriceForecastStart() + " years): ");
				logYearlyAverage.append(Statistics.round(priceForecastYearlyAverages
						.get(Investor.getYearsLongTermPriceForecastStart()), 2));
				logYearlyAverage.append("last forecast / forward price (afterwards): "
						+ (priceForecastYearlyAverages.size() - 1) + " ");
				logYearlyAverage.append(Statistics.round(
						priceForecastYearlyAverages.get(priceForecastYearlyAverages.size() - 1),
						2));
				logger.info(logYearlyAverage.toString());
			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		}).start();
	}

	public void logPricesHigh() {

		// Add log file
		if (logFilePricesHigh == null) {
			// Hourly prices and other data
			final List<LogData> columnsHourlyMarketClearing = new ArrayList<>();
			columnsHourlyMarketClearing.add(LogDataColumn.YEAR);
			columnsHourlyMarketClearing.add(LogDataColumn.DAY);
			columnsHourlyMarketClearing.add(LogDataColumn.HOUR_OF_DAY);
			columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_HISTORICAL);
			columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);

			columnsHourlyMarketClearing.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES);
			columnsHourlyMarketClearing.add(LogDataColumn.DEMAND);

			columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_MARKET_COUPLING);
			columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_MARKET_COUPLING);
			columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_EXOGENOUS);
			columnsHourlyMarketClearing.add(LogDataColumn.BALANCE);
			columnsHourlyMarketClearing.add(LogDataColumn.CURTAILMENT);
			columnsHourlyMarketClearing.add(LogDataColumnSeveral.CONVENTIONAL_PRODUCTION);
			columnsHourlyMarketClearing.add(LogDataColumnSeveral.RENEWABLE_PRODUCTION);
			columnsHourlyMarketClearing.add(LogDataColumn.CAPACITY_ALL_PLANTS);
			columnsHourlyMarketClearing
					.add(LogDataColumn.CAPACITY_ALL_PLANTS_EXCL_EXPECTED_AND_UNEXPECTED);
			logFilePricesHigh = new LogFile("PricesHigh",
					"Hourly prices and other data for market area in hours where price is high",
					Folder.DAY_AHEAD_PRICES, columnsHourlyMarketClearing, Frequency.HOURLY, this);
		}

		// Write if data is needed
		for (int hourOfYear = 0; hourOfYear < Date.getLastHourOfYear(); hourOfYear++) {
			if (getElectricityResultsDayAhead().getHourlyPriceOfYear(hourOfYear) >= 300) {
				logFilePricesHigh.executeLoggingHourly(hourOfYear);
			}
		}

		// Make sure data is written
		if (Date.isLastYear()) {
			logFilePricesHigh.close();
		}

	}

	public void logSecurityOfSupplyWithExchange() {

		// Write if data is needed
		final Integer hourOfYear = getSecurityOfSupply().getHourWithExchange();
		if (hourOfYear == null) {
			return;
		}

		// Add log file
		if (logSecurityOfSupplyWithExchange == null) {
			// Hourly prices and other data
			final List<LogData> columnsHourlyMarketClearing = new ArrayList<>();
			columnsHourlyMarketClearing.add(LogDataColumn.YEAR);
			columnsHourlyMarketClearing.add(LogDataColumn.DAY);
			columnsHourlyMarketClearing.add(LogDataColumn.HOUR_OF_DAY);
			columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_HISTORICAL);
			columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);
			columnsHourlyMarketClearing.add(LogDataColumn.SECURITY_OF_SUPPLY_LEVEL_WITH_EXCHANGE);
			columnsHourlyMarketClearing.add(LogDataColumn.SECURITY_OF_SUPPLY_LOAD_WITH_EXCHANGE);

			columnsHourlyMarketClearing.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES);
			columnsHourlyMarketClearing.add(LogDataColumn.DEMAND);
			columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_MARKET_COUPLING);
			columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_EXOGENOUS);
			columnsHourlyMarketClearing.add(LogDataColumn.BALANCE);
			columnsHourlyMarketClearing.add(LogDataColumn.CURTAILMENT);
			columnsHourlyMarketClearing.add(LogDataColumnSeveral.CONVENTIONAL_PRODUCTION);
			columnsHourlyMarketClearing.add(LogDataColumnSeveral.RENEWABLE_PRODUCTION);
			columnsHourlyMarketClearing.add(LogDataColumn.CAPACITY_ALL_PLANTS);
			columnsHourlyMarketClearing
					.add(LogDataColumn.CAPACITY_ALL_PLANTS_EXCL_EXPECTED_AND_UNEXPECTED);
			logSecurityOfSupplyWithExchange = new LogFile("SecurityOfSupplyWithExchange",
					"Security of supply and other data for market area in hours where the supply surplus is minimal",
					Folder.DAY_AHEAD_PRICES, columnsHourlyMarketClearing, Frequency.HOURLY, this);
		}

		final LinkedList<Float> loggingListWithoutBalancing = new LinkedList<>();
		// add withExchange
		loggingListWithoutBalancing.add(getSecurityOfSupply()
				.getSecurityOfSupplyLevelWithExchange(SecurityOfSupplyValue.LOW));
		loggingListWithoutBalancing.add(getSecurityOfSupply()
				.getSecurityOfSupplyLevelWithExchange(SecurityOfSupplyValue.HIGH));
		// add without Exchange
		loggingListWithoutBalancing.add(getSecurityOfSupply()
				.getSecurityOfSupplyLevelWithoutExchange(SecurityOfSupplyValue.LOW));
		loggingListWithoutBalancing.add(getSecurityOfSupply()
				.getSecurityOfSupplyLevelWithoutExchange(SecurityOfSupplyValue.HIGH));
		calculatedSecurityOfSupplyNiveausWithoutBalancing.put(Date.getYear(),
				loggingListWithoutBalancing);

		final LinkedList<Float> loggingListWithBalancing = new LinkedList<>();
		// add withExchange
		loggingListWithBalancing.add(getSecurityOfSupply()
				.getSecurityOfSupplyLevelWithExchange(SecurityOfSupplyValue.LOW));

		calculatedSecurityOfSupplyNiveausWithBalancing.put(Date.getYear(),
				loggingListWithBalancing);

		logSecurityOfSupplyWithExchange.executeLoggingHourly(hourOfYear);
	}

	public void logSecurityOfSupplyWithoutExchange() {

		// Write if data is needed
		final Integer hourOfYear = getSecurityOfSupply().getHourWithoutExchange();
		if (hourOfYear == null) {
			return;
		}

		// Add log file
		if (logSecurityOfSupplyWithoutExchange == null) {
			// Hourly prices and other data
			final List<LogData> columnsHourlyMarketClearing = new ArrayList<>();
			columnsHourlyMarketClearing.add(LogDataColumn.YEAR);
			columnsHourlyMarketClearing.add(LogDataColumn.DAY);
			columnsHourlyMarketClearing.add(LogDataColumn.HOUR_OF_DAY);
			columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_HISTORICAL);
			columnsHourlyMarketClearing.add(LogDataColumn.PRICE_DAY_AHEAD_SIMULATED);
			columnsHourlyMarketClearing
					.add(LogDataColumn.SECURITY_OF_SUPPLY_LEVEL_WITHOUT_EXCHANGE);
			columnsHourlyMarketClearing.add(LogDataColumn.SECURITY_OF_SUPPLY_LOAD_WITHOUT_EXCHANGE);

			columnsHourlyMarketClearing.add(LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES);
			columnsHourlyMarketClearing.add(LogDataColumn.DEMAND);
			columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_MARKET_COUPLING);
			columnsHourlyMarketClearing.add(LogDataColumn.EXCHANGE_EXOGENOUS);
			columnsHourlyMarketClearing.add(LogDataColumn.BALANCE);
			columnsHourlyMarketClearing.add(LogDataColumn.CURTAILMENT);
			columnsHourlyMarketClearing.add(LogDataColumnSeveral.CONVENTIONAL_PRODUCTION);
			columnsHourlyMarketClearing.add(LogDataColumnSeveral.RENEWABLE_PRODUCTION);
			columnsHourlyMarketClearing.add(LogDataColumn.CAPACITY_ALL_PLANTS);
			columnsHourlyMarketClearing
					.add(LogDataColumn.CAPACITY_ALL_PLANTS_EXCL_EXPECTED_AND_UNEXPECTED);
			logSecurityOfSupplyWithoutExchange = new LogFile("SecurityOfSupplyWithoutExchange",
					"Security of supply and other data for market area in hours where the supply surplus is minimal",
					Folder.DAY_AHEAD_PRICES, columnsHourlyMarketClearing, Frequency.HOURLY, this);
		}

		logSecurityOfSupplyWithoutExchange.executeLoggingHourly(hourOfYear);

		// Make sure data is written
		if (Date.isLastYear()) {
			logSecurityOfSupplyWithoutExchange.close();
		}

	}

	private void registerAgents(final Map<Object, String> list) {
		for (final Map.Entry<Object, String> entry : list.entrySet()) {
			try {

				final Object agent = entry.getKey();

				if (agent instanceof Agent) {
					final Agent agentInit = (Agent) agent;
					agentInit.setMarketArea(this);
				} else {
					logger.error(agent + " should be an agent!");
				}

				// market operators
				if (agent instanceof DayAheadMarketOperator) {
					dayAheadMarketOperator = (DayAheadMarketOperator) agent;

					marketOperators.add(dayAheadMarketOperator);
				}

				// trading agents
				if (agent instanceof ExchangeTrader) {
					exchangeTraders.add((ExchangeTrader) agent);
				}

				if (agent instanceof DayAheadTrader) {
					dayAheadElectricityTraders.add((Trader) agent);
				}

				if (agent instanceof DemandTrader) {
					demandTrader = (DemandTrader) agent;
				}

				if (agent instanceof InvestorNetValue) {
					final String name = ((Investor) agent).getName();
					investorsConventionalGeneration.put(name, (InvestorNetValue) agent);
				}

				if (agent instanceof Generator) {
					final String name = ((Generator) agent).getName();
					generators.put(name, (Generator) agent);
				}

			} catch (final Exception e) {
				logger.error("Error while register agents", e);
			}
		}
	}

	private void registerTradersAtOperators() {
		/** register agents at operators */
		if (marketOperators.contains(dayAheadMarketOperator)) {
			dayAheadElectricityTraders = reorderSet(dayAheadElectricityTraders);
			dayAheadMarketOperator.register(dayAheadElectricityTraders);
			// Set upper and lower price bound for Traders
			for (final Trader trader : dayAheadElectricityTraders) {
				trader.setMaximumDayAheadPrice(dayAheadMarketOperator.getMaxPriceAllowed());
				trader.setMinimumDayAheadPrice(dayAheadMarketOperator.getMinPriceAllowed());
			}
		}

	}

	/**
	 * Traders are initialized via threads and therefore their order can change.
	 * Since the order can influence the outcome of the model e.g. which agent
	 * invest first, decommissions first, order needs to be equal for each
	 * random number seed. Therefore first sort, then shuffle everything so runs
	 * can be replicated.
	 *
	 * @param set
	 */
	private Set<Trader> reorderSet(Set<Trader> set) {
		final List<Trader> tradersTemp = new ArrayList<>(set);
		Collections.sort(tradersTemp);
		Collections.shuffle(tradersTemp, random);
		set = new LinkedHashSet<>();
		for (final Trader trader : tradersTemp) {
			set.add(trader);
		}
		return set;
	}

	public void setAvailability(Availability availabilityFactors) {
		this.availabilityFactors = availabilityFactors;
	}

	public void setCompanyName(CompanyName companyName) {
		this.companyName = companyName;
	}

	public void setDemand(Demand demand) {
		demandData = demand;
	}

	public void setExchange(Flows exchange) {
		this.exchange = exchange;
	}

	public void setExchangeForecastFuture(ExchangeForecastFuture exchangeForecastFuture) {
		this.exchangeForecastFuture = exchangeForecastFuture;
	}

	/** Set last (yearly) available fuel price year */
	public void setFirstYearlyFuelPriceYear(int firstYearlyFuelPriceYear) {
		this.firstYearlyFuelPriceYear = firstYearlyFuelPriceYear;
	}

	public void setForwardPricesLast(Map<Integer, Map<Integer, Float>> forwardPricesLast) {
		this.forwardPricesLast = forwardPricesLast;
	}

	public void setFuelPrices(FuelPrices fuelPrices) {
		this.fuelPrices = fuelPrices;
	}

	public void setGenerationData(GenerationData generationData) {
		this.generationData = generationData;
	}

	public void setIdMarketCoupling(int idMarketCoupling) {
		this.idMarketCoupling = idMarketCoupling;
	}

	public void setInvestmentLogger(NetValue investmentLogger) {
		this.investmentLogger = investmentLogger;
	}

	/** Set last (yearly) available fuel price year */
	public void setLastYearlyFuelPriceYear(int lastYearlyFuelPriceYear) {
		this.lastYearlyFuelPriceYear = lastYearlyFuelPriceYear;
	}

	public void setManagerRenewables(RenewableManager managerRenewables) {
		this.managerRenewables = managerRenewables;
	}

	public void setMarketCoupling(boolean marketCoupling) {
		this.marketCoupling = marketCoupling;
	}

	public void setModel(PowerMarkets model) {
		this.model = model;
		model.addMarketArea(this);
	}

	public void setName(String name) {
		this.name = name;
		if (settingsFileName == null) {
			settingsFileName = "agents_"
					+ MarketAreaType.getMarketAreaTypeFromName(name).getInitials() + ".xml";
		}
	}

	public void setOperationMaintenanceCosts(OperationMaintenanceCost operationMaintenanceCosts) {
		this.operationMaintenanceCosts = operationMaintenanceCosts;
	}

	public void setPumpStorage(PumpStorage pumpStorage) {
		this.pumpStorage = pumpStorage;
	}

	public void setPumpStorageActiveTrading(int pumpStorageActiveTrading) {
		this.pumpStorageActiveTrading = pumpStorageActiveTrading;
	}

	public void setPumpStorageProfileData(String pumpStorageProfileData) {
		this.pumpStorageProfileData = pumpStorageProfileData;
	}
	public void setPumpStorageStaticProfile(float[] pumpStorageStaticProfile) {
		this.pumpStorageStaticProfile = pumpStorageStaticProfile;
	}

	public void setRenewablerTrader(RenewableTrader renewableTrader) {
		this.renewableTrader = renewableTrader;
	}

	public void setSeasonalProductionHistorical(
			Map<Integer, Map<Integer, Float>> seasonalProduction) {
		seasonalProductionHistorical = seasonalProduction;
	}

	public void setStartupCosts(StartupCost startupCosts) {
		this.startupCosts = startupCosts;
	}

	public void setStorageOperationForecastFuture(
			StorageOperationForecastFutureRegression storageOperationForecastFuture) {
		this.storageOperationForecastFuture = storageOperationForecastFuture;
	}
	public void setStorageValuesHistorical(Map<String, Map<Integer, Float>> storageValues) {
		storageValuesHistorical = storageValues;
	}
	public void setSupplyData(SupplyData supplyData) {
		this.supplyData = supplyData;
	}

	@Override
	public String toString() {
		return name;
	}
}