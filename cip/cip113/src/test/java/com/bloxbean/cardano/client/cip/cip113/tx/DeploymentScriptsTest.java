package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.cip.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.cip.cip113.Cip113Exception;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentScriptsTest {

    private static DeploymentScripts scripts() {
        return new DeploymentScripts((ScriptSupplier) null,
                Cip113Deployment.builder().network(Networks.testnet()).build());
    }

    @Test
    void registersAScriptUnderItsOwnHash() throws Exception {
        // The always-true validator, the same compiled code AlwaysTrueScriptHashTest pins.
        PlutusScript script = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                "585301010029800aba2aba1aab9eaab9dab9a4888896600264653001300600198031803800cc018"
                        + "0092225980099b8748010c01cdd500144c928980498041baa0028b200c180300098019baa0068a"
                        + "4d13656400401",
                PlutusVersion.v3);

        DeploymentScripts registry = scripts().register(script);

        assertThat(registry.getScript(script.getPolicyId())).contains(script);
    }

    /**
     * {@code HexUtil.encodeHexString} answers null rather than throwing, so a script that cannot
     * be hashed used to reach {@code toLowerCase()} and die with a bare NPE naming neither the
     * script nor the step that produced it. SonarCloud javabugs:S2259.
     */
    @Test
    void aScriptThatCannotBeHashedIsRefusedByName() throws Exception {
        PlutusScript unhashable = mock(PlutusScript.class);
        when(unhashable.getScriptHash()).thenReturn(null);

        assertThatThrownBy(() -> scripts().register(unhashable))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("no hash");
    }

    @Test
    void aNullScriptIsRefusedByName() {
        assertThatThrownBy(() -> scripts().register(null))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("null script");
    }
}
