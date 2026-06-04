package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Drives {@link RecursiveSumDevnetTest}. The datum is a recursive ADT
 * {@code IntList = Nil | Cons { head: Int, tail: IntList }} — exercises
 * self-referencing type codegen (the generated {@code Cons} class holds
 * a field of its own enclosing type) and CBOR depth handling on chain.
 */
@Blueprint(fileInResources = "blueprint/recursive_sum/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface RecursiveSumDevnetBlueprint {
}
