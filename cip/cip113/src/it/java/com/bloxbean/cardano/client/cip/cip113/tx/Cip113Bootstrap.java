package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
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
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deploys the whole CIP-113 protocol onto a fresh chain, in one transaction.
 *
 * <p>Reimplemented from the reference deployment
 * ({@code cip113-programmable-tokens-platform}'s {@code PreviewProtocolDeploymentMintTest}) so the
 * integration suite can stand up its own devnet instance rather than depending on someone else's
 * Preview deployment.</p>
 *
 * <p>Every validator is parameterized, and several are parameterized by <i>another</i> validator's
 * hash, so the order below is a dependency order rather than a stylistic one. Two UTxOs seed the
 * one-shot policies: {@code utxo1} for both {@code protocol_params_mint} and {@code registry_mint},
 * {@code utxo2} for {@code issuance_cbor_hex_mint}.</p>
 */
final class Cip113Bootstrap {

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
                    String registrySpendHash, List<PlutusScript> referenceScripts,
                    List<PlutusScript> appliedScripts) {}

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

        // blake2b_256(txHash ‖ outputIndex) — a nonce unique to this deployment.
        byte[] coordinationNonce = Blake2bUtil.blake2bHash256(HexUtil.decodeHexString(
                utxo1.getTxHash() + String.format("%02x", utxo1.getOutputIndex())));
        PlutusScript coordinationSpend = apply("coordination_spend.coordination_spend.spend",
                BytesPlutusData.of(coordinationNonce));

        PlutusScript protocolParamsMint = apply("protocol_params_mint.protocol_params_mint.mint",
                utxo1Ref, BytesPlutusData.of(coordinationSpend.getScriptHash()));

        // Every delegate hangs off the params policy — the deployment's permanent anchor.
        BytesPlutusData paramsPolicy = BytesPlutusData.of(protocolParamsMint.getScriptHash());

        PlutusScript plb = apply("programmable_logic_base.programmable_logic_base.spend", paramsPolicy);
        PlutusScript transfer = apply("transfer.transfer.withdraw", paramsPolicy);
        PlutusScript thirdParty = apply("third_party.third_party.withdraw", paramsPolicy);
        PlutusScript unfracking = apply("unfracking.unfracking.withdraw", paramsPolicy);

        PlutusScript upgradeMultisig = apply("upgrade_multisig.upgrade_multisig.withdraw",
                ListPlutusData.of(BytesPlutusData.of(
                        AddressProvider.getPaymentCredentialHash(new Address(admin.baseAddress()))
                                .orElseThrow())),
                BigIntPlutusData.of(1));

        PlutusScript issuanceCborHexMint = apply("issuance_cbor_hex_mint.issuance_cbor_hex_mint.mint",
                utxo2Ref, BytesPlutusData.of(alwaysFail.getScriptHash()));

        // registry_spend before registry_mint: registry_mint takes its credential as a parameter,
        // and RegistryInit binds the origin node's address to it.
        PlutusScript registrySpend = apply("registry_spend.registry_spend.spend", paramsPolicy);
        PlutusScript registryMint = apply("registry_mint.registry_mint.mint",
                utxo1Ref,
                BytesPlutusData.of(issuanceCborHexMint.getScriptHash()),
                scriptCredential(registrySpend));

        PlutusData issuanceTemplateDatum = buildIssuanceTemplate(plb, registryMint, protocolParamsMint);

        // ---- the datums ------------------------------------------------------------------------
        PlutusData coordinationDatum = ConstrPlutusData.of(0,
                BytesPlutusData.of(registryMint.getScriptHash()),
                scriptCredential(plb),
                scriptCredential(transfer),
                scriptCredential(thirdParty),
                scriptCredential(unfracking),
                scriptCredential(upgradeMultisig),
                BigIntPlutusData.of(BigInteger.valueOf(MAX_INLINE_DATUM_BYTES)));

        // Origin node: empty key, sentinel next, every credential the empty-vkey sentinel.
        PlutusData originNodeDatum = ConstrPlutusData.of(0,
                BytesPlutusData.of(new byte[0]),
                BytesPlutusData.of(HexUtil.decodeHexString(SENTINEL_NEXT_KEY)),
                emptyVkey(), emptyVkey(), emptyVkey(), emptyVkey(),
                BytesPlutusData.of(new byte[0]));

        // ---- the single bootstrap transaction --------------------------------------------------
        Address coordinationAddress = AddressProvider.getEntAddress(coordinationSpend, network);
        Address registryAddress = AddressProvider.getEntAddress(registrySpend, network);
        Address issuanceTemplateAddress = AddressProvider.getEntAddress(alwaysFail, network);

        Tx tx = new Tx()
                .collectFrom(List.of(utxo1, utxo2))
                .mintAsset(registryMint, new Asset("", BigInteger.ONE), ConstrPlutusData.of(0))
                .mintAsset(protocolParamsMint, new Asset(nameHex("ProtocolParams"), BigInteger.ONE),
                        ConstrPlutusData.of(1))
                .mintAsset(issuanceCborHexMint, new Asset(nameHex("IssuanceCborHex"), BigInteger.ONE),
                        ConstrPlutusData.of(2))
                .payToContract(coordinationAddress.getAddress(),
                        List.of(Amount.ada(5), asset(protocolParamsMint, "ProtocolParams")),
                        coordinationDatum)
                .payToContract(registryAddress.getAddress(),
                        List.of(Amount.ada(5), assetRaw(registryMint, "")),
                        originNodeDatum)
                // The template datum is over 2 KB, so its min-UTxO is far above the flat 5 ADA
                // the other two outputs use. Compute it rather than guess.
                .payToContract(issuanceTemplateAddress.getAddress(),
                        List.of(Amount.lovelace(issuanceTemplateLovelace(backendService,
                                        issuanceTemplateAddress, issuanceCborHexMint, issuanceTemplateDatum)),
                                asset(issuanceCborHexMint, "IssuanceCborHex")),
                        issuanceTemplateDatum)
                // Reference scripts, so later transactions do not have to carry them inline.
                .payToAddress(admin.baseAddress(), Amount.ada(5), plb)
                .payToAddress(admin.baseAddress(), Amount.ada(20), transfer)
                .payToAddress(admin.baseAddress(), Amount.ada(20), thirdParty)
                .payToAddress(admin.baseAddress(), Amount.ada(12), unfracking)
                .from(admin.baseAddress());

        // The three delegates are re-parameterized on every deployment, so their reward accounts
        // are new each time and always need registering. Without this their withdraw-zero — which
        // every transfer depends on — is invalid.
        for (PlutusScript delegate : List.of(transfer, thirdParty, unfracking, upgradeMultisig)) {
            tx.registerStakeAddress(AddressProvider.getRewardAddress(delegate, network).toBech32());
        }

        // A Mint redeemer's index is its policy's position in the sorted mint map, so an
        // evaluation error like RedeemerError{tag:"Mint", index:0} only names a validator once
        // that order is known.
        System.out.println("  scripts:");
        System.out.println("    always_fail        " + alwaysFail.getPolicyId());
        System.out.println("    coordination_spend " + coordinationSpend.getPolicyId()
                + "  addr " + coordinationAddress.getAddress());
        System.out.println("    protocol_params    " + protocolParamsMint.getPolicyId());
        System.out.println("    plb                " + plb.getPolicyId());
        System.out.println("    transfer           " + transfer.getPolicyId());
        System.out.println("    third_party        " + thirdParty.getPolicyId());
        System.out.println("    unfracking         " + unfracking.getPolicyId());
        System.out.println("    upgrade_multisig   " + upgradeMultisig.getPolicyId());
        System.out.println("    issuance_cbor_hex  " + issuanceCborHexMint.getPolicyId()
                + "  addr " + issuanceTemplateAddress.getAddress());
        System.out.println("    registry_spend     " + registrySpend.getPolicyId()
                + "  addr " + registryAddress.getAddress());
        System.out.println("    registry_mint      " + registryMint.getPolicyId());

        List<String> mintOrder = new ArrayList<>(List.of(
                registryMint.getPolicyId(), protocolParamsMint.getPolicyId(),
                issuanceCborHexMint.getPolicyId()));
        mintOrder.sort(String::compareTo);
        System.out.println("  mint redeemer indices (sorted policy order):");
        for (int i = 0; i < mintOrder.size(); i++) {
            String policy = mintOrder.get(i);
            String which = policy.equals(registryMint.getPolicyId()) ? "registry_mint (RegistryInit)"
                    : policy.equals(protocolParamsMint.getPolicyId()) ? "protocol_params_mint"
                    : "issuance_cbor_hex_mint";
            System.out.println("    index " + i + " -> " + which + "  " + policy);
        }
        System.out.println("  seeds: utxo1=" + utxo1.getTxHash() + "#" + utxo1.getOutputIndex()
                + "  utxo2=" + utxo2.getTxHash() + "#" + utxo2.getOutputIndex());

        Result<String> result = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(admin.baseAddress())
                .withSigner(SignerProviders.signerFrom(admin))
                .withTxEvaluator(new AikenTransactionEvaluator(backendService))
                .mergeOutputs(false)
                .completeAndWait(System.out::println);

        if (!result.isSuccessful()) {
            throw new IllegalStateException("CIP-113 bootstrap failed: " + result.getResponse());
        }

        return new Deployed(result.getValue(),
                HexUtil.encodeHexString(protocolParamsMint.getScriptHash()),
                HexUtil.encodeHexString(registryMint.getScriptHash()),
                HexUtil.encodeHexString(issuanceCborHexMint.getScriptHash()),
                HexUtil.encodeHexString(plb.getScriptHash()),
                HexUtil.encodeHexString(transfer.getScriptHash()),
                HexUtil.encodeHexString(thirdParty.getScriptHash()),
                HexUtil.encodeHexString(unfracking.getScriptHash()),
                HexUtil.encodeHexString(registrySpend.getScriptHash()),
                List.of(plb, transfer, thirdParty, unfracking),
                List.of(alwaysFail, coordinationSpend, protocolParamsMint, plb, transfer,
                        thirdParty, unfracking, upgradeMultisig, issuanceCborHexMint,
                        registrySpend, registryMint));
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

    private PlutusData buildIssuanceTemplate(PlutusScript plb, PlutusScript registryMint,
                                             PlutusScript protocolParamsMint) throws Exception {
        PlutusScript dummy = apply("issuance_mint.issuance_mint.mint",
                scriptCredential(plb),
                BytesPlutusData.of(registryMint.getScriptHash()),
                ConstrPlutusData.of(1, BytesPlutusData.of(HexUtil.decodeHexString(DUMMY_POLICY_ID))),
                // A bare policy id, not a credential. This parameter used to be a Credential in the
                // same position with the same arity, and passing the old shape still produces a
                // template — one whose derived policy ids registry_mint then refuses.
                BytesPlutusData.of(protocolParamsMint.getScriptHash()));

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

        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(parts[0])),
                BytesPlutusData.of(HexUtil.decodeHexString(parts[1])));
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
        lines.add("registry spend    : " + d.registrySpendHash());
        lines.add("issuance tmpl cs  : " + d.issuanceCborHexCs());
        return lines;
    }
}
