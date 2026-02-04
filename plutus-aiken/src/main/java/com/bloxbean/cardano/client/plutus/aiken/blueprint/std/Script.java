package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

/** Byte-array representation of a script as defined by the Aiken standard library. */
public final class Script extends ByteArrayWrapper {

    public Script(byte[] value) {
        super(value);
    }

    public static Script of(byte[] value) {
        return new Script(value);
    }
}
