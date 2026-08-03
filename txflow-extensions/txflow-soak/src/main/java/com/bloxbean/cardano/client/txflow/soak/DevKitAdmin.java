package com.bloxbean.cardano.client.txflow.soak;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Client for the Yaci DevKit admin API: the faucet, and the snapshot/rollback controls that let
 * a soak inject a real chain reorg on demand.
 *
 * <p>None of this exists on a public network. A soak pointed at preprod funds its lanes up front
 * and skips rollback chaos entirely — you cannot ask a public chain to reorg on cue.
 */
public final class DevKitAdmin {

    private final String adminUrl;

    public DevKitAdmin(String adminUrl) {
        this.adminUrl = adminUrl;
    }

    /** Whether a DevKit admin API is answering — decides devnet-only behaviour. */
    public boolean isReachable() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL(adminUrl + "/addresses/topup").openConnection();
            conn.setRequestMethod("OPTIONS");
            conn.setConnectTimeout(2_000);
            conn.getResponseCode();
            conn.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean topup(String address, double adaAmount) {
        return post("/addresses/topup",
                String.format("{\"address\":\"%s\",\"adaAmount\":%s}", address, adaAmount));
    }

    /** Mark a point the chain can later be rewound to. */
    public boolean takeSnapshot() {
        return post("/devnet/rollback/take-db-snapshot", null);
    }

    /**
     * Rewind the chain to the last snapshot — indistinguishable from a real reorg to any client.
     * The node restarts, so give it time before expecting sane answers.
     */
    public boolean rollbackToSnapshot() {
        return post("/devnet/rollback/rollback-to-db-snapshot", null);
    }

    private boolean post(String path, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(adminUrl + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(60_000);
            conn.setDoOutput(true);
            if (jsonBody != null) conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
