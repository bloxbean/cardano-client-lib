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
 * drep_voting_thresholds = [10 unit_interval].
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DRepVotingThresholds {
    private static final int SIZE = 10;

    private UnitInterval motionNoConfidence;
    private UnitInterval committeeNormal;
    private UnitInterval committeeNoConfidence;
    private UnitInterval updateToConstitution;
    private UnitInterval hardForkInitiation;
    private UnitInterval ppNetworkGroup;
    private UnitInterval ppEconomicGroup;
    private UnitInterval ppTechnicalGroup;
    private UnitInterval ppGovGroup;
    private UnitInterval treasuryWithdrawal;

    public DataItem serialize() throws CborSerializationException {
        return toRationalArray(motionNoConfidence, committeeNormal, committeeNoConfidence, updateToConstitution,
                hardForkInitiation, ppNetworkGroup, ppEconomicGroup, ppTechnicalGroup, ppGovGroup, treasuryWithdrawal);
    }

    public static DRepVotingThresholds deserialize(Array array) {
        List<UnitInterval> items = toUnitIntervals(array, "drep_voting_thresholds", SIZE);
        return new DRepVotingThresholds(items.get(0), items.get(1), items.get(2), items.get(3), items.get(4),
                items.get(5), items.get(6), items.get(7), items.get(8), items.get(9));
    }
}
