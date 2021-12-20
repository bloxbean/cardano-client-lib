package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.jna.CardanoJNAUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.bouncycastle.crypto.Signer;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

public class DefaultSigningProvider implements SigningProvider {

    @Override
    public byte[] sign(byte[] message, byte[] privateKey) {
        Ed25519PrivateKeyParameters privKeyParams = new Ed25519PrivateKeyParameters(privateKey, 0);

        // Sign
        Signer signer = new Ed25519Signer();
        signer.init(true, privKeyParams);
        signer.update(message, 0, message.length);
        try {
            byte[] signature = signer.generateSignature();
            return signature;
        } catch (org.bouncycastle.crypto.CryptoException e) {
            throw new CryptoException("Signing error", e);
        }
    }

    @Override
    public byte[] signExtended(byte[] message, byte[] privateKey, byte[] publicKey) {
        String msgHex = HexUtil.encodeHexString(message);
        String pvtKeyHex = HexUtil.encodeHexString(privateKey);
        String publicKeyHex = HexUtil.encodeHexString(publicKey);

        String signatureHex = CardanoJNAUtil.signExtended(msgHex, pvtKeyHex, publicKeyHex);
        return HexUtil.decodeHexString(signatureHex);
    }
}
