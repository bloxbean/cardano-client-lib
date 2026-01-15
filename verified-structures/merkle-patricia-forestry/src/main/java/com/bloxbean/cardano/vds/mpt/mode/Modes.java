package com.bloxbean.cardano.vds.mpt.mode;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpt.commitment.MpfCommitmentScheme;

/**
 * Factory for MPT modes.
 *
 * <p>This class provides factory methods for creating MPF mode configurations
 * compatible with Aiken's merkle-patricia-forestry library.</p>
 */
public final class Modes {
    private Modes() {
    }

    /**
     * Creates the MPF mode for Cardano/Aiken compatibility.
     *
     * @param hashFn the hash function to use
     * @return an MptMode configured for MPF
     */
    public static MptMode mpf(HashFunction hashFn) {
        CommitmentScheme cs = new MpfCommitmentScheme(hashFn);
        return new SimpleMode("MPF", cs, new MpfProofCodec(), true);
    }

    /**
     * Creates an MptMode from a commitment scheme.
     *
     * @param cs the commitment scheme
     * @param hashedKeySpace whether keys are pre-hashed
     * @return an MptMode
     */
    public static MptMode fromCommitments(CommitmentScheme cs, boolean hashedKeySpace) {
        return new SimpleMode("MPF", cs, new MpfProofCodec(), hashedKeySpace);
    }

    private static final class SimpleMode implements MptMode {
        private final String name;
        private final CommitmentScheme cs;
        private final ProofCodec codec;
        private final boolean hashed;

        private SimpleMode(String name, CommitmentScheme cs, ProofCodec codec, boolean hashed) {
            this.name = name;
            this.cs = cs;
            this.codec = codec;
            this.hashed = hashed;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CommitmentScheme commitments() {
            return cs;
        }

        @Override
        public ProofCodec proofCodec() {
            return codec;
        }

        @Override
        public boolean hashedKeySpace() {
            return hashed;
        }
    }
}
