package com.bloxbean.cardano.client.plutus.annotation.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

@Blueprint(fileInResources = "blueprint/multiple_validators.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.multiple")
@ExtendWith({LockUnlockValidatorExtender.class})
public interface MultipleValidatorsBlueprint {
}
