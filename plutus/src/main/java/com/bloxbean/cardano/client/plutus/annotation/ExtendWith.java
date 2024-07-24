package com.bloxbean.cardano.client.plutus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ExtendWith annotation is used to specify the super class for the generated validator class from Blueprint annotation
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExtendWith {

    Class value();
}

