package com.bloxbean.cardano.client.crypto.bip32;

import com.bloxbean.cardano.client.account.Account;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HdKeyGeneratorTest {

    @Test
    void getPublicKey() {
        Account account = new Account(2);
        HdKeyPair hdKeyPair = account.hdKeyPair();

        byte[] derivePubKey = HdKeyGenerator.getPublicKey(hdKeyPair.getPrivateKey().getKeyData());

        assertThat(derivePubKey).isEqualTo(hdKeyPair.getPublicKey().getKeyData());
    }
}
