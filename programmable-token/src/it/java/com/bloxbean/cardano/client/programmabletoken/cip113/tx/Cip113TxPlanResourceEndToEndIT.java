package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
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
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Executes the user-facing CIP-113 lifecycle from versioned TxPlan YAML resources. */
@ResourceLock("yaci-devkit")
class Cip113TxPlanResourceEndToEndIT {
    private static final Logger log = LoggerFactory.getLogger(
            Cip113TxPlanResourceEndToEndIT.class);
    private static final String RESOURCE_ROOT = "/txplan/cip113/";
    private static final String ASSET_NAME_HEX = "5478506c616e546f6b656e";
    private static final BigInteger INITIAL_MINT = BigInteger.valueOf(50);
    private static final BigInteger SECOND_MINT = BigInteger.valueOf(100);
    private static final String SENDER_MNEMONIC =
            "test test test test test test test test test test test test"
                    + " test test test test test test test test test test test sauce";

    @Test
    void executesRegistrationMintTransferAndBurnFromYamlResources() throws Exception {
        Assumptions.assumeTrue(DevNet.isRunning(),
                "Start Yaci DevKit before running the resource-driven TxPlan E2E test");

        BackendService backend = new BFBackendService(DevNet.BACKEND_URL, "Dummy");
        Network network = Networks.testnet();
        Account owner = new Account(network, SENDER_MNEMONIC);
        Address ownerAddress = new Address(owner.baseAddress());

        DevNet.reset();
        DevNet.topUp(owner.baseAddress(), 100_000L);
        DevNet.topUp(owner.baseAddress(), 100_000L);
        awaitSeedUtxos(backend, owner.baseAddress(), 2);

        // Protocol bootstrap is deliberately not a programmable-token intent. The portable plans
        // below bind to its transaction through extension deployment metadata.
        Cip113Bootstrap.Deployed deployed = new Cip113Bootstrap().deploy(backend, owner, network);
        Cip113ProgrammableTokenService programmableTokens =
                Cip113ProgrammableTokenService.create(backend,
                        Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network));
        Cip113ProtocolService protocol = programmableTokens.advanced();
        protocol.scripts().registerAll(deployed.appliedScripts());
        protocol.scripts().register(TxPlanSubstandardScripts.SCRIPT);

        Result<Cip113Deployment> deployment = protocol.resolveDeployment();
        assertThat(deployment.isSuccessful())
                .as("resolving the YAML test deployment: %s", deployment.getResponse()).isTrue();

        TxPlanCodec codec = programmableTokens.txPlanCodec();
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("owner_address", owner.baseAddress());
        common.put("bootstrap_tx", deployed.bootstrapTxHash());

        Map<String, Object> prerequisites = with(common,
                "logic_reward_address", TxPlanSubstandardScripts.rewardAddress(network).toBech32());
        submit("00-deployment-prerequisites.yml", prerequisites, codec,
                programmableTokens.extension(), backend, owner);

        Result<String> policyResult = protocol.derivePolicyId(TxPlanSubstandardScripts.credential());
        assertThat(policyResult.isSuccessful())
                .as("deriving YAML lifecycle policy: %s", policyResult.getResponse()).isTrue();
        String policyId = policyResult.getValue();
        String unit = policyId + ASSET_NAME_HEX;

        Map<String, Object> registration = with(common,
                "logic_script_hash", TxPlanSubstandardScripts.scriptHash());
        submit("01-register-and-initial-mint.yml", registration, codec,
                programmableTokens.extension(), backend, owner);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(INITIAL_MINT);

        submit("02-mint.yml", with(common, "policy_id", policyId), codec,
                programmableTokens.extension(), backend, owner);
        BigInteger afterMint = INITIAL_MINT.add(SECOND_MINT);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(afterMint);

        Set<String> beforeTransfer = smartWalletUtxos(protocol, ownerAddress);
        submit("03-transfer.yml", with(common, "token_unit", unit), codec,
                programmableTokens.extension(), backend, owner);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(afterMint);
        assertThat(smartWalletUtxos(protocol, ownerAddress))
                .as("the YAML transfer must consume a smart-wallet UTxO")
                .isNotEqualTo(beforeTransfer);

        submit("04-burn.yml", with(common, "policy_id", policyId), codec,
                programmableTokens.extension(), backend, owner);
        assertThat(quantity(protocol, ownerAddress, unit)).isZero();
    }

    private static void submit(String resource, Map<String, Object> variables,
                               TxPlanCodec codec, ProgrammableTokenExtension extension,
                               BackendService backend, Account signer) {
        TxPlan plan = codec.fromYaml(loadYaml(resource), variables);
        assertThat(plan.getVariables()).containsAllEntriesOf(variables);
        assertThat(plan.getTxs()).isNotEmpty().allSatisfy(tx ->
                assertThat(tx).isExactlyInstanceOf(Tx.class));

        Result<String> result = new QuickTxBuilder(backend)
                .withExtension(extension)
                .compose(plan)
                .withSigner(SignerProviders.signerFrom(signer))
                .withTxEvaluator(new AikenTransactionEvaluator(backend))
                .completeAndWait(message -> log.debug("{}: {}", resource, message));
        assertThat(result.isSuccessful())
                .as("submitting %s: %s", resource, result.getResponse()).isTrue();
    }

    private static String loadYaml(String resource) {
        try (InputStream input = Cip113TxPlanResourceEndToEndIT.class
                .getResourceAsStream(RESOURCE_ROOT + resource)) {
            if (input == null) throw new IllegalStateException("Missing TxPlan resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load TxPlan resource " + resource, e);
        }
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        result.put(key, value);
        return result;
    }

    private static BigInteger quantity(Cip113ProtocolService protocol, Address owner, String unit) {
        Result<List<Amount>> balance = protocol.getProgrammableBalance(owner);
        assertThat(balance.isSuccessful()).as("programmable balance: %s", balance.getResponse())
                .isTrue();
        return balance.getValue().stream()
                .filter(amount -> unit.equals(amount.getUnit()))
                .map(Amount::getQuantity)
                .findFirst().orElse(BigInteger.ZERO);
    }

    private static Set<String> smartWalletUtxos(Cip113ProtocolService protocol, Address owner) {
        Result<List<Utxo>> result = protocol.getUtxos(owner);
        assertThat(result.isSuccessful()).as("smart-wallet UTxOs: %s", result.getResponse()).isTrue();
        Set<String> references = new java.util.LinkedHashSet<>();
        result.getValue().forEach(utxo -> references.add(
                utxo.getTxHash() + "#" + utxo.getOutputIndex()));
        return references;
    }

    private static void awaitSeedUtxos(BackendService backend, String address, int required) {
        DefaultUtxoSupplier supplier = new DefaultUtxoSupplier(backend.getUtxoService());
        long deadline = System.currentTimeMillis() + 60_000L;
        int seen = 0;
        while (System.currentTimeMillis() < deadline) {
            seen = (int) supplier.getAll(address).stream()
                    .filter(utxo -> utxo.getAmount().stream().anyMatch(amount ->
                            "lovelace".equals(amount.getUnit())
                                    && amount.getQuantity().compareTo(
                                    BigInteger.valueOf(100_000_000L)) > 0))
                    .count();
            if (seen >= required) return;
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for DevKit funding", e);
            }
        }
        throw new IllegalStateException("Expected " + required + " funded UTxOs, found " + seen);
    }
}
