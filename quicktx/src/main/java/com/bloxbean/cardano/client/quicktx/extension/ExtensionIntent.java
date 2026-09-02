package com.bloxbean.cardano.client.quicktx.extension;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Marker for a typed, extension-owned semantic transaction intent.
 *
 * <p>Unlike directly applicable core intents, extension intents are declarations consumed by the
 * registered {@link TxBuildExtension} during {@link TxBuildExtension#prepare(ExtensionBuildContext)}.
 * They must not perform chain I/O while being authored and generally must not materialize
 * themselves through {@link #apply(IntentContext)} because an extension may need to aggregate all
 * of its intents before selecting inputs or changing the transaction.</p>
 *
 * <p>Concrete intent classes belong to extension modules. QuickTx only uses the canonical
 * extension and operation identifiers for ownership validation, routing, and TxPlan codecs.</p>
 */
public interface ExtensionIntent extends TxIntent {

    /** Stable extension identity, independent of a TxPlan document namespace. */
    @JsonIgnore
    String getExtensionId();

    /** Stable operation identity within the owning extension. */
    @JsonIgnore
    String getOperation();

    /** Canonical in-memory type id used by an instance-scoped extension codec. */
    @Override
    @JsonProperty("type")
    default String getType() {
        return canonicalType(getExtensionId(), getOperation());
    }

    /** Accept and verify the canonical type property exposed by Jackson polymorphic decoding. */
    @JsonProperty("type")
    default void setType(String type) {
        String expected = getType();
        if (type != null && !expected.equals(type))
            throw new IllegalArgumentException("Expected extension intent type " + expected
                    + " but got " + type);
    }

    static String canonicalType(String extensionId, String operation) {
        return extensionId + ":" + operation;
    }

    /**
     * Extension semantic intents are planned in aggregate by {@code TxBuildExtension.prepare()}.
     * This no-op is intentional and is not an extension execution hook.
     */
    @Override
    default TxBuilder apply(IntentContext context) {
        return (ctx, txn) -> { };
    }

    @Override
    default boolean hasRedeemer() {
        // The declaration may materialize scripts during extension preparation.
        return true;
    }
}
