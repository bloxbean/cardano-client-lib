package com.bloxbean.cardano.vds.jmt;

/**
 * CBOR tag identifiers for JMT nodes.
 */
public enum NodeTag {
    INTERNAL(0),
    LEAF(1),
    EXTENSION(2);

    private final int tag;

    NodeTag(int tag) {
        this.tag = tag;
    }

    public int tag() {
        return tag;
    }

    public static NodeTag fromByte(byte value) {
        for (NodeTag t : values()) {
            if (t.tag == (value & 0xFF)) return t;
        }
        throw new IllegalArgumentException("Unknown node tag: " + value);
    }
}

