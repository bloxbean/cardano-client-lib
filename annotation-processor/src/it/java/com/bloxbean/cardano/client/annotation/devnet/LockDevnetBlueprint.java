package com.bloxbean.cardano.client.annotation.devnet;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

@Blueprint(fileInResources = "blueprint/lock/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface LockDevnetBlueprint {
}
