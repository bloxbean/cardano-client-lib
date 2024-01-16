package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.crypto.cip1852.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HDWalletTest {

    @Test
    void generateMnemonicTest() {
        HDWallet hdWallet = new HDWallet();
        String mnemonic = hdWallet.getMnemonic();
        assertEquals(24, mnemonic.split(" ").length);
    }

    @Test
    void getAccountFromIndex() {
        HDWallet hdWallet = new HDWallet();
        Address address = hdWallet.getBaseAddress(0);
        Account a = new Account(hdWallet.getNetwork(), 0);
        assertEquals(address.getAddress(), a.getBaseAddress().getAddress());
    }

    @Test
    void utxoTest() {
        BackendService backendService = new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "preprodEwHzH2AgK20tmjrQnIN0P6zh65mUjSvR");
        String sender3Mnemonic = "clog book honey force cricket stamp until seed minimum margin denial kind volume undo simple federal then jealous solid legal crucial crazy acoustic thank";
        HDWallet hdWallet = new HDWallet(Networks.preprod(), sender3Mnemonic);
        List<Utxo> utxos = hdWallet.getUtxos(backendService);
        System.out.println("aasd");
    }
}
