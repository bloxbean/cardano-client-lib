package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;

/**
 * Template-method base class for scalar types that follow the standard pattern:
 * <ul>
 *   <li>Serialization: convert Java value → on-chain type via a single expression</li>
 *   <li>Deserialization: {@code instanceof} check on on-chain type, convert back</li>
 * </ul>
 *
 * Subclasses provide the variable parts via abstract hooks. Types that don't follow
 * this pattern (String, URL, byte[]) override the interface methods directly.
 */
@SuppressWarnings("java:S1192") // JavaPoet format strings are intentionally repeated across similar codegen methods
public abstract class AbstractMetadataTypeCodeGen implements MetadataTypeCodeGen {

    /**
     * The Java class that the Cardano metadata layer stores this type as.
     * For most numerics this is {@link BigInteger}; for text-like types this is {@link String}.
     */
    protected abstract Class<?> onChainType(String javaType);

    /**
     * Expression that converts the Java value to the on-chain type.
     * The expression may contain JavaPoet {@code $T} / {@code $L} placeholders.
     *
     * @param valueExpr the expression that evaluates to the Java value (e.g. "getExpr", "_el")
     * @param javaType  the concrete Java type
     * @return a format-args pair: [0]=format string, [1..]=args
     */
    protected abstract Object[] serializeExpression(String valueExpr, String javaType);

    /**
     * Expression that converts the on-chain value back to the Java type.
     * The on-chain value is accessed as {@code (OnChainType) v} (scalar) or
     * {@code (OnChainType) _el} (element).
     *
     * @param castVar  the variable holding the on-chain value (e.g. "v", "_el")
     * @param javaType the concrete Java type
     * @return a format-args pair: [0]=format string, [1..]=args
     */
    protected abstract Object[] deserializeExpression(String castVar, String javaType);

    @Override
    public boolean needsNullCheck(String javaType) {
        return switch (javaType) {
            case PRIM_INT, PRIM_LONG, PRIM_SHORT,
                 PRIM_BYTE, PRIM_BOOLEAN, PRIM_DOUBLE,
                 PRIM_FLOAT, PRIM_CHAR -> false;
            default -> true;
        };
    }

    // --- Serialization ---

    @Override
    public void emitSerializeToMapDefault(MethodSpec.Builder builder, String key, String getExpr,
                                          String javaType) {
        Object[] ser = serializeExpression(getExpr, javaType);
        boolean signed = onChainType(javaType) == BigInteger.class;
        String tmpl = signed ? "_putBigInt(map, $S, %s)" : "map.put($S, %s)";
        addFmtStatement(builder, tmpl, ser, key);
    }

    @Override
    public void emitSerializeToList(MethodSpec.Builder builder, String javaType) {
        Object[] ser = serializeExpression("_el", javaType);
        boolean signed = onChainType(javaType) == BigInteger.class;
        String tmpl = signed ? "_addBigInt(_list, %s)" : "_list.add(%s)";
        addFmtStatement(builder, tmpl, ser);
    }

    // --- Deserialization ---

    @Override
    public void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             MetadataFieldAccessor accessor) {
        Class<?> chain = onChainType(field.getJavaTypeName());
        if (chain == byte[].class) {
            builder.beginControlFlow("if (v instanceof byte[])");
        } else {
            builder.beginControlFlow("if (v instanceof $T)", chain);
        }
        emitSetFromDeser(builder, field, accessor, "v", field.getJavaTypeName());
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        Class<?> chain = onChainType(javaType);
        if (chain == byte[].class) {
            builder.beginControlFlow("if (_el instanceof byte[])");
        } else {
            builder.beginControlFlow("if (_el instanceof $T)", chain);
        }
        addFmtStatement(builder, "_result.add(%s)", deserializeExpression("_el", javaType));
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        MetadataFieldAccessor accessor) {
        String elementType = field.getElementTypeName();
        Class<?> chain = onChainType(elementType);
        if (chain == byte[].class) {
            builder.beginControlFlow("if (v instanceof byte[])");
        } else {
            builder.beginControlFlow("if (v instanceof $T)", chain);
        }
        emitOptionalOfFromDeser(builder, field, accessor, "v", elementType);
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }

    // --- Map value support ---

    @Override
    public void emitSerializeMapValue(MethodSpec.Builder builder, String mapVarSuffix, String javaType) {
        emitSerializeMapValue(builder, mapVarSuffix, javaType, "_entry.getKey()");
    }

    @Override
    public void emitSerializeMapValue(MethodSpec.Builder builder, String mapVarSuffix,
                                       String javaType, String serKeyExpr) {
        Object[] ser = serializeExpression("_entry.getValue()", javaType);
        boolean signed = onChainType(javaType) == BigInteger.class;
        String tmpl = signed
                ? "_putBigInt(_map" + mapVarSuffix + ", " + serKeyExpr + ", %s)"
                : "_map" + mapVarSuffix + ".put(" + serKeyExpr + ", %s)";
        addFmtStatement(builder, tmpl, ser);
    }

    @Override
    public void emitDeserializeMapValue(MethodSpec.Builder builder, String javaType) {
        emitDeserializeMapValue(builder, javaType, "(String) _k");
    }

    @Override
    public void emitDeserializeMapValue(MethodSpec.Builder builder, String javaType, String deserKeyExpr) {
        Class<?> chain = onChainType(javaType);
        if (chain == byte[].class) {
            builder.beginControlFlow("if (_val instanceof byte[])");
        } else {
            builder.beginControlFlow("if (_val instanceof $T)", chain);
        }
        addFmtStatement(builder, "_result.put(" + deserKeyExpr + ", %s)",
                deserializeExpression("_val", javaType));
        builder.endControlFlow();
    }

    // --- Composite support: variable-name-parameterized methods ---

    @Override
    public void emitSerializeToListVar(MethodSpec.Builder builder, String listVar, String javaType) {
        Object[] ser = serializeExpression("_innerEl", javaType);
        boolean signed = onChainType(javaType) == BigInteger.class;
        String tmpl = signed ? "_addBigInt(" + listVar + ", %s)" : listVar + ".add(%s)";
        addFmtStatement(builder, tmpl, ser);
    }

    @Override
    public void emitSerializeMapValueVar(MethodSpec.Builder builder, String mapVar,
                                          String keyExpr, String javaType) {
        Object[] ser = serializeExpression("_innerEntry.getValue()", javaType);
        boolean signed = onChainType(javaType) == BigInteger.class;
        String tmpl = signed
                ? "_putBigInt(" + mapVar + ", " + keyExpr + ", %s)"
                : mapVar + ".put(" + keyExpr + ", %s)";
        addFmtStatement(builder, tmpl, ser);
    }

    @Override
    public void emitDeserializeToCollectionVar(MethodSpec.Builder builder, String resultVar,
                                                String rawVar, String javaType) {
        Class<?> chain = onChainType(javaType);
        if (chain == byte[].class) {
            builder.beginControlFlow("if ($L instanceof byte[])", rawVar);
        } else {
            builder.beginControlFlow("if ($L instanceof $T)", rawVar, chain);
        }
        addFmtStatement(builder, resultVar + ".add(%s)", deserializeExpression(rawVar, javaType));
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeToMapVar(MethodSpec.Builder builder, String resultVar,
                                         String keyExpr, String rawVar, String javaType) {
        Class<?> chain = onChainType(javaType);
        if (chain == byte[].class) {
            builder.beginControlFlow("if ($L instanceof byte[])", rawVar);
        } else {
            builder.beginControlFlow("if ($L instanceof $T)", rawVar, chain);
        }
        addFmtStatement(builder, resultVar + ".put(" + keyExpr + ", %s)",
                deserializeExpression(rawVar, javaType));
        builder.endControlFlow();
    }

    // --- Internal helpers ---

    /**
     * Emit a JavaPoet statement whose format string and args come from a
     * {@code serializeExpression} / {@code deserializeExpression} return value.
     *
     * @param builder       method builder to emit into
     * @param stmtTemplate  statement template containing the format placeholder (e.g.
     *                      {@code "_list.add(%s)"} where {@code %s} is replaced by the
     *                      format string from {@code fmtAndArgs})
     * @param fmtAndArgs    array where [0] is the JavaPoet format string and [1..] are
     *                      its arguments
     * @param leadingArgs   optional leading args to prepend (e.g. the map key)
     */
    protected void addFmtStatement(MethodSpec.Builder builder, String stmtTemplate,
                                    Object[] fmtAndArgs, Object... leadingArgs) {
        String fmt = (String) fmtAndArgs[0];
        String stmt = String.format(stmtTemplate, fmt);
        int extraArgs = fmtAndArgs.length - 1;
        if (leadingArgs.length == 0 && extraArgs == 0) {
            builder.addStatement(stmt);
        } else {
            Object[] args = new Object[leadingArgs.length + extraArgs];
            System.arraycopy(leadingArgs, 0, args, 0, leadingArgs.length);
            if (extraArgs > 0) {
                System.arraycopy(fmtAndArgs, 1, args, leadingArgs.length, extraArgs);
            }
            builder.addStatement(stmt, args);
        }
    }

    /**
     * Emit setter with the deserialized value for a scalar field.
     */
    protected void emitSetFromDeser(MethodSpec.Builder builder, MetadataFieldInfo field,
                                    MetadataFieldAccessor accessor, String castVar,
                                    String javaType) {
        Object[] deser = deserializeExpression(castVar, javaType);
        String fmt = (String) deser[0];
        if (deser.length == 1) {
            accessor.emitSetRaw(builder, field, fmt);
        } else if (deser.length == 2) {
            accessor.emitSet(builder, field, fmt, deser[1]);
        } else {
            // Multiple args - use emitSetFmt
            Object[] args = new Object[deser.length - 1];
            System.arraycopy(deser, 1, args, 0, args.length);
            accessor.emitSetFmt(builder, field, fmt, args);
        }
    }

    /**
     * Emit Optional.of(deserialized-value) setter.
     */
    protected void emitOptionalOfFromDeser(MethodSpec.Builder builder, MetadataFieldInfo field,
                                           MetadataFieldAccessor accessor, String castVar,
                                           String javaType) {
        Object[] deser = deserializeExpression(castVar, javaType);
        String fmt = (String) deser[0];
        if (deser.length == 1) {
            accessor.emitOptionalOfSetRaw(builder, field, fmt);
        } else if (deser.length == 2) {
            accessor.emitOptionalOfSet(builder, field, fmt, deser[1]);
        } else {
            Object[] args = new Object[deser.length - 1];
            System.arraycopy(deser, 1, args, 0, args.length);
            accessor.emitOptionalOfSetFmt(builder, field, fmt, args);
        }
    }
}
