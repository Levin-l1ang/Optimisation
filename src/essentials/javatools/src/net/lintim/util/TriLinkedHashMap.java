package net.lintim.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Like {@link BiLinkedHashMap}, just with three keys instead of two.
 *
 * @param <T1> First key type.
 * @param <T2> Second key type.
 * @param <T3> Third key type.
 * @param <T4> Value type.
 */
@SuppressWarnings("serial")
public class TriLinkedHashMap<T1, T2, T3, T4> extends
LinkedHashMap<T1, BiLinkedHashMap<T2, T3, T4>> {

    public TriLinkedHashMap() {
        super();
    }

    public TriLinkedHashMap(
            Map<? extends T1, ? extends BiLinkedHashMap<T2, T3, T4>> m) {
        super(m);
    }

    public T4 put(T1 key1, T2 key2, T3 key3, T4 value) {
        BiLinkedHashMap<T2,T3,T4> toPut = get(key1);
        if(toPut == null){
            toPut = new BiLinkedHashMap<T2, T3, T4>();
        }
        T4 retval = toPut.put(key2, key3, value);
        super.put(key1, toPut);

        return retval;
    }

    public T4 get(T1 key1, T2 key2, T3 key3) {
        BiLinkedHashMap<T2, T3, T4> value = get(key1);
        if(value == null){
            return null;
        }
        return value.get(key2, key3);
    }

    public LinkedHashMap<T3, T4> get(T1 key1, T2 key2){
        BiLinkedHashMap<T2, T3, T4> value = get(key1);
        if(value == null){
            return null;
        }
        return value.get(key2);
    }

    public boolean containsKey(T1 key1, T2 key2, T3 key3) {
        BiLinkedHashMap<T2, T3, T4> resolveFirst = super.get(key1);
        return resolveFirst != null && resolveFirst.containsKey(key2, key3);
    }

}
