package tools.other;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class to translate plot descriptions.
 *
 * @author
 *
 */
public final class Localisation {

	public enum Language {
		DE,
		EN;
	}

	private static Map<String, Map<Language, String>> localisationStrings = new HashMap<>();

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Localisation.class.getName());

	/**
	 * Standard language which text is translated into.
	 */
	private static Language standard = Language.DE;
	static {
		// Fuels
		Localisation.addLocalisation("LIGNITE", "Braunkohle", Language.DE);
		Localisation.addLocalisation("COAL", "Steinkohle", Language.DE);
		Localisation.addLocalisation("URANIUM", "Uran", Language.DE);
		Localisation.addLocalisation("GAS", "Gas", Language.DE);
		Localisation.addLocalisation("OIL", "�l", Language.DE);
		Localisation.addLocalisation("RENEWABLE", "Erneuerbare", Language.DE);
		Localisation.addLocalisation("STORAGE", "Speicher", Language.DE);
		Localisation.addLocalisation("STORAGE", "Storage", Language.EN);
		Localisation.addLocalisation("WASTE", "M�ll", Language.DE);
		Localisation.addLocalisation("WASTE", "Waste", Language.EN);
		Localisation.addLocalisation("WIND_ONSHORE", "Wind an Land", Language.DE);
		Localisation.addLocalisation("WIND_ONSHORE", "Wind Onshore", Language.EN);
		Localisation.addLocalisation("WIND_OFFSHORE", "Wind auf See", Language.DE);
		Localisation.addLocalisation("WIND_OFFSHORE", "Wind Offshore", Language.EN);
		Localisation.addLocalisation("WATER", "Wasser", Language.DE);
		Localisation.addLocalisation("WATER", "Water", Language.EN);
		Localisation.addLocalisation("BIOGAS", "Biogas", Language.DE);
		Localisation.addLocalisation("BIOGAS", "Bio Gas", Language.EN);
		Localisation.addLocalisation("SOLAR", "Solar/PV", Language.DE);
		Localisation.addLocalisation("SOLAR", "Solar/PV", Language.EN);

		// Energy Conversions
		Localisation.addLocalisation("COMBINED_CYCLE", "GuD", Language.DE);
		Localisation.addLocalisation("CCGT", "GuD", Language.DE);
		Localisation.addLocalisation("CC", "GuD", Language.DE);
		Localisation.addLocalisation("GAS_TURBINE", "Gasturbine", Language.DE);
		Localisation.addLocalisation("STEAM_TURBINE", "Dampfturbine", Language.DE);
		Localisation.addLocalisation("ADIABATIC_COMPRESSED_AIR", "A-CAES", Language.DE);
		Localisation.addLocalisation("LITHIUM_ION_BATTERY", "Li-Ion Batterie", Language.DE);
		Localisation.addLocalisation("PUMPED_HYDRO", "Pumpspeicher", Language.DE);
		Localisation.addLocalisation("REDOX_FLOW_BATTERY", "Redox-flow Batterie", Language.DE);

		// Market areas
		Localisation.addLocalisation("Germany", "Deutschland", Language.DE);
		Localisation.addLocalisation("France", "Frankreich", Language.DE);
		Localisation.addLocalisation("Belgium", "Belgien", Language.DE);
		Localisation.addLocalisation("Netherlands", "Niederland", Language.DE);
		Localisation.addLocalisation("Switzerland", "Schweiz", Language.DE);

		// Plot titles
		Localisation.addLocalisation("Development of capacities in strategic reserve",
				"Kapazitaetsentwicklung in der Reserve", Language.DE);
		Localisation.addLocalisation("Investments and Decommissions", "Neubauten und Stilllegungen",
				Language.DE);
		Localisation.addLocalisation("Investments in Thermal Capacities", "Kraftwerksneubauten",
				Language.DE);
		Localisation.addLocalisation("Security Niveau for", "Sicherheitsniveau", Language.DE);
		Localisation.addLocalisation("SecurityLevel", "Sicherheitsniveau", Language.DE);
		Localisation.addLocalisation("Security Niveau including Exchanges", "inkl. Austausch",
				Language.DE);
		Localisation.addLocalisation("Security Niveau excluding Exchanges", "exkl. Austausch",
				Language.DE);
		Localisation.addLocalisation("Security Niveau including Exchanges Lower",
				"inkl. Austausch Minumum", Language.DE);
		Localisation.addLocalisation("Security Niveau including Exchanges Upper",
				"inkl. Austausch 10h Minimum", Language.DE);
		Localisation.addLocalisation("Security Niveau excluding Exchanges Lower",
				"exkl. Austausch Minimum", Language.DE);
		Localisation.addLocalisation("Security Niveau excluding Exchanges Upper",
				"exkl. Austausch 10h Minimum", Language.DE);
		Localisation.addLocalisation("Security Niveau excluding Balancing",
				"Keine Day-Ahead-Marktr�umung", Language.DE);
		Localisation.addLocalisation("Security Niveau including Balancing", "Sicherheitsniveau",
				Language.DE);
		Localisation.addLocalisation("Balancing Tertiary", "Minutenreserve", Language.DE);
		Localisation.addLocalisation("Balancing Secondary", "Sekundaerreserve", Language.DE);
		Localisation.addLocalisation("Balancing Primary", "Primaerreserve", Language.DE);

		Localisation.addLocalisation("Outcome of strategic reserve auctions",
				"Ergebnisse der Auktionen der Reserve", Language.DE);
		Localisation.addLocalisation("Strategic Reserve Auction", "Strategische Reserve Auktion",
				Language.DE);
		Localisation.addLocalisation("Strategic reserve bids", "Gebote Strategische Reserve",
				Language.DE);
		Localisation.addLocalisation("Strategic Reserve Capacities ",
				"Kapazitaeten Strategische Reserve", Language.DE);
		Localisation.addLocalisation("Strategic Reserve Total Costs ",
				"Gesamtkosten Strategische Reserve", Language.DE);
		Localisation.addLocalisation("Strategic Reserve Auction Yearly",
				"J�hrliche Auktion Strategische Reserve", Language.DE);
		Localisation.addLocalisation("Strategic Reserve Auction Results Total",
				"Ergebnisse von Auktionen Strategische Reserve", Language.DE);
		Localisation.addLocalisation("Total Costs", "Gesamtkosten", Language.DE);
		Localisation.addLocalisation("Yearly Costs", "Jaehrliche Kosten", Language.DE);
		Localisation.addLocalisation("Price developments", "Preisentwicklung", Language.DE);
		Localisation.addLocalisation("Income", "Einnahmen", Language.DE);
		Localisation.addLocalisation("Development of capacities", "Kapazit�tsentwicklung",
				Language.DE);
		Localisation.addLocalisation("Day-Ahead Price", "Day-Ahead Preis", Language.DE);
		Localisation.addLocalisation("Day-Ahead Price Historical", "Historischer Day-Ahead Preis",
				Language.DE);
		Localisation.addLocalisation("Day-Ahead Prices Avg.", "Day-Ahead Preise", Language.DE);
		Localisation.addLocalisation("Day-Ahead Price Peak", "Day-Ahead Preis Peak", Language.DE);
		Localisation.addLocalisation("Day-Ahead Price Off-Peak", "Day-Ahead Preis Off-Peak",
				Language.DE);
		Localisation.addLocalisation("Forward Price Average", "Forward Preis", Language.DE);
		Localisation.addLocalisation("Forward Price Average Price Cap", "Forward Preis gedeckelt",
				Language.DE);
		Localisation.addLocalisation("Price Duration Curve", "Preisdauerlinie", Language.DE);

		// Other
		Localisation.addLocalisation("Other", "Sonstige", Language.DE);
		Localisation.addLocalisation("Costs", "Kosten", Language.DE);
		Localisation.addLocalisation("Residual Load", "Residuale Last", Language.DE);
		Localisation.addLocalisation("Price", "Preis", Language.DE);
		Localisation.addLocalisation("Auction Volume", "Auktionsvolumen", Language.DE);
		Localisation.addLocalisation("Thermal Capacity", "Thermische Kapazit�t", Language.DE);
		Localisation.addLocalisation("Capacity Thermal Power Plants",
				"Kapazit�t thermische Kraftwerke", Language.DE);
		Localisation.addLocalisation("Capacity", "Kapazitaet", Language.DE);
		Localisation.addLocalisation("Year", "Jahr", Language.DE);
	}

	private static void addLocalisation(String original, String localised, Language language) {
		if (!localisationStrings.containsKey(original)) {
			localisationStrings.put(original, new HashMap<>());
		}
		localisationStrings.get(original).put(language, localised);
	}

	/**
	 * @param textEnglish
	 * @return expression in standard language
	 */
	public static String getLocalisation(String textEnglish) {
		return Localisation.getLocalisation(textEnglish, standard);
	}

	/**
	 * @param textEnglish
	 * @param language
	 * @return expression in the specific language
	 */
	public static String getLocalisation(String textEnglish, Language language) {

		// Text can only be translated from English to other languages
		if (language == Language.EN) {
			return textEnglish;
		}

		String localisedStrings;
		if (localisationStrings.get(textEnglish) == null) {
			logger.warn("Unable to translate text " + textEnglish);
			localisedStrings = textEnglish;
		} else {
			localisedStrings = localisationStrings.get(textEnglish).get(language);
		}

		return localisedStrings;
	}

}