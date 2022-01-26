package com.bloxbean.cardano.client.coinselection.impl.model;

import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
public class SelectionResult {

    private final List<Utxo> selection;
    private final Set<TransactionOutput> outputs;
    private final List<Utxo> remaining;
    private final Value amount;
    private final Value change;
}
