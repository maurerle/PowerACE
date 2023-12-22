package supply.tools.periods;

/**
 * A period is a time period with a start and end hour.
 * 
 * 
 * 
 */
public class Period {

	/** End of Period. Hour 0 represents first of current day. */
	private final int endHour;
	private final int length;
	/** Start of Period. Hour 0 represents first of current day. */
	private final int startHour;

	public Period(int startHour, int endHour) {
		this.startHour = startHour;
		this.endHour = endHour;
		length = (endHour - startHour) + 1;
	}

	public int getEndHour() {
		return endHour;
	}

	/**
	 * @return length of period in hours
	 */
	public int getLength() {
		return length;
	}

	public int getStartHour() {
		return startHour;
	}

	@Override
	public String toString() {
		return "Start: " + startHour + ", End: " + endHour;
	}

}