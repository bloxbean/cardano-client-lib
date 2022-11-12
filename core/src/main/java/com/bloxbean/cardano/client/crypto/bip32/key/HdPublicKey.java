package com.bloxbean.cardano.client.crypto.bip32.key;

import com.bloxbean.cardano.client.crypto.CryptoException;

import java.util.Arrays;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;

//This file is originally from https://github.com/semuxproject/semux-core
public class HdPublicKey extends HdKey {

    //Needed during address encoding
    public byte[] getKeyHash() {
        return blake2bHash224(this.getKeyData());
    }

    public static HdPublicKey fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 64)
            throw new CryptoException("Invalid key length. Key length should be 64");

        byte[] pubKeyBytes = Arrays.copyOfRange(bytes, 0, 32);
        byte[] chain = Arrays.copyOfRange(bytes, 32, 64);

        HdPublicKey hdPublicKey = new HdPublicKey();
        hdPublicKey.setKeyData(pubKeyBytes);
        hdPublicKey.setChainCode(chain);

        return hdPublicKey;
    }

}
