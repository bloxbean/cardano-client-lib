package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.squareup.javapoet.CodeBlock;

import java.util.LinkedHashMap;

public class MapFieldCodeGen implements FieldCodeGenerator {

    private final FieldCodeGeneratorRegistry registry;

    public MapFieldCodeGen(FieldCodeGeneratorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Type supportedType() { return Type.MAP; }

    @Override
    public CodeBlock generateSerialization(Field field, FieldAccessor accessor) {
        String outputMapName = field.getName() + "MapPlutusData";
        CodeBlock mapCodeBlock = generateMapSerCode(field.getFieldType(), outputMapName,
                "entry", "obj." + accessor.fieldOrGetter(field));

        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add(accessor.nullCheck(field))
                .add(mapCodeBlock)
                .addStatement("constr.getData().add($L)", outputMapName)
                .add("\n")
                .build();
    }

    @Override
    public CodeBlock generateDeserialization(Field field) {
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .addStatement("var $LMap = (MapPlutusData)constrData.getPlutusDataList().get($L)",
                        field.getName(), field.getIndex())
                .add(generateMapDeserCode(field.getFieldType(), field.getName(),
                        field.getName() + "Map", "entry"))
                .build();
    }

    @Override
    public CodeBlock generateNestedSerialization(FieldType type, String baseName, String outputVarName, String expression) {
        String entryVarName = baseName + "_$entry";

        return generateMapSerCode(type, outputVarName, entryVarName, expression);
    }

    @Override
    public CodeBlock generateNestedDeserialization(FieldType type, String baseName, String outputVarName, String pdExpression) {
        String mapVarName = baseName + "_map";
        String entryVarName = baseName + "_entry";

        return CodeBlock.builder()
                .addStatement("var $L = (MapPlutusData)$L", mapVarName, pdExpression)
                .add(generateMapDeserCode(type, outputVarName, mapVarName, entryVarName))
                .build();
    }

    private CodeBlock generateMapSerCode(FieldType fieldType, String outputVarName,
                                         String entryVarName, String mapObjectName) {
        FieldType keyType = fieldType.getGenericTypes().get(0);
        FieldType valueType = fieldType.getGenericTypes().get(1);

        String keyOutputVarName = outputVarName + "_$key";
        String valueOutputVarName = outputVarName + "_$value";

        CodeBlock keyCodeBlock = registry.dispatchNestedSerialization(keyType,
                entryVarName + "Key", keyOutputVarName, entryVarName + ".getKey()");
        CodeBlock valueCodeBlock = registry.dispatchNestedSerialization(valueType,
                entryVarName + "Value", valueOutputVarName, entryVarName + ".getValue()");

        return CodeBlock.builder()
                .addStatement("$T $L = $T.builder().build()", MapPlutusData.class, outputVarName, MapPlutusData.class)
                .beginControlFlow("for(var $L: $L.entrySet())", entryVarName, mapObjectName)
                .add(keyCodeBlock)
                .add(valueCodeBlock)
                .addStatement("$L.put($L, $L)", outputVarName, keyOutputVarName, valueOutputVarName)
                .endControlFlow()
                .build();
    }

    private CodeBlock generateMapDeserCode(FieldType fieldType, String fieldName,
                                           String pdMapName, String entryVarName) {
        FieldType keyType = fieldType.getGenericTypes().get(0);
        FieldType valueType = fieldType.getGenericTypes().get(1);

        String innerEntryVarName = entryVarName + "_$inner";
        String keyOutputVarName = fieldName + "Key";
        String valueOutputVarName = fieldName + "Value";

        CodeBlock keyDeserCodeBlock = registry.dispatchNestedDeserialization(keyType,
                innerEntryVarName + "_$key", keyOutputVarName, entryVarName + ".getKey()");
        CodeBlock valueDeserCodeBlock = registry.dispatchNestedDeserialization(valueType,
                innerEntryVarName + "_$value", valueOutputVarName, entryVarName + ".getValue()");

        return CodeBlock.builder()
                .addStatement("$L $L = new $T<>()", fieldType.getFqTypeName(), fieldName, LinkedHashMap.class)
                .beginControlFlow("for(var $L: (($T)$L).getMap().entrySet())", entryVarName, MapPlutusData.class, pdMapName)
                .add(keyDeserCodeBlock)
                .add(valueDeserCodeBlock)
                .addStatement("$L.put($L, $L)", fieldName, keyOutputVarName, valueOutputVarName)
                .endControlFlow()
                .build();
    }

}
