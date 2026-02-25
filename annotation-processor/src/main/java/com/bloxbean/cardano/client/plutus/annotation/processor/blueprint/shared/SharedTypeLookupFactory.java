package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared;

import javax.annotation.processing.ProcessingEnvironment;

public final class SharedTypeLookupFactory {

    public static final String OPTION_ENABLE_REGISTRY = "cardano.registry.enable";

    private SharedTypeLookupFactory() {
    }

    public static SharedTypeLookup create(ProcessingEnvironment processingEnv) {
        var options = processingEnv.getOptions();
        boolean enabled = Boolean.parseBoolean(options.getOrDefault(OPTION_ENABLE_REGISTRY, "true"));

        if (!enabled)
            return SharedTypeLookup.disabled();

        ClassLoader classLoader = SharedTypeLookupFactory.class.getClassLoader();

        return new ServiceLoaderSharedTypeLookup(classLoader);
    }

}
