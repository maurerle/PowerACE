package simulations.initialization;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import simulations.MarketArea;
import simulations.PowerMarkets;

/**
 * A class to call hourly bids parallel and improve running time. Has the same
 * functionality as FileParser
 *
 * @author 
 *
 */
public class Agent implements Callable<Map<Object, String>> {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Agent.class.getName());

	private String agentClass;
	private String agentName;
	/** List of all agents of the current class curClass. */
	private final Map<Object, String> agentsMap = new HashMap<>();
	private Class<?> curClass;
	private MarketArea marketArea;
	private String marketAreaName;
	private final PowerMarkets model;
	private final List<ScenarioSetting> multiRunSettings;
	private final Node node;

	public Agent(PowerMarkets model, Node node, List<ScenarioSetting> multiRunSettings, String marketAreaName,
			MarketArea marketArea) {
		this.model = model;
		this.node = node;
		this.multiRunSettings = multiRunSettings;
		this.marketAreaName = marketAreaName;
		this.marketArea = marketArea;
	}

	@Override
	public Map<Object, String> call() {
		try {
			parseClass(node);
		} catch (final Exception e) {
			logger.error("Error for class " + node.getAttributes().getNamedItem("name"), e);
			System.exit(0);
		}
		return agentsMap;
	}

	private String checkMultiRunValue(ScenarioSetting defaultSetting) {

		String valueName = defaultSetting.value;

		if ((multiRunSettings != null) && !multiRunSettings.isEmpty()) {
			for (final ScenarioSetting multiRunSetting : multiRunSettings) {
				if (compareValue(multiRunSetting, defaultSetting)) {
					valueName = multiRunSetting.value;
					break;
				}
			}
		}

		return valueName;

	}

	/**
	 *
	 * Compares the parameter and returns true, if the parameter have the same
	 * target (same agent class, agent name, market area and variable name). The
	 * value of the variable is irrelevant here.
	 *
	 * @param valueA
	 * @param valueB
	 * @return
	 */
	private boolean compareValue(ScenarioSetting valueA, ScenarioSetting valueB) {

		// return false, if only one value exists or both exist but are
		// unequal
		if ((valueA.agent != null) && (valueB.agent != null)) {
			if (((valueA.agent != null) && (valueB.agent == null)) || ((valueA.agent == null) && (valueB.agent != null))
					|| !valueA.agent.equals(valueB.agent)) {
				return false;
			}
		}

		// return false, if only one value exists or both exist but are
		// unequal
		if ((valueA.agentName != null) && (valueB.agentName != null)) {
			if (((valueA.agentName != null) && (valueB.agentName == null))
					|| ((valueA.agentName == null) && (valueB.agentName != null))
					|| !valueA.agentName.equals(valueB.agentName)) {
				return false;
			}
		}

		// return false, if only one value exists or both exist but are
		// unequal
		if ((valueA.area != null) && (valueB.area != null)) {
			if (((valueA.area != null) && (valueB.area == null)) || ((valueA.area == null) && (valueB.area != null))
					|| !valueA.area.equals(valueB.area)) {
				return false;
			}
		}

		// return false, if only one value exists or both exist but are
		// unequal
		if ((valueA.name != null) && (valueB.name != null)) {
			if (((valueA.name != null) && (valueB.name == null)) || ((valueA.name == null) && (valueB.name != null))
					|| !valueA.name.equals(valueB.name)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Constructs new instance of curClass and sets its properties according to
	 * property elements.
	 *
	 * @param agentNode
	 * @throws Exception
	 */
	private List<Object> constructAgent(final Node agentNode, final List<Node> globalProperties) throws Exception {

		Object agent;
		final NodeList childs = agentNode.getChildNodes();

		// get number of agents from attributes
		final Node num = agentNode.getAttributes().getNamedItem("number");
		final int numberOfAgents = Integer.parseInt(num.getNodeValue());

		final List<Object> nodeAgents = new ArrayList<>();

		// construct 'number' of identical agents
		for (int j = 0; j < numberOfAgents; j++) {
			// construct agent
			agent = curClass.getDeclaredConstructor().newInstance();

			for (final Node node : globalProperties) {
				setProperty(agent, node);
			}
			for (int i = 0; i < childs.getLength(); i++) {
				setProperty(agent, childs.item(i));
			}

			if (agent instanceof MarketArea) {
				// Set name of market area
				((MarketArea) agent).setName(marketAreaName);
				// Relate market area and PowerMarkets model
				((MarketArea) agent).setModel(model);
			}

			if (agent instanceof simulations.agent.Agent) {
				// Set market area
				((simulations.agent.Agent) agent).setMarketArea(marketArea);
			}

			nodeAgents.add(agent);
		}

		return nodeAgents;
	}

	/**
	 * Parse the agentclass element. Write implemented interfaces to curInterfaces,
	 * set static variables of agentclass and construct agents of this class.
	 *
	 * @param node
	 * @throws Exception
	 */
	private void parseClass(final Node node) {
		int childNodeIndex = 0;
		try {
			NamedNodeMap nodeMap = node.getAttributes();
			agentClass = nodeMap.getNamedItem("name").getNodeValue();
			curClass = Class.forName(agentClass);
			final NodeList childs = node.getChildNodes();
			String interfaces = "";

			/** List of all agent class nodes. */
			final List<Node> globalProperties = new ArrayList<>();

			for (; childNodeIndex < childs.getLength(); childNodeIndex++) {

				final Node childNode = childs.item(childNodeIndex);
				nodeMap = childNode.getAttributes();
				final String childNodeName = childNode.getNodeName();

				// set current country
				if ((nodeMap != null) && (nodeMap.getNamedItem("country") != null)) {
					marketAreaName = nodeMap.getNamedItem("country").getNodeValue();
				}

				// set current name
				if (!childNodeName.equals("global") // global does not have a
													// name
						&& (nodeMap != null) && (nodeMap.getNamedItem("name") != null)) {
					agentName = nodeMap.getNamedItem("name").getNodeValue();
				}

				// Interface
				if ("implements".equals(childNodeName)) {
					interfaces = (interfaces + (nodeMap.getNamedItem("interface")).getNodeValue());
				}
				// Static field
				else if ("static".equals(childNodeName)) {
					final String name = nodeMap.getNamedItem("name").getNodeValue();

					String value;

					// Check for multirunsettings
					final ScenarioSetting setting = new ScenarioSetting(agentClass, agentName, marketAreaName, name,
							nodeMap.getNamedItem("value").getNodeValue());
					final String nodeValue = checkMultiRunValue(setting);

					// Set value but first evaluate expression e.g. 365*13
					if (nodeMap.getNamedItem("class").getNodeValue().matches(".*(Integer|Short)")) {
						// To evaluate math expression from Strings
						try {
							if (nodeValue.contains("*")) {
								value = "1";
								String[] expression = nodeValue.split("\\*");
								for (String term : expression) {
									value = "" + Integer.parseInt(value) * Integer.parseInt(term);
								}
							} else {
								value = nodeValue;
							}
						} catch (final Exception e) {
							value = "";
							logger.error(name + " " + e, e);
						}
					} else {
						value = nodeValue;
					}

					final Class<?> clazz = Class.forName(nodeMap.getNamedItem("class").getNodeValue());
					final Object val = clazz.getConstructor(new Class[] { value.getClass() })
							.newInstance(new Object[] { value });

					// Set field accessible
					try {
						final Field field = curClass.getDeclaredField(name);
						field.setAccessible(true);
						// Set value for field
						field.set(null, val);
					} catch (NoSuchFieldException | NullPointerException e) {
						// logger may not work here yet, don't really know why
						// though
						logger.error("Field causes problems " + name + " in class " + curClass.toString(), e);
					}
				}
				// Field of an instance
				else if ("global".equals(childNodeName)) {
					globalProperties.add(childNode);
				}
				// New instance of an agent
				else if ("agent".equals(childNodeName)) {
					// Construct agent, first set properties and then invoke
					// initialize
					final List<Object> agents = constructAgent(childNode, globalProperties);
					for (final Object agent : agents) {
						// Invoke initalize
						agent.getClass().getMethod("initialize").invoke(agent);
						agentsMap.put(agent, interfaces);
					}
				}
			}

		} catch (final Exception e) {
			logger.error(
					"Current " + curClass.toString() + " for which initalization failed in Agent.parseClass()! Index: "
							+ childNodeIndex + ". Cause: " + e.getCause(),
					e);
			System.exit(0);
		}
	}

	/**
	 * Set the agents attributes via reflection.
	 *
	 * @param agent
	 * @param node
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setProperty(final Object agent, final Node node) throws Exception {
		Object arg;
		NamedNodeMap map;
		String name, value;
		Class<?> clazz;
		Method method = null;

		map = node.getAttributes();
		if ("property".equals(node.getNodeName()) || "global".equals(node.getNodeName())) {

			// get agent's property
			name = map.getNamedItem("name").getNodeValue();
			clazz = Class.forName(map.getNamedItem("class").getNodeValue());

			// Check value for multirunsettings (if multirun exist replace value
			// with multirunvalue)
			final String nodeValue = map.getNamedItem("value").getNodeValue();
			// Check for current name of agent
			if (name.equals("Name")) {
				agentName = nodeValue;
			}
			final ScenarioSetting setting = new ScenarioSetting(agentClass, agentName, marketAreaName, name, nodeValue);
			value = checkMultiRunValue(setting);

			// Check for enum value
			boolean enumValue = false;
			boolean enumValueSet = false;
			Class<?> clazzForEnum = curClass;
			if (!enumValueSet) {
				try {
					final Field field = clazzForEnum.getDeclaredField(name);
					if (field.getType().isEnum()) {
						field.setAccessible(true);
						field.set(agent, Enum.valueOf((Class<Enum>) field.getType(), value));
						enumValue = true;
						enumValueSet = true;
					}
				} catch (final NoSuchFieldException e) {
					clazzForEnum = clazzForEnum.getSuperclass();
				}
			}

			if (!enumValue) {

				// construct value object, clazz must have constructor with
				// String
				// argument
				arg = clazz.getConstructor(new Class[] { value.getClass() }).newInstance(new Object[] { value });
				final String methodName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
				boolean valueSet = false;

				// In order to set value try different methods use Integer/int
				// methods or direct setting via the field.

				// First try wrapper class, e.g. Integer for int
				Class<?> clazzForMethod = curClass;
				// Find methods, some are in super classes
				while (clazzForMethod != Object.class) {
					try {
						method = clazzForMethod.getDeclaredMethod(methodName, new Class[] { clazz });
						break;
					} catch (final NoSuchMethodException ex) {
						clazzForMethod = clazzForMethod.getSuperclass();
					} catch (final NullPointerException e) {
						logger.error(name + value, e);
						break;
					}
				}
				if (method != null) {
					method.setAccessible(true);
					method.invoke(agent, new Object[] { arg });
					valueSet = true;
				}

				// Try primitive type variant of parameter,
				// e.g. int (This this does not work for String)
				if (!valueSet) {
					// Find methods, some are in super classes
					clazzForMethod = curClass;
					while (clazzForMethod != Object.class) {
						try {
							method = clazzForMethod.getDeclaredMethod(methodName,
									new Class[] { (Class<?>) clazz.getField("TYPE").get(null) });
							break;
						} catch (final NoSuchMethodException ex) {
							clazzForMethod = clazzForMethod.getSuperclass();
						} catch (final NoSuchFieldException ex) {
							// For String fields that do not have a setter
							// method,
							// or fields that have neither set(Integer),set(int)
							break;
						}
					}
					if (method != null) {
						method.setAccessible(true);
						method.invoke(agent, new Object[] { arg });
						valueSet = true;
					}
				}

				// For String fields that do not have a setter
				// method
				if (!valueSet) {
					// Find methods, some are in super classes
					clazzForMethod = curClass;
					while (clazzForMethod != Object.class) {
						try {
							clazz = (Class<?>) clazzForMethod.getField("TYPE").get(null);
							method = clazzForMethod.getMethod(methodName, new Class[] { clazz });
							method.invoke(agent, new Object[] { arg });
							valueSet = true;
						} catch (final NoSuchFieldException | NoSuchMethodException e) {
							clazzForMethod = clazzForMethod.getSuperclass();
						}
					}
				}

				// For fields that have neither
				// set(Integer), set(int) or are not a String, set value
				// directly
				if (!valueSet) {
					Class<?> clazzForField = curClass;
					while (clazzForField != Object.class) {
						try {
							final Field field = clazzForField.getDeclaredField(name);
							field.setAccessible(true);
							field.set(agent, arg);
							valueSet = true;
							break;
						} catch (final NoSuchFieldException e) {
							clazzForField = clazzForField.getSuperclass();
						}
					}
				}

				if (!valueSet) {
					logger.error("Unknown field '" + name + "' for agent '" + agent.getClass().getSimpleName() + "/"
							+ agent.toString() + "/" + "'.");
				}

			}
		}
	}
}