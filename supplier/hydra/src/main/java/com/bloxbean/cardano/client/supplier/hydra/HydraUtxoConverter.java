package com.bloxbean.cardano.client.supplier.hydra;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.cardanofoundation.hydra.core.HydraException;
import org.cardanofoundation.hydra.core.model.UTXO;

import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter.toPlutusData;

/**
 * Helper class to convert hydra specific UTxO object to BloxBean's one.
 */
public class HydraUtxoConverter {

    public static Utxo convert(String txId, int outputIndex, UTXO utxo) {
        return Utxo.builder()
                .txHash(txId)
                .outputIndex(outputIndex)
                .address(utxo.getAddress())
                .amount(utxo.getValue().entrySet()
                        .stream()
                        .map(entry -> new Amount(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()))
                .dataHash(utxo.getDatumhash())
                .inlineDatum(convertInlineDatum(utxo.getInlineDatum()))
                .referenceScriptHash(utxo.getReferenceScript())
                .build();
    }

    public static String convertInlineDatum(JsonNode inlineDatum) {
        if (inlineDatum == null || inlineDatum instanceof NullNode) {
            return null;
        }

        try {
            PlutusData plutusData = toPlutusData(inlineDatum);

            return plutusData.serializeToHex();
        } catch (JsonProcessingException e) {
            throw new HydraException("Unable to convert inlineDatum to PlutusData");
        }
    }

}
