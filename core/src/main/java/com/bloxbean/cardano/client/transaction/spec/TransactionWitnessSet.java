package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
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
public class TransactionWitnessSet {

    @Builder.Default
    private List<VkeyWitness> vkeyWitnesses = new ArrayList<>();

    @Builder.Default
    private List<NativeScript> nativeScripts = new ArrayList<>();

    @Builder.Default
    private List<BootstrapWitness> bootstrapWitnesses = new ArrayList<>(); //Not implemented

    //Alonzo
    @Builder.Default
    private List<PlutusV1Script> plutusV1Scripts = new ArrayList<>();

    @Builder.Default
    private List<PlutusData> plutusDataList = new ArrayList<>();

    @Builder.Default
    private List<Redeemer> redeemers = new ArrayList<>();

    @Builder.Default
    private List<PlutusV2Script> plutusV2Scripts = new ArrayList<>();

    public Map serialize() throws CborSerializationException {
        //Array
        //1. native script [ script_pubkey]
        //script_pubkey = (0, addr_keyhash)
        Map witnessMap = new Map();
        if(vkeyWitnesses != null && vkeyWitnesses.size() > 0) {
            Array vkeyWitnessArray = new Array();

            for (VkeyWitness vkeyWitness : vkeyWitnesses) {
                vkeyWitnessArray.add(vkeyWitness.serialize());
            }

            witnessMap.put(new UnsignedInteger(0), vkeyWitnessArray);
        }

        if(nativeScripts != null && nativeScripts.size() > 0) {
            Array nativeScriptArray = new Array();

            for (NativeScript nativeScript : nativeScripts) {
                nativeScriptArray.add(nativeScript.serializeAsDataItem());
            }

            witnessMap.put(new UnsignedInteger(1), nativeScriptArray);
        }

        if(bootstrapWitnesses != null && bootstrapWitnesses.size() > 0) {
            Array bootstrapWitnessArray = new Array();
            for(BootstrapWitness bootstrapWitness: bootstrapWitnesses) {
                bootstrapWitnessArray.add(bootstrapWitness.serialize());
            }

            witnessMap.put(new UnsignedInteger(2), bootstrapWitnessArray);
        }

        if(plutusV1Scripts != null && plutusV1Scripts.size() > 0) {
            Array plutusScriptArray = new Array();
            for(PlutusV1Script plutusScript: plutusV1Scripts) {
                plutusScriptArray.add(plutusScript.serializeAsDataItem());
            }

            witnessMap.put(new UnsignedInteger(3), plutusScriptArray);
        }

        if(plutusDataList != null && plutusDataList.size() > 0) {
            Array plutusdataArray = new Array();
            for(PlutusData plutusData: plutusDataList) {
                plutusdataArray.add(plutusData.serialize());
            }

            witnessMap.put(new UnsignedInteger(4), plutusdataArray);
        }

        if(redeemers != null && redeemers.size() > 0) {
            Array redeemerArray = new Array();
            for(Redeemer redeemer: redeemers) {
                redeemerArray.add(redeemer.serialize());
            }

            witnessMap.put(new UnsignedInteger(5), redeemerArray);
        }

        //Plutus v2 script -- Babbage era
        if(plutusV2Scripts != null && plutusV2Scripts.size() > 0) {
            Array plutusV2ScriptArray = new Array();
            for(PlutusV2Script plutusV2Script: plutusV2Scripts) {
                plutusV2ScriptArray.add(plutusV2Script.serializeAsDataItem());
            }

            witnessMap.put(new UnsignedInteger(6), plutusV2ScriptArray);
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
    public static TransactionWitnessSet deserialize(Map witnessMap) throws CborDeserializationException {
        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();

        DataItem vkWitnessesArray = witnessMap.get(new UnsignedInteger(0));
        DataItem nativeScriptArray = witnessMap.get(new UnsignedInteger(1));
        DataItem bootstrapWitnessArray = witnessMap.get(new UnsignedInteger(2));
        DataItem plutusScriptArray = witnessMap.get(new UnsignedInteger(3));
        DataItem plutusDataArray = witnessMap.get(new UnsignedInteger(4));
        DataItem redeemerArray = witnessMap.get(new UnsignedInteger(5));
        DataItem plutusV2ScriptArray = witnessMap.get(new UnsignedInteger(6));

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
            List<DataItem> redeemerDIList = ((Array) redeemerArray).getDataItems();
            List<Redeemer> redeemers = new ArrayList<>();

            for(DataItem redeemerDI: redeemerDIList) {
                if (redeemerDI == SimpleValue.BREAK) continue;
                redeemers.add(Redeemer.deserialize((Array) redeemerDI));
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

        //Check if all fields are null, then return null
        if(transactionWitnessSet.getVkeyWitnesses() == null
                && transactionWitnessSet.getNativeScripts() == null
                && transactionWitnessSet.getBootstrapWitnesses() == null
                && transactionWitnessSet.getPlutusV1Scripts() == null
                && transactionWitnessSet.getPlutusV2Scripts() == null
                && transactionWitnessSet.getPlutusDataList() == null
                && transactionWitnessSet.getRedeemers() == null
        ) {
            return null;
        } else {
            return transactionWitnessSet;
        }
    }
}
