package com.bloxbean.cardano.client.plutus.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Utxo;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public interface ScriptUtxoSelection {
    /**
     * Find the first utxo matching the predicate
     * @param scriptAddress Script address
     * @param predicate Predicate to filter utxos
     * @return Utxo
     * @throws ApiException
     */
    Utxo findFirst(String scriptAddress, Predicate<Utxo> predicate) throws ApiException;

    /**
     * Find the first utxo matching the predicate
     * @param scriptAddress Script address
     * @param predicate Predicate to filter utxos
     * @param excludeUtxos Utxos to exclude
     * @return Utxo
     * @throws ApiException
     */
    Utxo findFirst(String scriptAddress, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException;

    /**
     * Find all utxos matching the predicate
     * @param scriptAddress Script address
     * @param predicate Predicate to filter utxos
     * @return List of Utxos
     * @throws ApiException
     */
    List<Utxo> findAll(String scriptAddress, Predicate<Utxo> predicate) throws ApiException;

    /**
     * Find all utxos matching the predicate
     * @param scriptAddress ScriptAddress Script address
     * @param predicate Predicate Predicate to filter utxos
     * @param excludeUtxos Utxos to exclude
     * @return List of Utxos
     * @throws ApiException
     */
    List<Utxo> findAll(String scriptAddress, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException;
}
