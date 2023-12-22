package markets.trader.future.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.powerplant.GenerationData;
import data.storage.PumpStoragePlant;
import markets.trader.spot.hydro.SeasonalStorageTrader;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import simulations.scheduling.SeasonAstronomical;
import supply.invest.Investment;
import supply.invest.Investor;
import supply.powerplant.CostCap;
import supply.powerplant.PlantOption;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.Unit;

/**
 * 
 * @author Massimo Genoese, Florian Zimmermann
 *
 */
public class PriceForecastFuture {

	protected static Map<MarketArea, Float> anticipatedHydrogenProduction = new ConcurrentHashMap<>();
	protected static final float AVAILABILITY_HYDRO = 0.50f;
	protected static final float AVAILABILITY_INTERCONNECTORS = 0.7f;
	protected static final float AVAILABILITY_PUMPERS = 0.3f;

	/** Compress log files */
	protected static boolean compress = true;
	protected static Map<MarketArea, Map<MarketArea, Map<Integer, Float>>> exchangeFlowForecast = new ConcurrentHashMap<>();
	/** If residual demand is negative, scarcity factor is set on value: */
	protected static final float FACTOR_SCARCITY_MAX = 10f;

	/** If supply is smaller than demand scarcity factor is set on value: */
	protected static final float FACTOR_SCARCITY_MIN = 0.95f;
	protected static final float FACTOR_VARIATION_SEASONAL = 0.05f;

	protected static Map<MarketArea, Map<Integer, Map<Integer, Float>>> forewardPriceWithAdditionalPlants = new ConcurrentHashMap<>();

	private static Map<MarketArea, Map<Integer, Float>> forwardPriceListCappedCurrentYear;

	/**
	 * How much of unit capacity is available for each power plant for
	 * calculating future prices Start Year of forecast,, Market Area, year
	 * offset, time series with forecast prices
	 */
	protected static Map<Integer, Map<MarketArea, Map<Integer, Map<Integer, Float>>>> forwardPrices = new ConcurrentHashMap<>();

	protected static Map<MarketArea, Map<Integer, Float>> forwardPricesStorage = new ConcurrentHashMap<>();

	protected static boolean logData = true;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	protected static final Logger logger = LoggerFactory // NOPMD
			.getLogger(PriceForecastFuture.class.getName());

	/** ID for logging price forecast */
	protected static Map<MarketArea, Integer> logIDPriceForecastXLSX = new ConcurrentHashMap<>();

	/** Default max price if demand cannot be met */
	protected static float MAX_HOURLY_PRICE = 3000f;
	/**
	 * Indicates whether in hours where demand cannot be met the maximum price
	 * is set automatically
	 */
	protected static boolean noMaxPriceForHighDemand = true;
	protected static Map<MarketArea, Map<Integer, Float>> operationHydrogenProduction = new ConcurrentHashMap<>();
	protected static Map<MarketArea, Map<Integer, Float>> operationSeasonalStorage = new ConcurrentHashMap<>();
	protected static final float PART_STRATEGIC_COSTS = 0.15f;
	protected static final float PERCENTAGE_WEEK_DAY = 5.0f / Date.DAYS_PER_WEEK;

	protected static final float PERCENTAGE_WEEKEND_DAY = 2.0f / Date.DAYS_PER_WEEK;

	protected static final float PRICE_PUMPED_STORAGE = 100;

	private static final Random random = new Random(Settings.getRandomNumberSeed());

	protected static AtomicInteger worksheetNameCounter = new AtomicInteger(0);

	private static final int YEAR_OFFSET_MAX = 6;

	// Reset for multiruns
	public static void endOfSimulation() {
		forwardPrices = new ConcurrentHashMap<>();
		forewardPriceWithAdditionalPlants = new ConcurrentHashMap<>();

	}

	/**
	 * This map does not consider static exchange
	 * 
	 * @return
	 */
	public static Map<MarketArea, Map<MarketArea, Map<Integer, Float>>> getExchangeFlowForecast() {
		return exchangeFlowForecast;
	}

	/** Map of hourly forward prices in future year under consideration */
	private static Map<Integer, Float> getForwardPriceList(MarketArea marketArea, int futureYear) {
		final Map<Integer, Map<Integer, Float>> priceForecast = PriceForecastFutureOptimization
				.calcMarketCouplingForecastOptimization(marketArea.getModel().getMarketAreas())
				.get(marketArea);
		return getPriceForecastNext(futureYear, priceForecast);
	}

	/** List of hourly forward prices in future year under consideration */
	public static Map<Integer, Float> getForwardPriceListCapped(int futureYear,
			MarketArea marketArea) {
		try {
			final Map<Integer, Float> forwardPriceListCapped = new HashMap<>();
			final float forwardPriceMax = marketArea.getPriceForwardMaximum();
			final Map<Integer, Float> forwardPriceList = getForwardPriceList(marketArea,
					futureYear);
			for (final Integer hourOfYear : forwardPriceList.keySet()) {
				forwardPriceListCapped.put(hourOfYear,
						Math.min(forwardPriceList.get(hourOfYear), forwardPriceMax));
			}
			return forwardPriceListCapped;
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	/** List of hourly forward prices in future year under consideration */
	public static Map<MarketArea, Map<Integer, Map<Integer, Float>>> getForwardPriceListCapped(
			Set<MarketArea> marketAreas, List<Investment> additionalPlants) {
		try {
			// Calculate forecast with additional plants
			final Map<MarketArea, Map<Integer, Map<Integer, Float>>> forwardPriceList = PriceForecastFutureOptimization
					.calcMarketCouplingForecastOptimizationWithPlants(marketAreas,
							additionalPlants);
			// Cap prices
			final Map<MarketArea, Map<Integer, Map<Integer, Float>>> forwardPriceListCapped = new HashMap<>();
			for (final MarketArea marketArea : marketAreas) {
				// add MarketArea
				forwardPriceListCapped.put(marketArea, new LinkedHashMap<>());

				final float forwardPriceMax = marketArea.getPriceForwardMaximum();
				for (final Integer year : forwardPriceList.get(marketArea).keySet()) {
					forwardPriceListCapped.get(marketArea).put(year, new LinkedHashMap<>());
					for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
						forwardPriceListCapped.get(marketArea).get(year).put(hourOfYear,
								Math.min(forwardPriceList.get(marketArea).get(year).get(hourOfYear),
										forwardPriceMax));
					}
				}
			}
			return forwardPriceListCapped;
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	/** List of hourly forward prices in the next year */
	public static Map<MarketArea, Map<Integer, Float>> getForwardPriceListCurrentYear() {
		return forwardPriceListCappedCurrentYear;
	}
	/** List of hourly forward prices in the next year */
	public static Map<MarketArea, Map<Integer, Float>> getForwardPriceListStorage(
			Set<MarketArea> marketAreas, Map<MarketArea, Map<Integer, Float>> operationSeasonal,
			int iteration) {
		try {
			operationSeasonalStorage = operationSeasonal;
			final Map<MarketArea, Map<Integer, Float>> forwardPriceListCapped = new ConcurrentHashMap<>();
			final float forwardPriceMax = 1000;
			Map<MarketArea, Map<Integer, Float>> forwardPriceList = null;

			for (final MarketArea marketArea : marketAreas) {
				forwardPriceList = getPriceForecastSeasonalStorage(marketArea, iteration);
				// Only one marketarea is needed
				break;

			}

			// Use not simple version of for loop due to thread safety regarding
			// random numbers
			for (final MarketArea marketArea : marketAreas) {
				forwardPriceListCapped.put(marketArea, new HashMap<>());
				// Use not simple version due to thread safety
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
					forwardPriceListCapped.get(marketArea).put(hourOfYear,
							(float) Math.min(
									Math.max(0,
											forwardPriceList.get(marketArea).get(hourOfYear)
													+ (0.002 * random.nextGaussian())),
									forwardPriceMax));

				}
			}
			forwardPriceListCappedCurrentYear = forwardPriceListCapped;
			return forwardPriceListCapped;
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * Selects future demand from database and adds electric vehicle, hydrogen
	 * and ancillary service demand. Hourly EV demand of the current year is
	 * scaled to demand of <b>futureYear</b>.
	 * 
	 * @param marketArea
	 * @param futureYear
	 *            for which futureDemand is needed
	 * @return list of hourly energy demands, incl. losses and electric vehicles
	 */
	protected static Map<Integer, Float> getFutureDemand(MarketArea marketArea, int futureYear) {
		// Demand load (including losses)
		final Map<Integer, Float> futureDemand = marketArea.getDemandData()
				.getYearlyDemandMap(futureYear);

		// add hydrogen values
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			// Static demand of not coupled market areas
			final float exchangeStatic = marketArea.getExchange().getHourlyFlowForecast(futureYear,
					hourOfYear);
			futureDemand.put(hourOfYear, futureDemand.get(hourOfYear)
					+ operationHydrogenProduction.get(marketArea).get(hourOfYear) + exchangeStatic);

		}

		return futureDemand;
	}

	protected static List<CostCap> getFutureMeritOrder(MarketArea marketArea, int yearOffset,
			int iteration) {

		final int yearFutureMeritOrder = Math.min(yearOffset, YEAR_OFFSET_MAX);
		final List<CostCap> futureMeritOrder = new ArrayList<>(
				marketArea.getGenerationData().getMeritOrderUnits(setYear(yearFutureMeritOrder)));

		// Seasonal Storage

		final List<CostCap> seasonalStoragePlants = getSeasonalStorageCapacities(marketArea,
				setYear(yearFutureMeritOrder), iteration);
		futureMeritOrder.addAll(seasonalStoragePlants);

		GenerationData.setCumulatedNetCapacity(futureMeritOrder);
		marketArea.getFutureMeritOrders().addMeritOrder(Date.getYear(), "a",
				setYear(yearFutureMeritOrder), futureMeritOrder);

		if (futureMeritOrder.isEmpty()) {
			logger.error(marketArea.getInitialsBrackets()
					+ "Error in Loading Future Merit Order - no plants available in future");
		}

		return futureMeritOrder;
	}

	protected static List<PumpStoragePlant> getFutureStorageUnits(MarketArea marketArea,
			int yearOffset, List<PlantOption> additionalPlants) {
		final List<PumpStoragePlant> allStoragePlants = new ArrayList<>();
		allStoragePlants
				.addAll(marketArea.getPumpStorage().getAvailablePumpers(setYear(yearOffset)));

		for (final PlantOption storagePlantOption : additionalPlants) {
			if ((storagePlantOption.getMarketArea().getMarketAreaType() != marketArea
					.getMarketAreaType()) || !storagePlantOption.isStorage()) {
				continue;
			}
			if ((yearOffset) >= storagePlantOption.getConstructionTime()) {
				allStoragePlants.add(new PumpStoragePlant(storagePlantOption));
			}
		}
		Collections.sort(allStoragePlants);
		return allStoragePlants;
	}

	/**
	 * Find the next price forecast that is exactly or smaller to the requested
	 * year
	 */
	private static Map<Integer, Float> getPriceForecastNext(int year,
			Map<Integer, Map<Integer, Float>> priceForecast) {
		// max year
		final int yearMax = Collections.max(priceForecast.keySet());
		if (year > yearMax) {
			return priceForecast.get(yearMax);
		}
		// min year (should actually not happen)
		final int yearMin = Collections.min(priceForecast.keySet());
		if (year < yearMin) {
			return priceForecast.get(yearMin);
		}
		// if year matches
		if (priceForecast.containsKey(year)) {
			return priceForecast.get(year);
		}
		// use year before
		return getPriceForecastNext(year - 1, priceForecast);
	}

	/** Map of hourly forward prices in future year under consideration */
	private static Map<MarketArea, Map<Integer, Float>> getPriceForecastSeasonalStorage(
			MarketArea marketArea, int iteration) {
		return PriceForecastFutureOptimization
				.priceForecastSeasonalStorage(marketArea.getModel().getMarketAreas(), iteration);

	}

	protected static List<CostCap> getSeasonalStorageCapacities(MarketArea marketArea, int year,
			int iterationCount) {
		// In order to not disturb price forecast bid start value for the first
		// iterations
		final List<CostCap> additionalCapacities = new ArrayList<>();
		// possible future operation
		for (final SeasonalStorageTrader trader : marketArea.getSeasonalStorageTraders()) {

			float remainingIteration = 0;
			final float costsVarCone = 50f;
			final float capacity = trader.getAvailableTurbineCapacity();

			if ((iterationCount != 0)
					&& (iterationCount <= (SeasonalStorageTrader.getIterations() * 0.33f))) {
				remainingIteration = 0.33f;
			}
			final CostCap seasonalStorageOption = new CostCap();
			seasonalStorageOption.setFuelName(FuelName.HYDRO_SEASONAL_STORAGE);
			seasonalStorageOption.setNetCapacity(capacity * remainingIteration);
			seasonalStorageOption.setVarCostsTotal(costsVarCone);
			additionalCapacities.add(seasonalStorageOption);
		}
		return additionalCapacities;
	}

	protected static float getVariationFactorSeasonal(int hourOfYear) {
		float seasonFactorHour = 0;
		if ((SeasonAstronomical.getSeason(hourOfYear) == SeasonAstronomical.SPRING)
				|| (SeasonAstronomical.getSeason(hourOfYear) == SeasonAstronomical.SUMMER)) {
			seasonFactorHour -= FACTOR_VARIATION_SEASONAL;
		} else {
			seasonFactorHour += FACTOR_VARIATION_SEASONAL;
		}
		return seasonFactorHour;
	}

	public static int getYearOffsetMax() {
		return YEAR_OFFSET_MAX;
	}

	protected static void logForecast(MarketArea marketArea, int numberOfHoursPeakerNormalPrice,
			int numberOfHoursMaxPrice, int numberOfHoursOnlyRenewables, final int year,
			Map<Integer, Float> forwardPrice, final List<Float> demand,
			final List<Float> renewableLoad, final List<Float> exchangeFlows,
			final List<Float> pumpedStorageFlows, float seasonalStorageCapacity,
			final List<Float> sheddableFlows, final List<Float> shiftableFlows,
			final Map<FuelType, List<Float>> generationByFuel, List<Float> capacityDeficit,
			Map<Integer, Float> futureScarcity, Map<Integer, Float> cummulatedCapacity) {
		// Logging of price forecast only for forward trader, not for
		// investmentplanners
		if (logData || ((Date.getDayOfYear() % 365) == 1)) {
			final String worksheetName = String.valueOf(year) + "_"
					+ worksheetNameCounter.incrementAndGet();
			logInitializePriceForecast(marketArea, worksheetName, year);

			for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
				final List<Object> values = new ArrayList<>();
				values.add(hourOfYear);
				if (demand != null) {
					values.add(demand.get(hourOfYear));
				}
				if (renewableLoad != null) {
					values.add(renewableLoad.get(hourOfYear));
				}
				values.add(forwardPrice.get(hourOfYear));

				if (capacityDeficit != null) {
					values.add(capacityDeficit.get(hourOfYear));
				}
				if (cummulatedCapacity != null) {
					values.add(cummulatedCapacity.get(hourOfYear));
				}
				if (futureScarcity != null) {
					values.add(futureScarcity.get(hourOfYear));
				}
				if (exchangeFlows != null) {
					values.add(exchangeFlows.get(hourOfYear));
				}
				if (pumpedStorageFlows != null) {
					values.add(pumpedStorageFlows.get(hourOfYear));
				}

				values.add(seasonalStorageCapacity);
				if (shiftableFlows != null) {
					values.add(shiftableFlows.get(hourOfYear));
				}
				if (sheddableFlows != null) {
					values.add(sheddableFlows.get(hourOfYear));
				}
				if (generationByFuel != null) {
					for (final FuelType fuelType : FuelType.values()) {
						values.add(generationByFuel.get(fuelType).get(hourOfYear));
					}
				}
				LoggerXLSX.writeLine(logIDPriceForecastXLSX.get(marketArea), worksheetName, values);
			}

			// Log extreme situations
			logger.info(marketArea.getInitialsBrackets() + year + ": only RES ("
					+ numberOfHoursOnlyRenewables + " h); peaker normal ("
					+ numberOfHoursPeakerNormalPrice + " h); VoLL max price ("
					+ numberOfHoursMaxPrice + " h)");
		}
	}

	/** Initialize log file for price forecast */
	protected static synchronized void logInitializePriceForecast(MarketArea marketArea,
			String worksheetName, int year) {
		final String description = "Price forecast in year " + Date.getYear()
				+ " for year: (yearOffset: " + year + ")";
		final Folder logFolder = Folder.INVESTMENT;
		final String fileName = marketArea.getInitialsUnderscore() + "Price_Forecast_In_"
				+ Date.getYear() + worksheetName;

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("hour_Of_Year", Unit.HOUR));
		columns.add(new ColumnHeader("load", Unit.CAPACITY));
		columns.add(new ColumnHeader("renewables", Unit.NONE));
		columns.add(new ColumnHeader("price", Unit.ENERGY_PRICE));
		columns.add(new ColumnHeader("capacityDeficit", Unit.CAPACITY));
		columns.add(new ColumnHeader("CummulatedCapacity", Unit.CAPACITY));
		columns.add(new ColumnHeader("future scarcity", Unit.NONE));
		columns.add(new ColumnHeader("Exchange", Unit.CAPACITY));
		columns.add(new ColumnHeader("Pump storage trader", Unit.CAPACITY));
		columns.add(new ColumnHeader("seasonal hydro storage trader", Unit.CAPACITY));
		columns.add(new ColumnHeader("Shiftable loads", Unit.CAPACITY));
		columns.add(new ColumnHeader("Sheddable loads", Unit.CAPACITY));
		for (final FuelType fuelType : FuelType.values()) {
			columns.add(new ColumnHeader(fuelType.toString(), Unit.CAPACITY));
		}

		final Frequency frequency = Frequency.HOURLY;

		// Check whether file has already been initialized

		if (!logIDPriceForecastXLSX.containsKey(marketArea)
				|| (logIDPriceForecastXLSX.get(marketArea) == -1)) {
			// Initialize xlsx log file
			logIDPriceForecastXLSX.put(marketArea,
					LoggerXLSX.newLogObject(logFolder, fileName, description, worksheetName,
							columns, marketArea.getIdentityAndNameLong(), frequency));
		} else {
			LoggerXLSX.createNewSheet(logIDPriceForecastXLSX.get(marketArea), description,
					worksheetName, columns, frequency);
		}
	}

	public static void recalculate(Set<MarketArea> marketAreas) {
		logger.info("Recalculate price forecast longterm");
		PriceForecastFutureOptimization.recalculate(marketAreas);
	}

	protected static Map<FuelName, Float> setAvailabilities(MarketArea marketArea,
			List<CostCap> futureMeritOrder) {
		final Map<FuelName, Float> availability = new HashMap<>();
		for (final CostCap meritOrderUnit : futureMeritOrder) {
			final float weekDayAvailability = marketArea.getAvailabilityFactors()
					.getAvailabilityFactors(meritOrderUnit.getFuelName(), true);
			final float weekendAvailability = marketArea.getAvailabilityFactors()
					.getAvailabilityFactors(meritOrderUnit.getFuelName(), false);
			final float averageYearlyAvailfactor = (PERCENTAGE_WEEK_DAY * weekDayAvailability)
					+ (PERCENTAGE_WEEKEND_DAY * weekendAvailability);
			availability.put(meritOrderUnit.getFuelName(), averageYearlyAvailfactor);
		}
		// Interconnectors
		availability.put(FuelName.INTERCONNECTOR, AVAILABILITY_INTERCONNECTORS);
		// Seasonal Storage
		availability.put(FuelName.HYDRO_SEASONAL_STORAGE, AVAILABILITY_HYDRO);

		return availability;
	}

	protected static int setYear(int yearOffset) {
		int year = Date.getYear() + yearOffset;
		// take future year longer than forecast
		if (yearOffset >= (Investor.getYearsLongTermPriceForecastEnd() + 1)) {
			year = Date.getYear() + 10;
		}
		return year;
	}
}