package tools.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


import tools.dataimport.parameters.ENTSOEAreas;

/**
 * Lists the different market areas to be simulated.<br>
 * <br>
 * Most market areas are equal to countries. Market area names and
 * initials should be based on the ISO 3166-1 alpha-2 code (see e.g.
 * <a href="http://en.wikipedia.org/wiki/ISO_3166-2"
 * >http://en.wikipedia.org/wiki/ISO_3166-2</a>) or on ENTSO-E abbreviations.
 * 
 * @author PR, modified by Florian Zimmermann
 */
public enum MarketAreaType {
	MARKET_AREA_1(
			//Abbreviation
			Locale.of("de", "AT"),
			// Corresponding area
			ENTSOEAreas.AT,
			// Corresponding bidding zone (e.g., DE-LU)
			ENTSOEAreas.AT,
			// Corresponding water storage level in case of data unavailabiltity
			ENTSOEAreas.AT,
			"skyblue")
	;

		/** Map to provide reference to MarketAreaType from name */
	private static Map<String, MarketAreaType> nameToMarketAreaTypeMapping;

	public static MarketAreaType getMarketAreaTypeFromEntsoe(final ENTSOEAreas area) {
		for (final MarketAreaType marketArea : MarketAreaType.values()) {
			if (marketArea.getArea().contains(area)) {
				return marketArea;
			}
		}
		return null;
	}

	public static MarketAreaType getMarketAreaTypeFromInitials(final String initials) {
		for (final MarketAreaType marketArea : MarketAreaType.values()) {
			if (marketArea.getInitials().equals(initials)) {
				return marketArea;
			}
		}
		return null;
	}

	public static MarketAreaType getMarketAreaTypeFromName(final String name) {
		MarketAreaType marketAreaTypeTemp = null;

		if (nameToMarketAreaTypeMapping == null) {
			initNameToMarketAreaTypeMapping();
		}
		marketAreaTypeTemp = nameToMarketAreaTypeMapping.get(name.toUpperCase(Locale.ENGLISH));

		return marketAreaTypeTemp;
	}

	private static void initNameToMarketAreaTypeMapping() {
		nameToMarketAreaTypeMapping = new HashMap<>();
		for (final MarketAreaType marketAreaType : values()) {
			nameToMarketAreaTypeMapping.put(marketAreaType.toString().toUpperCase(Locale.ENGLISH),
					marketAreaType);
		}
	}

	/** Corresponding ENTSOE market area */
	private Set<ENTSOEAreas> areas = new HashSet<>();

	/** ENTSOE price zone like DE, LU, AT */
	private ENTSOEAreas biddingZone;

	/** ENTSOE storage value zone */
	private ENTSOEAreas storageZone;

	/** Instance of Locale that corresponds to the market area */
	private Locale locale;

	/** Initials, safe as string in order to safe computation time */
	private String initals;

	/** Color that corresponds to the market area for plotting */
	private String color;

	/**
	 * Constructor locale Area for Grid data Price area
	 */
	private MarketAreaType(final Locale locale, final ENTSOEAreas area,
			final ENTSOEAreas biddingZone, final ENTSOEAreas storageZone, String color) {
		this.locale = locale;
		areas.add(area);
		this.biddingZone = biddingZone;
		this.storageZone = storageZone;
		this.color = color;
		initals = locale.getCountry();
	}

	/**
	 * Constructor locale Area for Grid data Price area
	 */
	private MarketAreaType(final Locale locale, final Set<ENTSOEAreas> areas,
			final ENTSOEAreas biddingZone, final ENTSOEAreas storageZone, String color) {
		this.locale = locale;
		this.areas.addAll(areas);
		this.biddingZone = biddingZone;
		this.color = color;
		this.storageZone = storageZone;
		initals = locale.getCountry();
	}

	public Set<ENTSOEAreas> getArea() {
		return areas;
	}

	public ENTSOEAreas getBiddingZone() {
		return biddingZone;
	}

	public String getColor() {
		return color;
	}

	public String getInitials() {
		return initals;
	}

	public ENTSOEAreas getStorageZone() {
		return storageZone;
	}
}
