package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.address.Address;

import java.util.Iterator;

/**
 * An iterator that provides a list of addresses. Used during UTXO selection in the
 * UtxoSelectionStrategy implementation.
 */
public interface AddressIterator extends Iterator<Address> {
    /**
     * Reset the pointer to the beginning
     */
    void reset();

}
