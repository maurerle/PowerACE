package supply.powerplant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.carbon.CarbonPrices;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.State;
import supply.invest.StateStrategic;
import supply.powerplant.Plant.RevenueType;
import supply.powerplant.technique.EnergyConversion;
import supply.powerplant.technique.Type;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.MarketAreaType;

/** Super class for all generation units */
public abstract class PlantAbstract {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	protected static final Logger logger = LoggerFactory.getLogger(PlantAbstract.class.getName());

	protected List<RevenueType> activeMarkets;
	/** Date from which on the unit is available [dd/MM/yyyy] */
	protected LocalDate availableDate;

	protected float carbonCapturePercentage;
	/** Power plant category */
	protected Type category;
	/** Combined heat */
	protected boolean chp;
	/** Time period in years for construction of unit */
	private int constructionTime;

	/** Variable costs for CO2 emission certificates [EUR/MWh] */
	protected float costsCarbonVar;
	/**
	 * Amount the fixed costs increase per year after keeping operation after
	 * technical lifetime.
	 */
	private final float costsFixedYearlyIncrease = 1f;
	/** Variable costs for fuel [EUR/MWh] */
	protected float costsFuelVar;
	/** Yearly operational fixed costs [EUR/MW] */
	protected float costsOperationMaintenanceFixed;
	/** Variable O&M costs [EUR/MWh_el] */
	protected float costsOperationMaintenanceVar;
	/** Current variable costs [EUR/MWh_el] */
	protected float costsVar;
	protected final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	/** Electric net efficiency [0.XX] */
	protected float efficiency;
	/** Type of energy conversion technology */
	protected EnergyConversion energyConversion;
	private int energyConversionIndex;
	/** Fuel name */
	protected FuelName fuelName;
	/**
	 * Electric gross capacity (before auxillary consumption) [MW] for CO2
	 * Calculation
	 */
	private float grossCapacity;
	/** Capital expenditures for construction of unit [EUR/kW] */
	private float investmentPayment;
	/** Minimum Production from database */
	protected Float minimumProduction = null;
	/** Mustrun */
	protected boolean mustrun;
	/** Mustrun capacity [MW] */
	protected float mustrunCapacity;
	/** Combined heat mustrun */
	protected boolean mustrunChp;
	/** Name (location or technology) */
	protected String name;
	/** Electric net name plate capacity [MW] */
	protected float netCapacity;
	/** The expected value when investment decision was made. [EUR/kW] */
	private float netPresentValue = 0;
	/**
	 * Needed for start-up time, better use dynamic values in future,
	 * Simplification!
	 */
	private final LocalDate oldPlant = LocalDate.of(1990, 01, 01);
	/** Technical/operating lifetime (exogenously given) [years] */
	private int operatingLifetime;
	private String ownerName;

	private float powerProfit;
	protected float profitTotal = 0;
	/** Date from which on unit is no longer available [dd/MM/yyyy] */
	protected LocalDate shutDownDate;
	/** Strategic state */
	protected Map<Integer, State> state = new HashMap<>();

	/**
	 * Unique ID of unit (as specified in corresponding database table / should
	 * be a primary key in the database table)
	 */
	protected int unitID;
	/** Full load hours [h] */
	private float utilisation;
	private final int[] yearlyUtilisationHours = new int[150];

	/** Zip code of plant location */
	private Integer zipCode;

	/**
	 * Calculate the variable costs for this unit.
	 */
	public void determineCostsVar(Integer year, MarketArea marketArea) {

		// Fuel costs
		costsFuelVar = getCostsFuelVar(
				marketArea.getFuelPrices().getPricesYearly(getFuelName(), year));

		// Carbon emission costs
		costsCarbonVar = getCostsCarbonVar(year,
				CarbonPrices.getPricesYearlyAverage(year, marketArea));

		float variableOMCosts = getCostsOperationMaintenanceVar();
		costsVar = costsFuelVar + costsCarbonVar + variableOMCosts;

	}

	/** Returns the date from which on the unit is available. */
	public LocalDate getAvailableDate() {
		return availableDate;
	}

	/** Returns the year from which on the unit is available. */
	public int getAvailableYear() {
		return availableDate.getYear();
	}

	public float getCarbonCapturePercentage() {
		return carbonCapturePercentage;
	}

	public Type getCategory() {
		return category;
	}

	public int getConstructionTime() {
		return constructionTime;
	}

	public float getCostsCarbonVar() {
		return costsCarbonVar;
	}

	public float getCostsCarbonVar(float carbonPrice) {
		return getCostsCarbonVar(Date.getYear(), carbonPrice);
	}

	public float getCostsCarbonVar(int year, float carbonPrice) {
		if (efficiency == 0) {
			return 0f;
		}
		return ((carbonPrice / efficiency)
				* (fuelName.getCarbonEmissionFactor(year) * (1 - carbonCapturePercentage)));
	}

	/**
	 * Amount the fixed costs increase per year after keeping operation after
	 * technical lifetime.
	 */
	public float getCostsFixedYearlyIncrease() {
		return costsFixedYearlyIncrease;
	}

	public float getCostsFuelVar() {
		return costsFuelVar;
	}

	public float getCostsFuelVar(float fuelPrice) {
		if (efficiency == 0) {
			return 0f;
		}
		return fuelPrice / efficiency;
	}

	public float getCostsOperationMaintenanceFixed() {
		return getCostsOperationMaintenanceFixed(0);
	}

	public float getCostsOperationMaintenanceFixed(int year) {
		float costsOperationMaintenanceFixed = this.costsOperationMaintenanceFixed;
		if (year > getShutDownYear()) {
			final float increasePercentage = (float) Math.pow(costsFixedYearlyIncrease,
					year - getShutDownYear());
			costsOperationMaintenanceFixed *= increasePercentage;
		}
		return costsOperationMaintenanceFixed;
	}

	/** {@link #costsOperationMaintenanceVar} */
	public float getCostsOperationMaintenanceVar() {
		return costsOperationMaintenanceVar;
	}

	public float getCostsVar() {
		return costsVar;
	}

	/**
	 * Return costs for given year.
	 */
	public float getCostsVar(int year, MarketArea marketArea) {

		/** Fuel costs for relevant year */
		final float costsFuelVar = getCostsFuelVar(
				marketArea.getFuelPrices().getPricesYearly(getFuelName(), year));

		/**
		 * Carbon costs for relevant year including possible carbon capture and
		 * storage
		 */
		final float costsCarbonVar = getCostsCarbonVar(year,
				CarbonPrices.getPricesYearlyAverage(year, marketArea));

		return costsCarbonVar + costsFuelVar + costsOperationMaintenanceVar;

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

	public float getGrossCapacity() {
		return grossCapacity;
	}

	/** Capital expenditures for construction of unit [EUR/kW] */
	public float getInvestmentPayment() {
		return investmentPayment;
	}

	public int getMinDownTime() {

		// Example values:
		final Type category = getCategory();

		if ((category == Type.COAL_SUB) || (category == Type.COAL_SUPER)
				|| (category == Type.COAL_ULTRASUPER)) {
			return 8;
		} else if ((category == Type.GAS_CC_OLD) || (category == Type.GAS_CC_NEW)) {
			return 2;
		} else if ((category == Type.GAS_COMB_OLD) || (category == Type.GAS_COMB_NEW)) {
			return 0;
		} else if (category == Type.GAS_STEAM) {
			return 2;
		} else if ((category == Type.LIG_OLD) || (category == Type.LIG_NEW)) {
			return 8;
		} else if ((category == Type.NUC_GEN_1) || (category == Type.NUC_GEN_2)
				|| (category == Type.NUC_GEN_3) || (category == Type.NUC_GEN_4)) {
			return 8;
		} else if (category == Type.OIL_CC) {
			return 2;
		} else if (category == Type.OIL_COMB) {
			return 0;
		} else if (category == Type.OIL_STEAM) {
			return 2;
		} else {
			return 8;
		}
	}

	public Float getMinProduction() {
		return minimumProduction;
	}

	public int getMinRunTime() {

		// Example values:
		final Type category = getCategory();

		if ((category == Type.COAL_SUB) || (category == Type.COAL_SUPER)
				|| (category == Type.COAL_ULTRASUPER)) {
			return 4;
		} else if ((category == Type.GAS_CC_OLD) || (category == Type.GAS_CC_NEW)) {
			return 4;
		} else if ((category == Type.GAS_COMB_OLD) || (category == Type.GAS_COMB_NEW)) {
			return 1;
		} else if (category == Type.GAS_STEAM) {
			return 4;
		} else if ((category == Type.LIG_OLD) || (category == Type.LIG_NEW)) {
			return 6;
		} else if ((category == Type.NUC_GEN_1) || (category == Type.NUC_GEN_2)
				|| (category == Type.NUC_GEN_3) || (category == Type.NUC_GEN_4)) {
			return 8;
		} else if (category == Type.OIL_CC) {
			return 4;
		} else if (category == Type.OIL_COMB) {
			return 1;
		} else if (category == Type.OIL_STEAM) {
			return 4;
		} else {
			return 8;
		}
	}

	public String getName() {
		return name;
	}

	public float getNetCapacity() {
		return netCapacity;
	}

	public float getNetPresentValue() {
		return netPresentValue;
	}

	public int getOperatingLifetime() {
		return operatingLifetime;
	}

	public int getEconomicLifetime() {
		return operatingLifetime / 2;
	}

	public String getOwnerName() {
		return ownerName;
	}

	/**
	 * Get the emission factor of this unit
	 *
	 * @param includingEfficiency
	 *            if <code>true</code> includes efficiency
	 * 
	 * @return [tCO2/MWh(el)] or [tCO2/MWh(th)]
	 */
	public float getPlantEmissionFactor(boolean includingEfficiency) {

		float factor = fuelName.getCarbonEmissionFactor();
		if (includingEfficiency) {
			factor /= efficiency;
		}
		return factor;
	}

	public float getPowerProfit() {
		return powerProfit;
	}

	public float getProfit() {
		return profitTotal;
	}

	/** Get remaining lifetime of plant */
	public int getRemainingTechnicalLifetime(int year) {
		final int remainingLifetime = getShutDownYear() - year;
		return remainingLifetime;
	}

	/** Get runtime of plant */
	public int getRunTime() {
		return Date.getYear() - getAvailableYear();
	}

	/**
	 * Returns the date from which on the thermal unit is not available anymore.
	 */
	public LocalDate getShutDownDate() {
		return shutDownDate;
	}

	/** Get last year of operation */
	public int getShutDownYear() {
		return shutDownDate.getYear();
	}

	/**
	 * Start up time cold from Thure/Traber
	 *
	 * @return startUpTime
	 */
	public int getStartUpTimeCold() {

		if (getFuelType() == FuelType.GAS) {
			if ((getEnergyConversion() == EnergyConversion.COMBINED_CYCLE)
					|| (getEnergyConversion() == EnergyConversion.COMBINED_HEAT_POWER)) {
				return 3;
			} else {
				return 0;
			}
		}

		if (getFuelType() == FuelType.COAL) {
			if (availableDate.isBefore(oldPlant)) {
				// old
				return 10;
			} else {
				// new
				return 4;
			}
		}

		if (getFuelType() == FuelType.LIGNITE) {
			if (availableDate.isBefore(oldPlant)) {
				// old
				return 10;
			} else {
				// new
				return 6;
			}
		}

		if (getFuelType() == FuelType.URANIUM) {
			// not known
			return 24;
		}

		if (getFuelType() == FuelType.OIL) {
			return 1;
		}

		return 4;
	}

	public State getState(int year) {
		return state.get(year);
	}

	public StateStrategic getStateStrategic(int year) {
		return state.get(year).getAttributeStateStrategic();
	}

	public int getUnitID() {
		return unitID;
	}

	public float getUtilisation() {
		return utilisation;
	}

	public int[] getYearlyUtilisationHours() {
		return yearlyUtilisationHours;
	}

	public int getYearlyUtilisationHours(int year) {
		if (isAvailableTechnically(year)) {
			return yearlyUtilisationHours[Date.getYearIndex(year)];
		} else {
			return 0;
		}
	}

	public Integer getZipCode() {
		return zipCode;
	}

	/**
	 * remaining life time < minimum remaining life time
	 */
	public boolean hasFewYearsLeft(int minRemainingLifeTime) {
		// Check if plant is shut down in the near future
		if ((getShutDownYear() - Date.getYear()) <= minRemainingLifeTime) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Checks whether plant is available for the specified
	 * <code>cutOffDate</code>.
	 */
	public boolean isAvailable(LocalDate cutOffDate) {
		return (getAvailableDate().isBefore(cutOffDate) || getAvailableDate().equals(cutOffDate))
				&& (getShutDownDate().isAfter(cutOffDate) || getShutDownDate().equals(cutOffDate));
	}

	/**
	 * Checks whether plant is technically available for the specified
	 * <code>year</code>, i.e. has been commissioned and not yet decommissioned.
	 *
	 * @param year
	 * @return Returns <b>true</b> if the plant is available at least one day in
	 *         the specified year. Whether the plant is really available on a
	 *         specific day within the year, is checked before each daily time
	 *         step.
	 */
	public boolean isAvailableTechnically(int year) {
		return (availableDate.getYear() <= year) && (year <= shutDownDate.getYear());
	}

	public boolean isChp() {
		return chp;
	}

	public boolean isMustrun() {
		// its not very likely that must run conditions are used until the end,
		// therefore define an end year
		if (Settings.getMustrunYearEnd() > Date.getYear()) {
			return false;
		}
		return mustrun;
	}

	public boolean isMustrunChp() {
		return mustrunChp;
	}

	/**
	 * Check whether plant is currently in operating state
	 *
	 * @param year
	 * @return <code>True</code> when operating, <code>false</code> if in
	 *         another state
	 */
	public boolean isOperating(int year) {
		return state.get(year).getAttributeStateStrategic() == StateStrategic.OPERATING;
	}

	/**
	 * Check if plant is build from the same technology option.
	 *
	 * @param plant
	 * @return
	 */
	public boolean isSameInvestmentOption(PlantAbstract plant) {
		return (plant.getAvailableYear() == getAvailableYear())
				&& (plant.getEfficiency() == getEfficiency())
				&& (plant.getEnergyConversion() == getEnergyConversion())
				&& (plant.getFuelName() == getFuelName());
	}

	/**
	 * Set the year from which on the unit is available.
	 *
	 * @param availableYear
	 */
	public void setAvailableDate(int availableYear) {
		setAvailableDate(LocalDate.of(availableYear, 1, 1));
	}

	/**
	 * Set the date from which on the unit is available.
	 *
	 * @param availableDate
	 */
	public void setAvailableDate(LocalDate availableDate) {
		this.availableDate = availableDate;
	}

	public void setCategory(Type category) {
		this.category = category;
	}

	public void setChp(boolean chp) {
		this.chp = chp;
	}

	public void setConstructionTime(int constructionTime) {
		this.constructionTime = constructionTime;
	}

	public void setCostsOperationMaintenanceFixed(float costsOperationMaintenanceFixed) {
		this.costsOperationMaintenanceFixed = costsOperationMaintenanceFixed;
		checkCostsOperationMaintenanceFixed();
	}

	public void setCostsOperationMaintenanceVar(float costsOperationMaintenanceVar) {
		this.costsOperationMaintenanceVar = costsOperationMaintenanceVar;
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

	public void setGrossCapacity(float grossCap) {
		grossCapacity = grossCap;
	}

	public void setInvestmentPayment(float investmentPayment) {
		this.investmentPayment = investmentPayment;
	}

	public void setMinProduction(float minProduction) {
		minimumProduction = minProduction;
	}

	public void setMustrun(boolean mustrun) {
		this.mustrun = mustrun;
	}

	public void setMustrunCapacity(float mustrunCapacity) {
		this.mustrunCapacity = mustrunCapacity;
	}

	public void setMustrunChp(boolean mustrunChp) {
		this.mustrunChp = mustrunChp;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setNetCapacity(float netCapacity) {
		this.netCapacity = netCapacity;
	}

	public void setNetPresentValue(float netPresentValue) {
		this.netPresentValue = netPresentValue;
	}

	public void setOperatingLifetime(int operatingLifetime) {
		this.operatingLifetime = operatingLifetime;
	}

	public void setOwnerName(String ownerName) {
		this.ownerName = ownerName;
	}

	public void setPowerProfit(float powerProfit) {
		this.powerProfit = powerProfit;
	}

	public void setProfitBeforeInvestment(float profit) {
		profitTotal = profit;
	}

	/**
	 * Set the date from which on the unit is not available anymore.
	 *
	 * @param ShutDownDate
	 */
	public void setShutDownDate(int shutDownYear) {
		if (shutDownYear == 0) {
			// In case expire year is null
			setShutDownDate(LocalDate.MAX);
		} else {
			setShutDownDate(LocalDate.of(shutDownYear, 12, 31));
		}
	}

	/**
	 * Set the date from which on the unit is not available anymore.
	 *
	 * @param ShutDownDate
	 */
	public void setShutDownDate(LocalDate shutDownDate) {
		this.shutDownDate = shutDownDate;
	}

	/**
	 * Sets the specified state for the given year
	 *
	 * @param year
	 *            year of simulation for which state is set
	 * @param state
	 *            state to set
	 */
	public void setStateStrategic(int year, State state) {
		this.state.put(year, state);
	}

	public void setUnitID(int unitID) {
		this.unitID = unitID;
	}

	public void setUtilisation(float utilisation) {
		this.utilisation = utilisation;
	}

	public void setVarCostsTotal(float costsVar) {
		this.costsVar = costsVar;
	}

	/** Reset full load hours in all years */
	public void setYearlyUtilisationHours() {
		for (int yearIndex = 0; yearIndex < yearlyUtilisationHours.length; yearIndex++) {
			yearlyUtilisationHours[yearIndex] = 0;
		}
	}

	public void setYearlyUtilisationHours(int year, int yearlyUtilisationHours) {
		this.yearlyUtilisationHours[year] = yearlyUtilisationHours;
	}

	public void setZipCode(Integer zipCode, MarketAreaType marketAreaType) {
		this.zipCode = zipCode;
	}

	protected void checkCostsOperationMaintenanceFixed() {
		if ((costsOperationMaintenanceFixed < 100) && (fuelName != FuelName.STORAGE)
				&& (fuelName != FuelName.HYDROGEN)) {
			logger.error(
					this + ": Correct unit? Unit needs to be EUR/MW! Current value seems too low: "
							+ costsOperationMaintenanceFixed + ".");
		}
	}

}