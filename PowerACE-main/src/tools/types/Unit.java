package tools.types;
/**
 * Lists various units used in PowerACE in their basic form
 * 
 */
public enum Unit {

	CAPACITY(
			"MW"),
	CAPACITY_GW(
			"GW"),
	CAPACITY_INVESTMENT(
			"EUR/kW"),
	CAPACITY_PRICE(
			"EUR/MW"),
	CAPACITY_PRICE_MILLION(
			"Mio. EUR/MW"),
	CURRENCY(
			"EUR"),
	CURRENCY_MILLION(
			"Mio. EUR"),
	EMISSION_FACTOR(
			"t(CO2)/MWh(el)"),
	ENERGY_PRICE(
			"EUR/MWh"),
	ENERGY_VOLUME(
			"MWh"),
	ENERGY_VOLUME_GIGAWATTHOUR(
			"GWh"),
	HOUR(
			"h"),
	MILSEC(
			"ms"),
	NONE(
			""),
	PERCENTAGE(
			"%"),
	TONS(
			"t"),
	TONS_CO2(
			"t CO_2"),
	YEAR(
			"Year");

	/** Unit */
	private String unit;

	/** Constructor */
	private Unit(String unit) {
		this.unit = unit;
	}

	public String getUnit() {
		return unit;
	}
}