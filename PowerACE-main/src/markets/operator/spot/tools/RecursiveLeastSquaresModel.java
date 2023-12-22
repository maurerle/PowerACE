package markets.operator.spot.tools;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.NonPositiveDefiniteMatrixException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RectangularCholeskyDecomposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.scheduling.Date;

/**
 * Model using the recursive least squares algorithm to estimate coefficients
 * that minimize a weighted linear least squares cost function relating to the
 * input signals (see e.g.
 * http://en.wikipedia.org/wiki/Recursive_least_squares_filter).
 * <p>
 * The model can be used e.g. for estimating a multiple linear regression model
 * by continuously updating the coefficients with new data instead of estimating
 * always the full model. Thereby, requirements for data storage are limited.
 */
public class RecursiveLeastSquaresModel {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static Logger logger = LoggerFactory // NOPMD
			.getLogger(RecursiveLeastSquaresModel.class.getName());
	/** Tolerance between thresholds and input data [%] */
	private final static double THRESHOLD_TOLERANCE = 0.1d;

	/**
	 * Indicates whether checking covariance matrix for positive definiteness is
	 * activated
	 */
	private boolean checkCovarianceMatrix;
	/** Current coefficients */
	private RealMatrix coefficientsMatrix;
	/** Current covariance matrix */
	private RealMatrix covarianceMatrix;
	/**
	 * Factor which gives exponentially less weight to older error samples [0 <
	 * factor <= 1]
	 */
	private final double forgettingFactor;
	/**
	 * Number of system variables (in a regression model these correspond to the
	 * independent variables without the constant)
	 */
	private final int numberOfSystemVars;
	/** Current lower threshold for determining numerical stability */
	private double thresholdLower;
	/** Current upper threshold for determining numerical stability */
	private double thresholdUpper;

	/** Public constructor */
	protected RecursiveLeastSquaresModel(int numberOfSystemVars, double forgettingFactor) {
		this.numberOfSystemVars = numberOfSystemVars;
		this.forgettingFactor = forgettingFactor;
	}

	/**
	 * Get current estimates of coefficients
	 * 
	 * @return Coefficients with constant at index 0 and other system variables
	 *         in same order as specified through input data
	 */
	public double[] getCoefficients() {
		return coefficientsMatrix.getColumn(0);
	}

	/**
	 * Calculate gain vector based on previous covariance matrix and sample
	 * update
	 * 
	 * @param covarianceMatrix
	 * @param sampleUpdate
	 * @return
	 */
	private RealMatrix calculateGainVector(RealMatrix covarianceMatrix, RealMatrix sampleUpdate) {
		final RealMatrix enumerator = covarianceMatrix.multiply(sampleUpdate);
		final RealMatrix denominator = (sampleUpdate.transpose()).multiply(covarianceMatrix)
				.multiply(sampleUpdate);
		final RealMatrix product = enumerator
				.scalarMultiply(1 / (forgettingFactor + denominator.getEntry(0, 0)));

		return product;
	}

	/**
	 * Calculate the prediction error based on the previous coefficients and
	 * current system state compared to new value of dependent variable
	 * 
	 * @param sampleUpdate
	 *            updated system state (vector of new values of independent
	 *            variables)
	 * @param sampleUpdateDepdendentVar
	 *            new value of dependent variable
	 * @param coefficients
	 *            previous coefficients
	 * @return
	 */
	private RealMatrix calculatePredictionError(RealMatrix sampleUpdate,
			double sampleUpdateDepdendentVar, RealMatrix coefficients) {
		final RealMatrix prediction = (sampleUpdate.transpose()).multiply(coefficients);
		final RealMatrix predictionError = prediction.scalarAdd(-sampleUpdateDepdendentVar)
				.scalarMultiply(-1);

		// If prediciton error exceeds thresholds, activate checking covariance
		// matrix for positive definiteness
		if ((prediction.getEntry(0, 0) > thresholdUpper)
				|| (prediction.getEntry(0, 0) < thresholdLower)) {
			checkCovarianceMatrix = true;
		}

		return predictionError;
	}

	/**
	 * Recursive update of covariance matrix
	 * 
	 * @param covarianceMatrix
	 * @param gainVector
	 * @param sampleUpdate
	 * @return
	 */
	private RealMatrix updateCovarianceMatrix(RealMatrix covarianceMatrix, RealMatrix gainVector,
			RealMatrix sampleUpdate) {
		final RealMatrix product = (gainVector.multiply((sampleUpdate.transpose()))
				.multiply(covarianceMatrix));
		return (covarianceMatrix.subtract(product)).scalarMultiply(1 / forgettingFactor);
	}

	/** Initialize model */
	protected void initializeModel() {

		/* Set initial covariance matrix */
		// Identity matrix multiplied with scalar
		final double initialWeightCovarianceMatrix = 100d;
		covarianceMatrix = MatrixUtils.createRealIdentityMatrix(numberOfSystemVars + 1)
				.scalarMultiply(initialWeightCovarianceMatrix);

		/* Coefficients */
		// Set equal to zero
		final double[] coefficients = new double[numberOfSystemVars + 1];
		for (int index = 0; index < coefficients.length; index++) {
			coefficients[index] = 0d;
		}
		coefficientsMatrix = MatrixUtils.createColumnRealMatrix(coefficients);

		/* Set lower and upper thresholds */
		thresholdLower = -5d;
		thresholdUpper = 5d;
		checkCovarianceMatrix = false;
	}

	/**
	 * Perform new iteration
	 * <p>
	 * 1. Calculate prediction error based on current coefficients and sample
	 * update<br>
	 * 2. Update gain term<br>
	 * 3. Update coefficients<br>
	 * 4. Update covariance matrix
	 * <p>
	 * Given issues of numerical instability, the model is from times to times
	 * reinitialized (if prediction errors become to large and covariance matrix
	 * is no longer positive definite)
	 */
	protected void performIteration(double sampleUpdateDepdendentVar, RealMatrix sampleUpdateMatrix,
			int hourOfDay) {

		/* Set lower and upper thresholds */
		if ((sampleUpdateDepdendentVar * (1 + THRESHOLD_TOLERANCE)) > thresholdUpper) {
			thresholdUpper = sampleUpdateDepdendentVar * (1 + THRESHOLD_TOLERANCE);
		} else if ((sampleUpdateDepdendentVar * (1 + THRESHOLD_TOLERANCE)) < thresholdLower) {
			thresholdLower = sampleUpdateDepdendentVar * (1 + THRESHOLD_TOLERANCE);
		}

		/* Calculate prediction error */
		final RealMatrix predictionError = calculatePredictionError(sampleUpdateMatrix,
				sampleUpdateDepdendentVar, coefficientsMatrix);

		/* Update gain term */
		final RealMatrix gain = calculateGainVector(covarianceMatrix, sampleUpdateMatrix);

		/* Gain weighted by prediction error */
		final RealMatrix coefficientDelta = gain.scalarMultiply(predictionError.getEntry(0, 0));

		/* Update coefficients */
		coefficientsMatrix = coefficientsMatrix.add(coefficientDelta);

		/* Update covariance matrix */
		covarianceMatrix = updateCovarianceMatrix(covarianceMatrix, gain, sampleUpdateMatrix);

		/* Reinitialize model if covariance matrix no longer positive definite */
		if (checkCovarianceMatrix) {
			try {
				@SuppressWarnings("unused")
				final RectangularCholeskyDecomposition test = new RectangularCholeskyDecomposition(
						covarianceMatrix);
			} catch (final NonPositiveDefiniteMatrixException e) {
				logger.warn("Covariance matrix no longer positive definite (year " + Date.getYear()
						+ "; hourOfYear " + Date.getHourOfYearFromHourOfDay(hourOfDay) + ")!");
				initializeModel();
			}
		}
	}

}
