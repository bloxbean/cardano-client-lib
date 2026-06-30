package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.quicktx.intent.NativeScriptAttachmentIntent;
import com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntent;
import com.bloxbean.cardano.client.quicktx.script.DefaultScriptRegistry;
import com.bloxbean.cardano.client.quicktx.script.ScriptRegistry;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScriptRegistryAttachmentTest extends QuickTxBaseTest {

    private final String sender = new Account().baseAddress();
    private final String receiver = new Account().baseAddress();

    @Mock
    private UtxoSupplier utxoSupplier;

    private ProtocolParamsSupplier protocolParamsSupplier;

    @Mock
    private TransactionProcessor transactionProcessor;

    @BeforeEach
    void setup() throws Exception {
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;
    }

    @Test
    void validatorScriptRef_roundTripPreservesRefWithoutScriptBytes() {
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.ref("validator://mint"));

        String yaml = TxPlan.from(tx).toYaml();

        assertThat(yaml).contains("script_ref: validator://mint");
        assertThat(yaml).doesNotContain("cbor_hex");
        assertThat(yaml).doesNotContain("version: v");

        Tx restoredTx = (Tx) TxPlan.from(yaml).getTxs().get(0);
        ScriptValidatorAttachmentIntent intent = (ScriptValidatorAttachmentIntent) restoredTx.getIntentions().get(0);
        assertThat(intent.getScriptRef()).isEqualTo("validator://mint");
    }

    @Test
    void nativeScriptHash_roundTripPreservesHashWithoutScriptBytes() throws Exception {
        String scriptHash = ScriptPubkey.createWithNewKey()._1.getPolicyId();
        Tx tx = new Tx()
                .attachNativeScript(ScriptRef.hash(scriptHash));

        String yaml = TxPlan.from(tx).toYaml();

        assertThat(yaml).contains("script_hash: " + scriptHash);
        assertThat(yaml).doesNotContain("script_hex");

        Tx restoredTx = (Tx) TxPlan.from(yaml).getTxs().get(0);
        NativeScriptAttachmentIntent intent = (NativeScriptAttachmentIntent) restoredTx.getIntentions().get(0);
        assertThat(intent.getScriptHash()).isEqualTo(scriptHash);
    }

    @Test
    void validatorScriptHash_roundTripPreservesHashWithoutScriptBytes() throws Exception {
        String scriptHash = plutusScript("49480100002221200101").getPolicyId();
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.hash(scriptHash));

        String yaml = TxPlan.from(tx).toYaml();

        assertThat(yaml).contains("script_hash: " + scriptHash);
        assertThat(yaml).doesNotContain("cbor_hex");
        assertThat(yaml).doesNotContain("version: v");

        Tx restoredTx = (Tx) TxPlan.from(yaml).getTxs().get(0);
        ScriptValidatorAttachmentIntent intent = (ScriptValidatorAttachmentIntent) restoredTx.getIntentions().get(0);
        assertThat(intent.getScriptHash()).isEqualTo(scriptHash);
    }

    @Test
    void nativeScriptRef_roundTripPreservesRefWithoutScriptBytes() {
        Tx tx = new Tx()
                .attachNativeScript(ScriptRef.ref("native://policy"));

        String yaml = TxPlan.from(tx).toYaml();

        assertThat(yaml).contains("script_ref: native://policy");
        assertThat(yaml).doesNotContain("script_hex");

        Tx restoredTx = (Tx) TxPlan.from(yaml).getTxs().get(0);
        NativeScriptAttachmentIntent intent = (NativeScriptAttachmentIntent) restoredTx.getIntentions().get(0);
        assertThat(intent.getScriptRef()).isEqualTo("native://policy");
    }

    @Test
    void yaml_variableResolution_resolvesScriptRefAndScriptHash() throws Exception {
        String nativeScriptHash = ScriptPubkey.createWithNewKey()._1.getPolicyId();
        String yaml = """
                version: 1.0
                variables:
                  validator_ref: validator://mint
                  native_hash: %s
                transaction:
                  - tx:
                      from: addr_test1qfrom
                      scripts:
                        - type: validator
                          role: mint
                          script_ref: ${validator_ref}
                        - type: native_script
                          script_hash: ${native_hash}
                """.formatted(nativeScriptHash);

        Tx tx = (Tx) TxPlan.from(yaml).getTxs().get(0);
        ScriptValidatorAttachmentIntent validatorIntent = tx.getIntentions().stream()
                .filter(ScriptValidatorAttachmentIntent.class::isInstance)
                .map(ScriptValidatorAttachmentIntent.class::cast)
                .findFirst()
                .orElseThrow();
        NativeScriptAttachmentIntent nativeIntent = tx.getIntentions().stream()
                .filter(NativeScriptAttachmentIntent.class::isInstance)
                .map(NativeScriptAttachmentIntent.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(validatorIntent.getScriptRef()).isEqualTo("validator://mint");
        assertThat(nativeIntent.getScriptHash()).isEqualTo(nativeScriptHash);
    }

    @Test
    void attachValidatorScriptRefOverloads_createExpectedRolesAndRefs() {
        Tx tx = new Tx()
                .attachSpendingValidator(ScriptRef.ref("validator://spend"))
                .attachMintValidator(ScriptRef.ref("validator://mint"))
                .attachCertificateValidator(ScriptRef.ref("validator://cert"))
                .attachRewardValidator(ScriptRef.ref("validator://reward"))
                .attachProposingValidator(ScriptRef.ref("validator://propose"))
                .attachVotingValidator(ScriptRef.ref("validator://vote"));

        List<ScriptValidatorAttachmentIntent> validatorIntents = tx.getIntentions().stream()
                .filter(ScriptValidatorAttachmentIntent.class::isInstance)
                .map(ScriptValidatorAttachmentIntent.class::cast)
                .collect(Collectors.toList());

        assertThat(validatorIntents)
                .extracting(ScriptValidatorAttachmentIntent::getRole)
                .containsExactly(RedeemerTag.Spend, RedeemerTag.Mint, RedeemerTag.Cert,
                        RedeemerTag.Reward, RedeemerTag.Proposing, RedeemerTag.Voting);
        assertThat(validatorIntents)
                .extracting(ScriptValidatorAttachmentIntent::getScriptRef)
                .containsExactly("validator://spend", "validator://mint", "validator://cert",
                        "validator://reward", "validator://propose", "validator://vote");
    }

    @Test
    void build_resolvesValidatorScriptRefAndAttachesWitness() throws Exception {
        PlutusV2Script script = plutusScript("49480100002221200101");
        givenSenderUtxo();

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(5))
                .attachMintValidator(ScriptRef.ref("validator://mint"))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(new DefaultScriptRegistry().addPlutusScript("validator://mint", script))
                .build();

        assertThat(transaction.getWitnessSet().getPlutusV2Scripts()).contains(script);
        assertThat(TxPlan.from(tx).toYaml()).contains("script_ref: validator://mint")
                .doesNotContain("cbor_hex");
    }

    @Test
    void build_resolvesNativeScriptRefAndAttachesWitness() throws Exception {
        ScriptPubkey script = ScriptPubkey.createWithNewKey()._1;
        givenSenderUtxo();

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(5))
                .attachNativeScript(ScriptRef.ref("native://policy"))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(new DefaultScriptRegistry().addNativeScript("native://policy", script))
                .build();

        assertThat(transaction.getWitnessSet().getNativeScripts()).contains(script);
        assertThat(TxPlan.from(tx).toYaml()).contains("script_ref: native://policy")
                .doesNotContain("script_hex");
    }

    @Test
    void build_resolvesNativeScriptHashAndAttachesWitness() throws Exception {
        ScriptPubkey script = ScriptPubkey.createWithNewKey()._1;
        givenSenderUtxo();

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(5))
                .attachNativeScript(ScriptRef.hash(script.getPolicyId()))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(new DefaultScriptRegistry().addNativeScript("native://policy", script))
                .build();

        assertThat(transaction.getWitnessSet().getNativeScripts()).contains(script);
        assertThat(TxPlan.from(tx).toYaml()).contains("script_hash: " + script.getPolicyId())
                .doesNotContain("script_hex");
    }

    @Test
    void build_resolvesValidatorScriptHashThroughScriptSupplier() throws Exception {
        PlutusV2Script script = plutusScript("49480100002221200101");
        String scriptHash = script.getPolicyId();
        ScriptSupplier scriptSupplier = hash -> scriptHash.equals(hash) ? Optional.of(script) : Optional.empty();
        givenSenderUtxo();

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(5))
                .attachMintValidator(ScriptRef.hash(scriptHash))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptSupplier(scriptSupplier)
                .build();

        assertThat(transaction.getWitnessSet().getPlutusV2Scripts()).contains(script);
    }

    @Test
    void build_throwsWhenScriptReferenceHasNoRegistry() {
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.ref("validator://mint"));

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("script_ref/script_hash set but no ScriptRegistry or ScriptSupplier configured");
    }

    @Test
    void build_throwsWhenScriptHashUnknown() throws Exception {
        String scriptHash = plutusScript("49480100002221200101").getPolicyId();
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.hash(scriptHash));

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(new DefaultScriptRegistry())
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Unable to resolve script_hash");
    }

    @Test
    void build_throwsWhenScriptHashIsNotHex() {
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.hash("not-a-script-hash"));

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(new DefaultScriptRegistry())
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("script_hash must be hex encoded");
    }

    @Test
    void build_throwsWhenScriptHashHasWrongLength() {
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.hash("abcd"));

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(new DefaultScriptRegistry())
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("script_hash must be 28 bytes");
    }

    @Test
    void build_throwsWhenResolvedScriptKindIsWrong() throws Exception {
        ScriptPubkey script = ScriptPubkey.createWithNewKey()._1;
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.ref("native://policy"));

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(new DefaultScriptRegistry().addNativeScript("native://policy", script))
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("not a PlutusScript");
    }

    @Test
    void build_throwsWhenResolvedScriptHashMismatches() throws Exception {
        PlutusV2Script requestedScript = plutusScript("49480100002221200101");
        PlutusV2Script wrongScript = plutusScript("49480100002221200102");
        Tx tx = new Tx()
                .attachMintValidator(ScriptRef.hash(requestedScript.getPolicyId()));

        ScriptRegistry registry = new ScriptRegistry() {
            @Override
            public Optional<Script> resolve(String ref) {
                return Optional.empty();
            }

            @Override
            public Optional<Script> resolveByHash(String scriptHash) {
                return Optional.of(wrongScript);
            }
        };

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withScriptRegistry(registry)
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Resolved script hash mismatch");
    }

    @Test
    void defaultScriptRegistry_throwsWhenSupplierReturnsDifferentScriptHash() throws Exception {
        PlutusV2Script requestedScript = plutusScript("49480100002221200101");
        PlutusV2Script wrongScript = plutusScript("49480100002221200102");
        DefaultScriptRegistry registry = new DefaultScriptRegistry()
                .withScriptSupplier(hash -> Optional.of(wrongScript));

        assertThatThrownBy(() -> registry.resolveByHash(requestedScript.getPolicyId()))
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Resolved script hash mismatch");
    }

    private PlutusV2Script plutusScript(String cborHex) {
        return PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex(cborHex)
                .build();
    }

    private void givenSenderUtxo() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(List.of(
                Utxo.builder()
                        .address(sender)
                        .txHash(generateRandomHexValue(32))
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(100)))
                        .build()
        ));
    }
}
