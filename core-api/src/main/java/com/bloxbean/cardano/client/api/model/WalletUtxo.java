package com.bloxbean.cardano.client.api.model;

import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WalletUtxo extends Utxo {
    private DerivationPath derivationPath;

    public static WalletUtxo from(Utxo utxo) {
        WalletUtxo walletUtxo = new WalletUtxo();
        walletUtxo.setTxHash(utxo.getTxHash());
        walletUtxo.setOutputIndex(utxo.getOutputIndex());
        walletUtxo.setAddress(utxo.getAddress());
        walletUtxo.setAmount(utxo.getAmount());
        walletUtxo.setDataHash(utxo.getDataHash());
        walletUtxo.setInlineDatum(utxo.getInlineDatum());
        walletUtxo.setReferenceScriptHash(utxo.getReferenceScriptHash());
        return walletUtxo;
    }

}
