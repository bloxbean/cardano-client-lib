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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides helper methods to get script utxos
 */
public class ScriptUtxoFinders {

    /**
     * Find first matching utxo by datum at a script address
     *
     * @param utxoService   <code>{@link UtxoService}</code> instance
     * @param scriptAddress Script address
     * @param datum         Datum object
     * @return An optional with matching <code>Utxo</code>
     * @throws ApiException if error
     */
    public static Optional<Utxo> findFirstByDatum(UtxoService utxoService, String scriptAddress, Object datum) throws ApiException {
        Objects.requireNonNull(datum);
        Objects.requireNonNull(utxoService);

        String datumHash = getDatumHash(datum);

        return findFirstByDatumHash(utxoService, scriptAddress, datumHash);
    }

    /**
     * Find first matching utxo by datum hash at a script address
     *
     * @param utxoService   <code>{@link UtxoService}</code> instance
     * @param scriptAddress Script address
     * @param datumHash     Datum hash
     * @return An optional with matching <code>Utxo</code>
     * @throws ApiException if error
     */
    public static Optional<Utxo> findFirstByDatumHash(UtxoService utxoService, String scriptAddress, String datumHash) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoService);

        return utxoSelector.findFirst(scriptAddress, utx -> datumHash.equals(utx.getDataHash()));
    }

    /**
     * Find all matching utxos by datum at a script address
     *
     * @param utxoService   <code>{@link UtxoService}</code> instance
     * @param scriptAddress Script address
     * @param datum         Datum object
     * @return List of <code>Utxo</code>
     * @throws ApiException if error
     */
    public static List<Utxo> findAllByDatum(UtxoService utxoService, String scriptAddress, Object datum) throws ApiException {
        Objects.requireNonNull(datum);
        Objects.requireNonNull(utxoService);

        String datumHash = getDatumHash(datum);

        return findAllByDatumHash(utxoService, scriptAddress, datumHash);
    }

    /**
     * Find all matching utxos by datum hash at a script address
     *
     * @param utxoService   <code>{@link UtxoService}</code> instance
     * @param scriptAddress Script address
     * @param datumHash     datum hash
     * @return List of <code>Utxo</code>
     * @throws ApiException if error
     */
    public static List<Utxo> findAllByDatumHash(UtxoService utxoService, String scriptAddress, String datumHash) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoService);

        List<Utxo> utxos = utxoSelector.findAll(scriptAddress, utx -> datumHash.equals(utx.getDataHash()));
        return utxos;
    }


    private static <T> String getDatumHash(T datum) {
        try {
            return Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum).getDatumHash();
        } catch (CborSerializationException | CborException e) {
            throw new CborRuntimeException(e);
        }
    }
}
