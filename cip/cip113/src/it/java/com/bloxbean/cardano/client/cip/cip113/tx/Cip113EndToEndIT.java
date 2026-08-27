package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.model.AccountInformation;
import com.bloxbean.cardano.client.cip.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.cip.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.cip.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.cip.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
 * ./gradlew :cip:cip113:integrationTest --tests '*Cip113EndToEndIT*'
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
public class Cip113EndToEndIT {

    private static BackendService backendService;
    private static Account account;
    private static Address ownerAddress;
    private static ProgrammableTokenService tokenService;

    /** The ordinary backend, decorated so it answers CIP-113 questions too. */
    private static ProgrammableBackendService programmableBackend;

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

    /** The scanning lookup caches; a transaction that changes the registry invalidates it. */
    private static void invalidateRegistryCache() {
        tokenService.registryLookup().invalidate();
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
        // Decorate the same backend rather than carrying a second object: a ProgrammableBackendService
        // is a BackendService, so it still goes straight into QuickTxBuilder below.
        // Nothing but the bootstrap transaction hash: every other value, the registry address
        // included, is discovered from chain.
        programmableBackend = ProgrammableBackendService.wrap(backendService,
                Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network));
        tokenService = programmableBackend.getProgrammableTokenService();

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

        Result<String> result = new QuickTxBuilder(programmableBackend)
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
     * <p>TODO not implemented — {@link ProgrammableTokenTx#registerToken} still throws. The
     * transaction has to spend the covering node, re-point its {@code next}, emit the new node,
     * mint the registry NFT named after the policy id, reference the issuance template, and
     * include the issuance credential's withdraw-zero — which step 5 has just made possible.</p>
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

        ProgrammableTokenTx tx = new ProgrammableTokenTx(programmableBackend)
                .from(account.baseAddress())
                .registerToken(RegistryNodeSpec.builder()
                        .mintingLogicScript(AlwaysTrueScripts.credential())
                        .transferLogicScript(AlwaysTrueScripts.credential())
                        .thirdPartyTransferLogicScript(AlwaysTrueScripts.credential())
                        .build(),
                PlutusData.unit());

        Result<String> result = new QuickTxBuilder(programmableBackend)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(evaluator())
                .completeAndWait(System.out::println);

        System.out.println("submit         : " + result.getResponse());
        assertThat(result.isSuccessful())
                .as("registering the example token: %s", result.getResponse())
                .isTrue();

        System.out.println("registered in  : " + result.getValue());
        tokenService.registryLookup().invalidate();   // drop the cached scan so later steps see the new node
        invalidateRegistryCache();
    }

    // ---------------------------------------------------------------- step 8

    /** Mint the example token into this account's own smart wallet. */
    @Test
    @Order(8)
    void step8_mintExampleToken() throws Exception {
        requireDeployment("Mint");
        Assumptions.assumeTrue(requireExamplePolicyId() != null,
                "Could not derive the example policy id");

        invalidateRegistryCache();
        Assumptions.assumeTrue(tokenService.isProgrammable(examplePolicyId).getValue(),
                "Policy " + examplePolicyId + " is not registered yet — registration must land first");

        System.out.println("\n=== Mint ===");
        System.out.println("policy id      : " + examplePolicyId);
        System.out.println("into           : " + tokenService.smartWalletAddress(ownerAddress).toBech32());

        // Assert on the delta, not an absolute total: this step is meant to be re-runnable, and
        // every previous run left its own supply behind.
        BigInteger heldBefore = programmableQuantity(unitOf(examplePolicyId));
        System.out.println("balance before : " + heldBefore);

        ProgrammableTokenTx tx = new ProgrammableTokenTx(programmableBackend)
                .from(account.baseAddress())
                .mintProgrammable(examplePolicyId, EXAMPLE_ASSET_NAME, MINT_QUANTITY,
                        account.baseAddress(), BigIntPlutusData.of(0));

        Result<String> result = new QuickTxBuilder(programmableBackend)
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

        System.out.println("\ntransferring 1 of " + unit);
        System.out.println("(sent to the same owner: the balance must be conserved, but every"
                + " validator on the transfer path still has to pass — which is what is being proved)");

        // Send it back to ourselves: the balance should be unchanged, but every validator on the
        // transfer path still has to pass. That isolates "does the machinery work" from "did the
        // right amount move", which is the thing worth learning first.
        ProgrammableTokenTx tx = new ProgrammableTokenTx(programmableBackend)
                .from(account.baseAddress())
                .payToAddress(account.baseAddress(),
                        Amount.asset(policyId, assetName(unit), amountToSend))
                .withRedeemer(policyId, BigIntPlutusData.of(0));   // alwaysTrue ignores it

        // Capture the transaction as balancing sees it. When evaluation fails there is no
        // result object to inspect, and Blockfrost's "empty ScriptFailures" says nothing, so the
        // shape has to be recorded on the way through.
        StringBuilder shape = new StringBuilder();

        Result<String> result;
        try {
            result = new QuickTxBuilder(programmableBackend)
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
        if (txn.getWitnessSet() != null && txn.getWitnessSet().getRedeemers() != null) {
            txn.getWitnessSet().getRedeemers().forEach(r ->
                    sb.append("  redeemer ").append(r.getTag()).append(" index=").append(r.getIndex()).append('\n'));
        }
        if (body.getWithdrawals() != null) {
            body.getWithdrawals().forEach(w ->
                    sb.append("  withdrawal ").append(w.getRewardAddress()).append('\n'));
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

        ProgrammableTokenService fresh = ProgrammableBackendService
                .wrap(backendService,
                        Cip113Deployments.fromBootstrapTx(deployed.bootstrapTxHash(), network))
                .getProgrammableTokenService();

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

        // And a transaction built from it carries the protocol UTxOs rather than nulls.
        assertThat(fresh.tx()).isNotNull();
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
        Result<String> reg = new QuickTxBuilder(programmableBackend)
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
        ProgrammableTokenTx tx = new ProgrammableTokenTx(programmableBackend)
                .from(account.baseAddress())
                .payToAddress(ownerAddress.getAddress(),
                        Amount.asset(policyId, EXAMPLE_ASSET_NAME, 1))
                .withRedeemer(policyId, BigIntPlutusData.of(0))
                .withdraw(rewardAddress, BigInteger.ZERO);

        Result<String> result = new QuickTxBuilder(programmableBackend)
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
