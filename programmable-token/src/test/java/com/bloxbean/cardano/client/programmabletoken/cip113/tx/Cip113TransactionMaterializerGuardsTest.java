package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionBuildContext;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The guards that turn an unbuildable transaction into a message naming the cause.
 *
 * <p>Each of these fires on a path that would otherwise reach the chain and be rejected by a
 * validator with no usable detail — an empty trace, or a redeemer index pointing at the wrong
 * thing. Every guard here fires before a UTxO is selected.</p>
 */
class Cip113TransactionMaterializerGuardsTest {

    private static final String POLICY = "5f7db4c0db37164903ade4e952db632245e048bbe76a5aae140ec15b";
    private static final String OTHER_POLICY = "6f7db4c0db37164903ade4e952db632245e048bbe76a5aae140ec15b";
    private static final String REGISTRY_NODE_CS =
            "59fd9f91c09ab82bbc40b58e62c455eab8d3105ca3d2f46948af7b0c";
    private static final String BASE_HASH =
            "f2182b00a37bd746e20575c9af01ab31312213514cd31e872e0a2a3e";
    private static final String LOGIC_HASH =
            "4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10";

    // ------------------------------------------------------------ pure guards

    /**
     * A credential decoded from a datum can carry no bytes: {@code BytesPlutusData.getValue()} is
     * nullable, and {@code HexUtil.encodeHexString} answers null rather than throwing.
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
     * The delegates pair inputs to outputs by position from {@code outputs_start_idx}, so
     * anything interleaved among the continuing outputs pairs an input against an output that
     * is not its continuation.
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
                .hasMessageContaining("No continuing output at holder");
    }

    /**
     * An unfracking's regrouped output sits at the same wallet as its continuing outputs, so the
     * run is located by content: the outputs that carry no acted-policy asset. Wherever the
     * builder places the regrouped output, the run must skip it.
     */
    @Test
    void unfrackingRunSkipsTheRegroupedOutputAtTheSameWallet() {
        List<TransactionOutput> regroupedFirst = List.of(
                outputAt("wallet", POLICY, "aa"),          // regrouped: carries the acted policy
                outputAt("wallet", OTHER_POLICY, "bb"),    // continuing
                outputAt("wallet", OTHER_POLICY, "cc"),    // continuing
                outputAt("change", null, null));
        assertThat(Cip113TransactionMaterializer.firstContiguousRunWithoutPolicy(
                regroupedFirst, "wallet", 2, POLICY)).isEqualTo(1);

        List<TransactionOutput> regroupedLast = List.of(
                outputAt("wallet", OTHER_POLICY, "bb"),
                outputAt("wallet", OTHER_POLICY, "cc"),
                outputAt("wallet", POLICY, "aa"),
                outputAt("change", null, null));
        assertThat(Cip113TransactionMaterializer.firstContiguousRunWithoutPolicy(
                regroupedLast, "wallet", 2, POLICY)).isEqualTo(0);

        List<TransactionOutput> split = List.of(
                outputAt("wallet", OTHER_POLICY, "bb"),
                outputAt("wallet", POLICY, "aa"),          // regrouped output breaks the run
                outputAt("wallet", OTHER_POLICY, "cc"));
        assertThatThrownBy(() -> Cip113TransactionMaterializer.firstContiguousRunWithoutPolicy(
                split, "wallet", 2, POLICY))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("not contiguous");
    }

    @Test
    void theAdapterAdvertisesUnfracking() {
        assertThat(new Cip113Protocol(mock(Cip113ProtocolService.class)).capabilities())
                .contains(ProgrammableTokenCapability.UNFRACK);
    }

    // ------------------------------------------------- guards on the verbs

    /**
     * {@code is_field_updated_registry_node} freezes key, next and minting_logic_script. Changing
     * one produces a transaction {@code registry_spend} rejects, so say which field moved.
     */
    @Test
    void changingAFrozenRegistryFieldIsRefusedByFieldName() {
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Cip113TransactionMaterializer tx = materializer(onChain, mock(UtxoSupplier.class));

        assertThatThrownBy(() -> tx.updateRegistryNode(
                onChain.toBuilder().next("eeee").build(), BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("next is frozen")
                .hasMessageContaining("ffff -> eeee")
                .hasMessageContaining("linked-list structure");
    }

    @Test
    void changingTheMintingLogicIsRefusedToo() {
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Cip113TransactionMaterializer tx = materializer(onChain, mock(UtxoSupplier.class));

        assertThatThrownBy(() -> tx.updateRegistryNode(
                onChain.toBuilder().mintingLogicScript(Credential.fromScript(BASE_HASH)).build(),
                BigIntPlutusData.of(0)))
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
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113TransactionMaterializer tx = materializer(onChain, utxoSupplier)
                .thirdPartyFrom(ownerAddress())
                .recordTransferForExtension(POLICY, ownerAddress().toBech32(),
                        Amount.asset(POLICY, "Tok", BigInteger.ONE), null);

        assertThatThrownBy(() -> tx.withRedeemer(POLICY, BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("same smart wallet it seized them from");
        verifyNoInteractions(utxoSupplier);
    }

    /** An unregistered policy is not a programmable token; the extension path never falls back. */
    @Test
    void mintingAnUnregisteredPolicyFailsClosed() {
        RegistryLookup empty = mock(RegistryLookup.class);
        when(empty.byPolicy(anyString())).thenReturn(Optional.empty());
        Cip113TransactionMaterializer tx = new Cip113TransactionMaterializer(
                serviceWith(node(POLICY, "ffff", LOGIC_HASH)), mock(UtxoSupplier.class), empty)
                .from(ownerAddress().toBech32());

        String unregistered = "aa".repeat(28);
        assertThatThrownBy(() -> tx.recordMintForExtension(unregistered,
                List.of(new Asset("0x546f6b", BigInteger.TEN)), BigIntPlutusData.of(0),
                ownerAddress().toBech32(), null))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("not registered");
    }

    /** Inline datums are valid seizable output shapes and must reach the mint materializer. */
    @Test
    void mintingAProgrammableTokenWithAnInlineDatumIsAccepted() {
        Cip113TransactionMaterializer tx = materializer(node(POLICY, "ffff", LOGIC_HASH),
                mock(UtxoSupplier.class));

        assertThatThrownBy(() -> tx.recordMintForExtension(POLICY,
                List.of(new Asset("0x546f6b", BigInteger.TEN)), BigIntPlutusData.of(0),
                ownerAddress().toBech32(), BigIntPlutusData.of(1)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("issuance template")
                .hasMessageNotContaining("cannot carry a datum");
    }

    /** {@code max_inline_datum_bytes} bounds holder-created datums; fail before evaluation. */
    @Test
    void anOversizedInlineDatumIsRefusedByTheDeploymentBound() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113TransactionMaterializer tx = materializer(node(POLICY, "ffff", LOGIC_HASH), utxoSupplier);
        PlutusData oversized = com.bloxbean.cardano.client.plutus.spec.BytesPlutusData.of(new byte[64]);

        assertThatThrownBy(() -> tx.recordTransferForExtension(POLICY, ownerAddress().toBech32(),
                Amount.asset(POLICY, "Tok", BigInteger.ONE), oversized))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("max_inline_datum_bytes");
        verifyNoInteractions(utxoSupplier);
    }

    // ----------------------------------------------- verification-key rules

    /**
     * CIP-113 permits key credentials, but this adapter cannot invoke them. Registering such a
     * token would succeed and every later operation on it would fail, so refuse up front.
     */
    @Test
    void registeringAKeyTransferCredentialIsRefusedBeforeChainAccess() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113TransactionMaterializer tx = materializer(node(POLICY, "ffff", LOGIC_HASH), utxoSupplier);
        RegistryNodeSpec spec = RegistryNodeSpec.builder()
                .mintingLogicScript(Credential.fromScript(LOGIC_HASH))
                .transferLogicScript(Credential.fromKey("33".repeat(28)))
                .thirdPartyTransferLogicScript(Credential.fromScript(LOGIC_HASH))
                .build();

        assertThatThrownBy(() -> tx.registerToken(spec, BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("verification-key transfer_logic_script");
        verifyNoInteractions(utxoSupplier);
    }

    @Test
    void updatingToAKeyThirdPartyCredentialIsRefused() {
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Cip113TransactionMaterializer tx = materializer(onChain, mock(UtxoSupplier.class));

        assertThatThrownBy(() -> tx.updateRegistryNode(onChain.toBuilder()
                .thirdPartyTransferLogicScript(Credential.fromKey("33".repeat(28))).build(),
                BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("verification-key third_party_transfer_logic_script");
    }

    /** The empty-key sentinel is data ("unfracking forbidden"), not an operation, so it stays valid. */
    @Test
    void theUnfrackingSentinelIsStillAcceptedForRegistration() {
        Cip113TransactionMaterializer tx = materializer(node(POLICY, "ffff", LOGIC_HASH),
                mock(UtxoSupplier.class));
        RegistryNodeSpec spec = RegistryNodeSpec.builder()
                .mintingLogicScript(Credential.fromScript(LOGIC_HASH))
                .transferLogicScript(Credential.fromScript(LOGIC_HASH))
                .thirdPartyTransferLogicScript(Credential.fromScript(LOGIC_HASH))
                .build();

        // Passes the credential guard and fails on the next missing prerequisite instead.
        assertThatThrownBy(() -> tx.registerToken(spec, BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageNotContaining("unfracking_logic_script")
                .hasMessageContaining("issuance-template");
    }

    // ---------------------------------------------------------- burns

    @Test
    void conflictingBurnIssuanceRedeemersAreRefused() {
        Cip113TransactionMaterializer tx = materializer(node(POLICY, "ffff", LOGIC_HASH),
                mock(UtxoSupplier.class));
        tx.recordBurnForExtension(POLICY, new Asset("0x01", BigInteger.ONE.negate()),
                BigIntPlutusData.of(0), BigIntPlutusData.of(1));

        // Semantically equal Plutus data is fine, whatever its container encoding.
        assertThatCode(() -> tx.recordBurnForExtension(POLICY, new Asset("0x02", BigInteger.ONE.negate()),
                BigIntPlutusData.of(0), BigIntPlutusData.of(1)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> tx.recordBurnForExtension(POLICY,
                new Asset("0x03", BigInteger.ONE.negate()), BigIntPlutusData.of(0), BigIntPlutusData.of(2)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("different issuance redeemers");
    }

    // ---------------------------------------------------------- unfracking

    @Test
    void unfrackingAForbiddenPolicyIsRefusedBeforeUtxoSelection() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        RegistryNode forbidden = node(POLICY, "ffff", LOGIC_HASH).toBuilder()
                .unfrackingLogicScript(RegistryNodeSpec.unfrackingForbidden()).build();
        Cip113TransactionMaterializer tx = materializer(forbidden, utxoSupplier);

        assertThatThrownBy(() -> tx.recordUnfrackForExtension(POLICY, BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("forbidden for policy " + POLICY)
                .hasMessageContaining("sentinel");
        verifyNoInteractions(utxoSupplier);
    }

    @Test
    void unfrackingWithNothingFrackedIsRefused() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        RegistryNode onChain = node(POLICY, "ffff", LOGIC_HASH);
        Cip113TransactionMaterializer tx = materializer(onChain, utxoSupplier);
        String wallet = SmartWalletAddress.ofPaymentCredential(deployment(), ownerAddress()).toBech32();
        when(utxoSupplier.getAll(wallet)).thenReturn(List.of(Utxo.builder()
                .txHash("33".repeat(32)).outputIndex(0).address(wallet)
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)),
                        Amount.asset(POLICY, "Tok", BigInteger.TEN)))          // alone: not fracked
                .build()));

        assertThatThrownBy(() -> tx.recordUnfrackForExtension(POLICY, BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("Nothing to unfrack");
    }

    @Test
    void unfrackingCannotShareATransactionWithOtherOperations() {
        Cip113TransactionMaterializer tx = materializer(node(POLICY, "ffff", LOGIC_HASH),
                mock(UtxoSupplier.class))
                .recordTransferForExtension(POLICY, ownerAddress().toBech32(),
                        Amount.asset(POLICY, "Tok", BigInteger.ONE), null);

        assertThatThrownBy(() -> tx.recordUnfrackForExtension(POLICY, BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("its own transaction");
    }

    // ------------------------------------------ transaction composition rules

    /** The third-party redeemer names one registry node, so one transaction acts on one policy. */
    @Test
    void thirdPartyTransactionsAreLimitedToOnePolicyBeforeAnyLookup() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .thirdPartyTransfer(holderAddress().toBech32(), ownerAddress().toBech32(),
                        Amount.asset(POLICY, "Tok", BigInteger.ONE), BigIntPlutusData.of(0))
                .thirdPartyTransfer(holderAddress().toBech32(), ownerAddress().toBech32(),
                        Amount.asset(OTHER_POLICY, "Tok", BigInteger.ONE), BigIntPlutusData.of(0));

        assertThatThrownBy(() -> prepare(service, utxoSupplier, tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("exactly one policy")
                .hasMessageContaining(POLICY)
                .hasMessageContaining(OTHER_POLICY);
        verifyNoInteractions(utxoSupplier);
        verify(service, never()).registryLookup();
    }

    @Test
    void thirdPartyOutputsForOnePolicyStillAggregate() {
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .thirdPartyTransfer(holderAddress().toBech32(), ownerAddress().toBech32(),
                        Amount.asset(POLICY, "Tok", BigInteger.ONE), BigIntPlutusData.of(0))
                .thirdPartyTransfer(holderAddress().toBech32(), ownerAddress().toBech32(),
                        Amount.asset(POLICY, "Tok", BigInteger.TWO), BigIntPlutusData.of(0));

        // Composition passes; the build then reaches the holder's wallet for input selection.
        assertThatThrownBy(() -> prepare(service, utxoSupplier, tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageNotContaining("exactly one policy");
        verify(utxoSupplier).getAll(
                SmartWalletAddress.ofPaymentCredential(deployment(), holderAddress()).toBech32());
    }

    @Test
    void unfrackingMustBeItsOwnTransaction() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .unfrack(POLICY, BigIntPlutusData.of(0))
                .transfer(ownerAddress().toBech32(), Amount.asset(POLICY, "Tok", BigInteger.ONE),
                        BigIntPlutusData.of(0));

        assertThatThrownBy(() -> prepare(service, utxoSupplier, tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("its own transaction");
        verifyNoInteractions(utxoSupplier);
        verify(service, never()).registryLookup();
    }

    // ---------------------------------- rules that span every Tx fragment of one transaction

    /** Splitting two third-party policies over two fragments is still one ledger transaction. */
    @Test
    void thirdPartyPoliciesSplitAcrossFragmentsAreRefusedBeforeAnyLookup() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        ProgrammableTokenTx first = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .thirdPartyTransfer(holderAddress().toBech32(), ownerAddress().toBech32(),
                        Amount.asset(POLICY, "Tok", BigInteger.ONE), BigIntPlutusData.of(0));
        ProgrammableTokenTx second = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .thirdPartyTransfer(holderAddress().toBech32(), ownerAddress().toBech32(),
                        Amount.asset(OTHER_POLICY, "Tok", BigInteger.ONE), BigIntPlutusData.of(0));

        assertThatThrownBy(() -> prepare(service, utxoSupplier, first, second))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("exactly one policy")
                .hasMessageContaining(POLICY)
                .hasMessageContaining(OTHER_POLICY);
        verifyNoInteractions(utxoSupplier);
        verify(service, never()).registryLookup();
    }

    /** Each fragment would get its own materializer and withdraw-zero invocation. */
    @Test
    void programmableOperationsSplitAcrossFragmentsAreRefusedBeforeAnyLookup() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        ProgrammableTokenTx first = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .transfer(holderAddress().toBech32(), Amount.asset(POLICY, "Tok", BigInteger.ONE),
                        BigIntPlutusData.of(0));
        ProgrammableTokenTx second = new ProgrammableTokenTx()
                .from(holderAddress().toBech32())
                .transfer(ownerAddress().toBech32(), Amount.asset(POLICY, "Tok", BigInteger.ONE),
                        BigIntPlutusData.of(0));

        assertThatThrownBy(() -> prepare(service, utxoSupplier, first, second))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("single Tx")
                .hasMessageContaining("2 Tx fragments");
        verifyNoInteractions(utxoSupplier);
        verify(service, never()).registryLookup();
    }

    /** The unfracking validator requires an empty mint, wherever in the transaction it is authored. */
    @Test
    void unfrackingCannotShareATransactionWithACoreMint() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        ProgrammableTokenTx unfrack = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .unfrack(POLICY, BigIntPlutusData.of(0));
        Tx mint = new Tx()
                .from(holderAddress().toBech32())
                .mintAssets(new ScriptPubkey("11".repeat(28)), new Asset("0x01", BigInteger.ONE),
                        holderAddress().toBech32());

        assertThatThrownBy(() -> prepare(service, utxoSupplier, unfrack, mint))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("empty mint");
        verifyNoInteractions(utxoSupplier);
        verify(service, never()).registryLookup();
    }

    /**
     * The third-party validator reads only the acted policy's mint and pins only the paired
     * smart-wallet outputs, so seizing policy A while minting an unrelated native token to an
     * ordinary address is a valid transaction and must reach the registry.
     */
    @Test
    void thirdPartyTransferWithAnUnrelatedNativeMintRemainsSupported() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        Tx tx = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .thirdPartyTransfer(holderAddress().toBech32(), ownerAddress().toBech32(),
                        Amount.asset(POLICY, "Tok", BigInteger.ONE), BigIntPlutusData.of(0))
                .mintAssets(new ScriptPubkey("11".repeat(28)), new Asset("0x01", BigInteger.ONE),
                        ownerAddress().toBech32());

        // Composition passes; the build then reaches the holder's wallet for input selection.
        assertThatThrownBy(() -> prepare(service, utxoSupplier, tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageNotContaining("cannot validate")
                .hasMessageNotContaining("exactly one policy");
        verify(service).registryLookup();
        verify(utxoSupplier).getAll(
                SmartWalletAddress.ofPaymentCredential(deployment(), holderAddress()).toBech32());
    }

    /** Owner operations are not policy-bound: several policies and a native mint compose in one Tx. */
    @Test
    void ownerTransfersOfSeveralPoliciesWithACoreMintRemainSupported() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        Tx tx = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .transfer(holderAddress().toBech32(), Amount.asset(POLICY, "Tok", BigInteger.ONE),
                        BigIntPlutusData.of(0))
                .transfer(holderAddress().toBech32(), Amount.asset(OTHER_POLICY, "Tok", BigInteger.ONE),
                        BigIntPlutusData.of(0))
                .mintAssets(new ScriptPubkey("11".repeat(28)), new Asset("0x01", BigInteger.ONE),
                        ownerAddress().toBech32());

        // Composition passes and the build goes on to the registry; whatever fails afterwards is
        // not a composition rule.
        assertThatThrownBy(() -> prepare(service, utxoSupplier, tx))
                .satisfies(e -> assertThat(String.valueOf(e.getMessage()))
                        .doesNotContain("exactly one policy")
                        .doesNotContain("single Tx")
                        .doesNotContain("cannot validate"));
        verify(service).registryLookup();
    }

    @Test
    void burnsOfOnePolicyMustAgreeOnTheIssuanceRedeemer() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        Cip113ProtocolService service = serviceWith(node(POLICY, "ffff", LOGIC_HASH));
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(ownerAddress().toBech32())
                .burn(POLICY, List.of(new Asset("0x01", BigInteger.ONE)),
                        BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(1)))
                .burn(POLICY, List.of(new Asset("0x02", BigInteger.ONE)),
                        BurnAuthorization.of(BigIntPlutusData.of(0), ConstrPlutusData.of(7)));

        assertThatThrownBy(() -> prepare(service, utxoSupplier, tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("different issuance redeemers");
        verifyNoInteractions(utxoSupplier);
    }

    // ------------------------------------------------------------------ fixtures

    /** Prepares one ledger transaction composed of {@code fragments}, as QuickTxBuilder would. */
    private static void prepare(Cip113ProtocolService service, UtxoSupplier utxoSupplier,
                                AbstractTx<?>... fragments) {
        ExtensionBuildContext context = new ExtensionBuildContext(fragments,
                utxoSupplier, mock(ProtocolParamsSupplier.class));
        new Cip113BuildExtension(service, EnumSet.allOf(ProgrammableTokenCapability.class))
                .prepare(context);
    }

    private static Cip113TransactionMaterializer materializer(RegistryNode onChain,
                                                              UtxoSupplier utxoSupplier) {
        return new Cip113TransactionMaterializer(serviceWith(onChain), utxoSupplier, registryOf(onChain))
                .from(ownerAddress().toBech32());
    }

    private static Address ownerAddress() {
        return AddressProvider.getEntAddress(
                Credential.fromKey("22222222222222222222222222222222222222222222222222222222"),
                Networks.testnet());
    }

    private static Address holderAddress() {
        return AddressProvider.getEntAddress(
                Credential.fromKey("33333333333333333333333333333333333333333333333333333333"),
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

    private static Cip113Deployment deployment() {
        return Cip113Deployment.builder()
                .network(Networks.testnet())
                .programmableLogicBaseHash(BASE_HASH)
                .transferScriptHash("44".repeat(28))
                .thirdPartyScriptHash("55".repeat(28))
                .unfrackingScriptHash("66".repeat(28))
                .registryNodeCs(REGISTRY_NODE_CS)
                .maxInlineDatumBytes(32)
                .build();
    }

    private static RegistryLookup registryOf(RegistryNode onChain) {
        Utxo nodeUtxo = Utxo.builder()
                .txHash("11".repeat(32)).outputIndex(0)
                .address("addr_test1registry")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L))))
                .build();
        RegistryLookup registry = mock(RegistryLookup.class);
        RegistryLookup.RegistryNodeUtxo entry = new RegistryLookup.RegistryNodeUtxo(nodeUtxo, onChain);
        when(registry.byPolicy(onChain.getKey())).thenReturn(Optional.of(entry));
        when(registry.all()).thenReturn(List.of(entry));
        return registry;
    }

    /** A service resolved far enough that the guards under test are reachable. */
    private static Cip113ProtocolService serviceWith(RegistryNode onChain) {
        Cip113Deployment deployment = deployment();
        // Built before any stubbing: registryOf stubs its own mock, and Mockito rejects stubbing
        // one mock inside another's thenReturn(...) argument.
        RegistryLookup registry = registryOf(onChain);
        DeploymentScripts scripts = new DeploymentScripts(
                (com.bloxbean.cardano.client.api.ScriptSupplier) null, deployment);
        Utxo coordination = Utxo.builder()
                .txHash("22".repeat(32)).outputIndex(0)
                .address("addr_test1coordination")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L))))
                .build();

        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(deployment);
        when(service.registryLookup()).thenReturn(registry);
        when(service.scripts()).thenReturn(scripts);
        when(service.coordinationUtxo()).thenReturn(coordination);
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
