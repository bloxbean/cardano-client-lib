package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Intention to add reference inputs to a transaction body.
 * Maps to ScriptTx.readFrom(...) overloads.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceInputIntention implements TxIntention {

    @JsonProperty("refs")
    @Builder.Default
    private List<UtxoRef> refs = new ArrayList<>();

    @Override
    public String getType() {
        return "reference_input";
    }

    @Override
    public void validate() {
        if (refs == null || refs.isEmpty()) {
            throw new IllegalStateException("ReferenceInputIntention requires at least one ref");
        }
        for (UtxoRef r : refs) {
            if (r.getTxHash() == null || r.getTxHash().isBlank())
                throw new IllegalStateException("tx_hash is required for reference input");
            if (r.getOutputIndex() == null || r.getOutputIndex() < 0)
                throw new IllegalStateException("output_index is required for reference input");
        }
    }

    @Override
    public TxIntention resolveVariables(java.util.Map<String, Object> variables) {
        // No string fields to resolve
        return this;
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> validate();
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            List<TransactionInput> list = txn.getBody().getReferenceInputs();
            if (list == null) {
                list = new ArrayList<>();
                txn.getBody().setReferenceInputs(list);
            }
            for (UtxoRef r : refs) {
                TransactionInput ti = new TransactionInput(r.getTxHash(), r.getOutputIndex());
                if (!list.contains(ti)) list.add(ti);
            }
        };
    }

    // Helper to add a ref
    public ReferenceInputIntention addRef(String txHash, int outputIndex) {
        if (refs == null) refs = new ArrayList<>();
        refs.add(new UtxoRef(txHash, outputIndex));
        return this;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UtxoRef {
        @JsonProperty("tx_hash")
        private String txHash;
        @JsonProperty("output_index")
        private Integer outputIndex;
    }

    // Factory helpers
    public static ReferenceInputIntention of(String txHash, int outputIndex) {
        return ReferenceInputIntention.builder()
                .refs(new ArrayList<>(List.of(new UtxoRef(txHash, outputIndex))))
                .build();
    }

    public static ReferenceInputIntention of(List<UtxoRef> refs) {
        return ReferenceInputIntention.builder().refs(new ArrayList<>(refs)).build();
    }
}

