package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class COSESignTest extends COSEBaseTest {

    @Test
    void serDesCoseSign() {
        HeaderMap hm = new HeaderMap()
                .contentType(-1000);

        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap(new byte[0]))
                .unprotected(hm);

        byte[] payload = getBytes(64, 39);

        byte[] signature1 = new byte[]{1, 2, 100};
        COSESignature coseSignature1 = new COSESignature()
                .signature(signature1)
                .headers(headers);

        byte[] signature2 = new byte[]{3, 2, 100, 101};
        COSESignature coseSignature2 = new COSESignature()
                .signature(signature2)
                .headers(headers);

        COSESign coseSign = new COSESign()
                .headers(headers)
                .payload(payload)
                .signature(coseSignature1)
                .signature(coseSignature2);

        COSESign deCoseSign = deserializationTest(coseSign, null);

        //Random fields check
        assertThat(deCoseSign.headers().unprotected().contentType()).isEqualTo(-1000L);
        assertThat(deCoseSign.signatures().get(0).headers().unprotected().contentType()).isEqualTo(-1000L);
        assertThat(deCoseSign.signatures().get(1).headers().unprotected().contentType()).isEqualTo(-1000L);
    }

    private COSESign deserializationTest(COSESign hm, String expectedHex) {
        byte[] serializeByte1 = hm.serializeAsBytes();
        byte[] serializeByte2;
        COSESign deCoseSign;
        try {
            deCoseSign = COSESign.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = deCoseSign.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return deCoseSign;
    }
}
