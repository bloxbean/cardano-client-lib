package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ConstrPlutusDataTest {

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_123() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1280))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .tag(123)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(123);
        assertThat(deConstrPlutusData.getTag()).isEqualTo(123);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(1280);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_121() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1280))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .tag(121)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(121);
        assertThat(deConstrPlutusData.getTag()).isEqualTo(121);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(1280);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_1280() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(5555))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .tag(1280)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(1280);
        assertThat(deConstrPlutusData.getTag()).isEqualTo(1280);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(5555);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_1283() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(5555))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .tag(1283)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(1283);
        assertThat(deConstrPlutusData.getTag()).isEqualTo(1283);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(5555);
    }

    @Test
    void serializeDeserialize_whenConciseFormWhenAltIs_127() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(5555))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .tag(127)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(127);
        assertThat(deConstrPlutusData.getTag()).isEqualTo(127);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(5555);
    }

    @Test
    void serializeDeserialize_whenGeneral() throws CborSerializationException, CborDeserializationException {
        ListPlutusData plutusDataList = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1280))
                )).build();

        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .tag(8900)
                .data(plutusDataList)
                .build();

        DataItem dataItem = constrPlutusData.serialize();

        ConstrPlutusData deConstrPlutusData = ConstrPlutusData.deserialize(dataItem);

        assertThat(dataItem.getTag().getValue()).isEqualTo(102);

        assertThat(deConstrPlutusData.getTag()).isEqualTo(8900);
        assertThat(((BigIntPlutusData) deConstrPlutusData.getData().getPlutusDataList().get(0)).getValue()).isEqualTo(1280);
    }
}
