package supply.powerplant;

import static simulations.scheduling.Date.DAYS_PER_YEAR;
import static simulations.scheduling.Date.HOURS_PER_DAY;
import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.scheduling.Date;
import supply.powerplant.capacity.CapacityType;
import supply.powerplant.state.Running;
import tools.math.Statistics;
import tools.types.Startup;
import tools.types.Unit;

/**
 * Specific data structure for operating generation units
 *
 * @since 31.03.2005
 * @author Massimo Genoese
 */
public class Plant extends PlantAbstract implements Comparable<Plant> {

	private class CapacityData {

		private final List<Float> capacityData;

		public CapacityData() {
			capacityData = Collections.synchronizedList(
					new ArrayList<>(Collections.nCopies(Date.HOURS_PER_YEAR, 0f)));
		}

		public CapacityData(float initialValue) {
			capacityData = Collections.synchronizedList(
					new ArrayList<>(Collections.nCopies(Date.HOURS_PER_YEAR, initialValue)));
		}

		/**
		 * Get the capacity for the specified <code>hourOfYear</code>
		 *
		 * @param hourOfYear
		 *            [0..]
		 * @return capacity
		 */
		public float getCapacityData(int hourOfYear) {
			if (hourOfYear >= Date.HOURS_PER_YEAR) {
				return getCapacityData(hourOfYear - 1);
			}
			return capacityData.get(hourOfYear);
		}

		/**
		 * Set <code>capacity</code> for the specified <code>hourOfYear</code>
		 *
		 * @param hourOfYear
		 *            [0..]
		 * @param capacity
		 */
		public void setCapacityData(int hourOfYear, float capacity) {
			capacityData.set(hourOfYear, capacity);
		}

		@Override
		public String toString() {
			return capacityData.toString();
		}
	}

	public enum CostType {
		CARBON,
		FUEL,
		OTHER_VAR,
		START_UP;
	}

	public enum RevenueType {
		ELECTRICTY_DAY_AHEAD
	}

	/** Threshold for rounding errors */
	private static final float EPSILON = 0.001f;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Plant.class.getName());

	private String bnaNumber;
	/** Map storing different kinds of hourly capacity data for current year */
	private final Map<CapacityType, CapacityData> capacitiesHourlyCurrentYear = new HashMap<>();

	/** Daily emissions of a plant */
	private float carbonEmissionsDaily = 0;
	private Map<Integer, Float> carbonEmissionsDailyMap = new HashMap<>();
	private Map<Integer, Float> carbonEmissionsYearly = new HashMap<>();
	private final Map<Integer, Float> costsYearlyDayAheadMarket = new HashMap<>();
	private final Map<Integer, Float> costsYearlyDayAheadMarketFuel = new HashMap<>();
	private final Map<Integer, Float> costsYearlyDayAheadMarketCarbon = new HashMap<>();
	private final Map<Integer, Float> costsYearlyDayAheadMarketOther = new HashMap<>();
	private final Map<Integer, Float> costsYearlyStartUp = new HashMap<>();
	private final Map<Integer, Float> costsYearlyOperationsAndMaintenanceFixed = new HashMap<>();
	/** Startup costs in hourly bid (current day) */
	private final float[] dayAheadBidHourlyStartUpCosts = new float[HOURS_PER_DAY];
	/** Hourly electricty production (current year) [MWh] */
	private ArrayList<Float> electricityProduction;
	private Map<Integer, Float> electricityProductionYearly = new HashMap<>();
	/** Installed heat capacity [MWh] */
	private float heatCapacity;
	/** Hourly electricty production (current year) [MWh] */
	private ArrayList<Float> heatProduction;

	/** Hourly profit on current day */
	private final float[] hourlyProfit = new float[HOURS_PER_DAY];
	private final float[] hoursOfStartUp = new float[HOURS_PER_DAY];
	private final Map<Integer, Float> incomeYearlyDayAheadMarket = new HashMap<>();
	/** Name of location (StandortName) */
	private String locationName;
	private MarketArea marketArea;

	/**
	 * Indicates whether plant data has been changed by model result (e.g. newly
	 * constructed plant, )
	 */
	private float numberOfStartUps = 0;
	private int operatingHours;
	private final Map<Integer, Integer> operatingHoursYearlyMap = new HashMap<>();
	/** ID of owner */
	private int ownerID;
	private boolean[] plantRunning;
	/** profit of primary reserve */
	private final Map<Integer, Float> profitYearly = new HashMap<>();
	private float safeTotalVarCosts;

	/** Number of hot starts of the power plant */
	private Map<Integer, Map<Startup, Integer>> startupCounter = new HashMap<>();
	/** sometimes variable costs aren't sufficient for bidding */
	private final float[] strategicCosts = new float[HOURS_PER_DAY];
	private final float[] strategicCostsExCO2 = new float[HOURS_PER_DAY];
	/** Current thermal state of unit [1=cold, 2=warm, 3=hot, 0=undefined] */
	private int thermalState = 3;
	/** Name of unit (EinheitName) */
	private String unitName;
	private float yearlyEmissionCosts = 0;

	private float latitude;
	private float longitude;

	public Plant(MarketArea marketArea) {
		this.marketArea = marketArea;
	}

	/**
	 * @param firstHourOfToday
	 * 
	 */
	public void calculateDailyRunningHours(int firstHourOfToday) {

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			if (electricityProduction.get(firstHourOfToday + hour) > 0) {
				operatingHours++;
				if (operatingHours == HOURS_PER_YEAR) {

					logger.warn("PLant is running 8760 hours per year (id: " + getUnitID() + ", "
							+ getFuelName() + ", " + getNetCapacity() + " "
							+ Unit.CAPACITY.getUnit() + ")");

				} else if (operatingHours > HOURS_PER_YEAR) {
					logger.error("Plant is running more than 8760 per year (id: " + getUnitID()
							+ ", " + getFuelName() + ", " + getNetCapacity() + " "
							+ Unit.CAPACITY.getUnit() + ")");
				}
			}
		}

		if (Date.isLastDayOfYear()) {
			operatingHoursYearlyMap.put(Date.getYear(), operatingHours);
		}

	}

	/**
	 * @param plant
	 * @return The number of years that plant made negative profit <i>starting
	 *         last year.</i>
	 */
	public int calculateYearsOfContinuousNegativeProfit() {

		final Float profitThisYear = getProfitYearly(Date.getYear());
		if ((profitThisYear != null) && (profitThisYear > 0)) {
			return 0;
		}

		final int startYear = Date.getStartYear();
		final int lastYear = Date.getYear() - 1;
		int yearsOfContinuousNegativeProfit = 0;

		for (int year = lastYear; year >= startYear; year--) {
			final Float profitYearly = getProfitYearly(year);
			if ((profitYearly != null) && (profitYearly < 0)) {
				yearsOfContinuousNegativeProfit++;
			} else {
				break;
			}
		}
		return yearsOfContinuousNegativeProfit;
	}

	/** check for total in a hour */
	private void checkProduction(int hour) {
		if ((electricityProduction.get(hour) - getNetCapacity()) > EPSILON) {
			logger.error("Production is above capacity.");
		} else if (electricityProduction.get(hour) < 0) {
			logger.error(Date.getYear() + "/" + Date.getDayOfYear() + "/" + hour + "/" + hour
					+ " Production is below 0: " + electricityProduction.get(hour) + ", UnitID: "
					+ getUnitID() + ", NetCapacity: " + getNetCapacity());

			printCapacityUsage(hour);
		}

	}
	/** check for total in a day */
	private void checkProductionToday(int hour) {

		final int firstHourOfToday = Date.getFirstHourOfToday();
		final int hourOfYear = firstHourOfToday + hour;
		final float production = electricityProduction.get(hourOfYear);
		final float diff = production - getNetCapacity();

		if (diff > EPSILON) {
			logger.error(Date.getYear() + "/" + Date.getDayOfYear() + "/" + hour + "/"
					+ firstHourOfToday + " Production is above capacity: " + diff + ", UnitID: "
					+ getUnitID() + ", NetCapacity: " + getNetCapacity() + ", Production "
					+ electricityProduction.get(hourOfYear));

			printCapacityUsage(hourOfYear);

		} else if (production < 0) {
			logger.error(Date.getYear() + "/" + Date.getDayOfYear() + "/" + hour + "/"
					+ firstHourOfToday + " Production is below 0: " + production + ", UnitID: "
					+ getUnitID() + ", NetCapacity: " + getNetCapacity());

			printCapacityUsage(hourOfYear);
		}
	}

	@Override
	public int compareTo(Plant other) {

		if (costsVar < other.costsVar) {
			return -1;
		} else if (costsVar > other.costsVar) {
			return 1;
		}

		if (getNetCapacity() > other.getNetCapacity()) {
			return -1;
		} else if (getNetCapacity() < other.getNetCapacity()) {
			return 1;
		}

		if (getUnitID() < other.getUnitID()) {
			return -1;
		} else if (getUnitID() > other.getUnitID()) {
			return 1;
		}

		return 0;
	}

	public void countStartUps() {
		final int year = Date.getYear();

		if (startupCounter.get(year) == null) {
			startupCounter.put(year, new HashMap<>());
			for (final Startup startup : Startup.values()) {
				startupCounter.get(year).put(startup, 0);
			}
		}

		final Map<Startup, Integer> startupCounterCurrentYear = startupCounter.get(year);

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {

			// Check if plant was switched on in this hour
			if (isRunningHour(hour) && !isRunningHour(hour - 1)) {
				// Determine start type
				if (isRunningRange(hour - Date.HOT_STARTUP_LENGTH, hour - 1)) {
					startupCounterCurrentYear.put(Startup.HOT,
							startupCounterCurrentYear.get(Startup.HOT) + 1);
				} else if (isRunningRange(hour - Date.WARM_STARTUP_LENGTH,
						hour - (Date.HOT_STARTUP_LENGTH + 1))) {
					startupCounterCurrentYear.put(Startup.WARM,
							startupCounterCurrentYear.get(Startup.WARM) + 1);
				} else {
					startupCounterCurrentYear.put(Startup.COLD,
							startupCounterCurrentYear.get(Startup.COLD) + 1);
				}
			}
		}
	}

	public void decreaseProduction(int hour, float decrease) {
		if (decrease < 0) {
			logger.error("Only positive values are allowed.");
		}

		final int hourOfYear = Date.getFirstHourOfToday() + hour;

		electricityProduction.set(hourOfYear, electricityProduction.get(hourOfYear) - decrease);
		plantRunning[hourOfYear] = electricityProduction.get(hourOfYear) > 0 ? true : false;

		if (electricityProduction.get(hourOfYear) < 0f) {
			logger.error("Production is below zero!");
		}

	}

	public void decreaseProductionToZero(int hour) {
		electricityProduction.set(Date.getFirstHourOfToday() + hour, 0f);
		plantRunning[hour] = false;
	}

	public void determineCostsVar(Integer year) {
		determineCostsVar(year, marketArea);
	}

	public float[] getBidStartUp() {
		return dayAheadBidHourlyStartUpCosts;
	}

	public String getBNANumber() {
		return bnaNumber;
	}

	public float getCapacity(CapacityType capacityType, int hourOfYear) {
		if (capacitiesHourlyCurrentYear.containsKey(capacityType)) {
			return capacitiesHourlyCurrentYear.get(capacityType).getCapacityData(hourOfYear);
		}
		return 0f;
	}

	/**
	 *
	 * Excludes unexpected outages
	 *
	 * @param hourOfYear
	 * @return
	 */
	public float getCapacityUnusedExpected(int hourOfYear) {
		return capacitiesHourlyCurrentYear.get(CapacityType.CAPACITY_UNUSED_EXPECTED)
				.getCapacityData(hourOfYear);
	}

	/**
	 *
	 * Includes unexpected outages
	 *
	 * @param hourOfYear
	 * @return
	 */
	public float getCapacityUnusedUnexpected(int hourOfYear) {
		return capacitiesHourlyCurrentYear.get(CapacityType.CAPACITY_UNUSED_UNEXPECTED)
				.getCapacityData(hourOfYear);
	}

	public float getCarbonEmissionsDailyCumulated(int day) {
		try {
			return carbonEmissionsDailyMap.get(day);
		} catch (final Exception e) {
			return 0f;
		}
	}

	public float getCarbonEmissionsYearly() {
		return getCarbonEmissionsYearly(Date.getYear());
	}

	public float getCarbonEmissionsYearly(int year) {
		if (!carbonEmissionsYearly.containsKey(year)) {
			carbonEmissionsYearly.put(year, 0f);
		}
		return carbonEmissionsYearly.get(year);
	}

	public float getCostYearlyDayAheadMarket(Integer year) {
		return costsYearlyDayAheadMarket.get(year);
	}
	public float getCostYearlyDayAheadMarketCarbon(Integer year) {
		return costsYearlyDayAheadMarketCarbon.get(year);
	}
	public float getCostYearlyDayAheadMarketFuel(Integer year) {
		return costsYearlyDayAheadMarketFuel.get(year);
	}
	public float getCostYearlyDayAheadMarketOther(Integer year) {
		return costsYearlyDayAheadMarketOther.get(year);
	}
	public float getCostYearlyOAndMFixed(int year) {
		return costsYearlyOperationsAndMaintenanceFixed.get(year);
	}

	public float getCostYearlyStartUp(int year) {
		return costsYearlyStartUp.get(year);
	}

	public float getElectricityProductionCurrentYear() {
		float dailySum = 0f;
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			dailySum += electricityProduction.get(hourOfYear);
		}

		electricityProductionYearly.put(Date.getYear(), dailySum);

		return dailySum;
	}

	public float getElectricityProductionDayOfYear(int dayOfYear) {
		float dailySum = 0f;
		final int startHour = Date.getFirstHourOfDay(dayOfYear);
		final int endHour = startHour + Date.HOURS_PER_DAY;
		for (int hourOfYear = startHour; hourOfYear < endHour; hourOfYear++) {
			dailySum += electricityProduction.get(hourOfYear);
		}
		return dailySum;
	}

	public float getElectricityProductionHourOfYear(int hourOfYear) {
		return electricityProduction.get(hourOfYear);
	}

	public float getElectricityProductionToday(int hour) {
		return electricityProduction.get(Date.getFirstHourOfToday() + hour);
	}

	public Float getElectricityProductionYearly(int year) {
		return electricityProductionYearly.get(year);
	}

	public float getHeatCapacity() {
		return heatCapacity;
	}

	public float getHeatProductionHourOfYear(int hourOfYear) {
		return heatProduction.get(hourOfYear);
	}

	public float[] getHourlyProfit() {
		return hourlyProfit;
	}

	public float[] getHoursOfStartUp() {
		return hoursOfStartUp;
	}

	public float getIncomeYearlyDayAheadMarket(int year) {
		return incomeYearlyDayAheadMarket.get(year);
	}

	/**
	 * Returns the last hour the plant was running in the past days. The value
	 * is given as the difference between the hour <code>0</code> of the current
	 * day and the last running hour. I.e. if plant was running yesterday at
	 * <code>22.00-23.00</code>h, then <code>-2</code> is returned. If the plant
	 * was not running in the last
	 * {@link simulations.scheduling.Date#WARM_STARTUP_LENGTH} hours,
	 * <code>-({@link simulations.scheduling.Date#WARM_STARTUP_LENGTH}+1)</code>
	 * is returned.
	 *
	 * @return [-({@link simulations.scheduling.Date#WARM_STARTUP_LENGTH}),-1]
	 */
	public int getLastRunningHour() {
		for (int hour = -1; hour < -Date.WARM_STARTUP_LENGTH; hour--) {
			if (isRunningHour(hour)) {
				return hour;
			}
		}
		return -(Date.WARM_STARTUP_LENGTH + 1);
	}

	/**
	 * Returns the last hour the plant was running starting from startFromHour.
	 * The value is given as the difference between the hour <code>0</code> of
	 * the current day and the last running hour. I.e. if plant was running
	 * yesterday at <code>22.00-23.00</code>h, then <code>-2</code> is returned.
	 * If the plant was not running in the last
	 * {@link simulations.scheduling.Date#WARM_STARTUP_LENGTH} hours,
	 * <code>-({@link simulations.scheduling.Date#WARM_STARTUP_LENGTH}+1)</code>
	 * is returned.
	 *
	 * @return [-({@link simulations.scheduling.Date#WARM_STARTUP_LENGTH}),-1]
	 */
	public int getLastRunningHour(int startFromHour) {
		for (int hour = startFromHour; hour < -Date.WARM_STARTUP_LENGTH; hour--) {
			if (isRunningHour(hour)) {
				return hour;
			}
		}
		return -(Date.WARM_STARTUP_LENGTH + 1);
	}

	public float getLatitude() {
		return latitude;
	}

	/**
	 * @return locationName
	 */
	public String getLocName() {
		return locationName;
	}

	public float getLongitude() {
		return longitude;
	}

	/** Get name of thermal unit [LocationName + UnitName] */
	@Override
	public String getName() {
		return locationName + " " + unitName;
	}

	public float getNumberOfStartUps() {
		return numberOfStartUps;
	}

	public int getOwnerID() {
		return ownerID;
	}

	@Override
	public float getProfit() {
		return profitTotal;
	}

	public Float getProfitYearly(int year) {
		if (!profitYearly.containsKey(year)) {
			return 0f;
		}
		return profitYearly.get(year);
	}

	public Integer getRunningHours(int year) {
		return operatingHoursYearlyMap.get(year);
	}

	public float getSavetotalvarcosts() {
		return safeTotalVarCosts;
	}

	public int getStartupCounter(int year, Startup startup) {
		return startupCounter.get(year).get(startup);
	}

	public float[] getStrategicCosts() {
		return strategicCosts;
	}

	public float[] getStrategicCostsExCO2() {
		return strategicCostsExCO2;
	}

	public int getThermalState() {
		return thermalState;
	}

	public int getUnit(List<Plant> unitList, int unitID) {
		for (int i = 0; i < unitList.size(); i++) {
			if (unitList.get(i).getUnitID() == unitID) {
				return i;
			}
		}
		return -1;
	}

	public String getUnitName() {
		return unitName;
	}

	public float getYearlyEmissionCosts() {
		return yearlyEmissionCosts;
	}

	public int getYearlyRunningHours() {
		return operatingHours;
	}

	public void increaseCarbonEmissions() {

		// regular conventional plants
		for (int hourOfDay = 0; hourOfDay < HOURS_PER_DAY; hourOfDay++) {

			final float production = getElectricityProductionToday(hourOfDay);

			if (production > 0) {
				final float carbonEmission = production * getPlantEmissionFactor(true);
				marketArea.getCarbonEmissions().addCarbon(hourOfDay, getFuelName(), carbonEmission);
				carbonEmissionsDaily += carbonEmission;
			}
		}

		// emissions caused by start ups
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			// Check if plant was switched on in this hour
			if (isRunningHour(hour) && !isRunningHour(hour - 1)) {
				// Determine start type
				if (isRunningRange(hour - Date.HOT_STARTUP_LENGTH, hour - 1)) {
					carbonEmissionsDaily += getElectricityProductionToday(hour)
							* marketArea.getStartUpCosts().getCarbonEmissions(this, Startup.HOT);
					marketArea.getCarbonEmissions().addCarbonStartup((hour), getFuelType(),
							getElectricityProductionToday(hour) * marketArea.getStartUpCosts()
									.getCarbonEmissions(this, Startup.HOT));
				} else if (isRunningRange(hour - Date.WARM_STARTUP_LENGTH,
						hour - (Date.HOT_STARTUP_LENGTH + 1))) {
					carbonEmissionsDaily += getElectricityProductionToday(hour)
							* marketArea.getStartUpCosts().getCarbonEmissions(this, Startup.WARM);
					marketArea.getCarbonEmissions().addCarbonStartup((hour), getFuelType(),
							getElectricityProductionToday(hour) * marketArea.getStartUpCosts()
									.getCarbonEmissions(this, Startup.WARM));
				} else {
					carbonEmissionsDaily += getElectricityProductionToday(hour)
							* marketArea.getStartUpCosts().getCarbonEmissions(this, Startup.COLD);
					marketArea.getCarbonEmissions().addCarbonStartup((hour), getFuelType(),
							getElectricityProductionToday(hour) * marketArea.getStartUpCosts()
									.getCarbonEmissions(this, Startup.COLD));
				}
			}
		}

		final int year = Date.getYear();
		if (!carbonEmissionsYearly.containsKey(year)) {
			carbonEmissionsYearly.put(year, 0f);
			carbonEmissionsDailyMap = new HashMap<>();
			for (int dayOfYear = 0; dayOfYear <= DAYS_PER_YEAR; dayOfYear++) {
				carbonEmissionsDailyMap.put(dayOfYear, 0f);
			}
		}
		final int day = Date.getDayOfYear();
		carbonEmissionsDailyMap.put(day,
				carbonEmissionsDailyMap.get(day - 1) + carbonEmissionsDaily);

		carbonEmissionsDailyMap.put(day + 1, carbonEmissionsDailyMap.get(day));
		carbonEmissionsYearly.put(year, carbonEmissionsYearly.get(year) + carbonEmissionsDaily);

	}

	public void increaseCostsDayAheadMarket(float costs) {
		final int year = Date.getYear();
		if (!costsYearlyDayAheadMarket.containsKey(year)) {
			costsYearlyDayAheadMarket.put(year, 0f);
		}
		costsYearlyDayAheadMarket.put(year, costsYearlyDayAheadMarket.get(year) + costs);
	}

	public void increaseCostsDayAheadMarketCarbon(float costs) {
		final int year = Date.getYear();
		if (!costsYearlyDayAheadMarketCarbon.containsKey(year)) {
			costsYearlyDayAheadMarketCarbon.put(year, 0f);
		}
		costsYearlyDayAheadMarketCarbon.put(year,
				costsYearlyDayAheadMarketCarbon.get(year) + costs);
	}

	public void increaseCostsDayAheadMarketFuel(float costs) {
		final int year = Date.getYear();
		if (!costsYearlyDayAheadMarketFuel.containsKey(year)) {
			costsYearlyDayAheadMarketFuel.put(year, 0f);
		}
		costsYearlyDayAheadMarketFuel.put(year, costsYearlyDayAheadMarketFuel.get(year) + costs);
	}

	public void increaseCostsDayAheadMarketOther(float costs) {
		final int year = Date.getYear();
		if (!costsYearlyDayAheadMarketOther.containsKey(year)) {
			costsYearlyDayAheadMarketOther.put(year, 0f);
		}
		costsYearlyDayAheadMarketOther.put(year, costsYearlyDayAheadMarketOther.get(year) + costs);
	}

	public void increaseCostsStartUp(float costs) {
		final int year = Date.getYear();
		if (!costsYearlyStartUp.containsKey(year)) {
			costsYearlyStartUp.put(year, 0f);
		}
		costsYearlyStartUp.put(year, costsYearlyStartUp.get(year) + costs);
	}

	public void increaseIncomeDayAheadMarket(float income) {
		final int year = Date.getYear();
		if (!incomeYearlyDayAheadMarket.containsKey(year)) {
			incomeYearlyDayAheadMarket.put(year, 0f);
		}
		incomeYearlyDayAheadMarket.put(year, incomeYearlyDayAheadMarket.get(year) + income);
	}

	public void increaseProduction(int hour, float increase) {

		if (increase < 0.0f) {
			logger.error(marketArea.getInitialsBrackets() + getUnitID()
					+ " Only positive values are allowed. " + increase);
		}

		final int firstHourOfToday = Date.getFirstHourOfToday();
		electricityProduction.set(firstHourOfToday + hour,
				increase + electricityProduction.get(firstHourOfToday + hour));
		plantRunning[firstHourOfToday + hour] = true;

		checkProductionToday(hour);
	}

	public void increaseProductionHeat(int hourOfYear, float increase) {

		if (increase < 0) {
			logger.error("Only positive values for heat increase are allowed.");
		}

		heatProduction.set(hourOfYear, increase + heatProduction.get(hourOfYear));

	}

	public void increaseProfit(float profit) {
		final int year = Date.getYear();
		increaseProfit(profit, year);
	}

	public void increaseProfit(float profit, int year) {
		profitTotal += profit;
		profitYearly.put(year, getProfitYearly(year) + profit);
	}

	/** Set relevant fields of power plant */
	public void initializePowerPlant(MarketArea marketArea) {
		// Markets
		activeMarkets = new ArrayList<>();
		activeMarkets.add(RevenueType.ELECTRICTY_DAY_AHEAD);
		this.marketArea = marketArea;

		determineCostsVar(Date.getYear());

		/** Initialize lists */
		// Day-ahead market
		capacitiesHourlyCurrentYear.put(CapacityType.CONTRACTED_DAY_AHEAD, new CapacityData());
		capacitiesHourlyCurrentYear.put(CapacityType.CONTRACTED_TOTAL, new CapacityData());
		capacitiesHourlyCurrentYear.put(CapacityType.CONTRACTED_COGENERATION, new CapacityData());
		capacitiesHourlyCurrentYear.put(CapacityType.NON_USABILITY_EXPECTED, new CapacityData());
		capacitiesHourlyCurrentYear.put(CapacityType.NON_USABILITY_UNEXPECTED, new CapacityData());
		capacitiesHourlyCurrentYear.put(CapacityType.CAPACITY_NET,
				new CapacityData(getNetCapacity()));
		capacitiesHourlyCurrentYear.put(CapacityType.CAPACITY_UNUSED_EXPECTED,
				new CapacityData(getNetCapacity()));
		capacitiesHourlyCurrentYear.put(CapacityType.CAPACITY_UNUSED_UNEXPECTED,
				new CapacityData(getNetCapacity()));

		// Electricity production
		electricityProduction = new ArrayList<>(Collections.nCopies(HOURS_PER_YEAR, 0f));
		heatProduction = new ArrayList<>(Collections.nCopies(HOURS_PER_YEAR, 0f));
		plantRunning = new boolean[Date.HOURS_PER_YEAR];
	}

	public void initializeProfit() {
		// Yearly profit
		final int year = Date.getYear();
		initializeProfit(year);
	}

	/**
	 * @param year
	 */
	public void initializeProfit(int year) {
		final float oAndMFixed = -getCostsOperationMaintenanceFixed(year) * netCapacity;
		if (!profitYearly.containsKey(year)) {
			profitYearly.put(year, oAndMFixed);
		}
		if (!costsYearlyOperationsAndMaintenanceFixed.containsKey(year)) {
			costsYearlyOperationsAndMaintenanceFixed.put(year, oAndMFixed);
		}
	}

	/**
	 * @param plant
	 * @param yearsOfNegativeProfit
	 * @return true, if for length yearsOfNegativeProfit always negative yearly
	 *         profit
	 */
	public boolean isContinuousNegativeProfit(int yearsOfNegativeProfit) {
		return calculateYearsOfContinuousNegativeProfit() >= yearsOfNegativeProfit ? true : false;
	}

	/**
	 * Checks if power plant is running for requested hour. Negative values are
	 * used for yesterday or the day before yesterday.
	 *
	 * @param hour
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 * @return
	 */
	public Running isRunning(int hour) {
		return runningType(Date.getFirstHourOfToday() + hour);
	}

	/**
	 * Checks if power plant is running for requested hour. Negative values are
	 * used for yesterday or the day before yesterday.
	 *
	 * Attention return if out of range.
	 *
	 * @param hour
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 * @return
	 */
	public boolean isRunningHour(int hour) {
		final int hourOfYear = Date.getFirstHourOfToday() + hour;

		if ((hourOfYear < 0) || (hourOfYear > HOURS_PER_YEAR)) {
			return true;
		}

		return plantRunning[hourOfYear];
	}

	/**
	 * Checks if power plant is running for requested hour. Negative values are
	 * used for yesterday or the day before yesterday.
	 *
	 * Attention return if out of range.
	 *
	 * @param hour
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 * @return
	 */
	public boolean isRunningHourYearly(int hourOfYear) {
		return plantRunning[hourOfYear];
	}

	/**
	 * Checks if power plant is running for requested period.
	 *
	 * @param hourStart
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 *
	 * @param hourEnd
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 *
	 * @return <code>true</code> if plant is running during that period
	 */
	public boolean isRunningRange(int hourStart, int hourEnd) {
		for (int hourIndex = hourStart; hourIndex <= hourEnd; hourIndex++) {
			if (isRunningHour(hourIndex)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if power plant is running for requested period.
	 *
	 * @param hourStart
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 *
	 * @param hourEnd
	 *            [-48,23] = [beforeyesterday.firstHour, ..., today.lastHour]
	 *
	 * @return <code>true</code> if plant is running during that period
	 */
	public boolean isRunningRangeContinuously(int hourStart, int hourEnd) {

		for (int hourIndex = hourStart; hourIndex <= hourEnd; hourIndex++) {
			if (!isRunningHour(hourIndex)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if power plant is running today for requested hour.
	 *
	 * @param hour
	 *            [0,23]
	 * @return
	 */
	public boolean isRunningTodayRange(int hour) {
		return isRunningRange(hour, hour);
	}

	/**
	 * Checks whether a power plant is still running on current day of the year.
	 * Especially relevant for shutdown of nuclear power plants in the middle of
	 * the year.
	 *
	 * @param powerPlant
	 * @return
	 */
	public boolean isStillRunning() {
		final LocalDate today = Date.getCurrentDateTime().toLocalDate();
		return isStillRunning(today);
	}

	/**
	 * Checks whether a power plant is still running on current day of the year.
	 * Especially relevant for shutdown of nuclear power plants in the middle of
	 * the year.
	 *
	 * @param powerPlant
	 * @return
	 */
	public boolean isStillRunning(LocalDate today) {
		boolean stillRunning;

		if ((getAvailableDate().isBefore(today) || getAvailableDate().isEqual(today))
				&& (getShutDownDate().isAfter(today) || getShutDownDate().isEqual(today))) {
			stillRunning = true;
		} else {
			stillRunning = false;
		}

		return stillRunning;
	}

	public void printCapacityUsage(int hourOfYear) {

		final StringBuffer sb = new StringBuffer();
		sb.append("UnitID: " + getUnitID() + ", ");
		for (final Entry<CapacityType, CapacityData> capacity : capacitiesHourlyCurrentYear
				.entrySet()) {

			sb.append(capacity.getKey() + ";");
			sb.append(capacity.getValue().getCapacityData(hourOfYear) + ";");
		}

		logger.error(sb.toString());

	}

	public void resetCarbonEmissionsDaily() {
		carbonEmissionsDaily = 0;
	}

	public void resetYearlyRunningHours() {
		operatingHours = 0;
	}

	private Running runningType(float volume) {

		if (volume == 0) {
			return Running.NOT;
		} else if (volume < netCapacity) {
			if (getMinProduction() == volume) {
				return Running.MINIMAL;
			} else if (getMinProduction() < volume) {
				return Running.PARTLY;
			} else {
				logger.error("Plant is running below min level.");
				return Running.PARTLY;
			}

		} else {
			return Running.FULL;
		}
	}

	public void setBidStartUp(int hour, float bidStartUp) {
		dayAheadBidHourlyStartUpCosts[hour] = bidStartUp;
	}

	public void setBNANumber(String bnaNumber) {
		this.bnaNumber = bnaNumber;
	}

	/**
	 * Sets capacity, <b>WARNING</b> does not update if capacity is already set!
	 *
	 * @param capacityType
	 * @param capacity
	 * @param day
	 *            - day of year [1...Days of the year]
	 */
	public void setCapacity(CapacityType capacityType, float[] capacity, int day) {
		final int firstHourOfToday = Date.getFirstHourOfDay(day);
		for (int hour = 0; hour < capacity.length; hour++) {
			final int hourOfYear = hour + firstHourOfToday;
			setCapacity(capacityType, hourOfYear, capacity[hour]);
		}

	}

	/**
	 *
	 *
	 * @param capacityType
	 *            <b>all but unused are allowed</b>
	 * @param hourOfYear
	 * @param capacity
	 * 
	 *            Synchronized due to some threads could access parallel.
	 */
	public synchronized void setCapacity(CapacityType capacityType, int hourOfYear,
			float capacity) {
		final CapacityData capacityDataUnusedExpected = capacitiesHourlyCurrentYear
				.get(CapacityType.CAPACITY_UNUSED_EXPECTED);
		final CapacityData capacityDataUnusedUnexpected = capacitiesHourlyCurrentYear
				.get(CapacityType.CAPACITY_UNUSED_UNEXPECTED);
		final CapacityData capacityDataChanged = capacitiesHourlyCurrentYear.get(capacityType);

		final float capacityChangedDifference = capacityDataChanged.getCapacityData(hourOfYear)
				- capacity;
		final float capacityUnusedExpectedUpdated = capacityDataUnusedExpected
				.getCapacityData(hourOfYear) + capacityChangedDifference;
		final float capacityUnusedUnexpectedUpdated = capacityDataUnusedUnexpected
				.getCapacityData(hourOfYear) + capacityChangedDifference;

		capacityDataChanged.setCapacityData(hourOfYear, capacity);
		capacityDataUnusedExpected.setCapacityData(hourOfYear, capacityUnusedExpectedUpdated);
		capacityDataUnusedUnexpected.setCapacityData(hourOfYear, capacityUnusedUnexpectedUpdated);

		if (capacityType.isContracted()) {
			final CapacityData capacityDataContracted = capacitiesHourlyCurrentYear
					.get(CapacityType.CONTRACTED_TOTAL);
			final float capacityContractedUpdated = capacityDataContracted
					.getCapacityData(hourOfYear) - capacityChangedDifference;
			capacityDataContracted.setCapacityData(hourOfYear, capacityContractedUpdated);

			if (capacityContractedUpdated < 0) {
				logger.error("Negative contracted capacity! " + capacityContractedUpdated
						+ " for plant " + getUnitID() + " in hour " + hourOfYear);
			} else if ((capacityContractedUpdated - netCapacity) > EPSILON) {
				logger.error("Wrong calculation of available capacity for plant " + getUnitID()
						+ " in hour " + hourOfYear + "!");
			}
		}

		if (capacityUnusedExpectedUpdated < 0) {
			String sb = "Negative non used capacity! " + capacityUnusedExpectedUpdated
					+ " for plant " + getUnitID() + " in hour " + hourOfYear + "; ";
			for (final Entry<CapacityType, CapacityData> capacityData : capacitiesHourlyCurrentYear
					.entrySet()) {
				sb += capacityData.getKey() + " "
						+ capacityData.getValue().getCapacityData(hourOfYear);
			}
			logger.error(sb);
		} else if ((capacityUnusedExpectedUpdated - netCapacity) > EPSILON) {
			logger.error("Wrong calculation of available capacity!");
		}
		if (capacityUnusedUnexpectedUpdated < -EPSILON) {
			String sb = "Negative non used capacity! " + capacityUnusedExpectedUpdated
					+ " for plant " + getUnitID() + " in hour " + hourOfYear + "; ";
			for (final Entry<CapacityType, CapacityData> capacityData : capacitiesHourlyCurrentYear
					.entrySet()) {
				sb += capacityData.getKey() + " "
						+ capacityData.getValue().getCapacityData(hourOfYear);
			}
			logger.error(sb);
		} else if (capacityUnusedUnexpectedUpdated > netCapacity) {
			logger.error("Wrong calculation of available capacity!");
		}
	}

	public void setCostsOperationMaintenanceFixed() {
		// Make sure that endogenous investments are not updated
		if (costsOperationMaintenanceFixed <= 0) {
			costsOperationMaintenanceFixed = marketArea.getOperationMaintenanceCosts()
					.getCostFixed(getCategory());
			checkCostsOperationMaintenanceFixed();
		}
	}

	public void setElectricityProduced(int hour, float volume) {
		electricityProduction.set(hour, volume);
		checkProduction(hour);
	}

	public void setHeatCapacity(float heatCapacity) {
		this.heatCapacity = heatCapacity;
	}

	public void setHourlyProfit(int hour, float dailyProfit) {
		hourlyProfit[hour] = dailyProfit;
	}

	public void setHoursOfStartUp(int hour, float hoursOfStartUp) {
		this.hoursOfStartUp[hour] = hoursOfStartUp;
	}

	public void setLatitude(float latitude) {
		this.latitude = latitude;
	}

	public void setLocationName(String locationName) {
		this.locationName = locationName;
	}

	public void setLongitude(float longitude) {
		this.longitude = longitude;
	}

	public void setNumberOfStartUps(float numberOfStartUps) {
		this.numberOfStartUps = numberOfStartUps;
	}

	public void setOwnerID(int ownerID) {
		this.ownerID = ownerID;
	}

	public void setProductionToday(int hour, float value) {
		electricityProduction.set(Date.getFirstHourOfToday() + hour, value);
		plantRunning[Date.getFirstHourOfToday() + hour] = value > 0 ? true : false;
		checkProductionToday(hour);
	}

	public void setSafeTotalVarCosts(float safeTotalVarCosts) {
		this.safeTotalVarCosts = safeTotalVarCosts;
	}

	public void setStrategicCosts(int hour, float strategicCosts) {
		this.strategicCosts[hour] = strategicCosts;
	}

	public void setStrategicCostsExCO2(int hour, float strategicCostsExCO2) {
		this.strategicCostsExCO2[hour] = strategicCostsExCO2;
	}

	public void setThermalState(int thermalState) {
		this.thermalState = thermalState;
	}

	public void setUnitName(String unitName) {
		this.unitName = unitName;
	}

	public void setYearlyEmissionCosts(float yearlyEmissionCosts) {
		this.yearlyEmissionCosts = yearlyEmissionCosts;

	}

	@Override
	public String toString() {
		return "unitID: " + getUnitID() + "; " + getFuelName() + "; net capacity: "
				+ getNetCapacity() + "; varCostsTotal: " + Statistics.round(costsVar, 2);
	}

	public void updateElectricityProduced(int hour, float volume) {
		electricityProduction.set(hour, electricityProduction.get(hour) + volume);
		checkProduction(hour);
	}

	public void updateProductionToday(int hour, float value) {
		electricityProduction.set(Date.getFirstHourOfToday() + hour,
				electricityProduction.get(Date.getFirstHourOfToday() + hour) + value);
		if (!plantRunning[Date.getFirstHourOfToday() + hour]) {
			plantRunning[Date.getFirstHourOfToday() + hour] = value > 0 ? true : false;
		}
		checkProductionToday(hour);

	}
}