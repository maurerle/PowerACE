package tools.math;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used for interpolation values between points.
 * 
 * @author Florian
 *
 */
public class Interpolation {
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(LinearRegressionRecord.class.getName());
	/**
	 * Simplest linear interpolation between two points. (lerp)
	 * 
	 * Given are two data points (x0, f0) and (x1, f1).
	 * 
	 * @param x1
	 *            First value on the x-axis
	 * @param x2
	 *            Second value on the x-axis
	 * @param f1
	 *            First point on the y-axis
	 * @param f2
	 *            Second point on the y-axis
	 * @param argument
	 *            Value where the interpolation point is missing
	 * @return The interpolant f(x) at the argument x by the function
	 *         f(x)=f0+(x-x1)(f2-f1)/(x2-x1)
	 */
	public static float linear(float x1, float x2, float f1, float f2, float argument) {
		if (x1 == x2) {
			logger.error(
					"Linear interpolation does not work because x0=x1! It would be a vertikal line");
			return Float.NaN;
		}
		return f1 + (((argument - x1) * (f2 - f1)) / (x2 - x1));
	}
}
