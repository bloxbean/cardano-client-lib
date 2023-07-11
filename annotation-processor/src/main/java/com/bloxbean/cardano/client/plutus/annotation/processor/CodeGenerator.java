package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.squareup.javapoet.TypeSpec;

public interface CodeGenerator {
    TypeSpec generate(ClassDefinition classDefinition);
}
