package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.jna.CardanoNative;
import com.bloxbean.cardano.client.util.Platform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

public class CardanoNativeTest {

//    static {
//        String folderPrefix = Platform.getNativeLibraryResourcePrefix();
//        String currentDir = System.getProperty("user.dir");
//        // String libPath = currentDir + "/rust/target/release".replace('/', File.separatorChar);
//        String libPath = (currentDir + "/native/" + folderPrefix).replace('/', File.separatorChar);
//        System.setProperty("cardano.lib.path", libPath);
//
////        System.out.println(libPath);
////
////        String currentDir = System.getProperty("user.dir");
////        String libPath = currentDir + "/rust/target/release".replace('/', File.separatorChar);
////        System.setProperty("cardano.lib.path", libPath);
////        System.out.println(libPath);
//    }
//
//    @BeforeAll
//    static void setup() {
//    }
//
//    String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
//    String getPhrase24WAddress0 = "addr1qxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps7zwsra";
//    String getPhrase24WAddress1 = "addr1q93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4zthxn";
//    String getPhrase24WAddress2 = "addr1q8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4qp6cs";
//    String getPhrase24WAddress3 = "addr1qxa5pll82u8lqtzqjqhdr828medvfvezv4509nzyuhwt5aql5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psy8jsmy";
//
//    @Test
//    public void testGetBaseAddressFromMnemonicIndex_0() {
//        String baseAddress = CardanoNative.getBaseAddressFromMnemonic(phrase24W, 0, false);
//        System.out.println(baseAddress);
//        Assertions.assertEquals(getPhrase24WAddress0, baseAddress);
//    }
//
//    @Test
//    public void testGetBaseAddressFromMnemonicIndex_1() {
//        String baseAddress = CardanoNative.getBaseAddressFromMnemonic(phrase24W, 1, false);
//        System.out.println(baseAddress);
//        Assertions.assertEquals(getPhrase24WAddress1, baseAddress);
//    }
//
//    @Test
//    public void testGetBaseAddressFromMnemonicIndex_2() {
//        String baseAddress = CardanoNative.getBaseAddressFromMnemonic(phrase24W, 2, false);
//        System.out.println(baseAddress);
//        Assertions.assertEquals(getPhrase24WAddress2, baseAddress);
//    }
}
