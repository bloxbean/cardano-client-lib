package com.bloxbean.cardano.client.crypto.bip32;

import com.bloxbean.cardano.client.crypto.bip32.key.HdPrivateKey;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;

/**
 * HD public and private key
 **/
public class HdKeyPair {

    private final HdPrivateKey privateKey;
    private final HdPublicKey publicKey;
    private final String path;

    public HdKeyPair(HdPrivateKey privateKey, HdPublicKey publicKey, String path) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.path = path;
    }

    public HdPrivateKey getPrivateKey() {
        return privateKey;
    }

    public HdPublicKey getPublicKey() {
        return publicKey;
    }

    public String getPath() {
        return path;
    }
}
