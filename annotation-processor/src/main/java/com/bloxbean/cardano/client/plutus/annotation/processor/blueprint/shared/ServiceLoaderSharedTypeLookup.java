package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.registry.*;
import com.squareup.javapoet.ClassName;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.*;

class ServiceLoaderSharedTypeLookup implements SharedTypeLookup {

    private final List<BlueprintTypeRegistry> registries;
    private final SchemaSignatureBuilder signatureBuilder = new SchemaSignatureBuilder();
    private final Map<CacheKey, Optional<ClassName>> cache = new HashMap<>();

    ServiceLoaderSharedTypeLookup(ClassLoader classLoader) {
        ServiceLoader<BlueprintTypeRegistry> loader = ServiceLoader.load(BlueprintTypeRegistry.class, classLoader);
        List<BlueprintTypeRegistry> loaded = new ArrayList<>();
        loader.iterator().forEachRemaining(loaded::add);
        this.registries = Collections.unmodifiableList(loaded);
    }

    @Override
    public Optional<ClassName> lookup(String namespace, BlueprintSchema schema) {
        return lookup(namespace, schema, LookupContext.EMPTY);
    }

    @Override
    public Optional<ClassName> lookup(String namespace, BlueprintSchema schema, LookupContext context) {
        if (registries.isEmpty()) {
            return Optional.empty();
        }

        BlueprintSchema resolved = schema.getRefSchema() != null ? schema.getRefSchema() : schema;
        if (resolved.getTitle() != null) {
            Optional<RegisteredType> titleOverride = BlueprintTypeRegistryExtensions.findByTitle(resolved.getTitle());
            if (titleOverride.isPresent()) {
                RegisteredType type = titleOverride.get();

                return Optional.of(ClassName.get(type.packageName(), type.simpleName()));
            }
        }

        SchemaSignature signature = signatureBuilder.build(resolved);

        CacheKey cacheKey = new CacheKey(signature, context.hints());
        return cache.computeIfAbsent(cacheKey, key -> resolve(signature, resolved, namespace, context));
    }

    private Optional<ClassName> resolve(SchemaSignature signature, BlueprintSchema schema,
                                        String namespace, LookupContext callerContext) {
        LookupContext context = new LookupContext(namespace, null, callerContext.hints());
        for (BlueprintTypeRegistry registry : registries) {
            Optional<RegisteredType> registeredType = registry.lookup(signature, schema, context);
            if (registeredType.isPresent()) {
                RegisteredType type = registeredType.get();

                return Optional.of(ClassName.get(type.packageName(), type.simpleName()));
            }
        }

        return Optional.empty();
    }

    @Override
    public LookupContext resolveHints(TypeElement typeElement) {
        Map<String, String> hints = new HashMap<>();
        for (BlueprintTypeRegistry registry : registries) {
            for (AnnotationHintDescriptor desc : registry.annotationHints()) {
                hints.put(desc.hintKey(), extractAnnotationValue(typeElement, desc));
            }
        }
        return new LookupContext(null, null, hints);
    }

    /**
     * Generically extracts an annotation element value from a type element using annotation mirrors.
     * For enum values, strips the package-qualified prefix (e.g. {@code "com.…V2"} → {@code "V2"}).
     * Returns the descriptor's default value when the annotation is absent or the element is not set.
     */
    private String extractAnnotationValue(TypeElement typeElement, AnnotationHintDescriptor desc) {
        for (AnnotationMirror mirror : typeElement.getAnnotationMirrors()) {
            DeclaredType annotationType = mirror.getAnnotationType();
            if (annotationType.toString().equals(desc.annotationFqn())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals(desc.elementName())) {
                        String raw = entry.getValue().getValue().toString();
                        int dot = raw.lastIndexOf('.');
                        return dot >= 0 ? raw.substring(dot + 1) : raw;
                    }
                }
                // Annotation present but element not explicitly set → use default
                return desc.defaultValue();
            }
        }
        return desc.defaultValue();
    }

    /**
     * Cache key that includes both the schema signature and context hints,
     * so lookups for different stdlib versions don't collide.
     */
    private record CacheKey(SchemaSignature signature, Map<String, String> hints) { }

}
