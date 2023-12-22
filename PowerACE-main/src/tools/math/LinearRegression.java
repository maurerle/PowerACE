package tools.math;

import java.util.ArrayList;
import java.util.List;

/**
 * @author
 *
 */
public class LinearRegression {

	public static LinearRegressionValues calcRegression(List<Float> values) {

		int n = 0;
		final double[] x = new double[values.size()];
		final double[] y = new double[values.size()];

		// first pass: read in data, compute xbar and ybar
		double sumx = 0.0, sumy = 0.0;
		for (int index = 0; index < values.size(); index++) {
			x[n] = index;
			y[n] = values.get(index);
			sumx += x[n];
			sumy += y[n];
			n++;
		}
		final double xbar = sumx / n;
		final double ybar = sumy / n;

		// second pass: compute summary statistics
		double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
		for (int i = 0; i < n; i++) {
			xxbar += (x[i] - xbar) * (x[i] - xbar);
			yybar += (y[i] - ybar) * (y[i] - ybar);
			xybar += (x[i] - xbar) * (y[i] - ybar);
		}
		final double beta1 = xybar / xxbar;
		final double beta0 = ybar - (beta1 * xbar);

		// analyze results
		final int df = n - 2;
		double rss = 0.0; // residual sum of squares
		double ssr = 0.0; // regression sum of squares
		final List<Float> residuals = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			final double fit = (beta1 * x[i]) + beta0;
			residuals.add(i, (float) (fit - y[i]));
			rss += (fit - y[i]) * (fit - y[i]);
			ssr += (fit - ybar) * (fit - ybar);
		}
		@SuppressWarnings("unused")
		final double R2 = ssr / yybar;
		final double svar = rss / df;
		final double svar1 = svar / xxbar;
		@SuppressWarnings("unused")
		final double svar0 = (svar / n) + (xbar * xbar * svar1);

		return new LinearRegressionValues((float) beta0, (float) beta1, residuals);

	}

	public static LinearRegressionValues calcRegression(List<Float> x, List<Float> y) {

		final int n = x.size();

		// first pass: read in data, compute xbar and ybar
		double sumx = 0.0, sumy = 0.0;
		for (int index = 0; index < x.size(); index++) {
			sumx += x.get(index);
			sumy += y.get(index);
		}
		final double xbar = sumx / n;
		final double ybar = sumy / n;

		// second pass: compute summary statistics
		double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
		for (int i = 0; i < n; i++) {
			xxbar += (x.get(i) - xbar) * (x.get(i) - xbar);
			yybar += (y.get(i) - ybar) * (y.get(i) - ybar);
			xybar += (x.get(i) - xbar) * (y.get(i) - ybar);
		}
		final double beta1 = xybar / xxbar;
		final double beta0 = ybar - (beta1 * xbar);

		// analyze results
		final int df = n - 2;
		double rss = 0.0; // residual sum of squares
		double ssr = 0.0; // regression sum of squares
		final List<Float> residuals = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			final double fit = (beta1 * x.get(i)) + beta0;
			residuals.add(i, (float) (fit - y.get(i)));
			rss += (fit - y.get(i)) * (fit - y.get(i));
			ssr += (fit - ybar) * (fit - ybar);
		}

		@SuppressWarnings("unused")
		final double R2 = ssr / yybar;

		final double svar = rss / df;
		final double svar1 = svar / xxbar;

		@SuppressWarnings("unused")
		final double svar0 = (svar / n) + (xbar * xbar * svar1);

		return new LinearRegressionValues((float) beta0, (float) beta1, residuals);

	}

	public static void main(String[] args) {

		final List<Float> values = new ArrayList<>();
		values.add(0f);
		values.add(1f);
		values.add(2f);
		values.add(3f);

		final LinearRegressionValues lrv = LinearRegression.calcRegression(values);

		System.out.println(lrv.getBeta0() + ", " + lrv.getBeta1());

	}

}