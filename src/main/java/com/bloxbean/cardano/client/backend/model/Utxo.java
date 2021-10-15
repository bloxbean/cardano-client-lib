package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Utxo {
    private String txHash;
    private int outputIndex;
    private List<Amount> amount;
    private String dataHash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Utxo utxo = (Utxo) o;
        return outputIndex == utxo.outputIndex && txHash.equals(utxo.txHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txHash, outputIndex);
    }
}


