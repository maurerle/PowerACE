package tools.other;

/**
 * Simple tuple class for two values that need to be stored together.
 * 
 * @author 
 * 
 * @param <X>
 * @param <Y>
 */
public class Tuple<X, Y> {

	private final X x;
	private final Y y;

	public Tuple(X x, Y y) {
		this.x = x;
		this.y = y;
	}

	public X getX() {
		return x;
	}

	public Y getY() {
		return y;
	}
	
	@Override
	public String toString() {
		return "x: " + x + ", y: " + y;
	}
}