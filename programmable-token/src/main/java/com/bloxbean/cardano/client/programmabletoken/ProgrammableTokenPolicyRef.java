package com.bloxbean.cardano.client.programmabletoken;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A literal policy id or the named result of a registration in the same plan. */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProgrammableTokenPolicyRef {
    @JsonProperty("policy_id")
    String policyId;

    @JsonProperty("policy_ref")
    String name;

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

    public void validate() {
        if ((policyId == null) == (name == null))
            throw new IllegalStateException("Exactly one of policy_id or policy_ref is required");
        if (policyId != null) policyId(policyId);
        if (name != null && name.isBlank())
            throw new IllegalStateException("policy_ref is required");
    }
}
