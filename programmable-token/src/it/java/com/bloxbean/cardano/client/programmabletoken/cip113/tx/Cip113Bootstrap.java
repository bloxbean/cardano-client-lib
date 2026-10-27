package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deploys the whole CIP-113 core v0.0.1 protocol onto a fresh chain, in three transactions.
 *
 * <p>Reimplemented from the reference deploy ceremony (cip113-programmable-tokens-platform's
 * frontend, which wraps {@code @easy1staking/cip113-sdk-ts}'s bootstrap) so the integration suite
 * can stand up its own devnet instance rather than depending on someone else's Preview
 * deployment.</p>
 *
 * <p>Every validator is parameterized, and several are parameterized by <i>another</i> validator's
 * hash, so the order below is a dependency order rather than a stylistic one. Two UTxOs seed the
 * one-shot policies: {@code utxo1} for {@code upgrade_multisig}, {@code utxo2} for
 * {@code protocol_params}, {@code registry} and {@code issuance_cbor_hex_mint}. The transactions:
 * the upgrade-authority config UTxO plus the reward-account registrations; the protocol genesis,
 * in which the authority must activate itself (upstream #143), so after the first; and the
 * reference scripts.</p>
 *
 * <p>Unlike the public deployments, the dispatcher is compiled against the <i>real</i>
 * unfracking script hash, so the suite can exercise unfracking.</p>
 */
final class Cip113Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Cip113Bootstrap.class);

    /** Fixed nonce for the always-fail script the issuance template is parked under. */
    private static final String ALWAYS_FAIL_NONCE =
            "fa5b084bbdc0336c1e3c086617d99cf6ecff1a190116784a0dd54aeca948e8fe";

    /**
     * 28-byte marker standing in for the real issuance credential while the template is compiled.
     * The template is then split on this value, so it must appear exactly once.
     */
    private static final String DUMMY_POLICY_ID =
            "deadbeefcafebabedeadbeefcafebabedeadbeefcafebabedeadbeef";

    /** {@code registry_node.sentinel_next_key} — 30 bytes, deliberately not 28 so it can never be a real key. */
    private static final String SENTINEL_NEXT_KEY =
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

    private static final long MAX_INLINE_DATUM_BYTES = 2048L;

    private final Map<String, String> compiledCode = new LinkedHashMap<>();

    Cip113Bootstrap() {
        try (var in = Cip113Bootstrap.class.getResourceAsStream("/blueprint/cip113/plutus.json")) {
            JsonNode blueprint = new ObjectMapper().readTree(in);
            blueprint.get("validators").forEach(v ->
                    compiledCode.put(v.get("title").asText(), v.get("compiledCode").asText()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the vendored CIP-113 blueprint", e);
        }
    }

    /**
     * The deployed protocol, as far as the bootstrap knows it.
     *
     * @param referenceScripts the delegates published as reference scripts in the bootstrap tx
     * @param appliedScripts   <i>every</i> script the bootstrap applied parameters to. A backend
     *                         cannot serve any of these until the chain reveals them, so callers
     *                         feed this straight into {@link DeploymentScripts#registerAll}.
     */
    record Deployed(String bootstrapTxHash, String paramsPolicy, String registryNodeCs,
                    String issuanceCborHexCs, String programmableLogicBaseHash,
                    String transferHash, String thirdPartyHash, String unfrackingHash,
                    List<PlutusScript> referenceScripts, List<PlutusScript> appliedScripts,
                    Cip113Deployment deployment) {}

    Deployed deploy(BackendService backendService, Account admin, Network network) throws Exception {
        var utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

        // Two seeds, each fat enough to cover the deployment on its own.
        List<Utxo> seeds = utxoSupplier.getAll(admin.baseAddress()).stream()
                .filter(u -> lovelaceOf(u).compareTo(BigInteger.valueOf(100_000_000L)) > 0)
                .limit(2)
                .toList();
        if (seeds.size() < 2) {
            throw new IllegalStateException("The bootstrap needs two UTxOs over 100 ADA at "
                    + admin.baseAddress() + " to seed the one-shot policies, but found "
                    + seeds.size() + ". Top the account up twice so it has two separate UTxOs.");
        }
        Utxo utxo1 = seeds.get(0);
        Utxo utxo2 = seeds.get(1);

        PlutusData utxo1Ref = outputReference(utxo1);
        PlutusData utxo2Ref = outputReference(utxo2);

        // ---- apply parameters, in dependency order -------------------------------------------
        PlutusScript alwaysFail = apply("always_fail.always_fail.spend",
                BytesPlutusData.of(HexUtil.decodeHexString(ALWAYS_FAIL_NONCE)));

        PlutusScript issuanceCborHexMint = apply("issuance_cbor_hex_mint.issuance_cbor_hex_mint.mint",
                utxo2Ref, BytesPlutusData.of(alwaysFail.getScriptHash()));
        // Mint and spend share one script; RegistryInit binds the origin node to it.
        PlutusScript registry = apply("registry.registry.mint",
                utxo2Ref, BytesPlutusData.of(issuanceCborHexMint.getScriptHash()));
        PlutusScript protocolParams = apply("protocol_params.protocol_params.mint", utxo2Ref);

        // Every delegate hangs off the params policy — the deployment's permanent anchor.
        BytesPlutusData paramsPolicy = BytesPlutusData.of(protocolParams.getScriptHash());

        PlutusScript plb = apply("programmable_logic_base.programmable_logic_base.spend", paramsPolicy);
        PlutusData plbCred = scriptCredential(plb);
        BytesPlutusData registryCs = BytesPlutusData.of(registry.getScriptHash());
        BigIntPlutusData bound = BigIntPlutusData.of(MAX_INLINE_DATUM_BYTES);
        PlutusScript transfer = apply("transfer.transfer.withdraw", plbCred, registryCs, bound);
        PlutusScript thirdParty = apply("third_party.third_party.withdraw", plbCred, registryCs, bound);
        PlutusScript unfracking = apply("unfracking.unfracking.withdraw", plbCred, registryCs, bound);
        PlutusScript issuanceLogic = apply("issuance_logic.issuance_logic.withdraw",
                plbCred, registryCs, paramsPolicy, bound);
        PlutusScript plg = apply("programmable_logic_global.programmable_logic_global.withdraw",
                BytesPlutusData.of(transfer.getScriptHash()),
                BytesPlutusData.of(thirdParty.getScriptHash()),
                BytesPlutusData.of(unfracking.getScriptHash()));

        PlutusScript upgradeMultisig = apply("upgrade_multisig.upgrade_multisig.mint", utxo1Ref);

        PlutusData issuanceTemplateDatum = buildIssuanceTemplate(protocolParams);

        byte[] adminKeyHash = AddressProvider.getPaymentCredentialHash(new Address(admin.baseAddress()))
                .orElseThrow();

        // ---- the datums ------------------------------------------------------------------------
        PlutusData coordinationDatum = ConstrPlutusData.of(0,
                scriptCredential(plg),
                scriptCredential(issuanceLogic),
                scriptCredential(transfer),
                scriptCredential(thirdParty),
                scriptCredential(upgradeMultisig),
                ConstrPlutusData.of(1));                        // pending_upgrade_cred: None

        // Origin node: empty key, sentinel next, every credential the empty-vkey sentinel.
        PlutusData originNodeDatum = ConstrPlutusData.of(0,
                BytesPlutusData.of(new byte[0]),
                BytesPlutusData.of(HexUtil.decodeHexString(SENTINEL_NEXT_KEY)),
                emptyVkey(), emptyVkey(), emptyVkey(), emptyVkey(),
                BytesPlutusData.of(new byte[0]));

        // The upgrade authority's tree: one signature, the admin's.
        PlutusData upgradeTree = ConstrPlutusData.of(0, BytesPlutusData.of(adminKeyHash));

        Address coordinationAddress = AddressProvider.getEntAddress(protocolParams, network);
        Address registryAddress = AddressProvider.getEntAddress(registry, network);
        Address issuanceTemplateAddress = AddressProvider.getEntAddress(alwaysFail, network);
        Address upgradeConfigAddress = AddressProvider.getEntAddress(upgradeMultisig, network);
        // Same address, named for what it is used for below.
        Address alwaysFailAddress = issuanceTemplateAddress;

        log.info("  scripts:");
        log.info("    always_fail        " + alwaysFail.getPolicyId());
        log.info("    protocol_params    " + protocolParams.getPolicyId()
                + "  addr " + coordinationAddress.getAddress());
        log.info("    plb                " + plb.getPolicyId());
        log.info("    plg                " + plg.getPolicyId());
        log.info("    transfer           " + transfer.getPolicyId());
        log.info("    third_party        " + thirdParty.getPolicyId());
        log.info("    unfracking         " + unfracking.getPolicyId());
        log.info("    issuance_logic     " + issuanceLogic.getPolicyId());
        log.info("    upgrade_multisig   " + upgradeMultisig.getPolicyId());
        log.info("    issuance_cbor_hex  " + issuanceCborHexMint.getPolicyId()
                + "  addr " + issuanceTemplateAddress.getAddress());
        log.info("    registry           " + registry.getPolicyId()
                + "  addr " + registryAddress.getAddress());
        log.info("  seeds: utxo1=" + utxo1.getTxHash() + "#" + utxo1.getOutputIndex()
                + "  utxo2=" + utxo2.getTxHash() + "#" + utxo2.getOutputIndex());

        // ---- transaction 1: the upgrade authority and the reward accounts ----------------------
        Tx authority = new Tx()
                .collectFrom(List.of(utxo1))
                .mintAsset(upgradeMultisig, new Asset(nameHex("UpgradeMultisig"), BigInteger.ONE),
                        ConstrPlutusData.of(0))
                .payToContract(upgradeConfigAddress.getAddress(),
                        List.of(Amount.ada(5), asset(upgradeMultisig, "UpgradeMultisig")),
                        upgradeTree)
                .from(admin.baseAddress());

        // Every withdraw-zero credential is re-parameterized on every deployment, so its reward
        // account is new each time and always needs registering. Without this their withdraw-zero
        // — which every operation depends on — is invalid.
        for (PlutusScript withdrawn : List.of(plg, transfer, thirdParty, unfracking, issuanceLogic,
                upgradeMultisig)) {
            authority.registerStakeAddress(AddressProvider.getRewardAddress(withdrawn, network).toBech32());
        }
        String authorityTx = submit(backendService, admin, authority, "upgrade authority", null);

        Utxo upgradeConfig = utxoSupplier.getAll(upgradeConfigAddress.getAddress()).stream()
                .filter(u -> u.getTxHash().equals(authorityTx))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The upgrade-authority config UTxO from "
                        + authorityTx + " is not visible at " + upgradeConfigAddress.getAddress()));

        // ---- transaction 2: the protocol genesis ------------------------------------------------
        Tx tx = new Tx()
                .collectFrom(List.of(utxo2))
                .readFrom(upgradeConfig)
                .mintAsset(registry, new Asset("", BigInteger.ONE), ConstrPlutusData.of(0))
                .mintAsset(protocolParams, new Asset(nameHex("ProtocolParams"), BigInteger.ONE),
                        ConstrPlutusData.of(1))
                .mintAsset(issuanceCborHexMint, new Asset(nameHex("IssuanceCborHex"), BigInteger.ONE),
                        ConstrPlutusData.of(2))
                // The initial authority activates itself: protocol_params requires upgrade_cred
                // among the withdrawals at genesis, and upgrade_multisig checks its tree against
                // the required signers.
                .withdraw(AddressProvider.getRewardAddress(upgradeMultisig, network).toBech32(),
                        BigInteger.ZERO, ConstrPlutusData.of(0))
                .attachRewardValidator(upgradeMultisig)
                .payToContract(coordinationAddress.getAddress(),
                        List.of(Amount.ada(5), asset(protocolParams, "ProtocolParams")),
                        coordinationDatum)
                .payToContract(registryAddress.getAddress(),
                        List.of(Amount.ada(5), assetRaw(registry, "")),
                        originNodeDatum)
                // The template datum is large, so its min-UTxO is far above the flat 5 ADA the
                // other two outputs use. Compute it rather than guess.
                .payToContract(issuanceTemplateAddress.getAddress(),
                        List.of(Amount.lovelace(issuanceTemplateLovelace(backendService,
                                        issuanceTemplateAddress, issuanceCborHexMint, issuanceTemplateDatum)),
                                asset(issuanceCborHexMint, "IssuanceCborHex")),
                        issuanceTemplateDatum)
                .from(admin.baseAddress());
        String bootstrapTx = submit(backendService, admin, tx, "protocol genesis", adminKeyHash);

        // ---- transaction 3: reference scripts ----------------------------------------------------
        // Published at the always-fail address rather than the admin's own: a reference-script
        // UTxO holding nothing but ADA is indistinguishable from ordinary change to coin
        // selection, and spending one destroys the published script for every transaction that
        // references it.
        List<PlutusScript> referenceScripts = List.of(plb, plg, transfer, thirdParty, unfracking,
                issuanceLogic, upgradeMultisig);
        Tx publish = new Tx().from(admin.baseAddress());
        for (PlutusScript script : referenceScripts) {
            publish.payToAddress(alwaysFailAddress.getAddress(), Amount.ada(25), script);
        }
        String referenceTx = submit(backendService, admin, publish, "reference scripts", null);

        Cip113Deployment.Cip113DeploymentBuilder deployment = Cip113Deployment.builder()
                .network(network)
                .bootstrapTxHash(bootstrapTx)
                .paramsPolicy(protocolParams.getPolicyId())
                .programmableLogicBaseHash(plb.getPolicyId())
                .programmableLogicGlobalHash(plg.getPolicyId())
                .issuanceLogicHash(issuanceLogic.getPolicyId())
                .transferScriptHash(transfer.getPolicyId())
                .thirdPartyScriptHash(thirdParty.getPolicyId())
                .unfrackingScriptHash(unfracking.getPolicyId())
                .unfrackingEnabled(true)
                .upgradeScriptHash(upgradeMultisig.getPolicyId())
                .registryNodeCs(registry.getPolicyId())
                .issuanceCborHexCs(issuanceCborHexMint.getPolicyId())
                .maxInlineDatumBytes((int) MAX_INLINE_DATUM_BYTES);
        // Where each output landed, read back from chain rather than assumed.
        for (TxContentUtxoOutputs output : outputsOf(backendService, referenceTx)) {
            String hash = output.getReferenceScriptHash();
            if (hash == null || hash.isEmpty()) continue;
            deployment.referenceScript(hash, TransactionInput.builder()
                    .transactionId(referenceTx).index(output.getOutputIndex()).build());
        }
        String templateUnit = issuanceCborHexMint.getPolicyId()
                + HexUtil.encodeHexString("IssuanceCborHex".getBytes(StandardCharsets.UTF_8));
        for (TxContentUtxoOutputs output : outputsOf(backendService, bootstrapTx)) {
            if (output.getAmount().stream().anyMatch(a -> templateUnit.equals(a.getUnit()))) {
                deployment.issuanceTemplateRef(TransactionInput.builder()
                        .transactionId(bootstrapTx).index(output.getOutputIndex()).build());
            }
        }

        return new Deployed(bootstrapTx,
                HexUtil.encodeHexString(protocolParams.getScriptHash()),
                HexUtil.encodeHexString(registry.getScriptHash()),
                HexUtil.encodeHexString(issuanceCborHexMint.getScriptHash()),
                HexUtil.encodeHexString(plb.getScriptHash()),
                HexUtil.encodeHexString(transfer.getScriptHash()),
                HexUtil.encodeHexString(thirdParty.getScriptHash()),
                HexUtil.encodeHexString(unfracking.getScriptHash()),
                referenceScripts,
                List.of(alwaysFail, protocolParams, plb, plg, transfer, thirdParty, unfracking,
                        issuanceLogic, upgradeMultisig, issuanceCborHexMint, registry),
                deployment.build());
    }

    private static String submit(BackendService backendService, Account admin, Tx tx, String what,
                                 byte[] requiredSigner) {
        QuickTxBuilder.TxContext context = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(admin.baseAddress())
                .withSigner(SignerProviders.signerFrom(admin))
                .withTxEvaluator(new AikenTransactionEvaluator(backendService))
                .mergeOutputs(false);
        if (requiredSigner != null) context = context.withRequiredSigners(requiredSigner);
        Result<String> result = context.completeAndWait(log::info);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("CIP-113 " + what + " failed: " + result.getResponse());
        }
        return result.getValue();
    }

    private static List<TxContentUtxoOutputs> outputsOf(BackendService backendService, String txHash)
            throws Exception {
        Result<TxContentUtxo> result = backendService.getTransactionService().getTransactionUtxos(txHash);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Could not read " + txHash + ": " + result.getResponse());
        }
        return result.getValue().getOutputs();
    }

    /**
     * The issuance template: the {@code issuance_mint} script with a hole where the issuance
     * credential goes.
     *
     * <p>Compiled with a dummy 28-byte credential, then split on that marker. The two halves are
     * what every future token's policy id is derived from —
     * {@code blake2b_224(0x03 ‖ prefix ‖ credentialHash ‖ postfix)} — so a marker appearing zero or
     * twice would silently produce a template no registration could ever satisfy.</p>
     */
    /** Ledger-exact min-UTxO for the template output, plus a whole-ADA rounded buffer. */
    private static BigInteger issuanceTemplateLovelace(BackendService backendService,
                                                       Address address, PlutusScript policy,
                                                       PlutusData datum) throws Exception {
        var output = com.bloxbean.cardano.client.transaction.spec.TransactionOutput.builder()
                .address(address.getAddress())
                .value(com.bloxbean.cardano.client.transaction.spec.Value.builder()
                        .coin(com.bloxbean.cardano.client.api.MinAdaCalculator.DUMMY_COIN_VAL)
                        .multiAssets(List.of(com.bloxbean.cardano.client.transaction.spec.MultiAsset.builder()
                                .policyId(policy.getPolicyId())
                                .assets(List.of(new Asset(nameHex("IssuanceCborHex"), BigInteger.ONE)))
                                .build()))
                        .build())
                .inlineDatum(datum)
                .build();

        BigInteger minAda = new com.bloxbean.cardano.client.api.MinAdaCalculator(
                backendService.getEpochService().getProtocolParameters().getValue())
                .calculateMinAda(output);

        BigInteger oneAda = BigInteger.valueOf(1_000_000L);
        return minAda.add(oneAda).add(oneAda).subtract(BigInteger.ONE).divide(oneAda).multiply(oneAda);
    }

    private PlutusData buildIssuanceTemplate(PlutusScript protocolParamsMint) throws Exception {
        PlutusScript dummy = issuanceMint(DUMMY_POLICY_ID, protocolParamsMint);

        String body = HexUtil.encodeHexString(dummy.serializeScriptBody());
        String[] parts = body.split(DUMMY_POLICY_ID);

        if (parts.length != 2) {
            throw new IllegalStateException("The dummy issuance marker must appear exactly once in"
                    + " the compiled template but appeared " + (parts.length - 1) + " time(s).");
        }
        if (parts[1].isEmpty()) {
            throw new IllegalStateException("The issuance template's postfix is empty, which means"
                    + " the parameter order changed — params_policy is applied after the issuance"
                    + " credential, so bytes must follow the hole.");
        }

        // The registry recomputes blake2b_224(0x03 ‖ prefix ‖ hash ‖ postfix) on every insert, so
        // check the split against a real application before it goes on chain.
        String probe = "0123456789abcdef0123456789abcdef0123456789abcdef01234567";
        String derived = HexUtil.encodeHexString(Blake2bUtil.blake2bHash224(
                HexUtil.decodeHexString("03" + parts[0] + probe + parts[1])));
        if (!derived.equals(issuanceMint(probe, protocolParamsMint).getPolicyId())) {
            throw new IllegalStateException("The issuance template does not reproduce issuance_mint's"
                    + " policy id: the split is off by a CBOR wrapping layer.");
        }

        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(parts[0])),
                BytesPlutusData.of(HexUtil.decodeHexString(parts[1])));
    }

    /** {@code issuance_mint(minting_logic_cred: Credential, params_policy: PolicyId)}. */
    private PlutusScript issuanceMint(String credentialHash, PlutusScript protocolParamsMint) throws Exception {
        return apply("issuance_mint.issuance_mint.mint",
                ConstrPlutusData.of(1, BytesPlutusData.of(HexUtil.decodeHexString(credentialHash))),
                BytesPlutusData.of(protocolParamsMint.getScriptHash()));
    }

    // ------------------------------------------------------------------------ helpers

    private PlutusScript apply(String title, PlutusData... params) {
        String code = compiledCode.get(title);
        if (code == null) {
            throw new IllegalStateException("The vendored blueprint has no validator titled " + title);
        }
        return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                AikenScriptUtil.applyParamToScript(ListPlutusData.of(params), code), PlutusVersion.v3);
    }

    /** {@code Credential.Script(hash)} — constructor 1. */
    private static ConstrPlutusData scriptCredential(PlutusScript script) throws Exception {
        return ConstrPlutusData.of(1, BytesPlutusData.of(script.getScriptHash()));
    }

    /** {@code empty_vkey} — {@code VerificationKey("")}, constructor 0 with a zero-length hash. */
    private static ConstrPlutusData emptyVkey() {
        return ConstrPlutusData.of(0, BytesPlutusData.of(new byte[0]));
    }

    /**
     * {@code OutputReference { transaction_id: ByteArray, output_index: Int }}.
     *
     * <p><b>The transaction id is a bare byte string.</b> Under Plutus V1 and V2 it was wrapped in
     * its own {@code TransactionId} constructor, and most Java examples still show that shape — but
     * V3 flattened it, and the CIP-113 blueprint declares {@code transaction_id} as a plain
     * {@code ByteArray}. Passing the wrapped form still compiles and still produces a perfectly
     * valid script; that script's one-shot check just never matches any input, so the mint fails
     * with an early evaluation error and no explanation.</p>
     */
    private static PlutusData outputReference(Utxo utxo) {
        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(utxo.getTxHash())),
                BigIntPlutusData.of(utxo.getOutputIndex()));
    }

    private static String nameHex(String assetName) {
        return "0x" + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8));
    }

    private static Amount asset(PlutusScript policy, String assetName) throws Exception {
        return Amount.asset(policy.getPolicyId(), assetName, BigInteger.ONE);
    }

    /** An asset with an empty name — the origin registry node's NFT. */
    private static Amount assetRaw(PlutusScript policy, String assetName) throws Exception {
        return Amount.builder().unit(policy.getPolicyId() + assetName).quantity(BigInteger.ONE).build();
    }

    private static BigInteger lovelaceOf(Utxo utxo) {
        return utxo.getAmount().stream()
                .filter(a -> "lovelace".equals(a.getUnit()))
                .map(Amount::getQuantity)
                .findFirst().orElse(BigInteger.ZERO);
    }

    /** Every applied script, for printing. */
    static List<String> describe(Deployed d) {
        List<String> lines = new ArrayList<>();
        lines.add("bootstrap tx      : " + d.bootstrapTxHash());
        lines.add("params policy     : " + d.paramsPolicy());
        lines.add("base script hash  : " + d.programmableLogicBaseHash());
        lines.add("transfer delegate : " + d.transferHash());
        lines.add("thirdparty deleg. : " + d.thirdPartyHash());
        lines.add("registry node cs  : " + d.registryNodeCs());
        lines.add("issuance tmpl cs  : " + d.issuanceCborHexCs());
        return lines;
    }
}
