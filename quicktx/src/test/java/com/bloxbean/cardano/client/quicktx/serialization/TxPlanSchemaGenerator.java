package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Generates the intent part of {@code quicktx-transaction-v1alpha1.schema.json} from the Jackson model the TxPlan
 * readers use (ADR "Generated TxPlan Intent Schema and Strict Intent Validation", D1-D4).
 * <p>
 * Every core intent registered in {@code @JsonSubTypes} on {@link TxIntent} becomes {@code $defs/intent.<type>}; nested
 * beans become {@code $defs/model.<SimpleName>}; the {@code items} of {@code tx.inputs}, {@code tx.intents} and
 * {@code tx.scripts} accept whatever their declared element type accepts. Property names come from the bean
 * deserializer Jackson actually builds, so the schema mirrors what the readers bind. Everything else in the file is
 * hand-written and is left untouched.
 * <p>
 * Run {@code ./gradlew :quicktx:generateTxPlanSchema} to rewrite the committed file; {@code TxPlanSchemaTest} fails
 * while the committed file differs from the generated one.
 */
public final class TxPlanSchemaGenerator {

    static final String SCHEMA_RESOURCE = "/schema/quicktx-transaction-v1alpha1.schema.json";
    static final String INTENT_PREFIX = "intent.";
    static final String MODEL_PREFIX = "model.";
    static final String PLACEHOLDER = "placeholder";
    static final String INTEGER_LIKE = "integerLike";
    static final String BOOLEAN_LIKE = "booleanLike";
    static final List<String> INTENT_ARRAYS = List.of("inputs", "intents", "scripts");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Schemas for properties or types read by a custom deserializer, keyed by the deserializer's class name.
     * A custom deserializer missing here fails generation: decide what it accepts and add it.
     */
    private static final Map<String, JsonNode> CUSTOM_DESERIALIZERS = Map.of(
            "com.bloxbean.cardano.client.quicktx.serialization.MetadataDeserializer", NODES.objectNode(),
            "com.bloxbean.cardano.client.util.serializers.HexToByteArrayDeserializer", type("string"),
            "com.bloxbean.cardano.client.util.serializers.InetAddressDeserializer", type("string"),
            "com.bloxbean.cardano.client.quicktx.intent.UtxoRefDeserializer", ref("inputRef"));

    private final ObjectMapper mapper;
    private final DeserializationConfig config;
    private final DefaultDeserializationContext context;
    private final Map<String, ObjectNode> definitions = new TreeMap<>();
    private final Map<String, String> modelClasses = new HashMap<>();

    private TxPlanSchemaGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
        this.config = mapper.getDeserializationConfig();
        this.context = ((DefaultDeserializationContext) mapper.getDeserializationContext())
                .createDummyInstance(config);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1)
            throw new IllegalArgumentException("Usage: TxPlanSchemaGenerator <schema-file>");
        Path file = Path.of(args[0]);
        Files.writeString(file, generate(Files.readString(file)));
    }

    /**
     * Regenerates the generated keys of a schema document and returns the formatted result.
     *
     * @param schemaJson the current schema file content
     * @return the schema with every generated key rewritten
     */
    public static String generate(String schemaJson) {
        try {
            ObjectNode schema = (ObjectNode) JSON.readTree(schemaJson);
            new TxPlanSchemaGenerator(YamlSerializer.getYamlMapper()).apply(schema);
            return format(schema);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot generate the TxPlan schema", e);
        }
    }

    /** True for a {@code $defs} key the generator owns. */
    static boolean isGeneratedDefinition(String key) {
        return key.startsWith(INTENT_PREFIX) || key.startsWith(MODEL_PREFIX)
                || key.equals(PLACEHOLDER) || key.equals(INTEGER_LIKE) || key.equals(BOOLEAN_LIKE);
    }

    private void apply(ObjectNode schema) throws JsonMappingException {
        ObjectNode defs = (ObjectNode) schema.required("$defs");
        Map<String, Class<?>> intents = subtypes(TxIntent.class);
        for (Map.Entry<String, Class<?>> intent : intents.entrySet()) {
            definitions.put(INTENT_PREFIX + intent.getKey(),
                    beanSchema(constructType(intent.getValue()), "type", intent.getKey()));
        }

        ObjectNode txProperties = (ObjectNode) defs.required("tx").required("properties");
        BeanDeserializerBase txContent = beanDeserializer(constructType(TransactionDocument.TxContent.class));
        for (String array : INTENT_ARRAYS) {
            Class<?> elementType = txContent.findProperty(array).getType().getContentType().getRawClass();
            ArrayNode branches = NODES.arrayNode();
            intents.forEach((name, type) -> {
                if (elementType.isAssignableFrom(type)) branches.add(ref(INTENT_PREFIX + name));
            });
            // Extension intents are allowed only in tx.intents (ADR 0008; FlowExtensions.checkTemplateIntents).
            if ("intents".equals(array)) branches.add(discriminatedOpenObject(type("string").put("pattern", "^[^:]+:.+$")));
            // TxPlanCodec resolves variables before binding, so the discriminator itself may be a placeholder.
            branches.add(discriminatedOpenObject(ref(PLACEHOLDER)));
            ObjectNode items = NODES.objectNode();
            items.set("anyOf", branches);
            ((ObjectNode) txProperties.required(array)).set("items", items);
        }

        definitions.put(PLACEHOLDER, type("string").put("pattern", "\\$\\{")
                .put("description", "A ${var} or ${{ inputs.x }} placeholder, resolved before binding"));
        definitions.put(INTEGER_LIKE, type("string").put("pattern", "^\\s*[+-]?[0-9]+\\s*$")
                .put("description", "An integer written as a string"));
        definitions.put(BOOLEAN_LIKE, type("string").put("pattern", "^\\s*(true|True|TRUE|false|False|FALSE)\\s*$")
                .put("description", "A boolean written as a string, e.g. a quoted '${flag}' substituted as text"));

        Map<String, JsonNode> rebuilt = new LinkedHashMap<>();
        defs.fields().forEachRemaining(e -> {
            if (!isGeneratedDefinition(e.getKey())) rebuilt.put(e.getKey(), e.getValue());
        });
        rebuilt.putAll(definitions);
        defs.removeAll();
        defs.setAll(rebuilt);
    }

    private ObjectNode beanSchema(JavaType type, String discriminator, String discriminatorValue)
            throws JsonMappingException {
        BeanDeserializerBase deserializer = beanDeserializer(type);
        BeanDescription description = config.introspect(type);
        Map<String, JsonNode> properties = new TreeMap<>();
        for (Iterator<SettableBeanProperty> it = deserializer.properties(); it.hasNext(); ) {
            SettableBeanProperty property = it.next();
            String name = property.getName();
            if (name.equals(discriminator)) continue;
            JsonNode propertySchema = propertySchema(type, property);
            properties.put(name, propertySchema);
            for (PropertyName alias : aliases(description, name)) properties.put(alias.getSimpleName(), propertySchema);
        }
        ObjectNode schema = type("object");
        if (discriminator != null) {
            properties.put(discriminator, NODES.objectNode().put("const", discriminatorValue));
            schema.set("required", NODES.arrayNode().add(discriminator));
        }
        schema.put("additionalProperties", description.findAnySetterAccessor() != null);
        ObjectNode props = NODES.objectNode();
        props.setAll(properties);
        schema.set("properties", props);
        return schema;
    }

    private JsonNode propertySchema(JavaType owner, SettableBeanProperty property) throws JsonMappingException {
        Object custom = config.getAnnotationIntrospector().findDeserializer(property.getMember());
        if (custom != null && custom != JsonDeserializer.None.class) {
            String name = custom instanceof Class<?> c ? c.getName() : custom.getClass().getName();
            return customSchema(name, owner.getRawClass().getName() + "." + property.getName());
        }
        return typeSchema(property.getType(), owner.getRawClass().getName() + "." + property.getName());
    }

    private JsonNode typeSchema(JavaType type, String where) throws JsonMappingException {
        Class<?> raw = type.getRawClass();
        if (JsonNode.class.isAssignableFrom(raw)) return NODES.objectNode();
        // Jackson coerces any scalar to text, and the canonical writer relies on it (unquoted digit-only hex,
        // numeric amounts in String fields), so a string field accepts every scalar.
        if (raw == String.class) return NODES.objectNode().set("type", NODES.arrayNode().add("string").add("number").add("boolean"));
        if (raw == Integer.class || raw == int.class || raw == Long.class || raw == long.class
                || raw == Short.class || raw == short.class || raw == BigInteger.class)
            return orPlaceholder(type("integer"), ref(INTEGER_LIKE));
        if (raw == Boolean.class || raw == boolean.class) return orPlaceholder(type("boolean"), ref(BOOLEAN_LIKE));
        if (type.isEnumType()) {
            ArrayNode names = NODES.arrayNode();
            for (Object constant : raw.getEnumConstants()) names.add(mapper.convertValue(constant, String.class));
            ObjectNode enumSchema = NODES.objectNode();
            enumSchema.set("enum", names);
            return orPlaceholder(enumSchema);
        }
        if (type.isCollectionLikeType() || (type.isArrayType() && raw != byte[].class)) {
            ObjectNode array = type("array");
            array.set("items", typeSchema(type.getContentType(), where + "[]"));
            return orPlaceholder(array);
        }
        if (type.isMapLikeType()) {
            ObjectNode map = type("object");
            map.set("additionalProperties", typeSchema(type.getContentType(), where + "{}"));
            return orPlaceholder(map);
        }
        if (raw.isPrimitive() || raw.getName().startsWith("java.") || raw == byte[].class)
            throw unsupported(where, "type " + type.toCanonical() + " has no schema mapping");

        AnnotatedClass annotated = config.introspectClassAnnotations(type).getClassInfo();
        JsonTypeInfo typeInfo = annotated.getAnnotation(JsonTypeInfo.class);
        if (typeInfo != null) return orPlaceholder(polymorphicSchema(type, typeInfo, where));

        JsonDeserializer<?> deserializer = valueDeserializer(type);
        if (deserializer instanceof BeanDeserializerBase) return orPlaceholder(ref(model(type, null, null)));
        return customSchema(deserializer.getClass().getName(), where);
    }

    private JsonNode polymorphicSchema(JavaType type, JsonTypeInfo typeInfo, String where) throws JsonMappingException {
        if (typeInfo.use() != JsonTypeInfo.Id.NAME
                || (typeInfo.include() != JsonTypeInfo.As.PROPERTY && typeInfo.include() != JsonTypeInfo.As.EXISTING_PROPERTY))
            throw unsupported(where, "only @JsonTypeInfo(use = NAME) with a property discriminator is supported");
        ArrayNode branches = NODES.arrayNode();
        for (Map.Entry<String, Class<?>> subtype : subtypes(type.getRawClass()).entrySet()) {
            branches.add(ref(model(constructType(subtype.getValue()), typeInfo.property(), subtype.getKey())));
        }
        ObjectNode schema = NODES.objectNode();
        schema.set("oneOf", branches);
        return schema;
    }

    /** Generates {@code $defs/model.<SimpleName>} once and returns its key. */
    private String model(JavaType type, String discriminator, String discriminatorValue) throws JsonMappingException {
        Class<?> raw = type.getRawClass();
        String key = MODEL_PREFIX + raw.getSimpleName();
        String existing = modelClasses.putIfAbsent(key, raw.getName());
        if (existing != null) {
            if (!existing.equals(raw.getName()))
                throw unsupported(raw.getName(), "simple name collides with " + existing);
            return key;
        }
        definitions.put(key, NODES.objectNode()); // reserve the key so recursive types terminate
        definitions.put(key, beanSchema(type, discriminator, discriminatorValue));
        return key;
    }

    private JsonNode customSchema(String deserializer, String where) throws JsonMappingException {
        JsonNode schema = CUSTOM_DESERIALIZERS.get(deserializer);
        if (schema == null)
            throw unsupported(where, "custom deserializer " + deserializer
                    + " is not mapped; add it to TxPlanSchemaGenerator.CUSTOM_DESERIALIZERS");
        return schema.isObject() && schema.has("$ref") ? orPlaceholder(schema.deepCopy()) : schema.deepCopy();
    }

    private BeanDeserializerBase beanDeserializer(JavaType type) throws JsonMappingException {
        JsonDeserializer<?> deserializer = valueDeserializer(type);
        if (!(deserializer instanceof BeanDeserializerBase bean))
            throw unsupported(type.toCanonical(), "expected a bean deserializer, found " + deserializer.getClass().getName());
        return bean;
    }

    /** The deserializer Jackson uses for a type, without the polymorphic type wrapper of a subtype. */
    private JsonDeserializer<?> valueDeserializer(JavaType type) throws JsonMappingException {
        // A root lookup wraps a polymorphic subtype in its type deserializer; the value lookup does not.
        return context.findContextualValueDeserializer(type, null);
    }

    private static List<PropertyName> aliases(BeanDescription description, String name) {
        for (BeanPropertyDefinition definition : description.findProperties()) {
            if (definition.getName().equals(name)) return definition.findAliases();
        }
        return List.of();
    }

    private static Map<String, Class<?>> subtypes(Class<?> base) {
        JsonSubTypes subTypes = base.getAnnotation(JsonSubTypes.class);
        if (subTypes == null) throw new IllegalStateException(base.getName() + " has no @JsonSubTypes");
        Map<String, Class<?>> result = new TreeMap<>();
        for (JsonSubTypes.Type subtype : subTypes.value()) {
            Set<String> names = new LinkedHashSet<>(List.of(subtype.names()));
            if (!subtype.name().isEmpty()) names.add(subtype.name());
            for (String name : names) result.put(name, subtype.value());
        }
        return result;
    }

    private JavaType constructType(Class<?> type) {
        return mapper.constructType(type);
    }

    private static ObjectNode discriminatedOpenObject(JsonNode discriminator) {
        ObjectNode schema = type("object");
        schema.set("required", NODES.arrayNode().add("type"));
        ObjectNode properties = NODES.objectNode();
        properties.set("type", discriminator);
        schema.set("properties", properties);
        return schema;
    }

    private static ObjectNode orPlaceholder(JsonNode... schemas) {
        ArrayNode anyOf = NODES.arrayNode();
        for (JsonNode schema : schemas) anyOf.add(schema);
        anyOf.add(ref(PLACEHOLDER));
        ObjectNode schema = NODES.objectNode();
        schema.set("anyOf", anyOf);
        return schema;
    }

    private static ObjectNode type(String type) {
        return NODES.objectNode().put("type", type);
    }

    private static ObjectNode ref(String definition) {
        return NODES.objectNode().put("$ref", "#/$defs/" + definition);
    }

    private static JsonMappingException unsupported(String where, String problem) {
        return new JsonMappingException(null, "TxPlan schema generation failed at " + where + ": " + problem);
    }

    private static String format(JsonNode schema) throws IOException {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER))
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        return JSON.writer(printer).writeValueAsString(schema) + "\n";
    }

    /** Names of every definition the generator writes, for tests. */
    static Collection<String> intentDefinitionNames() {
        List<String> names = new ArrayList<>();
        subtypes(TxIntent.class).keySet().forEach(name -> names.add(INTENT_PREFIX + name));
        return names;
    }
}
