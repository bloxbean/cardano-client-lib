package com.bloxbean.cardano.client.quicktx.annotation;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.MintValidatorExtender;

@Blueprint(fileInResources = "blueprint/helloworld/helloworld.json",
        packageName = "com.bloxbean.cardano.client.quicktx.annotation")
@ExtendWith({LockUnlockValidatorExtender.class, MintValidatorExtender.class})
public interface HelloWorldMintBlueprint {
}
