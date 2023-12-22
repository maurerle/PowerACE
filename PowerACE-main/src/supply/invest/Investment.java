package supply.invest;

import simulations.MarketArea;
import simulations.initialization.Settings;
import supply.powerplant.PlantOption;
/**
 * Represents an investment of a power plant with the corresponding plant option
 * and investor
 * 
 * @author Florian Zimmermann
 *
 */
public class Investment implements Comparable<Investment> {
	private InvestorNetValue investor;
	private PlantOption investmentOption;
	public Investment(InvestorNetValue investor, PlantOption investmentOption) {
		this.investor = investor;
		this.investmentOption = investmentOption;
	}
	@Override
	public int compareTo(Investment that) {
		if (getNetPresentValue() == that.getNetPresentValue()) {
			return 0;
		}
		if (getNetPresentValue() < that.getNetPresentValue()) {
			return 1;
		}
		return -1;
	}
	public PlantOption getInvestmentOption() {
		return investmentOption;
	}

	public InvestorNetValue getInvestor() {
		return investor;
	}

	public MarketArea getMarketArea() {
		return investor.getMarketArea();
	}
	public float getNetPresentValue() {
		return investmentOption.getNetPresentValue();
	}
	public boolean isEqualInvestment(Investment that) {
		if (Math.abs(getNetPresentValue()
				- that.getNetPresentValue()) < Settings.FLOATING_POINT_TOLERANCE) {
			if (Math.abs(getInvestmentOption().getNetCapacity() - that.getInvestmentOption()
					.getNetCapacity()) < Settings.FLOATING_POINT_TOLERANCE) {
				return true;
			}
		}

		return false;
	}

	@Override
	public String toString() {
		return "Investment " + investmentOption + ", investor " + investor;
	}

}