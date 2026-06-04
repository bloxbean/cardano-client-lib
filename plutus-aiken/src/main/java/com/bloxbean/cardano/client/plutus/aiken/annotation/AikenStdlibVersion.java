package com.bloxbean.cardano.client.plutus.aiken.annotation;

/**
 * Identifies the Aiken standard library version used to compile a blueprint.
 *
 * <p>Currently only {@code V3} (stdlib v3.x — covers 3.0 and 3.1) is supported.
 * The enum is kept single-valued for source compatibility and to leave a
 * clean extension point when a future stdlib (v4+) introduces incompatible
 * schema changes.</p>
 */
public enum AikenStdlibVersion {
    /** Aiken stdlib v3.x (covers 3.0 and 3.1). */
    V3;

    public static final AikenStdlibVersion LATEST = V3;
}
