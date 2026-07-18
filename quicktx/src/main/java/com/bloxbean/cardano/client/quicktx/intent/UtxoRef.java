package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable reference to a concrete transaction output.
 *
 * <p>The canonical form contains a transaction hash and output index. Legacy
 * QuickTx plans may keep the output index as a variable expression until plan
 * binding; {@link #resolveVariables(java.util.Map)} produces a resolved copy
 * when values are available. Flow-relative references use the
 * {@link FlowOutputRef} subtype and are materialized by TxFlow before QuickTx
 * composition.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = UtxoRefDeserializer.class)
public non-sealed class UtxoRef implements TxInputRef {

    @JsonProperty("tx_hash")
    private String txHash;

    @JsonProperty("output_index")
    private Integer outputIndex;

    @JsonIgnore
    @Builder.Default
    private String outputIndexTemplate = null;

    /**
     * Creates a reference from an existing UTXO model.
     *
     * @param utxo UTXO whose transaction hash and index identify the output
     * @return serializable reference to the same output
     */
    public static UtxoRef fromUtxo(Utxo utxo) {
        return UtxoRef.builder()
                .txHash(utxo.getTxHash())
                .outputIndex(utxo.getOutputIndex())
                .build();
    }

    /**
     * Resolves legacy variable expressions without mutating this reference.
     *
     * @param variables values available to the QuickTx plan
     * @return this instance when nothing changes, otherwise a resolved copy
     */
    public UtxoRef resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return this;

        String resolvedTx = VariableResolver.resolve(txHash, variables);

        Integer resolvedIndex = outputIndex;
        String resolvedTemplate = outputIndexTemplate;

        if (outputIndexTemplate != null) {
            String resolvedExpr = VariableResolver.resolve(outputIndexTemplate, variables);
            if (!java.util.Objects.equals(resolvedExpr, outputIndexTemplate)) {
                try {
                    resolvedIndex = Integer.parseInt(resolvedExpr);
                    resolvedTemplate = null;
                } catch (NumberFormatException e) {
                    resolvedTemplate = resolvedExpr;
                    resolvedIndex = null;
                }
            }
        }

        if (!java.util.Objects.equals(resolvedTx, txHash)
                || !java.util.Objects.equals(resolvedIndex, outputIndex)
                || !java.util.Objects.equals(resolvedTemplate, outputIndexTemplate)) {
            return UtxoRef.builder()
                    .txHash(resolvedTx)
                    .outputIndex(resolvedIndex)
                    .outputIndexTemplate(resolvedTemplate)
                    .build();
        }
        return this;
    }

    /**
     * Returns the concrete output index required for transaction composition.
     *
     * @return resolved output index
     * @throws IllegalStateException if the index is missing or still contains a variable expression
     */
    public int asIntOutputIndex() {
        if (outputIndex != null) {
            return outputIndex;
        }
        if (outputIndexTemplate != null) {
            throw new IllegalStateException("Unresolved variable for output_index: " + outputIndexTemplate);
        }
        throw new IllegalStateException("output_index is required");
    }

    /**
     * Returns either the numeric index or its unresolved legacy expression for serialization.
     *
     * @return serialized output-index value
     */
    @JsonGetter("output_index")
    public Object getOutputIndexForYaml() {
        return outputIndexTemplate != null ? outputIndexTemplate : outputIndex;
    }

    /**
     * Accepts a numeric index or a legacy string expression during deserialization.
     *
     * @param value serialized output-index value
     */
    @JsonSetter("output_index")
    public void setOutputIndexFromYaml(Object value) {
        if (value == null) {
            this.outputIndex = null;
            this.outputIndexTemplate = null;
            return;
        }

        if (value instanceof Number) {
            this.outputIndex = ((Number) value).intValue();
            this.outputIndexTemplate = null;
            return;
        }

        String str = value.toString();
        if (str.startsWith("${") && str.endsWith("}")) {
            this.outputIndex = null;
            this.outputIndexTemplate = str;
        } else {
            try {
                this.outputIndex = Integer.parseInt(str);
                this.outputIndexTemplate = null;
            } catch (NumberFormatException e) {
                this.outputIndex = null;
                this.outputIndexTemplate = str;
            }
        }
    }

    /**
     * Sets a resolved output index and clears any retained expression.
     *
     * @param outputIndex concrete output index, or {@code null} to clear it
     */
    public void setOutputIndex(Integer outputIndex) {
        this.outputIndex = outputIndex;
        if (outputIndex != null) {
            this.outputIndexTemplate = null;
        }
    }

    /**
     * Indicates whether composition must wait for variable binding.
     *
     * @return {@code true} when the output index is still an expression
     */
    public boolean hasPlaceholderOutputIndex() {
        return outputIndexTemplate != null;
    }
}
