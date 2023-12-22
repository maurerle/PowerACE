package supply.scenarios;

import java.util.List;

/**
 * Single scenario presents a single scenario that has values.
 * 
 * 
 * 
 */
public class ScenarioList<O> extends Scenario {

	private float probability;
	/** The values */
	private final List<O> values;

	public ScenarioList(int time, String name, List<O> values) {
		super(time, name);
		this.values = values;
	}

	public ScenarioList(int time, String name, List<O> values, float probability) {
		super(time, name);
		this.values = values;
		this.probability = probability;
	}

	public O get(int index) {
		return values.get(index);
	}

	public float getProbability() {
		return probability;
	}

	public int getSize() {
		return values.size();
	}

	public List<O> getValues() {
		return values;
	}
	@Override
	public String toString() {
		return values.toString();
	}
}