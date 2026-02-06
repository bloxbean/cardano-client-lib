package com.bloxbean.cardano.client.txflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for interacting with Yaci DevKit's admin API during integration tests.
 * <p>
 * This helper provides methods to:
 * <ul>
 *     <li>Top up addresses with ADA for testing</li>
 *     <li>Other administrative operations</li>
 * </ul>
 * <p>
 * Yaci DevKit Admin API must be running on the configured port (default: 10000).
 */
public class YaciDevKitUtil {

    private static final Logger log = LoggerFactory.getLogger(YaciDevKitUtil.class);

    private static final String ADMIN_BASE_URL = "http://localhost:10000/local-cluster/api";
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    /**
     * Top up an address with the specified amount of ADA.
     * <p>
     * This method calls the Yaci DevKit admin API to fund a test address.
     * The transaction is submitted directly by the DevKit and doesn't require
     * any signer or UTXO from the caller.
     *
     * @param address the Cardano address to top up
     * @param adaAmount the amount of ADA to send (e.g., 1000.0 for 1000 ADA)
     * @return true if topup was successful, false otherwise
     */
    public static boolean topup(String address, double adaAmount) {
        HttpURLConnection connection = null;
        try {
            log.info("Topping up address {} with {} ADA", shortenAddress(address), adaAmount);

            URL url = new URL(ADMIN_BASE_URL + "/addresses/topup");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            // Create JSON body
            String jsonBody = String.format("{\"address\":\"%s\",\"adaAmount\":%s}", address, adaAmount);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.info("Topup successful for {} ({} ADA)", shortenAddress(address), adaAmount);
                return true;
            } else {
                log.error("Topup failed with HTTP {}", responseCode);
                return false;
            }
        } catch (Exception e) {
            log.error("Topup failed: {}", e.getMessage(), e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Top up multiple addresses with the specified amount of ADA each.
     *
     * @param addresses array of Cardano addresses to top up
     * @param adaAmount the amount of ADA to send to each address
     * @return true if all topups were successful, false if any failed
     */
    public static boolean topupAll(String[] addresses, double adaAmount) {
        boolean allSuccessful = true;
        for (String address : addresses) {
            if (!topup(address, adaAmount)) {
                allSuccessful = false;
            }
        }
        return allSuccessful;
    }

    /**
     * Top up an address and wait for the transaction to be confirmed.
     * <p>
     * This method tops up the address and then waits a short time for
     * the UTXO to be available.
     *
     * @param address the Cardano address to top up
     * @param adaAmount the amount of ADA to send
     * @param waitMs milliseconds to wait after topup for UTXO availability
     * @return true if topup was successful, false otherwise
     */
    public static boolean topupAndWait(String address, double adaAmount, long waitMs) {
        if (!topup(address, adaAmount)) {
            return false;
        }

        try {
            Thread.sleep(waitMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Shorten an address for logging purposes.
     */
    private static String shortenAddress(String address) {
        if (address == null || address.length() <= 20) {
            return address;
        }
        return address.substring(0, 10) + "..." + address.substring(address.length() - 6);
    }
}
