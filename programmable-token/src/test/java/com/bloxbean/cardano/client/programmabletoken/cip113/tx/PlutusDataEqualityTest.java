package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlutusDataEqualityTest {
    @Test
    void treatsDefiniteAndIndefiniteCborContainersAsTheSameData() throws Exception {
        PlutusData structured = ConstrPlutusData.of(0,
                BigIntPlutusData.of(BigInteger.ONE),
                BigIntPlutusData.of(BigInteger.valueOf(42)));
        PlutusData fromHex = PlutusData.deserialize(HexUtil.decodeHexString("d8798201182a"));

        assertThat(structured).isNotEqualTo(fromHex);
        assertThat(PlutusDataEquality.equals(structured, fromHex)).isTrue();
        assertThat(PlutusDataEquality.equals(structured,
                ConstrPlutusData.of(0, BigIntPlutusData.of(1), BigIntPlutusData.of(43))))
                .isFalse();
    }
}
