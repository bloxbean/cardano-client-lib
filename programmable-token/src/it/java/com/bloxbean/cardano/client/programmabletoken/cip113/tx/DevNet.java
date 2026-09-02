package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Yaci DevKit admin calls, mirroring {@code QuickTxBaseIT} in the quicktx module.
 *
 * <p>Duplicated rather than shared because integration-test source sets are not on each other's
 * classpath. Kept to the two calls this suite needs.</p>
 */
final class DevNet {

    static final String BACKEND_URL = "http://localhost:8080/api/v1/";
    static final String ADMIN_URL = "http://localhost:10000/";

    private DevNet() {}

    /** Whether a devnet is listening. Lets the suite fall back to Preview rather than fail. */
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
                String.format("{\"address\": \"%s\", \"adaAmount\": %d}", address, adaAmount),
                "Topped up " + adaAmount + " ADA");
    }

    static void reset() {
        post("local-cluster/api/admin/devnet/reset", null, "Devnet reset");
    }

    private static void post(String path, String body, String okMessage) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(ADMIN_URL + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            if (body != null) {
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            } else {
                connection.getOutputStream().close();
            }

            int code = connection.getResponseCode();
            System.out.println(code == HttpURLConnection.HTTP_OK
                    ? okMessage
                    : "DevKit call " + path + " failed with " + code);
        } catch (Exception e) {
            System.out.println("DevKit call " + path + " failed: " + e.getMessage());
        }
    }
}
