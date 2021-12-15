package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.jna.CardanoJNAUtil;
import com.bloxbean.cardano.client.util.HexUtil;

public class NativeSigningProvider implements SigningProvider {

    @Override
    public byte[] sign(byte[] message, byte[] privateKey) {
        String msgHex = HexUtil.encodeHexString(message);
        String privateKeyHex = HexUtil.encodeHexString(privateKey);

        String signatureHex = CardanoJNAUtil.signMsg(msgHex, privateKeyHex);

        if (signatureHex == null || signatureHex.isEmpty()) {
            throw new CryptoException("Sign operation failed");
        }

        return HexUtil.decodeHexString(signatureHex);
    }
}
