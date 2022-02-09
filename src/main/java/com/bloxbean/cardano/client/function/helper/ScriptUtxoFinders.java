package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides helper methods to get script utxos
 */
public class ScriptUtxoFinders {

    public static <T> Optional<Utxo> findFirstByDatum(UtxoService utxoService, String scriptAddress, T datum) throws ApiException {
        Objects.requireNonNull(datum);
        Objects.requireNonNull(utxoService);

        String datumHash = getDatumHash(datum);

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoService);

        Utxo utxo = utxoSelector.findFirst(scriptAddress, utx -> datumHash.equals(utx.getDataHash()));
        return Optional.ofNullable(utxo);
    }

    public static <T> List<Utxo> findAllByDatum(UtxoService utxoService, String scriptAddress, T datum) throws ApiException {
        Objects.requireNonNull(datum);
        Objects.requireNonNull(utxoService);

        String datumHash = getDatumHash(datum);

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoService);

        List<Utxo> utxos = utxoSelector.findAll(scriptAddress, utx -> datumHash.equals(utx.getDataHash()));
        return utxos;
    }

    private static <T> String getDatumHash(T datum) {
        PlutusData datumPlutusData;
        if (datum instanceof PlutusData)
            datumPlutusData = (PlutusData) datum;
        else
            datumPlutusData = Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum);

        String datumHash;
        try {
            datumHash = datumPlutusData.getDatumHash();
        } catch (CborSerializationException | CborException e) {
            throw new CborRuntimeException(e);
        }
        return datumHash;
    }
}
