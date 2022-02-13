package com.bloxbean.cardano.client.coinselection;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Utxo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Implement this interface to find <code>Utxo</code> by predicate
 */
public interface UtxoSelector {

    /**
     * Find the first utxo matching the predicate
     * @param address Script address
     * @param predicate Predicate to filter utxos
     * @return An optional with matching <code>Utxo</code>
     * @throws ApiException if error
     */
    Optional<Utxo> findFirst(String address, Predicate<Utxo> predicate) throws ApiException;

    /**
     * Find the first utxo matching the predicate
     * @param address Script address
     * @param predicate Predicate to filter utxos
     * @param excludeUtxos Utxos to exclude
     * @return An optional with matching <code>Utxo</code>
     * @throws ApiException if error
     */
    Optional<Utxo> findFirst(String address, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException;

    /**
     * Find all utxos matching the predicate
     * @param address Script address
     * @param predicate Predicate to filter utxos
     * @return List of Utxos
     * @throws ApiException if error
     */
    List<Utxo> findAll(String address, Predicate<Utxo> predicate) throws ApiException;

    /**
     * Find all utxos matching the predicate
     * @param address ScriptAddress Script address
     * @param predicate Predicate Predicate to filter utxos
     * @param excludeUtxos Utxos to exclude
     * @return List of Utxos
     * @throws ApiException if error
     */
    List<Utxo> findAll(String address, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException;
}
