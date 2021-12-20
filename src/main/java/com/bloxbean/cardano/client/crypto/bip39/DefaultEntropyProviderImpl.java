package com.bloxbean.cardano.client.crypto.bip39;

import com.bloxbean.cardano.client.crypto.bip39.api.EntropyProvider;

import java.security.SecureRandom;

public class DefaultEntropyProviderImpl implements EntropyProvider {

    @Override
    public byte[] generateRandom(int byteLength) {
        byte[] entropy = new byte[byteLength];
        new SecureRandom().nextBytes(entropy);

        return entropy;
    }
}
