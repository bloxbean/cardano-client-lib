package com.bloxbean.cardano.client.plutus.annotation;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Blueprint {

    String file() default "";
    String fileInRessources() default "";
    String packageName() default "";
    String prefix() default "";
}

