package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.squareup.javapoet.MethodSpec;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Code generation strategy for types that serialize to/from String on-chain:
 * {@code URI, URL, UUID, Currency, Locale}.
 *
 * <ul>
 *   <li>DEFAULT and STRING are identical (these types are always text on chain)</li>
 *   <li>URL is special: deserialization needs try-catch for MalformedURLException</li>
 * </ul>
 */
public class StringCoercibleCodeGen implements MetadataTypeCodeGen {

    private static final Set<String> TYPES = Set.of(
            "java.net.URI", "java.net.URL",
            "java.util.UUID", "java.util.Currency", "java.util.Locale"
    );

    /** Maps type name → serialize expression suffix (applied to the value expression). */
    private static final Map<String, String> SERIALIZE_EXPR = Map.of(
            "java.net.URI", ".toString()",
            "java.net.URL", ".toString()",
            "java.util.UUID", ".toString()",
            "java.util.Currency", ".getCurrencyCode()",
            "java.util.Locale", ".toLanguageTag()"
    );

    @Override
    public Set<String> supportedJavaTypes() {
        return TYPES;
    }

    @Override
    public boolean needsNullCheck(String javaType) {
        return true;
    }

    // --- Serialization ---

    @Override
    public void emitSerializeToMapDefault(MethodSpec.Builder builder, String key, String getExpr,
                                          String javaType) {
        String suffix = SERIALIZE_EXPR.get(javaType);
        builder.addStatement("map.put($S, $L" + suffix + ")", key, getExpr);
    }

    @Override
    public void emitSerializeToList(MethodSpec.Builder builder, String javaType) {
        String suffix = SERIALIZE_EXPR.get(javaType);
        builder.addStatement("_list.add(_el" + suffix + ")");
    }

    // --- Deserialization ---

    @Override
    public void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             MetadataFieldAccessor accessor) {
        String javaType = field.getJavaTypeName();
        if ("java.net.URL".equals(javaType)) {
            emitUrlDeserialize(builder, field, accessor);
            return;
        }
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, deserFmt(javaType, "v"), deserClass(javaType));
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        if ("java.net.URL".equals(javaType)) {
            builder.beginControlFlow("if (_el instanceof $T)", String.class);
            builder.beginControlFlow("try");
            builder.addStatement("_result.add(new $T(($T) _el))", URL.class, String.class);
            builder.nextControlFlow("catch ($T _e)", MalformedURLException.class);
            builder.addStatement("throw new $T(\"Malformed URL: \" + _el, _e)", IllegalArgumentException.class);
            builder.endControlFlow();
            builder.endControlFlow();
            return;
        }
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        builder.addStatement("_result.add(" + deserFmtElement(javaType) + ")",
                deserElementArgs(javaType));
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        MetadataFieldAccessor accessor) {
        String elementType = field.getElementTypeName();
        if ("java.net.URL".equals(elementType)) {
            emitOptionalUrlDeserialize(builder, field, accessor);
            return;
        }
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitOptionalOfSet(builder, field, deserFmt(elementType, "v"), deserClass(elementType));
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }

    // --- URL special cases ---

    private void emitUrlDeserialize(MethodSpec.Builder builder, MetadataFieldInfo field,
                                    MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        builder.beginControlFlow("try");
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L(new $T((String) v))", field.getSetterName(), URL.class);
        } else {
            builder.addStatement("obj.$L = new $T((String) v)", field.getJavaFieldName(), URL.class);
        }
        builder.nextControlFlow("catch ($T _e)", MalformedURLException.class);
        builder.addStatement("throw new $T(\"Malformed URL: \" + v, _e)", IllegalArgumentException.class);
        builder.endControlFlow();
        builder.endControlFlow();
    }

    private void emitOptionalUrlDeserialize(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        builder.beginControlFlow("try");
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.of(new $T((String) v)))",
                    field.getSetterName(), Optional.class, URL.class);
        } else {
            builder.addStatement("obj.$L = $T.of(new $T((String) v))",
                    field.getJavaFieldName(), Optional.class, URL.class);
        }
        builder.nextControlFlow("catch ($T _e)", MalformedURLException.class);
        builder.addStatement("throw new $T(\"Malformed URL: \" + v, _e)", IllegalArgumentException.class);
        builder.endControlFlow();
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }

    // --- Helpers ---

    private String deserFmt(String javaType, String castVar) {
        return switch (javaType) {
            case "java.net.URI"        -> "$T.create((String) " + castVar + ")";
            case "java.util.UUID"      -> "$T.fromString((String) " + castVar + ")";
            case "java.util.Currency"  -> "$T.getInstance((String) " + castVar + ")";
            case "java.util.Locale"    -> "$T.forLanguageTag((String) " + castVar + ")";
            default -> throw new IllegalArgumentException("Unsupported: " + javaType);
        };
    }

    private Class<?> deserClass(String javaType) {
        return switch (javaType) {
            case "java.net.URI"        -> URI.class;
            case "java.util.UUID"      -> UUID.class;
            case "java.util.Currency"  -> Currency.class;
            case "java.util.Locale"    -> Locale.class;
            default -> throw new IllegalArgumentException("Unsupported: " + javaType);
        };
    }

    private String deserFmtElement(String javaType) {
        return switch (javaType) {
            case "java.net.URI"        -> "$T.create(($T) _el)";
            case "java.util.UUID"      -> "$T.fromString(($T) _el)";
            case "java.util.Currency"  -> "$T.getInstance(($T) _el)";
            case "java.util.Locale"    -> "$T.forLanguageTag(($T) _el)";
            default -> throw new IllegalArgumentException("Unsupported: " + javaType);
        };
    }

    private Object[] deserElementArgs(String javaType) {
        return switch (javaType) {
            case "java.net.URI"        -> new Object[]{URI.class, String.class};
            case "java.util.UUID"      -> new Object[]{UUID.class, String.class};
            case "java.util.Currency"  -> new Object[]{Currency.class, String.class};
            case "java.util.Locale"    -> new Object[]{Locale.class, String.class};
            default -> throw new IllegalArgumentException("Unsupported: " + javaType);
        };
    }
}
