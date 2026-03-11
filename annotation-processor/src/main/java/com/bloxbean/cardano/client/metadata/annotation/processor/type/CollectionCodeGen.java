package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGenRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Code generation for {@code List<T>}, {@code Set<T>}, and {@code SortedSet<T>} fields.
 * Provides the fixed skeleton (MetadataList creation, for-loop, null-check) and delegates
 * element-level serialization/deserialization to the registry.
 */
public class CollectionCodeGen {

    private final MetadataTypeCodeGenRegistry registry;
    private final MetadataFieldAccessor accessor;
    private final EnumCodeGen enumCodeGen;

    public CollectionCodeGen(MetadataTypeCodeGenRegistry registry, MetadataFieldAccessor accessor,
                             EnumCodeGen enumCodeGen) {
        this.registry = registry;
        this.accessor = accessor;
        this.enumCodeGen = enumCodeGen;
    }

    // --- Serialization ---

    public void emitSerializeToMap(MethodSpec.Builder builder, MetadataFieldInfo field,
                                   String getExpr) {
        String key = field.getMetadataKey();
        TypeName elemTypeName = elementTypeName(field);

        builder.addStatement("$T _list = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _el : $L)", elemTypeName, getExpr);
        builder.beginControlFlow("if (_el != null)");

        if (field.isElementEnumType()) {
            enumCodeGen.emitSerializeToList(builder);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getElementTypeName());
            codeGen.emitSerializeToList(builder, field.getElementTypeName());
        }

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // for loop
        builder.addStatement("map.put($S, _list)", key);
    }

    // --- Deserialization ---

    public void emitDeserializeFromMap(MethodSpec.Builder builder, MetadataFieldInfo field) {
        String javaType = field.getJavaTypeName();
        ClassName interfaceClass;
        ClassName implClass;

        if (javaType.startsWith("java.util.List<")) {
            interfaceClass = ClassName.get("java.util", "List");
            implClass = ClassName.get("java.util", "ArrayList");
        } else if (javaType.startsWith("java.util.Set<")) {
            interfaceClass = ClassName.get("java.util", "Set");
            implClass = ClassName.get("java.util", "LinkedHashSet");
        } else {
            interfaceClass = ClassName.get("java.util", "SortedSet");
            implClass = ClassName.get("java.util", "TreeSet");
        }

        TypeName elemTypeName = elementTypeName(field);
        ParameterizedTypeName collectionType =
                ParameterizedTypeName.get(interfaceClass, elemTypeName);

        builder.beginControlFlow("if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _rawList = ($T) v", MetadataList.class, MetadataList.class);
        builder.addStatement("$T _result = new $T<>()", collectionType, implClass);
        builder.beginControlFlow("for (int _i = 0; _i < _rawList.size(); _i++)");
        builder.addStatement("$T _el = _rawList.getValueAt(_i)", Object.class);

        if (field.isElementEnumType()) {
            enumCodeGen.emitDeserializeElement(builder, field);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getElementTypeName());
            codeGen.emitDeserializeElement(builder, field.getElementTypeName());
        }

        builder.endControlFlow(); // for loop
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataList
    }

    // --- Helper ---

    private TypeName elementTypeName(MetadataFieldInfo field) {
        if (field.isElementEnumType()) {
            return ClassName.bestGuess(field.getElementTypeName());
        }
        return switch (field.getElementTypeName()) {
            case "java.lang.String"         -> TypeName.get(String.class);
            case "java.math.BigInteger"     -> TypeName.get(BigInteger.class);
            case "java.math.BigDecimal"     -> TypeName.get(BigDecimal.class);
            case "java.lang.Long"           -> TypeName.get(Long.class);
            case "java.lang.Integer"        -> TypeName.get(Integer.class);
            case "java.lang.Short"          -> TypeName.get(Short.class);
            case "java.lang.Byte"           -> TypeName.get(Byte.class);
            case "java.lang.Boolean"        -> TypeName.get(Boolean.class);
            case "java.lang.Double"         -> TypeName.get(Double.class);
            case "java.lang.Float"          -> TypeName.get(Float.class);
            case "java.lang.Character"      -> TypeName.get(Character.class);
            case "byte[]"                   -> TypeName.get(byte[].class);
            case "java.net.URI"             -> TypeName.get(URI.class);
            case "java.net.URL"             -> TypeName.get(URL.class);
            case "java.util.UUID"           -> TypeName.get(UUID.class);
            case "java.util.Currency"       -> TypeName.get(Currency.class);
            case "java.util.Locale"         -> TypeName.get(Locale.class);
            case "java.time.Instant"        -> TypeName.get(Instant.class);
            case "java.time.LocalDate"      -> TypeName.get(LocalDate.class);
            case "java.time.LocalDateTime"  -> TypeName.get(LocalDateTime.class);
            case "java.util.Date"           -> TypeName.get(Date.class);
            default -> throw new IllegalArgumentException(
                    "Unsupported collection element type: " + field.getElementTypeName());
        };
    }
}
