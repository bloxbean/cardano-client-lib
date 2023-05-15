package com.bloxbean.cardano.client.supplier.local;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.yaci.core.protocol.localstate.queries.UtxoByAddressQuery;
import com.bloxbean.cardano.yaci.core.protocol.localstate.queries.UtxoByAddressQueryResult;
import com.bloxbean.cardano.yaci.helper.LocalStateQueryClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LocalUtxoSupplier implements UtxoSupplier {
    private LocalStateQueryClient localStateQueryClient;

    public LocalUtxoSupplier(LocalStateQueryClient localStateQueryClient) {
        this.localStateQueryClient = localStateQueryClient;
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        if (page != null)
            page = page + 1;
        else
            page = 1;

        if (page != 1)
            return Collections.EMPTY_LIST;

        UtxoByAddressQueryResult queryResult = (UtxoByAddressQueryResult) localStateQueryClient
                .executeQuery(new UtxoByAddressQuery(new Address(address))).block(Duration.ofSeconds(10));

        List<Utxo> utxos = queryResult.getUtxoList();
        //Replace . with empty string in unit as CCL doens't expect . in unit
        utxos.forEach(utxo -> {
            utxo.getAmount().forEach(amount -> {
                String unit = amount.getUnit();
                unit = unit.replace(".", "");
                amount.setUnit(unit);
            });
        });

        return utxos;
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        throw new UnsupportedOperationException("Not supported");
    }
}
