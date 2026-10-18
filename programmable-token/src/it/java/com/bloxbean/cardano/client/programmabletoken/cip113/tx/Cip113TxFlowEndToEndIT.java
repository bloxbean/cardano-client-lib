package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultScriptSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.Cip113TxPlanResourceEndToEndIT.awaitSeedUtxos;
import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.Cip113TxPlanResourceEndToEndIT.loadYaml;
import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.Cip113TxPlanResourceEndToEndIT.quantity;
import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.Cip113TxPlanResourceEndToEndIT.smartWalletUtxos;
import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.Cip113TxPlanResourceEndToEndIT.with;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs register, mint and transfer as one SEQUENTIAL TxFlow on a FlowEngine with the
 * programmable-token extension registered (ADR 0008 M1). TxFlow has no CIP-113 code: the plans
 * are the same TxPlan resources the standalone lifecycle test submits with QuickTxBuilder.
 */
@ResourceLock("yaci-devkit")
class Cip113TxFlowEndToEndIT {
    private static final Logger log = LoggerFactory.getLogger(Cip113TxFlowEndToEndIT.class);
    private static final String ASSET_NAME_HEX = "5478506c616e546f6b656e";
    private static final BigInteger INITIAL_MINT = BigInteger.valueOf(50);
    private static final BigInteger SECOND_MINT = BigInteger.valueOf(100);
    private static final String OWNER_REF = "account://owner";
    private static final String SENDER_MNEMONIC =
            "test test test test test test test test test test test test"
                    + " test test test test test test test test test test test sauce";

    @Test
    void registersMintsAndTransfersAsOneSequentialFlow() throws Exception {
        Lifecycle lifecycle = deploy();
        FlowExecutionResult result = run(lifecycle, ChainingMode.SEQUENTIAL, false);

        assertThat(result.state()).as("flow result: %s", result).isEqualTo(FlowExecutionState.COMPLETED);
        lifecycle.assertMintedAndTransferred();
    }

    /**
     * PIPELINED: mint and transfer build before the previous step confirms. Their funding edges
     * expose the pending registry node, change and smart-wallet outputs to the build's supplier,
     * which the extension reads the registry through (ADR 0008 M1b, D9a).
     */
    @Test
    void registersMintsAndTransfersAsOnePipelinedFlowAlongFundingEdges() throws Exception {
        Lifecycle lifecycle = deploy();
        FlowExecutionResult result = run(lifecycle, ChainingMode.PIPELINED, true);

        assertThat(result.state()).as("flow result: %s", result).isEqualTo(FlowExecutionState.COMPLETED);
        lifecycle.assertMintedAndTransferred();
    }

    /**
     * Without funding edges the pending registration is invisible to the mint, which fails while
     * building instead of minting against stale registry state. The register step was already
     * submitted, so this is a runtime build failure, not a pre-submission diagnostic. The engine's
     * backend view holds back the flow's own outputs, so the registration stays pending however
     * fast the devnet confirms it.
     */
    @Test
    void aPipelinedMintWithoutAFundingEdgeFailsClosed() throws Exception {
        Lifecycle lifecycle = deploy();
        FlowExecutionResult result = run(lifecycle, ChainingMode.PIPELINED, false,
                engineHidingSubmittedOutputs(lifecycle.backend));

        assertThat(result.state()).as("flow result: %s", result).isNotEqualTo(FlowExecutionState.COMPLETED);
        assertThat(result.steps()).anySatisfy(step -> {
            assertThat(step.getStepId()).isEqualTo("register");
            assertThat(step.getTransactionHash()).isNotNull();
        });
        assertThat(result.steps()).anySatisfy(step -> {
            assertThat(step.getStepId()).isEqualTo("mint");
            assertThat(step.isSuccessful()).isFalse();
            assertThat(step.getTransactionHash()).isNull();
        });
        assertThat(String.valueOf(result.error())).contains("is not registered");
        log.info("needs-only mint failed as expected: {}", result.error());
    }

    private FlowExecutionResult run(Lifecycle lifecycle, ChainingMode mode, boolean fundingEdges) {
        return run(lifecycle, mode, fundingEdges, FlowEngine.builder(lifecycle.backend));
    }

    private FlowExecutionResult run(Lifecycle lifecycle, ChainingMode mode, boolean fundingEdges,
                                    FlowEngine.Builder builder) {
        FlowStep.Builder mint = FlowStep.builder("mint").needs("register")
                .withTxPlan(plan(lifecycle.codec, "02-mint.yml", with(lifecycle.common, "policy_id", lifecycle.policyId)));
        FlowStep.Builder transfer = FlowStep.builder("transfer").needs("mint")
                .withTxPlan(plan(lifecycle.codec, "03-transfer.yml", with(lifecycle.common, "token_unit", lifecycle.unit)));
        if (fundingEdges) {
            // A funding edge per producer whose pending outputs the step reads: the transfer
            // needs the registry node from "register" and the minted holdings from "mint".
            mint.fundsFrom("register");
            transfer.fundsFrom("register").fundsFrom("mint");
        }
        TxFlow flow = TxFlow.builder("cip113-lifecycle")
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .chainingMode(mode)
                        .confirmationConfig(ConfirmationConfig.builder().minConfirmations(1)
                                .checkInterval(Duration.ofSeconds(1)).timeout(Duration.ofMinutes(2)).build())
                        .build())
                .addStep(FlowStep.builder("register")
                        .withTxPlan(plan(lifecycle.codec, "01-register-and-initial-mint.yml",
                                with(lifecycle.common, "logic_script_hash", TxPlanSubstandardScripts.scriptHash())))
                        .build())
                .addStep(mint.build())
                .addStep(transfer.build())
                .build();

        builder.executor(Runnable::run)
                .account(OWNER_REF, lifecycle.owner)
                .extension(lifecycle.extension);
        if (mode != ChainingMode.SEQUENTIAL) {
            // The backend evaluator knows only confirmed UTxOs; a step spending an earlier step's
            // pending output is evaluated locally over the step's flow-aware supplier.
            ScriptSupplier scripts = new DefaultScriptSupplier(lifecycle.backend.getScriptService());
            builder.txEvaluator((utxos, params) -> new AikenTransactionEvaluator(utxos, params, scripts));
        }
        FlowEngine engine = builder.build();
        lifecycle.smartWalletBefore = smartWalletUtxos(lifecycle.protocol, lifecycle.ownerAddress);
        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(flow)
                .executionId("cip113-" + mode + "-" + System.nanoTime()).build()).await();
        log.info("CIP-113 {} TxFlow finished {} with fingerprint {}", mode, result.state(),
                result.definitionFingerprint());
        return result;
    }

    /**
     * An engine on the backend whose UTxO view never shows the outputs of a transaction it
     * submitted, as if none of them had confirmed yet.
     */
    private static FlowEngine.Builder engineHidingSubmittedOutputs(BackendService backend) {
        Set<String> submitted = ConcurrentHashMap.newKeySet();
        UtxoSupplier confirmed = new DefaultUtxoSupplier(backend.getUtxoService());
        UtxoSupplier view = new UtxoSupplier() {
            @Override
            public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
                return confirmed.getPage(address, nrOfItems, page, order).stream()
                        .filter(utxo -> !submitted.contains(utxo.getTxHash())).toList();
            }

            @Override
            public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
                return submitted.contains(txHash) ? Optional.empty() : confirmed.getTxOutput(txHash, outputIndex);
            }
        };
        TransactionProcessor processor = new DefaultTransactionProcessor(backend.getTransactionService()) {
            @Override
            public Result<String> submitTransaction(byte[] cborData) throws ApiException {
                Result<String> result = super.submitTransaction(cborData);
                if (result.isSuccessful()) submitted.add(result.getValue());
                return result;
            }
        };
        return FlowEngine.builder(view, new DefaultProtocolParamsSupplier(backend.getEpochService()), processor,
                        new DefaultChainDataSupplier(backend.getBlockService(), backend.getTransactionService()))
                .scriptSupplier(new DefaultScriptSupplier(backend.getScriptService()));
    }

    private Lifecycle deploy() throws Exception {
        Assumptions.assumeTrue(DevNet.isRunning(), "Start Yaci DevKit before running the TxFlow E2E test");

        Lifecycle lifecycle = new Lifecycle();
        lifecycle.backend = new BFBackendService(DevNet.BACKEND_URL, "Dummy");
        Network network = Networks.testnet();
        lifecycle.owner = new Account(network, SENDER_MNEMONIC);
        lifecycle.ownerAddress = new Address(lifecycle.owner.baseAddress());

        DevNet.reset();
        DevNet.topUp(lifecycle.owner.baseAddress(), 100_000L);
        DevNet.topUp(lifecycle.owner.baseAddress(), 100_000L);
        awaitSeedUtxos(lifecycle.backend, lifecycle.owner.baseAddress(), 2);

        Cip113Bootstrap.Deployed deployed = new Cip113Bootstrap().deploy(lifecycle.backend, lifecycle.owner, network);
        Cip113ProgrammableTokenService programmableTokens = Cip113ProgrammableTokenService.create(lifecycle.backend,
                Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network));
        lifecycle.protocol = programmableTokens.advanced();
        lifecycle.protocol.scripts().registerAll(deployed.appliedScripts());
        lifecycle.protocol.scripts().register(TxPlanSubstandardScripts.SCRIPT);
        Result<Cip113Deployment> deployment = lifecycle.protocol.resolveDeployment();
        assertThat(deployment.isSuccessful()).as("resolving the deployment: %s", deployment.getResponse()).isTrue();

        lifecycle.codec = programmableTokens.txPlanCodec();
        lifecycle.extension = programmableTokens.extension();
        lifecycle.common.put("owner_address", lifecycle.owner.baseAddress());
        lifecycle.common.put("bootstrap_tx", deployed.bootstrapTxHash());

        // The logic script's stake registration is a plain core transaction outside the flow.
        Result<String> prerequisites = new QuickTxBuilder(lifecycle.backend).withExtension(lifecycle.extension)
                .compose(lifecycle.codec.fromYaml(loadYaml("00-deployment-prerequisites.yml"), with(lifecycle.common,
                        "logic_reward_address", TxPlanSubstandardScripts.rewardAddress(network).toBech32())))
                .withSigner(SignerProviders.signerFrom(lifecycle.owner))
                .completeAndWait(message -> log.debug("prerequisites: {}", message));
        assertThat(prerequisites.isSuccessful()).as("prerequisites: %s", prerequisites.getResponse()).isTrue();

        lifecycle.policyId = lifecycle.protocol.derivePolicyId(TxPlanSubstandardScripts.credential()).getValue();
        lifecycle.unit = lifecycle.policyId + ASSET_NAME_HEX;
        return lifecycle;
    }

    /** One deployed CIP-113 lifecycle on a fresh devnet. */
    private static final class Lifecycle {
        BackendService backend;
        Account owner;
        Address ownerAddress;
        Cip113ProtocolService protocol;
        TxPlanCodec codec;
        ProgrammableTokenExtension extension;
        final Map<String, Object> common = new LinkedHashMap<>();
        String policyId;
        String unit;
        Set<String> smartWalletBefore;

        void assertMintedAndTransferred() {
            assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(INITIAL_MINT.add(SECOND_MINT));
            assertThat(smartWalletUtxos(protocol, ownerAddress))
                    .as("the transfer step must consume a smart-wallet UTxO")
                    .isNotEqualTo(smartWalletBefore);
        }
    }

    /**
     * A plan decoded with variables keeps them as inert data; the portable FlowEngine path requires
     * the map cleared (ADR 0008 D4a). The bound values are unaffected.
     */
    private static TxPlan plan(TxPlanCodec codec, String resource, Map<String, Object> variables) {
        TxPlan plan = codec.fromYaml(loadYaml(resource), variables);
        plan.setVariables(Map.of());
        return plan.withSigner(OWNER_REF);
    }
}
