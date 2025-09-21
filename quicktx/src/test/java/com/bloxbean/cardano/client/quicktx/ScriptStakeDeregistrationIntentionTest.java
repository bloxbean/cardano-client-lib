package com.bloxbean.cardano.client.quicktx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptStakeDeregistrationIntentionTest {

    @Test
    void deregister_stake_with_redeemer_serializes_and_round_trips() {
        String stakeAddr = "stake_test1qxyzabc"; // simple placeholder
        PlutusData redeemer = BigIntPlutusData.of(5);

        ScriptTx tx = new ScriptTx()
                .deregisterStakeAddress(stakeAddr, redeemer);

        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: stake_deregistration");
        assertThat(yaml).contains("stake_address: " + stakeAddr);
        assertThat(yaml).contains("redeemer_hex:");

        ScriptTx restored = (ScriptTx) TxPlan.getTxs(yaml).get(0);
        assertThat(restored.getIntentions()).isNotEmpty();
        assertThat(restored.getIntentions().stream().anyMatch(i -> "stake_deregistration".equals(i.getType()))).isTrue();
    }
}

