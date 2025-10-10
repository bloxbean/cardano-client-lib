package com.bloxbean.cardano.statetrees.mpt.mode;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.mpt.commitment.ClassicMptCommitmentScheme;
import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.mpt.commitment.MpfCommitmentScheme;

/**
 * Factory for standard MPT modes.
 */
public final class Modes {
    private Modes() {
    }

    public static MptMode mpf(HashFunction hashFn) {
        CommitmentScheme cs = new MpfCommitmentScheme(hashFn);
        return new SimpleMode("MPF", cs, new MpfProofCodec(), true);
    }

    public static MptMode classic(HashFunction hashFn) {
        CommitmentScheme cs = new ClassicMptCommitmentScheme(hashFn);
        return new SimpleMode("CLASSIC", cs, new ClassicProofCodec(), false);
    }

    public static MptMode fromCommitments(CommitmentScheme cs, boolean hashedKeySpace) {
        if (cs instanceof MpfCommitmentScheme) {
            return new SimpleMode("MPF", cs, new MpfProofCodec(), hashedKeySpace);
        }
        if (cs instanceof ClassicMptCommitmentScheme) {
            return new SimpleMode("CLASSIC", cs, new ClassicProofCodec(), hashedKeySpace);
        }
        // Default to MPF proof codec if unknown (conservative);
        return new SimpleMode("CUSTOM", cs, new MpfProofCodec(), hashedKeySpace);
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

