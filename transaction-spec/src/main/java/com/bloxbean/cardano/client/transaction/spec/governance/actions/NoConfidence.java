package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.GovActionKind;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;


/**
 * no_confidence = (3, gov_action_id / null)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoConfidence implements GovAction {
    private final GovActionType type = GovActionType.NO_CONFIDENCE;

    private GovActionId prevGovActionId;

    @Override
    public Array serialize(Era era) {
        Array array = new Array();
        array.add(new UnsignedInteger(3));

        if (prevGovActionId != null)
            array.add(prevGovActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static GovAction deserialize(Array govActionArray) {
        return from(RawGovAction.lenient(GovActionKind.NO_CONFIDENCE, RawModels.span(govActionArray),
                RawModels.READ_IN));
    }

    /**
     * @param raw a motion of no confidence as received
     * @return the model
     */
    static NoConfidence from(RawGovAction raw) {
        return new NoConfidence(GovAction.previous(raw.previousActionId()));
    }
}
