package com.bloxbean.cardano.client.nativeimage;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Key derivation from a mnemonic, with the BIP39 English word list {@link MnemonicCode} loads from the class path.
 * The crypto jar registers that resource, but GraalVM also includes it on its own because it is looked up by a
 * constant name, so these tests pass without the crypto metadata as well.
 */
class MnemonicNativeTest {
    private static final String MNEMONIC = "test test test test test test test test test test test test test test test test"
            + " test test test test test test test sauce";

    @Test
    void accountFromMnemonic() {
        Account account = Account.createFromMnemonic(Networks.testnet(), MNEMONIC);

        assertEquals("addr_test1qryvgass5dsrf2kxl3vgfz76uhp83kv5lagzcp29tcana68ca5aqa6swlq6llfamln09tal7n5kvt4275ckwedpt4v7q48uhex",
                account.baseAddress());
    }

    @Test
    void mnemonicFromEntropy() throws Exception {
        List<String> words = MnemonicCode.INSTANCE.toMnemonic(new byte[32]);

        assertEquals(24, words.size());
        assertEquals("abandon", words.get(0));
        assertEquals("art", words.get(23));
    }
}
