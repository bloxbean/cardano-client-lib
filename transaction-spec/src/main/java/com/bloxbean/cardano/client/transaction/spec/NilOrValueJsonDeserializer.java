package com.bloxbean.cardano.client.transaction.spec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Optional;

/**
 * Reads what {@link NilOrValueJsonSerializer} writes, without Jackson's JDK 8 module: {@code null} is nil, an empty
 * Optional, and a value is that value in an Optional. A missing field is left null, which is an absent parameter. Used
 * as a field annotation, it takes the Optional's content type from the field, so one class serves any content type.
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
public class NilOrValueJsonDeserializer extends StdDeserializer<Optional<?>> implements ContextualDeserializer {
    private final transient JsonDeserializer<Object> content;

    public NilOrValueJsonDeserializer() {
        this(null);
    }

    private NilOrValueJsonDeserializer(JsonDeserializer<Object> content) {
        super(Optional.class);
        this.content = content;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
            throws JsonMappingException {
        JavaType type = property != null ? property.getType() : context.getContextualType();
        JavaType contentType = type.containedTypeCount() == 1 ? type.containedType(0)
                : context.constructType(Object.class);
        return new NilOrValueJsonDeserializer(context.findContextualValueDeserializer(contentType, property));
    }

    @Override
    public Optional<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return Optional.ofNullable(content.deserialize(parser, context));
    }

    /** Jackson reads a JSON null here, not through {@link #deserialize}: it is nil. */
    @Override
    public Optional<?> getNullValue(DeserializationContext context) {
        return Optional.empty();
    }
}
