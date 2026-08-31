package com.bloxbean.cardano.client.programmabletoken;

import lombok.Value;

/** A literal policy id or the named result of a registration in the same plan. */
@Value
public class ProgrammableTokenPolicyRef {
    String policyId;
    String name;

    private ProgrammableTokenPolicyRef(String policyId, String name) {
        this.policyId = policyId;
        this.name = name;
    }

    public static ProgrammableTokenPolicyRef policyId(String policyId) {
        if (policyId == null || policyId.isBlank()) throw new IllegalArgumentException("policyId is required");
        if (!policyId.matches("(?i)[0-9a-f]{56}"))
            throw new IllegalArgumentException("policyId must be a 28-byte hexadecimal policy id");
        return new ProgrammableTokenPolicyRef(policyId.toLowerCase(), null);
    }

    public static ProgrammableTokenPolicyRef named(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        return new ProgrammableTokenPolicyRef(null, name);
    }
}
