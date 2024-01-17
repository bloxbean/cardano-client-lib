package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.util.List;
import java.util.Optional;

public class QuickTXBaseIT {

    protected String BLOCKFROST = "blockfrost";
    protected String KOIOS = "koios";
    protected String DEVKIT = "devkit";
    protected String backendType = BLOCKFROST;

    public BackendService getBackendService() {
        if (BLOCKFROST.equals(backendType)) {
            String bfProjectId = System.getProperty("BF_PROJECT_ID");
            if (bfProjectId == null || bfProjectId.isEmpty()) {
                bfProjectId = System.getenv("BF_PROJECT_ID");
            }

            return new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, bfProjectId);
        } else if (KOIOS.equals(backendType)) {
            return new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREPROD_URL);
        } else if (DEVKIT.equals(backendType)) {
            return new BFBackendService("http://localhost:8080/api/v1/", "Dummy");
        } else
            return null;
    }

    public UtxoSupplier getUTXOSupplier() {
        return new DefaultUtxoSupplier(getBackendService().getUtxoService());
    }

    public void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = getBackendService().getTransactionService().getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
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
