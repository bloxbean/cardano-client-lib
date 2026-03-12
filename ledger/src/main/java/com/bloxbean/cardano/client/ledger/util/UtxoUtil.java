package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility for converting between CCL API model UTxOs and spec-level types.
 */
public class UtxoUtil {

    private UtxoUtil() {}

    /**
     * Convert a set of API-model UTxOs to a map of TransactionInput → TransactionOutput.
     *
     * @param utxos the UTxO set from the API model
     * @return resolved UTxO map suitable for validation
     */
    public static Map<TransactionInput, TransactionOutput> toUtxoMap(Set<Utxo> utxos) {
        Map<TransactionInput, TransactionOutput> map = new HashMap<>();
        if (utxos == null) return map;

        for (Utxo utxo : utxos) {
            TransactionInput input = TransactionInput.builder()
                    .transactionId(utxo.getTxHash())
                    .index(utxo.getOutputIndex())
                    .build();

            TransactionOutput output = toTransactionOutput(utxo);
            map.put(input, output);
        }
        return map;
    }

    private static TransactionOutput toTransactionOutput(Utxo utxo) {
        TransactionOutput.TransactionOutputBuilder builder = TransactionOutput.builder()
                .address(utxo.getAddress())
                .value(utxo.toValue());

        if (utxo.getDataHash() != null && !utxo.getDataHash().isEmpty()) {
            builder.datumHash(HexUtil.decodeHexString(utxo.getDataHash()));
        }

        return builder.build();
    }
}
