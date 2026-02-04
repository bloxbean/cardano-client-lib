package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.registry.*;
import com.squareup.javapoet.ClassName;

import java.util.*;

class ServiceLoaderSharedTypeLookup implements SharedTypeLookup {

    private final List<BlueprintTypeRegistry> registries;
    private final SchemaSignatureBuilder signatureBuilder = new SchemaSignatureBuilder();
    private final Map<SchemaSignature, Optional<ClassName>> cache = new HashMap<>();

    ServiceLoaderSharedTypeLookup(ClassLoader classLoader) {
        ServiceLoader<BlueprintTypeRegistry> loader = ServiceLoader.load(BlueprintTypeRegistry.class, classLoader);
        List<BlueprintTypeRegistry> loaded = new ArrayList<>();
        loader.iterator().forEachRemaining(loaded::add);
        this.registries = Collections.unmodifiableList(loaded);
    }

    @Override
    public Optional<ClassName> lookup(String namespace, BlueprintSchema schema) {
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

        return cache.computeIfAbsent(signature, sig -> resolve(sig, resolved, namespace));
    }

    private Optional<ClassName> resolve(SchemaSignature signature, BlueprintSchema schema, String namespace) {
        LookupContext context = new LookupContext(namespace, null);
        for (BlueprintTypeRegistry registry : registries) {
            Optional<RegisteredType> registeredType = registry.lookup(signature, schema, context);
            if (registeredType.isPresent()) {
                RegisteredType type = registeredType.get();

                return Optional.of(ClassName.get(type.packageName(), type.simpleName()));
            }
        }

        return Optional.empty();
    }

}
