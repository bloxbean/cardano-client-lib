package com.bloxbean.cardano.client.quicktx.extension;

import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Collection;
import java.util.List;

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

    /**
     * Scripts this build's reference inputs supply, read after {@link #prepare}. The builder
     * registers them like the caller's own {@code withReferenceScripts}, so the reference-script
     * fee is charged and, with {@link #removeDuplicateScriptWitnesses()}, their witness copies
     * are dropped — including when the caller's own reference scripts switch off resolving them
     * from the chain.
     */
    default Collection<PlutusScript> referenceScripts() {
        return List.of();
    }

    default void prepare(ExtensionBuildContext context) { }

    default void beforeScriptEvaluation(ExtensionBuildContext context, Transaction transaction) { }

    default BalanceFinalization afterBalance(ExtensionBuildContext context, Transaction transaction) {
        return BalanceFinalization.STABLE;
    }

    default void verify(ExtensionBuildContext context, Transaction transaction) { }
}
