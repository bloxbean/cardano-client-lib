package com.bloxbean.cardano.client.crypto.bip32;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HdKeyGeneratorTest {

    @Test
    void testGPublicKey() {
        Account account = new Account(2);
        HdKeyPair hdKeyPair = account.hdKeyPair();

        byte[] derivePubKey = HdKeyGenerator.getPublicKey(hdKeyPair.getPrivateKey().getKeyData());

        assertThat(derivePubKey).isEqualTo(hdKeyPair.getPublicKey().getKeyData());
    }

    @Test
    void testGetRootKeyPairFromEntropy() throws MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, MnemonicException.MnemonicLengthException {
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";
        byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String rootKey = Bech32.encode(rootKeyBytes, "root_xsk");

        assertThat(rootKey).isEqualTo("root_xsk1hp9an83kfma0ufdaeqft6xv0snf4ek9uqemk5chp3p25el8w0fglszmgkq9qxvguj33fnulms4qfnx9jawhde4ng9qzg3zzg5u8r2af4jxpe8nfjulrzey7p8ttnt5yn53exsawm6wkmtqm989ehwtr0kc244zfn");
    }
}
