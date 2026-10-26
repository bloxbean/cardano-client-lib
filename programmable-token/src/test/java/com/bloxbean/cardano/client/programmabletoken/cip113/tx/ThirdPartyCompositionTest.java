package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCredential;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistration;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.ThirdPartyBurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.Cip113Redeemers;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.ThirdPartyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every row of the third-party composition table (plan 2026-10-26, task 1): what the chain
 * accepts is supported — any mix of third-party burns and seizures of ONE policy, from any
 * holders, alongside mints and registrations — and everything else is refused by name.
 *
 * <p>The pairing invariant under test: the continuing outputs are the last {@code n}
 * base-script outputs, contiguous, one per acted-on input in the ledger's global input order
 * across all holders, and {@code outputs_start_idx} points at the first of them.</p>
 */
class ThirdPartyCompositionTest {

    private static final PlutusData R = BigIntPlutusData.of(7);

    private static ThirdPartyBurnAuthorization auth() {
        return ThirdPartyBurnAuthorization.of(R, R);
    }

    private static ProgrammableTokenTx admin() {
        return new ProgrammableTokenTx().from(ADMIN.toBech32());
    }

    // ---------------------------------------------------------------- allowed

    /** Several pinned UTxOs, two holders, tx hashes interleaving across holders in ledger order. */
    @Test
    void burnsFromSeveralUtxosAndHoldersPairInGlobalLedgerOrder() {
        Utxo a1 = held("30", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        Utxo b1 = held("20", 0, HOLDER_B, Amount.asset(UNIT_A, 4));
        Utxo a2 = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 6));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(a1, b1, a2);

        Transaction txn = fixture.build(admin()
                .thirdPartyBurn(ref(a1), POLICY_A, List.of(asset(TOK, 9)), auth())
                .thirdPartyBurn(ref(b1), POLICY_A, List.of(asset(TOK, 1)), auth())
                .thirdPartyBurn(ref(a2), POLICY_A, List.of(asset(TOK, 2)), auth()));

        assertContinuations(txn, List.of(a2, b1, a1), Map.of(
                ref(a2), BigInteger.valueOf(4), ref(b1), BigInteger.valueOf(3), ref(a1), BigInteger.ZERO));
        assertThat(minted(txn, UNIT_A)).as("one negative mint for the policy's total")
                .isEqualTo(BigInteger.valueOf(-12));
        assertCustodyHandedToThirdParty(txn, fixture);
    }

    /** A seizure and burns of one policy; the pinned UTxO is never picked by seizure selection. */
    @Test
    void aSeizureAndBurnsShareATransactionAndPinnedUtxosAreNotSelected() {
        Utxo aLoose = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 5));
        Utxo aPinned = held("30", 1, HOLDER_A, Amount.asset(UNIT_A, 9));
        Utxo bPinned = held("20", 0, HOLDER_B, Amount.asset(UNIT_A, 4));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(aPinned, aLoose, bPinned);

        Transaction txn = fixture.build(admin()
                .thirdPartyTransfer(HOLDER_A.toBech32(), RECEIVER.toBech32(), Amount.asset(UNIT_A, 3), R)
                .thirdPartyBurn(ref(aPinned), POLICY_A, List.of(asset(TOK, 9)), auth())
                .thirdPartyBurn(ref(bPinned), POLICY_A, List.of(asset(TOK, 1)), auth()));

        assertContinuations(txn, List.of(aLoose, bPinned, aPinned), Map.of(
                ref(aLoose), BigInteger.TWO, ref(bPinned), BigInteger.valueOf(3), ref(aPinned), BigInteger.ZERO));
        assertThat(outputsAt(txn, wallet(RECEIVER))).singleElement()
                .satisfies(o -> assertThat(quantity(o, UNIT_A)).isEqualTo(BigInteger.valueOf(3)));
        assertThat(minted(txn, UNIT_A)).isEqualTo(BigInteger.valueOf(-10));
    }

    /** Seizures from different holders of one policy (closes #15). */
    @Test
    void seizuresFromSeveralHoldersShareATransaction() {
        Utxo a1 = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 2));
        Utxo a2 = held("30", 0, HOLDER_A, Amount.asset(UNIT_A, 2));
        Utxo b1 = held("20", 0, HOLDER_B, Amount.asset(UNIT_A, 3));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(a1, a2, b1);

        Transaction txn = fixture.build(admin()
                .thirdPartyTransfer(HOLDER_A.toBech32(), RECEIVER.toBech32(), Amount.asset(UNIT_A, 4), R)
                .thirdPartyTransfer(HOLDER_B.toBech32(), RECEIVER.toBech32(), Amount.asset(UNIT_A, 2), R));

        assertContinuations(txn, List.of(a1, b1, a2), Map.of(
                ref(a1), BigInteger.ZERO, ref(b1), BigInteger.ONE, ref(a2), BigInteger.ZERO));
        assertThat(outputsAt(txn, wallet(RECEIVER)).stream().map(o -> quantity(o, UNIT_A)).toList())
                .containsExactlyInAnyOrder(BigInteger.valueOf(4), BigInteger.TWO);
        assertThat(txn.getBody().getMint()).as("a seizure mints nothing").isNullOrEmpty();
    }

    /** Seize A→B while burning from B: the destination at B's wallet precedes the continuations. */
    @Test
    void aSeizureIntoAWalletThatIsAlsoBurnedFromIsUnambiguous() {
        Utxo aLoose = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 5));
        Utxo bPinned = held("20", 0, HOLDER_B, Amount.asset(UNIT_A, 4));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(aLoose, bPinned);

        Transaction txn = fixture.build(admin()
                .thirdPartyTransfer(HOLDER_A.toBech32(), HOLDER_B.toBech32(), Amount.asset(UNIT_A, 3), R)
                .thirdPartyBurn(ref(bPinned), POLICY_A, List.of(asset(TOK, 4)), auth()));

        int start = assertContinuations(txn, List.of(aLoose, bPinned), Map.of(
                ref(aLoose), BigInteger.TWO, ref(bPinned), BigInteger.ZERO));
        List<TransactionOutput> outputs = txn.getBody().getOutputs();
        int destination = -1;
        for (int i = 0; i < start; i++)
            if (outputs.get(i).getAddress().equals(wallet(HOLDER_B))) destination = i;
        assertThat(destination).as("the seized tokens land at B's wallet before the continuations")
                .isBetween(0, start - 1);
        assertThat(quantity(outputs.get(destination), UNIT_A)).isEqualTo(BigInteger.valueOf(3));
    }

    /** A mint of the acted policy rides along; its output sits before outputs_start_idx. */
    @Test
    void aMintOfTheActedPolicyIsPlacedBeforeTheContinuations() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);

        Transaction txn = fixture.build(admin()
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(asset(TOK2, 10)), R, null)
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 4)), auth()));

        int start = assertContinuations(txn, List.of(pinned), Map.of(ref(pinned), BigInteger.valueOf(5)));
        assertThat(indexOfOutputHolding(txn, UNIT_A2)).as("the minted output precedes the continuations")
                .isLessThan(start);
        assertThat(minted(txn, UNIT_A)).isEqualTo(BigInteger.valueOf(-4));
        assertThat(minted(txn, UNIT_A2)).isEqualTo(BigInteger.TEN);
        assertCustodyHandedToThirdParty(txn, fixture);
    }

    /** A mint of another registered policy rides along under its own node and minting logic. */
    @Test
    void aMintOfAnotherPolicyRidesAlong() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        PlutusData mintRedeemer = BigIntPlutusData.of(3);

        Transaction txn = fixture.build(admin()
                .mint(POLICY_B, RECEIVER.toBech32(), List.of(asset(TOK, 10)), mintRedeemer, null)
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth()));

        int start = assertContinuations(txn, List.of(pinned), Map.of(ref(pinned), BigInteger.ZERO));
        assertThat(indexOfOutputHolding(txn, UNIT_B)).isLessThan(start);
        Utxo nodeA = fixture.nodeOf(POLICY_A).getUtxo();
        Utxo nodeB = fixture.nodeOf(POLICY_B).getUtxo();
        int nodeAIdx = LedgerOrdering.indexOfReferenceInput(txn, nodeA.getTxHash(), nodeA.getOutputIndex());
        int nodeBIdx = LedgerOrdering.indexOfReferenceInput(txn, nodeB.getTxHash(), nodeB.getOutputIndex());
        assertThat(thirdPartyIndices(txn)[0]).isEqualTo(nodeAIdx);
        assertThat(rewardRedeemer(txn, DEPLOYMENT.issuanceLogicCredential())).isEqualTo(
                Cip113Redeemers.issuanceLogic(Map.of(POLICY_A, Cip113Redeemers.mintRefInput(nodeAIdx),
                        POLICY_B, Cip113Redeemers.mintRefInput(nodeBIdx))));
        assertThat(rewardRedeemer(txn, Credential.fromScript(hash(LOGIC_M)))).isEqualTo(mintRedeemer);
        assertThat(minted(txn, UNIT_B)).isEqualTo(BigInteger.TEN);
        assertThat(minted(txn, UNIT_A)).isEqualTo(BigInteger.valueOf(-9));
    }

    /** A registration whose covering node is not the acted node rides along. */
    @Test
    void aRegistrationCoveredByAnotherNodeRidesAlong() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        PlutusScript logic = mintingLogicCoveredBy(fixture, false);
        fixture.withScripts(logic);

        Transaction txn = fixture.build(admin()
                .register("new", registration(logic), R)
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth()));

        assertContinuations(txn, List.of(pinned), Map.of(ref(pinned), BigInteger.ZERO));
        assertThat(minted(txn, hash(REGISTRY) + policyFor(logic))).as("the new registry node's NFT")
                .isEqualTo(BigInteger.ONE);
        assertCustodyHandedToThirdParty(txn, fixture);
    }

    /** Mint and burn of one asset name net into one mint entry. */
    @Test
    void aMintAndABurnOfOneAssetNameAreNetted() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);

        Transaction txn = fixture.build(admin()
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(asset(TOK, 10)), R, null)
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 4)), auth()));

        assertThat(minted(txn, UNIT_A)).isEqualTo(BigInteger.valueOf(6));
        long entries = txn.getBody().getMint().stream().filter(ma -> ma.getPolicyId().equals(POLICY_A))
                .mapToLong(ma -> ma.getAssets().size()).sum();
        assertThat(entries).isEqualTo(1);
        assertContinuations(txn, List.of(pinned), Map.of(ref(pinned), BigInteger.valueOf(5)));
    }

    /** A caller output at a base-script address authored in the same Tx lands before them: fine. */
    @Test
    void aCallerOutputAtASmartWalletBeforeTheContinuationsIsAllowed() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        ProgrammableTokenTx tx = admin()
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth());
        tx.payToAddress(wallet(RECEIVER), Amount.ada(2));

        Transaction txn = fixture.build(tx);

        assertContinuations(txn, List.of(pinned), Map.of(ref(pinned), BigInteger.ZERO));
    }

    // ---------------------------------------------------------------- refused

    @Test
    void differentThirdPartyRedeemersAreRefused() {
        Utxo a = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        Utxo b = held("20", 0, HOLDER_B, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(a, b);

        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyBurn(ref(a), POLICY_A, List.of(asset(TOK, 1)), ThirdPartyBurnAuthorization.of(R, R))
                .thirdPartyBurn(ref(b), POLICY_A, List.of(asset(TOK, 1)),
                        ThirdPartyBurnAuthorization.of(BigIntPlutusData.of(8), R))))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("different third-party redeemers");

        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyTransfer(HOLDER_A.toBech32(), RECEIVER.toBech32(), Amount.asset(UNIT_A, 1),
                        BigIntPlutusData.of(8))
                .thirdPartyBurn(ref(b), POLICY_A, List.of(asset(TOK, 1)), auth())))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("different third-party redeemers");
    }

    @Test
    void differentIssuanceRedeemersAreRefused() {
        Utxo a = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        Utxo b = held("20", 0, HOLDER_B, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().withThirdPartyLogicA(LOGIC_B).holding(a, b);

        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyBurn(ref(a), POLICY_A, List.of(asset(TOK, 1)), ThirdPartyBurnAuthorization.of(R, R))
                .thirdPartyBurn(ref(b), POLICY_A, List.of(asset(TOK, 1)),
                        ThirdPartyBurnAuthorization.of(R, BigIntPlutusData.of(8)))))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("different issuance redeemers");

        assertThatThrownBy(() -> fixture.build(admin()
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(asset(TOK2, 1)), BigIntPlutusData.of(8), null)
                .thirdPartyBurn(ref(b), POLICY_A, List.of(asset(TOK, 1)), auth())))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("different issuance redeemers");
    }

    @Test
    void aRegistrationCoveredByTheActedNodeIsRefused() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        PlutusScript logic = mintingLogicCoveredBy(fixture, true);
        fixture.withScripts(logic);

        assertThatThrownBy(() -> fixture.build(admin()
                .register("new", registration(logic), R)
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth())))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("covering")
                .hasMessageContaining(POLICY_A);
    }

    @Test
    void aMintAndABurnThatNetToZeroAreRefused() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);

        assertThatThrownBy(() -> fixture.build(admin()
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(asset(TOK, 4)), R, null)
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 4)), auth())))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("net");
    }

    @Test
    void theSameUtxoPinnedTwiceIsRefused() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);

        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 1)), auth())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 2)), auth())))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(ref(pinned))
                .hasMessageContaining("twice");
    }

    @Test
    void thirdPartyActionsOnTwoPoliciesAreRefused() {
        Utxo a = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        Utxo b = held("20", 0, HOLDER_B, Amount.asset(UNIT_B, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(a, b);

        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyBurn(ref(a), POLICY_A, List.of(asset(TOK, 1)), auth())
                .thirdPartyTransfer(HOLDER_B.toBech32(), RECEIVER.toBech32(), Amount.asset(UNIT_B, 1), R)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("exactly one policy")
                .hasMessageContaining(POLICY_A)
                .hasMessageContaining(POLICY_B);
    }

    @Test
    void ownerOperationsCannotJoinAThirdPartyAction() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        Utxo own = held("20", 0, ADMIN, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned, own);

        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 1)), auth())
                .transfer(RECEIVER.toBech32(), Amount.asset(UNIT_A, 1), R)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("cannot be mixed with owner operations");
        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 1)), auth())
                .burn(POLICY_A, List.of(asset(TOK, 1)), BurnAuthorization.of(R, R))))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("cannot be mixed with owner operations");
        assertThatThrownBy(() -> fixture.build(admin()
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 1)), auth())
                .unfrack(POLICY_A, R)))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("its own transaction");
    }

    /** A later fragment's output at a base-script address lands after the continuations. */
    @Test
    void aCallerOutputAtASmartWalletAfterTheContinuationsIsRefused() {
        Utxo pinned = held("10", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        Utxo receiverFunds = utxo("ef", 0, RECEIVER.toBech32(), Amount.ada(100));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned, receiverFunds);
        ProgrammableTokenTx tx = admin()
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth());
        Tx later = new Tx().from(RECEIVER.toBech32()).payToAddress(wallet(HOLDER_B), Amount.ada(2));

        assertThatThrownBy(() -> fixture.build(tx, later))
                .satisfies(e -> assertThat(causeChain(e)).contains("after or among"));
    }

    /** Among them: a foreign base-script output interleaved breaks the run. */
    @Test
    void aForeignBaseScriptOutputAmongTheContinuationsIsRefused() {
        TransactionOutput first = output(wallet(HOLDER_A), UNIT_A, 1);
        TransactionOutput foreign = output(wallet(RECEIVER), UNIT_A, 1);
        TransactionOutput second = output(wallet(HOLDER_B), UNIT_A, 2);
        List<TransactionOutput> outputs = List.of(first, foreign, second,
                output(ADMIN.toBech32(), null, 0));

        assertThatThrownBy(() -> Cip113TransactionMaterializer.continuationRunStart(outputs, hash(BASE),
                List.of(Cip113TransactionMaterializer.Continuation.of(first),
                        Cip113TransactionMaterializer.Continuation.of(second))))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("after or among");

        assertThat(Cip113TransactionMaterializer.continuationRunStart(
                List.of(foreign, first, second, output(ADMIN.toBech32(), null, 0)), hash(BASE),
                List.of(Cip113TransactionMaterializer.Continuation.of(first),
                        Cip113TransactionMaterializer.Continuation.of(second))))
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The continuing outputs are the last {@code n} base-script outputs, start at
     * outputs_start_idx, follow the ledger's input order and keep the expected token quantity.
     */
    private static int assertContinuations(Transaction txn, List<Utxo> ledgerOrder,
                                           Map<String, BigInteger> keptOfUnitA) {
        Set<String> refs = new LinkedHashSet<>();
        ledgerOrder.forEach(u -> refs.add(ref(u)));
        assertThat(sortedInputRefs(txn, refs)).as("the acted-on inputs, in ledger order")
                .containsExactlyElementsOf(new ArrayList<>(refs));
        for (Utxo u : ledgerOrder)
            assertThat(spendRedeemer(txn, u)).isEqualTo(Cip113Redeemers.baseSpend(
                    LedgerOrdering.indexOfReferenceInput(txn, PARAMS.getTxHash(), PARAMS.getOutputIndex()),
                    LedgerOrdering.indexOfWithdrawal(txn, DEPLOYMENT.programmableLogicGlobalCredential())));
        assertThat(rewardRedeemer(txn, DEPLOYMENT.programmableLogicGlobalCredential()))
                .isEqualTo(Cip113Redeemers.thirdPartyAct());

        int start = thirdPartyIndices(txn)[1];
        List<TransactionOutput> outputs = txn.getBody().getOutputs();
        int lastBase = -1;
        for (int i = 0; i < outputs.size(); i++) if (isBaseScript(outputs.get(i))) lastBase = i;
        assertThat(lastBase - start + 1).as("the continuations are the last n base-script outputs")
                .isEqualTo(ledgerOrder.size());
        for (int k = 0; k < ledgerOrder.size(); k++) {
            Utxo input = ledgerOrder.get(k);
            TransactionOutput continuing = outputs.get(start + k);
            assertThat(continuing.getAddress()).as("continuation %d address", k).isEqualTo(input.getAddress());
            assertThat(quantity(continuing, UNIT_A)).as("continuation %d keeps", k)
                    .isEqualTo(keptOfUnitA.get(ref(input)));
            assertThat(continuing.getValue().getCoin()).as("continuation %d lovelace ratchet", k)
                    .isGreaterThanOrEqualTo(input.getAmount().get(0).getQuantity());
        }
        return start;
    }

    private static void assertCustodyHandedToThirdParty(Transaction txn, ThirdPartyFixture fixture) {
        Utxo nodeA = fixture.nodeOf(POLICY_A).getUtxo();
        int nodeIdx = LedgerOrdering.indexOfReferenceInput(txn, nodeA.getTxHash(), nodeA.getOutputIndex());
        assertThat(thirdPartyIndices(txn)[0]).isEqualTo(nodeIdx);
        PlutusData issuanceLogic = rewardRedeemer(txn, DEPLOYMENT.issuanceLogicCredential());
        assertThat(issuanceLogic.serializeToHex()).as("issuance_logic names node A at the same index")
                .contains(Cip113Redeemers.mintRefInput(nodeIdx).serializeToHex());
    }

    /** Every message in the cause chain, so a wrapped refusal is still found. */
    private static String causeChain(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause())
            messages.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
        return messages.toString();
    }

    private static List<TransactionOutput> outputsAt(Transaction txn, String address) {
        return txn.getBody().getOutputs().stream().filter(o -> o.getAddress().equals(address)).toList();
    }

    private static int indexOfOutputHolding(Transaction txn, String unit) {
        List<TransactionOutput> outputs = txn.getBody().getOutputs();
        for (int i = 0; i < outputs.size(); i++) if (quantity(outputs.get(i), unit).signum() > 0) return i;
        throw new AssertionError("no output holds " + unit);
    }

    private static TransactionOutput output(String address, String unit, long quantity) {
        List<MultiAsset> assets = unit == null ? List.of() : List.of(new MultiAsset(unit.substring(0, 56),
                List.of(new Asset("0x" + unit.substring(56), BigInteger.valueOf(quantity)))));
        return TransactionOutput.builder().address(address)
                .value(Value.builder().coin(BigInteger.valueOf(2_000_000L)).multiAssets(assets).build()).build();
    }

    private static ProgrammableTokenRegistration registration(PlutusScript mintingLogic) {
        ProgrammableTokenCredential logic = ProgrammableTokenCredential.from(
                Credential.fromScript(hash(mintingLogic)));
        return ProgrammableTokenRegistration.builder()
                .mintingLogicScript(logic).transferLogicScript(logic)
                .thirdPartyTransferLogicScript(logic)
                .unfrackingLogicScript(ProgrammableTokenCredential.from(Credential.fromKey(new byte[0])))
                .build();
    }

    /** A stand-in minting logic whose derived policy is (or is not) covered by policy A's node. */
    private static PlutusScript mintingLogicCoveredBy(ThirdPartyFixture fixture, boolean byActedNode) {
        String key = fixture.nodeOf(POLICY_A).getDatum().getKey();
        String next = fixture.nodeOf(POLICY_A).getDatum().getNext();
        for (int b = 0x10; b < 0x100; b++) {
            PlutusScript candidate = script(String.format("%02x", b));
            String policy = policyFor(candidate);
            if (policy.equals(POLICY_A) || policy.equals(POLICY_B)) continue;
            boolean covered = PolicyOrdering.covers(key, next, policy);
            if (covered == byActedNode) return candidate;
        }
        throw new AssertionError("no stand-in script found");
    }
}
