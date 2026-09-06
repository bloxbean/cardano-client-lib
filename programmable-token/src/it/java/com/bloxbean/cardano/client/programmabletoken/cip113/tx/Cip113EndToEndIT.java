package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.model.AccountInformation;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Registration;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113RegistryUpdate;
import com.bloxbean.cardano.client.programmabletoken.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end CIP-113 test against a local Yaci DevKit devnet.
 *
 * <h2>Running it</h2>
 * <pre>
 * yaci-devkit up
 * ./gradlew :programmable-token:integrationTest --tests '*Cip113EndToEndIT*'
 * </pre>
 *
 * <p>No configuration: no API key, no funded account, no environment variables. The suite resets
 * the devnet, funds the standard DevKit account, and deploys the whole CIP-113 protocol itself.
 * The only condition it skips on is a devnet that is not running; once that is established,
 * every protocol, build, submission or state failure is a test failure.</p>
 *
 * <h2>What it proves</h2>
 * <p>Steps 0-5 stand the protocol up and read it back. Steps 6-18 are the end-to-end path a real
 * user walks: register a token, mint it, transfer it (to self and to another owner), burn it,
 * seize it, update its registry entry, and unfrack a shared UTxO. Because the chain is reset each
 * run, every step starts from a known state.</p>
 *
 * <p>Scripts are evaluated locally with Aiken rather than through the backend: a remote evaluator
 * that cannot build an evaluation context returns an empty {@code ScriptFailures} map, which names
 * neither the script nor the reason.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ResourceLock("yaci-devkit")
public class Cip113EndToEndIT {

    private static final Logger log = LoggerFactory.getLogger(Cip113EndToEndIT.class);

    private static BackendService backendService;
    private static Account account;
    private static Address ownerAddress;
    private static Cip113ProtocolService protocolService;

    /** Composition-based domain service used by every QuickTx build. */
    private static Cip113ProgrammableTokenService programmableTokens;

    /** Populated by step 1 and reused by the rest. */
    private static Cip113Deployment resolved;

    /**
     * What step 0 put on chain, including every applied script.
     *
     * <p>The backend cannot serve any of these until the chain reveals them, and a script is only
     * revealed when it is <i>used</i> — which is precisely what the steps below are trying to do.
     * Keeping the bootstrap's own scripts breaks that circularity.</p>
     */
    private static Cip113Bootstrap.Deployed deployed;

    private static Network network;

    /** Policy id of the example token, derived in step 7. */
    private static String examplePolicyId;

    private static final String EXAMPLE_ASSET_NAME = "Cip113Demo";
    private static final BigInteger MINT_QUANTITY = BigInteger.valueOf(1000);

    /** Minted in the same transaction as the registration — see step 7. */
    private static final BigInteger FIRST_MINT_QUANTITY = BigInteger.valueOf(50);

    /** Minted into a shared UTxO for the unfracking step. */
    private static final BigInteger FRACKED_QUANTITY = BigInteger.valueOf(5);

    /** The standard Yaci DevKit funded account. */
    private static final String SENDER_MNEMONIC =
            "test test test test test test test test test test test test"
            + " test test test test test test test test test test test sauce";

    /** "txHash#index" of every UTxO at the smart wallet right now. */
    private static Set<String> smartWalletUtxoRefs() {
        return smartWalletUtxos().stream()
                .map(u -> u.getTxHash().substring(0, 8) + "#" + u.getOutputIndex())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static List<Utxo> smartWalletUtxos() {
        Result<List<Utxo>> utxos = protocolService.getUtxos(ownerAddress);
        assertThat(utxos.isSuccessful()).as("smart-wallet UTxOs: %s", utxos.getResponse()).isTrue();
        return utxos.getValue();
    }

    /** How much of a unit the smart wallet currently holds, re-reading the chain each time. */
    private static BigInteger programmableQuantity(String unit) {
        return programmableQuantity(ownerAddress, unit);
    }

    private static BigInteger programmableQuantity(Address owner, String unit) {
        Result<List<Amount>> balance = protocolService.getProgrammableBalance(owner);
        assertThat(balance.isSuccessful()).as("programmable balance: %s", balance.getResponse()).isTrue();
        return quantity(balance.getValue(), unit);
    }

    /**
     * Evaluate scripts locally.
     *
     * <p>A remote evaluator that cannot build an evaluation context returns an empty
     * {@code ScriptFailures} map — no script name, no reason. Aiken's evaluator runs the validators
     * in-process and names the one that failed.</p>
     */
    private static com.bloxbean.cardano.aiken.AikenTransactionEvaluator evaluator() {
        return new com.bloxbean.cardano.aiken.AikenTransactionEvaluator(backendService);
    }

    /** The example token as a mintable {@link Asset}; negative quantity burns. */
    private static Asset exampleAsset(BigInteger quantity) {
        return new Asset("0x" + HexUtil.encodeHexString(
                EXAMPLE_ASSET_NAME.getBytes(StandardCharsets.UTF_8)), quantity);
    }

    private static String unitOf(String policyId) {
        return policyId + HexUtil.encodeHexString(EXAMPLE_ASSET_NAME.getBytes(StandardCharsets.UTF_8));
    }

    private static QuickTxBuilder.TxContext build(Tx tx) {
        return new QuickTxBuilder(backendService)
                .withExtension(programmableTokens.extension())
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(evaluator());
    }

    @BeforeAll
    static void setup() {
        if (!DevNet.isRunning()) {
            log.warn("No devnet is listening on {}. Start one with `yaci-devkit up` and re-run — this"
                    + " suite needs nothing else: no API key, no funded account, no environment"
                    + " variables.", DevNet.BACKEND_URL);
            Assumptions.abort("No devnet running on " + DevNet.BACKEND_URL);
        }

        backendService = new BFBackendService(DevNet.BACKEND_URL, "Dummy");
        network = Networks.testnet();
        account = new Account(network, SENDER_MNEMONIC);
        ownerAddress = new Address(account.baseAddress());

        log.info("=== CIP-113 end-to-end test === devnet {} account {}", DevNet.BACKEND_URL, account.baseAddress());

        // Start from an empty chain every run. The protocol is redeployed in step 0, so the whole
        // suite is reproducible and nothing carries over between runs.
        DevNet.reset();

        // Two separate top-ups: the bootstrap needs two distinct UTxOs to seed the one-shot
        // minting policies.
        DevNet.topUp(account.baseAddress(), 100_000L);
        DevNet.topUp(account.baseAddress(), 100_000L);

        awaitSeedUtxos(2);
    }

    /**
     * Block until the top-ups are visible as distinct UTxOs.
     *
     * <p>The admin endpoint returns as soon as the top-up is submitted, but the bootstrap reads
     * UTxOs through the indexer — so without this the suite races its own funding.</p>
     */
    private static void awaitSeedUtxos(int required) {
        var utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        long deadline = System.currentTimeMillis() + 60_000L;
        int seen = 0;
        while (System.currentTimeMillis() < deadline) {
            seen = (int) utxoSupplier.getAll(account.baseAddress()).stream()
                    .filter(u -> u.getAmount().stream()
                            .anyMatch(a -> "lovelace".equals(a.getUnit())
                                    && a.getQuantity().compareTo(BigInteger.valueOf(100_000_000L)) > 0))
                    .count();
            if (seen >= required) {
                log.info("Funded: {} seed UTxOs visible", seen);
                return;
            }
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for seed UTxOs", e);
            }
        }
        throw new IllegalStateException("Waited 60s for " + required + " UTxOs over 100 ADA at "
                + account.baseAddress() + " but only " + seen + " became visible. The top-ups were"
                + " accepted, so this is the devnet indexer falling behind or the admin endpoint"
                + " silently dropping one.");
    }

    /**
     * Resolve the deployment on demand.
     *
     * <p>The chain is reset every run, so the suite has to run as a whole: a step that finds no
     * deployment is a failure, not something to skip.</p>
     */
    private static void requireDeployment(String what) {
        assertThat(protocolService)
                .as("%s needs the deployment from step 0; the chain is reset every run, so the suite"
                        + " has to run as a whole. Drop the --tests method filter.", what)
                .isNotNull();
        if (resolved == null) {
            Result<Cip113Deployment> result = protocolService.resolveDeployment();
            assertThat(result.isSuccessful())
                    .as("%s: the deployment could not be resolved: %s", what, result.getResponse())
                    .isTrue();
            resolved = result.getValue();
            log.info("(resolved the deployment on demand for: {})", what);
        }
    }

    /** The example token's policy, derived on demand so later steps do not depend on step 7. */
    private static String requireExamplePolicyId() {
        if (examplePolicyId == null) {
            Result<String> derived = protocolService.derivePolicyId(AlwaysTrueScripts.credential());
            assertThat(derived.isSuccessful()).as("deriving the example policy id: %s", derived.getResponse()).isTrue();
            examplePolicyId = derived.getValue();
        }
        return examplePolicyId;
    }

    // ---------------------------------------------------------------- step 0

    /**
     * Deploy the whole CIP-113 protocol onto the devnet.
     *
     * <p>Runs on every invocation. The chain was just reset, so nothing exists yet — and building
     * on a protocol this suite deployed is what makes the run reproducible rather than dependent
     * on whatever state a shared testnet happens to be in.</p>
     */
    @Test
    @Order(0)
    void step0_deployProtocolOnDevnet() throws Exception {
        log.info("=== Deploying the CIP-113 protocol ===");
        deployed = new Cip113Bootstrap().deploy(backendService, account, network);
        Cip113Bootstrap.describe(deployed).forEach(l -> log.info("  {}", l));

        // Everything downstream resolves from the bootstrap transaction, exactly as it would for a
        // deployment someone else created — so this also exercises the discovery path.
        programmableTokens = Cip113ProgrammableTokenService.create(backendService,
                Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network));
        protocolService = programmableTokens.advanced();

        // Hand the service the scripts the bootstrap just applied. Nothing on chain has used them
        // yet, so no backend can serve them — and the steps below are what would reveal them.
        protocolService.scripts().registerAll(deployed.appliedScripts());

        // The example token's substandard, registered once for the whole run. Which logic scripts
        // a transaction invokes is read from the registry node, so no step below has to name it.
        protocolService.scripts().register(AlwaysTrueScripts.ALWAYS_TRUE);

        Result<Cip113Deployment> resolvedResult = protocolService.resolveDeployment();
        assertThat(resolvedResult.isSuccessful())
                .as("the freshly deployed protocol must resolve from its own bootstrap tx: %s",
                        resolvedResult.getResponse())
                .isTrue();
        resolved = resolvedResult.getValue();

        // What the bootstrap computed and what the chain reports must agree; if they do not, the
        // datum written and the scripts deployed have diverged.
        assertThat(resolved.getProgrammableLogicBaseHash())
                .isEqualToIgnoringCase(deployed.programmableLogicBaseHash());
        assertThat(resolved.getTransferScriptHash()).isEqualToIgnoringCase(deployed.transferHash());
        assertThat(resolved.getUnfrackingScriptHash()).isEqualToIgnoringCase(deployed.unfrackingHash());
        assertThat(resolved.getRegistryNodeCs()).isEqualToIgnoringCase(deployed.registryNodeCs());
        assertThat(resolved.getRegistrySpendScriptHash())
                .as("the registry address must be discovered from the bootstrap transaction, not"
                        + " supplied — the coordination datum does not carry it")
                .isEqualToIgnoringCase(deployed.registrySpendHash());
    }

    // ---------------------------------------------------------------- step 1

    @Test
    @Order(1)
    void step1_resolveDeployment() {
        requireDeployment("Deployment resolution");

        Result<Cip113Deployment> result = protocolService.resolveDeployment();
        assertThat(result.isSuccessful())
                .as("Step 0 deployed this protocol moments ago, so a failure here means the bootstrap"
                        + " wrote something the resolver cannot read back: %s", result.getResponse())
                .isTrue();

        resolved = result.getValue();
        log.info("=== CIP-113 deployment ===");
        log.info("bootstrap tx      : {}", protocolService.deployment().getBootstrapTxHash());
        log.info("params policy     : {}", resolved.getParamsPolicy());
        log.info("base script hash  : {}", resolved.getProgrammableLogicBaseHash());
        log.info("transfer delegate : {}", resolved.getTransferScriptHash());
        log.info("thirdparty deleg. : {}", resolved.getThirdPartyScriptHash());
        log.info("unfracking deleg. : {}", resolved.getUnfrackingScriptHash());
        log.info("registry node cs  : {}", resolved.getRegistryNodeCs());
        log.info("issuance tmpl cs  : {}", resolved.getIssuanceCborHexCs());
        log.info("registry address  : {}", resolved.registryAddress().toBech32());
        log.info("coordination utxo : {}#{}", protocolService.coordinationUtxo().getTxHash(),
                protocolService.coordinationUtxo().getOutputIndex());

        assertThat(resolved.getParamsPolicy()).isNotBlank();
        assertThat(resolved.getProgrammableLogicBaseHash()).isNotBlank();
    }

    // ---------------------------------------------------------------- step 2

    @Test
    @Order(2)
    void step2_deriveAddresses() {
        requireDeployment("Addresses");

        Address smartWallet = protocolService.smartWalletAddress(ownerAddress);

        log.info("=== Addresses === owner {} smart wallet {} alwaysTrue hash {} reward {}",
                ownerAddress.getAddress(), smartWallet.toBech32(), AlwaysTrueScripts.scriptHash(),
                AlwaysTrueScripts.rewardAddress(network).toBech32());

        assertThat(smartWallet.toBech32()).startsWith("addr_test");

        // Every address derived from the always-true script depends on its hash being right,
        // so check the compiled code and its CBOR wrapping reproduce what the blueprint declares.
        assertThat(AlwaysTrueScripts.scriptHash())
                .as("always-true script hash must match its blueprint")
                .isEqualToIgnoringCase(AlwaysTrueScripts.EXPECTED_SCRIPT_HASH);
    }

    // ---------------------------------------------------------------- step 3

    @Test
    @Order(3)
    void step3_scanRegistry() {
        requireDeployment("Registry scan");

        Result<List<RegistryNode>> registry = protocolService.getRegistry();
        assertThat(registry.isSuccessful())
                .as("registry scan: %s", registry.getResponse())
                .isTrue();

        log.info("=== Registry ({} node(s)) ===", registry.getValue().size());
        for (RegistryNode node : registry.getValue()) {
            log.info("  key {} next {} transfer={} thirdParty={} globalState={}", node.getKey(),
                    node.getNext(), hex(node.getTransferLogicScript()),
                    hex(node.getThirdPartyTransferLogicScript()),
                    node.hasGlobalState() ? node.getGlobalStateCs() : "none");
        }
        assertThat(registry.getValue()).as("the bootstrap creates the origin node").isNotEmpty();
    }

    // ---------------------------------------------------------------- step 4

    @Test
    @Order(4)
    void step4_derivePolicyIdForAlwaysTrueToken() {
        requireDeployment("Example token");

        Result<String> policyId = protocolService.derivePolicyId(AlwaysTrueScripts.credential());
        assertThat(policyId.isSuccessful())
                .as("policy id derivation: %s", policyId.getResponse()).isTrue();

        log.info("=== Example token === issuance credential {} derived policy id {}",
                AlwaysTrueScripts.scriptHash(), policyId.getValue());
        assertThat(policyId.getValue()).hasSize(56);

        Result<Boolean> registered = protocolService.isProgrammable(policyId.getValue());
        assertThat(registered.isSuccessful()).isTrue();
        log.info("already registered  : {}", registered.getValue());
    }

    // ---------------------------------------------------------------- step 5

    /**
     * Fetch the deployment's applied scripts by hash.
     *
     * <p>Every CIP-113 validator is parameterized, so a blueprint's hash never matches what is
     * deployed and the applied bytes have to come from somewhere. They are already on chain: a
     * Plutus script is revealed the first time it is used, so a backend can serve it by hash.</p>
     */
    @Test
    @Order(5)
    void step5_resolveDeploymentScripts() throws Exception {
        requireDeployment("Script resolution");

        DeploymentScripts scripts = protocolService.scripts();

        var base = scripts.programmableLogicBase();
        var transfer = scripts.transferDelegate();
        var unfracking = scripts.unfrackingDelegate();
        var registrySpend = scripts.registrySpend();
        var registryMint = scripts.registryMint();

        log.info("=== Applied scripts === base {} ({} bytes) transfer {} ({} bytes) unfracking {}"
                        + " registry_spend {} registry_mint {}",
                base.getPolicyId(), base.getCborHex().length() / 2, transfer.getPolicyId(),
                transfer.getCborHex().length() / 2, unfracking.getPolicyId(),
                registrySpend.getPolicyId(), registryMint.getPolicyId());

        // Fetched by hash, so a mismatch would mean the backend served the wrong script.
        assertThat(base.getPolicyId()).isEqualToIgnoringCase(resolved.getProgrammableLogicBaseHash());
        assertThat(transfer.getPolicyId()).isEqualToIgnoringCase(resolved.getTransferScriptHash());
        assertThat(unfracking.getPolicyId()).isEqualToIgnoringCase(resolved.getUnfrackingScriptHash());
        assertThat(registryMint.getPolicyId()).isEqualToIgnoringCase(resolved.getRegistryNodeCs());
    }

    // ---------------------------------------------------------------- step 6

    /**
     * Make sure the always-true script's reward account is registered, registering it if not.
     *
     * <p>A withdrawal is only valid against a <b>registered</b> stake credential — even a zero
     * one — so every CIP-113 substandard script needs its reward account registered before it can
     * ever be invoked via withdraw-zero.</p>
     *
     * <p>Registering a <i>script</i> stake credential needs no script witness: registration is
     * permissionless. Idempotent: the ledger's own rejection is the authority on "already
     * registered", because neither Blockfrost's nor yaci-store's account view means that.</p>
     */
    @Test
    @Order(6)
    void step6_ensureAlwaysTrueRewardAccountIsRegistered() throws Exception {
        String rewardAddress = AlwaysTrueScripts.rewardAddress(network).toBech32();
        log.info("=== Always-true withdraw-zero script === hash {} reward address {}",
                AlwaysTrueScripts.scriptHash(), rewardAddress);
        describeAccount(rewardAddress);

        Result<String> result = new QuickTxBuilder(backendService)
                .compose(new Tx()
                        .registerStakeAddress(rewardAddress)
                        .from(account.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(log::info);

        if (result.isSuccessful()) {
            log.info("registered in tx: {}", result.getValue());
            return;
        }
        assertThat(alreadyRegistered(result.getResponse()))
                .as("registering the always-true reward account failed for a reason other than"
                        + " it already being registered: %s", result.getResponse())
                .isTrue();
        log.info("status: the ledger rejected this as already registered, so the account is usable");
    }

    /** Diagnostics only: no backend answer here means "registered" reliably. */
    private static void describeAccount(String stakeAddress) {
        try {
            Result<AccountInformation> info =
                    backendService.getAccountService().getAccountInformation(stakeAddress);
            if (!info.isSuccessful() || info.getValue() == null) {
                log.info("account lookup : no account found — never registered ({})", info.getResponse());
                return;
            }
            AccountInformation ai = info.getValue();
            log.info("account lookup : found; active={} pool={} controlled={}", ai.getActive(),
                    ai.getPool_id(), ai.getControlledAmount());
        } catch (Exception e) {
            log.info("account lookup : failed ({})", e.getMessage());
        }
    }

    /**
     * Whether a rejected submission means the account is already usable: the ledger saying the
     * credential is registered, or an identical registration having landed already.
     */
    private static boolean alreadyRegistered(String submitResponse) {
        if (submitResponse == null) return false;
        return submitResponse.contains("StakeKeyRegistered")
                || submitResponse.contains("All inputs are spent")
                || submitResponse.contains("already been included");
    }

    // ---------------------------------------------------------------- step 7

    /**
     * Register the example token and mint its first supply in one transaction.
     *
     * <p>{@code issuance_mint} cannot take its RefInput proof here — the registry node it needs is
     * an output of this very transaction — so this is the OutputIndex proof, the ordinary case: a
     * token's first mint naturally belongs with its registration.</p>
     */
    @Test
    @Order(7)
    void step7_registerExampleToken() {
        requireDeployment("Token registration");
        examplePolicyId = requireExamplePolicyId();
        log.info("=== Token registration === policy id {}", examplePolicyId);

        assertThat(protocolService.isProgrammable(examplePolicyId).getValue())
                .as("the chain was reset, so the example token cannot be registered yet").isFalse();

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .register("example", Cip113Registration.from(RegistryNodeSpec.builder()
                        .mintingLogicScript(AlwaysTrueScripts.credential())
                        .transferLogicScript(AlwaysTrueScripts.credential())
                        .thirdPartyTransferLogicScript(AlwaysTrueScripts.credential())
                        .build()), PlutusData.unit())
                .mint(ProgrammableTokenPolicyRef.named("example"), account.baseAddress(),
                        List.of(exampleAsset(FIRST_MINT_QUANTITY)), PlutusData.unit(), null);

        StringBuilder shape = new StringBuilder();
        Result<String> result = build(tx)
                .preBalanceTx((ctx, txn) -> shape.append(describe("before balancing", txn)))
                .completeAndWait(log::info);
        log.info("{}", shape);
        assertThat(result.isSuccessful())
                .as("registering the example token: %s", result.getResponse())
                .isTrue();
        log.info("registered in  : {}", result.getValue());

        assertThat(programmableQuantity(unitOf(examplePolicyId)))
                .as("the first mint must have landed in the same transaction as the registration,"
                        + " which is only possible through issuance_mint's OutputIndex proof")
                .isEqualTo(FIRST_MINT_QUANTITY);
    }

    // ---------------------------------------------------------------- step 8

    /** Mint the example token into this account's own smart wallet. */
    @Test
    @Order(8)
    void step8_mintExampleToken() {
        requireDeployment("Mint");
        requireExamplePolicyId();
        assertThat(protocolService.isProgrammable(examplePolicyId).getValue())
                .as("Policy %s is not registered — registration must land first", examplePolicyId).isTrue();

        BigInteger heldBefore = programmableQuantity(unitOf(examplePolicyId));
        log.info("=== Mint === policy {} into {} balance before {}", examplePolicyId,
                protocolService.smartWalletAddress(ownerAddress).toBech32(), heldBefore);

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .mint(examplePolicyId, account.baseAddress(), List.of(exampleAsset(MINT_QUANTITY)),
                        BigIntPlutusData.of(0), null);

        Result<String> result = build(tx).completeAndWait(log::info);
        assertThat(result.isSuccessful())
                .as("minting the example token: %s", result.getResponse())
                .isTrue();

        BigInteger heldAfter = programmableQuantity(unitOf(examplePolicyId));
        log.info("balance after  : {}", heldAfter);
        assertThat(heldAfter)
                .as("minting %s should raise the smart-wallet balance by exactly that much", MINT_QUANTITY)
                .isEqualTo(heldBefore.add(MINT_QUANTITY));
    }

    // ---------------------------------------------------------------- step 9

    /**
     * Transfer a programmable token to self and check the holding is conserved and a UTxO was
     * actually spent: proofs, withdrawals, index resolution and all.
     */
    @Test
    @Order(9)
    void step9_transferAndCheckBalance() {
        requireDeployment("Transfer");
        String unit = unitOf(requireExamplePolicyId());

        BigInteger beforeQty = programmableQuantity(unit);
        Set<String> utxosBefore = smartWalletUtxoRefs();
        assertThat(beforeQty).as("steps 7 and 8 must have minted supply to transfer")
                .isGreaterThanOrEqualTo(BigInteger.TWO);
        log.info("=== Transfer === transferring two 1-unit outputs of {} to self", unit);

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(account.baseAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE),
                        BigIntPlutusData.of(0))
                .transfer(account.baseAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE),
                        BigIntPlutusData.of(0));   // alwaysTrue ignores it

        StringBuilder shape = new StringBuilder();
        Result<String> result = build(tx)
                .preBalanceTx((ctx, txn) -> shape.append(describe("before balancing", txn)))
                .postBalanceTx((ctx, txn) -> shape.append(describe("after balancing", txn)))
                .completeAndWait(log::info);
        log.info("{}", shape);
        assertThat(result.isSuccessful()).as("transfer: %s", result.getResponse()).isTrue();

        assertThat(programmableQuantity(unit))
                .as("a self-transfer must conserve the holding")
                .isEqualTo(beforeQty);
        assertThat(smartWalletUtxoRefs())
                .as("the transfer must actually have spent a base-script UTxO")
                .isNotEqualTo(utxosBefore);

        // Nothing above named a substandard script: which logic runs is read from the registry
        // node. A resolver with an empty local cache, backed only by the backend, must find it
        // now that using the script revealed it on chain.
        DeploymentScripts backendOnly =
                new DeploymentScripts(backendService.getScriptService(), resolved);
        assertThat(backendOnly.getScript(AlwaysTrueScripts.scriptHash()))
                .as("once a substandard has been used on chain, an ordinary caller must be able to"
                        + " resolve it by hash with no local registration at all")
                .isPresent();
    }

    // --------------------------------------------------------------- helpers

    /** A one-line summary of the shapes every CIP-113 redeemer indexes into. */
    private static String describe(String stage, com.bloxbean.cardano.client.transaction.spec.Transaction txn) {
        var body = txn.getBody();
        StringBuilder sb = new StringBuilder("\n--- " + stage + " ---\n");
        sb.append("  inputs=").append(body.getInputs() == null ? 0 : body.getInputs().size())
          .append(" refInputs=").append(body.getReferenceInputs() == null ? 0 : body.getReferenceInputs().size())
          .append(" outputs=").append(body.getOutputs() == null ? 0 : body.getOutputs().size())
          .append(" withdrawals=").append(body.getWithdrawals() == null ? 0 : body.getWithdrawals().size())
          .append(" requiredSigners=").append(body.getRequiredSigners() == null ? 0 : body.getRequiredSigners().size())
          .append("\n");
        if (txn.getWitnessSet() != null) {
            sb.append("  witnessScripts=")
              .append(txn.getWitnessSet().getPlutusV3Scripts() == null
                      ? 0 : txn.getWitnessSet().getPlutusV3Scripts().size())
              .append('\n');
        }
        if (txn.getWitnessSet() != null && txn.getWitnessSet().getRedeemers() != null) {
            txn.getWitnessSet().getRedeemers().forEach(r ->
                    sb.append("  redeemer ").append(r.getTag()).append(" index=").append(r.getIndex()).append('\n'));
        }
        if (body.getWithdrawals() != null) {
            body.getWithdrawals().forEach(w ->
                    sb.append("  withdrawal ").append(w.getRewardAddress()).append('\n'));
        }
        if (body.getMint() != null) {
            var sorted = new java.util.ArrayList<>(body.getMint());
            sorted.sort(Comparator.comparing(com.bloxbean.cardano.client.transaction.spec.MultiAsset::getPolicyId));
            for (int i = 0; i < sorted.size(); i++) {
                sb.append("  mint[").append(i).append("] ").append(sorted.get(i).getPolicyId());
                sorted.get(i).getAssets().forEach(a ->
                        sb.append(" {").append(a.getName()).append('=').append(a.getValue()).append('}'));
                sb.append('\n');
            }
        }
        if (body.getOutputs() != null) {
            for (int i = 0; i < body.getOutputs().size(); i++) {
                var o = body.getOutputs().get(i);
                sb.append("  output[").append(i).append("] ")
                  .append(o.getAddress() == null ? "?" : o.getAddress().substring(0, Math.min(24, o.getAddress().length())))
                  .append(" datum=").append(o.getInlineDatum() != null ? "inline" : "none");
                o.getValue().getMultiAssets().forEach(ma -> {
                    sb.append(' ').append(ma.getPolicyId(), 0, 8);
                    ma.getAssets().forEach(a -> sb.append(':').append(a.getName()).append('=').append(a.getValue()));
                });
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    // --------------------------------------------------------------- step 10

    /**
     * A service nobody initialised must still work: build a fresh service from nothing but a
     * bootstrap tx hash, never call {@code resolveDeployment()}, and go straight to using it.
     */
    @Test
    @Order(10)
    void step10_freshServiceResolvesItself() {
        requireDeployment("Lazy initialisation");

        Cip113ProtocolService fresh = Cip113ProgrammableTokenService.create(backendService,
                Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network)).advanced();

        log.info("=== A service that was never resolved ===");
        assertThat(fresh.coordinationUtxo()).as("the coordination UTxO must resolve on demand").isNotNull();
        assertThat(fresh.issuanceTemplateUtxo()).as("the issuance template must resolve on demand").isNotNull();
        log.info("coordination utxo : {}#{}", fresh.coordinationUtxo().getTxHash(),
                fresh.coordinationUtxo().getOutputIndex());

        Result<List<RegistryNode>> registry = fresh.getRegistry();
        assertThat(registry.isSuccessful())
                .as("registry scan on an unresolved service: %s", registry.getResponse())
                .isTrue();
        assertThat(registry.getValue())
                .as("the registry must contain the node registered in step 7, which means the scan"
                        + " ran against the resolved deployment rather than a null policy")
                .hasSizeGreaterThan(1);

        assertThat(fresh.smartWalletAddress(ownerAddress).toBech32())
                .isEqualTo(protocolService.smartWalletAddress(ownerAddress).toBech32());
        assertThat(fresh.deployment().getProgrammableLogicBaseHash())
                .isEqualToIgnoringCase(resolved.getProgrammableLogicBaseHash());

        Result<Boolean> programmable = fresh.isProgrammable(requireExamplePolicyId());
        assertThat(programmable.isSuccessful()).isTrue();
        assertThat(programmable.getValue())
                .as("the example policy must be visible to a service nobody initialised")
                .isTrue();
        assertThat(new Cip113ProgrammableTokenService(fresh).extension()).isNotNull();
    }

    // --------------------------------------------------------------- step 11

    /**
     * Settle the withdrawal-ordering question against a real ledger.
     *
     * <p>{@link LedgerOrdering} claims the ledger puts every script credential before every key
     * credential in the withdrawal map. Steps 0-9 never test this: their withdrawals are all
     * script credentials. This adds a <b>key</b>-credential withdrawal whose hash sorts
     * <i>before</i> the script hashes — exactly where hash-only and scripts-first ordering
     * disagree. If the claim is wrong, the transaction fails.</p>
     */
    @Test
    @Order(11)
    void step11_withdrawalOrderingWithMixedCredentials() {
        requireDeployment("Withdrawal ordering");
        String policyId = requireExamplePolicyId();

        // The two script credentials already in play, and the lowest byte among them.
        int lowestScript = Math.min(
                Integer.parseInt(resolved.getTransferScriptHash().substring(0, 2), 16),
                Integer.parseInt(AlwaysTrueScripts.scriptHash().substring(0, 2), 16));

        // A stake key whose hash sorts strictly before both script hashes. Fresh accounts, not
        // derived indices: CIP-1852 keeps one stake key per *account*. The key never holds funds.
        Account discriminating = null;
        for (int i = 0; i < 4000 && discriminating == null; i++) {
            Account candidate = new Account(network);
            if ((stakeKeyHashOf(candidate)[0] & 0xff) < lowestScript) discriminating = candidate;
        }
        assertThat(discriminating)
                .as("no fresh stake key sorted before script byte 0x%02x in 4000 attempts", lowestScript)
                .isNotNull();

        String rewardAddress = discriminating.stakeAddress();
        log.info("=== Withdrawal ordering === key credential {} script creds {}, {}",
                HexUtil.encodeHexString(stakeKeyHashOf(discriminating)),
                resolved.getTransferScriptHash(), AlwaysTrueScripts.scriptHash());

        // A key-credential withdrawal is only valid once the account is registered and, in Conway,
        // has delegated its voting power. The stake key signer pads the fee estimate as well.
        Result<String> reg = new QuickTxBuilder(backendService)
                .compose(new Tx()
                        .registerStakeAddress(rewardAddress)
                        .delegateVotingPowerTo(new Address(rewardAddress), DRep.abstain())
                        .from(account.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account))
                .withSigner(SignerProviders.stakeKeySignerFrom(discriminating))
                // Registering a key stake credential on this devnet is consistently under-estimated
                // by a flat ~25k lovelace; padding is a test-harness workaround, not a library fix.
                .additionalSignersCount(6)
                .completeAndWait(log::info);
        assertThat(reg.isSuccessful() || alreadyRegistered(reg.getResponse()))
                .as("could not register the discriminating stake key: %s", reg.getResponse()).isTrue();

        Tx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(ownerAddress.getAddress(),
                        Amount.asset(policyId, EXAMPLE_ASSET_NAME, 1), BigIntPlutusData.of(0))
                .withdraw(rewardAddress, BigInteger.ZERO);

        Result<String> result = build(tx)
                .withSigner(SignerProviders.stakeKeySignerFrom(discriminating))
                .completeAndWait(log::info);
        assertThat(result.isSuccessful())
                .as("A transfer carrying both script and key withdrawals must validate. A failure"
                        + " here means the Reward redeemer indices were computed against the wrong"
                        + " withdrawal ordering, i.e. LedgerOrdering is wrong: %s", result.getResponse())
                .isTrue();
    }

    // --------------------------------------------------------------- step 12

    /**
     * Burn part of the minted supply: a real transfer-path spend under {@code SpendViaTransfer},
     * with the remainder returned to the smart wallet.
     */
    @Test
    @Order(12)
    void step12_burnExampleToken() {
        requireDeployment("Burn");
        String unit = unitOf(requireExamplePolicyId());

        BigInteger held = programmableQuantity(unit);
        assertThat(held).as("step 8 must have minted %s before it can be burned", unit)
                .isGreaterThanOrEqualTo(BigInteger.TWO);

        BigInteger toBurn = BigInteger.ONE;
        log.info("=== Burn === burning {} of {} (held: {})", toBurn, unit, held);

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .burn(examplePolicyId, List.of(exampleAsset(toBurn)),
                        BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(0)));

        Result<String> result = build(tx).completeAndWait(log::info);
        assertThat(result.isSuccessful())
                .as("Burn must validate: issuance_mint delegates custody to the transfer validator"
                        + " and no_escape requires the remainder to stay at the base script: %s",
                        result.getResponse())
                .isTrue();

        assertThat(programmableQuantity(unit))
                .as("the burned amount must be destroyed and the remainder still held")
                .isEqualTo(held.subtract(toBurn));
    }

    // --------------------------------------------------------------- step 13

    /**
     * Seize tokens from a holder's smart wallet into someone else's, spanning at least two inputs
     * so positional pairing is exercised.
     */
    @Test
    @Order(13)
    void step13_thirdPartySeize() throws Exception {
        requireDeployment("Third-party seizure");
        String unit = unitOf(requireExamplePolicyId());

        BigInteger holderBefore = programmableQuantity(unit);
        assertThat(holderBefore)
                .as("the holder must hold something before it can be seized").isGreaterThan(BigInteger.ONE);

        Account recipient = new Account(network);
        Address recipientAddress = new Address(recipient.baseAddress());
        Address recipientWallet = protocolService.smartWalletAddress(recipientAddress);

        // Seize MORE than any single UTxO holds, so the seizure spans at least two inputs.
        List<BigInteger> perUtxo = smartWalletUtxos().stream()
                .map(u -> quantity(u.getAmount(), unit))
                .filter(q -> q.signum() > 0)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        assertThat(perUtxo.size())
                .as("the seizure needs the holder's supply split across at least two UTxOs;"
                        + " step 9's self-transfer should have done that. Holdings per UTxO: %s", perUtxo)
                .isGreaterThanOrEqualTo(2);

        BigInteger toSeize = perUtxo.get(0).add(BigInteger.ONE);
        log.info("=== Third-party seizure === seizing {} of {} across {} UTxOs from {} to {}",
                toSeize, unit, perUtxo.size(),
                protocolService.smartWalletAddress(ownerAddress).toBech32(), recipientWallet.toBech32());

        // An unrelated native token minted to an ordinary address in the same transaction: the
        // third-party validator reads only the acted policy's mint and pins only the paired
        // smart-wallet outputs, so this must validate too.
        ScriptPubkey unrelatedPolicy = new ScriptPubkey(HexUtil.encodeHexString(
                account.hdKeyPair().getPublicKey().getKeyHash()));
        String unrelatedPolicyId = unrelatedPolicy.getPolicyId();
        String unrelatedUnit = unrelatedPolicyId + "5365697a65";
        Tx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .thirdPartyTransfer(ownerAddress.getAddress(), recipientAddress.getAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, toSeize),
                        BigIntPlutusData.of(0))   // alwaysTrue ignores it
                .mintAssets(unrelatedPolicy, new Asset("0x5365697a65", BigInteger.ONE),
                        account.baseAddress());

        StringBuilder shape = new StringBuilder();
        int[] plbOutputs = {0};
        boolean[] minted = {false};
        Result<String> result = build(tx)
                .preBalanceTx((ctx, txn) -> {
                    shape.append(describe("before balancing", txn));
                    String holderWallet = protocolService.smartWalletAddress(ownerAddress).toBech32();
                    plbOutputs[0] = (int) txn.getBody().getOutputs().stream()
                            .filter(o -> holderWallet.equals(o.getAddress()))
                            .count();
                    minted[0] = txn.getBody().getMint() != null && txn.getBody().getMint().stream()
                            .anyMatch(ma -> ma.getPolicyId().equals(unrelatedPolicyId));
                })
                .completeAndWait(log::info);
        log.info("{}", shape);
        assertThat(result.isSuccessful())
                .as("A third-party seizure with an unrelated native mint must validate: %s",
                        result.getResponse())
                .isTrue();
        assertThat(minted[0]).as("the unrelated mint was part of the seizing transaction").isTrue();
        assertThat(new DefaultUtxoSupplier(backendService.getUtxoService()).getAll(account.baseAddress()))
                .as("the unrelated token landed at the ordinary address")
                .anyMatch(u -> holds(u, unrelatedUnit));

        assertThat(programmableQuantity(unit))
                .as("the holder's balance must fall by exactly what was seized")
                .isEqualTo(holderBefore.subtract(toSeize));
        assertThat(programmableQuantity(recipientAddress, unit))
                .as("the seized tokens must land in the recipient's smart wallet")
                .isEqualTo(toSeize);
        assertThat(plbOutputs[0])
                .as("a seizure spanning two inputs must emit one continuing output per input")
                .isGreaterThanOrEqualTo(2);
    }

    // --------------------------------------------------------------- step 14

    /**
     * The published reference scripts are used instead of witnesses: the bootstrap publishes the
     * base script and the delegates, and every transaction thereafter points at them.
     */
    @Test
    @Order(14)
    void step14_publishedScriptsAreReferencedNotWitnessed() {
        requireDeployment("Reference scripts");
        String unit = unitOf(requireExamplePolicyId());

        DeploymentScripts scripts = protocolService.scripts();
        assertThat(scripts.publishedAt(resolved.getProgrammableLogicBaseHash()))
                .as("the bootstrap published programmable_logic_base as a reference script").isPresent();
        assertThat(scripts.publishedAt(resolved.getTransferScriptHash()))
                .as("the transfer delegate is published too").isPresent();
        assertThat(scripts.publishedAt(resolved.getUnfrackingScriptHash()))
                .as("the unfracking delegate is published too").isPresent();
        assertThat(programmableQuantity(unit)).as("need supply to transfer").isGreaterThan(BigInteger.ZERO);

        int[] witnessScripts = {-1};
        int[] refInputs = {-1};
        Result<String> result = build(new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(account.baseAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE),
                        BigIntPlutusData.of(0)))
                // After balancing on purpose: the duplicate-witness removal runs there.
                .postBalanceTx((ctx, txn) -> {
                    witnessScripts[0] = txn.getWitnessSet().getPlutusV3Scripts() == null
                            ? 0 : txn.getWitnessSet().getPlutusV3Scripts().size();
                    refInputs[0] = txn.getBody().getReferenceInputs() == null
                            ? 0 : txn.getBody().getReferenceInputs().size();
                })
                .completeAndWait(log::info);
        assertThat(result.isSuccessful())
                .as("a transfer using referenced scripts must still validate: %s", result.getResponse())
                .isTrue();
        log.info("witness scripts {} ref inputs {}", witnessScripts[0], refInputs[0]);

        assertThat(witnessScripts[0])
                .as("the base script and the transfer delegate are published, so neither should be"
                        + " in the witness set — only a substandard the chain has not published")
                .isLessThanOrEqualTo(1);
        assertThat(refInputs[0])
                .as("coordination + registry node + the two referenced scripts, at least")
                .isGreaterThanOrEqualTo(4);
    }

    // --------------------------------------------------------------- step 15

    /**
     * Change a registered token's mutable rules in place: set its unfracking hook.
     *
     * <p>Before the update, unfracking the token must be refused at build time — its hook is the
     * empty-key sentinel the registration left, and no chain access is needed to say so. After the
     * update, re-reading the node proves the datum actually changed and the frozen fields did not.</p>
     */
    @Test
    @Order(15)
    void step15_updateRegistryNodeToAllowUnfracking() {
        requireDeployment("Registry node update");
        requireExamplePolicyId();

        RegistryNode current = protocolService.getRegistryNode(examplePolicyId).getValue();
        assertThat(RegistryNodeSpec.isUnfrackingForbidden(current.getUnfrackingLogicScript()))
                .as("the registration left the hook unset").isTrue();

        assertThatThrownBy(() -> build(new ProgrammableTokenTx()
                .from(account.baseAddress())
                .unfrack(examplePolicyId, PlutusData.unit())).build())
                .as("unfracking a token whose issuer never set a hook is refused before chain access")
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("forbidden for policy " + examplePolicyId);

        RegistryNode updated = current.toBuilder()
                .unfrackingLogicScript(AlwaysTrueScripts.credential())
                .build();
        log.info("=== Registry node update === unfracking hook {} -> {}",
                hex(current.getUnfrackingLogicScript()), hex(updated.getUnfrackingLogicScript()));

        Result<String> result = build(new ProgrammableTokenTx()
                .from(account.baseAddress())
                .updateRegistry(examplePolicyId, Cip113RegistryUpdate.from(updated),
                        BigIntPlutusData.of(0)))   // alwaysTrue ignores it
                .completeAndWait(log::info);
        assertThat(result.isSuccessful())
                .as("A registry-node update must validate: no node NFT minted, one continuing"
                        + " output at the same address, frozen fields unchanged, minting logic"
                        + " withdrawn-zero: %s", result.getResponse())
                .isTrue();

        RegistryNode reread = protocolService.getRegistryNode(examplePolicyId).getValue();
        assertThat(reread.getUnfrackingLogicScript())
                .as("the mutable field must have actually changed on chain")
                .isEqualTo(AlwaysTrueScripts.credential());
        assertThat(reread.getKey()).isEqualToIgnoringCase(current.getKey());
        assertThat(reread.getNext()).isEqualToIgnoringCase(current.getNext());
        assertThat(reread.getMintingLogicScript()).isEqualTo(current.getMintingLogicScript());
        assertThat(reread.getGlobalStateCs()).isEqualTo(current.getGlobalStateCs());
    }

    // --------------------------------------------------------------- step 16

    /**
     * Transfer to a different owner's smart wallet — two intents aggregated into one transaction,
     * each writing an inline datum on the receiving output — and check both balances moved.
     */
    @Test
    @Order(16)
    void step16_transferToAnotherOwnerWithDatums() {
        requireDeployment("Cross-owner transfer");
        String unit = unitOf(requireExamplePolicyId());

        Account recipient = new Account(network);
        Address recipientAddress = new Address(recipient.baseAddress());
        BigInteger senderBefore = programmableQuantity(unit);
        assertThat(senderBefore).isGreaterThanOrEqualTo(BigInteger.valueOf(3));
        assertThat(programmableQuantity(recipientAddress, unit)).isZero();

        PlutusData memo = ConstrPlutusData.of(0, BigIntPlutusData.of(7), BigIntPlutusData.of(113));
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(recipient.baseAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE),
                        BigIntPlutusData.of(0), memo)
                .transfer(recipient.baseAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.TWO),
                        BigIntPlutusData.of(0), memo);
        log.info("=== Cross-owner transfer === 1 + 2 of {} from {} to {}", unit,
                protocolService.smartWalletAddress(ownerAddress).toBech32(),
                protocolService.smartWalletAddress(recipientAddress).toBech32());

        Result<String> result = build(tx).completeAndWait(log::info);
        assertThat(result.isSuccessful()).as("cross-owner transfer: %s", result.getResponse()).isTrue();

        assertThat(programmableQuantity(unit)).isEqualTo(senderBefore.subtract(BigInteger.valueOf(3)));
        assertThat(programmableQuantity(recipientAddress, unit)).isEqualTo(BigInteger.valueOf(3));
        List<Utxo> received = protocolService.getUtxos(recipientAddress).getValue();
        assertThat(received).as("one output per transfer intent").hasSize(2);
        assertThat(received).allSatisfy(utxo -> assertThat(utxo.getInlineDatum())
                .as("the datum declared on the transfer is written on the receiving output")
                .isEqualToIgnoringCase(memo.serializeToHex()));
    }

    // --------------------------------------------------------------- step 17

    /**
     * Unfrack a shared UTxO: the example token minted together with an unrelated native asset into
     * one output — what a freeze scoped to the token would lock as a whole — is split so the token
     * sits in its own output and the other asset stays where it was.
     */
    @Test
    @Order(17)
    void step17_unfrackASharedUtxo() throws Exception {
        requireDeployment("Unfracking");
        String unit = unitOf(requireExamplePolicyId());
        Address smartWallet = protocolService.smartWalletAddress(ownerAddress);
        BigInteger heldBefore = programmableQuantity(unit);

        // A dust asset under the owner's own key, minted straight into the smart wallet together
        // with the example token. Output merging is what puts both into one UTxO; every other
        // programmable-token build keeps it off.
        ScriptPubkey dustPolicy = new ScriptPubkey(HexUtil.encodeHexString(
                account.hdKeyPair().getPublicKey().getKeyHash()));
        String dustUnit = dustPolicy.getPolicyId() + "44757374";
        Tx frackingTx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .mint(examplePolicyId, account.baseAddress(), List.of(exampleAsset(FRACKED_QUANTITY)),
                        PlutusData.unit(), null)
                .mintAssets(dustPolicy, new Asset("0x44757374", BigInteger.ONE), smartWallet.toBech32());
        Result<String> fracking = build(frackingTx).mergeOutputs(true).completeAndWait(log::info);
        assertThat(fracking.isSuccessful()).as("minting the shared UTxO: %s", fracking.getResponse()).isTrue();

        Utxo fracked = smartWalletUtxos().stream()
                .filter(u -> holds(u, unit) && holds(u, dustUnit))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the mint did not produce a shared UTxO"));
        assertThat(quantity(fracked.getAmount(), unit)).isEqualTo(FRACKED_QUANTITY);
        log.info("=== Unfracking === shared UTxO {}#{} holds {} + dust", fracked.getTxHash(),
                fracked.getOutputIndex(), unit);

        StringBuilder shape = new StringBuilder();
        Result<String> result = build(new ProgrammableTokenTx()
                .from(account.baseAddress())
                .unfrack(examplePolicyId, PlutusData.unit()))   // alwaysTrue hook ignores it
                .preBalanceTx((ctx, txn) -> shape.append(describe("before balancing", txn)))
                .completeAndWait(log::info);
        log.info("{}", shape);
        assertThat(result.isSuccessful())
                .as("An unfracking must validate: SpendViaUnfracking on every shared input, the"
                        + " unfracking delegate and the hook withdrawn-zero, each input paired with"
                        + " a byte-identical continuing output minus the acted policy, and the"
                        + " policy regrouped at the owner: %s", result.getResponse())
                .isTrue();

        List<Utxo> after = smartWalletUtxos();
        assertThat(programmableQuantity(unit))
                .as("unfracking conserves the holding").isEqualTo(heldBefore.add(FRACKED_QUANTITY));
        assertThat(after).as("the shared UTxO was spent")
                .noneMatch(u -> u.getTxHash().equals(fracked.getTxHash())
                        && u.getOutputIndex() == fracked.getOutputIndex());
        assertThat(after).as("the dust stays behind in a continuing output without the token")
                .anyMatch(u -> holds(u, dustUnit) && !holds(u, unit));
        assertThat(after).as("the token is regrouped into its own single-policy output")
                .anyMatch(u -> holds(u, unit) && !holds(u, dustUnit)
                        && FRACKED_QUANTITY.equals(quantity(u.getAmount(), unit)));

        assertThatThrownBy(() -> build(new ProgrammableTokenTx()
                .from(account.baseAddress())
                .unfrack(examplePolicyId, PlutusData.unit())).build())
                .as("with nothing shared left, a second unfracking has nothing to do")
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("Nothing to unfrack");
    }

    // --------------------------------------------------------------- step 18

    /** Transaction shapes the contract cannot express are refused before any UTxO is selected. */
    @Test
    @Order(18)
    void step18_invalidCompositionsFailBeforeChainAccess() {
        requireDeployment("Composition guards");
        requireExamplePolicyId();
        Address holder = ownerAddress;
        Address recipient = new Address(new Account(network).baseAddress());
        String otherPolicy = "ab".repeat(28);

        assertThatThrownBy(() -> build(new ProgrammableTokenTx()
                .from(account.baseAddress())
                .thirdPartyTransfer(holder.getAddress(), recipient.getAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE), BigIntPlutusData.of(0))
                .thirdPartyTransfer(holder.getAddress(), recipient.getAddress(),
                        Amount.asset(otherPolicy, EXAMPLE_ASSET_NAME, BigInteger.ONE), BigIntPlutusData.of(0)))
                .build())
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("exactly one policy");

        assertThatThrownBy(() -> build(new ProgrammableTokenTx()
                .from(account.baseAddress())
                .burn(examplePolicyId, List.of(exampleAsset(BigInteger.ONE)),
                        BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(0)))
                .burn(examplePolicyId, List.of(exampleAsset(BigInteger.ONE)),
                        BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(1))))
                .build())
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("different issuance redeemers");
    }

    // --------------------------------------------------------- TxPlan YAML path

    /**
     * Execute the portable TxPlan path, including its YAML boundary, against the devnet. The
     * restored transaction is a plain {@link Tx} carrying a semantic extension intent, so a
     * successful submission proves the runtime extension owns materialization.
     */
    @Test
    @Order(10)
    void txPlanYaml_executesProgrammableTokenIntentAfterRestore() throws Exception {
        requireDeployment("TxPlan YAML transfer");
        String unit = unitOf(requireExamplePolicyId());

        BigInteger balanceBefore = programmableQuantity(unit);
        Set<String> utxosBefore = smartWalletUtxoRefs();
        assertThat(balanceBefore).as("need supply for the YAML transfer").isGreaterThan(BigInteger.ZERO);

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(account.baseAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE),
                        BigIntPlutusData.of(0));
        TxPlan authoredPlan = programmableTokens.extension().configure(
                TxPlan.from(tx).feePayer(account.baseAddress()));
        TxPlanCodec codec = TxPlanCodec.builder()
                .withExtension(ProgrammableTokenExtension.DEFAULT_NAMESPACE,
                        programmableTokens.extension())
                .build();

        String yaml = codec.toYaml(authoredPlan);
        ObjectNode yamlTree = (ObjectNode) YamlSerializer.getYamlMapper().readTree(yaml);
        ObjectNode variables = (ObjectNode) yamlTree.get("variables");
        if (variables == null) {
            variables = YamlSerializer.getYamlMapper().createObjectNode();
            yamlTree.set("variables", variables);
        }
        variables.put("transfer_redeemer_cbor", BigIntPlutusData.of(0).serializeToHex());
        ObjectNode yamlIntent = (ObjectNode) yamlTree.withArray("transaction").get(0)
                .get("tx").withArray("intents").get(0);
        yamlIntent.remove("transfer_redeemer");
        yamlIntent.put("transfer_redeemer_hex", "${transfer_redeemer_cbor}");
        yaml = YamlSerializer.getYamlMapper().writeValueAsString(yamlTree);
        assertThat(yaml)
                .contains("extension: programmable-token")
                .contains("protocol: cip-113")
                .contains("type: pt:transfer")
                .contains("transfer_redeemer_hex: \"${transfer_redeemer_cbor}\"");

        TxPlan restoredPlan = codec.fromYaml(yaml);
        assertThat(restoredPlan.getTxs()).singleElement().isExactlyInstanceOf(Tx.class);

        Result<String> result = new QuickTxBuilder(backendService)
                .withExtension(programmableTokens.extension())
                .compose(restoredPlan)
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(evaluator())
                .completeAndWait(message -> log.debug("{}", message));
        assertThat(result.isSuccessful())
                .as("submitting a programmable-token TxPlan restored from YAML: %s", result.getResponse())
                .isTrue();
        assertThat(programmableQuantity(unit))
                .as("a YAML-authored self-transfer must conserve the programmable-token balance")
                .isEqualTo(balanceBefore);
        assertThat(smartWalletUtxoRefs())
                .as("the YAML-restored transfer must consume a smart-wallet UTxO")
                .isNotEqualTo(utxosBefore);
    }

    // --------------------------------------------------------------- step 19

    /**
     * Reference inputs the caller adds with {@code readFrom(...)} are ordinary {@code Tx} intents.
     * They land in the body next to the protocol's own (coordination UTxO, registry node, published
     * scripts), naming one the protocol already reads adds nothing twice, and every CIP-113 index
     * is computed over the ledger-sorted union of both — so the transaction validates on chain.
     */
    @Test
    @Order(19)
    void step19_callerReferenceInputsAreAppliedAsUsual() {
        requireDeployment("Caller reference inputs");
        String unit = unitOf(requireExamplePolicyId());
        BigInteger before = programmableQuantity(unit);

        // A UTxO at an address the protocol knows nothing about and nothing here spends.
        Address bystander = AddressProvider.getEntAddress(Credential.fromKey("22".repeat(28)), network);
        Result<String> funding = new QuickTxBuilder(backendService)
                .compose(new Tx().payToAddress(bystander.toBech32(), Amount.ada(2)).from(account.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(log::info);
        assertThat(funding.isSuccessful()).as("funding the bystander: %s", funding.getResponse()).isTrue();
        Utxo bystanderUtxo = new DefaultUtxoSupplier(backendService.getUtxoService())
                .getAll(bystander.toBech32()).stream().findFirst()
                .orElseThrow(() -> new AssertionError("the bystander UTxO is not visible"));
        Utxo coordination = protocolService.coordinationUtxo();

        List<TransactionInput> referenced = new ArrayList<>();
        Tx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(account.baseAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE),
                        BigIntPlutusData.of(0))
                .readFrom(bystanderUtxo)
                .readFrom(coordination);    // the protocol reads this one itself
        Result<String> result = build(tx)
                .postBalanceTx((ctx, txn) -> referenced.addAll(txn.getBody().getReferenceInputs()))
                .completeAndWait(log::info);
        assertThat(result.isSuccessful())
                .as("transfer with caller reference inputs: %s", result.getResponse()).isTrue();
        log.info("=== Caller reference inputs === body carries {} reference inputs", referenced.size());

        assertThat(referenced).as("the caller's reference input is in the body")
                .contains(new TransactionInput(bystanderUtxo.getTxHash(), bystanderUtxo.getOutputIndex()));
        assertThat(referenced).as("a reference input the protocol already reads is added once")
                .filteredOn(in -> in.getTransactionId().equals(coordination.getTxHash())
                        && in.getIndex() == coordination.getOutputIndex())
                .hasSize(1);
        assertThat(referenced).doesNotHaveDuplicates();
        assertThat(programmableQuantity(unit)).as("a self-transfer conserves the holding").isEqualTo(before);
    }

    /** An account's stake key hash, read from its base address's delegation credential. */
    private static byte[] stakeKeyHashOf(Account candidate) {
        return new Address(candidate.baseAddress()).getDelegationCredentialHash()
                .orElseThrow(() -> new IllegalStateException(
                        "Base address has no delegation credential: " + candidate.baseAddress()));
    }

    private static String hex(com.bloxbean.cardano.client.address.Credential credential) {
        return HexUtil.encodeHexString(credential.getBytes());
    }

    private static boolean holds(Utxo utxo, String unit) {
        return quantity(utxo.getAmount(), unit).signum() > 0;
    }

    private static BigInteger quantity(List<Amount> amounts, String unit) {
        return amounts.stream()
                .filter(a -> unit.equals(a.getUnit()))
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
