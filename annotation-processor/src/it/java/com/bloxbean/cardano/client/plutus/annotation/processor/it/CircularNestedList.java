package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * Circular nested list reference pattern functional test (CIP-57).
 *
 * <p>This blueprint demonstrates circular reference handling via nested lists:</p>
 * <pre>Script → Composite → List&lt;Script&gt;</pre>
 *
 * <p><strong>What This Tests:</strong></p>
 * <ul>
 *   <li>Circular reference detection in PlutusBlueprintLoader (prevents StackOverflowError)</li>
 *   <li>Code generation for self-referencing types via List</li>
 *   <li>Multisig script pattern common in governance and authorization</li>
 * </ul>
 *
 * @see <a href="https://cips.cardano.org/cip/CIP-57">CIP-57 Plutus Contract Blueprints</a>
 */
@Blueprint(fileInResources = "blueprint/circular-nested-list_aiken_v1_1_21.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.circular")
public interface CircularNestedList {
}
