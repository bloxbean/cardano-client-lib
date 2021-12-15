package com.bloxbean.cardano.client.crypto.bip39;

import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.crypto.bip39.Utils.WHITESPACE_SPLITTER;
import static org.junit.jupiter.api.Assertions.assertThrows;

//This file is originally from bitcoinj project. https://github.com/bitcoinj/bitcoinj
public class MnemonicCodeTest {

    private MnemonicCode mc;

    @BeforeEach
    public void setup() throws IOException {
        mc = new MnemonicCode();
    }

    @Test
    public void testBadEntropyLength() throws Exception {
        assertThrows(MnemonicException.MnemonicLengthException.class, () -> {
            byte[] entropy = HexUtil.decodeHexString("7f7f7f7f7f7f7f7f7f7f7f7f7f7f");
            mc.toMnemonic(entropy);
        });
    }

    @Test
    public void testBadLength() throws Exception {
        assertThrows(MnemonicException.MnemonicLengthException.class, () -> {
            List<String> words = WHITESPACE_SPLITTER.splitToList("risk tiger venture dinner age assume float denial penalty hello");
            mc.check(words);
        });
    }

    @Test
    public void testBadWord() throws Exception {
        assertThrows(MnemonicException.MnemonicWordException.class, () -> {
            List<String> words = WHITESPACE_SPLITTER.splitToList("risk tiger venture dinner xyzzy assume float denial penalty hello game wing");
            mc.check(words);
        });
    }

    @Test
    public void testBadChecksum() throws Exception {
        assertThrows(MnemonicException.MnemonicChecksumException.class, () -> {
            List<String> words = WHITESPACE_SPLITTER.splitToList("bless cloud wheel regular tiny venue bird web grief security dignity zoo");
            mc.check(words);
        });
    }

    @Test
    public void testEmptyMnemonic() throws Exception {
        assertThrows(MnemonicException.MnemonicLengthException.class, () -> {
            List<String> words = new ArrayList<>();
            mc.check(words);
        });
    }

    @Test
    public void testEmptyEntropy() throws Exception {
        assertThrows(MnemonicException.MnemonicLengthException.class, () -> {
            byte[] entropy = {};
            mc.toMnemonic(entropy);
        });
    }

    @Test
    public void testNullPassphrase() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            List<String> code = WHITESPACE_SPLITTER.splitToList("legal winner thank year wave sausage worth useful legal winner thank yellow");
            MnemonicCode.toSeed(code, null);
        });
    }
}

