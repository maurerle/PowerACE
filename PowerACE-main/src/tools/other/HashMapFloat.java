package tools.other;

import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Extends HashMap by an increase function
 * 
 * @author 
 * 
 * 
 * 
 * @param <K>
 */
public class HashMapFloat<K> extends ConcurrentSkipListMap<K, Float> {

	/** serial id */
	private static final long serialVersionUID = -6478963573781574123L;

	/**
	 * Constructs an empty <tt>HashMap</tt> with the default initial capacity
	 * (16) and the default load factor (0.75).
	 */
	public HashMapFloat() {
		super();
	}

	/**
	 * Increases the value at <code>key</code> by a given <code>number</code>
	 * 
	 * @param key
	 * @param number
	 */
	public void increase(K key, Float number) {
		if (get(key) != null) {
			put(key, get(key) + number);
		} else {
			put(key, number);
		}
	}

	public void increaseCheck(K key, Float number) {
		put(key, containsKey(key) ? get(key) + number : number);
	}

}