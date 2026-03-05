package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry to track generated types and avoid duplication across rounds.
 * Not enforced in Phase 1; provided for future use.
 */
public class GeneratedTypesRegistry {

    private final Set<String> generated = new HashSet<>();
    private final Set<String> interfaces = new HashSet<>();

    public boolean markGenerated(String packageName, String className) {
        return generated.add(key(packageName, className));
    }

    public void markInterface(String packageName, String className) {
        interfaces.add(key(packageName, className));
    }

    public boolean isInterface(String packageName, String className) {
        return interfaces.contains(key(packageName, className));
    }

    public void clear() {
        generated.clear();
        interfaces.clear();
    }

    private String key(String pkg, String cls) {
        return (pkg == null ? "" : pkg) + ":" + (cls == null ? "" : cls);
    }

}
