package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy;
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
public class ReferenceInputIntent implements TxInputIntent {

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
            if (r.getOutputIndex() == null && !r.hasPlaceholderOutputIndex())
                throw new IllegalStateException("output_index is required for reference input");
            if (r.getOutputIndex() != null && r.getOutputIndex() < 0)
                throw new IllegalStateException("output_index must be non-negative");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return this;

        if (refs == null || refs.isEmpty()) return this;

        boolean changed = false;
        List<UtxoRef> newRefs = new ArrayList<>(refs.size());
        for (UtxoRef r : refs) {
            if (r == null) {
                newRefs.add(null);
                continue;
            }
            UtxoRef resolved = r.resolveVariables(variables);
            if (resolved != r) {
                changed = true;
                newRefs.add(resolved);
            } else {
                newRefs.add(r);
            }
        }

        if (changed) return this.toBuilder().refs(newRefs).build();
        return this;
    }

    @Override
    public LazyUtxoStrategy utxoStrategy() {
        // Reference inputs don't consume UTXOs, return null
        return null;
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
                TransactionInput ti = new TransactionInput(r.getTxHash(), r.asIntOutputIndex());
                if (!list.contains(ti)) list.add(ti);
            }
        };
    }

    // Helper to add a ref
    public ReferenceInputIntent addRef(String txHash, int outputIndex) {
        if (refs == null) refs = new ArrayList<>();
        refs.add(UtxoRef.builder().txHash(txHash).outputIndex(outputIndex).build());
        return this;
    }

    // Factory helpers
    public static ReferenceInputIntent of(String txHash, int outputIndex) {
        return ReferenceInputIntent.builder()
                .refs(new ArrayList<>(List.of(UtxoRef.builder().txHash(txHash).outputIndex(outputIndex).build())))
                .build();
    }

    public static ReferenceInputIntent of(List<UtxoRef> refs) {
        return ReferenceInputIntent.builder().refs(new ArrayList<>(refs)).build();
    }

}
