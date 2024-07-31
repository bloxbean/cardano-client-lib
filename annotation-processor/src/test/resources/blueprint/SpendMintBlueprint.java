package com.bloxbean.cardano.client.plutus.annotation.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.MintValidatorExtender;

@Blueprint(fileInResources = "blueprint/spend_mint.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.spendmint")
@ExtendWith({LockUnlockValidatorExtender.class})
public interface SpendMintBlueprint {
}
