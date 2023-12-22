package data.storage;

import static markets.trader.spot.hydro.PumpStorageTrader.OPTIMIZATION_PERIOD;
import static simulations.scheduling.Date.HOURS_PER_DAY;
import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.time.LocalDate;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.scheduling.Date;
import supply.powerplant.PlantOption;
import supply.powerplant.technique.EnergyConversion;
import tools.types.FuelName;
import tools.types.FuelType;

public class PumpStoragePlant implements Comparable<PumpStoragePlant> {

	protected static final Logger logger = LoggerFactory
			.getLogger(PumpStoragePlant.class.getName());

	private int availability;
	private float availableCapacity;

	private LocalDate availableDate;
	private float chargeEfficiency;
	private int constructionTime;
	private float costsOperationMaintenanceFixed;

	private float efficiency;
	private EnergyConversion energyConversion;
	private int energyConversionIndex;
	private FuelName fuelName;
	private float generationCapacity;
	private float generationEfficiency;
	private float investmentPayment;
	private float[] longoperation = new float[HOURS_PER_YEAR];
	private float[] longStorageStatus = new float[HOURS_PER_YEAR];
	private String name;
	private float netPresentValue = 0;
	private int operatingLifetime;
	private float[] operation = new float[HOURS_PER_DAY];
	private int ownerID;
	private String ownerName;
	private float[] plannedOperation = new float[OPTIMIZATION_PERIOD];
	private float[] plannedStatus = new float[OPTIMIZATION_PERIOD];
	private float pumpCapacity;
	private LocalDate shutDownDate;
	private float[] storageInflow = new float[HOURS_PER_YEAR];
	private float storageStatus;
	private float storageVolume;
	private int unitID;

	public PumpStoragePlant() {
	}

	public PumpStoragePlant(PlantOption option) {
		setName(option.getName());
		setGenerationCapacity(option.getDischargeCapacity());
		setPumpCapacity(option.getChargeCapacity());
		setStorageVolume(option.getStorageVolume());
		setStorageStatus(0f);
		setStorageInflow(0f);
		setChargeEfficiency(option.getChargeEfficiency());
		setGenerationEfficiency(option.getDischargeEfficiency());
		setEfficiency(getChargeEfficiency() * getGenerationEfficiency());
		setAvailableDate(LocalDate.of(Date.getYear() + option.getConstructionTime(), 1, 1));
	}

	private void checkCostsOperationMaintenanceFixed() {
		if (costsOperationMaintenanceFixed < 100) {
			logger.error(
					this + ": Correct unit? Unit needs to be EUR/MW! Current value seems too low: "
							+ costsOperationMaintenanceFixed + ".");
		}
	}

	@Override
	public String toString() {
		return name + ", Generation Capacity: " + generationCapacity + ", Pump Capacity: "
				+ pumpCapacity;
	}

	// Compare according to efficiency or if identical according to construction
	// year
	@Override
	public int compareTo(PumpStoragePlant other) {
		if (getEfficiency() < other.getEfficiency()) {
			return -1;
		} else if (getEfficiency() > other.getEfficiency()) {
			return 1;
		}

		if (getStorageVolume() < other.getStorageVolume()) {
			return -1;
		} else if (getStorageVolume() > other.getStorageVolume()) {
			return 1;
		}

		if (getGenerationCapacity() < other.getGenerationCapacity()) {
			return -1;
		} else if (getGenerationCapacity() > other.getGenerationCapacity()) {
			return 1;
		}

		if (getAvailableYear() < other.getAvailableYear()) {
			return -1;
		} else if (getAvailableYear() > other.getAvailableYear()) {
			return 1;
		}

		if (getUnitID() < other.getUnitID()) {
			return -1;
		} else if (getUnitID() > other.getUnitID()) {
			return 1;
		} else {
			return 0;
		}
	}

	public int getAvailability() {
		return availability;
	}

	public float getAvailableCapacity() {
		return availableCapacity;
	}

	public LocalDate getAvailableDate() {
		return availableDate;
	}

	public int getAvailableYear() {
		return availableDate.getYear();
	}

	public float getChargeEfficiency() {
		return chargeEfficiency;
	}

	public int getConstructionTime() {
		return constructionTime;
	}

	public float getCostsOperationMaintenanceFixed() {
		return costsOperationMaintenanceFixed;
	}

	public float getEfficiency() {
		return efficiency;
	}

	public EnergyConversion getEnergyConversion() {
		return energyConversion;
	}

	public int getEnergyConversionIndex() {
		return energyConversionIndex;
	}

	public FuelName getFuelName() {
		return fuelName;
	}

	public FuelType getFuelType() {
		return FuelName.getFuelType(getFuelName());
	}

	public float getGenerationCapacity() {
		return generationCapacity;
	}

	public float getGenerationEfficiency() {
		return generationEfficiency;
	}

	public float getInvestmentPayment() {
		return investmentPayment;
	}

	public float[] getLongOperation() {
		return longoperation;
	}

	public float getLongStorageStatus(int hourOfYear) {
		return longStorageStatus[hourOfYear];
	}

	public String getName() {
		return name;
	}

	public float getNetPresentValue() {
		return netPresentValue;
	}

	public int getOperatingLifetime() {
		return operatingLifetime;
	}

	public float[] getOperation() {
		return operation;
	}

	public int getOwnerID() {
		return ownerID;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public float[] getPlannedOperation() {
		return plannedOperation;
	}

	public float[] getPlannedStatus() {
		return plannedStatus;
	}

	public float getPumpCapacity() {
		return pumpCapacity;
	}

	public LocalDate getShutDownDate() {
		return shutDownDate;
	}

	public int getShutDownYear() {
		return shutDownDate.getYear();
	}

	public float[] getStorageInflow() {
		return storageInflow;
	}

	public float getStorageInflow(int hourOfYear) {
		return storageInflow[hourOfYear];
	}

	public float getStorageStatus() {
		return storageStatus;
	}

	public float getStorageVolume() {
		return storageVolume;
	}

	public int getUnitID() {
		return unitID;
	}

	public void resetOperation() {
		Arrays.fill(operation, 0f);
	}

	public void resetPlannedOperation() {
		Arrays.fill(plannedOperation, 0f);
	}

	public void setAvailability(int availability) {
		this.availability = availability;
	}

	public void setAvailableCapacity(float availableCapacity) {
		this.availableCapacity = availableCapacity;
	}

	public void setAvailableDate(int availableYear) {
		setAvailableDate(LocalDate.of(availableYear, 1, 1));
	}

	public void setAvailableDate(LocalDate availableDate) {
		this.availableDate = availableDate;
	}

	public void setChargeEfficiency(float chargeEfficiency) {
		this.chargeEfficiency = chargeEfficiency;
	}

	public void setConstructionTime(int constructionTime) {
		this.constructionTime = constructionTime;
	}

	public void setCostsOperationMaintenanceFixed(float costsOperationMaintenanceFixed) {
		this.costsOperationMaintenanceFixed = costsOperationMaintenanceFixed;
		checkCostsOperationMaintenanceFixed();
	}

	public void setEfficiency(float efficiency) {
		this.efficiency = efficiency;
	}

	public void setEnergyConversion(EnergyConversion energyConversion) {
		this.energyConversion = energyConversion;
	}

	public void setEnergyConversionIndex(int energyConversionIndex) {
		this.energyConversionIndex = energyConversionIndex;
	}

	public void setFuelName(FuelName fuelName) {
		this.fuelName = fuelName;
	}

	public void setGenerationCapacity(float generationCapacity) {
		this.generationCapacity = generationCapacity;
	}

	public void setGenerationEfficiency(float generationEfficiency) {
		this.generationEfficiency = generationEfficiency;
	}

	public void setInvestmentPayment(float investmentPayment) {
		this.investmentPayment = investmentPayment;
	}

	public void setLongOperation(int hourOfYear, float operation) {
		this.longoperation[hourOfYear] = operation;
	}

	public void setLongStorageStatus(int hourOfYear, float longStorageStatus) {
		this.longStorageStatus[hourOfYear] = longStorageStatus;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setNetPresentValue(float netPresentValue) {
		this.netPresentValue = netPresentValue;
	}

	public void setOperatingLifetime(int operatingLifetime) {
		this.operatingLifetime = operatingLifetime;
	}

	public void setOwnerID(int ownerID) {
		this.ownerID = ownerID;
	}

	public void setOwnerName(String ownerName) {
		this.ownerName = ownerName;
	}

	public void setPlanIntoOperation() {
		System.arraycopy(plannedOperation, 0, operation, 0, HOURS_PER_DAY);
	}

	public void setPlannedOperation(float[] plannedOperation) {
		this.plannedOperation = plannedOperation;
	}

	public void setPlannedOperation(int hour, float value) {
		plannedOperation[hour] = value;
	}

	public void setPlannedStatus(float[] plannedStatus) {
		this.plannedStatus = plannedStatus;
	}

	public void setPlannedStatus(int hour, float value) {
		plannedStatus[hour] = value;
	}

	public void setPumpCapacity(float pumpCapacity) {
		this.pumpCapacity = pumpCapacity;
	}

	public void setShutDownDate(int shutDownYear) {
		setShutDownDate(LocalDate.of(shutDownYear, 12, 31));
	}

	public void setShutDownDate(LocalDate shutDownDate) {
		this.shutDownDate = shutDownDate;
	}

	public void setStorageInflow(float storageInflow) {
		Arrays.fill(this.storageInflow, storageInflow);
	}

	public void setStorageInflow(float[] storageInflow) {
		this.storageInflow = storageInflow;
	}

	public void setStorageStatus(float storageStatus) {
		if (getStorageVolume() + 0.2 < storageStatus) {
			logger.error("Storage overflow, should normally not occour. Storage volume "
					+ getStorageVolume() + " Storage status" + storageStatus);
		}

		if (storageStatus < -0.2) {
			logger.error(
					"Storage Deviation, should normally not occour, only in extreme shortage times. Storage status"
							+ storageStatus);
		}
		this.storageStatus = Math.min(Math.max(0, storageStatus), getStorageVolume());
	}

	public void setStorageVolume(float storageVolume) {
		this.storageVolume = storageVolume;
	}

	public void setUnitID(int unitID) {
		this.unitID = unitID;
	}
}