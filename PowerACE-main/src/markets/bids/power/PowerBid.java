package markets.bids.power;

import markets.bids.Bid;
import supply.powerplant.Plant;
import tools.types.FuelName;
import tools.types.FuelType;

public abstract class PowerBid extends Bid {

	/** Emission Costs */
	protected float emissionCosts;
	/** Fuel Costs */
	protected float fuelCosts;
	/** Type of power generation, coal, lignite, exchange. */
	protected FuelName fuelName;
	/** Type of power generation, coal, lignite, exchange. */
	protected FuelType fuelType;
	/** Operation/Maintenance Costs */
	protected float operAndMainCosts;
	/** Type of renewable */
	protected FuelName renewableType;
	/** Startup Costs */
	protected float runningHoursExpected;
	/** Startup Costs */
	protected float startupCosts;

	public float getEmissionCosts() {
		return emissionCosts;
	}

	public float getFuelCosts() {
		return fuelCosts;
	}

	public FuelName getFuelName() {
		return fuelName;
	}

	public FuelType getFuelType() {
		return fuelType;
	}

	public float getOperAndMainCosts() {
		return operAndMainCosts;
	}

	@Override
	public Plant getPlant() {
		return plant;
	}

	public FuelName getRenewableType() {
		return renewableType;
	}

	public float getRunningHoursExpected() {
		return runningHoursExpected;
	}

	public float getStartupCosts() {
		return startupCosts;
	}
}