package com.test.pseudoalias;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

@Blueprint(fileInResources = "blueprint/pseudo-alias-test.json",
           packageName = "com.test.pseudoalias")
public interface PseudoAliasTest {
}
