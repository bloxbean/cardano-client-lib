package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
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
    private List<VkeyWitness> vkeyWitnesses = new ArrayList<>();
    private List<NativeScript> nativeScripts = new ArrayList<>();

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

        if(transactionWitnessSet.getVkeyWitnesses() == null
                && transactionWitnessSet.getNativeScripts() == null) {
            return null;
        } else {
            return transactionWitnessSet;
        }
    }
}
