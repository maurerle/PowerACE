package tools.logging;

public enum Folder {

	CAPACITY(
			"Capacity"),
	COGENERATION(
			"Cogeneration"),
	CONSUMER(
			"Consumer"),
	DAY_AHEAD_PRICES(
			"DayAhead"),
	DEMAND(
			"Demand"),
	DEMAND_SIDE_MANAGEMENT(
			"DemandSideManagement"),
	EMISSIONS(
			"Emissions"),
	GENERATOR(
			"Generator"),
	HYDROGEN(
			"Hydrogen"),
	HYDROPOWER(
			"HydroPower"),
	INVESTMENT(
			"Investment"),
	MAIN(
			""),
	MARKET_COUPLING(
			"MarketCoupling"),
	RENEWABLE(
			"Renewable"),
	SUPPLIER(
			"Supplier"),
	SUPPLY(
			"Supply");

	private String name;

	private Folder(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}

}