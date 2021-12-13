package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NativeSigningProviderTest {

    @Test
    void sign() {
        String msg = "hello";
        String pvtKey = "78bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";

        NativeSigningProvider signingProvider = new NativeSigningProvider();
        byte[] signature = signingProvider.sign(msg.getBytes(StandardCharsets.UTF_8), HexUtil.decodeHexString(pvtKey));

        String signatureHex = HexUtil.encodeHexString(signature);

        assertThat(signatureHex).isEqualTo("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
    }

    @Test
    void sign_throwErrorWhenInvalidPrivateKey() {
        String msg = "hello";
        String pvtKey = "bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";

        Assertions.assertThrows(CryptoException.class, () -> {
            NativeSigningProvider signingProvider = new NativeSigningProvider();
            byte[] signature = signingProvider.sign(msg.getBytes(StandardCharsets.UTF_8), HexUtil.decodeHexString(pvtKey));
        });
    }
}
