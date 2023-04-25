package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListPlutusDataTest {

    @Test
    void serializeDeserialize() throws CborSerializationException, CborException, CborDeserializationException {
        ListPlutusData listPlutusData = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1001)),
                        new BigIntPlutusData(BigInteger.valueOf(200)),
                        new BytesPlutusData("hello".getBytes(StandardCharsets.UTF_8))
                )).build();


        byte[] serialize = CborSerializationUtil.serialize(listPlutusData.serialize());

        //deserialize
        List<DataItem> dis = CborDecoder.decode(serialize);
        ListPlutusData deListPlutusData = (ListPlutusData) PlutusData.deserialize(dis.get(0));
        byte[] serialize1 = CborSerializationUtil.serialize(deListPlutusData.serialize());

        assertThat(serialize1).isEqualTo(serialize);
    }

    @Test
    void serializeDeserialize_whenIsChunked_False() throws CborSerializationException, CborException, CborDeserializationException {
        ListPlutusData listPlutusData = ListPlutusData.builder()
                .plutusDataList(Arrays.asList(
                        new BigIntPlutusData(BigInteger.valueOf(1001)),
                        new BigIntPlutusData(BigInteger.valueOf(200)),
                        new BytesPlutusData("hello".getBytes(StandardCharsets.UTF_8))
                ))
                .isChunked(false)
                .build();


        byte[] serialize = CborSerializationUtil.serialize(listPlutusData.serialize());

        //deserialize
        List<DataItem> dis = CborDecoder.decode(serialize);
        ListPlutusData deListPlutusData = (ListPlutusData) PlutusData.deserialize(dis.get(0));
        byte[] serialize1 = CborSerializationUtil.serialize(deListPlutusData.serialize());

        assertThat(serialize1).isEqualTo(serialize);
    }
}
