package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * Synthetic blueprint exercising CIP-57 optional-title handling.
 * Contains a definition without a title ({@code types/custom/Data}) and one with a title
 * ({@code types/custom/Action}); the processor should fall back to the definition key
 * when the title is missing.
 */
// Test-fixture file: loaded by google-testing-compile via JavaFileObjects.forResource,
// not from a source root. Path/package decoupling is intentional.
@SuppressWarnings("java:S1598")
@Blueprint(fileInResources = "blueprint/missing-title-test.json",
           packageName = "com.test.missingtitle")
public interface MissingTitleBlueprint {
}
