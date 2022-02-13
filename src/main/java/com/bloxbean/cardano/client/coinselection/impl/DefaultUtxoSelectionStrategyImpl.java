package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Out-of-box implementation of {@link UtxoSelectionStrategy}
 * Applications can provide their own custom implementation
 */
public class DefaultUtxoSelectionStrategyImpl implements UtxoSelectionStrategy {

    private UtxoService utxoService;

    private boolean ignoreUtxosWithDatumHash;

    public DefaultUtxoSelectionStrategyImpl(UtxoService utxoService) {
        this.utxoService = utxoService;
        this.ignoreUtxosWithDatumHash = true;
    }

    @Override
    public List<Utxo> selectUtxos(String address, String unit, BigInteger amount, Set<Utxo> excludeUtxos) throws ApiException {
        return selectUtxos(address, unit, amount, null, excludeUtxos);
    }

    @Override
    public List<Utxo> selectUtxos(String address, String unit, BigInteger amount, String datumHash, Set<Utxo> excludeUtxos) throws ApiException {
        if(amount == null)
            amount = BigInteger.ZERO;

        BigInteger totalUtxoAmount = BigInteger.valueOf(0);
        List<Utxo> selectedUtxos = new ArrayList<>();
        boolean canContinue = true;
        int i = 1;

        while(canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(address, getUtxoFetchSize(),
                    i++, getUtxoFetchOrder());
            if(result.code() == 200) {
                List<Utxo> fetchData = result.getValue();

                List<Utxo> data = filter(fetchData);
                if(data == null || data.isEmpty()) {
                    canContinue = false; //Result code 200, but no utxo returned
                    throw new ApiException(String.format("Unable to get enough Utxos for address : %s, reason: %s", address, result.getResponse()));
                }

                for(Utxo utxo: data) {
                    if(excludeUtxos.contains(utxo))
                        continue;

                    if(utxo.getDataHash() != null && !utxo.getDataHash().isEmpty() && ignoreUtxosWithDatumHash())
                        continue;

                    if(datumHash != null && !datumHash.isEmpty() && !datumHash.equals(utxo.getDataHash()))
                        continue;

                    List<Amount> utxoAmounts = utxo.getAmount();

                    boolean unitFound = false;
                    for(Amount amt: utxoAmounts) {
                        if(unit.equals(amt.getUnit())) {
                            totalUtxoAmount = totalUtxoAmount.add(amt.getQuantity());
                            unitFound = true;
                        }
                    }

                    if(unitFound)
                        selectedUtxos.add(utxo);

                    if(totalUtxoAmount.compareTo(amount) == 1 || totalUtxoAmount.compareTo(amount) == 0) {
                        canContinue = false;
                        break;
                    }
                }
            } else {
                canContinue = false;
                throw new ApiException(String.format("Unable to get enough Utxos for address : %s, reason: %s", address, result.getResponse()));
            }
        }

        return selectedUtxos;
    }

    @Override
    public boolean ignoreUtxosWithDatumHash() {
        return ignoreUtxosWithDatumHash;
    }

    @Override
    public void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash) {
        this.ignoreUtxosWithDatumHash = ignoreUtxosWithDatumHash;
    }

    protected List<Utxo> filter(List<Utxo> fetchData) {
        return fetchData;
    }

    protected OrderEnum getUtxoFetchOrder() {
        return OrderEnum.asc;
    }

    protected int getUtxoFetchSize() {
        return 40;
    }
}
