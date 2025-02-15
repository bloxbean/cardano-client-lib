package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.WalletUtxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.exception.InputsLimitExceededException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Setter;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Out-of-box implementation of {@link UtxoSelectionStrategy}
 * Applications can provide their own custom implementation
 */
public class DefaultUtxoSelectionStrategyImpl implements UtxoSelectionStrategy {

    private final UtxoSupplier utxoSupplier;
    @Setter
    private boolean ignoreUtxosWithDatumHash;

    public DefaultUtxoSelectionStrategyImpl(UtxoSupplier utxoSupplier) {
        this(utxoSupplier, true);
    }

    public DefaultUtxoSelectionStrategyImpl(UtxoSupplier utxoSupplier, boolean ignoreUtxosWithDatumHash) {
        this.utxoSupplier = utxoSupplier;
        this.ignoreUtxosWithDatumHash = ignoreUtxosWithDatumHash;
    }

    @Override
    public Set<Utxo> select(AddressIterator addrIter, List<Amount> outputAmounts, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        if(outputAmounts == null || outputAmounts.isEmpty()){
            return Collections.emptySet();
        }

        //reset addrIter to handle reuse of same iterator from caller
        if (addrIter != null) addrIter.reset();

        //TODO -- Should we throw error if both datumHash and inlineDatum are set ??

        try{
            // loop over utxo's, find matching requested amount
            Set<Utxo> selectedUtxos = new HashSet<>();

            final Map<String, BigInteger> remaining = new HashMap<>(outputAmounts.stream()
                    .collect(Collectors.groupingBy(Amount::getUnit,
                            Collectors.reducing(BigInteger.ZERO,
                                    Amount::getQuantity,
                                    BigInteger::add))))
                    .entrySet().stream()
                    .filter(entry -> BigInteger.ZERO.compareTo(entry.getValue()) < 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


            final int nrOfItems = 100;

            String firstAddr = null;
            while(addrIter.hasNext()) {
                int page = 0;
                Address senderAddr = addrIter.next();
                String sender = senderAddr.toBech32();

                if (firstAddr == null)
                    firstAddr = sender; //TODO -- Just a workaround for log

                while (!remaining.isEmpty()) {
                    var fetchResult = utxoSupplier.getPage(sender, nrOfItems, page, OrderEnum.asc);

                    var fetched = fetchResult != null
                            ? fetchResult.stream()
                            .sorted(sortByMostMatchingAssets(outputAmounts))
                            .collect(Collectors.toList())
                            : Collections.<Utxo>emptyList();

                    page += 1;
                    for (Utxo utxo : fetched) {
                        if (!accept(utxo)) {
                            continue;
                        }
                        if (utxosToExclude != null && utxosToExclude.contains(utxo)) {
                            continue;
                        }
                        if (utxo.getDataHash() != null && !utxo.getDataHash().isEmpty() && ignoreUtxosWithDatumHash) {
                            continue;
                        }
                        if (datumHash != null && !datumHash.isEmpty() && !datumHash.equals(utxo.getDataHash())) {
                            continue;
                        }
                        //TODO - add tests for this scenario
                        if (inlineDatum != null && !inlineDatum.serializeToHex().equals(utxo.getInlineDatum())) {
                            continue;
                        }
                        if (selectedUtxos.contains(utxo)) {
                            continue;
                        }
                        List<Amount> utxoAmounts = utxo.getAmount();

                        boolean utxoSelected = false;
                        for (Amount amount : utxoAmounts) {
                            var remainingAmount = remaining.get(amount.getUnit());
                            if (remainingAmount != null && BigInteger.ZERO.compareTo(remainingAmount) < 0) {
                                utxoSelected = true;
                                var newRemaining = remainingAmount.subtract(amount.getQuantity());
                                if (BigInteger.ZERO.compareTo(newRemaining) < 0) {
                                    remaining.put(amount.getUnit(), newRemaining);
                                } else {
                                    remaining.remove(amount.getUnit());
                                }
                            }
                        }

                        if (utxoSelected) {
                            var walletUtxo = WalletUtxo.from(utxo);
                            walletUtxo.setDerivationPath(senderAddr.getDerivationPath().isPresent()? senderAddr.getDerivationPath().get(): null);

                            selectedUtxos.add(walletUtxo);
                            if (!remaining.isEmpty() && selectedUtxos.size() > maxUtxoSelectionLimit) {
                                throw new InputsLimitExceededException("Selection limit of " + maxUtxoSelectionLimit + " utxos reached with " + remaining + " remaining");
                            }
                        }
                    }
                    if (fetched.isEmpty()) {
                        break;
                    }
                }
            }
            if(!remaining.isEmpty()){
                throw new InsufficientBalanceException("Not enough funds for [" + remaining + "], address: " + firstAddr);
            }
            return selectedUtxos;
        }catch(InputsLimitExceededException e){
            var fallback = fallback();
            if(fallback != null){
                return fallback.select(addrIter, outputAmounts, datumHash, inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
            }
            throw new ApiRuntimeException("Input limit exceeded and no fallback provided", e);
        }
    }

    private static Comparator<Utxo> sortByMostMatchingAssets(List<Amount> outputAmounts){
        // first process utxos which contain most matching assets
        return (o1, o2) -> Integer.compare(countMatchingAssets(o2.getAmount(), outputAmounts), countMatchingAssets(o1.getAmount(), outputAmounts));
    }
    private static int countMatchingAssets(List<Amount> l1, List<Amount> outputAmounts){
        if(l1 == null || l1.isEmpty() || outputAmounts == null || outputAmounts.isEmpty()){
            return 0;
        }
        return (int) l1.stream()
                .filter(it1 -> outputAmounts.stream().filter(outputAmount -> it1.getUnit() != null && it1.getUnit().equals(outputAmount.getUnit())).findFirst().isPresent())
                .map(it -> 1)
                .count();
    }

    @Override
    public UtxoSelectionStrategy fallback() {
        return new LargestFirstUtxoSelectionStrategy(this.utxoSupplier, this.ignoreUtxosWithDatumHash);
    }

    protected boolean accept(Utxo utxo) {
        return true;
    }
}
