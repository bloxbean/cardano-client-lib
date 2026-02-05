package com.bloxbean.cardano.client.plutus.blueprint;

import com.bloxbean.cardano.client.plutus.blueprint.exception.PlutusBlueprintException;
import com.bloxbean.cardano.client.plutus.blueprint.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
        return resolveDatum(definitions, schema, new IdentityHashMap<>());
    }

    /**
     * Resolves the schema from the definitions map with circular reference detection
     * @param definitions the definitions map
     * @param schema the schema to resolve
     * @param visiting set of schemas currently being visited (for circular reference detection)
     * @return resolved schema
     */
    private static BlueprintSchema resolveDatum(Map<String, BlueprintSchema> definitions,
                                                BlueprintSchema schema,
                                                IdentityHashMap<BlueprintSchema, Boolean> visiting) {
        if (schema == null) {
            return null;
        }

        // Check for circular reference - if we're already visiting this schema, return it immediately
        if (visiting.containsKey(schema)) {
            return schema;
        }

        // Mark this schema as being visited
        visiting.put(schema, Boolean.TRUE);

        try {
            BlueprintSchema blueprintSchema = schema;
            if (schema.getRef() != null) {
                String ref = getAndPrepare(schema);
                var refDatumSchema = definitions.get(ref);

                blueprintSchema.copyFrom(refDatumSchema);

                if (blueprintSchema.getDataType() == null && ref.startsWith("Option$"))
                    blueprintSchema.setDataType(BlueprintDatatype.option);
            }
            blueprintSchema.setFields(extracted(definitions, blueprintSchema.getFields(), visiting));
            blueprintSchema.setAnyOf(extracted(definitions, blueprintSchema.getAnyOf(), visiting));
            if (blueprintSchema.getItems() != null && !blueprintSchema.getItems().isEmpty())
                blueprintSchema.setItems(extracted(definitions, blueprintSchema.getItems(), visiting));
            if (blueprintSchema.getKeys() != null)
                blueprintSchema.setKeys(extracted(definitions, List.of(blueprintSchema.getKeys()), visiting).get(0));
            if (blueprintSchema.getValues() != null)
                blueprintSchema.setValues(extracted(definitions, List.of(blueprintSchema.getValues()), visiting).get(0));

            if (blueprintSchema.getLeft() != null)
                blueprintSchema.setLeft(extracted(definitions, List.of(blueprintSchema.getLeft()), visiting).get(0));

            if (blueprintSchema.getRight() != null)
                blueprintSchema.setRight(extracted(definitions, List.of(blueprintSchema.getRight()), visiting).get(0));
            return blueprintSchema;
        } finally {
            // Remove from visiting set after processing
            visiting.remove(schema);
        }
    }

    /**
     * Extracts the schema from the definitions map
     * @param definitions
     * @param listInSchema
     * @param visiting set of schemas currently being visited (for circular reference detection)
     * @return
     */
    private static List<BlueprintSchema> extracted(Map<String, BlueprintSchema> definitions,
                                                   List<BlueprintSchema> listInSchema,
                                                   IdentityHashMap<BlueprintSchema, Boolean> visiting) {
        List<BlueprintSchema> list = null;
        if(listInSchema != null) {
            list = new ArrayList<>(listInSchema);
            for(int i = 0; i < listInSchema.size(); i++) {
                BlueprintSchema field = listInSchema.get(i);
                list.set(i, resolveDatum(definitions, field, visiting));
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
