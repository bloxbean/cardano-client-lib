package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.raw.RawProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.raw.RawUpdate;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.HashMap;
import java.util.LinkedHashMap;

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
        return from(RawUpdate.lenient(RawModels.span(din), RawModels.READ_IN));
    }

    /**
     * @param raw an update as received
     * @return the model: a genesis hash that equals an earlier one keeps that one's place and takes the later update
     * @throws CborDeserializationException if a parameter update is not what its model accepts
     * @throws CborRuntimeException         if the epoch does not fit an int
     */
    public static Update from(RawUpdate raw) throws CborDeserializationException {
        java.util.Map<String, ProtocolParamUpdate> updates = new LinkedHashMap<>();
        for (java.util.Map.Entry<byte[], RawProtocolParamUpdate> proposal : raw.proposals())
            updates.put(HexUtil.encodeHexString(proposal.getKey()), ProtocolParamUpdate.from(proposal.getValue()));
        long epoch = raw.epoch();
        if (epoch < 0 || epoch > Integer.MAX_VALUE)
            throw new CborRuntimeException("Expected an epoch that fits an int, found : "
                    + Long.toUnsignedString(epoch));
        return new Update(updates, (int) epoch);
    }

}
