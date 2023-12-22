package supply.invest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import markets.trader.TraderType;
import simulations.agent.Agent;
import simulations.scheduling.Date;
import supply.powerplant.PlantOption;
import tools.types.FuelName;

public class Investor extends Agent {

	/** Needed for threads so that count is not increased at the same time. */
	private static final Object LOCK = new Object();

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Investor.class.getName());
	protected static int count = 0;

	/** Number of years with detailed price forecast */
	protected static int yearsLongTermPriceForecastEnd = 6;
	/** Number of years from now when long-term price forecast is generated */
	protected static int yearsLongTermPriceForecastStart = 1;

	public static int getYearsLongTermPriceForecastEnd() {
		return yearsLongTermPriceForecastEnd;
	}

	public static int getYearsLongTermPriceForecastStart() {
		return yearsLongTermPriceForecastStart;
	}

	private static void increaseCount() {
		synchronized (LOCK) {
			count++;
		}
	}

	/**
	 * Stores the discount factor in order to speed up access. (Factor means
	 * multiplication!)
	 */
	private final Map<Integer, Float> discountFactor = new HashMap<>();

	/** List of unavailable investment options (by fuel name index) */
	private final List<Integer> unavailOptionsFuel = new ArrayList<>();
	/** List of unavailable investment options (by option ID from database) */
	private final List<Integer> unavailOptionsID = new ArrayList<>();
	/**
	 * Periods for which cash flows are regarded. Can differ from real run time
	 * in model.
	 */
	protected int amortisationTime = 20;
	protected TraderType bidderType;
	protected List<PlantOption> capacityOptions;
	protected int identity;
	protected float interestRate;
	public Investor() {
		super();
		Investor.increaseCount();
	}

	/** Get ID of this InvestmentPlanner */
	public int getID() {
		return identity;
	}

	/** Get interest rate of investor */
	public float getInterestRate() {
		return interestRate;
	}

	public List<Integer> getUnavailOptionsFuel() {
		return unavailOptionsFuel;
	}

	public List<Integer> getUnavailOptionsID() {
		return unavailOptionsID;
	}

	@Override
	public void initialize() {

		// id from database - 1 = index
		identity = marketArea.getCompanyName().getNumber(getName()) - 1;
		bidderType = TraderType.SUPPLY;
		if (interestRate == 0f) {
			interestRate = marketArea.getInterestRate();
		}

		// Initialize discount factor
		for (int index = 0; index <= amortisationTime; index++) {
			if (discountFactor.get(index) == null) {
				final float factor = 1 / (float) Math.pow(1 + interestRate, index);
				discountFactor.put(index, factor);
			}
		}
	}

	/**
	 * Set unavailable investment options (fuel name index) from String
	 * specified in xml file
	 */
	public void setUnavailOptionsFuel(String unavailOptions) {
		unavailOptionsFuel.addAll(parseUnavailOptions(unavailOptions));
	}

	/**
	 * Set unavailable investment options (option ID from database) from String
	 * specified in xml file
	 */
	public void setUnavailOptionsID(String unavailOptions) {
		unavailOptionsID.addAll(parseUnavailOptions(unavailOptions));
	}

	/** Parse specified string (split and add to temp list) */
	private List<Integer> parseUnavailOptions(String unavailOptions) {
		final List<Integer> tempList = new ArrayList<>();
		/** Parse only non-empty strings */
		if (!"".equals(unavailOptions)) {
			/** Buffer options in array */
			final String[] values = unavailOptions.split(",");
			/** Loop all options */
			for (final String value : values) {
				/** Add integer value to list */
				final int integerValue = Integer.parseInt(value.trim());
				tempList.add(integerValue);
			}
		}
		return tempList;
	}

	private void removeUnavailableOptions(Iterator<PlantOption> iterator) {
		while (iterator.hasNext()) {
			final PlantOption option = iterator.next();

			/** Fuels */
			if (unavailOptionsFuel.contains(FuelName.getFuelIndex(option.getFuelName()))) {
				iterator.remove();
				logger.info(marketArea.getInitialsBrackets() + "Fuel " + option.getFuelName()
						+ " removed from investment options.");
				continue;
			}

			/** Option IDs */
			if (unavailOptionsID.contains(option.getUnitID())) {
				iterator.remove();
				logger.info(marketArea.getInitialsBrackets() + "Investment option (ID) "
						+ option.getUnitID() + " removed from investment options.");
				continue;
			}

			/** Remove Nucs for early years */
			if (Date.getYear() < 2029 && (option.getFuelName() == FuelName.URANIUM)) {
				iterator.remove();
				logger.info(marketArea.getInitialsBrackets() + "Fuel " + option.getFuelName()
						+ " removed from investment options for early years.");
				continue;
			}
		}
	}

	private void removeUnprofitableOptions(Set<PlantOption> unprofitablePlantOptionsOtherAgents,
			Iterator<PlantOption> iterator) {

		while (iterator.hasNext()) {
			final PlantOption option = iterator.next();

			if (unprofitablePlantOptionsOtherAgents != null) {
				for (final PlantOption unprofitablePlantOption : unprofitablePlantOptionsOtherAgents) {
					if (option.isSameInvestmentOption(unprofitablePlantOption)) {
						iterator.remove();
						break;
					}
				}
			}
		}
	}

	/**
	 * Determines all available CapacityOption from and removes those that are
	 * not available for this agent
	 *
	 * @param unprofitablePlantOptions
	 */
	protected List<PlantOption> determineInvestmentOptions(
			Set<PlantOption> unprofitablePlantOptionsOtherAgents) {
		return determineInvestmentOptions(unprofitablePlantOptionsOtherAgents, null);
	}

	/**
	 * Determines all available CapacityOption from and removes those that are
	 * not available for this agent
	 *
	 * @param unprofitablePlantOptions
	 */
	protected List<PlantOption> determineInvestmentOptions(
			Set<PlantOption> unprofitablePlantOptionsOtherAgents,
			Set<PlantOption> unprofitablePlantOptionsThisAgent) {
		logger.info(marketArea.getInitialsBrackets() + "Determine available investment options");

		int year = Date.getYear();

		final List<PlantOption> capacityOptions = new ArrayList<>(
				marketArea.getGenerationData().getCopyOfCapacityOptions(year));
		removeUnavailableOptions(capacityOptions.iterator());
		removeUnprofitableOptions(unprofitablePlantOptionsOtherAgents, capacityOptions.iterator());
		removeUnprofitableOptions(unprofitablePlantOptionsThisAgent, capacityOptions.iterator());

		return capacityOptions;
	}

	protected Map<Integer, Float> getDiscountFactor() {
		return Collections.unmodifiableMap(discountFactor);
	}

}
