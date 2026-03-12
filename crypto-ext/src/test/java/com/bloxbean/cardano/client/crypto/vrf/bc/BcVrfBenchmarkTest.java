package com.bloxbean.cardano.client.crypto.vrf.bc;

import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import com.bloxbean.cardano.client.crypto.vrf.cardano.CardanoVrfInput;
import org.junit.jupiter.api.Test;

/**
 * Benchmark comparing i2p-based EcVrfVerifier vs BouncyCastle-based BcVrfVerifier.
 * Uses real mainnet block #10000000 data.
 */
class BcVrfBenchmarkTest {

    // Real mainnet block #10000000 data
    private static final long SLOT = 117736136L;
    private static final byte[] VRF_VKEY = hexToBytes(
            "5ebd45dfdf4ee76829d195ec24771904ee3947387f3e65005deb2b7ceab393c0");
    private static final byte[] VRF_PROOF = hexToBytes(
            "4dd5f5e34a33a4e162cd1957a95a471c8985710da24d1bfd0cbc95d909dbaa33"
                    + "b35680a86d4dc6f64348ef60db5bdd323809fccfd4d4f480cd4d5feae4d59f6b"
                    + "7d7ec2e9faa2bc8873d3963981075703");
    private static final byte[] EPOCH_NONCE = hexToBytes(
            "aa022d10f8a29863795ff14c4e82570d1db8906f1b3fd8a90fe69b699a4398d9");

    private static final int WARMUP_ITERATIONS = 500;
    private static final int BENCHMARK_ITERATIONS = 2000;

    @Test
    void benchmark_vrfVerify_comparison() {
        EcVrfVerifier i2pVerifier = new EcVrfVerifier();
        BcVrfVerifier bcVerifier = new BcVrfVerifier();
        byte[] alpha = CardanoVrfInput.mkInputVrf(SLOT, EPOCH_NONCE);

        // Warmup both
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            i2pVerifier.verify(VRF_VKEY, VRF_PROOF, alpha);
            bcVerifier.verify(VRF_VKEY, VRF_PROOF, alpha);
        }

        // Benchmark i2p
        long startI2p = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            VrfResult result = i2pVerifier.verify(VRF_VKEY, VRF_PROOF, alpha);
            if (!result.isValid()) throw new AssertionError("i2p VRF verify failed");
        }
        long elapsedI2p = System.nanoTime() - startI2p;

        // Benchmark BC
        long startBc = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            VrfResult result = bcVerifier.verify(VRF_VKEY, VRF_PROOF, alpha);
            if (!result.isValid()) throw new AssertionError("BC VRF verify failed");
        }
        long elapsedBc = System.nanoTime() - startBc;

        double avgUsI2p = (elapsedI2p / 1000.0) / BENCHMARK_ITERATIONS;
        double avgUsBc = (elapsedBc / 1000.0) / BENCHMARK_ITERATIONS;
        double opsPerSecI2p = BENCHMARK_ITERATIONS / (elapsedI2p / 1_000_000_000.0);
        double opsPerSecBc = BENCHMARK_ITERATIONS / (elapsedBc / 1_000_000_000.0);
        double speedup = avgUsI2p / avgUsBc;

        System.out.println("=== VRF Verify Benchmark (n=" + BENCHMARK_ITERATIONS + ") ===");
        System.out.printf("[i2p EcVrfVerifier]   avg=%.1f µs/op  throughput=%.0f ops/sec%n",
                avgUsI2p, opsPerSecI2p);
        System.out.printf("[BC  BcVrfVerifier]   avg=%.1f µs/op  throughput=%.0f ops/sec%n",
                avgUsBc, opsPerSecBc);
        System.out.printf("[Speedup]             BC is %.2fx %s than i2p%n",
                speedup > 1 ? speedup : 1 / speedup,
                speedup > 1 ? "faster" : "slower");
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
