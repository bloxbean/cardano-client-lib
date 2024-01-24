package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
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
        Map map = new Map();

        for (java.util.Map.Entry<Voter, java.util.Map<GovActionId, VotingProcedure>> entry : voting.entrySet()) {
            Voter voter = entry.getKey();
            java.util.Map<GovActionId, VotingProcedure> value = entry.getValue();

            Map govActIdVoteProcedureMap = new Map();
            for (java.util.Map.Entry<GovActionId, VotingProcedure> votingProEntry : value.entrySet()) {
                govActIdVoteProcedureMap.put(votingProEntry.getKey().serialize(), votingProEntry.getValue().serialize());
            }

            map.put(voter.serialize(), govActIdVoteProcedureMap);
        }

        return map;
    }

    public static VotingProcedures deserialize(DataItem di) {
        Map votingProceduresMap = (Map) di;

        VotingProcedures votingProcedures = new VotingProcedures();
        votingProceduresMap.getKeys().forEach(key -> {
            Array voterArray = (Array) key;
            Voter voter = Voter.deserialize(voterArray);

            Map votingProcedureMapDI = (Map) votingProceduresMap.get(key);
            java.util.Map<GovActionId, VotingProcedure> votingProcedureMap
                    = deserializeVotingProcedureMap(votingProcedureMapDI);
            votingProcedures.getVoting().put(voter, votingProcedureMap);
        });

        return votingProcedures;
    }

    /**
     * { + gov_action_id => voting_procedure }
     *
     * @param votingProcedureMapDI
     * @return
     */
    private static java.util.Map<GovActionId, VotingProcedure> deserializeVotingProcedureMap(Map votingProcedureMapDI) {
        java.util.Map<GovActionId, VotingProcedure> votingProcedureMap = new LinkedHashMap<>();
        votingProcedureMapDI.getKeys().forEach(key -> {
            GovActionId govActionId = GovActionId.deserialize(key);
            VotingProcedure votingProcedure = VotingProcedure.deserialize((Array) votingProcedureMapDI.get(key));
            votingProcedureMap.put(govActionId, votingProcedure);
        });

        return votingProcedureMap;
    }
}
