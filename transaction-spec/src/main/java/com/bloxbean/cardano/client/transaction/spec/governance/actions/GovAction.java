package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toInt;

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
        DataItem fistItem = govActionArray.getDataItems().get(0);
        int actionType = toInt(fistItem);
        switch (actionType) {
            case 0:
                return ParameterChangeAction.deserialize(govActionArray);
            case 1:
                return HardForkInitiationAction.deserialize(govActionArray);
            case 2:
                return TreasuryWithdrawalsAction.deserialize(govActionArray);
            case 3:
                return NoConfidence.deserialize(govActionArray);
            case 4:
                return UpdateCommittee.deserialize(govActionArray);
            case 5:
                return NewConstitution.deserialize(govActionArray);
            case 6:
                return new InfoAction();
            default:
                throw new IllegalArgumentException("GovAction is not a valid type : " + actionType);
        }
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
