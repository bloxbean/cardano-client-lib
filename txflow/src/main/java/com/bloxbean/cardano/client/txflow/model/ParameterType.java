package com.bloxbean.cardano.client.txflow.model;

/** Portable scalar types accepted for flow parameters and runtime bindings. */
public enum ParameterType {
    /** Arbitrary text. */
    STRING,
    /** Signed integral value representable as a Java {@code long}. */
    INTEGER,
    /** Boolean value. */
    BOOLEAN,
    /** Cardano address text, semantically distinguished from a general string. */
    ADDRESS,
    /** Cardano asset-unit text, semantically distinguished from a general string. */
    ASSET_UNIT
}
