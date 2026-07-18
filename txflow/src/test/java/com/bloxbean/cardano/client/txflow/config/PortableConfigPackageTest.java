package com.bloxbean.cardano.client.txflow.config;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PortableConfigPackageTest {
    @Test
    void canonicalConfigTypesAreOwnedByConfigPackage() {
        ConfirmationConfig confirmation = ConfirmationConfig.builder()
                .minConfirmations(4).timeout(Duration.ofMinutes(1)).build();
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
        assertEquals("com.bloxbean.cardano.client.txflow.config",
                RollbackStrategy.class.getPackageName());
        assertEquals(4, flow.getExecutionSettings().getConfirmationConfig().getMinConfirmations());

        FlowExecutor.create(mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class),
                        mock(TransactionProcessor.class), mock(ChainDataSupplier.class))
                .withConfirmationConfig(confirmation)
                .withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY);
    }

    @SuppressWarnings("deprecation")
    @Test
    void deprecatedClassFacadesForwardToCanonicalTypes() {
        com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig legacyConfirmation =
                com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig.builder()
                        .minConfirmations(2)
                        .build();
        com.bloxbean.cardano.client.txflow.FlowExecutionSettings legacySettings =
                com.bloxbean.cardano.client.txflow.FlowExecutionSettings.builder()
                        .confirmationConfig(legacyConfirmation)
                        .build();

        assertTrue(legacyConfirmation instanceof ConfirmationConfig);
        assertTrue(legacySettings instanceof FlowExecutionSettings);
        assertEquals(2, legacySettings.getConfirmationConfig().getMinConfirmations());
    }
}
