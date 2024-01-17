package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.List;
import java.util.Set;

public interface HDWalletUtxoSelectionStrategy {

    Set<Utxo> select(List<Utxo> inputUtxos, List<Amount> outputAmounts, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit);
}
