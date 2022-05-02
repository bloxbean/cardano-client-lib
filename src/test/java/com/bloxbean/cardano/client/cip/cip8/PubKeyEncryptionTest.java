package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PubKeyEncryptionTest extends COSEBaseTest {

    @Test
    void serDesPubKeyEncryption() {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(Long.valueOf(-9));
        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap())
                .unprotected(hm);

        COSEEncrypt coseEncrypt = new COSEEncrypt()
                .headers(headers)
                .ciphertext("This is a msg".getBytes())
                .recipient(new COSERecipient()
                        .headers(headers)
                        .ciphertext("Recipient1 msg".getBytes()))
                .recipient(new COSERecipient()
                        .headers(headers)
                        .ciphertext("Recipient2 msg".getBytes()));

        PubKeyEncryption pubKeyEncryption = new PubKeyEncryption(coseEncrypt);

        deserializationTest(pubKeyEncryption, null);
    }

    private PubKeyEncryption deserializationTest(PubKeyEncryption pke, String expectedHex) {
        byte[] serializeByte1 = pke.serializeAsBytes();
        byte[] serializeByte2;
        PubKeyEncryption dePKE;
        try {
            dePKE = PubKeyEncryption.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = dePKE.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return dePKE;
    }

}
