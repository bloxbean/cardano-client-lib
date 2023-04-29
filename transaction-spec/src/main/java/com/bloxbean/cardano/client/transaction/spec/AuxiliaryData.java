package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
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
    private List<PlutusV1Script> plutusV1Scripts = new ArrayList<>();

    @Builder.Default
    private List<PlutusV2Script> plutusV2Scripts = new ArrayList<>();

    public DataItem serialize() throws CborSerializationException {
        return getAuxiliaryData();
    }

    public static AuxiliaryData deserialize(Map map) throws CborDeserializationException {
        Tag mapTag = map.getTag();
        AuxiliaryData auxiliaryData = new AuxiliaryData();

        if (mapTag != null && mapTag.getValue() == 259) { //Alonzo
            DataItem metadataValueDI = map.get(new UnsignedInteger(0));
            DataItem nativeScriptsValueDI = map.get(new UnsignedInteger(1));
            DataItem plutusV1ScriptsValueDI = map.get(new UnsignedInteger(2));
            DataItem plutusV2ScriptsValueDI = map.get(new UnsignedInteger(3));

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

            //plutus_v1_script
            if (plutusV1ScriptsValueDI != null) {
                Array plutusV1ScriptsArray = (Array) plutusV1ScriptsValueDI;
                for (DataItem plutusV1ScriptDI : plutusV1ScriptsArray.getDataItems()) {
                    PlutusV1Script plutusV1Script = PlutusV1Script.deserialize((ByteString) plutusV1ScriptDI);
                    auxiliaryData.getPlutusV1Scripts().add(plutusV1Script);
                }
            }

            //plutus_v2_script
            if (plutusV2ScriptsValueDI != null) {
                Array plutusV2ScriptsArray = (Array) plutusV2ScriptsValueDI;
                for (DataItem plutusV2ScriptDI : plutusV2ScriptsArray.getDataItems()) {
                    PlutusV2Script plutusV2Script = PlutusV2Script.deserialize((ByteString) plutusV2ScriptDI);
                    auxiliaryData.getPlutusV2Scripts().add(plutusV2Script);
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
            byte[] encodedBytes = CborSerializationUtil.serialize(map);

            return Blake2bUtil.blake2bHash256(encodedBytes);
        } catch (Exception ex) {
            throw new MetadataSerializationException("CBOR serialization exception ", ex);
        }
    }

    private Map getAuxiliaryData() throws CborSerializationException {
        Map map = new Map();

        //Shelley-mary format
        if (metadata != null
                && (nativeScripts == null || nativeScripts.size() == 0)
                && (plutusV1Scripts == null || plutusV1Scripts.size() == 0)
                && (plutusV2Scripts == null || plutusV2Scripts.size() == 0)) {
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

        if (plutusV1Scripts != null && plutusV1Scripts.size() > 0) {
            Array plutusV1ScriptArray = new Array();
            for (PlutusV1Script plutusV1Script : plutusV1Scripts) {
                plutusV1ScriptArray.add(plutusV1Script.serializeAsDataItem());
            }

            map.put(new UnsignedInteger(2), plutusV1ScriptArray);
        }

        if (plutusV2Scripts != null && plutusV2Scripts.size() > 0) {
            Array plutusV2ScriptArray = new Array();
            for (PlutusV2Script plutusV2Script : plutusV2Scripts) {
                plutusV2ScriptArray.add(plutusV2Script.serializeAsDataItem());
            }

            map.put(new UnsignedInteger(3), plutusV2ScriptArray);
        }

        return map;
    }
}
