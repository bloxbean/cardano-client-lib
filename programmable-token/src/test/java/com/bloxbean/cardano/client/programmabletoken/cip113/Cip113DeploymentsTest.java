package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every hash a built-in deployment carries, re-derived from the vendored v0.0.1 blueprint.
 *
 * <p>The values the chain cannot state — the base script, registry, inline-datum bound and the
 * unfracking parameter the dispatcher was compiled with — are exactly the ones a typo would ship
 * unnoticed, and a wrong base-script hash sends tokens to an address no validator controls. So
 * each constant is recomputed here by applying the deployer's recorded parameters to the
 * sha-pinned blueprint, in dependency order, and the constant must equal the result. Only the
 * one-shot seed outrefs and the always-fail hash are taken on trust; everything else follows.</p>
 */
class Cip113DeploymentsTest {

    private static final Map<String, String> compiledCode = new HashMap<>();

    /** The 28 zero bytes every public deployment compiled its dispatcher's unfracking slot with. */
    private static final String NO_UNFRACKING = "00".repeat(28);

    record Seeds(String name, Cip113Deployment deployment,
                 String paramsSeedTx, int paramsSeedIdx,
                 String registrySeedTx, int registrySeedIdx,
                 String issuanceSeedTx, int issuanceSeedIdx,
                 String alwaysFailHash) {
        @Override public String toString() {
            return name;
        }
    }

    /** One-shot seeds and the always-fail hash, from each deployment's bootstrap record. */
    static Stream<Seeds> deployments() {
        return Stream.of(
                new Seeds("PREVIEW", Cip113Deployments.PREVIEW,
                        "529425c34280c68e81b384a2f12c355c1258578b152ebb5f6116d26cdb0b6da0", 7,
                        "529425c34280c68e81b384a2f12c355c1258578b152ebb5f6116d26cdb0b6da0", 7,
                        "8d851d35445842753ec5189a67de01a9570618af335a26f0b6764f2d77841780", 5,
                        "f56ecc476fe566d166e9f9cbf3eb935f2f939247abe44bf746a8d16a"),
                new Seeds("PREPROD", Cip113Deployments.PREPROD,
                        "0f31bf25a84e026bd0cbaba02e53bcbfee371a73a58575b2f03e65a2873f8089", 1,
                        "0f31bf25a84e026bd0cbaba02e53bcbfee371a73a58575b2f03e65a2873f8089", 1,
                        "bd3e0a34329b9d211f56b58b820df52735a6fe0e2b21bfefa79323633e440b36", 1,
                        "4bbe11a924a833ea06b488439409365ec41e270046fd9bef59c8b02d"),
                new Seeds("MAINNET", Cip113Deployments.MAINNET,
                        "d377232b00dd7c421f50ba2d0de9251945947d8e5df579a6ffc87c4e2fce98ac", 3,
                        "d377232b00dd7c421f50ba2d0de9251945947d8e5df579a6ffc87c4e2fce98ac", 3,
                        "d377232b00dd7c421f50ba2d0de9251945947d8e5df579a6ffc87c4e2fce98ac", 0,
                        "75ca74b539789793532627e82c9a2368a9ebad577d797597a9f9a0d5"));
    }

    @BeforeAll
    static void loadBlueprint() throws Exception {
        try (InputStream in = Cip113DeploymentsTest.class.getResourceAsStream("/blueprint/cip113/plutus.json")) {
            JsonNode blueprint = new ObjectMapper().readTree(in);
            assertThat(blueprint.path("preamble").path("version").asText()).isEqualTo("0.0.1");
            blueprint.get("validators").forEach(v ->
                    compiledCode.put(v.get("title").asText(), v.get("compiledCode").asText()));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deployments")
    void everyHashIsTheBlueprintAppliedToTheRecordedParameters(Seeds seeds) {
        Cip113Deployment d = seeds.deployment();

        String paramsPolicy = apply("protocol_params.protocol_params.mint",
                outputReference(seeds.paramsSeedTx(), seeds.paramsSeedIdx()));
        assertThat(d.getParamsPolicy()).as("paramsPolicy").isEqualTo(paramsPolicy);

        String templatePolicy = apply("issuance_cbor_hex_mint.issuance_cbor_hex_mint.mint",
                outputReference(seeds.issuanceSeedTx(), seeds.issuanceSeedIdx()), bytes(seeds.alwaysFailHash()));
        assertThat(d.getIssuanceCborHexCs()).as("issuanceCborHexCs").isEqualTo(templatePolicy);

        String registry = apply("registry.registry.mint",
                outputReference(seeds.registrySeedTx(), seeds.registrySeedIdx()), bytes(templatePolicy));
        assertThat(d.getRegistryNodeCs()).as("registryNodeCs").isEqualTo(registry);

        String plb = apply("programmable_logic_base.programmable_logic_base.spend", bytes(paramsPolicy));
        assertThat(d.getProgrammableLogicBaseHash()).as("programmableLogicBaseHash").isEqualTo(plb);

        // The delegates and issuance_logic are compiled with the deployment's inline-datum bound,
        // so matching them proves maxInlineDatumBytes too.
        PlutusData plbCred = scriptCredential(plb);
        PlutusData bound = BigIntPlutusData.of(d.getMaxInlineDatumBytes());
        assertThat(d.getMaxInlineDatumBytes()).isEqualTo(1024);
        assertThat(d.getTransferScriptHash()).as("transferScriptHash")
                .isEqualTo(apply("transfer.transfer.withdraw", plbCred, bytes(registry), bound));
        assertThat(d.getThirdPartyScriptHash()).as("thirdPartyScriptHash")
                .isEqualTo(apply("third_party.third_party.withdraw", plbCred, bytes(registry), bound));
        assertThat(d.getUnfrackingScriptHash()).as("unfrackingScriptHash")
                .isEqualTo(apply("unfracking.unfracking.withdraw", plbCred, bytes(registry), bound));
        assertThat(d.getIssuanceLogicHash()).as("issuanceLogicHash")
                .isEqualTo(apply("issuance_logic.issuance_logic.withdraw",
                        plbCred, bytes(registry), bytes(paramsPolicy), bound));

        // The dispatcher was compiled against 28 zero bytes rather than the unfracking script —
        // which is what makes unfracking impossible on this deployment, and why the constant says so.
        assertThat(d.getProgrammableLogicGlobalHash()).as("programmableLogicGlobalHash")
                .isEqualTo(apply("programmable_logic_global.programmable_logic_global.withdraw",
                        bytes(d.getTransferScriptHash()), bytes(d.getThirdPartyScriptHash()), bytes(NO_UNFRACKING)));
        assertThat(d.getProgrammableLogicGlobalHash())
                .as("a dispatcher able to unfrack would hash differently")
                .isNotEqualTo(apply("programmable_logic_global.programmable_logic_global.withdraw",
                        bytes(d.getTransferScriptHash()), bytes(d.getThirdPartyScriptHash()),
                        bytes(d.getUnfrackingScriptHash())));
        assertThat(d.isUnfrackingEnabled()).isFalse();
    }

    /** Each reference script the deployment points at is keyed by a hash the deployment names. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("deployments")
    void referenceScriptsAreKeyedByTheDeploymentsOwnHashes(Seeds seeds) {
        Cip113Deployment d = seeds.deployment();
        assertThat(d.getReferenceScripts().keySet()).contains(
                d.getProgrammableLogicBaseHash(), d.getProgrammableLogicGlobalHash(),
                d.getTransferScriptHash(), d.getThirdPartyScriptHash(),
                d.getUnfrackingScriptHash(), d.getIssuanceLogicHash(), d.getUpgradeScriptHash());
    }

    private static String apply(String title, PlutusData... params) {
        String code = compiledCode.get(title);
        assertThat(code).as("blueprint validator %s", title).isNotNull();
        try {
            return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                    AikenScriptUtil.applyParamToScript(ListPlutusData.of(params), code), PlutusVersion.v3)
                    .getPolicyId();
        } catch (Exception e) {
            throw new AssertionError("could not hash " + title + " applied to its parameters", e);
        }
    }

    private static PlutusData outputReference(String txHash, int index) {
        return ConstrPlutusData.of(0, bytes(txHash), BigIntPlutusData.of(index));
    }

    private static PlutusData scriptCredential(String hash) {
        return ConstrPlutusData.of(1, bytes(hash));
    }

    private static BytesPlutusData bytes(String hex) {
        return BytesPlutusData.of(HexUtil.decodeHexString(hex));
    }
}
