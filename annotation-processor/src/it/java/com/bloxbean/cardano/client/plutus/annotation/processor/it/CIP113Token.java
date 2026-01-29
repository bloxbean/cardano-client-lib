package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

@Blueprint(fileInResources = "blueprint/cip113Token.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113")
public interface CIP113Token {
}
