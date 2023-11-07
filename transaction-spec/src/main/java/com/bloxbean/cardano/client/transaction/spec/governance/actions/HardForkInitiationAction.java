package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.transaction.spec.ProtocolVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * hard_fork_initiation_action = (1, gov_action_id / null, [protocol_version])
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HardForkInitiationAction implements GovAction {
    private final GovActionType type = GovActionType.HARD_FORK_INITIATION_ACTION;

    private GovActionId govActionId;
    private ProtocolVersion protocolVersion;

    @Override
    public Array serialize() {
        Objects.requireNonNull(protocolVersion);

        Array array = new Array();
        array.add(new UnsignedInteger(1));

        if (govActionId != null)
            array.add(govActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        array.add(protocolVersion.serialize());
        return array;
    }

    public static HardForkInitiationAction deserialize(Array govActionArray) {
        List<DataItem> govActionDIList = govActionArray.getDataItems();

        DataItem actionIdDI = govActionDIList.get(1);
        GovActionId govActionId = GovAction.getGovActionId(actionIdDI);

        ProtocolVersion protocolVersion = ProtocolVersion.deserialize((Array) govActionDIList.get(2));
        return new HardForkInitiationAction(govActionId, protocolVersion);
    }
}
