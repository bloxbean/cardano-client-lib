package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.account.Account
import com.bloxbean.cardano.client.api.exception.ApiException
import com.bloxbean.cardano.client.api.model.Result
import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService
import com.bloxbean.cardano.client.backend.ogmios.http.OgmiosBackendService
import com.bloxbean.cardano.client.common.model.Networks
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder
import com.bloxbean.cardano.client.supplier.kupo.KupoUtxoSupplier
import com.bloxbean.cardano.client.supplier.ogmios.OgmiosProtocolParamSupplier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE
/**
 * Base class for Groovy DSL integration tests
 * Provides setup for Yaci DevKit, Blockfrost, or Koios backends
 */
abstract class TxGroovyDslBaseIT extends Specification {

    protected static final Logger log = LoggerFactory.getLogger(TxGroovyDslBaseIT.class)

    // Backend types
    protected static final String DEVKIT = "devkit"
    protected static final String BLOCKFROST = "blockfrost"
    protected static final String KOIOS = "koios"
    protected static final String OGMIOS_KUPO = "ogmios-kupo"

    // Yaci DevKit URLs
    protected static final String DEVKIT_BASE_URL = "http://localhost:8080/api/v1/"
    protected static final String DEVKIT_ADMIN_BASE_URL = "http://localhost:10000/"

    // Default backend
    protected static String backendType = DEVKIT

    // Services
    protected BackendService backendService
    protected QuickTxBuilder quickTxBuilder

    // Test accounts
    protected static Account sender1
    protected static Account sender2
    protected static Account receiver1
    protected static Account receiver2

    protected static String sender1Addr
    protected static String sender2Addr
    protected static String receiver1Addr
    protected static String receiver2Addr

    // Default test mnemonic (same as QuickTx tests)
    protected static final String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code"
    protected static final String senderMnemonic2 = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact"

    def setupSpec() {
        // Check for backend type from system property
        String type = System.getProperty("backend.type")
        if (type) {
            backendType = type
        }

        log.info("Using backend type: {}", backendType)

        // Create test accounts
        sender1 = new Account(Networks.testnet(), senderMnemonic)
        sender1Addr = sender1.baseAddress()

        sender2 = new Account(Networks.testnet(), senderMnemonic2)
        sender2Addr = sender2.baseAddress()

        receiver1 = new Account(Networks.testnet())
        receiver1Addr = receiver1.baseAddress()

        receiver2 = new Account(Networks.testnet())
        receiver2Addr = receiver2.baseAddress()

        log.info("Sender1 address: {}", sender1Addr)
        log.info("Sender2 address: {}", sender2Addr)
        log.info("Receiver1 address: {}", receiver1Addr)
        log.info("Receiver2 address: {}", receiver2Addr)

        // Fund accounts if using DevKit
        if (DEVKIT.equals(backendType)) {
            log.info("Funding test accounts...")
            topUpFund(sender1Addr, 50000)
            topUpFund(sender2Addr, 50000)
        }
    }

    def setup() {
        backendService = getBackendService()
        quickTxBuilder = new QuickTxBuilder(backendService)
    }

    /**
     * Get the configured backend service
     */
    protected BackendService getBackendService() {
        switch (backendType) {
            case DEVKIT:
                log.info("Using Yaci DevKit backend at {}", DEVKIT_BASE_URL)
                return new BFBackendService(DEVKIT_BASE_URL, "Dummy")

            case BLOCKFROST:
                String bfProjectId = System.getProperty("BF_PROJECT_ID")
                if (!bfProjectId) {
                    throw new RuntimeException("BF_PROJECT_ID not set for Blockfrost backend")
                }
                String bfUrl = System.getProperty("BF_URL", "https://cardano-preprod.blockfrost.io/api/v0/")
                log.info("Using Blockfrost backend at {}", bfUrl)
                return new BFBackendService(bfUrl, bfProjectId)

            case KOIOS:
                String koiosUrl = System.getProperty("KOIOS_URL", "https://preprod.koios.rest/api/v1/")
                log.info("Using Koios backend at {}", koiosUrl)
                return new KoiosBackendService(koiosUrl)

            case OGMIOS_KUPO:
                String ogmiosUrl = System.getProperty("OGMIOS_URL", "http://localhost:1337")
                String kupoUrl = System.getProperty("KUPO_URL", "http://localhost:1442")
                log.info("Using Ogmios/Kupo backend at {} / {}", ogmiosUrl, kupoUrl)

                def ogmiosBackendService = new OgmiosBackendService(ogmiosUrl)
                def protocolParamSupplier = new OgmiosProtocolParamSupplier(ogmiosUrl)
                def utxoSupplier = new KupoUtxoSupplier(kupoUrl)

                ogmiosBackendService.setProtocolParamSupplier(protocolParamSupplier)
                ogmiosBackendService.setUtxoSupplier(utxoSupplier)
                return ogmiosBackendService

            default:
                throw new RuntimeException("Unknown backend type: " + backendType)
        }
    }

    /**
     * Top up funds for an address using Yaci DevKit admin API
     */
    protected static void topUpFund(String address, long adaAmount) {
        if (!DEVKIT.equals(backendType)) {
            log.warn("Top-up only available for DevKit backend")
            return
        }

        try {
            String url = DEVKIT_ADMIN_BASE_URL + "local-cluster/api/addresses/topup"

            // Use the same JSON format as QuickTxBuilderIT
            String jsonInputString = String.format('{"address": "%s", "adaAmount": %d}', address, adaAmount)

            def connection = new URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")

            // Send the request
            connection.outputStream.withStream { os ->
                byte[] input = jsonInputString.getBytes("UTF-8")
                os.write(input, 0, input.length)
            }

            def responseCode = connection.responseCode
            if (responseCode == 200) {
                log.info("Successfully topped up {} with {} ADA", address, adaAmount)
            } else {
                log.error("Failed to top up funds. Response code: {}", responseCode)
            }
        } catch (Exception e) {
            log.error("Error topping up funds", e)
        }
    }

    /**
     * Reset the DevKit network
     */
    protected static void resetDevNet() {
        if (!DEVKIT.equals(backendType)) {
            log.warn("Reset only available for DevKit backend")
            return
        }

        try {
            String url = DEVKIT_ADMIN_BASE_URL + "local-cluster/api/admin/devnet/reset"

            def connection = new URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true

            def responseCode = connection.responseCode
            if (responseCode == 200) {
                log.info("Successfully reset DevKit network")
                Thread.sleep(2000) // Wait for reset to complete
            } else {
                log.error("Failed to reset network. Response code: {}", responseCode)
            }
        } catch (Exception e) {
            log.error("Error resetting network", e)
        }
    }

    /**
     * Wait for a transaction to be confirmed
     */
    protected void waitForTransaction(Result<String> result) {
        if (!result.isSuccessful()) {
            log.error("Transaction failed: {}", result.getResponse())
            return
        }

        String txHash = result.getValue()
        log.info("Waiting for transaction: {}", txHash)

        if (DEVKIT.equals(backendType)) {
            waitForTransactionHash(txHash)
        } else {
            // For other backends, just wait a bit
            Thread.sleep(5000)
        }
    }

    /**
     * Wait for a specific transaction hash to be confirmed
     */
    protected void waitForTransactionHash(String txHash) {
        int count = 0
        while (count < 20) {
            try {
                var txnResult = backendService.getTransactionService().getTransaction(txHash)
                if (txnResult.isSuccessful()) {
                    log.info("Transaction confirmed: {}", txHash)
                    return
                }
            } catch (ApiException e) {
                // Transaction not found yet
            }

            log.debug("Waiting for transaction... attempt {}", count + 1)
            Thread.sleep(2000)
            count++
        }

        throw new RuntimeException("Transaction not confirmed after 40 seconds: " + txHash)
    }

    /**
     * Check if UTXO is available at an address
     */
    protected boolean checkIfUtxoAvailable(String txHash, String address) {
        try {
            var utxos = backendService.getUtxoService().getUtxos(address, 100, 1)
            if (utxos.isSuccessful()) {
                return utxos.getValue().stream()
                    .anyMatch { utxo -> utxo.getTxHash().equals(txHash) }
            }
        } catch (Exception e) {
            log.error("Error checking UTXOs", e)
        }
        return false
    }

    /**
     * Get ADA balance for an address
     */
    protected long getAdaBalance(String address) {
        try {
            var result = backendService.getAccountService().getAccountInformation(address)
            if (result.isSuccessful() && result.getValue() != null) {
                return result.getValue().getControlledAmount()
                    .stream()
                    .filter { amount -> LOVELACE.equals(amount.getUnit()) }
                    .findFirst()
                    .map { amount -> amount.getQuantity().longValue() }
                    .orElse(0L)
            }
        } catch (Exception e) {
            log.error("Error getting balance", e)
        }
        return 0L
    }
}
