package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.raw.RawWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.transaction.util.UniqueList;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.createArray;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.requireDijkstra;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Builder
public class TransactionWitnessSet {
    @Builder.Default
    private List<VkeyWitness> vkeyWitnesses = new UniqueList<>();

    @Builder.Default
    private List<NativeScript> nativeScripts = new UniqueList<>();

    @Builder.Default
    private List<BootstrapWitness> bootstrapWitnesses = new UniqueList<>(); //Not implemented

    //Alonzo
    @Builder.Default
    private List<PlutusV1Script> plutusV1Scripts = new UniqueList<>();

    @Builder.Default
    private List<PlutusData> plutusDataList = new UniqueList<>();

    @Builder.Default
    private List<Redeemer> redeemers = new ArrayList<>();

    @Builder.Default
    private List<PlutusV2Script> plutusV2Scripts = new UniqueList<>();

    @Builder.Default
    private List<PlutusV3Script> plutusV3Scripts = new UniqueList<>();

    /**
     * The Plutus V4 scripts (key 8), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PlutusV4Script> plutusV4Scripts;

    /**
     * The constructor of the fields before Dijkstra, kept for source and binary compatibility; use the builder for the
     * Plutus V4 scripts.
     */
    public TransactionWitnessSet(List<VkeyWitness> vkeyWitnesses, List<NativeScript> nativeScripts,
                                 List<BootstrapWitness> bootstrapWitnesses, List<PlutusV1Script> plutusV1Scripts,
                                 List<PlutusData> plutusDataList, List<Redeemer> redeemers,
                                 List<PlutusV2Script> plutusV2Scripts, List<PlutusV3Script> plutusV3Scripts) {
        this(vkeyWitnesses, nativeScripts, bootstrapWitnesses, plutusV1Scripts, plutusDataList, redeemers,
                plutusV2Scripts, plutusV3Scripts, null);
    }

    public Map serialize() throws CborSerializationException {
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    public Map serialize(Era era) throws CborSerializationException {
        //Array
        //1. native script [ script_pubkey]
        //script_pubkey = (0, addr_keyhash)
        Map witnessMap = new Map();
        if(vkeyWitnesses != null && vkeyWitnesses.size() > 0) {
            Array vkeyWitnessArray = createArray(era);

            for (VkeyWitness vkeyWitness : vkeyWitnesses) {
                vkeyWitnessArray.add(vkeyWitness.serialize());
            }

            witnessMap.put(new UnsignedInteger(0), vkeyWitnessArray);
        }

        if(nativeScripts != null && nativeScripts.size() > 0) {
            Array nativeScriptArray = createArray(era);

            for (NativeScript nativeScript : nativeScripts) {
                nativeScriptArray.add(nativeScript.serializeAsDataItem());
            }

            witnessMap.put(new UnsignedInteger(1), nativeScriptArray);
        }

        if(bootstrapWitnesses != null && bootstrapWitnesses.size() > 0) {
            Array bootstrapWitnessArray = createArray(era);

            for(BootstrapWitness bootstrapWitness: bootstrapWitnesses) {
                bootstrapWitnessArray.add(bootstrapWitness.serialize());
            }

            witnessMap.put(new UnsignedInteger(2), bootstrapWitnessArray);
        }

        if(plutusV1Scripts != null && plutusV1Scripts.size() > 0) {
            Array plutusScriptArray = createArray(era);

            for(PlutusV1Script plutusScript: plutusV1Scripts) {
                plutusScriptArray.add(plutusScript.serializeAsDataItem());
            }

            witnessMap.put(new UnsignedInteger(3), plutusScriptArray);
        }

        if(plutusDataList != null && plutusDataList.size() > 0) {
            Array plutusdataArray = createArray(era);

            for(PlutusData plutusData: plutusDataList) {
                plutusdataArray.add(plutusData.serialize());
            }

            witnessMap.put(new UnsignedInteger(4), plutusdataArray);
        }

        if(redeemers != null && redeemers.size() > 0) {
            if (era == null || era.value >= Era.Conway.value) { //Conway era and no plutus v1 scripts, use old array format
                Map redeemerMap = new Map();
                for(Redeemer redeemer: redeemers) {
                    var tuple = redeemer.serialize();
                    redeemerMap.put(tuple._1, tuple._2);
                }

                witnessMap.put(new UnsignedInteger(5), redeemerMap);
            } else {
                Array redeemerArray = new Array();
                for(Redeemer redeemer: redeemers) {
                    redeemerArray.add(redeemer.serializePreConway());
                }

                witnessMap.put(new UnsignedInteger(5), redeemerArray);
            }
        }

        //Plutus v2 script -- Babbage era
        if(plutusV2Scripts != null && plutusV2Scripts.size() > 0) {
            Array plutusV2ScriptArray = createArray(era);

            for(PlutusV2Script plutusV2Script: plutusV2Scripts) {
                plutusV2ScriptArray.add(plutusV2Script.serializeAsDataItem());
            }

            witnessMap.put(new UnsignedInteger(6), plutusV2ScriptArray);
        }

        //Plutus v3 script -- Conway era
        if(plutusV3Scripts != null && plutusV3Scripts.size() > 0) {
            Array plutusV3ScriptArray = createArray(era);

            for(PlutusV3Script plutusV3Script: plutusV3Scripts) {
                plutusV3ScriptArray.add(plutusV3Script.serializeAsDataItem());
            }

            witnessMap.put(new UnsignedInteger(7), plutusV3ScriptArray);
        }

        //Plutus v4 script -- Dijkstra era
        if (plutusV4Scripts != null && !plutusV4Scripts.isEmpty()) {
            requireDijkstra(era, "TransactionWitnessSet.plutusV4Scripts");
            Array plutusV4ScriptArray = createArray(era);
            for (PlutusV4Script plutusV4Script : plutusV4Scripts)
                plutusV4ScriptArray.add(plutusV4Script.serializeAsDataItem());
            witnessMap.put(new UnsignedInteger(8), plutusV4ScriptArray);
        }

        return witnessMap;
    }

    public static TransactionWitnessSet deserialize(Map witnessMap) throws CborDeserializationException {
        try {
            return from(RawWitnessSet.lenient(CborSerializationUtil.span(witnessMap), RawModels.READ_IN));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("TransactionWitnessSet deserialization failed", e);
        }
    }

    /**
     * @return the model: a field absent from the witness set is null and one that is empty keeps its default; null if
     * no field is present
     */
    static TransactionWitnessSet from(RawWitnessSet raw) throws CborDeserializationException {
        TransactionWitnessSet set = new TransactionWitnessSet();
        set.setVkeyWitnesses(field(raw.vkeyWitnesses(), VkeyWitness::from, set.getVkeyWitnesses()));
        set.setNativeScripts(field(raw.nativeScripts(), script -> (NativeScript) RawModels.toScript(script),
                set.getNativeScripts()));
        set.setBootstrapWitnesses(field(raw.bootstrapWitnesses(), BootstrapWitness::from, set.getBootstrapWitnesses()));
        set.setPlutusV1Scripts(field(raw.plutusV1Scripts(), script -> (PlutusV1Script) RawModels.toScript(script),
                set.getPlutusV1Scripts()));
        set.setPlutusDataList(field(raw.datums(), RawModels::toPlutusData, set.getPlutusDataList()));
        set.setRedeemers(field(raw.redeemers(), redeemer -> Redeemer.from(redeemer, raw.era()), set.getRedeemers()));
        set.setPlutusV2Scripts(field(raw.plutusV2Scripts(), script -> (PlutusV2Script) RawModels.toScript(script),
                set.getPlutusV2Scripts()));
        set.setPlutusV3Scripts(field(raw.plutusV3Scripts(), script -> (PlutusV3Script) RawModels.toScript(script),
                set.getPlutusV3Scripts()));
        set.setPlutusV4Scripts(field(raw.plutusV4Scripts(), script -> (PlutusV4Script) RawModels.toScript(script),
                null));
        boolean empty = set.vkeyWitnesses == null && set.nativeScripts == null && set.bootstrapWitnesses == null
                && set.plutusV1Scripts == null && set.plutusV2Scripts == null && set.plutusV3Scripts == null
                && set.plutusV4Scripts == null && set.plutusDataList == null && set.redeemers == null;
        return empty ? null : set;
    }

    private static <R, M> List<M> field(Optional<List<R>> raw, RawModels.Mapper<R, M> mapper, List<M> whenEmpty)
            throws CborDeserializationException {
        if (raw.isEmpty())
            return null;
        return raw.get().isEmpty() ? whenEmpty : RawModels.mapAll(raw.get(), mapper);
    }
}
