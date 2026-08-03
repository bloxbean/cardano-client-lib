package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.YaciDevKitUtil;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the order-escrow settlement scenario: locking and
 * claiming real Plutus (Aiken) vault UTXOs through {@link TxFlowStream}.
 *
 * The validator is the repo's {@code address_check} Aiken vault
 * ({@code annotation-processor/src/it/resources/blueprint/address_check}):
 * datum {@code Vault { admin: Address }}, spend requires the admin's key hash
 * in {@code extra_signatories}. Claims ride ONE explicit lane keyed on the
 * contract address, so concurrent fulfillment events settle FIFO instead of
 * racing for the same escrow UTXOs, and each claim carries the fulfillment
 * event id as its idempotency key so a redelivered event attaches instead of
 * double-claiming.
 *
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest --tests TxFlowStreamContractIntegrationTest -Dyaci.integration.test=true
 */
class TxFlowStreamContractIntegrationTest {
    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    /**
     * compiledCode of validator {@code address_check.address_check.spend} from
     * the CIP-57 blueprint
     * annotation-processor/src/it/resources/blueprint/address_check/plutus.json
     * (plutusVersion v3). PlutusBlueprintUtil applies the double-CBOR wrapping
     * CCL's PlutusScript cborHex expects.
     */
    private static final String VAULT_COMPILED_CODE =
            "59010001010029800aba2aba1aab9faab9eaab9dab9a48888896600264646644b30013370e900118031baa00189"
            + "94c004c02800660146016003370e90002444b30013001300a375400d132325980098080014566002600660186"
            + "ea8012264b30013004300d37540031323322330020020012259800800c528456600266e3cdd71809800801c52"
            + "8c4cc008008c05000500f20243758602260246024602460246024602460246024601e6ea8c044030dd7180818"
            + "071baa0018b2018300f300d3754601e601a6ea8c03cc034dd500245900b45900e1bae300e001300b375400d16"
            + "402430073754003164014600e002600e6010002600e00260066ea801e29344d95900101";

    private static final String OPERATOR_REF = "account://operator";

    private BFBackendService backendService;
    private DefaultUtxoSupplier utxoSupplier;
    private Account operator;
    private Account beneficiary;
    private PlutusScript vaultScript;
    private String scriptAddress;
    private String escrowLane;
    private ExecutorService engineExecutor;
    private ExecutorService maintenanceExecutor;
    private ExecutorService streamExecutor;
    private FlowEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
        utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

        operator = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 710);
        beneficiary = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 711);
        assertTrue(YaciDevKitUtil.topup(operator.baseAddress(), 1000), "Failed to topup operator");
        Thread.sleep(2000);

        // Load the vault validator from the blueprint's compiledCode and derive
        // its script address.
        vaultScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                VAULT_COMPILED_CODE, PlutusVersion.v3);
        scriptAddress = AddressProvider.getEntAddress(vaultScript, Networks.testnet()).toBech32();
        // ONE lane per contract: every claim against this vault shares the
        // lane, so contended escrow UTXOs are settled FIFO instead of racing.
        escrowLane = "escrow:" + scriptAddress;

        engineExecutor = Executors.newFixedThreadPool(4);
        maintenanceExecutor = Executors.newSingleThreadExecutor();
        streamExecutor = Executors.newFixedThreadPool(2);
        engine = FlowEngine.builder(
                        utxoSupplier,
                        new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                        new DefaultTransactionProcessor(backendService.getTransactionService()),
                        new DefaultChainDataSupplier(backendService))
                .executor(engineExecutor)
                .maintenanceExecutor(maintenanceExecutor)
                .store(new InMemoryFlowExecutionStore())
                .signerRegistry(new DefaultSignerRegistry()
                        .addAccount(OPERATOR_REF, operator))
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        streamExecutor.shutdown();
        engineExecutor.shutdown();
        maintenanceExecutor.shutdown();
        streamExecutor.awaitTermination(10, TimeUnit.SECONDS);
        engineExecutor.awaitTermination(10, TimeUnit.SECONDS);
        maintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    void escrowLocksAndClaimsSettleThroughTheStreamOnOneContractLane() throws Exception {
        byte[] adminKeyHash = operator.getBaseAddress().getPaymentCredentialHash().get();
        PlutusData vaultDatum = vaultDatum(adminKeyHash);

        try (TxFlowStream stream = TxFlowStream.builder("escrow-settlement", engine)
                .lanes(LanePolicy.explicit())
                .laneResolver(laneName -> escrowLane.equals(laneName)
                        ? ResolvedLane.ofFundingRef(escrowLane, OPERATOR_REF)
                        : null)
                .executor(streamExecutor)
                .maxBufferSize(10)
                .build()) {
            stream.start();

            // --- 1. LOCK: two order escrows through the stream (perItem) ---
            TxStreamReceipt lock1 = stream.submit(TxWorkItem.builder("lock-order-1")
                    .withTxPlan(lockPlan(Amount.ada(5), vaultDatum))
                    .withLane(escrowLane)
                    .withIdempotencyKey("lock-order-1")
                    .build());
            TxStreamReceipt lock2 = stream.submit(TxWorkItem.builder("lock-order-2")
                    .withTxPlan(lockPlan(Amount.ada(6), vaultDatum))
                    .withLane(escrowLane)
                    .withIdempotencyKey("lock-order-2")
                    .build());

            TxStreamItemResult lock1Result = lock1.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult lock2Result = lock2.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CONFIRMED, lock1Result.getStatus(),
                    () -> String.valueOf(lock1Result.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, lock2Result.getStatus(),
                    () -> String.valueOf(lock2Result.getError()));
            waitForUtxo(lock1Result.getTransactionHash(), scriptAddress);
            waitForUtxo(lock2Result.getTransactionHash(), scriptAddress);

            // --- 2. FIND the escrow UTXOs by inline datum, map each to its order
            // by the lock transaction hash the service recorded ---
            List<Utxo> escrows = ScriptUtxoFinders.findAllByInlineDatum(
                    utxoSupplier, scriptAddress, vaultDatum);
            Utxo escrow1 = byTxHash(escrows, lock1Result.getTransactionHash());
            Utxo escrow2 = byTxHash(escrows, lock2Result.getTransactionHash());

            // --- 3. CLAIM both escrows through the stream on the shared
            // contract lane (fulfillment events; event id = idempotency key) ---
            TxStreamReceipt claim1 = stream.submit(claimItem("order-1", escrow1, Amount.ada(5)));
            TxStreamReceipt claim2 = stream.submit(claimItem("order-2", escrow2, Amount.ada(6)));

            TxStreamItemResult claim1Result = claim1.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult claim2Result = claim2.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            stream.awaitDrain(Duration.ofSeconds(60));

            assertEquals(TxStreamItemStatus.CONFIRMED, claim1Result.getStatus(),
                    () -> String.valueOf(claim1Result.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, claim2Result.getStatus(),
                    () -> String.valueOf(claim2Result.getError()));
            assertNotNull(claim1Result.getTransactionHash());
            assertNotNull(claim2Result.getTransactionHash());
            assertNotEquals(claim1Result.getTransactionHash(), claim2Result.getTransactionHash(),
                    "each order settles in its own claim transaction");
            assertEquals(escrowLane, claim1Result.getLaneName());
            assertEquals(escrowLane, claim2Result.getLaneName());

            // --- 4. Idempotency: a redelivered fulfillment event attaches to
            // the existing claim instead of double-claiming the escrow ---
            TxStreamReceipt redelivered = stream.submit(claimItem("order-1", escrow1, Amount.ada(5)));
            assertSame(claim1, redelivered);

            // --- 5. The funds arrived at the beneficiary ---
            List<Utxo> received = utxoSupplier.getAll(beneficiary.baseAddress());
            assertTrue(received.stream().anyMatch(
                            u -> u.getTxHash().equals(claim1Result.getTransactionHash())),
                    "order-1 payout should be at the beneficiary");
            assertTrue(received.stream().anyMatch(
                            u -> u.getTxHash().equals(claim2Result.getTransactionHash())),
                    "order-2 payout should be at the beneficiary");

            TxStreamStats stats = stream.getStats();
            assertEquals(4, stats.acceptedItemCount());
            assertEquals(4, stats.confirmedItemCount());
            assertEquals(0, stats.failedItemCount());
            assertTrue(stream.isHealthy());
        }
    }

    /**
     * Lock plan: pay the order's escrow amount to the vault with the inline
     * {@code Vault { admin }} datum, funded by the operator.
     */
    private TxPlan lockPlan(Amount amount, PlutusData vaultDatum) {
        return TxPlan.from(new Tx()
                        .payToContract(scriptAddress, amount, vaultDatum)
                        .fromRef(OPERATOR_REF))
                .withSigner(OPERATOR_REF);
    }

    /**
     * One claim work item per fulfillment event: same contract lane (FIFO),
     * fulfillment event id as the idempotency key.
     */
    private TxWorkItem claimItem(String orderId, Utxo escrowUtxo, Amount payout) {
        return TxWorkItem.builder("claim-" + orderId)
                .withTxPlan(claimPlan(orderId, escrowUtxo, payout))
                .withLane(escrowLane)
                .withIdempotencyKey("fulfill-" + orderId)
                .build();
    }

    /**
     * Claim plan on the portable script surface of the unified {@link Tx}:
     * spend the escrow UTXO with a redeemer, attach the validator inline
     * (serialized as cbor_hex + version), pay the beneficiary, and fund
     * fee/change from the operator. Collateral and the admin's required signer
     * ride the plan's portable context.
     */
    private TxPlan claimPlan(String orderId, Utxo escrowUtxo, Amount payout) {
        Tx claim = new Tx()
                .collectFrom(escrowUtxo, claimRedeemer(orderId))
                .payToAddress(beneficiary.baseAddress(), payout)
                .attachSpendingValidator(vaultScript)
                .fromRef(OPERATOR_REF);
        return TxPlan.from(claim)
                .withSigner(OPERATOR_REF)
                .collateralPayerRef(OPERATOR_REF)
                // The vault checks extra_signatories for the admin's key hash.
                .withRequiredSigners(HexUtil.encodeHexString(
                        operator.getBaseAddress().getPaymentCredentialHash().get()));
    }

    /**
     * Datum {@code Vault { admin: Address }}. An Aiken {@code Address} is a
     * constr structure — the shape below matches the generated blueprint
     * converters proven on devnet by AddressCheckDevnetTest:
     * Vault = Constr 0 [Address];
     * Address = Constr 0 [PaymentCredential, Option&lt;StakeCredential&gt;];
     * PaymentCredential.VerificationKey = Constr 0 [bytes(keyHash)];
     * Option None = Constr 1 [].
     */
    private PlutusData vaultDatum(byte[] adminKeyHash) {
        ConstrPlutusData paymentCredential = ConstrPlutusData.of(0, BytesPlutusData.of(adminKeyHash));
        ConstrPlutusData noStakeCredential = ConstrPlutusData.of(1);
        ConstrPlutusData adminAddress = ConstrPlutusData.of(0, paymentCredential, noStakeCredential);
        return ConstrPlutusData.of(0, adminAddress);
    }

    /** Redeemer {@code Redeemer { marker: ByteArray }} = Constr 0 [bytes]. */
    private PlutusData claimRedeemer(String marker) {
        return ConstrPlutusData.of(0,
                BytesPlutusData.of(marker.getBytes(StandardCharsets.UTF_8)));
    }

    private Utxo byTxHash(List<Utxo> utxos, String txHash) {
        Optional<Utxo> match = utxos.stream()
                .filter(u -> u.getTxHash().equals(txHash))
                .findFirst();
        assertTrue(match.isPresent(), "Escrow UTXO for tx " + txHash + " should be at the vault");
        return match.get();
    }

    private void waitForUtxo(String txHash, String address) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                List<Utxo> utxos = utxoSupplier.getAll(address);
                if (utxos.stream().anyMatch(u -> u.getTxHash().equals(txHash))) {
                    return;
                }
            } catch (Exception e) {
                // retry
            }
            Thread.sleep(1000);
        }
    }
}
