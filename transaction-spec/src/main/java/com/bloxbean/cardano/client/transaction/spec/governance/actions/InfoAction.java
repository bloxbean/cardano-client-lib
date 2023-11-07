package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class InfoAction implements GovAction {
    private final GovActionType type = GovActionType.INFO_ACTION;

    @Override
    public DataItem serialize() {
        return new UnsignedInteger(6);
    }

}
