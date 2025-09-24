package com.bloxbean.cardano.statetrees.jmt.mode;

import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

/**
 * Binds a JMT commitment scheme with a proof codec.
 */
public interface JmtMode {
    String name();

    CommitmentScheme commitments();

    JmtProofCodec proofCodec();
}

