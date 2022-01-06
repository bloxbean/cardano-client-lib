package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
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

}
