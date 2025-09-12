package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Utxo;
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
    private Integer outputIndex;

    public static UtxoRef fromUtxo(Utxo utxo) {
        return UtxoRef.builder()
                .txHash(utxo.getTxHash())
                .outputIndex(utxo.getOutputIndex())
                .build();
    }
}
