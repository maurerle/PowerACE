package supply.powerplant.capacity;

public enum CapacityType {

	CAPACITY_GROSS,
	CAPACITY_NET,
	CAPACITY_UNUSED_EXPECTED,
	CAPACITY_UNUSED_UNEXPECTED,
	CONTRACTED_COGENERATION,
	CONTRACTED_DAY_AHEAD,
	CONTRACTED_FORWARD,
	CONTRACTED_TOTAL,
	NON_USABILITY_EXPECTED,
	NON_USABILITY_UNEXPECTED;

	public boolean isContracted() {
		boolean contracted = false;
		if ((this == CONTRACTED_DAY_AHEAD) || (this == CONTRACTED_FORWARD)
				|| (this == CONTRACTED_COGENERATION)) {
			contracted = true;
		}
		return contracted;
	}

	public boolean isSubtractedExAnte() {
		boolean subtracted = false;
		if (isContracted()) {
			subtracted = true;
		}
		return subtracted;
	}

	public boolean isSubtractedExPost() {
		boolean subtracted = false;
		if (isContracted()) {
			subtracted = true;
		}
		return subtracted;
	}

}