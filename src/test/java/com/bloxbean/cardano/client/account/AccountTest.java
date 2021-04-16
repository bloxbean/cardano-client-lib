package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.util.Networks;
import com.bloxbean.cardano.client.util.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class AccountTest {

    static {
        String folderPrefix = Platform.getNativeLibraryResourcePrefix();
        String currentDir = System.getProperty("user.dir");
        String libPath = (currentDir + "/native/" + folderPrefix).replace('/', File.separatorChar);
        System.setProperty("jna.library.path", libPath);

        System.out.println(libPath);
    }

    @BeforeEach
    void setUp() {

    }

    @Test
    void getNewBaseAddress_Mainnet() {
        Account account = new Account();
        String address = account.baseAddress(0);
        String mnemonic = account.mnemonic();

        assertNotNull(address);
        assertNotNull(mnemonic);
        assertTrue(address.startsWith("addr") && !address.startsWith("addr_"));
        assertEquals(24, mnemonic.split(" ").length );
    }

    @Test
    void getNewBaseAddress_Test() {
        Account account = new Account(Networks.testnet());
        String address = account.baseAddress(0);
        String mnemonic = account.mnemonic();

        assertNotNull(address);
        assertNotNull(mnemonic);
        assertTrue(address.startsWith("addr_test"));
        assertEquals(24, mnemonic.split(" ").length );
    }

    @Test
    void getBaseAddressFromMnemonic() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String address0 = "addr1qxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps7zwsra";

        Account account = new Account(phrase24W);

        assertNotNull(account.baseAddress(0));
        assertNotNull(account.mnemonic());
        assertEquals(address0, account.baseAddress(0));
        assertEquals(phrase24W, account.mnemonic());
    }

    @Test
    void getBaseAddressFromMnemonic_byNetwork() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String address0 = "addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z";

        Account account = new Account(Networks.testnet(), phrase24W);

        assertNotNull(account.baseAddress(0));
        assertNotNull(account.mnemonic());
        assertEquals(address0, account.baseAddress(0));
        assertEquals(phrase24W, account.mnemonic());
    }

    @Test
    void getEntAddressFromMnemonic_byNetwork() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String address0 = "addr_test1vzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvssp226k";
        String address1 = "addr_test1vp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg99fsju";

        Account account = new Account(Networks.testnet(), phrase24W);

        assertEquals(address0, account.enterpriseAddress(0));
        assertEquals(address1, account.enterpriseAddress(1));
        assertNotNull(account.mnemonic());
    }
}
