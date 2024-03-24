package markets.trader.spot.hydro;

import static simulations.scheduling.Date.HOURS_PER_DAY;
import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.storage.PumpStoragePlant;
import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBModel;
import com.gurobi.gurobi.GRBVar;
import markets.bids.Bid.BidType;
import markets.bids.power.DayAheadHourlyBid;
import markets.bids.power.HourlyBidPower;
import markets.operator.spot.MarketCouplingOperator;
import markets.operator.spot.tools.MarginalBid;
import markets.trader.Trader;
import markets.trader.TraderType;
import markets.trader.future.tools.PriceForecastFuture;
import markets.trader.spot.DayAheadTrader;
import markets.trader.spot.supply.tools.AssignPowerPlantsForecast;
import markets.trader.spot.supply.tools.ForecastTypeDayAhead;
import simulations.initialization.Settings;
import simulations.scheduling.Date;
import supply.Generator;
import supply.powerplant.Plant;
import supply.scenarios.ScenarioList;
import tools.logging.Folder;
import tools.logging.LoggerCSV;
import tools.types.FuelType;

public class PumpStorageTrader extends Trader implements DayAheadTrader {

	/**
	 * Share of the total pump/turbine capacity that is available for operation
	 * on the day ahead market
	 */
	private static final float DAY_AHEAD_SHARE_OF_CAPACITY = 1f;
	/** Create Gurobi environment object */
	private static GRBEnv env;

	private static float EPSILON = 0.01f;

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(PumpStorageTrader.class.getName());

	/**
	 * Amount of hours over which the day ahead operation is optimized (rolling
	 * horizon approach). Currently only applicable for price-based operation.
	 * Minimum value: 24 hours
	 */
	public static final int OPTIMIZATION_PERIOD = 3 * Date.HOURS_PER_DAY;

	/**
	 * Minimum required difference between the highest and lowest load during
	 * the optimization period after the day ahead dispatch of the pump storage.
	 */
	private static final float REQUIRED_LOAD_DIFFERENCE = 0;

	private static float[] staticpumpStorageProfile = {1500f, 2200f, 2500f, 2100f, 2000f, 1900f,
			700f, 0f, -1000f, -800f, -900f, -1100f, -1200f, -1100f, -800f, -900f, -800f, -1000f,
			-2000f, -1200f, -900f, -350f, -200f, -1000};
	static {
		try {
			env = new GRBEnv();
		} catch (final GRBException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	public static void dispose() {
		try {
			// dispose environment
			env.dispose();
			env = new GRBEnv();
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Get pump operation for <code>hourOfDay</code> from static pump profile
	 */
	public static float getStaticPumpStorageLoad(int hourOfDay) {
		return staticpumpStorageProfile[hourOfDay];
	}

	/*** Variables defined in the xml file */
	private boolean dynamicPriceForecast;

	/**
	 * set this flag true when residual load is higher when installed flexible
	 * capacity
	 */
	private boolean isExtremeSituation = false;
	private int logIDPumpedStorageDispatch;

	private int logIDPumpedStorageReservoirLevel;
	private boolean logPumpedStorageDispatch;

	private int optimizationPeriod;
	private float[] priceForecast = new float[MarketCouplingOperator.getForecastLengthShort()];
	private final Float priceMaxAsk = null;
	private final Float priceMinSell = null;
	private List<PumpStoragePlant> pumpy = new ArrayList<>();
	private float[] residualLoadForecast = new float[MarketCouplingOperator
			.getForecastLengthShort()];
	private float[] soldCapacities = new float[HOURS_PER_YEAR];
	private float[] summedCapacity = new float[HOURS_PER_DAY];
	private float[] summedOperation = new float[HOURS_PER_DAY];
	private float[] summedOperationDayAhead = new float[HOURS_PER_DAY];

	private float[] summedStorageStatus = new float[HOURS_PER_DAY];

	private float[] totalIncome = new float[HOURS_PER_DAY];

	/*** Variables defined in the xml file */
	private int tradingDayAhead;
	private boolean usePriceBasedOperation;

	private float[] calculateDayAheadPriceForecast(ForecastTypeDayAhead type) {
		float[] forecastPricesAsArray = new float[optimizationPeriod];
		List<Float> forecastPricesAsList = new ArrayList<>();

		try {
			for (int hourOfForecast = 0; hourOfForecast < optimizationPeriod; hourOfForecast++) {
				final int dayOfYear = (hourOfForecast < Date.HOURS_PER_DAY)
						? Date.getDayOfYear()
						: (Date.getDayOfYear() + 1);
				final int hourOfDay = (hourOfForecast < Date.HOURS_PER_DAY)
						? hourOfForecast
						: (hourOfForecast - Date.HOURS_PER_DAY);
				forecastPricesAsList
						.add(PriceForecastFuture.getForwardPriceListCurrentYear().get(marketArea)
								.get(Date.getHourOfYearFromHourOfDay(dayOfYear, hourOfDay)));
			}
			// Convert to array
			for (int hourOfForecast = 0; hourOfForecast < optimizationPeriod; hourOfForecast++) {
				forecastPricesAsArray[hourOfForecast] = forecastPricesAsList.get(hourOfForecast);
			}

		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
		return forecastPricesAsArray;
	}

	/** bids for the EEX are sent to the Auctioneer */
	@Override
	public List<DayAheadHourlyBid> callForBidsDayAheadHourly() {
		if (Date.isFirstDayOfYear()) {
			soldCapacities = new float[HOURS_PER_YEAR];
		}
		isExtremeSituation = false;
		pumpy = marketArea.getPumpStorage().getAvailablePumpers();
		hourlyDayAheadPowerBids.clear();
		if (pumpy.isEmpty()) {
			return hourlyDayAheadPowerBids;
		}
		tradingDayAhead = 4;
		try {
			if (tradingDayAhead == 2) {
				if (usePriceBasedOperation) {
					// Choose one option for optimization:
					// Price-based (1-3)
					// (1) price maker (non-linear, full price regression)
					// (2) price maker (non-linear, adjustment of prices)
					// (3) price taker (linear, adjustment of prices)
					// Load-smoothing (4-5)
					// (4) minimizing max daily load (linear)
					// (5) minimizing squared deviation from avg load
					// (non-linear)

					// If last day of the year is exceeded within forecast
					// period, the forecast
					// period is shortened
					optimizationPeriod = Math.min(
							HOURS_PER_YEAR - (HOURS_PER_DAY * (Date.getDayOfYear() - 1)),
							OPTIMIZATION_PERIOD);
					determineOperationOptimized(3);
				} else {
					optimizationPeriod = 24;
					determineOperation();
				}
			} else if (tradingDayAhead == 3) {
				getDynamicPumpProfile();
			} else if (tradingDayAhead == 4) {
				// optimizer
				determineOperationOptimization();
			}

			// generate Bids
			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				final DayAheadHourlyBid bid = generateHBid(hour);
				if (bid != null) {
					hourlyDayAheadPowerBids.add(bid);
				}
			}

			if (tradingDayAhead == 4) {
				// generate extreme Bids
				hourlyDayAheadPowerBids.addAll(generateExtremeBids());
			}
		} catch (final Exception e) {
			logger.error(getName());
			logger.error(e.getMessage(), e);
			logger.error("Day of the year = " + Date.getDayOfYear());
		}
		return hourlyDayAheadPowerBids;
	}

	/**
	 * Corrects an overflow of the storage by reducing the amount of pumping in
	 * all hours before the overflow
	 */
	private boolean checkOverflowAdd(PumpStoragePlant pumper, int hour, float addVolume) {
		int forecastLength = MarketCouplingOperator.getForecastLengthShort();
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}
		for (; hour < forecastLength; hour++) {
			if ((pumper.getPlannedStatus()[hour] + addVolume) > (1.0001f
					* pumper.getStorageVolume())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if an underflow of the storage occurs considering a given
	 * operation per hour of the optimization period. A certain tolerance (0.01%
	 * of maxStorageLevel) is defined to reach faster convergence.
	 */
	private boolean checkUnderflowReducing(PumpStoragePlant pumper, int hour, float reduce) {
		int forecastLength = MarketCouplingOperator.getForecastLengthShort();
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}
		for (; hour < forecastLength; hour++) {
			if ((pumper.getPlannedStatus()[hour] - reduce) < (-0.0001f
					* pumper.getStorageVolume())) {
				return true;
			}
		}
		return false;
	}

	private void correctOverAndUnderFlows(final PumpStoragePlant pumper) throws Exception {
		int counter = 0;
		// Correct overflows and underflows of the storage
		while (isOverflow(pumper) || isUnderflow(pumper)) {
			if (isOverflow(pumper)) {
				// if (counter < 50) {
				if (counter < 1000) {
					correctOverflow(pumper);
				} else {
					correctOverflowCausedByInflow(pumper);
				}
				setStorageLevel(pumper);
			}
			if (isUnderflow(pumper)) {
				correctUnderflow(pumper);
				setStorageLevel(pumper);
			}
			counter += 1;
			// if (counter > 100) {
			if (counter > 2000) {
				logger.error(marketArea.getInitialsBrackets() + " Pumped Storage Operation for "
						+ pumper.getName() + " could not be solved for day " + Date.getDayOfYear()
						+ " in year " + Date.getYear() + ". Overflow: " + isOverflow(pumper)
						+ ", Underflow: " + isUnderflow(pumper));
				break;
			}
		}
	}

	private void correctOverAndUnderFlowsOptimization(final PumpStoragePlant pumper) {
		int counter = 0;
		try {
			// Correct overflows and underflows of the storage
			while (isOverflow(pumper) || isUnderflow(pumper)) {
				if (isOverflow(pumper)) {
					// if (counter < 50) {
					if (counter < 1000) {
						correctOverflowOptimization(pumper);
					}
					setStorageLevelOptimization(pumper);
				}
				if (isUnderflow(pumper)) {
					correctUnderflowOptimization(pumper);
					setStorageLevelOptimization(pumper);
				}
				counter += 1;
				// if (counter > 100) {
				if (counter > 2000) {
					logger.error(marketArea.getInitialsBrackets() + " Pumped Storage Operation for "
							+ pumper.getName() + " could not be solved for day "
							+ Date.getDayOfYear() + " in year " + Date.getYear() + ". Overflow: "
							+ isOverflow(pumper) + ", Underflow: " + isUnderflow(pumper));
					break;
				}
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Corrects an overflow of the storage by reducing the amount of pumping in
	 * all hours before the overflow
	 */
	private void correctOverflow(PumpStoragePlant pumper) throws Exception {
		int forecastLength = HOURS_PER_DAY;
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}
		// Choose price or load as criterion
		float[] forecast = new float[forecastLength];
		if (usePriceBasedOperation) {
			forecast = priceForecast;
		} else {
			forecast = residualLoadForecast;
		}

		final int hourWithWorstOverflow = findOverflow(pumper);

		// Find hour with min price or load before the overflow
		int hourWithMin = 0;
		for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
			if (forecast[hour] < forecast[hourWithMin]) {
				hourWithMin = hour;
			}
		}

		// Determine scaling factor
		float scalingFactor = 0;
		for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
			if (pumper.getPlannedOperation()[hour] > 0) {
				scalingFactor += forecast[hour] - forecast[hourWithMin];
			}
		}

		// Check if prices or loads in all problematic hours are the same
		boolean allHourlyValuesEqual = false;
		if (scalingFactor == 0) {
			allHourlyValuesEqual = true;

			int hoursWithOverflow = 0;
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (pumper.getPlannedOperation()[hour] > 0) {
					hoursWithOverflow += 1;
				}
			}

			scalingFactor = (pumper.getPlannedStatus()[hourWithWorstOverflow]
					- pumper.getStorageVolume()) / pumper.getChargeEfficiency() / hoursWithOverflow;
		} else {
			scalingFactor = 1 / scalingFactor;
			scalingFactor *= (pumper.getPlannedStatus()[hourWithWorstOverflow]
					- pumper.getStorageVolume()) / pumper.getChargeEfficiency();
		}

		// Correct pump operation
		if (allHourlyValuesEqual) {
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (pumper.getPlannedOperation()[hour] > 0) {
					pumper.setPlannedOperation(hour,
							Math.max(pumper.getPlannedOperation()[hour] - scalingFactor, 0));
				}
			}
		} else {
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (pumper.getPlannedOperation()[hour] > 0) {
					pumper.setPlannedOperation(hour,
							Math.max(pumper.getPlannedOperation()[hour]
									- ((forecast[hour] - forecast[hourWithMin]) * scalingFactor),
									0));
				}
			}
		}
	}

	private void correctOverflowCausedByInflow(PumpStoragePlant pumper) throws Exception {
		final float availableTurbineCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
				* pumper.getGenerationCapacity();
		int forecastLength = MarketCouplingOperator.getForecastLengthShort();
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}
		// Choose price or load as criterion
		float[] forecast = new float[forecastLength];
		if (usePriceBasedOperation) {
			forecast = priceForecast;
		} else {
			forecast = residualLoadForecast;
		}

		final int hourWithWorstOverflow = findOverflow(pumper);

		// Find hour with max price or load before the overflow, in which
		// turbine does not operate at full capacity
		int hourWithMax = 0;

		while (-pumper.getPlannedOperation()[hourWithMax] >= availableTurbineCapacity) {
			hourWithMax += 1;
		}

		for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
			if ((forecast[hour] > forecast[hourWithMax])
					&& (-pumper.getPlannedOperation()[hour] < availableTurbineCapacity)) {
				hourWithMax = hour;
			}
		}

		// Correct turbine operation
		pumper.getPlannedOperation()[hourWithMax] = -Math.min(
				-pumper.getPlannedOperation()[hourWithMax]
						+ ((pumper.getPlannedStatus()[hourWithWorstOverflow]
								- pumper.getStorageVolume()) * pumper.getGenerationEfficiency()),
				availableTurbineCapacity);
	}

	/**
	 * Corrects an overflow of the storage by reducing the amount of pumping in
	 * all hours before the overflow
	 */
	private void correctOverflowOptimization(PumpStoragePlant pumper) throws Exception {
		try {
			int forecastLength = HOURS_PER_DAY;
			if (Date.isLastDayOfYear()) {
				forecastLength = 24;
			}
			// Choose price or load as criterion
			float[] forecast = new float[forecastLength];
			if (usePriceBasedOperation) {
				forecast = priceForecast;
			} else {
				forecast = residualLoadForecast;
			}

			final int hourWithWorstOverflow = findOverflow(pumper);

			// Find hour with min price or load before the overflow
			int hourWithMin = 0;
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (forecast[hour] < forecast[hourWithMin]) {
					hourWithMin = hour;
				}
			}

			// Determine scaling factor
			float scalingFactor = 0;
			for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
				if (pumper.getPlannedOperation()[hour] > 0) {
					scalingFactor += forecast[hour] - forecast[hourWithMin];
				}
			}

			// Check if prices or loads in all problematic hours are the same
			boolean allHourlyValuesEqual = false;
			if (scalingFactor == 0) {
				allHourlyValuesEqual = true;

				int hoursWithOverflow = 0;
				for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
					if (pumper.getPlannedOperation()[hour] > 0) {
						hoursWithOverflow += 1;
					}
				}

				scalingFactor = (pumper.getPlannedStatus()[hourWithWorstOverflow]
						- pumper.getStorageVolume()) / pumper.getEfficiency() / hoursWithOverflow;
			} else {
				scalingFactor = 1 / scalingFactor;
				scalingFactor *= (pumper.getPlannedStatus()[hourWithWorstOverflow]
						- pumper.getStorageVolume()) / pumper.getEfficiency();
			}

			// Correct pump operation
			if (allHourlyValuesEqual) {
				for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
					if (pumper.getPlannedOperation()[hour] > 0) {
						pumper.setPlannedOperation(hour,
								Math.max(pumper.getPlannedOperation()[hour] - scalingFactor, 0));
					}
				}
			} else {
				for (int hour = 0; hour <= hourWithWorstOverflow; hour++) {
					if (pumper.getPlannedOperation()[hour] > 0) {
						pumper.setPlannedOperation(hour, Math.max(pumper.getPlannedOperation()[hour]
								- ((forecast[hour] - forecast[hourWithMin]) * scalingFactor), 0));
					}
				}
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void correctStorageVolume(int hour, float volume) {
		// Sort pumped storage power plants in the order that the most
		// inffective comes first
		final List<PumpStoragePlant> pumpers = new ArrayList<>(pumpy);
		Collections.sort(pumpers);

		if (volume > 0) {
			// positive volumes means reduction of pumping
			for (final PumpStoragePlant pumper : pumpers) {
				final float operatingVolume = pumper.getPlannedOperation()[hour];
				if (operatingVolume <= 0) {
					continue;
				}
				final float reducingVolume = Math.min(operatingVolume, volume);
				// If there is an underrun at anytime don't change
				if (checkUnderflowReducing(pumper, hour, reducingVolume)) {
					continue;
				}
				// The reduced pumped volume will result in a lower storage
				// volume
				pumper.setStorageStatus(pumper.getStorageStatus() - reducingVolume);
				volume -= reducingVolume;
				if (volume <= 0) {
					break;
				}
			}
			if (volume > EPSILON) {
				logger.error(
						marketArea.getInitialsBrackets() + " Pumper devation could not be solved");
			}
		} else {
			// negative values mean reduction of turbinine
			for (final PumpStoragePlant pumper : pumpers) {
				final float operatingVolume = pumper.getPlannedOperation()[hour];
				if (operatingVolume >= 0) {
					continue;
				}
				final float addVolume = -Math.max(operatingVolume, volume);
				// If there is an Overflow at anytime don't change
				if (checkOverflowAdd(pumper, hour, addVolume)) {
					continue;
				}
				// The reduced turbine volume will result in a higher volume
				pumper.setStorageStatus(pumper.getStorageStatus() - addVolume);
				volume += addVolume;
				if (volume >= 0) {
					break;
				}
			}
			if (volume < -EPSILON) {
				logger.error(marketArea.getInitialsBrackets()
						+ "Turbining deviation could not be solved");
			}

		}
	}

	/**
	 * Corrects an underflow of the storage by reducing the amount of turbining
	 * in all hours before the overflow
	 */
	private void correctUnderflow(PumpStoragePlant pumper) throws Exception {
		int forecastLength = HOURS_PER_DAY;
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}
		// Choose price or load as criterion
		float[] forecast = new float[forecastLength];
		if (usePriceBasedOperation) {
			forecast = priceForecast;
		} else {
			forecast = residualLoadForecast;
		}

		final int hourWithWorstUnderflow = findUnderflow(pumper);

		// Find hour with max price or load before the underflow
		int hourWithMax = 0;
		for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
			if (forecast[hour] > forecast[hourWithMax]) {
				hourWithMax = hour;
			}
		}

		// Determine scaling factor
		float scalingFactor = 0;
		for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
			// In case of negaitve residual load
			if (forecast[hour] < 0) {
				pumper.setPlannedOperation(hour, pumper.getPumpCapacity());
			} else if (pumper.getPlannedOperation()[hour] < 0) {
				scalingFactor += forecast[hourWithMax] - forecast[hour];
			}
		}

		// Check if prices or loads in all problematic hours are the same
		boolean allHourlyValuesEqual = false;
		if (scalingFactor == 0) {
			allHourlyValuesEqual = true;

			int hoursWithUnderflow = 0;
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (pumper.getPlannedOperation()[hour] < 0) {
					hoursWithUnderflow += 1;
				}
			}

			scalingFactor = (pumper.getPlannedStatus()[hourWithWorstUnderflow]
					* pumper.getGenerationEfficiency()) / hoursWithUnderflow;
		} else {
			scalingFactor = 1 / scalingFactor;
			scalingFactor *= (pumper.getPlannedStatus()[hourWithWorstUnderflow]
					* pumper.getGenerationEfficiency());
		}

		// Correct turbine operation
		if (allHourlyValuesEqual) {
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (pumper.getPlannedOperation()[hour] < 0) {
					pumper.setPlannedOperation(hour,
							Math.min(pumper.getPlannedOperation()[hour] - scalingFactor, 0));
				}
			}
		} else {
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (pumper.getPlannedOperation()[hour] < 0) {
					pumper.setPlannedOperation(hour,
							Math.min(pumper.getPlannedOperation()[hour]
									- ((forecast[hourWithMax] - forecast[hour]) * scalingFactor),
									0));
				}
			}
		}
	}

	/**
	 * Corrects an underflow of the storage by reducing the amount of turbining
	 * in all hours before the overflow
	 */
	private void correctUnderflowOptimization(PumpStoragePlant pumper) throws Exception {
		int forecastLength = HOURS_PER_DAY;
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}
		// Choose price or load as criterion
		float[] forecast = new float[forecastLength];
		if (usePriceBasedOperation) {
			forecast = priceForecast;
		} else {
			forecast = residualLoadForecast;
		}

		final int hourWithWorstUnderflow = findUnderflow(pumper);

		// Find hour with max price or load before the underflow
		int hourWithMax = 0;
		for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
			if (forecast[hour] > forecast[hourWithMax]) {
				hourWithMax = hour;
			}
		}

		// Determine scaling factor
		float scalingFactor = 0;
		for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
			// In case of negaitve residual load
			if (forecast[hour] < 0) {
				pumper.setPlannedOperation(hour, pumper.getPumpCapacity());
			} else if (pumper.getPlannedOperation()[hour] < 0) {
				scalingFactor += forecast[hourWithMax] - forecast[hour];
			}
		}

		// Check if prices or loads in all problematic hours are the same
		boolean allHourlyValuesEqual = false;
		if (scalingFactor == 0) {
			allHourlyValuesEqual = true;

			int hoursWithUnderflow = 0;
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (pumper.getPlannedOperation()[hour] < 0) {
					hoursWithUnderflow += 1;
				}
			}

			scalingFactor = (pumper.getPlannedStatus()[hourWithWorstUnderflow])
					/ hoursWithUnderflow;
		} else {
			scalingFactor = 1 / scalingFactor;
			scalingFactor *= (pumper.getPlannedStatus()[hourWithWorstUnderflow]);
		}

		// Correct turbine operation
		if (allHourlyValuesEqual) {
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (pumper.getPlannedOperation()[hour] < 0) {
					pumper.setPlannedOperation(hour,
							Math.min(pumper.getPlannedOperation()[hour] - scalingFactor, 0));
				}
			}
		} else {
			for (int hour = 0; hour <= hourWithWorstUnderflow; hour++) {
				if (pumper.getPlannedOperation()[hour] < 0) {
					pumper.setPlannedOperation(hour,
							Math.min(pumper.getPlannedOperation()[hour]
									- ((forecast[hourWithMax] - forecast[hour]) * scalingFactor),
									0));
				}
			}
		}
	}

	/**
	 * Determine whether the current bid (plantIndex) is in the current
	 * hourOfDay the marginal bid. If it is save the marginal bid in the
	 * corresponding data object.
	 */
	private void determineMarginalBid(int hourOfDay, float marketClearingPrice,
			HourlyBidPower bidPoint) {

		// Set actual marginal bid which is determined by comparing
		// the market clearing price and bid price
		final float priceDifference = Math
				.abs(Math.round((bidPoint.getPrice() - marketClearingPrice) * 100)) / 100f;
		if (priceDifference <= EPSILON) {
			// Create new instance of MarginalBid
			final MarginalBid marginalBid = new MarginalBid(bidPoint);
			// Set in results object
			marketArea.getElectricityResultsDayAhead().setMarginalBidHourOfDay(hourOfDay,
					marginalBid);
		}
	}

	/**
	 * Determines the heuristically optimized operation of all pumped storage
	 * plants using the objective of maximizing the profit or smoothing the load
	 */
	private void determineOperation() throws Exception {
		Arrays.fill(residualLoadForecast, 0f);
		Arrays.fill(summedOperation, 0f);
		Arrays.fill(summedCapacity, 0f);
		Arrays.fill(summedStorageStatus, 0f);
		Arrays.fill(totalIncome, 0f);
		final List<Plant> powerPlants = new ArrayList<>();
		boolean priceForecastUpdateRequired = true;
		final float[] differencesSummedOperation = new float[MarketCouplingOperator
				.getForecastLengthShort()];
		Arrays.fill(differencesSummedOperation, 0f);

		if (usePriceBasedOperation) {
			// Get available power plants
			// Test case
			for (final Generator generator : marketArea.getGenerators()) {
				for (final Plant plant : generator.getAvailablePlants()) {
					powerPlants.add(plant);
				}
			}

			// Create one price forecast that is used for all pumped storage
			// plants
			if (!dynamicPriceForecast) {
				priceForecast = calculateDayAheadPriceForecast(
						Settings.getDayAheadPriceForecastType());
			}
		}

		for (final PumpStoragePlant pumper : pumpy) {
			pumper.resetPlannedOperation();
			pumper.resetOperation();

			// Create price or load forecast for the optimization period
			if (usePriceBasedOperation && dynamicPriceForecast && priceForecastUpdateRequired) {

				Arrays.fill(differencesSummedOperation, 0f);
				priceForecast = getPriceForecastHeuristic(powerPlants);
			} else if (!usePriceBasedOperation) {
				residualLoadForecast = getLoadForecast();
			}

			// Determine preliminary operation and update storage level
			if (usePriceBasedOperation) {
				determinePrelimOperationPriceBased(pumper);
			} else {
				determinePrelimOperationLoadSmoothing(pumper);
			}
			setStorageLevel(pumper);

			correctOverAndUnderFlows(pumper);

			pumper.setStorageStatus(pumper.getPlannedStatus()[23]);

			/** turn operation into purchase array **/
			pumper.setPlanIntoOperation();

			/** Sum up data for all the plants */
			// Summed operation is required for the whole optimization period,
			// because it is used within the load forecast
			for (int hour = 0; hour < MarketCouplingOperator.getForecastLengthShort(); hour++) {
				differencesSummedOperation[hour] += pumper.getPlannedOperation()[hour];
				summedOperation[hour] += pumper.getPlannedOperation()[hour];
			}

			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				summedCapacity[hour] += DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getAvailableCapacity();
				summedStorageStatus[hour] += pumper.getPlannedStatus()[hour];

				// Prepare data for logging of pumped storage dispatch
				pumper.setLongOperation(Date.getHourOfYearFromHourOfDay(hour),
						pumper.getOperation()[hour]);
				pumper.setLongStorageStatus(Date.getHourOfYearFromHourOfDay(hour),
						pumper.getPlannedStatus()[hour]);
			}

			// Determine whether update of price forecast is required
			float posMaxDeviation = Float.MIN_VALUE;
			float negMaxDeviation = Float.MAX_VALUE;
			for (final float i : differencesSummedOperation) {
				posMaxDeviation = Math.max(posMaxDeviation, i);
			}
			for (final float i : differencesSummedOperation) {
				negMaxDeviation = Math.min(negMaxDeviation, i);
			}

			if ((posMaxDeviation > 500f) || (negMaxDeviation < -500f)) {
				priceForecastUpdateRequired = true;
			} else {
				priceForecastUpdateRequired = false;
			}
		}
	}

	/**
	 * Determines the heuristically optimized operation of all pumped storage
	 * plants using the objective of maximizing the profit or smoothing the load
	 */
	private void determineOperationOptimization() {
		Arrays.fill(summedOperation, 0f);
		Arrays.fill(residualLoadForecast, 0f);

		try {

			// https://ieeexplore.ieee.org/abstract/document/6254793
			// Positive = production
			// Negative = consumption
			// Create Gurobi model object
			final GRBModel model = new GRBModel(env);

			/* Objective function */
			final GRBLinExpr objective = new GRBLinExpr();

			// Enables (1) or disables (0) console logging.
			int LogToConsole;
			LogToConsole = 0;
			model.getEnv().set(GRB.IntParam.LogToConsole, LogToConsole);

			/* Define solving method */
			model.set(GRB.IntParam.Method, GRB.METHOD_DUAL);

			// production smaller than available storage capacity

			// Production/consuption smaller than installed capacity

			// Set Gurobi model parameters
			final GRBVar[][] operation = new GRBVar[pumpy.size()][];
			final GRBVar[][] storageLevel = new GRBVar[pumpy.size()][];
			final boolean priceBasedOptimization = false;
			if (priceBasedOptimization) {
				final List<Plant> powerPlants = new ArrayList<>();
				for (final Generator generator : marketArea.getGenerators()) {
					for (final Plant plant : generator.getAvailablePlants()) {
						powerPlants.add(plant);
					}
				}

				if (dynamicPriceForecast) {
					priceForecast = calculateDayAheadPriceForecast(ForecastTypeDayAhead.HEURISTIC);
				} else {
					priceForecast = calculateDayAheadPriceForecast(
							Settings.getDayAheadPriceForecastType());
				}

				for (int index = 0; index < pumpy.size(); index++) {
					final PumpStoragePlant pumper = pumpy.get(index);
					pumper.resetPlannedOperation();
					pumper.resetOperation();
					operation[index] = new GRBVar[MarketCouplingOperator.getForecastLengthShort()];
					storageLevel[index] = new GRBVar[MarketCouplingOperator
							.getForecastLengthShort()];

					// - Add variables to model

					// Add operation to be optimized and storage level
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {
						final float availableTurbineCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
								* pumper.getGenerationCapacity();
						final float availablePumpCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
								* pumper.getPumpCapacity();

						// Production/consumption smaller than installed
						// capacity
						final String variableNameOperation = "operation_" + pumper.getName()
								+ "_id_" + pumper.getUnitID() + "_hour_" + hourOfDay
								+ "_TurbineCapacity_" + availableTurbineCapacity + "_pumpCapacity_"
								+ availablePumpCapacity;
						operation[index][hourOfDay] = model.addVar(-availablePumpCapacity,
								availableTurbineCapacity, 1.0, GRB.CONTINUOUS,
								variableNameOperation);

						// Add storage level
						final String variableNameStorageLevel = "storagevolume_" + pumper.getName()
								+ "_id_" + pumper.getUnitID() + "_hour_" + hourOfDay
								+ "_volume_max_" + pumper.getStorageVolume();
						storageLevel[index][hourOfDay] = model.addVar(0, pumper.getStorageVolume(),
								1.0, GRB.CONTINUOUS, variableNameStorageLevel);

						// Minimizing deviation
						// Maximizing profit
						// add to objective function per pumped storage plant:
						// quantity(production of psp) * price
						objective.addTerm(priceForecast[hourOfDay], operation[index][hourOfDay]);

					}

					// Needed for constraints
					final GRBLinExpr storageLevelChange = new GRBLinExpr();

					// calculate Storage level
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {
						if (hourOfDay == 0) {
							storageLevelChange.addConstant(pumper.getStorageStatus());
						} else {
							storageLevelChange.addTerm(1, storageLevel[index][hourOfDay - 1]);
						}
						// change of storage level must be according to
						// operation
						storageLevelChange.addTerm(-1, operation[index][hourOfDay]);
						storageLevelChange.addTerm(-1, storageLevel[index][hourOfDay]);

						model.addConstr(storageLevelChange, GRB.EQUAL, 0,
								"Storage_level_" + pumper.getName() + "_ID_" + pumper.getUnitID()
										+ "_hour_" + hourOfDay);
						storageLevelChange.clear();

					}

				}
				model.setObjective(objective, GRB.MAXIMIZE);
			} else {
				// Get load forecast for the optimization period and convert to
				// list.
				residualLoadForecast = getLoadForecast();
				final float avgLoadBeforePumpStorageDispatch = getAvgLoadBeforePumpStorageDispatch();
				// Deviation level this should be minimal and just operatend
				// until zero deviation
				final List<GRBLinExpr> deviationLevel = new ArrayList<>();

				for (int index = 0; index < pumpy.size(); index++) {
					final PumpStoragePlant pumper = pumpy.get(index);
					pumper.resetPlannedOperation();
					pumper.resetOperation();
					operation[index] = new GRBVar[MarketCouplingOperator.getForecastLengthShort()];
					storageLevel[index] = new GRBVar[MarketCouplingOperator
							.getForecastLengthShort()];
					// ********************
					// - Add variables to model
					// ***********************
					// Add operation to be optimized and storage level
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {
						final float availableTurbineCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
								* pumper.getGenerationCapacity();
						final float availablePumpCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
								* pumper.getPumpCapacity();

						// Production/consumption smaller than installed
						// capacity
						// Pumping = negative values
						// Turbining = positive values
						final String variableNameOperation = "operation_" + pumper.getName()
								+ "_id_" + pumper.getUnitID() + "_hour_" + hourOfDay
								+ "_TurbineCapacity_" + availableTurbineCapacity + "_pumpCapacity_"
								+ availablePumpCapacity;
						operation[index][hourOfDay] = model.addVar(-availablePumpCapacity,
								availableTurbineCapacity, 1.0, GRB.CONTINUOUS,
								variableNameOperation);
					}

					// Add storage level
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {
						final String variableNameStorageLevel = "storagevolume_" + pumper.getName()
								+ "_id_" + pumper.getUnitID() + "_hour_" + hourOfDay
								+ "_volume_max_" + pumper.getStorageVolume();
						storageLevel[index][hourOfDay] = model.addVar(0, pumper.getStorageVolume(),
								1.0, GRB.CONTINUOUS, variableNameStorageLevel);
					}

					// ***********************
					// Add contraints
					// ***********************
					// Deviation level this should be minimal and just operatend
					// until zero deviation
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {

						final float deviation = residualLoadForecast[hourOfDay]
								- avgLoadBeforePumpStorageDispatch;
						if (deviation >= 0) {
							if (index == 0) {
								deviationLevel.add(new GRBLinExpr());
								deviationLevel.get(hourOfDay).addConstant(deviation);
							}
							deviationLevel.get(hourOfDay).addTerm(-1, operation[index][hourOfDay]);
						} else {
							if (index == 0) {
								deviationLevel.add(new GRBLinExpr());
								deviationLevel.get(hourOfDay).addConstant(-deviation);
							}
							deviationLevel.get(hourOfDay).addTerm(1, operation[index][hourOfDay]);
						}
					}

					// No pumping and turbining at the same time with more than
					// one powerplant is possible
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {

						final float deviation = residualLoadForecast[hourOfDay]
								- avgLoadBeforePumpStorageDispatch;
						final GRBLinExpr operationDirection = new GRBLinExpr();
						if (deviation >= 0) {
							// Turbining
							operationDirection.addTerm(1, operation[index][hourOfDay]);
							model.addConstr(operationDirection, GRB.GREATER_EQUAL, 0,
									"Deviation_hour_" + hourOfDay + "_powerplant_" + index);
						} else {
							// Pumping
							operationDirection.addTerm(1, operation[index][hourOfDay]);
							model.addConstr(operationDirection, GRB.LESS_EQUAL, 0,
									"Deviation_hour_" + hourOfDay + "_powerplant_" + index);
						}
					}

					// Needed for constraints
					final GRBLinExpr storageLevelChange = new GRBLinExpr();

					// calculate Storage level
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {
						// change of storage level must be according to
						// operation
						// Level_t=Level_(t-1)-Operation_t
						storageLevelChange.addTerm(1, operation[index][hourOfDay]);
						storageLevelChange.addTerm(1, storageLevel[index][hourOfDay]);
						if (hourOfDay == 0) {
							storageLevelChange.addConstant(-pumper.getStorageStatus());
						} else {
							storageLevelChange.addTerm(-1, storageLevel[index][hourOfDay - 1]);
						}

						model.addConstr(storageLevelChange, GRB.EQUAL, 0,
								"Storage_level_" + pumper.getName() + "_ID_" + pumper.getUnitID()
										+ "_hour_" + hourOfDay);
						storageLevelChange.clear();
					}

					// ***************
					// Objective function
					/// *****************

					// Minimizing deviation
					// add to objective function per pumped storage plant:
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {
						final float deviation = residualLoadForecast[hourOfDay]
								- avgLoadBeforePumpStorageDispatch;
						if (residualLoadForecast[hourOfDay] > 0) {
							if (index == 0) {
								objective.addConstant(deviation);
							}
							if (deviation > 0) {
								// Positive devition: turbining
								objective.addTerm(-1, operation[index][hourOfDay]);
							} else if (deviation < 0) {
								// negative deviation: pumping
								objective.addTerm(1, operation[index][hourOfDay]);
							} else {
								// Only pumping
								objective.addTerm(3000, operation[index][hourOfDay]);
							}
						}
					}
					for (int hourOfDay = 0; hourOfDay < MarketCouplingOperator
							.getForecastLengthShort(); hourOfDay++) {
						// Deviation
						model.addConstr(deviationLevel.get(hourOfDay), GRB.GREATER_EQUAL, 0,
								"Deviation_hour_" + hourOfDay);
					}
					model.setObjective(objective, GRB.MINIMIZE);
				}
				/* Solve model */
				model.optimize();

				if ((model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL)
						|| Date.isFirstDayOfYear()) {
					if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
						logger.error(
								"Pumped Storage operation is not optimal! Please check the bid lists for Bugs.");
					}
					// Create log folder
					final String path = Settings.getLogPathName(marketArea.getIdentityAndNameLong(),
							Folder.HYDROPOWER);

					model.write(path + File.separator + marketArea.getInitials()
							+ "_PumpedStorageGurobi_Day" + Date.getDayOfYear() + ".lp");
				}

				/* Results */
				for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
					for (int index = 0; index < pumpy.size(); index++) {
						final float operationPlant = -(float) model.get(GRB.DoubleAttr.X,
								operation)[index][hourOfDay];

						pumpy.get(index).setPlannedOperation(hourOfDay, operationPlant);

					}
				}
				// Dispose everything
				model.dispose();
				// Operation in case of negative prices or high values
				for (final PumpStoragePlant pumper : pumpy) {
					setLevelsAndStatus(pumper);
				}
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}

	}

	/**
	 * Determines the optimized operation of all pumped storage plants using the
	 * objective of maximizing the profit or smoothing the load
	 */
	private void determineOperationOptimized(int optimizationMode) {
		Arrays.fill(residualLoadForecast, 0f);
		Arrays.fill(summedOperation, 0f);
		Arrays.fill(summedCapacity, 0f);
		Arrays.fill(summedStorageStatus, 0f);
		Arrays.fill(totalIncome, 0f);

		boolean priceForecastUpdateRequired = true;
		final float[] differencesSummedOperation = new float[optimizationPeriod];
		Arrays.fill(differencesSummedOperation, 0f);

		for (final PumpStoragePlant pumper : pumpy) {
			Arrays.fill(pumper.getPlannedOperation(), 0f);
			Arrays.fill(pumper.getOperation(), 0f);

			if (priceForecastUpdateRequired || !Date.isFirstYear()) {
				Arrays.fill(differencesSummedOperation, 0f);
				if (dynamicPriceForecast) {
					priceForecast = calculateDayAheadPriceForecast(ForecastTypeDayAhead.HEURISTIC);
				} else {
					priceForecast = calculateDayAheadPriceForecast(
							Settings.getDayAheadPriceForecastType());
				}
			}

			optimizeOperation(optimizationMode, pumper);

			/** turn operation into purchase array **/
			System.arraycopy(pumper.getPlannedOperation(), 0, pumper.getOperation(), 0,
					HOURS_PER_DAY);

			/** Sum up data for all the plants */
			// Summed operation is required for the whole optimization period,
			// because it is used within the load forecast
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				differencesSummedOperation[hour] += pumper.getPlannedOperation()[hour];
				summedOperation[hour] += pumper.getPlannedOperation()[hour];
			}

			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				summedCapacity[hour] += DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getAvailableCapacity();
				summedStorageStatus[hour] += pumper.getPlannedStatus()[hour];

				// Prepare data for logging of pumped storage dispatch
				pumper.setLongOperation(Date.getHourOfYearFromHourOfDay(hour),
						pumper.getOperation()[hour]);
				pumper.setLongStorageStatus(Date.getHourOfYearFromHourOfDay(hour),
						pumper.getPlannedStatus()[hour]);
			}

			// Determine whether update of price forecast is required
			float posMaxDeviation = Float.MIN_VALUE;
			float negMaxDeviation = Float.MAX_VALUE;
			for (final float i : differencesSummedOperation) {
				posMaxDeviation = Math.max(posMaxDeviation, i);
			}
			for (final float i : differencesSummedOperation) {
				negMaxDeviation = Math.min(negMaxDeviation, i);
			}

			if ((posMaxDeviation > 500f) || (negMaxDeviation < -500f)) {
				priceForecastUpdateRequired = true;
			} else {
				priceForecastUpdateRequired = false;
			}
		}

	}

	/**
	 * Determines a preliminary operation of the storage for all hours of the
	 * optimization period. General idea: In hours with loads above the average
	 * load, the load should be reduced to the average load. The other way round
	 * for hours with loads under the average load. Restriction is the maximum
	 * pumping and turbining capacity of the storage. Loads within a certain
	 * limit from the average load are not effected and remain the same.
	 * Constraints with regard to overflows and underflows of the storage are
	 * not being considered in this method.
	 */
	private void determinePrelimOperationLoadSmoothing(PumpStoragePlant pumper) {
		final float avgLoadBeforePumpStorageDispatch = getAvgLoadBeforePumpStorageDispatch();
		final float availableTurbineCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
				* pumper.getGenerationCapacity();
		final float availablePumpCapacity = DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getPumpCapacity();
		final float loadLimitTurbine = avgLoadBeforePumpStorageDispatch
				+ (REQUIRED_LOAD_DIFFERENCE / 2);
		final float loadLimitPump = avgLoadBeforePumpStorageDispatch
				- (REQUIRED_LOAD_DIFFERENCE / 2);
		int forecastLength = MarketCouplingOperator.getForecastLengthShort();
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}

		for (int hour = 0; hour < forecastLength; hour++) {
			// Negative residual load, than only pumping
			if (residualLoadForecast[hour] < 0) {
				pumper.setPlannedOperation(hour,
						Math.min(-residualLoadForecast[hour], availablePumpCapacity));
			} else if (residualLoadForecast[hour] > loadLimitTurbine) {
				pumper.setPlannedOperation(hour,
						-Math.min(residualLoadForecast[hour] - loadLimitTurbine,
								availableTurbineCapacity));
			} else if (residualLoadForecast[hour] < loadLimitPump) {
				pumper.setPlannedOperation(hour, Math
						.min(loadLimitPump - residualLoadForecast[hour], availablePumpCapacity));
			} else {
				pumper.setPlannedOperation(hour, 0f);
			}
		}
	}

	/**
	 * Determines a preliminary operation of the storage for all hours of the
	 * optimization period. General idea: Price limits for a profitable
	 * operation are determined. In hours with high prices turbine operation at
	 * maximum capacity, in hours with low prices pump operation at maximum
	 * capacity. Constraints with regard to overflows and underflows of the
	 * storage are not being considered in this method.
	 */
	private void determinePrelimOperationPriceBased(PumpStoragePlant pumper) {
		final float availableTurbineCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
				* pumper.getGenerationCapacity();
		final float availablePumpCapacity = DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getPumpCapacity();

		// Initialize price limits. If even for the highest and lowest price of
		// the optimization period no profitable operation is possible, then
		// price limits must be chosen very high / low to avoid operation of the
		// plant.
		float priceLimitPump = -500000f;
		float priceLimitTurbine = 500000f;

		// Sort price forecast ascending
		final float[] sortedPriceForecast = Arrays.copyOf(priceForecast, priceForecast.length);
		Arrays.sort(sortedPriceForecast);

		// Get price limits for profitable operation
		for (int i = 0; i < Math.floorDiv(sortedPriceForecast.length, 2); i++) {
			// Check price pair for profitability
			if ((sortedPriceForecast[i]
					/ sortedPriceForecast[sortedPriceForecast.length - 1 - i]) <= pumper
							.getEfficiency()) {
				// Update price limits
				priceLimitPump = sortedPriceForecast[i];
				priceLimitTurbine = sortedPriceForecast[(sortedPriceForecast.length - 1 - i)];
			} else {
				break;
			}
		}

		for (int hour = 0; hour < priceForecast.length; hour++) {
			if (priceForecast[hour] >= priceLimitTurbine) {
				pumper.setPlannedOperation(hour, -availableTurbineCapacity);
			} else if (priceForecast[hour] <= priceLimitPump) {
				pumper.setPlannedOperation(hour, availablePumpCapacity);
			} else {
				pumper.setPlannedOperation(hour, 0f);
			}
		}
	}

	@Override
	public void evaluateResultsDayAhead() {
		// add produced electricity by PumpStorage to SupplyBidder
		for (final DayAheadHourlyBid hourlyBid : hourlyDayAheadPowerBids) {
			for (final HourlyBidPower bidPoint : hourlyBid.getBidPoints()) {
				final float volume = bidPoint.getBidType() == BidType.ASK
						? -bidPoint.getVolumeAccepted()
						: bidPoint.getVolumeAccepted();
				marketArea.getElectricityProduction()
						.addElectricityPumpedStorage(bidPoint.getHour(), volume);
				soldCapacities[(HOURS_PER_DAY * (Date.getDayOfYear() - 1))
						+ bidPoint.getHour()] += bidPoint.getBidType() == BidType.ASK
								? bidPoint.getVolumeAccepted()
								: -bidPoint.getVolumeAccepted();
			}
		}

		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			final float plannedOperation = summedOperation[hour];
			final float soldOperation = soldCapacities[(HOURS_PER_DAY * (Date.getDayOfYear() - 1))
					+ hour];
			final float operationDeviation = plannedOperation - soldOperation;

			if (Math.abs(operationDeviation) > EPSILON) {
				// In case of pumping (positve planned operation) deviation must
				// be positive
				// In case of turbining (negative planned operation)deviation
				// must be negative
				// Something in between should not happen

				if (((plannedOperation > 0) && (operationDeviation > EPSILON))
						|| ((plannedOperation < 0) && (operationDeviation < EPSILON))) {
					correctStorageVolume(hour, operationDeviation);
				} else {
					logger.error(marketArea.getInitialsBrackets()
							+ "Pumped storage plant operation is higher than planned operation. This should not happen. However, extreme situation? "
							+ isExtremeSituation);

				}
			}

		}
		// determine marginal bid
		for (final DayAheadHourlyBid dayAheadHourlyBid : hourlyDayAheadPowerBids) {
			final int hourOfDay = dayAheadHourlyBid.getHour();
			final float marketClearingPrice = hourlyDayAheadPowerBids.get(hourOfDay)
					.getMarketClearingPrice();

			for (final HourlyBidPower bidPoint : dayAheadHourlyBid.getBidPoints()) {
				// If bid is (partially) accepted
				if (Math.abs(bidPoint.getVolumeAccepted()) != 0) {
					/* Determine marginal bid */
					determineMarginalBid(hourOfDay, marketClearingPrice, bidPoint);
				}
			}
		}
	}

	/**
	 * Returns the hour of the optimization period with the worst overflow of
	 * the storage
	 */
	private int findOverflow(PumpStoragePlant pumper) throws Exception {
		int maxOverflow = 0;
		for (int hour = 0; hour < pumper.getPlannedStatus().length; hour++) {
			if (pumper.getPlannedStatus()[hour] >= pumper.getPlannedStatus()[maxOverflow]) {
				maxOverflow = hour;
			}
		}
		return maxOverflow;
	}

	/**
	 * Returns the hour of the optimization period with the worst underflow of
	 * the storage
	 */
	private int findUnderflow(PumpStoragePlant pumper) throws Exception {
		int maxUnderflow = 0;
		for (int hour = 0; hour < pumper.getPlannedStatus().length; hour++) {
			if (pumper.getPlannedStatus()[hour] <= pumper.getPlannedStatus()[maxUnderflow]) {
				maxUnderflow = hour;
			}
		}
		return maxUnderflow;
	}

	/** Creates an hourly bid */
	private List<DayAheadHourlyBid> generateExtremeBids() {
		final List<DayAheadHourlyBid> bids = new ArrayList<>();

		// Operation in case of negative prices or high values
		for (final PumpStoragePlant pumper : pumpy) {
			// find extremas
			// minimum of Storage volume can be used continously

			float possibleContinousTurbineCapacity = Float.POSITIVE_INFINITY;
			float possibleContinousPumpCapacity = Float.POSITIVE_INFINITY;
			for (final Float storageVolume : pumper.getPlannedStatus()) {
				if (storageVolume < possibleContinousTurbineCapacity) {
					possibleContinousTurbineCapacity = storageVolume;
				}
				// difference between total volume and max storage volume can be
				// used continously
				if ((pumper.getStorageVolume() - storageVolume) < possibleContinousPumpCapacity) {
					possibleContinousPumpCapacity = pumper.getStorageVolume() - storageVolume;
				}
			}

			for (int hourOfDay = 0; hourOfDay < Date.HOURS_PER_DAY; hourOfDay++) {
				final float availableTurbineCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
						* pumper.getGenerationCapacity();
				final float availablePumpCapacity = DAY_AHEAD_SHARE_OF_CAPACITY
						* pumper.getPumpCapacity();
				// Pumping
				if ((pumper.getOperation()[hourOfDay] > 0) && (possibleContinousPumpCapacity > 0)) {
					final float operationStillPossible = availablePumpCapacity
							- pumper.getOperation()[hourOfDay];
					if (operationStillPossible <= 0) {
						continue;
					}
					final float additionalOperation = Math.min(operationStillPossible,
							possibleContinousPumpCapacity / Date.HOURS_PER_DAY);

					// price lower than zero => Pumping
					final DayAheadHourlyBid bid = new DayAheadHourlyBid(hourOfDay,
							TraderType.PUMPED_STORAGE);
					bids.add(bid);
					bid.addBidPoint(new HourlyBidPower.Builder(additionalOperation, -5 * EPSILON,
							hourOfDay, BidType.ASK, marketArea).fuelType(FuelType.RENEWABLE)
									.traderType(TraderType.PUMPED_STORAGE)
									.comment("PumpedStorage Pump extreme").build());
				} else
				// Turbining
				if ((pumper.getOperation()[hourOfDay] <= 0)
						&& (possibleContinousTurbineCapacity > 0)) {
					final float operationStillPossible = availableTurbineCapacity
							+ pumper.getOperation()[hourOfDay];
					if (operationStillPossible <= 0) {
						continue;
					}
					float additionalOperation = Math.min(operationStillPossible,
							possibleContinousTurbineCapacity / Date.HOURS_PER_DAY);
					if (isExtremeSituation) {
						additionalOperation = operationStillPossible;
					}
					final DayAheadHourlyBid bid = new DayAheadHourlyBid(hourOfDay,
							TraderType.PUMPED_STORAGE);
					bids.add(bid);
					final float price = getMinimumDayAheadPrice();
					bid.addBidPoint(new HourlyBidPower.Builder(additionalOperation, price,
							hourOfDay, BidType.SELL, marketArea).fuelType(FuelType.RENEWABLE)
									.traderType(TraderType.PUMPED_STORAGE)
									.comment("PumpedStorage turbine extreme").build());
				}
			}
		}
		return bids;
	}

	/** Creates an hourly bid */
	private DayAheadHourlyBid generateHBid(int hour) {
		final DayAheadHourlyBid bid = new DayAheadHourlyBid(hour, TraderType.PUMPED_STORAGE);

		float volume;
		if ((tradingDayAhead == 1) || (tradingDayAhead == 3)) {
			volume = summedOperationDayAhead[hour];
		} else {
			volume = summedOperation[hour];
		}

		// Bids with volume of zero do not have to be regarded
		if (volume == 0) {
			return null;
		}

		BidType bidType;
		// For sell, he accepts any price even the lowest, for buying any price
		// even the highest, however, not the peaker price
		float price1;
		float price2;
		if (volume < 0) {
			bidType = BidType.SELL;
			volume = Math.abs(volume);
			if (priceMinSell == null) {
				// Plant won't generate for negative prices in oder to produce
				price1 = Math.max(0, EPSILON);
			} else {
				price1 = priceMinSell;
			}
			price1 = marketArea.getCostOfNewEntryPlant().getCostsVar(Date.getYear(), marketArea)
					/ 4;
			price2 = marketArea.getCostOfNewEntryPlant().getCostsVar(Date.getYear(), marketArea)
					* (1 + (Date.getYearIndex() / 100));

			final int hourOfYear = Date.getHourOfYearFromHourOfDay(hour);
			final float priceForecast = PriceForecastFuture.getForwardPriceListCurrentYear()
					.get(marketArea).get(hourOfYear);
			price1 = 5;
			price2 = Math.max(15, priceForecast + 100);
			price2 = 10;
		} else {
			bidType = BidType.ASK;

			price1 = priceMaxAsk;

			price2 = price1 - EPSILON;

		}

		if (!Settings.isMarketClearingPriceSensitive()) {
			price1 = getMinimumDayAheadPrice();
			price2 = getMinimumDayAheadPrice();
		}
		bid.addBidPoint(new HourlyBidPower.Builder(volume / 2, price1, hour, bidType, marketArea)
				.fuelType(FuelType.RENEWABLE).traderType(TraderType.PUMPED_STORAGE)
				.comment("PumpedStorage1: " + bidType).build());
		bid.addBidPoint(new HourlyBidPower.Builder(volume / 2, price2, hour, bidType, marketArea)
				.fuelType(FuelType.RENEWABLE).traderType(TraderType.PUMPED_STORAGE)
				.comment("PumpedStorage2: " + bidType).build());

		return bid;
	}

	/**
	 * Returns the average load during the optimization period before the pumped
	 * storage dispatch
	 */
	private float getAvgLoadBeforePumpStorageDispatch() {
		float avgLoadBeforePumpStorageDispatch = 0;

		for (final float element : residualLoadForecast) {
			avgLoadBeforePumpStorageDispatch += element;
		}

		return avgLoadBeforePumpStorageDispatch / residualLoadForecast.length;
	}

	private void getDynamicPumpProfile() {
		for (int hour = 0; hour < Date.HOURS_PER_DAY; hour++) {
			final int hourOfYear = Date.getHourOfYearFromHourOfDay(hour);
			final float value = marketArea.getPumpStorage()
					.getDynamicPumpStorageProfile(Date.getYear(), hourOfYear);
			summedOperationDayAhead[hour] = value;
		}
	}

	private float[] getLoadForecast() {
		final float[] loadResidual = new float[MarketCouplingOperator.getForecastLengthShort()];
		final List<Plant> powerPlants = new ArrayList<>();
		for (final Generator generator : marketArea.getGenerators()) {
			for (final Plant plant : generator.getAvailablePlants()) {
				powerPlants.add(plant);
			}
		}
		final float[] availableFlexibleCapacity = new float[MarketCouplingOperator
				.getForecastLengthShort()];
		for (int hour = 0; hour < MarketCouplingOperator.getForecastLengthShort(); hour++) {
			int hourOfYear;

			// If last day of the year is exceeded within forecast period,
			// corresponding values at the beginning of the current year will be
			// used
			if ((((Date.getDayOfYear() - 1) * HOURS_PER_DAY) + hour) >= HOURS_PER_YEAR) {
				hourOfYear = (hour + 1) - HOURS_PER_DAY;
			} else {
				hourOfYear = Date.getHourOfYearFromHourOfDay(hour);
			}

			/* Load data */
			// Demand load (including losses)
			final float demandLoadForecast = marketArea.getDemandData()
					.getHourlyDemandOfYear(hourOfYear);

			// Exchange
			final float exchangeForecast = marketArea.getMarketCouplingOperator()
					.getHourlyExchangeForecastAllMarketAreas().get(marketArea).get(hour)
					+ marketArea.getExchange().getHourlyFlowForecast(Date.getYear(), hourOfYear);
			// Renewables load
			final float renewablesForecast = marketArea.getManagerRenewables()
					.getTotalRenewableLoad(hourOfYear);
			// Electricity produced in cogeneration

			// Seasonal storage
			float seasonalOperation = 0;
			for (final SeasonalStorageTrader trader : marketArea.getSeasonalStorageTraders()) {
				seasonalOperation += trader.getOperationPlanned(hourOfYear);
			}

			// Determine residual load
			loadResidual[hour] = (((demandLoadForecast + exchangeForecast) - renewablesForecast
					- seasonalOperation));

			// Available Capacity PowerPlants
			for (final Plant plant : powerPlants) {
				availableFlexibleCapacity[hour] += plant.getCapacityUnusedExpected(hourOfYear);
			}
			if (loadResidual[hour] >= availableFlexibleCapacity[hour]) {
				isExtremeSituation = true;
			}
		}

		if (isExtremeSituation) {
			for (int hour = 0; hour < loadResidual.length; hour++) {
				loadResidual[hour] = loadResidual[hour] - availableFlexibleCapacity[hour];
			}
		}
		return loadResidual;
	}

	private float[] getPriceForecastHeuristic(List<Plant> powerPlants) {
		int forecastLength = MarketCouplingOperator.getForecastLengthShort();
		if (Date.isLastDayOfYear()) {
			forecastLength = 24;
		}
		Collections.sort(powerPlants);
		final float[] priceForecast = new float[forecastLength];
		final List<Float> loadForecast = new ArrayList<>(forecastLength);
		final List<ScenarioList<Float>> loadScenario = new ArrayList<>(1);

		// Get load forecast for the optimization period and convert to list.
		residualLoadForecast = getLoadForecast();
		for (int hour = 0; hour < forecastLength; hour++) {
			// Price forecast cannot handle negative residual demand
			loadForecast.add(Math.max(residualLoadForecast[hour], 0));
		}

		loadScenario.add(new ScenarioList<>(1, "LoadForecast", loadForecast, 1f));

		// Actual price forecast
		final AssignPowerPlantsForecast assignPowerPlantsForecast = new AssignPowerPlantsForecast(
				powerPlants, loadScenario, marketArea);
		assignPowerPlantsForecast.assignPlants();
		final List<ScenarioList<Float>> costScenario = assignPowerPlantsForecast.getMarginalCosts();

		// Convert price forecast to array
		for (int hour = 0; hour < forecastLength; hour++) {
			priceForecast[hour] = costScenario.get(0).getValues().get(hour);
		}

		return priceForecast;
	}

	public Float getPriceMaxAsk() {
		return priceMaxAsk;
	}

	public Float getPriceMinSell() {
		return priceMinSell;
	}

	public float[] getPrices() {
		// // Test case

		final List<Plant> powerPlants = new ArrayList<>();
		for (final Generator generator : marketArea.getGenerators()) {
			for (final Plant plant : generator.getAvailablePlants()) {
				powerPlants.add(plant);
			}
		}
		priceForecast = getPriceForecastHeuristic(powerPlants);
		return priceForecast;
	}

	public float[] getSoldCapacities() {
		return soldCapacities;
	}

	public float[] getSummedCapacity() {
		return summedCapacity;
	}

	public float[] getSummedOperation() {
		return summedOperation;
	}

	public float[] getSummedStorageStatus() {
		return summedStorageStatus;
	}

	public float[] getTotalIncome() {
		return totalIncome;
	}

	public int getTradingDayAhead() {
		return tradingDayAhead;
	}

	@Override
	public void initialize() {

		logger.info(marketArea.getInitialsBrackets() + "Initialize PumpStorageTrader");

		marketArea.addPumpStorageTrader(this);
		marketArea.setPumpStorageStaticProfile(staticpumpStorageProfile);
		marketArea.setPumpStorageActiveTrading(tradingDayAhead);
		pumpy.clear();
		optimizationPeriod = OPTIMIZATION_PERIOD;
		soldCapacities = new float[HOURS_PER_YEAR];
		summedCapacity = new float[optimizationPeriod];
		summedOperation = new float[optimizationPeriod];
		summedOperationDayAhead = new float[optimizationPeriod];

		summedStorageStatus = new float[optimizationPeriod];

		totalIncome = new float[optimizationPeriod];

		switch (tradingDayAhead) {
			/**
			 * 0 no pump storage 1 static profile 2 dynamic bidding
			 */
			case 0:
				for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
					summedOperationDayAhead[hour] = 0;
					summedCapacity[hour] = 0;
					summedStorageStatus[hour] = 0;
				}
				break;
			case 1:
				summedOperationDayAhead = staticpumpStorageProfile.clone();
				break;
			case 2:
				for (int hour = 0; hour < optimizationPeriod; hour++) {
					summedOperation[hour] = 0;
					summedCapacity[hour] = 0;
					summedStorageStatus[hour] = 0;
				}
				break;
			default:
				for (int hour = 0; hour < optimizationPeriod; hour++) {
					summedOperation[hour] = 0;
					summedCapacity[hour] = 0;
					summedStorageStatus[hour] = 0;
				}
				break;
		}
	}

	public boolean isLogPumpedStorageDispatch() {
		return logPumpedStorageDispatch;
	}

	/**
	 * Checks if an overflow of the storage occurs considering a given operation
	 * per hour of the optimization period. A certain tolerance (0.01% of
	 * maxStorageLevel) is defined to reach faster convergence.
	 */
	private boolean isOverflow(PumpStoragePlant pumper) {
		for (int hour = 0; hour < pumper.getPlannedStatus().length; hour++) {
			if (pumper.getPlannedStatus()[hour] > (1.0001f * pumper.getStorageVolume())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if an underflow of the storage occurs considering a given
	 * operation per hour of the optimization period. A certain tolerance (0.01%
	 * of maxStorageLevel) is defined to reach faster convergence.
	 */
	private boolean isUnderflow(PumpStoragePlant pumper) {
		for (int hour = 0; hour < pumper.getPlannedStatus().length; hour++) {
			if (pumper.getPlannedStatus()[hour] < (-0.0001f * pumper.getStorageVolume())) {
				return true;
			}
		}
		return false;
	}

	public void logInitializePumpedStorageDispatch() {
		// Dispatch logging file
		final String fileName = marketArea.getInitialsUnderscore() + "PumpedStorageDispatch_"
				+ Date.getYear() + Settings.LOG_FILE_SUFFIX_CSV;
		String unitLine = "#Unit-ID;Plant name;Turbine Capacity;Day Ahead Available Turbine Capacity;";

		for (int i = 0; i < Date.getLastHourOfYear(); i++) {
			unitLine = unitLine + String.valueOf(i) + ";";
		}
		final String titleLine = "#";
		final String description = "#";
		logIDPumpedStorageDispatch = LoggerCSV.newLogObject(Folder.SUPPLY, fileName, description,
				titleLine, unitLine, marketArea.getIdentityAndNameLong());

		// Storage level logging file
		final String fileNameLevel = marketArea.getInitialsUnderscore()
				+ "PumpedStorageReservoirLevel_" + Date.getYear() + Settings.LOG_FILE_SUFFIX_CSV;
		String unitLineLevel = "#Unit-ID;Plant name;Reservoir volume;";

		for (int i = 0; i < Date.getLastHourOfYear(); i++) {
			unitLineLevel = unitLineLevel + String.valueOf(i) + ";";
		}
		final String titleLineLevel = "#";
		final String descriptionLevel = "#";
		logIDPumpedStorageReservoirLevel = LoggerCSV.newLogObject(Folder.SUPPLY, fileNameLevel,
				descriptionLevel, titleLineLevel, unitLineLevel,
				marketArea.getIdentityAndNameLong());
	}

	public void logPumpedStorageDispatch() {
		final int hoursOfCurrentYear = Date.getLastHourOfYear();

		// Dispatch logging file
		for (final PumpStoragePlant pumper : pumpy) {
			String dataLine = "";
			dataLine = dataLine + String.valueOf(pumper.getUnitID()) + ";" + pumper.getName() + ";"
					+ pumper.getGenerationCapacity() + ";"
					+ (DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getGenerationCapacity()) + ";";

			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				dataLine = dataLine + String.valueOf(pumper.getLongOperation()[hour]) + ";";
			}
			LoggerCSV.writeLine(logIDPumpedStorageDispatch, dataLine);
		}

		// Storage level logging file
		for (final PumpStoragePlant pumper : pumpy) {
			String dataLineLevel = "";
			dataLineLevel = dataLineLevel + String.valueOf(pumper.getUnitID()) + ";"
					+ pumper.getName() + ";" + pumper.getStorageVolume() + ";";

			for (int hour = 0; hour < hoursOfCurrentYear; hour++) {
				dataLineLevel = dataLineLevel + String.valueOf(pumper.getLongStorageStatus(hour))
						+ ";";
			}
			LoggerCSV.writeLine(logIDPumpedStorageReservoirLevel, dataLineLevel);
		}
	}

	private void optimizeOperation(int optimizationMode, PumpStoragePlant pumper) {
		try {

			// create environment and model

			final GRBModel model = new GRBModel(env);
			model.getEnv().set("OutputFlag", "0");

			// Solver settings
			// Set number of threads per model
			model.set(GRB.IntParam.Threads, 1);

			// Use primal simplex (0), dual simplex (1) or barrier (2)
			model.set(GRB.IntParam.Method, 0);

			// Presolve level automatic (-1), off (0), conservative (1) or
			// aggressive (2)
			model.getEnv().set(GRB.IntParam.Presolve, 0);

			// storage level
			final GRBVar[] storageLevel = new GRBVar[optimizationPeriod];
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				storageLevel[hour] = model.addVar(0, pumper.getStorageVolume(), 0, GRB.CONTINUOUS,
						"storageLevel" + hour);
			}

			// pump operation
			final GRBVar[] pumpOperation = new GRBVar[optimizationPeriod];
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				pumpOperation[hour] = model.addVar(0,
						DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getPumpCapacity(), 0, GRB.CONTINUOUS,
						"pumpOperation" + hour);
			}

			final GRBVar pumpOperationMax = model.addVar(0,
					DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getPumpCapacity(), 0, GRB.CONTINUOUS,
					"pumpOperationMax");

			// turbine operation
			final GRBVar[] turbineOperation = new GRBVar[optimizationPeriod];
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				turbineOperation[hour] = model.addVar(
						-DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getGenerationCapacity(), 0, 0,
						GRB.CONTINUOUS, "turbineOperation" + hour);
			}

			final GRBVar turbineOperationMax = model.addVar(
					-DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getGenerationCapacity(), 0, 0,
					GRB.CONTINUOUS, "turbineOperationMax");

			// operation
			final GRBVar[] operation = new GRBVar[optimizationPeriod];
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				operation[hour] = model.addVar(
						-DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getGenerationCapacity(),
						DAY_AHEAD_SHARE_OF_CAPACITY * pumper.getPumpCapacity(), 0, GRB.CONTINUOUS,
						"operation" + hour);
			}

			// update model
			model.update();

			// setting objective function
			final GRBLinExpr objexpr = new GRBLinExpr();
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				objexpr.addTerm(-priceForecast[hour], operation[hour]);
			}
			// Needed for regularization
			objexpr.addTerm(-0.01, pumpOperationMax);
			objexpr.addTerm(0.01, turbineOperationMax);

			model.setObjective(objexpr, GRB.MAXIMIZE);

			// add constraint for operation
			GRBLinExpr expr;
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				expr = new GRBLinExpr();
				expr.addTerm(1, pumpOperation[hour]);
				expr.addTerm(1, turbineOperation[hour]);
				model.addConstr(expr, GRB.EQUAL, operation[hour], "constraintOperation" + hour);
			}

			// add constraint for storage level
			expr = new GRBLinExpr();
			expr.addConstant(pumper.getStorageStatus());
			expr.addTerm(Math.sqrt(pumper.getEfficiency()), pumpOperation[0]);
			expr.addTerm(1 / Math.sqrt(pumper.getEfficiency()), turbineOperation[0]);
			expr.addConstant(pumper.getStorageInflow(Date.getHourOfYearFromHourOfDay(0)));
			model.addConstr(expr, GRB.EQUAL, storageLevel[0], "constraintLevel0");

			for (int hour = 1; hour < optimizationPeriod; hour++) {
				int inflowHour;

				// If last day of the year is exceeded within forecast period,
				// corresponding values at the beginning of the current year
				// will be
				// used
				if ((((Date.getDayOfYear() - 1) * HOURS_PER_DAY) + hour) >= HOURS_PER_YEAR) {
					inflowHour = (hour + 1) - HOURS_PER_DAY;
				} else {
					inflowHour = Date.getHourOfYearFromHourOfDay(hour);
				}

				expr = new GRBLinExpr();
				expr.addTerm(1, storageLevel[hour - 1]);
				expr.addTerm(Math.sqrt(pumper.getEfficiency()), pumpOperation[hour]);
				expr.addTerm(1 / Math.sqrt(pumper.getEfficiency()), turbineOperation[hour]);
				expr.addConstant(pumper.getStorageInflow(inflowHour));
				model.addConstr(expr, GRB.EQUAL, storageLevel[hour], "constraintLevel" + hour);
			}

			// add aux constraints for regularization
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				expr = new GRBLinExpr();
				expr.addTerm(1, pumpOperationMax);
				model.addConstr(expr, GRB.GREATER_EQUAL, pumpOperation[hour],
						"constraintPumpAux" + hour);

				expr = new GRBLinExpr();
				expr.addTerm(1, turbineOperationMax);
				model.addConstr(expr, GRB.LESS_EQUAL, turbineOperation[hour],
						"constraintTurbineAux" + hour);
			}

			// perform optimization
			model.optimize();

			// transfer operation and storage status
			for (int hour = 0; hour < optimizationPeriod; hour++) {
				pumper.setPlannedOperation(hour, (float) operation[hour].get(GRB.DoubleAttr.X));

			}
			pumper.setStorageStatus((float) storageLevel[23].get(GRB.DoubleAttr.X));

			// dispose of model and environment
			model.dispose();
		}

		catch (final GRBException e) {
			System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
		}
	}

	private void setLevelsAndStatus(final PumpStoragePlant pumper) throws Exception {
		setStorageLevelOptimization(pumper);
		correctOverAndUnderFlowsOptimization(pumper);
		// transfer to new day
		pumper.setStorageStatus(pumper.getPlannedStatus()[23]);
		/** turn operation into purchase array **/
		pumper.setPlanIntoOperation();

		/** Sum up data for all the plants */
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			summedOperation[hour] += pumper.getPlannedOperation()[hour];
			// Prepare data for logging of pumped storage dispatch
			pumper.setLongOperation(Date.getHourOfYearFromHourOfDay(hour),
					pumper.getOperation()[hour]);
			pumper.setLongStorageStatus(Date.getHourOfYearFromHourOfDay(hour),
					pumper.getPlannedStatus()[hour]);
		}

	}

	/**
	 * Sets the storage level for every hour of the optimization period
	 * resulting from the planned operation
	 */
	private void setStorageLevel(PumpStoragePlant pumper) {
		if (pumper.getPlannedOperation()[0] > 0) {
			pumper.setPlannedStatus(0,
					pumper.getStorageStatus()
							+ (pumper.getPlannedOperation()[0] * pumper.getChargeEfficiency())
							+ pumper.getStorageInflow(Date.getHourOfYearFromHourOfDay(0)));
		} else if (pumper.getPlannedOperation()[0] < 0) {
			pumper.setPlannedStatus(0,
					pumper.getStorageStatus()
							+ (pumper.getPlannedOperation()[0] / pumper.getGenerationEfficiency())
							+ pumper.getStorageInflow(Date.getHourOfYearFromHourOfDay(0)));
		} else {
			pumper.setPlannedStatus(0, pumper.getStorageStatus()
					+ pumper.getStorageInflow(Date.getHourOfYearFromHourOfDay(0)));
		}

		for (int hour = 1; hour < HOURS_PER_DAY; hour++) {
			int inflowHour;

			// If last day of the year is exceeded within forecast period,
			// corresponding values at the beginning of the current year will be
			// used
			if ((((Date.getDayOfYear() - 1) * HOURS_PER_DAY) + hour) >= HOURS_PER_YEAR) {
				inflowHour = (hour + 1) - HOURS_PER_DAY;
			} else {
				inflowHour = Date.getHourOfYearFromHourOfDay(hour);
			}

			if (pumper.getPlannedOperation()[hour] > 0) {
				pumper.setPlannedStatus(hour, pumper.getPlannedStatus()[hour - 1]
						+ (pumper.getPlannedOperation()[hour] * pumper.getChargeEfficiency())
						+ pumper.getStorageInflow(inflowHour));
			} else if (pumper.getPlannedOperation()[hour] < 0) {
				pumper.setPlannedStatus(hour, pumper.getPlannedStatus()[hour - 1]
						+ (pumper.getPlannedOperation()[hour] / pumper.getGenerationEfficiency())
						+ pumper.getStorageInflow(inflowHour));
			} else {
				pumper.setPlannedStatus(hour,
						pumper.getPlannedStatus()[hour - 1] + pumper.getStorageInflow(inflowHour));
			}
		}
	}

	private void setStorageLevelOptimization(PumpStoragePlant pumper) {
		try {
			int inflowHour = Date.getHourOfYearFromHourOfDay(0);
			if (pumper.getPlannedOperation()[0] > 0) {
				pumper.setPlannedStatus(0,
						pumper.getStorageStatus()
								+ (pumper.getPlannedOperation()[0] * pumper.getEfficiency())
								+ pumper.getStorageInflow(inflowHour));
			} else if (pumper.getPlannedOperation()[0] < 0) {
				pumper.setPlannedStatus(0, pumper.getStorageStatus()
						+ pumper.getPlannedOperation()[0] + pumper.getStorageInflow(inflowHour));
			} else {
				pumper.setPlannedStatus(0,
						pumper.getStorageStatus() + pumper.getStorageInflow(inflowHour));
			}

			for (int hour = 1; hour < HOURS_PER_DAY; hour++) {

				// If last day of the year is exceeded within forecast period,
				// corresponding values at the beginning of the current year
				// will be used
				if ((((Date.getDayOfYear() - 1) * HOURS_PER_DAY) + hour) >= HOURS_PER_YEAR) {
					inflowHour = (hour + 1) - HOURS_PER_DAY;
				} else {
					inflowHour = Date.getHourOfYearFromHourOfDay(hour);
				}

				if (pumper.getPlannedOperation()[hour] > 0) {
					pumper.setPlannedStatus(hour,
							pumper.getPlannedStatus()[hour - 1]
									+ (pumper.getPlannedOperation()[hour] * pumper.getEfficiency())
									+ pumper.getStorageInflow(inflowHour));
				} else if (pumper.getPlannedOperation()[hour] < 0) {
					pumper.setPlannedStatus(hour,
							pumper.getPlannedStatus()[hour - 1]
									+ (pumper.getPlannedOperation()[hour])
									+ pumper.getStorageInflow(inflowHour));
				} else {
					pumper.setPlannedStatus(hour, pumper.getPlannedStatus()[hour - 1]
							+ pumper.getStorageInflow(inflowHour));
				}
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
}