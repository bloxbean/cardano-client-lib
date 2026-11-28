package com.bloxbean.cardano.client.txflow.config;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.PortableFlowValidator;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PortableConfigPackageTest {
    private static final String TEST_ADDRESS =
            "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2"
                    + "k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp";

    @Test
    void canonicalConfigTypesAreOwnedByConfigPackage() {
        ConfirmationConfig confirmation = ConfirmationConfig.builder()
                .minConfirmations(4)
                .timeout(Duration.ofMinutes(1))
                .build();
        FlowExecutionSettings settings = FlowExecutionSettings.builder()
                .chainingMode(ChainingMode.PIPELINED)
                .confirmationConfig(confirmation)
                .retryPolicy(RetryPolicy.defaults())
                .build();
        TxFlow flow = TxFlow.builder("config-package")
                .withExecutionSettings(settings)
                .addStep(FlowStep.builder("step")
                        .withTxPlan(TxPlan.from(new Tx()
                                .from(TEST_ADDRESS)
                                .payToAddress(TEST_ADDRESS, Amount.ada(1))))
                        .build())
                .build();

        assertSame(settings, flow.getExecutionSettings());
        assertEquals(FlowExecutionSettings.class, flow.getExecutionSettings().getClass());
        assertTrue(Modifier.isFinal(FlowExecutionSettings.class.getModifiers()));
        assertTrue(Modifier.isFinal(ConfirmationConfig.class.getModifiers()));
        assertEquals("com.bloxbean.cardano.client.txflow.config",
                RollbackStrategy.class.getPackageName());
        assertEquals(4, flow.getExecutionSettings().getConfirmationConfig().getMinConfirmations());

        FlowExecutionSettings settingsCopy = settings.toBuilder().build();
        assertEquals(settings, settingsCopy);
        assertEquals(settings.hashCode(), settingsCopy.hashCode());
        assertTrue(settings.toString().contains("PIPELINED"));
        assertNotEquals(settings, settings.toBuilder().chainingMode(ChainingMode.BATCH).build());
    }

    @Test
    void confirmationConfigToBuilderEqualsHashCodeAndToString() {
        @SuppressWarnings("deprecation")
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
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertTrue(original.toString().contains("minConfirmations=5"));

        @SuppressWarnings("deprecation")
        ConfirmationConfig overridden = original.toBuilder()
                .minConfirmations(1)
                .waitForBackendAfterRollback(false)
                .build();
        assertNotEquals(original, overridden);
        assertEquals(1, overridden.getMinConfirmations());
        assertFalse(overridden.isWaitForBackendAfterRollback());
        assertEquals(Duration.ofMinutes(7), overridden.getTimeout());
        assertEquals(5, original.getMinConfirmations());
        assertTrue(original.isWaitForBackendAfterRollback());
    }

    @Test
    void devnetAndQuickPresetsArePortableAndCompileOnFlowEngine() {
        FlowEngine engine = FlowEngine.builder(
                        mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class),
                        mock(TransactionProcessor.class),
                        mock(ChainDataSupplier.class))
                .executor(Runnable::run)
                .build();

        for (ConfirmationConfig preset : new ConfirmationConfig[]{
                ConfirmationConfig.defaults(),
                ConfirmationConfig.devnet(),
                ConfirmationConfig.testnet(),
                ConfirmationConfig.quick()}) {
            TxFlow flow = TxFlow.builder("preset-check")
                    .withConfirmationConfig(preset)
                    .addStep(FlowStep.builder("pay")
                            .withTxPlan(TxPlan.from(new Tx()
                                    .from(TEST_ADDRESS)
                                    .payToAddress(TEST_ADDRESS, Amount.ada(1))))
                            .build())
                    .build();

            assertTrue(PortableFlowValidator.validate(flow).isEmpty(),
                    () -> "Expected portable preset " + preset + " to pass PortableFlowValidator");
            FlowCompilationResult preflight = engine.preflight(FlowExecutionRequest.builder(flow).build());
            assertFalse(preflight.hasErrors(),
                    () -> "Expected portable preset " + preset + " to compile cleanly: "
                            + preflight.getDiagnostics());
        }
    }
}
