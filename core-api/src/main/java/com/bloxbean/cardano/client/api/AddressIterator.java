package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.address.Address;

import java.util.Iterator;

/**
 * An iterator that provides a list of addresses. Used during UTXO selection in the
 * UtxoSelectionStrategy implementation.
 */
public interface AddressIterator extends Iterator<Address> {

    /**
     * Retrieves the first address in the iterator's list of addresses without moving the cursor.
     *
     * @return the first {@link Address} in the list, or null if the list is empty.
     */
    Address getFirst();

    /**
     * Reset the pointer to the beginning
     */
    void reset();

    /**
     * Creates and returns a copy of this AddressIterator.
     * The cloned instance is independent of the original and can be used separately.
     *
     * @return a new AddressIterator instance that is a copy of the current iterator.
     */
    AddressIterator clone();

}
