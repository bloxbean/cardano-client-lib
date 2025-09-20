package com.bloxbean.cardano.client.quicktx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptStakeDelegationIntentionTest {

    @Test
    void delegate_to_with_redeemer_serializes_and_round_trips() {
        String stakeAddr = "stake_test1qrl4fvyfake";
        String poolId = "pool1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq";
        PlutusData redeemer = BigIntPlutusData.of(7);

        ScriptTx tx = new ScriptTx()
                .delegateTo(stakeAddr, poolId, redeemer);

        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: stake_delegation");
        assertThat(yaml).contains("stake_address: " + stakeAddr);
        assertThat(yaml).contains("pool_id: " + poolId);
        assertThat(yaml).contains("redeemer_hex:");

        ScriptTx restored = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
        assertThat(restored.getIntentions()).isNotEmpty();
        assertThat(restored.getIntentions().stream().anyMatch(i -> "stake_delegation".equals(i.getType()))).isTrue();
    }
}

