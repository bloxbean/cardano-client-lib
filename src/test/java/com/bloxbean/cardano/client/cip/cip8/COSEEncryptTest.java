package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class COSEEncryptTest extends COSEBaseTest {

    @Test
    void serDesCOSEEncrypt() {
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

        COSEEncrypt deCOSEEnc = deserializationTest(coseEncrypt, null);

        assertThat(deCOSEEnc.recipients().size()).isEqualTo(2);
        assertThat(deCOSEEnc.recipients().get(0)).isEqualTo(coseEncrypt.recipients().get(0));
        assertThat(deCOSEEnc.recipients().get(1)).isEqualTo(coseEncrypt.recipients().get(1));
    }

    private COSEEncrypt deserializationTest(COSEEncrypt hm, String expectedHex) {
        byte[] serializeByte1 = hm.serializeAsBytes();
        byte[] serializeByte2;
        COSEEncrypt deCOSEncrypt;
        try {
            deCOSEncrypt = COSEEncrypt.deserialize((Array) CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = deCOSEncrypt.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return deCOSEncrypt;
    }
}
