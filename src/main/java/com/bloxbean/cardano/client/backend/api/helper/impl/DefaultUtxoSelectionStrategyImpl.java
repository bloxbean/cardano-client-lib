package com.bloxbean.cardano.client.backend.api.helper.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.api.helper.UtxoSelectionStrategy;
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

    public DefaultUtxoSelectionStrategyImpl(UtxoService utxoService) {
        this.utxoService = utxoService;
    }

    @Override
    public List<Utxo> selectUtxos(String address, String unit, BigInteger amount, Set<Utxo> excludeUtxos) throws ApiException {
        if(amount == null)
            amount = BigInteger.ZERO;

        BigInteger totalUtxoAmount = BigInteger.valueOf(0);
        List<Utxo> selectedUtxos = new ArrayList<>();
        boolean canContinue = true;
        int i = 0;

        while(canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(address, getUtxoFetchSize(),
                    i++, getUtxoFetchOrder());
            if(result.code() == 200) {
                List<Utxo> fetchData = result.getValue();

                List<Utxo> data = filter(fetchData);
                if(data == null || data.isEmpty())
                    canContinue = false;

                for(Utxo utxo: data) {
                    if(excludeUtxos.contains(utxo))
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

                    if(totalUtxoAmount.compareTo(amount) == 1) {
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
