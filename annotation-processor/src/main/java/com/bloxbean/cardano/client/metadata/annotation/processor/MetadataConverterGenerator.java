package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
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
        return generate(packageName, simpleClassName, fields, -1, false);
    }

    public TypeSpec generate(String packageName, String simpleClassName, List<MetadataFieldInfo> fields, long label) {
        return generate(packageName, simpleClassName, fields, label, false);
    }

    /**
     * Describes a single record component for constructor call generation.
     * Includes both serialized and ignored components.
     */
    record RecordComponentInfo(String name, String javaTypeName) {}

    public TypeSpec generate(String packageName, String simpleClassName, List<MetadataFieldInfo> fields,
                             long label, boolean isRecord) {
        return generate(packageName, simpleClassName, fields, label, isRecord, List.of());
    }

    public TypeSpec generate(String packageName, String simpleClassName, List<MetadataFieldInfo> fields,
                             long label, boolean isRecord, List<RecordComponentInfo> allComponents) {
        ClassName targetClass = ClassName.get(packageName, simpleClassName);
        String converterClassName = simpleClassName + CONVERTER_SUFFIX;

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(converterClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(GENERATED_CODE);

        // Static adapter instances (one per distinct adapter class)
        addAdapterFields(classBuilder, fields);

        classBuilder.addMethod(buildToMetadataMapMethod(targetClass, simpleClassName, fields));
        classBuilder.addMethod(buildFromMetadataMapMethod(targetClass, fields, isRecord, allComponents));

        // Feature 3: Label — generate toMetadata / fromMetadata if label >= 0
        if (label >= 0) {
            classBuilder.addMethod(buildToMetadataMethod(targetClass, simpleClassName, label));
            classBuilder.addMethod(buildFromMetadataMethod(targetClass, label));
        }

        // Negative-aware BigInteger helpers for serialization
        addBigIntHelpers(classBuilder);

        // Adapter helper for putting adapter results into MetadataMap
        addAdapterHelper(classBuilder, fields);

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

        // Custom adapter — takes priority over all built-in handling
        if (field.isAdapterType()) {
            builder.addStatement("_putAdapted(map, $S, $L.toMetadata($L))",
                    key, adapterFieldName(field.getAdapterFqn()), getExpr);
            return;
        }

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

        // Polymorphic @MetadataDiscriminator
        if (field.isPolymorphicType()) {
            nestedCodeGen.emitSerializePolymorphic(builder, field, getExpr);
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

    private MethodSpec buildFromMetadataMapMethod(ClassName targetClass, List<MetadataFieldInfo> fields,
                                                  boolean isRecord, List<RecordComponentInfo> allComponents) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("fromMetadataMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(targetClass)
                .addParameter(MetadataMap.class, "map");

        if (isRecord) {
            // Use allComponents for constructor (includes ignored fields); fall back to fields if empty
            List<RecordComponentInfo> components = allComponents.isEmpty()
                    ? fields.stream().map(f -> new RecordComponentInfo(f.getJavaFieldName(), f.getJavaTypeName())).toList()
                    : allComponents;

            // Collect names of serialized fields for lookup
            java.util.Set<String> serializedNames = fields.stream()
                    .map(MetadataFieldInfo::getJavaFieldName)
                    .collect(java.util.stream.Collectors.toSet());

            // Phase 1: declare local variables with type-appropriate defaults for ALL components
            for (RecordComponentInfo comp : components) {
                String localName = "_" + comp.name();
                String defaultVal = defaultForType(comp.javaTypeName());
                builder.addStatement("$L $L = $L", simplifyTypeName(comp.javaTypeName()), localName, defaultVal);
            }

            builder.addStatement("$T v", Object.class);

            // Phase 2: deserialize into locals (only for non-ignored fields)
            for (MetadataFieldInfo field : fields) {
                builder.addStatement("v = map.get($S)", field.getMetadataKey());
                emitRequiredOrDefault(builder, field);
                emitFromMapGet(builder, field);
            }

            // Phase 3: constructor call with ALL components (including ignored, which keep defaults)
            StringBuilder ctorArgs = new StringBuilder();
            for (int i = 0; i < components.size(); i++) {
                if (i > 0) ctorArgs.append(", ");
                ctorArgs.append("_").append(components.get(i).name());
            }
            builder.addStatement("return new $T($L)", targetClass, ctorArgs.toString());
        } else {
            builder.addStatement("$T obj = new $T()", targetClass, targetClass);
            builder.addStatement("$T v", Object.class);

            for (MetadataFieldInfo field : fields) {
                builder.addStatement("v = map.get($S)", field.getMetadataKey());
                emitRequiredOrDefault(builder, field);
                emitFromMapGet(builder, field);
            }

            builder.addStatement("return obj");
        }

        return builder.build();
    }

    /**
     * Returns a source-code default value for the given Java type name.
     * Reference types return "null", primitives return their zero values.
     */
    private String defaultForType(String javaTypeName) {
        return switch (javaTypeName) {
            case "int" -> "0";
            case "long" -> "0L";
            case "short" -> "(short) 0";
            case "byte" -> "(byte) 0";
            case "boolean" -> "false";
            case "double" -> "0.0d";
            case "float" -> "0.0f";
            case "char" -> "'\\0'";
            default -> "null";
        };
    }

    /**
     * Simplifies fully-qualified type names to simple names for use in local variable declarations.
     * Handles generic types like {@code java.util.List<java.lang.String>}.
     */
    private String simplifyTypeName(String fqn) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < fqn.length()) {
            // Find the next segment (class name part before < , > or end)
            int angleOpen = fqn.indexOf('<', i);
            int angleClose = fqn.indexOf('>', i);
            int comma = fqn.indexOf(',', i);

            // Find the nearest delimiter
            int next = fqn.length();
            if (angleOpen >= 0 && angleOpen < next) next = angleOpen;
            if (angleClose >= 0 && angleClose < next) next = angleClose;
            if (comma >= 0 && comma < next) next = comma;

            String segment = fqn.substring(i, next).trim();
            if (!segment.isEmpty()) {
                // Extract simple name from FQN segment
                int lastDot = segment.lastIndexOf('.');
                sb.append(lastDot >= 0 ? segment.substring(lastDot + 1) : segment);
            }

            if (next < fqn.length()) {
                sb.append(fqn.charAt(next));
                i = next + 1;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private void emitFromMapGet(MethodSpec.Builder builder, MetadataFieldInfo field) {
        String javaType = field.getJavaTypeName();
        MetadataFieldType enc = field.getEnc();

        // Custom adapter — takes priority over all built-in handling
        if (field.isAdapterType()) {
            builder.beginControlFlow("if (v != null)");
            accessor.emitSetRaw(builder, field,
                    "(" + javaType + ") " + adapterFieldName(field.getAdapterFqn()) + ".fromMetadata(v)");
            builder.endControlFlow();
            return;
        }

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

        // Polymorphic @MetadataDiscriminator
        if (field.isPolymorphicType()) {
            nestedCodeGen.emitDeserializePolymorphic(builder, field);
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
    // Required / DefaultValue injection
    // -------------------------------------------------------------------------

    private void emitRequiredOrDefault(MethodSpec.Builder builder, MetadataFieldInfo field) {
        if (field.isRequired()) {
            builder.beginControlFlow("if (v == null)")
                    .addStatement("throw new $T($S)",
                            IllegalArgumentException.class,
                            "Required metadata key '" + field.getMetadataKey() + "' is missing")
                    .endControlFlow();
        } else if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
            String defaultExpr = buildDefaultExpression(field);
            builder.beginControlFlow("if (v == null)")
                    .addStatement("v = " + defaultExpr)
                    .endControlFlow();
        }
    }

    /**
     * Converts the {@code defaultValue} string into an expression that matches
     * the on-chain representation used by the {@code instanceof} checks in
     * {@code emitFromMapGet}.
     */
    private String buildDefaultExpression(MetadataFieldInfo field) {
        String dv = field.getDefaultValue();
        String javaType = field.getJavaTypeName();

        return switch (javaType) {
            // String-encoded types → plain string literal
            case "java.lang.String",
                 "java.math.BigDecimal",
                 "double", "java.lang.Double",
                 "float", "java.lang.Float",
                 "char", "java.lang.Character",
                 "java.net.URI", "java.net.URL",
                 "java.util.UUID", "java.util.Currency", "java.util.Locale",
                 "java.util.Date",
                 "java.time.Instant", "java.time.LocalDate", "java.time.LocalDateTime" ->
                    "\"" + escapeJava(dv) + "\"";

            // Integer types → BigInteger
            case "int", "java.lang.Integer",
                 "short", "java.lang.Short",
                 "byte", "java.lang.Byte",
                 "long", "java.lang.Long" ->
                    "java.math.BigInteger.valueOf(" + Long.parseLong(dv) + "L)";

            case "java.math.BigInteger" ->
                    "new java.math.BigInteger(\"" + dv + "\")";

            // Boolean → BigInteger 0 or 1
            case "boolean", "java.lang.Boolean" -> {
                boolean bval = Boolean.parseBoolean(dv);
                yield "java.math.BigInteger.valueOf(" + (bval ? "1" : "0") + "L)";
            }

            // Enum fields are stored as strings in metadata
            default -> {
                if (field.isEnumType()) {
                    yield "\"" + escapeJava(dv) + "\"";
                }
                // Fallback: treat as string
                yield "\"" + escapeJava(dv) + "\"";
            }
        };
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
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
        if (!isScalar(field)) return true;
        MetadataTypeCodeGen codeGen = registry.get(javaType);

        return codeGen.needsNullCheck(javaType);
    }

    private boolean isScalar(MetadataFieldInfo field) {
        return !field.isCollectionType() && !field.isOptionalType()
                && !field.isMapType() && !field.isNestedType() && !field.isEnumType()
                && !field.isPolymorphicType() && !field.isAdapterType();
    }

    private String firstLowerCase(String s) {
        if (s == null || s.isEmpty()) return s;

        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // -------------------------------------------------------------------------
    // Negative-aware BigInteger helpers (added to generated class)
    // -------------------------------------------------------------------------

    private void addBigIntHelpers(TypeSpec.Builder classBuilder) {
        // _putBigInt(MetadataMap, String, BigInteger)
        classBuilder.addMethod(MethodSpec.methodBuilder("_putBigInt")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(MetadataMap.class, "_m")
                .addParameter(String.class, "_k")
                .addParameter(BigInteger.class, "_v")
                .beginControlFlow("if (_v.signum() >= 0)")
                .addStatement("_m.put(_k, _v)")
                .nextControlFlow("else")
                .addStatement("_m.putNegative(_k, _v)")
                .endControlFlow()
                .build());

        // _putBigInt(MetadataMap, BigInteger, BigInteger)
        classBuilder.addMethod(MethodSpec.methodBuilder("_putBigInt")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(MetadataMap.class, "_m")
                .addParameter(BigInteger.class, "_k")
                .addParameter(BigInteger.class, "_v")
                .beginControlFlow("if (_v.signum() >= 0)")
                .addStatement("_m.put(_k, _v)")
                .nextControlFlow("else")
                .addStatement("_m.putNegative(_k, _v)")
                .endControlFlow()
                .build());

        // _putBigInt(MetadataMap, byte[], BigInteger)
        classBuilder.addMethod(MethodSpec.methodBuilder("_putBigInt")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(MetadataMap.class, "_m")
                .addParameter(byte[].class, "_k")
                .addParameter(BigInteger.class, "_v")
                .beginControlFlow("if (_v.signum() >= 0)")
                .addStatement("_m.put(_k, _v)")
                .nextControlFlow("else")
                .addStatement("_m.putNegative(_k, _v)")
                .endControlFlow()
                .build());

        // _addBigInt(MetadataList, BigInteger)
        classBuilder.addMethod(MethodSpec.methodBuilder("_addBigInt")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(MetadataList.class, "_l")
                .addParameter(BigInteger.class, "_v")
                .beginControlFlow("if (_v.signum() >= 0)")
                .addStatement("_l.add(_v)")
                .nextControlFlow("else")
                .addStatement("_l.addNegative(_v)")
                .endControlFlow()
                .build());
    }

    // -------------------------------------------------------------------------
    // Adapter support
    // -------------------------------------------------------------------------

    /**
     * Adds static adapter instance fields for each distinct adapter class used by the fields.
     * Avoids repeated instantiation in serialize/deserialize methods.
     */
    private void addAdapterFields(TypeSpec.Builder classBuilder, List<MetadataFieldInfo> fields) {
        fields.stream()
                .filter(MetadataFieldInfo::isAdapterType)
                .map(MetadataFieldInfo::getAdapterFqn)
                .distinct()
                .forEach(fqn -> classBuilder.addField(
                        com.squareup.javapoet.FieldSpec.builder(
                                        ClassName.bestGuess(fqn),
                                        adapterFieldName(fqn),
                                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                                .initializer("new $T()", ClassName.bestGuess(fqn))
                                .build()));
    }

    /**
     * Derives a static field name from an adapter FQN.
     * E.g. {@code "com.example.EpochSecondsAdapter"} → {@code "_epochSecondsAdapter"}.
     */
    static String adapterFieldName(String adapterFqn) {
        String simple = adapterFqn.substring(adapterFqn.lastIndexOf('.') + 1);
        return "_" + Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /**
     * Adds a {@code _putAdapted(MetadataMap, String, Object)} helper only when
     * at least one adapter field exists. The helper dispatches at runtime based on
     * the value's concrete type.
     */
    private void addAdapterHelper(TypeSpec.Builder classBuilder, List<MetadataFieldInfo> fields) {
        boolean hasAdapter = fields.stream().anyMatch(MetadataFieldInfo::isAdapterType);
        if (!hasAdapter) return;

        classBuilder.addMethod(MethodSpec.methodBuilder("_putAdapted")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(MetadataMap.class, "_m")
                .addParameter(String.class, "_k")
                .addParameter(Object.class, "_v")
                .beginControlFlow("if (_v instanceof $T)", String.class)
                .addStatement("_m.put(_k, ($T) _v)", String.class)
                .nextControlFlow("else if (_v instanceof $T)", BigInteger.class)
                .addStatement("_putBigInt(_m, _k, ($T) _v)", BigInteger.class)
                .nextControlFlow("else if (_v instanceof byte[])")
                .addStatement("_m.put(_k, (byte[]) _v)")
                .nextControlFlow("else if (_v instanceof $T)", MetadataMap.class)
                .addStatement("_m.put(_k, ($T) _v)", MetadataMap.class)
                .nextControlFlow("else if (_v instanceof $T)", MetadataList.class)
                .addStatement("_m.put(_k, ($T) _v)", MetadataList.class)
                .nextControlFlow("else")
                .addStatement("throw new $T($S + _v.getClass().getName())",
                        IllegalArgumentException.class, "Adapter returned unsupported metadata type: ")
                .endControlFlow()
                .build());
    }

}
