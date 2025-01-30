package com.bloxbean.cardano.hdwallet.util;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

@Slf4j
public class HDWalletAddressIterator implements AddressIterator {
    private Wallet wallet;
    private UtxoSupplier utxoSupplier;
    private int index = 0;
    private int gapCount = 0;
    private Iterator<Integer> indexesToScan;

    public HDWalletAddressIterator(Wallet wallet, UtxoSupplier utxoSupplier) {
        this.wallet = wallet;
        this.utxoSupplier = utxoSupplier;

        //If only specific indexes to scan
        this.indexesToScan = wallet.getIndexesToScan() != null && wallet.getIndexesToScan().length > 0 ?
                Arrays.stream(wallet.getIndexesToScan()).iterator() : null;
    }

    @Override
    public boolean hasNext() {
        if (indexesToScan != null) {
            return indexesToScan.hasNext();
        } else {
            if (gapCount >= wallet.getGapLimit())
                return false;
            else
                return true;
        }
    }

    @Override
    public Address next() {
        if (!hasNext())
            throw new NoSuchElementException();

        Address address;
        if (indexesToScan != null) {
            address = wallet.getBaseAddress(indexesToScan.next());
        } else {
            address = wallet.getBaseAddress(index);

            if (log.isTraceEnabled())
                log.trace("Scanning derivation path: " + address.getDerivationPath().get());

            if (utxoSupplier.isUsedAddress(address)) {
                gapCount = 0; //reset gap-count
            } else {
                gapCount++;
            }
            index++;
        }
        return address;
    }

    @Override
    public String toString() {
        //Print address at first index
        if (wallet != null) {
            return wallet.getBaseAddress(0).toBech32();
        } else {
            return super.toString();
        }
    }

    @Override
    public void reset() {
        index = 0;
        gapCount = 0;

        this.indexesToScan = wallet.getIndexesToScan() != null && wallet.getIndexesToScan().length > 0 ?
                Arrays.stream(wallet.getIndexesToScan()).iterator() : null;
    }
}
