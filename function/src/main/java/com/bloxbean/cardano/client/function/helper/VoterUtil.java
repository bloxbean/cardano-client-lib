package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class for sorting voters in the order the Cardano ledger uses.
 * The ledger sorts voters by role (constitutional committee, DRep, stake pool), with script credentials
 * before key credentials within a role, then by credential hash. Voting redeemer indices must match
 * positions in this sorted list.
 * <p>
 * This is not the order of {@link com.bloxbean.cardano.client.transaction.spec.governance.VoterType},
 * which follows the CBOR tags (key before script).
 */
public class VoterUtil {

    /**
     * Returns a comparator that sorts Voters in ledger order: committee script, committee key,
     * DRep script, DRep key, stake pool, and by credential hash within each group.
     *
     * @return Comparator for Voter objects
     */
    public static Comparator<Voter> getVoterComparator() {
        return Comparator.comparingInt(VoterUtil::rank)
                .thenComparing(voter -> HexUtil.encodeHexString(voter.getCredential().getBytes()));
    }

    /**
     * Returns the voters of the voting procedures sorted in ledger order.
     *
     * @param votingProcedures voting procedures
     * @return sorted list of voters
     */
    public static List<Voter> getSortedVoters(@NonNull VotingProcedures votingProcedures) {
        return votingProcedures.getVoting().keySet().stream()
                .sorted(getVoterComparator())
                .collect(Collectors.toList());
    }

    /**
     * Returns the index of a voter in the list.
     *
     * @param voters list of voters (should be sorted)
     * @param voter voter to find
     * @return index of the voter, or -1 if not found
     */
    public static int getIndexByVoter(@NonNull List<Voter> voters, @NonNull Voter voter) {
        return IntStream.range(0, voters.size())
                .filter(i -> voter.equals(voters.get(i)))
                .findFirst()
                .orElse(-1);
    }

    private static int rank(Voter voter) {
        switch (voter.getType()) {
            case CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH:
                return 0;
            case CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH:
                return 1;
            case DREP_SCRIPT_HASH:
                return 2;
            case DREP_KEY_HASH:
                return 3;
            case STAKING_POOL_KEY_HASH:
                return 4;
            default:
                throw new IllegalArgumentException("Invalid voter type: " + voter.getType());
        }
    }
}
