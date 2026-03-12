package com.bloxbean.cardano.client.crypto.vrf.cardano;

import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Performance benchmark for VRF verification and leader eligibility check.
 * Not a correctness test — measures throughput and latency.
 */
class CardanoVrfBenchmarkTest {

    // Real mainnet block #10000000 data
    private static final long SLOT = 117736136L;
    private static final byte[] VRF_VKEY = hexToBytes(
            "5ebd45dfdf4ee76829d195ec24771904ee3947387f3e65005deb2b7ceab393c0");
    private static final byte[] VRF_PROOF = hexToBytes(
            "4dd5f5e34a33a4e162cd1957a95a471c8985710da24d1bfd0cbc95d909dbaa33"
                    + "b35680a86d4dc6f64348ef60db5bdd323809fccfd4d4f480cd4d5feae4d59f6b"
                    + "7d7ec2e9faa2bc8873d3963981075703");
    private static final byte[] VRF_OUTPUT = hexToBytes(
            "8eef20bfce43e2a0a64d53cb31af230862509ac2aa9536613cf8102039564fad"
                    + "3563236e7f5e5d1cf52eafb017b9f8e63b511ede8c798fd49c766b37bf0e054e");
    private static final byte[] EPOCH_NONCE = hexToBytes(
            "aa022d10f8a29863795ff14c4e82570d1db8906f1b3fd8a90fe69b699a4398d9");
    private static final BigDecimal SIGMA = new BigDecimal("17979009392314")
            .divide(new BigDecimal("22861134936826292"), 40, RoundingMode.HALF_EVEN);
    private static final BigDecimal F = new BigDecimal("0.05");

    private static final int WARMUP_ITERATIONS = 500;
    private static final int BENCHMARK_ITERATIONS = 2000;

    @Test
    void benchmark_vrfVerifyOnly() {
        EcVrfVerifier verifier = new EcVrfVerifier();
        byte[] alpha = CardanoVrfInput.mkInputVrf(SLOT, EPOCH_NONCE);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            verifier.verify(VRF_VKEY, VRF_PROOF, alpha);
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            VrfResult result = verifier.verify(VRF_VKEY, VRF_PROOF, alpha);
            if (!result.isValid()) throw new AssertionError("VRF verify failed during benchmark");
        }
        long elapsed = System.nanoTime() - start;

        double avgUs = (elapsed / 1000.0) / BENCHMARK_ITERATIONS;
        double opsPerSec = BENCHMARK_ITERATIONS / (elapsed / 1_000_000_000.0);
        System.out.printf("[VRF Verify Only]       avg=%.1f µs/op  throughput=%.0f ops/sec  (n=%d)%n",
                avgUs, opsPerSec, BENCHMARK_ITERATIONS);
    }

    @Test
    void benchmark_mkInputVrf() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            CardanoVrfInput.mkInputVrf(SLOT + i, EPOCH_NONCE);
        }

        int iterations = 50000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            CardanoVrfInput.mkInputVrf(SLOT + i, EPOCH_NONCE);
        }
        long elapsed = System.nanoTime() - start;

        double avgUs = (elapsed / 1000.0) / iterations;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);
        System.out.printf("[mkInputVrf]            avg=%.2f µs/op  throughput=%.0f ops/sec  (n=%d)%n",
                avgUs, opsPerSec, iterations);
    }

    @Test
    void benchmark_vrfLeaderValue() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            CardanoLeaderCheck.vrfLeaderValue(VRF_OUTPUT);
        }

        int iterations = 50000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            CardanoLeaderCheck.vrfLeaderValue(VRF_OUTPUT);
        }
        long elapsed = System.nanoTime() - start;

        double avgUs = (elapsed / 1000.0) / iterations;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);
        System.out.printf("[vrfLeaderValue]        avg=%.2f µs/op  throughput=%.0f ops/sec  (n=%d)%n",
                avgUs, opsPerSec, iterations);
    }

    @Test
    void benchmark_checkLeaderValue() {
        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(VRF_OUTPUT);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            CardanoLeaderCheck.checkLeaderValue(leaderValue, SIGMA, F);
        }

        int iterations = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            CardanoLeaderCheck.checkLeaderValue(leaderValue, SIGMA, F);
        }
        long elapsed = System.nanoTime() - start;

        double avgUs = (elapsed / 1000.0) / iterations;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);
        System.out.printf("[checkLeaderValue]      avg=%.1f µs/op  throughput=%.0f ops/sec  (n=%d)%n",
                avgUs, opsPerSec, iterations);
    }

    @Test
    void benchmark_endToEnd_verifyAndCheckLeader() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            CardanoLeaderCheck.verifyAndCheckLeader(VRF_VKEY, VRF_PROOF, SLOT, EPOCH_NONCE, SIGMA, F);
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            boolean result = CardanoLeaderCheck.verifyAndCheckLeader(
                    VRF_VKEY, VRF_PROOF, SLOT, EPOCH_NONCE, SIGMA, F);
            if (!result) throw new AssertionError("End-to-end check failed during benchmark");
        }
        long elapsed = System.nanoTime() - start;

        double avgUs = (elapsed / 1000.0) / BENCHMARK_ITERATIONS;
        double opsPerSec = BENCHMARK_ITERATIONS / (elapsed / 1_000_000_000.0);
        System.out.printf("[End-to-End Pipeline]   avg=%.1f µs/op  throughput=%.0f ops/sec  (n=%d)%n",
                avgUs, opsPerSec, BENCHMARK_ITERATIONS);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
