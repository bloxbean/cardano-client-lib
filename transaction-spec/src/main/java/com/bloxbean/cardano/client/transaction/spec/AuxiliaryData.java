package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.raw.RawAuxData;
import com.bloxbean.cardano.client.transaction.raw.RawScript;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuxiliaryData {
    @JsonSerialize(using = MetadataJacksonSerializer.class)
    private Metadata metadata;

    @Builder.Default
    private List<NativeScript> nativeScripts = new ArrayList<>();

    @Builder.Default
    private List<PlutusV1Script> plutusV1Scripts = new ArrayList<>();

    @Builder.Default
    private List<PlutusV2Script> plutusV2Scripts = new ArrayList<>();

    @Builder.Default
    private List<PlutusV3Script> plutusV3Scripts = new ArrayList<>();

    public DataItem serialize() throws CborSerializationException {
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    public DataItem serialize(Era era) throws CborSerializationException {
        return getAuxiliaryData(era);
    }

    /**
     * Decodes aux data in the Allegra/Mary shape {@code [metadata, [* native_script]]}, which stays valid in later eras.
     *
     * @param array the aux data
     * @return the aux data, with its metadata and native scripts
     * @throws CborDeserializationException if the array is not of that shape
     */
    public static AuxiliaryData deserialize(Array array) throws CborDeserializationException {
        return deserialize((DataItem) array);
    }

    public static AuxiliaryData deserialize(Map map) throws CborDeserializationException {
        return deserialize((DataItem) map);
    }

    private static AuxiliaryData deserialize(DataItem item) throws CborDeserializationException {
        try {
            return from(RawAuxData.lenient(RawModels.span(item), TransactionBody.READ_IN));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("AuxiliaryData deserialization failed", e);
        }
    }

    static AuxiliaryData from(RawAuxData raw) throws CborDeserializationException {
        AuxiliaryData auxiliaryData = new AuxiliaryData();
        Optional<CborSpan> metadata = raw.metadata();
        if (metadata.isPresent())
            auxiliaryData.setMetadata(RawModels.toMetadata(metadata.get()));
        for (RawScript script : raw.nativeScripts())
            auxiliaryData.getNativeScripts().add((NativeScript) RawModels.toScript(script));
        for (RawScript script : raw.plutusV1Scripts())
            auxiliaryData.getPlutusV1Scripts().add((PlutusV1Script) RawModels.toScript(script));
        for (RawScript script : raw.plutusV2Scripts())
            auxiliaryData.getPlutusV2Scripts().add((PlutusV2Script) RawModels.toScript(script));
        for (RawScript script : raw.plutusV3Scripts())
            auxiliaryData.getPlutusV3Scripts().add((PlutusV3Script) RawModels.toScript(script));
        return auxiliaryData;
    }

    /**
     * The hash of the serialized aux data, which is right for aux data built with CCL. For aux data received in a
     * transaction, take the hash from the original bytes with
     * {@link com.bloxbean.cardano.client.transaction.raw.RawTx#auxDataHash()}: the model keeps one entry per metadata
     * key and re-encodes in its own shape.
     *
     * @return the aux data hash
     */
    @JsonIgnore
    public byte[] getAuxiliaryDataHash() throws MetadataSerializationException {
        return getAuxiliaryDataHash(EraSerializationConfig.INSTANCE.getEra());
    }

    @JsonIgnore
    public byte[] getAuxiliaryDataHash(Era era) throws MetadataSerializationException {
        try {
            Map map = getAuxiliaryData(era);
            byte[] encodedBytes = CborSerializationUtil.serialize(map);

            return Blake2bUtil.blake2bHash256(encodedBytes);
        } catch (Exception ex) {
            throw new MetadataSerializationException("CBOR serialization exception ", ex);
        }
    }

    private Map getAuxiliaryData(Era era) throws CborSerializationException {
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

        if (plutusV3Scripts != null && plutusV3Scripts.size() > 0) {
            Array plutusV3ScriptArray = new Array();
            for (PlutusV3Script plutusV3Script : plutusV3Scripts) {
                plutusV3ScriptArray.add(plutusV3Script.serializeAsDataItem());
            }

            map.put(new UnsignedInteger(4), plutusV3ScriptArray);
        }

        return map;
    }
}
