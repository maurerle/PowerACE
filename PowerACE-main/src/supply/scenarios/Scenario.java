package supply.scenarios;

/**
 * Super class for all scenarios.
 * 
 * @see ScenarioCluster
 * @see ScenarioSet
 * 
 * 
 * 
 */
public class Scenario {

	/** Name of scenario, e.g. ScenarioHigh */
	private final String name;
	/** time is represented by discrete time steps (eg. 1,2,...) */
	private final int time;

	public Scenario(int time, String name) {
		this.time = time;
		this.name = name;
	}

	/**
	 * @return The name of scenario, which should be <i>unique</i>.
	 */
	public String getName() {
		return name;
	}

	/**
	 * time is represented by discrete time steps (eg. 1,2,...)
	 * 
	 * @return The time of the scenario.
	 */
	public int getTime() {
		return time;
	}

}