package results.powerplant;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * The amount of the carbon produced by all the power plants is stored here.
 *
 * 
 *
 * 
 */
public final class EmissionsCarbon extends Agent {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(EmissionsCarbon.class.getName());

	private static synchronized void addValue(Map<Integer, Map<Integer, Float>> mapYearly, int year,
			int hourOfYear, float value) {
		checkYearAvailable(mapYearly);
		if (!mapYearly.get(year).containsKey(hourOfYear)) {
			mapYearly.get(year).put(hourOfYear, value);
		} else {
			mapYearly.get(year).put(hourOfYear, mapYearly.get(year).get(hourOfYear) + value);
		}

	}
	private static void checkYearAvailable(Map<Integer, Map<Integer, Float>> mapYearly) {
		if (!mapYearly.containsKey(Date.getYear())) {
			mapYearly.put(Date.getYear(), new HashMap<>());
		}
	}

	private static float getValueHourOfYear(Map<Integer, Map<Integer, Float>> mapYearly, int year,
			int hourOfYear) {
		if (mapYearly == null) {
			return 0;
		}
		if (!mapYearly.containsKey(year)) {
			return 0;
		}
		if (!mapYearly.get(year).containsKey(hourOfYear)) {
			return 0;
		}
		return mapYearly.get(year).get(hourOfYear);
	}

	/** Maps: Year, hours per year, value */
	private Map<Integer, Map<Integer, Float>> carbonCleanCoal = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonCleanGas = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonCleanLignite = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonCoal = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonGas = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonLignite = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonNuclear = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonOil = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonOther = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonPeaker = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonWaste = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonMineGas = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonSewageGas = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonRenewable = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonCoalStartup = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonGasStartup = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonLigniteStartup = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonCleanCoalStartup = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonCleanGasStartup = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonCleanLigniteStartup = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonOilStartup = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonFactorDemandBased = new ConcurrentHashMap<>();
	private Map<Integer, Map<Integer, Float>> carbonFactorProductionBased = new ConcurrentHashMap<>();
	private final Map<Integer, Float> yearlyCarbonEmissions = new ConcurrentHashMap<>();

	public EmissionsCarbon(MarketArea marketArea) {
		super(marketArea);
	}

	/**
	 *
	 * @param hour
	 *            of day
	 * @param fuel
	 * @param volume
	 */
	public void addCarbon(int hourOfDay, FuelName fuelName, float volume) {
		this.addCarbonHourly(Date.getHourOfYearFromHourOfDay(hourOfDay), fuelName, volume);
	}
	/**
	 * adds the <code>demand based emission factor</code> to the specified
	 * <code>hour of year</code> of carbon production for a given day
	 *
	 * @param hour
	 *            0-8759
	 * @param factor
	 *            in t/MWh
	 */
	public void addCarbonFactorDemandBased(int hourOfYear, float volume) {
		addValue(carbonFactorDemandBased, Date.getYear(), hourOfYear, volume);
	}

	/**
	 * adds the <code>production based emission factor</code> to the specified
	 * <code>hour of year</code> of carbon production for a given day
	 *
	 * @param hour
	 *            0-8759
	 * @param factor
	 *            in t/MWh
	 */
	public void addCarbonFactorProductionBased(int hourOfYear, float volume) {
		addValue(carbonFactorProductionBased, Date.getYear(), hourOfYear, volume);
	}

	/**
	 *
	 * Need to be synchronized since evaluation in SupplyTrader can be done
	 * simultaneously.
	 *
	 * @param hour
	 *            of year
	 * @param fuel
	 * @param volume
	 */
	public void addCarbonHourly(int hourOfYear, FuelName fuel, float volume) {
		addCarbonHourly(hourOfYear, FuelName.getFuelType(fuel), volume);
	}

	/**
	 *
	 * Need to be synchronized since evaluation in SupplyTrader can be done
	 * simultaneously.
	 *
	 * @param hour
	 *            of year
	 * @param fuelType
	 * @param volume
	 */
	public void addCarbonHourly(int hourOfYear, FuelType fuelType, float volume) {
		if (volume < 0) {
			logger.error("Negative amount cannot be produced. " + volume);
			return;
		}
		final int year = Date.getYear();
		increaseEmissionsYearly(year, volume);
		switch (fuelType) {
			case URANIUM:
				addValue(carbonNuclear, year, hourOfYear, volume);
				break;
			case COAL:
				addValue(carbonCoal, year, hourOfYear, volume);
				break;
			case LIGNITE:
				addValue(carbonLignite, year, hourOfYear, volume);
				break;
			case GAS:
				addValue(carbonGas, year, hourOfYear, volume);
				break;
			case OIL:
				addValue(carbonOil, year, hourOfYear, volume);
				break;
			case CLEAN_COAL:
				addValue(carbonCleanCoal, year, hourOfYear, volume);
				break;
			case CLEAN_GAS:
				addValue(carbonCleanGas, year, hourOfYear, volume);
				break;
			case CLEAN_LIGNITE:
				addValue(carbonCleanLignite, year, hourOfYear, volume);
				break;
			case OTHER:
				addValue(carbonOther, year, hourOfYear, volume);
				break;
			case RENEWABLE:
			case WATER:
			case STORAGE:
				addValue(carbonRenewable, year, hourOfYear, volume);
				break;
			case MINEGAS:
				addValue(carbonMineGas, year, hourOfYear, volume);
				break;
			case WASTE:
				addValue(carbonWaste, year, hourOfYear, volume);
				break;
			case SEWAGEGAS:
				addValue(carbonSewageGas, year, hourOfYear, volume);
				break;
			default:
				logger.error("Undefined Fueltype. " + fuelType);
		}
	}
	/**
	 * adds the amount of <code>carbonPeaker</code> to the specified
	 * <code>hour</code> of carbon production for a given day
	 *
	 * @param hour
	 *            0-23
	 * @param carbonPeaker
	 *            amount in MWh
	 */
	public void addCarbonPeaker(int hourOfDay, float volume) {
		addValue(carbonPeaker, Date.getYear(), Date.getHourOfYearFromHourOfDay(hourOfDay), volume);
	}

	public void addCarbonStartup(int hourOfDay, FuelType fuelType, float volume) {
		addCarbonStartupHourly(Date.getHourOfYearFromHourOfDay(hourOfDay), fuelType, volume);
	}

	public void addCarbonStartupHourly(int hourOfYear, FuelType fuelType, float volume) {

		if (volume < 0) {
			logger.error("Negative amount cannot be produced. " + volume);
			return;
		}
		final int year = Date.getYear();
		increaseEmissionsYearly(year, volume);
		switch (fuelType) {
			case LIGNITE:
				addValue(carbonLigniteStartup, year, hourOfYear, volume);
				break;
			case COAL:
				addValue(carbonCoalStartup, year, hourOfYear, volume);
				break;
			case GAS:
				addValue(carbonGasStartup, year, hourOfYear, volume);
				break;
			case OIL:
				addValue(carbonOilStartup, year, hourOfYear, volume);
				break;
			case URANIUM:
				addValue(carbonNuclear, year, hourOfYear, volume);
				break;
			case RENEWABLE:
				addValue(carbonRenewable, year, hourOfYear, volume);
				break;
			case OTHER:
				addValue(carbonOther, year, hourOfYear, volume);
				break;
			case CLEAN_COAL:
				addValue(carbonCleanCoalStartup, year, hourOfYear, volume);
				break;
			case CLEAN_GAS:
				addValue(carbonCleanGasStartup, year, hourOfYear, volume);
				break;
			case CLEAN_LIGNITE:
				addValue(carbonCleanLigniteStartup, year, hourOfYear, volume);
				break;
			case MINEGAS:
				addValue(carbonMineGas, year, hourOfYear, volume);
				break;
			case WASTE:
				addValue(carbonWaste, year, hourOfYear, volume);
				break;
			case SEWAGEGAS:
				addValue(carbonSewageGas, year, hourOfYear, volume);
				break;
			default:
				logger.error("Undefined Fueltype. " + fuelType);
		}
	}

	public float getCarbonCleanCoal(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCleanCoal, year, hourOfYear);
	}

	public float getCarbonCleanCoalStartup(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCleanCoalStartup, year, hourOfYear);
	}

	public float getCarbonCleanGas(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCleanGas, year, hourOfYear);
	}

	public float getCarbonCleanGasStartup(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCleanGasStartup, year, hourOfYear);
	}

	public float getCarbonCleanLignite(int year, int hourOfYear) {
		return getValueHourOfYear(carbonLignite, year, hourOfYear);
	}

	public float getCarbonCleanLigniteStartup(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCleanLigniteStartup, year, hourOfYear);
	}

	public float getCarbonCoal(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCoal, year, hourOfYear);
	}

	public float getCarbonCoalStartup(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCoalStartup, year, hourOfYear);
	}

	public float getCarbonGas(int year, int hourOfYear) {
		return getValueHourOfYear(carbonGas, year, hourOfYear);
	}

	public float getCarbonGasStartup(int year, int hourOfYear) {
		return getValueHourOfYear(carbonGasStartup, year, hourOfYear);
	}
	public float getCarbonLignite(int year, int hourOfYear) {
		return getValueHourOfYear(carbonLignite, year, hourOfYear);
	}

	public float getCarbonLigniteStartup(int year, int hourOfYear) {
		return getValueHourOfYear(carbonLigniteStartup, year, hourOfYear);
	}

	public float getCarbonMineGas(int year, int hourOfYear) {
		return getValueHourOfYear(carbonMineGas, year, hourOfYear);
	}

	public float getCarbonOil(int year, int hourOfYear) {
		return getValueHourOfYear(carbonOil, year, hourOfYear);
	}

	public float getCarbonOilStartup(int year, int hourOfYear) {
		return getValueHourOfYear(carbonOilStartup, year, hourOfYear);
	}

	public float getCarbonPeaker(int year, int hourOfYear) {
		return getValueHourOfYear(carbonPeaker, year, hourOfYear);
	}

	public float getCarbonSewageGas(int year, int hourOfYear) {
		return getValueHourOfYear(carbonSewageGas, year, hourOfYear);
	}

	public float getCarbonWaste(int year, int hourOfYear) {
		return getValueHourOfYear(carbonWaste, year, hourOfYear);
	}

	/**
	 * get emission factor caused by conventional plants excl. startup-emissions
	 * and pump storage plants for a specific hour of the year
	 */
	public float getEmissionsFactorDemandBasedHourly(int year, int hourOfYear) {
		return getValueHourOfYear(carbonFactorDemandBased, year, hourOfYear);
	}

	/**
	 * get emission factor caused by conventional plants excl. startup-emissions
	 * and pump storage plants for a specific hour of the year
	 */
	public float getEmissionsFactorProductionBasedHourly(int year, int hourOfYear) {
		return getValueHourOfYear(carbonFactorProductionBased, year, hourOfYear);
	}

	/**
	 * get total emissions caused by conventional plants excl. startup-emissions
	 * for a specific hour of the year
	 */
	public float getEmissionsHourlyProduction(int year, int hourOfYear) {
		return getValueHourOfYear(carbonCleanCoal, year, hourOfYear)
				+ getValueHourOfYear(carbonCleanGas, year, hourOfYear)
				+ getValueHourOfYear(carbonCleanLignite, year, hourOfYear)
				+ getValueHourOfYear(carbonCoal, year, hourOfYear)
				+ getValueHourOfYear(carbonGas, year, hourOfYear)
				+ getValueHourOfYear(carbonLignite, year, hourOfYear)
				+ getValueHourOfYear(carbonNuclear, year, hourOfYear)
				+ getValueHourOfYear(carbonOil, year, hourOfYear)
				+ getValueHourOfYear(carbonOther, year, hourOfYear)
				+ getValueHourOfYear(carbonRenewable, year, hourOfYear)
				+ getValueHourOfYear(carbonPeaker, year, hourOfYear)
				+ getValueHourOfYear(carbonMineGas, year, hourOfYear)
				+ getValueHourOfYear(carbonWaste, year, hourOfYear)
				+ getValueHourOfYear(carbonSewageGas, year, hourOfYear);
	}

	/**
	 * get total emissions caused by plant start-ups in a specific hour of day
	 */
	public float getEmissionsHourlyStartUp(int year, int hourOfYear) {
		float emissionSU = 0;
		if (carbonLigniteStartup.containsKey(year)
				&& carbonLigniteStartup.get(year).containsKey(hourOfYear)) {
			emissionSU += carbonLigniteStartup.get(year).get(hourOfYear);
		}

		if (carbonGasStartup.containsKey(year)
				&& carbonGasStartup.get(year).containsKey(hourOfYear)) {
			emissionSU += carbonGasStartup.get(year).get(hourOfYear);
		}
		if (carbonCoalStartup.containsKey(year)
				&& carbonCoalStartup.get(year).containsKey(hourOfYear)) {
			emissionSU += carbonCoalStartup.get(year).get(hourOfYear);
		}
		if (carbonCleanLigniteStartup.containsKey(year)
				&& carbonCleanLigniteStartup.get(year).containsKey(hourOfYear)) {
			emissionSU += carbonCleanLigniteStartup.get(year).get(hourOfYear);
		}

		if (carbonCleanGasStartup.containsKey(year)
				&& carbonCleanGasStartup.get(year).containsKey(hourOfYear)) {
			emissionSU += carbonCleanGasStartup.get(year).get(hourOfYear);
		}
		if (carbonCleanCoalStartup.containsKey(year)
				&& carbonCleanCoalStartup.get(year).containsKey(hourOfYear)) {
			emissionSU += carbonCleanCoalStartup.get(year).get(hourOfYear);
		}
		if (carbonOilStartup.containsKey(year)
				&& carbonOilStartup.get(year).containsKey(hourOfYear)) {
			emissionSU += carbonOilStartup.get(year).get(hourOfYear);
		}
		return emissionSU;
	}

	/**
	 * Get total emissions caused by conventional plants incl. startup-emissions
	 * for a specific hour of the year
	 */
	public float getEmissionsHourlyTotal(int year, int hourOfYear) {
		return getEmissionsHourlyProduction(year, hourOfYear)
				+ getEmissionsHourlyStartUp(year, hourOfYear);
	}

	/**
	 * get total emissions caused by conventional plants excl. startup-emissions
	 * for a specific hour of the day
	 */
	public float getEmissionsHourOfDay(int year, int hourOfDay) {
		return getEmissionsHourlyProduction(year, Date.getHourOfYearFromHourOfDay(hourOfDay));
	}

	/**
	 * get total emissions caused by plant start-ups in a specific hour of day
	 */
	public float getEmissionsHourOfDayStartUp(int year, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		return getEmissionsHourlyStartUp(year, hourOfYear);
	}

	public float getEmissionsYearly(int year) {
		if (!yearlyCarbonEmissions.containsKey(year)) {
			return 0;
		}
		return yearlyCarbonEmissions.get(year);
	}

	private void increaseEmissionsYearly(int year, float emission) {
		if (!yearlyCarbonEmissions.containsKey(year)) {
			yearlyCarbonEmissions.put(year, emission);
		} else {
			yearlyCarbonEmissions.put(year, yearlyCarbonEmissions.get(year) + emission);
		}
	}

	@Override
	public void initialize() {
	}
}