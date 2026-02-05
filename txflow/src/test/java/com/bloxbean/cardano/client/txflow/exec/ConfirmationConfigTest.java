package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationConfigTest {

    @Test
    void testDefaultConfiguration() {
        ConfirmationConfig config = ConfirmationConfig.defaults();

        assertEquals(10, config.getMinConfirmations());
        assertEquals(2160, config.getSafeConfirmations());
        assertEquals(Duration.ofSeconds(5), config.getCheckInterval());
        assertEquals(Duration.ofMinutes(30), config.getTimeout());
        assertFalse(config.isRequireFinalization());
        assertEquals(3, config.getMaxRollbackRetries());
        // Post-rollback wait defaults (production: disabled)
        assertFalse(config.isWaitForBackendAfterRollback());
        assertEquals(5, config.getPostRollbackWaitAttempts());
        assertEquals(Duration.ZERO, config.getPostRollbackUtxoSyncDelay());
    }

    @Test
    void testDevnetPreset() {
        ConfirmationConfig config = ConfirmationConfig.devnet();

        assertEquals(3, config.getMinConfirmations());
        assertEquals(100, config.getSafeConfirmations());
        assertEquals(Duration.ofSeconds(1), config.getCheckInterval());
        assertEquals(Duration.ofMinutes(5), config.getTimeout());
        assertFalse(config.isRequireFinalization());
        // Post-rollback wait enabled for devnet
        assertTrue(config.isWaitForBackendAfterRollback());
        assertEquals(30, config.getPostRollbackWaitAttempts());
        assertEquals(Duration.ofSeconds(3), config.getPostRollbackUtxoSyncDelay());
    }

    @Test
    void testTestnetPreset() {
        ConfirmationConfig config = ConfirmationConfig.testnet();

        assertEquals(6, config.getMinConfirmations());
        assertEquals(100, config.getSafeConfirmations());
        assertEquals(Duration.ofSeconds(3), config.getCheckInterval());
        assertEquals(Duration.ofMinutes(10), config.getTimeout());
    }

    @Test
    void testQuickPreset() {
        ConfirmationConfig config = ConfirmationConfig.quick();

        assertEquals(1, config.getMinConfirmations());
        assertEquals(10, config.getSafeConfirmations());
        assertEquals(Duration.ofSeconds(1), config.getCheckInterval());
        assertEquals(Duration.ofMinutes(2), config.getTimeout());
        // Post-rollback wait enabled for quick/test
        assertTrue(config.isWaitForBackendAfterRollback());
        assertEquals(30, config.getPostRollbackWaitAttempts());
        assertEquals(Duration.ofSeconds(3), config.getPostRollbackUtxoSyncDelay());
    }

    @Test
    void testCustomConfiguration() {
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(20)
                .safeConfirmations(500)
                .checkInterval(Duration.ofSeconds(10))
                .timeout(Duration.ofHours(1))
                .requireFinalization(true)
                .build();

        assertEquals(20, config.getMinConfirmations());
        assertEquals(500, config.getSafeConfirmations());
        assertEquals(Duration.ofSeconds(10), config.getCheckInterval());
        assertEquals(Duration.ofHours(1), config.getTimeout());
        assertTrue(config.isRequireFinalization());
    }

    @Test
    void testBuilderPartialOverride() {
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(15)
                .build();

        // Overridden value
        assertEquals(15, config.getMinConfirmations());

        // Default values should remain
        assertEquals(2160, config.getSafeConfirmations());
        assertEquals(Duration.ofSeconds(5), config.getCheckInterval());
    }

    @Test
    void testCustomMaxRollbackRetries() {
        ConfirmationConfig config = ConfirmationConfig.builder()
                .maxRollbackRetries(5)
                .build();

        assertEquals(5, config.getMaxRollbackRetries());

        // Other defaults should remain
        assertEquals(10, config.getMinConfirmations());
        assertEquals(Duration.ofSeconds(5), config.getCheckInterval());
    }

    @Test
    void testMaxRollbackRetriesInCustomConfiguration() {
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(20)
                .maxRollbackRetries(10)
                .requireFinalization(true)
                .build();

        assertEquals(20, config.getMinConfirmations());
        assertEquals(10, config.getMaxRollbackRetries());
        assertTrue(config.isRequireFinalization());
    }

    @Test
    void testCustomPostRollbackWaitConfiguration() {
        ConfirmationConfig config = ConfirmationConfig.builder()
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(15)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(5))
                .build();

        assertTrue(config.isWaitForBackendAfterRollback());
        assertEquals(15, config.getPostRollbackWaitAttempts());
        assertEquals(Duration.ofSeconds(5), config.getPostRollbackUtxoSyncDelay());
        // Other defaults should remain
        assertEquals(10, config.getMinConfirmations());
        assertEquals(Duration.ofSeconds(5), config.getCheckInterval());
    }

    @Test
    void testProductionConfigDisablesPostRollbackWait() {
        // Production configs should not wait for backend after rollback
        ConfirmationConfig defaultConfig = ConfirmationConfig.defaults();
        ConfirmationConfig testnetConfig = ConfirmationConfig.testnet();

        assertFalse(defaultConfig.isWaitForBackendAfterRollback(),
                "Production (defaults) should not wait for backend after rollback");
        assertFalse(testnetConfig.isWaitForBackendAfterRollback(),
                "Testnet should not wait for backend after rollback (real network)");
    }

    @Test
    void testDevAndQuickConfigsEnablePostRollbackWait() {
        // Dev/test configs should enable wait for backend after rollback
        ConfirmationConfig devnetConfig = ConfirmationConfig.devnet();
        ConfirmationConfig quickConfig = ConfirmationConfig.quick();

        assertTrue(devnetConfig.isWaitForBackendAfterRollback(),
                "Devnet should wait for backend after rollback (Yaci DevKit)");
        assertTrue(quickConfig.isWaitForBackendAfterRollback(),
                "Quick/dev should wait for backend after rollback");
    }
}
