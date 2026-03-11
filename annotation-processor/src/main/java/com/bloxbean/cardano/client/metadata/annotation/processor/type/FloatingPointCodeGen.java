package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.util.Set;

/**
 * Code generation strategy for {@code double/Double} and {@code float/Float} fields.
 *
 * <ul>
 *   <li>DEFAULT: stored as String (String.valueOf), parsed back via parseDouble/parseFloat</li>
 *   <li>STRING: identical to DEFAULT</li>
 * </ul>
 */
public class FloatingPointCodeGen extends AbstractMetadataTypeCodeGen {

    private static final Set<String> TYPES = Set.of(
            "java.lang.Double", "double",
            "java.lang.Float", "float"
    );

    @Override
    public Set<String> supportedJavaTypes() {
        return TYPES;
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return String.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{"$T.valueOf(" + valueExpr + ")", String.class};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return switch (javaType) {
            case "java.lang.Double", "double" ->
                    new Object[]{"$T.parseDouble((String) " + castVar + ")", Double.class};
            case "java.lang.Float", "float" ->
                    new Object[]{"$T.parseFloat((String) " + castVar + ")", Float.class};
            default -> throw new IllegalArgumentException("Unsupported floating point type: " + javaType);
        };
    }

    // For collection elements, Double and Float have split deserialize paths

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        switch (javaType) {
            case "java.lang.Double" ->
                    builder.addStatement("_result.add($T.parseDouble(($T) _el))", Double.class, String.class);
            case "java.lang.Float" ->
                    builder.addStatement("_result.add($T.parseFloat(($T) _el))", Float.class, String.class);
        }
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        MetadataFieldAccessor accessor) {
        String elementType = field.getElementTypeName();
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        switch (elementType) {
            case "java.lang.Double" -> accessor.emitOptionalOfSet(builder, field,
                    "$T.parseDouble((String) v)", Double.class);
            case "java.lang.Float" -> accessor.emitOptionalOfSet(builder, field,
                    "$T.parseFloat((String) v)", Float.class);
        }
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }
}
