package simulations.agent;

import simulations.MarketArea;

/**
 * General abstract super class for all agents in PowerACE project.
 *
 * @since 14.10.2004
 * @author Petr Nenutil
 */
public abstract class Agent implements Comparable<Agent> {

	private String name;
	/**
	 * Market area this agent is active in. Market area is set when the agent is
	 * initialized.
	 */
	protected MarketArea marketArea;

	public Agent() {
		this(null, null);
	}

	public Agent(MarketArea marketArea) {
		this(null, marketArea);
	}

	public Agent(String name, MarketArea marketArea) {
		this.marketArea = marketArea;
		if (name == null) {
			this.name = this.getClass().getSimpleName();
		} else {
			this.name = name;
		}
	}

	@Override
	public int compareTo(Agent other) {
		if (this.getClass() == other.getClass()) {
			return name.compareToIgnoreCase(other.name);
		} else {
			return this.getClass().getName().compareToIgnoreCase(other.getClass().getName());
		}
	}

	public MarketArea getMarketArea() {
		return marketArea;
	}

	/**
	 * 
	 * @return the name of this object
	 */
	public String getName() {
		return name;
	}

	public abstract void initialize();

	public void setMarketArea(final MarketArea marketArea) {
		this.marketArea = marketArea;
	}

	/**
	 * Needed when name is not known at construction of object. See for example
	 * generator
	 */
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}

}