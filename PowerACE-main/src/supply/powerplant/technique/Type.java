package supply.powerplant.technique;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import supply.powerplant.CostCap;
import supply.powerplant.PlantAbstract;
import tools.types.FuelType;

public enum Type {

	/** Coal subcritical */
	COAL_SUB(
			"Coal_Subcritial"),
	/** Coal supercritical */
	COAL_SUPER(
			"Coal_Supercritial"),
	/** Coal ultrasupercritical */
	COAL_ULTRASUPER(
			"Coal_Ultrasupercritial"),
	/** Gas_Combined_Cycle_New */
	GAS_CC_NEW(
			"Gas_Combined_Cycle_New"),
	/** Gas_Combined_Cycle_New */
	GAS_CC_OLD(
			"Gas_Combined_Cycle_Old"),
	/** Gas_Internal_Combustion_New */
	GAS_COMB_NEW(
			"Gas_Combustion_New"),
	/** Gas_Internal_Combustion_New */
	GAS_COMB_OLD(
			"Gas_Combustion_Old"),
	/** Gas_Internal_Combustion */
	GAS_STEAM(
			"Gas_Steam_Turbine"),
	/** Lignite_New */
	LIG_NEW(
			"Lignite_New"),
	/** Lignite_Old */
	LIG_OLD(
			"Lignite_Old"),
	/** Nuclear_Generation_1 */
	NUC_GEN_1(
			"Nuclear_Generation_1"),
	/** Nuclear_Generation_2 */
	NUC_GEN_2(
			"Nuclear_Generation_2"),
	/** Nuclear_Generation_3 */
	NUC_GEN_3(
			"Nuclear_Generation_3"),
	/** Nuclear_Generation_4 */
	NUC_GEN_4(
			"Nuclear_Generation_4"),
	/** Oil_Internal_Combustion */
	OIL_CC(
			"Oil_Combined_Cycle"),
	/** Oil_Combustion */
	OIL_COMB(
			"Oil_Combustion"),
	/** Oil_Steam_Turbine */
	OIL_STEAM(
			"Oil_Steam_Turbine"),
	OTHER(
			"");

	private static LocalDate limitGas = LocalDate.of(1980, 1, 1);
	private static LocalDate limitLignite = LocalDate.of(1980, 1, 1);
	private static LocalDate limitNuclear1 = LocalDate.of(1965, 1, 1);
	private static LocalDate limitNuclear2 = LocalDate.of(1996, 1, 1);
	private static LocalDate limitNuclear3 = LocalDate.of(2015, 1, 1);

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Type.class.getName());

	public static void determinePowerPlantCategory(CostCap costCap) {

		// Nuclear
		if (costCap.getFuelType() == FuelType.URANIUM) {
			Type.determineNuclearCategory(costCap);
		}
		// Coal
		else if ((costCap.getFuelType() == FuelType.CLEAN_COAL)
				|| (costCap.getFuelType() == FuelType.COAL)) {
			Type.determineCoalCategory(costCap);
		}
		// Gas
		else if ((costCap.getFuelType() == FuelType.CLEAN_GAS)
				|| (costCap.getFuelType() == FuelType.GAS)) {
			Type.determineGasCategory(costCap);
		}
		// Lignite
		else if ((costCap.getFuelType() == FuelType.CLEAN_LIGNITE)
				|| (costCap.getFuelType() == FuelType.LIGNITE)) {
			Type.determineLigniteCategory(costCap);
		}
		// Oil
		else if (costCap.getFuelType() == FuelType.OIL) {
			Type.determineOilCategory(costCap);
		}
		// Other
		else {
			costCap.setCategory(Type.OTHER);
		}
	}

	/**
	 * Determine the power plant category based on the FuelType and the age of
	 * the power plant. This is relevant for start up costs that differ for each
	 * PowerPlantCategory.
	 * 
	 * @param plant
	 *            The plant for which the category is determined.
	 */
	public static void determinePowerPlantCategory(PlantAbstract plant) {

		// Nuclear
		if (plant.getFuelType() == FuelType.URANIUM) {
			Type.determineNuclearCategory(plant);
		}
		// Coal
		else if ((plant.getFuelType() == FuelType.CLEAN_COAL)
				|| (plant.getFuelType() == FuelType.COAL)) {
			Type.determineCoalCategory(plant);
		}
		// Gas
		else if ((plant.getFuelType() == FuelType.CLEAN_GAS)
				|| (plant.getFuelType() == FuelType.GAS)) {
			Type.determineGasCategory(plant);
		}
		// Lignite
		else if ((plant.getFuelType() == FuelType.CLEAN_LIGNITE)
				|| (plant.getFuelType() == FuelType.LIGNITE)) {
			Type.determineLigniteCategory(plant);
		}
		// Oil
		else if (plant.getFuelType() == FuelType.OIL) {
			Type.determineOilCategory(plant);
		}
		// Other
		else {
			plant.setCategory(Type.OTHER);
		}
	}

	private static void determineCoalCategory(CostCap costCap) {
		final float efficiency = costCap.getEfficiency();

		// see Current and Prospective Costs of Electricity Generation until
		// 2050 / DIW 2012

		if (efficiency <= 0.40) {
			costCap.setCategory(Type.COAL_SUB);
		} else if (efficiency <= 0.45) {
			costCap.setCategory(Type.COAL_SUPER);
		} else {
			costCap.setCategory(Type.COAL_ULTRASUPER);
		}
	}

	private static void determineCoalCategory(PlantAbstract plant) {
		final float efficiency = plant.getEfficiency();

		// see Current and Prospective Costs of Electricity Generation until
		// 2050 / DIW 2012

		if (efficiency <= 0.40) {
			plant.setCategory(Type.COAL_SUB);
		} else if (efficiency <= 0.45) {
			plant.setCategory(Type.COAL_SUPER);
		} else {
			plant.setCategory(Type.COAL_ULTRASUPER);
		}
	}

	private static void determineGasCategory(CostCap costCap) {
		if ((costCap.getEnergyConversion() == EnergyConversion.COMBINED_CYCLE)
				|| (costCap.getEnergyConversion() == EnergyConversion.COMBINED_HEAT_POWER)) {
			if (costCap.getAvailableDate().isBefore(limitGas)) {
				costCap.setCategory(Type.GAS_CC_OLD);
			} else {
				costCap.setCategory(Type.GAS_CC_NEW);
			}
		} else if ((costCap.getEnergyConversion() == EnergyConversion.GAS_TURBINE)
				|| (costCap.getEnergyConversion() == EnergyConversion.GAS_TURBINE_H)
				|| (costCap.getEnergyConversion() == EnergyConversion.HEATING_POWER)
				|| (costCap.getEnergyConversion() == EnergyConversion.INTERNAL_COMBUSTION)) {
			if (costCap.getAvailableDate().isBefore(limitGas)) {
				costCap.setCategory(Type.GAS_COMB_OLD);
			} else {
				costCap.setCategory(Type.GAS_COMB_NEW);
			}
		} else if (costCap.getEnergyConversion().isSteamTurbine()) {
			costCap.setCategory(Type.GAS_STEAM);
		} else {
			costCap.setCategory(Type.OTHER);
			logger.error("what happened for gas category" + costCap.getEnergyConversion());
		}
	}

	private static void determineGasCategory(PlantAbstract plant) {
		if ((plant.getEnergyConversion() == EnergyConversion.COMBINED_CYCLE)
				|| (plant.getEnergyConversion() == EnergyConversion.COMBINED_HEAT_POWER)) {
			if (plant.getAvailableDate().isBefore(limitGas)) {
				plant.setCategory(Type.GAS_CC_OLD);
			} else {
				plant.setCategory(Type.GAS_CC_NEW);
			}
		} else if ((plant.getEnergyConversion() == EnergyConversion.GAS_TURBINE)
				|| (plant.getEnergyConversion() == EnergyConversion.GAS_TURBINE_H)
				|| (plant.getEnergyConversion() == EnergyConversion.HEATING_POWER)
				|| (plant.getEnergyConversion() == EnergyConversion.INTERNAL_COMBUSTION)) {
			if (plant.getAvailableDate().isBefore(limitGas)) {
				plant.setCategory(Type.GAS_COMB_OLD);
			} else {
				plant.setCategory(Type.GAS_COMB_NEW);
			}
		} else if (plant.getEnergyConversion().isSteamTurbine()) {
			plant.setCategory(Type.GAS_STEAM);
		} else {
			plant.setCategory(Type.OTHER);
			logger.error("what happened for gas category" + plant.getEnergyConversion());
		}
	}

	private static void determineLigniteCategory(CostCap costCap) {
		if (costCap.getAvailableDate().isBefore(limitLignite)) {
			costCap.setCategory(Type.LIG_OLD);
		} else {
			costCap.setCategory(Type.LIG_NEW);
		}
	}

	private static void determineLigniteCategory(PlantAbstract plant) {
		if (plant.getAvailableDate().isBefore(limitLignite)) {
			plant.setCategory(Type.LIG_OLD);
		} else {
			plant.setCategory(Type.LIG_NEW);
		}
	}

	private static void determineNuclearCategory(CostCap costCap) {
		if (costCap.getAvailableDate().isBefore(limitNuclear1)) {
			costCap.setCategory(Type.NUC_GEN_1);
		} else if (costCap.getAvailableDate().isBefore(limitNuclear2)) {
			costCap.setCategory(Type.NUC_GEN_2);
		} else if (costCap.getAvailableDate().isBefore(limitNuclear3)) {
			costCap.setCategory(Type.NUC_GEN_3);
		} else {
			costCap.setCategory(Type.NUC_GEN_4);
		}
	}

	private static void determineNuclearCategory(PlantAbstract plant) {
		if (plant.getAvailableDate().isBefore(limitNuclear1)) {
			plant.setCategory(Type.NUC_GEN_1);
		} else if (plant.getAvailableDate().isBefore(limitNuclear2)) {
			plant.setCategory(Type.NUC_GEN_2);
		} else if (plant.getAvailableDate().isBefore(limitNuclear3)) {
			plant.setCategory(Type.NUC_GEN_3);
		} else {
			plant.setCategory(Type.NUC_GEN_4);
		}
	}

	private static void determineOilCategory(CostCap costCap) {
		if ((costCap.getEnergyConversion() == EnergyConversion.GAS_TURBINE)
				|| (costCap.getEnergyConversion() == EnergyConversion.GAS_TURBINE_H)
				|| (costCap.getEnergyConversion() == EnergyConversion.HEATING_POWER)
				|| (costCap.getEnergyConversion() == EnergyConversion.INTERNAL_COMBUSTION)) {
			costCap.setCategory(Type.OIL_COMB);
		} else if (costCap.getEnergyConversion().isSteamTurbine()) {
			costCap.setCategory(Type.OIL_STEAM);
		} else if ((costCap.getEnergyConversion() == EnergyConversion.COMBINED_CYCLE)
				|| (costCap.getEnergyConversion() == EnergyConversion.COMBINED_HEAT_POWER)) {
			costCap.setCategory(OIL_CC);
		} else {
			costCap.setCategory(Type.OTHER);
			logger.error("oil category failure");
		}
	}

	private static void determineOilCategory(PlantAbstract plant) {
		if ((plant.getEnergyConversion() == EnergyConversion.GAS_TURBINE)
				|| (plant.getEnergyConversion() == EnergyConversion.GAS_TURBINE_H)
				|| (plant.getEnergyConversion() == EnergyConversion.HEATING_POWER)
				|| (plant.getEnergyConversion() == EnergyConversion.INTERNAL_COMBUSTION)) {
			plant.setCategory(Type.OIL_COMB);
		} else if (plant.getEnergyConversion().isSteamTurbine()) {
			plant.setCategory(Type.OIL_STEAM);
		} else if ((plant.getEnergyConversion() == EnergyConversion.COMBINED_CYCLE)
				|| (plant.getEnergyConversion() == EnergyConversion.COMBINED_HEAT_POWER)) {
			plant.setCategory(OIL_CC);
		} else {
			plant.setCategory(Type.OTHER);
			logger.error("oil category failure");
		}
	}

	private final String name;

	private Type(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

}