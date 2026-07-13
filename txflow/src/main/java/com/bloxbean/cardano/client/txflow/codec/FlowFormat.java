package com.bloxbean.cardano.client.txflow.codec;

/** Output syntax supported by the portable TxFlow writer. */
public enum FlowFormat {
    /** YAML document output; supported by portable and legacy schemas. */
    YAML,
    /** JSON document output; supported by the portable schema only. */
    JSON
}
