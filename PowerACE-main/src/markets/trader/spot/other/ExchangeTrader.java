package markets.trader.spot.other;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.bids.Bid.BidType;
import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.trader.Trader;
import markets.trader.TraderType;
import markets.trader.spot.DayAheadTrader;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.types.FuelType;

/**
 * Agent bidding the import / export balance
 *
 * @since 31.03.2005
 * @author Massimo Genoese
 *
 */
public class ExchangeTrader extends Trader implements DayAheadTrader {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(ExchangeTrader.class.getName());

	private Float minimumImport = null;
	private Float priceAddSell = null;
	private Float priceMaxAsk = null;
	private Float priceMinSell = null;

	/**
	 * Constructs instance with standardized names.
	 */
	public ExchangeTrader() {
		super(ExchangeTrader.class.getSimpleName());
	}

	@Override
	public List<DayAheadHourlyBid> callForBidsDayAheadHourly() {
		hourlyDayAheadPowerBids.clear();

		try {
			for (int i = 0; i < 24; i++) {
				final DayAheadHourlyBid bid = generateHBid(i);
				if (bid != null) {
					hourlyDayAheadPowerBids.add(bid);
				}
			}
		} catch (final NullPointerException e) {
			logger.error(getName());
			logger.error(e.getMessage());
		}

		return hourlyDayAheadPowerBids;
	}

	@Override
	public void evaluateResultsDayAhead() {
	}

	public Float getMinimumImport() {
		return minimumImport;
	}

	public Float getPriceAddSell() {
		return priceAddSell;
	}

	public Float getPriceMaxAsk() {
		return priceMaxAsk;
	}

	public Float getPriceMinSell() {
		return priceMinSell;
	}

	@Override
	public void initialize() {
		logger.info("Initialize " + getName());
	}

	public boolean isMinimumImport() {
		return (minimumImport != null) && (priceAddSell != null);
	}

	private void addMinimumImportBid(int hour, DayAheadHourlyBid bid, final float volumeExchange) {

		if (isMinimumImport()) {

			// If already importing, make sure at least minimum can be imported
			if (volumeExchange < 0) {
				final float diff = minimumImport - Math.abs(volumeExchange);
				// If import at least minimumImport should be possible
				if (diff > 0) {
					bid.addBidPoint(
							new HourlyBidPower.Builder(diff, 250, hour, BidType.SELL, marketArea)
									.fuelType(FuelType.OTHER).traderType(TraderType.EXCHANGE)
									.build());
				}
			}
			// If export make sure that import is possible as well
			else {
				bid.addBidPoint(new HourlyBidPower.Builder(minimumImport, 250, hour, BidType.SELL,
						marketArea).fuelType(FuelType.OTHER).traderType(TraderType.EXCHANGE)
								.build());
			}
		}

	}

	/**
	 *
	 * generates one hourly bid from plant data.
	 *
	 * @return Generated hourly bid.
	 */
	private DayAheadHourlyBid generateHBid(int hour) {

		final DayAheadHourlyBid bid = new DayAheadHourlyBid(hour, TraderType.EXCHANGE);

		// Exchange equals export. Positive bids equal an additional demand.
		// So if the export is positive, also a positive bid has to be made.
		final float volumeExchange = marketArea.getExchange()
				.getHourlyFlow((Date.HOURS_PER_DAY * (Date.getDayOfYear() - 1)) + hour);

		if (volumeExchange == 0) {
			return null;
		}

		BidType bidType;
		// For sell, he accepts any price even the lowest, for buying any price
		// even the highest
		float price;
		if (volumeExchange < 0) {
			bidType = BidType.SELL;
			if (priceMinSell == null) {
				price = getMinimumDayAheadPrice();
			} else {
				price = priceMinSell;
			}
		} else {
			bidType = BidType.ASK;
			if (priceMaxAsk == null) {
				price = getMaximumDayAheadPrice();
			} else {
				price = priceMaxAsk;
			}
		}

		if (!Settings.isMarketClearingPriceSensitive()) {
			price = getMinimumDayAheadPrice();
		}

		// only positive values allowed
		bid.addBidPoint(new HourlyBidPower.Builder(Math.abs(volumeExchange), price, hour, bidType,
				marketArea).fuelType(FuelType.OTHER).traderType(TraderType.EXCHANGE)
						.comment("Static Exchange").build());

		addMinimumImportBid(hour, bid, volumeExchange);

		return bid;
	}

}