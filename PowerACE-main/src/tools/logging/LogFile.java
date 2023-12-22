package tools.logging;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.operator.spot.tools.MarginalBid;
import simulations.MarketArea;
import simulations.scheduling.Date;
import supply.invest.Investor;
import tools.math.Statistics;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.Unit;

/**
 * Defines the general structure of the log classes. All columns can be accessed
 * over LogDataColumn, if several columns are needed to be written they can be
 * collected in ear 0 + 876x+0</code><br>
 */
public class LogFile {

	public enum Frequency {
		DAILY,
		HOURLY,
		SIMULATION,
		YEARLY;
	}

	public enum LogDataColumn
			implements
				LogData {

		ASSIGNED_VOLUME_POWER_TO_TECHNOLOGY(
				Unit.ENERGY_VOLUME),
		AVG_EF_PRODUCTION_BASED_EMISSION(
				Unit.EMISSION_FACTOR),
		AVG_EF_PRODUCTION_BASED_EMISSION_CORRECTED_BY_NET_EXPORTS(
				Unit.EMISSION_FACTOR),
		BALANCE(
				Unit.ENERGY_VOLUME),
		CAPACITY_ALL_PLANTS(
				Unit.CAPACITY),
		CAPACITY_ALL_PLANTS_EXCL_EXPECTED_AND_UNEXPECTED(
				Unit.CAPACITY),
		CURTAILMENT(
				Unit.ENERGY_VOLUME),
		DAY(
				Unit.NONE),
		DEMAND(
				Unit.ENERGY_VOLUME),
		DEMAND_ELECTRIC_CARS(
				Unit.ENERGY_VOLUME),
		DEMAND_EXCL_LOSSES(
				Unit.ENERGY_VOLUME),
		DEMAND_HEAT(
				Unit.ENERGY_VOLUME),
		DEMAND_POWER_TO_HEAT(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_DEMAND_POWER_TO_HYDROGEN(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PROD_TOTAL(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PRODUCTION_BIO_GAS(
				Unit.ENERGY_VOLUME,
				FuelName.BIOGAS),
		ELECTRICITY_PRODUCTION_BIO_MASS(
				Unit.ENERGY_VOLUME,
				FuelName.BIOMASS),
		ELECTRICITY_PRODUCTION_BLAST_FURNACE(
				Unit.ENERGY_VOLUME,
				FuelName.BLASTFURNACEGAS),
		ELECTRICITY_PRODUCTION_CLEAN_COAL(
				Unit.ENERGY_VOLUME,
				FuelName.CLEAN_COAL),
		ELECTRICITY_PRODUCTION_CLEAN_GAS(
				Unit.ENERGY_VOLUME,
				FuelName.CLEAN_GAS),
		ELECTRICITY_PRODUCTION_CLEAN_LIGNITE(
				Unit.ENERGY_VOLUME,
				FuelName.CLEAN_LIGNITE),
		ELECTRICITY_PRODUCTION_COAL(
				Unit.ENERGY_VOLUME,
				FuelName.COAL),
		ELECTRICITY_PRODUCTION_COAL_AT_COAST(
				Unit.ENERGY_VOLUME,
				FuelName.COAL_AT_COAST),
		ELECTRICITY_PRODUCTION_COAL_FAR_COAST(
				Unit.ENERGY_VOLUME,
				FuelName.COAL_FAR_COAST),
		/**
		 * Production based on heat demand from all plants.
		 */
		ELECTRICITY_PRODUCTION_GAS(
				Unit.ENERGY_VOLUME,
				FuelName.GAS),
		ELECTRICITY_PRODUCTION_GEO_THERMAL(
				Unit.ENERGY_VOLUME,
				FuelName.GEOTHERMAL),
		ELECTRICITY_PRODUCTION_HEATING_OIL(
				Unit.ENERGY_VOLUME,
				FuelName.HEATING_OIL),
		ELECTRICITY_PRODUCTION_HES(
				Unit.ENERGY_VOLUME,
				FuelName.HEATING_OIL),
		ELECTRICITY_PRODUCTION_HYDRO_LARGE_SCALE(
				Unit.ENERGY_VOLUME,
				FuelName.HYDROLARGESCALE),
		ELECTRICITY_PRODUCTION_HYDRO_SMALL_SCALE(
				Unit.ENERGY_VOLUME,
				FuelName.HYDROSMALLSCALE),
		ELECTRICITY_PRODUCTION_LAND_FILL_GAS(
				Unit.ENERGY_VOLUME,
				FuelName.LANDFILLGAS),
		ELECTRICITY_PRODUCTION_LAND_FILL_GAS_RENEWABLE(
				Unit.ENERGY_VOLUME,
				FuelName.LANDFILLGAS),
		ELECTRICITY_PRODUCTION_LIGNITE(
				Unit.ENERGY_VOLUME,
				FuelName.LIGNITE),
		ELECTRICITY_PRODUCTION_MINENGAS(
				Unit.ENERGY_VOLUME,
				FuelName.MINEGAS),
		ELECTRICITY_PRODUCTION_OIL(
				Unit.ENERGY_VOLUME,
				FuelName.OIL),
		ELECTRICITY_PRODUCTION_OTHER(
				Unit.ENERGY_VOLUME,
				FuelName.OTHER),
		ELECTRICITY_PRODUCTION_PEAKER_VOLL(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PRODUCTION_PUMPED_STORAGE(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PRODUCTION_PV(
				Unit.ENERGY_VOLUME,
				FuelName.SOLAR),
		ELECTRICITY_PRODUCTION_SEASONAL_STORAGE(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PRODUCTION_SEWAGE_GAS(
				Unit.ENERGY_VOLUME,
				FuelName.SEWAGEGAS),
		ELECTRICITY_PRODUCTION_TIDAL(
				Unit.ENERGY_VOLUME,
				FuelName.TIDAL),
		ELECTRICITY_PRODUCTION_TOTAL_CONVENTIONAL(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES_EXPECTED(
				Unit.ENERGY_VOLUME),
		ELECTRICITY_PRODUCTION_URANIUM(
				Unit.ENERGY_VOLUME,
				FuelName.URANIUM),
		ELECTRICITY_PRODUCTION_WASTE(
				Unit.ENERGY_VOLUME,
				FuelName.WASTE),
		ELECTRICITY_PRODUCTION_WIND_OFF_SHORE(
				Unit.ENERGY_VOLUME,
				FuelName.WIND_OFFSHORE),
		ELECTRICITY_PRODUCTION_WIND_ON_SHORE(
				Unit.ENERGY_VOLUME,
				FuelName.WIND_ONSHORE),

		EMISSION_COAL(
				Unit.TONS_CO2),
		EMISSION_COAL_START_UP(
				Unit.TONS_CO2),
		EMISSION_CLEAN_COAL(
				Unit.TONS_CO2),
		EMISSION_CLEAN_COAL_START_UP(
				Unit.TONS_CO2),

		EMISSION_CLEAN_GAS(
				Unit.TONS_CO2),
		EMISSION_GAS(
				Unit.TONS_CO2),

		EMISSION_CLEAN_GAS_START_UP(
				Unit.TONS_CO2),
		EMISSION_GAS_START_UP(
				Unit.TONS_CO2),
		EMISSION_LIGNITE(
				Unit.TONS_CO2),
		EMISSION_LIGNITE_START_UP(
				Unit.TONS_CO2),
		EMISSION_CLEAN_LIGNITE(
				Unit.TONS_CO2),
		EMISSION_CLEAN_LIGNITE_START_UP(
				Unit.TONS_CO2),

		EMISSION_OIL(
				Unit.TONS_CO2),
		EMISSION_OIL_START_UP(
				Unit.TONS_CO2),

		EMISSION_WASTE(
				Unit.TONS_CO2),
		EMISSION_SEWAGEGAS(
				Unit.TONS_CO2),
		EMISSION_MINE(
				Unit.TONS_CO2),

		EMISSION_PEAKER(
				Unit.TONS_CO2),
		EMISSION_TOTAL_HOURLY_PRODUCTION(
				Unit.TONS_CO2),
		EMISSION_TOTAL_HOURLY_STARTUP(
				Unit.TONS_CO2),
		EMISSIONS_CONSUMPTION_BASED_TOTAL(
				Unit.TONS_CO2),
		EXCHANGE_EXOGENOUS(
				Unit.ENERGY_VOLUME),
		EXCHANGE_MARKET_COUPLING(
				Unit.ENERGY_VOLUME),
		FORWARD_PRICE(
				Unit.ENERGY_PRICE),
		HOUR_OF_DAY(
				Unit.NONE),
		HOUR_OF_YEAR(
				Unit.NONE),
		MARGINAL_BID_COMMENT(
				Unit.NONE),
		MARGINAL_BID_FUEL_NAME(
				Unit.NONE),
		MARGINAL_BID_IN_AREA(
				Unit.NONE),
		MARGINAL_BID_STARTUP_IN_BID(
				Unit.ENERGY_PRICE),
		MARGINAL_BID_VAR_COSTS(
				Unit.ENERGY_PRICE),
		MONTH(
				Unit.NONE),
		PRICE_DAY_AHEAD_HISTORICAL(
				Unit.ENERGY_PRICE),
		PRICE_DAY_AHEAD_SIMULATED(
				Unit.ENERGY_PRICE),
		PRICE_DAY_AHEAD_SIMULATED_FILTERED(
				Unit.ENERGY_PRICE),

		PRICEFORECAST_INCL_EV(
				Unit.ENERGY_VOLUME),
		PROD_TEST(
				Unit.ENERGY_VOLUME),
		PRODUCTION_BASED_EMISSION_EV(
				Unit.TONS_CO2),
		PRODUCTION_BASED_EMISSION_EV_CORRECTED_BY_NET_EXPORTS(
				Unit.TONS_CO2),
		SECURITY_OF_SUPPLY_LEVEL_WITH_EXCHANGE(
				Unit.PERCENTAGE),
		SECURITY_OF_SUPPLY_LEVEL_WITHOUT_EXCHANGE(
				Unit.PERCENTAGE),
		SECURITY_OF_SUPPLY_LOAD_WITH_EXCHANGE(
				Unit.CAPACITY),
		SECURITY_OF_SUPPLY_LOAD_WITHOUT_EXCHANGE(
				Unit.CAPACITY),
		YEAR(
				Unit.NONE);

		private FuelName fuelName;
		private FuelType fuelType;
		private final Unit unit;

		private LogDataColumn(Unit unit) {
			this.unit = unit;
		}

		private LogDataColumn(Unit unit, FuelName fuelName) {
			this.unit = unit;
			this.fuelName = fuelName;
		}

		private LogDataColumn(Unit unit, FuelType fuelType) {
			this.unit = unit;
			this.fuelType = fuelType;
		}

		public boolean isElectricityProduction() {
			return toString().contains("ELECTRICITY_PRODUCTION");
		}

		public boolean isMarginalBid() {
			return toString().contains("MARGINAL_BID");
		}

	}

	public enum LogDataColumnSeveral
			implements
				LogData {
		CONVENTIONAL_PRODUCTION,
		MARGINAL_BID,
		RENEWABLE_PRODUCTION;

		public List<LogDataColumn> getLogDataColumns() {
			List<LogDataColumn> columns = null;

			switch (this) {
				case RENEWABLE_PRODUCTION: {
					columns = Arrays.asList(LogDataColumn.ELECTRICITY_PRODUCTION_BIO_GAS,
							LogDataColumn.ELECTRICITY_PRODUCTION_BIO_MASS,
							LogDataColumn.ELECTRICITY_PRODUCTION_GEO_THERMAL,
							LogDataColumn.ELECTRICITY_PRODUCTION_HYDRO_LARGE_SCALE,
							LogDataColumn.ELECTRICITY_PRODUCTION_HYDRO_SMALL_SCALE,
							LogDataColumn.ELECTRICITY_PRODUCTION_LAND_FILL_GAS_RENEWABLE,
							LogDataColumn.ELECTRICITY_PRODUCTION_PV,
							LogDataColumn.ELECTRICITY_PRODUCTION_SEWAGE_GAS,
							LogDataColumn.ELECTRICITY_PRODUCTION_TIDAL,
							LogDataColumn.ELECTRICITY_PRODUCTION_WIND_OFF_SHORE,
							LogDataColumn.ELECTRICITY_PRODUCTION_WIND_ON_SHORE);
					break;
				}
				case MARGINAL_BID: {
					columns = Arrays.asList(LogDataColumn.MARGINAL_BID_FUEL_NAME,
							LogDataColumn.MARGINAL_BID_VAR_COSTS,
							LogDataColumn.MARGINAL_BID_STARTUP_IN_BID,
							LogDataColumn.MARGINAL_BID_COMMENT, LogDataColumn.MARGINAL_BID_IN_AREA);
					break;
				}
				case CONVENTIONAL_PRODUCTION: {
					columns = Arrays.asList(LogDataColumn.ELECTRICITY_PRODUCTION_BLAST_FURNACE,
							LogDataColumn.ELECTRICITY_PRODUCTION_CLEAN_COAL,
							LogDataColumn.ELECTRICITY_PRODUCTION_CLEAN_GAS,
							LogDataColumn.ELECTRICITY_PRODUCTION_CLEAN_LIGNITE,
							LogDataColumn.ELECTRICITY_PRODUCTION_COAL,
							LogDataColumn.ELECTRICITY_PRODUCTION_COAL_AT_COAST,
							LogDataColumn.ELECTRICITY_PRODUCTION_COAL_FAR_COAST,
							LogDataColumn.ELECTRICITY_PRODUCTION_GAS,
							LogDataColumn.ELECTRICITY_PRODUCTION_HEATING_OIL,
							LogDataColumn.ELECTRICITY_PRODUCTION_HES,
							LogDataColumn.ELECTRICITY_PRODUCTION_LAND_FILL_GAS,
							LogDataColumn.ELECTRICITY_PRODUCTION_LIGNITE,
							LogDataColumn.ELECTRICITY_PRODUCTION_MINENGAS,
							LogDataColumn.ELECTRICITY_PRODUCTION_OIL,
							LogDataColumn.ELECTRICITY_PRODUCTION_OTHER,
							LogDataColumn.ELECTRICITY_PRODUCTION_PUMPED_STORAGE,
							LogDataColumn.ELECTRICITY_PRODUCTION_SEASONAL_STORAGE,
							LogDataColumn.ELECTRICITY_PRODUCTION_URANIUM,
							LogDataColumn.ELECTRICITY_PRODUCTION_WASTE,
							LogDataColumn.ELECTRICITY_PRODUCTION_PEAKER_VOLL);
					break;
				}
			}

			return columns;
		}
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(LogFile.class.getName());

	/** Appends the specified String and a closing ';' to the specified line. */
	public static void appendElement(StringBuilder line, String element) {
		line.append(element);
		line.append(";");
	}

	/** List of elements to be logged corresponding to columns in log file */
	private List<LogData> dataColumns;

	/** Description of log file */
	private final String description;
	/** Name of log file */
	private String fileName;
	private final Frequency frequency;
	private int hourOfYear;
	/** Year of the logging data, if applicable */
	private int year;
	/** Day of the logging data, if applicable */
	private int day;
	/** ID of log file */
	private int logFileID;
	/** Log folder where file is saved */
	private final Folder logFolder;
	/** Indicates whether log file has been initialized for logging period. */
	private boolean logInitialized = false;
	/** Log folder where file is saved */
	private final MarketArea marketArea;

	public LogFile(String fileName, String description, Folder logFolder, List<LogData> dataColumns,
			Frequency frequency, MarketArea marketArea) {
		this.marketArea = marketArea;
		this.description = description;
		this.fileName = fileName;
		this.dataColumns = new ArrayList<>(dataColumns);
		this.frequency = frequency;
		this.logFolder = logFolder;
		setFirstDataColumn();
		initialize();
	}

	public void close() {
		LoggerXLSX.close(logFileID);
	}

	/** Executes the logging of the defined elements */
	public void executeLoggingDay(int day) {
		try {
			// Initialize log file
			this.day = day;
			initialize();
			// Log values
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

				// Determine hourOfYear [0,HOURS_PER_YEAR]
				hourOfYear = Date.getFirstHourOfDay(day) + hour;

				// Add values of each log element
				final List<Object> values = logDataColumns();

				// Write dataLine in file
				LoggerXLSX.writeLine(logFileID, values);

				if ((frequency == Frequency.YEARLY) || (frequency == Frequency.DAILY)) {
					break;
				}
			}

			if ((day == Date.getLastDayOfYear())
					&& ((frequency == Frequency.HOURLY) || (frequency == Frequency.DAILY))) {
				LoggerXLSX.close(logFileID);
			}

			if (((day == Date.getLastDayOfYear()) && Date.isLastYear())
					&& ((frequency == Frequency.YEARLY) || (frequency == Frequency.SIMULATION))) {
				LoggerXLSX.close(logFileID);
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/** Executes the logging of the defined elements */
	public void executeLoggingHourly(int hourOfYear) {
		try {
			// Log values
			this.hourOfYear = hourOfYear;
			day = Date.getDayFromHourOfYear(hourOfYear);
			// Initialize log file
			initialize();

			// Add values of each log element
			final List<Object> values = logDataColumns();

			// Write dataLine in file
			LoggerXLSX.writeLine(logFileID, values);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private float getBalance() {
		if (frequency == Frequency.YEARLY) {
			return marketArea.getBalanceDayAhead().getBalanceYearlySum(year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getBalanceDayAhead().getHourlyBalance(year, hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private float getCapacityAllPlants() {
		return marketArea.getAvailabilitiesPlants().getCapacity(year, hourOfYear);
	}

	private float getCurtailment() {
		if (frequency == Frequency.YEARLY) {
			return marketArea.getBalanceDayAhead().getCurtailmentYearlySum(year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getBalanceDayAhead().getHourlyCurtailmentRenewables(year, hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private int getDay() {
		return (hourOfYear / HOURS_PER_DAY) + 1;
	}

	private float getDemandLoad() {
		if (frequency == Frequency.YEARLY) {
			return marketArea.getDemandData().getDemandYearlySum(year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getDemandData().getHourlyDemand(year, hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private float getElectricityProduction(FuelName fuelName) {
		if (fuelName.isRenewableType()) {
			if (frequency == Frequency.YEARLY) {
				return marketArea.getManagerRenewables().getRenewableLoadYearlySum(fuelName, year);
			}
			if (frequency == Frequency.HOURLY) {
				return marketArea.getManagerRenewables().getRenewableLoad(fuelName, year,
						hourOfYear);
			} else {
				return Float.NaN;
			}
		}
		if (frequency == Frequency.YEARLY) {
			return marketArea.getElectricityProduction().getElectricityYearlySum(fuelName, year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getElectricityProduction().getElectricityGeneration(fuelName, year,
					hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private float getElectricityProductionPumpedStorage() {
		if (frequency == Frequency.YEARLY) {
			return marketArea.getElectricityProduction().getElectricityPumpedStorageYearlySum(year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getElectricityProduction().getElectricityPumpedStorage(year,
					hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private float getElectricityProductionSeasonalStorage() {

		if (FuelName.getRenewableTypes().contains(FuelName.HYDRO_SEASONAL_STORAGE)) {
			if (frequency == Frequency.YEARLY) {
				return marketArea.getManagerRenewables()
						.getRenewableLoadYearlySum(FuelName.HYDRO_SEASONAL_STORAGE, year);
			}
			if (frequency == Frequency.HOURLY) {
				return marketArea.getManagerRenewables()
						.getRenewableLoad(FuelName.HYDRO_SEASONAL_STORAGE, year, hourOfYear);
			} else {
				return Float.NaN;
			}
		}
		if (frequency == Frequency.YEARLY) {
			return marketArea.getElectricityProduction()
					.getElectricityYearlySum(FuelName.HYDRO_SEASONAL_STORAGE, year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getElectricityProduction()
					.getElectricityGeneration(FuelName.HYDRO_SEASONAL_STORAGE, year, hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private float getElectricityProductionTotalConventional() {
		if (frequency == Frequency.YEARLY) {
			return marketArea.getElectricityProduction().getElectricityConventionalYearlySum(year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getElectricityProduction().getElectricityConventionalHourly(year,
					hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private float getElectricityProductionTotalRenewable() {
		return marketArea.getManagerRenewables().getTotalRenewableLoad(year, hourOfYear);
	}

	private Float getElectricityProductionTotalRenewableExpected() {
		return marketArea.getManagerRenewables().getTotalRenewableLoad(year, hourOfYear);
	}

	private float getEmissionsHourlyProduction() {
		return marketArea.getCarbonEmissions().getEmissionsHourlyProduction(year, hourOfYear);
	}

	private float getEmissionsHourlyStartUp() {
		return marketArea.getCarbonEmissions().getEmissionsHourlyStartUp(year, hourOfYear);
	}

	private float getExchangeExogenous() {
		if (frequency == Frequency.YEARLY) {
			return marketArea.getExchange().calculateYearlyExchangeSum(year);
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.getExchange().getHourlyFlow(year, hourOfYear);
		} else {
			return Float.NaN;
		}
	}

	private double getExchangeMarketCoupling() {
		if (frequency == Frequency.YEARLY) {
			return marketArea.isMarketCoupling()
					? marketArea.getMarketCouplingOperator().getExchangeFlows()
							.getYearlyFlowByMarketArea(marketArea, year)
					: 0f;
		}
		if (frequency == Frequency.HOURLY) {
			return marketArea.isMarketCoupling()
					? marketArea.getMarketCouplingOperator().getExchangeFlows()
							.getHourlyFlowByMarketArea(marketArea, year, hourOfYear)
					: 0f;
		} else {
			return Float.NaN;
		}
	}

	public String getFileName() {
		return fileName;
	}

	private float getForwardPrice() {
		final int forwardYear = Investor.getYearsLongTermPriceForecastEnd();
		return marketArea.getFuturePrices().getFuturePricesAverages(year + forwardYear, forwardYear,
				hourOfYear);
	}

	public Frequency getFrequency() {
		return frequency;
	}

	private int getHourOfDay() {
		return Date.getHourOfDayFromHourOfYear(hourOfYear);
	}

	private Object getMarginalBid(LogDataColumn columnElement) {
		final MarginalBid bid = marketArea.getElectricityResultsDayAhead()
				.getMarginalBidHourOfYear(year, hourOfYear);
		switch (columnElement) {
			case MARGINAL_BID_FUEL_NAME: {
				return bid.getFuelName();
			}
			case MARGINAL_BID_STARTUP_IN_BID: {
				return bid.getStartUpinBid();
			}
			case MARGINAL_BID_VAR_COSTS: {
				return bid.getVarcosts();
			}
			case MARGINAL_BID_COMMENT: {
				return bid.getComment();
			}
			case MARGINAL_BID_IN_AREA: {
				return bid.getMarketAreaOfBid();
			}
			default:
				logger.error("Marginal Bid is undefined!");
				return "";
		}

	}

	private int getMonth() {
		return Date.getMonth();
	}

	private float getPriceDASimulated() {

		if (frequency == Frequency.YEARLY) {
			return Statistics
					.calcAvg(marketArea.getElectricityResultsDayAhead().getYearlyPrices(year));
		} else if (frequency == Frequency.DAILY) {
			return Statistics
					.calcAvg(marketArea.getElectricityResultsDayAhead().getDailyPrices(year));
		} else if (frequency == Frequency.HOURLY) {
			return marketArea.getElectricityResultsDayAhead().getHourlyPriceOfYear(year,
					hourOfYear);
		}

		return Float.NaN;
	}

	private Object getSecurityOfSupplyLevelWithExchange() {
		return marketArea.getSecurityOfSupply().getSecurityOfSupplyLevelWithExchange(year);
	}

	private Object getSecurityOfSupplyLevelWithoutExchange() {
		return marketArea.getSecurityOfSupply().getSecurityOfSupplyLevelWithoutExchange(year);
	}

	private Object getSecurityOfSupplyLoadWithExchange() {
		return marketArea.getSecurityOfSupply().getSecurityOfSupplyLoadWithExchange(year);
	}

	private Object getSecurityOfSupplyLoadWithoutExchange() {
		return marketArea.getSecurityOfSupply().getSecurityOfSupplyLoadWithoutExchange(year);
	}

	private float getTotalElectricityProduction() {

		return getElectricityProductionTotalConventional()
				+ getElectricityProductionTotalRenewable();
	}

	/**
	 * @param columnElement
	 * @return
	 */
	private Object getValue(LogDataColumn columnElement) {

		// Electricty Production (negative sign)
		if (columnElement.isElectricityProduction()) {
			if (columnElement.fuelName != null) {
				return -getElectricityProduction(columnElement.fuelName);
			}
			if (columnElement == LogDataColumn.ELECTRICITY_PRODUCTION_PUMPED_STORAGE) {
				return -getElectricityProductionPumpedStorage();
			}
			if (columnElement == LogDataColumn.ELECTRICITY_PRODUCTION_SEASONAL_STORAGE) {
				return -getElectricityProductionSeasonalStorage();
			}
			if (columnElement == LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_CONVENTIONAL) {
				return getElectricityProductionTotalConventional();
			}
			if (columnElement == LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES) {
				return getElectricityProductionTotalRenewable();
			}
			if (columnElement == LogDataColumn.ELECTRICITY_PRODUCTION_TOTAL_RENEWABLES_EXPECTED) {
				return getElectricityProductionTotalRenewableExpected();
			}
		}

		// Marginal bid
		if (columnElement.isMarginalBid()) {
			return getMarginalBid(columnElement);
		}

		switch (columnElement) {
			case AVG_EF_PRODUCTION_BASED_EMISSION_CORRECTED_BY_NET_EXPORTS: {
				return getAverageHourlyEFInclExchange();
			}
			case AVG_EF_PRODUCTION_BASED_EMISSION: {
				return getAverageHourlyEFExclExchange();
			}
			case BALANCE: {
				return getBalance();
			}
			case CURTAILMENT: {
				return getCurtailment();
			}
			case CAPACITY_ALL_PLANTS: {
				return getCapacityAllPlants();
			}
			case EMISSIONS_CONSUMPTION_BASED_TOTAL: {
				final float demandSystem = marketArea.getDemandData()
						.getHourlyDemand(Date.getYear(), hourOfYear);

				return demandSystem * marketArea.getCarbonEmissions()
						.getEmissionsFactorDemandBasedHourly(year, hourOfYear);
			}
			case DAY: {
				return getDay();
			}
			case DEMAND: {
				return getDemandLoad();
			}
			case ELECTRICITY_PROD_TOTAL: {
				return getTotalElectricityProduction();
			}
			case EMISSION_TOTAL_HOURLY_PRODUCTION: {
				return getEmissionsHourlyProduction();
			}
			case EMISSION_TOTAL_HOURLY_STARTUP: {
				return getEmissionsHourlyStartUp();
			}
			case EMISSION_LIGNITE: {
				return marketArea.getCarbonEmissions().getCarbonLignite(year, hourOfYear);
			}
			case EMISSION_LIGNITE_START_UP: {
				return marketArea.getCarbonEmissions().getCarbonLigniteStartup(year, hourOfYear);
			}
			case EMISSION_CLEAN_LIGNITE: {
				return marketArea.getCarbonEmissions().getCarbonCleanLignite(year, hourOfYear);
			}
			case EMISSION_CLEAN_LIGNITE_START_UP: {
				return marketArea.getCarbonEmissions().getCarbonCleanLigniteStartup(year,
						hourOfYear);
			}
			case EMISSION_COAL: {
				return marketArea.getCarbonEmissions().getCarbonCoal(year, hourOfYear);
			}
			case EMISSION_COAL_START_UP: {
				return marketArea.getCarbonEmissions().getCarbonCoalStartup(year, hourOfYear);
			}
			case EMISSION_CLEAN_COAL: {
				return marketArea.getCarbonEmissions().getCarbonCleanCoal(year, hourOfYear);
			}
			case EMISSION_CLEAN_COAL_START_UP: {
				return marketArea.getCarbonEmissions().getCarbonCleanCoalStartup(year, hourOfYear);
			}
			case EMISSION_MINE: {
				return marketArea.getCarbonEmissions().getCarbonMineGas(year, hourOfYear);
			}
			case EMISSION_SEWAGEGAS: {
				return marketArea.getCarbonEmissions().getCarbonSewageGas(year, hourOfYear);
			}
			case EMISSION_WASTE: {
				return marketArea.getCarbonEmissions().getCarbonWaste(year, hourOfYear);
			}
			case EMISSION_GAS: {
				return marketArea.getCarbonEmissions().getCarbonGas(year, hourOfYear);
			}
			case EMISSION_GAS_START_UP: {
				return marketArea.getCarbonEmissions().getCarbonGasStartup(year, hourOfYear);
			}
			case EMISSION_CLEAN_GAS: {
				return marketArea.getCarbonEmissions().getCarbonCleanGas(year, hourOfYear);
			}
			case EMISSION_CLEAN_GAS_START_UP: {
				return marketArea.getCarbonEmissions().getCarbonCleanGasStartup(year, hourOfYear);
			}
			case EMISSION_OIL: {
				return marketArea.getCarbonEmissions().getCarbonOil(year, hourOfYear);
			}
			case EMISSION_OIL_START_UP: {
				return marketArea.getCarbonEmissions().getCarbonOilStartup(year, hourOfYear);
			}
			case EMISSION_PEAKER: {
				return marketArea.getCarbonEmissions().getCarbonPeaker(year, hourOfYear);
			}
			case EXCHANGE_EXOGENOUS: {
				return getExchangeExogenous();
			}
			case EXCHANGE_MARKET_COUPLING: {
				return getExchangeMarketCoupling();
			}
			case FORWARD_PRICE: {
				return getForwardPrice();
			}
			case HOUR_OF_DAY: {
				return getHourOfDay();
			}
			case HOUR_OF_YEAR: {
				return hourOfYear;
			}
			case MONTH: {
				return getMonth();
			}
			case PRICE_DAY_AHEAD_SIMULATED: {
				return getPriceDASimulated();
			}

			case SECURITY_OF_SUPPLY_LOAD_WITH_EXCHANGE: {
				return getSecurityOfSupplyLoadWithExchange();
			}
			case SECURITY_OF_SUPPLY_LOAD_WITHOUT_EXCHANGE: {
				return getSecurityOfSupplyLoadWithoutExchange();
			}
			case SECURITY_OF_SUPPLY_LEVEL_WITH_EXCHANGE: {
				return getSecurityOfSupplyLevelWithExchange();
			}
			case SECURITY_OF_SUPPLY_LEVEL_WITHOUT_EXCHANGE: {
				return getSecurityOfSupplyLevelWithoutExchange();
			}

			case YEAR: {
				return year;
			}
			case PROD_TEST: {
				return marketArea.getElectricityProduction().getTotalElectricityYearly(year,
						hourOfYear);
			}

			default:
				logger.error("Undefined element " + columnElement);
				return "";
		}
	}

	/** Production-based average hourly emission factor */
	private double getAverageHourlyEFExclExchange() {
		final double exch = getExchangeExogenous() + getExchangeMarketCoupling();
		final float emissions = getEmissionsHourlyProduction()
				+ marketArea.getCarbonEmissions().getEmissionsHourlyStartUp(year, hourOfYear);

		return emissions / (getDemandLoad() + exch);
	}

	/**
	 * Production-based average hourly emission factor corrected by net exports
	 */
	private float getAverageHourlyEFInclExchange() {
		return (getEmissionsHourlyProduction()
				+ marketArea.getCarbonEmissions().getEmissionsHourlyStartUp(year, hourOfYear))
				/ (getDemandLoad());
	}

	public synchronized void initialize() {
		// Check whether log file has been initialized
		if (!logInitialized) {
			year = Date.getYear();
			String currentFileName;
			// Set file name (includes the current year)
			if (frequency != Frequency.YEARLY) {
				currentFileName = marketArea.getInitialsUnderscore() + fileName + "_" + year;
			} else {
				currentFileName = marketArea.getInitialsUnderscore() + fileName;
			}

			final List<ColumnHeader> columns = new ArrayList<>();
			// Add title and unit of each log element
			for (final LogData logElement : dataColumns) {

				// Log element has more than one value
				if (logElement instanceof LogDataColumnSeveral) {
					for (final LogDataColumn columnElement : ((LogDataColumnSeveral) logElement)
							.getLogDataColumns()) {
						// Add title and unit
						columns.add(new ColumnHeader(columnElement.toString(), columnElement.unit));
					}
				}
				// Log element has only one element
				else if (logElement instanceof LogDataColumn) {
					columns.add(new ColumnHeader(logElement.toString(),
							((LogDataColumn) logElement).unit));
				} else {
					logger.warn("Logtype is undefined!");
				}
			}

			// Set logID
			logFileID = LoggerXLSX.newLogObject(logFolder, currentFileName, description, columns,
					marketArea.getIdentityAndNameLong(), frequency);

			// Set logInitialized true
			logInitialized = true;
		}

		if ((Frequency.YEARLY != frequency) && (day == Date.getLastDayOfYear())) {
			logInitialized = false;
		}
	}

	private List<Object> logDataColumns() {
		final List<Object> values = new ArrayList<>();
		for (final LogData logElement : dataColumns) {

			// Log element has more than one value
			if (logElement instanceof LogDataColumnSeveral) {
				for (final LogDataColumn columnElement : ((LogDataColumnSeveral) logElement)
						.getLogDataColumns()) {
					values.add(getValue(columnElement));
				}
			}
			// Log element has only one element
			else if (logElement instanceof LogDataColumn) {
				values.add(getValue((LogDataColumn) logElement));
			} else {
				logger.warn("Logtype is undefined!");
			}

		}
		return values;
	}

	private void setFirstDataColumn() {
		if (frequency == Frequency.HOURLY) {
			dataColumns.add(0, LogDataColumn.HOUR_OF_YEAR);
		} else if (frequency == Frequency.DAILY) {
			dataColumns.add(0, LogDataColumn.DAY);
		} else if (frequency == Frequency.YEARLY) {
			dataColumns.add(0, LogDataColumn.YEAR);
		}
	}
}