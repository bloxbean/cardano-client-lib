package com.bloxbean.cardano.client.quicktx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.NoConfidence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GovIntentionsInScriptTxTest {

    @Test
    void register_drep_with_redeemer_serializes_and_round_trips() {
        Credential cred = Credential.fromKey(new byte[]{1,2,3});
        Anchor anchor = new Anchor("https://example.org", null);
        PlutusData redeemer = BigIntPlutusData.of(1);

        ScriptTx tx = new ScriptTx().registerDRep(cred, anchor, redeemer);
        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: drep_registration");
        assertThat(yaml).contains("redeemer_hex:");

        ScriptTx restored = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
        assertThat(restored.getIntentions().stream().anyMatch(i -> "drep_registration".equals(i.getType()))).isTrue();
    }

    @Test
    void unregister_drep_with_redeemer_serializes_and_round_trips() {
        Credential cred = Credential.fromKey(new byte[]{9,9,9});
        PlutusData redeemer = BigIntPlutusData.of(2);

        ScriptTx tx = new ScriptTx().unRegisterDRep(cred, "addr_test1refund", null, redeemer);
        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: drep_deregistration");
        assertThat(yaml).contains("redeemer_hex:");

        ScriptTx restored = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
        assertThat(restored.getIntentions().stream().anyMatch(i -> "drep_deregistration".equals(i.getType()))).isTrue();
    }

    @Test
    void governance_proposal_with_redeemer_serializes_and_round_trips() {
        GovAction action = new NoConfidence();
        PlutusData redeemer = BigIntPlutusData.of(3);
        ScriptTx tx = new ScriptTx().createProposal(action, "stake_test1return", null, redeemer);
        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: governance_proposal");
        assertThat(yaml).contains("redeemer_hex:");
        ScriptTx restored = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
        assertThat(restored.getIntentions().stream().anyMatch(i -> "governance_proposal".equals(i.getType()))).isTrue();
    }

//    @Test
//    void voting_with_redeemer_serializes_and_round_trips() {
//        Voter voter = Voter.drepId("drep_test_hash");
//        GovActionId gaid = new GovActionId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0);
//        PlutusData redeemer = BigIntPlutusData.of(4);
//        ScriptTx tx = new ScriptTx().createVote(voter, gaid, Vote.YES, null, redeemer);
//        String yaml = TxPlan.fromTransaction(tx).toYaml();
//        assertThat(yaml).contains("type: voting");
//        assertThat(yaml).contains("redeemer_hex:");
//        ScriptTx restored = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
//        assertThat(restored.getIntentions().stream().anyMatch(i -> "voting".equals(i.getType()))).isTrue();
//    }
}
