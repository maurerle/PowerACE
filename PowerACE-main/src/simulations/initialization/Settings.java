package simulations.initialization;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.StatusPrinter;
import markets.trader.spot.supply.tools.ForecastTypeDayAhead;
import simulations.PowerMarkets;
import simulations.scheduling.Date;
import tools.file.Operations;
import tools.logging.Folder;
import tools.logging.LoggerCSV;
import tools.other.Concurrency;

/**
 * Reads basic settings from settings xml file before buidling the model
 */
public class Settings {

	private static boolean adjustWeekend;
	private static boolean aggregatePlantBid;
	private static boolean calculateLossOfLoad;

	private static int capFilter;
	private static float carbonPenalty;
	private static String carbonPriceScenario;
	private static String carbonPriceScenarioHistorical;
	private static boolean checkBlackout;
	private static boolean colorHist;
	private static boolean cumulatedBids;
	private static String databaseEffectiveDate;
	private static ForecastTypeDayAhead dayAheadPriceForecastType = ForecastTypeDayAhead.OPTIMIZATION;
	private static boolean eexLike;
	/**
	 * Use "yyyy-MM-dd" pattern!
	 */
	public static float FLOATING_POINT_TOLERANCE = 0.001f;
	private static float greenPenalty;
	private static boolean hydrogenOptimizeGlobal;
	private static boolean includeCarbonGlobal;
	private static double inflation;
	private static String interconnectionDataHourly;
	private static String interconnectionDataScenario;
	private static int investDynamicStateTransformationScenario;
	private static int investmentHorizonMax;
	private static int investmentsYearStart;

	private static String languageSettings;
	public static String LOG_FILE_SUFFIX_CSV = ".csv";
	public static String LOG_FILE_SUFFIX_EXCEL = ".xlsx";
	/** Load logs-intial setting from XML File */
	private static boolean logAnnualInvestment;
	private static boolean logBenchmark;
	private static boolean logBids;
	private static boolean logDayAheadForecast;
	private static String logFolderTopLevel;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Settings.class);
	private static boolean logHourlyGeneration;
	private static boolean logInstalledCapacity;
	private static boolean logMarketGrowth;
	private static String logPathName;
	private static boolean logPricesStructure;
	private static boolean logPumpStorage;
	private static boolean logPumpStorageDetailed;
	private static boolean logRegUtilisation;
	private static boolean logRenewableFullLoadHours;
	private static boolean logRenewableLoad;
	private static boolean logRenewableNewConstruction;
	private static boolean logRenewableRemainingTechPotential;
	/** RenewableSupport */
	private static boolean logRenewableSupport;
	private static boolean logSortedResPlants;
	private static boolean logSupplyData;
	private static boolean longMeritOrder;
	private static boolean loqLossOperations;
	private static short mainRegSupportSceme;
	private static boolean marketClearingPriceSensitive;
	private static String multiRunName;
	private static boolean multirunRandomNumberSeed;
	private static String multiRunsFile;
	private static Integer mustrunYearEnd = Integer.MAX_VALUE;
	private static String name;
	private static boolean naturalGasPhaseOut;
	private static boolean nuclearPhaseOut;
	private static int numberOfCores;
	/** Number of simulated years */
	private static int numberOfYears;
	private static String operationMaintenanceScenarioFixed;
	private static String operationMaintenanceScenarioVar;
	private static String plantAvailabilityScenario;
	private static int quotaScenario;
	private static long randomNumberSeed;
	private static Integer referenceYear;
	/** periods for blocks sold to suppliers */
	private static int renewablePeriods;
	/** RES Capacities scenario the is needed in RenewableManager */
	private static String resCapacityScenario;

	private static String scenarioString;
	private static String startupCostsScenario;
	private static int startYear;

	private static Integer startYearPlots;

	private static String staticExchange;

	private static boolean stratCosts;

	private static float strategicStartUp;
	private static float strategicStartUpCoal;
	private static float strategicStartUpGuD;

	private static short taxScenario;
	private static String technologyOptions;

	private static int totalDays;

	private static boolean useGIS;

	private static boolean useHistoricalAvailability = true;
	private static boolean useSyntheticGas;

	public static boolean calculateLossOfLoad() {
		return calculateLossOfLoad;
	}

	public static int getCapFilter() {
		return capFilter;
	}

	public static float getCarbonPenalty() {
		return carbonPenalty;
	}

	public static String getCarbonPriceScenario() {
		return carbonPriceScenario;
	}

	public static String getCarbonPriceScenarioHistorical() {
		return carbonPriceScenarioHistorical;
	}

	/**
	 *
	 * @return Effective Date for the power plant Database until the updates
	 *         have to be considered. Current Date if nothing is set in the XML.
	 */
	public static LocalDateTime getDatabaseEffectiveDate() {
		if (databaseEffectiveDate == null) {
			return LocalDateTime.now();
		}
		return LocalDateTime.parse(databaseEffectiveDate, DateTimeFormatter.ISO_DATE);
	}

	public static ForecastTypeDayAhead getDayAheadPriceForecastType() {
		return dayAheadPriceForecastType;
	}

	public static float getGreenPenalty() {
		return greenPenalty;
	}

	public static double getInflation() {
		return inflation;
	}

	public static String getInterconnectionDataHourly() {
		return interconnectionDataHourly;
	}

	public static String getInterconnectionDataScenario() {
		return interconnectionDataScenario;
	}

	public static int getInvestmentHorizonMax() {
		return investmentHorizonMax;
	}

	public static int getInvestmentsStart() {
		return investmentsYearStart;
	}

	public static String getLanguageSettings() {
		return languageSettings;
	}

	public static String getLogFolderTopLevel() {
		return logFolderTopLevel;
	}

	/**
	 * @return the absolute log path name for the current run for example
	 *         C:\simulations\MyScenario-2012-01\RUN1\
	 */
	public static String getLogPathName() {
		return logPathName;
	}

	/**
	 * @return the absolute log path name for the current run plus market area
	 *         and folder for example
	 *         C:\simulations\MyScenario-2012-01\RUN1\MarketArea\Folder
	 */
	public static synchronized String getLogPathName(String marketArea, Folder folder) {
		try {
			// Check whether market area is empty
			final String marketAreaPart = (marketArea.equals("")
					? ""
					: marketArea + File.separator);
			// Create MarketArea folder if does not exist
			final Path folderMarketArea = Paths.get(logPathName + marketAreaPart);
			if (!Files.exists(folderMarketArea)) {

				Files.createDirectory(folderMarketArea);
			}
			// Create file folder if does not exist
			final Path fileFolder = Paths.get(logPathName + marketAreaPart + folder.toString());
			if (!Files.exists(fileFolder)) {
				Files.createDirectory(fileFolder);
			}
			// Add separators before and after folder name (only when folder
			// name is not empty)
			final String folderWithSeparators = (folder.equals(Folder.MAIN) ? "" : File.separator)
					+ folder.toString() + File.separator;
			return logPathName + marketAreaPart + folderWithSeparators;
		} catch (final IOException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return null;
	}

	/**
	 * @return the absolute log path name for the current run for example
	 *         C:\simulations\MyScenario-2012-01\multirunResults
	 */
	public static String getLogPathNameMultiruns() {
		return logFolderTopLevel + File.separator + "multirunResults" + File.separator;
	}

	public static short getMainRegSupportSceme() {
		return mainRegSupportSceme;
	}

	public static String getMultiRunName() {
		return multiRunName;
	}

	public static String getMultiRunsFile() {
		return multiRunsFile;
	}

	public static int getMustrunYearEnd() {
		return mustrunYearEnd;
	}

	public static int getNumberOfCores() {
		return numberOfCores;
	}

	public static int getNumberOfYears() {
		return numberOfYears;
	}

	public static String getOperationMaintenanceScenarioFixed() {
		return operationMaintenanceScenarioFixed;
	}

	public static String getOperationMaintenanceScenarioVar() {
		return operationMaintenanceScenarioVar;
	}

	public static String getPlantAvailabilityScenario() {
		return plantAvailabilityScenario;
	}

	public static int getQuotaScenario() {
		return quotaScenario;
	}

	public static long getRandomNumberSeed() {
		return randomNumberSeed;
	}

	public static int getRenewablePeriods() {
		return renewablePeriods;
	}

	public static String getResCapacityScenario() {
		return resCapacityScenario;
	}

	public static String getScenarioString() {
		return scenarioString;
	}

	public static String getStartupCostsScenario() {
		return startupCostsScenario;
	}

	public static int getStartYear() {
		return startYear;
	}

	public static int getStateTransformationTableScenario() {
		return investDynamicStateTransformationScenario;
	}

	public static String getStaticExchange() {
		return staticExchange;
	}

	public static float getStrategicStartUp() {
		return strategicStartUp;
	}

	public static float getStrategicStartUpCoal() {
		return strategicStartUpCoal;
	}

	public static float getStrategicStartUpGuD() {
		return strategicStartUpGuD;
	}

	public static short getTaxScenario() {
		return taxScenario;
	}

	public static String getTechnologyOptions() {
		return technologyOptions;
	}

	public static boolean isAdjustWeekend() {
		return adjustWeekend;
	}

	public static boolean isAggregatePlantBid() {
		return aggregatePlantBid;
	}

	public static boolean isCheckBlackout() {
		return checkBlackout;
	}

	public static boolean isColorHist() {
		return colorHist;
	}

	public static boolean isCumulatedBids() {
		return cumulatedBids;
	}

	public static boolean isEexLike() {
		return eexLike;
	}

	public static boolean isHydrogenOptimizeGlobal() {
		return hydrogenOptimizeGlobal;
	}

	public static boolean isIncludeCO2global() {
		return includeCarbonGlobal;
	}

	public static boolean isLogAnnualInvestment() {
		return logAnnualInvestment;
	}

	public static boolean isLogBenchmark() {
		return logBenchmark;
	}

	public static boolean isLogBids() {
		return logBids;
	}

	public static boolean isLogDayAheadForecast() {
		return logDayAheadForecast;
	}

	public static boolean isLogHourlyGeneration() {
		return logHourlyGeneration;
	}

	public static boolean isLogInstalledCapacity() {
		return logInstalledCapacity;
	}

	public static boolean isLogLossOperations() {
		return loqLossOperations;
	}

	public static boolean isLogMarketGrowth() {
		return logMarketGrowth;
	}

	public static boolean isLogPricesStructure() {
		return logPricesStructure;
	}

	public static boolean isLogPumpStorage() {
		return logPumpStorage;
	}

	public static boolean isLogPumpStorageDetailed() {
		return logPumpStorageDetailed;
	}

	public static boolean isLogRegUtilisation() {
		return logRegUtilisation;
	}

	public static boolean isLogRenewableFullLoadHours() {
		return logRenewableFullLoadHours;
	}

	public static boolean isLogRenewableLoad() {
		return logRenewableLoad;
	}

	public static boolean isLogRenewableNewConstruction() {
		return logRenewableNewConstruction;
	}

	public static boolean isLogRenewableRemainingTechPotential() {
		return logRenewableRemainingTechPotential;
	}

	public static boolean isLogRenewableSupport() {
		return logRenewableSupport;
	}

	public static boolean isLogSortedResPlants() {
		return logSortedResPlants;
	}

	public static boolean isLogSupplyData() {
		return logSupplyData;
	}

	public static boolean isLongMeritOrder() {
		return longMeritOrder;
	}

	public static boolean isMarketClearingPriceSensitive() {
		return marketClearingPriceSensitive;
	}

	public static boolean isNaturalGasPhaseOut() {
		return naturalGasPhaseOut;
	}

	public static boolean isNuclearPhaseOut() {
		return nuclearPhaseOut;
	}

	public static boolean isStratCosts() {
		return stratCosts;
	}

	public static boolean isUseGIS() {
		return useGIS;
	}

	public static boolean isUseHistoricalAvailability() {
		return useHistoricalAvailability;
	}

	public static boolean isUseSyntheticGas() {
		return useSyntheticGas;
	}

	public static void setInvestmentHorizonMax(int investmentHorizonMax) {
		Settings.investmentHorizonMax = investmentHorizonMax;
	}

	public static void setMultiRunsFile(String multiRunsFile) {
		Settings.multiRunsFile = multiRunsFile;
	}

	/**
	 * The seed for the random number generator as a double value in the
	 * interval [0,2^63 -1]
	 */
	public static void setNewRandomNumberSeed() {
		if (multirunRandomNumberSeed) {
			randomNumberSeed = (long) (Math.random() * Math.pow(2, 63));
		} else {
			randomNumberSeed = 0;
		}
	}

	/**
	 * @param input
	 * @return
	 */
	private static String toFolderCompatible(String input) {
		final StringBuilder titleCase = new StringBuilder();
		boolean nextTitleCase = true;

		for (char c : input.toCharArray()) {
			if (Character.isSpaceChar(c)) {
				c = '_';
				nextTitleCase = true;
			} else if (nextTitleCase) {
				c = Character.toTitleCase(c);
				nextTitleCase = false;
			}

			titleCase.append(c);
		}

		return titleCase.toString();
	}

	public void initialize() {
		if (startYearPlots == null) {
			startYearPlots = startYear;
		}
		try {
			Date.setInitialDate(startYear, startYearPlots, referenceYear, totalDays);

			setFileNameEndings();
			createOutputFolder();
			initializeLogger();
			logger.info("Initialize Settings");
			// First load data logger, since other classes need the path name
			// for logging
			LoggerCSV.initialize();

			final boolean loadData = true;
			// For testing purposes it is easier is not all data is loaded
			if (loadData) {
				loadData();
			}
		} catch (final SQLException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Create the output folder in which log files and graphs are stored
	 */
	private void createOutputFolder() {

		String settingsFile = PowerMarkets.getSettingsFolder().substring(0,
				Math.min(Math.max(1, PowerMarkets.getSettingsFolder().length() - 1), 10));

		if (settingsFile.endsWith("_")) {
			settingsFile = settingsFile.substring(0, settingsFile.length() - 1);
		}

		scenarioString = "";

		// Add custom name if existing
		if (name != null) {
			// ensure no space in title
			scenarioString += Settings.toFolderCompatible(name) + File.separator;
		}

		scenarioString += "" + settingsFile + "__Range." + startYear + "-"
				+ (Date.getLastYear() - 2000) + "__"
				+ Date.getStartTimeFormatted("'Start.'MM.dd-kk.mm.ss");

		// Write folder for log files
		final String folder = PowerMarkets.getScenarioPath();
		logFolderTopLevel = folder + scenarioString;
		if (PowerMarkets.getMultiRunCurrent() == 1) {
			final File f = new File(logFolderTopLevel);
			if (f.exists()) {
				Operations.deleteTree(f);
			}
		}
		final File f = new File(folder);
		final File p = new File(logFolderTopLevel + File.separator);
		if (!f.exists()) {
			f.mkdir();
		}
		if (!p.exists()) {
			p.mkdir();
		}

		if (PowerMarkets.getMultiRunsTotal() == 1) {
			// no need for run Folder if only one run exists
			logPathName = logFolderTopLevel + File.separator;
		} else if ((multiRunName != null) && !multiRunName.isEmpty()) {
			logPathName = logFolderTopLevel + File.separator
					+ String.format("%02d", PowerMarkets.getMultiRunCurrent()) + "_" + multiRunName
					+ File.separator;
		} else {
			logPathName = logFolderTopLevel + File.separator + "RUN"
					+ PowerMarkets.getMultiRunCurrent() + File.separator;
		}

	}

	/** Set parameters for logger and initialize logger */
	private void initializeLogger() {

		// Set run counter so logger knows which run is currently used
		String runCounter = "";
		if (PowerMarkets.getMultiRunsTotal() > 1) {
			runCounter = PowerMarkets.getMultiRunCurrent() + "/" + PowerMarkets.getMultiRunsTotal()
					+ " -";
		}
		System.setProperty("runCounter", runCounter);

		// Set output directory
		// Currently now working correctly for each run
		final String loggingDirectory = logPathName;
		// Set to global folder

		// Set output directory
		System.setProperty("log.dir", loggingDirectory);

		// assume SLF4J is bound to logback in the current environment
		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

		try {
			final JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			// Call context.reset() to clear any previous configuration, e.g.
			// default configuration. For multi-step configuration, omit calling
			// context.reset(). Set configuration file
			final String logConfigFile = PowerMarkets.getPathWorkspace()
					+ PowerMarkets.getProjectName() + File.separator
					+ PowerMarkets.getParamDirectory() + "logback.xml";
			context.reset();
			configurator.doConfigure(logConfigFile);
		} catch (final JoranException je) {
			// StatusPrinter will handle this
		}
		StatusPrinter.printInCaseOfErrorsOrWarnings(context);

		// Add another log file for all errors for multiruns
		if (PowerMarkets.getMultiRunsTotal() > 1) {

			// Set logging layout
			final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
			final PatternLayoutEncoder ple = new PatternLayoutEncoder();
			ple.setPattern(runCounter
					+ " %d{HH:mm:ss.SSS} - %-5level - %-100msg %throwable{full} [%logger{36}.%M.%L.%t]%n");
			ple.setContext(lc);
			ple.start();

			// Only accept errors
			final LevelFilter errorFilter = new LevelFilter();
			errorFilter.setContext(lc);
			errorFilter.setLevel(Level.ERROR);
			errorFilter.setOnMatch(FilterReply.ACCEPT);
			errorFilter.setOnMismatch(FilterReply.DENY);
			errorFilter.start();

			// Set file location
			final FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
			fileAppender.setFile(logFolderTopLevel + File.separator + "errorAll.log");
			fileAppender.setEncoder(ple);
			fileAppender.setContext(lc);
			fileAppender.addFilter(errorFilter);
			fileAppender.start();

			final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
					.getLogger(Logger.ROOT_LOGGER_NAME);
			logger.addAppender(fileAppender);

		}

	}

	/**
	 * Loads the data for the utility classes. In order to optimize the
	 * performance threads are used. If a utility class depends on another, then
	 * it is loaded after the other class has been initialized.
	 * 
	 * @throws SQLException
	 */
	private void loadData() throws SQLException {

		final Collection<Callable<Void>> tasks = new ArrayList<>();

		Concurrency.executeConcurrently(tasks);
	}

	/**
	 * Set ending of log file name in order to have quasi-unique file names.
	 * This is useful when opening several files with Excel which need to have
	 * different file names.
	 * <p>
	 * Pattern: {Run number (in case of a multirun)} + "_" + {Fraction of second
	 * of start time}
	 */
	private void setFileNameEndings() {
		String fileNameSuffix;
		if (PowerMarkets.getMultiRunsTotal() == 1) {
			fileNameSuffix = Integer.toString(PowerMarkets.getFileSuffixCounter());
		} else {
			fileNameSuffix = String.format("%02d", PowerMarkets.getMultiRunCurrent()) + "_"
					+ Date.getStartTimeFormatted("S");
		}
		LOG_FILE_SUFFIX_CSV = "_" + fileNameSuffix + ".csv";
		LOG_FILE_SUFFIX_EXCEL = "_" + fileNameSuffix + ".xlsx";
	}
}