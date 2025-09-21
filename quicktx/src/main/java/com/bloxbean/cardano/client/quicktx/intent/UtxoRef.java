package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable UTXO reference.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UtxoRef {
    @JsonProperty("tx_hash")
    private String txHash;

    @JsonProperty("output_index")
    private String outputIndex;

    public static UtxoRef fromUtxo(Utxo utxo) {
        return UtxoRef.builder()
                .txHash(utxo.getTxHash())
                .outputIndex(String.valueOf(utxo.getOutputIndex()))
                .build();
    }

    /**
     * Resolve variables in tx_hash and output_index. Returns a new instance if any field changes; otherwise returns this.
     */
    public UtxoRef resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return this;

        String resolvedTx = VariableResolver.resolve(txHash, variables);
        String resolvedIdx = VariableResolver.resolve(outputIndex, variables);

        if (!java.util.Objects.equals(resolvedTx, txHash) || !java.util.Objects.equals(resolvedIdx, outputIndex)) {
            return UtxoRef.builder()
                    .txHash(resolvedTx)
                    .outputIndex(resolvedIdx)
                    .build();
        }
        return this;
    }

    /**
     * Parse output_index to int with validation.
     * @return output index as int
     * @throws IllegalStateException if null, unresolved or not a valid integer
     */
    public int asIntOutputIndex() {
        if (outputIndex == null)
            throw new IllegalStateException("output_index is required");
        if (outputIndex.startsWith("${"))
            throw new IllegalStateException("Unresolved variable for output_index: " + outputIndex);
        try {
            return Integer.parseInt(outputIndex);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid output_index value: " + outputIndex);
        }
    }
}
