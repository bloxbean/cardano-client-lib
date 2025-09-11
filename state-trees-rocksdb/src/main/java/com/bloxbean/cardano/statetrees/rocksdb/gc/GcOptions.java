package com.bloxbean.cardano.statetrees.rocksdb.gc;

import java.util.function.LongConsumer;

public class GcOptions {
  public boolean dryRun = false;
  public int deleteBatchSize = 10_000;
  public boolean useSnapshot = true;
  public LongConsumer progress = null; // called with deleted count
}

