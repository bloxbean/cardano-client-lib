package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.util.Optional;

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
            case "int", "long", "short", "byte", "boolean", "double", "float", "char" -> false;
            default -> true;
        };
    }

    // --- Serialization ---

    @Override
    public void emitSerializeToMapDefault(MethodSpec.Builder builder, String key, String getExpr,
                                          String javaType) {
        Object[] ser = serializeExpression(getExpr, javaType);
        String fmt = (String) ser[0];
        if (ser.length == 1) {
            builder.addStatement("map.put($S, " + fmt + ")", key);
        } else {
            Object[] args = new Object[ser.length]; // key + remaining
            args[0] = key;
            System.arraycopy(ser, 1, args, 1, ser.length - 1);
            builder.addStatement("map.put($S, " + fmt + ")", args);
        }
    }

    @Override
    public void emitSerializeToList(MethodSpec.Builder builder, String javaType) {
        Object[] ser = serializeExpression("_el", javaType);
        String fmt = (String) ser[0];
        if (ser.length == 1) {
            builder.addStatement("_list.add(" + fmt + ")");
        } else {
            Object[] args = new Object[ser.length - 1];
            System.arraycopy(ser, 1, args, 0, args.length);
            builder.addStatement("_list.add(" + fmt + ")", args);
        }
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
        Object[] deser = deserializeExpression("_el", javaType);
        String fmt = (String) deser[0];
        if (deser.length == 1) {
            builder.addStatement("_result.add(" + fmt + ")");
        } else {
            Object[] args = new Object[deser.length - 1];
            System.arraycopy(deser, 1, args, 0, args.length);
            builder.addStatement("_result.add(" + fmt + ")", args);
        }
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

    // --- Internal helpers ---

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
