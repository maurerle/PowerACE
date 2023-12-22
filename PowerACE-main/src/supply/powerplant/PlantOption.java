package supply.powerplant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import simulations.MarketArea;
import simulations.initialization.Settings;
import supply.invest.State;
import supply.invest.StateStrategic;
import supply.powerplant.Plant.RevenueType;
import supply.powerplant.technique.Type;

/**
 * Specific data structure for investment options
 *
 * @since 28.02.2006
 * @author Massimo Genoese
 */
public class PlantOption extends PlantAbstract implements Comparable<PlantOption>, Cloneable {

	private Map<Integer, Float> cashFlow;
	private float emissionCosts = 0;
	private float fuelCosts = 0;
	private float investmentAdd = 0;
	private boolean isBuiltNow;
	private MarketArea marketArea;
	private float netPresentValue = 0;
	private float annuity = 0;
	/**
	 * Indicates whether investment option is singular (e.g. retrofit) or can be
	 * executed repeatedly (generally construction of new plant)
	 */
	private boolean singularOption = false;
	private int startUpCosts = 1;
	/** Indicates whether storage technology or not */
	private boolean storage;
	/** Only applicable for storage technologies */
	private float storageVolume;
	private float totalEmissionCosts = 0;
	private float totalFixedCosts = 0;
	private float totalFuelCosts = 0;
	private float totalInvestment = 0;
	private float totalPowerSales = 0;
	private float totalStartUpCosts = 0;
	private float totalVariableOMCosts = 0;

	private int utilisationHours = 0;
	/** Only applicable for storage technologies */
	private Map<Integer, List<Float>> yearlyOperation = new HashMap<>();

	private final float[] yearlyStartUpCosts = new float[150];

	public PlantOption() {
	}

	public PlantOption(PlantOption capacityOptionOrg) {
		marketArea = capacityOptionOrg.marketArea;
		setAvailableDate(capacityOptionOrg.getAvailableYear());
		setConstructionTime(capacityOptionOrg.getConstructionTime());
		setEfficiency(capacityOptionOrg.getEfficiency());
		setEnergyConversionIndex(capacityOptionOrg.getEnergyConversionIndex());
		setEnergyConversion(capacityOptionOrg.getEnergyConversion());
		setFuelName(capacityOptionOrg.getFuelName());
		setInvestmentPayment(capacityOptionOrg.getInvestmentPayment());
		setName(capacityOptionOrg.getName());
		setNetCapacity(capacityOptionOrg.getNetCapacity());
		setOperatingLifetime(capacityOptionOrg.getOperatingLifetime());
		setShutDownDate(capacityOptionOrg.getShutDownDate());
		setStartUpCosts(capacityOptionOrg.getStartUpCosts());
		setCostsOperationMaintenanceVar(capacityOptionOrg.getCostsOperationMaintenanceVar());
		setCostsOperationMaintenanceFixed(capacityOptionOrg.getCostsOperationMaintenanceFixed());
		setStorage(capacityOptionOrg.isStorage());
		if (capacityOptionOrg.isStorage()) {
			setStorageVolume(capacityOptionOrg.getStorageVolume());
		}
		Type.determinePowerPlantCategory(this);
		cashFlow = new HashMap<>();
	}

	@Override
	public PlantOption clone() {
		try {
			return (PlantOption) super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new InternalError();
		}
	}

	/** Compare two plant option according to their current net present value */
	@Override
	public int compareTo(PlantOption that) {
		if (getNetPresentValue() == that.getNetPresentValue()) {
			return 0;
		}
		if (getNetPresentValue() < that.getNetPresentValue()) {
			return 1;
		}
		return -1;
	}

	public Map<Integer, Float> getCashFlow() {
		return cashFlow;
	}

	public float getCashFlow(int index) {
		if (cashFlow.containsKey(index)) {
			return cashFlow.get(index);
		}
		return 0f;
	}

	public float getCertificateCosts() {
		return emissionCosts;
	}

	/** Only applicable for storage technologies */
	public float getChargeCapacity() {
		return getNetCapacity();
	}

	/** Only applicable for storage technologies */
	public float getChargeEfficiency() {
		return (float) Math.sqrt(getEfficiency());
	}

	/** Only applicable for storage technologies */
	public float getDischargeCapacity() {
		return getNetCapacity();
	}

	/** Only applicable for storage technologies */
	public float getDischargeEfficiency() {
		return (float) Math.sqrt(getEfficiency());
	}

	public float getFuelCosts() {
		return fuelCosts;
	}

	public float getInvestmentAdd() {
		return investmentAdd;
	}

	public MarketArea getMarketArea() {
		return marketArea;
	}

	@Override
	public float getNetPresentValue() {
		return netPresentValue;
	}

	public int getStartUpCosts() {
		return startUpCosts;
	}

	/** Only applicable for storage technologies */
	public float getStorageVolume() {
		return storageVolume;
	}

	public float getTotalEmissionCost() {
		return totalEmissionCosts;
	}

	public float getTotalFixedCosts() {
		return totalFixedCosts;
	}

	public float getTotalFuelCosts() {
		return totalFuelCosts;
	}

	public float getTotalInvestment() {
		return totalInvestment;
	}

	public float getTotalPowerSales() {
		return totalPowerSales;
	}

	public float getTotalStartUpCosts() {
		return totalStartUpCosts;
	}

	public float getTotalVariableOMCosts() {
		return totalVariableOMCosts;
	}

	public int getUtilisationHours() {
		return utilisationHours;
	}

	/** Only applicable for storage technologies */
	public Map<Integer, List<Float>> getYearlyOperation() {
		return yearlyOperation;
	}

	public float[] getYearlyStartUpCosts() {
		return yearlyStartUpCosts;
	}

	public void initialize(MarketArea marketArea, int year) {
		this.marketArea = marketArea;
		State.setStatesStrategicInitial(this, StateStrategic.NEWBUILD_OPPORTUNITY);
		Type.determinePowerPlantCategory(this);
		// Efficiency slightly adjusted to avoid having two new built power
		// plants with the exact same efficiency
		efficiency = efficiency
				* (1.0f + ((new Random(Settings.getRandomNumberSeed()).nextFloat() * 0.001f)
						- (0.001f / 2)));

		// Markets
		activeMarkets = new ArrayList<>();
		activeMarkets.add(RevenueType.ELECTRICTY_DAY_AHEAD);
	}

	public boolean isBuiltNow() {
		return isBuiltNow;
	}

	public boolean isSingularOption() {
		return singularOption;
	}

	public boolean isStorage() {
		return storage;
	}

	/** Reset cash flows in all years */
	public void resetCashFlow() {
		cashFlow.clear();
	}

	public void resetFields() {
		resetCashFlow();
		setYearlyUtilisationHours();
		setNetPresentValue(0f);
		setAnnuity(0f);
	}

	public void setBuiltNow(boolean isBuiltNow) {
		this.isBuiltNow = isBuiltNow;
	}

	public void setCashFlow(int index, float cashFlow) {
		this.cashFlow.put(index, cashFlow);
	}

	public void setEmissionCosts(float emissionCosts) {
		this.emissionCosts = emissionCosts;
	}

	public void setFuelCosts(float fuelCosts) {
		this.fuelCosts = fuelCosts;
	}

	public void setInvestmentAdd(float investmentAdd) {
		this.investmentAdd = investmentAdd;
	}

	@Override
	public void setNetPresentValue(float netPresentValue) {
		this.netPresentValue = netPresentValue;
	}

	public void setSingularOption(boolean singularOption) {
		this.singularOption = singularOption;
	}

	public void setStartUpCosts(int startUpCosts) {
		this.startUpCosts = startUpCosts;
	}

	public void setStorage(boolean storage) {
		this.storage = storage;
	}

	/** Only applicable for storage technologies */
	public void setStorageVolume(float storageVolume) {
		this.storageVolume = storageVolume;
	}

	public void setTotalEmissionCost(float totalEmissionCosts) {
		this.totalEmissionCosts = totalEmissionCosts;
	}

	public void setTotalFixedCosts(float totalFixedCosts) {
		this.totalFixedCosts = totalFixedCosts;
	}

	public void setTotalFuelCosts(float totalFuelCosts) {
		this.totalFuelCosts = totalFuelCosts;
	}

	public void setTotalInvestment(float totalInvestment) {
		this.totalInvestment = totalInvestment;
	}

	public void setTotalPowerSales(float totalPowerSales) {
		this.totalPowerSales = totalPowerSales;
	}

	public void setTotalStartUpCosts(float totalStartUpCosts) {
		this.totalStartUpCosts = totalStartUpCosts;
	}

	public void setTotalVariableOMCosts(float totalVariableOMCosts) {
		this.totalVariableOMCosts = totalVariableOMCosts;
	}

	public void setUtilisationHours(int utilisationHours) {
		this.utilisationHours = utilisationHours;
	}

	/** Only applicable for storage technologies */
	public void setYearlyOperation(int year, List<Float> operation) {
		yearlyOperation.put(year, operation);
	}

	public void setYearlyStartUpCosts(int year, float yearlyStartUpCosts) {
		this.yearlyStartUpCosts[year] = yearlyStartUpCosts;
	}

	@Override
	public String toString() {
		return getName() + ": " + (Math.round(getNetPresentValue() * 100) / 100f) + " EURmn/MW";
	}

	public float getAnnuity() {
		return annuity;
	}

	public void setAnnuity(float annuity) {
		this.annuity = annuity;
	}

}