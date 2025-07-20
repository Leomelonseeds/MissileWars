package com.leomelonseeds.missilewars.invs.deck;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple bidirectional hashmap
 * Thanks geeksforgeeks
 */
public class BiMap<K, V> {    
    
    private final Map<K, V> keyToValueMap = new HashMap<>();
    private final Map<V, K> valueToKeyMap = new HashMap<>();
    
    public void put(K key, V value) {
        keyToValueMap.put(key, value);
        valueToKeyMap.put(value, key);
    }

    /**
     * Get value based on key
     * 
     * @param key
     * @return
     */
    public V get(K key) {
        return keyToValueMap.get(key);
    }

    /**
     * Get key based on value
     * 
     * @param value
     * @return
     */
    public K getKey(V value) {
        return valueToKeyMap.get(value);
    }
}
