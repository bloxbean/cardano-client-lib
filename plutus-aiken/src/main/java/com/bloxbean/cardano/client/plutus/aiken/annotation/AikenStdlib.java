package com.bloxbean.cardano.client.plutus.aiken.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the Aiken standard library version used by a {@code @Blueprint}-annotated interface.
 *
 * <p>The annotation processor reads this to filter the type registry so that only
 * schema signatures matching the declared version are resolved as shared types.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @Blueprint(fileInResources = "blueprint.json", packageName = "my.generated")
 * @AikenStdlib(AikenStdlibVersion.V2)
 * public interface MyBlueprint {}
 * }</pre>
 *
 * <p>When omitted, defaults to {@link AikenStdlibVersion#V3} (the latest).</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AikenStdlib {
    AikenStdlibVersion value() default AikenStdlibVersion.V3;
}
