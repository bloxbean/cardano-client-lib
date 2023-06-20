package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Initial implementation of Plutus Contract Blueprint (CIP-57)
 * Current implementation only returns Preamble and validator's compiled code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlutusContractBlueprint {
    private Preamble preamble;
    private List<Validator> validators;

    @JsonIgnore
    public PlutusScript getPlutusScript(String title) {
        if (validators == null || validators.isEmpty())
            return null;

        return validators.stream().filter(v -> v.getTitle().equals(title)).findFirst()
                .map(v -> {
                    if (preamble.getPlutusVersion() == PlutusVersion.v1) {
                        return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(v.getCompiledCode(), preamble.getPlutusVersion());
                    } else {
                        return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(v.getCompiledCode(), preamble.getPlutusVersion());
                    }
                }).orElse(null);
    }
}




