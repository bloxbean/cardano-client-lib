package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared;

import javax.annotation.processing.ProcessingEnvironment;

public final class SharedTypeLookupFactory {

    public static final String OPTION_ENABLE_REGISTRY = "cardano.registry.enable";
    public static final String OPTION_DISABLE_REGISTRY = "cardano.registry.disable";

    private SharedTypeLookupFactory() {
    }

    public static SharedTypeLookup create(ProcessingEnvironment processingEnv) {
        var options = processingEnv.getOptions();
        boolean disable = Boolean.parseBoolean(options.getOrDefault(OPTION_DISABLE_REGISTRY, "false"));

        boolean enabled = options.containsKey(OPTION_ENABLE_REGISTRY)
                ? Boolean.parseBoolean(options.get(OPTION_ENABLE_REGISTRY))
                : true;

        if (disable || !enabled)
            return SharedTypeLookup.disabled();

        ClassLoader classLoader = SharedTypeLookupFactory.class.getClassLoader();

        return new ServiceLoaderSharedTypeLookup(classLoader);
    }

}
