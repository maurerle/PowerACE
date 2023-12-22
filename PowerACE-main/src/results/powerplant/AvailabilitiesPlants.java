package results.powerplant;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.scheduling.Date;
import supply.invest.StateStrategic;
import supply.powerplant.Plant;
import supply.powerplant.capacity.CapacityType;
import tools.other.HashMapFloat;
import tools.types.FuelType;

/**
 * This class stores the capacities of power plants on a daily basis taking
 * non-usabilities into account.
 *
 * , PR
 *
 */
public class AvailabilitiesPlants {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(AvailabilitiesPlants.class.getName());

	/** Total daily capacity of all conventional power plants */
	private Map<Integer, Float> capacityAll = new ConcurrentHashMap<>();
	/**
	 * Total daily capacity of all available conventional power plants by fuel
	 * type neither accounting for expected nor unexpected non-usabilities.
	 */
	private Map<FuelType, HashMapFloat<Integer>> capacityAllByFuelType;
	/** Total daily capacity of all unavailable conventional power plants */
	private Map<FuelType, Map<CapacityType, HashMapFloat<Integer>>> capacityReserveMarkets;
	/** Total daily capacity of all unavailable conventional power plants */
	private Map<CapacityType, Map<Integer, Float>> capacityUnavailable = new ConcurrentHashMap<>();
	/**
	 * Total daily capacity of all available conventional power plants by fuel
	 * type after accounting for expected and not unexpected non-usabilities
	 */
	private Map<FuelType, Map<CapacityType, HashMapFloat<Integer>>> capacityUnavailableByFuelType;

	private Set<CapacityType> nonusabilties;
	private Set<CapacityType> reserves;

	public AvailabilitiesPlants() {
		initialize();
	}

	public void calculateCapacities(MarketArea marketArea, int year, int day) {
		final List<Plant> powerPlants = marketArea.getSupplyData().getPowerPlantsAsList(year,
				Collections.singleton(StateStrategic.OPERATING));
		// Net installed capacity of all plants
		final int firstHourOfToday = Date.getFirstHourOfDay(day);
		try {
			Collections.sort(powerPlants);
			for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
				float allPlants = 0f;
				float expected = 0f;
				float unexpected = 0f;
				final int hourOfYear = firstHourOfToday + hourOfDay;
				final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
				for (final Plant unit : powerPlants) {
					final FuelType fuelType = unit.getFuelType();

					// Check if plant is not already shut down
					if (unit.isAvailableTechnically(year) && unit.isStillRunning()) {

						// This should be the available capacity including
						// unavailabilities (Capacity_UNUSED already has
						// unavailabitlies subtracted)

						final float unitCapacity = unit.getCapacity(CapacityType.CAPACITY_NET,
								hourOfYear);

						allPlants += unitCapacity;
						capacityAllByFuelType.get(fuelType).increase(key, unitCapacity);

						// Calculate the unavailabilities

						// List also contains all plants but these that are
						// expected
						final float capacityExpected = unit
								.getCapacity(CapacityType.NON_USABILITY_EXPECTED, hourOfYear);
						capacityUnavailableByFuelType.get(fuelType)
								.get(CapacityType.NON_USABILITY_EXPECTED)
								.increase(key, capacityExpected);
						expected += capacityExpected;

						final float capacityUnexpected = unit
								.getCapacity(CapacityType.NON_USABILITY_UNEXPECTED, hourOfYear);
						capacityUnavailableByFuelType.get(fuelType)
								.get(CapacityType.NON_USABILITY_UNEXPECTED)
								.increase(key, capacityUnexpected);
						unexpected += capacityUnexpected;

						for (final CapacityType capacityType : reserves) {
							final Float capacity = unit.getCapacity(capacityType, hourOfYear);
							if (capacity != null) {
								capacityReserveMarkets.get(fuelType).get(capacityType).increase(key,
										capacity);
							}
						}

					}
				}
				capacityAll.put(key, allPlants);
				capacityUnavailable.get(CapacityType.NON_USABILITY_EXPECTED).put(key, expected);
				capacityUnavailable.get(CapacityType.NON_USABILITY_UNEXPECTED).put(key, unexpected);
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * @return Return the total available capacity including the un-/expected
	 *         non availabilities.
	 */
	public Float getCapacity(int year, int hourOfYear) {
		final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
		return getCapacityAllKey(key);
	}

	/**
	 * Return the available capacity for each fuel type either without the
	 * expected non availabilities.
	 *
	 * @param dayOfYear
	 *            [1,365]
	 * @param fuelType
	 *
	 * @return
	 */
	public Float getCapacity(int year, int hourOfYear, FuelType fuelType,
			Set<CapacityType> unavailables) {

		Float unavailableCapacity = 0f;
		final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);

		// Non usability
		if (unavailables != null) {
			for (final CapacityType unavailable : unavailables) {
				unavailableCapacity += getCapacityUnavailableByFuelTypeKey(fuelType, unavailable,
						key);
			}
		}

		return capacityAllByFuelType.get(fuelType).get(key);
	}

	/**
	 * @return Return the total available capacity excluding the non
	 *         availabilities in <code>unavailable</code>.
	 */
	public Float getCapacity(Set<CapacityType> unavailables, Set<CapacityType> contractedOtherwise,
			int year, int hourOfYear) {
		final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
		Float unavailableCapacity = 0f;
		for (final CapacityType unavailable : unavailables) {
			unavailableCapacity += getCapacityUnavailableKey(unavailable, key);
		}
		for (final CapacityType unavailable : contractedOtherwise) {
			for (final FuelType fuelType : capacityReserveMarkets.keySet()) {
				unavailableCapacity += getcapacityReserveMarketsKey(fuelType, unavailable, key);
			}
		}
		return getCapacityAllKey(key) - unavailableCapacity;
	}

	private float getCapacityAllKey(int keyHourly) {
		if (!capacityAll.containsKey(keyHourly)) {
			logger.error("Huge problem! Why is the capacity not calculated " + keyHourly);
			return 0;
		}
		return capacityAll.get(keyHourly);
	}

	/**
	 * @return Return the total available capacity excluding the non
	 *         availabilities in <code>unavailable</code>.
	 */
	public Float getCapacityAvg(List<CapacityType> unavailables, int year) {
		Float unavailableCapacity = 0f;
		float capacity = 0f;
		try {
			if (year > Date.getLastYear()) {
				return null;
			}
			for (int hourOfYear = 0; hourOfYear < Date.getLastHourOfYear(year); hourOfYear++) {
				final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
				for (final CapacityType unavailable : unavailables) {
					unavailableCapacity += getCapacityUnavailableKey(unavailable, key);
				}
				capacity += getCapacityAllKey(key) - unavailableCapacity;
			}

		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}

		return capacity / Date.getLastDayOfYear(year);
	}

	private float getcapacityReserveMarketsKey(FuelType fuelType, CapacityType unavailable,
			int keyHourly) {
		if (!capacityReserveMarkets.containsKey(fuelType)) {
			logger.error("Why is fuel type not in the map? FuelType: " + fuelType);
			return 0;
		}
		if (!capacityReserveMarkets.get(fuelType).containsKey(unavailable)) {
			logger.error("Why is capacity type not in the map? CapacityType: " + unavailable);
			return 0;
		}
		if (!capacityReserveMarkets.get(fuelType).get(unavailable).containsKey(keyHourly)) {
			logger.error("Why is the capacity not calculated? " + keyHourly);
			return 0;
		}
		return capacityReserveMarkets.get(fuelType).get(unavailable).get(keyHourly);
	}

	private float getCapacityUnavailableByFuelTypeKey(FuelType fuelType, CapacityType unavailable,
			int keyHourly) {
		if (!capacityUnavailableByFuelType.containsKey(fuelType)) {
			logger.error("Why is fuel type not in the map? FuelType: " + fuelType);
			return 0;
		}
		if (!capacityUnavailableByFuelType.get(fuelType).containsKey(unavailable)) {
			logger.error("Why is capacity type not in the map?CapacityType: " + unavailable);
			return 0;
		}
		if (!capacityUnavailableByFuelType.get(fuelType).get(unavailable).containsKey(keyHourly)) {
			logger.error(" Why is the capacity not calculated? " + keyHourly);
			return 0;
		}
		return capacityUnavailableByFuelType.get(fuelType).get(unavailable).get(keyHourly);
	}

	private float getCapacityUnavailableKey(CapacityType unavailable, int keyHourly) {
		if (!capacityUnavailable.containsKey(unavailable)) {
			logger.error("Why is capacity type not in the map? CapacityType: " + unavailable);
			return 0;
		}
		if (!capacityUnavailable.get(unavailable).containsKey(keyHourly)) {
			logger.error("Why is the capacity not calculated: Key Hourly " + keyHourly);
			return 0;
		}
		return capacityUnavailable.get(unavailable).get(keyHourly);
	}

	/**
	 * Initialize all lists for each year.
	 */
	public void initialize() {
		for (final CapacityType unavailable : nonusabilties) {
			capacityUnavailable.put(unavailable, new ConcurrentHashMap<>());
		}

		capacityAllByFuelType = new ConcurrentHashMap<>();
		capacityUnavailableByFuelType = new ConcurrentHashMap<>();
		capacityReserveMarkets = new ConcurrentHashMap<>();

		for (final FuelType fuelType : FuelType.values()) {
			capacityAllByFuelType.put(fuelType, new HashMapFloat<Integer>());
			capacityUnavailableByFuelType.put(fuelType, new ConcurrentHashMap<>());
			capacityReserveMarkets.put(fuelType, new ConcurrentHashMap<>());

			for (final CapacityType unavailable : nonusabilties) {
				capacityUnavailableByFuelType.get(fuelType).put(unavailable,
						new HashMapFloat<Integer>());
			}

			for (final CapacityType capacityType : reserves) {
				capacityReserveMarkets.get(fuelType).put(capacityType, new HashMapFloat<>());
			}
			for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
				for (int hourOfYear = 0; hourOfYear <= Date.getLastHourOfYear(year); hourOfYear++) {
					final int key = Date.getKeyHourlyWithHourOfYear(year, hourOfYear);
					capacityAllByFuelType.get(fuelType).put(key, 0f);
					for (final CapacityType unavailable : nonusabilties) {
						capacityUnavailableByFuelType.get(fuelType).get(unavailable).put(key, 0f);
					}
					for (final CapacityType capacityType : reserves) {
						capacityReserveMarkets.get(fuelType).get(capacityType).put(key, 0f);
					}
				}
			}
		}
	}
}