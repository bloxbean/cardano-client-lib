package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.utxostrategy.FixedUtxoRefStrategy;
import com.bloxbean.cardano.client.quicktx.utxostrategy.FixedUtxoStrategy;
import com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Intention to collect specific non-script UTXOs as inputs for Tx.
 * Mirrors Tx.collectFrom(List/Set&lt;Utxo&gt;) and enables YAML serialization.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectFromIntent implements TxInputIntent {

    /**
     * Original UTXOs for runtime use (preserves all UTXO data).
     */
    @JsonIgnore
    private List<Utxo> utxos;

    /**
     * Serializable UTXO references for YAML/JSON.
     */
    @JsonProperty("utxo_refs")
    private List<UtxoRef> utxoRefs;

    @JsonProperty("utxo_refs")
    public List<UtxoRef> getUtxoRefs() {
        if (utxos != null && !utxos.isEmpty()) {
            return utxos.stream().map(UtxoRef::fromUtxo).collect(java.util.stream.Collectors.toList());
        }
        return utxoRefs;
    }

    @Override
    public String getType() {
        return "collect_from";
    }

    @Override
    public void validate() {
        TxInputIntent.super.validate();
        if ((utxos == null || utxos.isEmpty()) && (utxoRefs == null || utxoRefs.isEmpty())) {
            throw new IllegalStateException("UTXOs are required for input collection");
        }
        if (utxoRefs != null) {
            for (UtxoRef ref : utxoRefs) {
                if (ref.getTxHash() == null || ref.getTxHash().isEmpty())
                    throw new IllegalStateException("UTXO transaction hash is required");
                if (ref.getOutputIndex() == null)
                    throw new IllegalStateException("UTXO output index is required");
                int idx = ref.asIntOutputIndex();
                if (idx < 0)
                    throw new IllegalStateException("UTXO output index must be non-negative");
                if (ref.getTxHash().length() != 64)
                    throw new IllegalStateException("Invalid transaction hash format: " + ref.getTxHash());
            }
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        boolean changed = false;

        // Resolve UTxO refs if present
        if (utxoRefs != null && !utxoRefs.isEmpty() && variables != null && !variables.isEmpty()) {
            List<UtxoRef> newRefs = new ArrayList<>(utxoRefs.size());
            for (UtxoRef ref : utxoRefs) {
                if (ref == null) {
                    newRefs.add(null);
                    continue;
                }
                UtxoRef resolved = ref.resolveVariables(variables);
                if (resolved != ref) {
                    changed = true;
                    newRefs.add(resolved);
                } else {
                    newRefs.add(ref);
                }
            }

            if (changed) {
                return this.toBuilder().utxoRefs(newRefs).build();
            }
        }

        return this;
    }

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // No outputs are created by selecting inputs; return null.
        return null;
    }

    @Override
    public LazyUtxoStrategy utxoStrategy() {
        if (utxos != null && !utxos.isEmpty()) {
            return new FixedUtxoStrategy(utxos, null, null);
        }
        // Build from refs
        var inputs = utxoRefs.stream()
                .map(ref -> new TransactionInput(ref.getTxHash(), ref.asIntOutputIndex()))
                .collect(Collectors.toList());
        return new FixedUtxoRefStrategy(inputs, null, null);
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> validate();
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        // No-op; inputs are materialized during input selection phase.
        return (ctx, txn) -> {};
    }

}
