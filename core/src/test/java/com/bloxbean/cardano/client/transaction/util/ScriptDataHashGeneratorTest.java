package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.plutus.util.ScriptDataHashGenerator;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static com.bloxbean.cardano.client.api.util.CostModelUtil.PlutusV1CostModel;
import static com.bloxbean.cardano.client.api.util.CostModelUtil.PlutusV2CostModel;
import static org.assertj.core.api.Assertions.assertThat;

//TODO -- Move this test to spec module later
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

    private CostMdls costMdls = new CostMdls();

    @BeforeEach
    void setup() {
        costMdls.add(costModel);
    }

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

        byte[] hashBytes = ScriptDataHashGenerator.generate(Era.Babbage, Arrays.asList(redeemer), Arrays.asList(plutusData), costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("57240d358f8ab6128c4a66340271e4fec39b4971232add308f01a5809313adcf");
    }

    @Test
    void generate_plutusV1_noEraSet() throws CborException, CborSerializationException {
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

        byte[] hashBytes = ScriptDataHashGenerator.generate(Arrays.asList(redeemer), Arrays.asList(plutusData), costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("57240d358f8ab6128c4a66340271e4fec39b4971232add308f01a5809313adcf");
    }

    @Test
    void generate_plutusV1_eraSet_shouldNotUseBabbageEra() throws CborException, CborSerializationException { //negative test
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

        byte[] hashBytes = ScriptDataHashGenerator.generate(Era.Conway, Arrays.asList(redeemer), Arrays.asList(plutusData), costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isNotEqualTo("57240d358f8ab6128c4a66340271e4fec39b4971232add308f01a5809313adcf");
    }

    @Test
    void generate_plutusV2() throws CborException, CborSerializationException {
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

        var _costMdls = new CostMdls();
        _costMdls.add(PlutusV2CostModel);

        byte[] hashBytes = ScriptDataHashGenerator.generate(Arrays.asList(redeemer), Arrays.asList(plutusData), _costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("83d39add124e06e9cf8c4fee28c8b8063932c4bdfc3d4900fb08f49beffef601");
    }

    @Test
    void generate_plutusV2_eraSetToBabbage() throws CborException, CborSerializationException { //negative test
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

        var _costMdls = new CostMdls();
        _costMdls.add(PlutusV2CostModel);

        byte[] hashBytes = ScriptDataHashGenerator.generate(Era.Babbage, Arrays.asList(redeemer), Arrays.asList(plutusData), _costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isNotEqualTo("83d39add124e06e9cf8c4fee28c8b8063932c4bdfc3d4900fb08f49beffef601");
    }

    @Test
    void generate_plutusV2_eraSetToConway() throws CborException, CborSerializationException {
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

        var _costMdls = new CostMdls();
        _costMdls.add(PlutusV2CostModel);

        byte[] hashBytes = ScriptDataHashGenerator.generate(Era.Conway, Arrays.asList(redeemer), Arrays.asList(plutusData), _costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("83d39add124e06e9cf8c4fee28c8b8063932c4bdfc3d4900fb08f49beffef601");
    }

    @Test
    void generate_plutusV2_emptyRedeemer() throws CborException, CborSerializationException {
        ListPlutusData listPlutusData = new ListPlutusData();
        PlutusData plutusData = new BigIntPlutusData(new BigInteger("1000"));
        listPlutusData.add(plutusData);

        var _costMdls = new CostMdls();
        _costMdls.add(PlutusV2CostModel);

        byte[] hashBytes = ScriptDataHashGenerator.generate(Era.Conway, Arrays.asList(), Arrays.asList(plutusData), _costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("62d142288edfbd6b5bceec2bd476a22e6856c0904fcf26116fc4dd54440c038e");
    }

    @Test
    void generate_plutusV1_emptyRedeemer() throws CborException, CborSerializationException {
        ListPlutusData listPlutusData = new ListPlutusData();
        PlutusData plutusData = new BigIntPlutusData(new BigInteger("1000"));
        listPlutusData.add(plutusData);

        var _costMdls = new CostMdls();
        _costMdls.add(PlutusV1CostModel);

        byte[] hashBytes = ScriptDataHashGenerator.generate(Arrays.asList(), Arrays.asList(plutusData), _costMdls);
        String hash = HexUtil.encodeHexString(hashBytes);
        System.out.println(hash);

        assertThat(hash).isEqualTo("d6e05995f657a2bf1a3dd246756733f9e680487df08c5d958617e5eec70b9fc2");
    }

    @Test
    void generate_emptyRedemeers_nonEmptyDatum() throws Exception {
        PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString("d8799f4114d8799fd8799fd8799fd8799f581c3050f6f4d5981748bc3a2b84d8165b20c100a75057b6593befd9323cffd8799fd8799fd8799f581cc5cdc99429b4ce659f2542994c48b6c801f0b8e21ca7fb586326a545ffffffffd87a80ffd87a80ff1a002625a0d8799fd879801a0025a559d8799f01ffffff"));

        byte[] scriptDataHash = ScriptDataHashGenerator.generate(Era.Babbage, Arrays.asList(), Arrays.asList(plutusData),
                costMdls);

        byte[] expected = new byte[]{71, -22, -92, 74, -39, 124, 55, -108, 120, -127, -125, 119, 41, -77, 48, -72, 121, 0, -10, -77, -29, 103, -99, -9, -111, -118, 11, -126, -52, -29, -81, 105};
        assertThat(scriptDataHash).isEqualTo(expected);
    }

    @Test
    void generate_nullRedemeers_nonEmptyDatum() throws Exception {
        PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString("d8799f4114d8799fd8799fd8799fd8799f581c3050f6f4d5981748bc3a2b84d8165b20c100a75057b6593befd9323cffd8799fd8799fd8799f581cc5cdc99429b4ce659f2542994c48b6c801f0b8e21ca7fb586326a545ffffffffd87a80ffd87a80ff1a002625a0d8799fd879801a0025a559d8799f01ffffff"));

        byte[] scriptDataHash = ScriptDataHashGenerator.generate(Era.Babbage, null, Arrays.asList(plutusData),
                costMdls);

        byte[] expected = new byte[]{71, -22, -92, 74, -39, 124, 55, -108, 120, -127, -125, 119, 41, -77, 48, -72, 121, 0, -10, -77, -29, 103, -99, -9, -111, -118, 11, -126, -52, -29, -81, 105};
        assertThat(scriptDataHash).isEqualTo(expected);
    }
}
