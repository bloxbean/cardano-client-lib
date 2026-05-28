package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Step 3 hello-world contract: minimal validator whose datum carries an Aiken
 * {@code aiken/crypto/VerificationKeyHash} (a shared stdlib v3 type) rather than
 * a bare {@code ByteArray}. The accompanying devnet test then confirms the
 * type resolved via {@code AikenBlueprintTypeRegistry} actually round-trips
 * through ledger-side {@code PlutusData} during a real lock/unlock.
 */
@Blueprint(fileInResources = "blueprint/hello_world/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface HelloWorldDevnetBlueprint {
}
