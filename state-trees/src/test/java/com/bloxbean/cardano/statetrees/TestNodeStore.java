package com.bloxbean.cardano.statetrees;

import com.bloxbean.cardano.statetrees.api.NodeStore;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestNodeStore implements NodeStore {
  private final Map<String, byte[]> map = new ConcurrentHashMap<>();

  private static String k(byte[] h) { return Arrays.toString(h); }

  @Override public byte[] get(byte[] hash) { return map.get(k(hash)); }
  @Override public void put(byte[] hash, byte[] nodeBytes) { map.put(k(hash), nodeBytes); }
  @Override public void delete(byte[] hash) { map.remove(k(hash)); }
  
  public int size() { return map.size(); }
  public void clear() { map.clear(); }
}

