package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.config.Configuration;
import lombok.NonNull;

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
     * @param utxoSupplier   <code>{@link UtxoSupplier}</code> instance
     * @param scriptAddress Script address
     * @param datum         Datum object
     * @return An optional with matching <code>Utxo</code>
     * @throws ApiException if error
     */
    public static Optional<Utxo> findFirstByDatumHashUsingDatum(@NonNull UtxoSupplier utxoSupplier, @NonNull String scriptAddress,
                                                                @NonNull Object datum) throws ApiException {
        Objects.requireNonNull(datum);
        Objects.requireNonNull(utxoSupplier);

        String datumHash = getDatumHash(datum);

        return findFirstByDatumHash(utxoSupplier, scriptAddress, datumHash);
    }

    /**
     * Find first matching utxo by datum hash at a script address
     *
     * @param utxoSupplier   <code>{@link UtxoSupplier}</code> instance
     * @param scriptAddress Script address
     * @param datumHash     Datum hash
     * @return An optional with matching <code>Utxo</code>
     * @throws ApiException if error
     */
    public static Optional<Utxo> findFirstByDatumHash(@NonNull UtxoSupplier utxoSupplier, @NonNull String scriptAddress,
                                                      @NonNull String datumHash) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        return utxoSelector.findFirst(scriptAddress, utx -> datumHash.equals(utx.getDataHash()));
    }

    /**
     * Find all matching utxos by datum at a script address
     *
     * @param utxoSupplier   <code>{@link UtxoSupplier}</code> instance
     * @param scriptAddress Script address
     * @param datum         Datum object
     * @return List of <code>Utxo</code>
     * @throws ApiException if error
     */
    public static List<Utxo> findAllByDatumHashUsingDatum(@NonNull UtxoSupplier utxoSupplier, @NonNull String scriptAddress,
                                                          @NonNull Object datum) throws ApiException {
        Objects.requireNonNull(datum);
        Objects.requireNonNull(utxoSupplier);

        String datumHash = getDatumHash(datum);

        return findAllByDatumHash(utxoSupplier, scriptAddress, datumHash);
    }

    /**
     * Find all matching utxos by datum hash at a script address
     *
     * @param utxoSupplier   <code>{@link UtxoSupplier}</code> instance
     * @param scriptAddress Script address
     * @param datumHash     datum hash
     * @return List of <code>Utxo</code>
     * @throws ApiException if error
     */
    public static List<Utxo> findAllByDatumHash(@NonNull UtxoSupplier utxoSupplier, @NonNull String scriptAddress,
                                                @NonNull String datumHash) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        List<Utxo> utxos = utxoSelector.findAll(scriptAddress, utx -> datumHash.equals(utx.getDataHash()));
        return utxos;
    }

    /**
     * Find first matching utxo by inline datum at a script address
     *
     * @param utxoSupplier   <code>{@link UtxoSupplier}</code> instance
     * @param scriptAddress Script address
     * @param inlineDatum   Datum object
     * @return An optional with matching <code>Utxo</code>
     * @throws ApiException if error
     */
    public static Optional<Utxo> findFirstByInlineDatum(@NonNull UtxoSupplier utxoSupplier, @NonNull String scriptAddress,
                                                        @NonNull Object inlineDatum) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        String datumCborHex = serializeDatumToHex(inlineDatum);

        return utxoSelector.findFirst(scriptAddress, utx -> datumCborHex.equals(utx.getInlineDatum()));
    }

    /**
     * Find all matching utxos by inline datum at a script address
     *
     * @param utxoSupplier   <code>{@link UtxoSupplier}</code> instance
     * @param scriptAddress Script address
     * @param inlineDatum     datum hash
     * @return List of <code>Utxo</code>
     * @throws ApiException if error
     */
    public static List<Utxo> findAllByInlineDatum(@NonNull UtxoSupplier utxoSupplier, @NonNull String scriptAddress,
                                                  @NonNull Object inlineDatum) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        String datumCborHex = serializeDatumToHex(inlineDatum);

        List<Utxo> utxos = utxoSelector.findAll(scriptAddress, utx -> datumCborHex.equals(utx.getInlineDatum()));
        return utxos;
    }

    private static <T> String getDatumHash(T datum) {
        return Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum).getDatumHash();
    }

    private static <T> String serializeDatumToHex(T datum) {
        return Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum).serializeToHex();
    }
}
