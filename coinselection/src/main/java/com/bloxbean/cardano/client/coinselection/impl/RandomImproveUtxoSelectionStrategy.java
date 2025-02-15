package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.coinselection.exception.InputsLimitExceededException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * https://input-output-hk.github.io/cardano-coin-selection/haddock/cardano-coin-selection-1.0.1/Cardano-CoinSelection-Algorithm-RandomImprove.html
 */
public class RandomImproveUtxoSelectionStrategy implements UtxoSelectionStrategy {

    private final SecureRandom secureRandom = initRandomGenerator();

    private final UtxoSupplier utxoSupplier;
    @Setter
    private boolean ignoreUtxosWithDatumHash;

    public RandomImproveUtxoSelectionStrategy(UtxoSupplier utxoSupplier) {
        this(utxoSupplier, true);
    }

    public RandomImproveUtxoSelectionStrategy(UtxoSupplier utxoSupplier, boolean ignoreUtxosWithDatumHash) {
        this.utxoSupplier = utxoSupplier;
        this.ignoreUtxosWithDatumHash = ignoreUtxosWithDatumHash;
    }

    @Override
    public Set<Utxo> select(AddressIterator addrIter, List<Amount> outputAmounts, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        try{
            /*
             * Phase 1: Random Selection
             *
             * Goal: randomly select a minimal set of UTxO entries to pay for each of the given outputs.
             *
             * - process outputs in descending order of coin value.
             * - maintain a remaining UTxO set, initially equal to the given UTxO set parameter.
             *
             * For each output of value v
             *     randomly select entries from the remaining UTxO set, until the total value of selected entries is greater than or equal to v.
             *     The selected entries are then associated with that output, and removed from the remaining UTxO set.
             *
             * This phase ends when every output has been associated with a selection of UTxO entries.
             *
             * If the remaining UTxO set is completely exhausted before all outputs can be processed, the algorithm terminates with an error.
             */

            //Reset the iterator incase it's reused
            if (addrIter != null)
                addrIter.reset();

            List<Utxo> allUtxos = new ArrayList<>();
            while (addrIter.hasNext()) {
                var utxos = this.utxoSupplier.getAll(addrIter.next().toBech32());
                allUtxos.addAll(utxos);
            }

            var randomPhaseResult = selectRandom(outputAmounts,allUtxos , datumHash, inlineDatum, utxosToExclude, maxUtxoSelectionLimit);

            /*
             * Phase 2: Improvement
             *
             * attempts to improve upon each of the UTxO selections made in the previous phase, by conservatively expanding the selection made for each output
             *
             * - process outputs in ascending order of coin value.
             * - continue to maintain the remaining UTxO set produced by the previous phase.
             * - maintain an accumulated coin selection, which is initially empty.
             *
             * For each output of value v
             *     - Calculates a target range for the total value of inputs used to pay for that output
             *         (minimum, ideal, maximum) = (v, 2v, 3v)
             *         --> goal is to have double as much input as required output (which is then send to change address)
             *
             *     - Attempts to improve upon the existing UTxO selection for that output, by repeatedly selecting additional entries at random from the remaining UTxO set, stopping when the selection can be improved upon no further.
             *
             *         A selection with value v1 is considered to be an improvement over a selection with value v0 if all of the following conditions are satisfied:
             *
             *             - Condition 1: we have moved closer to the ideal value:
             *                 abs (ideal − v1) < abs (ideal − v0)
             *
             *             - Condition 2: we have not exceeded the maximum value:
             *                 v1 ≤ maximum
             *
             *             - Condition 3: when counting cumulatively across all outputs considered so far, we have not selected more than the maximum number of UTxO entries specified by limit.
             *
             */
            var improvedResult = improve(outputAmounts, randomPhaseResult, datumHash, inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
            return Stream.concat(randomPhaseResult.getSelectedUtxos().stream(),
                                 improvedResult.stream()).collect(Collectors.toSet());
        }catch(InputsLimitExceededException e){
            var fallback = fallback();
            if(fallback != null){
                return fallback.select(addrIter, outputAmounts, datumHash, inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
            }
            throw new ApiRuntimeException("Input limit exceeded and no fallback provided", e);
        }
    }

    private Set<Utxo> improve(List<Amount> outputAmounts, RandomPhaseResult randomPhaseResult, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit){
        final Map<String, BigInteger> outputsToProcess = outputAmounts.stream()
                .collect(Collectors.groupingBy(Amount::getUnit,
                        Collectors.reducing(BigInteger.ZERO,
                                Amount::getQuantity,
                                BigInteger::add)))
                .entrySet().stream()
                .filter(entry -> BigInteger.ZERO.compareTo(entry.getValue()) < 0)
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, BigInteger::add, TreeMap::new));
        final Set<Utxo> selectedUtxos = new HashSet<>();
        for(var entry : outputsToProcess.entrySet()){
            final List<Utxo> availableUtxos = new ArrayList<>(randomPhaseResult.getAllAvailableUtxos());
            availableUtxos.removeAll(selectedUtxos);
            availableUtxos.removeAll(randomPhaseResult.getSelectedUtxos());

            var requiredAmount = new Amount(entry.getKey(), entry.getValue());
            var idealTarget = entry.getValue().multiply(BigInteger.valueOf(2));
            var maxTarget = entry.getValue().multiply(BigInteger.valueOf(3));

            BigInteger processedAmount = Stream.concat(selectedUtxos.stream(), randomPhaseResult.getSelectedUtxos().stream())
                    .flatMap(utxo -> utxo.getAmount().stream())
                    .filter(utxo -> isEqualUnit(utxo.getUnit(), entry.getKey()))
                    .map(Amount::getQuantity)
                    .reduce(BigInteger.ZERO, BigInteger::add);
            if(processedAmount.compareTo(maxTarget) >= 0){
                // already reached max for asset
                continue;
            }
            if(processedAmount.equals(idealTarget)){
                // ideal target reached
                continue;
            }

            while(true){
                var randomUtxo = selectRandomUtxo(requiredAmount.getUnit(), availableUtxos, datumHash, inlineDatum, utxosToExclude);
                if(randomUtxo == null){
                    break;
                }
                var utxoAmount = randomUtxo.getAmount().stream()
                        .filter(utxo -> isEqualUnit(utxo.getUnit(), requiredAmount.getUnit()))
                        .reduce(new Amount(requiredAmount.getUnit(), BigInteger.ZERO),
                                RandomImproveUtxoSelectionStrategy::add);
                var potentiallyNewProcessedAmount = processedAmount.add(utxoAmount.getQuantity());
                var isImprovement = idealTarget.subtract(potentiallyNewProcessedAmount).abs()
                                               .compareTo(idealTarget.subtract(processedAmount).abs()) < 0
                                        && potentiallyNewProcessedAmount.compareTo(maxTarget) <= 0
                                        && randomPhaseResult.getSelectedUtxos().size() + selectedUtxos.size() < maxUtxoSelectionLimit;
                if(isImprovement){
                    selectedUtxos.add(randomUtxo);
                    processedAmount = potentiallyNewProcessedAmount;
                    if(processedAmount.equals(idealTarget)){
                        // ideal target reached exactly
                        break;
                    }
                }
                availableUtxos.remove(randomUtxo);
            }
        }
        return selectedUtxos;
    }

    private RandomPhaseResult selectRandom(List<Amount> outputAmounts, List<Utxo> allAvailableUtxos, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) throws InputsLimitExceededException{
        if(allAvailableUtxos == null || allAvailableUtxos.isEmpty()){
            throw new InsufficientBalanceException("No UTXOs available");
        }
        final Map<String, BigInteger> outputsToProcess = outputAmounts.stream()
                .collect(Collectors.groupingBy(Amount::getUnit,
                        Collectors.reducing(BigInteger.ZERO,
                                Amount::getQuantity,
                                BigInteger::add)))
                .entrySet().stream()
                .filter(entry -> BigInteger.ZERO.compareTo(entry.getValue()) < 0)
                .sorted((it1, it2) -> it2.getValue().compareTo(it1.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, BigInteger::add, TreeMap::new));
        final List<Utxo> availableUtxos = new ArrayList<>(allAvailableUtxos);
        final Map<String, BigInteger> remainingOutputs = new HashMap<>(outputsToProcess);
        final Set<Utxo> selectedUtxos = new HashSet<>();

        for(var entry : outputsToProcess.entrySet()){
            while(remainingOutputs.containsKey(entry.getKey())
                    && BigInteger.ZERO.compareTo(remainingOutputs.get(entry.getKey())) < 0){
                // calculate required amount
                    // start from entry key - value
                    // then subtract all from selectedUtxos (UTXOs can contain multiple assets)
                var requiredUnit = entry.getKey();

                var randomUtxo = selectRandomUtxo(requiredUnit, availableUtxos, datumHash, inlineDatum, utxosToExclude);
                if(randomUtxo == null){
                    throw new InsufficientBalanceException("Unable to find random UTXO for " + requiredUnit);
                }
                selectedUtxos.add(randomUtxo);
                availableUtxos.remove(randomUtxo);

                for(var amountToRemove : randomUtxo.getAmount()){
                    var existing = remainingOutputs.getOrDefault(amountToRemove.getUnit(), BigInteger.ZERO);
                    var adjusted = existing.subtract(amountToRemove.getQuantity());
                    if(BigInteger.ZERO.compareTo(adjusted) < 0){
                        remainingOutputs.put(amountToRemove.getUnit(), adjusted);
                    }else{
                        remainingOutputs.remove(amountToRemove.getUnit());
                    }
                }

                if(!remainingOutputs.isEmpty() && selectedUtxos.size() > maxUtxoSelectionLimit){
                    throw new InputsLimitExceededException("Selection limit of " + maxUtxoSelectionLimit + " utxos reached with " + remainingOutputs + " remaining");
                }
            }
        }
        return new RandomPhaseResult(selectedUtxos, availableUtxos);
    }

    private Utxo selectRandomUtxo(String requiredAsset, List<Utxo> allAvailableUtxos, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude){
        if(allAvailableUtxos.isEmpty()){
            return null;
        }
        var available = new ArrayList<>(allAvailableUtxos);
        // randomly select entries from the remaining UTxO set, until the total value of selected entries is greater than or equal to v.
        var randomIndex = secureRandom.nextInt(available.size());
        var utxo = available.get(randomIndex);

        //TODO - add tests to cover inline datum
        if(!accept(utxo)
            || (utxosToExclude != null && utxosToExclude.contains(utxo))
            || (utxo.getDataHash() != null && !utxo.getDataHash().isEmpty() && ignoreUtxosWithDatumHash)
            || (datumHash != null && !datumHash.isEmpty() && !datumHash.equals(utxo.getDataHash()))
            || (inlineDatum != null && !inlineDatum.serializeToHex().equals(utxo.getInlineDatum()))){
            // remove from available + try again
            available.remove(randomIndex);
            return selectRandomUtxo(requiredAsset, available, datumHash, inlineDatum, utxosToExclude);
        }

        // The selected entries are then associated with that output, and removed from the remaining UTxO set.
        for(Amount amount: utxo.getAmount()) {
            if(isEqualUnit(amount.getUnit(), requiredAsset)){
                return utxo;
            }
        }

        // not found, try again
        available.remove(randomIndex);
        return selectRandomUtxo(requiredAsset, available, datumHash, inlineDatum, utxosToExclude);
    }

    private static Amount add(Amount a1, Amount a2){
        if(a1 == null){
            return a2;
        }
        if(a2 == null){
            return a1;
        }
        if(!isEqualUnit(a1.getUnit(), a2.getUnit())){
            throw new IllegalArgumentException("Failed to add [" + a1 + "] and [" + a2 + "] due to unit miss-match");
        }
        return new Amount(a1.getUnit(), a1.getQuantity().add(a2.getQuantity()));
    }

    private static boolean isEqualUnit(String u1, String u2){
        if (u1 == u2) {
            return true;
        }
        if (u1 == null || u2 == null) {
            return false;
        }
        return u1.equals(u2);
    }

    protected boolean accept(Utxo utxo) {
        return true;
    }

    private static SecureRandom initRandomGenerator(){
        try{
            return SecureRandom.getInstance("SHA1PRNG");
        }catch(NoSuchAlgorithmException e){
            throw new IllegalStateException("Invalid algorithm for secure random", e);
        }
    }

    @Override
    public UtxoSelectionStrategy fallback() {
        return new LargestFirstUtxoSelectionStrategy(this.utxoSupplier, this.ignoreUtxosWithDatumHash);
    }

    @Getter
    @ToString
    private static class RandomPhaseResult{
        private final Set<Utxo> selectedUtxos = new HashSet<>();
        private final List<Utxo> allAvailableUtxos = new ArrayList<>();

        public RandomPhaseResult(Set<Utxo> selectedUtxos, List<Utxo> allAvailableUtxos) {
            if(selectedUtxos != null){
                this.selectedUtxos.addAll(selectedUtxos);
            }
            if(allAvailableUtxos != null){
                this.allAvailableUtxos.addAll(allAvailableUtxos);
            }
        }
    }
}
