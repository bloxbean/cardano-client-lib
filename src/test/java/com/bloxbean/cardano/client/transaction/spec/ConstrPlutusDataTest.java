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
}
