/**
 * 
 */
package results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.scheduling.Date;
import tools.logging.ColumnHeader;
import tools.logging.Folder;
import tools.logging.LogFile.Frequency;
import tools.logging.LoggerXLSX;
import tools.math.Statistics;
import tools.types.FuelName;
import tools.types.Unit;

/**
 * Class that writes results and statistics in an Excel spread sheet in order to
 * validate the model results more easily than searching in different log files.
 * 
 * @author Florian Zimmermann
 *
 */
public class Validation {
	private enum StatisticAttribute {
		AVERAGE_ARITHMETIC,
		AVERAGE_WEIGHTED,
		MINIMUM,
		MEDIAN,
		MAXIMUM,
		MAE,
		MAPE,
		RSME,
		VARIANCE,
		STANDARD_DEVIATION,
		SUM;
	}

	private enum ValidationCase {
		SIMULATED;
	}

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Validation.class.getName());

	/** Reference to the MarketAreas in the PowerMarkets model */
	private Set<MarketArea> marketAreas;

	private int logID = -1;
	private int year;
	// Price statistics for each MarketArea and year. Year, marketArea, Case,
	// Statstic, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsPrices = new ConcurrentHashMap<>();
	// Correlation between simulated and historical prices for each MarketArea
	// and year. Year, marketArea, Value
	private final Map<Integer, Map<MarketArea, Float>> correlationPrices = new ConcurrentHashMap<>();

	// Production statistics for each MarketArea and year. Year, marketArea,
	// Case, Statstic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Map<FuelName, Float>>>>> statisticsProduction = new ConcurrentHashMap<>();

	// Carbon statistics for each MarketArea and year. Year, marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsCarbon = new ConcurrentHashMap<>();

	// Exchange exogenous statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsExchangeExogenous = new ConcurrentHashMap<>();

	// Exchange endogenous statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsExchangeEndogenous = new ConcurrentHashMap<>();

	// Exchange endogenous statistics for each MarketArea and year. Year,
	// marketArea from, marketArea to Case, Statistic, Value
	private final Map<Integer, Map<MarketArea, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>>> statisticsExchangePerCountry = new ConcurrentHashMap<>();

	// Exchange total statistics for each MarketArea and year. Year, marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsExchangeTotal = new ConcurrentHashMap<>();

	// Peaker VoLL energy statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsPeakerVoLLEnergy = new ConcurrentHashMap<>();

	// Peaker total energy statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsPeakerTotalEnergy = new ConcurrentHashMap<>();

	// Loss of load expectation (LOLE) statistics for each MarketArea and year.
	// Year, marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Integer>>>> statisticsLossOfLoadExpectations = new ConcurrentHashMap<>();

	// Loss of load expectation (LOLE) VoLL statistics for each MarketArea and
	// year.
	// Year, marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Integer>>>> statisticsLossOfLoadExpectationsVoLL = new ConcurrentHashMap<>();

	// Curtailment energy statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsCurtailmentEnergy = new ConcurrentHashMap<>();

	// Curtailment energy statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsBalancing = new ConcurrentHashMap<>();

	// Demand energy statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsDemand = new ConcurrentHashMap<>();
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsDemandEV = new ConcurrentHashMap<>();
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsDemandHydrogen = new ConcurrentHashMap<>();
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsStorageCharge = new ConcurrentHashMap<>();
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsStorageDischarge = new ConcurrentHashMap<>();

	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsCostsDemand = new ConcurrentHashMap<>();
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsCostsDemandEV = new ConcurrentHashMap<>();
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsCostsDemandHydrogen = new ConcurrentHashMap<>();

	// Curtailment hours statistics for each MarketArea and year. Year,
	// marketArea,
	// Case, Statistic, FuelName, Value
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Integer>>>> statisticsCurtailmentHours = new ConcurrentHashMap<>();

	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Integer>>>> statisticsStrategicReserveHours = new ConcurrentHashMap<>();
	private final Map<Integer, Map<MarketArea, Map<ValidationCase, Map<StatisticAttribute, Float>>>> statisticsStrategicReserveEnergy = new ConcurrentHashMap<>();

	public Validation(PowerMarkets model, int year) {
		try {
			marketAreas = model.getMarketAreas();
			this.year = year;
			// first calcualte Data day Ahead prices
			validateDayAheadPrices();

			// Production and emissions
			validateProduction();
			validateCarbon();
			validateCurtailment();
			validateExchange();
			validatePeaker();
			validateBalancing();
			validateDemand();
			validateCosts();
			validateStorage();
			validateExchangePerCountry();

			// Write in an excel worksheet
			logInitialize();
			// consolidate the worksheets
			log();
			// Write whole excel table
			LoggerXLSX.close(logID);
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private Map<StatisticAttribute, Float> addStatistics(List<Float> valuesToAnalyse) {
		final Map<StatisticAttribute, Float> values = new ConcurrentHashMap<>();
		values.put(StatisticAttribute.AVERAGE_ARITHMETIC, Statistics.calcAvg(valuesToAnalyse));
		values.put(StatisticAttribute.MEDIAN, Statistics.calcMedian(valuesToAnalyse));
		values.put(StatisticAttribute.MINIMUM, Statistics.calcMin(valuesToAnalyse));
		values.put(StatisticAttribute.MAXIMUM, Statistics.calcMax(valuesToAnalyse));
		values.put(StatisticAttribute.VARIANCE, Statistics.calcVar(valuesToAnalyse));
		values.put(StatisticAttribute.STANDARD_DEVIATION, Statistics.calcStDev(valuesToAnalyse));
		return values;
	}

	private Map<ValidationCase, Map<StatisticAttribute, Float>> initializeMapsValidationCase() {
		final Map<ValidationCase, Map<StatisticAttribute, Float>> map = new ConcurrentHashMap<>();
		for (final ValidationCase validationCase : ValidationCase.values()) {
			map.put(validationCase, new ConcurrentHashMap<>());
		}
		return map;
	}

	private void log() {
		try {

			for (final Integer year : statisticsPrices.keySet()) {
				for (final MarketArea area : marketAreas) {
					final List<Object> values = new ArrayList<>();
					// year
					values.add(year);
					// Market Area
					values.add(area);
					// And the different Values
					// Prices
					for (final StatisticAttribute attribute : StatisticAttribute.values()) {
						for (final ValidationCase validationCase : ValidationCase.values()) {

							if (statisticsPrices.get(year).get(area).get(validationCase)
									.containsKey(attribute)) {
								values.add(statisticsPrices.get(year).get(area).get(validationCase)
										.get(attribute));
							} else {
								values.add("-");
							}
						}
					}
					// correlation
					if (correlationPrices.get(year).containsKey(area)) {
						values.add(correlationPrices.get(year).get(area));
					} else {
						values.add("-");
					}

					// Production
					// And the different Values
					for (final FuelName fuelName : FuelName.values()) {
						for (final ValidationCase validationCase : ValidationCase.values()) {
							if (statisticsProduction.get(year).get(area).get(validationCase)
									.containsKey(StatisticAttribute.SUM)) {
								// In GWh
								values.add(
										statisticsProduction.get(year).get(area).get(validationCase)
												.get(StatisticAttribute.SUM).get(fuelName) / 1000);
							} else {
								values.add("-");
							}
						}
					}

					// Exchange
					// And the different Values

					for (final ValidationCase validationCase : ValidationCase.values()) {
						if (statisticsExchangeEndogenous.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsExchangeEndogenous.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						if (statisticsExchangeExogenous.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsExchangeExogenous.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						if (statisticsExchangeTotal.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsExchangeTotal.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
					}

					// Carbon
					// And the different Values
					for (final ValidationCase validationCase : ValidationCase.values()) {
						if (statisticsCarbon.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsCarbon.get(year).get(area).get(validationCase)
									.get(StatisticAttribute.SUM));
						} else {
							values.add("-");
						}
					}

					// Curtailment
					// And the different Values

					for (final ValidationCase validationCase : ValidationCase.values()) {
						if (statisticsCurtailmentEnergy.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsCurtailmentEnergy.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						if (statisticsCurtailmentHours.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsCurtailmentHours.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM));
						} else {
							values.add("-");
						}
					}

					// Balancing

					for (final ValidationCase validationCase : ValidationCase.values()) {
						if (statisticsBalancing.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsBalancing.get(year).get(area).get(validationCase)
									.get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
					}
					// Demand
					for (final ValidationCase validationCase : ValidationCase.values()) {
						// demand sole
						if (statisticsDemand.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsDemand.get(year).get(area).get(validationCase)
									.get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						// Hydrogen
						if (statisticsDemandHydrogen.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsDemandHydrogen.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						// EV
						if (statisticsDemandEV.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsDemandEV.get(year).get(area).get(validationCase)
									.get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
					}
					// Storage
					for (final ValidationCase validationCase : ValidationCase.values()) {
						// charge
						if (statisticsStorageCharge.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsStorageCharge.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						// discharge
						if (statisticsStorageDischarge.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsStorageDischarge.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
					}

					// Costs
					for (final ValidationCase validationCase : ValidationCase.values()) {
						// demand sole
						if (statisticsCostsDemand.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsCostsDemand.get(year).get(area).get(validationCase)
									.get(StatisticAttribute.SUM) / 1000000);
						} else {
							values.add("-");
						}
						// Hydrogen
						if (statisticsCostsDemandHydrogen.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsCostsDemandHydrogen.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000000);
						} else {
							values.add("-");
						}
						// EV
						if (statisticsCostsDemandEV.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsCostsDemandEV.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000000);
						} else {
							values.add("-");
						}
					}

					// Strategic reserve
					for (final ValidationCase validationCase : ValidationCase.values()) {
						if (statisticsStrategicReserveEnergy.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsStrategicReserveEnergy.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						if (statisticsStrategicReserveHours.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsStrategicReserveHours.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM));
						} else {
							values.add("-");
						}
					}

					// Peaker
					// And the different Values

					for (final ValidationCase validationCase : ValidationCase.values()) {

						if (statisticsPeakerVoLLEnergy.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsPeakerVoLLEnergy.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}
						if (statisticsPeakerTotalEnergy.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsPeakerTotalEnergy.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM) / 1000);
						} else {
							values.add("-");
						}

						if (statisticsLossOfLoadExpectationsVoLL.get(year).get(area)
								.get(validationCase).containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsLossOfLoadExpectationsVoLL.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM));
						} else {
							values.add("-");
						}
						if (statisticsLossOfLoadExpectations.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsLossOfLoadExpectations.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM));
						} else {
							values.add("-");
						}

						// LOLP
						if (statisticsLossOfLoadExpectationsVoLL.get(year).get(area)
								.get(validationCase).containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsLossOfLoadExpectationsVoLL.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM)
									/ Date.HOURS_PER_YEAR);
						} else {
							values.add("-");
						}
						if (statisticsLossOfLoadExpectations.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsLossOfLoadExpectations.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM)
									/ Date.HOURS_PER_YEAR);
						} else {
							values.add("-");
						}
						if (statisticsPeakerVoLLEnergy.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsPeakerVoLLEnergy.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM)
									/ Date.HOURS_PER_YEAR);
						} else {
							values.add("-");
						}
						if (statisticsPeakerTotalEnergy.get(year).get(area).get(validationCase)
								.containsKey(StatisticAttribute.SUM)) {
							values.add(statisticsPeakerTotalEnergy.get(year).get(area)
									.get(validationCase).get(StatisticAttribute.SUM)
									/ Date.HOURS_PER_YEAR);
						} else {
							values.add("-");
						}
					}

					// Exchange Flows
					for (final MarketArea toMarketArea : marketAreas) {
						for (final ValidationCase validationCase : ValidationCase.values()) {
							// demand sole
							if (!area.equals(toMarketArea) && statisticsExchangePerCountry.get(year)
									.get(area).get(toMarketArea).get(validationCase)
									.containsKey(StatisticAttribute.SUM)) {
								values.add(statisticsExchangePerCountry.get(year).get(area)
										.get(toMarketArea).get(validationCase)
										.get(StatisticAttribute.SUM) / 1000);
							} else {
								values.add("-");
							}
						}
					}

					LoggerXLSX.writeLine(logID, values);
				}
			}
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Initializes the file at the beginning of each year by setting the file
	 * name, title line and unit line in the xslx file.
	 */
	private void logInitialize() {
		final Folder folder = Folder.MARKET_COUPLING;
		final String fileName = "0_Validation_" + Date.getYear();

		List<ColumnHeader> titleLine;
		final String description = "Validation of the simulated prices of all market areas";

		titleLine = new ArrayList<>();
		titleLine.add(new ColumnHeader("Year", Unit.NONE));
		titleLine.add(new ColumnHeader("Market Area", Unit.NONE));
		// Prices
		for (final StatisticAttribute attribute : StatisticAttribute.values()) {
			for (final ValidationCase validationCase : ValidationCase.values()) {

				titleLine.add(new ColumnHeader(validationCase + "_price_" + attribute,
						Unit.ENERGY_PRICE));
			}
		}
		// correlation
		titleLine.add(new ColumnHeader("Correlation_prices_historical_simulated", Unit.NONE));

		// Production
		for (final FuelName fuelName : FuelName.values()) {
			for (final ValidationCase validationCase : ValidationCase.values()) {

				titleLine.add(new ColumnHeader(
						validationCase + "_production_" + StatisticAttribute.SUM + "_" + fuelName,
						Unit.ENERGY_VOLUME_GIGAWATTHOUR));
			}
		}
		// Exchange
		for (final ValidationCase validationCase : ValidationCase.values()) {

			titleLine.add(new ColumnHeader(
					validationCase + "_Exchange_Endogenous_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));

			titleLine.add(new ColumnHeader(
					validationCase + "_Exchange_Exogenous_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));

			titleLine.add(
					new ColumnHeader(validationCase + "_Exchange_Total_" + StatisticAttribute.SUM,
							Unit.ENERGY_VOLUME_GIGAWATTHOUR));
		}
		// Carbon

		for (final ValidationCase validationCase : ValidationCase.values()) {

			titleLine.add(new ColumnHeader(validationCase + "_CO2_" + StatisticAttribute.SUM,
					Unit.TONS_CO2));
		}

		// Curtailment
		for (final ValidationCase validationCase : ValidationCase.values()) {

			titleLine.add(new ColumnHeader(
					validationCase + "_Curtailment_energy_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));
			titleLine.add(new ColumnHeader(
					validationCase + "_Curtailment_hours_" + StatisticAttribute.SUM, Unit.HOUR));
		}
		// Balancing
		for (final ValidationCase validationCase : ValidationCase.values()) {

			titleLine.add(new ColumnHeader(validationCase + "_Balancing_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));
		}
		// Demand
		for (final ValidationCase validationCase : ValidationCase.values()) {

			titleLine.add(new ColumnHeader(validationCase + "_Demand_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));
			titleLine.add(
					new ColumnHeader(validationCase + "_Demand_Hydrogen_" + StatisticAttribute.SUM,
							Unit.ENERGY_VOLUME_GIGAWATTHOUR));
			titleLine.add(new ColumnHeader(validationCase + "_Demand_EV_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));
		}
		// Storage
		for (final ValidationCase validationCase : ValidationCase.values()) {

			titleLine.add(
					new ColumnHeader(validationCase + "_Storage_Charge_" + StatisticAttribute.SUM,
							Unit.ENERGY_VOLUME_GIGAWATTHOUR));
			titleLine.add(new ColumnHeader(
					validationCase + "_Storage_Discharge_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));
		}
		// Costs
		for (final ValidationCase validationCase : ValidationCase.values()) {

			titleLine.add(
					new ColumnHeader(validationCase + "_Costs_Demand_" + StatisticAttribute.SUM,
							Unit.CURRENCY_MILLION));
			titleLine.add(new ColumnHeader(
					validationCase + "_Costs_Demand_Hydrogen" + StatisticAttribute.SUM,
					Unit.CURRENCY_MILLION));
			titleLine.add(
					new ColumnHeader(validationCase + "_Costs_Demand_EV_" + StatisticAttribute.SUM,
							Unit.CURRENCY_MILLION));
		}
		// Strategic reserve
		for (final ValidationCase validationCase : ValidationCase.values()) {
			titleLine.add(new ColumnHeader(
					validationCase + "_Strategic_Reserve_energy_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));
			titleLine.add(new ColumnHeader(
					validationCase + "_Strategic_Reserve_hours_" + StatisticAttribute.SUM,
					Unit.HOUR));

		}
		// Peaker
		for (final ValidationCase validationCase : ValidationCase.values()) {
			// Energy not served

			titleLine.add(new ColumnHeader(
					validationCase + "_Peaker_VoLL_energy_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));

			titleLine.add(new ColumnHeader(
					validationCase + "_Peaker_Total_energy_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME_GIGAWATTHOUR));

			titleLine.add(new ColumnHeader(validationCase + "_LOLE_VoLL_" + StatisticAttribute.SUM,
					Unit.HOUR));

			titleLine.add(new ColumnHeader(validationCase + "_LOLE_" + StatisticAttribute.SUM,
					Unit.HOUR));

			titleLine.add(new ColumnHeader(validationCase + "_LOLP_VoLL_" + StatisticAttribute.SUM,
					Unit.PERCENTAGE));

			titleLine.add(new ColumnHeader(validationCase + "_LOLP_" + StatisticAttribute.SUM,
					Unit.PERCENTAGE));

			titleLine.add(new ColumnHeader(
					validationCase + "_Expected_Energy_not_Served_VoLL_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME));
			titleLine.add(new ColumnHeader(
					validationCase + "_Expected_Energy_not_Served_" + StatisticAttribute.SUM,
					Unit.ENERGY_VOLUME));

		}

		// For endogenous flows between each market area
		for (final MarketArea marketArea : marketAreas) {
			for (final ValidationCase validationCase : ValidationCase.values()) {
				titleLine.add(
						new ColumnHeader(validationCase + "_Flow_to_" + marketArea.getInitials()
								+ "_" + StatisticAttribute.SUM, Unit.ENERGY_VOLUME_GIGAWATTHOUR));
			}
		}

		logID = LoggerXLSX.newLogObject(folder, fileName, description, titleLine, "",
				Frequency.HOURLY, "#,##0.00");
	}

	private void validateBalancing() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// Energy
			if (!statisticsBalancing.containsKey(yearOfAverage)) {
				statisticsBalancing.put(yearOfAverage, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsBalancing.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsBalancing.get(yearOfAverage).get(marketArea).put(validationCase,
								new ConcurrentHashMap<>());
					}

					statisticsBalancing.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED)
							.put(StatisticAttribute.SUM, Math.abs(marketArea.getBalanceDayAhead()
									.getBalanceYearlySum(yearOfAverage)));

				});
			}
		}
	}

	private void validateCarbon() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;
			if (!statisticsCarbon.containsKey(yearOfAverage)) {
				statisticsCarbon.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsCarbon.get(yearOfAverage).put(marketArea, new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsCarbon.get(yearOfAverage).get(marketArea).put(validationCase,
								new ConcurrentHashMap<>());
					}

					statisticsCarbon.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).put(StatisticAttribute.SUM, marketArea
									.getCarbonEmissions().getEmissionsYearly(yearOfAverage));

				});
			}
		}
	}

	private void validateCosts() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// Demand
			if (!statisticsCostsDemand.containsKey(yearOfAverage)) {
				statisticsCostsDemand.put(yearOfAverage, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsCostsDemand.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsCostsDemand.get(yearOfAverage).get(marketArea).put(validationCase,
								new ConcurrentHashMap<>());
					}
					float costsDemand = 0;
					for (int hourOfYear = 0; hourOfYear < Date.HOURS_PER_YEAR; hourOfYear++) {
						costsDemand += marketArea.getDemandData().getHourlyDemand(yearOfAverage,
								hourOfYear)
								* marketArea.getElectricityResultsDayAhead()
										.getHourlyPriceOfYear(yearOfAverage, hourOfYear);

					}
					statisticsCostsDemand.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).put(StatisticAttribute.SUM, costsDemand);

				});
			}

		}
	}

	private void validateCurtailment() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// Energy
			if (!statisticsCurtailmentEnergy.containsKey(yearOfAverage)) {
				statisticsCurtailmentEnergy.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsCurtailmentEnergy.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsCurtailmentEnergy.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}

					statisticsCurtailmentEnergy.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED)
							.put(StatisticAttribute.SUM, Math.abs(marketArea.getBalanceDayAhead()
									.getCurtailmentYearlySum(yearOfAverage)));

				});
			}

			// Hours
			if (!statisticsCurtailmentHours.containsKey(yearOfAverage)) {
				statisticsCurtailmentHours.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsCurtailmentHours.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsCurtailmentHours.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}

					statisticsCurtailmentHours.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).put(StatisticAttribute.SUM, marketArea
									.getBalanceDayAhead().getCurtailmentYearlyHours(yearOfAverage));

				});
			}
		}
	}

	private void validateDayAheadPrices() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;
			if (!statisticsPrices.containsKey(yearOfAverage)) {
				statisticsPrices.put(yearOfAverage, new ConcurrentHashMap<>());
				correlationPrices.put(yearOfAverage, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {

					final List<Float> dayAheadPricesSimulated = Collections
							.unmodifiableList(marketArea.getElectricityResultsDayAhead()
									.getYearlyPrices(yearOfAverage));

					statisticsPrices.get(yearOfAverage).put(marketArea, new ConcurrentHashMap<>());
					statisticsPrices.get(yearOfAverage).get(marketArea)
							.putAll(initializeMapsValidationCase());

					statisticsPrices.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).put(StatisticAttribute.AVERAGE_WEIGHTED,
									marketArea.getElectricityResultsDayAhead()
											.getYearlyAverageVolumeWeightedPrice(yearOfAverage));

					// Simulated
					statisticsPrices.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED)
							.putAll(addStatistics(dayAheadPricesSimulated));

					// Volume weighted
				});
			}
		}
		// Record number of values for historical

	}

	private void validateDemand() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// Demand
			if (!statisticsDemand.containsKey(yearOfAverage)) {
				statisticsDemand.put(yearOfAverage, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsDemand.get(yearOfAverage).put(marketArea, new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsDemand.get(yearOfAverage).get(marketArea).put(validationCase,
								new ConcurrentHashMap<>());
					}
					statisticsDemand.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).put(StatisticAttribute.SUM, Math.abs(
									marketArea.getDemandData().getDemandYearlySum(yearOfAverage)));

				});
			}

		}
	}

	private void validateExchange() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// Endogenous
			if (!statisticsExchangeEndogenous.containsKey(yearOfAverage)) {
				statisticsExchangeEndogenous.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsExchangeEndogenous.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsExchangeEndogenous.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
				});
			}
			// Exoginous
			if (!statisticsExchangeExogenous.containsKey(yearOfAverage)) {
				statisticsExchangeExogenous.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsExchangeExogenous.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsExchangeExogenous.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
				});
			}
			// Total
			if (!statisticsExchangeTotal.containsKey(yearOfAverage)) {
				statisticsExchangeTotal.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsExchangeTotal.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsExchangeTotal.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
				});
			}
			marketAreas.parallelStream().forEach(marketArea -> {

				final float exchangeExogenous = marketArea.getExchange()
						.calculateYearlyExchangeSum(yearOfAverage);

				final float exchangeEndogenous = (float) (marketArea.isMarketCoupling()
						? marketArea.getMarketCouplingOperator().getExchangeFlows()
								.getYearlyFlowByMarketArea(marketArea, yearOfAverage)
						: 0f);

				statisticsExchangeEndogenous.get(yearOfAverage).get(marketArea)
						.get(ValidationCase.SIMULATED)
						.put(StatisticAttribute.SUM, exchangeEndogenous);
				statisticsExchangeExogenous.get(yearOfAverage).get(marketArea)
						.get(ValidationCase.SIMULATED)
						.put(StatisticAttribute.SUM, exchangeExogenous);
				statisticsExchangeTotal.get(yearOfAverage).get(marketArea)
						.get(ValidationCase.SIMULATED)
						.put(StatisticAttribute.SUM, exchangeExogenous + exchangeEndogenous);

			});
		}
	}

	private void validateExchangePerCountry() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// Add cases
			if (!statisticsExchangePerCountry.containsKey(yearOfAverage)) {
				statisticsExchangePerCountry.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(fromMarketArea -> {
					statisticsExchangePerCountry.get(yearOfAverage).put(fromMarketArea,
							new ConcurrentHashMap<>());

					for (final MarketArea toMarketArea : marketAreas) {
						if (!fromMarketArea.equals(toMarketArea)) {
							statisticsExchangePerCountry.get(yearOfAverage).get(fromMarketArea)
									.put(toMarketArea, new ConcurrentHashMap<>());
							for (final ValidationCase validationCase : ValidationCase.values()) {
								statisticsExchangePerCountry.get(yearOfAverage).get(fromMarketArea)
										.get(toMarketArea)
										.put(validationCase, new ConcurrentHashMap<>());
							}
						}
					}
				});
			}
			// calculate sum
			marketAreas.parallelStream().forEach(fromMarketArea -> {
				for (final MarketArea toMarketArea : marketAreas) {
					if (!fromMarketArea.equals(toMarketArea)) {
						final float exchangeFlow = (float) fromMarketArea
								.getMarketCouplingOperator().getExchangeFlows()
								.getFlowSumYear(fromMarketArea, toMarketArea, yearOfAverage);

						statisticsExchangePerCountry.get(yearOfAverage).get(fromMarketArea)
								.get(toMarketArea).get(ValidationCase.SIMULATED)
								.put(StatisticAttribute.SUM, exchangeFlow);
					}
				}
			});
		}
	}

	private void validatePeaker() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// Peaker total Energy
			if (!statisticsPeakerTotalEnergy.containsKey(yearOfAverage)) {
				statisticsPeakerTotalEnergy.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsPeakerTotalEnergy.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsPeakerTotalEnergy.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
				});
			}
			// Peaker VoLL Energy
			if (!statisticsPeakerVoLLEnergy.containsKey(yearOfAverage)) {
				statisticsPeakerVoLLEnergy.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsPeakerVoLLEnergy.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsPeakerVoLLEnergy.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
				});
			}

			// LOLE VoLL
			if (!statisticsLossOfLoadExpectationsVoLL.containsKey(yearOfAverage)) {
				statisticsLossOfLoadExpectationsVoLL.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsLossOfLoadExpectationsVoLL.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsLossOfLoadExpectationsVoLL.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
				});
			}
			// LOLE Total
			if (!statisticsLossOfLoadExpectations.containsKey(yearOfAverage)) {
				statisticsLossOfLoadExpectations.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsLossOfLoadExpectations.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsLossOfLoadExpectations.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
				});
			}

		}
	}

	private void validateProduction() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;
			if (!statisticsProduction.containsKey(yearOfAverage)) {
				statisticsProduction.put(year, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsProduction.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsProduction.get(yearOfAverage).get(marketArea).put(validationCase,
								new ConcurrentHashMap<>());
					}
					statisticsProduction.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED)
							.put(StatisticAttribute.SUM, new ConcurrentHashMap<>());

					final Map<FuelName, Float> productionSumSimulated = new ConcurrentHashMap<>();
					for (final FuelName fuelName : FuelName.values()) {
						if (fuelName.isRenewableType()) {
							productionSumSimulated.put(fuelName, marketArea.getManagerRenewables()
									.getRenewableLoadYearlySum(fuelName, yearOfAverage));
						} else if (fuelName == FuelName.HYDRO_PUMPED_STORAGE) {
							productionSumSimulated.put(fuelName,
									marketArea.getElectricityProduction()
											.getElectricityPumpedStorageYearlySum(yearOfAverage));
						} else {
							productionSumSimulated.put(fuelName,
									marketArea.getElectricityProduction()
											.getElectricityYearlySum(fuelName, yearOfAverage));
						}
					}
					// Simulated
					statisticsProduction.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).get(StatisticAttribute.SUM)
							.putAll(Collections.unmodifiableMap(productionSumSimulated));
					// Volume weighted
				});
			}
		}
		// Record number of values for historical

	}

	private void validateStorage() {
		for (int year = Date.getStartYear(); year <= this.year; year++) {
			// stream needs a final variable
			final int yearOfAverage = year;

			// charge
			if (!statisticsStorageCharge.containsKey(yearOfAverage)) {
				statisticsStorageCharge.put(yearOfAverage, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsStorageCharge.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsStorageCharge.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
					statisticsStorageCharge.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).put(StatisticAttribute.SUM,
									Math.abs(marketArea.getElectricityProduction()
											.getElectricityPumpedStorageChargeYearlySum(
													yearOfAverage)));
				});
			}
			// discharge
			if (!statisticsStorageDischarge.containsKey(yearOfAverage)) {
				statisticsStorageDischarge.put(yearOfAverage, new ConcurrentHashMap<>());

				marketAreas.parallelStream().forEach(marketArea -> {
					statisticsStorageDischarge.get(yearOfAverage).put(marketArea,
							new ConcurrentHashMap<>());
					for (final ValidationCase validationCase : ValidationCase.values()) {
						statisticsStorageDischarge.get(yearOfAverage).get(marketArea)
								.put(validationCase, new ConcurrentHashMap<>());
					}
					statisticsStorageDischarge.get(yearOfAverage).get(marketArea)
							.get(ValidationCase.SIMULATED).put(StatisticAttribute.SUM,
									Math.abs(marketArea.getElectricityProduction()
											.getElectricityPumpedStorageDischargeYearlySum(
													yearOfAverage)));
				});
			}
		}
	}
}
