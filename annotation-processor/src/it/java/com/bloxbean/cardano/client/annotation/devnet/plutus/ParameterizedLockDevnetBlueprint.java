package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Drives {@link ParameterizedLockDevnetTest}. The validator takes a
 * compile-time {@code VerificationKeyHash} parameter, so the script hash
 * differs per applied param value — exercises the {@code apply_params}
 * encoding path and the {@code applyParamCompiledCode} constructor branch
 * of the generated validator class.
 */
@Blueprint(fileInResources = "blueprint/parameterized_lock/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface ParameterizedLockDevnetBlueprint {
}
