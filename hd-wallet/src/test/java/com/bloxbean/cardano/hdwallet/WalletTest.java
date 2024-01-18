package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.model.Networks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WalletTest {

    @Test
    void generateMnemonicTest() {
        Wallet hdWallet = new Wallet(Networks.testnet(), null);
        String mnemonic = hdWallet.getMnemonic();
        assertEquals(24, mnemonic.split(" ").length);
    }

    @Test
    void getAccountFromIndex() {
        Wallet hdWallet = new Wallet(Networks.testnet(), null);
        Address address = hdWallet.getBaseAddress(0);
        Account a = new Account(hdWallet.getNetwork(), hdWallet.getMnemonic(), 0);
        assertEquals(address.getAddress(), a.getBaseAddress().getAddress());
    }
}
