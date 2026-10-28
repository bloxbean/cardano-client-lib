package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.impl.StaticTransactionEvaluator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * Voting redeemer indices must follow the ledger voter order (script before key, then hash),
 * not the order in which votes are added.
 */
@ExtendWith(MockitoExtension.class)
class VotingRedeemerOrderTest extends QuickTxBaseTest {
    private static final String GOV_ACTION_TX = "12745f09b138d4d0a11a560b4591ebb830cf12336347606d2edbbf1893d395c6";

    @Mock
    private UtxoSupplier utxoSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;
    private ProtocolParamsSupplier protocolParamsSupplier;

    private final String sender1 = new Account().baseAddress();

    // Two Plutus V3 scripts; lowScript has the lower script hash
    private PlutusV3Script lowScript;
    private PlutusV3Script highScript;
    private Voter lowScriptVoter;
    private Voter highScriptVoter;
    // Its hash sorts before both script hashes, so hash-only ordering would put it first
    private final Voter keyVoter = new Voter(VoterType.DREP_KEY_HASH, Credential.fromKey("00".repeat(28)));

    @BeforeEach
    @SneakyThrows
    void setup() {
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;

        lenient().when(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).thenReturn(List.of(
                Utxo.builder().address(sender1)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f").outputIndex(0)
                        .amount(List.of(Amount.ada(10))).build(),
                Utxo.builder().address(sender1)
                        .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f").outputIndex(0)
                        .amount(List.of(Amount.ada(50))).build()));

        PlutusV3Script s1 = PlutusV3Script.builder().type("PlutusScriptV3").cborHex("49480100002221200101").build();
        PlutusV3Script s2 = PlutusV3Script.builder().type("PlutusScriptV3").cborHex("46450101002499").build();
        boolean s1Low = HexUtil.encodeHexString(s1.getScriptHash()).compareTo(HexUtil.encodeHexString(s2.getScriptHash())) < 0;
        lowScript = s1Low ? s1 : s2;
        highScript = s1Low ? s2 : s1;
        lowScriptVoter = new Voter(VoterType.DREP_SCRIPT_HASH, Credential.fromScript(lowScript.getScriptHash()));
        highScriptVoter = new Voter(VoterType.DREP_SCRIPT_HASH, Credential.fromScript(highScript.getScriptHash()));
    }

    @Test
    void scriptVoters_redeemerIndexFollowsLedgerOrder() {
        // Added in an order that differs from ledger order: key, high script, low script
        Tx tx = new Tx()
                .createVote(keyVoter, govAction(0), Vote.YES, null, null)
                .createVote(highScriptVoter, govAction(0), Vote.YES, null, BigIntPlutusData.of(2))
                .createVote(lowScriptVoter, govAction(0), Vote.NO, null, BigIntPlutusData.of(1))
                .attachVotingValidator(highScript)
                .attachVotingValidator(lowScript);

        Transaction transaction = build(tx, 2);

        // Ledger order: low script (0), high script (1), key (2)
        assertThat(transaction.getBody().getVotingProcedures().getVoting()).hasSize(3);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(2);
        assertVotingRedeemer(transaction, 0, BigIntPlutusData.of(1));
        assertVotingRedeemer(transaction, 1, BigIntPlutusData.of(2));
    }

    @Test
    void sameScriptVoterOnTwoGovActions_identicalRedeemer_oneRedeemer() {
        Tx tx = new Tx()
                .createVote(lowScriptVoter, govAction(0), Vote.YES, null, BigIntPlutusData.of(1))
                .createVote(lowScriptVoter, govAction(1), Vote.NO, null, BigIntPlutusData.of(1))
                .attachVotingValidator(lowScript);

        Transaction transaction = build(tx, 1);

        assertThat(transaction.getBody().getVotingProcedures().getVoting()).hasSize(1);
        assertThat(transaction.getBody().getVotingProcedures().getVoting().get(lowScriptVoter)).hasSize(2);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(1);
        assertVotingRedeemer(transaction, 0, BigIntPlutusData.of(1));
    }

    @Test
    void sameScriptVoterOnTwoGovActions_conflictingRedeemers_throws() {
        Tx tx = new Tx()
                .createVote(lowScriptVoter, govAction(0), Vote.YES, null, BigIntPlutusData.of(1))
                .createVote(lowScriptVoter, govAction(1), Vote.NO, null, BigIntPlutusData.of(2))
                .attachVotingValidator(lowScript);

        assertThatThrownBy(() -> build(tx, 1))
                .hasStackTraceContaining("Conflicting voting redeemers");
    }

    @Test
    @SneakyThrows
    void txPlanYaml_withVoterVariable_redeemerIndexFollowsLedgerOrder() {
        String lowHex = voterHex(lowScriptVoter);
        String highHex = voterHex(highScriptVoter);

        Tx tx = new Tx()
                .createVote(keyVoter, govAction(0), Vote.YES, null, null)
                .createVote(highScriptVoter, govAction(0), Vote.YES, null, BigIntPlutusData.of(2))
                .createVote(lowScriptVoter, govAction(0), Vote.NO, null, BigIntPlutusData.of(1))
                .attachVotingValidator(highScript)
                .attachVotingValidator(lowScript);

        // Turn both script voters into variables, so the same template can be bound in two ways
        String template = TxPlan.from(tx).addVariable("first", "x").addVariable("second", "x").toYaml()
                .replace(highHex, "${first}")
                .replace(lowHex, "${second}");
        assertThat(template).contains("${first}", "${second}");

        // Binding 1: same as the Java tx -> low script (data 1) at 0, high script (data 2) at 1
        Transaction t1 = buildPlan(bind(template, highHex, lowHex));
        assertVotingRedeemer(t1, 0, BigIntPlutusData.of(1));
        assertVotingRedeemer(t1, 1, BigIntPlutusData.of(2));

        // Binding 2: swap the voters -> data 2 now belongs to the low script, at index 0
        Transaction t2 = buildPlan(bind(template, lowHex, highHex));
        assertVotingRedeemer(t2, 0, BigIntPlutusData.of(2));
        assertVotingRedeemer(t2, 1, BigIntPlutusData.of(1));
    }

    // Fill the template's variables section; variables are resolved when the YAML is read
    private static String bind(String template, String first, String second) {
        String bound = template.replace("first: x", "first: " + first).replace("second: x", "second: " + second);
        assertThat(bound).isNotEqualTo(template);
        return bound;
    }

    private Transaction buildPlan(String yaml) {
        TxPlan plan = TxPlan.from(yaml);
        return new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(plan)
                .feePayer(sender1)
                .withTxEvaluator(evaluator(2))
                .build();
    }

    private Transaction build(Tx tx, int redeemers) {
        return new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .feePayer(sender1)
                .withTxEvaluator(evaluator(redeemers))
                .build();
    }

    private StaticTransactionEvaluator evaluator(int redeemers) {
        return new StaticTransactionEvaluator(Collections.nCopies(redeemers, ExUnits.builder()
                .mem(BigInteger.valueOf(800))
                .steps(BigInteger.valueOf(1000000))
                .build()));
    }

    private static void assertVotingRedeemer(Transaction transaction, int index, PlutusData expectedData) {
        assertThat(transaction.getWitnessSet().getRedeemers())
                .filteredOn(rd -> rd.getTag() == RedeemerTag.Voting && BigInteger.valueOf(index).equals(rd.getIndex()))
                .singleElement()
                .satisfies(rd -> assertThat(rd.getData()).isEqualTo(expectedData));
    }

    private static GovActionId govAction(int index) {
        return new GovActionId(GOV_ACTION_TX, index);
    }

    @SneakyThrows
    private static String voterHex(Voter voter) {
        return HexUtil.encodeHexString(CborSerializationUtil.serialize(voter.serialize()));
    }
}
