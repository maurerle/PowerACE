package results.spot;

import static simulations.scheduling.Date.HOURS_PER_DAY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.operator.spot.tools.MarginalBid;
import simulations.MarketArea;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import tools.math.Statistics;

public final class DayAhead extends Agent {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(DayAhead.class.getName());

	/** Map with the accepted demand for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> demandAccepted = new HashMap<>(Date.getTotalHours());
	/** Map with the accepted exchange for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> exchangeAccepted = new HashMap<>(Date.getTotalHours());
	/** List with marginal bids for all simulated hours */
	private final Map<Integer, MarginalBid> marginalBids = new ConcurrentHashMap<>();
	/**
	 * Map with the running hours for all marginal bids (hourOfYear,
	 * hourOfContinousRunning)
	 */
	private HashMap<Integer, Integer> marginalBidsRunningHours;

	/** Map with the prices for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> prices = new ConcurrentHashMap<>(Date.getTotalHours());
	/** Map with the accepted exchange for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> renewablesAccepted = new HashMap<>(Date.getTotalHours());
	/** Map with the accepted exchange for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> sheddableLoadAccepted = new HashMap<>(Date.getTotalHours());
	/** Map with the accepted exchange for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> shiftableLoadAcceptedAsk = new HashMap<>(
			Date.getTotalHours());
	/** Map with the accepted exchange for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> shiftableLoadAcceptedSell = new HashMap<>(
			Date.getTotalHours());
	/**
	 * Map with the startup costs in the bid that set the price for all
	 * simulated hours [Euro/MWh]
	 */
	private final Map<Integer, Float> startupCosts = new HashMap<>(Date.getTotalHours());
	/** Map with the volumes for all simulated hours [Euro/MWh] */
	private final Map<Integer, Float> volumes = new ConcurrentHashMap<>(Date.getTotalHours());

	public DayAhead(MarketArea marketArea) {
		super(marketArea);
	}

	public void addMarginalRunningHours(Map<Integer, Integer> marginalBidsHoursDaily) {

		// Only keep data in map for one year
		if (Date.isFirstDayOfYear()) {
			marginalBidsRunningHours = new HashMap<>();
		}
		marginalBidsRunningHours.putAll(marginalBidsHoursDaily);

	}

	/**
	 * Return a copy of current daily prices of the current year.
	 *
	 * @return prices in Euro/MWh
	 */
	public List<Float> getDailyPrices() {
		return getDailyPrices(Date.getDayOfYear());
	}

	/**
	 * Return a copy of requested daily prices of the current year.
	 *
	 * @param day
	 *            [1,365]
	 * @return prices in Euro/MWh
	 */
	public List<Float> getDailyPrices(int day) {
		return getDailyPrices(Date.getYear(), day);

	}

	/**
	 * Return a copy of requested daily prices of the current year.
	 *
	 * @param day
	 *            [1,365]
	 * @param year
	 * @return prices in Euro/MWh
	 */
	public List<Float> getDailyPrices(int year, int day) {
		final List<Float> dailyPrices = new ArrayList<>();
		int key = Date.getKeyHourly(Date.getYear(), day, 0);
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			dailyPrices.add(prices.get(key++));
		}
		return dailyPrices;

	}

	/**
	 * Return a copy of current daily prices of the current year.
	 *
	 * @return prices in Euro/MWh
	 */
	public float[] getDailyPricesArray() {
		return getDailyPricesArray(Date.getDayOfYear());
	}

	/**
	 * Return a copy of requested daily prices of the current year.
	 *
	 * @param day
	 *            [1,365]
	 * @return prices in Euro/MWh
	 */
	public float[] getDailyPricesArray(int day) {
		final List<Float> pricesTemp = getDailyPrices(day);
		final float[] prices = new float[pricesTemp.size()];
		for (int index = 0; index < pricesTemp.size(); index++) {
			final Float price = pricesTemp.get(index);
			prices[index] = (price == null ? Float.NaN : price);
		}
		return prices;
	}

	/**
	 * Return a copy of requested daily volumes of the current year.
	 *
	 * @return volumes in MWh
	 */
	public List<Float> getDailyVolumes(int year, int dayOfYear) {
		final List<Float> dailyVolumes = new ArrayList<>();
		int key = Date.getKeyHourly(year, dayOfYear, 0);
		for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
			dailyVolumes.add(volumes.get(key++));
		}
		return dailyVolumes;
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getDemandAcceptedHourOfDay(int hourOfDay) {
		return demandAccepted.get(Date.getKeyHourlyWithHourOfDay(hourOfDay));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getExchangeAcceptedHourOfDay(int hourOfDay) {
		return exchangeAccepted.get(Date.getKeyHourlyWithHourOfDay(hourOfDay));
	}

	/**
	 * Return the requested hourly price of the current day of the current year.
	 *
	 * @param hour
	 *            [0, HOURS_DAY]
	 * @return price in Euro/MWh
	 */
	public float getHourlyPriceOfDay(int hour) {
		return prices.get(Date.getKeyHourlyWithHourOfDay(hour));
	}

	/**
	 * Return the requested hourly price of the current year.
	 *
	 * @param hour
	 *            [0, HOURS_PER_YEAR]
	 * @return price in Euro/MWh
	 */
	public float getHourlyPriceOfYear(int hour) {
		return prices.get(Date.getKeyHourlyWithHourOfYear(hour));
	}

	/**
	 * Return the requested hourly price of the current year.
	 *
	 * @param hour
	 *            [0, HOURS_PER_YEAR]
	 *
	 * @return price in Euro/MWh
	 */
	public float getHourlyPriceOfYear(int year, int hour) {
		return prices.get(Date.getKeyHourlyWithHourOfYear(year, hour));
	}

	/**
	 * Return the requested hourly Startup Costs of the current year.
	 *
	 * @param hour
	 *            [0, HOURS_PER_YEAR]
	 * @return price in Euro/MWh
	 */
	public float getHourlyStartupCostsOfYear(int year, int hour) {
		return startupCosts.get(Date.getKeyHourlyWithHourOfYear(year, hour));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getHourlyVolumeOfYear(int year, int hour) {
		return volumes.get(Date.getKeyHourlyWithHourOfYear(year, hour));
	}

	/** Get marginal bid for specified hourOfDay */
	public MarginalBid getMarginalBidHourOfDay(int hourOfDay) {
		return getMarginalBid(Date.getKeyHourlyWithHourOfDay(hourOfDay));
	}

	/** Get marginal bid for specified hourOfDay */
	public MarginalBid getMarginalBidHourOfYear(int hourOfYear) {
		return getMarginalBid(Date.getKeyHourlyWithHourOfYear(Date.getYear(), hourOfYear));
	}

	/** Get marginal bid for specified hourOfDay */
	public MarginalBid getMarginalBidHourOfYear(int year, int hourOfYear) {
		return getMarginalBid(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	public Integer getMarginalBidRunningHours(int hourOfYear) {
		if (marginalBidsRunningHours == null) {
			return null;
		} else {
			return marginalBidsRunningHours.get(hourOfYear);
		}
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getRenewablesAcceptedHourOfDay(int hourOfDay) {
		return renewablesAccepted.get(Date.getKeyHourlyWithHourOfDay(hourOfDay));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getSheddableLoadAcceptedHourOfYear(int hourOfYear) {
		return sheddableLoadAccepted.get(Date.getKeyHourlyWithHourOfYear(hourOfYear));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getSheddableLoadAcceptedHourOfYear(int year, int hourOfYear) {
		return sheddableLoadAccepted.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getShiftableLoadAcceptedHourOfYearAsk(int hourOfYear) {
		return shiftableLoadAcceptedAsk.get(Date.getKeyHourlyWithHourOfYear(hourOfYear));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getShiftableLoadAcceptedHourOfYearAsk(int year, int hourOfYear) {
		return shiftableLoadAcceptedAsk.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getShiftableLoadAcceptedHourOfYearSell(int hourOfYear) {
		return shiftableLoadAcceptedSell.get(Date.getKeyHourlyWithHourOfYear(hourOfYear));
	}

	/**
	 * Return the requested hourly volume of the current day of the current
	 * year.
	 *
	 * @return volume in MWh
	 */
	public float getShiftableLoadAcceptedHourOfYearSell(int year, int hourOfYear) {
		return shiftableLoadAcceptedSell.get(Date.getKeyHourlyWithHourOfYear(year, hourOfYear));
	}

	/**
	 * Return the yearly average of hourly electricity prices of current year.
	 *
	 * @return yearly average in Euro/MWh
	 */
	public float getYearlyAveragePrice() {
		final int year = Date.getYear();
		return getYearlyAveragePrice(year);
	}

	/**
	 * Return average of hourly electricity prices of year.
	 * 
	 * @param year
	 * @return average in Euro/MWh of year
	 */
	public float getYearlyAveragePrice(int year) {
		return Statistics.calcAvg(getYearlyPrices(year));
	}

	/**
	 * Return average of hourly off-peak electricity prices in
	 * 
	 * @param year
	 * @return average in Euro/MWh of year
	 */
	public float getYearlyAveragePriceOffPeak(int year) {
		final List<Float> pricesOffPeak = new ArrayList<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			if (!Date.isPeakTime(hourOfYear)) {
				pricesOffPeak.add(getHourlyPriceOfYear(year, hourOfYear));
			}
		}
		return Statistics.calcAvg(pricesOffPeak);
	}
	/**
	 * Return average of hourly peak electricity prices in
	 * 
	 * @param year
	 * @return average in Euro/MWh of year
	 */
	public float getYearlyAveragePricePeak(int year) {
		final List<Float> pricesPeak = new ArrayList<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			if (Date.isPeakTime(hourOfYear)) {
				pricesPeak.add(getHourlyPriceOfYear(year, hourOfYear));
			}
		}
		return Statistics.calcAvg(pricesPeak);
	}

	/**
	 * Return the yearly average of hourly electricty prices weighted by the
	 * market clearing volume
	 *
	 * @return yearly average weighted by volume in Euro/MWh
	 */
	public float getYearlyAverageVolumeWeightedPrice(int year) {

		if (year > Date.getYear()) {
			return 0f;
		}

		float yearlyAverage = 0f;
		final float sumVolume = getYearlyVolume(year);
		final List<Float> pricesTemp = getYearlyPrices(year);
		final List<Float> volumesTemp = getYearlyVolumes(year);
		for (int index = 0; index < pricesTemp.size(); index++) {
			yearlyAverage += pricesTemp.get(index) * volumesTemp.get(index);
		}
		yearlyAverage /= sumVolume;
		return yearlyAverage;
	}

	/**
	 * Return a copy of the yearly prices of the current year.
	 *
	 * @return prices in Euro/MWh
	 */
	public List<Float> getYearlyPrices() {
		return getYearlyPrices(Date.getYear());
	}

	/**
	 * Return a copy of the the yearly prices of the request year.
	 *
	 * @return prices in Euro/MWh
	 */
	public List<Float> getYearlyPrices(int year) {
		final List<Float> yearlyPrices = new ArrayList<>();
		int key = Date.getKeyHourlyWithHourOfYear(year, 0);
		for (int hour = 0; hour < Date.getLastHourOfYear(year); hour++) {
			yearlyPrices.add(prices.get(key++));
		}
		return yearlyPrices;
	}

	/**
	 * Return a copy of the yearly prices of the current year.
	 *
	 * @return prices in Euro/MWh
	 */
	public float[] getYearlyPricesArray() {
		return getYearlyPricesArray(Date.getYear());
	}

	/**
	 * Return a copy of the yearly prices of the current year.
	 *
	 * @return prices in Euro/MWh
	 */
	public float[] getYearlyPricesArray(int year) {
		final List<Float> pricesTemp = getYearlyPrices(year);
		final float[] prices = new float[pricesTemp.size()];
		for (int index = 0; index < pricesTemp.size(); index++) {
			final Float price = pricesTemp.get(index);
			prices[index] = (price == null ? Float.NaN : price);
		}
		return prices;
	}
	/**
	 * Return a copy of the yearly prices of the current year.
	 *
	 * @return prices in Euro/MWh
	 */
	public Map<Integer, Float> getYearlyPricesMap() {
		return getYearlyPricesMap(Date.getYear());
	}

	/**
	 * Return a copy of the the yearly prices of the request year.
	 *
	 * @return prices in Euro/MWh
	 */
	public Map<Integer, Float> getYearlyPricesMap(int year) {
		final Map<Integer, Float> yearlyPrices = new HashMap<>();
		int key = Date.getKeyHourlyWithHourOfYear(year, 0);
		for (int hour = 0; hour < Date.getLastHourOfYear(year); hour++) {
			yearlyPrices.put(hour, prices.get(key++));
		}
		return yearlyPrices;
	}

	/**
	 * Return the yearly start up costs of the current year.
	 *
	 * @return price in Euro/MWh
	 */
	public float getYearlyStartupCostsAvg() {

		final List<Float> values = new ArrayList<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			final MarginalBid bid = marketArea.getElectricityResultsDayAhead()
					.getMarginalBidHourOfYear(hourOfYear);
			if (bid != null) {
				values.add(bid.getStartUpinBid());
			}
		}
		return Statistics.calcAvgWithValidValues(values);
	}

	/**
	 * Return the yearly var costs of the current year.
	 *
	 * 
	 * @return price in Euro/MWh
	 */
	public float getYearlyVarCostsAvg() {
		final List<Float> values = new ArrayList<>();
		for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
			final MarginalBid bid = marketArea.getElectricityResultsDayAhead()
					.getMarginalBidHourOfYear(hourOfYear);
			if (bid != null) {
				values.add(bid.getVarcosts());
			}
		}
		return Statistics.calcAvgWithValidValues(values);
	}

	/**
	 * Return the yearly sum of hourly electricity volume
	 *
	 * @return yearly volume in MWh
	 */
	public float getYearlyVolume(int year) {
		float sumVolume = 0f;
		try {
			for (final float volumeHourly : getYearlyVolumes(year)) {
				sumVolume += volumeHourly;
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return sumVolume;
	}

	/**
	 * Return a copy of the the yearly volumes of the request year.
	 *
	 * @return volumes in MWh
	 */
	public List<Float> getYearlyVolumes(int year) {
		final List<Float> yearlyVolumes = new ArrayList<>();
		int key = Date.getKeyHourlyWithHourOfYear(year, 0);
		for (int hour = 0; hour < Date.getLastHourOfYear(year); hour++) {
			yearlyVolumes.add(volumes.get(key++));
		}
		return yearlyVolumes;
	}

	/**
	 * Return a copy of the yearly volumes of the current year.
	 *
	 * @return volumes in MW
	 */
	public float[] getYearlyVolumesArray() {
		return getYearlyVolumesArray(Date.getYear());
	}

	/**
	 * Return a copy of the yearly prices of the current year.
	 *
	 * @return volumes in MW
	 */
	public float[] getYearlyVolumesArray(int year) {
		final List<Float> volumesTemp = getYearlyVolumes(year);
		final float[] prices = new float[volumesTemp.size()];
		for (int index = 0; index < volumesTemp.size(); index++) {
			final Float volume = volumesTemp.get(index);
			prices[index] = (volume == null ? Float.NaN : volume);
		}
		return prices;
	}

	@Override
	public void initialize() {
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param prices
	 */
	public void setDailyDemandAccepted(List<Float> demandAcceptedDay) {
		int key = Date.getKeyHourly(Date.getYear(), Date.getDayOfYear(), 0);
		for (final Float volume : demandAcceptedDay) {
			demandAccepted.put(key++, volume);
		}
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param prices
	 */
	public void setDailyExchangeAccepted(List<Float> exchangeAcceptedDay) {
		int key = Date.getKeyHourly(Date.getYear(), Date.getDayOfYear(), 0);
		for (final Float volume : exchangeAcceptedDay) {
			exchangeAccepted.put(key++, volume);
		}
	}

	/**
	 * Set the prices of the current day.
	 *
	 * @param prices
	 *            in Euro/MWh
	 */
	public void setDailyPrices(List<Float> dailyPrices) {
		setDailyPrices(dailyPrices, Date.getDayOfYear());
	}

	/**
	 * Set the prices of the requested day.
	 *
	 * @param prices
	 *            in EUR/MWh
	 */
	public void setDailyPrices(List<Float> dailyPrices, int dayOfYear) {
		int hour = 0;
		for (final float price : dailyPrices) {
			setHourlyPrice(price, dayOfYear, hour++);
		}
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param prices
	 */
	public void setDailyRenewablesAccepted(List<Float> renewableAcceptedDay) {
		int key = Date.getKeyHourly(Date.getYear(), Date.getDayOfYear(), 0);
		for (final Float volume : renewableAcceptedDay) {
			renewablesAccepted.put(key++, volume);
		}
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param prices
	 */
	public void setDailySheddableLoadsAccepted(List<Float> sheddableLoadAcceptedDay) {
		int key = Date.getKeyHourly(Date.getYear(), Date.getDayOfYear(), 0);
		for (final Float volume : sheddableLoadAcceptedDay) {
			sheddableLoadAccepted.put(key++, volume);
		}
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param prices
	 */
	public void setDailyShiftableLoadsAcceptedAsk(List<Float> shiftableLoadAcceptedDay) {
		int key = Date.getKeyHourly(Date.getYear(), Date.getDayOfYear(), 0);
		for (final Float volume : shiftableLoadAcceptedDay) {
			shiftableLoadAcceptedAsk.put(key++, volume);
		}
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param prices
	 */
	public void setDailyShiftableLoadsAcceptedSell(List<Float> shiftableLoadAcceptedDay) {
		int key = Date.getKeyHourly(Date.getYear(), Date.getDayOfYear(), 0);
		for (final Float volume : shiftableLoadAcceptedDay) {
			shiftableLoadAcceptedSell.put(key++, volume);
		}
	}

	/**
	 * Set the prices of the current day.
	 *
	 * @param prices
	 *            in Euro/MWh
	 */
	public void setDailyStartupCosts(List<Float> dailyStartupCosts) {
		setDailyStartupCosts(dailyStartupCosts, Date.getDayOfYear());
	}

	/**
	 * Set the prices of the requested day.
	 *
	 * @param costs
	 *            in EUR/MWh
	 */
	public void setDailyStartupCosts(List<Float> dailyStartupCosts, int day) {
		int key = Date.getKeyHourly(Date.getYear(), Date.getDayOfYear(), 0);
		for (final float startupCost : dailyStartupCosts) {
			startupCosts.put(key++, startupCost);
		}
	}

	/**
	 * Set the volumes of the current day.
	 *
	 * @param volumes
	 *            in MWh
	 */
	public void setDailyVolumes(List<Float> dailyVolumes) {
		setDailyVolumes(dailyVolumes, Date.getDayOfYear());
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param prices
	 */
	public void setDailyVolumes(List<Float> dailyVolumes, int dayOfYear) {
		int hour = 0;
		for (final float volume : dailyVolumes) {
			setHourlyVolume(volume, dayOfYear, hour++);
		}
	}

	/**
	 * Set the prices of the requested day.
	 *
	 * @param prices
	 */
	public synchronized void setHourlyPrice(float price, int dayOfYear, int hourOfDay) {
		final int key = Date.getKeyHourly(Date.getYear(), dayOfYear, hourOfDay);
		prices.put(key, price);
	}

	/**
	 * Set the volumes of the requested day.
	 *
	 * @param volume
	 */
	public synchronized void setHourlyVolume(float volume, int dayOfYear, int hourOfDay) {
		final int key = Date.getKeyHourly(Date.getYear(), dayOfYear, hourOfDay);
		volumes.put(key, volume);
	}

	/** Get marginal bid for specified hourOfDay */
	public void setMarginalBidHourOfDay(int hourOfDay, MarginalBid marginalBid) {
		setMarginalBid(Date.getKeyHourlyWithHourOfDay(hourOfDay), marginalBid);
	}

	private MarginalBid getMarginalBid(int hourOfTotal) {
		return marginalBids.get(hourOfTotal);
	}

	private void setMarginalBid(int hourOfTotal, MarginalBid marginalBid) {
		marginalBids.put(hourOfTotal, marginalBid);
	}
}