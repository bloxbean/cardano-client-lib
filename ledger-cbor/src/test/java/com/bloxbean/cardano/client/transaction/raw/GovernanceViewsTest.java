package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0003 M5: the views of governance, of a parameter update and of the update before Conway. */
class GovernanceViewsTest {
    private static final String H28 = "581c" + "11".repeat(28);
    private static final String H32 = "5820" + "22".repeat(32);
    private static final String RA = "581d" + "e1" + "33".repeat(28);
    private static final String HALF = "d81e820102";
    private static final String ANCHOR = "82" + "6161" + H32;
    private static final String ACTION_ID = "82" + H32 + "03";
    private static final String MIN = "00" + "80" + "01" + "80" + "02" + "00";

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }

    private static String hex(byte[] bytes) {
        return HexUtil.encodeHexString(bytes);
    }

    /** Reads every typed field of the governance and the update of a body, for a test that reads them all. */
    static void readAll(RawTxBody body) {
        body.update().ifPresent(update -> {
            update.epoch();
            update.proposals().forEach(proposal -> readAll(proposal.getValue()));
        });
        body.votingProcedures().ifPresent(procedures -> procedures.voters().forEach(voter -> {
            voter.voter().hash();
            voter.votes().forEach(vote -> {
                vote.getValue().vote();
                vote.getValue().anchor().ifPresent(RawAnchor::url);
            });
        }));
        body.proposalProcedures().ifPresent(proposals -> proposals.forEach(proposal -> {
            proposal.deposit();
            proposal.rewardAccount();
            proposal.anchor().url();
            readAll(proposal.govAction());
        }));
        body.currentTreasury();
        body.donation();
    }

    static void readAll(RawGovAction action) {
        switch (action.kind()) {
            case PARAMETER_CHANGE:
                action.previousActionId();
                readAll(action.protocolParamUpdate());
                action.policyHash();
                break;
            case HARD_FORK_INITIATION:
                action.previousActionId();
                action.protocolVersion().minor();
                action.protocolVersion().major();
                break;
            case TREASURY_WITHDRAWALS:
                action.withdrawals();
                action.policyHash();
                break;
            case NO_CONFIDENCE:
                action.previousActionId();
                break;
            case UPDATE_COMMITTEE:
                action.previousActionId();
                action.membersToRemove().forEach(RawCredential::hash);
                action.membersToAdd().forEach(member -> member.getKey().hash());
                action.quorum();
                break;
            case NEW_CONSTITUTION:
                action.previousActionId();
                action.constitution().anchor().url();
                action.constitution().scriptHash();
                break;
            default:
        }
    }

    static void readAll(RawProtocolParamUpdate update) {
        for (Runnable read : List.<Runnable>of(update::minFeeA, update::minFeeB, update::maxBlockBodySize,
                update::maxTxSize, update::maxBlockHeaderSize, update::keyDeposit, update::poolDeposit, update::maxEpoch,
                update::nOpt, update::poolPledgeInfluence, update::expansionRate, update::treasuryGrowthRate,
                update::decentralization, () -> update.extraEntropy().ifPresent(RawNonce::hash),
                () -> update.protocolVersion().ifPresent(RawProtocolVersion::minor), update::minUtxoValue,
                update::minPoolCost, update::coinsPerUtxoWord, update::coinsPerUtxoByte,
                () -> update.costModels().ifPresent(m -> m.models().forEach(RawCostModels.Model::costs)),
                update::executionCosts,
                update::maxTxExUnits, update::maxBlockExUnits, update::maxValueSize, update::collateralPercentage,
                update::maxCollateralInputs,
                () -> update.poolVotingThresholds().ifPresent(t -> t.get(RawVotingThresholds.Pool.SECURITY_GROUP)),
                () -> update.drepVotingThresholds().ifPresent(t -> t.get(RawVotingThresholds.DRep.TREASURY_WITHDRAWAL)),
                update::minCommitteeSize, update::committeeTermLimit, update::govActionLifetime,
                update::govActionDeposit, update::drepDeposit, update::drepInactivity,
                update::minFeeRefScriptCostPerByte))
            read.run();
    }

    /** A parameter update with every key, 0 to 33, each with a value of its type. */
    static String everyParameter() {
        String[] values = {"00", "01", "1a00010000", "1a00004000", "190400", "1a00010000", "1a00020000", "12",
                "1864", HALF, HALF, HALF, HALF, "8201" + H32, "820600", "1a000f4240", "1a05f5e100", "1902d0",
                "a1" + "00" + "8201" + "20", "82" + HALF + HALF, "82" + "01" + "02", "82" + "03" + "04", "1a00001388",
                "1896", "03", "85" + HALF.repeat(5), "8a" + HALF.repeat(10), "07", "1892", "18b4",
                "1b000000174876e800", "1b00000002540be400", "14", HALF};
        StringBuilder update = new StringBuilder("b822");
        for (int key = 0; key < values.length; key++)
            update.append(key < 24 ? String.format("%02x", key) : "18" + String.format("%02x", key)).append(values[key]);
        return update.toString();
    }

    @Test
    void readsEveryParameterInConway() {
        RawProtocolParamUpdate update = RawProtocolParamUpdate.of(span(everyParameter()), Era.Conway);
        assertThat(update.minFeeA()).contains(BigInteger.ZERO);
        assertThat(update.minFeeB()).contains(BigInteger.ONE);
        assertThat(update.maxBlockBodySize().getAsLong()).isEqualTo(65536);
        assertThat(update.maxBlockHeaderSize().getAsInt()).isEqualTo(1024);
        assertThat(update.maxEpoch().getAsLong()).isEqualTo(18);
        assertThat(update.nOpt().getAsInt()).isEqualTo(100);
        assertThat(update.poolPledgeInfluence()).contains(new UnitInterval(BigInteger.ONE, BigInteger.TWO));
        assertThat(update.decentralization()).isPresent();
        assertThat(update.extraEntropy().orElseThrow().hash()).isPresent();
        assertThat(update.protocolVersion().orElseThrow().major()).isEqualTo(6);
        assertThat(update.minUtxoValue()).contains(BigInteger.valueOf(1_000_000));
        assertThat(update.coinsPerUtxoByte()).contains(BigInteger.valueOf(720));
        RawCostModels.Model model = update.costModels().orElseThrow().models().get(0);
        assertThat(model.language()).isZero();
        assertThat(model.costs()).containsExactly(1, -1);
        assertThat(hex(model.span().bytes())).isEqualTo("8201" + "20");
        assertThat(hex(update.costModels().orElseThrow().span().bytes())).isEqualTo("a1" + "00" + "8201" + "20");
        assertThat(update.executionCosts().orElseThrow().steps()).isEqualTo(new UnitInterval(BigInteger.ONE,
                BigInteger.TWO));
        assertThat(update.maxTxExUnits().orElseThrow().steps()).isEqualTo(2);
        assertThat(update.maxBlockExUnits().orElseThrow().mem()).isEqualTo(3);
        assertThat(update.maxValueSize().getAsLong()).isEqualTo(5000);
        assertThat(update.collateralPercentage().getAsInt()).isEqualTo(150);
        assertThat(update.maxCollateralInputs().getAsInt()).isEqualTo(3);
        assertThat(update.poolVotingThresholds().orElseThrow().get(RawVotingThresholds.Pool.SECURITY_GROUP))
                .isEqualTo(new UnitInterval(BigInteger.ONE, BigInteger.TWO));
        assertThat(update.drepVotingThresholds().orElseThrow().get(RawVotingThresholds.DRep.TREASURY_WITHDRAWAL))
                .isEqualTo(new UnitInterval(BigInteger.ONE, BigInteger.TWO));
        assertThat(update.minCommitteeSize().getAsInt()).isEqualTo(7);
        assertThat(update.committeeTermLimit().getAsLong()).isEqualTo(146);
        assertThat(update.govActionLifetime().getAsLong()).isEqualTo(180);
        assertThat(update.govActionDeposit()).contains(BigInteger.valueOf(100_000_000_000L));
        assertThat(update.drepDeposit()).contains(BigInteger.valueOf(10_000_000_000L));
        assertThat(update.drepInactivity().getAsLong()).isEqualTo(20);
        assertThat(update.minFeeRefScriptCostPerByte()).isPresent();
        assertThatThrownBy(() -> update.poolVotingThresholds().orElseThrow().get(RawVotingThresholds.DRep.NETWORK_GROUP))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keySeventeenIsCoinsPerWordInAlonzoAndCoinsPerByteAfter() {
        String update = "a1" + "11" + "1a0000868d";
        RawProtocolParamUpdate alonzo = RawProtocolParamUpdate.of(span(update), Era.Alonzo);
        assertThat(alonzo.coinsPerUtxoWord()).contains(BigInteger.valueOf(34_445));
        for (Era era : List.of(Era.Babbage, Era.Conway)) {
            RawProtocolParamUpdate later = RawProtocolParamUpdate.of(span(update), era);
            assertThat(later.coinsPerUtxoWord()).as(era.name()).isEmpty();
            assertThat(later.coinsPerUtxoByte()).as(era.name()).contains(BigInteger.valueOf(34_445));
        }
        // before its era a field is read as it is (ADR 0003 D5), as key 14 of a body is
        assertThat(alonzo.coinsPerUtxoByte()).contains(BigInteger.valueOf(34_445));

        FieldSpec word = ProtocolParamField.COINS_PER_UTXO_WORD.spec();
        FieldSpec bytes = ProtocolParamField.COINS_PER_UTXO_BYTE.spec();
        assertThat(ProtocolParamField.RECORD.field(17, Era.Alonzo)).isSameAs(word);
        assertThat(ProtocolParamField.RECORD.field(17, Era.Babbage)).isSameAs(bytes);
        // a key of a later era is unknown in a keyed record (ADR 0003 D4a)
        assertThat(ProtocolParamField.RECORD.field(17, Era.Mary)).isNull();
    }

    @Test
    void theKeysThatChangedTypeAreReadInEveryEra() {
        for (Era era : List.of(Era.Shelley, Era.Babbage, Era.Conway)) {
            RawProtocolParamUpdate update = RawProtocolParamUpdate.of(span("a2" + "00" + "182c" + "01" + "1a00025ef5"),
                    era);
            assertThat(update.minFeeA()).as(era.name()).contains(BigInteger.valueOf(44));
            assertThat(update.minFeeB()).as(era.name()).contains(BigInteger.valueOf(155_381));
        }
    }

    @Test
    void aParameterUpdateIsReadLikeAMapThatTheModelsDecode() {
        // a repeated key means its last value, and keys that are no untagged integer are passed over
        String update = "a5" + "00" + "01" + "00" + "02" + "c100" + "05" + "1800" + "03" + "6161" + "04";
        RawProtocolParamUpdate read = RawProtocolParamUpdate.of(span(update), Era.Conway);
        assertThat(read.minFeeA()).contains(BigInteger.valueOf(3));
        assertThat(read.keyDeposit()).isEmpty();

        // a number that is no number: absent for the models, rejected by the reader
        String text = "a1" + "05" + "6161";
        assertThat(RawProtocolParamUpdate.lenient(span(text), Era.Conway).keyDeposit()).isEmpty();
        assertThatThrownBy(() -> RawProtocolParamUpdate.of(span(text), Era.Conway).keyDeposit())
                .isInstanceOf(CborRuntimeException.class);
        // a nonce with items after its last, and prices with one: only the models' reading takes them
        String extra = "a2" + "0d" + "830001" + "02" + "13" + "83" + HALF + HALF + "00";
        assertThat(RawProtocolParamUpdate.lenient(span(extra), Era.Alonzo).extraEntropy().orElseThrow().hash())
                .isEmpty();
        assertThat(RawProtocolParamUpdate.lenient(span(extra), Era.Alonzo).executionCosts()).isPresent();
        assertThatThrownBy(() -> RawProtocolParamUpdate.of(span(extra), Era.Alonzo).extraEntropy())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawProtocolParamUpdate.of(span(extra), Era.Alonzo).executionCosts())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawProtocolParamUpdate.lenient(span("a1" + "13" + "9f" + HALF + HALF + "ff"),
                Era.Alonzo).executionCosts()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawProtocolParamUpdate.of(span("80"), Era.Conway))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void costModelsInTheLanguageViewsFormAreReadOnlyAsTheModelReadsThem() {
        // {h'00': h'9f0102ff'}: language 0 and its costs, each in a byte string
        String views = "a1" + "4100" + "44" + "9f0102ff";
        RawCostModels.Model model = RawCostModels.lenient(span(views)).models().get(0);
        assertThat(model.language()).isZero();
        assertThat(model.costs()).containsExactly(1, 2);
        assertThat(hex(model.span().bytes())).isEqualTo("9f0102ff");
        assertThatThrownBy(() -> RawCostModels.of(span(views)).models()).isInstanceOf(CborRuntimeException.class);
        // a cost beyond an int64, and a bignum
        assertThatThrownBy(() -> RawCostModels.of(span("a1" + "00" + "81" + "1b8000000000000000")).models().get(0)
                .costs()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawCostModels.of(span("a1" + "00" + "81" + "c24101")).models().get(0).costs())
                .isInstanceOf(CborRuntimeException.class);
        // the strict reading keeps a repeated language, the lenient one the last
        String twice = "a2" + "00" + "8101" + "00" + "8102";
        assertThat(RawCostModels.of(span(twice)).models()).hasSize(2);
        assertThat(RawCostModels.lenient(span(twice)).models()).singleElement()
                .satisfies(m -> assertThat(m.costs()).containsExactly(2));
    }

    @Test
    void readsAnUpdateBeforeConway() {
        String update = "82" + "a2" + H28 + "a1" + "11" + "1a0000868d" + "581c" + "44".repeat(28) + "a0" + "1901f4";
        RawUpdate read = RawUpdate.of(span(update), Era.Alonzo);
        assertThat(read.epoch()).isEqualTo(500);
        List<Map.Entry<byte[], RawProtocolParamUpdate>> proposals = read.proposals();
        assertThat(proposals).hasSize(2);
        assertThat(hex(proposals.get(0).getKey())).isEqualTo("11".repeat(28));
        assertThat(proposals.get(0).getValue().coinsPerUtxoWord()).contains(BigInteger.valueOf(34_445));
        assertThat(proposals.get(0).getValue().era()).isEqualTo(Era.Alonzo);

        assertThatThrownBy(() -> RawUpdate.of(span("83" + "a0" + "00" + "00"), Era.Alonzo))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawUpdate.lenient(span("9f" + "a0" + "00" + "ff"), Era.Alonzo))
                .isInstanceOf(CborRuntimeException.class);
        assertThat(RawUpdate.of(span("9f" + "a0" + "00" + "ff"), Era.Alonzo).epoch()).isZero();
    }

    @Test
    void readsEveryKindOfAction() {
        RawGovAction change = RawGovAction.of(span("84" + "00" + ACTION_ID + "a1" + "00" + "01" + H28), Era.Conway);
        assertThat(change.kind()).isEqualTo(GovActionKind.PARAMETER_CHANGE);
        assertThat(change.previousActionId().orElseThrow().index()).isEqualTo(3);
        assertThat(change.protocolParamUpdate().minFeeA()).contains(BigInteger.ONE);
        assertThat(hex(change.policyHash().orElseThrow())).isEqualTo("11".repeat(28));

        RawGovAction hardFork = RawGovAction.of(span("83" + "01" + "f6" + "82" + "0a" + "00"), Era.Conway);
        assertThat(hardFork.previousActionId()).isEmpty();
        assertThat(hardFork.protocolVersion().major()).isEqualTo(10);
        assertThat(hardFork.protocolVersion().minor()).isZero();

        RawGovAction treasury = RawGovAction.of(span("83" + "02" + "a1" + RA + "1864" + "f6"), Era.Conway);
        assertThat(treasury.withdrawals()).singleElement().satisfies(w -> assertThat(w.coin()).isEqualTo(100));
        assertThat(treasury.policyHash()).isEmpty();

        assertThat(RawGovAction.of(span("82" + "03" + ACTION_ID), Era.Conway).previousActionId()).isPresent();

        String credential = "8200" + H28;
        RawGovAction committee = RawGovAction.of(span("85" + "04" + "f6" + "d90102" + "81" + credential + "a1"
                + "8201" + H28 + "1b8000000000000000" + HALF), Era.Conway);
        assertThat(committee.membersToRemove()).singleElement().satisfies(c -> assertThat(c.isScript()).isFalse());
        assertThat(committee.membersToAdd().get(0).getKey().isScript()).isTrue();
        assertThat(committee.membersToAdd().get(0).getValue()).isEqualTo(Long.MIN_VALUE);
        assertThat(committee.quorum()).isEqualTo(new UnitInterval(BigInteger.ONE, BigInteger.TWO));

        RawGovAction constitution = RawGovAction.of(span("83" + "05" + "f6" + "82" + ANCHOR + "f6"), Era.Conway);
        assertThat(constitution.constitution().anchor().url()).isEqualTo("a");
        assertThat(constitution.constitution().scriptHash()).isEmpty();

        assertThat(RawGovAction.of(span("8106"), Era.Conway).kind()).isEqualTo(GovActionKind.INFO);
    }

    @Test
    void anActionIsReadExactlyOrAsTheModelsReadIt() {
        // an item after the last: rejected, or ignored as the models ignore it
        String longer = "83" + "06" + "00" + "00";
        assertThatThrownBy(() -> RawGovAction.of(span(longer), Era.Conway)).isInstanceOf(CborRuntimeException.class);
        assertThat(RawGovAction.lenient(span(longer), Era.Conway).kind()).isEqualTo(GovActionKind.INFO);
        assertThatThrownBy(() -> RawGovAction.of(span("8107"), Era.Conway)).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawGovAction.lenient(span("80"), Era.Conway))
                .isInstanceOf(CborRuntimeException.class);
        // read as a kind, the tag is not read
        RawGovAction asKind = RawGovAction.lenient(GovActionKind.NO_CONFIDENCE, span("82" + "6161" + "f6"),
                Era.Conway);
        assertThat(asKind.kind()).isEqualTo(GovActionKind.NO_CONFIDENCE);
        assertThat(asKind.previousActionId()).isEmpty();
        // an indefinite id is the models' to reject
        String indefiniteId = "82" + "03" + "9f" + H32 + "00" + "ff";
        assertThat(RawGovAction.of(span(indefiniteId), Era.Conway).previousActionId()).isPresent();
        assertThatThrownBy(() -> RawGovAction.lenient(span(indefiniteId), Era.Conway).previousActionId())
                .isInstanceOf(CborRuntimeException.class);
        // a coin that is no number is the models' null
        RawGovAction treasury = RawGovAction.lenient(span("83" + "02" + "a1" + RA + "6161" + "f6"), Era.Conway);
        assertThat(treasury.withdrawals().get(0).coin()).isNull();
    }

    @Test
    void readsVotesInEncodedOrderOrAsTheModelsDo() {
        String vote = "a1" + ACTION_ID + "82" + "01" + "f6";
        String voter = "8202" + H28;
        String repeated = "a2" + voter + vote + voter + "a1" + ACTION_ID + "82" + "00" + ANCHOR;
        List<RawVotingProcedures.Votes> all = RawVotingProcedures.of(span(repeated)).voters();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).voter().kind()).isEqualTo(RawVoter.Kind.DREP_KEY_HASH);
        assertThat(all.get(0).votes().get(0).getValue().vote()).isEqualTo(1);
        List<RawVotingProcedures.Votes> distinct = RawVotingProcedures.lenient(span(repeated)).voters();
        assertThat(distinct).singleElement().satisfies(v -> {
            assertThat(v.votes().get(0).getValue().vote()).isZero();
            assertThat(v.votes().get(0).getValue().anchor().orElseThrow().url()).isEqualTo("a");
        });
        assertThatThrownBy(() -> RawVoter.of(span("8205" + H28))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawVotingProcedures.lenient(span("a1" + voter + "a1" + ACTION_ID + "9f00f6ff"))
                .voters()).isInstanceOf(CborRuntimeException.class);
        assertThat(RawVotingProcedures.of(span("a1" + voter + "a1" + ACTION_ID + "9f00f6ff")).voters()).hasSize(1);
    }

    @Test
    void readsProposals() {
        String proposal = "84" + "1a000f4240" + RA + "8106" + ANCHOR;
        RawProposal read = RawProposal.of(span(proposal), Era.Conway);
        assertThat(read.deposit()).isEqualTo(1_000_000);
        assertThat(hex(read.rewardAccount())).isEqualTo("e1" + "33".repeat(28));
        assertThat(read.govAction().kind()).isEqualTo(GovActionKind.INFO);
        assertThat(read.anchor().url()).isEqualTo("a");
        // a deposit that is no number, and an item after the anchor: only the models' reading takes them
        String lenient = "85" + "6161" + RA + "8106" + ANCHOR + "00";
        assertThat(RawProposal.lenient(span(lenient), Era.Conway).deposit()).isNull();
        assertThatThrownBy(() -> RawProposal.of(span(lenient), Era.Conway)).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawProposal.lenient(span("9f" + proposal.substring(2) + "ff"), Era.Conway))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aBodyHasItsUpdateAndGovernanceTyped() {
        String body = "a8" + MIN + "06" + "82" + "a1" + H28 + "a1" + "11" + "01" + "00"
                + "13" + "a1" + "8204" + H28 + "a1" + ACTION_ID + "8202f6"
                + "14" + "d90102" + "81" + "84" + "00" + RA + "8106" + ANCHOR + "15" + "20" + "16" + "05";
        byte[] tx = HexUtil.decodeHexString("84" + body + "a0" + "f5" + "f6");
        RawTxBody strict = RawTx.of(tx).body(Era.Conway);
        assertThat(strict.update().orElseThrow().proposals().get(0).getValue().coinsPerUtxoByte())
                .contains(BigInteger.ONE);
        assertThat(strict.votingProcedures().orElseThrow().voters().get(0).voter().kind())
                .isEqualTo(RawVoter.Kind.STAKING_POOL_KEY_HASH);
        assertThat(strict.proposalProcedures().orElseThrow()).hasSize(1);
        assertThat(strict.donation()).contains(BigInteger.valueOf(5));
        // the treasury value: the reader's unsigned, the models' any integer, a follower's any number
        assertThatThrownBy(strict::currentTreasury).isInstanceOf(CborRuntimeException.class);
        assertThat(RawTx.lenient(tx).body(Era.Conway).currentTreasury()).contains(BigInteger.ONE.negate());
        RawTxBody tolerant = RawTxBody.tolerant(span(body), Era.Conway);
        assertThat(tolerant.currentTreasury()).contains(BigInteger.ONE.negate());
        assertThat(RawTxBody.tolerant(span("a4" + MIN + "15" + "c24101"), Era.Conway).currentTreasury())
                .contains(BigInteger.ONE);
        assertThatThrownBy(() -> RawTxBody.lenient(span("a4" + MIN + "15" + "c24101"), Era.Conway).currentTreasury())
                .isInstanceOf(CborRuntimeException.class);
        assertThat(RawTxBody.tolerant(span("a4" + MIN + "16" + "6161"), Era.Conway).donation()).isEmpty();
        readAll(RawTx.lenient(tx).body(Era.Conway));
        readAll(tolerant);
    }

    @Test
    void theWalkKnowsEveryFieldOfGovernanceAndUpdates() {
        String everyAction = "87"
                + "84" + "00" + RA + "84" + "00" + ACTION_ID + parametersOf(Era.Conway) + H28 + ANCHOR
                + "84" + "00" + RA + "83" + "01" + "f6" + "820a00" + ANCHOR
                + "84" + "00" + RA + "83" + "02" + "a1" + RA + "01" + "f6" + ANCHOR
                + "84" + "00" + RA + "82" + "03" + "f6" + ANCHOR
                + "84" + "00" + RA + "85" + "04" + "f6" + "81" + "8200" + H28 + "a1" + "8201" + H28 + "01" + HALF
                + ANCHOR
                + "84" + "00" + RA + "83" + "05" + "f6" + "82" + ANCHOR + H28 + ANCHOR
                + "84" + "00" + RA + "8106" + ANCHOR;
        String conway = "a7" + MIN + "13" + "a1" + "8200" + H28 + "a1" + ACTION_ID + "82" + "02" + ANCHOR
                + "14" + everyAction + "15" + "00" + "16" + "01";
        assertThat(unknown(conway, Era.Conway)).isEmpty();

        String alonzo = "a4" + MIN + "06" + "82" + "a1" + H28 + parametersOf(Era.Alonzo) + "00";
        assertThat(unknown(alonzo, Era.Alonzo)).isEmpty();
        // a Conway field in an Alonzo update is a key the Alonzo ledger does not know
        assertThat(unknown("a4" + MIN + "06" + "82" + "a1" + H28 + "a1" + "181e" + "00" + "00", Era.Alonzo))
                .containsExactly("protocol_param_update 181e");
    }

    // The parameter update of everyParameter's keys valid in an era.
    private static String parametersOf(Era era) {
        StringBuilder entries = new StringBuilder();
        int count = 0;
        for (Map.Entry<CborSpan, CborSpan> entry : span(everyParameter()).entries()) {
            FieldSpec row = ProtocolParamField.RECORD.field(entry.getKey().asLong(), era);
            if (row != null && row.validIn(era)) {
                entries.append(hex(entry.getKey().bytes())).append(hex(entry.getValue().bytes()));
                count++;
            }
        }
        assertThat(count).isEqualTo(era == Era.Alonzo ? 24 : 30);
        return "b8" + String.format("%02x", count) + entries;
    }

    // The entries a walk reports as unknown or outside their era, and the fields it reports missing.
    private static List<String> unknown(String body, Era era) {
        List<String> found = new ArrayList<>();
        RawTx.walk(HexUtil.decodeHexString("84" + body + "a0" + "f5" + "f6"), era, new FormatPolicy() {
            @Override
            public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence,
                              Era read) {
                if (field == null || !field.validIn(read))
                    found.add(record + " " + (field != null ? field.key() : key == null ? "position" : hex(key.bytes())));
            }

            @Override
            public void missing(RecordSpec record, FieldSpec field, CborSpan container, Era read) {
                found.add("missing " + record + " " + field.key());
            }
        });
        return found;
    }
}
