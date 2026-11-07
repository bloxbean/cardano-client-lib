package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.transaction.raw.RawGovActionId;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GovActionId {
    private String transactionId;
    private int govActionIndex;

    public DataItem serialize() {
        Objects.requireNonNull(transactionId);

        Array array = new Array();
        array.add(new ByteString(HexUtil.decodeHexString(transactionId)));
        array.add(new UnsignedInteger(govActionIndex));

        return array;
    }

    public static GovActionId deserialize(DataItem di) {
        return from(RawGovActionId.lenient(CborSerializationUtil.span(di)));
    }

    /**
     * @param raw a governance action id as received
     * @return the model
     */
    public static GovActionId from(RawGovActionId raw) {
        return new GovActionId(HexUtil.encodeHexString(raw.transactionId()), raw.index());
    }
}
