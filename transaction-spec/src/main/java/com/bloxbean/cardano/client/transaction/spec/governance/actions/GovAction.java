package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.raw.RawGovActionId;
import com.bloxbean.cardano.client.transaction.util.RawModels;

import java.util.Optional;

/**
 * gov_action =
 * [ parameter_change_action
 * // hard_fork_initiation_action
 * // treasury_withdrawals_action
 * // no_confidence
 * // update_committee
 * // new_constitution
 * // info_action
 * ]
 */

public interface GovAction {
    GovActionType getType();

    static GovAction deserialize(Array govActionArray) {
        return from(RawGovAction.lenient(CborSerializationUtil.span(govActionArray), RawModels.READ_IN));
    }

    /**
     * @param raw a governance action as received
     * @return the model of its kind
     */
    static GovAction from(RawGovAction raw) {
        switch (raw.kind()) {
            case PARAMETER_CHANGE:
                return ParameterChangeAction.from(raw);
            case HARD_FORK_INITIATION:
                return HardForkInitiationAction.from(raw);
            case TREASURY_WITHDRAWALS:
                return TreasuryWithdrawalsAction.from(raw);
            case NO_CONFIDENCE:
                return NoConfidence.from(raw);
            case UPDATE_COMMITTEE:
                return UpdateCommittee.from(raw);
            case NEW_CONSTITUTION:
                return NewConstitution.from(raw);
            default:
                return new InfoAction();
        }
    }

    /**
     * @param raw the previous action's id as received, or empty
     * @return its model, or null
     */
    static GovActionId previous(Optional<RawGovActionId> raw) {
        return raw.map(GovActionId::from).orElse(null);
    }

    static GovActionId getGovActionId(DataItem actionIdDI) {
        GovActionId govActionId;
        if (actionIdDI == SimpleValue.NULL)
            govActionId = null;
        else {
            govActionId = GovActionId.deserialize(actionIdDI);
        }
        return govActionId;
    }

    default DataItem serialize() {
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    DataItem serialize(Era era);

}
