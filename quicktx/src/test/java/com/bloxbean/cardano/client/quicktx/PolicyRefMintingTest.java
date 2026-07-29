package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.intent.MintingIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.quicktx.signing.SignerBinding;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.hdwallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicyRefMintingTest extends QuickTxBaseTest {

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
    void yaml_roundTrip_preservesPolicyRefWithoutScriptFields() {
        Tx tx = new Tx()
                .mintAssets(PolicyRef.ref("policy://nft"), new Asset("ExampleToken", BigInteger.ONE), receiver)
                .from(sender);

        String yaml = TxPlan.from(tx).toYaml();

        assertThat(yaml).contains("policy_ref: policy://nft");
        assertThat(yaml).doesNotContain("script_hex");
        assertThat(yaml).doesNotContain("script_type");

        Tx restoredTx = (Tx) TxPlan.from(yaml).getTxs().get(0);
        MintingIntent restoredIntent = (MintingIntent) restoredTx.getIntentions().get(0);
        assertThat(restoredIntent.getPolicyRef()).isEqualTo("policy://nft");
    }

    @Test
    void yaml_variableResolution_resolvesPolicyRef() {
        String yaml = """
                version: 1.0
                variables:
                  policy_uri: policy://nft
                transaction:
                  - tx:
                      from: addr_test1qfrom
                      intents:
                        - type: minting
                          policy_ref: ${policy_uri}
                          assets:
                            - name: ExampleToken
                              value: 1
                          receiver: addr_test1qreceiver
                """;

        Tx tx = (Tx) TxPlan.from(yaml).getTxs().get(0);
        MintingIntent intent = (MintingIntent) tx.getIntentions().get(0);

        assertThat(intent.getPolicyRef()).isEqualTo("policy://nft");
    }

    @Test
    void build_resolvesPolicyRefToPolicyScriptAndSigner() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("policy-ref", 1, 1);
        Asset asset = new Asset("ExampleToken", BigInteger.valueOf(10));
        givenSenderUtxo();

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(5))
                .mintAssets(PolicyRef.ref("policy://nft"), asset, sender)
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withSignerRegistry(new DefaultSignerRegistry().addPolicy("policy://nft", policy))
                .build();

        assertThat(transaction.getBody().getMint()).contains(MultiAsset.builder()
                .policyId(policy.getPolicyId())
                .assets(List.of(asset))
                .build());
        assertThat(transaction.getWitnessSet().getNativeScripts()).contains(policy.getPolicyScript());

        String yamlAfterBuild = TxPlan.from(tx).toYaml();
        assertThat(yamlAfterBuild).contains("policy_ref: policy://nft");
        assertThat(yamlAfterBuild).doesNotContain("script_hex");
        assertThat(yamlAfterBuild).doesNotContain("script_type");
    }

    @Test
    void build_withDuplicatePolicyRefSigner_doesNotDuplicateNativeWitness() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("policy-ref", 1, 1);
        Asset asset = new Asset("ExampleToken", BigInteger.valueOf(10));
        givenSenderUtxo();

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(5))
                .mintAssets(PolicyRef.ref("policy://nft"), asset, sender)
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withSignerRegistry(new DefaultSignerRegistry().addPolicy("policy://nft", policy))
                .withSignerRef("policy://nft", "policy")
                .build();

        assertThat(transaction.getWitnessSet().getNativeScripts())
                .filteredOn(script -> script.equals(policy.getPolicyScript()))
                .hasSize(1);
    }

    @Test
    void build_throwsWhenPolicyRefHasNoRegistry() {
        Tx tx = new Tx()
                .mintAssets(PolicyRef.ref("policy://nft"), new Asset("ExampleToken", BigInteger.ONE), receiver)
                .from(sender);

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("policy_ref set but no SignerRegistry configured");
    }

    @Test
    void build_throwsWhenPolicyRefIsUnknown() {
        Tx tx = new Tx()
                .mintAssets(PolicyRef.ref("policy://nft"), new Asset("ExampleToken", BigInteger.ONE), receiver)
                .from(sender);

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withSignerRegistry(new DefaultSignerRegistry())
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Unable to resolve policy_ref: policy://nft");
    }

    @Test
    void build_throwsWhenBindingCannotExposePolicy() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("policy-ref", 1, 1);
        Tx tx = new Tx()
                .mintAssets(PolicyRef.ref("policy://nft"), new Asset("ExampleToken", BigInteger.ONE), receiver)
                .from(sender);

        DefaultSignerRegistry registry = new DefaultSignerRegistry()
                .addCustom("policy://nft", signerOnlyBinding(policy));

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withSignerRegistry(registry)
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Resolved policy_ref does not expose a policy script: policy://nft");
    }

    @Test
    void build_throwsWhenPolicyRefIsCombinedWithScriptFields() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("policy-ref", 1, 1);
        Tx tx = new Tx().from(sender);
        tx.addIntention(MintingIntent.builder()
                .policyRef("policy://nft")
                .scriptHex("00")
                .scriptType(0)
                .assets(List.of(new Asset("ExampleToken", BigInteger.ONE)))
                .receiver(receiver)
                .build());

        assertThatThrownBy(() -> new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .withSignerRegistry(new DefaultSignerRegistry().addPolicy("policy://nft", policy))
                .build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("policy_ref cannot be combined with script_hex or script_type");
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

    private SignerBinding signerOnlyBinding(Policy policy) {
        return new SignerBinding() {
            @Override
            public TxSigner signerFor(String scope) {
                return SignerProviders.signerFrom(policy);
            }

            @Override
            public Optional<Wallet> asWallet() {
                return Optional.empty();
            }

            @Override
            public Optional<String> preferredAddress() {
                return Optional.empty();
            }
        };
    }
}
