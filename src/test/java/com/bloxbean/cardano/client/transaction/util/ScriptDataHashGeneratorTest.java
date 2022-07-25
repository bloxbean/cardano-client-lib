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

    //This is Alonzo cost model. Update later //TODO
    private final static long[] plutusV1Costs = new long[]
            {197209, 0, 1, 1, 396231, 621, 0, 1, 150000, 1000, 0, 1, 150000, 32,
                    2477736, 29175, 4, 29773, 100, 29773, 100, 29773, 100, 29773, 100, 29773,
                    100, 29773, 100, 100, 100, 29773, 100, 150000, 32, 150000, 32, 150000, 32,
                    150000, 1000, 0, 1, 150000, 32, 150000, 1000, 0, 8, 148000, 425507, 118,
                    0, 1, 1, 150000, 1000, 0, 8, 150000, 112536, 247, 1, 150000, 10000, 1,
                    136542, 1326, 1, 1000, 150000, 1000, 1, 150000, 32, 150000, 32, 150000,
                    32, 1, 1, 150000, 1, 150000, 4, 103599, 248, 1, 103599, 248, 1, 145276,
                    1366, 1, 179690, 497, 1, 150000, 32, 150000, 32, 150000, 32, 150000, 32,
                    150000, 32, 150000, 32, 148000, 425507, 118, 0, 1, 1, 61516, 11218, 0, 1,
                    150000, 32, 148000, 425507, 118, 0, 1, 1, 148000, 425507, 118, 0, 1, 1,
                    2477736, 29175, 4, 0, 82363, 4, 150000, 5000, 0, 1, 150000, 32, 197209, 0,
                    1, 1, 150000, 32, 150000, 32, 150000, 32, 150000, 32, 150000, 32, 150000,
                    32, 150000, 32, 3345831, 1, 1};
    private final CostModel costModel = new CostModel(Language.PLUTUS_V1, plutusV1Costs);

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

        byte[] hashBytes = ScriptDataHashGenerator.generate(Arrays.asList(redeemer), Arrays.asList(plutusData), CostModelUtil.getLanguageViewsEncoding(costModel));
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("57240d358f8ab6128c4a66340271e4fec39b4971232add308f01a5809313adcf");
    }
}
