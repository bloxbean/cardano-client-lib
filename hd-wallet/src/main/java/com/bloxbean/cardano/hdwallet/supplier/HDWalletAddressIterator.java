package com.bloxbean.cardano.hdwallet.supplier;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.hdwallet.Wallet;

import java.util.NoSuchElementException;

public class HDWalletAddressIterator implements AddressIterator {
    private Wallet wallet;
    private UtxoSupplier utxoSupplier;
    private int index = 0;
    private int gapCount = 0;

    public HDWalletAddressIterator(Wallet wallet, UtxoSupplier utxoSupplier) {
        this.wallet = wallet;
        this.utxoSupplier = utxoSupplier;
    }

    @Override
    public boolean hasNext() {
        if (gapCount >= wallet.getGapLimit())
            return false;
        else
            return true;
    }

    @Override
    public Address next() {
        if (!hasNext())
            throw new NoSuchElementException();

        Address address = wallet.getBaseAddress(index);
        System.out.println("Derivation Path: " + address.getDerivationPath().get());

        if (utxoSupplier.isUsedAddress(address)) {
            gapCount = 0; //reset gap-count
            index++;
            return address;
        } else {
            gapCount++;
            index++;
            return address;
        }
    }

    private Address getBaseAddress(int account, int index) {
        return wallet.getBaseAddress(account, index);
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
}
