package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.proof.ClassicJmtProofCodec;
import com.bloxbean.cardano.vds.jmt.proof.JmtProofCodec;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatDescriptor;

import java.util.Objects;

/**
 * Named cryptographic and wire-format profile for a JMT.
 */
public final class JmtProfile {

    private final JmtFormatDescriptor format;
    private final HashFunction hashFunction;
    private final CommitmentScheme commitmentScheme;
    private final JmtProofCodec proofCodec;

    private JmtProfile(JmtFormatDescriptor format,
                       HashFunction hashFunction,
                       CommitmentScheme commitmentScheme,
                       JmtProofCodec proofCodec) {
        this.format = Objects.requireNonNull(format, "format");
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction");
        this.commitmentScheme = Objects.requireNonNull(commitmentScheme, "commitmentScheme");
        this.proofCodec = Objects.requireNonNull(proofCodec, "proofCodec");
    }

    public static JmtProfile classicBlake2b256V1() {
        HashFunction hashFunction = Blake2b256::digest;
        return new JmtProfile(
                JmtFormatDescriptor.classicBlake2b256V1(),
                hashFunction,
                new ClassicJmtCommitmentScheme(hashFunction),
                new ClassicJmtProofCodec());
    }

    public static JmtProfile custom(JmtFormatDescriptor format,
                                    HashFunction hashFunction,
                                    CommitmentScheme commitmentScheme,
                                    JmtProofCodec proofCodec) {
        format.requirePersistent();
        return new JmtProfile(format, hashFunction, commitmentScheme, proofCodec);
    }

    public JmtFormatDescriptor format() {
        return format;
    }

    public HashFunction hashFunction() {
        return hashFunction;
    }

    public CommitmentScheme commitmentScheme() {
        return commitmentScheme;
    }

    public JmtProofCodec proofCodec() {
        return proofCodec;
    }
}
