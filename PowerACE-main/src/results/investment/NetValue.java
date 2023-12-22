package results.investment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.invest.Investor;
import supply.powerplant.PlantOption;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.Unit;

/** Logs information and decisions of the InvestmentPlanner agents */
public class NetValue extends AbstractInvestmentLogger {
	private static final Logger logger = LoggerFactory.getLogger(NetValue.class.getName());
	private int logIDCapacityCertificateInvestorNetValueStats = -1;

	/** ID for logging the profitability of capacity options */
	private int logIDCapOpt = -1;

	/** ID for logging price forecast */
	private final Map<Investor, Integer> logIDPriceForecastXLSX = new HashMap<>();

	public NetValue(MarketArea marketArea) {
		super(marketArea);
	}

	@Override
	public void closeLogFiles() {
		try {

			// Files written end of simulation
			if (Date.isLastDay()) {
				for (final Investor investor : logIDPriceForecastXLSX.keySet()) {
					LoggerXLSX.close(logIDPriceForecastXLSX.get(investor));
				}

				// Check whether log file has been initialized after all
				if (logIDCapOpt != -1) {
					LoggerXLSX.close(logIDCapOpt);
				}

			}
			if (marketArea.getInvestmentLogger().getLogIDInvestmentPlannerGap() != -1) {
				LoggerXLSX.closeYearly(logIDGap);
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	public int getLogIDCapacityCertificateInvestorNetValueStats() {
		return logIDCapacityCertificateInvestorNetValueStats;
	}

	/** {@link NetValue#logIDCapOpt} */
	public int getLogIDInvestmentPlannerCapOpt() {
		return logIDCapOpt;
	}

	@Override
	public void initialize() {
	}

	/** Initialzes the logging of capacity options */
	public void logCapOptInitialize() {
		if (logIDCapOpt != -1) {
			return;
		}
		final String fileName = marketArea.getInitialsUnderscore() + "InvestmentPlannerCapOptStats";
		final String description = "PowerPlant CapacityOption";
		final ArrayList<ColumnHeader> titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("year", Unit.YEAR));
		titleLine.add(new ColumnHeader("BidderID", Unit.NONE));
		titleLine.add(new ColumnHeader("BidderName", Unit.NONE));
		titleLine.add(new ColumnHeader("Name", Unit.NONE));
		titleLine.add(new ColumnHeader("NetCapacity", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("Efficiency", Unit.NONE));
		titleLine.add(new ColumnHeader("EmissionFactor", Unit.EMISSION_FACTOR));
		titleLine.add(new ColumnHeader("YearOfAvailability", Unit.YEAR));
		titleLine.add(new ColumnHeader("InvestmentPayments", Unit.CAPACITY_INVESTMENT));
		titleLine.add(new ColumnHeader("EconomicLifetime", Unit.YEAR));
		titleLine.add(new ColumnHeader("FixedCosts", Unit.CAPACITY_INVESTMENT));
		titleLine.add(new ColumnHeader("VariableCosts", Unit.ENERGY_PRICE));
		titleLine.add(new ColumnHeader("NPV", Unit.CAPACITY_INVESTMENT));
		titleLine.add(new ColumnHeader("Income_electricty_sold", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("FuelCosts", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("EmissionCosts", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("VariableCosts", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("InvestmentAdd", Unit.CAPACITY_PRICE));
		titleLine.add(new ColumnHeader("CapacityPayments", Unit.CAPACITY_PRICE));

		for (int i = 0; i < Settings.getInvestmentHorizonMax(); i++) {
			titleLine.add(new ColumnHeader("CashFlow" + i, Unit.CAPACITY_PRICE));
		}
		for (int i = 0; i < Settings.getInvestmentHorizonMax(); i++) {
			titleLine.add(new ColumnHeader("UtilHours" + i, Unit.HOUR));
		}
		titleLine.add(new ColumnHeader("Fuel", Unit.NONE));
		titleLine.add(new ColumnHeader("UtilisationHours", Unit.HOUR));
		titleLine.add(new ColumnHeader("ConstructionTime", Unit.YEAR));
		titleLine.add(new ColumnHeader("Technology", Unit.NONE));
		logIDCapOpt = LoggerXLSX.newLogObject(Folder.INVESTMENT, fileName, description, titleLine,
				marketArea.getIdentityAndNameLong(), Frequency.SIMULATION);
	}

	public void logInitializeStatsInvestorNetValue() {
		// Initialize log file PriceForeCast
		final String fileName = marketArea.getInitialsUnderscore() + "StatsInvestorNetValue"
				+ Date.getYear();
		final String description = "Describes the capacityprice forecast of the CapacityCertificateMarket.";

		final List<ColumnHeader> columns = new ArrayList<>();
		columns.add(new ColumnHeader("Bidder", Unit.NONE));
		columns.add(new ColumnHeader("BidderName", Unit.NONE));
		columns.add(new ColumnHeader("year", Unit.YEAR));
		columns.add(new ColumnHeader("TechnologyName", Unit.NONE));
		columns.add(new ColumnHeader("NPV before Capamarket", Unit.CAPACITY_PRICE));

		final List<PlantOption> capacityOption = marketArea.getGenerationData()
				.getCopyOfCapacityOptions(Date.getYear());
		float maxOperatingLifetime = 0;
		for (final PlantOption option : capacityOption) {
			if (maxOperatingLifetime < option.getOperatingLifetime()) {
				maxOperatingLifetime = option.getOperatingLifetime();
			}
		}
		maxOperatingLifetime /= 2;
		columns.add(new ColumnHeader("Calculated Difference Price", Unit.CAPACITY_PRICE));
		columns.add(new ColumnHeader("Calculated certificate revenues new Project",
				Unit.CAPACITY_PRICE));
		columns.add(new ColumnHeader("new Project Revenues", Unit.CAPACITY_PRICE));
		for (int index = 1; index <= (maxOperatingLifetime); index++) {
			columns.add(new ColumnHeader("Calculated yearly difference Price " + index,
					Unit.CAPACITY_PRICE));
			columns.add(new ColumnHeader("Yearly revenues " + index, Unit.CAPACITY_PRICE));
		}
		columns.add(new ColumnHeader("Revenues sum new project time ", Unit.CAPACITY_PRICE));
		columns.add(new ColumnHeader("Revenues sum remaining time ", Unit.CAPACITY_PRICE));
		columns.add(new ColumnHeader("NPV after Capamarket", Unit.CAPACITY_PRICE));

		logIDCapacityCertificateInvestorNetValueStats = LoggerXLSX.newLogObject(Folder.CAPACITY,
				fileName, description, columns, marketArea.getIdentityAndNameLong(),
				Frequency.YEARLY);
	}
}
