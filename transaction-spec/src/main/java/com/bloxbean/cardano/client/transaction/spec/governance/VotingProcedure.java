package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.definiteItems;

/**
 * {@literal
 * voting_procedure =
 *        [ vote, anchor / null]
 * }
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VotingProcedure {
    private Vote vote;
    private Anchor anchor;

    public DataItem serialize() {
        Objects.requireNonNull(vote);

        Array array = new Array();
        array.add(vote.serialize());

        if (anchor != null)
            array.add(anchor.serialize());
        else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static VotingProcedure deserialize(Array array) {
        List<DataItem> diList = definiteItems(array, "voting_procedure");
        if (diList.size() != 2)
            throw new IllegalArgumentException("Invalid voting_procedure array. Expected 2 items. Found : "
                    + diList.size());

        Vote vote = Vote.deserialize((UnsignedInteger) diList.get(0));

        //anchor
        Anchor anchor;
        if (diList.get(1) == SimpleValue.NULL) {
            anchor = null;
        } else {
            anchor = Anchor.deserialize((Array) diList.get(1));
        }

        return new VotingProcedure(vote, anchor);
    }
}
