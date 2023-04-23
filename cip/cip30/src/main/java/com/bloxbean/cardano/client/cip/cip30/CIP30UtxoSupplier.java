package com.bloxbean.cardano.client.cip.cip30;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * CIP30 getUtxos() implementation to receive Hex-Encoded Cbor Representation of UTxOs and provide it to Transaction Build.
 */
public class CIP30UtxoSupplier implements UtxoSupplier {

    private final List<Utxo> utxos = new ArrayList<>();

    /**
     * CIP30UtxoSupplier Constructor
     *
     * @param hexEncodedCborUtxos Hex-Encoded Cbor Representation of UTxOs from Wallet getUtxos() Api
     * @throws CborDeserializationException upon Failure in Utxo Deserialization.
     */
    public CIP30UtxoSupplier(List<String> hexEncodedCborUtxos) throws CborDeserializationException {
        for (String cbor : hexEncodedCborUtxos) {
            utxos.add(CIP30UtxoDeserializer.deserialize(HexUtil.decodeHexString(cbor)));
        }
    }

    @Override
    public List<Utxo> getPage(String address, Integer pageSize, Integer page, OrderEnum order) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Invalid page size: " + pageSize);
        }
        if (page < 0) {
            throw new IllegalArgumentException("Invalid page number: " + page);
        }
        int fromIndex = (page) * pageSize;
        if (utxos.size() <= fromIndex) {
            return Collections.emptyList();
        }
        return utxos.subList(fromIndex, Math.min(fromIndex + pageSize, utxos.size()));
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        return utxos.stream()
                .filter(utxo -> utxo.getTxHash().equals(txHash) && utxo.getOutputIndex() == outputIndex).findFirst();
    }

    @Override
    public List<Utxo> getAll(String paymentAddress) {
        return utxos;
    }
}
