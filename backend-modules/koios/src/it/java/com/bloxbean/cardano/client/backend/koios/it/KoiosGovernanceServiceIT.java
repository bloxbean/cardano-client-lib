package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.GovernanceService;
import com.bloxbean.cardano.client.backend.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class KoiosGovernanceServiceIT extends KoiosBaseTest {

    private GovernanceService governanceService;

    @BeforeEach
    public void setup() {
        governanceService = backendService.getGovernanceService();
    }

    @Test
    void getDRepsEpochSummaryTest() throws ApiException {
        Integer epochNo = 31;  // Example epoch number
        Result<List<DRepEpochSummary>> result = governanceService.getDRepsEpochSummary(epochNo);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("DReps Epoch Summary: " + result.getValue());
    }

    @Test
    void getDRepsListTest() throws ApiException {
        Result<List<DRep>> result = governanceService.getDRepsList();
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("DReps List: " + result.getValue());
    }

    @Test
    void getDRepsInfoTest() throws ApiException {
        List<String> drepIds = List.of("drep1kxtwaqtayj6vklc57u93xayjvkwgvefh8drscqp5a5y6jz7m6rd", "drep14x62vyme8l8dkhvxg6rauc6vpcd2va9fquz8k3jstsqczwlvqqh");
        Result<List<DRepInfo>> result = governanceService.getDRepsInfo(drepIds);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("DReps Info: " + result.getValue());
    }

    @Test
    void getDRepsMetadataTest() throws ApiException {
        List<String> drepIds = List.of("drep1kxtwaqtayj6vklc57u93xayjvkwgvefh8drscqp5a5y6jz7m6rd", "drep14x62vyme8l8dkhvxg6rauc6vpcd2va9fquz8k3jstsqczwlvqqh");
        Result<List<DRepMetadata>> result = governanceService.getDRepsMetadata(drepIds);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("DReps Metadata: " + result.getValue());
    }

    @Test
    void getDRepsUpdatesTest() throws ApiException {
        String drepId = "drep1kxtwaqtayj6vklc57u93xayjvkwgvefh8drscqp5a5y6jz7m6rd";
        Result<List<DRepUpdate>> result = governanceService.getDRepsUpdates(drepId);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("DReps Updates: " + result.getValue());
    }

    @Test
    void getDRepsVotesTest() throws ApiException {
        String drepId = "drep1kxtwaqtayj6vklc57u93xayjvkwgvefh8drscqp5a5y6jz7m6rd";
        Result<List<DRepVote>> result = governanceService.getDRepsVotes(drepId);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("DReps Votes: " + result.getValue());
    }

    @Test
    void getDRepsDelegatorsTest() throws ApiException {
        String drepId = "drep1kxtwaqtayj6vklc57u93xayjvkwgvefh8drscqp5a5y6jz7m6rd";
        Result<List<DRepDelegator>> result = governanceService.getDRepsDelegators(drepId);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("DReps Delegators: " + result.getValue());
    }

    @Test
    void getCommitteeInformationTest() throws ApiException {
        Result<List<CommitteeInfo>> result = governanceService.getCommitteeInformation();
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("Committee Information: " + result.getValue());
    }

    @Test
    void getCommitteeVotesTest() throws ApiException {
        String ccHotId = "cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7";
        Result<List<CommitteeVote>> result = governanceService.getCommitteeVotes(ccHotId);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("Committee Votes: " + result.getValue());
    }

    @Test
    void getProposalListTest() throws ApiException {
        Result<List<Proposal>> result = governanceService.getProposalList();
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("Proposal List: " + result.getValue());
    }

    @Test
    void getVoterProposalsTest() throws ApiException {
        String voterId = "drep1kxtwaqtayj6vklc57u93xayjvkwgvefh8drscqp5a5y6jz7m6rd";
        Result<List<Proposal>> result = governanceService.getVoterProposals(voterId);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("Voter Proposals: " + result.getValue());
    }

    @Test
    void getProposalVotingSummaryTest() throws ApiException {
        String proposalId = "gov_action1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqpzklpgpf";
        Result<List<ProposalVotingSummary>> result = governanceService.getProposalVotingSummary(proposalId);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("Proposal Voting Summary: " + result.getValue());
    }

    @Test
    void getProposalVotesTest() throws ApiException {
        String proposalId = "gov_action1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqpzklpgpf";
        Result<List<ProposalVote>> result = governanceService.getProposalVotes(proposalId);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("Proposal Votes: " + result.getValue());
    }

    @Test
    void getPoolVotesTest() throws ApiException {
        String poolBech32 = "pool1x4p3cwemsm356vpxnjwuud7w76jz64hyss729zp7xa6wuey6yr9";
        Result<List<PoolVote>> result = governanceService.getPoolVotes(poolBech32);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertNotNull(result.getValue());
        System.out.println("Pool Votes: " + result.getValue());
    }
}
