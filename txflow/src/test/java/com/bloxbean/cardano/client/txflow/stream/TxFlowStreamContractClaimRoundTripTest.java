package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.ScriptCollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.codec.FlowFormat;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion;
import com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions;
import com.bloxbean.cardano.client.txflow.codec.PortableFlowValidator;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic proof (no devnet needed) that the escrow-claim transaction
 * shape used by TxFlowStreamContractIntegrationTest is PORTABLE: the unified
 * {@link Tx} script surface — {@code collectFrom(utxo, redeemer)} plus
 * {@code attachSpendingValidator(script)} — survives the exact re-encoding the
 * stream performs at submit ({@code TxPlan} YAML round-trip and the portable
 * flow codec), and {@link PortableFlowValidator} accepts the single-step flow.
 */
class TxFlowStreamContractClaimRoundTripTest {

    /**
     * compiledCode of validator {@code address_check.address_check.spend} from
     * annotation-processor/src/it/resources/blueprint/address_check/plutus.json
     * (CIP-57 blueprint, plutusVersion v3).
     */
    private static final String VAULT_COMPILED_CODE =
            "59010001010029800aba2aba1aab9faab9eaab9dab9a48888896600264646644b30013370e900118031baa00189"
            + "94c004c02800660146016003370e90002444b30013001300a375400d132325980098080014566002600660186"
            + "ea8012264b30013004300d37540031323322330020020012259800800c528456600266e3cdd71809800801c52"
            + "8c4cc008008c05000500f20243758602260246024602460246024602460246024601e6ea8c044030dd7180818"
            + "071baa0018b2018300f300d3754601e601a6ea8c03cc034dd500245900b45900e1bae300e001300b375400d16"
            + "402430073754003164014600e002600e6010002600e00260066ea801e29344d95900101";

    private static final String OPERATOR_REF = "account://operator";
    private static final String ESCROW_TX_HASH =
            "d5deb18908c95d445a17a4275e00a97f1abdb85a24e3e1cb9e2f8b40b1e30f4c";
    private static final String BENEFICIARY_ADDRESS =
            "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
    private static final String ADMIN_KEY_HASH_HEX =
            "c88b0842103eb87b347dd876c805a13ca7e135ce3e8bdfb308fe216b";

    @Test
    void claimShapeSurvivesTxPlanYamlRoundTripAndPortableEncoding() {
        PlutusScript vaultScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                VAULT_COMPILED_CODE, PlutusVersion.v3);
        Utxo escrowUtxo = Utxo.builder()
                .txHash(ESCROW_TX_HASH)
                .outputIndex(0)
                .amount(List.of(Amount.ada(5)))
                .build();
        PlutusData redeemer = ConstrPlutusData.of(0,
                BytesPlutusData.of("order-1".getBytes(StandardCharsets.UTF_8)));

        // The exact claim shape the integration test streams.
        Tx claim = new Tx()
                .collectFrom(escrowUtxo, redeemer)
                .payToAddress(BENEFICIARY_ADDRESS, Amount.ada(5))
                .attachSpendingValidator(vaultScript)
                .fromRef(OPERATOR_REF);
        TxPlan claimPlan = TxPlan.from(claim)
                .withSigner(OPERATOR_REF)
                .collateralPayerRef(OPERATOR_REF)
                .withRequiredSigners(ADMIN_KEY_HASH_HEX);

        // 1. TxPlan YAML round-trip — the stream's re-encoding of a plan.
        TxPlan reparsedPlan = TxPlan.from(claimPlan.toYaml());
        assertClaimShape(reparsedPlan, vaultScript, redeemer);
        assertEquals(OPERATOR_REF, reparsedPlan.getCollateralPayerRef());
        assertTrue(reparsedPlan.getRequiredSigners().contains(ADMIN_KEY_HASH_HEX));
        assertEquals(1, reparsedPlan.getSignerRefs().size());
        assertEquals(OPERATOR_REF, reparsedPlan.getSignerRefs().get(0).getRef());

        // 2. The portable validator accepts the single-step flow (this is the
        // check TxFlowStream.submit runs before accepting the item).
        TxFlow claimFlow = TxFlow.builder("claim-flow")
                .addStep(FlowStep.builder("claim").withTxPlan(claimPlan).build())
                .build();
        List<FlowDiagnostic> diagnostics = PortableFlowValidator.validate(claimFlow);
        assertTrue(diagnostics.isEmpty(), () -> "expected portable claim flow: " + diagnostics);

        // 3. Portable flow codec write -> parse -> compile — the stream's
        // submit-time encoding plus the engine's materialization — keeps the
        // script intents intact end to end.
        TxFlowCodec codec = TxFlowCodec.standard();
        String encoded = codec.write(claimFlow,
                FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
        TxFlow decoded = codec.parse(encoded, FlowParseOptions.serverDefaults()).requireFlow();
        assertEquals(1, decoded.getSteps().size());
        FlowCompilationResult compiled = new TxFlowCompiler().compile(
                FlowCompilationRequest.of(decoded, FlowBindings.empty()));
        assertTrue(!compiled.hasErrors(),
                () -> "expected clean compile: " + compiled.getDiagnostics());
        TxPlan decodedPlan = compiled.requireCompiledFlow().getExecutionPlan()
                .getSteps().get(0).getTxPlan();
        assertNotNull(decodedPlan, "compiled step should carry a materialized TxPlan");
        assertClaimShape(decodedPlan, vaultScript, redeemer);
    }

    private void assertClaimShape(TxPlan plan, PlutusScript vaultScript, PlutusData redeemer) {
        assertEquals(1, plan.getTxs().size());
        Tx tx = (Tx) plan.getTxs().get(0);
        assertEquals(OPERATOR_REF, tx.getFromRef());

        ScriptCollectFromIntent collect = findIntent(tx, ScriptCollectFromIntent.class);
        assertEquals(1, collect.getUtxoRefs().size());
        assertEquals(ESCROW_TX_HASH, collect.getUtxoRefs().get(0).getTxHash());
        assertTrue(collect.hasRedeemer(), "claim must keep its redeemer");
        // The structured redeemer decodes back to the identical PlutusData.
        ScriptCollectFromIntent resolved =
                (ScriptCollectFromIntent) collect.resolveVariables(new HashMap<>());
        assertNotNull(resolved.getRedeemerData());
        assertEquals(redeemer.serializeToHex(), resolved.getRedeemerData().serializeToHex());

        ScriptValidatorAttachmentIntent attachment =
                findIntent(tx, ScriptValidatorAttachmentIntent.class);
        assertEquals(RedeemerTag.Spend, attachment.getRole());
        assertEquals(vaultScript.getCborHex(), attachment.getScriptHex(),
                "inline validator must ride the plan as cbor_hex");
        assertEquals(PlutusVersion.v3, attachment.getScriptVersion());
    }

    private <T extends TxIntent> T findIntent(Tx tx, Class<T> type) {
        return tx.getIntentions().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected " + type.getSimpleName() + " in " + tx.getIntentions()));
    }
}
