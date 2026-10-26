package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.ThirdPartyBurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.Cip113Redeemers;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableThirdPartyBurnIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTokenAsset;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.ThirdPartyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@code ProgrammableTokenTx.thirdPartyBurn}: an authorised party burns tokens out of one named
 * holder UTxO. On chain that is the seizure shape (base spend → dispatcher {@code ThirdPartyAct}
 * → {@code third_party} → the token's third-party logic) plus the burn shape (negative issuance
 * mint → minting logic + {@code issuance_logic}), with the custody check handed to
 * {@code third_party} because both name the same registry node.
 */
class ThirdPartyBurnTest {

    private static final PlutusData TP = BigIntPlutusData.of(7);
    private static final PlutusData ISS = BigIntPlutusData.of(7);

    private static ThirdPartyBurnAuthorization auth() {
        return ThirdPartyBurnAuthorization.of(TP, ISS);
    }

    // ---------------------------------------------------------------- shapes

    @Test
    void aFullBurnPinsEveryRedeemerAndHandsCustodyToThirdParty() {
        Utxo pinned = held("1a", 2, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth());

        Transaction txn = fixture.build(tx);

        int paramsIdx = LedgerOrdering.indexOfReferenceInput(txn, PARAMS.getTxHash(), PARAMS.getOutputIndex());
        Utxo nodeA = fixture.nodeOf(POLICY_A).getUtxo();
        int nodeIdx = LedgerOrdering.indexOfReferenceInput(txn, nodeA.getTxHash(), nodeA.getOutputIndex());

        // The spend and the dispatch.
        assertThat(spendRedeemer(txn, pinned)).isEqualTo(Cip113Redeemers.baseSpend(paramsIdx,
                LedgerOrdering.indexOfWithdrawal(txn, DEPLOYMENT.programmableLogicGlobalCredential())));
        assertThat(rewardRedeemer(txn, DEPLOYMENT.programmableLogicGlobalCredential()))
                .isEqualTo(Cip113Redeemers.thirdPartyAct());
        assertThat(withdrawnCredentials(txn)).as("a third-party burn never loads the transfer delegate")
                .doesNotContain(DEPLOYMENT.getTransferScriptHash());

        // The custody handover: third_party.registry_node_idx == issuance_logic's RefInput idx.
        int[] tp = thirdPartyIndices(txn);
        assertThat(tp[0]).isEqualTo(nodeIdx);
        assertThat(rewardRedeemer(txn, DEPLOYMENT.issuanceLogicCredential()))
                .isEqualTo(Cip113Redeemers.issuanceLogic(Map.of(POLICY_A, Cip113Redeemers.mintRefInput(tp[0]))));
        TransactionInput named = LedgerOrdering.sortedReferenceInputs(txn).get(tp[0]);
        assertThat(named.getTransactionId() + "#" + named.getIndex()).isEqualTo(ref(nodeA));

        // The negative mint and its redeemer.
        assertThat(minted(txn, UNIT_A)).isEqualTo(BigInteger.valueOf(-9));
        assertThat(mintRedeemer(txn, POLICY_A)).isEqualTo(Cip113Redeemers.issuanceMint(paramsIdx));

        // Both of the token's own scripts run: here one shared credential, so one withdrawal.
        assertThat(rewardRedeemer(txn, Credential.fromScript(hash(LOGIC_A)))).isEqualTo(TP);

        // The continuing output: same address, all lovelace, no token left.
        TransactionOutput continuing = txn.getBody().getOutputs().get(tp[1]);
        assertThat(continuing.getAddress()).isEqualTo(pinned.getAddress());
        assertThat(quantity(continuing, UNIT_A)).isZero();
        assertThat(continuing.getValue().getCoin()).isGreaterThanOrEqualTo(BigInteger.valueOf(2_000_000L));
        assertThat(txn.getBody().getOutputs().stream().filter(ThirdPartyFixture::isBaseScript).count())
                .as("one base-script output: the continuation").isEqualTo(1);
    }

    @Test
    void aPartialBurnKeepsTheRemainderInTheContinuingOutput() {
        Utxo pinned = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        Transaction txn = fixture.build(new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 4)), auth()));

        TransactionOutput continuing = txn.getBody().getOutputs().get(thirdPartyIndices(txn)[1]);
        assertThat(continuing.getAddress()).isEqualTo(pinned.getAddress());
        assertThat(quantity(continuing, UNIT_A)).isEqualTo(BigInteger.valueOf(5));
        assertThat(minted(txn, UNIT_A)).isEqualTo(BigInteger.valueOf(-4));
    }

    @Test
    void theInlineDatumAndASiblingAssetNameArePreserved() {
        PlutusData datum = ConstrPlutusData.of(0, BigIntPlutusData.of(42));
        Utxo pinned = held("1a", 1, HOLDER_A, Amount.asset(UNIT_A, 9), Amount.asset(UNIT_A2, 3));
        pinned.setInlineDatum(datum.serializeToHex());
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        Transaction txn = fixture.build(new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth()));

        TransactionOutput continuing = txn.getBody().getOutputs().get(thirdPartyIndices(txn)[1]);
        assertThat(continuing.getInlineDatum()).isEqualTo(datum);
        assertThat(quantity(continuing, UNIT_A)).isZero();
        assertThat(quantity(continuing, UNIT_A2)).as("the sibling asset name is untouched")
                .isEqualTo(BigInteger.valueOf(3));
        assertThat(minted(txn, UNIT_A2)).as("nothing of the sibling is burned").isNull();
    }

    /**
     * third_party.ak pins every non-acted policy byte-identical across the pair and reads no other
     * registry node, so another registered policy in the burnt UTxO just rides along.
     */
    @Test
    void anotherRegisteredPolicyInTheHolderUtxoIsPassedThrough() {
        Utxo pinned = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9), Amount.asset(UNIT_B, 5));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        Transaction txn = fixture.build(new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth()));

        TransactionOutput continuing = txn.getBody().getOutputs().get(thirdPartyIndices(txn)[1]);
        assertThat(quantity(continuing, UNIT_A)).isZero();
        assertThat(quantity(continuing, UNIT_B)).isEqualTo(BigInteger.valueOf(5));
        assertThat(minted(txn, UNIT_B)).isNull();
    }

    @Test
    void separateMintingAndThirdPartyCredentialsEachGetTheirOwnWithdrawalAndRedeemer() {
        Utxo pinned = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().withThirdPartyLogicA(LOGIC_B).holding(pinned);
        PlutusData thirdParty = BigIntPlutusData.of(1);
        PlutusData issuance = BigIntPlutusData.of(2);
        Transaction txn = fixture.build(new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)),
                        ThirdPartyBurnAuthorization.of(thirdParty, issuance)));

        assertThat(rewardRedeemer(txn, Credential.fromScript(hash(LOGIC_B)))).isEqualTo(thirdParty);
        assertThat(rewardRedeemer(txn, Credential.fromScript(hash(LOGIC_A)))).isEqualTo(issuance);
    }

    @Test
    void aSharedCredentialIsOneWithdrawal() {
        Utxo pinned = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        Transaction txn = fixture.build(new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)), auth()));

        long logicWithdrawals = txn.getBody().getWithdrawals().stream()
                .filter(w -> w.getRewardAddress().equals(com.bloxbean.cardano.client.address.AddressProvider
                        .getRewardAddress(Credential.fromScript(hash(LOGIC_A)), DEPLOYMENT.getNetwork()).toBech32()))
                .count();
        assertThat(logicWithdrawals).isEqualTo(1);
    }

    @Test
    void aSharedCredentialWithDifferentRedeemersIsRefused() {
        Utxo pinned = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(pinned);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)),
                        ThirdPartyBurnAuthorization.of(BigIntPlutusData.of(1), BigIntPlutusData.of(2)));

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(hash(LOGIC_A))
                .hasMessageContaining("different redeemers");
    }

    // ----------------------------------------------------------------- guards

    @Test
    void anUnknownHolderUtxoIsRefusedByName() {
        String unknown = "4f".repeat(32) + "#0";
        ThirdPartyFixture fixture = new ThirdPartyFixture();
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(unknown, POLICY_A, List.of(asset(TOK, 1)), auth());

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(unknown)
                .hasMessageContaining("not found");
    }

    @Test
    void anAlreadySpentHolderUtxoIsRefusedByName() {
        Utxo gone = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().spentAlready(gone);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(gone), POLICY_A, List.of(asset(TOK, 1)), auth());

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(ref(gone))
                .hasMessageContaining("spent");
    }

    @Test
    void aUtxoOutsideThisDeploymentsSmartWalletsIsRefused() {
        Utxo plain = utxo("1a", 0, HOLDER_A.toBech32(),
                Amount.lovelace(BigInteger.valueOf(2_000_000L)), Amount.asset(UNIT_A, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(plain);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(plain), POLICY_A, List.of(asset(TOK, 1)), auth());

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(ref(plain))
                .hasMessageContaining("smart wallet");
    }

    @Test
    void aUtxoNotHoldingThePolicyIsRefused() {
        Utxo other = held("1a", 0, HOLDER_A, Amount.asset(UNIT_B, 9));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(other);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(other), POLICY_A, List.of(asset(TOK, 1)), auth());

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(ref(other))
                .hasMessageContaining("does not hold policy");
    }

    @Test
    void aUtxoThatDoesNotCoverTheBurnIsRefused() {
        Utxo small = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 3));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(small);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(small), POLICY_A, List.of(asset(TOK, 4)), auth());

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(ref(small))
                .hasMessageContaining("does not cover");
    }

    @Test
    void aUtxoWithADatumHashIsRefused() {
        Utxo hashed = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        hashed.setDataHash("ab".repeat(32));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(hashed);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(hashed), POLICY_A, List.of(asset(TOK, 1)), auth());

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(ref(hashed))
                .hasMessageContaining("datum hash");
    }

    @Test
    void aUtxoCarryingAReferenceScriptIsRefused() {
        Utxo withScript = held("1a", 0, HOLDER_A, Amount.asset(UNIT_A, 9));
        withScript.setReferenceScriptHash(hash(LOGIC_B));
        ThirdPartyFixture fixture = new ThirdPartyFixture().holding(withScript);
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(withScript), POLICY_A, List.of(asset(TOK, 1)), auth());

        assertThatThrownBy(() -> fixture.build(tx))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining(ref(withScript))
                .hasMessageContaining("reference script");
    }

    @Test
    void aMalformedHolderUtxoRefIsRefusedWhenAuthored() {
        assertThatThrownBy(() -> new ProgrammableTokenTx().thirdPartyBurn("abc#0", POLICY_A,
                List.of(asset(TOK, 1)), auth()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<txHash>#<outputIndex>");
    }

    // ------------------------------------------------------------------- YAML

    @Test
    void theIntentRoundTripsThroughYaml() {
        String outref = "1a".repeat(32) + "#3";
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(outref, POLICY_A, List.of(asset(TOK, 5)),
                        ThirdPartyBurnAuthorization.of(BigIntPlutusData.of(1),
                                ConstrPlutusData.of(0, BigIntPlutusData.of(2))));
        ProgrammableTokenExtension extension = ProgrammableTokenExtension.builder()
                .protocol(new Cip113Protocol(mock(Cip113ProtocolService.class, invocation -> {
                    if (invocation.getMethod().getName().equals("deployment")) return DEPLOYMENT;
                    return null;
                })))
                .deployment(Map.of()).build();
        TxPlanCodec codec = TxPlanCodec.builder()
                .withExtension(ProgrammableTokenExtension.DEFAULT_NAMESPACE, extension).build();

        String yaml = codec.toYaml(extension.configure(TxPlan.from(tx)));
        TxPlan restored = codec.fromYaml(yaml);

        assertThat(yaml).contains("type: pt:third_party_burn", "holder_utxo_ref: " + outref,
                "third_party_redeemer:", "issuance_redeemer:");
        assertThat(restored.getTxs().get(0).getIntentions()).singleElement()
                .isInstanceOfSatisfying(ProgrammableThirdPartyBurnIntent.class, intent -> {
                    assertThat(intent.getHolderUtxoRef()).isEqualTo(outref);
                    assertThat(intent.getPolicy().getPolicyId()).isEqualTo(POLICY_A);
                    assertThat(intent.getAssets()).containsExactly(
                            new ProgrammableTokenAsset(TOK, BigInteger.valueOf(5)));
                    assertThat(intent.getThirdPartyRedeemer().requireResolved())
                            .isEqualTo(BigIntPlutusData.of(1));
                    assertThat(intent.getIssuanceRedeemer().requireResolved())
                            .isEqualTo(ConstrPlutusData.of(0, BigIntPlutusData.of(2)));
                });
        assertThat(codec.toYaml(restored)).isEqualTo(yaml);
    }

    @Test
    void theIntentRefusesANamedPolicyRef() {
        ProgrammableThirdPartyBurnIntent intent = ProgrammableThirdPartyBurnIntent.builder()
                .holderUtxoRef("1a".repeat(32) + "#0")
                .policy(com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef.named("p"))
                .assets(List.of(new ProgrammableTokenAsset(TOK, BigInteger.ONE)))
                .thirdPartyRedeemer(com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue.of(TP))
                .issuanceRedeemer(com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue.of(ISS))
                .build();

        assertThatThrownBy(intent::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("literal policy_id");
    }
}
