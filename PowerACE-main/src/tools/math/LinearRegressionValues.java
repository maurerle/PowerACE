package tools.math;

import java.util.List;

/**
 * @author 
 *
 */
public class LinearRegressionValues {

	private final float beta0;
	private final float beta1;
	private List<Float> residuals;

	public LinearRegressionValues(float beta0, float beta1) {
		this.beta0 = beta0;
		this.beta1 = beta1;
	}

	public LinearRegressionValues(float beta0, float beta1, List<Float> residuals) {
		this.beta0 = beta0;
		this.beta1 = beta1;
		this.residuals = residuals;
	}

	public float getBeta0() {
		return beta0;
	}

	public float getBeta1() {
		return beta1;
	}

	public List<Float> getResiduals() {
		return residuals;
	}

}