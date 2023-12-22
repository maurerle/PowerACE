package tools;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import markets.bids.reserve.ReserveBid;

public final class Sorting {

	/**
	 * Checks whether an Iterable sorted .
	 *
	 * @author http://stackoverflow.com/a/3047160
	 *
	 */
	public static <T extends Comparable<? super T>> boolean isSorted(Iterable<T> iterable) {
		final Iterator<T> iter = iterable.iterator();
		if (!iter.hasNext()) {
			return true;
		}
		T t = iter.next();
		while (iter.hasNext()) {
			final T t2 = iter.next();
			if (t.compareTo(t2) > 0) {
				return false;
			}
			t = t2;
		}
		return true;
	}

	/**
	 * Sorts a map based on its values (Descending).
	 *
	 * @author Carter Page
	 *
	 */
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValueDecreasing(
			Map<K, V> map) {

		final List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
		Collections.sort(list, (Map.Entry<K, V> object1, Map.Entry<K, V> object2) -> -1
				* object1.getValue().compareTo(object2.getValue()));

		final Map<K, V> result = new LinkedHashMap<>();
		for (final Map.Entry<K, V> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	/**
	 * Sorts a map based on its values (Ascending).
	 *
	 * @author Carter Page
	 *
	 */
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValueIncreasing(
			Map<K, V> map) {

		final List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
		Collections.sort(list, (Map.Entry<K, V> object1, Map.Entry<K, V> object2) -> object1
				.getValue().compareTo(object2.getValue()));

		final Map<K, V> result = new LinkedHashMap<>();
		for (final Map.Entry<K, V> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	public static List<Object> sortReserveArrayList(List<Object> arrlist) {
		Object help = new Object();
		for (int x = 0; x < arrlist.size(); x++) {
			for (int y = x + 1; y < arrlist.size(); y++) {
				if (((ReserveBid) arrlist.get(x)).getCapacityPrice() < ((ReserveBid) arrlist.get(y))
						.getCapacityPrice()) {
					help = arrlist.get(x);
					arrlist.set(x, arrlist.get(y));
					arrlist.set(y, help);
				}
			}
		}
		return arrlist;
	}

}