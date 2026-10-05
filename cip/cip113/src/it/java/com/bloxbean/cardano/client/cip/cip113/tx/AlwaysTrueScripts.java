package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;

/**
 * The always-true withdraw-zero script used to stand up a throwaway CIP-113 test token.
 *
 * <p>Compiled from the Aiken project {@code cip113/alwaystrue} (Aiken {@code v1.1.23+8949565},
 * Plutus V3) — the same compiler the CIP-113 reference implementation uses. Its blueprint is
 * checked in at {@code src/it/resources/blueprint/alwaystrue/plutus.json}; the constants below
 * are copied from it verbatim.</p>
 *
 * <p>The validator exposes a {@code withdraw} handler that always succeeds, which is exactly the
 * shape CIP-113's three substandard roles need. One script serves all three — issuance, transfer
 * and third-party — which the standard permits: a registry node's three logic fields are
 * independent credentials and nothing requires them to differ, and the reference implementation's
 * own {@code dummy} substandard reuses one script across roles. It also means the withdrawal map
 * carries the credential once while satisfying every "logic script must be invoked" check.</p>
 *
 * <p><b>This authorises everything.</b> It exists so the transfer path can be exercised without a
 * real compliance substandard in the way. Never point a real token at it.</p>
 */
public final class AlwaysTrueScripts {

    /**
     * {@code alwaystrue.placeholder.withdraw}, verbatim from the blueprint.
     *
     * <p>Aiken emits the flat-encoded script already wrapped in one CBOR byte string, and CCL's
     * {@link PlutusBlueprintUtil} wraps it once more to produce the {@code cborHex} a
     * {@code PlutusScript} carries. The script hash covers the single-wrapped form — that is,
     * {@code blake2b_224(0x03 ‖ compiledCode)} — which is why {@link #EXPECTED_SCRIPT_HASH}
     * below is asserted at runtime rather than trusted.</p>
     */
    public static final String COMPILED_CODE =
            "585301010029800aba2aba1aab9eaab9dab9a4888896600264653001300600198031803800cc018"
            + "0092225980099b8748010c01cdd500144c928980498041baa0028b200c180300098019baa0068a"
            + "4d13656400401";

    /** The hash the blueprint declares. Both the {@code withdraw} and {@code else} purposes share it. */
    public static final String EXPECTED_SCRIPT_HASH =
            "4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10";

    public static final PlutusScript ALWAYS_TRUE =
            PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(COMPILED_CODE, PlutusVersion.v3);

    private AlwaysTrueScripts() {}

    public static Credential credential() {
        return Credential.fromScript(scriptHash());
    }

    public static String scriptHash() {
        try {
            String hash = ALWAYS_TRUE.getPolicyId();
            if (!EXPECTED_SCRIPT_HASH.equalsIgnoreCase(hash)) {
                throw new IllegalStateException(
                        "Always-true script hash is " + hash + " but the blueprint declares "
                        + EXPECTED_SCRIPT_HASH + ". The compiled code or its CBOR wrapping is wrong,"
                        + " and every address derived from it would be wrong too.");
            }
            return hash;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash the always-true script", e);
        }
    }

    /**
     * Reward address whose withdraw-zero invokes the script.
     *
     * <p>The stake credential must be <b>registered on chain</b> before any withdrawal against it
     * is valid — even a zero one. {@code Cip113PreviewIT} step 5 registers it if it is not.</p>
     */
    public static Address rewardAddress(Network network) {
        return AddressProvider.getRewardAddress(credential(), network);
    }
}
