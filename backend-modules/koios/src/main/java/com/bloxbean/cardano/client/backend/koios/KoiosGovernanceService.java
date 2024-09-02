package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.GovernanceService;
import com.bloxbean.cardano.client.backend.koios.mapper.GovernanceMapper;
import com.bloxbean.cardano.client.backend.model.*;

import java.util.List;
import java.util.function.Function;

/**
 * Koios Governance Service
 */
public class KoiosGovernanceService implements GovernanceService {

    private final rest.koios.client.backend.api.governance.GovernanceService governanceService;
    private final GovernanceMapper mapper = GovernanceMapper.INSTANCE;

    public KoiosGovernanceService(rest.koios.client.backend.api.governance.GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Override
    public Result<List<DRepEpochSummary>> getDRepsEpochSummary(Integer epochNo) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.governance.model.DRepEpochSummary>> result = governanceService.getDRepsEpochSummary(epochNo, null);
            return map(result, mapper::mapToDRepEpochSummaryList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<DRep>> getDRepsList() throws ApiException {
        try {
            return map(governanceService.getDRepsList(null), mapper::mapToDRepList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<DRepInfo>> getDRepsInfo(List<String> drepIds) throws ApiException {
        try {
            return map(governanceService.getDRepsInfo(drepIds, null), mapper::mapToDRepInfoList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<DRepMetadata>> getDRepsMetadata(List<String> drepIds) throws ApiException {
        try {
            return map(governanceService.getDRepsMetadata(drepIds, null), mapper::mapToDRepMetadataList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<DRepUpdate>> getDRepsUpdates(String drepId) throws ApiException {
        try {
            return map(governanceService.getDRepsUpdates(drepId, null), mapper::mapToDRepUpdateList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<DRepVote>> getDRepsVotes(String drepId) throws ApiException {
        try {
            return map(governanceService.getDRepsVotes(drepId, null), mapper::mapToDRepVoteList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<DRepDelegator>> getDRepsDelegators(String drepId) throws ApiException {
        try {
            return map(governanceService.getDRepsDelegators(drepId, null), mapper::mapToDRepDelegatorList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<CommitteeInfo>> getCommitteeInformation() throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.governance.model.CommitteeInfo>> result = governanceService.getCommitteeInformation(null);
            return map(result, mapper::mapToCommitteeInfoList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<CommitteeVote>> getCommitteeVotes(String ccHotId) throws ApiException {
        try {
            return map(governanceService.getCommitteeVotes(ccHotId, null), mapper::mapToCommitteeVoteList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<Proposal>> getProposalList() throws ApiException {
        try {
            return map(governanceService.getProposalList(null), mapper::mapToProposalList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<Proposal>> getVoterProposals(String voterId) throws ApiException {
        try {
            return map(governanceService.getVoterProposals(voterId, null), mapper::mapToProposalList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<ProposalVotingSummary>> getProposalVotingSummary(String proposalId) throws ApiException {
        try {
            return map(governanceService.getProposalVotingSummary(proposalId, null), mapper::mapToProposalVotingSummaryList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<ProposalVote>> getProposalVotes(String proposalId) throws ApiException {
        try {
            return map(governanceService.getProposalVotes(proposalId, null), mapper::mapToProposalVoteList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<PoolVote>> getPoolVotes(String poolBech32) throws ApiException {
        try {
            return map(governanceService.getPoolVotes(poolBech32, null), mapper::mapToPoolVoteList);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    /**
     * Utility method to map a KoiosResult to the Result used by this API.
     *
     * @param koiosResult The result from Koios API
     * @param mapperFunc  The mapping function to convert Koios model to API model
     * @param <K>         The type of data in the Koios result
     * @param <T>         The type of data in the mapped result
     * @return The mapped result
     */
    private <K, T> Result<T> map(rest.koios.client.backend.api.base.Result<K> koiosResult, Function<K, T> mapperFunc) {
        if (koiosResult.isSuccessful()) {
            return Result.success("OK").withValue(mapperFunc.apply(koiosResult.getValue()));
        } else {
            return Result.error(koiosResult.getResponse()).code(koiosResult.getCode());
        }
    }
}
