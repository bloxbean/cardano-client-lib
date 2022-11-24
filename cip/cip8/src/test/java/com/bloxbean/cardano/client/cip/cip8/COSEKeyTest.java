package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class COSEKeyTest extends COSEBaseTest {

    @Test
    void serDesCOSEKey() {
        COSEKey coseKey = new COSEKey()
                .keyType("key type 1")
                .keyId(new byte[]{1, 2, 5, 10, 20, 40, 50})
                .algorithmId(-10)
                .keyOp("dfdsfds")
                .keyOp(-130)
                .baseInitVector(getBytes(0, 128));

        String expectedHex = "a5016a6b65792074797065203102470102050a14283203290482676466647366647338810558800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        deserializationTest(coseKey, expectedHex);
    }

    @Test
    void serDesCOSEKey_otherKey_overlap() {
        COSEKey coseKey = new COSEKey()
                .keyType("key type 1")
                .keyId(new byte[]{1, 2, 5, 10, 20, 40, 50})
                .algorithmId(-10)
                .keyOp("dfdsfds")
                .keyOp(-130)
                .baseInitVector(getBytes(0, 128));

        long kty2 = 352;
        byte[] kid2 = getBytes(7, 23);
        String alg2 = "algorithm 2";
        List<Object> ops2 = new ArrayList<>();
        ops2.add("89583249384");
        byte[] biv2 = new byte[]{10, 0, 5, 9, 50, 100, 30};

        DataItem kty2Value = new UnsignedInteger(352);
        DataItem kid2Value = new ByteString(kid2.clone());
        DataItem alg2Value = new UnicodeString("algorithm 2");
        Array ops2Value = new Array();
        ops2Value.add(new UnicodeString("89583249384"));
        DataItem biv2Value = new ByteString(biv2.clone());

        String expectedHex = "a5016a6b65792074797065203102470102050a14283203290482676466647366647338810558800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        COSEKey deCoseKey = deserializationTest(coseKey, expectedHex);

        assertThat(deCoseKey.keyType()).isEqualTo(coseKey.keyType());
        assertThat(deCoseKey.keyId()).isEqualTo(coseKey.keyId());
        assertThat(deCoseKey.algorithmId()).isEqualTo(coseKey.algorithmId());
        assertThat(deCoseKey.keyOps()).isEqualTo(coseKey.keyOps());
        assertThat(deCoseKey.baseInitVector()).isEqualTo(coseKey.baseInitVector());

        //overwrite some default headers
        coseKey.addOtherHeader(1L, kty2Value);
        coseKey.addOtherHeader(2L, kid2Value);
        coseKey.addOtherHeader(3L, alg2Value);
        coseKey.addOtherHeader(4L, ops2Value);
        coseKey.addOtherHeader(5L, biv2Value);

        coseKey.addOtherHeader("key1", new UnicodeString("key1 value"));
        coseKey.addOtherHeader(-100L, new ByteString(new byte[]{2, 3}));

        deCoseKey = deserializationTest(coseKey, null);

        assertThat(deCoseKey.keyType()).isEqualTo(kty2);
        assertThat(deCoseKey.keyId()).isEqualTo(kid2);
        assertThat(deCoseKey.algorithmId()).isEqualTo(alg2);
        assertThat(deCoseKey.keyOps()).isEqualTo(ops2);
        assertThat(deCoseKey.baseInitVector()).isEqualTo(biv2);
        assertThat(deCoseKey.otherHeaders().get("key1")).isEqualTo(new UnicodeString("key1 value"));
        assertThat(deCoseKey.otherHeaders().get(-100L)).isEqualTo(new ByteString(new byte[]{2, 3}));
    }

    private COSEKey deserializationTest(COSEKey coseKey, String expectedHex) {
        byte[] serializeByte1 = coseKey.serializeAsBytes();
        byte[] serializeByte2;
        COSEKey deCOSEKey;
        try {
            deCOSEKey = COSEKey.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = deCOSEKey.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return deCOSEKey;
    }
}
