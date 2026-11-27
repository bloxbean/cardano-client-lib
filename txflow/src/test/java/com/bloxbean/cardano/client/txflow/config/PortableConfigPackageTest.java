package com.bloxbean.cardano.client.txflow.config;

import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableConfigPackageTest {
    @Test
    void canonicalConfigTypesAreOwnedByConfigPackage() {
        ConfirmationConfig confirmation = ConfirmationConfig.builder()
                .minConfirmations(4)
                .timeout(Duration.ofMinutes(1))
                .build();
        FlowExecutionSettings settings = FlowExecutionSettings.builder()
                .chainingMode(ChainingMode.PIPELINED)
                .confirmationConfig(confirmation)
                .rollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY)
                .build();
        TxFlow flow = TxFlow.builder("config-package")
                .withExecutionSettings(settings)
                .addStep(FlowStep.builder("step").withTxContext(builder -> null).build())
                .build();

        assertSame(settings, flow.getExecutionSettings());
        assertEquals(FlowExecutionSettings.class, flow.getExecutionSettings().getClass());
        assertTrue(Modifier.isFinal(FlowExecutionSettings.class.getModifiers()));
        assertTrue(Modifier.isFinal(ConfirmationConfig.class.getModifiers()));
        assertEquals("com.bloxbean.cardano.client.txflow.config",
                RollbackStrategy.class.getPackageName());
        assertEquals(4, flow.getExecutionSettings().getConfirmationConfig().getMinConfirmations());
    }

    @Test
    void confirmationConfigToBuilderCopiesAllFields() {
        ConfirmationConfig original = ConfirmationConfig.builder()
                .minConfirmations(5)
                .checkInterval(Duration.ofSeconds(2))
                .timeout(Duration.ofMinutes(7))
                .maxRollbackRetries(4)
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(12)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(4))
                .requiredAuthoritativeAbsences(3)
                .build();

        ConfirmationConfig copy = original.toBuilder().build();
        assertEquals(original.getMinConfirmations(), copy.getMinConfirmations());
        assertEquals(original.getCheckInterval(), copy.getCheckInterval());
        assertEquals(original.getTimeout(), copy.getTimeout());
        assertEquals(original.getMaxRollbackRetries(), copy.getMaxRollbackRetries());
        assertEquals(original.isWaitForBackendAfterRollback(), copy.isWaitForBackendAfterRollback());
        assertEquals(original.getPostRollbackWaitAttempts(), copy.getPostRollbackWaitAttempts());
        assertEquals(original.getPostRollbackUtxoSyncDelay(), copy.getPostRollbackUtxoSyncDelay());
        assertEquals(original.getRequiredAuthoritativeAbsences(), copy.getRequiredAuthoritativeAbsences());

        ConfirmationConfig overridden = original.toBuilder()
                .minConfirmations(1)
                .waitForBackendAfterRollback(false)
                .build();
        assertEquals(1, overridden.getMinConfirmations());
        assertFalse(overridden.isWaitForBackendAfterRollback());
        assertEquals(Duration.ofMinutes(7), overridden.getTimeout());
        assertEquals(5, original.getMinConfirmations());
        assertTrue(original.isWaitForBackendAfterRollback());
    }
}
