package simulations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.Format.TextMode;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.initialization.FileParser;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import simulations.scheduling.MarketScheduler;
import simulations.scheduling.Steps;
import tools.logging.LoggerXLSX;
import tools.types.MarketAreaType;

/**
 * Contains the main method. Constitutes the core class of the simulation.
 * Starts the method that constructs agents from the input XML files with data
 * from the corresponding databases.
 *
 * @since 08.11.2004
 * @author PowerACE team
 */
public class PowerMarkets {

	private class AgentsSetup {

		/** Builds general agents from XML file. */
		private void buildModelFromFile(PowerMarkets model) {
			initializeValuesModel(model);
		}

		private void initializeValuesModel(PowerMarkets model) {
			try {
				new FileParser(model, pathWorkspace + projectName + File.separator + PARAM_DIRECTORY
						+ settingsFolder + settingsFile, null);
			} catch (final Exception e) {
				logger.error("Maybe file was not found " + pathWorkspace + projectName
						+ File.separator + PARAM_DIRECTORY + settingsFolder + settingsFile, e);
				System.exit(0);
			}
		}
	}

	public static String projectName;

	/**
	 * This counter is incremented with every run and saved in the user settings
	 * file.
	 */
	private static int fileSuffixCounter;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(PowerMarkets.class.getName());
	/** Mail address for sending notifications */
	private static String mailAddress = "";

	private static int multiRunCurrent;
	private static int multiRunsTotal;
	/**
	 * Used for better output format. Make it tread safe via ThreadLocal since
	 * DecimalFormat is not.
	 */
	private static final ThreadLocal<NumberFormat> numberFormat = new ThreadLocal<>() {
		@Override
		public NumberFormat initialValue() {
			return new DecimalFormat(",##0.00", new DecimalFormatSymbols(Locale.of("en")));
		}
	};
	/** Location of xml files defining agents. */
	private static final String PARAM_DIRECTORY = "params" + File.separator;
	private static String pathWorkspace;
	private static String scenarioPath;
	/**
	 * Default value for list of parameter interface. If this file does not
	 * exist, this will throw an error.
	 */
	/** Name of xml file with base settings */
	private static String settingsFile;
	/**
	 * Name of folder containing base xml file and market areas xml files<br>
	 * <br>
	 * <b>NOTE:</b> If the xml files are not saved in a folder but directly in
	 * 'params', <code>settingsFolder</code> must be an empty String (not null).
	 */
	private static String settingsFolder;
	private static final String SOURCE_DIRECTORY = "source" + File.separator;
	private static final String USER_SETTINGS = "userSettings.xml";

	public static NumberFormat getDecimalFormat() {
		return numberFormat.get();
	}

	/**
	 * @return The current counter for the file suffix that is incremented with
	 *         every run.
	 */
	public static int getFileSuffixCounter() {
		return fileSuffixCounter;
	}

	/** Get {@link#mailAddress} */
	public static String getMailAddress() {
		return mailAddress;
	}

	public static int getMultiRunCurrent() {
		return multiRunCurrent;
	}

	public static int getMultiRunsTotal() {
		return multiRunsTotal;
	}

	/** {@link PowerMarkets#settingsFolder} */
	public static String getParamDirectory() {
		return PARAM_DIRECTORY;
	}

	public static String getPathWorkspace() {
		return pathWorkspace;
	}

	public static String getProjectName() {
		return projectName;
	}

	public static String getScenarioPath() {
		return scenarioPath;
	}

	public static String getSettingsFile() {
		return settingsFile;
	}

	public static String getSettingsFileWithoutFileSuffix() {
		return settingsFile.replace(".xml", "");
	}

	public static String getSettingsFolder() {
		return settingsFolder;
	}

	public static String getSettingsFolderTotal() {
		return pathWorkspace + projectName + File.separator + PARAM_DIRECTORY + settingsFolder;
	}

	public static String getSourceDirectory() {
		return SOURCE_DIRECTORY;
	}

	public static String getUserSettingsFile() {
		return USER_SETTINGS;
	}

	public static void main(final String[] args) {

		final PowerMarkets model = new PowerMarkets();
		multiRunCurrent = 1;
		model.buildModel();
		while (multiRunCurrent <= multiRunsTotal) {
			if (multiRunCurrent > 1) {
				System.gc();
			}
			try {
				// multirunsTotal is only known after first initialization
				if (multiRunCurrent > 1) {
					// Set new seed in case of variable multirunRandomNumberSeed
					// in settings.xml is true
					Settings.setNewRandomNumberSeed();
					model.buildModel();
				}
				// Run all steps for current run
				while (Date.getDayOfTotal() <= Date.getTotalDays()) {
					model.steps.step();
				}
			} catch (final Exception e) {
				// for multiruns try next run, maybe error occurs just in one
				// run and others can continue
				logger.error(e.getMessage(), e);
			}
			multiRunCurrent++;

		}

		// Logger xlsx
		LoggerXLSX.closeFinal();
	}

	public static void setMultiRunsTotal(int multiRunsTotal) {
		PowerMarkets.multiRunsTotal = multiRunsTotal;
	}

	public static void setPathWorkspace(String pathWorkspace) {
		PowerMarkets.pathWorkspace = pathWorkspace;
	}

	public static void setProjectName(String projectName) {
		PowerMarkets.projectName = projectName;
	}

	public static void setScenarioPath(String pathScenario) {
		PowerMarkets.scenarioPath = pathScenario;
	}

	public static void setSettingsFile(String settingsFile) {
		PowerMarkets.settingsFile = settingsFile;
	}

	/** Indicates whether model has already been initiated */
	public boolean initializer = true;

	/** List of market areas */
	private LinkedHashSet<MarketArea> marketAreas;
	/** Map of market areas with the initials as key */
	private Map<String, MarketArea> marketAreasMappedInitials;
	/** Market scheduler */
	private MarketScheduler marketScheduler;

	/** Steps object */
	private Steps steps;

	public PowerMarkets() {
		Date.setStartTime(LocalDateTime.now());
	}

	public void addMarketArea(MarketArea marketArea) {
		marketAreas.add(marketArea);
		marketAreasMappedInitials.put(
				MarketAreaType.valueOf(marketArea.getName().toUpperCase()).getInitials(),
				marketArea);
	}

	/**
	 * Build the model based on the current parameters.
	 *
	 * @see uchicago.src.sim.engine.SimpleModel#buildModel()
	 */
	public void buildModel() {
		try {
			final long time1 = System.currentTimeMillis();

			readUserSettingsFromFile();

			steps = new Steps(this);
			marketScheduler = new MarketScheduler(this);
			marketAreas = new LinkedHashSet<>();
			marketAreasMappedInitials = new HashMap<>();
			MarketArea.resetCounter();

			/**
			 * Here the general xml file will be parsed making general settings,
			 * instantiating market areas and building general agents
			 */
			new AgentsSetup().buildModelFromFile(this);

			final long time2 = System.currentTimeMillis();

			logger.info("AgentBuilding total time " + (time2 - time1) + " ms");
			logger.info("Build model complete");
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	public Set<MarketArea> getMarketAreas() {
		return marketAreas;
	}

	public Map<String, MarketArea> getMarketAreasMappedInitials() {
		return marketAreasMappedInitials;
	}

	public MarketScheduler getMarketScheduler() {
		return marketScheduler;
	}

	private void readUserSettingsFromFile() {
		try {
			final SAXBuilder builder = new SAXBuilder();
			final Document doc = builder
					.build(new FileInputStream("params" + File.separator + USER_SETTINGS));
			final Element elRoot = doc.getRootElement();

			if (elRoot != null) {
				final List<?> listPaths = elRoot.getChildren();

				final String attributeName = "name";

				final Element elPathWS = (Element) listPaths.get(0);
				PowerMarkets.pathWorkspace = elPathWS.getAttributeValue(attributeName);

				final Element elProject = (Element) listPaths.get(1);
				PowerMarkets.projectName = elProject.getAttributeValue(attributeName);

				final Element elscenarioPath = (Element) listPaths.get(2);
				PowerMarkets.scenarioPath = elscenarioPath.getAttributeValue(attributeName);

				final Element elXmlFolder = (Element) listPaths.get(3);
				PowerMarkets.settingsFolder = elXmlFolder.getAttributeValue(attributeName)
						+ File.separator;

				final Element elXmlFile = (Element) listPaths.get(4);
				PowerMarkets.settingsFile = elXmlFile.getAttributeValue(attributeName);

				// Change counter
				final Element elXmlCounter = (Element) listPaths.get(5);
				fileSuffixCounter = Integer.valueOf(elXmlCounter.getAttributeValue("number"));
				elXmlCounter.setAttribute("number", Integer.toString(++fileSuffixCounter % 100));

				// EMail Adress
				final Element elMail = (Element) listPaths.get(6);
				PowerMarkets.mailAddress = elMail.getAttributeValue(attributeName);

				// Write values in order to save counter value for future runs
				final XMLOutputter xmlOutput = new XMLOutputter();
				// Set the display
				final Format format = Format.getRawFormat();
				format.setIndent("\t");
				format.setTextMode(TextMode.TRIM);
				xmlOutput.setFormat(format);
				xmlOutput.output(doc,
						new FileWriter("params" + File.separator + "userSettings.xml"));

			}

		} catch (final FileNotFoundException e) {
			logger.error("FileNotFoundException : ", e);
		} catch (final IOException e) {
			logger.error("IOException : ", e);
		} catch (final JDOMException e) {
			logger.error("JDOMException : ", e);
		}
	}

}