package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.exception.InputsLimitExceededException;
import lombok.Setter;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Largest-first implementation of {@link UtxoSelectionStrategy}
 */
public class LargestFirstUtxoSelectionStrategy implements UtxoSelectionStrategy {

    private final UtxoService utxoService;
    @Setter
    private boolean ignoreUtxosWithDatumHash;

    public LargestFirstUtxoSelectionStrategy(UtxoService utxoService) {
        this(utxoService, true);
    }

    public LargestFirstUtxoSelectionStrategy(UtxoService utxoService, boolean ignoreUtxosWithDatumHash) {
        this.utxoService = utxoService;
        this.ignoreUtxosWithDatumHash = ignoreUtxosWithDatumHash;
    }

    @Override
    public Set<Utxo> select(String sender, List<Amount> outputAmounts, String datumHash, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        if(outputAmounts == null || outputAmounts.isEmpty()){
            return Collections.emptySet();
        }
        try{
            Set<Utxo> selectedUtxos = new HashSet<>();

            final Map<String, BigInteger> remaining = new HashMap<>(outputAmounts.stream()
                    .collect(Collectors.groupingBy(Amount::getUnit,
                            Collectors.reducing(BigInteger.ZERO,
                                    Amount::getQuantity,
                                    BigInteger::add))))
                    .entrySet().stream()
                    .filter(entry -> BigInteger.ZERO.compareTo(entry.getValue()) < 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            var fetchResult = fetchAllUtxos(sender, this.utxoService);

            var allUtxos = fetchResult.stream()
                                                 .sorted(sortLargestFirst(outputAmounts))
                                                 .collect(Collectors.toList());

            for(Utxo utxo : allUtxos) {
                if(!accept(utxo)){
                    continue;
                }
                if(utxosToExclude != null && utxosToExclude.contains(utxo)){
                    continue;
                }
                if(utxo.getDataHash() != null && !utxo.getDataHash().isEmpty() && ignoreUtxosWithDatumHash){
                    continue;
                }
                if(datumHash != null && !datumHash.isEmpty() && !datumHash.equals(utxo.getDataHash())){
                    continue;
                }
                if(selectedUtxos.contains(utxo)){
                    continue;
                }
                List<Amount> utxoAmounts = utxo.getAmount();

                boolean utxoSelected = false;
                for(Amount amount: utxoAmounts) {
                    var remainingAmount = remaining.get(amount.getUnit());
                    if(remainingAmount != null && BigInteger.ZERO.compareTo(remainingAmount) < 0){
                        utxoSelected = true;
                        var newRemaining = remainingAmount.subtract(amount.getQuantity());
                        if(BigInteger.ZERO.compareTo(newRemaining) < 0){
                            remaining.put(amount.getUnit(), newRemaining);
                        }else{
                            remaining.remove(amount.getUnit());
                        }
                    }
                }

                if(utxoSelected){
                    selectedUtxos.add(utxo);
                    if(!remaining.isEmpty() && selectedUtxos.size() > maxUtxoSelectionLimit){
                        throw new InputsLimitExceededException("Selection limit of " + maxUtxoSelectionLimit + " utxos reached with " + remaining + " remaining");
                    }
                }
            }
            if(!remaining.isEmpty()){
                throw new InsufficientBalanceException("Not enough funds for [" + remaining + "]");
            }
            return selectedUtxos;
        }catch(InputsLimitExceededException e){
            var fallback = fallback();
            if(fallback != null){
                return fallback.select(sender, outputAmounts, datumHash, utxosToExclude, maxUtxoSelectionLimit);
            }
            throw new ApiRuntimeException("Input limit exceeded and no fallback provided", e);
        }catch(ApiException e){
            throw new ApiRuntimeException("Unable to fetch UTXOs", e);
        }
    }

    static List<Utxo> fetchAllUtxos(String address, UtxoService utxoService) throws ApiException{
        final var nrOfItemsToFetch = 100;
        var pageToFetch = 0;
        var result = new ArrayList<Utxo>();
        // call fetch until result is empty or < nr of items
        while(true){
            var pageResult = utxoService.getUtxos(address, nrOfItemsToFetch, pageToFetch + 1, OrderEnum.asc);
            if(pageResult != null && pageResult.getValue() != null){
                result.addAll(pageResult.getValue());
            }
            if(pageResult == null || pageResult.getValue() == null || pageResult.getValue().isEmpty()){
                break;
            }
            pageToFetch += 1;
        }
        return result;
    }

    private static Comparator<Utxo> sortLargestFirst(List<Amount> outputAmounts){
        // first process utxos which contain most matching assets
        return (o1, o2) -> countTotalAssetsQuantity(o2.getAmount(), outputAmounts).compareTo(countTotalAssetsQuantity(o1.getAmount(), outputAmounts));
    }
    private static BigInteger countTotalAssetsQuantity(List<Amount> l1, List<Amount> outputAmounts){
        // for each asset in outputAmounts
            // add
        var outputAssets = outputAmounts.stream().map(Amount::getUnit).collect(Collectors.toSet());

        return l1.stream()
                .filter(it -> outputAssets.contains(it.getUnit()))
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    @Override
    public UtxoSelectionStrategy fallback() {
        return null;
    }

    protected boolean accept(Utxo utxo) {
        return true;
    }
}
