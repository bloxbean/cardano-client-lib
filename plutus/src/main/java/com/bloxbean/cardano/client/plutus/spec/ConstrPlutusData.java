package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.serializers.ConstrDataJsonDeserializer;
import com.bloxbean.cardano.client.plutus.spec.serializers.ConstrDataJsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonSerialize(using = ConstrDataJsonSerializer.class)
@JsonDeserialize(using = ConstrDataJsonDeserializer.class)
public class ConstrPlutusData implements PlutusData {
    // see: https://github.com/input-output-hk/plutus/blob/1f31e640e8a258185db01fa899da63f9018c0e85/plutus-core/plutus-core/src/PlutusCore/Data.hs#L61
    // We don't directly serialize the alternative in the tag, instead the scheme is:
    // - Alternatives 0-6 -> tags 121-127, followed by the arguments in a list
    // - Alternatives 7-127 -> tags 1280-1400, followed by the arguments in a list
    // - Any alternatives, including those that don't fit in the above -> tag 102 followed by a list containing
    //   an unsigned integer for the actual alternative, and then the arguments in a (nested!) list.
    private static final long GENERAL_FORM_TAG = 102;
    private long alternative;
    private ListPlutusData data;

    public static ConstrPlutusData of(long alternative, PlutusData... plutusDataList) {
        return ConstrPlutusData.builder()
                .alternative(alternative)
                .data(ListPlutusData.of(plutusDataList))
                .build();
    }

    public static ConstrPlutusData deserialize(DataItem di) throws CborDeserializationException {
        return PlutusDataCodec.decodeConstr(di);
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
        return PlutusDataCodec.valueEquals(this, o);
    }

    @Override
    public int hashCode() {
        return PlutusDataCodec.hash(this);
    }
}
