package com.demo.helloblueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

@Blueprint(fileInResources = "blueprint/HelloWorldNoNS.json", packageName = "com.test.hello")
public interface HelloWorldNoNSBlueprint {
}
