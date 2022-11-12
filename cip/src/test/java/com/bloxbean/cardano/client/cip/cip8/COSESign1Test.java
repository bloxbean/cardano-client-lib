package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.UnicodeString;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assertions;
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

    @Test
    void testSignedData() {
        //Payload = "hello"
        String coseSignMsgInHex = "845869a30127045820674d11e432450118d70ea78673d5e31d5cc1aec63de0ff6284784876544be3406761646472657373583901d2eb831c6cad4aba700eb35f86966fbeff19d077954430e32ce65e8da79a3abe84f4ce817fad066acc1435be2ffc6bd7dce2ec1cc6cca6cba166686173686564f44568656c6c6f5840a3b5acd99df5f3b5e4449c5a116078e9c0fcfc126a4d4e2f6a9565f40b0c77474cafd89845e768fae3f6eec0df4575fcfe7094672c8c02169d744b415c617609";
        COSESign1 coseSign1 = COSESign1.deserialize(HexUtil.decodeHexString(coseSignMsgInHex));

        SigStructure sigStructure = coseSign1.signedData(null, null);

        assertThat(sigStructure.sigContext()).isEqualTo(SigContext.Signature1);
        assertThat(sigStructure.bodyProtected).isEqualTo(coseSign1.headers()._protected());
        assertThat(sigStructure.externalAad()).isNull();
        assertThat(sigStructure.payload()).isEqualTo("hello".getBytes());
        assertThat(sigStructure.signProtected()).isNull();
    }

    @Test
    void testSignedData_noparams() {
        //Payload = "hello"
        String coseSignMsgInHex = "845869a30127045820674d11e432450118d70ea78673d5e31d5cc1aec63de0ff6284784876544be3406761646472657373583901d2eb831c6cad4aba700eb35f86966fbeff19d077954430e32ce65e8da79a3abe84f4ce817fad066acc1435be2ffc6bd7dce2ec1cc6cca6cba166686173686564f44568656c6c6f5840a3b5acd99df5f3b5e4449c5a116078e9c0fcfc126a4d4e2f6a9565f40b0c77474cafd89845e768fae3f6eec0df4575fcfe7094672c8c02169d744b415c617609";
        COSESign1 coseSign1 = COSESign1.deserialize(HexUtil.decodeHexString(coseSignMsgInHex));

        SigStructure sigStructure = coseSign1.signedData();

        assertThat(sigStructure.sigContext()).isEqualTo(SigContext.Signature1);
        assertThat(sigStructure.bodyProtected).isEqualTo(coseSign1.headers()._protected());
        assertThat(sigStructure.externalAad()).isNull();
        assertThat(sigStructure.payload()).isEqualTo("hello".getBytes());
        assertThat(sigStructure.signProtected()).isNull();
    }

    @Test
    void testSignedData_nopayloadAndnoextpayload_throwsException() {
        COSESign1 coseSign1 = new COSESign1()
                .headers(new Headers()
                    ._protected(new ProtectedHeaderMap(new HeaderMap()
                        .addOtherHeader("key1", new UnicodeString("value1"))
                    )))
                .signature(getBytes(01, 64));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            coseSign1.signedData(null, null);
        });
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
