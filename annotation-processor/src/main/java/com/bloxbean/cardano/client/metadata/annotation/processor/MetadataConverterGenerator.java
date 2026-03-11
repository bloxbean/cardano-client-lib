package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.CollectionCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.EnumCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.type.OptionalCodeGen;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

/**
 * Generates a {@code {ClassName}MetadataConverter} class using JavaPoet.
 * The generated class contains:
 * <ul>
 *   <li>{@code toMetadataMap(T obj) -> MetadataMap}</li>
 *   <li>{@code fromMetadataMap(MetadataMap map) -> T}</li>
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
    private final CollectionCodeGen collectionCodeGen;
    private final OptionalCodeGen optionalCodeGen;

    public MetadataConverterGenerator() {
        this.registry = new MetadataTypeCodeGenRegistry();
        this.accessor = new MetadataFieldAccessor();
        this.enumCodeGen = new EnumCodeGen(accessor);
        this.collectionCodeGen = new CollectionCodeGen(registry, accessor, enumCodeGen);
        this.optionalCodeGen = new OptionalCodeGen(registry, accessor, enumCodeGen);
    }

    public TypeSpec generate(String packageName, String simpleClassName, List<MetadataFieldInfo> fields) {
        ClassName targetClass = ClassName.get(packageName, simpleClassName);
        String converterClassName = simpleClassName + CONVERTER_SUFFIX;

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(converterClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(GENERATED_CODE);

        classBuilder.addMethod(buildToMetadataMapMethod(targetClass, simpleClassName, fields));
        classBuilder.addMethod(buildFromMetadataMapMethod(targetClass, simpleClassName, fields));

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

        // Collections
        if (javaType.startsWith("java.util.List<") || javaType.startsWith("java.util.Set<")
                || javaType.startsWith("java.util.SortedSet<")) {
            collectionCodeGen.emitSerializeToMap(builder, field, getExpr);
            return;
        }

        // Optional
        if (javaType.startsWith("java.util.Optional<")) {
            if (field.isElementEnumType()) {
                enumCodeGen.emitSerializeOptionalToMap(builder, field, getExpr);
            } else {
                optionalCodeGen.emitSerializeToMap(builder, field, getExpr);
            }
            return;
        }

        // Enum
        if (field.isEnumType()) {
            enumCodeGen.emitSerializeToMap(builder, field, getExpr);
            return;
        }

        // byte[] encoding variants
        switch (enc) {
            case STRING_HEX:
                registry.getByteArrayCodeGen().emitSerializeHex(builder, key, getExpr);
                return;
            case STRING_BASE64:
                registry.getByteArrayCodeGen().emitSerializeBase64(builder, key, getExpr);
                return;
            default:
                break;
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

    private MethodSpec buildFromMetadataMapMethod(ClassName targetClass, String simpleClassName,
                                                  List<MetadataFieldInfo> fields) {
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

        // Collections
        if (javaType.startsWith("java.util.List<") || javaType.startsWith("java.util.Set<")
                || javaType.startsWith("java.util.SortedSet<")) {
            collectionCodeGen.emitDeserializeFromMap(builder, field);
            return;
        }

        // Optional
        if (javaType.startsWith("java.util.Optional<")) {
            optionalCodeGen.emitDeserializeFromMap(builder, field);
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
        if (field.isEnumType()) return true;
        if (!isScalar(javaType)) return true;
        MetadataTypeCodeGen codeGen = registry.get(javaType);
        return codeGen.needsNullCheck(javaType);
    }

    private boolean isScalar(String javaType) {
        return !javaType.startsWith("java.util.List<")
                && !javaType.startsWith("java.util.Set<")
                && !javaType.startsWith("java.util.SortedSet<")
                && !javaType.startsWith("java.util.Optional<");
    }

    private String firstLowerCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
