package com.bloxbean.cardano.client.transaction.spec.governance;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.raw.RawVotingThresholds;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.bloxbean.cardano.client.transaction.raw.RawVotingThresholds.DRep;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRationalArray;


/**
 * drep_voting_thresholds = [10 unit_interval].
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DRepVotingThresholds {
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
        return from(RawVotingThresholds.drep(CborSerializationUtil.span(array), true));
    }

    /**
     * @param raw the thresholds as received
     * @return the model
     */
    public static DRepVotingThresholds from(RawVotingThresholds raw) {
        return new DRepVotingThresholds(raw.get(DRep.MOTION_NO_CONFIDENCE),
                raw.get(DRep.COMMITTEE_NORMAL),
                raw.get(DRep.COMMITTEE_NO_CONFIDENCE),
                raw.get(DRep.UPDATE_CONSTITUTION),
                raw.get(DRep.HARD_FORK_INITIATION),
                raw.get(DRep.NETWORK_GROUP),
                raw.get(DRep.ECONOMIC_GROUP),
                raw.get(DRep.TECHNICAL_GROUP),
                raw.get(DRep.GOVERNANCE_GROUP),
                raw.get(DRep.TREASURY_WITHDRAWAL));
    }
}
