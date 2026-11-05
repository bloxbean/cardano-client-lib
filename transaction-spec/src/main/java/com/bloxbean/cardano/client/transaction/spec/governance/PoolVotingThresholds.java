package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.raw.RawVotingThresholds;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.bloxbean.cardano.client.transaction.raw.RawVotingThresholds.Pool;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRationalArray;


/**
 * pool_voting_thresholds = [5 unit_interval].
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoolVotingThresholds {
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
        return from(RawVotingThresholds.pool(RawModels.span(array), true));
    }

    /**
     * @param raw the thresholds as received
     * @return the model
     */
    public static PoolVotingThresholds from(RawVotingThresholds raw) {
        return new PoolVotingThresholds(raw.get(Pool.MOTION_NO_CONFIDENCE),
                raw.get(Pool.COMMITTEE_NORMAL),
                raw.get(Pool.COMMITTEE_NO_CONFIDENCE),
                raw.get(Pool.HARD_FORK_INITIATION),
                raw.get(Pool.SECURITY_GROUP));
    }
}
