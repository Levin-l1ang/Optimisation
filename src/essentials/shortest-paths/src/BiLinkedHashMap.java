

import java.util.LinkedHashMap;
import java.util.Map;

/** This is like a matrix, but with possibly non-integral indices. */
public class BiLinkedHashMap<T1, T2, T3> extends
LinkedHashMap<T1, LinkedHashMap<T2, T3>> {

	private static final long serialVersionUID = 1L;

	public BiLinkedHashMap() {
		super();
	}

	public BiLinkedHashMap(Map<? extends T1, ? extends LinkedHashMap<T2, T3>> m) {
		super(m);
	}

	public T3 put(T1 key1, T2 key2, T3 value) {
	    LinkedHashMap<T2,T3> toPut = get(key1);
	    if(toPut == null){
	        toPut = new LinkedHashMap<>();
	    }
	    T3 retval = toPut.put(key2, value);
	    super.put(key1, toPut);

	    return retval;
	}

	public T3 get(T1 key1, T2 key2) {
		LinkedHashMap<T2, T3> value = get(key1);
		if(value == null){
			return null;
		}
		return value.get(key2);
	}

	public boolean containsKey(T1 key1, T2 key2) {
		LinkedHashMap<T2, T3> resolveFirst = super.get(key1);
		return resolveFirst != null && resolveFirst.containsKey(key2);
	}

}
