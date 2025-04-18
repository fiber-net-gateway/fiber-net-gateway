package io.fiber.net.common.utils;

import io.netty.util.HashingStrategy;

import java.util.Objects;
import java.util.function.BiConsumer;

public class QuadraticProbingHashTable<K, V> {
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private static <S> int hash(HashingStrategy<? super S> strategy, S key) {
        int h;
        return (h = strategy.hashCode(key)) ^ (h >>> 16);
    }

    private static int computeCap(int uCap) {
        if (uCap <= 0) return DEFAULT_SIZE;
        if (uCap >= MAXIMUM_CAPACITY) return MAXIMUM_CAPACITY;
        if ((uCap & uCap - 1) == 0) {
            return uCap;
        }
        uCap--;
        uCap |= uCap >>> 1;
        uCap |= uCap >>> 2;
        uCap |= uCap >>> 4;
        uCap |= uCap >>> 8;
        uCap |= uCap >>> 16;
        return uCap + 1;
    }

    private static final int DEFAULT_SIZE = 16;
    private final HashingStrategy<? super K> hashingStrategy;
    private Object[] keys;
    private Object[] values;
    private int capacity;
    private int size;

    public QuadraticProbingHashTable(HashingStrategy<? super K> hashingStrategy) {
        this(DEFAULT_SIZE, hashingStrategy);
    }

    public QuadraticProbingHashTable(int capacity, HashingStrategy<? super K> hashingStrategy) {
        this.capacity = computeCap(capacity);
        this.hashingStrategy = Objects.requireNonNull(hashingStrategy);
        keys = new Object[this.capacity];
        values = new Object[this.capacity];
        size = 0;
    }

    @SuppressWarnings("unchecked")
    private void rehash() {
        Object[] oldKeys = keys, newKeys;
        Object[] oldValues = values, newValues;
        int cap = capacity;
        int newCap = capacity = cap << 1;
        newKeys = keys = new Object[newCap];
        newValues = values = new Object[newCap];
        int mask = newCap - 1;

        K k;
        HashingStrategy<? super K> strategy = hashingStrategy;
        for (int i = 0; i < cap; i++) {
            if ((k = (K) oldKeys[i]) != null) {
                int idx = hash(strategy, k) & mask;
                while (newKeys[idx] != null) {
                    idx = (idx + 1) & mask;
                }
                newKeys[idx] = k;
                newValues[idx] = oldValues[i];
            }
        }
    }

    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        Objects.requireNonNull(key);

        Object[] ks = keys;
        Object[] vs = values;
        HashingStrategy<? super K> strategy = hashingStrategy;
        int mask = capacity - 1;
        int index = hash(strategy, key) & mask;

        K k;
        while ((k = (K) ks[index]) != null) {
            if (strategy.equals(k, key)) {
                V old = (V) vs[index];
                vs[index] = value;
                return old;
            }
            index = (index + 1) & mask;
        }


        ks[index] = key;
        vs[index] = value;
        if ((++size << 1) >= capacity) {
            rehash();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public V get(K key) {
        if (key == null) {
            return null;
        }
        HashingStrategy<? super K> strategy = hashingStrategy;
        int mask = capacity - 1;
        Object[] ks = keys;
        Object[] vs = values;
        K k;

        int index = hash(strategy, key) & mask;
        while ((k = (K) ks[index]) != null) {
            if (strategy.equals(k, key)) {
                return (V) vs[index];
            }
            index = (index + 1) & mask;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<K, V> func) {
        Object[] keys = this.keys;
        Object[] values = this.values;
        for (int i = 0, c = capacity; i < c; i++) {
            Object key;
            if ((key = keys[i]) != null) {
                func.accept((K) key, (V) values[i]);
            }
        }
    }

    public int size() {
        return size;
    }
}