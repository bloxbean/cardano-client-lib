package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderMapTest {

    @Test
    void headerMapSerDeSer() throws CborException {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(Long.valueOf(-9));

        COSESignature coseSignature = COSESignature.builder()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap(hm))
                        .unprotected(hm)
                ).signature(getBytes(87, 74))
                .build();

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

        DataItem hmDI = headerMap.serialize();
        String serializedHex1 = HexUtil.encodeHexString(CborSerializationUtil.serialize(hmDI));
        String expectedHex = "a80100028238a5781b647366647366383335336a6835202066736466642125262325336a036c636f6e74656e742d747970650458202222222222222222222222222222222222222222222222222222222222222222055061616161616161616161616161616161064d0505050505050505050505050507834ca20328044707070707070707a20328044707070707070707584a5757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757716920616d206120737472696e67206b65796d616c736f206120737472696e67";

        //De-serialize
        HeaderMap desHM = HeaderMap.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serializedHex1)).get(0));
        //Serialize again
        String serializedHex2 = HexUtil.encodeHexString(CborSerializationUtil.serialize(desHM.serialize()));

        assertThat(serializedHex1).isEqualTo(expectedHex);
        assertThat(serializedHex2).isEqualTo(expectedHex);
    }

    @Test
    void headerMapSerDeSer2() throws CborException {
        HeaderMap hm = new HeaderMap()
                .keyId(getBytes(7, 7))
                .contentType(-9);

        COSESignature coseSignature = COSESignature.builder()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap(hm))
                        .unprotected(hm)
                ).signature(getBytes(87, 74))
                .build();


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

        DataItem hmDI = headerMap.serialize();
        String serializedHex1 = HexUtil.encodeHexString(CborSerializationUtil.serialize(hmDI, false));
        String expectedHex = "a90100028238a5781b647366647366383335336a6835202066736466642125262325336a036c636f6e74656e742d747970650458202222222222222222222222222222222222222222222222222222222222222222055061616161616161616161616161616161064d0505050505050505050505050507834ca20328044707070707070707a20328044707070707070707584a5757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757575757716920616d206120737472696e67206b65796d616c736f206120737472696e67258203f6";

        //De-serialize
        HeaderMap desHM = HeaderMap.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serializedHex1)).get(0));
        //Serialize again
        String serializedHex2 = HexUtil.encodeHexString(CborSerializationUtil.serialize(desHM.serialize(), false));

        assertThat(serializedHex1).isEqualTo(expectedHex);
        assertThat(serializedHex2).isEqualTo(expectedHex);
    }

    byte[] getBytes(int b, int noOf) {
        byte[] result = new byte[noOf];

        for (int i = 0; i < noOf; i++) {
            result[i] = (byte) b;
        }

        return result;
    }

}
