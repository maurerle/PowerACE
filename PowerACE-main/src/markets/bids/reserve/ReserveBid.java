package markets.bids.reserve;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.storage.PumpStoragePlant;
import supply.powerplant.Plant;

public class ReserveBid implements Comparable<ReserveBid> {
	private static final Logger logger = LoggerFactory.getLogger(ReserveBid.class.getName());
	private int bidID;
	private float capacity;
	private float capacityPrice;
	private float clearingPrice;
	private float contractedCapacity;
	private Plant plant;
	private boolean pump = false;
	private PumpStoragePlant pumpStoragePlant;

	public ReserveBid(float capacity, float capacityPrice, Plant plant) {
		this.capacity = capacity;
		this.capacityPrice = capacityPrice;
		this.plant = plant;
	}

	/**
	 * Compares this bid to another bid. First criterion is capacity price, second
	 * criterion is volume.
	 * <p>
	 * More specifically, when comparing two bids the one with a lower capacity
	 * price is higher ranked, i.e. appears before the other bid in a sorted list.
	 * If capacity prices are equal, the same ordering rule applies.
	 */
	@Override
	public int compareTo(ReserveBid other) {
		if (Float.isNaN(capacityPrice) || Float.isNaN(other.capacityPrice) || Float.isNaN(capacity)
				|| Float.isNaN(other.capacity)||Float.isNaN(plant.getUnitID())||Float.isNaN(other.getPlant().getUnitID())) {
			logger.error("Reserve bid has a NaN error. Check why.");
		}
		// First criterion is price
		if (capacityPrice > other.capacityPrice) {
			return 1;
		} else if (capacityPrice < other.capacityPrice) {
			return -1;
		}

		// Bigger volumes are considered first, although higher volumes are
		// higher ranked
		if (capacity < other.capacity) {
			return 1;
		} else if (capacity > other.capacity) {
			return -1;
		} else {
			// for deterministic use unit id as last criterion
			return Integer.compare(plant.getUnitID(), other.getPlant().getUnitID());
		}

	}

	public int getBidID() {
		return bidID;
	}

	public float getCapacity() {
		return capacity;
	}

	public float getCapacityPrice() {
		return capacityPrice;
	}

	public float getClearingPrice() {
		return clearingPrice;
	}

	public float getContractedCapacity() {
		return contractedCapacity;
	}

	public Plant getPlant() {
		return plant;
	}

	public PumpStoragePlant getPumpStoragePlant() {
		return pumpStoragePlant;
	}

	public boolean isPump() {
		return pump;
	}

	public void setBidID(int bidID) {
		this.bidID = bidID;
	}

	public void setCapacity(float capacity) {
		this.capacity = capacity;
	}

	public void setCapacityPrice(float capacityPrice) {
		this.capacityPrice = capacityPrice;
	}

	public void setClearingPrice(float clearingPrice) {
		this.clearingPrice = clearingPrice;
	}

	public void setContractedCapacity(float soldCapacity) {
		contractedCapacity = soldCapacity;
	}

	public void setPlant(Plant plant) {
		this.plant = plant;
	}

	public void setPump(boolean pump) {
		this.pump = pump;
	}

	public void setPumpStoragePlant(PumpStoragePlant pumpStoragePlant) {
		this.pumpStoragePlant = pumpStoragePlant;
	}
}