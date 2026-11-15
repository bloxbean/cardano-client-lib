package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.NoConfidence;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.ParameterChangeAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.UpdateCommittee;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0003 M5: what the mappers of governance and of parameter updates keep, read from the reader's views. */
class GovernanceMappingTest {
    private static final String MIN = "0080" + "0180" + "0200";
    private static final String H28 = "581c" + "11".repeat(28);
    private static final String RA = "581d" + "e1" + "33".repeat(28);
    private static final String ANCHOR = "82" + "6161" + "5820" + "22".repeat(32);
    private static final String ACTION_ID = "82" + "5820" + "44".repeat(32) + "01";

    private static byte[] hex(String hex) {
        return HexUtil.decodeHexString(hex);
    }

    private static TransactionBody body(String fields, int count) throws Exception {
        String body = String.format("a%x", 3 + count) + MIN + fields;
        return Transaction.deserialize(hex("84" + body + "a0" + "f5" + "f6")).getBody();
    }

    @Test
    void keySeventeenIsTheCoinsPerByteWhateverTheEra() throws Exception {
        // an Alonzo proposal's coins per UTxO word: the model has one field for key 17, and keeps it there
        TransactionBody body = body("06" + "82" + "a1" + H28 + "a1" + "11" + "1a0000868d" + "1901f4", 1);
        ProtocolParamUpdate update = body.getUpdate().getProtocolParamUpdates().get("11".repeat(28));
        assertThat(update.getAdaPerUtxoByte()).isEqualTo(34_445);
        assertThat(body.getUpdate().getEpoch()).isEqualTo(500);
    }

    @Test
    void anUpdateBeforeConwayKeepsItsRemovedFields() throws Exception {
        String update = "a4" + "0c" + "d81e820102" + "0d" + "8201" + "5820" + "55".repeat(32) + "0e" + "820500"
                + "0f" + "1a000f4240";
        ProtocolParamUpdate read = ProtocolParamUpdate.deserialize(CborSerializationUtil.deserialize(hex(update)));
        assertThat(read.getDecentralisationConstant().getDenominator()).isEqualTo(2);
        assertThat(read.getExtraEntropy().getHash()).hasSize(32);
        assertThat(read.getProtocolVersion()).isEqualTo(new ProtocolVersion(5, 0));
        assertThat(read.getMinUtxo()).isEqualTo(1_000_000);
        assertThat(CborSerializationUtil.serialize(read.serialize())).isEqualTo(hex(update));
    }

    @Test
    void costModelsInTheLanguageViewsFormKeepTheirLanguage() throws Exception {
        CostMdls costs = CostMdls.deserialize(CborSerializationUtil.deserialize(hex("a2" + "4100" + "44" + "9f0102ff"
                + "01" + "8103")));
        assertThat(costs.get(Language.PLUTUS_V1).getCosts()).containsExactly(1, 2);
        assertThat(costs.get(Language.PLUTUS_V2).getCosts()).containsExactly(3);
    }

    @Test
    void anActionModelReadsItsItemsWhateverTheTag() {
        // as the models always have: a kind's own decoder does not look at the tag
        Array array = (Array) CborSerializationUtil.deserialize(hex("82" + "07" + ACTION_ID));
        NoConfidence noConfidence = (NoConfidence) NoConfidence.deserialize(array);
        assertThat(noConfidence.getPrevGovActionId().getGovActionIndex()).isEqualTo(1);
        assertThatThrownBy(() -> GovAction.deserialize(array)).isInstanceOf(CborRuntimeException.class);

        Array change = (Array) CborSerializationUtil.deserialize(hex("85" + "00" + "f6" + "a0" + H28 + "00"));
        assertThat(((ParameterChangeAction) GovAction.deserialize(change)).getPolicyHash()).hasSize(28);
    }

    @Test
    void aRepeatedVoterOrActionKeepsItsPlaceAndTakesTheLaterVote() throws Exception {
        String voter = "8202" + H28;
        String other = "8203" + H28;
        String procedures = "a3" + voter + "a1" + ACTION_ID + "8200f6" + other + "a0" + voter + "a2" + ACTION_ID
                + "8201f6" + ACTION_ID + "8202f6";
        VotingProcedures read = body("13" + procedures, 1).getVotingProcedures();
        List<Map.Entry<com.bloxbean.cardano.client.transaction.spec.governance.Voter, Map<GovActionId,
                VotingProcedure>>> voters = List.copyOf(read.getVoting().entrySet());
        assertThat(voters).hasSize(2);
        assertThat(voters.get(0).getKey().getCredential()).isEqualTo(Credential.fromKey("11".repeat(28)));
        assertThat(voters.get(0).getValue().values()).singleElement()
                .satisfies(procedure -> assertThat(procedure.getVote()).isEqualTo(Vote.ABSTAIN));
        assertThat(voters.get(1).getValue()).isEmpty();
        assertThat(VotingProcedures.deserialize(CborSerializationUtil.deserialize(hex(procedures)))).isEqualTo(read);
    }

    @Test
    void proposalsAreReadAsTheModelsAlwaysHave() throws Exception {
        String proposal = "85" + "6161" + RA + "8106" + ANCHOR + "00";
        List<ProposalProcedure> proposals = body("14" + "82" + proposal + proposal, 1).getProposalProcedures();
        assertThat(proposals).singleElement().satisfies(read -> {
            assertThat(read.getDeposit()).isNull();
            assertThat(read.getAnchor().getAnchorUrl()).isEqualTo("a");
        });
        assertThat(body("14" + "80", 1).getProposalProcedures()).isNull();
    }

    @Test
    void aCommitteeUpdateKeepsTheLaterTermOfARepeatedMember() {
        String credential = "8200" + H28;
        Array array = (Array) CborSerializationUtil.deserialize(hex("85" + "04" + "f6" + "82" + credential + credential
                + "a2" + credential + "01" + "9f00" + H28 + "ff" + "02" + "d81e820102"));
        UpdateCommittee committee = (UpdateCommittee) GovAction.deserialize(array);
        assertThat(committee.getMembersForRemoval()).hasSize(1);
        assertThat(committee.getNewMembersAndTerms()).containsExactly(
                Map.entry(Credential.fromKey("11".repeat(28)), 2L));
    }

    @Test
    void theTreasuryValueAndTheDonationKeepTheirModelsTypes() throws Exception {
        TransactionBody body = body("15" + "20" + "16" + "05", 2);
        assertThat(body.getCurrentTreasuryValue()).isEqualTo(BigInteger.ONE.negate());
        assertThat(body.getDonation()).isEqualTo(5);
        assertThatThrownBy(() -> body("15" + "c24101", 1)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> body("16" + "20", 1)).isInstanceOf(Exception.class);
    }
}
