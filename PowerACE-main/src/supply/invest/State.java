package supply.invest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.scheduling.Date;
import supply.powerplant.PlantAbstract;
import supply.powerplant.PlantOption;

/**
 * Class to describe the state of an investment option
 * <p>
 * A state is defined by<br>
 * - its strategic state<br>
 * - the number of remaining periods in the current strategic state (e.g. for
 * construction)<br>
 * - node in lattice of contribution margins
 */
public class State {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger // NOPMD
	(State.class.getName());

	/**
	 * Set strategic states initially. As long as technical lifetime is not
	 * reached plant is assumed to be operating.
	 */
	public static void setStatesStrategicInitial(PlantAbstract plant,
			StateStrategic stateStrategic) {

		final int yearStart = Math.min(Date.getStartYear(), plant.getAvailableYear());
		final int yearEnd = (Math.max(Date.getLastDetailedForecastYear(),
				Date.getStartYear() + plant.getRemainingTechnicalLifetime(Date.getYear())) + 1);

		for (int year = yearStart; year <= yearEnd; year++) {
			if ((plant.isAvailableTechnically(year)) || (plant instanceof PlantOption)) {
				plant.setStateStrategic(year, new State(stateStrategic, 0));
			} else {
				plant.setStateStrategic(year, new State(StateStrategic.DECOMMISSIONED, 0));
			}
		}
	}

	/**
	 * Remaining construction periods (zero when in state unequal to
	 * StateStrategic.UNDER_CONSTRUCTION)
	 */
	private final int attributeConstructionPeriodsRemaining;
	/** Strategic state */
	private final StateStrategic attributeStateStrategic;

	/** Public constructor */
	public State(StateStrategic stateStrategic, int constructionPeriodsRemaining) {
		attributeStateStrategic = stateStrategic;
		if (constructionPeriodsRemaining < 0) {
			logger.error("No negative construction periods possible!");
		} else if ((constructionPeriodsRemaining < 0)
				&& (!stateStrategic.equals(StateStrategic.UNDER_CONSTRUCTION))) {
			logger.error("Strategic state is " + stateStrategic.toString()
					+ ", but non-zero construction period is set!");
		}
		attributeConstructionPeriodsRemaining = constructionPeriodsRemaining;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final State other = (State) obj;
		if (attributeConstructionPeriodsRemaining != other.attributeConstructionPeriodsRemaining) {
			return false;
		}
		if (attributeStateStrategic != other.attributeStateStrategic) {
			return false;
		}
		return true;
	}

	/** Get number of remaining periods in current state */
	public int getAttributeConstructionPeriodsRemaining() {
		return attributeConstructionPeriodsRemaining;
	}

	/** Get strategic state */
	public StateStrategic getAttributeStateStrategic() {
		return attributeStateStrategic;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + attributeConstructionPeriodsRemaining;
		result = (prime * result)
				+ ((attributeStateStrategic == null) ? 0 : attributeStateStrategic.hashCode());
		return result;
	}

	@Override
	public String toString() {
		if (attributeConstructionPeriodsRemaining > 0) {
			return attributeStateStrategic + "{" + attributeConstructionPeriodsRemaining + "}";
		} else {
			return attributeStateStrategic.toString();
		}
	}
}