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
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
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

/**
 * Executes the user-facing CIP-113 lifecycle from versioned TxPlan YAML resources: registration,
 * mint, transfer, burn, a cross-owner transfer with datums and variable-bearing redeemers, a
 * registry update that sets the unfracking hook, and an unfracking of a shared UTxO.
 *
 * <p>The substandard is the JuLC {@code TxPlanSubstandard}, which accepts only
 * {@code Authorization(1, 42)} and requires a {@code HolderDatum(1, 113)} in the transaction — so
 * every structured or CBOR-hex value in the plans is checked by a real validator.</p>
 */
@ResourceLock("yaci-devkit")
class Cip113TxPlanResourceEndToEndIT {
    private static final Logger log = LoggerFactory.getLogger(
            Cip113TxPlanResourceEndToEndIT.class);
    private static final String RESOURCE_ROOT = "/txplan/cip113/";
    private static final String ASSET_NAME_HEX = "5478506c616e546f6b656e";
    private static final BigInteger INITIAL_MINT = BigInteger.valueOf(50);
    private static final BigInteger SECOND_MINT = BigInteger.valueOf(100);
    private static final BigInteger CROSS_OWNER_TOTAL = BigInteger.valueOf(3);
    private static final BigInteger FRACKED_QUANTITY = BigInteger.valueOf(5);
    private static final String SENDER_MNEMONIC =
            "test test test test test test test test test test test test"
                    + " test test test test test test test test test test test sauce";

    /** {@code Authorization(1, 42)} and {@code HolderDatum(1, 113)}, as the substandard demands. */
    private static final PlutusData AUTHORIZATION = ConstrPlutusData.of(0,
            BigIntPlutusData.of(1), BigIntPlutusData.of(42));
    private static final PlutusData HOLDER_DATUM = ConstrPlutusData.of(0,
            BigIntPlutusData.of(1), BigIntPlutusData.of(113));

    @Test
    void executesTheLifecycleFromYamlResources() throws Exception {
        Assumptions.assumeTrue(DevNet.isRunning(),
                "Start Yaci DevKit before running the resource-driven TxPlan E2E test");

        BackendService backend = new BFBackendService(DevNet.BACKEND_URL, "Dummy");
        Network network = Networks.testnet();
        Account owner = new Account(network, SENDER_MNEMONIC);
        Address ownerAddress = new Address(owner.baseAddress());
        Account recipient = new Account(network);
        Address recipientAddress = new Address(recipient.baseAddress());

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
        ProgrammableTokenExtension extension = programmableTokens.extension();
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("owner_address", owner.baseAddress());
        common.put("bootstrap_tx", deployed.bootstrapTxHash());

        Map<String, Object> prerequisites = with(common,
                "logic_reward_address", TxPlanSubstandardScripts.rewardAddress(network).toBech32());
        submit("00-deployment-prerequisites.yml", prerequisites, codec, extension, backend, owner);

        Result<String> policyResult = protocol.derivePolicyId(TxPlanSubstandardScripts.credential());
        assertThat(policyResult.isSuccessful())
                .as("deriving YAML lifecycle policy: %s", policyResult.getResponse()).isTrue();
        String policyId = policyResult.getValue();
        String unit = policyId + ASSET_NAME_HEX;

        Map<String, Object> registration = with(common,
                "logic_script_hash", TxPlanSubstandardScripts.scriptHash());
        submit("01-register-and-initial-mint.yml", registration, codec, extension, backend, owner);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(INITIAL_MINT);

        submit("02-mint.yml", with(common, "policy_id", policyId), codec, extension, backend, owner);
        BigInteger afterMint = INITIAL_MINT.add(SECOND_MINT);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(afterMint);

        Set<String> beforeTransfer = smartWalletUtxos(protocol, ownerAddress);
        submit("03-transfer.yml", with(common, "token_unit", unit), codec, extension, backend, owner);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(afterMint);
        assertThat(smartWalletUtxos(protocol, ownerAddress))
                .as("the YAML transfer must consume a smart-wallet UTxO")
                .isNotEqualTo(beforeTransfer);

        submit("04-burn.yml", with(common, "policy_id", policyId), codec, extension, backend, owner);
        assertThat(quantity(protocol, ownerAddress, unit)).isZero();

        // Fresh supply for the cross-owner flow; the same mint plan is simply run again.
        submit("02-mint.yml", with(common, "policy_id", policyId), codec, extension, backend, owner);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(SECOND_MINT);

        // Two transfers to another owner, aggregated, with datums and variable-bearing redeemers.
        // The decoded plan is then built and submitted a second time against the refreshed chain:
        // building never changes the authored plan, so it stays reusable.
        Map<String, Object> crossOwner = with(with(common, "token_unit", unit),
                "recipient_address", recipient.baseAddress());
        TxPlan crossOwnerPlan = codec.fromYaml(loadYaml("05-transfer-cross-owner.yml"), crossOwner);
        String crossOwnerYaml = codec.toYaml(crossOwnerPlan);
        int authoredIntents = crossOwnerPlan.getTxs().get(0).getIntentions().size();
        submit(crossOwnerPlan, "05-transfer-cross-owner.yml (first)", extension, backend, owner);
        assertThat(quantity(protocol, recipientAddress, unit)).isEqualTo(CROSS_OWNER_TOTAL);
        assertThat(quantity(protocol, ownerAddress, unit)).isEqualTo(SECOND_MINT.subtract(CROSS_OWNER_TOTAL));
        // Compared as Plutus data, not bytes: the structured datum and the CBOR-hex datum encode
        // the same value with different (indefinite/definite) list encodings.
        assertThat(protocol.getUtxos(recipientAddress).getValue())
                .as("the receiver's outputs carry the datums the plan declared")
                .allSatisfy(utxo -> assertThat(plutusJson(utxo.getInlineDatum()))
                        .isEqualTo(PlutusDataJsonConverter.toJson(HOLDER_DATUM)));
        assertThat(crossOwnerPlan.getTxs().get(0).getIntentions()).hasSize(authoredIntents);
        assertThat(codec.toYaml(crossOwnerPlan)).as("building must not change the plan").isEqualTo(crossOwnerYaml);

        submit(crossOwnerPlan, "05-transfer-cross-owner.yml (rebuilt)", extension, backend, owner);
        assertThat(quantity(protocol, recipientAddress, unit)).isEqualTo(CROSS_OWNER_TOTAL.multiply(BigInteger.TWO));
        assertThat(codec.toYaml(crossOwnerPlan)).isEqualTo(crossOwnerYaml);

        // Set the unfracking hook through a registry update; the frozen fields are repeated as-is.
        RegistryNode node = protocol.getRegistryNode(policyId).getValue();
        Map<String, Object> hook = with(with(with(common, "policy_id", policyId),
                "policy_next", node.getNext()), "logic_script_hash", TxPlanSubstandardScripts.scriptHash());
        submit("06-update-unfracking-hook.yml", hook, codec, extension, backend, owner);
        RegistryNode updated = protocol.getRegistryNode(policyId).getValue();
        assertThat(HexUtil.encodeHexString(updated.getUnfrackingLogicScript().getBytes()))
                .isEqualToIgnoringCase(TxPlanSubstandardScripts.scriptHash());
        assertThat(updated.getNext()).isEqualTo(node.getNext());

        // A shared ("fracked") UTxO: the token minted together with an unrelated native asset into
        // one output, which is what a freeze scoped to one policy would lock as a whole.
        BigInteger heldBeforeFrack = quantity(protocol, ownerAddress, unit);
        String dustUnit = mintFrackedUtxo(backend, extension, owner, policyId, protocol.smartWalletAddress(ownerAddress));
        Utxo fracked = protocol.getUtxos(ownerAddress).getValue().stream()
                .filter(utxo -> holds(utxo, unit) && holds(utxo, dustUnit))
                .findFirst().orElseThrow(() -> new AssertionError("no fracked UTxO was created"));
        log.info("fracked UTxO {}#{}", fracked.getTxHash(), fracked.getOutputIndex());

        submit("07-unfrack.yml", with(common, "policy_id", policyId), codec, extension, backend, owner);

        List<Utxo> afterUnfrack = protocol.getUtxos(ownerAddress).getValue();
        assertThat(quantity(protocol, ownerAddress, unit))
                .as("unfracking conserves the holding").isEqualTo(heldBeforeFrack.add(FRACKED_QUANTITY));
        assertThat(afterUnfrack).noneMatch(utxo -> utxo.getTxHash().equals(fracked.getTxHash())
                && utxo.getOutputIndex() == fracked.getOutputIndex());
        assertThat(afterUnfrack).as("the dust stays behind in a UTxO without the acted policy")
                .anyMatch(utxo -> holds(utxo, dustUnit) && !holds(utxo, unit));
        assertThat(afterUnfrack).as("the acted policy is regrouped into its own output")
                .anyMatch(utxo -> holds(utxo, unit) && !holds(utxo, dustUnit)
                        && FRACKED_QUANTITY.equals(quantityOf(utxo, unit)));
    }

    /** Mint {@link #FRACKED_QUANTITY} of the token and a dust native asset into one smart-wallet output. */
    private static String mintFrackedUtxo(BackendService backend, ProgrammableTokenExtension extension,
                                          Account owner, String policyId, Address smartWallet) {
        ScriptPubkey dustPolicy = new ScriptPubkey(HexUtil.encodeHexString(
                owner.hdKeyPair().getPublicKey().getKeyHash()));
        Asset dust = new Asset("0x44757374", BigInteger.ONE);
        Tx tx = new ProgrammableTokenTx()
                .from(owner.baseAddress())
                .mint(policyId, owner.baseAddress(),
                        List.of(new Asset("0x" + ASSET_NAME_HEX, FRACKED_QUANTITY)), AUTHORIZATION, HOLDER_DATUM)
                .mintAssets(dustPolicy, dust, smartWallet.toBech32());

        Result<String> result = new QuickTxBuilder(backend)
                .withExtension(extension)
                .compose(tx)
                .feePayer(owner.baseAddress())
                .withSigner(SignerProviders.signerFrom(owner))
                .withTxEvaluator(new AikenTransactionEvaluator(backend))
                // Merging is what puts both assets into one output; programmable-token builds
                // otherwise keep it off.
                .mergeOutputs(true)
                .completeAndWait(message -> log.debug("fracked mint: {}", message));
        assertThat(result.isSuccessful()).as("minting the fracked UTxO: %s", result.getResponse()).isTrue();
        try {
            return dustPolicy.getPolicyId() + "44757374";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void submit(String resource, Map<String, Object> variables,
                               TxPlanCodec codec, ProgrammableTokenExtension extension,
                               BackendService backend, Account signer) {
        TxPlan plan = codec.fromYaml(loadYaml(resource), variables);
        assertThat(plan.getVariables()).containsAllEntriesOf(variables);
        assertThat(plan.getTxs()).isNotEmpty().allSatisfy(tx ->
                assertThat(tx).isExactlyInstanceOf(Tx.class));
        submit(plan, resource, extension, backend, signer);
    }

    private static void submit(TxPlan plan, String label, ProgrammableTokenExtension extension,
                               BackendService backend, Account signer) {
        Result<String> result = new QuickTxBuilder(backend)
                .withExtension(extension)
                .compose(plan)
                .withSigner(SignerProviders.signerFrom(signer))
                .withTxEvaluator(new AikenTransactionEvaluator(backend))
                .completeAndWait(message -> log.debug("{}: {}", label, message));
        assertThat(result.isSuccessful())
                .as("submitting %s: %s", label, result.getResponse()).isTrue();
        log.info("{} submitted in {}", label, result.getValue());
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

    private static String plutusJson(String inlineDatumHex) {
        try {
            return PlutusDataJsonConverter.toJson(
                    PlutusData.deserialize(HexUtil.decodeHexString(inlineDatumHex)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decode inline datum " + inlineDatumHex, e);
        }
    }

    private static boolean holds(Utxo utxo, String unit) {
        return quantityOf(utxo, unit).signum() > 0;
    }

    private static BigInteger quantityOf(Utxo utxo, String unit) {
        return utxo.getAmount().stream()
                .filter(amount -> unit.equals(amount.getUnit()))
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
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
