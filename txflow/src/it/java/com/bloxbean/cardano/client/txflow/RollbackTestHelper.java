package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.backend.api.BackendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class for interacting with Yaci DevKit's rollback API during integration tests.
 * <p>
 * This helper provides methods to:
 * <ul>
 *     <li>Take database snapshots for rollback testing</li>
 *     <li>Trigger rollbacks to the last snapshot</li>
 *     <li>Wait for the node to be ready after rollback</li>
 * </ul>
 * <p>
 * Yaci DevKit Admin API must be running on the configured port (default: 10000).
 */
public class RollbackTestHelper {

    private static final Logger log = LoggerFactory.getLogger(RollbackTestHelper.class);

    private static final String ROLLBACK_BASE_URL = "http://localhost:10000/local-cluster/api/devnet/rollback";
    private static final String ADMIN_BASE_URL = "http://localhost:10000/local-cluster/api/admin/devnet";
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    /**
     * Reset the devnet to its initial state.
     * <p>
     * This operation restarts the node from block 0 with fresh state.
     * Use this between tests to ensure a clean starting point.
     *
     * @return true if reset was successful, false otherwise
     */
    public static boolean resetDevnet() {
        return executePost(ADMIN_BASE_URL + "/reset", "Reset devnet");
    }

    /**
     * Take a database snapshot for later rollback.
     * <p>
     * The snapshot captures the current node database state. Any transactions
     * confirmed after this snapshot will be removed when rollback is triggered.
     *
     * @return true if snapshot was taken successfully, false otherwise
     */
    public static boolean takeDbSnapshot() {
        return executePost(ROLLBACK_BASE_URL + "/take-db-snapshot", "Take DB snapshot");
    }

    /**
     * Rollback the node database to the last snapshot.
     * <p>
     * This operation:
     * <ul>
     *     <li>Restores the database to the last snapshot state</li>
     *     <li>Restarts the node (takes a few seconds)</li>
     *     <li>Removes any transactions confirmed after the snapshot</li>
     * </ul>
     * <p>
     * After calling this method, use {@link #waitForNodeReady(BackendService, int)}
     * to ensure the node is ready before continuing tests.
     *
     * @return true if rollback was triggered successfully, false otherwise
     */
    public static boolean rollbackToSnapshot() {
        return executePost(ROLLBACK_BASE_URL + "/rollback-to-db-snapshot", "Rollback to DB snapshot");
    }

    /**
     * Wait for the node to be ready after a rollback.
     * <p>
     * Polls the backend service to verify the node is responsive.
     * This is necessary because rollback causes a node restart.
     *
     * @param backendService the backend service to check
     * @param maxAttempts maximum number of attempts (1 second between attempts)
     * @return true if node became ready within max attempts, false otherwise
     */
    public static boolean waitForNodeReady(BackendService backendService, int maxAttempts) {
        log.info("Waiting for node to be ready after rollback (max {} attempts)...", maxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var result = backendService.getBlockService().getLatestBlock();
                if (result.isSuccessful() && result.getValue() != null) {
                    log.info("Node is ready after {} attempts (block height: {})",
                            attempt, result.getValue().getHeight());
                    return true;
                }
            } catch (Exception e) {
                log.debug("Attempt {}/{}: Node not ready yet - {}", attempt, maxAttempts, e.getMessage());
            }

            // Wait 1 second before next attempt
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error("Node did not become ready after {} attempts", maxAttempts);
        return false;
    }

    /**
     * Wait for a specific number of blocks to be produced.
     * <p>
     * Useful for ensuring transactions have enough confirmations before triggering rollback.
     *
     * @param backendService the backend service to check
     * @param blocksToWait number of blocks to wait for
     * @param maxWaitSeconds maximum time to wait
     * @return true if the blocks were produced, false if timeout
     */
    public static boolean waitForBlocks(BackendService backendService, int blocksToWait, int maxWaitSeconds) {
        try {
            var initialResult = backendService.getBlockService().getLatestBlock();
            if (!initialResult.isSuccessful() || initialResult.getValue() == null) {
                log.error("Failed to get initial block height");
                return false;
            }

            long initialHeight = initialResult.getValue().getHeight();
            long targetHeight = initialHeight + blocksToWait;
            log.info("Waiting for {} blocks (from {} to {})...", blocksToWait, initialHeight, targetHeight);

            long startTime = System.currentTimeMillis();
            long maxWaitMs = maxWaitSeconds * 1000L;

            while (System.currentTimeMillis() - startTime < maxWaitMs) {
                var result = backendService.getBlockService().getLatestBlock();
                if (result.isSuccessful() && result.getValue() != null) {
                    long currentHeight = result.getValue().getHeight();
                    if (currentHeight >= targetHeight) {
                        log.info("Reached target block height: {}", currentHeight);
                        return true;
                    }
                    log.debug("Current height: {}, waiting for: {}", currentHeight, targetHeight);
                }

                Thread.sleep(500);
            }

            log.error("Timeout waiting for {} blocks", blocksToWait);
            return false;
        } catch (Exception e) {
            log.error("Error waiting for blocks", e);
            return false;
        }
    }

    /**
     * Execute a POST request to the admin API.
     *
     * @param urlString the URL to POST to
     * @param operation description for logging
     * @return true if successful (2xx response), false otherwise
     */
    private static boolean executePost(String urlString, String operation) {
        HttpURLConnection connection = null;
        try {
            log.info("Executing: {}", operation);

            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            // Send empty body
            connection.getOutputStream().write(new byte[0]);
            connection.getOutputStream().flush();

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.info("{} successful (HTTP {})", operation, responseCode);
                return true;
            } else {
                log.error("{} failed with HTTP {}", operation, responseCode);
                return false;
            }
        } catch (Exception e) {
            log.error("{} failed: {}", operation, e.getMessage(), e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
