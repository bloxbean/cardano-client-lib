package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry to track generated types and avoid duplication across rounds.
 * Not enforced in Phase 1; provided for future use.
 */
public class GeneratedTypesRegistry {

    private final Set<String> generated = new HashSet<>();

    public boolean markGenerated(String packageName, String className) {
        return generated.add(key(packageName, className));
    }

    private String key(String pkg, String cls) {
        return (pkg == null ? "" : pkg) + ":" + (cls == null ? "" : cls);
    }

}

