package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncryptionTest extends COSEBaseTest {

    @Test
    void serDesPubKeyEncryption() {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(Long.valueOf(-9));
        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap())
                .unprotected(hm);

        COSEEncrypt0 coseEncrypt = new COSEEncrypt0()
                .headers(headers)
                .ciphertext("This is a msg".getBytes());

        PasswordEncryption pwdEncryption = new PasswordEncryption(coseEncrypt);

        deserializationTest(pwdEncryption, null);
    }

    private PasswordEncryption deserializationTest(PasswordEncryption pke, String expectedHex) {
        byte[] serializeByte1 = pke.serializeAsBytes();
        byte[] serializeByte2;
        PasswordEncryption dePEnc;
        try {
            dePEnc = PasswordEncryption.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = dePEnc.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return dePEnc;
    }

}
