package markets.trader.future.tools;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import markets.operator.spot.tools.ExchangeForecastMarketCoupling;
import markets.trader.spot.supply.tools.ForecastTypeDayAhead;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.Investor;

/**
 * Estimation of hourly electricity exchange from market coupling using multiple
 * linear regression. Can be used for the long-term price forecast, however not
 * accounting for future interconnector capacities!
 * <p>
 * For the respective day-ahead forecast see
 * {@link ExchangeForecastMarketCoupling}.
 * 
 * @author CF
 */
public class ExchangeForecastFuture {
	private Map<MarketArea, Map<Integer, Map<Integer, Float>>> hourlyExchangeForecastFuture;
	final PowerMarkets model;

	public ExchangeForecastFuture(PowerMarkets model) {
		this.model = model;
		for (final MarketArea marketArea : model.getMarketAreas()) {
			marketArea.setExchangeForecastFuture(this);
		}
	}

	public Map<MarketArea, Map<Integer, Map<Integer, Float>>> getHourlyExchangeForecastFuture() {
		return hourlyExchangeForecastFuture;
	}

	private void initialize() {
		hourlyExchangeForecastFuture = new HashMap<>();
		for (final MarketArea marketArea : model.getMarketAreas()) {
			hourlyExchangeForecastFuture.put(marketArea, new HashMap<>());
		}
	}

	private void updateExchangeForecastFuture(MarketArea marketArea, int year) {
		hourlyExchangeForecastFuture.get(marketArea).put(year, new LinkedHashMap<>());
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			hourlyExchangeForecastFuture.get(marketArea).get(year).put(hourOfYear, 0f);
		}
		for (final MarketArea marketAreaInterconnected : marketArea.getMarketCouplingOperator()
				.getCapacitiesData().getMarketAreasInterconnected(marketArea)) {
			if ((marketArea.getMarketCouplingOperator().getExchangeForecastMarketCoupling()
					.get(marketArea) != null)
					&& (marketArea.getMarketCouplingOperator().getExchangeForecastMarketCoupling()
							.get(marketArea).get(marketAreaInterconnected) != null)) {
				final List<Float> tempExchange = marketArea.getMarketCouplingOperator()
						.getExchangeForecastMarketCoupling(marketArea, marketAreaInterconnected)
						.getExchangeForecast(Date.HOURS_PER_YEAR, year);
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
					hourlyExchangeForecastFuture.get(marketArea).get(year).put(hourOfYear,
							hourlyExchangeForecastFuture.get(marketArea).get(year).get(hourOfYear)
									+ tempExchange.get(hourOfYear));
				}
			} else {
				final List<Float> tempExchange = marketArea.getMarketCouplingOperator()
						.getExchangeForecastMarketCoupling(marketAreaInterconnected, marketArea)
						.getExchangeForecast(Date.HOURS_PER_YEAR, year);
				for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
					hourlyExchangeForecastFuture.get(marketArea).get(year).put(hourOfYear,
							hourlyExchangeForecastFuture.get(marketArea).get(year).get(hourOfYear)
									- tempExchange.get(hourOfYear));
				}
			}
		}
	}

	public void updateExchangeForecastFutureForAllMarketAreas() {
		// Day-ahead price forecast via optimization requires exchange forecast
		// for current year
		if (Settings.getDayAheadPriceForecastType() == ForecastTypeDayAhead.OPTIMIZATION) {
			return;
		}
		initialize();
		for (int yearOffset = Investor.getYearsLongTermPriceForecastStart(); yearOffset <= (Investor
				.getYearsLongTermPriceForecastEnd() + 1); yearOffset++) {
			final int year;
			if (yearOffset == (Investor.getYearsLongTermPriceForecastEnd() + 1)) {
				year = Date.getYear() + 10;
			} else {
				year = Date.getYear() + yearOffset;
			}
			for (final MarketArea marketArea : model.getMarketAreas()) {
				updateExchangeForecastFuture(marketArea, year);
			}
		}

		// Day-ahead price forecast via optimization requires exchange forecast
		// for current year
		if (Settings.getDayAheadPriceForecastType() == ForecastTypeDayAhead.OPTIMIZATION) {
			for (MarketArea marketArea : model.getMarketAreas()) {
				updateExchangeForecastFuture(marketArea, Date.getYear());
			}
		}
	}
}