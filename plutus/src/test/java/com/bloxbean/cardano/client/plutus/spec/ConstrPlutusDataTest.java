package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ConstrPlutusDataTest {

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_lessThan_6() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1280))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(2)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(123);
        assertThat(deConstrPlutusData.getAlternative()).isEqualTo(2);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(1280);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_6() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1280))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(6)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(127);
        assertThat(deConstrPlutusData.getAlternative()).isEqualTo(6);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(1280);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_7() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(5555))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(7)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(1280);
        assertThat(deConstrPlutusData.getAlternative()).isEqualTo(7);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(5555);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_10() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(5555))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(10)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(1283);
        assertThat(deConstrPlutusData.getAlternative()).isEqualTo(10);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(5555);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_127() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(5555))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(127)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(1400);
        assertThat(deConstrPlutusData.getAlternative()).isEqualTo(127);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(5555);
    }

    @Test
    void serializeDeserialize_whenGeneral() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1280))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(8900)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(102);

        assertThat(deConstrPlutusData.getAlternative()).isEqualTo(8900);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(1280);
    }

    @Test
    void verifyDatumHash() throws CborException, CborSerializationException {
        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.builder()
                        .plutusDataList(new ArrayList<>())
                        .build())
                .build();

        String datumHash = constrPlutusData.getDatumHash();

        assertThat(datumHash).isEqualTo("923918e403bf43c34b4ef6b48eb2ee04babed17320d8d1b9ff9ad086e86f44ec");
    }

    @Test
    void serializedContr() throws CborSerializationException, CborException, CborDeserializationException {
        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.builder()
                        .plutusDataList(Arrays.asList(BytesPlutusData.builder()
                                .value("Hello World!".getBytes())
                                .build()))
                        .build())
                .build();

        DataItem di = constrPlutusData.serialize();
        byte[] serBytes = CborSerializationUtil.serialize(di);

        String expected = "d8799f4c48656c6c6f20576f726c6421ff";

        assertThat(HexUtil.encodeHexString(serBytes)).isEqualTo(expected);

        DataItem deDI = CborDecoder.decode(serBytes).get(0);
        ConstrPlutusData deConstData = ConstrPlutusData.deserialize(deDI);

        assertThat(deConstData.getAlternative()).isEqualTo(0);
        assertThat(deConstData.getData().getPlutusDataList().size()).isEqualTo(1);
    }

    @Test
    void unitPlutusDataSerialization() {
        PlutusData plutusData = PlutusData.unit();
        String hex = plutusData.serializeToHex();

        assertThat(hex).isNotNull();
        assertThat(((ConstrPlutusData)plutusData).getData().getPlutusDataList()).hasSize(0);
    }

    @Test
    void bigIntAsByteString() throws Exception {
        String datumCbor = "d8798b581c217a7bf7a6494a0165ae2cb77aabeadb865d33d5be5f9f57b9b0e8f6581c276ba8bb7fe700d0d21b7da11edeb650729efe0b1595af0e643e31844b4e656f6e50756e6b3033311a3b9aca005563687447785a454339663038514d504b6970773073d87980d87980c2410040c241005820dee0b6f2b41b1a75244ec7b83c77c45d5cda5ad4ff3a4964340bb6089d5256e5";
        byte[] datumBytes = HexUtil.decodeHexString(datumCbor);

        var constrPlutusData = ConstrPlutusData.deserialize(CborSerializationUtil.deserialize(datumBytes));

//      assertThat(constrPlutusData.getDatumHash()).isEqualTo("d350694acac39f6951f3e4ce2344d321d0cb1b3fa398759a72250b3f0732e16f");

        assertThat(constrPlutusData.getData().getPlutusDataList().get(7)).isInstanceOf(BigIntPlutusData.class);
        assertThat(constrPlutusData.getData().getPlutusDataList().get(8)).isInstanceOf(BytesPlutusData.class);
        assertThat(constrPlutusData.getData().getPlutusDataList().get(9)).isInstanceOf(BigIntPlutusData.class);
        assertThat(constrPlutusData.getData().getPlutusDataList().get(10)).isInstanceOf(BytesPlutusData.class);
    }

    @Test
    void bigIntAsByteString_withNegativeValues() throws Exception {
        var constrData = ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.builder()
                        .plutusDataList(Arrays.asList(
                                BigIntPlutusData.of(new BigInteger("18446744073709551615")),
                                BigIntPlutusData.of(new BigInteger("18446744073709551616")),
                                BigIntPlutusData.of(new BigInteger("-18446744073709551618")),
                                BigIntPlutusData.of(new BigInteger("100100000000200000900000004000000").negate()),
                                BigIntPlutusData.of(new BigInteger("20010000000020000090000000000")),
                                BigIntPlutusData.of(BigInteger.valueOf(Long.MAX_VALUE)),
                                BigIntPlutusData.of(BigInteger.valueOf(Long.MAX_VALUE).negate()),
                                BigIntPlutusData.of(BigInteger.valueOf(Integer.MAX_VALUE)),
                                BigIntPlutusData.of(BigInteger.valueOf(6700))
                        ))
                        .build())
                .build();

        var serializedBytes = constrData.serializeToBytes();

        var deserConstrData = ConstrPlutusData.deserialize(CborSerializationUtil.deserialize(serializedBytes));

        assertThat(deserConstrData.getData().getPlutusDataList().get(0)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(new BigInteger("18446744073709551615"));

        assertThat(deserConstrData.getData().getPlutusDataList().get(1)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(1)).getValue()).isEqualTo(new BigInteger("18446744073709551616"));

        assertThat(deserConstrData.getData().getPlutusDataList().get(2)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(2)).getValue()).isEqualTo(new BigInteger("18446744073709551618").negate());

        assertThat(deserConstrData.getData().getPlutusDataList().get(3)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(3)).getValue()).isEqualTo(new BigInteger("100100000000200000900000004000000").negate());

        assertThat(deserConstrData.getData().getPlutusDataList().get(4)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(4)).getValue()).isEqualTo(new BigInteger("20010000000020000090000000000"));

        assertThat(deserConstrData.getData().getPlutusDataList().get(5)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(5)).getValue()).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE));

        assertThat(deserConstrData.getData().getPlutusDataList().get(6)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(6)).getValue()).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).negate());

        assertThat(deserConstrData.getData().getPlutusDataList().get(7)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(7)).getValue()).isEqualTo(BigInteger.valueOf(Integer.MAX_VALUE));

        assertThat(deserConstrData.getData().getPlutusDataList().get(8)).isInstanceOf(BigIntPlutusData.class);
        assertThat(((BigIntPlutusData)deserConstrData.getData().getPlutusDataList().get(8)).getValue()).isEqualTo(new BigInteger("6700"));

    }

}
