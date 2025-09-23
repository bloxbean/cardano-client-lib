package com.bloxbean.cardano.statetrees.rocksdb.jmt;

final class RocksDbJmtSchema {
  static final String CF_NODES = "nodes_jmt";
  static final String CF_VALUES = "values_jmt";
  static final String CF_ROOTS = "roots_jmt";
  static final String CF_STALE = "stale_jmt";

  static final byte[] LATEST_ROOT_KEY = new byte[] { 'J', 'M', 'T', '_', 'L', 'A', 'T', 'E', 'S', 'T' };
  static final byte[] LATEST_VERSION_KEY = new byte[] { 'J', 'M', 'T', '_', 'V', 'E', 'R' };

  private RocksDbJmtSchema() {
    throw new AssertionError("Utility class");
  }
}

