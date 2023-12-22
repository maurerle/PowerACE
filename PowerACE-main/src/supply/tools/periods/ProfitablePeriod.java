package supply.tools.periods;

/**
 * 
 * A profitable period is a time period in which a plant is continuously running
 * or not running.
 * 
 * 
 * 
 */
public class ProfitablePeriod extends Period {

	/** Running state (FULL, PARTIAL, ... ) */
	private final boolean profitable;

	public ProfitablePeriod(int startHour, int endHour, boolean profitable) {
		super(startHour, endHour);
		this.profitable = profitable;
	}

	public boolean isProfitable() {
		return profitable;
	}

}