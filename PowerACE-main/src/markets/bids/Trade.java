package markets.bids;

import simulations.scheduling.Date;

/**
 * Class that contains information about made trades. hour of year
 */
public class Trade {

	public final int day;
	public final int hour;
	public final int hourOfYear;
	public final float price;
	public final float volume;
	public final int year;

	/**
	 * @param volume
	 * @param price
	 * @param hour
	 *            [0,Hours_PER_DAY]
	 * 
	 */
	public Trade(float volume, float price, int hour) {
		this.volume = volume;
		this.price = price;
		this.hour = hour;
		day = Date.getDayOfYear();
		year = Date.getYear();
		hourOfYear = ((day - 1) * Date.HOURS_PER_DAY) + hour;
	}

}