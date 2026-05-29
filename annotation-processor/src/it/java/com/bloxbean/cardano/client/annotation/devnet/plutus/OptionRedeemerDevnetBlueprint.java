package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Drives {@link OptionRedeemerDevnetTest}. The redeemer is {@code Option<ByteArray>}
 * — exercises the generic {@code Option<T>} codegen path at the validator
 * boundary, including the {@code None} CBOR encoding (constructor index 1
 * with empty fields). Other devnet tests use {@code Option<T>} only as a datum
 * that's required to be {@code Some}, leaving the {@code None} arm untested.
 */
@Blueprint(fileInResources = "blueprint/option_redeemer/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface OptionRedeemerDevnetBlueprint {
}
