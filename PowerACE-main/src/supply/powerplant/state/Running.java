package supply.powerplant.state;

/**
 * The possible <b>states</b> for the current power plant production:
 * 
 * <ul>
 * <li><code>{@link #FULL}</code> - meaning production cannot be increased</li>
 * <li><code>{@link #PARTLY}</code> - meaning production between full and
 * minimal</li>
 * <li><code>{@link #MINIMAL}</code> - meaning production at minimal
 * capacity</li>
 * <li><code>{@link #NOT}</code> - meaning plant is not producing</li>
 * </ul>
 * 
 * 
 * 
 */
public enum Running {

	FULL,
	MINIMAL,
	NOT,
	PARTLY;

	public boolean isRunning() {
		boolean running;
		if (this == NOT) {
			running = false;
		} else {
			running = true;
		}
		return running;
	}

}