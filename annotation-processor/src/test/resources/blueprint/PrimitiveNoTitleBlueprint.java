package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * Synthetic blueprint exercising primitives without explicit titles
 * ({@code ByteArray}, {@code Int}, {@code Bool} — only dataType set).
 */
@Blueprint(fileInResources = "blueprint/primitive-no-title.json",
           packageName = "com.test.primitivenotitle")
public interface PrimitiveNoTitleBlueprint {
}
