package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.watcher.chain.ChainContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Strategy interface for selecting UTXOs from a previous step's outputs.
 * 
 * Different strategies can be used to select which UTXOs from a previous
 * step should be used as inputs for the current step.
 */
public interface UtxoSelectionStrategy {
    
    /**
     * Select UTXOs from the available outputs of a previous step.
     * 
     * @param availableUtxos the UTXOs available from the previous step
     * @param context the chain context for additional information
     * @return the selected UTXOs to use as inputs
     */
    List<Utxo> selectUtxos(List<Utxo> availableUtxos, ChainContext context);
    
    // Pre-defined strategies
    
    /**
     * Strategy that selects all available UTXOs.
     */
    UtxoSelectionStrategy ALL = (utxos, ctx) -> new ArrayList<>(utxos);
    
    /**
     * Strategy that selects UTXOs in descending order by ADA value.
     */
    UtxoSelectionStrategy LARGEST_FIRST = (utxos, ctx) -> utxos.stream()
        .sorted((u1, u2) -> {
            // Compare by ADA value (lovelace)
            return u2.getAmount().stream()
                .filter(amount -> "lovelace".equals(amount.getUnit()))
                .findFirst()
                .orElse(com.bloxbean.cardano.client.api.model.Amount.ada(0))
                .getQuantity()
                .compareTo(
                    u1.getAmount().stream()
                        .filter(amount -> "lovelace".equals(amount.getUnit()))
                        .findFirst()
                        .orElse(com.bloxbean.cardano.client.api.model.Amount.ada(0))
                        .getQuantity()
                );
        })
        .collect(Collectors.toList());
    
    /**
     * Strategy that selects UTXOs in ascending order by ADA value.
     */
    UtxoSelectionStrategy SMALLEST_FIRST = (utxos, ctx) -> utxos.stream()
        .sorted((u1, u2) -> {
            // Compare by ADA value (lovelace)
            return u1.getAmount().stream()
                .filter(amount -> "lovelace".equals(amount.getUnit()))
                .findFirst()
                .orElse(com.bloxbean.cardano.client.api.model.Amount.ada(0))
                .getQuantity()
                .compareTo(
                    u2.getAmount().stream()
                        .filter(amount -> "lovelace".equals(amount.getUnit()))
                        .findFirst()
                        .orElse(com.bloxbean.cardano.client.api.model.Amount.ada(0))
                        .getQuantity()
                );
        })
        .collect(Collectors.toList());
}