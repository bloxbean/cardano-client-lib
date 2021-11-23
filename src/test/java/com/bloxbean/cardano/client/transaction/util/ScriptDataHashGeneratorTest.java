package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptDataHashGeneratorTest {

    @Test
    void generate() throws CborException, CborSerializationException {

        ListPlutusData listPlutusData = new ListPlutusData();
        PlutusData plutusData = new BigIntPlutusData(new BigInteger("1000"));
        listPlutusData.add(plutusData);

        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.valueOf(1))
                .data(new BigIntPlutusData(new BigInteger("2000")))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(0))
                        .steps(BigInteger.valueOf(0))
                        .build()
                ).build();

        byte[] hashBytes = ScriptDataHashGenerator.generate(Arrays.asList(redeemer), Arrays.asList(plutusData), CostModelConstants.LANGUAGE_VIEWS);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("57240d358f8ab6128c4a66340271e4fec39b4971232add308f01a5809313adcf");
    }
}
