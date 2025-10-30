package com.bloxbean.cardano.vds.jmt;

/**
 * Base type for Jellyfish Merkle Tree nodes.
 */
public interface JmtNode {

    NodeTag tag();

    byte[] encode();
}

