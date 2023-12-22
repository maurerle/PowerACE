package tools.math;
/**
 * List of the names of regression variables for the price forecast
 * 
 * @since 08/2013
 * @author PR
 */
public enum RegressionVariableName {

	MCP(
			RegressionVariableType.CONTINUOUS),
	DEMAND(
			RegressionVariableType.CONTINUOUS),
	RENEWABLES(
			RegressionVariableType.CONTINUOUS),
	PUMPSTORAGE(
			RegressionVariableType.CONTINUOUS),
	DUMMY_WEEKEND(
			RegressionVariableType.BINARY),
	DUMMY_HOUR(
			RegressionVariableType.BINARY),
	DUMMY_SEASON(
			RegressionVariableType.BINARY),
	MEAN_MCP(
			RegressionVariableType.CONTINUOUS),
	MEAN_MCP_LAGGED(
			RegressionVariableType.CONTINUOUS),
	SUM_MCP(
			RegressionVariableType.CONTINUOUS);

	/** Type of regression variable */
	private RegressionVariableType regressionVariableType;

	/** Constructor */
	private RegressionVariableName(RegressionVariableType regressionVariableType) {
		this.regressionVariableType = regressionVariableType;
	}

	public RegressionVariableType getRegressionVariableType() {
		return regressionVariableType;
	}
}
