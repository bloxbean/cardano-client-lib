package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.util.UniqueList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.createArray;

@Data
@AllArgsConstructor
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
            if (era == Era.Conway) { //Conway era and no plutus v1 scripts, use old array format
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

        return witnessMap;
    }

//    transaction_witness_set =
//    { ? 0: [* vkeywitness ]
//  , ? 1: [* native_script ]
//  , ? 2: [* bootstrap_witness ]
//  , ? 3: [* plutus_v1_script ]
//  , ? 4: [* plutus_data ]
//  , ? 5: [* redeemer ]
//  , ? 6: [* plutus_v2_script ] ; New
//    }

//-- Conway Era
//    transaction_witness_set =
//    { ? 0: nonempty_set<vkeywitness>
//  , ? 1: nonempty_set<native_script>
//  , ? 2: nonempty_set<bootstrap_witness>
//  , ? 3: nonempty_set<plutus_v1_script>
//  , ? 4: nonempty_set<plutus_data>
//  , ? 5: redeemers
//  , ? 6: nonempty_set<plutus_v2_script>
//  , ? 7: nonempty_set<plutus_v3_script>
//    }
    public static TransactionWitnessSet deserialize(Map witnessMap) throws CborDeserializationException {
        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();

        DataItem vkWitnessesArray = witnessMap.get(new UnsignedInteger(0));
        DataItem nativeScriptArray = witnessMap.get(new UnsignedInteger(1));
        DataItem bootstrapWitnessArray = witnessMap.get(new UnsignedInteger(2));
        DataItem plutusScriptArray = witnessMap.get(new UnsignedInteger(3));
        DataItem plutusDataArray = witnessMap.get(new UnsignedInteger(4));
        DataItem redeemerArray = witnessMap.get(new UnsignedInteger(5));
        DataItem plutusV2ScriptArray = witnessMap.get(new UnsignedInteger(6));
        DataItem plutusV3ScriptArray = witnessMap.get(new UnsignedInteger(7));

        if(vkWitnessesArray != null) { //vkwitnesses
            List<DataItem> vkeyWitnessesDIList = ((Array) vkWitnessesArray).getDataItems();
            List<VkeyWitness> vkeyWitnesses = new ArrayList<>();
            for(DataItem vkWitness: vkeyWitnessesDIList) {
                if (vkWitness == SimpleValue.BREAK) continue;
                VkeyWitness vkeyWitness = VkeyWitness.deserialize((Array) vkWitness);
                vkeyWitnesses.add(vkeyWitness);
            }

            if(vkeyWitnesses.size() > 0) {
                transactionWitnessSet.setVkeyWitnesses(vkeyWitnesses);
            }
        } else {
            transactionWitnessSet.setVkeyWitnesses(null);
        }

        if(nativeScriptArray != null) { //nativeScriptArray
            List<DataItem> nativeScriptsDIList = ((Array)nativeScriptArray).getDataItems();
            List<NativeScript> nativeScripts = new ArrayList<>();

            for(DataItem nativeScriptDI: nativeScriptsDIList) {
                if (nativeScriptDI == SimpleValue.BREAK) continue;
                NativeScript nativeScript = NativeScript.deserialize((Array)nativeScriptDI);
                if(nativeScript != null)
                    nativeScripts.add(nativeScript);
            }

            if(nativeScripts.size() > 0) {
                transactionWitnessSet.setNativeScripts(nativeScripts);
            }
        } else {
            transactionWitnessSet.setNativeScripts(null);
        }

        if(bootstrapWitnessArray != null) {
            List<DataItem> bootstrapWitnessDIList = ((Array)bootstrapWitnessArray).getDataItems();
            List<BootstrapWitness> bootstrapWitnesses = new ArrayList<>();

            for(DataItem bootstrapWitnessDI: bootstrapWitnessDIList) {
                if (bootstrapWitnessDI == SimpleValue.BREAK) continue;
                BootstrapWitness bootstrapWitness = BootstrapWitness.deserialize((Array) bootstrapWitnessDI);
                if(bootstrapWitness != null)
                    bootstrapWitnesses.add(bootstrapWitness);
            }

            if(bootstrapWitnesses.size() > 0) {
                transactionWitnessSet.setBootstrapWitnesses(bootstrapWitnesses);
            }
        } else {
            transactionWitnessSet.setBootstrapWitnesses(null);
        }

        //plutus_v1_script
        if(plutusScriptArray != null) {
            List<DataItem> plutusV1ScriptDIList = ((Array)plutusScriptArray).getDataItems();
            List<PlutusV1Script> plutusV1Scripts = new ArrayList<>();

            for(DataItem plutusV1ScriptDI: plutusV1ScriptDIList) {
                if (plutusV1ScriptDI == SimpleValue.BREAK) continue;
                PlutusV1Script plutusV1Script = PlutusV1Script.deserialize((ByteString) plutusV1ScriptDI);
                if(plutusV1Script != null)
                    plutusV1Scripts.add(plutusV1Script);
            }

            if(plutusV1Scripts.size() > 0) {
                transactionWitnessSet.setPlutusV1Scripts(plutusV1Scripts);
            }
        } else {
            transactionWitnessSet.setPlutusV1Scripts(null);
        }

        //plutus_data
        if(plutusDataArray != null) {
            List<DataItem> plutusDataDIList = ((Array) plutusDataArray).getDataItems();
            List<PlutusData> plutusDataList = new ArrayList<>();

            for(DataItem plutusDataDI: plutusDataDIList) {
                if (plutusDataDI == SimpleValue.BREAK) continue;
                plutusDataList.add(PlutusData.deserialize(plutusDataDI));
            }

            if(plutusDataList.size() > 0) {
                transactionWitnessSet.setPlutusDataList(plutusDataList);
            }
        } else {
            transactionWitnessSet.setPlutusDataList(null);
        }

        //redeemers
        if(redeemerArray != null) {
            List<Redeemer> redeemers = new ArrayList<>();
            if (redeemerArray instanceof Array) {
                List<DataItem> redeemerDIList = ((Array) redeemerArray).getDataItems();

                for (DataItem redeemerDI : redeemerDIList) {
                    if (redeemerDI == SimpleValue.BREAK) continue;
                    redeemers.add(Redeemer.deserializePreConway((Array) redeemerDI));
                }
            } else if (redeemerArray instanceof Map) { //Conway
                Map redeemerMap = (Map)redeemerArray;
                for(DataItem key: redeemerMap.getKeys()) {
                    //if (key == SimpleValue.BREAK) continue;
                    DataItem value = redeemerMap.get(key);
                    Redeemer redeemer = Redeemer.deserialize((Array) key, (Array) value);
                    redeemers.add(redeemer);
                }
            }

            if(redeemers.size() > 0) {
                transactionWitnessSet.setRedeemers(redeemers);
            }
        } else {
            transactionWitnessSet.setRedeemers(null);
        }

        //plutus_v2_script (Babbage era or Post Alonzo)
        if(plutusV2ScriptArray != null) {
            List<DataItem> plutusV2ScriptDIList = ((Array)plutusV2ScriptArray).getDataItems();
            List<PlutusV2Script> plutusV2Scripts = new ArrayList<>();

            for(DataItem plutusV2ScriptDI: plutusV2ScriptDIList) {
                if (plutusV2ScriptDI == SimpleValue.BREAK) continue;
                PlutusV2Script plutusV2Script = PlutusV2Script.deserialize((ByteString) plutusV2ScriptDI);
                if(plutusV2Script != null)
                    plutusV2Scripts.add(plutusV2Script);
            }

            if(plutusV2Scripts.size() > 0) {
                transactionWitnessSet.setPlutusV2Scripts(plutusV2Scripts);
            }
        } else {
            transactionWitnessSet.setPlutusV2Scripts(null);
        }

        //plutus_v3_script (Conway era)
        if(plutusV3ScriptArray != null) {
            List<DataItem> plutusV3ScriptDIList = ((Array)plutusV3ScriptArray).getDataItems();
            List<PlutusV3Script> plutusV3Scripts = new ArrayList<>();

            for(DataItem plutusV3ScriptDI: plutusV3ScriptDIList) {
                if (plutusV3ScriptDI == SimpleValue.BREAK) continue;
                PlutusV3Script plutusV3Script = PlutusV3Script.deserialize((ByteString) plutusV3ScriptDI);
                if(plutusV3Script != null)
                    plutusV3Scripts.add(plutusV3Script);
            }

            if(plutusV3Scripts.size() > 0) {
                transactionWitnessSet.setPlutusV3Scripts(plutusV3Scripts);
            }
        } else {
            transactionWitnessSet.setPlutusV3Scripts(null);
        }

        //Check if all fields are null, then return null
        if(transactionWitnessSet.getVkeyWitnesses() == null
                && transactionWitnessSet.getNativeScripts() == null
                && transactionWitnessSet.getBootstrapWitnesses() == null
                && transactionWitnessSet.getPlutusV1Scripts() == null
                && transactionWitnessSet.getPlutusV2Scripts() == null
                && transactionWitnessSet.getPlutusV3Scripts() == null
                && transactionWitnessSet.getPlutusDataList() == null
                && transactionWitnessSet.getRedeemers() == null
        ) {
            return null;
        } else {
            return transactionWitnessSet;
        }
    }

}
