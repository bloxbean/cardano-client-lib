package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.util.HexUtil;
import net.i2p.crypto.eddsa.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
    void verify_returnsFalseForAPublicKeyOffTheCurve() {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("374ec224aa6b66698506c892208e14ae6d990fa49d6e93037a2654366cca8caa");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);

        assertThat(new EdDSASigningProvider().verify(signature, message, publicKey)).isFalse();
    }

    @Test
    void verify_returnsFalseForMalformedInput() {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("3b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);
        SigningProvider signingProvider = new EdDSASigningProvider();

        assertThat(signingProvider.verify(Arrays.copyOf(signature, 63), message, publicKey)).isFalse();
        assertThat(signingProvider.verify(Arrays.copyOf(signature, 65), message, publicKey)).isFalse();
        assertThat(signingProvider.verify(signature, message, Arrays.copyOf(publicKey, 33))).isFalse();
        assertThat(signingProvider.verify(null, message, publicKey)).isFalse();
        assertThat(signingProvider.verify(signature, message, null)).isFalse();
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

    /**
     * Edge-case vectors from novifinancial/ed25519-speccheck ({@code cases.json} at commit
     * 65519336fda78a3d016e947df6d82848aca0c9da, Apache-2.0). The expected outcomes are libsodium's
     * ({@code crypto_sign_verify_detached}), which cardano-node uses: only vector 3 verifies. Vectors 4 and 5
     * pass the cofactored equation only; libsodium checks the cofactorless one and rejects them.
     */
    @ParameterizedTest(name = "speccheck vector {0} -> {4}")
    @CsvSource({
            "0, 8c93255d71dcab10e8f379c26200f3c7bd5f09d9bc3068d3ef4edeb4853022b6, c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa, c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a0000000000000000000000000000000000000000000000000000000000000000, false",
            "1, 9bd9f44f4dcc75bd531b56b2cd280b0bb38fc1cd6d1230e14861d861de092e79, c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa, f7badec5b8abeaf699583992219b7b223f1df3fbbea919844e3f7c554a43dd43a5bb704786be79fc476f91d3f3f89b03984d8068dcf1bb7dfc6637b45450ac04, false",
            "2, aebf3f2601a0c8c5d39cc7d8911642f740b78168218da8471772b35f9d35b9ab, f7badec5b8abeaf699583992219b7b223f1df3fbbea919844e3f7c554a43dd43, c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa8c4bd45aecaca5b24fb97bc10ac27ac8751a7dfe1baff8b953ec9f5833ca260e, false",
            "3, 9bd9f44f4dcc75bd531b56b2cd280b0bb38fc1cd6d1230e14861d861de092e79, cdb267ce40c5cd45306fa5d2f29731459387dbf9eb933b7bd5aed9a765b88d4d, 9046a64750444938de19f227bb80485e92b83fdb4b6506c160484c016cc1852f87909e14428a7a1d62e9f22f3d3ad7802db02eb2e688b6c52fcd6648a98bd009, true",
            "4, e47d62c63f830dc7a6851a0b1f33ae4bb2f507fb6cffec4011eaccd55b53f56c, cdb267ce40c5cd45306fa5d2f29731459387dbf9eb933b7bd5aed9a765b88d4d, 160a1cb0dc9c0258cd0a7d23e94d8fa878bcb1925f2c64246b2dee1796bed5125ec6bc982a269b723e0668e540911a9a6a58921d6925e434ab10aa7940551a09, false",
            "5, e47d62c63f830dc7a6851a0b1f33ae4bb2f507fb6cffec4011eaccd55b53f56c, cdb267ce40c5cd45306fa5d2f29731459387dbf9eb933b7bd5aed9a765b88d4d, 21122a84e0b5fca4052f5b1235c80a537878b38f3142356b2c2384ebad4668b7e40bc836dac0f71076f9abe3a53f9c03c1ceeeddb658d0030494ace586687405, false",
            "6, 85e241a07d148b41e47d62c63f830dc7a6851a0b1f33ae4bb2f507fb6cffec40, 442aad9f089ad9e14647b1ef9099a1ff4798d78589e66f28eca69c11f582a623, e96f66be976d82e60150baecff9906684aebb1ef181f67a7189ac78ea23b6c0e547f7690a0e2ddcd04d87dbc3490dc19b3b3052f7ff0538cb68afb369ba3a514, false",
            "7, 85e241a07d148b41e47d62c63f830dc7a6851a0b1f33ae4bb2f507fb6cffec40, 442aad9f089ad9e14647b1ef9099a1ff4798d78589e66f28eca69c11f582a623, 8ce5b96c8f26d0ab6c47958c9e68b937104cd36e13c33566acd2fe8d38aa19427e71f98a473474f2f13f06f97c20d58cc3f54b8bd0d272f42b695dd7e89a8c22, false",
            "8, 9bedc267423725d473888631ebf45988bad3db83851ee85c85e241a07d148b41, f7badec5b8abeaf699583992219b7b223f1df3fbbea919844e3f7c554a43dd43, ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff03be9678ac102edcd92b0210bb34d7428d12ffc5df5f37e359941266a4e35f0f, false",
            "9, 9bedc267423725d473888631ebf45988bad3db83851ee85c85e241a07d148b41, f7badec5b8abeaf699583992219b7b223f1df3fbbea919844e3f7c554a43dd43, ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffca8c5b64cd208982aa38d4936621a4775aa233aa0505711d8fdcfdaa943d4908, false",
            "10, e96b7021eb39c1a163b6da4e3093dcd3f21387da4cc4572be588fafae23c155b, ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff, a9d55260f765261eb9b84e106f665e00b867287a761990d7135963ee0a7d59dca5bb704786be79fc476f91d3f3f89b03984d8068dcf1bb7dfc6637b45450ac04, false",
            "11, 39a591f5321bbe07fd5a23dc2f39d025d74526615746727ceefd6e82ae65c06f, ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff, a9d55260f765261eb9b84e106f665e00b867287a761990d7135963ee0a7d59dca5bb704786be79fc476f91d3f3f89b03984d8068dcf1bb7dfc6637b45450ac04, false"
    })
    void verify_matchesCardanoNodeOnEdgeCaseVectors(int index, String message, String publicKey, String signature,
                                                    boolean expected) {
        SigningProvider signingProvider = new EdDSASigningProvider();

        boolean verified = signingProvider.verify(HexUtil.decodeHexString(signature),
                HexUtil.decodeHexString(message), HexUtil.decodeHexString(publicKey));

        assertThat(verified).as("vector %d", index).isEqualTo(expected);
    }
}
