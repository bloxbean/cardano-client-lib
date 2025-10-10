package com.bloxbean.cardano.client.it;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Base integration test class providing common utilities for all integration tests.
 * Supports multiple backend types including YaciDevKit, Blockfrost, and Koios.
 *
 * To use YaciDevKit (default):
 * - Ensure YaciDevKit is running on localhost:8080
 * - No additional configuration needed
 *
 * To use Blockfrost:
 * - Set system property or environment variable: BF_PROJECT_ID
 * - Set backendType to "blockfrost"
 *
 * To use Koios:
 * - Set backendType to "koios"
 */
public class BaseIT {
    private static final Logger log = LoggerFactory.getLogger(BaseIT.class);

    // Backend type constants
    public static final String BLOCKFROST = "blockfrost";
    public static final String KOIOS = "koios";
    public static final String DEVKIT = "devkit";

    // YaciDevKit specific constants
    public static final String DEVKIT_ADMIN_BASE_URL = "http://localhost:10000/";
    public static final String DEVKIT_API_URL = "http://localhost:8080/api/v1/";

    // Default backend type
    protected static String backendType = DEVKIT;

    // Test accounts - these will be created with YaciDevKit or existing accounts for mainnet/testnet
    protected Account account1;
    protected Account account2;
    protected Account account3;
    protected Account account4;
    protected String address1;
    protected String address2;
    protected String address3;
    protected String address4;

    // Default mnemonic used by YaciDevKit for test accounts
    protected static final String YACI_DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    /**
     * Get the configured backend service based on backend type.
     * @return BackendService instance
     */
    public BackendService getBackendService() {
        if (BLOCKFROST.equals(backendType)) {
            String bfProjectId = System.getProperty("BF_PROJECT_ID");
            if (bfProjectId == null || bfProjectId.isEmpty()) {
                bfProjectId = System.getenv("BF_PROJECT_ID");
            }

            if (bfProjectId == null || bfProjectId.isEmpty()) {
                throw new IllegalStateException("BF_PROJECT_ID not set. Please set it as system property or environment variable.");
            }

            log.info("Using Blockfrost backend with project ID: {}...", bfProjectId.substring(0, Math.min(8, bfProjectId.length())));
            return new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, bfProjectId);
        } else if (KOIOS.equals(backendType)) {
            log.info("Using Koios backend (preprod)");
            return new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREPROD_URL);
        } else if (DEVKIT.equals(backendType)) {
            log.info("Using YaciDevKit backend at {}", DEVKIT_API_URL);
            return new BFBackendService(DEVKIT_API_URL, "Dummy");
        } else {
            throw new IllegalArgumentException("Unknown backend type: " + backendType);
        }
    }

    /**
     * Initialize test accounts. For YaciDevKit, accounts are pre-funded.
     * For other backends, you may need to provide your own test accounts.
     */
    protected void initializeAccounts() {
        // Always use testnet for integration tests
        String mnemonic = getMnemonic();

        account1 = new Account(Networks.testnet(), mnemonic, 0);
        account2 = new Account(Networks.testnet(), mnemonic, 1);
        account3 = new Account(Networks.testnet(), mnemonic, 2);
        account4 = new Account(Networks.testnet(), mnemonic, 3);

        address1 = account1.baseAddress();
        address2 = account2.baseAddress();
        address3 = account3.baseAddress();
        address4 = account4.baseAddress();

        log.info("Initialized test accounts:");
        log.info("Account 1: {}", address1);
        log.info("Account 2: {}", address2);
        log.info("Account 3: {}", address3);
        log.info("Account 4: {}", address4);
    }

    /**
     * Get the mnemonic to use for test accounts.
     * Override this method to provide custom mnemonics for different backends.
     * @return mnemonic string
     */
    protected String getMnemonic() {
        if (DEVKIT.equals(backendType)) {
            return YACI_DEFAULT_MNEMONIC;
        } else {
            // For other backends, check for custom mnemonic
            String customMnemonic = System.getProperty("TEST_MNEMONIC");
            if (customMnemonic == null || customMnemonic.isEmpty()) {
                customMnemonic = System.getenv("TEST_MNEMONIC");
            }

            if (customMnemonic == null || customMnemonic.isEmpty()) {
                log.warn("No TEST_MNEMONIC provided. Using YaciDevKit default mnemonic. This may not work with real networks!");
                return YACI_DEFAULT_MNEMONIC;
            }

            return customMnemonic;
        }
    }

    /**
     * Wait for a transaction to be confirmed on chain.
     * @param result The transaction submission result
     */
    public void waitForTransaction(Result<String> result) {
        waitForTransaction(result, 60, 200);
    }

    /**
     * Wait for a transaction to be confirmed on chain with custom delay between attempts.
     * @param result The transaction submission result
     * @param delayMs Delay between attempts in milliseconds
     */
    public void waitForTransaction(Result<String> result, int delayMs) {
        waitForTransaction(result, 60, delayMs);
    }

    /**
     * Wait for a transaction to be confirmed on chain with custom timeout.
     * @param result The transaction submission result
     * @param maxAttempts Maximum number of attempts
     * @param delayMs Delay between attempts in milliseconds
     */
    public void waitForTransaction(Result<String> result, int maxAttempts, int delayMs) {
        if (!result.isSuccessful()) {
            log.error("Transaction submission failed: {}", result.getResponse());
            return;
        }

        String txHash = result.getValue();
        log.info("Waiting for transaction {} to be confirmed...", txHash);

        try {
            int count = 0;
            while (count < maxAttempts) {
                Result<TransactionContent> txnResult = getBackendService().getTransactionService().getTransaction(txHash);
                if (txnResult.isSuccessful()) {
                    log.info("Transaction confirmed: {}", txHash);
                    if (log.isDebugEnabled()) {
                        log.debug("Transaction details: {}", JsonUtil.getPrettyJson(txnResult.getValue()));
                    }
                    return;
                } else {
                    log.debug("Transaction not yet confirmed, attempt {}/{}", count + 1, maxAttempts);
                }

                count++;
                Thread.sleep(delayMs);
            }

            log.warn("Transaction {} was not confirmed after {} attempts", txHash, maxAttempts);
        } catch (Exception e) {
            log.error("Error waiting for transaction: ", e);
        }
    }

    /**
     * Check if a UTXO is available at an address.
     * @param txHash Transaction hash to look for
     * @param address Address to check
     * @return true if UTXO was found, false otherwise
     */
    protected boolean checkIfUtxoAvailable(String txHash, String address) {
        return checkIfUtxoAvailable(txHash, address, 20, 1000);
    }

    /**
     * Check if a UTXO is available at an address with custom timeout.
     * @param txHash Transaction hash to look for
     * @param address Address to check
     * @param maxAttempts Maximum number of attempts
     * @param delayMs Delay between attempts in milliseconds
     * @return true if UTXO was found, false otherwise
     */
    protected boolean checkIfUtxoAvailable(String txHash, String address, int maxAttempts, int delayMs) {
        log.info("Checking for UTXO from tx {} at address {}", txHash, address);

        int count = 0;
        while (count < maxAttempts) {
            List<Utxo> utxos = new DefaultUtxoSupplier(getBackendService().getUtxoService()).getAll(address);
            Optional<Utxo> utxo = utxos.stream()
                    .filter(u -> u.getTxHash().equals(txHash))
                    .findFirst();

            if (utxo.isPresent()) {
                log.info("UTXO found from tx {} at address {}", txHash, address);
                return true;
            }

            count++;
            log.debug("UTXO not yet available, attempt {}/{}", count, maxAttempts);

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for UTXO", e);
                return false;
            }
        }

        log.warn("UTXO from tx {} not found at address {} after {} attempts", txHash, address, maxAttempts);
        return false;
    }

    /**
     * Get the balance of an address in lovelace.
     * @param address The address to check
     * @return Balance in lovelace
     */
    protected long getBalance(String address) {
        List<Utxo> utxos = new DefaultUtxoSupplier(getBackendService().getUtxoService()).getAll(address);
        long balance = utxos.stream()
                .mapToLong(utxo -> utxo.getAmount().stream()
                        .filter(amount -> "lovelace".equals(amount.getUnit()))
                        .mapToLong(amount -> amount.getQuantity().longValue())
                        .sum())
                .sum();

        log.debug("Balance for {}: {} lovelace ({} ADA)", address, balance, balance / 1_000_000.0);
        return balance;
    }

    /**
     * Print the current balances of all test accounts.
     */
    protected void printBalances() {
        log.info("=== Account Balances ===");
        log.info("Account 1 ({}...): {} ADA", address1.substring(0, 20), getBalance(address1) / 1_000_000.0);
        log.info("Account 2 ({}...): {} ADA", address2.substring(0, 20), getBalance(address2) / 1_000_000.0);
        log.info("Account 3 ({}...): {} ADA", address3.substring(0, 20), getBalance(address3) / 1_000_000.0);
        log.info("Account 4 ({}...): {} ADA", address4.substring(0, 20), getBalance(address4) / 1_000_000.0);
        log.info("========================");
    }

    /**
     * Wait for a specified number of blocks.
     * Useful for ensuring time-based operations are processed.
     * @param blocks Number of blocks to wait
     */
    protected void waitForBlocks(int blocks) {
        // For YaciDevKit, blocks are typically produced every 2 seconds
        // For real networks, this may vary
        int blockTimeMs = DEVKIT.equals(backendType) ? 2000 : 20000;

        log.info("Waiting for {} blocks (approximately {} seconds)...", blocks, (blocks * blockTimeMs) / 1000);
        try {
            Thread.sleep(blocks * blockTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for blocks", e);
        }
    }

    /**
     * Set the backend type to use for tests.
     * Must be called before initializing accounts or getting backend service.
     * @param type One of BLOCKFROST, KOIOS, or DEVKIT
     */
    public static void setBackendType(String type) {
        if (!BLOCKFROST.equals(type) && !KOIOS.equals(type) && !DEVKIT.equals(type)) {
            throw new IllegalArgumentException("Invalid backend type: " + type + ". Must be one of: " + BLOCKFROST + ", " + KOIOS + ", " + DEVKIT);
        }
        backendType = type;
        log.info("Backend type set to: {}", type);
    }

    // Topup constants
    private static final long TOPUP_THRESHOLD_ADA = 1000L;  // Threshold below which to topup (1000 ADA)
    private static final long TOPUP_AMOUNT_ADA = 5000L;     // Amount to topup (5000 ADA)
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Topup account balance using YaciDevKit admin API if balance is below threshold.
     * Only works with YaciDevKit backend.
     *
     * Checks if the account balance is below 1000 ADA and tops up to 5000 ADA if needed.
     * @param address The address to check and potentially topup
     */
    protected void topupIfNeeded(String address) {
        if (!DEVKIT.equals(backendType)) {
            log.debug("Topup is only available for YaciDevKit backend, current backend: {}", backendType);
            return;
        }

        try {
            long currentBalanceLovelace = getBalanceFromDevKit(address);
            long currentBalanceAda = currentBalanceLovelace / 1_000_000L;

            log.info("Address {} current balance: {} ADA ({} lovelace)",
                    address.substring(0, Math.min(20, address.length())) + "...", currentBalanceAda, currentBalanceLovelace);

            if (currentBalanceAda < TOPUP_THRESHOLD_ADA) {
                log.info("Balance ({} ADA) is below threshold ({} ADA), topping up to {} ADA...",
                        currentBalanceAda, TOPUP_THRESHOLD_ADA, TOPUP_AMOUNT_ADA);

                boolean success = topupAccount(address, TOPUP_AMOUNT_ADA);

                if (success) {
                    log.info("✓ Address successfully topped up to {} ADA", TOPUP_AMOUNT_ADA);

                    // Wait a moment for the topup to be reflected
                    Thread.sleep(2000);

                    // Verify the new balance
                    long newBalanceLovelace = getBalanceFromDevKit(address);
                    long newBalanceAda = newBalanceLovelace / 1_000_000L;
                    log.info("  New balance: {} ADA", newBalanceAda);
                } else {
                    log.warn("Failed to topup address: {}", address);
                }
            } else {
                log.info("✓ Balance ({} ADA) is sufficient, no topup needed", currentBalanceAda);
            }

        } catch (Exception e) {
            log.error("Error during topup for address {}: ", address, e);
        }
    }

    /**
     * Topup all test accounts (account1, account2, account3) if needed.
     * Convenience method that calls topupIfNeeded for each test account.
     */
    protected void topupAllTestAccounts() {
        if (address1 != null) topupIfNeeded(address1);
        if (address2 != null) topupIfNeeded(address2);
        if (address3 != null) topupIfNeeded(address3);
        if (address4 != null) topupIfNeeded(address4);
    }

    /**
     * Get the balance of an address using YaciDevKit admin API.
     * This provides a direct way to check balance without relying on the backend service.
     * @param address Address to check balance for
     * @return Balance in lovelace
     */
    private long getBalanceFromDevKit(String address) throws IOException {
        String url = DEVKIT_ADMIN_BASE_URL + "local-cluster/api/addresses/" + address + "/utxos?page=1";

        log.debug("Fetching UTXOs from: {}", url);

        URL obj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to fetch UTXOs for address " + address + ". Response code: " + responseCode);
        }

        // Read the response
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        // Parse JSON response and calculate total balance
        JsonNode utxosArray = objectMapper.readTree(response.toString());
        long totalBalance = 0;

        if (utxosArray.isArray()) {
            for (JsonNode utxo : utxosArray) {
                JsonNode amounts = utxo.get("amount");
                if (amounts != null && amounts.isArray()) {
                    for (JsonNode amount : amounts) {
                        String unit = amount.get("unit").asText();
                        if ("lovelace".equals(unit)) {
                            totalBalance += amount.get("quantity").asLong();
                        }
                    }
                }
            }
        }

        log.debug("Total balance for {}: {} lovelace", address, totalBalance);
        return totalBalance;
    }

    /**
     * Perform the actual topup operation using YaciDevKit admin API.
     * @param address Address to topup
     * @param adaAmount Amount in ADA to topup
     * @return true if topup was successful, false otherwise
     */
    private boolean topupAccount(String address, long adaAmount) {
        try {
            String url = DEVKIT_ADMIN_BASE_URL + "local-cluster/api/addresses/topup";
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

            // Set request method to POST
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Create JSON payload
            String jsonInputString = String.format("{\"address\": \"%s\", \"adaAmount\": %d}", address, adaAmount);
            log.debug("Topup request: {}", jsonInputString);

            // Send the request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check the response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                log.debug("Topup API call successful for address: {}", address);
                return true;
            } else {
                log.error("Failed to topup address {}. Response code: {}", address, responseCode);
                return false;
            }
        } catch (Exception e) {
            log.error("Exception during topup for address {}: ", address, e);
            return false;
        }
    }

    protected static void resetDevNet() {
        try {
            // URL to reset the network
            String url = DEVKIT_ADMIN_BASE_URL + "local-cluster/api/admin/devnet/reset";
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

            // Set request method to POST
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Check the response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Devnet reset successful");
            } else {
                System.out.println("Failed to reset the network. Response code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
