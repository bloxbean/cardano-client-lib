package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Code generation strategy for the 8 integral types:
 * {@code int/Integer, long/Long, short/Short, byte/Byte}.
 *
 * <ul>
 *   <li>DEFAULT: store as BigInteger, read back via narrowing methods (longValue, intValue, ...)</li>
 *   <li>STRING: store as String.valueOf, parse via parseLong/parseInt/etc.</li>
 * </ul>
 */
public class IntegralCodeGen extends AbstractMetadataTypeCodeGen {

    private static final Set<String> TYPES = Set.of(
            LONG, PRIM_LONG,
            INTEGER, PRIM_INT,
            SHORT, PRIM_SHORT,
            BYTE, PRIM_BYTE
    );

    // Maps boxed type → narrowing method name on BigInteger
    private static final Map<String, String> NARROW_METHOD = Map.of(
            LONG, "longValue",
            PRIM_LONG, "longValue",
            INTEGER, "intValue",
            PRIM_INT, "intValue",
            SHORT, "shortValue",
            PRIM_SHORT, "shortValue",
            BYTE, "byteValue",
            PRIM_BYTE, "byteValue"
    );

    // Maps boxed type → parse method on the wrapper class
    private static final Map<String, String> PARSE_METHOD = Map.of(
            LONG, "parseLong",
            PRIM_LONG, "parseLong",
            INTEGER, "parseInt",
            PRIM_INT, "parseInt",
            SHORT, "parseShort",
            PRIM_SHORT, "parseShort",
            BYTE, "parseByte",
            PRIM_BYTE, "parseByte"
    );

    // Maps type → wrapper class for STRING enc deserialization
    private static final Map<String, Class<?>> WRAPPER_CLASS = Map.of(
            LONG, Long.class,
            PRIM_LONG, Long.class,
            INTEGER, Integer.class,
            PRIM_INT, Integer.class,
            SHORT, Short.class,
            PRIM_SHORT, Short.class,
            BYTE, Byte.class,
            PRIM_BYTE, Byte.class
    );

    @Override
    public Set<String> supportedJavaTypes() {
        return TYPES;
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return BigInteger.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return switch (javaType) {
            case LONG, PRIM_LONG ->
                    new Object[]{"$T.valueOf(" + valueExpr + ")", BigInteger.class};
            case INTEGER, PRIM_INT,
                 SHORT, PRIM_SHORT,
                 BYTE, PRIM_BYTE ->
                    new Object[]{"$T.valueOf((long) " + valueExpr + ")", BigInteger.class};
            default -> throw new IllegalArgumentException("Unsupported integral type: " + javaType);
        };
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        String narrow = NARROW_METHOD.get(javaType);
        return new Object[]{"(($T) " + castVar + ")." + narrow + "()", BigInteger.class};
    }

    // STRING enc

    @Override
    public void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                         String javaType) {
        builder.addStatement("map.put($S, $T.valueOf($L))", key, String.class, getExpr);
    }

    @Override
    public void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        String javaType = field.getJavaTypeName();
        Class<?> wrapper = WRAPPER_CLASS.get(javaType);
        String parseMethod = PARSE_METHOD.get(javaType);
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, "$T." + parseMethod + "((String) v)", wrapper);
        builder.endControlFlow();
    }

    // Collection element: boxed types only — use the boxed type's narrow method

    @Override
    public void emitSerializeToList(MethodSpec.Builder builder, String javaType) {
        // Collection elements are always boxed
        switch (javaType) {
            case LONG ->
                    builder.addStatement("_list.add($T.valueOf(_el))", BigInteger.class);
            case INTEGER, SHORT, BYTE ->
                    builder.addStatement("_list.add($T.valueOf((long) _el))", BigInteger.class);
            default ->
                    throw new IllegalArgumentException("Unsupported collection element type: " + javaType);
        }
    }

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        String narrow = NARROW_METHOD.get(javaType);
        builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
        builder.addStatement("_result.add((($T) _el)." + narrow + "())", BigInteger.class);
        builder.endControlFlow();
    }
}
