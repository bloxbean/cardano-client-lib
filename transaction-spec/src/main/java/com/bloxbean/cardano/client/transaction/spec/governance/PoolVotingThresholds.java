package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.UnitInterval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRationalArray;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toUnitIntervals;


/**
 * pool_voting_thresholds = [5 unit_interval].
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoolVotingThresholds {
    private static final int SIZE = 5;

    private UnitInterval motionNoConfidence;
    private UnitInterval committeeNormal;
    private UnitInterval committeeNoConfidence;
    private UnitInterval hardForkInitiation;
    private UnitInterval ppSecurityGroup;

    public DataItem serialize() throws CborSerializationException {
        return toRationalArray(motionNoConfidence, committeeNormal, committeeNoConfidence, hardForkInitiation,
                ppSecurityGroup);
    }

    public static PoolVotingThresholds deserialize(Array array) {
        List<UnitInterval> items = toUnitIntervals(array, "pool_voting_thresholds", SIZE);
        return new PoolVotingThresholds(items.get(0), items.get(1), items.get(2), items.get(3),
                items.get(4));
    }
}
