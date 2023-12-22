package simulations.scheduling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;

import markets.operator.spot.MarketCouplingOperator;
import simulations.MarketArea;
import simulations.PowerMarkets;
import tools.other.Concurrency;

/**
 * Class to schedule different markets differentiating by market area and time
 * of execution.
 */
public class MarketScheduler {

	/** Collection of current task to be executed */
	private final Collection<Callable<Void>> currentTasks = new ArrayList<>();
	private MarketCouplingOperator dayAheadMarketCouplingOperator;
	private final PowerMarkets model;

	public MarketScheduler(PowerMarkets model) {
		this.model = model;
	}

	/** Create new market coupling operator */
	public void createNewMarketCouplingOperator() {
		dayAheadMarketCouplingOperator = new MarketCouplingOperator();
	}

	public void executeMarkets() {
		/* Day ahead markets */
		// Market coupling
		if (dayAheadMarketCouplingOperator != null) {
			dayAheadMarketCouplingOperator.execute();
		}
		// National clearing
		for (final MarketArea marketArea : model.getMarketAreas()) {
			// Execute here only day-ahead markets of market areas not using
			// market coupling
			if (!marketArea.isMarketCoupling()) {
				currentTasks.add(marketArea.getDayAheadMarketOperator());
			}
		}
		executeMarketsConcurrently();

	}

	private void executeMarketsConcurrently() {
		Concurrency.executeConcurrently(currentTasks);
		currentTasks.clear();
	}

	/** Get market coupling operator */
	public MarketCouplingOperator getMarketCouplingOperator() {
		return dayAheadMarketCouplingOperator;
	}
}
