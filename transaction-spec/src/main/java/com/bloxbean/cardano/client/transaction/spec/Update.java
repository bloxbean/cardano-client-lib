package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.definiteItems;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Update {
    private java.util.Map<String, ProtocolParamUpdate> protocolParamUpdates;
    private int epoch;

    public void addProtocolParameterUpdate(String genesisHash, ProtocolParamUpdate protocolParamUpdate) {
        if (protocolParamUpdates == null)
            this.protocolParamUpdates = new HashMap<>();
        protocolParamUpdates.put(genesisHash, protocolParamUpdate);
    }

    public DataItem serialize() throws CborSerializationException {
        Array updateArray = new Array();

        Map protoParamUpdatesMap = new Map();
        for(String genesisHash: protocolParamUpdates.keySet()) {
            ProtocolParamUpdate protocolParam = protocolParamUpdates.get(genesisHash);
            protoParamUpdatesMap.put(new ByteString(HexUtil.decodeHexString(genesisHash)), protocolParam.serialize());
        }
        updateArray.add(protoParamUpdatesMap);

        //epoch
        updateArray.add(new UnsignedInteger(epoch));

        return updateArray;
    }

    public static Update deserialize(DataItem din) throws CborDeserializationException {
        List<DataItem> updateItems = definiteItems((Array) din, "update");
        if (updateItems.size() != 2)
            throw new CborDeserializationException("Invalid number of dataitems for update. Expected 2, found: " + updateItems.size());

        java.util.Map deProtoParamUpdatesMap = new LinkedHashMap<>();
        Map protoParamUpdatesMap = (Map) updateItems.get(0);
        for (DataItem genhashDI: protoParamUpdatesMap.getKeys()) {
            String genesisHash = HexUtil.encodeHexString(((ByteString)genhashDI).getBytes());
            ProtocolParamUpdate protocolParamUpdate = ProtocolParamUpdate.deserialize(protoParamUpdatesMap.get(genhashDI));

            deProtoParamUpdatesMap.put(genesisHash, protocolParamUpdate);
        }

        int epoch = ((UnsignedInteger) updateItems.get(1)).getValue().intValue();

        return new Update(deProtoParamUpdatesMap, epoch);
    }

}
