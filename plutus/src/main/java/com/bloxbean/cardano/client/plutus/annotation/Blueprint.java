package com.bloxbean.cardano.client.plutus.annotation;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Blueprint {

    /**
     * Absolute file path
     * @return
     */
    String file() default "";

    /**
     * File in ressources
     * @return
     */
    String fileInRessources() default "";

    /**
     * Name of package the generated classes should be in
     * @return
     */
    String packageName() default "";
}

