package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.squareup.javapoet.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
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

        // TypeName objTypeName = ClassName.bestGuess(classDef.getObjType());
        // Create the serialize method
        MethodSpec serializeMethod = generateSerializeMethod(classDef);
        MethodSpec deserializeMethod = generateDeserializeMethod(classDef);

        return classBuilder
                .addMethod(serializeMethod)
                .addMethod(deserializeMethod)
                .build();
    }

    private MethodSpec generateSerializeMethod(ClassDefinition classDef) {
        TypeName objTypeName = bestGuess(classDef.getObjType());
        // Create the serialize method
        MethodSpec.Builder serializeMethodBuilder = MethodSpec.methodBuilder("serialize")
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
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .addStatement("$T $LListPlutusData = $T.builder().build();", ListPlutusData.class, field.getName(), ListPlutusData.class)
                            .beginControlFlow("for(var item: obj.$L)", fieldOrGetterName(field))
                            .addStatement("$LListPlutusData.add($L)",
                                    field.getName(), toPlutusDataCodeBlock(field.getFieldType().getGenericTypes().get(0), "item"))
                            .endControlFlow()
                            .addStatement("constr.getData().add($LListPlutusData)", field.getName())
                            .add("\n")
                            .build();
                    break;
                case MAP:
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .addStatement("$T $LMapPlutusData = $T.builder().build();", MapPlutusData.class, field.getName(), MapPlutusData.class)
                            .beginControlFlow("for(var entry: obj.$L.entrySet())", fieldOrGetterName(field))
                            .addStatement("$LMapPlutusData.put($L, $L)", field.getName(),
                                    toPlutusDataCodeBlock(field.getFieldType().getGenericTypes().get(0), "entry.getKey()"),
                                    toPlutusDataCodeBlock(field.getFieldType().getGenericTypes().get(1), "entry.getValue()")
                            )
                            .endControlFlow()
                            .addStatement("constr.getData().add($LMapPlutusData)", field.getName())
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
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .beginControlFlow("if(obj.$L.isEmpty())", fieldOrGetterName(field))
                            .addStatement("var $LConstr = $T.builder().alternative(1).data($T.of()).build()", field.getName(),
                                    ConstrPlutusData.class, ListPlutusData.class)
                            .addStatement("constr.getData().add($LConstr)", field.getName())
                            .nextControlFlow("else")
                            .addStatement("var $LConstr = $T.builder().alternative(0).data($T.of($L)).build()", field.getName(), ConstrPlutusData.class, ListPlutusData.class,
                                    toPlutusDataCodeBlock(field.getFieldType().getGenericTypes().get(0), "obj." + fieldOrGetterName(field) + ".get()"))
                            .addStatement("constr.getData().add($LConstr)", field.getName())
                            .endControlFlow()
                            .add("\n")
                            .build();
                    break;

                case CONSTRUCTOR:
                    codeBlock = CodeBlock.builder()
                            .add("//Field $L\n", field.getName())
                            .add(nullCheckStatement(field, fieldOrGetterName(field)))
                            .addStatement("constr.getData().add(new $LPlutusDataConverter().serialize(obj.$L))", field.getFieldType().getJavaType().getName(), fieldOrGetterName(field))
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

    private String toPlutusDataCodeBlock(FieldType itemType, String fieldOrGetterName) {
        switch (itemType.getType()) {
            case INTEGER:
            case BYTES:
            case STRING:
                return "toPlutusData(" + fieldOrGetterName + ")";
            default:
                return String.format("new %sPlutusDataConverter().serialize(%s)", itemType.getJavaType().getName(), fieldOrGetterName);
        }
    }

    private CodeBlock nullCheckStatement(Field field, String fieldOrGetterName) {
        CodeBlock.Builder nullCheckBuilder = CodeBlock.builder();
        nullCheckBuilder.addStatement("$T.requireNonNull(obj.$L, \"$L cannot be null\")", Objects.class, fieldOrGetterName, field.getName());

        return nullCheckBuilder.build();
    }

    // ---- Deserialize method
    private MethodSpec generateDeserializeMethod(ClassDefinition classDef) {
        TypeName objTypeName = bestGuess(classDef.getObjType());
        // Create the serialize method
        MethodSpec.Builder deserializeMethodBuilder = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(objTypeName)
                .addParameter(ConstrPlutusData.class, "constr");

        CodeBlock initObjCodeBlock = CodeBlock.builder()
                .addStatement("var obj = new $T()", objTypeName)
                .addStatement("var data = constr.getData()")
                .build();

        CodeBlock.Builder bodyCodeBlock = CodeBlock.builder();
        int index = 0;
        for (Field field : classDef.getFields()) {
            CodeBlock codeBlock = getDeserializeCodeBlockForField(field);
            index++;
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
                TypeName listGenericType = bestGuess(field.getFieldType().getGenericTypes().get(0).getJavaType().getName());
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("var $LList = (ListPlutusData)data.getPlutusDataList().get($L)", field.getName(), field.getIndex())
                        .addStatement("var $L = new $T<$T>()", field.getName(), ArrayList.class, listGenericType) //TODO -- check if ArrayList of any other list is needed
                        .beginControlFlow("for(var item: $LList.getPlutusDataList())", field.getName())
                        .addStatement("var o = $L", fromPlutusDataToObj(field.getFieldType().getGenericTypes().get(0), "item"))
                        .addStatement("$L.add(o)", field.getName())
                        .endControlFlow()
                        .add("\n")
                        .build();
                break;
            case MAP:
                TypeName keyGenericType = bestGuess(field.getFieldType().getGenericTypes().get(0).getJavaType().getName());
                TypeName valueGenericType = bestGuess(field.getFieldType().getGenericTypes().get(1).getJavaType().getName());
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("var $LMap = (MapPlutusData)data.getPlutusDataList().get($L)", field.getName(), field.getIndex())
                        .addStatement("var $L = new $T<$T, $T>()", field.getName(), HashMap.class,
                                keyGenericType,
                                valueGenericType) //TODO -- check
                        .beginControlFlow("for(var entry: $LMap.getMap().entrySet())", field.getName())
                        .addStatement("var key = $L", fromPlutusDataToObj(field.getFieldType().getGenericTypes().get(0), "entry.getKey()"))
                        .addStatement("var value = $L", fromPlutusDataToObj(field.getFieldType().getGenericTypes().get(1), "entry.getValue()"))
                        .addStatement("$L.put(key, value)", field.getName())
                        .endControlFlow()
                        .add("\n")
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
                TypeName optionalGenericType = bestGuess(field.getFieldType().getGenericTypes().get(0).getJavaType().getName());
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .add("$T<$T> $L = null;\n", Optional.class, optionalGenericType, field.getName())
                        .addStatement("var $LConstr = (ConstrPlutusData)data.getPlutusDataList().get($L)", field.getName(), field.getIndex())
                        .beginControlFlow("if($LConstr.getAlternative() == 1)", field.getName())
                        .addStatement("$L = $T.empty()", field.getName(), Optional.class)
                        .nextControlFlow("else")
                        .addStatement("var $LPlutusData = $LConstr.getData().getPlutusDataList().get(0)", field.getName(), field.getName())
                        .addStatement("$L = Optional.ofNullable($L)",
                                field.getName(), fromPlutusDataToObj(field.getFieldType().getGenericTypes().get(0), field.getName() + "PlutusData"))
                        //.addStatement("obj.$L = Optional.of($L)", field.getName(), field.getName())
                        .endControlFlow()
                        .add("\n")
                        .build();
                break;
            case CONSTRUCTOR:
                TypeName fieldTypeName = bestGuess(field.getFieldType().getJavaType().getName());
                codeBlock = CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("$T $L = new $LPlutusDataConverter().deserialize((($T)data.getPlutusDataList().get($L)))",
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
                        .addStatement("$L = true", field.getName())
                        .nextControlFlow("else")
                        .addStatement("$L = false", field.getName())
                        .endControlFlow()
                        .add("\n")
                        .build();
                break;
            default:
                throw new RuntimeException("Unsupported type : " + field.getFieldType().getType());

        }
        return codeBlock;
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
            default:
                return String.format("new %sPlutusDataConverter().deserialize((ConstrPlutusData)%s)", itemType.getJavaType().getName(), fieldName);
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
