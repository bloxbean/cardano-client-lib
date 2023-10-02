package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.squareup.javapoet.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Code generator for Plutus Data Converter
 */
@Slf4j
public class ConverterCodeGenerator implements CodeGenerator {
    private ProcessingEnvironment processEnv;

    public ConverterCodeGenerator(ProcessingEnvironment processingEnvironment) {
        this.processEnv = processingEnvironment;
    }

    public TypeSpec generate(ClassDefinition classDef) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(classDef.getName())
                .addModifiers(Modifier.PUBLIC)
                .superclass(BasePlutusDataConverter.class);

        // Create the serialize method
        MethodSpec toPlutusDataMethod = generateToPlutusDataMethod(classDef);
        MethodSpec fromPlutusDataMethod = generateFromPlutusDataMethod(classDef);

        return classBuilder
                .addJavadoc("Auto generated code. DO NOT MODIFY")
                .addMethod(toPlutusDataMethod)
                .addMethod(fromPlutusDataMethod)
                .addMethod(generateSerialize(classDef))
                .addMethod(generateSerializeToHex(classDef))
                .addMethod(generateDeserialize(classDef))
                .addMethod(generateDeserializeFromHex(classDef))
                .build();
    }

    //-- serializeToHex(obj) method
    private MethodSpec generateSerializeToHex(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("serializeToHex")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addParameter(objTypeName, "obj");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(obj);", Objects.class)
                .addStatement("var constr = toPlutusData(obj)")
                .addStatement("return constr.serializeToHex()")
                .build();

        return methodBuilder.addCode(body)
                .build();
    }

    //-- serialize(obj) method
    private MethodSpec generateSerialize(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(byte[].class)
                .addParameter(objTypeName, "obj");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(obj);", Objects.class)
                .beginControlFlow("try")
                .addStatement("var constr = toPlutusData(obj)")
                .addStatement("return $T.serialize(constr.serialize())", CborSerializationUtil.class)
                .nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(e)", CborRuntimeException.class)
                .endControlFlow()
                .build();

        return methodBuilder.addCode(body)
                .build();
    }

    //-- Object deserialize(hex) method
    private MethodSpec generateDeserializeFromHex(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(objTypeName)
                .addParameter(String.class, "hex");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(hex);", Objects.class)
                .addStatement("var bytes = $T.decodeHexString(hex)", HexUtil.class)
                .addStatement("return deserialize(bytes)")
                .build();

        return methodBuilder.addCode(body)
                .build();
    }

    //-- deserialize method
    private MethodSpec generateDeserialize(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder serializeMethodBuilder = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(objTypeName)
                .addParameter(byte[].class, "bytes");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(bytes);", Objects.class)
                .beginControlFlow("try")
                .addStatement("var di = $T.deserialize(bytes)", CborSerializationUtil.class)
                .addStatement("var constr = $T.deserialize(di)", ConstrPlutusData.class)
                .addStatement("return fromPlutusData(constr)")
                .nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(e)", CborRuntimeException.class)
                .endControlFlow()
                .build();

        return serializeMethodBuilder.addCode(body)
                .build();
    }

    /**
     * Generate deserialize method
     * @param classDef Class definition
     * @return MethodSpec
     */
    private MethodSpec generateToPlutusDataMethod(ClassDefinition classDef) {
        TypeName objTypeName = bestGuess(classDef.getObjType());
        // Create the serialize method
        MethodSpec.Builder serializeMethodBuilder = MethodSpec.methodBuilder("toPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(ConstrPlutusData.class)
                .addParameter(objTypeName, "obj");

        var serializeBody = CodeBlock.builder()
                .addStatement("$T constr = initConstr($L)", ConstrPlutusData.class, classDef.getAlternative());

        for (Field field : classDef.getFields()) {
            CodeBlock codeBlock;
            switch (field.getFieldType().getType()) {
                case INTEGER:
                case BYTES:
                case STRING:
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .addStatement("constr.getData().add(toPlutusData(obj.$L))", fieldOrGetterName(field))
                            .add("\n")
                            .build();
                    break;
                case LIST:
                    String outputListName = field.getName() + "ListPlutusData";
                    var listCodeBlock = generateNestedListCode(field.getFieldType(), field.getName(), outputListName, "item", "obj." + fieldOrGetterName(field));
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .add(listCodeBlock)
                            .addStatement("constr.getData().add($L)", outputListName)
                            .add("\n")
                            .build();
                    break;
                case MAP:
                    String outputMapName = field.getName() + "MapPlutusData";
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .add(generateNestedMapCode(field.getFieldType(), outputMapName, "entry", "obj." + fieldOrGetterName(field)))
                            .addStatement("constr.getData().add($L)", outputMapName)
                            .add("\n")
                            .build();
                    break;
                case OPTIONAL:
                    /*** Sample Optional Code
                    if (obj.isEmpty())
                        return ConstrPlutusData.builder()
                                .alternative(1)
                                .data(ListPlutusData.of()).build();
                    else
                        return ConstrPlutusData.builder()
                                .alternative(0)
                                .data(ListPlutusData.of(toPlutusData(obj.get())))
                                .build();
                     ****/
                    codeBlock = generateOptionalSerializationCode(field);
                    break;

                case CONSTRUCTOR:
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .addStatement("constr.getData().add(new $LConverter().toPlutusData(obj.$L))", field.getFieldType().getJavaType().getName(), fieldOrGetterName(field))
                            .add("\n")
                            .build();
                    break;
                case BOOL:
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .addStatement("constr.getData().add(toPlutusData(obj.$L))", fieldOrGetterName(field))
                            .add("\n")
                            .build();
                    break;
                default:
                    throw new RuntimeException("Unsupported type : " + field.getFieldType().getType());

            }

            if (codeBlock != null) {
                serializeBody.add(codeBlock);
            }
        }

        serializeBody.addStatement("return constr");
        CodeBlock serializeBodyCodeBlock = serializeBody.build();
        return serializeMethodBuilder.addCode(serializeBodyCodeBlock).build();
    }

    /**
     * Generate code to convert primitive types to PlutusData
     * @param itemType
     * @param fieldOrGetterName
     * @return CodeBlock as String
     */
    private String toPlutusDataCodeBlock(FieldType itemType, String fieldOrGetterName) {
        switch (itemType.getType()) {
            case INTEGER:
            case BYTES:
            case STRING:
            case BOOL:
                return "toPlutusData(" + fieldOrGetterName + ")";
            default:
                return String.format("new %sConverter().toPlutusData(%s)", itemType.getJavaType().getName(), fieldOrGetterName);
        }
    }

    /**
     * Generate CodeBlock for null check
     * @param field
     * @param fieldOrGetterName
     * @return CodeBlock
     */
    private CodeBlock nullCheckStatement(Field field, String fieldOrGetterName) {
        CodeBlock.Builder nullCheckBuilder = CodeBlock.builder();
        nullCheckBuilder.addStatement("$T.requireNonNull(obj.$L, \"$L cannot be null\")", Objects.class, fieldOrGetterName, field.getName());

        return nullCheckBuilder.build();
    }

    private CodeBlock nullCheckStatement(String fieldName, String fieldOrGetterName) {
        CodeBlock.Builder nullCheckBuilder = CodeBlock.builder();
        nullCheckBuilder.addStatement("$T.requireNonNull(obj.$L, \"$L cannot be null\")", Objects.class, fieldOrGetterName, fieldName);

        return nullCheckBuilder.build();
    }

    /**
     * Generate CodeBlock for Nested List. This is a recursive function.
     * @param fieldType FieldType of the field
     * @param fieldName Name of the field
     * @param outputVarName Name of the output variable. This is used to store the final result
     * @param loopVarName Name of the loop variable. This is used to iterate over the list
     * @param objectName Name of the object. This is used to access the list. It could be a field or a getter method
     * @return CodeBlock
     */
    private CodeBlock generateNestedListCode(FieldType fieldType, String fieldName, String outputVarName, String loopVarName, String objectName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();

        codeBlockBuilder.addStatement("$T $L = $T.builder().build()", ListPlutusData.class, outputVarName, ListPlutusData.class)
                .beginControlFlow("for(var $L: $L)", loopVarName, objectName);

        if (genericType.isList()) {
            String innerListName = fieldName + "_$inner";
            String innerLoopVarName = loopVarName + "_$inner";
            String innerOutputVarName = outputVarName + "_$inner";
            var nestedListCodeBlock = generateNestedListCode(genericType, innerListName, innerOutputVarName, innerLoopVarName,loopVarName);
            codeBlockBuilder.add(nestedListCodeBlock)
                    .addStatement("$L.add($L)", outputVarName, innerOutputVarName);
        } else if (genericType.isMap()) {
           // String innerMapName = fieldName + "_$inner";
            String innerLoopVarName = loopVarName + "_$inner";
            String innerOutputVarName = outputVarName + "_$inner";
            var nestedMapCodeBlock = generateNestedMapCode(genericType, innerOutputVarName, innerLoopVarName, loopVarName);
            codeBlockBuilder.add(nestedMapCodeBlock)
                    .addStatement("$L.add($L)", outputVarName, innerOutputVarName);
        } else if (genericType.getType() == Type.OPTIONAL) {
            String returnVarName = loopVarName + "_$optional";
            CodeBlock optionalCodeBlock = generateNestedOptionalSerializationCode(genericType, loopVarName, returnVarName);
            codeBlockBuilder.add(optionalCodeBlock);
            codeBlockBuilder.addStatement("$L.add($L)", outputVarName, returnVarName);
        } else {
            codeBlockBuilder.addStatement("$L.add($L)", outputVarName, toPlutusDataCodeBlock(genericType, loopVarName));
        }

        codeBlockBuilder.endControlFlow();

        return codeBlockBuilder.build();
    }

    /**
     * Generate CodeBlock for Nested Map. This is a recursive function.
     * @param fieldType FieldType of the field
     * @param outputVarName Name of the output variable. This is used to store the final result
     * @param entryVarName Name of the entry variable. This is used to iterate over the map
     * @param mapObjectName Name of the object. This is used to access the map. It could be a field or a getter method
     * @return CodeBlock
     */
    private CodeBlock generateNestedMapCode(FieldType fieldType, String outputVarName, String entryVarName, String mapObjectName) {
        FieldType keyType = fieldType.getGenericTypes().get(0);
        FieldType valueType = fieldType.getGenericTypes().get(1);

        CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
        codeBlockBuilder.addStatement("$T $L = $T.builder().build();", MapPlutusData.class, outputVarName, MapPlutusData.class)
                .beginControlFlow("for(var $L: $L.entrySet())", entryVarName, mapObjectName);

        String keyOutputVarName = outputVarName + "_$key";
        String valueOutputVarName = outputVarName + "_$value";
        CodeBlock keyCodeBlock = generateCodeForKeyOrValue(keyType,   entryVarName + "Key", keyOutputVarName, entryVarName + ".getKey()");
        CodeBlock valueCodeBlock = generateCodeForKeyOrValue(valueType,  entryVarName + "Value", valueOutputVarName, entryVarName + ".getValue()");
        codeBlockBuilder.add(keyCodeBlock)
                        .add(valueCodeBlock);

        codeBlockBuilder
                .addStatement("$L.put($L, $L)", outputVarName, keyOutputVarName, valueOutputVarName)
                .endControlFlow();

        return codeBlockBuilder.build();
    }

    private CodeBlock generateCodeForKeyOrValue(FieldType type, String name, String outputVarName, String objName) {
        if (type.isList()) {
            return generateNestedListCode(type, name, outputVarName, name + "Item", objName);
        } else if (type.isMap()) {
            return generateNestedMapCode(type, outputVarName, name + "Entry", objName);
        } else if (type.getType() == Type.OPTIONAL) {
            CodeBlock optionalCodeBlock = generateNestedOptionalSerializationCode(type, name, outputVarName);
            CodeBlock codeBlock = CodeBlock.builder()
                    .addStatement("var $L = $L", name, objName)
                    .add(optionalCodeBlock)
                    .build();
            return codeBlock;
        } else {
            return CodeBlock.builder().addStatement("var $L = $L", outputVarName, toPlutusDataCodeBlock(type, objName)).build();
        }
    }

    /**
     * Generate CodeBlock for Optional serialization. This handles only top level Optional serialization.
     * @param field Field
     * @return CodeBlock
     */
    private CodeBlock generateOptionalSerializationCode(Field field) {
        FieldType fieldType = field.getFieldType();
        String fieldName = field.getName();
        String fieldOrGetterName = fieldOrGetterName(field);

        return generateOptionalSerializationCode(fieldType, fieldName, fieldOrGetterName);
    }

    // This only handles top level Optional serialization. Nested Optional serialization is handled by generateNestedOptionalSerializationCode()
    private CodeBlock generateOptionalSerializationCode(FieldType fieldType, String fieldName, String fieldOrGetterName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        CodeBlock nestedBlock = null;
        String nestedVarName = fieldName + "_$nested";
        if (!genericType.isCollection()) {
            nestedBlock = CodeBlock.builder()
                    .add("var $L=$L;",nestedVarName,
                            toPlutusDataCodeBlock(fieldType.getGenericTypes().get(0), "obj." + fieldOrGetterName + ".get()"))
                    .build();
        } else {
            if (genericType.isList()) {
                nestedBlock = generateNestedListCode(genericType, fieldName, nestedVarName, "item", "obj." + fieldOrGetterName + ".get()");
            } else if (genericType.isMap()) {
                nestedBlock = generateNestedMapCode(genericType, nestedVarName, "entry", "obj." + fieldOrGetterName + ".get()");
            } else {
                throw new RuntimeException("Unsupported type " + genericType);
            }
        }

        return CodeBlock.builder()
                .add("//Field $L\n", fieldName)
                .add(nullCheckStatement(fieldName, fieldOrGetterName))
                .beginControlFlow("if(obj.$L.isEmpty())", fieldOrGetterName)
                .addStatement("var $LConstr = $T.builder().alternative(1).data($T.of()).build()", fieldName,
                        ConstrPlutusData.class, ListPlutusData.class)
                .addStatement("constr.getData().add($LConstr)", fieldName)
                .nextControlFlow("else")
                .add(nestedBlock)
                .addStatement("var $LConstr = $T.builder().alternative(0).data($T.of($L)).build()", fieldName, ConstrPlutusData.class, ListPlutusData.class,
                        nestedVarName)
                .addStatement("constr.getData().add($LConstr)", fieldName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    /**
     * Generate serialization CodeBlock for Nested Optional in Collection, not top level Optional.
     * For example:
     * <p>
     *     {@literal
     *     List<Optional<List<List<String>>>> opt
     *     }
     * </p>
     * <p>
     *     {@literal
     *     Map<Optional<Map<String, List<Optional<String>>>>, Optional<List<BigInteger>>> optMap
     *     }
     * </p>
     * @param fieldType
     * @param varName
     * @param outputVarName
     * @return
     */
    private CodeBlock generateNestedOptionalSerializationCode(FieldType fieldType, String varName, String outputVarName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        CodeBlock nestedBlock = null;
        String nestedVarName = varName + "_$nested";
        if (!genericType.isCollection()) {
            nestedBlock = CodeBlock.builder()
                    .add("var $L=$L;",nestedVarName,
                            toPlutusDataCodeBlock(fieldType.getGenericTypes().get(0), varName + ".get()"))
                    .build();
        } else {
            if (genericType.isList()) {
                String innerLoopVarName = varName + "_$inner";

                nestedBlock = generateNestedListCode(genericType, varName, nestedVarName, innerLoopVarName, varName + ".get()");
            } else if (genericType.isMap()) {
                String innerEntryVarName = varName + "_$inner";
                nestedBlock = generateNestedMapCode(genericType, nestedVarName, innerEntryVarName, varName + ".get()");
            } else {
                throw new RuntimeException("Unsupported type " + genericType);
            }
        }

        return CodeBlock.builder()
                .add("//Field $L\n", varName)
                .addStatement("$T.requireNonNull($L, \"$L\")", Objects.class, varName, varName + " must not be null")
                .addStatement("$T $L = null", ConstrPlutusData.class, outputVarName)
                .beginControlFlow("if($L.isEmpty())", varName)
                .addStatement("$L = $T.builder().alternative(1).data($T.of()).build()", outputVarName,
                        ConstrPlutusData.class, ListPlutusData.class)
                //.addStatement("$L.getPlutusDataList().add($LConstr)", outputVarName, varName)
                .nextControlFlow("else")
                .add(nestedBlock)
                .add("\n")
                .addStatement("$L = $T.builder().alternative(0).data($T.of($L)).build()", outputVarName, ConstrPlutusData.class, ListPlutusData.class,
                        nestedVarName)
               // .addStatement("$L.getPlutusDataList().add($LConstr)", outputVarName, varName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    // ---- Deserialize methods

    /**
     * Generate the deserialize method
     * @param classDef ClassDefinition
     * @return MethodSpec
     */
    private MethodSpec generateFromPlutusDataMethod(ClassDefinition classDef) {
        TypeName objTypeName = bestGuess(classDef.getObjType());
        // Create the serialize method
        MethodSpec.Builder deserializeMethodBuilder = MethodSpec.methodBuilder("fromPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(objTypeName)
                .addParameter(ConstrPlutusData.class, "constr");

        CodeBlock initObjCodeBlock = CodeBlock.builder()
                .addStatement("var obj = new $T()", objTypeName)
                .addStatement("var data = constr.getData()")
                .build();

        CodeBlock.Builder bodyCodeBlock = CodeBlock.builder();
        for (Field field : classDef.getFields()) {
            CodeBlock codeBlock = getDeserializeCodeBlockForField(field);
            if (codeBlock != null) {
                bodyCodeBlock.add("\n");
                bodyCodeBlock.add(codeBlock);
                CodeBlock.Builder assignmentBlock = CodeBlock.builder();
                if (field.isHashGetter()) {
                    assignmentBlock.addStatement("obj.$L($L)", setterName(field.getName()), field.getName());
                } else {
                    assignmentBlock.addStatement("obj.$L = $L", field.getName(), field.getName());
                }
                bodyCodeBlock.add(assignmentBlock.build());
            }
        }

        CodeBlock returnObjCodeBlock = CodeBlock.builder()
                .addStatement("return obj")
                .build();
        return deserializeMethodBuilder
                .addCode(initObjCodeBlock)
                .addCode(bodyCodeBlock.build())
                .addCode(returnObjCodeBlock)
                .build();
    }

    /**
     * Generate the deserialize code block for a field. This is the main method that generates the code for the field
     * @param field
     * @return CodeBlock
     */
    private CodeBlock getDeserializeCodeBlockForField(Field field) {
        CodeBlock codeBlock = null;
        switch (field.getFieldType().getType()) {
            case INTEGER:
                String getValueMethodName = getValueMethodNameForIntType(field.getFieldType());
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("var $L = (($T)data.getPlutusDataList().get($L)).$L",
                                field.getName(), BigIntPlutusData.class, field.getIndex(), getValueMethodName)
                        .build();
                break;
            case BYTES:
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("var $L = (($T)data.getPlutusDataList().get($L)).getValue()",
                                field.getName(), BytesPlutusData.class, field.getIndex())
                        .build();
                break;
            case STRING:
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("var $L = deserializeBytesToString((($T)data.getPlutusDataList().get($L)).getValue(), $S)",
                                field.getName(), BytesPlutusData.class, field.getIndex(), field.getFieldType().getEncoding())
                        .build();
                break;
            case LIST:
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("var $LList = (ListPlutusData)data.getPlutusDataList().get($L)", field.getName(), field.getIndex())
                        .add(generateListDeserializeCode(field.getFieldType(), field.getName(), field.getName() + "List", "item"))
                       // .add("\n")
                        .build();
                break;
            case MAP:
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("var $LMap = (MapPlutusData)data.getPlutusDataList().get($L)", field.getName(), field.getIndex())
                        .add(generateMapDeserializeCode(field.getFieldType(), field.getName(), field.getName() + "Map", "entry"))
                        .build();
                break;
            case OPTIONAL:
//                var optConstr = (ConstrPlutusData)data.getPlutusDataList().get(2);
//                if (optConstr.getAlternative() == 1) {
//                    obj.setOpt(Optional.empty());
//                } else {
//                    AnotherData opt = new AnotherDataSerializer().deserialize(((ListPlutusData)optConstr.getData().get(0)).getPlutusDataList().get(0));
//                    obj.setOpt(Optional.of(opt));
//                }

                codeBlock = generateOptionalDeserializationCode(field);
                break;
            case CONSTRUCTOR:
                TypeName fieldTypeName = bestGuess(field.getFieldType().getJavaType().getName());
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("$T $L = new $LConverter().fromPlutusData((($T)data.getPlutusDataList().get($L)))",
                                fieldTypeName, field.getName(), fieldTypeName, ConstrPlutusData.class, field.getIndex())
                        .build();
                break;
            case BOOL:
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .add("$T $L = null;\n", Boolean.class, field.getName())
                        .addStatement("var $LConstr = (($T)data.getPlutusDataList().get($L))",
                                field.getName(), ConstrPlutusData.class, field.getIndex())
                        .beginControlFlow("if($LConstr.getAlternative() == 0)", field.getName())
                        .addStatement("$L = false", field.getName())
                        .nextControlFlow("else")
                        .addStatement("$L = true", field.getName())
                        .endControlFlow()
                        .add("\n")
                        .build();
                break;
            default:
                throw new RuntimeException("Unsupported type : " + field.getFieldType().getType());

        }
        return codeBlock;
    }

    /**
     * Generate deserialize code block for lists. It is a recursive method that calls itself when the list contains another collection
     * @param fieldType The type of the field
     * @param fieldName The name of the field
     * @param pdListName The name of the PlutusData list
     * @param itemVarName The name of the variable that will be used during iteration
     * @return CodeBlock
     */
    private CodeBlock generateListDeserializeCode(FieldType fieldType, String fieldName, String pdListName, String itemVarName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();

        codeBlockBuilder
                .addStatement("$L $L = new $T<>()", fieldType.getFqTypeName(), fieldName, ArrayList.class)
                .beginControlFlow("for(var $L: (($T)$L).getPlutusDataList())", itemVarName, ListPlutusData.class, pdListName);

        if (genericType.isList()) {
            String innerListName = fieldName + "_$inner";
            String innerItemVarName = itemVarName+ "_$inner";

            CodeBlock innerListDeserializeCodeBlock = generateListDeserializeCode(genericType, innerListName, itemVarName, innerItemVarName);
            codeBlockBuilder.add(innerListDeserializeCodeBlock)
                    .addStatement("$L.add($L)", fieldName, innerListName);
        } else if (genericType.isMap()) {
            String innerMapName = fieldName + "_$inner";
            String innerEntryVarName = itemVarName+ "_$inner";
            CodeBlock innerMapDeserializeCodeBlock = generateMapDeserializeCode(genericType, innerMapName, itemVarName, innerEntryVarName);
            codeBlockBuilder.add(innerMapDeserializeCodeBlock)
                    .addStatement("$L.add($L)", fieldName, innerMapName);
        } else if (genericType.getType() == Type.OPTIONAL) {
            String returnVarName = itemVarName + "_$optional";
            String innerItemVarName = itemVarName+ "_$inner";

            codeBlockBuilder.addStatement("var $L = ($T)$L", innerItemVarName, ConstrPlutusData.class, itemVarName);
            CodeBlock optionalCodeBlock = generateNestedOptionalDeserializationCode(genericType, innerItemVarName, returnVarName);
            codeBlockBuilder.add(optionalCodeBlock);
            codeBlockBuilder.addStatement("$L.add($L)", fieldName, returnVarName);
        } else {
            codeBlockBuilder.addStatement("var o = $L", fromPlutusDataToObj(genericType, itemVarName))
                    .addStatement("$L.add(o)", fieldName);
        }

        codeBlockBuilder.endControlFlow();

        return codeBlockBuilder.build();
    }

    /**
     * Generate deserialize code block for maps. It is a recursive method that calls itself when the map contains another collection
     * @param fieldType The type of the field
     * @param fieldName The name of the field
     * @param pdMapName The name of the PlutusData map
     * @param entryVarName The name of the variable that will be used during iteration
     * @return CodeBlock
     */
    private CodeBlock generateMapDeserializeCode(FieldType fieldType, String fieldName, String pdMapName, String entryVarName) {
        FieldType keyType = fieldType.getGenericTypes().get(0);
        FieldType valueType = fieldType.getGenericTypes().get(1);

        CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
        codeBlockBuilder
                .addStatement("$L $L = new $T<>()", fieldType.getFqTypeName(), fieldName, LinkedHashMap.class)
                .beginControlFlow("for(var $L: (($T)$L).getMap().entrySet())", entryVarName, MapPlutusData.class, pdMapName);

        String innerEntryVarName = entryVarName+ "_$inner";
        String keyOutputVarName = fieldName + "Key";
        String valueOutputVarName = fieldName + "Value";
        CodeBlock keyDeserializeCodeBlock = generateDeserializeCodeBlock(keyType, keyOutputVarName, entryVarName + ".getKey()", innerEntryVarName + "_$key");
        CodeBlock valueDeserializeCodeBlock = generateDeserializeCodeBlock(valueType, valueOutputVarName, entryVarName + ".getValue()", innerEntryVarName + "_$value");

        codeBlockBuilder.add(keyDeserializeCodeBlock)
                .add(valueDeserializeCodeBlock)
                .addStatement("$L.put($L, $L)", fieldName, keyOutputVarName, valueOutputVarName)
                .endControlFlow();

        return codeBlockBuilder.build();
    }

    private CodeBlock generateDeserializeCodeBlock(FieldType type, String fieldName, String pdName, String itemVarName) {
        if (type.isList()) {
            return generateListDeserializeCode(type, fieldName, pdName, itemVarName);
        } else if (type.isMap()) {
            return generateMapDeserializeCode(type, fieldName, pdName, itemVarName);
        } else if (type.getType() == Type.OPTIONAL) {
            CodeBlock optionalCodeBlock = generateNestedOptionalDeserializationCode(type, itemVarName, fieldName); //fieldName == outputVarName
            CodeBlock codeBlock = CodeBlock.builder()
                    .addStatement("var $L = ($T)$L", itemVarName, ConstrPlutusData.class, pdName)
                    .add(optionalCodeBlock)
                    .build();
            return codeBlock;
        } else {
            return CodeBlock.builder().addStatement("var $L = $L", fieldName, fromPlutusDataToObj(type, pdName)).build();
        }
    }

    /**
     * Generate code block for optional fields (top level)
     * @param field
     * @return CodeBlock
     */
    private CodeBlock generateOptionalDeserializationCode(Field field) {
        CodeBlock nestedBlock = null;
        String nestedVarName = field.getName() + "_$nested";

        FieldType genericType = field.getFieldType().getGenericTypes().get(0);

        if (genericType.isList()) {
            nestedBlock = generateListDeserializeCode(genericType, nestedVarName, field.getName() + "PlutusData", "item");
        } else if (genericType.isMap()) {
            nestedBlock = generateMapDeserializeCode(genericType, nestedVarName, field.getName() + "PlutusData", "entry");
        } else {
            nestedBlock = CodeBlock.builder()
                    .addStatement("var $L=$L;",nestedVarName,
                            fromPlutusDataToObj(field.getFieldType().getGenericTypes().get(0), field.getName() + "PlutusData"))
                    .build();
        }

        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add("$L $L = null;\n", field.getFieldType().getFqTypeName(), field.getName())
                .addStatement("var $LConstr = (ConstrPlutusData)data.getPlutusDataList().get($L)", field.getName(), field.getIndex())
                .beginControlFlow("if($LConstr.getAlternative() == 1)", field.getName())
                .addStatement("$L = $T.empty()", field.getName(), Optional.class)
                .nextControlFlow("else")
                .addStatement("var $LPlutusData = $LConstr.getData().getPlutusDataList().get(0)", field.getName(), field.getName())
                .add(nestedBlock)
                .addStatement("$L = Optional.ofNullable($L)",
                        field.getName(), nestedVarName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    //-- Handle when Optional is nested inside another collection
    private CodeBlock generateNestedOptionalDeserializationCode(FieldType fieldType, String varName, String outputVarName) {
        CodeBlock nestedBlock = null;
        String nestedVarName = varName + "_$nested";
        FieldType genericType = fieldType.getGenericTypes().get(0);

        if (genericType.isList()) {
            String innerVarName = varName + "_$inner";
            nestedBlock = generateListDeserializeCode(genericType, nestedVarName, varName + "PlutusData", innerVarName);
        } else if (genericType.isMap()) {
            String innerEntryName = varName + "_$inner";
            nestedBlock = generateMapDeserializeCode(genericType, nestedVarName, varName + "PlutusData", innerEntryName);
        } else {
            nestedBlock = CodeBlock.builder()
                    .addStatement("var $L=$L;",nestedVarName,
                            fromPlutusDataToObj(fieldType.getGenericTypes().get(0), varName + "PlutusData"))
                    .build();
        }

        return CodeBlock.builder()
                .add("//Field $L\n", varName)
                .add("$L $L = null;\n", fieldType.getFqTypeName(), outputVarName)
                .beginControlFlow("if($L.getAlternative() == 1)", varName)
                .addStatement("$L = $T.empty()", outputVarName, Optional.class)
                .nextControlFlow("else")
                .addStatement("var $LPlutusData = $L.getData().getPlutusDataList().get(0)", varName, varName)
                .add(nestedBlock)
                .addStatement("$L = Optional.ofNullable($L)",
                        outputVarName, nestedVarName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    private String fromPlutusDataToObj(FieldType itemType, String fieldName) {
        switch (itemType.getType()) {
            case INTEGER:
                String getValueMethodName = getValueMethodNameForIntType(itemType);
                if (itemType.getJavaType() == JavaType.INT || itemType.getJavaType() == JavaType.INTEGER)
                    return String.format("plutusDataToInteger(%s)", fieldName);
                else if (itemType.getJavaType() == JavaType.LONG || itemType.getJavaType() == JavaType.LONG_OBJECT)
                    return String.format("plutusDataToLong(%s)", fieldName);
                else if (itemType.getJavaType() == JavaType.BIGINTEGER)
                    return String.format("plutusDataToBigInteger(%s)", fieldName);
                break;
            case BYTES:
                return String.format("plutusDataToBytes(%s)", fieldName);
            case STRING:
                if (itemType.getEncoding() == null)
                    return String.format("plutusDataToString(%s, null)", fieldName);
                else
                    return String.format("plutusDataToString(%s, \"%s\")", fieldName, itemType.getEncoding());
            case BOOL:
                return String.format("plutusDataToBoolean(%s)", fieldName);
            default:
                return String.format("new %sConverter().fromPlutusData((ConstrPlutusData)%s)", itemType.getJavaType().getName(), fieldName);
        }

        return "";
    }

    private static String getValueMethodNameForIntType(FieldType fieldType) {
        String getValueMethodName = "getValue()";
        if (fieldType.getJavaType() == JavaType.INT || fieldType.getJavaType() == JavaType.INTEGER)
            getValueMethodName = "getValue().intValue()";
        else if (fieldType.getJavaType() == JavaType.LONG || fieldType.getJavaType() == JavaType.LONG_OBJECT)
            getValueMethodName = "getValue().longValue()";

        return getValueMethodName;
    }

    private String fieldOrGetterName(Field field) {
        if (field.isHashGetter()) {
            return field.getGetterName() + "()";
        } else {
            return field.getName();
        }
    }

    private String setterName(String fieldName) {
        return "set" + capitalize(fieldName);
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private TypeName bestGuess(String name) {
        if ("int".equals(name))
            return ClassName.get(Integer.class);
        else if ("long".equals(name))
            return ClassName.get(Long.class);
        else if ("byte[]".equals(name))
            return ArrayTypeName.of(TypeName.BYTE);
        else
            return ClassName.bestGuess(name);
    }
}
