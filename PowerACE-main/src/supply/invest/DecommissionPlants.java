package supply.invest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.trader.future.tools.PriceForecastFuture;
import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.powerplant.Plant;

/**
 * Simple approach to check if plants should be decommissioned.
 * 
 * 
 *
 */
public class DecommissionPlants {

	private static Map<Integer, Float> decommissionsCapacityPerYear = new HashMap<>();
	private static float decommissionsCapacityPerYearMax = 1000;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(DecommissionPlants.class.getName());

	/**
	 * Check if plant should be decommissioned.
	 * 
	 * @param plant
	 */
	public static void checkDecommissionOfPlant(Plant plant, MarketArea marketArea,
			int yearsOfNegativeProfit, int yearsToShutdown) {

		// initialize values
		final int currentYear = Date.getYear();
		if (!decommissionsCapacityPerYear.containsKey(currentYear)) {
			decommissionsCapacityPerYear.put(currentYear, 0f);
		}

		// assume decommission does not happen before a certain year
		// these decomissions are already done
		if (currentYear < marketArea.getYearDecommissionsStart()) {
			return;
		}

		final float decomissionsCapacity = decommissionsCapacityPerYear.get(currentYear);
		boolean capacityLeft = true;
		// First plant can always be decommissioned afterwards check that with
		// plant capacity limit is not surpassed
		if ((decomissionsCapacity > Settings.FLOATING_POINT_TOLERANCE) && ((decomissionsCapacity
				+ plant.getNetCapacity()) > decommissionsCapacityPerYearMax)) {
			capacityLeft = false;
		}

		final boolean oldEnough = plant.hasFewYearsLeft(yearsToShutdown);
		final boolean continuousNegativeProfit = plant
				.isContinuousNegativeProfit(yearsOfNegativeProfit);
		final boolean expectedNegativeProfit = DecommissionPlants.calcExpectedProfit(plant,
				marketArea) < 0 ? true : false;

		final boolean decommissioned = oldEnough && continuousNegativeProfit
				&& expectedNegativeProfit && capacityLeft;

		final boolean decommissionDenied = oldEnough && continuousNegativeProfit
				&& expectedNegativeProfit && !capacityLeft;

		final int yearsOfNegativeProfitForPlant = plant.calculateYearsOfContinuousNegativeProfit();
		final int yearsToRun = plant.getShutDownYear() - Date.getYear();
		final float expectedFutureProfit = DecommissionPlants.calcExpectedProfit(plant, marketArea);

		marketArea.getPlantsDecommissioned().addPlantDecision(Date.getYear(), plant,
				"Plant decomissioned? oldenough run time " + plant.getRunTime()
						+ ", yearsOfNegativeProfit"
						+ plant.calculateYearsOfContinuousNegativeProfit() + ", expectedProfit "
						+ DecommissionPlants.calcExpectedProfit(plant, marketArea),
				decommissioned, capacityLeft, decommissionDenied, expectedFutureProfit,
				yearsOfNegativeProfitForPlant, yearsToRun, decomissionsCapacity);

		if (decommissioned) {
			final int startYear = Date.getYear();
			final int endYear = Math.min(Date.getLastDetailedForecastYear(),
					plant.getShutDownYear());
			for (int year = startYear; year <= endYear; year++) {
				plant.setStateStrategic(year, new State(StateStrategic.DECOMMISSIONED, 0));
			}

			if ((plant.getProfitYearly(Date.getYear()) != null)
					&& (plant.getProfitYearly(Date.getYear()) > 0)) {
				logger.error("This should not happen! Do not shut down, when making profit!");
			}

			marketArea.getPlantsDecommissioned().addDecommissionedPlant(startYear, plant,
					"Plant decomissioned! oldenough run time " + plant.getRunTime()
							+ ", yearsOfNegativeProfit"
							+ plant.calculateYearsOfContinuousNegativeProfit() + ", expectedProfit "
							+ DecommissionPlants.calcExpectedProfit(plant, marketArea));

			decommissionsCapacityPerYear.put(currentYear,
					decommissionsCapacityPerYear.get(currentYear) + plant.getNetCapacity());
			// recalculate value of future prices, if plant is decommissioned

			PriceForecastFuture.recalculate(marketArea.getModel().getMarketAreas());
		}
	}

	/**
	 * Calculate the expected profit.
	 * 
	 * @param plant
	 * @return
	 */
	private static float calcExpectedProfit(Plant plant, MarketArea marketArea) {

		final int shutDownYear = plant.getShutDownYear();
		final int currentYear = Date.getYear();
		int futureYear = ((shutDownYear + currentYear) / 2) + 1;
		// if shutdown in current year
		futureYear = Math.max(shutDownYear, futureYear);

		final Map<Integer, Float> forwardPrice = PriceForecastFuture
				.getForwardPriceListCapped(futureYear, marketArea);

		final List<Float> margin = new ArrayList<>();
		final float variableCosts = plant.getCostsVar(Date.getYear() + futureYear, marketArea);
		for (final int hour : forwardPrice.keySet()) {
			final float marginHourly = forwardPrice.get(hour) - variableCosts;
			margin.add(marginHourly);
		}
		final YearlyProfit yearlyProfit = new YearlyProfit(margin, plant, marketArea);
		yearlyProfit.calcYearlyProfit();

		return yearlyProfit.getTotalProfit();
	}

}