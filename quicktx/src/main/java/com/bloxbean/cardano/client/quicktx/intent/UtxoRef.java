package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable UTXO reference with support for variable placeholders.
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
    private Integer outputIndex;

    @JsonIgnore
    @Builder.Default
    private String outputIndexTemplate = null;

    public static UtxoRef fromUtxo(Utxo utxo) {
        return UtxoRef.builder()
                .txHash(utxo.getTxHash())
                .outputIndex(utxo.getOutputIndex())
                .build();
    }

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

    public int asIntOutputIndex() {
        if (outputIndex != null) {
            return outputIndex;
        }
        if (outputIndexTemplate != null) {
            throw new IllegalStateException("Unresolved variable for output_index: " + outputIndexTemplate);
        }
        throw new IllegalStateException("output_index is required");
    }

    @JsonGetter("output_index")
    public Object getOutputIndexForYaml() {
        return outputIndexTemplate != null ? outputIndexTemplate : outputIndex;
    }

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

    public void setOutputIndex(Integer outputIndex) {
        this.outputIndex = outputIndex;
        if (outputIndex != null) {
            this.outputIndexTemplate = null;
        }
    }

    public boolean hasPlaceholderOutputIndex() {
        return outputIndexTemplate != null;
    }
}
