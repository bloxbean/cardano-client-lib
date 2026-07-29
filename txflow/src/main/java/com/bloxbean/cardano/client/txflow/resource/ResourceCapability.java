package com.bloxbean.cardano.client.txflow.resource;

/** Capability that a logical resource advertises for compile-time preflight. */
public enum ResourceCapability {
    /** May act as a transaction input, fee payer, collateral, or other spending source. */
    SPEND,
    /** May provide a required transaction signature. */
    SIGN,
    /** May authorize minting or burning under a policy. */
    MINT,
    /** May provide or identify a script required by a transaction. */
    SCRIPT
}
