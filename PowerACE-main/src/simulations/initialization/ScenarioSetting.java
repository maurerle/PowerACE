package simulations.initialization;

public class ScenarioSetting {

	public String agent;
	public String agentName;
	public String area;
	public String name;
	public String value;

	public ScenarioSetting(String agent, String agentName, String area, String name, String value) {
		this.agent = agent;
		this.agentName = agentName;
		this.area = area;
		this.name = name;
		this.value = value;
	}

	@Override
	public String toString() {
		return agent + "." + agentName + "." + area + "." + name + "." + value;
	}

}