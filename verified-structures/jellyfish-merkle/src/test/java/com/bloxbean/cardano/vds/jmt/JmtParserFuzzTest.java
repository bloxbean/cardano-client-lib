package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.jmt.proof.JmtProofCodec;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Coverage-guided parser and verifier targets for attacker-controlled JMT inputs. */
class JmtParserFuzzTest {

    private static final int MAX_NODE_INPUT = 64 * 1024;
    private static final int MAX_NODE_KEY_INPUT = 1024;
    private static final int MAX_WIRE_INPUT = 1024 * 1024 + 1;
    private static final JmtProfile PROFILE = JmtProfile.classicBlake2b256V1();

    @FuzzTest(maxDuration = "5s")
    void nodeKeyDecoderRejectsMalformedInputWithoutUnexpectedFailure(byte[] input) {
        try {
            NodeKey.fromBytes(bounded(input, MAX_NODE_KEY_INPUT));
        } catch (IllegalArgumentException expected) {
            // Malformed external input is the expected outcome.
        }
    }

    @FuzzTest(maxDuration = "5s")
    void nodeCborDecoderRejectsMalformedInputWithoutUnexpectedFailure(byte[] input) {
        try {
            JmtEncoding.decode(bounded(input, MAX_NODE_INPUT));
        } catch (IllegalArgumentException expected) {
            // Malformed external input is the expected outcome.
        }
    }

    @FuzzTest(maxDuration = "5s")
    void wireProofVerifierIsTotalForBoundedUntrustedInput(byte[] input) {
        byte[] wire = bounded(input, MAX_WIRE_INPUT);
        byte[] key = "fuzz-key".getBytes(StandardCharsets.UTF_8);
        byte[] value = "fuzz-value".getBytes(StandardCharsets.UTF_8);
        byte[] root = PROFILE.commitmentScheme().nullHash();
        JmtProofCodec codec = PROFILE.proofCodec();
        codec.verify(root, key, value, true, wire,
                PROFILE.hashFunction(), PROFILE.commitmentScheme());
        codec.verify(root, key, null, false, wire,
                PROFILE.hashFunction(), PROFILE.commitmentScheme());
    }

    private static byte[] bounded(byte[] input, int maximum) {
        if (input == null || input.length <= maximum) {
            return input;
        }
        return Arrays.copyOf(input, maximum);
    }
}
