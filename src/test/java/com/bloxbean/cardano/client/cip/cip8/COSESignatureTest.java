package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class COSESignatureTest extends COSEBaseTest {

    @Test
    void serDesCOSESignature() {
        COSESignature coseSignature = new COSESignature()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap())
                        .unprotected(new HeaderMap()
                                .addCriticality(8)
                                .algorithmId(3)
                        )
                ).signature(getBytes(5, 64));

        COSESignature desCoseSignature = deserializationTest(coseSignature, null);
    }

    private COSESignature deserializationTest(COSESignature hm, String expectedHex) {
        byte[] serializeByte1 = hm.serializeAsBytes();
        byte[] serializeByte2;
        COSESignature coseSignature;
        try {
            coseSignature = COSESignature.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = coseSignature.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return coseSignature;
    }
}
