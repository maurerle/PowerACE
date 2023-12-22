package results.spot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import markets.operator.spot.MarketCouplingOperator;
import markets.operator.spot.tools.MarginalBid;
import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.scheduling.Date;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/**
 * Writes hourly market clearing results of all market areas in log files
 * <p>
 * 
 * @since 03/2013
 * @author PR
 * 
 */
public class PricesMarketArea {

	/** Log ID */
	private int logIDMarketCouplingXLSX;
	/** List of all market areas */
	private final Set<MarketArea> marketAreasAll;
	/** List of all coupled market areas */
	private final Set<MarketArea> marketAreasCoupled;
	/** Market coupling operator of model */
	private final MarketCouplingOperator marketCouplingOperator;

	public PricesMarketArea(PowerMarkets model) {
		marketCouplingOperator = model.getMarketScheduler().getMarketCouplingOperator();
		marketAreasAll = model.getMarketAreas();
		marketAreasCoupled = model.getMarketScheduler().getMarketCouplingOperator()
				.getMarketAreas();
	}

	private void logAvailableCapacity(List<Object> values, int hourOfDay) {
		final int hourOfYear = Date.getHourOfYearFromHourOfDay(hourOfDay);
		final int year = Date.getYear();

		for (final MarketArea fromMarketArea : marketAreasCoupled) {
			for (final MarketArea toMarketArea : marketAreasCoupled) {
				if (!fromMarketArea.equals(toMarketArea)) {
					values.add(marketCouplingOperator.getCapacitiesData()
							.getInterconnectionCapacityHour(fromMarketArea, toMarketArea, year,
									hourOfYear));
				}
			}
		}
	}

	private void logCongestionRevenue(List<Object> values, int hourOfDay) {
		values.add(marketCouplingOperator.getCongestionRevenue()
				.getCongestionRevenueTotalHour(hourOfDay));
	}

	private void logFlows(List<Object> values, int hourOfDay) {
		for (final MarketArea fromMarketArea : marketAreasCoupled) {
			for (final MarketArea toMarketArea : marketAreasCoupled) {
				if (!fromMarketArea.equals(toMarketArea)) {
					values.add(marketCouplingOperator.getExchangeFlows()
							.getHourlyFlow(fromMarketArea, toMarketArea, hourOfDay));
				}
			}
		}
	}

	private void logInitializeAvailableCapacity(List<ColumnHeader> titleLine) {
		for (final MarketArea fromMarketArea : marketAreasCoupled) {
			for (final MarketArea toMarketArea : marketAreasCoupled) {
				if (!fromMarketArea.equals(toMarketArea)) {
					titleLine.add(
							new ColumnHeader("Available_capacity_" + fromMarketArea.getInitials()
									+ "_" + toMarketArea.getInitials(), Unit.CAPACITY));
				}
			}
		}
	}

	private void logInitializeCongestionRevenue(List<ColumnHeader> titleLine) {
		titleLine.add(new ColumnHeader("Congestion revenue", Unit.CURRENCY));
	}

	private void logInitializeFlows(List<ColumnHeader> titleLine) {
		for (final MarketArea fromMarketArea : marketAreasCoupled) {
			for (final MarketArea toMarketArea : marketAreasCoupled) {
				if (!fromMarketArea.equals(toMarketArea)) {
					titleLine.add(new ColumnHeader("Flow_" + fromMarketArea.getInitials() + "_"
							+ toMarketArea.getInitials(), Unit.CAPACITY));
				}
			}
		}
	}

	private void logInitializeMarginalBids(List<ColumnHeader> titleLine) {
		for (final MarketArea marketArea : marketAreasAll) {
			titleLine.add(new ColumnHeader(marketArea.getInitialsUnderscore() + "Marginal_bid",
					Unit.NONE));
			titleLine.add(new ColumnHeader("Price", Unit.ENERGY_PRICE));
			titleLine.add(new ColumnHeader("Bid_Volume", Unit.CAPACITY));
			titleLine.add(new ColumnHeader("Accepted_Volume", Unit.CAPACITY));
			titleLine.add(new ColumnHeader("FuelName", Unit.NONE));
			titleLine.add(new ColumnHeader("StartupInBid", Unit.ENERGY_PRICE));
			titleLine.add(new ColumnHeader("HoursOfStartup", Unit.NONE));
		}
	}

	private void logInitializeMarketClearingPrices(List<ColumnHeader> titleLine) {
		for (final MarketArea marketArea : marketAreasAll) {
			titleLine.add(new ColumnHeader(marketArea.getIdentityAndNameLong(), Unit.ENERGY_PRICE));
		}
	}
	private void logInitializePriceDifferenceAfterMC(List<ColumnHeader> titleLine) {
		for (final MarketArea fromMarketArea : marketAreasCoupled) {
			for (final MarketArea toMarketArea : marketAreasCoupled) {
				if (!fromMarketArea.equals(toMarketArea)) {
					titleLine
							.add(new ColumnHeader("Price_Difference_" + fromMarketArea.getInitials()
									+ "_" + toMarketArea.getInitials(), Unit.ENERGY_PRICE));
				}
			}
		}
	}

	/**
	 * Initializes the file at the beginning of each year by setting the file
	 * name, title line and unit line in the csv file.
	 */
	public void logInitializePrices() {

		final String fileName = "0_Market_Clearing_Prices_" + Date.getYear();

		final Folder folder = Folder.MARKET_COUPLING;

		List<ColumnHeader> titleLine;
		final String description = "Market coupling prices";

		titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("Hour_Of_Day", Unit.NONE));
		titleLine.add(new ColumnHeader("Day", Unit.NONE));
		titleLine.add(new ColumnHeader("Hour", Unit.HOUR));

		// Market clearing prices
		logInitializeMarketClearingPrices(titleLine);

		// Price difference between market areas (after coupling)
		logInitializePriceDifferenceAfterMC(titleLine);

		if (!marketAreasCoupled.isEmpty()) {
			// Flows
			logInitializeFlows(titleLine);

			// Available capacity
			logInitializeAvailableCapacity(titleLine);

			// Congestion revenue
			logInitializeCongestionRevenue(titleLine);
		}

		// Marginal bids
		logInitializeMarginalBids(titleLine);

		logIDMarketCouplingXLSX = LoggerXLSX.newLogObject(folder, fileName, description, titleLine,
				"", Frequency.HOURLY, "#,##0.00");
	}

	private void logMarginalBids(List<Object> values, int hourOfDay) {
		for (final MarketArea marketArea : marketAreasAll) {
			final MarginalBid marginalBid = marketArea.getElectricityResultsDayAhead()
					.getMarginalBidHourOfDay(hourOfDay);
			values.add("");
			if ((marginalBid == null) || Float.isNaN(marginalBid.getPrice())) {
				values.add("");
				values.add("");
				values.add("");
				values.add("");
				values.add("");
				values.add("");
				values.add("");

			} else {
				values.add(marginalBid.getPrice());
				values.add(marginalBid.getBidVolume());
				values.add(marginalBid.getAcceptedVolume());
				values.add(marginalBid.getFuelName());
				values.add(marginalBid.getStartUpinBid());
				values.add(marginalBid.getHoursOfStartUp());
			}
		}
	}

	private void logMarketClearingPrices(List<Object> values, int hourOfDay) {
		for (final MarketArea marketArea : marketAreasAll) {
			values.add(marketArea.getDayAheadMarketOperator().getMCPs()[hourOfDay]);
		}
	}

	/**
	 * Writes the results in the corresponding csv file.
	 */
	public void logMarketPrices() {

		for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
			final List<Object> values = new ArrayList<>();
			// Hour of year, Date and hour
			values.add(Date.getFirstHourOfToday() + hourOfDay);
			values.add(Date.getDayOfYear());
			values.add(hourOfDay + 1);

			// Market clearing prices
			logMarketClearingPrices(values, hourOfDay);

			// Price difference between market areas (after coupling)
			logPriceDifferenceAfterMC(values, hourOfDay);

			if (!marketAreasCoupled.isEmpty()) {

				// Flows
				logFlows(values, hourOfDay);

				// Available capacity
				logAvailableCapacity(values, hourOfDay);

				// Congestion revenue
				logCongestionRevenue(values, hourOfDay);
			}

			// Marginal bids
			logMarginalBids(values, hourOfDay);

			LoggerXLSX.writeLine(logIDMarketCouplingXLSX, values);
		}
	}

	private void logPriceDifferenceAfterMC(List<Object> values, int hourOfDay) {
		for (final MarketArea fromMarketArea : marketAreasCoupled) {
			for (final MarketArea toMarketArea : marketAreasCoupled) {
				if (!fromMarketArea.equals(toMarketArea)) {
					values.add(fromMarketArea.getDayAheadMarketOperator().getMCPs()[hourOfDay]
							- toMarketArea.getDayAheadMarketOperator().getMCPs()[hourOfDay]);
				}
			}
		}
	}

	/** Write log file */
	public void writeFile() {
		LoggerXLSX.close(logIDMarketCouplingXLSX);
	}

}
