package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.ProtocolVersion;
import com.bloxbean.cardano.client.transaction.raw.GovActionKind;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private GovActionId prevGovActionId;
    private ProtocolVersion protocolVersion;

    @Override
    public Array serialize(Era era) {
        Objects.requireNonNull(protocolVersion);

        Array array = new Array();
        array.add(new UnsignedInteger(1));

        if (prevGovActionId != null)
            array.add(prevGovActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        array.add(protocolVersion.serialize());
        return array;
    }

    public static HardForkInitiationAction deserialize(Array govActionArray) {
        return from(RawGovAction.lenient(GovActionKind.HARD_FORK_INITIATION, RawModels.span(govActionArray),
                RawModels.READ_IN));
    }

    /**
     * @param raw a hard fork initiation as received
     * @return the model
     */
    static HardForkInitiationAction from(RawGovAction raw) {
        return new HardForkInitiationAction(GovAction.previous(raw.previousActionId()),
                ProtocolVersion.from(raw.protocolVersion()));
    }
}
