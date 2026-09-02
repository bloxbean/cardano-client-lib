package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.model.AccountInformation;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Registration;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113RegistryUpdate;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
 * the devnet, funds the standard DevKit account, and deploys the whole CIP-113 protocol itself.</p>
 *
 * <h2>What it proves</h2>
 * <p>Steps 0-5 stand the protocol up and read it back. Steps 6-9 are the end-to-end path a real
 * user walks: register a token, mint it, transfer it, and check the balance moved as expected.
 * Because the chain is reset each run, every step starts from a known state — no accumulated
 * supply, no half-registered credentials, no registry left over from a previous attempt.</p>
 *
 * <p>Scripts are evaluated locally with Aiken rather than through the backend: a remote evaluator
 * that cannot build an evaluation context returns an empty {@code ScriptFailures} map, which names
 * neither the script nor the reason.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ResourceLock("yaci-devkit")
public class Cip113EndToEndIT {

    private static BackendService backendService;
    private static Account account;
    private static Address ownerAddress;
    private static Cip113ProtocolService tokenService;

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

    /** "txHash#index" of every UTxO at the smart wallet right now. */
    private static Set<String> smartWalletUtxoRefs() {
        Result<List<Utxo>> utxos = tokenService.getUtxos(ownerAddress);
        Set<String> refs = new java.util.LinkedHashSet<>();
        if (utxos.isSuccessful()) {
            utxos.getValue().forEach(u -> refs.add(u.getTxHash().substring(0, 8) + "#" + u.getOutputIndex()));
        }
        return refs;
    }

    /** How much of a unit the smart wallet currently holds, re-reading the chain each time. */
    private static BigInteger programmableQuantity(String unit) {
        Result<List<Amount>> balance = tokenService.getProgrammableBalance(ownerAddress);
        if (!balance.isSuccessful()) return BigInteger.ZERO;
        return quantity(balance.getValue(), unit);
    }

    /**
     * Evaluate scripts locally.
     *
     * <p>A remote evaluator that cannot build an evaluation context returns an empty
     * {@code ScriptFailures} map — no script name, no reason — which says nothing about what is
     * wrong. Aiken's evaluator runs the validators in-process and names the one that failed.</p>
     */
    private static com.bloxbean.cardano.aiken.AikenTransactionEvaluator evaluator() {
        return new com.bloxbean.cardano.aiken.AikenTransactionEvaluator(backendService);
    }

    /** The example token as a mintable {@link Asset}; negative quantity burns. */
    private static Asset exampleAsset(BigInteger quantity) {
        return new Asset("0x" + com.bloxbean.cardano.client.util.HexUtil.encodeHexString(
                EXAMPLE_ASSET_NAME.getBytes(java.nio.charset.StandardCharsets.UTF_8)), quantity);
    }

    private static String unitOf(String policyId) {
        return policyId + com.bloxbean.cardano.client.util.HexUtil.encodeHexString(
                EXAMPLE_ASSET_NAME.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @BeforeAll
    static void setup() {
        if (!DevNet.isRunning()) {
            System.out.println("\nNo devnet is listening on " + DevNet.BACKEND_URL + ".");
            System.out.println("Start one with `yaci-devkit up` and re-run — this suite needs"
                    + " nothing else: no API key, no funded account, no environment variables.");
            Assumptions.abort("No devnet running on " + DevNet.BACKEND_URL);
        }

        backendService = new BFBackendService(DevNet.BACKEND_URL, "Dummy");
        network = Networks.testnet();
        account = new Account(network, SENDER_MNEMONIC);
        ownerAddress = new Address(account.baseAddress());

        System.out.println("\n=== CIP-113 end-to-end test ===");
        System.out.println("devnet  : " + DevNet.BACKEND_URL);
        System.out.println("account : " + account.baseAddress());

        // Start from an empty chain every run. The protocol is redeployed in step 0, so the whole
        // suite is reproducible and nothing carries over between runs — no accumulated supply, no
        // half-registered credentials, no stale registry.
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
     * UTxOs through the indexer — so without this the suite races its own funding and step 0 fails
     * with "found 1" even though both top-ups succeeded. Whether it passed depended purely on how
     * fast the devnet indexed, which is exactly the kind of intermittent failure that gets blamed
     * on the code under test.</p>
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
                System.out.println("Funded: " + seen + " seed UTxOs visible");
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

    /** The standard Yaci DevKit funded account. */
    private static final String SENDER_MNEMONIC =
            "test test test test test test test test test test test test"
            + " test test test test test test test test test test test sauce";

    /**
     * Skip a step that needs a funded account, saying so loudly.
     *
     * <p>Gradle's console prints a bare {@code SKIPPED} and swallows the assumption message, so
     * the reason has to go to stdout or it is invisible.</p>
     */
    /**
     * Resolve the deployment on demand.
     *
     * <p>Lets any step run on its own — {@code --tests '*Cip113EndToEndIT.step9_*'} — instead of
     * only as part of the full ordered suite. Iterating on the transfer should not mean
     * re-registering and re-minting a token every time.</p>
     */
    private static void requireDeployment(String what) {
        if (tokenService == null) {
            System.out.println("\n=== " + what + " — SKIPPED ===");
            System.out.println("No deployment — the chain is reset every run, so the suite has to"
                    + " run as a whole. Drop the --tests method filter.");
            Assumptions.abort("No deployment");
        }
        if (resolved == null) {
            Result<Cip113Deployment> result = tokenService.resolveDeployment();
            if (result.isSuccessful()) {
                resolved = result.getValue();
                System.out.println("(resolved the deployment on demand for: " + what + ")");
            }
        }
        if (resolved == null) {
            System.out.println("\n=== " + what + " — SKIPPED ===");
            System.out.println("The deployment could not be resolved, so nothing downstream can run.");
            Assumptions.abort("Deployment not resolved");
        }
    }

    /** The example token's policy, derived on demand so later steps do not depend on step 7. */
    private static String requireExamplePolicyId() {
        if (examplePolicyId == null) {
            Result<String> derived = tokenService.derivePolicyId(AlwaysTrueScripts.credential());
            if (derived.isSuccessful()) examplePolicyId = derived.getValue();
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
        System.out.println("\n=== Deploying the CIP-113 protocol ===");
        deployed = new Cip113Bootstrap().deploy(backendService, account, network);
        Cip113Bootstrap.describe(deployed).forEach(l -> System.out.println("  " + l));

        // Everything downstream resolves from the bootstrap transaction, exactly as it would for a
        // deployment someone else created — so this also exercises the discovery path.
        // Nothing but the bootstrap transaction hash: every other value, the registry address
        // included, is discovered from chain. The domain service composes with QuickTx through
        // its extension; the backend itself remains an ordinary BackendService.
        programmableTokens = Cip113ProgrammableTokenService.create(backendService,
                Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network));
        tokenService = programmableTokens.advanced();

        // Hand the service the scripts the bootstrap just applied. Nothing on chain has used them yet,
        // so no backend can serve them — and the steps below are what would reveal them.
        tokenService.scripts().registerAll(deployed.appliedScripts());

        // The example token's substandard, registered once for the whole run. Which logic scripts
        // a transaction invokes is read from the registry node, so no step below has to name it —
        // this is only here because an unused script is not yet on chain for a backend to serve.
        tokenService.scripts().register(AlwaysTrueScripts.ALWAYS_TRUE);

        Result<Cip113Deployment> resolvedResult = tokenService.resolveDeployment();
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
        Assumptions.assumeTrue(tokenService != null,
                "No deployment yet — on a devnet, step 0 creates it");

        Result<Cip113Deployment> result = tokenService.resolveDeployment();

        System.out.println("\n=== CIP-113 deployment ===");
        System.out.println("bootstrap tx      : " + tokenService.deployment().getBootstrapTxHash());

        if (!result.isSuccessful()) {
            System.out.println("resolution FAILED : " + result.getResponse());
            System.out.println("\nStep 0 deployed this protocol moments ago, so a failure here"
                    + " means the bootstrap wrote something the resolver cannot read back —"
                    + " the datum shape and the resolver have diverged.");
            Assumptions.abort("Deployment could not be resolved: " + result.getResponse());
        }

        resolved = result.getValue();
        System.out.println("params policy     : " + resolved.getParamsPolicy());
        System.out.println("base script hash  : " + resolved.getProgrammableLogicBaseHash());
        System.out.println("transfer delegate : " + resolved.getTransferScriptHash());
        System.out.println("thirdparty deleg. : " + resolved.getThirdPartyScriptHash());
        System.out.println("unfracking deleg. : " + resolved.getUnfrackingScriptHash());
        System.out.println("registry node cs  : " + resolved.getRegistryNodeCs());
        System.out.println("issuance tmpl cs  : " + resolved.getIssuanceCborHexCs());
        System.out.println("registry address  : " + resolved.registryAddress().toBech32());
        System.out.println("coordination utxo : " + tokenService.coordinationUtxo().getTxHash()
                + "#" + tokenService.coordinationUtxo().getOutputIndex());

        assertThat(resolved.getParamsPolicy()).isNotBlank();
        assertThat(resolved.getProgrammableLogicBaseHash()).isNotBlank();

    }

    // ---------------------------------------------------------------- step 2

    @Test
    @Order(2)
    void step2_deriveAddresses() {
        requireDeployment("Addresses");

        Address smartWallet = tokenService.smartWalletAddress(ownerAddress);

        System.out.println("\n=== Addresses ===");
        System.out.println("owner             : " + ownerAddress.getAddress());
        System.out.println("smart wallet      : " + smartWallet.toBech32());
        System.out.println("alwaysTrue hash   : " + AlwaysTrueScripts.scriptHash());
        System.out.println("alwaysTrue reward : "
                + AlwaysTrueScripts.rewardAddress(network).toBech32());

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

        Result<List<RegistryNode>> registry = tokenService.getRegistry();
        assertThat(registry.isSuccessful())
                .as("registry scan: %s", registry.getResponse())
                .isTrue();

        System.out.println("\n=== Registry (" + registry.getValue().size() + " node(s)) ===");
        for (RegistryNode node : registry.getValue()) {
            System.out.println("  key  " + node.getKey());
            System.out.println("  next " + node.getNext());
            System.out.println("       transfer=" + hex(node.getTransferLogicScript())
                    + " thirdParty=" + hex(node.getThirdPartyTransferLogicScript())
                    + " globalState=" + (node.hasGlobalState() ? node.getGlobalStateCs() : "none"));
        }
    }

    // ---------------------------------------------------------------- step 4

    @Test
    @Order(4)
    void step4_derivePolicyIdForAlwaysTrueToken() {
        requireDeployment("Example token");

        Result<String> policyId = tokenService.derivePolicyId(AlwaysTrueScripts.credential());

        System.out.println("\n=== Example token ===");
        if (!policyId.isSuccessful()) {
            System.out.println("policy id derivation FAILED: " + policyId.getResponse());
            Assumptions.abort(policyId.getResponse());
        }

        System.out.println("issuance credential : " + AlwaysTrueScripts.scriptHash());
        System.out.println("derived policy id   : " + policyId.getValue());
        System.out.println("\nThis is the policy an alwaysTrue-governed token would have on this"
                + " deployment. It is pure hashing — blake2b_224 over the issuance template with"
                + " the credential spliced in — so no UPLC applier is involved.");

        assertThat(policyId.getValue()).hasSize(56);

        Result<Boolean> registered = tokenService.isProgrammable(policyId.getValue());
        System.out.println("already registered  : " + registered.getValue());
    }

    // ---------------------------------------------------------------- step 5

    /**
     * Fetch the deployment's applied scripts by hash.
     *
     * <p>Every CIP-113 validator is parameterized, so a blueprint's hash never matches what is
     * deployed and the applied bytes have to come from somewhere. They are already on chain: a
     * Plutus script is revealed the first time it is used, so a backend can serve it by hash.
     * That removes the need for a UPLC parameter applier just to build a transaction.</p>
     */
    @Test
    @Order(5)
    void step5_resolveDeploymentScripts() throws Exception {
        requireDeployment("Script resolution");

        DeploymentScripts scripts = tokenService.scripts();

        System.out.println("\n=== Applied scripts ===");
        var base = scripts.programmableLogicBase();
        var transfer = scripts.transferDelegate();
        var registrySpend = scripts.registrySpend();
        var registryMint = scripts.registryMint();

        System.out.println("base script    : " + base.getPolicyId()
                + "  (" + base.getCborHex().length() / 2 + " bytes)");
        System.out.println("transfer       : " + transfer.getPolicyId()
                + "  (" + transfer.getCborHex().length() / 2 + " bytes)");
        System.out.println("registry_spend : " + registrySpend.getPolicyId());
        System.out.println("registry_mint  : " + registryMint.getPolicyId());

        // Fetched by hash, so a mismatch would mean the backend served the wrong script.
        assertThat(base.getPolicyId()).isEqualToIgnoringCase(resolved.getProgrammableLogicBaseHash());
        assertThat(transfer.getPolicyId()).isEqualToIgnoringCase(resolved.getTransferScriptHash());
        assertThat(registryMint.getPolicyId()).isEqualToIgnoringCase(resolved.getRegistryNodeCs());
    }

    // ---------------------------------------------------------------- step 5

    /**
     * Make sure the always-true script's reward account is registered, registering it if not.
     *
     * <p>A withdrawal is only valid against a <b>registered</b> stake credential — even a zero
     * one — so every CIP-113 substandard script needs its reward account registered before it can
     * ever be invoked via withdraw-zero. That makes this a hard prerequisite for registering a
     * token, for minting, and for any transfer whose transfer logic is this script.</p>
     *
     * <p>Registering a <i>script</i> stake credential needs no script witness: registration is
     * permissionless, and only delegation and deregistration make the script run. So this is a
     * plain {@link Tx} with a certificate and no validator attached — the same shape as
     * {@code StakeTxIT#scriptStakeAddress_registration}. It costs the key deposit (2 ADA on
     * the devnet), refunded on deregistration.</p>
     *
     * <p>Idempotent: if the account is already registered the step reports that and submits
     * nothing, so the suite can be re-run freely.</p>
     */
    @Test
    @Order(6)
    void step6_ensureAlwaysTrueRewardAccountIsRegistered() throws Exception {

        String rewardAddress = AlwaysTrueScripts.rewardAddress(network).toBech32();

        System.out.println("\n=== Always-true withdraw-zero script ===");
        System.out.println("script hash    : " + AlwaysTrueScripts.scriptHash());
        System.out.println("reward address : " + rewardAddress);

        // Printed for diagnostics only — see looksRegistered's note on why no backend answer here
        // is trustworthy enough to skip the work.
        looksRegistered(rewardAddress);

        System.out.println("status         : attempting registration");

        Result<String> result = new QuickTxBuilder(backendService)
                .compose(new Tx()
                        .registerStakeAddress(rewardAddress)
                        .from(account.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        if (result.isSuccessful()) {
            System.out.println("registered in tx: " + result.getValue());
            return;
        }

        // The ledger is the authority on this, and it disagrees with the backend often enough
        // that the error has to be interpreted rather than just failed on.
        if (alreadyRegistered(result.getResponse())) {
            System.out.println("status         : the ledger rejected this as already registered"
                    + " (or an identical registration already landed), so the account is usable"
                    + " and this step is a no-op.");
            return;
        }

        System.out.println("submit         : " + result.getResponse());
        assertThat(false)
                .as("registering the always-true reward account failed for a reason other than"
                        + " it already being registered: %s", result.getResponse())
                .isTrue();
    }

    /**
     * Reports what the backend thinks of this stake account. <b>Diagnostics only.</b>
     *
     * <p>No answer here is load-bearing, because no field means what it needs to mean.
     * Blockfrost's {@code active} tracks whether the credential <i>delegates to a pool</i>, not
     * whether it is registered — a script credential registered purely so its withdraw-zero is
     * valid never delegates, so it reads inactive forever. Yaci-store goes the other way and
     * answers 200 for an account it has never seen registered, which made this step skip the
     * registration and left the withdrawal to fail on chain with
     * {@code ConwayWithdrawalsMissingAccounts}.</p>
     *
     * <p>So the step registers unconditionally and reads the ledger's rejection instead: that is
     * the only authority on the question, and re-registering costs one rejected submission.</p>
     */
    private static boolean looksRegistered(String stakeAddress) {
        try {
            Result<AccountInformation> info =
                    backendService.getAccountService().getAccountInformation(stakeAddress);
            if (!info.isSuccessful() || info.getValue() == null) {
                System.out.println("account lookup : no account found — never registered ("
                        + info.getResponse() + ")");
                return false;
            }
            AccountInformation ai = info.getValue();
            System.out.println("account lookup : found; active=" + ai.getActive()
                    + " pool=" + ai.getPool_id()
                    + " controlled=" + ai.getControlledAmount());

            return true;
        } catch (Exception e) {
            System.out.println("account lookup : failed (" + e.getMessage() + ")");
            return false;
        }
    }

    /**
     * Whether a rejected submission means the account is already usable.
     *
     * <p>Two shapes mean that. {@code StakeKeyRegisteredDELEG} is the ledger saying the credential
     * is registered. {@code "All inputs are spent"} means an identical transaction already landed —
     * input selection is deterministic, so a re-run rebuilds the same registration byte for byte.
     * Neither is a failure of this step.</p>
     */
    private static boolean alreadyRegistered(String submitResponse) {
        if (submitResponse == null) return false;
        return submitResponse.contains("StakeKeyRegistered")
                || submitResponse.contains("All inputs are spent")
                || submitResponse.contains("already been included");
    }

    // ---------------------------------------------------------------- step 5b

    /**
     * Registering the example token itself.
     *
     * <p>The transaction spends the covering node, re-points its {@code next}, emits the new node,
     * mints the registry NFT named after the policy id, references the issuance template, and
     * includes the issuance credential's withdraw-zero — which step 6 has just made possible.</p>
     */
    @Test
    @Order(7)
    void step7_registerExampleToken() {
        requireDeployment("Token registration");

        Result<String> policyIdResult = tokenService.derivePolicyId(AlwaysTrueScripts.credential());
        assertThat(policyIdResult.isSuccessful()).isTrue();
        examplePolicyId = policyIdResult.getValue();

        System.out.println("\n=== Token registration ===");
        System.out.println("policy id      : " + examplePolicyId);

        if (tokenService.isProgrammable(examplePolicyId).getValue()) {
            System.out.println("status         : already registered — nothing to do");
            return;
        }

        // Register and mint the first supply in ONE transaction. issuance_mint cannot take its
        // RefInput proof here — the registry node it needs is an output of this very transaction,
        // not a reference input, and referencing a UTxO this transaction creates would be rejected
        // as non-disjoint anyway. So this is the OutputIndex proof, and it is the ordinary case:
        // a token's first mint naturally belongs with its registration.
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
        Result<String> result;
        try {
            result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                    .compose(tx)
                    .feePayer(account.baseAddress())
                    .withSigner(SignerProviders.signerFrom(account))
                    .withTxEvaluator(evaluator())
                    .preBalanceTx((ctx, txn) -> shape.append(describe("before balancing", txn)))
                    .completeAndWait(System.out::println);
        } catch (Exception e) {
            System.out.println(shape);
            throw e;
        }

        System.out.println(shape);
        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("registering the example token: %s", result.getResponse())
                .isTrue();

        System.out.println("registered in  : " + result.getValue());

        assertThat(programmableQuantity(unitOf(examplePolicyId)))
                .as("the first mint must have landed in the same transaction as the registration,"
                        + " which is only possible through issuance_mint's OutputIndex proof")
                .isEqualTo(FIRST_MINT_QUANTITY);
        System.out.println("first mint     : " + FIRST_MINT_QUANTITY + " minted alongside the node");
    }

    // ---------------------------------------------------------------- step 8

    /** Mint the example token into this account's own smart wallet. */
    @Test
    @Order(8)
    void step8_mintExampleToken() throws Exception {
        requireDeployment("Mint");
        Assumptions.assumeTrue(requireExamplePolicyId() != null,
                "Could not derive the example policy id");

        Assumptions.assumeTrue(tokenService.isProgrammable(examplePolicyId).getValue(),
                "Policy " + examplePolicyId + " is not registered yet — registration must land first");

        System.out.println("\n=== Mint ===");
        System.out.println("policy id      : " + examplePolicyId);
        System.out.println("into           : " + tokenService.smartWalletAddress(ownerAddress).toBech32());

        // Assert on the delta, not an absolute total: this step is meant to be re-runnable, and
        // every previous run left its own supply behind.
        BigInteger heldBefore = programmableQuantity(unitOf(examplePolicyId));
        System.out.println("balance before : " + heldBefore);

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .mint(examplePolicyId, account.baseAddress(), List.of(exampleAsset(MINT_QUANTITY)),
                        BigIntPlutusData.of(0), null);

        Result<String> result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(evaluator())
                .completeAndWait(System.out::println);

        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("minting the example token: %s", result.getResponse())
                .isTrue();

        BigInteger heldAfter = programmableQuantity(unitOf(examplePolicyId));
        System.out.println("balance after  : " + heldAfter);

        assertThat(heldAfter)
                .as("minting %s should raise the smart-wallet balance by exactly that much",
                        MINT_QUANTITY)
                .isEqualTo(heldBefore.add(MINT_QUANTITY));
    }

    // ---------------------------------------------------------------- step 6

    /**
     * Transfer a programmable token and check the balance moved.
     *
     * <p>Runs against whatever token the smart wallet already holds, so it is useful before
     * registration works: fund the smart wallet with any registered programmable token and this
     * exercises the whole transfer path — proofs, withdrawals, index resolution and all.</p>
     */
    @Test
    @Order(9)
    void step9_transferAndCheckBalance() throws Exception {
        requireDeployment("Transfer");

        Result<List<Amount>> before = tokenService.getProgrammableBalance(ownerAddress);
        assertThat(before.isSuccessful()).as("balance: %s", before.getResponse()).isTrue();

        // Conservation on its own is a weak proof — it would also hold if nothing happened. Record
        // which UTxOs back the balance now, so we can show the transfer actually consumed one.
        Set<String> utxosBefore = smartWalletUtxoRefs();

        System.out.println("\n=== Smart-wallet balance before ===");
        before.getValue().forEach(a -> System.out.println("  " + a.getUnit() + " " + a.getQuantity()));

        // Prefer the token this suite minted; fall back to anything else the wallet holds.
        requireExamplePolicyId();
        Optional<Amount> holding = before.getValue().stream()
                .filter(a -> examplePolicyId != null && a.getUnit().startsWith(examplePolicyId))
                .filter(a -> a.getQuantity().compareTo(BigInteger.valueOf(2)) >= 0)
                .findFirst()
                .or(() -> before.getValue().stream()
                        .filter(a -> a.getQuantity().compareTo(BigInteger.valueOf(2)) >= 0)
                        .findFirst());

        if (holding.isEmpty()) {
            System.out.println("\n=== Transfer — SKIPPED ===");
            System.out.println("The smart wallet at "
                    + tokenService.smartWalletAddress(ownerAddress).toBech32()
                    + " holds no programmable token to move.");
            System.out.println("Fund it with a token from one of the "
                    + tokenService.getRegistry().getValue().size() + " registered policies listed in step 3,"
                    + " or finish step 6 so this account can mint its own, then re-run.");
            Assumptions.abort("No programmable token in the smart wallet");
        }

        String unit = holding.get().getUnit();
        String policyId = unit.substring(0, 56);
        BigInteger amountToSend = BigInteger.ONE;

        System.out.println("\ntransferring two 1-unit outputs of " + unit);
        System.out.println("(sent to the same owner: the balance must be conserved, but every"
                + " validator on the transfer path still has to pass — which is what is being proved)");

        // Send it back to ourselves: the balance should be unchanged, but every validator on the
        // transfer path still has to pass. That isolates "does the machinery work" from "did the
        // right amount move", which is the thing worth learning first.
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(account.baseAddress(),
                        Amount.asset(policyId, assetName(unit), amountToSend),
                        BigIntPlutusData.of(0))
                .transfer(account.baseAddress(),
                        Amount.asset(policyId, assetName(unit), amountToSend),
                        BigIntPlutusData.of(0));   // alwaysTrue ignores it

        // Capture the transaction as balancing sees it. When evaluation fails there is no
        // result object to inspect, and Blockfrost's "empty ScriptFailures" says nothing, so the
        // shape has to be recorded on the way through.
        StringBuilder shape = new StringBuilder();

        Result<String> result;
        try {
            result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                    .compose(tx)
                    .feePayer(account.baseAddress())
                    .withSigner(SignerProviders.signerFrom(account))
                    .withTxEvaluator(evaluator())
                    .preBalanceTx((ctx, txn) -> shape.append(describe("before balancing", txn)))
                    .postBalanceTx((ctx, txn) -> shape.append(describe("after balancing", txn)))
                    .completeAndWait(System.out::println);
        } catch (Exception e) {
            System.out.println(shape);
            System.out.println("\nBuild failed: " + e.getMessage());
            System.out.println("\nIf the input count grew between the two dumps above, balancing"
                    + " appended an input and renumbered the canonical ordering. The builder funds"
                    + " itself to the protocol's maximum possible fee precisely so that cannot"
                    + " happen, so this would mean the fee ceiling was computed too low — not that"
                    + " a buffer needs raising.");
            throw e;
        }

        System.out.println(shape);
        System.out.println("\nsubmit: " + result.getResponse());
        assertThat(result.isSuccessful()).as("transfer: %s", result.getResponse()).isTrue();

        Result<List<Amount>> after = tokenService.getProgrammableBalance(ownerAddress);
        System.out.println("\n=== Smart-wallet balance after ===");
        after.getValue().forEach(a -> System.out.println("  " + a.getUnit() + " " + a.getQuantity()));

        BigInteger beforeQty = quantity(before.getValue(), unit);
        BigInteger afterQty = quantity(after.getValue(), unit);
        assertThat(afterQty)
                .as("a self-transfer must conserve the holding")
                .isEqualTo(beforeQty);

        Set<String> utxosAfter = smartWalletUtxoRefs();
        System.out.println("\nsmart-wallet UTxOs before: " + utxosBefore);
        System.out.println("smart-wallet UTxOs after : " + utxosAfter);

        assertThat(utxosAfter)
                .as("the transfer must actually have spent a base-script UTxO, not just submitted"
                        + " something that left the wallet untouched")
                .isNotEqualTo(utxosBefore);

        // Nothing above named a substandard script: which logic runs is read from the registry
        // node. This is the other half of that claim — that a caller who deployed nothing and
        // registered nothing can still resolve it, because using a script reveals it on chain.
        // A resolver with an empty local cache, backed only by the backend, must find it.
        DeploymentScripts backendOnly =
                new DeploymentScripts(backendService.getScriptService(), resolved);
        System.out.println("\nsubstandard resolvable from the backend alone: "
                + backendOnly.getScript(AlwaysTrueScripts.scriptHash()).isPresent());
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
            sorted.sort(java.util.Comparator.comparing(
                    com.bloxbean.cardano.client.transaction.spec.MultiAsset::getPolicyId));
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
     * A service nobody initialised must still work.
     *
     * <p>Everything the service does needs values that only exist after the bootstrap transaction
     * has been walked. Leaving that to the caller made correct use depend on call order, and the
     * failures were late and misleading — a null coordination UTxO surfaces as a script error deep
     * in evaluation, and a registry lookup built before resolution binds to a deployment whose
     * registry policy is null and scans for it forever.</p>
     *
     * <p>So this builds a completely fresh service from nothing but a bootstrap tx hash,
     * <b>never calls {@code resolveDeployment()}</b>, and goes straight to using it.</p>
     */
    @Test
    @Order(10)
    void step10_freshServiceResolvesItself() {
        requireDeployment("Lazy initialisation");

        Cip113ProtocolService fresh = Cip113ProgrammableTokenService.create(backendService,
                Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network)).advanced();

        System.out.println("\n=== A service that was never resolved ===");

        // Each of these is a first call on an unresolved service.
        assertThat(fresh.coordinationUtxo())
                .as("the coordination UTxO must resolve on demand")
                .isNotNull();
        assertThat(fresh.issuanceTemplateUtxo())
                .as("the issuance template must resolve on demand")
                .isNotNull();
        System.out.println("coordination utxo : " + fresh.coordinationUtxo().getTxHash()
                + "#" + fresh.coordinationUtxo().getOutputIndex());

        // The registry lookup is the one that used to bind to an unresolved deployment forever.
        Result<List<RegistryNode>> registry = fresh.getRegistry();
        assertThat(registry.isSuccessful())
                .as("registry scan on an unresolved service: %s", registry.getResponse())
                .isTrue();
        System.out.println("registry nodes    : " + registry.getValue().size());
        assertThat(registry.getValue())
                .as("the registry must contain the node registered in step 7, which means the scan"
                        + " ran against the resolved deployment rather than a null policy")
                .hasSizeGreaterThan(1);

        // Derived values must match the service that was resolved explicitly.
        assertThat(fresh.smartWalletAddress(ownerAddress).toBech32())
                .isEqualTo(tokenService.smartWalletAddress(ownerAddress).toBech32());
        assertThat(fresh.deployment().getProgrammableLogicBaseHash())
                .isEqualToIgnoringCase(resolved.getProgrammableLogicBaseHash());

        Result<Boolean> programmable = fresh.isProgrammable(requireExamplePolicyId());
        assertThat(programmable.isSuccessful()).isTrue();
        assertThat(programmable.getValue())
                .as("the example policy must be visible to a service nobody initialised")
                .isTrue();

        assertThat(new Cip113ProgrammableTokenService(fresh).extension()).isNotNull();
        System.out.println("status            : usable with no resolveDeployment() call");
    }

    // --------------------------------------------------------------- step 11

    /**
     * Settle the withdrawal-ordering question against a real ledger.
     *
     * <p>Every CIP-113 redeemer points at a withdrawal by <b>index into the ledger's own ordering
     * of the withdrawal map</b>. {@link LedgerOrdering} claims that ordering puts <i>every script
     * credential before every key credential</i>, then sorts bytewise within each kind — derived
     * from cardano-ledger's {@code Credential} deriving {@code Ord} with {@code ScriptHashObj}
     * declared first. CCL's own {@code WithdrawalUtil} instead sorts by credential hash alone.
     * Where the two disagree, the {@code Reward} redeemer indices are wrong and the core transfer
     * validator receives the substandard's redeemer.</p>
     *
     * <p>Steps 0-9 never test this: their two withdrawals are <i>both</i> script credentials, so
     * both orderings agree. This adds a <b>key</b>-credential withdrawal whose hash sorts
     * <i>before</i> the script hashes, which is exactly the case where hash-only ordering and
     * scripts-first ordering disagree — hash-only would place it at index 0 and shift both script
     * redeemers by one. If the claim is wrong, the transaction fails.</p>
     */
    @Test
    @Order(11)
    void step11_withdrawalOrderingWithMixedCredentials() throws Exception {
        String policyId = requireExamplePolicyId();

        // The two script credentials already in play, and the lowest byte among them.
        byte lowestScript = (byte) Math.min(
                Integer.parseInt(resolved.getTransferScriptHash().substring(0, 2), 16),
                Integer.parseInt(AlwaysTrueScripts.scriptHash().substring(0, 2), 16));

        // Find a stake key whose hash sorts strictly before both script hashes. Only then do the
        // two candidate orderings actually differ, so only then is this test worth anything.
        // Fresh accounts, not derived indices: CIP-1852 keeps one stake key per *account*, so
        // varying the address index yields the same stake key every time. This key never holds
        // funds — the sender pays the deposit and the fee; it only has to sign its own withdrawal.
        // The stake key hash comes from the base address's delegation part: a reward address
        // carries a single credential, so its "delegation" slot is empty.
        Account discriminating = null;
        for (int i = 0; i < 60 && discriminating == null; i++) {
            Account candidate = new Account(network);
            byte[] stakeHash = stakeKeyHashOf(candidate);
            if ((stakeHash[0] & 0xff) < (lowestScript & 0xff)) discriminating = candidate;
        }

        System.out.println("\n=== Withdrawal ordering: script vs key credential ===");
        if (discriminating == null) {
            System.out.println("No derived stake key sorts before the script credentials, so the"
                    + " two orderings cannot be told apart on this deployment. Skipping rather"
                    + " than passing vacuously.");
            Assumptions.abort("No discriminating stake key found");
        }

        String rewardAddress = discriminating.stakeAddress();
        byte[] stakeHash = stakeKeyHashOf(discriminating);
        System.out.println("key credential : " + HexUtil.encodeHexString(stakeHash));
        System.out.println("script creds   : " + resolved.getTransferScriptHash()
                + ", " + AlwaysTrueScripts.scriptHash());
        System.out.println("hash-only order would put the key FIRST; scripts-first puts it LAST.");

        // A key-credential withdrawal is only valid once the account is registered.
        // The stake key signer is declared even though the certificate does not require it: the
        // fee estimate is derived from the declared signers, and registering a *key* credential
        // produces a witness the estimate otherwise misses (FeeTooSmallUTxO).
        Result<String> reg = new QuickTxBuilder(backendService)
                .compose(new Tx()
                        .registerStakeAddress(rewardAddress)
                        // Conway rejects a withdrawal from an account that has not delegated its
                        // voting power (ConwayWdrlNotDelegatedToDRep) — even a zero one.
                        .delegateVotingPowerTo(new Address(rewardAddress), DRep.abstain())
                        .from(account.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account))
                .withSigner(com.bloxbean.cardano.client.function.helper.SignerProviders
                        .stakeKeySignerFrom(discriminating))
                // Pads the fee estimate. Registering a *key* stake credential on this devnet is
                // consistently under-estimated by a flat ~25,377 lovelace, independent of tx size
                // (see the run log); padding is a test-harness workaround, not a library fix.
                .additionalSignersCount(6)
                .completeAndWait(System.out::println);
        if (!reg.isSuccessful() && !alreadyRegistered(reg.getResponse())) {
            System.out.println("registration   : " + reg.getResponse());
            assertThat(false).as("could not register the discriminating stake key: %s",
                    reg.getResponse()).isTrue();
        }

        // A programmable transfer that also carries a zero withdrawal from that key account.
        Tx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .transfer(ownerAddress.getAddress(),
                        Amount.asset(policyId, EXAMPLE_ASSET_NAME, 1), BigIntPlutusData.of(0))
                .withdraw(rewardAddress, BigInteger.ZERO);

        Result<String> result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withSigner(com.bloxbean.cardano.client.function.helper.SignerProviders
                        .stakeKeySignerFrom(discriminating))
                .withTxEvaluator(evaluator())
                .completeAndWait(System.out::println);

        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("A transfer carrying both script and key withdrawals must validate. A failure"
                        + " here means the Reward redeemer indices were computed against the wrong"
                        + " withdrawal ordering — i.e. the ledger does NOT sort script credentials"
                        + " before key credentials, and LedgerOrdering is wrong: %s",
                        result.getResponse())
                .isTrue();
        System.out.println("status         : ledger confirms script credentials sort before key"
                + " credentials — LedgerOrdering is correct and WithdrawalUtil's hash-only"
                + " comparator would have been wrong here.");
    }

    // --------------------------------------------------------------- step 12

    /**
     * Burn part of the minted supply.
     *
     * <p>Proves the burn is a real transfer-path spend, not a negative mint: the holder's
     * base-script UTxOs are consumed through {@code programmable_logic_base} under
     * {@code SpendViaTransfer}, {@code validate_transfer} folds the negative mint into the input
     * side for a policy present in those inputs, and the remainder comes back to the smart
     * wallet. A balance that drops by exactly the burned amount, with the rest still held, is
     * what distinguishes that from "the tokens went somewhere".</p>
     */
    @Test
    @Order(12)
    void step12_burnExampleToken() throws Exception {
        requireDeployment("Burn");
        requireExamplePolicyId();

        Result<List<Amount>> before = tokenService.getProgrammableBalance(ownerAddress);
        assertThat(before.isSuccessful()).as("balance: %s", before.getResponse()).isTrue();

        String unit = examplePolicyId + com.bloxbean.cardano.client.util.HexUtil.encodeHexString(
                EXAMPLE_ASSET_NAME.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BigInteger held = quantity(before.getValue(), unit);
        assertThat(held)
                .as("step 8 must have minted %s before it can be burned", unit)
                .isGreaterThanOrEqualTo(BigInteger.TWO);

        BigInteger toBurn = BigInteger.ONE;
        System.out.println("\nburning " + toBurn + " of " + unit + " (held: " + held + ")");

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .burn(examplePolicyId, List.of(exampleAsset(toBurn)),
                        BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(0)));

        Result<String> result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(evaluator())
                .completeAndWait(System.out::println);

        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("Burn must validate. issuance_mint delegates custody to the transfer validator"
                        + " when its TransferRedeemer.proofs names the same registry node, and"
                        + " no_escape requires the remainder to stay at the base script: %s",
                        result.getResponse())
                .isTrue();

        Result<List<Amount>> after = tokenService.getProgrammableBalance(ownerAddress);
        assertThat(after.isSuccessful()).as("balance: %s", after.getResponse()).isTrue();
        assertThat(quantity(after.getValue(), unit))
                .as("the burned amount must be destroyed and the remainder still held")
                .isEqualTo(held.subtract(toBurn));

        System.out.println("status         : " + held + " -> " + held.subtract(toBurn)
                + " — supply destroyed, remainder returned to the smart wallet");
    }

    // --------------------------------------------------------------- step 15

    /**
     * Change a registered token's mutable rules in place.
     *
     * <p>{@code registry_spend} recognises an update by the absence of a registry-node mint: it
     * then requires one continuing output carrying the node NFT at the same address, the three
     * frozen fields unchanged, and the node's own minting logic to withdraw-zero. Re-reading the
     * node afterwards is what proves the datum actually changed rather than the transaction
     * merely being accepted.</p>
     */
    @Test
    @Order(15)
    void step15_updateRegistryNode() throws Exception {
        requireDeployment("Registry node update");
        requireExamplePolicyId();

        RegistryNode current = tokenService.registryLookup().byPolicy(examplePolicyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Policy " + examplePolicyId + " is not registered"))
                .getDatum();

        System.out.println("\nnode before    : global_state_cs = '" + current.getGlobalStateCs() + "'");

        // global_state_cs is the one mutable field with no script behind it, so flipping it proves
        // the update path without needing a second substandard deployed.
        String newGlobalState = current.getGlobalStateCs() == null || current.getGlobalStateCs().isEmpty()
                ? AlwaysTrueScripts.ALWAYS_TRUE.getPolicyId()
                : "";
        RegistryNode updated = current.toBuilder().globalStateCs(newGlobalState).build();

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .updateRegistry(examplePolicyId, Cip113RegistryUpdate.from(updated),
                        BigIntPlutusData.of(0));   // alwaysTrue ignores it

        Result<String> result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(evaluator())
                .completeAndWait(System.out::println);

        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("A registry-node update must validate: no node NFT minted, one continuing"
                        + " output at the same address, frozen fields unchanged, minting logic"
                        + " withdrawn-zero: %s", result.getResponse())
                .isTrue();

        RegistryNode reread = tokenService.registryLookup().byPolicy(examplePolicyId)
                .orElseThrow(() -> new IllegalStateException("Node vanished after update"))
                .getDatum();

        assertThat(reread.getGlobalStateCs())
                .as("the mutable field must have actually changed on chain")
                .isEqualTo(newGlobalState);
        assertThat(reread.getKey()).isEqualToIgnoringCase(current.getKey());
        assertThat(reread.getNext()).isEqualToIgnoringCase(current.getNext());
        assertThat(reread.getMintingLogicScript()).isEqualTo(current.getMintingLogicScript());

        System.out.println("node after     : global_state_cs = '" + reread.getGlobalStateCs()
                + "' — frozen fields intact");
    }

    // --------------------------------------------------------------- step 13

    /**
     * Seize tokens from a holder's smart wallet into someone else's.
     *
     * <p>The {@code third_party} validator pairs every acted-on base-script input positionally
     * with a continuing output from {@code outputs_start_idx}, each preserving its input's
     * address, datum and reference script and carrying at least its lovelace. The seized tokens
     * land in outputs <i>before</i> that index. Asserting both balances — one down, one up — is
     * what distinguishes a real seizure from a transaction that merely validated.</p>
     *
     * <p>The always-true substandard authorises it; a real one would consult its own rules.</p>
     */
    @Test
    @Order(13)
    void step13_thirdPartySeize() throws Exception {
        requireDeployment("Third-party seizure");
        requireExamplePolicyId();

        String unit = unitOf(examplePolicyId);
        BigInteger holderBefore = programmableQuantity(unit);
        assertThat(holderBefore)
                .as("the holder must hold something before it can be seized").isGreaterThan(BigInteger.ONE);

        // A second account plays the recipient — its smart wallet is a different address, which is
        // what lets the seized tokens be told apart from the holder's continuing outputs.
        Account recipient = new Account(network);
        Address recipientAddress = new Address(recipient.baseAddress());
        Address recipientWallet = tokenService.smartWalletAddress(recipientAddress);

        // Seize MORE than any single UTxO holds, so the seizure spans at least two inputs. That is
        // the case worth proving: third_party pairs inputs to outputs positionally against the
        // ledger's input order, and with one input the ordering is correct no matter what the
        // comparator does. Step 9's self-transfer leaves the wallet split, so this is available.
        List<BigInteger> perUtxo = tokenService.getUtxos(ownerAddress).getValue().stream()
                .map(u -> quantity(u.getAmount(), unit))
                .filter(q -> q.signum() > 0)
                .sorted(java.util.Comparator.reverseOrder())
                .collect(java.util.stream.Collectors.toList());
        assertThat(perUtxo.size())
                .as("the seizure needs the holder's supply split across at least two UTxOs to"
                        + " exercise positional pairing; step 9's self-transfer should have done"
                        + " that. Holdings per UTxO: %s", perUtxo)
                .isGreaterThanOrEqualTo(2);

        BigInteger toSeize = perUtxo.get(0).add(BigInteger.ONE);
        System.out.println("\nseizing " + toSeize + " of " + unit
                + " across " + perUtxo.size() + " UTxOs (largest holds " + perUtxo.get(0) + ")");
        System.out.println("from           : " + tokenService.smartWalletAddress(ownerAddress).toBech32());
        System.out.println("to             : " + recipientWallet.toBech32());

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(account.baseAddress())
                .thirdPartyTransfer(ownerAddress.getAddress(), recipientAddress.getAddress(),
                        Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, toSeize),
                        BigIntPlutusData.of(0));   // alwaysTrue ignores it

        StringBuilder shape = new StringBuilder();
        int[] plbOutputs = {0};
        Result<String> result;
        try {
            result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                    .compose(tx)
                    .feePayer(account.baseAddress())
                    .withSigner(SignerProviders.signerFrom(account))
                    .withTxEvaluator(evaluator())
                    .preBalanceTx((ctx, txn) -> {
                        shape.append(describe("before balancing", txn));
                        String holderWallet =
                                tokenService.smartWalletAddress(ownerAddress).toBech32();
                        plbOutputs[0] = (int) txn.getBody().getOutputs().stream()
                                .filter(o -> holderWallet.equals(o.getAddress()))
                                .count();
                    })
                    .completeAndWait(System.out::println);
        } catch (Exception e) {
            System.out.println(shape);
            throw e;
        }

        System.out.println(shape);
        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("A third-party seizure must validate: paired continuing outputs preserving"
                        + " address/datum/reference-script, the seized tokens at a PLB output"
                        + " before outputs_start_idx, and the third-party logic withdrawn-zero: %s",
                        result.getResponse())
                .isTrue();

        assertThat(programmableQuantity(unit))
                .as("the holder's balance must fall by exactly what was seized")
                .isEqualTo(holderBefore.subtract(toSeize));

        Result<List<Amount>> recipientBalance = tokenService.getProgrammableBalance(recipientAddress);
        assertThat(recipientBalance.isSuccessful())
                .as("recipient balance: %s", recipientBalance.getResponse()).isTrue();
        assertThat(quantity(recipientBalance.getValue(), unit))
                .as("the seized tokens must land in the recipient's smart wallet")
                .isEqualTo(toSeize);

        assertThat(plbOutputs[0])
                .as("a seizure spanning two inputs must emit one continuing output per input, each"
                        + " paired positionally — a single merged remainder would pair the second"
                        + " input against the wrong output")
                .isGreaterThanOrEqualTo(2);

        System.out.println("paired outputs : " + plbOutputs[0]);
        System.out.println("status         : " + holderBefore + " -> "
                + holderBefore.subtract(toSeize) + " held, " + toSeize + " seized");
    }

    // --------------------------------------------------------------- step 14

    /**
     * The published reference scripts are used instead of witnesses.
     *
     * <p>The bootstrap publishes the base script and the three delegates as reference scripts.
     * Resolving the deployment discovers them, and every transaction thereafter points at them
     * rather than carrying them — several kilobytes saved on each one. Comparing a transfer's
     * transaction size against one built with the scripts forced inline is what shows it actually
     * happened; asserting that a transaction merely validated would not.</p>
     */
    @Test
    @Order(14)
    void step14_publishedScriptsAreReferencedNotWitnessed() throws Exception {
        requireDeployment("Reference scripts");

        DeploymentScripts scripts = tokenService.scripts();
        assertThat(scripts.publishedAt(resolved.getProgrammableLogicBaseHash()))
                .as("the bootstrap published programmable_logic_base as a reference script, so"
                        + " resolving the deployment should have found it")
                .isPresent();
        assertThat(scripts.publishedAt(resolved.getTransferScriptHash()))
                .as("the transfer delegate is published too").isPresent();

        System.out.println("\nbase script    : referenced at "
                + scripts.publishedAt(resolved.getProgrammableLogicBaseHash()).get().getTxHash());
        System.out.println("transfer       : referenced at "
                + scripts.publishedAt(resolved.getTransferScriptHash()).get().getTxHash());

        // A transfer that goes through the whole path, so the base script and the transfer
        // delegate are both needed.
        String unit = unitOf(examplePolicyId);
        assertThat(programmableQuantity(unit))
                .as("need supply to transfer").isGreaterThan(BigInteger.ZERO);

        int[] witnessScripts = {-1};
        int[] refInputs = {-1};

        Result<String> result = new QuickTxBuilder(backendService)
                    .withExtension(programmableTokens.extension())
                .compose(new ProgrammableTokenTx()
                        .from(account.baseAddress())
                        .transfer(account.baseAddress(),
                                Amount.asset(examplePolicyId, EXAMPLE_ASSET_NAME, BigInteger.ONE),
                                BigIntPlutusData.of(0)))
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(evaluator())
                // After balancing on purpose: the duplicate-witness removal runs there, so a
                // pre-balance reading would still show the witnesses that are about to be dropped.
                .postBalanceTx((ctx, txn) -> {
                    witnessScripts[0] = txn.getWitnessSet().getPlutusV3Scripts() == null
                            ? 0 : txn.getWitnessSet().getPlutusV3Scripts().size();
                    refInputs[0] = txn.getBody().getReferenceInputs() == null
                            ? 0 : txn.getBody().getReferenceInputs().size();
                })
                .completeAndWait(System.out::println);

        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("a transfer using referenced scripts must still validate: %s",
                        result.getResponse())
                .isTrue();

        System.out.println("witness scripts: " + witnessScripts[0]);
        System.out.println("ref inputs     : " + refInputs[0]);

        assertThat(witnessScripts[0])
                .as("the base script and the transfer delegate are published, so neither should be"
                        + " in the witness set — only a substandard the chain has not published")
                .isLessThanOrEqualTo(1);
        assertThat(refInputs[0])
                .as("coordination + registry node + the two referenced scripts, at least")
                .isGreaterThanOrEqualTo(4);
    }

    // --------------------------------------------------------- TxPlan YAML path

    /**
     * Execute the portable TxPlan path, including its YAML boundary, against the devnet.
     *
     * <p>This deliberately restores the plan before composition. The restored transaction is a
     * plain {@link Tx} carrying a semantic extension intent, rather than the original
     * {@link ProgrammableTokenTx}; successful submission proves that the runtime extension owns
     * materialization and that no in-memory facade state is required.</p>
     */
    @Test
    @Order(10)
    void txPlanYaml_executesProgrammableTokenIntentAfterRestore() throws Exception {
        requireDeployment("TxPlan YAML transfer");
        requireExamplePolicyId();

        String unit = unitOf(examplePolicyId);
        BigInteger balanceBefore = programmableQuantity(unit);
        Set<String> utxosBefore = smartWalletUtxoRefs();
        assertThat(balanceBefore).as("need supply for the YAML transfer")
                .isGreaterThan(BigInteger.ZERO);

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
                .completeAndWait(message -> { });

        assertThat(result.isSuccessful())
                .as("submitting a programmable-token TxPlan restored from YAML: %s",
                        result.getResponse())
                .isTrue();
        assertThat(programmableQuantity(unit))
                .as("a YAML-authored self-transfer must conserve the programmable-token balance")
                .isEqualTo(balanceBefore);
        assertThat(smartWalletUtxoRefs())
                .as("the YAML-restored transfer must consume a smart-wallet UTxO")
                .isNotEqualTo(utxosBefore);
    }

    /** An account's stake key hash, read from its base address's delegation credential. */
    private static byte[] stakeKeyHashOf(Account candidate) {
        return new Address(candidate.baseAddress()).getDelegationCredentialHash()
                .orElseThrow(() -> new IllegalStateException(
                        "Base address has no delegation credential: " + candidate.baseAddress()));
    }

    private static String hex(com.bloxbean.cardano.client.address.Credential credential) {
        return com.bloxbean.cardano.client.util.HexUtil.encodeHexString(credential.getBytes());
    }

    private static String assetName(String unit) {
        return new String(com.bloxbean.cardano.client.util.HexUtil.decodeHexString(unit.substring(56)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static BigInteger quantity(List<Amount> amounts, String unit) {
        return amounts.stream()
                .filter(a -> unit.equals(a.getUnit()))
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
