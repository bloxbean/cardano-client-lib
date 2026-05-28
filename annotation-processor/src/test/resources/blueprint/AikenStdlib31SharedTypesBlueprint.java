package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

/**
 * Synthetic blueprint marker for an Aiken stdlib v3.1 contract that exercises the
 * shared-type resolution path (Address, PaymentCredential, StakeCredential,
 * OutputReference, VerificationKeyHash, ScriptHash).
 *
 * <p>The blueprint's stdlib definitions mirror real Aiken v3.x output verbatim, so
 * {@code AikenBlueprintTypeRegistry} should match them via SchemaSignature lookup
 * and resolve every shared type to its prebuilt class in
 * {@code com.bloxbean.cardano.client.plutus.aiken.blueprint.std}. The processor
 * must therefore NOT generate local copies and MUST emit the corresponding
 * {@code *Converter} classes for each resolved type.</p>
 */
@Blueprint(fileInResources = "blueprint/aiken-stdlib-3.1-shared-types.json",
           packageName = "com.test.aikenstdlib31")
@AikenStdlib(AikenStdlibVersion.V3)
public interface AikenStdlib31SharedTypesBlueprint {
}
