package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.Cip113Data;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deployment resolution is shared state: one attempt per service, the same outcome for every
 * caller waiting on it, and never a spent coordination output.
 */
class DefaultCip113ProtocolServiceTest {

    private static final String BOOTSTRAP_TX = "ab".repeat(32);
    private static final String PARAMS_POLICY = "11".repeat(28);
    private static final String REGISTRY_NODE_CS = "22".repeat(28);
    private static final String PLB_HASH = "33".repeat(28);
    private static final String REGISTRY_SPEND_HASH = "44".repeat(28);
    private static final String COORDINATION_SPEND_HASH = "55".repeat(28);
    private static final String PARAMS_UNIT = PARAMS_POLICY + HexUtil.encodeHexString(
            Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME.getBytes(StandardCharsets.UTF_8));

    private final Address coordinationAddress = AddressProvider.getEntAddress(
            Credential.fromScript(COORDINATION_SPEND_HASH), Networks.testnet());
    private final Address registryAddress = AddressProvider.getEntAddress(
            Credential.fromScript(REGISTRY_SPEND_HASH), Networks.testnet());

    private BackendService backend;
    private TransactionService transactionService;
    private UtxoService utxoService;
    private AssetService assetService;
    private final AtomicInteger bootstrapReads = new AtomicInteger();

    @BeforeEach
    void mockBackend() throws Exception {
        backend = mock(BackendService.class);
        transactionService = mock(TransactionService.class);
        utxoService = mock(UtxoService.class);
        assetService = mock(AssetService.class);
        when(backend.getTransactionService()).thenReturn(transactionService);
        when(backend.getUtxoService()).thenReturn(utxoService);
        when(backend.getAssetService()).thenReturn(assetService);
        // Every address is empty unless a test says otherwise.
        when(utxoService.getUtxos(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(Result.success("ok").withValue(List.of()));
    }

    private DefaultCip113ProtocolService service() {
        return new DefaultCip113ProtocolService(backend,
                Cip113Deployments.fromBootstrapTx(BOOTSTRAP_TX, Networks.testnet()));
    }

    @Test
    void concurrentFirstCallersShareOneResolution() throws Exception {
        bootstrapReads(300L, Result.success("ok").withValue(bootstrap()));
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, 2048));
        DefaultCip113ProtocolService service = service();

        for (CompletableFuture<Utxo> caller : twoConcurrentCallers(service)) {
            assertThat(caller.join().getTxHash()).isEqualTo(BOOTSTRAP_TX);
        }
        assertThat(bootstrapReads).as("one chain walk for both callers").hasValue(1);
        assertThat(service.deployment().getProgrammableLogicBaseHash()).isEqualTo(PLB_HASH);
        assertThat(service.deployment().getRegistrySpendScriptHash()).isEqualTo(REGISTRY_SPEND_HASH);
    }

    @Test
    void concurrentFirstCallersShareTheSameFailureAndALaterCallRetries() throws Exception {
        when(transactionService.getTransactionUtxos(BOOTSTRAP_TX)).thenAnswer(invocation -> {
            int attempt = bootstrapReads.incrementAndGet();
            Thread.sleep(300L);
            return attempt == 1 ? Result.error("backend down") : Result.success("ok").withValue(bootstrap());
        });
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, 2048));
        DefaultCip113ProtocolService service = service();

        for (CompletableFuture<Utxo> caller : twoConcurrentCallers(service)) {
            assertThatThrownBy(caller::join)
                    .hasCauseInstanceOf(Cip113Exception.class)
                    .hasMessageContaining("backend down");
        }
        assertThat(bootstrapReads).as("both waiters received the one failed attempt").hasValue(1);

        assertThatCode(service::coordinationUtxo).as("a later call retries").doesNotThrowAnyException();
        assertThat(bootstrapReads).hasValue(2);
    }

    /**
     * An in-place upgrade spends the bootstrap coordination output. When the backend cannot say
     * who holds the NFT now, the bootstrap output may only be used after verifying it is unspent.
     */
    @Test
    void aSpentBootstrapOutputIsNeverAssumedLive() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        when(assetService.getAllAssetAddresses(PARAMS_UNIT)).thenThrow(new RuntimeException("no asset index"));
        // Nothing unspent at the coordination address: the bootstrap output was consumed.

        Result<Cip113Deployment> result = service().resolveDeployment();

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getResponse())
                .contains(PARAMS_UNIT)
                .contains(BOOTSTRAP_TX + "#0")
                .contains("spent or no longer carries");
    }

    @Test
    void theBootstrapOutputIsUsedOnlyAfterPositiveVerification() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        when(assetService.getAllAssetAddresses(PARAMS_UNIT)).thenReturn(Result.error("unsupported"));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, 2048));

        DefaultCip113ProtocolService service = service();

        assertThat(service.coordinationUtxo().getTxHash()).isEqualTo(BOOTSTRAP_TX);
        assertThat(service.deployment().getMaxInlineDatumBytes()).isEqualTo(2048);
    }

    /** The live coordination datum wins over the bootstrap one after an in-place upgrade. */
    @Test
    void anUpgradedCoordinationUtxoIsFollowedThroughItsNft() throws Exception {
        String upgradeTx = "cd".repeat(32);
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(upgradeTx, 4096));

        DefaultCip113ProtocolService service = service();

        assertThat(service.coordinationUtxo().getTxHash()).isEqualTo(upgradeTx);
        assertThat(service.deployment().getMaxInlineDatumBytes()).isEqualTo(4096);
    }

    @Test
    void anExplicitResolutionRefreshesThePublishedState() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, 2048));
        DefaultCip113ProtocolService service = service();
        service.coordinationUtxo();

        unspentAt(coordinationAddress, coordinationUtxo("ef".repeat(32), 1024));
        Result<Cip113Deployment> refreshed = service.resolveDeployment();

        assertThat(refreshed.isSuccessful()).as(refreshed.getResponse()).isTrue();
        assertThat(service.coordinationUtxo().getTxHash()).isEqualTo("ef".repeat(32));
        assertThat(service.deployment().getMaxInlineDatumBytes()).isEqualTo(1024);
        assertThat(bootstrapReads).hasValue(2);
    }

    // ------------------------------------------------- plan metadata pinning

    @Test
    void planMetadataIsValidatedAgainstTheConfiguredDeploymentBeforeChainAccess() {
        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(
                Cip113Deployments.fromBootstrapTx(BOOTSTRAP_TX, Networks.testnet()));
        Cip113Protocol protocol = new Cip113Protocol(service);

        assertThatCode(() -> protocol.validateMetadata(metadata(Map.of(
                "network", 0, "bootstrap_tx", BOOTSTRAP_TX.toUpperCase()))))
                .doesNotThrowAnyException();
        assertThatCode(() -> protocol.validateMetadata(metadata(Map.of("network", "0"))))
                .as("a network id that resolved to text still matches")
                .doesNotThrowAnyException();
        assertThatCode(() -> protocol.validateMetadata(metadata(Map.of())))
                .as("omission binds to the configured deployment")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> protocol.validateMetadata(metadata(Map.of("network", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network '1'");
        assertThatThrownBy(() -> protocol.validateMetadata(metadata(Map.of("bootstrap_tx", "ff".repeat(32)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match configured CIP-113 deployment");
        assertThatThrownBy(() -> protocol.validateMetadata(metadata(Map.of("bootstrap_tx", "not-a-hash"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32-byte transaction hash");

        verify(service, never()).resolveDeployment();
        verify(service, never()).registryLookup();
        verify(service, never()).coordinationUtxo();
    }

    // ------------------------------------------------------------- fixtures

    private static ExtensionMetadata metadata(Map<String, Object> deployment) {
        return ExtensionMetadata.builder()
                .extension("programmable-token").schemaVersion("1").protocol(Cip113Protocol.ID)
                .deployment(deployment).build();
    }

    /**
     * Two callers on their own threads, so they overlap regardless of how many cores the common
     * pool was given (a one-core CI runner would serialise {@code supplyAsync} callers).
     */
    private static List<CompletableFuture<Utxo>> twoConcurrentCallers(DefaultCip113ProtocolService service) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            return List.of(CompletableFuture.supplyAsync(service::coordinationUtxo, pool),
                    CompletableFuture.supplyAsync(service::coordinationUtxo, pool));
        } finally {
            pool.shutdown();
        }
    }

    private void bootstrapReads(long delayMillis, Result<TxContentUtxo> answer) throws Exception {
        when(transactionService.getTransactionUtxos(BOOTSTRAP_TX)).thenAnswer(invocation -> {
            bootstrapReads.incrementAndGet();
            if (delayMillis > 0) Thread.sleep(delayMillis);
            return answer;
        });
    }

    private void holderOfParamsNft(Address address) throws Exception {
        when(assetService.getAllAssetAddresses(PARAMS_UNIT)).thenReturn(Result.success("ok")
                .withValue(List.of(AssetAddress.builder().address(address.toBech32()).quantity("1").build())));
    }

    private void unspentAt(Address address, Utxo utxo) throws Exception {
        when(utxoService.getUtxos(eq(address.toBech32()), anyInt(), eq(1), any()))
                .thenReturn(Result.success("ok").withValue(List.of(utxo)));
    }

    private Utxo coordinationUtxo(String txHash, int maxInlineDatumBytes) {
        return Utxo.builder()
                .txHash(txHash).outputIndex(0)
                .address(coordinationAddress.toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)),
                        Amount.builder().unit(PARAMS_UNIT).quantity(BigInteger.ONE).build()))
                .inlineDatum(coordinationDatum(maxInlineDatumBytes).serializeToHex())
                .build();
    }

    private static PlutusData coordinationDatum(int maxInlineDatumBytes) {
        return ConstrPlutusData.of(0,
                Cip113Data.bytesOfHex(REGISTRY_NODE_CS),
                Cip113Data.credential(Credential.fromScript(PLB_HASH)),
                Cip113Data.credential(Credential.fromScript("66".repeat(28))),
                Cip113Data.credential(Credential.fromScript("77".repeat(28))),
                Cip113Data.credential(Credential.fromScript("88".repeat(28))),
                Cip113Data.credential(Credential.fromScript("99".repeat(28))),
                BigIntPlutusData.of(maxInlineDatumBytes));
    }

    /** The bootstrap transaction: coordination output #0 and the origin registry node #1. */
    private TxContentUtxo bootstrap() {
        TxContentUtxoOutputs coordination = TxContentUtxoOutputs.builder()
                .address(coordinationAddress.toBech32()).outputIndex(0)
                .amount(List.of(
                        TxContentOutputAmount.builder().unit("lovelace").quantity("5000000").build(),
                        TxContentOutputAmount.builder().unit(PARAMS_UNIT).quantity("1").build()))
                .inlineDatum(coordinationDatum(2048).serializeToHex())
                .build();
        TxContentUtxoOutputs originNode = TxContentUtxoOutputs.builder()
                .address(registryAddress.toBech32()).outputIndex(1)
                .amount(List.of(
                        TxContentOutputAmount.builder().unit("lovelace").quantity("5000000").build(),
                        TxContentOutputAmount.builder().unit(REGISTRY_NODE_CS).quantity("1").build()))
                .inlineDatum("00")
                .build();
        return TxContentUtxo.builder().outputs(List.of(coordination, originNode)).build();
    }
}
