package com.bloxbean.cardano.client.backend.model;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TxContentUtxoOutputs {

    private String address;

    @Builder.Default
    private List<TxContentOutputAmount> amount = new ArrayList<>();
    private int outputIndex;
    private String dataHash;
    private String inlineDatum;
    private String referenceScriptHash;

    public Utxo toUtxos() {
        Utxo utxo = new Utxo();
        utxo.setAddress(this.getAddress());

        List<Amount> amounts = this.getAmount() != null? this.getAmount()
                .stream().map(amount -> new Amount(amount.getUnit(), new BigInteger(amount.getQuantity())))
                .collect(Collectors.toList()): Collections.emptyList();
        utxo.setAmount(amounts);

        utxo.setOutputIndex(this.getOutputIndex());
        utxo.setDataHash(this.getDataHash());
        utxo.setInlineDatum(this.getInlineDatum());
        utxo.setReferenceScriptHash(this.getReferenceScriptHash());

        return utxo;
    }
}
