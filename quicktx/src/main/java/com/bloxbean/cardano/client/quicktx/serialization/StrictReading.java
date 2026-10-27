package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.util.AccessPattern;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Strict reading of core TxPlan intents, shared by {@link TxPlan#from(String)} and {@link TxPlanCodec}.
 * <p>
 * A strict reader rejects what the lenient one silently drops or coerces:
 * <ul>
 *     <li>unknown intent fields: core intents carry {@code @JsonIgnoreProperties(ignoreUnknown = true)}, which is
 *     ignored here, so {@code donation_amout: 5} fails instead of becoming {@code donation_amount = null}</li>
 *     <li>names of {@code @JsonIgnore} Java-only fields</li>
 *     <li>fractional numbers for integer fields ({@code quantity: 2.5} is not {@code 2}). A whole number written as a
 *     float ({@code 2000000.0}, as JSON tools often write it) still reads as that integer.</li>
 *     <li>numeric enum indexes ({@code role: 0} is not {@code spend})</li>
 *     <li>an empty string for a non-text value ({@code update: ""} is not {@code false}), and a number for a boolean
 *     ({@code is_update: 1}). The strings {@code "true"} and {@code "false"} still read as booleans, because
 *     {@code TxPlan.from} substitutes a quoted {@code '${flag}'} as text.</li>
 *     <li>UTxO references with extra or missing keys (see {@code UtxoRefDeserializer})</li>
 *     <li>explicit {@code null} values, including an empty value ({@code address:} with nothing after it), the
 *     string {@code "null"} for a primitive, and {@code null} elements in a list ({@code amounts: [null]}). The
 *     canonical writer never writes {@code null}.</li>
 *     <li>an intent that sets a property under two of its names, e.g. both {@code utxo_refs} and its alias
 *     {@code refs}, and a pool intent whose {@code type} disagrees with its update flag
 *     (see {@link #checkDocument(JsonNode)})</li>
 *     <li>a declared variable whose value is itself a {@code ${...}} template (see {@link #checkVariables(JsonNode)})</li>
 *     <li>YAML aliases ({@code *name}), which the YAML reader would otherwise read as the string {@code "name"}, and
 *     unquoted numbers not written in plain decimal form: {@code 010} (octal 8), {@code 0x10}, {@code 1_000},
 *     {@code 0102} (read as 66 even for a hex string field), {@code +5}, {@code .5}, {@code 1e3}
 *     (see {@link #checkYamlText(String)})</li>
 *     <li>a second YAML document with content after {@code ---}</li>
 *     <li>an intent written as a list ({@code [payment, {...}]}) instead of a mapping with {@code type}</li>
 * </ul>
 * These rules apply to the whole document. Extension intent classes keep their own {@code ignoreUnknown} setting, but
 * the scalar, null and YAML rules apply to their fields too.
 * TxFlow's portable compiler builds its strict mapper with {@link #apply(ObjectMapper)}, so embedded transactions are
 * bound by the same rules.
 * <p>
 * Shared with the TxFlow module; not a stable public API.
 */
public final class StrictReading {

    /** Context attribute set on strict mappers; custom deserializers read it to apply their own strict checks. */
    public static final String STRICT_ATTRIBUTE = "com.bloxbean.cardano.client.quicktx.strictReading";

    private static final ObjectMapper INTROSPECTION = new ObjectMapper();
    private static final YAMLFactory YAML = new YAMLFactory();
    private static final Map<String, Class<?>> CORE_INTENTS = coreIntents();
    private static final Map<Class<?>, List<Set<String>>> ALIAS_GROUPS = new ConcurrentHashMap<>();
    private static final SimpleModule WHOLE_NUMBERS = new SimpleModule("TxPlanWholeNumbers")
            .addDeserializer(BigInteger.class, new WholeNumber<>(BigInteger.class, Function.identity()))
            .addDeserializer(Long.class, new WholeNumber<>(Long.class, BigInteger::longValueExact))
            .addDeserializer(long.class, new WholeNumber<>(long.class, BigInteger::longValueExact))
            .addDeserializer(Integer.class, new WholeNumber<>(Integer.class, BigInteger::intValueExact))
            .addDeserializer(int.class, new WholeNumber<>(int.class, BigInteger::intValueExact));

    private StrictReading() {
    }

    /**
     * Makes {@code mapper} read core intents strictly. Call it on a copy: it changes the mapper's configuration.
     *
     * @param mapper a mapper copy used to read TxPlan documents
     * @return the same mapper
     */
    public static ObjectMapper apply(ObjectMapper mapper) {
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        // Also the signal UtxoRefDeserializer uses to check reference shapes strictly.
        mapper.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        mapper.registerModule(WHOLE_NUMBERS);
        mapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        mapper.coercionConfigDefaults().setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        for (CoercionInputShape shape : List.of(CoercionInputShape.Integer, CoercionInputShape.Float))
            mapper.coercionConfigFor(LogicalType.Boolean).setCoercion(shape, CoercionAction.Fail);
        mapper.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.FAIL));
        JsonSetter.Value noNullElements = JsonSetter.Value.forContentNulls(Nulls.FAIL);
        mapper.configOverride(List.class).setSetterInfo(noNullElements);
        mapper.configOverride(Set.class).setSetterInfo(noNullElements);
        AnnotationIntrospector existing = mapper.getDeserializationConfig().getAnnotationIntrospector();
        mapper.setAnnotationIntrospector(AnnotationIntrospectorPair.pair(new CoreIntentFieldsAreKnown(), existing));
        mapper.addHandler(new DiscriminatorIsKnown());
        mapper.setDefaultAttributes(mapper.getDeserializationConfig().getAttributes()
                .withSharedAttribute(STRICT_ATTRIBUTE, Boolean.TRUE));
        return mapper;
    }

    /** True when {@code context} belongs to a mapper configured by {@link #apply(ObjectMapper)}. */
    public static boolean isStrict(DeserializationContext context) {
        return Boolean.TRUE.equals(context.getAttribute(STRICT_ATTRIBUTE));
    }

    /**
     * Rejects YAML constructs that would be read as something other than what is written: aliases, and unquoted numbers
     * whose text is not the plain decimal form of the value they are read as.
     *
     * @param yaml TxPlan YAML text, after any textual variable substitution
     * @throws IllegalArgumentException naming the offending value and its location
     * @throws IOException when the text is not well-formed YAML
     */
    public static void checkYamlText(String yaml) throws IOException {
        try (JsonParser parser = YAML.createParser(yaml)) {
            int roots = 0;
            int depth = 0;
            for (JsonToken token = parser.nextToken(); token != null; token = parser.nextToken()) {
                // An empty document (a trailing ---) reads as a null root; only a document with content counts.
                if (depth == 0 && (token.isStructStart() || token.isScalarValue()) && token != JsonToken.VALUE_NULL
                        && ++roots > 1)
                    throw new IllegalArgumentException("A TxPlan file holds one YAML document; found another after ---");
                if (token.isStructStart()) depth++;
                else if (token.isStructEnd()) depth--;
                if (!token.isScalarValue()) continue;
                if (((YAMLParser) parser).isCurrentAlias())
                    throw new IllegalArgumentException("YAML alias *" + parser.getText() + " at "
                            + parser.getParsingContext().pathAsPointer() + " is not supported; repeat the value instead");
                // A decimal variable value keeps its written scale (1.50) and is substituted as that number, so only
                // a text that is not a decimal at all (.inf, 1_0.5) is rejected there.
                if (token == JsonToken.VALUE_NUMBER_FLOAT
                        && parser.getParsingContext().pathAsPointer().toString().startsWith("/variables/")) {
                    if (!isDecimal(parser.getText()))
                        throw new IllegalArgumentException("Number " + parser.getText() + " at "
                                + parser.getParsingContext().pathAsPointer()
                                + " is not written in plain decimal form; write the value in decimal, or quote it to keep the text");
                    continue;
                }
                if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                    String written = parser.getText();
                    String canonical;
                    try {
                        // Floats are read as BigDecimal, so compare with that text: 20000000.0 is plain, 1e3 is not.
                        canonical = token == JsonToken.VALUE_NUMBER_FLOAT
                                ? parser.getDecimalValue().toString()
                                : parser.getNumberValue().toString();
                    } catch (IOException | NumberFormatException notANumber) { // .inf, .nan
                        throw new IllegalArgumentException("Number " + written + " at " + parser.getParsingContext().pathAsPointer()
                                + " has no decimal value; quote it to keep the text");
                    }
                    if (!written.equals(canonical))
                        throw new IllegalArgumentException("Number " + written + " at " + parser.getParsingContext().pathAsPointer()
                                + " is not written in plain decimal form (it reads as " + canonical
                                + "); write the value in decimal, or quote it to keep the text");
                }
            }
        }
    }

    private static boolean isDecimal(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException notDecimal) {
            return false;
        }
    }

    /**
     * Rejects intents whose fields contradict each other in a way Jackson would resolve silently by key order: one
     * property set under two names, its canonical name and an alias (for example {@code collect_from} with both
     * {@code utxo_refs} and {@code refs}), and a pool intent whose {@code type} disagrees with {@code is_update} or
     * {@code update}. Called on the resolved document tree by the strict readers.
     *
     * @param document a TxPlan document tree
     * @throws IllegalArgumentException naming the conflict
     */
    public static void checkDocument(JsonNode document) {
        if (document == null) return;
        for (JsonNode entry : document.path("transaction")) {
            JsonNode tx = entry.path("tx");
            for (String array : List.of("inputs", "intents", "scripts")) {
                for (JsonNode intent : tx.path(array)) {
                    // Jackson also binds the list form [type, {...}], which would skip the checks below.
                    if (intent.isArray())
                        throw new IllegalArgumentException("An entry in tx." + array + " is a list; write each intent as a"
                                + " mapping with a type field");
                    Class<?> type = CORE_INTENTS.get(intent.path("type").asText(""));
                    if (type == null || !intent.isObject()) continue;
                    checkPoolUpdateFlag(intent);
                    for (Set<String> names : ALIAS_GROUPS.computeIfAbsent(type, StrictReading::aliasGroups)) {
                        List<String> present = names.stream().filter(intent::has).sorted().toList();
                        if (present.size() > 1)
                            throw new IllegalArgumentException("Intent " + intent.path("type").asText()
                                    + " sets one property under several names " + present + "; use one");
                    }
                }
            }
        }
    }

    /**
     * Rejects a declared variable whose value is itself a template ({@code v1: "${v2}"}). Substitution is one level, so
     * such a chain resolves a little further on every read and a written plan never reads back the same.
     * {@link TxPlan#from(String)} calls it on the document's {@code variables} section as written, before its textual
     * substitution. {@link TxPlanCodec} does not need it: it replaces a variable literally, never re-reading the value as
     * a template, and reads snapshots with {@code readLiteral}.
     *
     * @param variables the document's {@code variables} section, or null
     * @throws IllegalArgumentException naming the variable
     */
    public static void checkVariables(JsonNode variables) {
        if (variables == null || !variables.isObject()) return;
        variables.fields().forEachRemaining(entry -> {
            // TxPlan.from substitutes ${name} as text, so a name with $, braces or a line break breaks the match.
            if (!entry.getKey().matches("[^${}\\r\\n]+"))
                throw new IllegalArgumentException("Variable name '" + entry.getKey()
                        + "' must not contain $, {, } or a line break");
            if (containsPlaceholder(entry.getValue()))
                throw new IllegalArgumentException("Variable " + entry.getKey() + " refers to another placeholder ("
                        + entry.getValue() + "); variable values must be literal");
        });
    }

    /**
     * Rejects a mapping key that holds a placeholder ({@code '${field}': 5}) anywhere outside {@code variables}.
     * {@link TxPlan#from(String)} substitutes text, so it would rename the key; {@link TxPlanCodec} and the schema
     * substitute values only and reject the key as unknown. Called on the document as written.
     *
     * @param document a TxPlan document tree, before substitution
     * @throws IllegalArgumentException naming the key and its location
     */
    static void checkKeys(JsonNode document) {
        if (document == null || !document.isObject()) return;
        document.fields().forEachRemaining(entry -> {
            if (!"variables".equals(entry.getKey())) {
                checkKey(entry.getKey(), "/");
                checkKeys(entry.getValue(), "/" + entry.getKey());
            }
        });
    }

    private static void checkKeys(JsonNode node, String path) {
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) checkKeys(node.get(i), path + "/" + i);
            return;
        }
        node.fields().forEachRemaining(entry -> {
            checkKey(entry.getKey(), path);
            checkKeys(entry.getValue(), path + "/" + entry.getKey());
        });
    }

    private static void checkKey(String key, String path) {
        if (key.contains("${"))
            throw new IllegalArgumentException("Key '" + key + "' at " + path
                    + " is a placeholder; placeholders are values, so write the key out");
    }

    private static boolean containsPlaceholder(JsonNode value) {
        if (value.isTextual()) return value.asText().contains("${");
        for (JsonNode child : value) if (containsPlaceholder(child)) return true;
        return false;
    }

    private static void checkPoolUpdateFlag(JsonNode intent) {
        String type = intent.path("type").asText();
        if (!"pool_registration".equals(type) && !"pool_update".equals(type)) return;
        for (String flag : List.of("is_update", "update")) {
            JsonNode value = intent.get(flag);
            if (value == null) continue;
            String text = value.asText().trim();
            boolean isUpdate = value.isBoolean() ? value.booleanValue() : Boolean.parseBoolean(text);
            boolean recognised = value.isBoolean() || text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false");
            if (recognised && isUpdate != "pool_update".equals(type))
                throw new IllegalArgumentException("Intent " + type + " has " + flag + ": " + value
                        + ", which contradicts its type; omit " + flag + " and let type decide");
        }
    }

    private static List<Set<String>> aliasGroups(Class<?> type) {
        List<Set<String>> groups = new ArrayList<>();
        BeanDescription description = INTROSPECTION.getDeserializationConfig().introspect(INTROSPECTION.constructType(type));
        for (BeanPropertyDefinition property : description.findProperties()) {
            if (property.findAliases().isEmpty()) continue;
            Set<String> names = new LinkedHashSet<>();
            names.add(property.getName());
            property.findAliases().forEach(alias -> names.add(alias.getSimpleName()));
            groups.add(names);
        }
        return groups;
    }

    private static Map<String, Class<?>> coreIntents() {
        Map<String, Class<?>> intents = new HashMap<>();
        for (JsonSubTypes.Type subtype : TxIntent.class.getAnnotation(JsonSubTypes.class).value())
            intents.put(subtype.name(), subtype.value());
        return Map.copyOf(intents);
    }

    /**
     * Overrides {@code ignoreUnknown} for every class except extension intents. The pair merges ignoral values by
     * OR-ing {@code ignoreUnknown}, so the override is returned without merging; extension intents return null and
     * keep the secondary introspector's value.
     */
    private static final class CoreIntentFieldsAreKnown extends NopAnnotationIntrospector {
        private final AnnotationIntrospector annotations = new JacksonAnnotationIntrospector();

        @Override
        public JsonIgnoreProperties.Value findPropertyIgnoralByName(MapperConfig<?> config, Annotated annotated) {
            if (annotated instanceof AnnotatedClass type && ExtensionIntent.class.isAssignableFrom(type.getRawType()))
                return null;
            JsonIgnoreProperties.Value value = annotations.findPropertyIgnoralByName(config, annotated);
            return (value == null ? JsonIgnoreProperties.Value.empty() : value.withoutIgnoreUnknown()).withoutMerge();
        }
    }

    /**
     * Reads an integer field written as a float. A whole number ({@code 2000000.0}) becomes that integer; a fractional
     * one ({@code 2.5}) is rejected rather than truncated. Every other input goes to Jackson's standard deserializer.
     */
    private static final class WholeNumber<T> extends StdDeserializer<T> {
        private final JsonDeserializer<?> standard;
        private final Function<BigInteger, T> convert;

        WholeNumber(Class<T> type, Function<BigInteger, T> convert) {
            super(type);
            this.standard = type == BigInteger.class
                    ? NumberDeserializers.BigIntegerDeserializer.instance
                    : NumberDeserializers.find(type, type.getName());
            this.convert = convert;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) return (T) standard.deserialize(parser, context);
            BigDecimal value = parser.getDecimalValue();
            BigInteger whole;
            try {
                whole = value.toBigIntegerExact();
            } catch (ArithmeticException fractional) {
                return (T) context.handleWeirdNumberValue(handledType(), value,
                        "has a fractional part; an integer field takes a whole number");
            }
            try {
                return convert.apply(whole);
            } catch (ArithmeticException outOfRange) {
                return (T) context.handleWeirdNumberValue(handledType(), value, "is out of range");
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T getNullValue(DeserializationContext context) throws JsonMappingException {
            return (T) standard.getNullValue(context);
        }

        @Override
        public AccessPattern getNullAccessPattern() {
            return standard.getNullAccessPattern();
        }

        @Override
        public LogicalType logicalType() {
            return LogicalType.Integer;
        }
    }

    /**
     * Core intents use {@code EXISTING_PROPERTY} with a visible {@code type}, so the already-resolved discriminator is
     * offered to the intent, and most intents compute {@code getType()} without a setter.
     */
    private static final class DiscriminatorIsKnown extends DeserializationProblemHandler {
        @Override
        public boolean handleUnknownProperty(DeserializationContext context, JsonParser parser,
                                             JsonDeserializer<?> deserializer, Object beanOrClass,
                                             String propertyName) throws IOException {
            if ("type".equals(propertyName) && beanOrClass instanceof TxIntent) {
                parser.skipChildren();
                return true;
            }
            return false;
        }
    }
}
