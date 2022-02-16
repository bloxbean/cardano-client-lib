package com.bloxbean.cardano.client.function.helper.model;

import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.ExUnits;
import com.bloxbean.cardano.client.transaction.spec.PlutusScript;
import com.bloxbean.cardano.client.transaction.spec.RedeemerTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A class for Plutus script specific data used in transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptCallContext<T, K> {
    private PlutusScript script;
    private Utxo utxo;
    private T datum;
    private K redeemer;

    @Builder.Default
    private RedeemerTag redeemerTag = RedeemerTag.Spend;

    private ExUnits exUnits;
}
