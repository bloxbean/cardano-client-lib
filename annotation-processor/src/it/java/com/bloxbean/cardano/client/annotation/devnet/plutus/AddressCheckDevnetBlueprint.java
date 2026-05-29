package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Drives {@link AddressCheckDevnetTest}. The datum is {@code Vault { admin: Address }}
 * where {@code Address = cardano/address/Address} — the most composite shared
 * stdlib v3 type (PaymentCredential sum type plus an Optional StakeCredential).
 */
@Blueprint(fileInResources = "blueprint/address_check/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface AddressCheckDevnetBlueprint {
}
