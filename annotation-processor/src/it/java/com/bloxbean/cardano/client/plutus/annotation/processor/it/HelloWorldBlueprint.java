package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

@Blueprint(fileInResources = "blueprint/Hello_world-Blueprint_aiken_v1_0_21_alpha_4b04517.json", packageName = "com.test.hello")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface HelloWorldBlueprint {

}
