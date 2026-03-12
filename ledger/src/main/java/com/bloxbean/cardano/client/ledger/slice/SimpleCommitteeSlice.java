package com.bloxbean.cardano.client.ledger.slice;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simple in-memory implementation of {@link CommitteeSlice} backed by HashMaps.
 * <p>
 * Suitable for testing and single-transaction validation.
 */
public class SimpleCommitteeSlice implements CommitteeSlice {
    private final Map<String, String> hotCredentials; // coldHash → hotHash
    private final Set<String> resigned;

    public SimpleCommitteeSlice(Map<String, String> hotCredentials, Set<String> resigned) {
        this.hotCredentials = new HashMap<>(hotCredentials);
        this.resigned = Set.copyOf(resigned);
    }

    @Override
    public boolean isMember(String coldCredentialHash) {
        return hotCredentials.containsKey(coldCredentialHash);
    }

    @Override
    public Optional<String> getHotCredential(String coldCredentialHash) {
        return Optional.ofNullable(hotCredentials.get(coldCredentialHash));
    }

    @Override
    public boolean hasResigned(String coldCredentialHash) {
        return resigned.contains(coldCredentialHash);
    }
}
