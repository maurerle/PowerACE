package markets.trader.future.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.HourlyBidI;
import markets.bids.power.PowerBid;
import markets.trader.Trader;
import markets.trader.TraderType;
import simulations.PowerMarkets;
import simulations.scheduling.Date;
import supply.powerplant.Plant;
import tools.types.FuelName;
import tools.types.FuelType;

/**
 * Class for a simple bid containing bid price, bid volume, and resulting price
 * and volume.
 *
 * @since 03.07.2005
 * @author Anke Weidlich et al.
 */
public class HourlyBidPowerForecast extends PowerBid
		implements
			Comparable<HourlyBidPowerForecast>,
			HourlyBidI {

	/** Builder class for easier building of a BidPoint. */
	public static class Builder {

		private final BidType bidType;
		private String comment;
		private float emissionCosts;
		private float fuelCosts;
		private FuelName fuelName;
		private FuelType fuelType;
		private final int hour;
		private int identifier;
		private float markup;
		private float operAndMainCosts;
		private Plant plant;
		private final float price;
		private FuelName renewableType;
		private float runningHoursExpected;
		private float startupCosts;
		private Trader trader;
		private TraderType traderType;
		private final float volume;

		/**
		 * Constructs a new block bid with the following Parameters
		 *
		 * @param volume
		 *            Bid volume [MWh]. All volumes are positive.
		 * @param price
		 *            Bid price [Euro/MWh]
		 * @param hour
		 *            The hour in which the bid starts, i.e. range from [0,22]
		 * @param bidType
		 *            Bid type either <code>ASK</code> or <code>SELL</code>
		 */
		public Builder(float volume, float price, int hour, BidType bidType) {
			this.volume = checkVolume(volume);
			this.price = price;
			this.hour = hour;
			this.bidType = bidType;
		}

		/**
		 * Constructs a new block bid with the following Parameters
		 *
		 * @param price
		 *            Bid price [Euro/MWh]
		 * @param volume
		 *            Bid volume [MWh]. All volumes are positive.
		 * @param startHour
		 *            The hour in which the bid starts, i.e. range from [0,22]
		 * @param length
		 *            Bid length [h]
		 * @param bidType
		 *            Bid type either <code>ASK</code> or <code>SELL</code>
		 */
		public Builder(HourlyBidPowerForecast bidPoint) {
			volume = checkVolume(bidPoint.volume);
			price = bidPoint.price;
			hour = bidPoint.hour;
			bidType = bidPoint.bidType;
		}

		public HourlyBidPowerForecast build() {
			return new HourlyBidPowerForecast(this);
		}

		/**
		 * @param comment
		 *            Any comment
		 * @return
		 */
		public Builder comment(String comment) {
			this.comment = comment;
			return this;
		}

		/**
		 * @param emissionCosts
		 *            Emission costs [Euro/MWh]
		 * @return
		 */
		public Builder emissionCosts(float emissionCosts) {
			this.emissionCosts = checkCosts(emissionCosts);
			return this;
		}

		/**
		 * @param fuelCosts
		 *            Fuel costs [Euro/MWh]
		 * @return
		 */
		public Builder fuelCosts(float fuelCosts) {
			this.fuelCosts = checkCosts(fuelCosts);
			return this;
		}

		/**
		 * @param fuelType
		 *            The fuel type of the plant that makes the bid.
		 * @return
		 */
		public Builder fuelName(FuelName fuelName) {
			this.fuelName = fuelName;
			return this;
		}

		/**
		 * @param fuelType
		 *            The fuel type of the plant that makes the bid.
		 * @return
		 */
		public Builder fuelType(FuelType fuelType) {
			this.fuelType = fuelType;
			return this;
		}

		/**
		 * @param startupCosts
		 *            Startup costs [Euro/MWh] Can be negative!
		 * @return
		 */
		public Builder identifier(int identifier) {
			this.identifier = identifier;
			return this;
		}

		/**
		 * @param markup
		 *            The markup added to the costs of the bid. [Euro/MWh]
		 * @return
		 */
		public Builder markup(float markup) {
			this.markup = markup;
			return this;
		}

		/**
		 * @param operAndMainCosts
		 *            O&M costs [Euro/MWh]
		 * @return
		 */
		public Builder operAndMainCosts(float operAndMainCosts) {
			this.operAndMainCosts = checkCosts(operAndMainCosts);
			return this;
		}

		/**
		 * @param plant
		 *            The plant that made the bid.
		 * @return
		 */
		public Builder plant(Plant plant) {
			this.plant = plant;
			return this;
		}

		/**
		 * @param fuelCosts
		 *            Fuel costs [Euro/MWh]
		 * @return
		 */
		public Builder renewableType(FuelName renewableType) {
			this.renewableType = renewableType;
			return this;
		}

		/**
		 * @param fuelCosts
		 *            Fuel costs [Euro/MWh]
		 * @return
		 */
		public Builder runningHoursExpected(float runningHoursExpected) {
			this.runningHoursExpected = runningHoursExpected;
			return this;
		}

		/**
		 * @param startupCosts
		 *            Startup costs [Euro/MWh] Can be negative!
		 * @return
		 */
		public Builder startupCosts(float startupCosts) {
			this.startupCosts = startupCosts;
			return this;
		}

		/**
		 * @param emissionCosts
		 *            Emission costs [Euro/MWh]
		 * @return
		 */
		public Builder trader(Trader trader) {
			this.trader = trader;
			return this;
		}

		/**
		 * @param traderType
		 *            The bidder that makes the bid
		 * @return
		 */
		public Builder traderType(TraderType traderType) {
			this.traderType = traderType;
			return this;
		}

		private float checkCosts(float costs) {
			if (costs >= 0) {
				return costs;
			} else {
				logger.error("Costs cannot be negative.");
				return 0;
			}
		}

		private float checkVolume(float volume) {
			if (0 < volume) {
				return volume;
			} else if (volume == 0) {
				logger.warn("Wrong volume for hourly bid. The volume is zero.");
				return 0;
			} else {
				logger.warn("Wrong volume for hourly bid. Only positive values are allowed. ");
				return 0;
			}
		}
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(HourlyBidPowerForecast.class.getName());

	/** Bid volume; negative value: sell bid, positive value: buy bid */
	private int hour;

	/**
	 * @param price
	 *            Bid price [Euro/MWh]
	 * @param volume
	 *            Bid volume; negative value: sell bid, positive value: buy bid
	 * @param identifier
	 *            Type of power generation, coal, lignite, exchange
	 * @param type
	 *            Buying or selling bid: -1 = selling bid, 1 = buying bid
	 */
	public HourlyBidPowerForecast(float price, float volume, int identifier, BidType bidType,
			TraderType bidder) {
		this.bidType = bidType;
		this.price = price;
		this.volume = checkVolume(volume);
		this.identifier = identifier;
		traderType = bidder;
	}

	/**
	 * @param price
	 *            Bid price [Euro/MWh]
	 * @param volume
	 *            Bid volume; negative value: sell bid, positive value: buy bid
	 * @param identifier
	 *            Type of power generation, coal, lignite, exchange
	 * @param type
	 *            Buying or selling bid: -1 = selling bid, 1 = buying bid
	 */
	public HourlyBidPowerForecast(float price, float volume, int hour, int identifier,
			BidType bidType, TraderType bidder) {
		this.bidType = bidType;
		this.price = price;
		this.hour = checkHour(hour);
		this.volume = checkVolume(volume);
		this.identifier = identifier;
		traderType = bidder;
	}

	/**
	 * @param price
	 *            Bid price [Euro/MWh]
	 * @param volume
	 *            Bid volume; negative value: sell bid, positive value: buy bid
	 * @param identifier
	 *            Type of power generation, coal, lignite, exchange
	 * @param type
	 *            Buying or selling bid: -1 = selling bid, 1 = buying bid
	 * @param plant
	 *            plant submitting the bid
	 *
	 */
	public HourlyBidPowerForecast(float price, float volume, int hour, int identifier,
			BidType bidType, TraderType bidder, Plant plant) {
		this.bidType = bidType;
		this.price = price;
		this.hour = checkHour(hour);
		this.volume = checkVolume(volume);
		this.identifier = identifier;
		this.plant = plant;
		comment = "Regular bid of the power plant.";
		traderType = bidder;
	}

	public HourlyBidPowerForecast(HourlyBidPowerForecast bidPoint) {
		bidType = bidPoint.bidType;
		price = bidPoint.price;
		volume = bidPoint.volume;
		identifier = bidPoint.identifier;
		traderType = bidPoint.traderType;
	}

	/**
	 * Private constructor for builder.
	 *
	 * @param builder
	 */
	private HourlyBidPowerForecast(Builder builder) {
		traderType = builder.traderType;
		bidType = builder.bidType;
		comment = builder.comment;
		emissionCosts = builder.emissionCosts;
		fuelCosts = builder.fuelCosts;
		fuelType = builder.fuelType;
		fuelName = builder.fuelName;
		hour = builder.hour;
		runningHoursExpected = builder.runningHoursExpected;
		identifier = builder.identifier;
		operAndMainCosts = builder.operAndMainCosts;
		plant = builder.plant;
		price = builder.price;
		startupCosts = builder.startupCosts;
		volume = builder.volume;
		trader = builder.trader;
		renewableType = builder.renewableType;
	}

	/**
	 * Compares this bid to another bid. First criterion is price, second
	 * criterion is volume.
	 *
	 * @return -1 if this bid is lower ranked than the other bid <br>
	 *         0 if this bid is equally ranked than the other bid <br>
	 *         1 if this bid is higher ranked than the other bid
	 * @param bidPoint
	 *            The bid to compare to this bid
	 */
	@Override
	public int compareTo(HourlyBidPowerForecast bidPoint) {

		// First criterion is price
		if (price > bidPoint.price) {
			return 1;
		} else if (price < bidPoint.price) {
			return -1;
		}

		// Second criterion is type, demand bids are considered first
		if ((bidType == BidType.ASK) && (bidPoint.bidType == BidType.SELL)) {
			return -1;
		} else if ((bidType == BidType.SELL) && (bidPoint.bidType == BidType.ASK)) {
			return 1;
		}

		if (Math.signum(volume) < Math.signum(bidPoint.volume)) {
			return 1;
		} else if (Math.signum(volume) > Math.signum(bidPoint.volume)) {
			return -1;
		}

		// Bigger volumes are considered first, although higher volumes are
		// higher ranked
		if (Math.abs(volume) < Math.abs(bidPoint.volume)) {
			return 1;
		} else if (volume == bidPoint.volume) {
			return 0;
		} else {
			return -1;
		}
	}

	@Override
	public int getHour() {
		return hour;
	}

	@Override
	public boolean isValid(float priceMinimum, float priceMaximum) {
		if (price > priceMaximum) {
			logger.error("Bid Price is higher than maximum price. That's not realistic!");
		}
		return (priceMinimum <= price) && (price <= priceMaximum) && (volume != 0);
	}

	@Override
	public String toString() {
		return "(hour:" + hour + ", price: " + PowerMarkets.getDecimalFormat().format(price)
				+ ", volume " + PowerMarkets.getDecimalFormat().format(volume) + ")";
	}

	private int checkHour(int hour) {
		// For BiddingAlgorithm dummy bids for next day are needed
		if ((0 <= hour) && (hour <= Date.HOURS_PER_YEAR)) {
			return hour;
		} else {
			logger.error("Only bids for current day are accepted.");
			return -1;
		}
	}

	private float checkVolume(float volume) {
		if (volume >= 0) {
			return volume;
		} else {
			logger.error("Volume cannot be negative.");
			return 0;
		}
	}

}