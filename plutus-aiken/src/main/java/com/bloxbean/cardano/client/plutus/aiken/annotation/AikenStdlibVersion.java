package com.bloxbean.cardano.client.plutus.aiken.annotation;

/**
 * Identifies the Aiken standard library version used to compile a blueprint.
 *
 * <p>The version determines which schema signatures the type registry uses
 * when resolving shared types (e.g., Credential, Address, OutputReference).</p>
 */
public enum AikenStdlibVersion {
    /** stdlib &gt;= 1.9.0, &lt; 2.0.0 */
    V1,
    /** stdlib &gt;= 2.0.0, &lt; 3.0.0 */
    V2,
    /** stdlib &gt;= 3.0.0 (latest) */
    V3;

    public static final AikenStdlibVersion LATEST = V3;
}
