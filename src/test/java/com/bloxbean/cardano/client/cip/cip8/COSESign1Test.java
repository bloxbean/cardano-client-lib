package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class COSESign1Test extends COSEBaseTest {

    @Test
    void serDesCoseSign1() {
        HeaderMap hm = new HeaderMap()
                .contentType(-1000);

        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap(new byte[0]))
                .unprotected(hm);

        byte[] payload = getBytes(64, 39);
        byte[] signature = new byte[]{1, 2, 100};

        COSESign1 coseSign1 = new COSESign1()
                .headers(headers)
                .payload(payload)
                .signature(signature);

        COSESign1 coseSignNoPayload = new COSESign1()
                .headers(headers)
                .payload(null)
                .signature(signature);

        String expectedPayloadSerBytes = "8440a1033903e7582740404040404040404040404040404040404040404040404040404040404040404040404040404043010264";
        String expectedNoPayloadSerBytes = "8440a1033903e7f643010264";

        deserializationTest(coseSign1, expectedPayloadSerBytes);
        deserializationTest(coseSignNoPayload, expectedNoPayloadSerBytes);
    }

    private COSESign1 deserializationTest(COSESign1 hm, String expectedHex) {
        byte[] serializeByte1 = hm.serializeAsBytes();
        byte[] serializeByte2;
        COSESign1 deCoseSign1;
        try {
            deCoseSign1 = COSESign1.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = deCoseSign1.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return deCoseSign1;
    }
}
