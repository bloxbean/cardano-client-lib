package com.bloxbean.cardano.client.transaction.spec.governance;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.common.cbor.custom.SortedMap;
import com.bloxbean.cardano.client.transaction.raw.RawGovActionId;
import com.bloxbean.cardano.client.transaction.raw.RawVotingProcedure;
import com.bloxbean.cardano.client.transaction.raw.RawVotingProcedures;
import com.bloxbean.cardano.client.transaction.spec.Comparators;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * {@literal
 * voting_procedures = { + voter => { + gov_action_id => voting_procedure } }
 * }
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VotingProcedures {

    @Builder.Default
    private java.util.Map<Voter, java.util.Map<GovActionId, VotingProcedure>> voting = new java.util.LinkedHashMap<>();

    public void add(Voter voter, GovActionId govActionId, VotingProcedure votingProcedure) {
        Objects.requireNonNull(voter);
        Objects.requireNonNull(govActionId);
        Objects.requireNonNull(votingProcedure);

        var voterVotingProcedureMap = voting.get(voter);
        if (voterVotingProcedureMap == null) {
            voterVotingProcedureMap = new LinkedHashMap<>();
            voting.put(voter, voterVotingProcedureMap);
        }

        voterVotingProcedureMap.put(govActionId, votingProcedure);
    }

    public DataItem serialize() {
        Map map = new SortedMap();

        List<java.util.Map.Entry<Voter, java.util.Map<GovActionId, VotingProcedure>>> voters =
                new ArrayList<>(voting.entrySet());
        voters.sort(java.util.Map.Entry.comparingByKey(Comparators.VOTER));
        for (java.util.Map.Entry<Voter, java.util.Map<GovActionId, VotingProcedure>> entry : voters) {
            Voter voter = entry.getKey();
            java.util.Map<GovActionId, VotingProcedure> value = entry.getValue();

            Map govActIdVoteProcedureMap = new SortedMap();
            List<java.util.Map.Entry<GovActionId, VotingProcedure>> votes = new ArrayList<>(value.entrySet());
            votes.sort(java.util.Map.Entry.comparingByKey(Comparators.GOV_ACTION_ID));
            for (java.util.Map.Entry<GovActionId, VotingProcedure> votingProEntry : votes) {
                govActIdVoteProcedureMap.put(votingProEntry.getKey().serialize(), votingProEntry.getValue().serialize());
            }

            map.put(voter.serialize(), govActIdVoteProcedureMap);
        }

        return map;
    }

    public static VotingProcedures deserialize(DataItem di) {
        return from(RawVotingProcedures.lenient(CborSerializationUtil.span(di)));
    }

    /**
     * @param raw voting procedures as received
     * @return the model: a voter or an action id that equals an earlier one keeps that one's place and takes the later
     * value
     */
    public static VotingProcedures from(RawVotingProcedures raw) {
        VotingProcedures votingProcedures = new VotingProcedures();
        for (RawVotingProcedures.Votes voter : raw.voters()) {
            java.util.Map<GovActionId, VotingProcedure> votes = new LinkedHashMap<>();
            for (java.util.Map.Entry<RawGovActionId, RawVotingProcedure> vote : voter.votes())
                votes.put(GovActionId.from(vote.getKey()), VotingProcedure.from(vote.getValue()));
            votingProcedures.getVoting().put(Voter.from(voter.voter()), votes);
        }
        return votingProcedures;
    }
}
