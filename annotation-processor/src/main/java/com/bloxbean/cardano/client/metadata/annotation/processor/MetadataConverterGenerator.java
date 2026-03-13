package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.CollectionCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.EnumCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.MapCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.NestedTypeCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.OptionalCodeGen;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

/**
 * Generates a {@code {ClassName}MetadataConverter} class using JavaPoet.
 * The generated class contains:
 * <ul>
 *   <li>{@code toMetadataMap(T obj) -> MetadataMap}</li>
 *   <li>{@code fromMetadataMap(MetadataMap map) -> T}</li>
 *   <li>Optionally: {@code toMetadata(T obj) -> Metadata} and {@code fromMetadata(Metadata) -> T}
 *       when {@code @MetadataType(label=N)} is specified.</li>
 * </ul>
 *
 * <p>This is a slim facade that delegates type-specific code generation to
 * strategy classes via {@link MetadataTypeCodeGenRegistry}.
 */
public class MetadataConverterGenerator {

    static final String CONVERTER_SUFFIX = "MetadataConverter";

    private final MetadataTypeCodeGenRegistry registry;
    private final MetadataFieldAccessor accessor;
    private final EnumCodeGen enumCodeGen;
    private final NestedTypeCodeGen nestedCodeGen;
    private final CollectionCodeGen collectionCodeGen;
    private final OptionalCodeGen optionalCodeGen;
    private final MapCodeGen mapCodeGen;

    public MetadataConverterGenerator() {
        this.registry = new MetadataTypeCodeGenRegistry();
        this.accessor = new MetadataFieldAccessor();
        this.enumCodeGen = new EnumCodeGen(accessor);
        this.nestedCodeGen = new NestedTypeCodeGen(accessor);
        this.collectionCodeGen = new CollectionCodeGen(registry, accessor, enumCodeGen);
        this.collectionCodeGen.setNestedCodeGen(nestedCodeGen);
        this.optionalCodeGen = new OptionalCodeGen(registry, accessor, enumCodeGen);
        this.optionalCodeGen.setNestedCodeGen(nestedCodeGen);
        this.mapCodeGen = new MapCodeGen(registry, accessor, enumCodeGen, nestedCodeGen);
    }

    public TypeSpec generate(String packageName, String simpleClassName, List<MetadataFieldInfo> fields) {
        return generate(packageName, simpleClassName, fields, -1);
    }

    public TypeSpec generate(String packageName, String simpleClassName, List<MetadataFieldInfo> fields, long label) {
        ClassName targetClass = ClassName.get(packageName, simpleClassName);
        String converterClassName = simpleClassName + CONVERTER_SUFFIX;

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(converterClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(GENERATED_CODE);

        classBuilder.addMethod(buildToMetadataMapMethod(targetClass, simpleClassName, fields));
        classBuilder.addMethod(buildFromMetadataMapMethod(targetClass, fields));

        // Feature 3: Label — generate toMetadata / fromMetadata if label >= 0
        if (label >= 0) {
            classBuilder.addMethod(buildToMetadataMethod(targetClass, simpleClassName, label));
            classBuilder.addMethod(buildFromMetadataMethod(targetClass, label));
        }

        return classBuilder.build();
    }

    // -------------------------------------------------------------------------
    // toMetadataMap
    // -------------------------------------------------------------------------

    private MethodSpec buildToMetadataMapMethod(ClassName targetClass, String simpleClassName,
                                                List<MetadataFieldInfo> fields) {
        String paramName = firstLowerCase(simpleClassName);

        MethodSpec.Builder builder = MethodSpec.methodBuilder("toMetadataMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(MetadataMap.class)
                .addParameter(targetClass, paramName);

        builder.addStatement("$T map = $T.createMap()", MetadataMap.class, MetadataBuilder.class);

        for (MetadataFieldInfo field : fields) {
            String getExpr = buildGetExpression(paramName, field);
            String javaType = field.getJavaTypeName();
            boolean needsNullCheck = needsNullCheck(javaType, field);

            if (needsNullCheck) {
                builder.beginControlFlow("if ($L != null)", getExpr);
            }

            emitToMapPut(builder, field, getExpr);

            if (needsNullCheck) {
                builder.endControlFlow();
            }
        }

        builder.addStatement("return map");
        return builder.build();
    }

    private void emitToMapPut(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        String key = field.getMetadataKey();
        String javaType = field.getJavaTypeName();
        MetadataFieldType enc = field.getEnc();

        // Map<String, V>
        if (field.isMapType()) {
            mapCodeGen.emitSerializeToMap(builder, field, getExpr);
            return;
        }

        // Collections
        if (field.isCollectionType()) {
            collectionCodeGen.emitSerializeToMap(builder, field, getExpr);
            return;
        }

        // Optional
        if (field.isOptionalType()) {
            if (field.isElementNestedType()) {
                optionalCodeGen.emitSerializeToMap(builder, field, getExpr);
            } else if (field.isElementEnumType()) {
                enumCodeGen.emitSerializeOptionalToMap(builder, field, getExpr);
            } else {
                optionalCodeGen.emitSerializeToMap(builder, field, getExpr);
            }

            return;
        }

        // Nested @MetadataType
        if (field.isNestedType()) {
            nestedCodeGen.emitSerializeToMap(builder, field, getExpr);
            return;
        }

        // Enum
        if (field.isEnumType()) {
            enumCodeGen.emitSerializeToMap(builder, field, getExpr);
            return;
        }

        // byte[] encoding variants
        switch (enc) {
            case STRING_HEX -> {
                registry.getByteArrayCodeGen().emitSerializeHex(builder, key, getExpr);
                return;
            }
            case STRING_BASE64 -> {
                registry.getByteArrayCodeGen().emitSerializeBase64(builder, key, getExpr);
                return;
            }
            default -> { /* handled below as scalar */ }
        }

        // Scalar dispatch
        MetadataTypeCodeGen codeGen = registry.get(javaType);
        if (enc == MetadataFieldType.STRING) {
            codeGen.emitSerializeToMapString(builder, key, getExpr, javaType);
        } else {
            codeGen.emitSerializeToMapDefault(builder, key, getExpr, javaType);
        }
    }

    // -------------------------------------------------------------------------
    // fromMetadataMap
    // -------------------------------------------------------------------------

    private MethodSpec buildFromMetadataMapMethod(ClassName targetClass, List<MetadataFieldInfo> fields) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("fromMetadataMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(targetClass)
                .addParameter(MetadataMap.class, "map");

        builder.addStatement("$T obj = new $T()", targetClass, targetClass);
        builder.addStatement("$T v", Object.class);

        for (MetadataFieldInfo field : fields) {
            builder.addStatement("v = map.get($S)", field.getMetadataKey());
            emitFromMapGet(builder, field);
        }

        builder.addStatement("return obj");
        return builder.build();
    }

    private void emitFromMapGet(MethodSpec.Builder builder, MetadataFieldInfo field) {
        String javaType = field.getJavaTypeName();
        MetadataFieldType enc = field.getEnc();

        // Map<String, V>
        if (field.isMapType()) {
            mapCodeGen.emitDeserializeFromMap(builder, field);
            return;
        }

        // Collections
        if (field.isCollectionType()) {
            collectionCodeGen.emitDeserializeFromMap(builder, field);
            return;
        }

        // Optional
        if (field.isOptionalType()) {
            optionalCodeGen.emitDeserializeFromMap(builder, field);
            return;
        }

        // Nested @MetadataType
        if (field.isNestedType()) {
            nestedCodeGen.emitDeserializeScalar(builder, field);
            return;
        }

        // Enum
        if (field.isEnumType()) {
            enumCodeGen.emitDeserializeScalar(builder, field);
            return;
        }

        // byte[] encoding variants
        switch (enc) {
            case STRING_HEX:
                registry.getByteArrayCodeGen().emitDeserializeHex(builder, field, accessor);
                return;
            case STRING_BASE64:
                registry.getByteArrayCodeGen().emitDeserializeBase64(builder, field, accessor);
                return;
            default:
                break;
        }

        // Scalar dispatch
        MetadataTypeCodeGen codeGen = registry.get(javaType);
        if (enc == MetadataFieldType.STRING) {
            codeGen.emitDeserializeScalarString(builder, field, accessor);
        } else {
            codeGen.emitDeserializeScalarDefault(builder, field, accessor);
        }
    }

    // -------------------------------------------------------------------------
    // toMetadata / fromMetadata (Feature 3: Label)
    // -------------------------------------------------------------------------

    private MethodSpec buildToMetadataMethod(ClassName targetClass, String simpleClassName, long label) {
        String paramName = firstLowerCase(simpleClassName);

        return MethodSpec.methodBuilder("toMetadata")
                .addModifiers(Modifier.PUBLIC)
                .returns(Metadata.class)
                .addParameter(targetClass, paramName)
                .addStatement("$T metadata = $T.createMetadata()", Metadata.class, MetadataBuilder.class)
                .addStatement("metadata.put($T.valueOf($LL), toMetadataMap($L))",
                        BigInteger.class, label, paramName)
                .addStatement("return metadata")
                .build();
    }

    private MethodSpec buildFromMetadataMethod(ClassName targetClass, long label) {
        return MethodSpec.methodBuilder("fromMetadata")
                .addModifiers(Modifier.PUBLIC)
                .returns(targetClass)
                .addParameter(Metadata.class, "metadata")
                .addStatement("$T raw = metadata.get($T.valueOf($LL))",
                        Object.class, BigInteger.class, label)
                .beginControlFlow("if (raw instanceof $T)", MetadataMap.class)
                .addStatement("return fromMetadataMap(($T) raw)", MetadataMap.class)
                .endControlFlow()
                .addStatement("throw new $T($S + $L)",
                        IllegalArgumentException.class, "Expected MetadataMap at label ", label)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildGetExpression(String paramName, MetadataFieldInfo field) {
        if (field.getGetterName() != null) {
            return paramName + "." + field.getGetterName() + "()";
        } else {
            return paramName + "." + field.getJavaFieldName();
        }
    }

    private boolean needsNullCheck(String javaType, MetadataFieldInfo field) {
        if (field.isEnumType() || field.isNestedType() || field.isMapType()) return true;
        if (!isScalar(field)) return true;
        MetadataTypeCodeGen codeGen = registry.get(javaType);

        return codeGen.needsNullCheck(javaType);
    }

    private boolean isScalar(MetadataFieldInfo field) {
        return !field.isCollectionType() && !field.isOptionalType()
                && !field.isMapType() && !field.isNestedType() && !field.isEnumType();
    }

    private String firstLowerCase(String s) {
        if (s == null || s.isEmpty()) return s;

        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

}
