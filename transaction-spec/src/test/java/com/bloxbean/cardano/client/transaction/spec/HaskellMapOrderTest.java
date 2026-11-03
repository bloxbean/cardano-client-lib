package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.TreasuryWithdrawalsAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.UpdateCommittee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0002 D6.1: a map keyed by a ledger type is written in Haskell's {@code Ord} order for that type, in canonical and
 * non-canonical encoding alike, except where CIP-21 decides. Expected orders are written out by hand, not from the
 * comparators.
 */
class HaskellMapOrderTest {

    private static final String LOW = "0a".repeat(28);
    private static final String HIGH = "aa".repeat(28);

    private static Credential key(String hash) {
        return Credential.fromKey(hash);
    }

    private static Credential script(String hash) {
        return Credential.fromScript(hash);
    }

    private static Withdrawal withdrawal(Credential credential, Network network) {
        return new Withdrawal(AddressProvider.getRewardAddress(credential, network).toBech32(), BigInteger.ONE);
    }

    /** Testnet before mainnet, script before key, then hash. High hash bytes sort after low ones, unsigned. */
    private static final List<String> REWARD_ACCOUNTS_IN_ORDER = List.of(
            "581df0" + LOW, "581df0" + HIGH, "581de0" + LOW, "581de0" + HIGH, "581df1" + LOW, "581de1" + LOW);

    /** The same accounts by their bytes, as CIP-21 wants them: e0, e1, f0, f1. */
    private static final List<String> REWARD_ACCOUNTS_IN_BYTES_ORDER = List.of(
            "581de0" + LOW, "581de0" + HIGH, "581de1" + LOW, "581df0" + LOW, "581df0" + HIGH, "581df1" + LOW);

    private static List<Withdrawal> shuffledWithdrawals() {
        return List.of(
                withdrawal(key(LOW), Networks.mainnet()),
                withdrawal(key(HIGH), Networks.testnet()),
                withdrawal(script(LOW), Networks.mainnet()),
                withdrawal(key(LOW), Networks.testnet()),
                withdrawal(script(HIGH), Networks.testnet()),
                withdrawal(script(LOW), Networks.testnet()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bodyWithdrawalsAreInBytesOrder(boolean canonical) throws Exception {
        TransactionBody body = TransactionBody.builder().withdrawals(new ArrayList<>(shuffledWithdrawals())).build();

        Map map = (Map) body.serialize().get(new UnsignedInteger(5));

        assertThat(encodedKeys(map, canonical)).isEqualTo(REWARD_ACCOUNTS_IN_BYTES_ORDER);
    }

    /** Only mixing keys and scripts tells Haskell's order and CIP-21's apart. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void withoutMixingBothOrdersAreTheSame(boolean canonical) throws Exception {
        for (Function<String, Credential> credential : List.<Function<String, Credential>>of(
                HaskellMapOrderTest::key, HaskellMapOrderTest::script)) {
            List<Withdrawal> withdrawals = List.of(withdrawal(credential.apply(HIGH), Networks.mainnet()),
                    withdrawal(credential.apply(LOW), Networks.testnet()),
                    withdrawal(credential.apply(LOW), Networks.mainnet()));

            assertThat(encodedKeys(Withdrawal.serialize(withdrawals), canonical))
                    .isEqualTo(encodedKeys(Withdrawal.serializeInLedgerOrder(withdrawals), canonical))
                    .hasSize(3);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void treasuryWithdrawalsAreInRewardAccountOrder(boolean canonical) throws Exception {
        TreasuryWithdrawalsAction action = TreasuryWithdrawalsAction.builder().withdrawals(shuffledWithdrawals())
                .build();

        Map map = (Map) ((Array) action.serialize()).getDataItems().get(1);

        assertThat(encodedKeys(map, canonical)).isEqualTo(REWARD_ACCOUNTS_IN_ORDER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void committeeMembersAreScriptFirstThenByHash(boolean canonical) throws Exception {
        LinkedHashMap<Credential, Integer> members = new LinkedHashMap<>();
        members.put(key(HIGH), 1);
        members.put(script(HIGH), 2);
        members.put(key(LOW), 3);
        members.put(script(LOW), 4);
        UpdateCommittee action = UpdateCommittee.builder().membersForRemoval(new LinkedHashSet<>())
                .newMembersAndTerms(members).quorumThreshold(new UnitInterval(BigInteger.ONE, BigInteger.TWO)).build();

        Map map = (Map) ((Array) action.serialize()).getDataItems().get(3);

        assertThat(encodedKeys(map, canonical)).containsExactly(
                "8201581c" + LOW, "8201581c" + HIGH, "8200581c" + LOW, "8200581c" + HIGH);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void votersAreCommitteeThenDRepThenPoolScriptFirst(boolean canonical) throws Exception {
        GovActionId action = GovActionId.builder().transactionId("cd".repeat(32)).govActionIndex(0).build();
        VotingProcedures votes = new VotingProcedures();
        List.of(new Voter(VoterType.STAKING_POOL_KEY_HASH, key(LOW)),
                new Voter(VoterType.DREP_KEY_HASH, key(LOW)),
                new Voter(VoterType.DREP_SCRIPT_HASH, script(HIGH)),
                new Voter(VoterType.DREP_SCRIPT_HASH, script(LOW)),
                new Voter(VoterType.CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH, key(LOW)),
                new Voter(VoterType.CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH, script(HIGH)))
                .forEach(voter -> votes.add(voter, action, new VotingProcedure(Vote.YES, null)));

        assertThat(encodedKeys((Map) votes.serialize(), canonical)).containsExactly(
                "8201581c" + HIGH, "8200581c" + LOW, "8203581c" + LOW, "8203581c" + HIGH, "8202581c" + LOW,
                "8204581c" + LOW);
    }

    /** Encoded bytes would put index 24, a two-byte integer, after every smaller index whatever its transaction id. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void votesAreByTransactionIdThenIndex(boolean canonical) throws Exception {
        String lowId = "00".repeat(32);
        String highId = "ff".repeat(32);
        Voter voter = new Voter(VoterType.DREP_KEY_HASH, key(LOW));
        VotingProcedures votes = new VotingProcedures();
        for (GovActionId id : List.of(new GovActionId(highId, 0), new GovActionId(lowId, 24),
                new GovActionId(lowId, 1))) {
            votes.add(voter, id, new VotingProcedure(Vote.YES, null));
        }

        Map inner = (Map) ((Map) votes.serialize()).getValues().iterator().next();

        assertThat(encodedKeys(inner, canonical)).containsExactly(
                "825820" + lowId + "01", "825820" + lowId + "1818", "825820" + highId + "00");
    }

    @Test
    void assetNamesAreLengthFirstThenUnsigned() {
        List<Asset> assets = new ArrayList<>(List.of(asset("0xff"), asset("0x01"), asset("0x0000"), asset("0x80")));

        assets.sort(Comparators.ASSET);

        assertThat(assets.stream().map(Asset::getName)).containsExactly("0x01", "0x80", "0xff", "0x0000");
    }

    private static Asset asset(String name) {
        return Asset.builder().name(name).value(BigInteger.ONE).build();
    }

    /** @return the map's keys in the order the encoder writes them */
    private static List<String> encodedKeys(Map map, boolean canonical) throws Exception {
        Map encoded = (Map) CborSerializationUtil.deserialize(CborSerializationUtil.serialize(map, canonical));
        List<String> keys = new ArrayList<>();
        for (DataItem key : encoded.getKeys()) {
            keys.add(HexFormat.of().formatHex(CborSerializationUtil.serialize(key, false)));
        }
        return keys;
    }
}
