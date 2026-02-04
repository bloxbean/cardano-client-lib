package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier;

/**
 * Classification categories for Blueprint schemas.
 */
/**
 * Classification categories describing how a blueprint schema should be treated during
 * code generation.
 */
public enum SchemaClassification {
    ALIAS,
    OPTION,
    PAIR_ALIAS,
    ENUM,
    INTERFACE,
    CLASS,
    UNKNOWN
}
