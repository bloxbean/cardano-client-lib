package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.transaction.raw.RawVotingProcedure;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

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
        return from(RawVotingProcedure.lenient(RawModels.span(array)));
    }

    /**
     * @param raw a voting procedure as received
     * @return the model
     */
    public static VotingProcedure from(RawVotingProcedure raw) {
        return new VotingProcedure(Vote.from(raw.vote()), RawModels.anchor(raw.anchor()));
    }
}
