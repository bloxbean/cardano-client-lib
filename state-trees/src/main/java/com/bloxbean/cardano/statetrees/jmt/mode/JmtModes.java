package com.bloxbean.cardano.statetrees.jmt.mode;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;

/**
 * Factory for JMT modes.
 */
public final class JmtModes {
    private JmtModes() {
    }

    /**
     * MPF mode: MPF commitments and MPF wire proofs.
     */
    public static JmtMode mpf(HashFunction hashFn) {
        final CommitmentScheme cs = new MpfCommitmentScheme(hashFn);
        final JmtProofCodec codec = new MpfJmtProofCodec();
        return new JmtMode() {
            @Override
            public String name() {
                return "MPF";
            }

            @Override
            public CommitmentScheme commitments() {
                return cs;
            }

            @Override
            public JmtProofCodec proofCodec() {
                return codec;
            }
        };
    }

    /**
     * Classic mode: Classic commitments and Classic wire proofs (node list).
     */
    public static JmtMode classic(HashFunction hashFn) {
        final CommitmentScheme cs = new com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme(hashFn);
        final JmtProofCodec codec = new ClassicJmtProofCodec();
        return new JmtMode() {
            @Override
            public String name() {
                return "CLASSIC";
            }

            @Override
            public CommitmentScheme commitments() {
                return cs;
            }

            @Override
            public JmtProofCodec proofCodec() {
                return codec;
            }
        };
    }
}
