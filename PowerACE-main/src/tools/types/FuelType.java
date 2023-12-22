package tools.types;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Combine, change move together with FuelName and RenewableType
 * 
 * @author PowerACE fans
 *
 */
public enum FuelType {

	CLEAN_COAL(
			25,
			"cyan"),
	CLEAN_GAS(
			24,
			"orange"),
	CLEAN_LIGNITE(
			23,
			"pink"),
	COAL(
			2,
			"gray"),
	GAS(
			5,
			"#00BFFF"),
	LIGNITE(
			3,
			"#8B4513"),
	OIL(
			4,
			"black"),
	OTHER(
			0,
			"#FA8072"),
	MINEGAS(
			20,
			"#224b5c"),
	SEWAGEGAS(
			18,
			"#6e4f29"),
	SOLAR(
			10,
			"yellow"),
	WASTE(
			22,
			"#695768"),
	WIND_OFFSHORE(
			12,
			"dark-turquoise"),
	WIND_ONSHORE(
			11,
			"light-turquoise"),
	BIOGAS(
			13,
			"green"),
	TIDAL(
			21,
			"navy"),
	GEOTHERMAL(
			9,
			"red"),
	WATER(
			6,
			"blue"),
	RENEWABLE(
			30,
			"#9370DB"),
	URANIUM(
			1,
			"#DA70D6"),
	HYDROGEN(
			33,
			"#4C6341"),
	STORAGE(34,
			"#660066");

	/** Map to provide reference from fuel index to fuel name */
	private static Map<Integer, FuelType> fuelTypeIndexToFuelTypeMapping;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(FuelType.class.getName());

	public static FuelType getFuelTypeFromIndex(int fuelTypeIndex) {
		FuelType fuelTypeTemp = null;

		if (fuelTypeIndexToFuelTypeMapping == null) {
			FuelType.initMapping();
		}
		fuelTypeTemp = fuelTypeIndexToFuelTypeMapping.get(fuelTypeIndex);

		if (fuelTypeTemp == null) {
			throw new ClassCastException(
					"FuelType for fuelTypeIndex " + fuelTypeIndex + " is not defined.");
		}
		return fuelTypeTemp;
	}

	/** Get fuel type index */
	public static int getFuelTypeIndex(FuelType fuelType) {
		if (fuelType.equals(FuelType.OTHER)) {
			logger.warn("Fuel type " + FuelType.OTHER + " may be not defined!");
		}
		return fuelType.index;
	}

	private static void initMapping() {
		fuelTypeIndexToFuelTypeMapping = new HashMap<>();
		for (final FuelType fuelType : FuelType.values()) {
			fuelTypeIndexToFuelTypeMapping.put(fuelType.index, fuelType);
		}
	}

	/** Fuel type index (as used in database) */
	private int index;
	/** Use custom colors of the specific FuelType for plotting */
	private String color;
	/** Private constructor */
	private FuelType(int index, String color) {
		this.index = index;
		this.color = color;
	}

	public String getColor() {
		return color;
	}

	public boolean isCoal() {
		return ((this == COAL) || (this == CLEAN_COAL));
	}

	public boolean isGas() {
		return ((this == GAS) || (this == CLEAN_GAS));
	}

	public boolean isLignite() {
		return ((this == LIGNITE) || (this == CLEAN_LIGNITE));
	}
}