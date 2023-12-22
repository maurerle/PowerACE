package simulations.initialization;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import simulations.MarketArea;
import simulations.PowerMarkets;

/**
 * Parses XML file with agent definitions and their properties. Format of this
 * file is defined in file 'agents.dtd'.
 *
 * @since 06.12.2004
 * @author
 */
public class FileParser {

	private static final String AGENT = "agent";
	private static final String AGENTCLASS = "agentclass";
	private static final String COUNTRY = "country";
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(FileParser.class.getName());
	/**
	 * Multiruns values, stay the same for each initialization run, therefore
	 * have to be static, since class is also called in market area.
	 */
	private static List<ScenarioSetting> multiRunValues;
	private static final String NAME = "name";
	private static final String PRIORITY = "priority";
	private static final String RUN = "run";
	private static final String VALUE = "value";
	/** List of all agent class nodes with <i>high</i> priority. */
	private final List<Node> agentClassesPrioHigh = new ArrayList<>();
	/** List of all agent class nodes with <i>low</i> priority. */
	private final List<Node> agentClassesPrioLow = new ArrayList<>();
	/** List of all agents of the current class curClass. */
	private final Map<Object, String> agentsMap = new LinkedHashMap<>();
	private int currentRun = 0;
	/** Current market area */
	private MarketArea marketArea;
	private final PowerMarkets model;

	/**
	 * Initialization of DOM parser.
	 *
	 * @param fileName
	 *            File to be parsed.
	 * @throws Exception
	 *             Can be raised due to mistakes in XML file.
	 */
	public FileParser(PowerMarkets model, final String fileName, MarketArea marketArea)
			throws Exception {

		final DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		final Document doc = parser.parse(new FileInputStream(fileName), fileName);
		final NodeList agentClassNodes = doc.getDocumentElement().getChildNodes();
		this.model = model;
		this.marketArea = marketArea;

		// For market areas this is already known from the first call of the
		// basesim
		if (currentRun != PowerMarkets.getMultiRunCurrent()) {
			// Find settings for multiruns. Multiruns can be set either
			// indirectly via a separate multirun file (with varying scenario
			// settings) or directly via settings for Monte Carlo simulation
			findMultiRunSettings(agentClassNodes);
			// Find the simulation settings for each multirun
			findMultiRunValues();
			currentRun++;
		}

		setupTask(agentClassNodes);
		processTasks();
	}

	public Map<Object, String> getAgents() {
		return agentsMap;
	}

	/**
	 * Find all the setting for current run.
	 *
	 * @param agentClassNodes
	 * @param agentParent
	 * @param agentNameParent
	 * @param areaParent
	 */
	private void findMultiRunCurrentValues(NodeList agentClassNodes, String agentParent,
			String agentNameParent, String areaParent) {

		String agent = agentParent;
		String agentName = agentNameParent;
		String area = areaParent;

		for (int index = 0; index < agentClassNodes.getLength(); index++) {
			final Node node = agentClassNodes.item(index);

			// Get class values
			if (node.getNodeName().equals(AGENTCLASS)) {
				if (node.hasAttributes()) {
					final NamedNodeMap map = node.getAttributes();

					if (map.getNamedItem(NAME) != null) {
						agent = map.getNamedItem(NAME).getNodeValue();
					}

					if (map.getNamedItem(COUNTRY) != null) {
						area = map.getNamedItem(COUNTRY).getNodeValue();
					}
				}
			}

			// Get class values
			if (node.getNodeName().equals(AGENT)) {
				if (node.hasAttributes()) {
					final NamedNodeMap map = node.getAttributes();
					if (map.getNamedItem(NAME) != null) {
						agentName = map.getNamedItem(NAME).getNodeValue();
					}
					if (map.getNamedItem(COUNTRY) != null) {
						area = map.getNamedItem(COUNTRY).getNodeValue();
					}
				}
			}

			// Add values to list
			if (node.hasAttributes()) {
				final NamedNodeMap map = node.getAttributes();
				if (map.getNamedItem(VALUE) != null) {
					final String value = node.getAttributes().getNamedItem(VALUE).getNodeValue();
					final String name = node.getAttributes().getNamedItem(NAME).getNodeValue();
					multiRunValues.add(new ScenarioSetting(agent, agentName, area, name, value));
				}
			}

			// Check for child nodes
			if (node.hasChildNodes()) {
				findMultiRunCurrentValues(node.getChildNodes(), agent, agentName, area);
			}
		}

	}

	/**
	 * Check settings for multirun file and Monte Carlo settings. The settings
	 * are found in the general xml file (static fields
	 * <code>multiRunsFile</code> and <code>monteCarloRuns</code>). Save
	 * identified settings in Settings class.
	 *
	 * @param agentClassNodes
	 */
	private void findMultiRunSettings(NodeList agentClassNodes) {

		for (int index = 0; index < agentClassNodes.getLength(); index++) {
			final Node node = agentClassNodes.item(index);

			// Check all agent classes
			if (node.getNodeName().equals(AGENTCLASS)) {
				final NamedNodeMap map = node.getAttributes();
				final Node subNode = map.getNamedItem(NAME);
				if (subNode != null) {

					// Find Settings class
					if (subNode.getNodeValue()
							.equals(simulations.initialization.Settings.class.getName())) {
						final NodeList mapDataConfig = node.getChildNodes();
						String fileName = null;

						// Check values of Settings class
						for (int indexDataConfig = 0; indexDataConfig < mapDataConfig
								.getLength(); indexDataConfig++) {

							if (mapDataConfig.item(indexDataConfig).getNodeName()
									.equals("static")) {
								final NamedNodeMap mapStatic = mapDataConfig.item(indexDataConfig)
										.getAttributes();

								if (mapStatic.getNamedItem(NAME) != null) {

									// Check for multirun file name
									if (mapStatic.getNamedItem(NAME).getNodeValue()
											.equals("multiRunsFile")) {
										fileName = mapStatic.getNamedItem(VALUE).getNodeValue();
										Settings.setMultiRunsFile(fileName);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Find the number of multiruns by counting the different nodes with name
	 * run.
	 *
	 * @param agentClassNodes
	 */
	private void findMultirunsNumber(NodeList agentClassNodes) {

		// Only do this once at beginning.
		if (PowerMarkets.getMultiRunsTotal() > 1) {
			return;
		}

		int numberOfRuns = 0;
		for (int index = 0; index < agentClassNodes.getLength(); index++) {
			if (agentClassNodes.item(index).getNodeName().equals(RUN)) {
				numberOfRuns++;
			}
		}

		PowerMarkets.setMultiRunsTotal(numberOfRuns);
	}

	/**
	 * Set up the number of multi runs and read the settings of this run.
	 */
	private void findMultiRunValues() {
		try {

			// No file specified
			if (Settings.getMultiRunsFile() == null) {
				PowerMarkets.setMultiRunsTotal(1);
				return;
			}

			// Get xml file
			final String multiRunsFile = PowerMarkets.getSettingsFolderTotal()
					+ Settings.getMultiRunsFile();

			final DocumentBuilder parser = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder();
			final Document doc = parser.parse(new FileInputStream(multiRunsFile), multiRunsFile);
			final NodeList agentClassNodes = doc.getDocumentElement().getChildNodes();

			// find number of runs
			findMultirunsNumber(agentClassNodes);

			// find current run
			int currentRunSettings = 0;
			for (int index = 0; index < agentClassNodes.getLength(); index++) {

				if ((agentClassNodes.item(index) != null)
						&& agentClassNodes.item(index).getNodeName().equals(RUN)) {
					currentRunSettings++;
				}

				if (currentRunSettings == PowerMarkets.getMultiRunCurrent()) {
					// Reinitialize settings and find settings for current run
					multiRunValues = new ArrayList<>();
					findMultiRunCurrentValues(agentClassNodes.item(index).getChildNodes(), null,
							null, null);
					return;
				}
			}

		} catch (ParserConfigurationException | SAXException e) {
			logger.error(e.getMessage());
		} catch (final IOException e) {
			logger.error(e.getMessage());
			PowerMarkets.setMultiRunsTotal(1);
		} catch (final Exception e) {
			logger.error(e.getMessage());
		}

	}

	private void processTasks() {

		// Process high priority first, and run sequentially (concurrent not yet
		// possible)
		for (final Node node : agentClassesPrioHigh) {
			String area = null;
			if (marketArea != null) {
				area = marketArea.getName();
			}
			final Agent taskAgent = new Agent(model, node, multiRunValues, area, marketArea);
			taskAgent.call();
		}

		// Process low priority parallel, since order is only relevant in
		// comparison with high or medium priority task
		// Current problem with multicores, sometimes initialization fails
		for (final Node node : agentClassesPrioLow) {
			String area = null;
			if (marketArea != null) {
				area = marketArea.getName();
			}
			final Agent taskAgent = new Agent(model, node, multiRunValues, area, marketArea);
			agentsMap.putAll(taskAgent.call());
		}

	}

	/**
	 * Setup different task for the agents based on their priority, e.g. high,
	 * medium or low.
	 *
	 * @param
	 */
	private void setupTask(final NodeList agentClassNodes) {

		for (int index = 0; index < agentClassNodes.getLength(); index++) {
			final Node agentNode = agentClassNodes.item(index);

			if (AGENTCLASS.equals(agentNode.getNodeName())) {

				if (agentNode.getAttributes().getNamedItem(PRIORITY) != null) {
					final String priority = agentNode.getAttributes().getNamedItem(PRIORITY)
							.getNodeValue();
					// high priority
					if ("High".equalsIgnoreCase(priority)) {
						agentClassesPrioHigh.add(agentClassNodes.item(index));
					}
					// low priority
					// Maybe clone is needed since concurrency can lead to
					// problems
					// Seems to be a workaround but further testing is needed
					else if ("Low".equalsIgnoreCase(priority)) {
						agentClassesPrioLow.add(agentClassNodes.item(index));
					}
				}
				// no priority is given so take default priority (low)
				else {
					agentClassesPrioLow.add(agentNode);
				}
			}
		}
	}

}
