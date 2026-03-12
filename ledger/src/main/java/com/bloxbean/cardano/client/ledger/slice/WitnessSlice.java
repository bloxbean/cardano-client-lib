package com.bloxbean.cardano.client.ledger.slice;

import java.util.Set;

/**
 * Accumulates required witnesses during validation.
 * <p>
 * As rules process inputs, certificates, and withdrawals, they record which
 * VKey hashes and script hashes must be witnessed. The witness validation rule
 * then checks that all required witnesses are present in the transaction.
 */
public interface WitnessSlice {

    /**
     * Record that a VKey witness is required.
     *
     * @param vkeyHash the verification key hash (hex-encoded, 28 bytes)
     */
    void requireVKey(String vkeyHash);

    /**
     * Record that a script witness is required.
     *
     * @param scriptHash the script hash (hex-encoded, 28 bytes)
     */
    void requireScript(String scriptHash);

    /**
     * Get all required VKey hashes accumulated so far.
     *
     * @return set of required VKey hashes
     */
    Set<String> getRequiredVKeys();

    /**
     * Get all required script hashes accumulated so far.
     *
     * @return set of required script hashes
     */
    Set<String> getRequiredScripts();
}
