package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.MintValidatorExtender;

/**
 * Drives {@link MintPolicyDevnetTest}. The validator's purpose is
 * {@code mint} (no datum, redeemer-only) — exercises the mint pathway and
 * the {@link MintValidatorExtender} surface that the other devnet tests
 * (all spend-purpose) do not.
 */
@Blueprint(fileInResources = "blueprint/mint_policy/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(MintValidatorExtender.class)
public interface MintPolicyDevnetBlueprint {
}
