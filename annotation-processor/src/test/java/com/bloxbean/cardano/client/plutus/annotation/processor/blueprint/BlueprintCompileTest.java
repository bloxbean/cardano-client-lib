package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Co-compiles multiple kept V3 blueprints in a single javac invocation to verify that
 * generated classes from different blueprints don't collide and the processor handles
 * a multi-blueprint compile cleanly.
 */
class BlueprintCompileTest {

    @Test
    void multipleBlueprintsCoCompileCleanly() {
        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(
                        JavaFileObjects.forResource("blueprint/SundaeSwapV3.java"),
                        JavaFileObjects.forResource("blueprint/UVerify.java"),
                        JavaFileObjects.forResource("blueprint/GiftCard.java"),
                        JavaFileObjects.forResource("blueprint/JpgStoreSniper.java"),
                        JavaFileObjects.forResource("blueprint/CircularNestedList.java")
                );

        assertThat(compilation).succeeded();
    }
}
