package com.bloxbean.cardano.client.plutus.blueprint;

import com.bloxbean.cardano.client.plutus.blueprint.exception.PlutusBlueprintException;
import com.bloxbean.cardano.client.plutus.blueprint.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            ObjectMapper objectMapper = new ObjectMapper();

        PlutusContractBlueprint plutusContractBlueprint = null;
        try {
            plutusContractBlueprint = objectMapper.readValue(is, PlutusContractBlueprint.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        plutusContractBlueprint = resolveReferences(plutusContractBlueprint);
        return plutusContractBlueprint;
    }

    /**
     * Resolves the references in the blueprint
     * @param plutusContractBlueprint
     * @return
     */
    private static PlutusContractBlueprint resolveReferences(PlutusContractBlueprint plutusContractBlueprint) {
        Map<String, BlueprintSchema> definitions = plutusContractBlueprint.getDefinitions();
        List<Validator> validators = plutusContractBlueprint.getValidators();
        for (Validator validator : validators) {
            if(validator.getDatum() != null) {
                BlueprintSchema schema = validator.getDatum().getSchema();
                validator.getDatum().setSchema(resolveDatum(definitions, schema));
            }
            if(validator.getRedeemer() != null) {
                validator.getRedeemer().setSchema(resolveDatum(definitions, validator.getRedeemer().getSchema()));
            }
            if(validator.getParameters() != null) {
                for(int i = 0; i < validator.getParameters().size(); i++) {
                    BlueprintSchema parameterSchema = validator.getParameters().get(i).getSchema();
                    validator.getParameters().get(i).setSchema(resolveDatum(definitions, parameterSchema));
                }
            }

        }
        return plutusContractBlueprint;
    }


    /**
     * Resolves the schema from the definitions map
     * @param definitions
     * @param schema
     * @return
     */
    private static BlueprintSchema resolveDatum(Map<String, BlueprintSchema> definitions, BlueprintSchema schema) {
        BlueprintSchema blueprintSchema = schema;
        if(schema.getRef() != null) {
            String ref = getAndPrepare(schema);
            var refDatumSchema = definitions.get(ref);

            blueprintSchema.copyFrom(refDatumSchema);

            if (blueprintSchema.getDataType() == null && ref.startsWith("Option$"))
                blueprintSchema.setDataType(BlueprintDatatype.option);
        }
        blueprintSchema.setFields(extracted(definitions, blueprintSchema.getFields()));
        blueprintSchema.setAnyOf(extracted(definitions, blueprintSchema.getAnyOf()));
        if(blueprintSchema.getItems() != null)
            blueprintSchema.setItems(extracted(definitions, List.of(blueprintSchema.getItems())).get(0));
        if(blueprintSchema.getKeys() != null)
            blueprintSchema.setKeys(extracted(definitions, List.of(blueprintSchema.getKeys())).get(0));
        if(blueprintSchema.getValues() != null)
            blueprintSchema.setValues(extracted(definitions, List.of(blueprintSchema.getValues())).get(0));

        if (blueprintSchema.getLeft() != null)
            blueprintSchema.setLeft(extracted(definitions, List.of(blueprintSchema.getLeft())).get(0));

        if (blueprintSchema.getRight() != null)
            blueprintSchema.setRight(extracted(definitions, List.of(blueprintSchema.getRight())).get(0));
        return blueprintSchema;
    }

    /**
     * Extracts the schema from the definitions map
     * @param definitions
     * @param listInSchema
     * @return
     */
    private static List<BlueprintSchema> extracted(Map<String, BlueprintSchema> definitions, List<BlueprintSchema> listInSchema) {
        List<BlueprintSchema> list = null;
        if(listInSchema != null) {
            list = new ArrayList<>(listInSchema);
            for(int i = 0; i < listInSchema.size(); i++) {
                BlueprintSchema field = listInSchema.get(i);
                list.set(i, resolveDatum(definitions, field));
            }
        }
        return list;
    }

    /**
     * Prepares the ref string to be used as key in the definitions map
     * @param schema
     * @return
     */
    private static String getAndPrepare(BlueprintSchema schema) {
        String ref = schema.getRef();
        ref = ref.replace("#/definitions/", "");
        ref = ref.replace("~1", "/");
        return ref;
    }
}
