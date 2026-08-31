package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The guards that turn an unbuildable transaction into a message naming the cause.
 *
 * <p>Each of these fires on a path that would otherwise reach the chain and be rejected by a
 * validator with no usable detail — an empty trace, or a redeemer index pointing at the wrong
 * thing. They are the difference between "this seizure pairs an input against the wrong output"
 * and "EvaluationFailure". Untested, they are just assertions someone believed once.</p>
 */
class Cip113TransactionMaterializerGuardsTest {

    private static final String POLICY = "5f7db4c0db37164903ade4e952db632245e048bbe76a5aae140ec15b";
    private static final String REGISTRY_NODE_CS =
            "59fd9f91c09ab82bbc40b58e62c455eab8d3105ca3d2f46948af7b0c";
    private static final String BASE_HASH =
            "f2182b00a37bd746e20575c9af01ab31312213514cd31e872e0a2a3e";
    private static final String LOGIC_HASH =
            "4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10";

    // ------------------------------------------------------------ pure guards

    /**
     * A credential decoded from a datum can carry no bytes: {@code BytesPlutusData.getValue()} is
     * nullable, and {@code HexUtil.encodeHexString} answers null rather than throwing. Without
     * this the failure is an NPE inside {@code toLowerCase()} naming neither token nor role.
     * SonarCloud javabugs:S2259.
     */
    @Test
    void aLogicCredentialWithNoHashIsRefusedByRole() {
        assertThatThrownBy(() -> Cip113TransactionMaterializer.logicScriptHash(
                Credential.fromScript((byte[]) null), "transfer logic"))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("transfer logic")
                .hasMessageContaining("no script hash");

        assertThatThrownBy(() -> Cip113TransactionMaterializer.logicScriptHash(
                Credential.fromScript(new byte[0]), "minting logic"))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("minting logic");
    }

    @Test
    void aLogicCredentialWithAHashResolvesToLowercaseHex() {
        assertThat(Cip113TransactionMaterializer.logicScriptHash(
                Credential.fromScript(LOGIC_HASH.toUpperCase()), "transfer logic"))
                .isEqualTo(LOGIC_HASH);
    }

    /**
     * The registry node created by a registration is found by its NFT, whose asset name is the
     * policy id. If it is absent, {@code issuance_mint}'s OutputIndex proof would point at some
     * unrelated output.
     */
    @Test
    void aMissingNodeOutputIsRefusedRatherThanPointedAtBlindly() {
        Transaction txn = txWith(
                outputAt("addr_a", REGISTRY_NODE_CS, "aaaa"),   // a node, but a different policy
                outputAt("addr_b", null, null));

        assertThatThrownBy(() -> Cip113TransactionMaterializer.indexOfNodeOutput(txn, REGISTRY_NODE_CS, POLICY))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("registry-node NFT")
                .hasMessageContaining(POLICY);
    }

    @Test
    void theNodeOutputIsFoundByItsPolicyNamedNft() {
        Transaction txn = txWith(
                outputAt("addr_x", null, null),
                outputAt("addr_y", REGISTRY_NODE_CS, POLICY));

        assertThat(Cip113TransactionMaterializer.indexOfNodeOutput(txn, REGISTRY_NODE_CS, POLICY)).isEqualTo(1);
    }

    /**
     * {@code third_party} pairs inputs to outputs by position from {@code outputs_start_idx}, so
     * anything interleaved among the continuing outputs pairs an input against an output that is
     * not its continuation.
     */
    @Test
    void interleavedContinuingOutputsAreRefused() {
        List<TransactionOutput> outputs = List.of(
                outputAt("holder", null, null),
                outputAt("someone_else", null, null),     // breaks the run
                outputAt("holder", null, null));

        assertThatThrownBy(() -> Cip113TransactionMaterializer.firstContiguousRun(outputs, "holder", 2))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("not contiguous");
    }

    @Test
    void aContiguousRunResolvesToItsFirstIndex() {
        List<TransactionOutput> outputs = List.of(
                outputAt("recipient", null, null),
                outputAt("holder", null, null),
                outputAt("holder", null, null),
                outputAt("change", null, null));

        assertThat(Cip113TransactionMaterializer.firstContiguousRun(outputs, "holder", 2)).isEqualTo(1);
    }

    @Test
    void noContinuingOutputAtAllIsRefused() {
        assertThatThrownBy(() -> Cip113TransactionMaterializer.firstContiguousRun(
                List.of(outputAt("recipient", null, null)), "holder", 1))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("No output at holder");
    }

    // ------------------------------------------------------- guards behind wiring

    /**
     * {@code is_field_updated_registry_node} freezes key, next and minting_logic_script. Changing
     * one produces a transaction {@code registry_spend} rejects, so say which field moved.
     */
    @Test
    void changingAFrozenRegistryFieldIsRefusedByFieldName() {
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Cip113TransactionMaterializer tx = new Cip113TransactionMaterializer()
                .from(ownerAddress().toBech32())
                .updateRegistryNode(onChain.toBuilder().next("eeee").build(), BigIntPlutusData.of(0));

        assertThatThrownBy(() -> tx.wire(serviceWith(onChain), mock(UtxoSupplier.class)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("next is frozen")
                .hasMessageContaining("ffff -> eeee")
                .hasMessageContaining("linked-list structure");
    }

    @Test
    void changingTheMintingLogicIsRefusedToo() {
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Cip113TransactionMaterializer tx = new Cip113TransactionMaterializer()
                .from(ownerAddress().toBech32())
                .updateRegistryNode(onChain.toBuilder()
                        .mintingLogicScript(Credential.fromScript(BASE_HASH)).build(),
                        BigIntPlutusData.of(0));

        assertThatThrownBy(() -> tx.wire(serviceWith(onChain), mock(UtxoSupplier.class)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("minting_logic_script")
                .hasMessageContaining("derive the policy id");
    }

    /**
     * The continuing outputs are located by address, so a destination at the holder's own smart
     * wallet is indistinguishable from them — and the seizure moves nothing anyway.
     */
    @Test
    void seizingBackIntoTheSameWalletIsRefused() {
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Address owner = ownerAddress();

        Cip113TransactionMaterializer tx = new Cip113TransactionMaterializer()
                .from(owner.toBech32())
                .thirdPartyFrom(owner)
                .payToAddress(owner.toBech32(), Amount.asset(POLICY, "Tok", BigInteger.ONE))
                .withRedeemer(POLICY, BigIntPlutusData.of(0));

        assertThatThrownBy(() -> tx.wire(serviceWith(onChain), mock(UtxoSupplier.class)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("same smart wallet it seized them from");
    }

    // ------------------------------------------------- mintAsset routes by token

    /**
     * An unregistered policy is not a programmable token, so {@code mintAsset} must hand it
     * straight to {@code Tx} — that is what lets one transaction mint a programmable token and an
     * ordinary native asset together. Asserting the intent reaches CCL is the point; asserting the
     * call was merely recorded would pass even if the routing were inverted.
     */
    @Test
    void mintingAnUnregisteredPolicyBecomesAnOrdinaryTxMint() {
        RegistryLookup empty = mock(RegistryLookup.class);
        when(empty.byPolicy(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        when(service.registryLookup()).thenReturn(empty);

        String unregistered = "aa".repeat(28);
        Cip113TransactionMaterializer tx = new Cip113TransactionMaterializer()
                .from(ownerAddress().toBech32())
                .mintAsset(unregistered, new Asset("0x546f6b", BigInteger.TEN),
                        BigIntPlutusData.of(0), ownerAddress().toBech32());

        tx.wire(service, mock(UtxoSupplier.class));

        assertThat(tx.getIntentions())
                .as("an unregistered policy must reach Tx's own minting intent, untouched")
                .anyMatch(i -> i instanceof com.bloxbean.cardano.client.quicktx.intent.ScriptMintingIntent
                        && unregistered.equals(
                                ((com.bloxbean.cardano.client.quicktx.intent.ScriptMintingIntent) i)
                                        .getPolicyId()));
    }

    /** Inline datums are valid seizable output shapes and must reach the mint materializer. */
    @Test
    void mintingAProgrammableTokenWithAnInlineDatumIsAccepted() {
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Cip113TransactionMaterializer tx = new Cip113TransactionMaterializer()
                .from(ownerAddress().toBech32())
                .mintAsset(POLICY, List.of(new Asset("0x546f6b", BigInteger.TEN)),
                        BigIntPlutusData.of(0), ownerAddress().toBech32(), BigIntPlutusData.of(1));

        assertThatThrownBy(() -> tx.wire(serviceWith(onChain), mock(UtxoSupplier.class)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("issuance template")
                .hasMessageNotContaining("cannot carry a datum");
    }

    // ------------------------------------------------------------------ fixtures

    private static Address ownerAddress() {
        return AddressProvider.getEntAddress(
                Credential.fromKey("22222222222222222222222222222222222222222222222222222222"),
                Networks.testnet());
    }

    private static RegistryNode node(String key, String next, String logicHash) {
        Credential logic = Credential.fromScript(logicHash);
        return RegistryNode.builder()
                .key(key).next(next)
                .mintingLogicScript(logic)
                .transferLogicScript(logic)
                .thirdPartyTransferLogicScript(logic)
                .unfrackingLogicScript(logic)
                .globalStateCs("")
                .build();
    }

    /** A service resolved far enough that the guards under test are reachable. */
    private static Cip113ProtocolService serviceWith(RegistryNode onChain) {
        Cip113Deployment deployment = Cip113Deployment.builder()
                .network(Networks.testnet())
                .programmableLogicBaseHash(BASE_HASH)
                .registryNodeCs(REGISTRY_NODE_CS)
                .build();

        Utxo nodeUtxo = Utxo.builder()
                .txHash("11".repeat(32)).outputIndex(0)
                .address(SmartWalletAddress.ofPaymentCredential(deployment, ownerAddress()).toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L))))
                .build();

        RegistryLookup registry = mock(RegistryLookup.class);
        when(registry.byPolicy(POLICY))
                .thenReturn(Optional.of(new RegistryLookup.RegistryNodeUtxo(nodeUtxo, onChain)));

        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(deployment);
        when(service.registryLookup()).thenReturn(registry);
        when(service.scripts()).thenReturn(new DeploymentScripts((com.bloxbean.cardano.client.api.ScriptSupplier) null, deployment));
        when(service.coordinationUtxo()).thenReturn(Utxo.builder()
                .txHash("22".repeat(32)).outputIndex(0)
                .address("addr_test1coordination")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L))))
                .build());
        return service;
    }

    private static Transaction txWith(TransactionOutput... outputs) {
        Transaction txn = new Transaction();
        txn.setBody(TransactionBody.builder().outputs(List.of(outputs)).build());
        return txn;
    }

    private static TransactionOutput outputAt(String address, String policy, String assetNameHex) {
        Value value = Value.builder().coin(BigInteger.valueOf(2_000_000L)).build();
        if (policy != null) {
            value = value.toBuilder()
                    .multiAssets(List.of(MultiAsset.builder()
                            .policyId(policy)
                            .assets(List.of(new Asset("0x" + assetNameHex, BigInteger.ONE)))
                            .build()))
                    .build();
        }
        return TransactionOutput.builder().address(address).value(value).build();
    }

    /** Guards against a silently-changed hex helper in the fixtures above. */
    @Test
    void fixtureAssetNamesRoundTripAsHex() {
        assertThat(HexUtil.encodeHexString(HexUtil.decodeHexString(POLICY))).isEqualTo(POLICY);
    }
}
