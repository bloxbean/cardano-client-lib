package com.bloxbean.cardano.vds.mpf.test;

import com.bloxbean.cardano.client.util.HexUtil;
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

    private String toHex(byte[] bytes) {
        return HexUtil.encodeHexString(bytes);
    }
}
