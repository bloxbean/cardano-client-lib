package com.bloxbean.cardano.client.txflow.exec;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * Configuration for transaction confirmation tracking.
 * <p>
 * Defines thresholds and timing parameters for monitoring transaction
 * confirmation status, including:
 * <ul>
 *     <li>Minimum confirmations for practical safety</li>
 *     <li>Polling intervals and timeouts</li>
 * </ul>
 *
 * <h2>Default Values (Public Networks)</h2>
 * <table border="1">
 *   <caption>Default configuration values for public Cardano networks</caption>
 *   <tr><th>Parameter</th><th>Value</th><th>Reasoning</th></tr>
 *   <tr><td>minConfirmations</td><td>10 blocks</td><td>~200 seconds, practical safety threshold</td></tr>
 *   <tr><td>checkInterval</td><td>5 seconds</td><td>Balance between responsiveness and API load</td></tr>
 *   <tr><td>timeout</td><td>30 minutes</td><td>Maximum time to wait for confirmation</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use defaults
 * ConfirmationConfig config = ConfirmationConfig.defaults();
 *
 * // Custom configuration
 * ConfirmationConfig config = ConfirmationConfig.builder()
 *     .minConfirmations(20)
 *     .checkInterval(Duration.ofSeconds(10))
 *     .build();
 *
 * // Devnet preset (faster block times)
 * ConfirmationConfig config = ConfirmationConfig.devnet();
 * }</pre>
 */
@Getter
@Builder
public class ConfirmationConfig {

    /**
     * Minimum number of confirmations to consider a transaction practically safe.
     * <p>
     * Once this depth is reached, the transaction status transitions from IN_BLOCK to CONFIRMED.
     * Default: 10 blocks (~200 seconds on mainnet with 20-second block time).
     */
    @Builder.Default
    private final int minConfirmations = 10;

    /**
     * Interval between confirmation status checks.
     * <p>
     * Default: 5 seconds.
     */
    @Builder.Default
    private final Duration checkInterval = Duration.ofSeconds(5);

    /**
     * Maximum time to wait for a transaction to reach the target confirmation status.
     * <p>
     * Default: 30 minutes.
     */
    @Builder.Default
    private final Duration timeout = Duration.ofMinutes(30);

    /**
     * Maximum number of rebuild attempts when using REBUILD_FROM_FAILED or REBUILD_ENTIRE_FLOW strategies.
     * <p>
     * For REBUILD_FROM_FAILED: Max times a single step can be rebuilt after rollback.
     * For REBUILD_ENTIRE_FLOW: Max times the entire flow can be restarted after rollback.
     * <p>
     * After this limit is reached, the flow will fail.
     * <p>
     * Default: 3 attempts.
     */
    @Builder.Default
    private final int maxRollbackRetries = 3;

    /**
     * Whether to wait for the backend to be ready after a rollback is detected.
     * <p>
     * In production environments, rollbacks don't cause node restarts, so waiting
     * is unnecessary overhead. In test environments (e.g., Yaci DevKit), rollback
     * simulation may cause node restart, requiring a wait for backend availability.
     * <p>
     * Default: false (production default - no wait).
     */
    @Builder.Default
    private final boolean waitForBackendAfterRollback = false;

    /**
     * Maximum number of attempts to check if backend is ready after rollback.
     * <p>
     * Only used when {@link #waitForBackendAfterRollback} is true.
     * Each attempt waits for {@link #checkInterval} before retrying.
     * <p>
     * Default: 5 attempts (quick health check).
     */
    @Builder.Default
    private final int postRollbackWaitAttempts = 5;

    /**
     * Additional delay to wait for UTXO indexer to sync after backend becomes ready.
     * <p>
     * In test environments, the block service may respond before UTXO indexes are
     * fully updated. This delay ensures UTXOs are available for transaction building.
     * <p>
     * Only used when {@link #waitForBackendAfterRollback} is true.
     * <p>
     * Default: Duration.ZERO (no additional delay - production default).
     */
    @Builder.Default
    private final Duration postRollbackUtxoSyncDelay = Duration.ZERO;

    /**
     * Create a configuration with default values suitable for public Cardano networks.
     *
     * @return default configuration
     */
    public static ConfirmationConfig defaults() {
        return builder().build();
    }

    /**
     * Create a configuration preset for fast devnets (e.g., Yaci DevKit).
     * <p>
     * Devnets typically have 1-second block times, so confirmation thresholds
     * are adjusted accordingly. This preset also enables post-rollback waiting
     * since devnet rollback simulation may cause node restart.
     *
     * @return devnet configuration preset
     */
    public static ConfirmationConfig devnet() {
        return builder()
                .minConfirmations(3)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(5))
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Create a configuration preset for testnet usage.
     * <p>
     * Uses slightly lower thresholds than mainnet defaults since testnet
     * transactions have no real value at risk.
     *
     * @return testnet configuration preset
     */
    public static ConfirmationConfig testnet() {
        return builder()
                .minConfirmations(6)
                .checkInterval(Duration.ofSeconds(3))
                .timeout(Duration.ofMinutes(10))
                .build();
    }

    /**
     * Create a quick configuration for development/testing.
     * <p>
     * Waits for only 1 confirmation with aggressive polling.
     * Enables post-rollback waiting for test environments.
     * NOT suitable for production use.
     *
     * @return quick development configuration
     */
    public static ConfirmationConfig quick() {
        return builder()
                .minConfirmations(1)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(2))
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();
    }
}
