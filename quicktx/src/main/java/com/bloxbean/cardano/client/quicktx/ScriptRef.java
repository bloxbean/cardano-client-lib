package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.function.exception.TxBuildException;

/**
 * Runtime script lookup reference for QuickTx attachment APIs.
 */
public final class ScriptRef {
    private final String ref;
    private final String hash;

    private ScriptRef(String ref, String hash) {
        this.ref = ref;
        this.hash = hash;
    }

    public static ScriptRef ref(String ref) {
        if (ref == null || ref.isBlank())
            throw new TxBuildException("script ref cannot be null or blank");
        return new ScriptRef(ref, null);
    }

    public static ScriptRef hash(String scriptHash) {
        if (scriptHash == null || scriptHash.isBlank())
            throw new TxBuildException("script hash cannot be null or blank");
        return new ScriptRef(null, scriptHash);
    }

    public String getRef() {
        return ref;
    }

    public String getHash() {
        return hash;
    }
}
