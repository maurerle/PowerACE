package supply.tools.periods;

/**
 * A running period is a time period in which a plant is continuously running or
 * not running.
 * 
 * 
 * 
 */
public class RunningPeriodSimple extends Period {

	/**
	 * True if running.
	 */
	private final boolean running;

	public RunningPeriodSimple(int startHour, int endHour, boolean running) {
		super(startHour, endHour);
		this.running = running;
	}

	public boolean isRunning() {
		return running;
	}

}