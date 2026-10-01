package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Special;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.serializers.ListDataJsonDeserializer;
import com.bloxbean.cardano.client.plutus.spec.serializers.ListDataJsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonSerialize(using = ListDataJsonSerializer.class)
@JsonDeserialize(using = ListDataJsonDeserializer.class)
public class ListPlutusData implements PlutusData {

    @Builder.Default
    private List<PlutusData> plutusDataList = new ArrayList<>();

    @Builder.Default
    private boolean isChunked = true;

    public static ListPlutusData of(PlutusData... plutusDataList) {
        ListPlutusData listPlutusData = new ListPlutusData();
        Arrays.stream(plutusDataList).forEach(plutusData -> listPlutusData.add(plutusData));

        return listPlutusData;
    }

    public static ListPlutusData deserialize(Array arrayDI) throws CborDeserializationException {
        if (arrayDI == null)
            return null;
        return PlutusDataCodec.decodeList(arrayDI);
    }

    public void add(PlutusData plutusData) {
        if (plutusDataList == null)
            plutusDataList = new ArrayList<>();

        plutusDataList.add(plutusData);
    }

    @Override
    public DataItem serialize() throws CborSerializationException {
        return PlutusDataCodec.encode(this);
    }

    /**
     * Value equality, computed without recursion so that data of any nesting depth can be compared.
     */
    @Override
    public boolean equals(Object o) {
        return PlutusDataCodec.equal(this, o);
    }

    @Override
    public int hashCode() {
        return PlutusDataCodec.hash(this);
    }
}
