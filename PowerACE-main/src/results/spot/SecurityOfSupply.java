package results.spot;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import simulations.agent.Agent;
import simulations.scheduling.Date;

/**
 * Contains the minimal hourly supply surplus for all years.
 * 
 * 
 *
 */
public final class SecurityOfSupply extends Agent {

	public enum SecurityOfSupplyValue {
		HIGH,
		LOW
	}

	private final class SecurityOfSupplyLogObject implements Comparable<SecurityOfSupplyLogObject> {

		int hour;
		float level;
		float loadSurplus;

		public SecurityOfSupplyLogObject(int hour, float loadSurplus, float level) {
			super();
			this.hour = hour;
			this.loadSurplus = loadSurplus;
			this.level = level;
		}

		@Override
		public int compareTo(SecurityOfSupplyLogObject other) {
			return Float.compare(level, other.level);
		}

	}

	/**
	 * Maximum number of stored values
	 */
	private int maxNumberOfValues = 10;

	/**
	 * Contains for all years the hour in which the minimal hourly capacity
	 * surplus occurred.
	 */
	private final Map<Integer, TreeSet<SecurityOfSupplyLogObject>> securityOfSupplyWithExchange = new HashMap<>();

	/**
	 * Contains for all years the hour in which the minimal hourly capacity
	 * surplus occurred.
	 */
	private final Map<Integer, TreeSet<SecurityOfSupplyLogObject>> securityOfSupplyWithoutExchange = new HashMap<>();

	public SecurityOfSupply() {
		initialize();
	}

	public void addValue(TreeSet<SecurityOfSupplyLogObject> list, SecurityOfSupplyLogObject value) {
		list.add(value);

		// required to keep list short
		if (list.size() > maxNumberOfValues) {
			list.remove(list.last());
		}
	}

	public void addValue(TreeSet<SecurityOfSupplyLogObject> list, SecurityOfSupplyLogObject value,
			float maxNumberOfValues) {
		list.add(value);

		// required to keep list short
		if (list.size() > maxNumberOfValues) {
			list.remove(list.last());
		}
	}

	/**
	 * @return add volume and store it if new minimum is found.
	 */
	public void addVolume(int hour, float capacityWithExchange, float capacityWithoutExchange,
			float levelWithExchange, float levelWithoutExchange) {
		final int currentYear = Date.getYear();

		final SecurityOfSupplyLogObject logWithExchange = new SecurityOfSupplyLogObject(hour,
				capacityWithExchange, levelWithExchange);
		addValue(securityOfSupplyWithExchange.get(currentYear), logWithExchange);

		final SecurityOfSupplyLogObject logWithoutExchange = new SecurityOfSupplyLogObject(hour,
				capacityWithoutExchange, levelWithoutExchange);
		addValue(securityOfSupplyWithoutExchange.get(currentYear), logWithoutExchange);

	}

	/**
	 * @return The hour of the current year in which the minimal capacity
	 *         surplus occurred.
	 */
	public Integer getHourWithExchange() {
		return getHourWithExchange(Date.getYear(), SecurityOfSupplyValue.LOW);
	}

	/**
	 * @return The hour of the requested year in which the minimal capacity
	 *         surplus occurred.
	 */
	public Integer getHourWithExchange(int year, SecurityOfSupplyValue value) {
		if (securityOfSupplyWithExchange.get(year).isEmpty()) {
			return null;
		}

		if (value == SecurityOfSupplyValue.HIGH) {
			return securityOfSupplyWithExchange.get(year).last().hour;
		} else if (value == SecurityOfSupplyValue.LOW) {
			return securityOfSupplyWithExchange.get(year).first().hour;
		}
		return null;
	}

	/**
	 * @return The hour of the current year in which the minimal capacity
	 *         surplus occurred.
	 */
	public Integer getHourWithExchange(SecurityOfSupplyValue value) {
		return getHourWithExchange(Date.getYear(), value);
	}

	/**
	 * @return The hour of the current year in which the minimal capacity
	 *         surplus occurred.
	 */
	public Integer getHourWithoutExchange() {
		return getHourWithoutExchange(Date.getYear(), SecurityOfSupplyValue.LOW);
	}

	/**
	 * @return The hour of the requested year in which the minimal capacity
	 *         surplus occurred.
	 */
	public Integer getHourWithoutExchange(int year, SecurityOfSupplyValue value) {
		if (securityOfSupplyWithoutExchange.get(year).isEmpty()) {
			return null;
		}

		if (value == SecurityOfSupplyValue.HIGH) {
			return securityOfSupplyWithoutExchange.get(year).last().hour;
		} else if (value == SecurityOfSupplyValue.LOW) {
			return securityOfSupplyWithoutExchange.get(year).first().hour;
		}
		return null;
	}

	/**
	 * @return The hour of the current year in which the minimal capacity
	 *         surplus occurred.
	 */
	public Integer getHourWithoutExchange(SecurityOfSupplyValue value) {
		return getHourWithoutExchange(Date.getYear(), value);
	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLevelWithExchange(int year) {
		return getSecurityOfSupplyLevelWithExchange(year, SecurityOfSupplyValue.LOW);
	}

	/**
	 * @return The minimal capacity surplus of the requested year.
	 */
	public Float getSecurityOfSupplyLevelWithExchange(int year, SecurityOfSupplyValue value) {
		if (securityOfSupplyWithExchange.get(year).isEmpty()) {
			return null;
		}

		if (value == SecurityOfSupplyValue.HIGH) {
			return securityOfSupplyWithExchange.get(year).last().level;
		} else if (value == SecurityOfSupplyValue.LOW) {
			return securityOfSupplyWithExchange.get(year).first().level;
		}
		return null;

	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLevelWithExchange(SecurityOfSupplyValue value) {
		return getSecurityOfSupplyLevelWithExchange(Date.getYear(), value);
	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLevelWithoutExchange(int year) {
		return getSecurityOfSupplyLevelWithoutExchange(year, SecurityOfSupplyValue.LOW);
	}

	/**
	 * @return The minimal capacity surplus of the requested year.
	 */
	public Float getSecurityOfSupplyLevelWithoutExchange(int year, SecurityOfSupplyValue value) {

		if (value == SecurityOfSupplyValue.HIGH) {
			return securityOfSupplyWithoutExchange.get(year).last().level;
		} else {
			return securityOfSupplyWithoutExchange.get(year).first().level;
		}

	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLevelWithoutExchange(SecurityOfSupplyValue value) {
		return getSecurityOfSupplyLevelWithoutExchange(Date.getYear(), value);
	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLoadWithExchange(int year) {
		return getSecurityOfSupplyLoadWithExchange(year, SecurityOfSupplyValue.LOW);
	}

	/**
	 * @return The minimal capacity surplus of the requested year.
	 */
	public Float getSecurityOfSupplyLoadWithExchange(int year, SecurityOfSupplyValue value) {

		if (value == SecurityOfSupplyValue.HIGH) {
			return securityOfSupplyWithExchange.get(year).last().loadSurplus;
		} else {
			return securityOfSupplyWithExchange.get(year).first().loadSurplus;
		}

	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLoadWithExchange(SecurityOfSupplyValue value) {
		return getSecurityOfSupplyLoadWithExchange(Date.getYear(), value);
	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLoadWithoutExchange(int year) {
		return getSecurityOfSupplyLoadWithoutExchange(year, SecurityOfSupplyValue.LOW);
	}

	/**
	 * @return The minimal capacity surplus of the requested year.
	 */
	public Float getSecurityOfSupplyLoadWithoutExchange(int year, SecurityOfSupplyValue value) {
		if (value == SecurityOfSupplyValue.HIGH) {
			return securityOfSupplyWithoutExchange.get(year).last().loadSurplus;
		} else {
			return securityOfSupplyWithoutExchange.get(year).first().loadSurplus;
		}
	}

	/**
	 * @return The minimal capacity surplus of the current year.
	 */
	public Float getSecurityOfSupplyLoadWithoutExchange(SecurityOfSupplyValue value) {
		return getSecurityOfSupplyLoadWithoutExchange(Date.getYear(), value);
	}

	@Override
	public void initialize() {
		// Insert dummy values
		for (int year = Date.getYear(); year <= Date.getLastYear(); year++) {
			securityOfSupplyWithExchange.put(year, new TreeSet<>());
			securityOfSupplyWithoutExchange.put(year, new TreeSet<>());
		}
	}

}