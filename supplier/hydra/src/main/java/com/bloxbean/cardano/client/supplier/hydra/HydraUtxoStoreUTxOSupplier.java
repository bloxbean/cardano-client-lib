package com.bloxbean.cardano.client.supplier.hydra;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.util.Tuple;
import org.cardanofoundation.hydra.core.model.UTXO;
import org.cardanofoundation.hydra.core.store.UTxOStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.supplier.hydra.HydraUtxoConverter.convert;
import static org.cardanofoundation.hydra.core.utils.StringUtils.split;

/**
 * Implementation of UtxoSupplier that sources utxos from the <code>UTxOStore</code>.
 */
public class HydraUtxoStoreUTxOSupplier implements UtxoSupplier {

    private final UTxOStore utxoStore;

    public HydraUtxoStoreUTxOSupplier(UTxOStore utxoStore) {
        this.utxoStore = utxoStore;
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        int noOfItemsWithFallback = nrOfItems == null ? DEFAULT_NR_OF_ITEMS_TO_FETCH : nrOfItems;
        // no paging support in this supplier
        if (page != 0) {
            return Collections.emptyList();
        }

        Map<String, UTXO> snapshot = utxoStore.getLatestUTxO();
        if (snapshot.isEmpty()) {
            return Collections.emptyList();
        }

        return snapshot.entrySet()
                .stream()
                .filter(utxoEntry -> utxoEntry.getValue().getAddress().equals(address))
                .map(utxoEntry -> new Tuple<>(split(utxoEntry.getKey(), "#"), utxoEntry.getValue()))
                .map(tuple -> {
                    String txId = tuple._1[0];
                    int outputIndex = Integer.parseInt(tuple._1[1]);
                    UTXO utxo = tuple._2;

                    return convert(txId, outputIndex, utxo);
                })
                .limit(noOfItemsWithFallback)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        String key = String.format("%s#%d", txHash, outputIndex);

        return Optional.ofNullable(utxoStore.getLatestUTxO().get(key))
                .map(utxo -> convert(txHash, outputIndex, utxo));
    }

}