package supply.powerplant.technique;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EnergyConversion {

	ADIABATIC_COMPRESSED_AIR(
			18,
			" fs pattern 1 "),
	COMBINED_CYCLE(
			3,
			" fs solid "),
	COMBINED_HEAT_POWER(
			9,
			" fs solid "),
	FUEL_CELL(
			19,
			" fs solid "),
	GAS_TURBINE(
			2,
			" fs pattern 4 "),
	GAS_TURBINE_H(
			7,
			" fs pattern 4 "),
	HEATING(
			11,
			" fs pattern 1 "),
	HEATING_PEAK(
			6,
			" fs pattern 1 "),
	HEATING_POWER(
			10,
			" fs pattern 1 "),
	INTERNAL_COMBUSTION(
			8,
			" fs pattern 2 "),
	LITHIUM_ION_BATTERY(
			16,
			" fs solid "),
	PUMPED_HYDRO(
			15,
			" fs solid "),
	REDOX_FLOW_BATTERY(
			17,
			" fs pattern 2 "),
	STEAM_TURBINE(
			1,
			" fs pattern 5 "),
	STEAM_TURBINE_BPT(
			14,
			" fs pattern 5 "),
	STEAM_TURBINE_ECT(
			13, " fs pattern 5 "),
	ELECTRIC_THERMAL(
			19, " fs pattern 7 ");

	/** Map to provide reference to EnergyConversion from index */
	private static Map<Integer, EnergyConversion> indexToEnergyConversionMapping;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(EnergyConversion.class.getName());

	public static EnergyConversion getEnergyConversionFromIndex(int index) {
		EnergyConversion energyConversionTemp = null;

		if (indexToEnergyConversionMapping == null) {
			EnergyConversion.initMapping();
		}
		energyConversionTemp = indexToEnergyConversionMapping.get(index);

		if (energyConversionTemp == null) {
			logger.warn("Index " + index + " undefined! STEAM_TURBINE_ECT assumed.");
			energyConversionTemp = STEAM_TURBINE_ECT;
		}
		return energyConversionTemp;
	}

	private static void initMapping() {
		indexToEnergyConversionMapping = new HashMap<>();
		for (final EnergyConversion energyConversion : EnergyConversion.values()) {
			indexToEnergyConversionMapping.put(energyConversion.index, energyConversion);
		}
	}

	/** Fill style of each energy conversion for plotting */
	private String fillStyle;
	/** Energy conversion index (as used in database) */
	private int index;
	/** Private constructor */
	private EnergyConversion(int index, String fillStyle) {
		this.index = index;
		this.fillStyle = fillStyle;
	}

	public String getFillStyle() {
		return fillStyle;
	}

	public String getPowerHeatRatioCategory() {
		switch (this) {
			case HEATING_POWER:
				return "HKW";
			case GAS_TURBINE:
				return "GT";
			case STEAM_TURBINE_ECT:
				return "ST_ECT";
			case STEAM_TURBINE_BPT:
				return "ST_BPT";
			case COMBINED_CYCLE:
				return "CC";
			default:
				return "no cogeneration";
		}
	}

	public boolean isGasTurbine() {
		return (this == GAS_TURBINE) || (this == GAS_TURBINE_H);
	}
	public boolean isSteamTurbine() {
		return (this == STEAM_TURBINE) || (this == STEAM_TURBINE_ECT)
				|| (this == STEAM_TURBINE_BPT);
	}

}