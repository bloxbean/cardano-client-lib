package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CostMdlsTest {

    @Test
    void getLanguageViewEncoding() {
        long[] costs = new long[]
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

        CostModel plutusV1CostModel = new CostModel(Language.PLUTUS_V1, costs);
        CostMdls costMdls = new CostMdls();
        costMdls.add(plutusV1CostModel);
        byte[] languageView = costMdls.getLanguageViewEncoding();

        String expectedHex = "a141005901d59f1a000302590001011a00060bc719026d00011a000249f01903e800011a000249f018201a0025cea81971f70419744d186419744d186419744d186419744d186419744d186419744d18641864186419744d18641a000249f018201a000249f018201a000249f018201a000249f01903e800011a000249f018201a000249f01903e800081a000242201a00067e2318760001011a000249f01903e800081a000249f01a0001b79818f7011a000249f0192710011a0002155e19052e011903e81a000249f01903e8011a000249f018201a000249f018201a000249f0182001011a000249f0011a000249f0041a000194af18f8011a000194af18f8011a0002377c190556011a0002bdea1901f1011a000249f018201a000249f018201a000249f018201a000249f018201a000249f018201a000249f018201a000242201a00067e23187600010119f04c192bd200011a000249f018201a000242201a00067e2318760001011a000242201a00067e2318760001011a0025cea81971f704001a000141bb041a000249f019138800011a000249f018201a000302590001011a000249f018201a000249f018201a000249f018201a000249f018201a000249f018201a000249f018201a000249f018201a00330da70101ff";

        assertThat(HexUtil.encodeHexString(languageView)).isEqualTo(expectedHex);
    }

    @Test
    void serDeser_keysShouldBeOrdered() throws Exception {
        CostMdls costMdls = new CostMdls();
        CostModel costModel1 = new CostModel(Language.PLUTUS_V1, new long[]{1, 2, 3});
        CostModel costModel2 = new CostModel(Language.PLUTUS_V2, new long[]{4, 5, 6});
        CostModel costModel3 = new CostModel(Language.PLUTUS_V3, new long[]{7, 8, 9});

        costMdls.add(costModel3);
        costMdls.add(costModel1);
        costMdls.add(costModel2);

        var serializedMap = costMdls.serialize();
        var serializedBytes = CborSerializationUtil.serialize(serializedMap);

        //deserialize
        var deserializedMap = (Map) CborSerializationUtil.deserialize(serializedBytes);
        var deserializedCostMdls = CostMdls.deserialize(deserializedMap);

        assertThat(deserializedCostMdls.get(Language.PLUTUS_V1).getCosts()).isEqualTo(new long[]{1, 2, 3});
        assertThat(deserializedCostMdls.get(Language.PLUTUS_V2).getCosts()).isEqualTo(new long[]{4, 5, 6});
        assertThat(deserializedCostMdls.get(Language.PLUTUS_V3).getCosts()).isEqualTo(new long[]{7, 8, 9});

        //Check order
        var keysDI = new ArrayList<>(deserializedMap.getKeys());
        assertThat(keysDI.get(0)).isEqualTo(new UnsignedInteger(0));
        assertThat(keysDI.get(1)).isEqualTo(new UnsignedInteger(1));
        assertThat(keysDI.get(2)).isEqualTo(new UnsignedInteger(2));
    }
}
