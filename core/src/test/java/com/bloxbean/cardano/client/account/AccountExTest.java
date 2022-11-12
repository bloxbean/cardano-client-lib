package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

//TODO -- These tests are from JNA era. Check if these tests are still required. If not, remove.
public class AccountExTest {

    @BeforeAll
    static void setup() {
    }

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
    String testnetEntAddress2 = "addr_test1vrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgh9ewg";

    String testByronAddress0 = "DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK";
    private String testByronAddress1 = "Ae2tdPwUPEZ3MHKkpT5Bpj549vrRH7nBqYjNXnCV8G2Bc2YxNcGHEa8ykDp";

    @Test
    public void testGetBaseAddressFromMnemonicIndex_0() {
        String baseAddress = new Account(Networks.mainnet(), phrase24W, 0).baseAddress();
        System.out.println(baseAddress);
        Assertions.assertEquals(baseAddress0, baseAddress);
    }

    @Test
    public void testGetBaseAddressFromMnemonicIndex_1() {
        String baseAddress = new Account(Networks.mainnet(), phrase24W, 1).baseAddress();
        System.out.println(baseAddress);
        Assertions.assertEquals(baseAddress1, baseAddress);
    }

    @Test
    public void testGetBaseAddressFromMnemonicIndex_2() {
        String baseAddress = new Account(Networks.mainnet(), phrase24W, 2).baseAddress();
        System.out.println(baseAddress);
        Assertions.assertEquals(baseAddress2, baseAddress);
    }

    @Test
    public void generateMnemonic24w() {
        String mnemonic = new Account().mnemonic();
        System.out.println(mnemonic);
        String[] tokens = mnemonic.split("\\s");
        assertThat(tokens).hasSize(24);
    }

    @Test
    public void generateMnemonic15w() {
        Account account = new Account(Networks.mainnet(), DerivationPath.createExternalAddressDerivationPath(), Words.FIFTEEN);
        String mnemonic = account.mnemonic();
        System.out.println(mnemonic);
        String[] tokens = mnemonic.split("\\s");
        assertThat(tokens).hasSize(15);
    }

    @Test
    public void testGetBaseAddressFromMnemonicByNetworkInfoMainnet() {
        String baseAddress = new Account(Networks.mainnet(), phrase24W, 2).baseAddress();
        Assertions.assertEquals(baseAddress2, baseAddress);
    }

    @Test
    public void testGetBaseAddressFromMnemonicByNetworkInfoTestnet() {
        String baseAddress = new Account(Networks.testnet(), phrase24W, 1).baseAddress();
        Assertions.assertEquals(testnetBaseAddress1, baseAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndex_0() {
        String entAddress = new Account(phrase24W, 0).enterpriseAddress();
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress0, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndex_1() {
        String entAddress = new Account(phrase24W, 1).enterpriseAddress();
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress1, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndex_2() {
        String entAddress = new Account(Networks.mainnet(), phrase24W, 2).enterpriseAddress();
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress2, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndexByNetwork_0() {
        String entAddress = new Account(Networks.mainnet(), phrase24W, 0).enterpriseAddress();
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress0, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndexByNetwork_1() {
        String entAddress = new Account(Networks.mainnet(), phrase24W, 1).enterpriseAddress();
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress1, entAddress);
    }

    @Test
    public void testBech32AddressToBytes() throws AddressExcepion {
        String baseAddress = "addr_test1qpu5vlrf4xkxv2qpwngf6cjhtw542ayty80v8dyr49rf5ewvxwdrt70qlcpeeagscasafhffqsxy36t90ldv06wqrk2qum8x5w";
        byte[] bytes = AddressUtil.addressToBytes(baseAddress);
        Assertions.assertNotEquals(0, bytes);
    }

    @Test
    public void testHexBytesAddressToBech32() throws AddressExcepion {
        String baseAddress = "addr_test1qpu5vlrf4xkxv2qpwngf6cjhtw542ayty80v8dyr49rf5ewvxwdrt70qlcpeeagscasafhffqsxy36t90ldv06wqrk2qum8x5w";
        byte[] bytes = AddressUtil.addressToBytes(baseAddress);

        String finalBech32Address = AddressUtil.bytesToAddress(bytes);

        Assertions.assertEquals(baseAddress, finalBech32Address);
    }

    @Test
    public void testGetPrivateKeyFromMnemonic() {
        String pvtKey = new Account(phrase24W, 0).getBech32PrivateKey();
        System.out.println(pvtKey);
        Assertions.assertTrue(pvtKey.length() > 5);
    }

    @Test
    public void testGetPrivateKeyBytesFromMnemonic() {
        byte[] pvtKey = new Account(phrase24W, 0).hdKeyPair().getPrivateKey().getBytes();
        Assertions.assertEquals(96, pvtKey.length);
    }

    @Test
    public void testGetPrivateKeyFromInvalidMnemonic() {
        Assertions.assertThrows(AddressRuntimeException.class, () -> {
            new Account(phrase24W.substring(3), 0).privateKeyBytes();
        });
    }

    @Test
    public void testGetPublicKeyBytesFromMnemonic() {
        byte[] pubKey = new Account(phrase24W, 0).publicKeyBytes();
        Assertions.assertEquals(32, pubKey.length);
    }

    @Test
    public void testGetPublicKeyBytesFromInvalidMnemonic() {
        Assertions.assertThrows(AddressRuntimeException.class, () -> {
            new Account(phrase24W.substring(3), 0).publicKeyBytes();
        });
    }

    @Test
    public void testBase58AddressToBytes() throws AddressExcepion {
        byte[] bytes = AddressUtil.addressToBytes(testByronAddress0);
        Assertions.assertNotEquals(0, bytes);
    }

    @Test
    public void testBase58AddressToBytes1() throws AddressExcepion {
        byte[] bytes = AddressUtil.addressToBytes(testByronAddress1);
        Assertions.assertNotEquals(0, bytes);
    }

    @Test
    public void testHexBytesToBase58Address() throws AddressExcepion {
        byte[] bytes = AddressUtil.addressToBytes(testByronAddress0);

        String byronAddress = AddressUtil.bytesToBase58Address(bytes);
        Assertions.assertEquals(testByronAddress0, byronAddress);
    }

    @Test
    public void testHexBytesToBase58Address1() throws AddressExcepion {
        byte[] bytes = AddressUtil.addressToBytes(testByronAddress1);

        String byronAddress = AddressUtil.bytesToBase58Address(bytes);
        Assertions.assertEquals(testByronAddress1, byronAddress);
    }
}
