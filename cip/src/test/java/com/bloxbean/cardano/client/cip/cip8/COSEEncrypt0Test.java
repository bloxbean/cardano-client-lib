package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class COSEEncrypt0Test extends COSEBaseTest {

    @Test
    void serDesCOSEEncrypt() {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(Long.valueOf(-9));
        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap())
                .unprotected(hm);

        COSEEncrypt0 coseEncrypt = new COSEEncrypt0()
                .headers(headers)
                .ciphertext("This is a msg".getBytes());

        COSEEncrypt0 deCOSEEnc = deserializationTest(coseEncrypt, null);

        assertThat(deCOSEEnc).isEqualTo(coseEncrypt);
    }

    private COSEEncrypt0 deserializationTest(COSEEncrypt0 cosEnc, String expectedHex) {
        byte[] serializeByte1 = cosEnc.serializeAsBytes();
        byte[] serializeByte2;
        COSEEncrypt0 deCOSEncrypt;
        try {
            deCOSEncrypt = COSEEncrypt0.deserialize((Array) CborDecoder.decode(serializeByte1).get(0));
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
