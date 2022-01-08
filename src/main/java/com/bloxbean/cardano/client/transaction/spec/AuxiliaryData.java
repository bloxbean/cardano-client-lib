package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuxiliaryData {
    private Metadata metadata;

    @Builder.Default
    private List<NativeScript> nativeScripts = new ArrayList<>();

    @Builder.Default
    private List<PlutusScript> plutusScripts = new ArrayList<>();

    public DataItem serialize() throws CborSerializationException {
        return getAuxiliaryData();
    }

    public static AuxiliaryData deserialize(Map map) throws CborDeserializationException {
        Tag mapTag = map.getTag();
        AuxiliaryData auxiliaryData = new AuxiliaryData();

        if (mapTag != null && mapTag.getValue() == 259) { //Alonzo
            DataItem metadataValueDI = map.get(new UnsignedInteger(0));
            DataItem nativeScriptsValueDI = map.get(new UnsignedInteger(1));
            DataItem plutusScriptsValueDI = map.get(new UnsignedInteger(2));

            if (metadataValueDI != null) {
                Metadata cborMetadata = CBORMetadata.deserialize((Map) metadataValueDI);
                auxiliaryData.setMetadata(cborMetadata);
            }

            if (nativeScriptsValueDI != null) {
                Array nativeScriptsArray = (Array) nativeScriptsValueDI;
                for (DataItem nativeScriptDI : nativeScriptsArray.getDataItems()) {
                    NativeScript nativeScript = NativeScript.deserialize((Array) nativeScriptDI);
                    auxiliaryData.getNativeScripts().add(nativeScript);
                }
            }

            if (plutusScriptsValueDI != null) {
                Array plutusScriptsArray = (Array) plutusScriptsValueDI;
                for (DataItem plutusScriptDI : plutusScriptsArray.getDataItems()) {
                    PlutusScript plutusScript = PlutusScript.deserialize((ByteString) plutusScriptDI);
                    auxiliaryData.getPlutusScripts().add(plutusScript);
                }
            }
        } else { //Shelley-mary
            Metadata metadata = CBORMetadata.deserialize(map);
            auxiliaryData.setMetadata(metadata);
        }

        return auxiliaryData;
    }

    @JsonIgnore
    public byte[] getAuxiliaryDataHash() throws MetadataSerializationException {
        try {
            Map map = getAuxiliaryData();
            byte[] encodedBytes = null;

            if (metadata != null && (nativeScripts == null || nativeScripts.size() == 0)
                    && (plutusScripts == null || plutusScripts.size() == 0)) {
                encodedBytes = CborSerializationUtil.serialize(map);
            } else {
                encodedBytes = CborSerializationUtil.serialize(map);
            }

            return KeyGenUtil.blake2bHash256(encodedBytes);
        } catch (Exception ex) {
            throw new MetadataSerializationException("CBOR serialization exception ", ex);
        }
    }

    private Map getAuxiliaryData() throws CborSerializationException {
        Map map = new Map();

        //Shelley-mary format
        if (metadata != null && (nativeScripts == null || nativeScripts.size() == 0)
                && (plutusScripts == null || plutusScripts.size() == 0)) {
            return metadata.getData();
        }

        //Alonzo format
        map.setTag(new Tag(259));

        if (metadata != null) {
            map.put(new UnsignedInteger(0), metadata.getData());
        }

        if (nativeScripts != null && nativeScripts.size() > 0) {
            Array nativeScriptArray = new Array();
            for (NativeScript nativeScript : nativeScripts) {
                nativeScriptArray.add(nativeScript.serializeAsDataItem());
            }

            map.put(new UnsignedInteger(1), nativeScriptArray);
        }

        if (plutusScripts != null && plutusScripts.size() > 0) {
            Array plutusScriptArray = new Array();
            for (PlutusScript plutusScript : plutusScripts) {
                plutusScriptArray.add(plutusScript.serializeAsDataItem());
            }

            map.put(new UnsignedInteger(2), plutusScriptArray);
        }
        return map;
    }
}
