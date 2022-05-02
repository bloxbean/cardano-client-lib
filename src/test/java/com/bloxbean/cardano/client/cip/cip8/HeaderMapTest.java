package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderMapTest extends COSEBaseTest {

    @Test
    void emptyOrSerializedMap() {
        HeaderMap hm = new HeaderMap()
                .algorithmId(199)
                .partialInitVector(new byte[]{0, 1, 2});

        HeaderMap deHm = deserializationTest(hm);

        assertThat(hm.algorithmId()).isEqualTo(deHm.algorithmId());
        assertThat(hm.partialInitVector()).isEqualTo(deHm.partialInitVector());
    }

    @Test
    void emptyMap() {
        HeaderMap hm = new HeaderMap();

        deserializationTest(hm);
    }

    @Test
    void serDesHeaderMap() throws CborException {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(Long.valueOf(-9));

        COSESignature coseSignature = new COSESignature()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap(hm))
                        .unprotected(hm)
                ).signature(getBytes(87, 74));

        LinkedHashMap<Object, DataItem> otherHeaderMap = new LinkedHashMap<>();
        otherHeaderMap.put("i am a string key", new UnicodeString("also a string"));

        HeaderMap headerMap = new HeaderMap()
                .algorithmId(0)
                .criticality(List.of(-166, "dsfdsf8353jh5  fsdfd!%&#%3j"))
                .contentType("content-type")
                .keyId(getBytes(34, 32))
                .initVector(getBytes(97, 16))
                .partialInitVector(getBytes(5, 13))
                .addSignature(coseSignature)
                .otherHeaders(otherHeaderMap);

        String serializedHex = HexUtil.encodeHexString(headerMap.serializeAsBytes());
        String expectedHex = "a80100028238a5781b647366647366383335336a6835202066736466642125262325336a036c636f6e74656e742d747970650458202222222222222222222222222222222222222222222222222222222222222222055061616161616161616161616161616161064d0505050505050505050505050507834ca20328044707070707070707a20328044707070707070707584a5757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757716920616d206120737472696e67206b65796d616c736f206120737472696e67";
        assertThat(serializedHex).isEqualTo(expectedHex);

        deserializationTest(headerMap);
    }

    @Test
    void serDesHeaderMap2() throws CborException {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(-9);

        COSESignature coseSignature = new COSESignature()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap(hm))
                        .unprotected(hm)
                ).signature(getBytes(87, 74));


        //additional header item
        Array nullValue = new Array();
        nullValue.add(new UnsignedInteger(3));
        nullValue.add(SimpleValue.NULL);

        //TODO -- Why Tagged CBOR is producing different value than message-signing rust test case
//        DataItem nullValue = SimpleValue.NULL;
//        nullValue.setTag(3);

        HeaderMap headerMap = new HeaderMap()
                .algorithmId(Long.valueOf(0))
                .addCriticality(-166L)
                .addCriticality("dsfdsf8353jh5  fsdfd!%&#%3j")
                .contentType("content-type")
                .keyId(getBytes(34, 32))
                .initVector(getBytes(97, 16))
                .partialInitVector(getBytes(5, 13))
                .counterSignature(List.of(
                        coseSignature
                ))
                .addOtherHeader("i am a string key", new UnicodeString("also a string"))
                .addOtherHeader(-6, nullValue);

        String serializedHex = HexUtil.encodeHexString(headerMap.serializeAsBytes());
        String expectedHex = "a90100028238a5781b647366647366383335336a6835202066736466642125262325336a036c636f6e74656e742d747970650458202222222222222222222222222222222222222222222222222222222222222222055061616161616161616161616161616161064d0505050505050505050505050507834ca20328044707070707070707a20328044707070707070707584a5757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757716920616d206120737472696e67206b65796d616c736f206120737472696e67258203f6";
        assertThat(serializedHex).isEqualTo(expectedHex);

        deserializationTest(headerMap);
    }

    @Test
    void serDesHeaderMap_multipleSignatures() throws CborException {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(Long.valueOf(-9))
                .addOtherHeader(BigInteger.valueOf(99999999), new UnicodeString("Value1"))
                .addOtherHeader(200L, new UnsignedInteger(30000));

        COSESignature coseSignature1 = new COSESignature()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap(hm))
                        .unprotected(hm)
                ).signature(getBytes(87, 74));

        COSESignature coseSignature2 = new COSESignature()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap(hm))
                        .unprotected(hm)
                ).signature(getBytes(22, 64));

        LinkedHashMap<Object, DataItem> otherHeaderMap = new LinkedHashMap<>();
        otherHeaderMap.put("i am a string key", new UnicodeString("also a string"));

        HeaderMap headerMap = new HeaderMap()
                .algorithmId(0)
                .criticality(List.of(-166, "dsfdsf8353jh5  fsdfd!%&#%3j"))
                .contentType("content-type")
                .keyId(getBytes(34, 32))
                .initVector(getBytes(97, 16))
                .partialInitVector(getBytes(5, 13))
                .addSignature(coseSignature1)
                .addSignature(coseSignature2)
                .otherHeaders(otherHeaderMap);

        deserializationTest(headerMap);
    }

    private HeaderMap deserializationTest(HeaderMap hm) {
        byte[] serializeByte1 = hm.serializeAsBytes();
        byte[] serializeByte2;
        HeaderMap deHM;
        try {
            deHM = HeaderMap.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = deHM.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));

        assertThat(serializeByte2).isEqualTo(serializeByte1);

        return deHM;
    }

}
