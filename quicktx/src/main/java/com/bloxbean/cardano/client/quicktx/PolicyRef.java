package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.function.exception.TxBuildException;

/**
 * Runtime policy lookup reference for QuickTx native minting APIs.
 */
public final class PolicyRef {
    private final String ref;

    private PolicyRef(String ref) {
        this.ref = ref;
    }

    public static PolicyRef ref(String ref) {
        if (ref == null || ref.isBlank())
            throw new TxBuildException("policy ref cannot be null or blank");
        return new PolicyRef(ref);
    }

    public String getRef() {
        return ref;
    }
}
