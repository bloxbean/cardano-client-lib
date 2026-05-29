package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * Synthetic V3-style blueprint exercising the Map codegen path.
 * A field with {@code dataType: "map"} should generate a {@code Map<K, V>}-typed
 * Java field — without a {@code Map.java} class being emitted (Map is a built-in container).
 */
// Test-fixture file: loaded by google-testing-compile via JavaFileObjects.forResource,
// not from a source root. Path/package decoupling is intentional.
@SuppressWarnings("java:S1598")
@Blueprint(fileInResources = "blueprint/map-test.json",
           packageName = "com.test.map")
public interface MapBlueprint {
}
