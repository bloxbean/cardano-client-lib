package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * Conway keeps the cost models of unknown languages (cardano-ledger CostModels' costModelsUnknown, keyed by a
     * Word8); they are read and written back. A sample of cardano-cbor-dataset's Conway corpus, with keys 0, 1, 2
     * and 80 and indefinite cost arrays, whose BREAK was read as an extra cost of 0.
     */
    @Test
    void unknownLanguagesAreKept() throws Exception {
        String hex = "bf009f3b1337822b12e70b24ff0180029f1b30c371e6c48c01fa1b33459fc1bb65755fff1850841b42d548e7ad09f787"
                + "1b5a3ba121004321641b146e2fb171290a143b3489aa05030c8f5cff";
        CostMdls costMdls = CostMdls.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(hex)));

        assertThat(costMdls.get(Language.PLUTUS_V1).getCosts()).containsExactly(-0x1337822b12e70b25L);
        assertThat(costMdls.get(Language.PLUTUS_V2).getCosts()).isEmpty();
        assertThat(costMdls.get(Language.PLUTUS_V3).getCosts()).containsExactly(0x30c371e6c48c01faL, 0x33459fc1bb65755fL);
        assertThat(costMdls.getUnknownCostModels()).containsOnlyKeys(80);
        assertThat(costMdls.getUnknownCostModels().get(80))
                .containsExactly(0x42d548e7ad09f787L, 0x5a3ba12100432164L, 0x146e2fb171290a14L, -0x3489aa05030c8f5dL);

        // written back with definite arrays, in key order
        assertThat(HexUtil.encodeHexString(CborSerializationUtil.serialize(costMdls.serialize())))
                .isEqualTo("a4" + "00813b1337822b12e70b24" + "0180" + "02821b30c371e6c48c01fa1b33459fc1bb65755f"
                        + "1850841b42d548e7ad09f7871b5a3ba121004321641b146e2fb171290a143b3489aa05030c8f5c");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a1190100" + "80",                   // language 256: not a Word8
            "a103" + "81" + "1b8000000000000000", // cost 2^63: not an Int64
            "a103" + "81" + "4100",               // cost not an integer
    })
    void invalidLanguagesAndCostsAreRejected(String hex) {
        assertThatThrownBy(() -> CostMdls.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(hex))))
                .isInstanceOf(CborDeserializationException.class);
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
