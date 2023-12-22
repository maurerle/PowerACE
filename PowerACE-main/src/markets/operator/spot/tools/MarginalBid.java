package markets.operator.spot.tools;

import markets.bids.power.HourlyBidPower;
import simulations.MarketArea;
import supply.powerplant.Plant;
import supply.powerplant.technique.EnergyConversion;
import tools.types.FuelName;

/** Stores relevant information of a marginal bid */
public class MarginalBid {

	private EnergyConversion conversion;

	private float hoursOfStartUp = Float.NaN;
	private float varcosts = Float.NaN;
	private String comment = "";
	private float startUpInBid = Float.NaN;
	private float price = Float.NaN;
	private float volumeAccepted = Float.NaN;
	private float volumeBid = Float.NaN;
	private FuelName fuelName = null;
	private MarketArea marketAreaOfBid = null;

	private HourlyBidPower bidPoint;

	public MarginalBid() {
	}

	public MarginalBid(HourlyBidPower bidPoint) {
		// Get bid information

		final Plant plant = bidPoint.getPlant();
		if (plant != null) {
			conversion = plant.getEnergyConversion();
			varcosts = plant.getCostsVar();
			hoursOfStartUp = (int) plant.getHoursOfStartUp()[bidPoint.getHour()];
			fuelName = plant.getFuelName();
		}
		this.bidPoint = bidPoint;
		startUpInBid = bidPoint.getStartupCosts();
		price = bidPoint.getPrice();
		volumeAccepted = bidPoint.getVolumeAccepted();
		volumeBid = bidPoint.getVolume();
		comment = bidPoint.getComment();

		marketAreaOfBid = bidPoint.getMarketAreaOfBid();
	}

	public float getAcceptedVolume() {
		return volumeAccepted;
	}
	public HourlyBidPower getBidPoint() {
		return bidPoint;
	}

	public float getBidVolume() {
		return volumeBid;
	}

	public String getComment() {
		return comment;
	}

	public EnergyConversion getConversion() {
		return conversion;
	}

	public FuelName getFuelName() {
		return fuelName;
	}

	public float getHoursOfStartUp() {
		return hoursOfStartUp;
	}

	public MarketArea getMarketAreaOfBid() {
		return marketAreaOfBid;
	}

	public float getPrice() {
		return price;
	}

	public float getStartUpinBid() {
		return startUpInBid;
	}

	public float getVarcosts() {
		return varcosts;
	}

	@Override
	public String toString() {
		return getPrice() + ";" + getAcceptedVolume() + ";" + getBidVolume() + ";" + getFuelName();
	}
}