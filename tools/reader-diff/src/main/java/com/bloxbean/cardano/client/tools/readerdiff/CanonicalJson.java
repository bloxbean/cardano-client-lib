package com.bloxbean.cardano.client.tools.readerdiff;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.impl.ObjectIdWriter;
import com.fasterxml.jackson.databind.ser.impl.UnknownSerializer;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A model as canonical JSON, built to miss nothing:
 * <ul>
 *     <li>every field, by field access (Lombok classes without getters are covered); the models' Jackson annotations
 *     ({@code @JsonIgnore}, custom serializers and so on) are off, as they describe an API and not the model;</li>
 *     <li>each object has its runtime class in {@code @class}, as two classes can have the same fields
 *     ({@code StakeRegistration} and {@code StakeDeregistration});</li>
 *     <li>properties are sorted; byte arrays are hex; a {@link BigDecimal} is a string, which keeps its scale;</li>
 *     <li>a cbor-java {@link DataItem} kept in a model is the hex of its encoding, in its own order, tags and chunks
 *     included. That is all it holds: its fields also hold hashes that differ between class loaders and JVMs, as its
 *     {@code hashCode} starts from an enum's identity hash;</li>
 *     <li>a map or set without an order of its own (a {@code HashMap}, {@code HashSet} or {@code Set.of}) is sorted by
 *     the JSON of its keys or elements. One with an order ({@code LinkedHashMap}, {@code TreeMap} and so on) stays in
 *     that order: callers see it, so it is part of the result. Keys that are objects are written as JSON, never with
 *     {@code toString}.</li>
 * </ul>
 * Runs inside a side's class loader, on that side's Jackson.
 */
final class CanonicalJson {
    private static final long BIG_STACK = 1L << 30;

    private final ObjectMapper mapper;

    @SuppressWarnings({"rawtypes", "unchecked"})
    CanonicalJson() {
        SimpleModule module = new SimpleModule("canonical");
        module.addSerializer(byte[].class, new StdSerializer<>(byte[].class) {
            @Override
            public void serialize(byte[] value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(HexFormat.of().formatHex(value));
            }
        });
        module.addSerializer(BigDecimal.class, new StdSerializer<>(BigDecimal.class) {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addSerializer(DataItem.class, new StdSerializer<>(DataItem.class) {
            @Override
            public void serialize(DataItem value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(encoded(value));
            }
        });
        module.addSerializer((Class) Map.class, new MapWriter());
        module.addSerializer((Class) Set.class, new SetWriter());
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription description,
                                                      JsonSerializer<?> serializer) {
                if (serializer instanceof BeanSerializerBase bean)
                    return new TaggedBean(bean);
                if (serializer instanceof UnknownSerializer) // a class with no fields
                    return new EmptyBean();
                return serializer;
            }
        });
        JsonFactory factory = JsonFactory.builder()
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        mapper = JsonMapper.builder(factory)
                .disable(MapperFeature.USE_ANNOTATIONS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .visibility(PropertyAccessor.ALL, Visibility.NONE)
                .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                .addModule(module)
                .build();
    }

    /**
     * @return the JSON of a model. A model too deep for this thread's stack is written on a much larger one: how deep a
     * parser can go is what is being compared, not how deep this class can.
     */
    String write(Object model) throws Exception {
        try {
            return mapper.writeValueAsString(model);
        } catch (StackOverflowError | JsonMappingException e) {
            return writeOnBigStack(model);
        }
    }

    private String writeOnBigStack(Object model) throws Exception {
        AtomicReference<String> json = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                json.set(mapper.writeValueAsString(model));
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "canonical-json", BIG_STACK);
        thread.start();
        thread.join();
        if (failure.get() != null)
            throw new IOException("cannot write the model as JSON", failure.get());
        return json.get();
    }

    /** @return the hex of an item's encoding, as it stands: not sorted */
    static String encoded(DataItem item) throws IOException {
        try {
            return HexFormat.of().formatHex(CborSerializationUtil.serialize(item, false));
        } catch (CborException e) {
            throw new IOException(e);
        }
    }

    /** The name of a map key: a scalar as itself, anything else as its JSON. */
    String keyName(Object key) throws IOException {
        return switch (key) {
            case null -> "null";
            case String s -> s;
            case Number n -> n.toString();
            case Boolean b -> b.toString();
            case Enum<?> e -> e.name();
            case byte[] bytes -> HexFormat.of().formatHex(bytes);
            case DataItem item -> encoded(item);
            default -> mapper.writeValueAsString(key);
        };
    }

    private final class MapWriter extends StdSerializer<Map<?, ?>> {
        @SuppressWarnings("unchecked")
        MapWriter() {
            super((Class<Map<?, ?>>) (Class<?>) Map.class);
        }

        @Override
        public void serialize(Map<?, ?> map, JsonGenerator gen, SerializerProvider provider) throws IOException {
            Map<String, Object> named = map instanceof LinkedHashMap || map instanceof SortedMap
                    ? new LinkedHashMap<>() : new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet())
                named.put(keyName(entry.getKey()), entry.getValue());
            gen.writeStartObject();
            for (Map.Entry<String, Object> entry : named.entrySet()) {
                gen.writeFieldName(entry.getKey());
                provider.defaultSerializeValue(entry.getValue(), gen);
            }
            gen.writeEndObject();
        }
    }

    private final class SetWriter extends StdSerializer<Set<?>> {
        @SuppressWarnings("unchecked")
        SetWriter() {
            super((Class<Set<?>>) (Class<?>) Set.class);
        }

        @Override
        public void serialize(Set<?> set, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartArray();
            if (set instanceof LinkedHashSet || set instanceof SortedSet) {
                for (Object element : set)
                    provider.defaultSerializeValue(element, gen);
            } else {
                List<String> elements = new ArrayList<>(set.size());
                for (Object element : set)
                    elements.add(mapper.writeValueAsString(element));
                Collections.sort(elements);
                for (String element : elements)
                    gen.writeRawValue(element);
            }
            gen.writeEndArray();
        }
    }

    /** Jackson's writer of a class's fields, with the class first. */
    private static final class TaggedBean extends BeanSerializerBase {
        TaggedBean(BeanSerializerBase source) {
            super(source);
        }

        @Override
        public void serialize(Object bean, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(bean);
            gen.writeStringField("@class", bean.getClass().getName());
            serializeFields(bean, gen, provider);
            gen.writeEndObject();
        }

        // Annotations are off, so Jackson has no reason to call these with anything that changes the writer.
        @Override
        public BeanSerializerBase withObjectIdWriter(ObjectIdWriter objectIdWriter) {
            return this;
        }

        @Override
        protected BeanSerializerBase withByNameInclusion(Set<String> toIgnore, Set<String> toInclude) {
            return this;
        }

        @Override
        protected BeanSerializerBase asArraySerializer() {
            return this;
        }

        @Override
        public BeanSerializerBase withFilterId(Object filterId) {
            return this;
        }

        @Override
        protected BeanSerializerBase withProperties(BeanPropertyWriter[] properties,
                                                    BeanPropertyWriter[] filteredProperties) {
            return this;
        }
    }

    private static final class EmptyBean extends JsonSerializer<Object> {
        @Override
        public void serialize(Object bean, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(bean);
            gen.writeStringField("@class", bean.getClass().getName());
            gen.writeEndObject();
        }
    }
}
