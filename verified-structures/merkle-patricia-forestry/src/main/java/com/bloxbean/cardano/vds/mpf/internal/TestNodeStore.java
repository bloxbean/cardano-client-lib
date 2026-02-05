package com.bloxbean.cardano.vds.mpf.internal;

import com.bloxbean.cardano.vds.core.api.NodeStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory node store for testing purposes.
 */
public class TestNodeStore implements NodeStore {
    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public byte[] get(byte[] key) {
        return store.get(toHex(key));
    }

    @Override
    public void put(byte[] key, byte[] value) {
        store.put(toHex(key), value);
    }

    @Override
    public void delete(byte[] key) {
        store.remove(toHex(key));
    }

    public void clear() {
        store.clear();
    }

    private String toHex(byte[] bytes) {
        if (bytes == null) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
