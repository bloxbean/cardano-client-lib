package com.bloxbean.cardano.vds.jmt.store;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.proof.ClassicJmtProofCodec;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JmtFormatDescriptorTest {

    @Test
    void descriptorRoundTripsExactly() {
        JmtFormatDescriptor descriptor = JmtFormatDescriptor.classicBlake2b256V1();
        assertEquals(descriptor, JmtFormatDescriptor.decode(descriptor.encode()));
    }

    @Test
    void descriptorRejectsTrailingAndTruncatedBytes() {
        byte[] valid = JmtFormatDescriptor.classicBlake2b256V1().encode();
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 1);

        assertThrows(IllegalArgumentException.class,
                () -> JmtFormatDescriptor.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> JmtFormatDescriptor.decode(truncated));
    }

    @Test
    void oneStoreRejectsASecondCryptographicProfile() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        new JellyfishMerkleTree(store, JmtProfile.classicBlake2b256V1());

        HashFunction sha256 = input -> {
            try {
                return MessageDigest.getInstance("SHA-256").digest(input);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        };
        JmtProfile different = JmtProfile.custom(
                JmtFormatDescriptor.custom("classic-radix16-sha256-v1", "sha-256", 32),
                sha256,
                new ClassicJmtCommitmentScheme(sha256),
                new ClassicJmtProofCodec());

        assertThrows(JmtFormatMismatchException.class,
                () -> new JellyfishMerkleTree(store, different));
    }
}
