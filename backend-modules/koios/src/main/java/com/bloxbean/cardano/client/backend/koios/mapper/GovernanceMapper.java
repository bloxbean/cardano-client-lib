package com.bloxbean.cardano.client.backend.koios.mapper;

import com.bloxbean.cardano.client.backend.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface GovernanceMapper {

    GovernanceMapper INSTANCE = Mappers.getMapper(GovernanceMapper.class);

    List<DRepEpochSummary> mapToDRepEpochSummaryList(List<rest.koios.client.backend.api.governance.model.DRepEpochSummary> dRepEpochSummaryList);

    List<DRep> mapToDRepList(List<rest.koios.client.backend.api.governance.model.DRep> dRepList);

    List<DRepInfo> mapToDRepInfoList(List<rest.koios.client.backend.api.governance.model.DRepInfo> dRepInfoList);

    List<DRepMetadata> mapToDRepMetadataList(List<rest.koios.client.backend.api.governance.model.DRepMetadata> dRepMetadataList);

    List<DRepUpdate> mapToDRepUpdateList(List<rest.koios.client.backend.api.governance.model.DRepUpdate> dRepUpdateList);

    List<DRepVote> mapToDRepVoteList(List<rest.koios.client.backend.api.governance.model.DRepVote> dRepVoteList);

    List<DRepDelegator> mapToDRepDelegatorList(List<rest.koios.client.backend.api.governance.model.DRepDelegator> dRepDelegators);

    List<CommitteeInfo> mapToCommitteeInfoList(List<rest.koios.client.backend.api.governance.model.CommitteeInfo> committeeInfo);

    List<CommitteeVote> mapToCommitteeVoteList(List<rest.koios.client.backend.api.governance.model.CommitteeVote> committeeVoteList);

    List<Proposal> mapToProposalList(List<rest.koios.client.backend.api.governance.model.Proposal> proposalList);

    List<ProposalVotingSummary> mapToProposalVotingSummaryList(List<rest.koios.client.backend.api.governance.model.ProposalVotingSummary> proposalVotingSummaryList);

    List<ProposalVote> mapToProposalVoteList(List<rest.koios.client.backend.api.governance.model.ProposalVote> proposalVoteList);

    List<PoolVote> mapToPoolVoteList(List<rest.koios.client.backend.api.governance.model.PoolVote> poolVoteList);
}
