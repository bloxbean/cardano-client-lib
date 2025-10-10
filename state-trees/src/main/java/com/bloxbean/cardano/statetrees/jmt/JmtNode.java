package com.bloxbean.cardano.statetrees.jmt;

/**
 * Base type for Jellyfish Merkle Tree nodes.
 */
public interface JmtNode {

    NodeTag tag();

    byte[] encode();
}

