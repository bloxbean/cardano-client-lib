package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;

import java.io.IOException;

/**
 * Strict reading of core TxPlan intents, shared by {@link TxPlan#from(String)} and {@link TxPlanCodec}.
 * <p>
 * A strict reader rejects what the lenient one silently drops or coerces:
 * <ul>
 *     <li>unknown intent fields: core intents carry {@code @JsonIgnoreProperties(ignoreUnknown = true)}, which is
 *     ignored here, so {@code donation_amout: 5} fails instead of becoming {@code donation_amount = null}</li>
 *     <li>names of {@code @JsonIgnore} Java-only fields</li>
 *     <li>fractional numbers for integer fields ({@code quantity: 2.5} is not {@code 2})</li>
 *     <li>numeric enum indexes ({@code role: 0} is not {@code spend})</li>
 *     <li>UTxO references with extra or missing keys (see {@code UtxoRefDeserializer})</li>
 * </ul>
 * TxFlow's portable compiler applies the same rules to embedded transactions. Extension intents keep their own
 * {@code ignoreUnknown} setting; their codec validates them.
 */
final class StrictReading {

    private StrictReading() {
    }

    /**
     * Makes {@code mapper} read core intents strictly. Call it on a copy: it changes the mapper's configuration.
     *
     * @param mapper a mapper copy used to read TxPlan documents
     * @return the same mapper
     */
    static ObjectMapper apply(ObjectMapper mapper) {
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Also the signal UtxoRefDeserializer uses to check reference shapes strictly.
        mapper.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        mapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        AnnotationIntrospector existing = mapper.getDeserializationConfig().getAnnotationIntrospector();
        mapper.setAnnotationIntrospector(AnnotationIntrospectorPair.pair(new CoreIntentFieldsAreKnown(), existing));
        mapper.addHandler(new DiscriminatorIsKnown());
        return mapper;
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
