package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import lombok.*;

import java.util.List;
import java.util.Objects;

/**
 * parameter_change_action = (0, gov_action_id / null, protocol_param_update)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParameterChangeAction implements GovAction {
    private final GovActionType type = GovActionType.PARAMETER_CHANGE_ACTION;

    private GovActionId govActionId;
    private ProtocolParamUpdate protocolParamUpdate;

    @Override
    @SneakyThrows
    public Array serialize() {
        Objects.requireNonNull(protocolParamUpdate);

        Array array = new Array();
        array.add(new UnsignedInteger(0));
        if (govActionId != null)
            array.add(govActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        array.add(protocolParamUpdate.serialize());

        return array;
    }

    @SneakyThrows
    public static GovAction deserialize(Array govActionArray) {
        List<DataItem> govActionDIList = govActionArray.getDataItems();
        DataItem actionIdDI = govActionDIList.get(1);

        GovActionId govActionId = GovAction.getGovActionId(actionIdDI); //handles both govActionId and null
        ProtocolParamUpdate protocolParamUpdate = ProtocolParamUpdate.deserialize(govActionDIList.get(2));

        return new ParameterChangeAction(govActionId, protocolParamUpdate);
    }
}
