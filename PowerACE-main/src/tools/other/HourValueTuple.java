package tools.other;

public class HourValueTuple implements Comparable<HourValueTuple> {

	private int hour;
	private float value;

	public HourValueTuple(int hour, float value) {
		this.hour = hour;
		this.value = value;
	}

	@Override
	public int compareTo(HourValueTuple hourValueTuple) {
		return Float.compare(value, hourValueTuple.getValue());
	}

	public int getHour() {
		return hour;
	}

	public float getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "hour: " + hour + "; value: " + value;
	}

}