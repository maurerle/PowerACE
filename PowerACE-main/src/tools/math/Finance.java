package tools.math;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains static methods from financial mathematics (e.g.
 * calculation of annuity factor)
 *
 * @author Massimo Genoese
 *
 */
public final class Finance {
	public enum Payment {
		/** Payment will take place in advance e.g. first day of the year. */
		IN_ADVANCE,
		/** Payment will take place in arrear e.g. last day of the year. */
		IN_ARREAR;
	}

	private static class AnnuityFactorParameters {

		private int hashCode;
		private final float interest;
		private final float length;
		private final Payment payment;

		AnnuityFactorParameters(float interest, float length, Payment payment) {
			this.interest = interest;
			this.length = length;
			this.payment = payment;
		}

		@Override
		public boolean equals(Object thatObject) {
			if (thatObject == null) {
				return false;
			}

			if (thatObject == this) {
				return true;
			}

			if (!thatObject.getClass().equals(getClass())) {
				return false;
			}

			final AnnuityFactorParameters that = (AnnuityFactorParameters) thatObject;

			return (interest == that.interest) && (length == that.length)
					&& (payment == that.payment);
		}

		@Override
		public int hashCode() {

			if (hashCode == 0) {
				final int prime = 31;
				int result = 1;
				result = (prime * result) + Float.floatToIntBits(interest);
				result = (prime * result) + Float.floatToIntBits(length);
				result = (prime * result) + (payment == null ? 0 : payment.hashCode());
				hashCode = result;
			}
			return hashCode;
		}
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Finance.class.getName());

	/**
	 * Contains the used inverse annuity factors. Size of map might be limited.
	 */
	private static Map<Float, Map<Integer, Float>> discountFactor = new ConcurrentHashMap<>();

	/**
	 * Contains the used inverse annuity factors. Size of map might be limited.
	 */
	private static Map<Payment, Map<AnnuityFactorParameters, Float>> usedInverseAnnuityFactor = new ConcurrentHashMap<>();

	static {
		usedInverseAnnuityFactor.put(Payment.IN_ADVANCE, new ConcurrentHashMap<>());
		usedInverseAnnuityFactor.put(Payment.IN_ARREAR, new ConcurrentHashMap<>());
	}

	public static float getAnnuityFactor(float interest, float length) {
		return (float) ((Math.pow(1 + interest, length) - 1)
				/ (Math.pow(1 + interest, length) * interest));
	}

	/**
	 * Calculate discount factor for a given interest rate and investment
	 * horizon
	 *
	 * @param interest
	 *            Discount rate
	 * @param years
	 *            year in future for that the discount factor is calculated
	 * @return discount factor.
	 */
	public static float getDiscountFactor(float interest, int years) {

		if (!discountFactor.containsKey(interest)) {
			discountFactor.put(interest, new ConcurrentHashMap<>());
		}

		if (!discountFactor.get(interest).containsKey(years)) {
			discountFactor.get(interest).put(years, 1 / (float) Math.pow(1 + interest, years));
		}

		return discountFactor.get(interest).get(years);
	}

	/**
	 * Calculate or retrieve inverse annuity factor for the specified parameters
	 *
	 * @param interest
	 *            Interest rate (coded like 0.XX)
	 * @param length
	 *            Number of periods
	 */
	public static float getInverseAnnuityFactor(float interest, float length, Payment payment) {

		float inverseAnnuityFactorTemp = 0f;
		final AnnuityFactorParameters annuityFactorParameters = new AnnuityFactorParameters(
				interest, length, payment);

		// If inverse annuity factor has already been calculated before, use the
		// stored value
		if (usedInverseAnnuityFactor.get(payment).containsKey(annuityFactorParameters)) {
			inverseAnnuityFactorTemp = usedInverseAnnuityFactor.get(payment)
					.get(annuityFactorParameters);
		}

		// Else calculate inverse annuity factor
		else {

			if (payment == Payment.IN_ADVANCE) {
				inverseAnnuityFactorTemp = (float) ((Math.pow(1 + interest, length - 1) * interest)
						/ (Math.pow(1 + interest, length) - 1));
			} else if (payment == Payment.IN_ARREAR) {
				inverseAnnuityFactorTemp = (float) ((Math.pow(1 + interest, length) * interest)
						/ (Math.pow(1 + interest, length) - 1));
			}
			if (Float.isNaN(inverseAnnuityFactorTemp)) {
				logger.error("NaN! Problem! Set to 0");
				inverseAnnuityFactorTemp = 0;
			}
			// Store the new inverse annuity factor (and limit size of map)
			if (usedInverseAnnuityFactor.get(payment).size() < 100) {
				usedInverseAnnuityFactor.get(payment).put(annuityFactorParameters,
						inverseAnnuityFactorTemp);
			}
		}

		return inverseAnnuityFactorTemp;
	}

	/**
	 * Calculate net present value for a given investment
	 *
	 * @param interest
	 *            Discount rate
	 * @param cashFlow
	 *            Series of undiscounted cash flows
	 * @return Net present value of the investment. The unit of the returned
	 *         value depends on the input data.
	 */
	public static float getNetPresentValue(float interest, List<Float> cashFlow,
			boolean endOfYearPayments) {
		float netPresentValue = 0;
		int addOneIfEndOfYear = 0;
		if (endOfYearPayments) {
			addOneIfEndOfYear = 1;
		}
		for (int yearIndex = 0; yearIndex < cashFlow.size(); yearIndex++) {
			netPresentValue += cashFlow.get(yearIndex)
					/ Math.pow(1 + interest, yearIndex + addOneIfEndOfYear);
		}
		return netPresentValue;
	}

	private Finance() {
	}
}