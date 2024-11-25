package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WalletTest {

    String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";

    String baseAddress0 = "addr1qxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps7zwsra";
    String baseAddress1 = "addr1q93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4zthxn";
    String baseAddress2 = "addr1q8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4qp6cs";
    String baseAddress3 = "addr1qxa5pll82u8lqtzqjqhdr828medvfvezv4509nzyuhwt5aql5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psy8jsmy";

    String testnetBaseAddress0 = "addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z";
    String testnetBaseAddress1 = "addr_test1qp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psk5kh2v";
    String testnetBaseAddress2 = "addr_test1qrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8pskku650";

    String entAddress0 = "addr1vxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvstf7k4n";
    String entAddress1 = "addr1v93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg7davae";
    String entAddress2 = "addr1v8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgvddj89";

    String testnetEntAddress0 = "addr_test1vzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvssp226k";
    String testnetEntAddress1 = "addr_test1vp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg99fsju";
    String testnetEntAddress2 = "addr_test1vrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgh9ewgq";

    @Test
    void generateMnemonic24w() {
        Wallet hdWallet = new Wallet(Networks.testnet());
        String mnemonic = hdWallet.getMnemonic();
        assertEquals(24, mnemonic.split(" ").length);
    }

    @Test
    void generateMnemonic15w() {
        Wallet hdWallet = new Wallet(Networks.testnet(), Words.FIFTEEN);
        String mnemonic = hdWallet.getMnemonic();
        assertEquals(15, mnemonic.split(" ").length);
    }

    @Test
    void WalletAddressToAccountAddressTest() {
        Wallet hdWallet = new Wallet(Networks.testnet());
        Address address = hdWallet.getBaseAddress(0);
        Account a = new Account(hdWallet.getNetwork(), hdWallet.getMnemonic(), 0);
        assertEquals(address.getAddress(), a.getBaseAddress().getAddress());
    }

    @Test
    void testGetBaseAddressFromMnemonicIndex_0() {
        Wallet wallet = new Wallet(Networks.mainnet(), phrase24W);
        Assertions.assertEquals(baseAddress0, wallet.getBaseAddressString(0));
        Assertions.assertEquals(baseAddress1, wallet.getBaseAddressString(1));
        Assertions.assertEquals(baseAddress2, wallet.getBaseAddressString(2));
        Assertions.assertEquals(baseAddress3, wallet.getBaseAddressString(3));
    }

    @Test
    void testGetBaseAddressFromMnemonicByNetworkInfoTestnet() {
        Wallet wallet = new Wallet(Networks.testnet(), phrase24W);
        Assertions.assertEquals(testnetBaseAddress0, wallet.getBaseAddressString(0));
        Assertions.assertEquals(testnetBaseAddress1, wallet.getBaseAddressString(1));
        Assertions.assertEquals(testnetBaseAddress2, wallet.getBaseAddressString(2));
    }

    @Test
    void testGetEnterpriseAddressFromMnemonicIndex() {
        Wallet wallet = new Wallet(Networks.mainnet(), phrase24W);
        Assertions.assertEquals(entAddress0, wallet.getEntAddress(0).getAddress());
        Assertions.assertEquals(entAddress1, wallet.getEntAddress(1).getAddress());
        Assertions.assertEquals(entAddress2, wallet.getEntAddress(2).getAddress());
    }

    @Test
    void testGetEnterpriseAddressFromMnemonicIndexByNetwork() {
        Wallet wallet = new Wallet(Networks.testnet(), phrase24W);
        Assertions.assertEquals(testnetEntAddress0, wallet.getEntAddress(0).getAddress());
        Assertions.assertEquals(testnetEntAddress1, wallet.getEntAddress(1).getAddress());
        Assertions.assertEquals(testnetEntAddress2, wallet.getEntAddress(2).getAddress());
    }

    @Test
    void testGetPublicKeyBytesFromMnemonic() {
        byte[] pubKey = new Wallet(phrase24W).getRootKeyPair().getPublicKey().getKeyData();
        Assertions.assertEquals(32, pubKey.length);
    }

    @Test
    void testGetPrivateKeyBytesFromMnemonic() {
        byte[] pvtKey = new Wallet(phrase24W).getRootKeyPair().getPrivateKey().getBytes();
        Assertions.assertEquals(96, pvtKey.length);
    }


}
