package com.bloxbean.cardano.statetrees.smt;

import com.bloxbean.cardano.statetrees.TestNodeStore;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.SparseMerkleTree;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SmtCoreBenchmark {

  private static final HashFunction HASH_FN = Blake2b256::digest;

  @Param({"100", "1000", "5000"})
  private int datasetSize;

  private TestNodeStore store;
  private SparseMerkleTree smt;
  private List<byte[]> keys;
  private List<byte[]> values;
  private Random random;

  @Setup(Level.Trial)
  public void setupTrial() {
    store = new TestNodeStore();
    smt = new SparseMerkleTree(store, HASH_FN);
    random = new Random(42);

    keys = new ArrayList<>(datasetSize);
    values = new ArrayList<>(datasetSize);
    for (int i = 0; i < datasetSize; i++) {
      byte[] k = new byte[16]; // slightly larger keys
      byte[] v = new byte[32];
      random.nextBytes(k);
      random.nextBytes(v);
      keys.add(k);
      values.add(v);
    }
  }

  @Setup(Level.Iteration)
  public void setupIteration() {
    store.clear();
    smt = new SparseMerkleTree(store, HASH_FN);
  }

  @Benchmark
  public void sequentialInserts(Blackhole bh) {
    for (int i = 0; i < datasetSize; i++) smt.put(keys.get(i), values.get(i));
    bh.consume(smt.getRootHash());
  }

  @Benchmark
  public void randomReads(Blackhole bh) {
    for (int i = 0; i < datasetSize; i++) smt.put(keys.get(i), values.get(i));
    Random r = new Random(123);
    int numReads = datasetSize * 5;
    for (int i = 0; i < numReads; i++) {
      int idx = r.nextInt(datasetSize);
      bh.consume(smt.get(keys.get(idx)));
    }
  }

  @Benchmark
  public void deletionsWithCompression(Blackhole bh) {
    for (int i = 0; i < datasetSize; i++) smt.put(keys.get(i), values.get(i));
    int numDeletes = datasetSize / 2;
    for (int i = 0; i < numDeletes; i++) smt.delete(keys.get(i));
    bh.consume(smt.getRootHash());
  }

  @Benchmark
  public void mixedWorkload(Blackhole bh) {
    Random wr = new Random(456);
    int initial = Math.max(1, datasetSize / 4);
    for (int i = 0; i < initial; i++) smt.put(keys.get(i), values.get(i));
    int insertIndex = initial;
    int ops = datasetSize * 3;
    for (int i = 0; i < ops; i++) {
      double p = wr.nextDouble();
      if (p < 0.6) {
        if (insertIndex > 0) {
          int idx = wr.nextInt(insertIndex);
          bh.consume(smt.get(keys.get(idx)));
        }
      } else if (p < 0.9) {
        if (insertIndex < datasetSize) smt.put(keys.get(insertIndex), values.get(insertIndex++));
      } else {
        if (insertIndex > 0) smt.delete(keys.get(wr.nextInt(insertIndex)));
      }
    }
    bh.consume(smt.getRootHash());
  }

  @Benchmark
  public void stateReconstruction(Blackhole bh) {
    for (int i = 0; i < datasetSize; i++) smt.put(keys.get(i), values.get(i));
    byte[] root = smt.getRootHash();
    SparseMerkleTree reloaded = new SparseMerkleTree(store, HASH_FN, root);
    Random vr = new Random(999);
    int checks = Math.min(100, datasetSize);
    for (int i = 0; i < checks; i++) bh.consume(reloaded.get(keys.get(vr.nextInt(datasetSize))));
  }

  @Benchmark
  public void largeValueOperations(Blackhole bh) {
    Random rv = new Random(111);
    int count = Math.min(datasetSize / 10, 100);
    for (int i = 0; i < count; i++) {
      byte[] big = new byte[1024];
      rv.nextBytes(big);
      smt.put(keys.get(i), big);
    }
    for (int i = 0; i < count; i++) bh.consume(smt.get(keys.get(i)));
    bh.consume(smt.getRootHash());
  }
}

