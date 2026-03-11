package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

/**
 * Code generation strategy for temporal types:
 * {@code Instant, LocalDate, LocalDateTime, java.util.Date}.
 *
 * <ul>
 *   <li>DEFAULT: Instant/LocalDate/Date → epoch as BigInteger; LocalDateTime → ISO-8601 String</li>
 *   <li>STRING: Instant/LocalDate → ISO-8601 text; LocalDateTime → same as DEFAULT; Date → toInstant().toString()</li>
 * </ul>
 */
public class TemporalCodeGen implements MetadataTypeCodeGen {

    private static final Set<String> TYPES = Set.of(
            "java.time.Instant", "java.time.LocalDate",
            "java.time.LocalDateTime", "java.util.Date"
    );

    @Override
    public Set<String> supportedJavaTypes() {
        return TYPES;
    }

    @Override
    public boolean needsNullCheck(String javaType) {
        return true;
    }

    // --- Serialization: DEFAULT ---

    @Override
    public void emitSerializeToMapDefault(MethodSpec.Builder builder, String key, String getExpr,
                                          String javaType) {
        switch (javaType) {
            case "java.time.Instant" -> builder.addStatement(
                    "map.put($S, $T.valueOf($L.getEpochSecond()))", key, BigInteger.class, getExpr);
            case "java.time.LocalDate" -> builder.addStatement(
                    "map.put($S, $T.valueOf($L.toEpochDay()))", key, BigInteger.class, getExpr);
            case "java.time.LocalDateTime" -> builder.addStatement(
                    "map.put($S, $L.toString())", key, getExpr);
            case "java.util.Date" -> builder.addStatement(
                    "map.put($S, $T.valueOf($L.getTime()))", key, BigInteger.class, getExpr);
        }
    }

    // --- Serialization: STRING ---

    @Override
    public void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                         String javaType) {
        switch (javaType) {
            case "java.time.Instant", "java.time.LocalDate" ->
                    builder.addStatement("map.put($S, $L.toString())", key, getExpr);
            case "java.time.LocalDateTime" ->
                    emitSerializeToMapDefault(builder, key, getExpr, javaType);
            case "java.util.Date" ->
                    builder.addStatement("map.put($S, $L.toInstant().toString())", key, getExpr);
        }
    }

    // --- Serialization: list element ---

    @Override
    public void emitSerializeToList(MethodSpec.Builder builder, String javaType) {
        switch (javaType) {
            case "java.time.Instant" ->
                    builder.addStatement("_list.add($T.valueOf(_el.getEpochSecond()))", BigInteger.class);
            case "java.time.LocalDate" ->
                    builder.addStatement("_list.add($T.valueOf(_el.toEpochDay()))", BigInteger.class);
            case "java.time.LocalDateTime" ->
                    builder.addStatement("_list.add(_el.toString())");
            case "java.util.Date" ->
                    builder.addStatement("_list.add($T.valueOf(_el.getTime()))", BigInteger.class);
        }
    }

    // --- Deserialization: DEFAULT ---

    @Override
    public void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             MetadataFieldAccessor accessor) {
        switch (field.getJavaTypeName()) {
            case "java.time.Instant" -> {
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                accessor.emitSetFmt(builder, field,
                        "$T.ofEpochSecond((($T) v).longValue())", Instant.class, BigInteger.class);
                builder.endControlFlow();
            }
            case "java.time.LocalDate" -> {
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                accessor.emitSetFmt(builder, field,
                        "$T.ofEpochDay((($T) v).longValue())", LocalDate.class, BigInteger.class);
                builder.endControlFlow();
            }
            case "java.time.LocalDateTime" -> {
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                accessor.emitSet(builder, field, "$T.parse((String) v)", LocalDateTime.class);
                builder.endControlFlow();
            }
            case "java.util.Date" -> {
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                accessor.emitSetFmt(builder, field,
                        "new $T((($T) v).longValue())", Date.class, BigInteger.class);
                builder.endControlFlow();
            }
        }
    }

    // --- Deserialization: STRING ---

    @Override
    public void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        switch (field.getJavaTypeName()) {
            case "java.time.Instant" -> {
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                accessor.emitSet(builder, field, "$T.parse((String) v)", Instant.class);
                builder.endControlFlow();
            }
            case "java.time.LocalDate" -> {
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                accessor.emitSet(builder, field, "$T.parse((String) v)", LocalDate.class);
                builder.endControlFlow();
            }
            case "java.time.LocalDateTime" ->
                    emitDeserializeScalarDefault(builder, field, accessor);
            case "java.util.Date" -> {
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                accessor.emitSetFmt(builder, field,
                        "$T.from($T.parse((String) v))", Date.class, Instant.class);
                builder.endControlFlow();
            }
        }
    }

    // --- Deserialization: list element ---

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        switch (javaType) {
            case "java.time.Instant" -> {
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add($T.ofEpochSecond((($T) _el).longValue()))",
                        Instant.class, BigInteger.class);
                builder.endControlFlow();
            }
            case "java.time.LocalDate" -> {
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add($T.ofEpochDay((($T) _el).longValue()))",
                        LocalDate.class, BigInteger.class);
                builder.endControlFlow();
            }
            case "java.time.LocalDateTime" -> {
                builder.beginControlFlow("if (_el instanceof $T)", String.class);
                builder.addStatement("_result.add($T.parse(($T) _el))", LocalDateTime.class, String.class);
                builder.endControlFlow();
            }
            case "java.util.Date" -> {
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add(new $T((($T) _el).longValue()))",
                        Date.class, BigInteger.class);
                builder.endControlFlow();
            }
        }
    }

    // --- Deserialization: Optional ---

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        MetadataFieldAccessor accessor) {
        switch (field.getElementTypeName()) {
            case "java.time.Instant" -> {
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                accessor.emitOptionalOfSetFmt(builder, field,
                        "$T.ofEpochSecond((($T) v).longValue())", Instant.class, BigInteger.class);
                builder.nextControlFlow("else");
                accessor.emitOptionalEmpty(builder, field);
                builder.endControlFlow();
            }
            case "java.time.LocalDate" -> {
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                accessor.emitOptionalOfSetFmt(builder, field,
                        "$T.ofEpochDay((($T) v).longValue())", LocalDate.class, BigInteger.class);
                builder.nextControlFlow("else");
                accessor.emitOptionalEmpty(builder, field);
                builder.endControlFlow();
            }
            case "java.time.LocalDateTime" -> {
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                accessor.emitOptionalOfSet(builder, field,
                        "$T.parse((String) v)", LocalDateTime.class);
                builder.nextControlFlow("else");
                accessor.emitOptionalEmpty(builder, field);
                builder.endControlFlow();
            }
            case "java.util.Date" -> {
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                accessor.emitOptionalOfSetFmt(builder, field,
                        "new $T((($T) v).longValue())", Date.class, BigInteger.class);
                builder.nextControlFlow("else");
                accessor.emitOptionalEmpty(builder, field);
                builder.endControlFlow();
            }
        }
    }
}
