package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import com.bloxbean.cardano.client.transaction.model.script.NativeScript;
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
    private List<NativeScript> nativeScripts = new ArrayList<>();

    public void serialize(MapBuilder mapBuilder) throws CborException {
        //Array
        //1. native script [ script_pubkey]
        //script_pubkey = (0, addr_keyhash)
        if(nativeScripts != null && nativeScripts.size() > 0) {
            ArrayBuilder nativeScriptArray = mapBuilder.startArray(1);

            for (NativeScript nativeScript : nativeScripts) {
                nativeScriptArray.add(nativeScript.serializeAsDataItem());
            }

            nativeScriptArray.end();
        }
    }
}
