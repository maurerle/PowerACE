package supply.tools.periods;

import supply.powerplant.state.Running;

/**
 * A running period is a time period in which a plant is continuously running or
 * not running.
 * 
 * 
 * 
 */
public class RunningPeriod extends Period {

	private final Running running;

	public RunningPeriod(int startHour, int endHour, boolean running) {
		super(startHour, endHour);
		if (running) {
			this.running = Running.FULL;
		} else {
			this.running = Running.NOT;
		}
	}

	public RunningPeriod(int startHour, int endHour, Running running) {
		super(startHour, endHour);
		this.running = running;
	}

	public Running getRunning() {
		return running;
	}

}