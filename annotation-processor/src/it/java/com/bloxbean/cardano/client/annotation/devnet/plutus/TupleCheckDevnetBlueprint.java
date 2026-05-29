package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Drives {@link TupleCheckDevnetTest}. The datum carries a tuple field
 * {@code entry: (Int, ByteArray)} which Aiken emits with the
 * {@code Tuple<<Int,ByteArray>>} doubled-angle-bracket definition key
 * (and {@code dataType: "list"} with positional {@code items}) standardised
 * in the CIP-57 generics amendment.
 */
@Blueprint(fileInResources = "blueprint/tuple_check/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface TupleCheckDevnetBlueprint {
}
