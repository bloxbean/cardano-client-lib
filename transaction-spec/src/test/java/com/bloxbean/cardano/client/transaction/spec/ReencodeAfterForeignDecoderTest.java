package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDRepCert;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cbor-java's decoder tags its shared null on {@code d818f6}, for the whole JVM. The overloads that re-encode a tree
 * (ADR 0003 D7) must read a tree's nulls as nulls wherever that decoder has run, as Yaci's does.
 */
class ReencodeAfterForeignDecoderTest {
    private static final String KEY_HASH = "581c" + "11".repeat(28);

    @Test
    void aCertificateWithANullFieldIsRead() throws Exception {
        try {
            new CborDecoder(new ByteArrayInputStream(HexUtil.decodeHexString("d818f6"))).decodeNext();
            assertThat(SimpleValue.NULL.hasTag()).isTrue();

            RegDRepCert drep = (RegDRepCert) Certificate.deserialize((Array) CborSerializationUtil.deserialize(
                    HexUtil.decodeHexString("84" + "10" + "8200" + KEY_HASH + "00" + "f6")));
            assertThat(drep.getAnchor()).isNull();

            PoolRegistration pool = (PoolRegistration) Certificate.deserialize(
                    (Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(
                            "8a" + "03" + KEY_HASH + "5820" + "22".repeat(32) + "00" + "00" + "d81e820001"
                                    + "581de1" + "33".repeat(28) + "80" + "80" + "f6")));
            assertThat(pool.getPoolMetadataUrl()).isNull();
        } finally {
            SimpleValue.NULL.removeTag();
        }
    }
}
