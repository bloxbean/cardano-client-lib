package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.raw.GovActionKind;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

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

    public static GovAction deserialize(Array govActionArray) {
        return from(RawGovAction.lenient(GovActionKind.PARAMETER_CHANGE, CborSerializationUtil.span(govActionArray),
                RawModels.READ_IN));
    }

    /**
     * @param raw a parameter change as received
     * @return the model
     */
    @SneakyThrows
    static ParameterChangeAction from(RawGovAction raw) {
        return new ParameterChangeAction(GovAction.previous(raw.previousActionId()),
                ProtocolParamUpdate.from(raw.protocolParamUpdate()), raw.policyHash().orElse(null));
    }
}
