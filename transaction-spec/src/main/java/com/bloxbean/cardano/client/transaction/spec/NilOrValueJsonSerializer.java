package com.bloxbean.cardano.client.transaction.spec;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Optional;

/**
 * Writes a parameter that a Dijkstra update can set to nil (keys 38 and 53) as JSON without Jackson's JDK 8 module: an
 * empty Optional, nil, as {@code null}, and a value as the value. An absent parameter is a null field, which is left
 * out.
 */
class NilOrValueJsonSerializer extends StdSerializer<Optional<?>> {

    @SuppressWarnings("unchecked")
    public NilOrValueJsonSerializer() {
        super((Class<Optional<?>>) (Class<?>) Optional.class);
    }

    @Override
    public void serialize(Optional<?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value.isPresent())
            provider.defaultSerializeValue(value.get(), gen);
        else
            gen.writeNull();
    }
}
