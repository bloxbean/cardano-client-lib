package com.bloxbean.cardano.client.quicktx.extension;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical, protocol-independent carrier for an intent owned by a QuickTx extension.
 * Namespace aliases are deliberately absent; they are resolved by {@code TxPlanCodec}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionIntent implements TxIntent {
    public static final String TYPE = "extension";

    @JsonProperty("extension_id")
    private String extensionId;

    private String operation;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Override
    public String getType() {
        return TYPE;
    }

    @JsonProperty("type")
    public void setType(String type) {
        if (type != null && !TYPE.equals(type))
            throw new IllegalArgumentException("Expected extension intent type but got " + type);
    }

    @Override
    public void validate() {
        if (extensionId == null || extensionId.isBlank())
            throw new IllegalStateException("Extension intent requires extension_id");
        if (operation == null || operation.isBlank())
            throw new IllegalStateException("Extension intent requires operation");
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        // The owning extension consumes this declaration during its preparation phase.
        return (ctx, txn) -> { };
    }

    @Override
    public boolean hasRedeemer() {
        // Extension operations may materialize scripts. This ensures collateral/evaluation setup
        // is enabled even before the extension replaces the semantic declaration with core intents.
        return true;
    }
}
