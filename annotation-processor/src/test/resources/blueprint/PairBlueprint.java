package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * Synthetic V3-style blueprint exercising the Pair codegen path.
 * A field with {@code dataType: "pair"} should generate a {@code Pair<L, R>}-typed
 * Java field — without a {@code Pair.java} class being emitted (Pair is a built-in container).
 */
// Test-fixture file: loaded by google-testing-compile via JavaFileObjects.forResource,
// not from a source root. Path/package decoupling is intentional.
@SuppressWarnings("java:S1598")
@Blueprint(fileInResources = "blueprint/pair-test.json",
           packageName = "com.test.pair")
public interface PairBlueprint {
}
