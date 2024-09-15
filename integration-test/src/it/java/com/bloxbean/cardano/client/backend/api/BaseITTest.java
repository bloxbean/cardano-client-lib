package com.bloxbean.cardano.client.backend.api;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.ogmios.http.OgmiosBackendService;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.util.HexUtil;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class BaseITTest {
    public static final String DEVKIT_ADMIN_BASE_URL = "http://localhost:10000/";
    protected static String BLOCKFROST = "blockfrost";
    protected static String KOIOS = "koios";
    protected static String OGMIOS = "ogmios";
    protected static String DEVKIT = "devkit";
    protected static String backendType = BLOCKFROST;

    public BackendService getBackendService() {
        if (BLOCKFROST.equals(backendType)) {
            String bfProjectId = System.getProperty("BF_PROJECT_ID");
            if (bfProjectId == null || bfProjectId.isEmpty()) {
                bfProjectId = System.getenv("BF_PROJECT_ID");
            }
            return new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, bfProjectId);
        } else if (KOIOS.equals(backendType)) {
            return new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREPROD_URL);
        } else if (OGMIOS.equals(backendType)) {
            return new OgmiosBackendService(com.bloxbean.cardano.client.backend.ogmios.Constants.OGMIOS_DANDELION_TESTNET_URL);
        } else if (DEVKIT.equals(backendType)) {
            return new BFBackendService("http://localhost:8080/api/v1/", "Dummy");
        } else
            return null;
    }

    protected PlutusV2Script getPlutusScript(String aikenCompileCode) {
        //Do double encoding for aiken compileCode
        ByteString bs = new ByteString(HexUtil.decodeHexString(aikenCompileCode));
        try {
            String cborHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(bs));
            return PlutusV2Script.builder()
                    .cborHex(cborHex)
                    .build();
        } catch (CborException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void topUpFund(String address, long adaAmount) {
        try {
            // URL to the top-up API
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

            // Send the request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check the response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Funds topped up successfully.");
            } else {
                System.out.println("Failed to top up funds. Response code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void checkIfUtxoAvailable(String txHash, String address) {
        Optional<Utxo> utxo = Optional.empty();
        int count = 0;
        while (utxo.isEmpty()) {
            if (count++ >= 20)
                break;
            List<Utxo> utxos = new DefaultUtxoSupplier(getBackendService().getUtxoService()).getAll(address);
            utxo = utxos.stream().filter(u -> u.getTxHash().equals(txHash))
                    .findFirst();
            System.out.println("Try to get new output... txhash: " + txHash);
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }
}
