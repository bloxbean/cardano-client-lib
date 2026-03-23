package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.processor.type.*;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry that maps Java type names to their {@link MetadataTypeCodeGen} strategy.
 * Single-lookup dispatch replaces all switch statements in the monolith.
 */
public class MetadataTypeCodeGenRegistry {

    private final Map<String, MetadataTypeCodeGen> generators = new HashMap<>();

    /**
     * -- GETTER --
     *  Returns the byte[] strategy for direct access to STRING_HEX / STRING_BASE64 methods.
     */
    @Getter
    private final ByteArrayCodeGen byteArrayCodeGen;

    public MetadataTypeCodeGenRegistry() {
        byteArrayCodeGen = new ByteArrayCodeGen();
        register(new StringCodeGen());
        register(byteArrayCodeGen);
        register(new BigIntegerCodeGen());
        register(new BigDecimalCodeGen());
        register(new IntegralCodeGen());
        register(new BooleanCodeGen());
        register(new FloatingPointCodeGen());
        register(new CharCodeGen());
        register(new UriCodeGen());
        register(new UrlCodeGen());
        register(new UuidCodeGen());
        register(new CurrencyCodeGen());
        register(new LocaleCodeGen());
        register(new InstantCodeGen());
        register(new LocalDateCodeGen());
        register(new LocalDateTimeCodeGen());
        register(new DateCodeGen());
        register(new DurationCodeGen());
    }

    private void register(MetadataTypeCodeGen codeGen) {
        for (String type : codeGen.supportedJavaTypes()) {
            generators.put(type, codeGen);
        }
    }

    /**
     * Look up the code generator for the given Java type name.
     *
     * @throws IllegalArgumentException if no strategy is registered for the type
     */
    public MetadataTypeCodeGen get(String javaTypeName) {
        MetadataTypeCodeGen codeGen = generators.get(javaTypeName);
        if (codeGen == null) {
            throw new IllegalArgumentException("No MetadataTypeCodeGen registered for: " + javaTypeName);
        }

        return codeGen;
    }

}
