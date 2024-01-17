package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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
        Array actionIdArray = (Array) di;
        if (actionIdArray != null && actionIdArray.getDataItems().size() != 2)
            throw new IllegalArgumentException("Invalid gov_action_id array. Expected 2 items. Found : "
                    + actionIdArray.getDataItems().size());

        List<DataItem> diList = actionIdArray.getDataItems();
        String txId = HexUtil.encodeHexString(((ByteString) diList.get(0)).getBytes());
        int govActionIndex = ((UnsignedInteger) diList.get(1)).getValue().intValue();

        return new GovActionId(txId, govActionIndex);
    }
}
