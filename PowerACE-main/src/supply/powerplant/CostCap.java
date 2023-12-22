package supply.powerplant;

import supply.powerplant.technique.Type;

/**
 * Specific subclass of PlantAbstract which allows cumulating merit order units
 * with the same economic parameters.
 */
public class CostCap extends PlantAbstract implements Comparable<CostCap> {

	/**
	 * Cumulated net capacity of merit order units with the same economic
	 * parameters
	 */
	private float cumulatedNetCapacity;

	/**
	 * Real power plant.
	 */
	private boolean realPowerPlant;

	public CostCap() {
	}

	/** Make (deep) copy of object */
	public CostCap(CostCap costCapOrg) {
		availableDate = costCapOrg.availableDate;
		name = costCapOrg.getName();
		unitID = costCapOrg.unitID;
		costsCarbonVar = costCapOrg.costsCarbonVar;
		category = costCapOrg.category;
		efficiency = costCapOrg.efficiency;
		energyConversion = costCapOrg.energyConversion;
		costsFuelVar = costCapOrg.costsFuelVar;
		fuelName = costCapOrg.fuelName;
		netCapacity = costCapOrg.netCapacity;
		minimumProduction = costCapOrg.minimumProduction;
		mustrun = costCapOrg.mustrun;
		category = costCapOrg.getCategory();
		cumulatedNetCapacity = costCapOrg.cumulatedNetCapacity;
		setInvestmentPayment(costCapOrg.getInvestmentPayment());
		costsVar = costCapOrg.costsVar;
		costsOperationMaintenanceVar = costCapOrg.costsOperationMaintenanceVar;
		shutDownDate = costCapOrg.shutDownDate;
		realPowerPlant = costCapOrg.realPowerPlant;
		carbonCapturePercentage = costCapOrg.carbonCapturePercentage;
	}

	/** Transform Plant object in CostCap object */
	public CostCap(PlantAbstract plant) {
		unitID = plant.getUnitID();
		name = plant.getName();
		cumulatedNetCapacity = plant.getNetCapacity();
		netCapacity = plant.getNetCapacity();
		minimumProduction = plant.getMinProduction();
		mustrun = plant.isMustrun();
		efficiency = plant.getEfficiency();
		fuelName = plant.getFuelName();
		costsFuelVar = plant.getCostsFuelVar();
		costsCarbonVar = plant.costsCarbonVar;
		costsOperationMaintenanceVar = plant.costsOperationMaintenanceVar;
		availableDate = plant.getAvailableDate();
		shutDownDate = plant.getShutDownDate();
		energyConversion = plant.getEnergyConversion();
		category = plant.getCategory();
		setInvestmentPayment(plant.getInvestmentPayment());
		state = plant.state;
		Type.determinePowerPlantCategory(this);
		realPowerPlant = true;
		carbonCapturePercentage = plant.carbonCapturePercentage;
	}

	/** Compare with respect to total variable costs */
	@Override
	public int compareTo(CostCap costCap) {
		if (Float.compare(costsVar, costCap.costsVar) != 0) {
			return Float.compare(costsVar, costCap.costsVar);
		}
		if (Float.compare(costCap.netCapacity, netCapacity) != 0) {
			return Float.compare(costCap.netCapacity, netCapacity);
		}
		return Integer.compare(costCap.unitID, unitID);
	}

	public float getCumulatedNetCapacity() {
		return cumulatedNetCapacity;
	}

	/**
	 * 
	 * 
	 * @return true, if representing a true power plant, false if for exampling
	 *         peaker or sheddable load
	 */
	public boolean isRealPowerPlant() {
		return realPowerPlant;
	}

	public void setCumulatedNetCapacity(float cumulatedNetCapacity) {
		this.cumulatedNetCapacity = cumulatedNetCapacity;
	}

	/**
	 * 
	 * 
	 * @return true, if representing a true power plant, false if for exampling
	 *         peaker or sheddable load
	 */
	public void setRealPowerPlant(boolean realPowerPlant) {
		this.realPowerPlant = realPowerPlant;
	}

	@Override
	public String toString() {
		return "UnitID: " + unitID + "; varCost: " + costsVar + " NetCapacity: " + netCapacity
				+ "; cumulatedNetCapacity: " + cumulatedNetCapacity + "; Fuel Name: " + fuelName;
	}

}