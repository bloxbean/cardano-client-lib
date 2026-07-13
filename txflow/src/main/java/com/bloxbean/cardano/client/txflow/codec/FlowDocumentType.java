package com.bloxbean.cardano.client.txflow.codec;

/** Best-effort classification of a YAML or JSON document accepted by QuickTx tooling. */
public enum FlowDocumentType {
    /** A portable or legacy TxFlow document. */
    TX_FLOW,
    /** A standalone QuickTx transaction plan document. */
    TX_PLAN,
    /** A malformed document or one without a recognized top-level shape. */
    UNKNOWN
}
