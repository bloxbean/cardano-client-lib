package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Drives {@link HelloWorldDevnetTest} — same shape as the lock contract
 * but the datum's {@code owner} field is typed
 * {@code aiken/crypto/VerificationKeyHash} so the registry resolution
 * path is exercised end-to-end on chain.
 */
@Blueprint(fileInResources = "blueprint/hello_world/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface HelloWorldDevnetBlueprint {
}
