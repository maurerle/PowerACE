package tools.other;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.PowerMarkets;
import simulations.scheduling.Date;
/**
 * Measure the total runtime.
 */
public final class SpeedTest {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(SpeedTest.class.getName());

	public static void speedtest(LocalDateTime timeForecast) {
		if (Date.getDayOfTotal() == 365) {
			// Calculate length
			final LocalDateTime end = LocalDateTime.now();
			final Duration length = Duration.between(timeForecast, end);
			final long expectedLength = (length.getSeconds()
					* (PowerMarkets.getMultiRunsTotal() * Date.getTotalDays())) / 365;

			// Format output
			final String lengthFormatted = String.format("%d min, %d sec",
					TimeUnit.SECONDS.toMinutes(expectedLength),
					TimeUnit.SECONDS.toSeconds(expectedLength) - TimeUnit.MINUTES
							.toSeconds(TimeUnit.SECONDS.toMinutes(expectedLength)));

			// Write output
			logger.info("Time for 365 tics " + lengthFormatted);
			logger.info("Required Time for " + PowerMarkets.getMultiRunsTotal() + " run(s) "
					+ lengthFormatted);
		}
	}
}