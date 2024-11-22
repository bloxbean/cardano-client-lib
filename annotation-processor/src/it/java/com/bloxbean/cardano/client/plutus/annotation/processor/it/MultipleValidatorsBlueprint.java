package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

@Blueprint(fileInResources = "blueprint/multiple_validators.json", packageName = "com.test.multiple")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface MultipleValidatorsBlueprint {

}
