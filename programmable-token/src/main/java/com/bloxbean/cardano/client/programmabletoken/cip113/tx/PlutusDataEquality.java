package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.fasterxml.jackson.core.JsonProcessingException;

/** Semantic equality for Plutus Data that ignores equivalent CBOR container encodings. */
final class PlutusDataEquality {
    private PlutusDataEquality() { }

    static boolean equals(PlutusData left, PlutusData right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        try {
            // PlutusData implementations include CBOR's definite/indefinite container flag in
            // equals(), although that flag is not part of the decoded Plutus Data value. The
            // detailed-schema JSON form represents the value itself and is independent of that
            // wire-level choice.
            return PlutusDataJsonConverter.toJson(left)
                    .equals(PlutusDataJsonConverter.toJson(right));
        } catch (JsonProcessingException e) {
            throw new Cip113Exception("Could not compare Plutus Data values", e);
        }
    }
}
