package com.bloxbean.cardano.client.quicktx.extension;

import com.bloxbean.cardano.client.transaction.spec.Transaction;

/** One build's mutable extension participant. Instances must never be shared across builds. */
public interface TxBuildExtension {
    /**
     * Whether witness copies of scripts already supplied by transaction reference inputs should
     * be removed before serialization. This remains build-local so installing an extension does
     * not change unrelated builder instances.
     */
    default boolean removeDuplicateScriptWitnesses() {
        return false;
    }

    default void prepare(ExtensionBuildContext context) { }

    default void beforeScriptEvaluation(ExtensionBuildContext context, Transaction transaction) { }

    default BalanceFinalization afterBalance(ExtensionBuildContext context, Transaction transaction) {
        return BalanceFinalization.STABLE;
    }

    default void verify(ExtensionBuildContext context, Transaction transaction) { }
}
