package tools.dataimport.parameters;

import java.util.HashSet;
import java.util.Set;

/**
 * Types for the ENTSO-E API for automatic downloads.
 * 
 * A.10. Areas
 * 
 * BZ = Bidding Zone
 * 
 * BZA = Bidding Zone Aggregation
 * 
 * CA/CTA = Control Area
 * 
 * MBA = Market Balance Area
 * 
 * Boolean indicates an whole country, but no guaranty that this is true
 * 
 * BY, RU will be removed from the list soon, IS is not sending any data at the
 * moment because it is not mandatory for them TR Turkey will send data in the
 * near future and the configuration will be changed.
 * 
 * @author Florian Zimmermann
 * @see <a href=
 *      "https://transparency.entsoe.eu/content/static_content/Static%20content/web%20api/Guide.html">Source</a>
 */
public enum ENTSOEAreas
		implements
			ENTSOEParameter {

	AL(
			"10YAL-KESH-----5",
			"Albania, OST BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	AT(
			"10YAT-APG------L",
			"Austria, APG CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	BY_BZ_CA_MBA(
			"10Y1001A1001A51S",
			"Belarus BZ / CA / MBA",
			false,
			false,
			true,
			false,
			true,
			true),

	BE(
			"10YBE----------2",
			"Belgium, Elia BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	BA(
			"10YBA-JPCC-----D",
			"Bosnia Herzegovina, NOS BiH BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	BG(
			"10YCA-BULGARIA-R",
			"Bulgaria, ESO BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	BZ_CZ_DE_SK(
			"10YDOM-CZ-DE-SKK",
			"BZ CZ+DE+SK BZ / BZA",
			false,
			false,
			true,
			true,
			false,
			false),

	HR(
			"10YHR-HEP------M",
			"Croatia, HOPS BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	CWE(
			"10YDOM-REGION-1V",
			"CWE Region",
			false,
			false,
			false,
			false,
			false,
			false),

	CY(
			"10YCY-1001A0003J",
			"Cyprus, Cyprus TSO BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	CZ_CEPS(
			"10YCZ-CEPS-----N",
			"Czech Republic, CEPS BZ / CA/ MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	BZ_DE_AT_LU(
			"10Y1001A1001A63L",
			"DE-AT-LU BZ",
			false,
			false,
			true,
			false,
			false,
			false),

	MBA_DE_LU(
			"10Y1001A1001A82H",
			"DE-LU BZ / MBA",
			false,
			false,
			true,
			false,
			false,
			true),

	DK(
			"10Y1001A1001A65H",
			"Denmark",
			true,
			true,
			false,
			false,
			false,
			false),

	DK_1(
			"10YDK-1--------W",
			"DK1 BZ / MBA",
			false,
			true,
			true,
			false,
			false,
			true),

	DK_2(
			"10YDK-2--------M",
			"DK2 BZ / MBA",
			false,
			true,
			true,
			false,
			false,
			true),

	DK_ENERGINET(
			"10Y1001A1001A796",
			"Energinet CA",
			false,
			true,
			false,
			false,
			true,
			false),

	EE(
			"10Y1001A1001A39I",
			"Estonia, Elering BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	FI(
			"10YFI-1--------U",
			"Finland, Fingrid BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),
	MK(
			"10YMK-MEPSO----8",
			"Former Yugoslav Republic of Macedonia, MEPSO BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	FR(
			"10YFR-RTE------C",
			"France, RTE BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	DE(
			"10Y1001A1001A83F",
			"Germany",
			true,
			false,
			false,
			false,
			false,
			false),
	DE_50Hertz(
			"10YDE-VE-------2",
			"50Hertz CA, DE(50HzT) BZA",
			false,
			true,
			false,
			true,
			true,
			false),
	DE_AMPRION(
			"10YDE-RWENET---I",
			"Amprion CA",
			false,
			true,
			false,
			false,
			true,
			false),
	DE_TENNET(
			"10YDE-EON------1",
			"TenneT GER CA",
			false,
			true,
			false,
			false,
			true,
			false),

	DE_TRANSNET(
			"10YDE-ENBW-----N",
			"TransnetBW CA",
			false,
			true,
			false,
			false,
			true,
			false),
	GR(
			"10YGR-HTSO-----Y",
			"Greece, IPTO BZ / CA/ MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	HU(
			"10YHU-MAVIR----U",
			"Hungary, MAVIR CA / BZ / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	// IS("IS","Iceland",true),

	IE_SEM(
			"10Y1001A1001A59C",
			"Ireland (SEM) BZ / MBA",
			true,
			true,
			true,
			false,
			false,
			true),

	IE_EIRGRID(
			"10YIE-1001A00010",
			"Ireland, EirGrid CA",
			false,
			true,
			false,
			false,
			true,
			false),

	IT(
			"10YIT-GRTN-----B",
			"Italy, IT CA / MBA",
			true,
			false,
			false,
			false,
			true,
			true),

	IT_SACO_AC(
			"10Y1001A1001A885",
			"Italy_Saco_AC",
			false,
			false,
			true,
			false,
			false,
			false),

	IT_SACO_DC(
			"10Y1001A1001A893",
			"Italy_Saco_DC",
			false,
			false,
			true,
			false,
			false,
			false),

	IT_BRINDISI(
			"10Y1001A1001A699",
			"IT-Brindisi BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_CENTRE_NORTH(
			"10Y1001A1001A70O",
			"IT-Centre-North BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_CENTRE_SOUTH(
			"10Y1001A1001A71M",
			"IT-Centre-South BZ",
			false,
			true,
			true,
			false,
			false,
			false),
	IT_FOGGIA(
			"10Y1001A1001A72K",
			"IT-Foggia BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_GR(
			"10Y1001A1001A66F",
			"IT-GR BZ",
			false,
			false,
			true,
			false,
			false,
			false),

	IT_MACROZONE_NORTH(
			"10Y1001A1001A84D",
			"IT-MACROZONE NORTH MBA",
			false,
			false,
			false,
			false,
			false,
			true),

	IT_MACROZONE_SOUTH(
			"10Y1001A1001A85B",
			"IT-MACROZONE SOUTH MBA",
			false,
			false,
			false,
			false,
			false,
			true),

	IT_MALTA(
			"10Y1001A1001A877",
			"IT-Malta BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_NORTH(
			"10Y1001A1001A73I",
			"IT-North BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_NORTH_AT(
			"10Y1001A1001A80L",
			"IT-North-AT BZ",
			false,
			false,
			true,
			false,
			false,
			false),

	IT_NORTH_CH(
			"10Y1001A1001A68B",
			"IT-North-CH BZ",
			false,
			false,
			true,
			false,
			false,
			false),

	IT_NORTH_FR(
			"10Y1001A1001A81J",
			"IT-North-FR BZ",
			false,
			false,
			true,
			false,
			false,
			false),

	IT_NORTH_SI(
			"10Y1001A1001A67D",
			"IT-North-SI BZ",
			false,
			false,
			true,
			false,
			false,
			false),

	IT_PRIOLO(
			"10Y1001A1001A76C",
			"IT-Priolo BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_ROSSANO(
			"10Y1001A1001A77A",
			"IT-Rossano BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_SARDINIA(
			"10Y1001A1001A74G",
			"IT-Sardinia BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_SICILY(
			"10Y1001A1001A75E",
			"IT-Sicily BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	IT_SOUTH(
			"10Y1001A1001A788",
			"IT-South BZ",
			false,
			true,
			true,
			false,
			false,
			false),

	KALININGRAD(
			"10Y1001A1001A50U",
			"Kaliningrad BZ / CA / MBA",
			false,
			true,
			true,
			false,
			true,
			true),

	LV(
			"10YLV-1001A00074",
			"Latvia, AST BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	LT(
			"10YLT-1001A0008Q",
			"Lithuania, Litgrid BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	LU(
			"10YLU-CEGEDEL-NQ",
			"Luxembourg, CREOS CA",
			true,
			true,
			false,
			false,
			true,
			false),

	MT(
			"10Y1001A1001A93C",
			"Malta, Malta BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	CS(
			"10YCS-CG-TSO---S",
			"Montenegro, CGES BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	GB_NATIONAL_GRID(
			"10YGB----------A",
			"National Grid BZ / CA/ MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	NL_TENNET(
			"10YNL----------L",
			"Netherlands, TenneT NL BZ / CA/ MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	NO_1(
			"10YNO-1--------2",
			"NO1 BZ / MBA",
			false,
			true,
			true,
			false,
			false,
			true),

	NO_2(
			"10YNO-2--------T",
			"NO2 BZ / MBA",
			false,
			true,
			true,
			false,
			false,
			true),

	NO_3(
			"10YNO-3--------J",
			"NO3 BZ / MBA",
			false,
			true,
			true,
			false,
			false,
			true),

	NO_4(
			"10YNO-4--------9",
			"NO4 BZ / MBA",
			false,
			true,
			true,
			false,
			false,
			true),

	NO_5(
			"10Y1001A1001A48H",
			"NO5 BZ / MBA",
			false,
			true,
			true,
			false,
			false,
			true),

	NO(
			"10YNO-0--------C",
			"Norway, Norway MBA, Stattnet CA",
			true,
			false,
			false,
			false,
			true,
			true),

	PL_CZ(
			"10YDOM-1001A082L",
			"PL-CZ BZA / CA",
			false,
			false,
			false,
			true,
			true,
			false),

	PL(
			"10YPL-AREA-----S",
			"Poland, PSE SA BZ / BZA / CA / MBA",
			true,
			true,
			true,
			true,
			true,
			true),

	PT(
			"10YPT-REN------W",
			"Portugal, REN BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	// Update: MD("MD","Republic of Moldova",true),
	MD(
			"10Y1001A1001A990",
			"Republic of Moldova, Moldelectica BZ/CA/MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	RO(
			"10YRO-TEL------P",
			"Romania, Transelectrica BZ / CA/ MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	RU_BZ(
			"10Y1001A1001A49F",
			"Russia BZ / CA / MBA",
			false,
			true,
			true,
			false,
			true,
			true),

	// RU("RU","Russian Federation", true),
	// Only Germany uses SE Export instead of SE_1...4
	SE(
			"10YSE-1--------K",
			"Sweden, Sweden MBA, SvK CA",
			true,
			true,
			false,
			false,
			true,
			true),
	SE_1(
			"10Y1001A1001A44P",
			"SE1 BZ / MBA",
			false,
			false,
			true,
			false,
			false,
			true),

	SE_2(
			"10Y1001A1001A45N",
			"SE2 BZ / MBA",
			false,
			false,
			true,
			false,
			false,
			true),

	SE_3(
			"10Y1001A1001A46L",
			"SE3 BZ / MBA",
			false,
			false,
			true,
			false,
			false,
			true),

	SE_4(
			"10Y1001A1001A47J",
			"SE4 BZ / MBA",
			false,
			false,
			true,
			false,
			false,
			true),

	RS(
			"10YCS-SERBIATSOV",
			"Serbia, EMS BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	SK(
			"10YSK-SEPS-----K",
			"Slovakia, SEPS BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	SI(
			"10YSI-ELES-----O",
			"Slovenia, ELES BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),
	// Northern Ireland
	IE_SONI(
			"10Y1001A1001A016",
			"Northern Ireland, SONI CA",
			false,
			true,
			false,
			false,
			true,
			false),

	ES(
			"10YES-REE------0",
			"Spain, REE BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	CH(
			"10YCH-SWISSGRIDZ",
			"Switzerland, Swissgrid BZ / CA / MBA",
			true,
			true,
			true,
			false,
			true,
			true),

	// TR( "TR","Turkey",true),

	TR_BZ(
			"10YTR-TEIAS----W",
			"Turkey BZ / CA / MBA",
			false,
			true,
			true,
			false,
			true,
			true),

	UA(
			"10Y1001C--00003F",
			"Ukraine, Ukraine BZ, MBA",
			true,
			true,
			true,
			false,
			false,
			true),

	UA_Dob(
			"10Y1001A1001A869",
			"Ukraine-DobTPP CTA",
			false,
			true,
			false,
			false,
			true,
			false),

	UA_WEPS(
			"10YUA-WEPS-----0",
			"Ukraine BEI CTA",
			false,
			true,
			false,
			false,
			true,
			false),
	UA_IPS(
			"10Y1001C--000182",
			"Ukraine IPS CTA",
			false,
			true,
			false,
			false,
			true,
			false);


	private static Set<ENTSOEAreas> countries;
	public static ENTSOEAreas getAreaByCode(String entsoeCode) {
		for (final ENTSOEAreas area : ENTSOEAreas.values()) {
			if (area.getCode().equals(entsoeCode)) {
				return area;
			}
		}
		return null;
	}
	public static Set<ENTSOEAreas> getCountries() {
		if (countries == null) {
			initCountres();
		}
		return countries;
	}
	private static void initCountres() {
		countries = new HashSet<>();
		for (final ENTSOEAreas area : ENTSOEAreas.values()) {
			if (area.isCountry) {
				countries.add(area);
			}
		}
	}

	public static boolean isCountry(String entsoeCode) {
		if (countries == null) {
			initCountres();
		}
		for (final ENTSOEAreas area : countries) {
			if (area.getCode().equals(entsoeCode)) {
				return true;
			}
		}
		return false;
	}
	private String meaning;
	private String code;
	/**
	 * Country: respecting internationally recognized geo-political borders
	 * Country: United Kingdom, including Northern Ireland (UK)
	 * 
	 */
	private boolean isCountry;
	/**
	 * If the Entos-e area should be used for the calculation of exchange flows
	 */
	private boolean useForExchangeFlows;

	/**
	 * 
	 */
	private boolean biddingZoneAggregation;

	/**
	 * Bidding zone: the largest geographical area within which market
	 * participants are able to exchange energy without capacity allocation
	 * Bidding zone: Ireland (IE - SEM), which includes both Northern Ireland
	 * and the Republic of Ireland
	 */
	private boolean biddingZone;

	/**
	 * Control area: a coherent part of the interconnected system, operated by a
	 * single system operator. Control area: Germany (DE) has four control areas
	 * under the jurisdiction of four TSOs (50Hertz, Amprion, TenneT Germany,
	 * TransnetBW.
	 */
	private boolean controlArea;

	/**
	 * 
	 */
	private boolean marketBalanceArea;

	private ENTSOEAreas(String code, String meaning, boolean isCountry, boolean exchangeFlows,
			boolean biddingZone, boolean biddingZoneAggregation, boolean controlArea,
			boolean marketBalanceArea) {
		this.code = code;
		this.meaning = meaning;
		this.isCountry = isCountry;
		useForExchangeFlows = exchangeFlows;
		this.biddingZone = biddingZone;
		this.biddingZoneAggregation = biddingZoneAggregation;
		this.controlArea = controlArea;
		this.marketBalanceArea = marketBalanceArea;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getMeaning() {
		return meaning;
	}

	public boolean isBiddingZone() {
		return biddingZone;
	}
	public boolean isBiddingZoneAggregation() {
		return biddingZoneAggregation;
	}

	public boolean isControlArea() {
		return controlArea;
	}

	public boolean isCountry() {
		return isCountry;
	}

	public boolean isMarketBalanceArea() {
		return marketBalanceArea;
	}

	public boolean isUseForExchangeFlows() {
		return useForExchangeFlows;
	}

}
