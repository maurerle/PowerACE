package results.powerplant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.scheduling.Date;
import supply.Generator;
import supply.powerplant.Plant;
import tools.math.Statistics;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * The amount of the electricity produced by all the power plants is stored
 * here.
 * 
 * 
 * 
 */
public final class ProductionElectricity {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(ProductionElectricity.class.getName());

	/**
	 * The electricity production of the pumped storage plant for each hour of
	 * the year.
	 */
	private TreeMap<Integer, Float> electricityPumpedStorageTotal = new TreeMap<>();

	/**
	 * The electricity production of the pumped storage plant for each hour of
	 * the year.
	 */
	private TreeMap<Integer, Float> electricityPumpedStorageCharge = new TreeMap<>();

	/**
	 * The electricity production of the pumped storage plant for each hour of
	 * the year.
	 */
	private TreeMap<Integer, Float> electricityPumpedStorageDischarging = new TreeMap<>();

	/** The electricity production of each fuel for each hour of the day. */
	private Map<FuelName, Map<Integer, Float>> generation;

	public ProductionElectricity() {
		initialize();
	}

	/**
	 * Adds the amount of <code>electricityPeaker</code> to the specified
	 * <code>hour</code> of electricity production for a given day.
	 * 
	 * 
	 * @param hour
	 *            [0, {@link Date#HOURS_PER_YEAR}-1]
	 * @param electricityPeaker
	 *            amount in MWh
	 */
	public synchronized void addDemandReduction(int hour, float volume) {
		if (volume < 0) {
			logger.error("Negative amount cannot be produced. " + volume);
			return;
		}

	}

	/**
	 * Add electricity production for current day.
	 * 
	 * Need to be synchronized since evaluation in SupplyTrader can be done
	 * simultaneously.
	 * 
	 * @param hour
	 *            [0, 23]
	 * @param volume
	 *            [0,infinity]
	 * @param fuel
	 *            FuelName
	 */
	public synchronized void addElectricityDaily(int hour, float volume, FuelName fuel) {
		addElectricityYearly(Date.getFirstHourOfToday() + hour, volume, fuel);
	}

	/**
	 * Adds the amount of <code>electricityPumpedStorage</code> to the specified
	 * <code>hour</code> of electricity production for a given day.
	 * 
	 * 
	 * @param hour
	 *            [0, {@link Date#HOURS_PER_YEAR}-1]
	 * @param electricityPumpedStorageTotal
	 *            amount in MWh
	 */
	public synchronized void addElectricityPumpedStorage(int hour, float volume) {

		final int key = Date.getKeyHourlyWithHourOfYear(Date.getYear(),
				Date.getFirstHourOfToday() + hour);
		float currentValueTotal = 0f;
		if (electricityPumpedStorageTotal.containsKey(key)) {
			currentValueTotal = electricityPumpedStorageTotal.get(key);
		}
		electricityPumpedStorageTotal.put(key, currentValueTotal + volume);

		if (volume < 0) {
			float currentValueDemand = 0f;
			if (electricityPumpedStorageCharge.containsKey(key)) {
				currentValueDemand = electricityPumpedStorageCharge.get(key);
			}
			electricityPumpedStorageCharge.put(key, currentValueDemand + volume);
		} else {
			float currentValueProduction = 0f;
			if (electricityPumpedStorageDischarging.containsKey(key)) {
				currentValueProduction = electricityPumpedStorageDischarging.get(key);
			}
			electricityPumpedStorageDischarging.put(key, currentValueProduction + Math.abs(volume));
		}
	}

	/**
	 * Add electricity production for current year.
	 * 
	 * Need to be synchronized since evaluation in SupplyTrader can be done
	 * simultaneously.
	 * 
	 * @param hour
	 *            [0, {@link Date#HOURS_PER_YEAR}-1]
	 * @param volume
	 *            [0,infinity] (only pump-storage can be negative)
	 * @param fuel
	 *            FuelName
	 */
	public synchronized void addElectricityYearly(int hourOfYear, float volume, FuelName fuel) {
		if ((fuel != FuelName.HYDRO_PUMPED_STORAGE) && (volume < 0)) {
			logger.error("Negative amount cannot be produced. " + volume);
			return;
		}
		if (!generation.containsKey(fuel)) {
			generation.put(fuel, new HashMap<>());
		}
		generation.get(fuel).put(Date.getKeyHourlyWithHourOfYear(Date.getYear(), hourOfYear),
				getElectricityGeneration(fuel, Date.getYear(), hourOfYear) + volume);
	}

	/**
	 * Return total electricity produced in hour of year.
	 * 
	 * @param hourOfYear
	 *            [0, {@link Date#HOURS_PER_YEAR}-1]
	 * @return
	 */
	public float getElectricityConventionalHourly(int year, int hourOfYear) {
		float totalElectricity = 0;

		for (final FuelName fuel : FuelName.values()) {
			if (fuel.getFuelType() != FuelType.RENEWABLE) {
				totalElectricity += getElectricityGeneration(fuel, year, hourOfYear);
			}
		}

		return totalElectricity;
	}

	/**
	 * Get the sum of electricity produced from conventional generation units in
	 * the current year
	 * 
	 * @return
	 */
	public float getElectricityConventionalYearlySum(int year) {
		float totalElectricity = 0;

		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			totalElectricity += getElectricityConventionalHourly(year, hourOfYear);
		}

		return totalElectricity;
	}

	/**
	 * Returns the production of fuel name for hour of current day.
	 * 
	 * @param fuel
	 * @param hour
	 *            [0, 23]
	 * @return
	 */
	public float getElectricityDaily(FuelName fuel, int hour) {
		return getElectricityGeneration(fuel, Date.getYear(), Date.getFirstHourOfToday() + hour);
	}

	/**
	 * Returns the production of fuel type for hour of current day.
	 * 
	 * @param columnNameFuel
	 * @param hour
	 *            [0, 23]
	 * @return
	 */
	public float getElectricityDaily(FuelType type, int hour) {
		float production = 0;
		for (final FuelName fuelName : FuelName.values()) {
			if (FuelName.getFuelType(fuelName) == type) {
				production *= getElectricityGeneration(fuelName, Date.getYear(),
						Date.getFirstHourOfToday() + hour);
			}
		}
		return production;
	}

	/**
	 * @return The hourly production for today of the peaker plant.
	 */
	public float getElectricityGeneration(FuelName fuelName, int year, int hourOfYear) {
		if (!generation.containsKey(fuelName)) {
			return 0;
		}
		if (!generation.get(fuelName)
				.containsKey(Date.getKeyHourlyWithHourOfYear(year, hourOfYear))) {
			return 0;
		}
		return generation.get(fuelName).get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * @return The hourly production for today of the pumped storage plants.
	 */
	public float getElectricityPumpedStorage(int year, int hourOfYear) {
		if (!electricityPumpedStorageTotal
				.containsKey(Date.getKeyHourlyWithHourOfYear(year, hourOfYear))) {
			return 0;
		}
		return electricityPumpedStorageTotal.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * @return The hourly production for today of the pumped storage plants.
	 */
	public float getElectricityPumpedStorageCharge(int year, int hourOfYear) {
		if (!electricityPumpedStorageCharge
				.containsKey(Date.getKeyHourlyWithHourOfYear(year, hourOfYear))) {
			return 0;
		}
		return electricityPumpedStorageCharge
				.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * Get summed hourly electricity production by pumped storage for current
	 * year
	 */
	public float getElectricityPumpedStorageChargeYearlySum(int year) {
		final int startIndex = Date.getKeyHourlyWithHourOfYear(year, 0);
		final int endIndex = Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear(year));
		return Statistics.calcSumMap(electricityPumpedStorageCharge, startIndex, endIndex);
	}
	/**
	 * @return The hourly production for today of the peaker plant.
	 */
	public float getElectricityPumpedStorageDaily(int hour) {
		return getElectricityPumpedStorage(Date.getYear(), (Date.getFirstHourOfToday() + hour));
	}

	/**
	 * @return The hourly production for today of the pumped storage plants.
	 */
	public float getElectricityPumpedStorageDischarge(int year, int hourOfYear) {
		if (!electricityPumpedStorageDischarging
				.containsKey(Date.getKeyHourlyWithHourOfYear(year, hourOfYear))) {
			return 0;
		}
		return electricityPumpedStorageDischarging
				.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * Get summed hourly electricity production by pumped storage for current
	 * year
	 */
	public float getElectricityPumpedStorageDischargeYearlySum(int year) {
		final int startIndex = Date.getKeyHourlyWithHourOfYear(year, 0);
		final int endIndex = Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear(year));
		return Statistics.calcSumMap(electricityPumpedStorageDischarging, startIndex, endIndex);
	}

	/**
	 * Get summed hourly electricity production by pumped storage for current
	 * year
	 */
	public float getElectricityPumpedStorageYearlySum(int year) {
		final int startIndex = Date.getKeyHourlyWithHourOfYear(year, 0);
		final int endIndex = Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear(year));
		return Statistics.calcSumMap(electricityPumpedStorageTotal, startIndex, endIndex);
	}

	/**
	 * Returns the production of fuel for hour of current year.
	 * 
	 * @param fuel
	 * @param hour
	 *            [0, {@link Date#HOURS_PER_YEAR}-1]
	 * @return
	 */
	public float getElectricityYearly(FuelName fuel, int hourOfYear) {
		return getElectricityGeneration(fuel, Date.getYear(), hourOfYear);
	}

	/**
	 * Returns the total production of fuel summed over current year.
	 * 
	 * @param fuel
	 * @return
	 */
	public float getElectricityYearlySum(FuelName fuel, int year) {
		return Statistics.calcSumMap(generation.get(fuel), Date.getKeyHourlyWithHourOfYear(year, 0),
				Date.getKeyHourlyWithHourOfYear(year, Date.getLastHourOfYear(year)));
	}

	/**
	 * Return total electricity produced in hour of year.
	 * 
	 * @param hourOfYear
	 *            [0, {@link Date#HOURS_PER_YEAR}-1]
	 * @return
	 */
	public float getTotalElectricityYearly(int year, int hourOfYear) {
		float totalElectricity = 0;

		for (final FuelName fuel : FuelName.values()) {
			totalElectricity += getElectricityGeneration(fuel, Date.getYear(), hourOfYear);
		}

		totalElectricity += getElectricityPumpedStorage(year, hourOfYear);

		return totalElectricity;
	}

	public void initialize() {
		generation = new HashMap<>();

		for (final FuelName type : FuelName.values()) {
			generation.put(type, new HashMap<>());
		}

	}

	/**
	 * Compute statistics for generators.
	 * 
	 * @param generators
	 */
	public void summarize(List<Generator> generators) {

		/** [fuel type][counter for plants] */
		final float[][] averageUtilRateByFuel = new float[5][2];
		/** All power plants in market. */
		final List<Plant> powerPlants = new ArrayList<>();
		/** The electricity production of each generator. */
		final float[] electricityByGen = new float[generators.size()];

		logger.info("Compute plant statistics");

		for (final float[] element : averageUtilRateByFuel) {
			Arrays.fill(element, 0);
		}
		Arrays.fill(electricityByGen, 0);

		for (int i = 0; i < generators.size(); i++) {
			for (final Plant powerPlant : generators.get(i).getPowerPlantsList()) {
				powerPlants.add(powerPlant);
				electricityByGen[i] += powerPlant.getElectricityProductionCurrentYear();
			}
		}

		for (final Plant powerPlant : powerPlants) {
			switch (powerPlant.getFuelType()) {
				case URANIUM:
					averageUtilRateByFuel[0][0] += powerPlant.getUtilisation();
					averageUtilRateByFuel[0][1]++;
					break;
				case COAL:
					averageUtilRateByFuel[1][0] += powerPlant.getUtilisation();
					averageUtilRateByFuel[1][1]++;
					break;
				case LIGNITE:
					averageUtilRateByFuel[2][0] += powerPlant.getUtilisation();
					averageUtilRateByFuel[2][1]++;
					break;
				case OIL:
					averageUtilRateByFuel[3][0] += powerPlant.getUtilisation();
					averageUtilRateByFuel[3][1]++;
					break;
				case GAS:
					averageUtilRateByFuel[4][0] += powerPlant.getUtilisation();
					averageUtilRateByFuel[4][1]++;
					break;
				default:

			}
		}

		for (int i = 0; i < averageUtilRateByFuel.length; i++) {
			averageUtilRateByFuel[i][0] /= averageUtilRateByFuel[i][1];
		}
	}
}