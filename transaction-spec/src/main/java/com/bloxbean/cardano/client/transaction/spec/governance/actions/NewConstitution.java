package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.governance.Constitution;
import com.bloxbean.cardano.client.transaction.raw.GovActionKind;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * new_constitution = (5, gov_action_id / null, constitution)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NewConstitution implements GovAction {
    private final GovActionType type = GovActionType.NEW_CONSTITUTION;

    private GovActionId prevGovActionId;
    private Constitution constitution;

    @Override
    public Array serialize(Era era) {
        Objects.requireNonNull(constitution);

        Array array = new Array();
        array.add(new UnsignedInteger(5));

        if (prevGovActionId != null)
            array.add(prevGovActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        array.add(constitution.serialize());

        return array;
    }

    public static NewConstitution deserialize(Array govActionArray) {
        return from(RawGovAction.lenient(GovActionKind.NEW_CONSTITUTION, RawModels.span(govActionArray),
                RawModels.READ_IN));
    }

    /**
     * @param raw a new constitution as received
     * @return the model
     */
    static NewConstitution from(RawGovAction raw) {
        return new NewConstitution(GovAction.previous(raw.previousActionId()), Constitution.from(raw.constitution()));
    }

}
