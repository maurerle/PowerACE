package markets.bids.power;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.BlockBidI;
import markets.trader.TraderType;
import simulations.MarketArea;
import simulations.PowerMarkets;
import supply.powerplant.Plant;
import tools.types.FuelName;
import tools.types.FuelType;

public class BlockBidPower extends PowerBid implements BlockBidI, Comparable<BlockBidPower> {

	/** Builder class for easier building of a block bid. */
	public static class Builder {

		private final BidType bidType;
		private String comment;
		private float emissionCosts;
		private float fuelCosts;
		private FuelType fuelType;
		private final int length;
		private float operAndMainCosts;
		private Plant plant;
		private final float price;
		private FuelName renewableType;
		private final int startHour;
		private float startupCosts;
		private TraderType traderType;
		private final float volume;
		private MarketArea marketArea;
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
		public Builder(float volume, float price, int startHour, int length, BidType bidType,
				MarketArea marketArea) {
			this.volume = checkVolume(volume);
			this.price = price;
			this.startHour = checkStart(startHour);
			this.length = checkLength(length);
			this.bidType = bidType;
			this.marketArea = marketArea;
		}

		public BlockBidPower build() {
			return new BlockBidPower(this);
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
		public Builder fuelType(FuelType fuelType) {
			this.fuelType = fuelType;
			return this;
		}

		/**
		 * @param O
		 *            &M costs [Euro/MWh]
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
		 * @param startupCosts
		 *            Startup costs [Euro/MWh] Can be negative!
		 * @return
		 */
		public Builder startupCosts(float startupCosts) {
			this.startupCosts = startupCosts;
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

		private int checkLength(int length) {
			if (length > 1) {
				return length;
			} else {
				logger.warn("Wrong length for block bid. ");
				return 0;
			}
		}

		private int checkStart(int startHour) {
			if ((0 <= startHour) && (startHour <= 22)) {
				return startHour;
			} else {
				logger.warn("Wrong start hour for block bid. ");
				return -1;
			}
		}

		private float checkVolume(float volume) {
			if (0 < volume) {
				return volume;
			} else {
				logger.warn("Wrong volume for block bid. Only positive values are allowed. ");
				return 0;
			}
		}

	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(BlockBidPower.class.getName());
	/** Bid start hour i.e. range from [1,23] */
	private final int endHour;
	/** Bid length [h] */
	private final int length;
	/** Bid start hour i.e. range from [0,22] */
	private final int startHour;

	/**
	 * Private constructor for builder.
	 *
	 * @param builder
	 */
	private BlockBidPower(Builder builder) {
		traderType = builder.traderType;
		bidType = builder.bidType;
		comment = builder.comment;
		emissionCosts = builder.emissionCosts;
		fuelCosts = builder.fuelCosts;
		fuelType = builder.fuelType;
		startHour = builder.startHour;
		length = builder.length;
		endHour = (startHour + length) - 1;
		operAndMainCosts = builder.operAndMainCosts;
		plant = builder.plant;
		price = builder.price;
		renewableType = builder.renewableType;
		startupCosts = builder.startupCosts;
		volume = builder.volume;
		marketArea = builder.marketArea;
	}

	@Override
	public int compareTo(BlockBidPower bidPoint) {
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

	/** The last hour of the bid, which should between [1,23] */
	@Override
	public int getEnd() {
		return endHour;
	}

	/** The length of the bid in hours should be between [2,24] */
	@Override
	public int getLength() {
		return length;
	}

	/** The start hour of the bid, which should between [0,22] */
	@Override
	public int getStart() {
		return startHour;
	}

	@Override
	public float getVolumeAccepted() {
		return isAccepted() ? volume : 0f;
	}

	@Override
	public float getVolumeRemaining() {
		return isAccepted() ? 0f : volume;
	}

	@Override
	public boolean isValid(float pMin, float pMax) {
		return (pMin <= price) && (price <= pMax) && (length >= 2) && (startHour <= 22)
				&& (startHour >= 0) && (volume != 0);
	}

	@Override
	public void setVolumeAccepted(float volume) {
		if ((Math.abs(this.volume - volume) > 0.0001) && (volume != 0)) {
			logger.error("Only total volume can be accepted.");
		}
		super.setVolumeAccepted(volume);
	}

	@Override
	public String toString() {
		return "(price: " + PowerMarkets.getDecimalFormat().format(price) + ", volume "
				+ PowerMarkets.getDecimalFormat().format(volume) + ", startHour " + startHour
				+ ", length " + length + ")";
	}

}