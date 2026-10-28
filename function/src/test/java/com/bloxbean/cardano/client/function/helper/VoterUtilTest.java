package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoterUtilTest {

    private static final String GOV_ACTION_TX = "12745f09b138d4d0a11a560b4591ebb830cf12336347606d2edbbf1893d395c6";

    private Voter voter(VoterType type, String hashPrefix) {
        String hash = hashPrefix + "00".repeat(27);
        Credential credential = (type == VoterType.CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH || type == VoterType.DREP_SCRIPT_HASH)
                ? Credential.fromScript(hash) : Credential.fromKey(hash);
        return new Voter(type, credential);
    }

    private VotingProcedures votingProcedures(List<Voter> voters) {
        VotingProcedures votingProcedures = new VotingProcedures();
        for (Voter voter : voters) {
            votingProcedures.add(voter, new GovActionId(GOV_ACTION_TX, 0), new VotingProcedure(Vote.YES, null));
        }
        return votingProcedures;
    }

    @Test
    void getSortedVoters_allRoles_ledgerOrder() {
        // Hashes are chosen so that hash order alone, or VoterType (CBOR tag) order, gives a different result
        Voter ccScript = voter(VoterType.CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH, "ff");
        Voter ccKey = voter(VoterType.CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH, "01");
        Voter drepScript = voter(VoterType.DREP_SCRIPT_HASH, "ee");
        Voter drepKey = voter(VoterType.DREP_KEY_HASH, "02");
        Voter pool = voter(VoterType.STAKING_POOL_KEY_HASH, "00");

        List<Voter> sorted = VoterUtil.getSortedVoters(votingProcedures(List.of(pool, drepKey, drepScript, ccKey, ccScript)));

        assertThat(sorted).containsExactly(ccScript, ccKey, drepScript, drepKey, pool);
    }

    @Test
    void getSortedVoters_sameRole_scriptBeforeKey() {
        // Key hash sorts before the script hash, but the ledger puts script credentials first
        Voter drepKey = voter(VoterType.DREP_KEY_HASH, "11");
        Voter drepScript = voter(VoterType.DREP_SCRIPT_HASH, "dd");

        List<Voter> sorted = VoterUtil.getSortedVoters(votingProcedures(List.of(drepKey, drepScript)));

        assertThat(sorted).containsExactly(drepScript, drepKey);
    }

    @Test
    void getSortedVoters_sameType_sortedByHash() {
        Voter scriptDd = voter(VoterType.DREP_SCRIPT_HASH, "dd");
        Voter script22 = voter(VoterType.DREP_SCRIPT_HASH, "22");
        Voter script8a = voter(VoterType.DREP_SCRIPT_HASH, "8a");

        List<Voter> sorted = VoterUtil.getSortedVoters(votingProcedures(List.of(scriptDd, script22, script8a)));

        assertThat(sorted).containsExactly(script22, script8a, scriptDd);
    }

    @Test
    void getIndexByVoter_mixedVoters() {
        Voter drepKey = voter(VoterType.DREP_KEY_HASH, "11");
        Voter scriptDd = voter(VoterType.DREP_SCRIPT_HASH, "dd");
        Voter script22 = voter(VoterType.DREP_SCRIPT_HASH, "22");

        List<Voter> sorted = VoterUtil.getSortedVoters(votingProcedures(List.of(drepKey, scriptDd, script22)));

        assertThat(VoterUtil.getIndexByVoter(sorted, script22)).isEqualTo(0);
        assertThat(VoterUtil.getIndexByVoter(sorted, scriptDd)).isEqualTo(1);
        assertThat(VoterUtil.getIndexByVoter(sorted, drepKey)).isEqualTo(2);
    }

    @Test
    void getIndexByVoter_notFound() {
        Voter drepKey = voter(VoterType.DREP_KEY_HASH, "11");
        Voter missing = voter(VoterType.DREP_SCRIPT_HASH, "11");

        List<Voter> sorted = VoterUtil.getSortedVoters(votingProcedures(new ArrayList<>(List.of(drepKey))));

        assertThat(VoterUtil.getIndexByVoter(sorted, missing)).isEqualTo(-1);
    }

    @Test
    void getSortedVoters_sameVoterOnSeveralGovActions_oneEntry() {
        Voter drepScript = voter(VoterType.DREP_SCRIPT_HASH, "dd");
        VotingProcedures votingProcedures = new VotingProcedures();
        votingProcedures.add(drepScript, new GovActionId(GOV_ACTION_TX, 0), new VotingProcedure(Vote.YES, null));
        votingProcedures.add(drepScript, new GovActionId(GOV_ACTION_TX, 1), new VotingProcedure(Vote.NO, null));

        assertThat(VoterUtil.getSortedVoters(votingProcedures)).containsExactly(drepScript);
    }
}
