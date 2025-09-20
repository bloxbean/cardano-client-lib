package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptVotingDelegationIntentionTest {

    @Test
    void voting_delegation_with_redeemer_serializes_and_round_trips() {
        Address addr = new Address(new Account().baseAddress());
        DRep drep = DRep.noConfidence();
        PlutusData redeemer = BigIntPlutusData.of(21);

        ScriptTx tx = new ScriptTx()
                .delegateVotingPowerTo(addr, drep, redeemer);

        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: voting_delegation");
        assertThat(yaml).contains("redeemer_hex:");

        ScriptTx restored = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
        assertThat(restored.getIntentions()).isNotEmpty();
        assertThat(restored.getIntentions().stream().anyMatch(i -> "voting_delegation".equals(i.getType()))).isTrue();
    }
}
