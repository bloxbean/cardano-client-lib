package com.bloxbean.cardano.statetrees.mpt.mode;

import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;

/**
 * Binds an MPT commitment scheme and proof codec into a single mode.
 */
public interface MptMode {
    String name();

    CommitmentScheme commitments();

    ProofCodec proofCodec();

    boolean hashedKeySpace();
}

