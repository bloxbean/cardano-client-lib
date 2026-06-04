package com.bloxbean.cardano.client.plutus.aiken.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the Aiken standard library version used by a {@code @Blueprint}-annotated interface.
 *
 * <p>Currently a no-op marker: the annotation processor supports a single stdlib
 * family ({@link AikenStdlibVersion#V3}) so declaring the version is not required.
 * The annotation is retained for source compatibility with existing user code and
 * as an extension point for future stdlib versions.</p>
 *
 * <p>Usage (optional):</p>
 * <pre>{@code
 * @Blueprint(fileInResources = "blueprint.json", packageName = "my.generated")
 * @AikenStdlib(AikenStdlibVersion.V3)
 * public interface MyBlueprint {}
 * }</pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AikenStdlib {
    AikenStdlibVersion value() default AikenStdlibVersion.V3;
}
