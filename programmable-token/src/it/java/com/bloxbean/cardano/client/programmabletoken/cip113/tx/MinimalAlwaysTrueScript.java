package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;

/**
 * A second always-true substandard, so the suite can register a second programmable policy.
 *
 * <p>A CIP-113 policy id is derived from its minting logic's hash, so a second policy needs a
 * second script. This one is the smallest Plutus V3 program that succeeds:
 * {@code (program 1.1.0 (lam ctx (con unit ())))}. Its flat encoding is {@code 01 01 00}
 * (version), {@code 0010} (lambda), {@code 0100} (constant), {@code 1 0011 0} (type list: unit)
 * and the {@code 01} filler, i.e. {@code 0101002499}. The compiled code below wraps that in one
 * CBOR byte string, like an Aiken blueprint's {@code compiledCode}.</p>
 *
 * <p><b>This authorises everything.</b> It exists only to stand up a throwaway test token.</p>
 */
final class MinimalAlwaysTrueScript {

    static final String COMPILED_CODE = "450101002499";

    static final PlutusScript SCRIPT =
            PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(COMPILED_CODE, PlutusVersion.v3);

    private MinimalAlwaysTrueScript() { }

    static String scriptHash() {
        try {
            String hash = SCRIPT.getPolicyId();
            if (hash.equalsIgnoreCase(AlwaysTrueScripts.EXPECTED_SCRIPT_HASH))
                throw new IllegalStateException("The minimal script must differ from the Aiken always-true one");
            return hash;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash the minimal always-true script", e);
        }
    }

    static Credential credential() {
        return Credential.fromScript(scriptHash());
    }

    /** Must be registered on chain before any withdraw-zero against it, like every substandard. */
    static Address rewardAddress(Network network) {
        return AddressProvider.getRewardAddress(credential(), network);
    }
}
