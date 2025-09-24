package com.bloxbean.cardano.statetrees.smt;

import com.bloxbean.cardano.statetrees.TestNodeStore;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.SparseMerkleProof;
import com.bloxbean.cardano.statetrees.api.SparseMerkleTree;
import com.bloxbean.cardano.statetrees.api.SparseMerkleVerifier;
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
public class SmtProofBenchmark {

    private static final HashFunction HASH_FN = Blake2b256::digest;

    @Param({"100", "1000"})
    private int datasetSize;

    private TestNodeStore store;
    private SparseMerkleTree smt;
    private List<byte[]> presentKeys;
    private List<byte[]> presentValues;
    private List<byte[]> absentKeys;

    @Setup(Level.Trial)
    public void setupTrial() {
        store = new TestNodeStore();
        smt = new SparseMerkleTree(store, HASH_FN);

        presentKeys = new ArrayList<>(datasetSize);
        presentValues = new ArrayList<>(datasetSize);
        absentKeys = new ArrayList<>(datasetSize);

        Random rnd = new Random(777);
        for (int i = 0; i < datasetSize; i++) {
            byte[] k = new byte[12];
            byte[] v = new byte[16];
            rnd.nextBytes(k);
            rnd.nextBytes(v);
            presentKeys.add(k);
            presentValues.add(v);
            byte[] absent = new byte[12];
            rnd.nextBytes(absent);
            absentKeys.add(absent);
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        store.clear();
        smt = new SparseMerkleTree(store, HASH_FN);
        for (int i = 0; i < datasetSize; i++) smt.put(presentKeys.get(i), presentValues.get(i));
    }

    @Benchmark
    public void inclusionProofGeneration(Blackhole bh) {
        Random r = new Random(1001);
        int idx = r.nextInt(datasetSize);
        SparseMerkleProof proof = smt.getProof(presentKeys.get(idx));
        bh.consume(proof);
    }

    @Benchmark
    public void inclusionProofVerification(Blackhole bh) {
        Random r = new Random(1002);
        int idx = r.nextInt(datasetSize);
        byte[] key = presentKeys.get(idx);
        byte[] val = presentValues.get(idx);
        SparseMerkleProof proof = smt.getProof(key);
        boolean ok = SparseMerkleVerifier.verifyInclusion(smt.getRootHash(), HASH_FN, key, val, proof.getSiblings());
        bh.consume(ok);
    }

    @Benchmark
    public void nonInclusionProofGeneration(Blackhole bh) {
        Random r = new Random(1003);
        int idx = r.nextInt(datasetSize);
        SparseMerkleProof proof = smt.getProof(absentKeys.get(idx));
        bh.consume(proof);
    }

    @Benchmark
    public void nonInclusionProofVerification(Blackhole bh) {
        Random r = new Random(1004);
        int idx = r.nextInt(datasetSize);
        byte[] key = absentKeys.get(idx);
        SparseMerkleProof proof = smt.getProof(key);
        boolean ok = SparseMerkleVerifier.verifyNonInclusion(smt.getRootHash(), HASH_FN, key, proof.getSiblings());
        bh.consume(ok);
    }
}

