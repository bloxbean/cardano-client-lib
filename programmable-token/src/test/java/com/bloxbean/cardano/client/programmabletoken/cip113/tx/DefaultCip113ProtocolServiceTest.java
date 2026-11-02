package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.ProgrammableLogicGlobalParams;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
    /** The registry's mint and spend share one script in v0.0.1, so its nodes sit at its policy. */
    private static final String REGISTRY_SPEND_HASH = REGISTRY_NODE_CS;
    /** protocol_params' mint and spend share one script: the coordination UTxO sits at its policy. */
    private static final String COORDINATION_SPEND_HASH = PARAMS_POLICY;
    private static final String TEMPLATE_POLICY = "44".repeat(28);
    private static final String PLG_HASH = "55".repeat(28);
    private static final String ISSUANCE_LOGIC_HASH = "56".repeat(28);
    private static final String TRANSFER_HASH = "66".repeat(28);
    private static final String THIRD_PARTY_HASH = "77".repeat(28);
    private static final String UPGRADE_HASH = "99".repeat(28);
    private static final String PARAMS_UNIT = PARAMS_POLICY + HexUtil.encodeHexString(
            Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME.getBytes(StandardCharsets.UTF_8));

    private final Address coordinationAddress = AddressProvider.getEntAddress(
            Credential.fromScript(COORDINATION_SPEND_HASH), Networks.testnet());
    private final Address registryAddress = AddressProvider.getEntAddress(
            Credential.fromScript(REGISTRY_SPEND_HASH), Networks.testnet());
    private final Address refScriptsAddress = AddressProvider.getEntAddress(
            Credential.fromKey("bb".repeat(28)), Networks.testnet());

    /**
     * A complete v0.0.1 description: the chain cannot supply the base script, registry or bounds.
     * The transfer delegate is published as a reference script by the bootstrap tx, output #2.
     */
    private static final Cip113Deployment DEPLOYMENT = Cip113Deployment.builder()
            .network(Networks.testnet())
            .bootstrapTxHash(BOOTSTRAP_TX)
            .paramsPolicy(PARAMS_POLICY)
            .programmableLogicBaseHash(PLB_HASH)
            .programmableLogicGlobalHash(PLG_HASH)
            .issuanceLogicHash(ISSUANCE_LOGIC_HASH)
            .transferScriptHash(TRANSFER_HASH)
            .thirdPartyScriptHash(THIRD_PARTY_HASH)
            .unfrackingScriptHash("88".repeat(28))
            .upgradeScriptHash(UPGRADE_HASH)
            .registryNodeCs(REGISTRY_NODE_CS)
            .issuanceCborHexCs(TEMPLATE_POLICY)
            .maxInlineDatumBytes(2048)
            .referenceScript(TRANSFER_HASH,
                    TransactionInput.builder().transactionId(BOOTSTRAP_TX).index(2).build())
            .build();

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
        return new DefaultCip113ProtocolService(backend, DEPLOYMENT);
    }

    @Test
    void concurrentFirstCallersShareOneResolution() throws Exception {
        bootstrapReads(300L, Result.success("ok").withValue(bootstrap()));
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
        DefaultCip113ProtocolService service = service();

        for (CompletableFuture<Utxo> caller : twoConcurrentCallers(service)) {
            assertThat(caller.join().getTxHash()).isEqualTo(BOOTSTRAP_TX);
        }
        assertThat(bootstrapReads).as("one chain walk for both callers").hasValue(1);
        assertThat(service.deployment().getProgrammableLogicBaseHash()).isEqualTo(PLB_HASH);
        assertThat(service.deployment().getRegistryNodeCs()).isEqualTo(REGISTRY_SPEND_HASH);
    }

    @Test
    void concurrentFirstCallersShareTheSameFailureAndALaterCallRetries() throws Exception {
        when(transactionService.getTransactionUtxos(BOOTSTRAP_TX)).thenAnswer(invocation -> {
            int attempt = bootstrapReads.incrementAndGet();
            Thread.sleep(300L);
            return attempt == 1 ? Result.error("backend down") : Result.success("ok").withValue(bootstrap());
        });
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
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
                .contains(coordinationAddress.toBech32());
    }

    @Test
    void theBootstrapOutputIsUsedOnlyAfterPositiveVerification() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        when(assetService.getAllAssetAddresses(PARAMS_UNIT)).thenReturn(Result.error("unsupported"));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));

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
        unspentAt(coordinationAddress, coordinationUtxo(upgradeTx, "ee".repeat(28)));

        DefaultCip113ProtocolService service = service();

        assertThat(service.coordinationUtxo().getTxHash()).isEqualTo(upgradeTx);
        assertThat(service.deployment().getTransferScriptHash()).isEqualTo("ee".repeat(28));
    }

    @Test
    void anExplicitResolutionRefreshesThePublishedState() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
        DefaultCip113ProtocolService service = service();
        service.coordinationUtxo();

        unspentAt(coordinationAddress, coordinationUtxo("ef".repeat(32), "ee".repeat(28)));
        Result<Cip113Deployment> refreshed = service.resolveDeployment();

        assertThat(refreshed.isSuccessful()).as(refreshed.getResponse()).isTrue();
        assertThat(service.coordinationUtxo().getTxHash()).isEqualTo("ef".repeat(32));
        assertThat(service.deployment().getTransferScriptHash()).isEqualTo("ee".repeat(28));
        assertThat(bootstrapReads).hasValue(2);
    }

    /**
     * The bootstrap's reference-script output, once spent, is never assumed live: resolution
     * succeeds, but the script is not recorded as published, so a transaction carries it as a
     * witness instead of referencing a spent output.
     */
    @Test
    void aSpentReferenceScriptOutputIsNeverAssumedLive() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));

        DefaultCip113ProtocolService service = service();
        Result<Cip113Deployment> result = service.resolveDeployment();

        assertThat(result.isSuccessful()).as(result.getResponse()).isTrue();
        assertThat(service.scripts().publishedAt(TRANSFER_HASH)).isEmpty();
    }

    @Test
    void aReferenceScriptIsUsedOnlyAfterPositiveVerification() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
        unspentAt(refScriptsAddress, publishedTransfer());

        DefaultCip113ProtocolService service = service();

        assertThat(service.coordinationUtxo().getTxHash()).isEqualTo(BOOTSTRAP_TX);
        assertThat(service.scripts().publishedAt(TRANSFER_HASH)).contains(publishedTransfer());
    }

    /**
     * Re-resolving replaces the published set: an outref spent since the last resolution stops
     * being referenced, and a failed resolution leaves the last good set untouched.
     */
    @Test
    void reResolutionDropsAReferenceScriptSpentSinceAndAFailureLeavesTheLastGoodSet() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
        unspentAt(refScriptsAddress, publishedTransfer());
        DefaultCip113ProtocolService service = service();
        assertThat(service.resolveDeployment().isSuccessful()).isTrue();
        assertThat(service.scripts().publishedAt(TRANSFER_HASH)).contains(publishedTransfer());

        when(utxoService.getUtxos(eq(coordinationAddress.toBech32()), anyInt(), eq(1), any()))
                .thenReturn(Result.success("ok").withValue(List.of()));
        assertThat(service.resolveDeployment().isSuccessful()).isFalse();
        assertThat(service.scripts().publishedAt(TRANSFER_HASH)).as("last good set kept")
                .contains(publishedTransfer());

        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
        when(utxoService.getUtxos(eq(refScriptsAddress.toBech32()), anyInt(), eq(1), any()))
                .thenReturn(Result.success("ok").withValue(List.of()));
        assertThat(service.resolveDeployment().isSuccessful()).isTrue();
        assertThat(service.scripts().publishedAt(TRANSFER_HASH)).isEmpty();
    }

    /** A reference-script outref publishing some other script means the description is wrong. */
    @Test
    void aReferenceScriptOutrefPublishingAnotherScriptFailsResolution() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap("cc".repeat(28))));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));

        Result<Cip113Deployment> result = service().resolveDeployment();

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getResponse())
                .contains(BOOTSTRAP_TX + "#2")
                .contains("cc".repeat(28))
                .contains(TRANSFER_HASH);
    }

    /** A backend that does not report reference-script hashes falls back to witnesses. */
    @Test
    void aBackendThatReportsNoReferenceScriptHashFallsBackToWitnesses() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap(null)));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));

        DefaultCip113ProtocolService service = service();
        Result<Cip113Deployment> result = service.resolveDeployment();

        assertThat(result.isSuccessful()).as(result.getResponse()).isTrue();
        assertThat(service.scripts().publishedAt(TRANSFER_HASH)).isEmpty();
    }

    /** A description missing a compile-time parameter cannot be completed from chain. */
    @Test
    void anIncompleteDescriptionIsRefusedBeforeChainAccess() throws Exception {
        DefaultCip113ProtocolService service = new DefaultCip113ProtocolService(backend,
                DEPLOYMENT.toBuilder().programmableLogicBaseHash(null).registryNodeCs(null).build());

        Result<Cip113Deployment> result = service.resolveDeployment();

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getResponse())
                .contains("programmableLogicBaseHash")
                .contains("registryNodeCs");
        verify(transactionService, never()).getTransactionUtxos(anyString());
    }

    /** A described template outref is read directly — no asset-holder index needed. */
    @Test
    void aDescribedIssuanceTemplateIsReadFromItsOutref() throws Exception {
        String genesisTx = "ad".repeat(32);
        Address alwaysFail = AddressProvider.getEntAddress(Credential.fromScript("ce".repeat(28)), Networks.testnet());
        String templateUnit = TEMPLATE_POLICY + HexUtil.encodeHexString(
                Cip113Deployment.ISSUANCE_CBOR_HEX_ASSET_NAME.getBytes(StandardCharsets.UTF_8));
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        when(transactionService.getTransactionUtxos(genesisTx)).thenReturn(Result.success("ok")
                .withValue(TxContentUtxo.builder().outputs(List.of(TxContentUtxoOutputs.builder()
                        .address(alwaysFail.toBech32()).outputIndex(2)
                        .amount(List.of(TxContentOutputAmount.builder().unit(templateUnit).quantity("1").build()))
                        .build())).build()));
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
        Utxo template = Utxo.builder().txHash(genesisTx).outputIndex(2).address(alwaysFail.toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(10_000_000L)),
                        Amount.builder().unit(templateUnit).quantity(BigInteger.ONE).build()))
                .build();

        DefaultCip113ProtocolService described = new DefaultCip113ProtocolService(backend, DEPLOYMENT.toBuilder()
                .issuanceTemplateRef(TransactionInput.builder().transactionId(genesisTx).index(2).build()).build());

        Result<Cip113Deployment> missing = described.resolveDeployment();
        assertThat(missing.isSuccessful()).as("an outref that is not an unspent template output").isFalse();
        assertThat(missing.getResponse()).contains(genesisTx + "#2").contains("is not an unspent");

        unspentAt(alwaysFail, template);
        assertThat(described.issuanceTemplateUtxo()).isEqualTo(template);
        verify(assetService, never()).getAllAssetAddresses(templateUnit);
    }

    // ------------------------------------------------------ programmable balance

    /**
     * A registered policy's empty asset name is a 56-character unit. The builder transfers and
     * burns it like any other asset, so the programmable balance has to report it too.
     */
    @Test
    void programmableBalanceIncludesARegisteredPolicysEmptyAssetName() {
        String policy = "5f".repeat(28);
        String unregistered = "6f".repeat(28);
        Address owner = AddressProvider.getEntAddress(Credential.fromKey("22".repeat(28)), Networks.testnet());
        List<Amount> held = List.of(
                Amount.lovelace(BigInteger.valueOf(2_000_000L)),
                Amount.builder().unit(policy).quantity(BigInteger.valueOf(7)).build(),
                Amount.builder().unit(policy + "546f6b").quantity(BigInteger.valueOf(3)).build(),
                Amount.builder().unit(unregistered).quantity(BigInteger.valueOf(4)).build());
        Utxo nodeUtxo = Utxo.builder().txHash("cd".repeat(32)).outputIndex(0)
                .address(registryAddress.toBech32()).amount(List.of()).build();
        RegistryLookup registry = mock(RegistryLookup.class);
        when(registry.all()).thenReturn(List.of(new RegistryLookup.RegistryNodeUtxo(nodeUtxo,
                RegistryNode.builder().key(policy).next("ff".repeat(28)).build())));

        DefaultCip113ProtocolService service = spy(service());
        doReturn(Result.success("OK").withValue(held)).when(service).getBalance(owner);
        doReturn(registry).when(service).registryLookup();

        Result<List<Amount>> balance = service.getProgrammableBalance(owner);

        assertThat(balance.isSuccessful()).as(balance.getResponse()).isTrue();
        assertThat(balance.getValue()).extracting(Amount::getUnit, Amount::getQuantity).containsExactly(
                tuple(policy, BigInteger.valueOf(7)),
                tuple(policy + "546f6b", BigInteger.valueOf(3)));
    }

    // ------------------------------------------------- plan metadata pinning

    @Test
    void planMetadataIsValidatedAgainstTheConfiguredDeploymentBeforeChainAccess() {
        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(DEPLOYMENT);
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

    @Test
    void planMetadataPinsTheParamsPolicyAndContractVersion() {
        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(DEPLOYMENT);
        Cip113Protocol protocol = new Cip113Protocol(service);

        assertThatCode(() -> protocol.validateMetadata(metadata(Cip113Protocol.CONTRACT_VERSION,
                Map.of("params_policy", PARAMS_POLICY.toUpperCase()))))
                .doesNotThrowAnyException();
        ExtensionMetadata otherPolicy = metadata(null, Map.of("params_policy", "fe".repeat(28)));
        assertThatThrownBy(() -> protocol.validateMetadata(otherPolicy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("params_policy");
        ExtensionMetadata malformedPolicy = metadata(null, Map.of("params_policy", "short"));
        assertThatThrownBy(() -> protocol.validateMetadata(malformedPolicy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("28-byte policy id");

        // A plan persisted against the 0.8.0-pre6 adapter: alpha.2 contracts, the old Preview
        // deployment. It describes transaction shapes v0.0.1 rejects, so it is refused outright.
        ExtensionMetadata alpha2Plan = metadata("0.5.0-alpha.2", Map.of(
                "network", 0,
                "bootstrap_tx", "a432339cbd7318222c8c51ed4fb52ee4c68f676037622aa7361dd45d897324a4"));
        assertThatThrownBy(() -> protocol.validateMetadata(alpha2Plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.5.0-alpha.2")
                .hasMessageContaining(Cip113Protocol.CONTRACT_VERSION);

        verify(service, never()).resolveDeployment();
        verify(service, never()).coordinationUtxo();
    }

    /**
     * A custom deployment need not name a bootstrap transaction, so the network alone cannot tell
     * two deployments on one network apart. A plan the service wrote for one must be refused by a
     * service configured for the other, or its register and policy_ref mint would target it.
     */
    @Test
    void aServiceGeneratedPlanIsRefusedByAnotherDeploymentOnTheSameNetwork() {
        Cip113Deployment first = DEPLOYMENT.toBuilder().bootstrapTxHash(null).build();
        Cip113Deployment second = first.toBuilder().paramsPolicy("12".repeat(28)).build();
        Cip113ProgrammableTokenService firstTokens = serviceFor(first);
        Cip113ProgrammableTokenService secondTokens = serviceFor(second);

        ProgrammableTokenTx tx = new ProgrammableTokenTx().transfer(coordinationAddress.toBech32(),
                Amount.asset(PLB_HASH + "00", 1), PlutusData.unit());
        String yaml = firstTokens.txPlanCodec().toYaml(firstTokens.extension().configure(TxPlan.from(tx)));
        ExtensionMetadata replayed = firstTokens.txPlanCodec().fromYaml(yaml).getExtensions().get("pt");

        assertThat(replayed.getDeployment()).containsEntry("params_policy", PARAMS_POLICY);
        assertThatThrownBy(() -> secondTokens.txPlanCodec().fromYaml(yaml))
                .as("the other deployment's codec refuses the plan while reading it")
                .hasStackTraceContaining("params_policy " + PARAMS_POLICY);
        var secondExtension = secondTokens.extension();
        assertThatThrownBy(() -> secondExtension.validateMetadata(replayed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("params_policy " + PARAMS_POLICY)
                .hasMessageContaining("12".repeat(28));
        assertThatThrownBy(() -> secondExtension.newBuildExtension(replayed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("params_policy");
    }

    private static Cip113ProgrammableTokenService serviceFor(Cip113Deployment deployment) {
        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(deployment);
        return new Cip113ProgrammableTokenService(service);
    }

    // ------------------------------------------------------------- fixtures

    /**
     * A build reads the coordination UTxO through its own supplier (contract C1): it picks the
     * replacement an earlier step created, and fails closed when it sees none or sees other
     * protocol parameters than the deployment was resolved from.
     */
    @Test
    void aBuildReadsTheCoordinationUtxoThroughItsSupplier() throws Exception {
        bootstrapReads(0L, Result.success("ok").withValue(bootstrap()));
        holderOfParamsNft(coordinationAddress);
        unspentAt(coordinationAddress, coordinationUtxo(BOOTSTRAP_TX, TRANSFER_HASH));
        DefaultCip113ProtocolService service = service();
        String replacementTx = "cd".repeat(32);

        UtxoSupplier recreated = mock(UtxoSupplier.class);
        when(recreated.getAll(coordinationAddress.toBech32())).thenReturn(List.of(coordinationUtxo(replacementTx, TRANSFER_HASH)));
        assertThat(service.coordinationUtxo(recreated).getTxHash()).isEqualTo(replacementTx);

        UtxoSupplier spent = mock(UtxoSupplier.class);
        when(spent.getAll(coordinationAddress.toBech32())).thenReturn(List.of());
        assertThatThrownBy(() -> service.coordinationUtxo(spent))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("is visible at " + coordinationAddress.toBech32());

        UtxoSupplier upgraded = mock(UtxoSupplier.class);
        when(upgraded.getAll(coordinationAddress.toBech32())).thenReturn(List.of(coordinationUtxo(replacementTx, "ee".repeat(28))));
        assertThatThrownBy(() -> service.coordinationUtxo(upgraded))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("other protocol parameters");
    }

    /** The global-state holder's UTxOs are read through the build's supplier too. */
    @Test
    void aBuildReadsTheGlobalStateUtxoThroughItsSupplier() throws Exception {
        String globalStateCs = "99".repeat(28);
        String unit = globalStateCs + "aa";
        PolicyAsset asset = new PolicyAsset();
        asset.setAsset(unit);
        when(assetService.getAllPolicyAssets(globalStateCs)).thenReturn(Result.success("ok").withValue(List.of(asset)));
        when(assetService.getAllAssetAddresses(unit)).thenReturn(Result.success("ok")
                .withValue(List.of(AssetAddress.builder().address(registryAddress.toBech32()).quantity("1").build())));
        Utxo state = Utxo.builder().txHash("ee".repeat(32)).outputIndex(0).address(registryAddress.toBech32())
                .amount(List.of(Amount.builder().unit(unit).quantity(BigInteger.ONE).build())).build();
        UtxoSupplier buildSupplier = mock(UtxoSupplier.class);
        when(buildSupplier.getAll(registryAddress.toBech32())).thenReturn(List.of(state));

        DefaultCip113ProtocolService service = service();

        assertThat(service.globalStateUtxo(globalStateCs, buildSupplier)).isEqualTo(state);
        assertThat(service.globalStateUtxo(globalStateCs)).as("the backend sees no such output").isNull();
    }

    private static ExtensionMetadata metadata(Map<String, Object> deployment) {
        return metadata(null, deployment);
    }

    private static ExtensionMetadata metadata(String contractVersion, Map<String, Object> deployment) {
        return ExtensionMetadata.builder()
                .extension("programmable-token").schemaVersion("1").protocol(Cip113Protocol.ID)
                .contractVersion(contractVersion)
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

    private Utxo coordinationUtxo(String txHash, String transferHash) {
        return Utxo.builder()
                .txHash(txHash).outputIndex(0)
                .address(coordinationAddress.toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)),
                        Amount.builder().unit(PARAMS_UNIT).quantity(BigInteger.ONE).build()))
                .inlineDatum(coordinationDatum(transferHash).serializeToHex())
                .build();
    }

    private static PlutusData coordinationDatum(String transferHash) {
        return ProgrammableLogicGlobalParams.builder()
                .programmableLogicGlobalCred(Credential.fromScript(PLG_HASH))
                .issuanceLogicCred(Credential.fromScript(ISSUANCE_LOGIC_HASH))
                .transferCred(Credential.fromScript(transferHash))
                .thirdPartyCred(Credential.fromScript(THIRD_PARTY_HASH))
                .upgradeCred(Credential.fromScript(UPGRADE_HASH))
                .build()
                .toPlutusData();
    }

    /** The transfer delegate's reference-script output, as an unspent UTxO. */
    private Utxo publishedTransfer() {
        return Utxo.builder().txHash(BOOTSTRAP_TX).outputIndex(2)
                .address(refScriptsAddress.toBech32()).referenceScriptHash(TRANSFER_HASH)
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(20_000_000L)))).build();
    }

    private TxContentUtxo bootstrap() {
        return bootstrap(TRANSFER_HASH);
    }

    /**
     * The bootstrap transaction: coordination output #0, the origin registry node #1 and the
     * transfer delegate's reference script #2 (reported as {@code referenceScriptHash}).
     */
    private TxContentUtxo bootstrap(String referenceScriptHash) {
        TxContentUtxoOutputs coordination = TxContentUtxoOutputs.builder()
                .address(coordinationAddress.toBech32()).outputIndex(0)
                .amount(List.of(
                        TxContentOutputAmount.builder().unit("lovelace").quantity("5000000").build(),
                        TxContentOutputAmount.builder().unit(PARAMS_UNIT).quantity("1").build()))
                .inlineDatum(coordinationDatum(TRANSFER_HASH).serializeToHex())
                .build();
        TxContentUtxoOutputs originNode = TxContentUtxoOutputs.builder()
                .address(registryAddress.toBech32()).outputIndex(1)
                .amount(List.of(
                        TxContentOutputAmount.builder().unit("lovelace").quantity("5000000").build(),
                        TxContentOutputAmount.builder().unit(REGISTRY_NODE_CS).quantity("1").build()))
                .inlineDatum("00")
                .build();
        TxContentUtxoOutputs transferScript = TxContentUtxoOutputs.builder()
                .address(refScriptsAddress.toBech32()).outputIndex(2)
                .amount(List.of(TxContentOutputAmount.builder().unit("lovelace").quantity("20000000").build()))
                .referenceScriptHash(referenceScriptHash)
                .build();
        return TxContentUtxo.builder().outputs(List.of(coordination, originNode, transferScript)).build();
    }
}
