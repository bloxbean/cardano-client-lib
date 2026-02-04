package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

/** Hash of a script (Blake2b-224). */
public final class ScriptHash extends Hash {

    public ScriptHash(byte[] value) {
        super(value, "Blake2b_224");
    }

    public static ScriptHash of(byte[] value) {
        return new ScriptHash(value);
    }

}
