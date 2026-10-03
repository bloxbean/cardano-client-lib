package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline guard for the always-true test script's CBOR wrapping.
 *
 * <p>The constants are deliberately repeated from {@code AlwaysTrueScripts} (which lives in the
 * integration-test source set and therefore cannot be seen from here). That duplication is the
 * point: this test independently pins the compiled code to the hash its blueprint declares, so a
 * wrapping mistake fails here without needing a network or a funded account.</p>
 *
 * <p>Aiken emits {@code compiledCode} already wrapped in one CBOR byte string;
 * {@link PlutusBlueprintUtil} wraps it once more for the script's {@code cborHex}, and the script
 * hash covers the single-wrapped form — {@code blake2b_224(0x03 ‖ compiledCode)}.</p>
 */
class AlwaysTrueScriptHashTest {

    private static final String COMPILED_CODE =
            "585301010029800aba2aba1aab9eaab9dab9a4888896600264653001300600198031803800cc018"
            + "0092225980099b8748010c01cdd500144c928980498041baa0028b200c180300098019baa0068a"
            + "4d13656400401";

    private static final String BLUEPRINT_HASH =
            "4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10";

    @Test
    void compiledCodeHashesToTheHashTheBlueprintDeclares() throws Exception {
        PlutusScript script =
                PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(COMPILED_CODE, PlutusVersion.v3);

        assertThat(script.getPolicyId()).isEqualToIgnoringCase(BLUEPRINT_HASH);
        assertThat(script.getCborHex()).endsWith(COMPILED_CODE);   // wrapped exactly once more
    }

    @Test
    void rewardAddressIsDerivedFromTheScriptCredential() throws Exception {
        PlutusScript script =
                PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(COMPILED_CODE, PlutusVersion.v3);

        String fromScript = AddressProvider.getRewardAddress(script, Networks.preview()).toBech32();
        String fromCredential = AddressProvider
                .getRewardAddress(Credential.fromScript(BLUEPRINT_HASH), Networks.preview()).toBech32();

        assertThat(fromScript).isEqualTo(fromCredential).startsWith("stake_test");
        System.out.println("always-true reward address: " + fromScript);
    }
}
