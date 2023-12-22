package supply.invest;

/**
 * Strategic state of generation unit
 * <p>
 * StateStrategic.DECOMMISSIONED is the only state which is final once reached
 * in the sense that no action except for Action.CONTINUE is available in all
 * subsequent periods. This also means that the underlying stochastic evolution
 * of other state variables does not need to be simulated in this state.
 */
public enum StateStrategic {
	DECOMMISSIONED,
	MOTHBALLED,
	NEWBUILD_OPPORTUNITY,
	OPERATING,
	UNDER_CONSTRUCTION;
}