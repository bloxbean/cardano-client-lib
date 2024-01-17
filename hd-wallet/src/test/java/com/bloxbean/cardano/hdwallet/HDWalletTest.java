package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HDWalletTest {

    @Test
    void generateMnemonicTest() {
        HDWallet hdWallet = new HDWallet(null);
        String mnemonic = hdWallet.getMnemonic();
        assertEquals(24, mnemonic.split(" ").length);
    }

    @Test
    void getAccountFromIndex() {
        HDWallet hdWallet = new HDWallet(null);
        Address address = hdWallet.getBaseAddress(0);
        Account a = new Account(hdWallet.getNetwork(), hdWallet.getMnemonic(), 0);
        assertEquals(address.getAddress(), a.getBaseAddress().getAddress());
    }
}
