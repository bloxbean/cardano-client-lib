package com.bloxbean.cardano.client.common;

import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MnemonicUtil {

    public static void validateMnemonic(String mnemonic) {
        if (mnemonic == null) {
            throw new AddressRuntimeException("Mnemonic cannot be null");
        }

        mnemonic = mnemonic.replaceAll("\\s+", " ");
        String[] words = mnemonic.split("\\s+");

        try {
            MnemonicCode.INSTANCE.check(Arrays.asList(words));
        } catch (MnemonicException e) {
            throw new AddressRuntimeException("Invalid mnemonic phrase", e);
        }
    }

    public static String generateNew(Words noOfWords) {
        String mnemonic = null;
        try {
            mnemonic = MnemonicCode.INSTANCE.createMnemonic(noOfWords).stream().collect(Collectors.joining(" "));
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException("Mnemonic generation failed", e);
        }
        return mnemonic;
    }
}
