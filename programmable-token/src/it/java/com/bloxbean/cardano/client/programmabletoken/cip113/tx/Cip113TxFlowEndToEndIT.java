package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
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
import java.util.Map;
import java.util.Set;

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
    void registersMintsAndTransfersAsOneFlow() throws Exception {
        Assumptions.assumeTrue(DevNet.isRunning(), "Start Yaci DevKit before running the TxFlow E2E test");

        BackendService backend = new BFBackendService(DevNet.BACKEND_URL, "Dummy");
        Network network = Networks.testnet();
        Account owner = new Account(network, SENDER_MNEMONIC);
        Address ownerAddress = new Address(owner.baseAddress());

        DevNet.reset();
        DevNet.topUp(owner.baseAddress(), 100_000L);
        DevNet.topUp(owner.baseAddress(), 100_000L);
        awaitSeedUtxos(backend, owner.baseAddress(), 2);

        Cip113Bootstrap.Deployed deployed = new Cip113Bootstrap().deploy(backend, owner, network);
        Cip113ProgrammableTokenService programmableTokens = Cip113ProgrammableTokenService.create(backend,
                Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network));
        Cip113ProtocolService protocol = programmableTokens.advanced();
        protocol.scripts().registerAll(deployed.appliedScripts());
        protocol.scripts().register(TxPlanSubstandardScripts.SCRIPT);
        Result<Cip113Deployment> deployment = protocol.resolveDeployment();
        assertThat(deployment.isSuccessful()).as("resolving the deployment: %s", deployment.getResponse()).isTrue();

        TxPlanCodec codec = programmableTokens.txPlanCodec();
        ProgrammableTokenExtension extension = programmableTokens.extension();
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("owner_address", owner.baseAddress());
        common.put("bootstrap_tx", deployed.bootstrapTxHash());

        // The logic script's stake registration is a plain core transaction outside the flow.
        Result<String> prerequisites = new QuickTxBuilder(backend).withExtension(extension)
                .compose(codec.fromYaml(loadYaml("00-deployment-prerequisites.yml"), with(common,
                        "logic_reward_address", TxPlanSubstandardScripts.rewardAddress(network).toBech32())))
                .withSigner(SignerProviders.signerFrom(owner))
                .completeAndWait(message -> log.debug("prerequisites: {}", message));
        assertThat(prerequisites.isSuccessful()).as("prerequisites: %s", prerequisites.getResponse()).isTrue();

        String policyId = protocol.derivePolicyId(TxPlanSubstandardScripts.credential()).getValue();
        String unit = policyId + ASSET_NAME_HEX;

        TxFlow flow = TxFlow.builder("cip113-lifecycle")
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .chainingMode(ChainingMode.SEQUENTIAL)
                        .confirmationConfig(ConfirmationConfig.builder().minConfirmations(1)
                                .checkInterval(Duration.ofSeconds(1)).timeout(Duration.ofMinutes(2)).build())
                        .build())
                .addStep(FlowStep.builder("register")
                        .withTxPlan(plan(codec, "01-register-and-initial-mint.yml",
                                with(common, "logic_script_hash", TxPlanSubstandardScripts.scriptHash())))
                        .build())
                .addStep(FlowStep.builder("mint").needs("register")
                        .withTxPlan(plan(codec, "02-mint.yml", with(common, "policy_id", policyId)))
                        .build())
                .addStep(FlowStep.builder("transfer").needs("mint")
                        .withTxPlan(plan(codec, "03-transfer.yml", with(common, "token_unit", unit)))
                        .build())
                .build();

        FlowEngine engine = FlowEngine.builder(backend)
                .executor(Runnable::run)
                .account(OWNER_REF, owner)
                .extension(extension)
                .build();
        Set<String> smartWalletBefore = smartWalletUtxos(protocol, ownerAddress);
        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(flow)
                .executionId("cip113-lifecycle-" + System.nanoTime()).build()).await();

        assertThat(result.state()).as("flow result: %s", result).isEqualTo(FlowExecutionState.COMPLETED);
        log.info("CIP-113 TxFlow completed with fingerprint {}", result.definitionFingerprint());
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(INITIAL_MINT.add(SECOND_MINT));
        assertThat(smartWalletUtxos(protocol, ownerAddress))
                .as("the transfer step must consume a smart-wallet UTxO")
                .isNotEqualTo(smartWalletBefore);
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
