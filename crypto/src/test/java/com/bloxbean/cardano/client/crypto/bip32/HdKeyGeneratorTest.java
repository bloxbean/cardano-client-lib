package com.bloxbean.cardano.client.crypto.bip32;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.OSUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HdKeyGeneratorTest {

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

    @Test
    void testGetRootKeyPairFromEntropy_whenAndroid() throws MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, MnemonicException.MnemonicLengthException {
        OSUtil.setAndroid(true);
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";
        byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String rootKey = Bech32.encode(rootKeyBytes, "root_xsk");
        OSUtil.setAndroid(false);

        assertThat(rootKey).isEqualTo("root_xsk1hp9an83kfma0ufdaeqft6xv0snf4ek9uqemk5chp3p25el8w0fglszmgkq9qxvguj33fnulms4qfnx9jawhde4ng9qzg3zzg5u8r2af4jxpe8nfjulrzey7p8ttnt5yn53exsawm6wkmtqm989ehwtr0kc244zfn");

    }

    @Test
    void testGetRootKeyPairFromEntropy_Ledger_NoPassphrase() throws MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, MnemonicException.MnemonicLengthException {
        String mnemonicPhrase = "recall grace sport punch exhibit mad harbor stand obey short width stem awkward used stairs wool ugly trap season stove worth toward congress jaguar";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.LEDGER);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("a08cf85b564ecf3b947d8d4321fb96d70ee7bb760877e371899b14e2ccf88658104b884682b57efd97decbb318a45c05a527b9cc5c2f64f7352935a049ceea60680d52308194ccef2a18e6812b452a5815fbd7f5babc083856919aaf668fe7e4");
    }

    @Test
    void testGetRootKeyPairFromEntropy_Ledger_WithIterations() throws MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, MnemonicException.MnemonicLengthException {
        String mnemonicPhrase = "correct cherry mammal bubble want mandate polar hazard crater better craft exotic choice fun tourist census gap lottery neglect address glow carry old business";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.LEDGER);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("587c6774357ecbf840d4db6404ff7af016dace0400769751ad2abfc77b9a3844cc71702520ef1a4d1b68b91187787a9b8faab0a9bb6b160de541b6ee62469901fc0beda0975fe4763beabd83b7051a5fd5cbce5b88e82c4bbaca265014e524bd");
    }

    @Test
    void testGetRootKeyPairFromEntropy_Ledger_StandardTestVector() throws MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, MnemonicException.MnemonicLengthException {
        String mnemonicPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
        String passphrase = "foo";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, passphrase, Bip32Type.LEDGER);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("f053a1e752de5c26197b60f032a4809f08bb3e5d90484fe42024be31efcba7578d914d3ff992e21652fee6a4d99f6091006938fac2c0c0f9d2de0ba64b754e92a4f3723f23472077aa4cd4dd8a8a175dba07ea1852dad1cf268c61a2679c3890");
    }

    @Test
    void testGetRootKeyPairFromEntropy_Ledger_BackwardCompatibility() throws MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, MnemonicException.MnemonicLengthException {
        // Verify that the entropy-based API still works for backward compatibility
        String mnemonicPhrase = "recall grace sport punch exhibit mad harbor stand obey short width stem awkward used stairs wool ugly trap season stove worth toward congress jaguar";
        byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy, Bip32Type.LEDGER);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        // Should produce the same result as the mnemonic-based API
        assertThat(masterKeyHex).isEqualTo("a08cf85b564ecf3b947d8d4321fb96d70ee7bb760877e371899b14e2ccf88658104b884682b57efd97decbb318a45c05a527b9cc5c2f64f7352935a049ceea60680d52308194ccef2a18e6812b452a5815fbd7f5babc083856919aaf668fe7e4");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Trezor_15Words_NoPassphrase() {
        // 15 words (< 24), should use same algorithm as ICARUS
        String mnemonicPhrase = "eight country switch draw meat scout mystery blade tip drift useless good keep usage title";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.TREZOR);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        // For < 24 words, Trezor uses ICARUS algorithm
        assertThat(masterKeyHex).isEqualTo("c065afd2832cd8b087c4d9ab7011f481ee1e0721e78ea5dd609f3ab3f156d245d176bd8fd4ec60b4731c3918a2a72a0226c0cd119ec35b47e4d55884667f552a23f7fdcd4a10c6cd2c7393ac61d877873e248f417634aa3d812af327ffe9d620");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Trezor_15Words_SameAsIcarus() {
        // Verify that Trezor with < 24 words produces the same result as ICARUS
        String mnemonicPhrase = "eight country switch draw meat scout mystery blade tip drift useless good keep usage title";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();

        HdKeyPair trezorKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.TREZOR);
        HdKeyPair icarusKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.ICARUS);

        byte[] trezorBytes = trezorKeyPair.getPrivateKey().getBytes();
        byte[] icarusBytes = icarusKeyPair.getPrivateKey().getBytes();

        // For < 24 words, Trezor and ICARUS should produce identical results
        assertThat(trezorBytes).isEqualTo(icarusBytes);
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Trezor_24Words() {
        // Test 24-word mnemonic - Trezor has a bug where it includes the checksum byte
        String mnemonicPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.TREZOR);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("60e4d66a4ac3f3abdfbabc56a451fe52b265d574879276859d47f03a964a8d5246069e680f9290ba8cbcc30194d9687cb63d8def4fd00d1a308a4c318bcb4e7451b8b2cde121e8cfb436804ce4b9dd181860de0fcc3500517fbcf3e6fe7bdbf1");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Trezor_24Words_DifferentFromIcarus() {
        // Verify that Trezor with 24 words produces DIFFERENT results than ICARUS (due to bug)
        String mnemonicPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();

        HdKeyPair trezorKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.TREZOR);
        HdKeyPair icarusKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.ICARUS);

        byte[] trezorBytes = trezorKeyPair.getPrivateKey().getBytes();
        byte[] icarusBytes = icarusKeyPair.getPrivateKey().getBytes();

        // For 24 words, Trezor and ICARUS should produce DIFFERENT results due to the checksum bug
        assertThat(trezorBytes).isNotEqualTo(icarusBytes);
    }

    @Test
    void testGetRootKeyPairFromEntropy_Trezor_BackwardCompatibility() throws MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, MnemonicException.MnemonicLengthException {
        // Verify that the entropy-based API still works for backward compatibility
        String mnemonicPhrase = "eight country switch draw meat scout mystery blade tip drift useless good keep usage title";
        byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy, Bip32Type.TREZOR);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        // Should produce the same result as the mnemonic-based API
        assertThat(masterKeyHex).isEqualTo("c065afd2832cd8b087c4d9ab7011f481ee1e0721e78ea5dd609f3ab3f156d245d176bd8fd4ec60b4731c3918a2a72a0226c0cd119ec35b47e4d55884667f552a23f7fdcd4a10c6cd2c7393ac61d877873e248f417634aa3d812af327ffe9d620");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Trezor_NoPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.TREZOR);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("e0670f289f625a230b3f35dcf8ebaa22ab40109906933108714c2de13dd33048cbba40347f99f27155642f2f6cc393d5dfb9a890c1b3917969d77a17fabb5bc5a78061692265491f5c4670a2eb1166bc9681489f126b7bb383b8ea0d2234add7");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Trezor_WithPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, passphrase, Bip32Type.TREZOR);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("2001d74c3d96956aa6eccbd179cf88b73f39f4ce0b0b4915090776012de5ac44140d27f9a0fb074471feebeacb8d1aaee241feb29e29af60a818f7d2597997c97bf6591b8083509945e780b78ee5c83557c7fa736e3af08998958829f84c3c4d");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Ledger_NoPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.LEDGER);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("6055a48d940d17d35ed44af96e8c1455e551b9d6eb094bdcec365071d95166594d9eca705dd02ceef312fe8ebeede67a7595ae57e4708d254dc48e26a9dd8639b540c14b4cc95bc1024f31e336662cce960437a63ce0a560efcb276b28015626");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Ledger_WithPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, passphrase, Bip32Type.LEDGER);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("e8eaf3ee0b95e31ea5ab4ce6f79d2cebd95dc91a9e91be483f70d535a2d28959d0f4271f660f1d3aa99dbd33577900d65d6b58387b8cdc6b28f40faf5ffb54ea4f672c8b43357c9fbdceaf125225c3a82122de0cae70fce08e8d704760ab05c7");
    }

    @Test
    void testGetRootKeyPairFromMnemonic_Icarus_WithPassphrase() {
        String mnemonicPhrase = "eight country switch draw meat scout mystery blade tip drift useless good keep usage title";
        String passphrase = "foo";
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromMnemonic(mnemonicPhrase, passphrase, Bip32Type.ICARUS);

        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("70531039904019351e1afb361cd1b312a4d0565d4ff9f8062d38acf4b15cce41d7b5738d9c893feea55512a3004acb0d222c35d3e3d5cde943a15a9824cbac59443cf67e589614076ba01e354b1a432e0e6db3b59e37fc56b5fb0222970a010e");
    }

}
