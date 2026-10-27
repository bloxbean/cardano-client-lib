package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
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
    /** What Jackson strips from scalar text before parsing it (String.trim: every char up to U+0020). */
    private static final String TRIMMED = "[\\u0000-\\u0020]*";
    static final List<String> INTENT_ARRAYS = List.of("inputs", "intents", "scripts");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Schemas for properties or types read by a custom deserializer, keyed by the deserializer's class name.
     * A custom deserializer missing here fails generation: decide what it accepts and add it.
     */
    private static final Map<String, JsonNode> CUSTOM_DESERIALIZERS = Map.of(
            "com.bloxbean.cardano.client.quicktx.serialization.MetadataDeserializer", NODES.objectNode(),
            // Both read the token's text, so a digit-only hex or address written unquoted is accepted.
            "com.bloxbean.cardano.client.util.serializers.HexToByteArrayDeserializer", scalarText("string", "number"),
            "com.bloxbean.cardano.client.util.serializers.InetAddressDeserializer", scalarText("string", "number"),
            "com.bloxbean.cardano.client.quicktx.intent.UtxoRefDeserializer", ref("inputRef"));

    private final ObjectMapper mapper;
    private final DeserializationConfig config;
    private final DefaultDeserializationContext context;
    private final Map<String, ObjectNode> definitions = new TreeMap<>();
    private final Map<String, ModelShape> modelShapes = new HashMap<>();

    private record ModelShape(JavaType type, String discriminator, String discriminatorValue) {
    }

    private TxPlanSchemaGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
        this.config = mapper.getDeserializationConfig();
        if (config.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
            throw new IllegalStateException("ACCEPT_SINGLE_VALUE_AS_ARRAY is enabled on the reader; the array schemas would be wrong");
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
            // TxPlanCodec resolves variables before binding, so an element, or the whole array, may be a placeholder.
            branches.add(ref(PLACEHOLDER));
            ObjectNode items = NODES.objectNode();
            items.set("anyOf", branches);
            ObjectNode arraySchema = type("array");
            arraySchema.set("items", items);
            txProperties.set(array, orPlaceholder(arraySchema));
        }

        definitions.put(PLACEHOLDER, type("string").put("pattern", "\\$\\{")
                .put("description", "A ${var} or ${{ inputs.x }} placeholder, resolved before binding"));
        definitions.put(INTEGER_LIKE, type("string").put("pattern", "^" + TRIMMED + "[+-]?[0-9]+" + TRIMMED + "$")
                .put("description", "An integer written as a string"));
        definitions.put(BOOLEAN_LIKE, type("string").put("pattern", "^" + TRIMMED + "(true|True|TRUE|false|False|FALSE)" + TRIMMED + "$")
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
        checkEveryBoundPropertyIsListed(type, deserializer, description, discriminator);
        // A polymorphic subtype (discriminator != null) is only reached through an object carrying its type id, so a
        // scalar creator it may have (e.g. Lombok's single-String constructor) is unreachable.
        ValueInstantiator instantiator = deserializer.getValueInstantiator();
        if (discriminator == null && (instantiator.canCreateFromString() || instantiator.canCreateUsingDelegate()
                || instantiator.canCreateUsingArrayDelegate() || instantiator.canCreateFromInt()
                || instantiator.canCreateFromLong() || instantiator.canCreateFromBoolean()
                || instantiator.canCreateFromDouble() || instantiator.canCreateFromBigInteger()
                || instantiator.canCreateFromBigDecimal()))
            throw unsupported(type.getRawClass().getName(), "it can also be created from a scalar or a delegate,"
                    + " which the object schema would not describe");
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

    /**
     * Fails when Jackson binds a property the generator would not list, e.g. an {@code @JsonUnwrapped} member (bound
     * through a separate handler and absent from {@link BeanDeserializerBase#properties()}). Without this check such a
     * field would be accepted by the readers while the regenerated schema stayed unchanged.
     */
    private void checkEveryBoundPropertyIsListed(JavaType type, BeanDeserializerBase deserializer,
                                                 BeanDescription description, String discriminator)
            throws JsonMappingException {
        Set<String> listed = new HashSet<>();
        deserializer.properties().forEachRemaining(property -> listed.add(property.getName()));
        for (BeanPropertyDefinition definition : description.findProperties()) {
            AnnotatedMember member = definition.getPrimaryMember();
            String where = type.getRawClass().getName() + "." + definition.getName();
            if (member != null) {
                Object contentDeserializer = config.getAnnotationIntrospector().findContentDeserializer(member);
                Object keyDeserializer = config.getAnnotationIntrospector().findKeyDeserializer(member);
                if ((contentDeserializer != null && contentDeserializer != JsonDeserializer.None.class)
                        || (keyDeserializer != null && keyDeserializer != KeyDeserializer.None.class))
                    throw unsupported(where, "content or key deserializers are not supported; map the property explicitly");
                JsonFormat.Value format = config.getAnnotationIntrospector().findFormat(member);
                if (format != null && Boolean.TRUE.equals(format.getFeature(JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)))
                    throw unsupported(where, "ACCEPT_SINGLE_VALUE_AS_ARRAY is not supported");
            }
            if (member != null
                    && config.getAnnotationIntrospector().findUnwrappingNameTransformer(member) != null)
                throw unsupported(type.getRawClass().getName() + "." + definition.getName(),
                        "@JsonUnwrapped properties are not supported; map their fields explicitly");
            if (definition.couldDeserialize() && !definition.getName().equals(discriminator)
                    && !listed.contains(definition.getName()))
                throw unsupported(type.getRawClass().getName() + "." + definition.getName(),
                        "Jackson binds this property but its bean deserializer does not list it");
        }
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
        // A deserializer on the type itself overrides the usual scalar/enum/container representation too.
        // Inspect it before the built-in mappings, just as propertySchema does for a member annotation.
        AnnotatedClass annotated = config.introspectClassAnnotations(type).getClassInfo();
        Object custom = config.getAnnotationIntrospector().findDeserializer(annotated);
        if (custom != null && custom != JsonDeserializer.None.class) {
            String name = custom instanceof Class<?> c ? c.getName() : custom.getClass().getName();
            return customSchema(name, where);
        }
        if (JsonNode.class.isAssignableFrom(raw)) return NODES.objectNode();
        // Jackson coerces any scalar to text, and the canonical writer relies on it (unquoted digit-only hex,
        // numeric amounts in String fields), so a string field accepts every scalar.
        if (raw == String.class) return NODES.objectNode().set("type", NODES.arrayNode().add("string").add("number").add("boolean"));
        if (raw == Integer.class || raw == int.class || raw == Long.class || raw == long.class
                || raw == Short.class || raw == short.class || raw == BigInteger.class)
            return orPlaceholder(type("integer"), ref(INTEGER_LIKE));
        if (raw == Boolean.class || raw == boolean.class) return orPlaceholder(type("boolean"), ref(BOOLEAN_LIKE));
        if (type.isEnumType()) {
            for (Field constant : raw.getFields()) {
                if (constant.isEnumConstant() && constant.isAnnotationPresent(JsonAlias.class))
                    throw unsupported(where, "enum constant aliases (@JsonAlias on " + constant.getName() + ") are not supported");
            }
            ArrayNode names = NODES.arrayNode();
            StringJoiner alternatives = new StringJoiner("|", "^" + TRIMMED + "(", ")" + TRIMMED + "$");
            for (Object constant : raw.getEnumConstants()) {
                String name = mapper.convertValue(constant, String.class);
                names.add(name);
                alternatives.add(name.replaceAll("[^A-Za-z0-9_]", "\\\\$0"));
            }
            ObjectNode enumSchema = NODES.objectNode();
            enumSchema.set("enum", names);
            // Jackson also accepts a name with surrounding whitespace.
            return orPlaceholder(enumSchema, type("string").put("pattern", alternatives.toString()));
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
        branches.add(discriminatedOpenObject(typeInfo.property(), ref(PLACEHOLDER)));
        ObjectNode schema = NODES.objectNode();
        schema.set("anyOf", branches);
        return schema;
    }

    /** Generates {@code $defs/model.<SimpleName>} once and returns its key. */
    private String model(JavaType type, String discriminator, String discriminatorValue) throws JsonMappingException {
        Class<?> raw = type.getRawClass();
        String key = MODEL_PREFIX + raw.getSimpleName();
        ModelShape shape = new ModelShape(type, discriminator, discriminatorValue);
        ModelShape existing = modelShapes.putIfAbsent(key, shape);
        if (existing != null) {
            if (existing.type().getRawClass() != raw)
                throw unsupported(raw.getName(), "simple name collides with " + existing.type().getRawClass().getName());
            if (!existing.equals(shape))
                throw unsupported(raw.getName(), key + " is reused with incompatible Java types or discriminators: "
                        + existing + " and " + shape + "; map each shape explicitly");
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

    /** Type ids of the subtypes of a polymorphic base, as Jackson resolves them (incl. {@code @JsonTypeName}). */
    private static Map<String, Class<?>> subtypes(Class<?> base) {
        ObjectMapper mapper = YamlSerializer.getYamlMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        AnnotatedClass annotated = config.introspectClassAnnotations(mapper.constructType(base)).getClassInfo();
        JsonTypeInfo typeInfo = annotated.getAnnotation(JsonTypeInfo.class);
        if (typeInfo == null) throw new IllegalStateException(base.getName() + " has no @JsonTypeInfo");
        if (typeInfo.defaultImpl() != JsonTypeInfo.class)
            throw new IllegalStateException(base.getName() + " declares @JsonTypeInfo(defaultImpl); an unknown type id"
                    + " would then be accepted, which the schema cannot describe");
        Map<String, Class<?>> result = new TreeMap<>();
        for (NamedType subtype : config.getSubtypeResolver().collectAndResolveSubtypesByTypeId(config, annotated)) {
            if (subtype.getType() == base || !subtype.hasName()) continue;
            Class<?> previous = result.put(subtype.getName(), subtype.getType());
            if (previous != null && previous != subtype.getType())
                throw new IllegalStateException("Type id " + subtype.getName() + " maps to both " + previous.getName()
                        + " and " + subtype.getType().getName());
        }
        if (result.isEmpty()) throw new IllegalStateException(base.getName() + " has no named subtypes");
        return result;
    }

    private JavaType constructType(Class<?> type) {
        return mapper.constructType(type);
    }

    private static ObjectNode discriminatedOpenObject(JsonNode discriminator) {
        return discriminatedOpenObject("type", discriminator);
    }

    private static ObjectNode discriminatedOpenObject(String property, JsonNode discriminator) {
        ObjectNode schema = type("object");
        schema.set("required", NODES.arrayNode().add(property));
        ObjectNode properties = NODES.objectNode();
        properties.set(property, discriminator);
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

    private static ObjectNode scalarText(String... types) {
        ArrayNode array = NODES.arrayNode();
        for (String t : types) array.add(t);
        return (ObjectNode) NODES.objectNode().set("type", array);
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

    /** The generated schema of one bean type, for tests of the generator's own rules. */
    static JsonNode describe(Class<?> type) throws JsonMappingException {
        TxPlanSchemaGenerator generator = new TxPlanSchemaGenerator(YamlSerializer.getYamlMapper());
        return generator.beanSchema(generator.constructType(type), null, null);
    }

    /** Names of every definition the generator writes, for tests. */
    static Collection<String> intentDefinitionNames() {
        List<String> names = new ArrayList<>();
        subtypes(TxIntent.class).keySet().forEach(name -> names.add(INTENT_PREFIX + name));
        return names;
    }
}
