package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdditionalUtxoDTO {
    private Map<String, String> transaction;
    private long index;
    private String address;
    private Map<String, Map<String, String>> value;
    private String datumHash;
    private String datum;
    private ScriptDTO script;

    public static AdditionalUtxoDTO fromUtxo(Utxo utxo) {
        AdditionalUtxoDTO additionalUtxoDTO = new AdditionalUtxoDTO();
        additionalUtxoDTO.setAddress(utxo.getAddress());
        additionalUtxoDTO.setIndex(utxo.getOutputIndex());
        additionalUtxoDTO.setDatum(utxo.getInlineDatum());
        additionalUtxoDTO.setDatumHash(utxo.getDataHash());
//        additionalUtxoDTO.setScript(ScriptDTO.fromScript(utxo.getScript()));// TODO
        Map<String, Map<String, String>> value = new HashMap<>();
        utxo.getAmount().stream().forEach(amt -> {
            value.put(amt.getUnit(), new HashMap() {{
                put("ada", amt.getQuantity().toString());
            }});
        });
        additionalUtxoDTO.setValue(value);
        additionalUtxoDTO.setTransaction(new HashMap() {{
            put("id", utxo.getTxHash()); // TODO need to check
        }});
        additionalUtxoDTO.setScript(new ScriptDTO() {{
            setLanguage("native");
            setCbor(utxo.getReferenceScriptHash());
        }});

        return additionalUtxoDTO;
    }
}
