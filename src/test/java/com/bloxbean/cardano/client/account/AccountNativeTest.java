package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.config.Configuration;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;

public class AccountNativeTest extends AccountTest {

    static {
        //String folderPrefix = Platform.getNativeLibraryResourcePrefix();
        String currentDir = System.getProperty("user.dir");
        String libPath = (currentDir + "/native").replace('/', File.separatorChar);
        System.setProperty("jna.library.path", libPath);

        System.out.println(libPath);
    }

    @BeforeAll
    static void setUp() {
        Configuration.INSTANCE.setUseNativeLibForAccountGen(true);
        System.out.println("Testing with useNativeLibForAccountGen: " + Configuration.INSTANCE.isUseNativeLibForAccountGen());
    }

}
