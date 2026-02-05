package com.bloxbean.cardano.client.plutus.annotation.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

@Blueprint(fileInResources = "blueprint/multiple_validators_aiken_v1_0_29_alpha_16fb02e.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.multiple")
@ExtendWith({LockUnlockValidatorExtender.class})
public interface MultipleValidatorsBlueprint {
}
