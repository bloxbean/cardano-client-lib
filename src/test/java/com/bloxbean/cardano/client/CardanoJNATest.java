package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.jna.CardanoJNA;
import com.bloxbean.cardano.client.util.Networks;
import com.bloxbean.cardano.client.util.Platform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

public class CardanoJNATest {

    static {
        String folderPrefix = Platform.getNativeLibraryResourcePrefix();
        String currentDir = System.getProperty("user.dir");
       // String libPath = currentDir + "/rust/target/release".replace('/', File.separatorChar);
        String libPath = (currentDir + "/native/" + folderPrefix).replace('/', File.separatorChar);
        System.setProperty("jna.library.path", libPath);

        System.out.println(libPath);
    }

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

    @Test
    public void testGetBaseAddressFromMnemonicIndex_0() {
        String baseAddress = CardanoJNA.INSTANCE.getBaseAddress(phrase24W, 0, false);
        System.out.println(baseAddress);
        Assertions.assertEquals(baseAddress0, baseAddress);
    }

    @Test
    public void testGetBaseAddressFromMnemonicIndex_1() {
        String baseAddress = CardanoJNA.INSTANCE.getBaseAddress(phrase24W, 1, false);
        System.out.println(baseAddress);
        Assertions.assertEquals(baseAddress1, baseAddress);
    }

    @Test
    public void testGetBaseAddressFromMnemonicIndex_2() {
        String baseAddress = CardanoJNA.INSTANCE.getBaseAddress(phrase24W, 2, false);
        System.out.println(baseAddress);
        Assertions.assertEquals(baseAddress2, baseAddress);
    }

    @Test
    public void generateMnemonic24w() {
        String mnemonic = CardanoJNA.INSTANCE.generateMnemonic();
        System.out.println(mnemonic);
        Assertions.assertTrue(mnemonic.length() > 0);
    }

    @Test
    public void testGetBaseAddressFromMnemonicByNetworkInfoMainnet() {
        String baseAddress = CardanoJNA.INSTANCE.getBaseAddressByNetwork(phrase24W, 2, Networks.mainnet());
        Assertions.assertEquals(baseAddress2, baseAddress);
    }

    @Test
    public void testGetBaseAddressFromMnemonicByNetworkInfoTestnet() {
        String baseAddress = CardanoJNA.INSTANCE.getBaseAddressByNetwork(phrase24W, 1, Networks.testnet());
        Assertions.assertEquals(testnetBaseAddress1, baseAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndex_0() {
        String entAddress = CardanoJNA.INSTANCE.getEnterpriseAddress(phrase24W, 0, false);
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress0, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndex_1() {
        String entAddress = CardanoJNA.INSTANCE.getEnterpriseAddress(phrase24W, 1, false);
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress1, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndex_2() {
        String entAddress = CardanoJNA.INSTANCE.getEnterpriseAddress(phrase24W, 2, false);
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress2, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndexByNetwork_0() {
        String entAddress = CardanoJNA.INSTANCE.getEnterpriseAddressByNetwork(phrase24W, 0, Networks.mainnet());
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress0, entAddress);
    }

    @Test
    public void testGetEnterpriseAddressFromMnemonicIndexByNetwork_1() {
        String entAddress = CardanoJNA.INSTANCE.getEnterpriseAddressByNetwork(phrase24W, 1, Networks.mainnet());
        System.out.println(entAddress);
        Assertions.assertEquals(entAddress1, entAddress);
    }
}
