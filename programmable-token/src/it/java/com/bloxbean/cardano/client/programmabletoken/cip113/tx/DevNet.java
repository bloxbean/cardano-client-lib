package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Yaci DevKit admin calls, mirroring {@code QuickTxBaseIT} in the quicktx module.
 *
 * <p>Duplicated rather than shared because integration-test source sets are not on each other's
 * classpath. Kept to the two calls this suite needs. Every call either succeeds or throws: a
 * reset or top-up that silently failed would surface much later as a confusing funding error.</p>
 */
final class DevNet {

    private static final Logger log = LoggerFactory.getLogger(DevNet.class);

    static final String BACKEND_URL = "http://localhost:8080/api/v1/";
    static final String ADMIN_URL = "http://localhost:10000/";

    private DevNet() {}

    /** Whether a devnet is listening. The only condition a suite may skip on. */
    static boolean isRunning() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BACKEND_URL + "blocks/latest").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(2000);
            c.setReadTimeout(2000);
            return c.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }

    static void topUp(String address, long adaAmount) {
        post("local-cluster/api/addresses/topup",
                String.format("{\"address\": \"%s\", \"adaAmount\": %d}", address, adaAmount));
        log.info("Topped up {} ADA at {}", adaAmount, address);
    }

    static void reset() {
        post("local-cluster/api/admin/devnet/reset", null);
        log.info("Devnet reset");
    }

    private static void post(String path, String body) {
        int code;
        String response;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(ADMIN_URL + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                if (body != null) os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            response = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("DevKit call " + path + " failed: " + e.getMessage(), e);
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("DevKit call " + path + " answered HTTP " + code
                    + (response.isBlank() ? "" : ": " + response.trim()));
        }
    }
}
