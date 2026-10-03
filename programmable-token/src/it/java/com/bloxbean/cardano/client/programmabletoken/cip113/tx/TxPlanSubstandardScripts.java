package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Loads the checked-in UPLC generated from the JuLC integration-test substandard. */
final class TxPlanSubstandardScripts {
    private static final String BLUEPRINT = "/blueprint/txplan-substandard/plutus.json";
    private static final String EXPECTED_HASH =
            "09626cef617f240106cbdd95eb391e51850297784e41bb13a2581859";
    static final PlutusScript SCRIPT = load();

    private TxPlanSubstandardScripts() { }

    static Credential credential() {
        return Credential.fromScript(scriptHash());
    }

    static String scriptHash() {
        try {
            String actual = SCRIPT.getPolicyId();
            if (!EXPECTED_HASH.equalsIgnoreCase(actual))
                throw new IllegalStateException("JuLC substandard hash mismatch: " + actual);
            return actual;
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash the JuLC substandard", e);
        }
    }

    static Address rewardAddress(Network network) {
        return AddressProvider.getRewardAddress(credential(), network);
    }

    private static PlutusScript load() {
        try (var input = TxPlanSubstandardScripts.class.getResourceAsStream(BLUEPRINT)) {
            if (input == null) throw new IllegalStateException("Missing " + BLUEPRINT);
            JsonNode blueprint = new ObjectMapper().readTree(input);
            String compiledCode = blueprint.path("validators").path(0)
                    .path("compiledCode").asText();
            if (compiledCode.isBlank())
                throw new IllegalStateException("No compiledCode in " + BLUEPRINT);
            // JuLC emits the CCL-ready double-CBOR script, unlike an Aiken blueprint's
            // single-wrapped compiledCode. Wrapping it again changes the script hash.
            return PlutusV3Script.builder().cborHex(compiledCode).build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the JuLC substandard blueprint", e);
        }
    }
}
