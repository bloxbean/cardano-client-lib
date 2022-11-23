package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.util.HexUtil;
import net.i2p.crypto.eddsa.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EdDSASigningProviderTest {

    @Test
    void signExtended() {
        String msg = "hello";
        String pvtKey = "78bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";
        String pubKey = "9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8";

        SigningProvider signingProvider = new EdDSASigningProvider();
        byte[] signature = signingProvider.signExtended(msg.getBytes(StandardCharsets.UTF_8), HexUtil.decodeHexString(pvtKey));

        String signatureHex = HexUtil.encodeHexString(signature);

        assertThat(signatureHex).isEqualTo("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
    }

    @Test
    public void verifyExtended() throws Exception {
        byte[] signature = Utils.hexToBytes("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
        byte[] publicKey = Utils.hexToBytes("9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8");
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        SigningProvider signingProvider = new EdDSASigningProvider();
        boolean verified = signingProvider.verify(signature, message, publicKey);

        assertThat(verified).isEqualTo(true);
    }

    @Test
    public void verifyExtendedInvalidKey() throws Exception {
        byte[] signature = Utils.hexToBytes("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
        byte[] publicKey = Utils.hexToBytes("INVALID103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8");
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        SigningProvider signingProvider = new EdDSASigningProvider();
        boolean verified = signingProvider.verify(signature, message, publicKey);

        assertThat(verified).isEqualTo(false);
    }

    @Test
    public void verifyExtendedInvalidMessage() throws Exception {
        byte[] signature = Utils.hexToBytes("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
        byte[] publicKey = Utils.hexToBytes("9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8");
        byte[] message = "ola".getBytes(StandardCharsets.UTF_8);

        SigningProvider signingProvider = new EdDSASigningProvider();
        boolean verified = signingProvider.verify(signature, message, publicKey);

        assertThat(verified).isEqualTo(false);
    }

    @Test
    public void sign() {
        byte[] seed = Utils.hexToBytes("0000000000000000000000000000000000000000000000000000000000000000");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);
        SigningProvider signingProvider = new EdDSASigningProvider();
        byte[] signature = signingProvider.sign(message, seed);
        byte[] expectedSignature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        assertThat(signature).isEqualTo(expectedSignature);
    }

    @Test
    public void verify() {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("3b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);

        SigningProvider signingProvider = new EdDSASigningProvider();
        boolean verified = signingProvider.verify(signature, message, publicKey);

        assertThat(verified).isEqualTo(true);
    }

    @Test
    public void verifyInvalidKey() throws Exception {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("INVALIDcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);

        SigningProvider signingProvider = new EdDSASigningProvider();
        boolean verified = signingProvider.verify(signature, message, publicKey);

        assertThat(verified).isEqualTo(false);
    }

    @Test
    public void verifyInvalidMessage() throws Exception {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("3b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29");
        byte[] message = "This is a modified message".getBytes(StandardCharsets.UTF_8);

        SigningProvider signingProvider = new EdDSASigningProvider();
        boolean verified = signingProvider.verify(signature, message, publicKey);

        assertThat(verified).isEqualTo(false);
    }

    @Test
    void signAndVerify() throws Exception{
        byte[] msg = "eyJhbGciOiJFZERTQSJ9.RXhhbXBsZSBvZiBFZDI1NTE5IHNpZ25pbmc".getBytes(StandardCharsets.UTF_8);
        byte[] privateKey = Base64.getUrlDecoder().decode("nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A");
        byte[] publicKey = Base64.getUrlDecoder().decode("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo");

        // sign
        SigningProvider signingProvider = new EdDSASigningProvider();
        byte[] signature = signingProvider.sign(msg, privateKey);
        String actualSignature = Base64.getUrlEncoder().encodeToString(signature).replace("=", "");
        String expectedSignature = "hgyY0il_MGCjP0JzlnLWG1PPOt7-09PGcvMg3AIbQR6dWbhijcNR4ki4iylGjg5BhVsPt9g7sVvpAr_MuM0KAg";
        assertThat(actualSignature).isEqualTo(expectedSignature);

        // verify
        boolean verified = signingProvider.verify(signature, msg, publicKey);

        assertThat(verified).isEqualTo(true);
    }

    @Test
    void sign_throwErrorWhenInvalidPrivateKey() {
        String msg = "hello";
        String pvtKey = "bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";

        Assertions.assertThrows(CryptoException.class, () -> {
            SigningProvider signingProvider = new EdDSASigningProvider();
            signingProvider.sign(msg.getBytes(StandardCharsets.UTF_8), HexUtil.decodeHexString(pvtKey));
        });
    }


}
