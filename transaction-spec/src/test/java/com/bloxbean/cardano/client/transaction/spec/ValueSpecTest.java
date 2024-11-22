package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ValueSpecTest {

    @Test
    void addTwoLovelacesValues() {
        Value lovelaceValue1 = new Value();
        lovelaceValue1.setCoin(BigInteger.valueOf(1000000L));

        Value lovelaceValue2 = new Value();
        lovelaceValue2.setCoin(BigInteger.valueOf(1500000L));

        Value actualValue = lovelaceValue1.add(lovelaceValue2);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(2500000L));
        expectedValue.setMultiAssets(Arrays.asList());

        assertThat(actualValue).isEqualTo(expectedValue);
    }

    @Test
    void addCoin() {
        Value value1 = Value.builder()
                .coin(BigInteger.valueOf(1000))
                .build();

        var value2 = value1.addCoin(BigInteger.valueOf(5000));

        assertThat(value2.getCoin()).isEqualTo(BigInteger.valueOf(6000));
        assertThat(value1.getCoin()).isEqualTo(BigInteger.valueOf(1000));
    }

    @Test
    void addMultiAssetToLovelacesValues() {
        Value lovelaceValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(Arrays.asList()).build();

        List<MultiAsset> testMultiAssets = Arrays.asList(MultiAsset.builder().policyId("policy_ud").assets(Arrays.asList(Asset.builder().name("asset_name").value(BigInteger.valueOf(123456L)).build())).build());

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets).build();

        Value actualValue = lovelaceValue.add(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));
        expectedValue.setMultiAssets(testMultiAssets);

        assertThat(actualValue).isEqualTo(expectedValue);
    }

    @Test
    void addDifferentMultiAssetsToLovelacesValues() {
        List<MultiAsset> testMultiAssets1 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        List<MultiAsset> testMultiAssets2 = Arrays.asList(MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build())).build());

        Value lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        Value actualValue = lovelaceAndMultiAssetValue.add(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));

        expectedValue.setMultiAssets(MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2));

        assertThat(actualValue).isEqualTo(expectedValue);
    }


    @Test
    void addSamePolicyMultiAssetsToLovelacesValues() {
        List<MultiAsset> testMultiAssets1 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        List<MultiAsset> testMultiAssets2 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build())).build());

        Value lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        Value actualValue = lovelaceAndMultiAssetValue.add(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));

        List<MultiAsset> testMultiAssets = MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2);
        expectedValue.setMultiAssets(testMultiAssets);

        assertThat(actualValue).isEqualTo(expectedValue);
    }

    @Test
    void addSameMultiAssetsToLovelacesValues() {
        List<MultiAsset> testMultiAssets1 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        List<MultiAsset> testMultiAssets2 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(2000000L)).build())).build());

        Value lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        Value actualValue = lovelaceAndMultiAssetValue.add(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));
        expectedValue.setMultiAssets(MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2));

        assertThat(actualValue).isEqualTo(expectedValue);
    }

    @Test
    void subtractValuesWithMultiAssetsList() {
        Asset l1asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset l1asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset l1moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(100L)).build();

        MultiAsset l1multiAsset1 = MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(l1asset1, l1asset2)).build();
        MultiAsset l1multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(l1asset1, l1moreAsset2)).build();
        List<MultiAsset> multiAssetList1 = Arrays.asList(l1multiAsset1, l1multiAsset2);

        Value value1 = Value.builder().coin(BigInteger.valueOf(3000000L)).multiAssets(multiAssetList1).build();

        Asset l2asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(60L)).build();
        Asset l2asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(150L)).build();
        Asset l2moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(50L)).build();

        MultiAsset l2multiAsset1 = MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(l2asset1, l2asset2)).build();
        MultiAsset l2multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(l2asset1, l2moreAsset2)).build();
        List<MultiAsset> multiAssetList2 = Arrays.asList(l2multiAsset1, l2multiAsset2);

        Value value2 = Value.builder().coin(BigInteger.valueOf(2000000L)).multiAssets(multiAssetList2).build();

        List<MultiAsset> expectedMultiAssetList = Arrays.asList(l1multiAsset1.subtract(l2multiAsset1), l1multiAsset2.subtract(l2multiAsset2));
        Value expectedValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(expectedMultiAssetList).build();

        assertThat(MultiAsset.subtractMultiAssetLists(multiAssetList1, multiAssetList2)).isEqualTo(expectedMultiAssetList);
        assertThat(value1.subtract(value2)).isEqualTo(expectedValue);
    }

    @Test
    void subtractValuesWithMultiAssetsListWhenFirstListHashExtraAssets() {
        Asset l1asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset l1asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(300L)).build();
        Asset l1Asset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(500L)).build();
        Asset l1Asset4 = Asset.builder().name("asset4").value(BigInteger.valueOf(550L)).build();

        MultiAsset l1multiAsset1 = MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(l1asset1, l1asset2)).build();
        MultiAsset l1multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(l1asset1, l1Asset3)).build();
        MultiAsset l1multiAsset3 = MultiAsset.builder().policyId("policy_id3").assets(Arrays.asList(l1Asset4)).build();
        List<MultiAsset> multiAssetList1 = Arrays.asList(l1multiAsset1, l1multiAsset2, l1multiAsset3);

        Value value1 = Value.builder().coin(BigInteger.valueOf(3000000L)).multiAssets(multiAssetList1).build();

        Asset l2asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(50L)).build();
        Asset l2asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset l2moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(50L)).build();
        MultiAsset l2multiAsset1 = MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(l2asset1, l2asset2)).build();
        MultiAsset l2multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(l2asset1, l2moreAsset2)).build();
        List<MultiAsset> multiAssetList2 = Arrays.asList(l2multiAsset1, l2multiAsset2);

        Value value2 = Value.builder().coin(BigInteger.valueOf(2000000L)).multiAssets(multiAssetList2).build();

        List<MultiAsset> expectedMultiAssetList = Arrays.asList(l1multiAsset1.subtract(l2multiAsset1), l1multiAsset2.subtract(l2multiAsset2), l1multiAsset3);
        Value expectedValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(expectedMultiAssetList).build();

        assertThat(MultiAsset.subtractMultiAssetLists(multiAssetList1, multiAssetList2)).isEqualTo(expectedMultiAssetList);
        assertThat(value1.subtract(value2)).isEqualTo(expectedValue);
    }

    @Test
    void toMapTest() {
        List<MultiAsset> testMultiAssetst = Arrays.asList(
                MultiAsset.builder().policyId("policy_id1").assets(
                        Arrays.asList(
                                Asset.builder().name("asset_name1").value(BigInteger.valueOf(5000000L)).build(),
                                Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build(),
                                Asset.builder().name("asset_name3").value(BigInteger.valueOf(3000000L)).build()
                        )
                ).build(),
                MultiAsset.builder().policyId("policy_id2").assets(
                        Arrays.asList(
                                Asset.builder().name("my_asset_name1").value(BigInteger.valueOf(111)).build(),
                                Asset.builder().name("asset_name2").value(BigInteger.valueOf(333)).build()
                        )
                ).build(),
                MultiAsset.builder().policyId("policy_id3").assets(
                        Arrays.asList(
                                Asset.builder().name("abc").value(BigInteger.valueOf(555)).build()
                        )
                ).build()
        );

        Value value = new Value(BigInteger.valueOf(1000), testMultiAssetst);

        Map<String, HashMap<String, BigInteger>> map = value.toMap();

        //Expected map
        Map<String, HashMap<String, BigInteger>> expected = new HashMap<>();
        expected.put("policy_id1", new HashMap<String, BigInteger>() {{
            put("asset_name1", BigInteger.valueOf(5000000L));
            put("asset_name2", BigInteger.valueOf(2000000L));
            put("asset_name3", BigInteger.valueOf(3000000L));
        }});
        expected.put("policy_id2", new HashMap<String, BigInteger>() {{
            put("my_asset_name1", BigInteger.valueOf(111));
            put("asset_name2", BigInteger.valueOf(333));
        }});
        expected.put("policy_id3", new HashMap<String, BigInteger>() {{
            put("abc", BigInteger.valueOf(555));
        }});

        assertThat(map).isEqualTo(expected);
    }

    @Test
    void subtractWhenNoAssetsLeft() {
        Asset l1asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(1)).build();
        Asset l1asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(1)).build();
        Asset l1Asset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(1)).build();
        Asset l1Asset4 = Asset.builder().name("asset4").value(BigInteger.valueOf(1)).build();

        MultiAsset multiAsset = MultiAsset.builder()
                .policyId("policy-1")
                .assets(List.of(l1asset1, l1asset2, l1Asset3, l1Asset4)).build();


        Value value = new Value(BigInteger.valueOf(100), List.of(multiAsset));
        Value valueToSubstract = new Value(BigInteger.valueOf(20), List.of(multiAsset));

        Value result = value.subtract(valueToSubstract);

        assertThat(result.getCoin()).isEqualTo(BigInteger.valueOf(80));
        assertThat(result.getMultiAssets()).isEmpty();
    }

    @Test
    void subtractWithAssetWithZeroValueInResult_shouldBeRemoved() {
        Asset l1asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(5)).build();
        Asset l1asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(1)).build();
        Asset l1Asset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(10)).build();
        Asset l1Asset4 = Asset.builder().name("asset4").value(BigInteger.valueOf(20)).build();

        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId("policy-1")
                .assets(List.of(l1asset1, l1asset2, l1Asset3, l1Asset4)).build();


        Asset l2asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(2)).build();
        Asset l2asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(1)).build();
        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId("policy-1")
                .assets(List.of(l2asset1, l2asset2)).build();


        Value value1 = new Value(BigInteger.valueOf(100), List.of(multiAsset1));
        Value valueToSubstract = new Value(BigInteger.valueOf(20), List.of(multiAsset2));

        Value result = value1.subtract(valueToSubstract);

        Value expectedValue = Value.builder()
                .coin(BigInteger.valueOf(80))
                .multiAssets(List.of(MultiAsset.builder()
                        .policyId("policy-1")
                        .assets(List.of(
                                        new Asset("asset1", BigInteger.valueOf(3)),
                                        new Asset("asset3", BigInteger.valueOf(10)),
                                        new Asset("asset4", BigInteger.valueOf(20))
                                )
                        ).build()
                )).build();

        assertThat(result).isEqualTo(expectedValue);
    }

    @Test
    void deserializationTest() throws CborException {
        Value expected = new Value();
        expected.setCoin(BigInteger.valueOf(133402997L));
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId("1774343241680e4daef7cbfe3536fc857ce23fb66cd0b66320b2e3dd")
                .assets(List.of(Asset.builder().name("0x4249534f4e").value(BigInteger.valueOf(5000000L)).build()))
                .build();
        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId("1a71dc14baa0b4fcfb34464adc6656d0e562571e2ac1bc990c9ce5f6")
                .assets(List.of(Asset.builder().name("0x574f4c46").value(BigInteger.valueOf(5555555555L)).build()))
                .build();
        MultiAsset multiAsset3 = MultiAsset.builder()
                .policyId("2afb448ef716bfbed1dcb676102194c3009bee5399e93b90def9db6a")
                .assets(List.of(Asset.builder().name("0x4249534f4e").value(BigInteger.valueOf(5000000L)).build()))
                .build();
        MultiAsset multiAsset4 = MultiAsset.builder()
                .policyId("2d7444cf9e317a12e3eb72bf424fd2a0c8fbafedf10e20bfdb4ad8ab")
                .assets(List.of(Asset.builder().name("0x434845444441").value(BigInteger.valueOf(100000)).build()))
                .build();
        MultiAsset multiAsset5 = MultiAsset.builder()
                .policyId("4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd")
                .assets(List.of(Asset.builder().name("0x546f6b68756e").value(BigInteger.valueOf(2)).build()))
                .build();
        MultiAsset multiAsset6 = MultiAsset.builder()
                .policyId("5029eeccd52fef299509d509a8318fd7930c3dffcce1f9f39ff11ef9")
                .assets(List.of(Asset.builder().name("0x464743").value(BigInteger.valueOf(50)).build()))
                .build();
        MultiAsset multiAsset7 = MultiAsset.builder()
                .policyId("544571c086d0e5c5022aca9717dd0f438e21190abb48f37b3ae129f0")
                .assets(List.of(Asset.builder().name("0x47524f57").value(BigInteger.valueOf(3)).build()))
                .build();
        MultiAsset multiAsset8 = MultiAsset.builder()
                .policyId("547ceed647f57e64dc40a29b16be4f36b0d38b5aa3cd7afb286fc094")
                .assets(List.of(Asset.builder().name("0x6262486f736b79").value(BigInteger.valueOf(500)).build()))
                .build();
        MultiAsset multiAsset9 = MultiAsset.builder()
                .policyId("8d0ae3c5b13b47907b16511a540d47436d12dcc96453c0f59089b451")
                .assets(List.of(Asset.builder().name("0x42524f4f4d").value(BigInteger.valueOf(37972049)).build()))
                .build();
        MultiAsset multiAsset10 = MultiAsset.builder()
                .policyId("9668ef339ea4b29a29b7a500b1a1f6769568ddb623cc463f95fe07f2")
                .assets(List.of(Asset.builder().name("0x4d75736963476c6f626554776f").value(BigInteger.valueOf(2)).build()))
                .build();
        MultiAsset multiAsset11 = MultiAsset.builder()
                .policyId("98dc68b04026544619a251bc01aad2075d28433524ac36cbc75599a1")
                .assets(List.of(Asset.builder().name("0x686f736b").value(BigInteger.valueOf(100)).build()))
                .build();
        MultiAsset multiAsset12 = MultiAsset.builder()
                .policyId("a0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c235")
                .assets(List.of(Asset.builder().name("0x484f534b59").value(BigInteger.valueOf(3000000)).build()))
                .build();
        MultiAsset multiAsset13 = MultiAsset.builder()
                .policyId("af2e27f580f7f08e93190a81f72462f153026d06450924726645891b")
                .assets(List.of(Asset.builder().name("0x44524950").value(BigInteger.valueOf(3000000000L)).build()))
                .build();
        MultiAsset multiAsset14 = MultiAsset.builder()
                .policyId("afc910d7a306d20c12903979d4935ae4307241d03245743548e76783")
                .assets(List.of(Asset.builder().name("0x4153484942").value(BigInteger.valueOf(1000000000)).build()))
                .build();
        MultiAsset multiAsset15 = MultiAsset.builder()
                .policyId("b0446f1c9105f0cc5bb6bd092f5c3e523e13f8a999b31c870298fa40")
                .assets(List.of(Asset.builder().name("0x51554944").value(BigInteger.valueOf(3)).build()))
                .build();
        MultiAsset multiAsset16 = MultiAsset.builder()
                .policyId("b788fbee71a32d2efc5ee7d151f3917d99160f78fb1e41a1bbf80d8f")
                .assets(List.of(Asset.builder().name("0x4c454146544f4b454e").value(BigInteger.valueOf(9232173891L)).build()))
                .build();
        MultiAsset multiAsset17 = MultiAsset.builder()
                .policyId("b84c0133554a0c098ebaded08fa55790873ae6b6b5febad154678bb9")
                .assets(List.of(Asset.builder().name("0x466f7274756e654e616d695765616c7468396f663130").value(BigInteger.valueOf(1)).build()))
                .build();
        MultiAsset multiAsset18 = MultiAsset.builder()
                .policyId("d030b626219d81673bd32932d2245e0c71ae5193281f971022b23a78")
                .assets(List.of(Asset.builder().name("0x436172646f67656f").value(BigInteger.valueOf(840)).build()))
                .build();
        MultiAsset multiAsset19 = MultiAsset.builder()
                .policyId("d1333653aa3ac24adfa9c6d09c1a2cc8e2b7b86ad334c17f2acb8647")
                .assets(List.of(Asset.builder().name("0x42696f546f6b656e").value(BigInteger.valueOf(2)).build()))
                .build();
        MultiAsset multiAsset20 = MultiAsset.builder()
                .policyId("d894897411707efa755a76deb66d26dfd50593f2e70863e1661e98a0")
                .assets(List.of(Asset.builder().name("0x7370616365636f696e73").value(BigInteger.valueOf(9)).build()))
                .build();
        MultiAsset multiAsset21 = MultiAsset.builder()
                .policyId("ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9")
                .assets(List.of(Asset.builder().name("0x506f6c795065657237353436").value(BigInteger.valueOf(1)).build(),
                        Asset.builder().name("0x506f6c795065657239373236").value(BigInteger.valueOf(1)).build())).build();
        expected.setMultiAssets(List.of(multiAsset1, multiAsset2, multiAsset3, multiAsset4, multiAsset5, multiAsset6, multiAsset7, multiAsset8, multiAsset9, multiAsset10,
                multiAsset11, multiAsset12, multiAsset13, multiAsset14, multiAsset15, multiAsset16, multiAsset17, multiAsset18, multiAsset19, multiAsset20, multiAsset21));
        String cbor = "821a07f39175b5581c1774343241680e4daef7cbfe3536fc857ce23fb66cd0b66320b2e3dda1454249534f4e1a004c4b40581c1a71dc14baa0b4fcfb34464adc6656d0e562571e2ac1bc990c9ce5f6a144574f4c461b000000014b230ce3581c2afb448ef716bfbed1dcb676102194c3009bee5399e93b90def9db6aa1454249534f4e1a004c4b40581c2d7444cf9e317a12e3eb72bf424fd2a0c8fbafedf10e20bfdb4ad8aba1464348454444411a000186a0581c4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bda146546f6b68756e02581c5029eeccd52fef299509d509a8318fd7930c3dffcce1f9f39ff11ef9a1434647431832581c544571c086d0e5c5022aca9717dd0f438e21190abb48f37b3ae129f0a14447524f5703581c547ceed647f57e64dc40a29b16be4f36b0d38b5aa3cd7afb286fc094a1476262486f736b791901f4581c8d0ae3c5b13b47907b16511a540d47436d12dcc96453c0f59089b451a14542524f4f4d1a02436851581c9668ef339ea4b29a29b7a500b1a1f6769568ddb623cc463f95fe07f2a14d4d75736963476c6f626554776f02581c98dc68b04026544619a251bc01aad2075d28433524ac36cbc75599a1a144686f736b1864581ca0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c235a145484f534b591a002dc6c0581caf2e27f580f7f08e93190a81f72462f153026d06450924726645891ba144445249501ab2d05e00581cafc910d7a306d20c12903979d4935ae4307241d03245743548e76783a14541534849421a3b9aca00581cb0446f1c9105f0cc5bb6bd092f5c3e523e13f8a999b31c870298fa40a1445155494403581cb788fbee71a32d2efc5ee7d151f3917d99160f78fb1e41a1bbf80d8fa1494c454146544f4b454e1b000000022647cb43581cb84c0133554a0c098ebaded08fa55790873ae6b6b5febad154678bb9a156466f7274756e654e616d695765616c7468396f66313001581cd030b626219d81673bd32932d2245e0c71ae5193281f971022b23a78a148436172646f67656f190348581cd1333653aa3ac24adfa9c6d09c1a2cc8e2b7b86ad334c17f2acb8647a14842696f546f6b656e02581cd894897411707efa755a76deb66d26dfd50593f2e70863e1661e98a0a14a7370616365636f696e7309581cef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9a24c506f6c795065657237353436014c506f6c79506565723937323601";
        Assertions.assertEquals(expected, Value.deserialize(CborDecoder.decode(HexUtil.decodeHexString(cbor)).get(0)));
    }

    @Test
    void serializeDeserializeTest() throws CborException {
        Value value = new Value();
        value.setCoin(BigInteger.valueOf(133402997L));
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId("1774343241680e4daef7cbfe3536fc857ce23fb66cd0b66320b2e3dd")
                .assets(List.of(Asset.builder().name("0x4249534f4e").value(BigInteger.valueOf(5000000L)).build()))
                .build();
        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId("1a71dc14baa0b4fcfb34464adc6656d0e562571e2ac1bc990c9ce5f6")
                .assets(List.of(Asset.builder().name("0x574f4c46").value(BigInteger.valueOf(5555555555L)).build()))
                .build();
        MultiAsset multiAsset3 = MultiAsset.builder()
                .policyId("2afb448ef716bfbed1dcb676102194c3009bee5399e93b90def9db6a")
                .assets(List.of(Asset.builder().name("0x4249534f4e").value(BigInteger.valueOf(5000000L)).build()))
                .build();
        MultiAsset multiAsset4 = MultiAsset.builder()
                .policyId("2d7444cf9e317a12e3eb72bf424fd2a0c8fbafedf10e20bfdb4ad8ab")
                .assets(List.of(Asset.builder().name("0x434845444441").value(BigInteger.valueOf(100000)).build()))
                .build();
        MultiAsset multiAsset5 = MultiAsset.builder()
                .policyId("4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd")
                .assets(List.of(Asset.builder().name("0x546f6b68756e").value(BigInteger.valueOf(2)).build()))
                .build();
        MultiAsset multiAsset6 = MultiAsset.builder()
                .policyId("5029eeccd52fef299509d509a8318fd7930c3dffcce1f9f39ff11ef9")
                .assets(List.of(Asset.builder().name("0x464743").value(BigInteger.valueOf(50)).build()))
                .build();
        MultiAsset multiAsset7 = MultiAsset.builder()
                .policyId("544571c086d0e5c5022aca9717dd0f438e21190abb48f37b3ae129f0")
                .assets(List.of(Asset.builder().name("0x47524f57").value(BigInteger.valueOf(3)).build()))
                .build();
        MultiAsset multiAsset8 = MultiAsset.builder()
                .policyId("547ceed647f57e64dc40a29b16be4f36b0d38b5aa3cd7afb286fc094")
                .assets(List.of(Asset.builder().name("0x6262486f736b79").value(BigInteger.valueOf(500)).build()))
                .build();
        MultiAsset multiAsset9 = MultiAsset.builder()
                .policyId("8d0ae3c5b13b47907b16511a540d47436d12dcc96453c0f59089b451")
                .assets(List.of(Asset.builder().name("0x42524f4f4d").value(BigInteger.valueOf(37972049)).build()))
                .build();
        MultiAsset multiAsset10 = MultiAsset.builder()
                .policyId("9668ef339ea4b29a29b7a500b1a1f6769568ddb623cc463f95fe07f2")
                .assets(List.of(Asset.builder().name("0x4d75736963476c6f626554776f").value(BigInteger.valueOf(2)).build()))
                .build();
        MultiAsset multiAsset11 = MultiAsset.builder()
                .policyId("98dc68b04026544619a251bc01aad2075d28433524ac36cbc75599a1")
                .assets(List.of(Asset.builder().name("0x686f736b").value(BigInteger.valueOf(100)).build()))
                .build();
        MultiAsset multiAsset12 = MultiAsset.builder()
                .policyId("a0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c235")
                .assets(List.of(Asset.builder().name("0x484f534b59").value(BigInteger.valueOf(3000000)).build()))
                .build();
        MultiAsset multiAsset13 = MultiAsset.builder()
                .policyId("af2e27f580f7f08e93190a81f72462f153026d06450924726645891b")
                .assets(List.of(Asset.builder().name("0x44524950").value(BigInteger.valueOf(3000000000L)).build()))
                .build();
        MultiAsset multiAsset14 = MultiAsset.builder()
                .policyId("afc910d7a306d20c12903979d4935ae4307241d03245743548e76783")
                .assets(List.of(Asset.builder().name("0x4153484942").value(BigInteger.valueOf(1000000000)).build()))
                .build();
        MultiAsset multiAsset15 = MultiAsset.builder()
                .policyId("b0446f1c9105f0cc5bb6bd092f5c3e523e13f8a999b31c870298fa40")
                .assets(List.of(Asset.builder().name("0x51554944").value(BigInteger.valueOf(3)).build()))
                .build();
        MultiAsset multiAsset16 = MultiAsset.builder()
                .policyId("b788fbee71a32d2efc5ee7d151f3917d99160f78fb1e41a1bbf80d8f")
                .assets(List.of(Asset.builder().name("0x4c454146544f4b454e").value(BigInteger.valueOf(9232173891L)).build()))
                .build();
        MultiAsset multiAsset17 = MultiAsset.builder()
                .policyId("b84c0133554a0c098ebaded08fa55790873ae6b6b5febad154678bb9")
                .assets(List.of(Asset.builder().name("0x466f7274756e654e616d695765616c7468396f663130").value(BigInteger.valueOf(1)).build()))
                .build();
        MultiAsset multiAsset18 = MultiAsset.builder()
                .policyId("d030b626219d81673bd32932d2245e0c71ae5193281f971022b23a78")
                .assets(List.of(Asset.builder().name("0x436172646f67656f").value(BigInteger.valueOf(840)).build()))
                .build();
        MultiAsset multiAsset19 = MultiAsset.builder()
                .policyId("d1333653aa3ac24adfa9c6d09c1a2cc8e2b7b86ad334c17f2acb8647")
                .assets(List.of(Asset.builder().name("0x42696f546f6b656e").value(BigInteger.valueOf(2)).build()))
                .build();
        MultiAsset multiAsset20 = MultiAsset.builder()
                .policyId("d894897411707efa755a76deb66d26dfd50593f2e70863e1661e98a0")
                .assets(List.of(Asset.builder().name("0x7370616365636f696e73").value(BigInteger.valueOf(9)).build()))
                .build();
        MultiAsset multiAsset21 = MultiAsset.builder()
                .policyId("ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9")
                .assets(List.of(Asset.builder().name("0x506f6c795065657237353436").value(BigInteger.valueOf(1)).build(),
                        Asset.builder().name("0x506f6c795065657239373236").value(BigInteger.valueOf(1)).build())).build();
        value.setMultiAssets(List.of(multiAsset1, multiAsset2, multiAsset3, multiAsset4, multiAsset5, multiAsset6, multiAsset7, multiAsset8, multiAsset9, multiAsset10,
                multiAsset11, multiAsset12, multiAsset13, multiAsset14, multiAsset15, multiAsset16, multiAsset17, multiAsset18, multiAsset19, multiAsset20, multiAsset21));
        String expected = "821a07f39175b5581c1774343241680e4daef7cbfe3536fc857ce23fb66cd0b66320b2e3dda1454249534f4e1a004c4b40581c1a71dc14baa0b4fcfb34464adc6656d0e562571e2ac1bc990c9ce5f6a144574f4c461b000000014b230ce3581c2afb448ef716bfbed1dcb676102194c3009bee5399e93b90def9db6aa1454249534f4e1a004c4b40581c2d7444cf9e317a12e3eb72bf424fd2a0c8fbafedf10e20bfdb4ad8aba1464348454444411a000186a0581c4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bda146546f6b68756e02581c5029eeccd52fef299509d509a8318fd7930c3dffcce1f9f39ff11ef9a1434647431832581c544571c086d0e5c5022aca9717dd0f438e21190abb48f37b3ae129f0a14447524f5703581c547ceed647f57e64dc40a29b16be4f36b0d38b5aa3cd7afb286fc094a1476262486f736b791901f4581c8d0ae3c5b13b47907b16511a540d47436d12dcc96453c0f59089b451a14542524f4f4d1a02436851581c9668ef339ea4b29a29b7a500b1a1f6769568ddb623cc463f95fe07f2a14d4d75736963476c6f626554776f02581c98dc68b04026544619a251bc01aad2075d28433524ac36cbc75599a1a144686f736b1864581ca0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c235a145484f534b591a002dc6c0581caf2e27f580f7f08e93190a81f72462f153026d06450924726645891ba144445249501ab2d05e00581cafc910d7a306d20c12903979d4935ae4307241d03245743548e76783a14541534849421a3b9aca00581cb0446f1c9105f0cc5bb6bd092f5c3e523e13f8a999b31c870298fa40a1445155494403581cb788fbee71a32d2efc5ee7d151f3917d99160f78fb1e41a1bbf80d8fa1494c454146544f4b454e1b000000022647cb43581cb84c0133554a0c098ebaded08fa55790873ae6b6b5febad154678bb9a156466f7274756e654e616d695765616c7468396f66313001581cd030b626219d81673bd32932d2245e0c71ae5193281f971022b23a78a148436172646f67656f190348581cd1333653aa3ac24adfa9c6d09c1a2cc8e2b7b86ad334c17f2acb8647a14842696f546f6b656e02581cd894897411707efa755a76deb66d26dfd50593f2e70863e1661e98a0a14a7370616365636f696e7309581cef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9a24c506f6c795065657237353436014c506f6c79506565723937323601";
        String actual = HexUtil.encodeHexString(CborSerializationUtil.serialize(value.serialize()));
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(value, Value.deserialize(CborDecoder.decode(HexUtil.decodeHexString(actual)).get(0)));
    }

    @Test
    void addLovelace() {
        Value expectedValue = Value.builder().coin(BigInteger.valueOf(110_000_000L)).build();
        Value actualValue = Value.fromCoin(BigInteger.valueOf(100_000_000L))
                .addCoin(BigInteger.valueOf(10_000_000L));
        Assertions.assertEquals(expectedValue, actualValue);
    }


     @Test
     void addLovelaceWithToken() {

         String policyId = "ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9";
         Asset asset = Asset.builder().name("0x506f6c795065657237353436").value(BigInteger.valueOf(100_000_000L)).build();
         List<Asset> assets = new ArrayList<>();
         assets.add(asset);

         Value expectedValue = Value.builder()
                 .coin(BigInteger.valueOf(110_000_000L))
                 .multiAssets(List.of(MultiAsset.builder()
                         .policyId(policyId)
                         .assets(assets)
                         .build()))
                 .build();

         Value actualValue = Value.builder()
                 .coin(BigInteger.valueOf(100_000_000L))
                 .multiAssets(List.of(MultiAsset.builder()
                         .policyId(policyId)
                         .assets(assets)
                         .build()))
                 .build()
                 .addCoin(BigInteger.valueOf(10_000_000L));
         Assertions.assertEquals(expectedValue, actualValue);

    }


    @Test
    void addSingleToken() {
        String policyId = "ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9";
        BigInteger hundredMil = BigInteger.valueOf(100_000_000L);
        Value value = Value.builder().coin(BigInteger.valueOf(10_000_000L)).build();
        Value value1 = value.add(policyId, "0x506f6c795065657237353436", hundredMil);
        String assetName = new String(HexUtil.decodeHexString("506f6c795065657237353436"));
        Value actual = value1.add(policyId, assetName, hundredMil);

        Value expected = value
                .toBuilder()
                .multiAssets(List.of(MultiAsset.builder()
                        .policyId(policyId)
                        .assets(List.of(Asset.builder().name("0x506f6c795065657237353436").value(BigInteger.valueOf(200_000_000L)).build()))
                        .build()))
                .build();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void subtractLovelace() {
        Value expectedValue = Value.builder().coin(BigInteger.valueOf(90_000_000L)).build();
        Value actualValue = Value.fromCoin(BigInteger.valueOf(100_000_000L))
                .substractCoin(BigInteger.valueOf(10_000_000L));
        Assertions.assertEquals(expectedValue, actualValue);
    }

    @Test
    void subtractLovelaceWithTokens() {
        String policyId = "ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9";
        Asset asset = Asset.builder().name("0x506f6c795065657237353436").value(BigInteger.valueOf(100_000_000L)).build();
        List<Asset> assets = new ArrayList<>();
        assets.add(asset);

        Value expectedValue = Value.builder()
                .coin(BigInteger.valueOf(90_000_000L))
                .multiAssets(List.of(MultiAsset.builder()
                        .policyId(policyId)
                        .assets(assets)
                        .build()))
                .build();

        Value actualValue = Value.builder()
                .coin(BigInteger.valueOf(100_000_000L))
                .multiAssets(List.of(MultiAsset.builder()
                        .policyId(policyId)
                        .assets(assets)
                        .build()))
                .build()
                .substractCoin(BigInteger.valueOf(10_000_000L));
        Assertions.assertEquals(expectedValue, actualValue);
    }


    @Test
    void subtractSingleToken() {
        String policyId = "ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9";
        String assetNameHex = "0x506f6c795065657237353436";
        String assetName = new String(HexUtil.decodeHexString(assetNameHex));
        Value actual = Value.builder()
                .coin(BigInteger.valueOf(10_000_000L))
                .multiAssets(List.of(
                        MultiAsset.builder()
                                .policyId(policyId)
                                .assets(List.of(Asset.builder()
                                        .name(assetNameHex)
                                        .value(BigInteger.valueOf(300_000_000L))
                                        .build()))
                                .build()
                ))
                .build();
        actual = actual.subtract(policyId, assetNameHex, BigInteger.valueOf(100_000_000L));
        actual = actual.subtract(policyId, assetName, BigInteger.valueOf(100_000_000L));

        Value expected = Value.builder()
                .coin(BigInteger.valueOf(10_000_000L))
                .multiAssets(List.of(
                        MultiAsset.builder()
                                .policyId(policyId)
                                .assets(List.of(Asset.builder()
                                        .name(assetNameHex)
                                        .value(BigInteger.valueOf(100_000_000L))
                                        .build()))
                                .build()
                ))
                .build();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void amountOfExistingTokenIsCorrect() {
        String policyId = "ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9";
        BigInteger hundredMil = BigInteger.valueOf(100_000_000L);
        Value value = Value.builder().coin(BigInteger.valueOf(10_000_000L)).build();
        value = value.add(policyId, "0x506f6c795065657237353436", hundredMil);
        String assetName = new String(HexUtil.decodeHexString("506f6c795065657237353436"));
        value = value.add(policyId, assetName, hundredMil);
        BigInteger actual = value.amountOf(policyId, "0x506f6c795065657237353436");
        Assertions.assertEquals(BigInteger.valueOf(200_000_000L), actual);
    }

    @Test
    void amountOfMissingTokenIsZero() {
        String policyId = "ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9";
        BigInteger hundredMil = BigInteger.valueOf(100_000_000L);
        Value value = Value.builder().coin(BigInteger.valueOf(10_000_000L)).build();
        value = value.add(policyId, "0x506f6c795065657237353436", hundredMil);
        String assetName = new String(HexUtil.decodeHexString("506f6c795065657237353436"));
        value = value.add(policyId, assetName, hundredMil);
        BigInteger actual = value.amountOf("4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd", "0x546f6b68756e");
        Assertions.assertEquals(BigInteger.ZERO, actual);
    }

    @Test
    void isZero1() {
        Assertions.assertTrue(Value.builder().build().isZero());
    }

    @Test
    void isZero2() {
        String policyId = "ef76f6f0b3558ea0aaad6af5c9a5f3e5bf20b393314de747662e8ce9";
        String assetNameHex = "0x506f6c795065657237353436";
        Value value = Value.builder()
                .coin(BigInteger.valueOf(10_000_000L))
                .multiAssets(List.of(
                        MultiAsset.builder()
                                .policyId(policyId)
                                .assets(List.of(Asset.builder()
                                        .name(assetNameHex)
                                        .value(BigInteger.valueOf(100_000_000L))
                                        .build()))
                                .build()
                ))
                .build();
        Assertions.assertTrue(value.subtract(value).isZero());
    }

    @Test
    void isPositiveAdaOnly() {
        Assertions.assertTrue(Value.builder().build().isPositive());
    }

    @ParameterizedTest
    @CsvSource({
            "0,true",
            "1000000,true",
            "-1000000,false"
    })
    void isPositiveAdaOnlyParametric(String amount, boolean outcome) {
        Assertions.assertEquals(Value.builder().coin(BigInteger.valueOf(Long.parseLong(amount))).build().isPositive(), outcome);
    }

    @ParameterizedTest
    @CsvSource({
            "0,4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd,0x546f6b68756e,1000000,true",
            "0,4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd,0x546f6b68756e,-1000000,false",
            "-1000000,4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd,0x546f6b68756e,1000000,false",
            "-1000000,4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd,0x546f6b68756e,-1000000,false",
            "1000000,4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd,0x546f6b68756e,0,true",
    })
    void isPositiveParametric(String lovelace, String policyId, String assetName, String tokenAmount, boolean outcome) {
        Value value = Value.builder().coin(BigInteger.valueOf(Long.parseLong(lovelace))).build();
        value = value.add(policyId, assetName, BigInteger.valueOf(Long.parseLong(tokenAmount)));
        Assertions.assertEquals(value.isPositive(), outcome);
    }


}
