package com.bloxbean.cardano.client.ledger.slice;

import java.util.Optional;

/**
 * Provides access to Constitutional Committee state during validation.
 * <p>
 * Used by committee certificate and governance validation rules.
 * Yaci provides a concrete implementation backed by its ledger state storage.
 */
public interface CommitteeSlice {

    /**
     * Check if a cold credential is an authorized committee member.
     *
     * @param coldCredentialHash the cold credential hash (hex-encoded)
     * @return true if authorized
     */
    boolean isMember(String coldCredentialHash);

    /**
     * Get the hot credential currently authorized for a committee member.
     *
     * @param coldCredentialHash the cold credential hash (hex-encoded)
     * @return the hot credential hash, or empty if not authorized
     */
    Optional<String> getHotCredential(String coldCredentialHash);

    /**
     * Check if a committee member has resigned.
     *
     * @param coldCredentialHash the cold credential hash (hex-encoded)
     * @return true if resigned
     */
    boolean hasResigned(String coldCredentialHash);
}
