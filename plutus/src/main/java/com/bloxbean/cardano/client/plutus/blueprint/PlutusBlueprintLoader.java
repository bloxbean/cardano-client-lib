package com.bloxbean.cardano.client.plutus.blueprint;

import com.bloxbean.cardano.client.plutus.blueprint.exception.PlutusBlueprintException;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusContractBlueprint;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads Plutus contract blueprint from plutus.json file
 */
public class PlutusBlueprintLoader {

    /**
     * Loads Plutus contract blueprint from plutus.json file
     * @param file plutus.json file
     * @return PlutusContractBlueprint
     */
    public static PlutusContractBlueprint loadBlueprint(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            return loadBlueprint(input);
        } catch (IOException ex) {
            throw new PlutusBlueprintException(ex);
        }
    }

    /**
     * Loads Plutus contract blueprint from plutus.json file
     * @param is input stream of plutus.json file
     * @return PlutusContractBlueprint
     */
    public static PlutusContractBlueprint loadBlueprint(InputStream is) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            PlutusContractBlueprint plutusContractBlueprint = objectMapper.readValue(is, PlutusContractBlueprint.class);
            return plutusContractBlueprint;
        } catch (IOException ex) {
            throw new PlutusBlueprintException(ex);
        }
    }
}
