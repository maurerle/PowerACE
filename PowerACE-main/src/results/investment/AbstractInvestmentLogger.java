package results.investment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.Generator;
import supply.invest.Investor;
import supply.powerplant.PlantOption;
import supply.powerplant.technique.EnergyConversion;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.types.FuelName;
import tools.types.FuelType;
import tools.types.Unit;

/** Abstract investment logger */
public abstract class AbstractInvestmentLogger extends Agent {

	/** {@link NetValue#getInvestmentsByYearAndFuel} */
	private final Map<Investor, Map<Integer, List<PlantOption>>> investments = new LinkedHashMap<>();
	protected final Map<Integer, Map<String, Float>> capacityAgentCurrent;
	protected final Map<Integer, Map<String, Float>> capacityAgentExpectedFuture;
	/** factor for overall capacity development (future Cap/actual cap) */
	protected final Map<Integer, Float> capacityFactor;
	/** gap in agents capacity */
	protected final Map<Integer, Map<String, Float>> gapAgentExclCapacityFactor;
	protected final Map<Integer, Map<String, Float>> gapAgentInclCapacityFactor;
	protected final Map<Integer, Float> gapAllAgentsExclCapacityFactor;
	protected final Map<Integer, Float> gapAllAgentsInclCapacityFactor;
	/** ID for logging the capacity gap of the InvestmentPlanner agents */
	protected int logIDGap = -1;
	private int logIDNewCap = -1;

	protected final Map<Integer, Map<String, Float>> profit;

	public AbstractInvestmentLogger(MarketArea marketArea) {
		super(marketArea);
		capacityFactor = new HashMap<>();
		gapAllAgentsExclCapacityFactor = new HashMap<>();
		gapAllAgentsInclCapacityFactor = new HashMap<>();
		gapAgentExclCapacityFactor = new HashMap<>();
		gapAgentInclCapacityFactor = new HashMap<>();
		profit = new HashMap<>();
		capacityAgentCurrent = new HashMap<>();
		capacityAgentExpectedFuture = new HashMap<>();
		for (int year = Date.getStartYear(); year <= Date.getLastYear(); year++) {
			gapAgentExclCapacityFactor.put(year, new HashMap<>());
			gapAgentInclCapacityFactor.put(year, new HashMap<>());
			profit.put(year, new HashMap<>());
			capacityAgentCurrent.put(year, new HashMap<>());
			capacityAgentExpectedFuture.put(year, new HashMap<>());
		}
	}

	/** Log new installation */
	public void addNewInvestment(Investor investmentPlanner, int yearOfCommissioning,
			PlantOption capacityOption, float unitSize) {
		capacityOption = capacityOption.clone();
		capacityOption.setNetCapacity(unitSize);
		investments.get(investmentPlanner).get(yearOfCommissioning).add(capacityOption);
	}

	/**
	 * Get map of new installations by InvestmentPlanner agents
	 * [InvestmentPlanner[Start year (after construction)[CapacityOption]]]
	 */
	public float getInvestmentsByYearAndFuel(int year, FuelType fuelType) {
		float investmentsByYearAndFuel = 0f;
		for (final Investor investmentPlanner : investments.keySet()) {
			final List<PlantOption> capacityOptions = investments.get(investmentPlanner).get(year);
			for (final PlantOption capacityOption : capacityOptions) {
				if (FuelName.getFuelType(capacityOption.getFuelName()).equals(fuelType)) {
					investmentsByYearAndFuel += capacityOption.getNetCapacity();
				}
			}
		}
		return investmentsByYearAndFuel;
	}

	public float getInvestmentsByYearAndFuelAndTechnology(int year, FuelType fuelType,
			EnergyConversion energyConversion) {
		// initialize the total investments for the year and fuel type to 0
		float investmentsByYearAndFuel = 0f;

		// iterate over all investors in the investments map
		for (final Investor investmentPlanner : investments.keySet()) {
			// get the list of plant options for the specific year from the
			// current investor
			final List<PlantOption> capacityOptions = investments.get(investmentPlanner).get(year);

			// iterate over all plant options
			for (final PlantOption capacityOption : capacityOptions) {
				// if the fuel type of the current plant option matches the
				// input fuel type
				if (FuelName.getFuelType(capacityOption.getFuelName()).equals(fuelType)
						&& capacityOption.getEnergyConversion().equals(energyConversion)) {
					// add the net capacity of the current plant option to the
					// total investments
					investmentsByYearAndFuel += capacityOption.getNetCapacity();
				}
			}
		}
		// return the total investments for the year and fuel type
		return investmentsByYearAndFuel;
	}

	/** {@link NetValue#logIDGap} */
	public int getLogIDInvestmentPlannerGap() {
		return logIDGap;
	}

	@Override
	public void initialize() {
	}

	/** Initialize map with investments */
	public void initializeInvestmentsMap() {

		// Total size of map with investment
		int totalMapSize;
		// Maximum construction time of investment options (corresponds to new
		// nuclear plant)
		final int MAX_CONSTRUCTION_TIME = 8;
		// Maximum time lag (currently timelag is 0 because no investments are
		// made for later years
		final int MAX_TIME_LAG = 2;

		// Because investments are saved according to their start year (i.e.
		// after accounting for a potential time lag and construction time), the
		// map has to be initialized for more years
		totalMapSize = Date.getLastYear() + MAX_TIME_LAG + MAX_CONSTRUCTION_TIME;

		for (final Investor investmentPlanner : marketArea.getInvestorsConventionalGeneration()) {
			investments.put(investmentPlanner, new HashMap<Integer, List<PlantOption>>());
			for (int year = Settings.getStartYear(); year < totalMapSize; year++) {
				investments.get(investmentPlanner).put(year, new ArrayList<PlantOption>());
			}
		}
	}

	/** Initialzes the logging of the InvestmentPlanner gaps */
	public void logGapInitialize() {
		final String filename = marketArea.getInitialsUnderscore() + "Gap";
		final String description = "Logging gap";
		final ArrayList<ColumnHeader> titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("Year", Unit.YEAR));
		titleLine.add(new ColumnHeader("BidderID", Unit.NONE));
		titleLine.add(new ColumnHeader("BidderName", Unit.NONE));
		titleLine.add(new ColumnHeader("futuremaxremload", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("initialmaxremload", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("future Max Rem Load incl. Exchange", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("initial Max Rem Load incl. Exchange", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("initialCap", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("futureCap", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("gap", Unit.CAPACITY));
		logIDGap = LoggerXLSX.newLogObject(Folder.INVESTMENT, filename, description, titleLine,
				marketArea.getIdentityAndNameLong(), Frequency.SIMULATION);

	}

	public void logGapsOverview() {

		// Initialization
		final String fileNameGapsAndProfit = marketArea.getInitialsUnderscore() + "Gap_Overview";
		final String description = "Development of total and individual gaps";
		final List<ColumnHeader> titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("Year", Unit.YEAR));
		titleLine.add(new ColumnHeader("total Capacity", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("total Expected Capacity", Unit.CAPACITY));
		titleLine.add(new ColumnHeader("total Gap", Unit.CAPACITY));

		final List<Generator> generators = marketArea.getGenerators();
		final Comparator<Generator> comp = (Generator g1, Generator g2) -> g1.getName()
				.compareTo(g2.getName());
		Collections.sort(generators, comp);

		for (final Generator generator : generators) {
			titleLine.add(new ColumnHeader(generator.getName() + " Gap (incl cap factor)",
					Unit.CAPACITY));
		}
		titleLine.add(new ColumnHeader("Capacity Factor", Unit.NONE));
		titleLine.add(new ColumnHeader("Max Remaining Load", Unit.CAPACITY));

		final int logFile = LoggerXLSX.newLogObject(Folder.INVESTMENT, fileNameGapsAndProfit,
				description, titleLine, marketArea.getIdentityAndNameLong(), Frequency.HOURLY);

		// Log data
		final List<Object> data = new ArrayList<>();
		final int startYear = Date.getStartYear();
		final int endYear = Date.getLastYear();

		for (int year = startYear; year < endYear; year++) {
			data.clear();
			data.add(year);

			float totalCapacity = 0f;
			for (final Generator generator : marketArea.getGenerators()) {
				if (capacityAgentCurrent.containsKey(year)
						&& capacityAgentCurrent.get(year).containsKey(generator.getName())) {
					totalCapacity += capacityAgentCurrent.get(year).get(generator.getName());
				}
			}
			data.add(totalCapacity);

			float totalExpectedCapacity = 0f;
			for (final Generator generator : marketArea.getGenerators()) {
				if (capacityAgentExpectedFuture.containsKey(year)
						&& capacityAgentExpectedFuture.get(year).containsKey(generator.getName())) {
					totalExpectedCapacity += capacityAgentExpectedFuture.get(year)
							.get(generator.getName());
				}
			}
			data.add(totalExpectedCapacity);

			data.add(gapAllAgentsInclCapacityFactor.get(year));
			for (final Generator generator : marketArea.getGenerators()) {
				data.add(gapAgentInclCapacityFactor.get(year).get(generator.getName()));
			}
			data.add(capacityFactor.get(year));
			data.add(marketArea.getManagerRenewables().getRemainingLoadMax(year));
			LoggerXLSX.writeLine(logFile, data);
		}

		LoggerXLSX.close(logFile);
	}

	/**
	 * Log the new installations by the InvestmentPlanner agents for each year
	 * and fuel type
	 */
	public void logNewInvestments() {

		// Initialize file
		final int currentYear = Date.getYear();
		final String filename = marketArea.getInitialsUnderscore() + "newPowerPlants" + currentYear;
		final String description = "Logging newly build capacities";

		final ArrayList<ColumnHeader> titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("Year", Unit.YEAR));
		for (final FuelType fuelType : FuelType.values()) {
			for (final EnergyConversion energyConversion : EnergyConversion.values()) {
				titleLine.add(new ColumnHeader(fuelType.name() + "_" + energyConversion.name(),
						Unit.CAPACITY));
			}
		}

		logIDNewCap = LoggerXLSX.newLogObject(Folder.INVESTMENT, filename, description, titleLine,
				marketArea.getIdentityAndNameLong(), Frequency.YEARLY);

		// Loop all years
		for (int year = Settings.getStartYear(); year <= Date.getLastYear(); year++) {
			final List<Object> dataLine = new ArrayList<>();
			dataLine.add(year);
			// Loop all fuel types
			for (final FuelType fuelType : FuelType.values()) {
				for (final EnergyConversion energyConversion : EnergyConversion.values()) {
					dataLine.add(getInvestmentsByYearAndFuelAndTechnology(year, fuelType,
							energyConversion));

				}
			}
			LoggerXLSX.writeLine(logIDNewCap, dataLine);
		}

		LoggerXLSX.close(logIDNewCap);
		logIDNewCap = -1;
	}

	public void setGapsAndProfit(float gapAgentInclCapacityFactor, float gapAgentExclCapacityFactor,
			float gapAllAgentsInclCapacityFactor, float gapAllAgentsExclCapacityFactor,
			float capacityAgentCurrent, float capacityAgentExpectedFuture, float capacityFactor,
			float profit, String name) {
		final int year = Date.getYear();
		this.capacityFactor.put(year, capacityFactor);
		this.gapAgentInclCapacityFactor.get(year).put(name, gapAgentInclCapacityFactor);
		this.gapAgentExclCapacityFactor.get(year).put(name, gapAgentExclCapacityFactor);
		this.gapAllAgentsInclCapacityFactor.put(year, gapAllAgentsInclCapacityFactor);
		this.gapAllAgentsExclCapacityFactor.put(year, gapAllAgentsExclCapacityFactor);
		this.capacityAgentCurrent.get(year).put(name, capacityAgentCurrent);
		this.capacityAgentExpectedFuture.get(year).put(name, capacityAgentExpectedFuture);
		this.profit.get(year).put(name, profit);
	}

	/** Write and close log files */
	protected abstract void closeLogFiles();

}
