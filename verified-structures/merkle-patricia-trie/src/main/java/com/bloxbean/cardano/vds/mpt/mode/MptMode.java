package com.bloxbean.cardano.vds.mpt.mode;

import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;

/**
 * Binds an MPT commitment scheme and proof codec into a single mode.
 */
public interface MptMode {
    String name();

    CommitmentScheme commitments();

    ProofCodec proofCodec();

    boolean hashedKeySpace();
}

