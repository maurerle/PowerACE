package tools.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.initialization.Settings;
import simulations.scheduling.Date;

/**
 * The different fuel names used in the model.
 * <p>
 * Whenever possible use these types instead of {@link FuelType} which has a
 * lower resolution.
 * 
 * 
 * 2017-07-17 Removed Solar and Wind since it is not a Fuel TODO: Switch some
 * Fueles to Renewables or consolidate the enums
 * 
 * 2019-09-23 Updated emission factors from UBA, year 2017:
 * <ul>
 * https://www.umweltbundesamt.de/themen/klima-energie/treibhausgas-emissionen
 * </ul>
 * Change: tco2/TJ in tCO2/MJ with Factor 3600/10^6
 * 
 * according to Energieträger
 * 
 * @author
 */
public enum FuelName {
	BIOFUEL(
			21,
			FuelType.BIOGAS,
			0.256f// mean of biodiesel and bio ethanol
	),
	BIOGAS(
			16,
			FuelType.BIOGAS,
			0.326f),
	BIOMASS(
			13,
			FuelType.BIOGAS,
			0.284f// mean of black liquore, fibre and
					// de-inking residues, meat
					// and bone-meals
	),
	BLASTFURNACEGAS(
			17,
			FuelType.GAS,
			0.9299f),
	CLEAN_COAL(
			25,
			FuelType.CLEAN_COAL,
			0.336f),
	CLEAN_GAS(
			24,
			FuelType.CLEAN_GAS,
			0.201f),
	CLEAN_LIGNITE(
			23,
			FuelType.CLEAN_LIGNITE,
			0.404f), // See lignite
	COAL(
			2,
			FuelType.COAL,
			0.336f),
	COAL_AT_COAST(
			25,
			FuelType.COAL,
			0.336f),
	COAL_FAR_COAST(
			26,
			FuelType.COAL,
			0.336f),
	GAS(
			5,
			FuelType.GAS,
			0.201f),
	GEOTHERMAL(
			9,
			FuelType.GEOTHERMAL,
			0f),
	HEATING_OIL(
			14,
			FuelType.OIL,
			0.2665f),
	HEATING_OIL_HEAVY(
			15,
			FuelType.OIL,
			0.291f),
	HYDRO_PUMPED_STORAGE(
			29,
			FuelType.WATER,
			0f),
	HYDRO_SEASONAL_STORAGE(
			30,
			FuelType.WATER,
			0f),
	HYDROGEN(
			33,
			FuelType.HYDROGEN,
			0f),
	HYDROLARGESCALE(
			28,
			FuelType.WATER,
			0f),
	HYDROSMALLSCALE(
			31,
			FuelType.WATER,
			0f),
	LANDFILLGAS(
			19,
			FuelType.GAS,
			0.401f),
	LIGNITE(// Mean of Rhineland and
			// luatian mining destrict,
			// not central
			// germany, because its very
			// small
			3,
			FuelType.LIGNITE,
			0.404f),
	MINEGAS(
			20,
			FuelType.MINEGAS,
			0.245f // UBA: Pit gas
	),
	OIL(
			4,
			FuelType.OIL,
			0.26388f// UBA: crude oil
	),
	OTHER(
			0,
			FuelType.RENEWABLE,
			0f),
	INTERCONNECTOR(
			0,
			FuelType.OTHER,
			0f),
	RUNNING_WATER(
			6,
			FuelType.WATER,
			0f),
	SEWAGEGAS(
			18,
			FuelType.SEWAGEGAS,
			0.3776f),
	SOLAR(
			10,
			FuelType.SOLAR,
			0f),
	STORAGE(
			34,
			FuelType.STORAGE,
			0f),
	TIDAL(
			32,
			FuelType.TIDAL,
			0f),
	URANIUM(
			1,
			FuelType.URANIUM,
			0f),
	WASTE(
			22,
			FuelType.WASTE,
			0.295f // mean of municipal, industrial and hazardous
					// waste
	),
	WATER(
			7,
			FuelType.WATER,
			0f),
	WIND_OFFSHORE(
			12,
			FuelType.WIND_OFFSHORE,
			0f),
	WIND_ONSHORE(
			11,
			FuelType.WIND_ONSHORE,
			0f),
	WOOD(
			27,
			FuelType.RENEWABLE,
			0.353f// UBA: untreated fuel wood, waste
					// wood (industry), waste wood
					// (small combustion), bark
	);

	/** Map to provide reference from fuel index to fuel name */
	private static Map<Integer, FuelName> fuelIndexToFuelNameMapping;

	/** List of renewable types with fluctuating profile */
	private static Set<FuelName> fluctuatingRenewableTechnologies = new HashSet<>();

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(FuelName.class.getName());

	public static Set<FuelName> getFluctuatingTechnologies() {
		if (!fluctuatingRenewableTechnologies.isEmpty()) {
			return fluctuatingRenewableTechnologies;
		}
		for (final FuelName type : getRenewableTypes()) {
			if (type.isFluctuating()) {
				fluctuatingRenewableTechnologies.add(type);
			}
		}
		return fluctuatingRenewableTechnologies;
	}

	/** Get fuel index from fuel name */
	public static int getFuelIndex(FuelName fuelName) {
		return fuelName.index;
	}

	/** Get fuel names mapped to their index */
	public static Map<Integer, FuelName> getFuelIndexToFuelNameMap() {
		if (fuelIndexToFuelNameMapping == null) {
			FuelName.initMapping();
		}
		return fuelIndexToFuelNameMapping;
	}

	/** Get fuel name from specified fuel index */
	public static FuelName getFuelName(int fuelIndex) {
		FuelName fuelNameTemp = null;

		if (fuelIndexToFuelNameMapping == null) {
			FuelName.initMapping();
		}
		fuelNameTemp = fuelIndexToFuelNameMapping.get(fuelIndex);

		if (fuelNameTemp == null) {
			throw new ClassCastException(
					"FuelName for fuelIndex " + fuelIndex + " is not defined.");
		}
		return fuelNameTemp;
	}

	/** Get clean fuel name of gas, lignite or coal */
	public static FuelName getFuelNameClean(FuelName fuelName) {
		if ((fuelName == FuelName.COAL) || (fuelName == FuelName.COAL_AT_COAST)
				|| (fuelName == FuelName.COAL_FAR_COAST)) {
			return FuelName.CLEAN_COAL;
		}
		if (fuelName == FuelName.LIGNITE) {
			return FuelName.CLEAN_LIGNITE;
		}
		if (fuelName == FuelName.GAS) {
			return FuelName.CLEAN_GAS;
		}

		logger.error("No clean version of FuelName " + fuelName.name() + " available.");
		return fuelName;
	}

	/**
	 * Returns the corresponding <code>FuelType</code> for the fuel.
	 * 
	 * @param fuelName
	 *            The FuelName for which the <code>FuelType</code> is requested.
	 * @return The corresponding <code>FuelType</code>.
	 */
	public static FuelType getFuelType(FuelName fuelName) {
		return fuelName.fuelType;
	}

	public static Set<FuelName> getRenewableTypes() {
		final Set<FuelName> renewableType = new HashSet<>();
		renewableType.add(FuelName.BIOGAS);
		renewableType.add(FuelName.BIOMASS);
		renewableType.add(FuelName.GEOTHERMAL);
		renewableType.add(FuelName.HYDROLARGESCALE);
		renewableType.add(FuelName.HYDROSMALLSCALE);
		// Sometimes Other is considered in scenarios as renewable
		renewableType.add(FuelName.OTHER);
		renewableType.add(FuelName.SOLAR);
		renewableType.add(FuelName.TIDAL);
		renewableType.add(FuelName.WATER);
		renewableType.add(FuelName.RUNNING_WATER);
		renewableType.add(FuelName.WIND_OFFSHORE);
		renewableType.add(FuelName.WIND_ONSHORE);
		return renewableType;
	}

	private static void initMapping() {
		fuelIndexToFuelNameMapping = new HashMap<>();
		for (final FuelName fuelName : FuelName.values()) {
			fuelIndexToFuelNameMapping.put(fuelName.index, fuelName);
		}
	}

	private static float naturalGasPhaseOutFactor(int year) {
		// For verseas Project a phase-out for natural gas is assumed
		final int yearMathanePhaseOutStart = 2030;
		if (Settings.isNaturalGasPhaseOut() && (year >= yearMathanePhaseOutStart)) {
			final float quotaYearlyIncrease = 0.05f;

			final float initialCarbonFreeGasQuota = 0.25f;
			return Math.max(0, 1 - initialCarbonFreeGasQuota
					- (quotaYearlyIncrease * (year - yearMathanePhaseOutStart)));
		} else {
			return 1;
		}
	}

	/** Emission factor in [tCO2/MWh(thermal)] */
	private float emissionFactor;

	/** Corresponding fuel type */
	private FuelType fuelType;

	/** Fuel name index (as used in database) */
	private int index;

	/** Private constructor */
	private FuelName(int index, FuelType fuelType, float emissionFactor) {
		this.index = index;
		this.fuelType = fuelType;
		this.emissionFactor = emissionFactor;
	}

	/**
	 * @return Emission factor in ton Carbon/MWh(th)
	 */
	public float getCarbonEmissionFactor() {
		if (this == GAS) {
			return emissionFactor * naturalGasPhaseOutFactor(Date.getYear());
		}
		return emissionFactor;
	}
	/**
	 * @return Emission factor in ton Carbon/MWh(th)
	 */
	public float getCarbonEmissionFactor(int year) {
		if (this == GAS) {
			return emissionFactor * naturalGasPhaseOutFactor(year);
		}
		return emissionFactor;
	}

	/**
	 * Returns the corresponding <code>FuelType</code> for this fuel.
	 * 
	 * @return The corresponding <code>FuelType</code>.
	 */
	public FuelType getFuelType() {
		return FuelName.getFuelType(this);
	}

	/**
	 * @return true if type is fluctuating eg. pv
	 */
	public boolean isFluctuating() {
		boolean fluctuating;

		if ((this == FuelName.SOLAR) || (this == FuelName.WIND_OFFSHORE)
				|| (this == FuelName.WIND_ONSHORE)) {
			fluctuating = true;
		} else {
			fluctuating = false;
		}

		return fluctuating;
	}

	public boolean isRenewableType() {
		if (getRenewableTypes().contains(this)) {
			return true;
		}
		return false;
	}
}