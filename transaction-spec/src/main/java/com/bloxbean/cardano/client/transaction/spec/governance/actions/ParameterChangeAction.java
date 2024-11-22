package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import lombok.*;

import java.util.List;
import java.util.Objects;

/**
 * parameter_change_action = (0, gov_action_id / null, protocol_param_update, policy_hash / null)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParameterChangeAction implements GovAction {
    private final GovActionType type = GovActionType.PARAMETER_CHANGE_ACTION;

    private GovActionId prevGovActionId;
    private ProtocolParamUpdate protocolParamUpdate;

    private byte[] policyHash;

    @Override
    @SneakyThrows
    public Array serialize(Era era) {
        Objects.requireNonNull(protocolParamUpdate);

        Array array = new Array();
        array.add(new UnsignedInteger(0));
        if (prevGovActionId != null)
            array.add(prevGovActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        array.add(protocolParamUpdate.serialize());

        if (policyHash != null && policyHash.length > 0) {
            if (policyHash.length != 28)
                throw new IllegalArgumentException("Policy hash length should be 28 bytes");
            array.add(new ByteString(policyHash));
        } else
            array.add(SimpleValue.NULL);

        return array;
    }

    @SneakyThrows
    public static GovAction deserialize(Array govActionArray) {
        List<DataItem> govActionDIList = govActionArray.getDataItems();
        DataItem actionIdDI = govActionDIList.get(1);

        GovActionId govActionId = GovAction.getGovActionId(actionIdDI); //handles both govActionId and null
        ProtocolParamUpdate protocolParamUpdate = ProtocolParamUpdate.deserialize(govActionDIList.get(2));

        var policyHashDI = govActionDIList.get(3); //policy hash
        byte[] policyHash = null;
        if (policyHashDI != SimpleValue.NULL) {
            policyHash = ((ByteString) policyHashDI).getBytes();
        }

        return new ParameterChangeAction(govActionId, protocolParamUpdate, policyHash);
    }
}
