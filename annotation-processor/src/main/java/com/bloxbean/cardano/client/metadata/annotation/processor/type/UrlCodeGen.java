package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.Set;

/**
 * Code generation strategy for {@link URL} fields.
 *
 * <p>Always stored as String on-chain (DEFAULT == STRING).
 * Deserialization requires try-catch for {@link MalformedURLException}.
 */
public class UrlCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.net.URL");
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return String.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr + ".toString()"};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"new $T(($T) " + castVar + ")", URL.class, String.class};
    }

    // --- Deserialization overrides: try-catch for MalformedURLException ---

    @Override
    public void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
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

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        builder.beginControlFlow("try");
        builder.addStatement("_result.add(new $T(($T) _el))", URL.class, String.class);
        builder.nextControlFlow("catch ($T _e)", MalformedURLException.class);
        builder.addStatement("throw new $T(\"Malformed URL: \" + _el, _e)", IllegalArgumentException.class);
        builder.endControlFlow();
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
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
}
