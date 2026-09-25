package com.shivankkapoor.aldrop.Cache;

import java.util.function.Function;

public interface DataCache<K, V> {

    V get(K key, Function<? super K, ? extends V> loader);

    void invalidate(K key);

    void invalidateAll();
}
