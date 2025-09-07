package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class providing common predicates for UTXO selection in ScriptTx operations.
 */
public class ScriptPredicates {
    
    private ScriptPredicates() {
        // Utility class
    }
    
    // Single UTXO predicates
    
    /**
     * Select UTXO with specific datum hash.
     */
    public static Predicate<Utxo> withDatumHash(String datumHash) {
        return utxo -> datumHash != null && datumHash.equals(utxo.getDataHash());
    }
    
    /**
     * Select UTXO with specific inline datum.
     */
    public static Predicate<Utxo> withInlineDatum(String inlineDatum) {
        return utxo -> inlineDatum != null && inlineDatum.equals(utxo.getInlineDatum());
    }
    
    /**
     * Select UTXO with minimum ADA amount.
     */
    public static Predicate<Utxo> withMinAda(BigInteger minAda) {
        return utxo -> {
            if (utxo.getAmount() == null) return false;
            return utxo.getAmount().stream()
                .filter(amount -> amount.getUnit().equals("lovelace"))
                .findFirst()
                .map(amount -> amount.getQuantity().compareTo(minAda) >= 0)
                .orElse(false);
        };
    }
    
    /**
     * Select UTXO that has a reference script.
     */
    public static Predicate<Utxo> withReferenceScript() {
        return utxo -> utxo.getReferenceScriptHash() != null && !utxo.getReferenceScriptHash().isEmpty();
    }
    
    /**
     * Select specific UTXO by transaction hash and output index.
     */
    public static Predicate<Utxo> withSpecificUtxo(String txHash, int outputIndex) {
        return utxo -> txHash.equals(utxo.getTxHash()) && outputIndex == utxo.getOutputIndex();
    }
    
    /**
     * Select UTXO containing specific token.
     */
    public static Predicate<Utxo> withToken(String policyId, String assetName) {
        String unit = policyId + assetName;
        return utxo -> {
            if (utxo.getAmount() == null) return false;
            return utxo.getAmount().stream()
                .anyMatch(amount -> unit.equals(amount.getUnit()) && amount.getQuantity().compareTo(BigInteger.ZERO) > 0);
        };
    }
    
    /**
     * Select UTXO containing any amount of a specific token (by unit).
     */
    public static Predicate<Utxo> withTokenUnit(String unit) {
        return utxo -> {
            if (utxo.getAmount() == null) return false;
            return utxo.getAmount().stream()
                .anyMatch(amount -> unit.equals(amount.getUnit()) && amount.getQuantity().compareTo(BigInteger.ZERO) > 0);
        };
    }
    
    // List predicates
    
    /**
     * Select first N UTXOs.
     */
    public static Predicate<List<Utxo>> firstN(int n) {
        return utxos -> {
            utxos.clear();
            return false;  // This will be handled by the strategy implementation
        };
    }
    
    /**
     * Select largest UTXOs first up to count.
     */
    public static Predicate<List<Utxo>> largestFirst(int count) {
        return utxos -> {
            List<Utxo> sorted = utxos.stream()
                .sorted(Comparator.comparing(ScriptPredicates::getAdaAmount).reversed())
                .limit(count)
                .collect(Collectors.toList());
            utxos.clear();
            utxos.addAll(sorted);
            return !utxos.isEmpty();
        };
    }
    
    /**
     * Select UTXOs with at least the specified total ADA.
     */
    public static Predicate<List<Utxo>> withTotalAda(BigInteger totalAda) {
        return utxos -> {
            BigInteger sum = BigInteger.ZERO;
            List<Utxo> selected = new java.util.ArrayList<>();
            
            for (Utxo utxo : utxos) {
                selected.add(utxo);
                sum = sum.add(getAdaAmount(utxo));
                if (sum.compareTo(totalAda) >= 0) {
                    utxos.clear();
                    utxos.addAll(selected);
                    return true;
                }
            }
            
            return false;  // Not enough ADA
        };
    }
    
    /**
     * Select all UTXOs.
     */
    public static Predicate<List<Utxo>> all() {
        return utxos -> !utxos.isEmpty();
    }
    
    // Helper methods
    
    private static BigInteger getAdaAmount(Utxo utxo) {
        if (utxo.getAmount() == null) return BigInteger.ZERO;
        return utxo.getAmount().stream()
            .filter(amount -> "lovelace".equals(amount.getUnit()))
            .findFirst()
            .map(Amount::getQuantity)
            .orElse(BigInteger.ZERO);
    }
}