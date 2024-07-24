package com.demo.helloblueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

@Blueprint(fileInResources = "blueprint/HelloWorldNoNS.json", packageName = "com.test.hello")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface HelloWorldNoNSBlueprint {
}
