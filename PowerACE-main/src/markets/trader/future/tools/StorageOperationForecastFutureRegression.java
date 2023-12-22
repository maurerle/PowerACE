package markets.trader.future.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import markets.operator.spot.tools.StorageOperationForecast;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.scheduling.Date;
import supply.invest.Investor;

/**
 * Estimation of hourly storage operation using multiple linear regression. Can
 * be used for the long-term price forecast.
 * <p>
 * For the respective day-ahead forecast see {@link StorageOperationForecast}.
 * 
 * @author CF
 */
public class StorageOperationForecastFutureRegression {
	private Map<MarketArea, Map<Integer, List<Float>>> hourlyStorageOperationForecastFuture;
	final PowerMarkets model;

	public StorageOperationForecastFutureRegression(PowerMarkets model) {
		this.model = model;
		for (final MarketArea marketArea : model.getMarketAreas()) {
			marketArea.setStorageOperationForecastFuture(this);
		}
	}
	public void updateStorageOperationForecastFutureForAllMarketAreas() {
		initialize();
		for (int yearOffset = Investor.getYearsLongTermPriceForecastStart(); yearOffset <= Investor
				.getYearsLongTermPriceForecastEnd(); yearOffset++) {
			final int year = Math.min(Date.getYear() + yearOffset, Date.getLastYear());
			for (final MarketArea marketArea : model.getMarketAreas()) {
				updateStorageOperationForecastFuture(marketArea, year);
			}
		}
	}

	private void initialize() {
		hourlyStorageOperationForecastFuture = new HashMap<>();
		for (final MarketArea marketArea : model.getMarketAreas()) {
			hourlyStorageOperationForecastFuture.put(marketArea, new HashMap<>());
		}
	}

	private void updateStorageOperationForecastFuture(MarketArea marketArea, int year) {
		hourlyStorageOperationForecastFuture.get(marketArea).put(year,
				new ArrayList<>(Date.HOURS_PER_YEAR));
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			hourlyStorageOperationForecastFuture.get(marketArea).get(year).add(0f);
		}
		final List<Float> tempStorageOperation = marketArea.getMarketCouplingOperator()
				.getStorageOperationForecast().get(marketArea)
				.getStorageOperationForecast(Date.HOURS_PER_YEAR, year);
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			hourlyStorageOperationForecastFuture.get(marketArea).get(year).set(hourOfYear,
					hourlyStorageOperationForecastFuture.get(marketArea).get(year).get(hourOfYear)
							+ tempStorageOperation.get(hourOfYear));
		}
	}
}