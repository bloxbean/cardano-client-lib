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
    private List<PlutusScript> plutusScripts = new ArrayList<>();

    @Builder.Default
    private List<PlutusData> plutusDataList = new ArrayList<>();

    @Builder.Default
    private List<Redeemer> redeemers = new ArrayList<>();

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

        if(plutusScripts != null && plutusScripts.size() > 0) {
            Array plutusScriptArray = new Array();
            for(PlutusScript plutusScript: plutusScripts) {
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

        return witnessMap;
    }

//    transaction_witness_set =
//    { ? 0: [* vkeywitness ]
//  , ? 1: [* native_script ]
//  , ? 2: [* bootstrap_witness ]
//        ; In the future, new kinds of witnesses can be added like this:
//        ; , ? 4: [* foo_script ]
//        ; , ? 5: [* plutus_script ]
//    }
    public static TransactionWitnessSet deserialize(Map witnessMap) throws CborDeserializationException {
        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();

        DataItem vkWitnessesArray = witnessMap.get(new UnsignedInteger(0));
        DataItem nativeScriptArray = witnessMap.get(new UnsignedInteger(1));
        DataItem bootstrapWitnessArray = witnessMap.get(new UnsignedInteger(2));
        DataItem plutusScriptArray = witnessMap.get(new UnsignedInteger(3));
        DataItem plutusDataArray = witnessMap.get(new UnsignedInteger(4));
        DataItem redeemerArray = witnessMap.get(new UnsignedInteger(5));


        if(vkWitnessesArray != null) { //vkwitnesses
            List<DataItem> vkeyWitnessesDIList = ((Array) vkWitnessesArray).getDataItems();
            List<VkeyWitness> vkeyWitnesses = new ArrayList<>();
            for(DataItem vkWitness: vkeyWitnessesDIList) {
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

        //plutus_script
        if(plutusScriptArray != null) {
            List<DataItem> plutusScriptDIList = ((Array)plutusScriptArray).getDataItems();
            List<PlutusScript> plutusScripts = new ArrayList<>();

            for(DataItem plutusScriptDI: plutusScriptDIList) {
                PlutusScript plutusScript = PlutusScript.deserialize((ByteString) plutusScriptDI);
                if(plutusScript != null)
                    plutusScripts.add(plutusScript);
            }

            if(plutusScripts.size() > 0) {
                transactionWitnessSet.setPlutusScripts(plutusScripts);
            }
        } else {
            transactionWitnessSet.setPlutusScripts(null);
        }

        //plutus_data
        if(plutusDataArray != null) {
            List<DataItem> plutusDataDIList = ((Array) plutusDataArray).getDataItems();
            List<PlutusData> plutusDataList = new ArrayList<>();

            for(DataItem plutusDataDI: plutusDataDIList) {
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
                redeemers.add(Redeemer.deserialize((Array) redeemerDI));
            }

            if(redeemers.size() > 0) {
                transactionWitnessSet.setRedeemers(redeemers);
            }
        } else {
            transactionWitnessSet.setRedeemers(null);
        }


        if(transactionWitnessSet.getVkeyWitnesses() == null
                && transactionWitnessSet.getNativeScripts() == null
                && transactionWitnessSet.getBootstrapWitnesses() == null
                && transactionWitnessSet.getPlutusScripts() == null) {
            return null;
        } else {
            return transactionWitnessSet;
        }
    }
}
