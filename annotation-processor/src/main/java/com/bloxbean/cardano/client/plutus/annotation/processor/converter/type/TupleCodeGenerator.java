package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.TupleInfo;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.squareup.javapoet.CodeBlock;

import java.util.Objects;

/**
 * Generates serialization and deserialization code for tuple types (Pair, Triple, Quartet, Quintet)
 * using arity-parameterized loops instead of copy-pasted per-arity methods.
 */
public class TupleCodeGenerator {

    /**
     * Generates top-level serialization code for a tuple field on an object.
     * Produces: null check, element serialization, ListPlutusData assembly, add to constr.
     *
     * @param info             the tuple arity info
     * @param fieldType        the FieldType of the tuple field
     * @param fieldName        the field name (used for variable naming)
     * @param fieldOrGetterName the field access expression (e.g., "myField" or "getMyField()")
     * @param elementGen       callback for generating each element's serialization code
     */
    public CodeBlock generateTopLevelSerialization(TupleInfo info,
                                                   FieldType fieldType,
                                                   String fieldName,
                                                   String fieldOrGetterName,
                                                   CodeBlock nullCheck,
                                                   ElementCodeGenerator elementGen) {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("//Field $L\n", fieldName)
                .add(nullCheck);

        // Generate element serialization
        String[] nestedVarNames = new String[info.arity()];
        for (int i = 0; i < info.arity(); i++) {
            FieldType elemType = fieldType.getGenericTypes().get(i);
            nestedVarNames[i] = fieldName + "_$nested_" + info.ordinal(i);
            CodeBlock elemBlock = elementGen.generate(elemType, fieldName + "_$" + info.ordinal(i),
                    nestedVarNames[i], "obj." + fieldOrGetterName + "." + info.accessor(i) + "()");
            builder.add(elemBlock);
            builder.add("\n");
        }

        // Build ListPlutusData
        builder.addStatement("$T $L$L = $T.builder().build()", ListPlutusData.class, fieldName, info.label(), ListPlutusData.class);
        for (int i = 0; i < info.arity(); i++) {
            builder.addStatement("$L$L.add($L)", fieldName, info.label(), nestedVarNames[i]);
        }
        builder.addStatement("//Add the $L to the constructor", info.label().toLowerCase());
        builder.addStatement("constr.getData().add($L$L)", fieldName, info.label());
        builder.add("\n");

        return builder.build();
    }

    /**
     * Generates nested serialization code for a tuple value (not top-level field).
     * Produces: null check, element serialization, ListPlutusData assembly.
     *
     * @param info          the tuple arity info
     * @param tupleType     the FieldType of the tuple
     * @param tupleVarName  the variable holding the tuple value
     * @param outputVarName the variable name for the resulting ListPlutusData
     * @param elementGen    callback for generating each element's serialization code
     */
    public CodeBlock generateNestedSerialization(TupleInfo info, FieldType tupleType,
                                                  String tupleVarName, String outputVarName,
                                                  ElementCodeGenerator elementGen) {
        CodeBlock.Builder builder = CodeBlock.builder()
                .addStatement("$T.requireNonNull($L, \"$L must not be null\")", Objects.class, tupleVarName, tupleVarName);

        String[] nestedVarNames = new String[info.arity()];
        for (int i = 0; i < info.arity(); i++) {
            FieldType elemType = tupleType.getGenericTypes().get(i);
            nestedVarNames[i] = outputVarName + "_$" + info.ordinal(i);
            CodeBlock elemBlock = elementGen.generate(elemType, tupleVarName + "_" + info.ordinal(i),
                    nestedVarNames[i], tupleVarName + "." + info.accessor(i) + "()");
            builder.add(elemBlock);
            builder.add("\n");
        }

        builder.addStatement("$T $L = $T.builder().build()", ListPlutusData.class, outputVarName, ListPlutusData.class);
        for (int i = 0; i < info.arity(); i++) {
            builder.addStatement("$L.add($L)", outputVarName, nestedVarNames[i]);
        }

        return builder.build();
    }

    /**
     * Generates serialization from an expression (assigns expression to a variable, then delegates to nested).
     *
     * @param info          the tuple arity info
     * @param tupleType     the FieldType of the tuple
     * @param varName       variable name for the intermediate tuple value
     * @param outputVarName the variable name for the resulting ListPlutusData
     * @param expression    the expression that produces the tuple value
     * @param elementGen    callback for generating each element's serialization code
     */
    public CodeBlock generateSerializationFromExpression(TupleInfo info, FieldType tupleType,
                                                          String varName, String outputVarName,
                                                          String expression, ElementCodeGenerator elementGen) {
        return CodeBlock.builder()
                .addStatement("var $L = $L", varName, expression)
                .add(generateNestedSerialization(info, tupleType, varName, outputVarName, elementGen))
                .build();
    }

    /**
     * Generates deserialization from a PlutusData expression.
     * Produces: cast to ListPlutusData, extract elements, deserialize each, construct tuple.
     *
     * @param info                   the tuple arity info
     * @param tupleType              the FieldType of the tuple
     * @param baseName               base name for intermediate variable naming
     * @param outputVarName          variable name for the resulting tuple
     * @param pdExpression           the PlutusData expression to deserialize from
     * @param elementDeserializer    callback for generating each element's deserialization code
     */
    public CodeBlock generateDeserializationFromExpression(TupleInfo info, FieldType tupleType,
                                                            String baseName, String outputVarName,
                                                            String pdExpression,
                                                            ElementCodeGenerator elementDeserializer) {
        CodeBlock.Builder builder = CodeBlock.builder();

        String listVarName = baseName + "_list";
        builder.addStatement("var $L = (ListPlutusData)$L", listVarName, pdExpression);

        // Extract PlutusData elements
        String[] pdVarNames = new String[info.arity()];
        String[] valueVarNames = new String[info.arity()];
        for (int i = 0; i < info.arity(); i++) {
            pdVarNames[i] = baseName + "_pd_" + info.ordinal(i);
            valueVarNames[i] = baseName + "_value_" + info.ordinal(i);
            builder.addStatement("var $L = $L.getPlutusDataList().get($L)", pdVarNames[i], listVarName, i);
        }

        // Deserialize each element
        for (int i = 0; i < info.arity(); i++) {
            FieldType elemType = tupleType.getGenericTypes().get(i);
            CodeBlock elemBlock = elementDeserializer.generate(elemType, baseName + "_" + info.ordinal(i),
                    valueVarNames[i], pdVarNames[i]);
            builder.add(elemBlock);
            builder.add("\n");
        }

        // Construct the tuple
        StringBuilder constructorArgs = new StringBuilder();
        for (int i = 0; i < info.arity(); i++) {
            if (i > 0) constructorArgs.append(", ");
            constructorArgs.append(valueVarNames[i]);
        }
        builder.addStatement("var $L = new $T($L)", outputVarName, info.tupleClass(), constructorArgs.toString());

        return builder.build();
    }

}
