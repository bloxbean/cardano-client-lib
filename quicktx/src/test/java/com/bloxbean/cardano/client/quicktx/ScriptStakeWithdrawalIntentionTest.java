package com.bloxbean.cardano.client.quicktx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptStakeWithdrawalIntentionTest {

    @Test
    void withdraw_with_redeemer_serializes_and_round_trips() {
        String rewardAddr = "stake_test1qpwithdraw";
        BigInteger amount = BigInteger.valueOf(1234567);
        PlutusData redeemer = BigIntPlutusData.of(9);

        ScriptTx tx = new ScriptTx()
                .withdraw(rewardAddr, amount, redeemer);

        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: stake_withdrawal");
        assertThat(yaml).contains("reward_address: " + rewardAddr);
        assertThat(yaml).contains("redeemer_hex:");

        ScriptTx restored = (ScriptTx) TxPlan.getTxs(yaml).get(0);
        assertThat(restored.getIntentions()).isNotEmpty();
        assertThat(restored.getIntentions().stream().anyMatch(i -> "stake_withdrawal".equals(i.getType()))).isTrue();
    }

    @Test
    void withdraw_with_receiver_and_redeemer_serializes_and_round_trips() {
        String rewardAddr = "stake_test1qpwithdraw2";
        BigInteger amount = BigInteger.valueOf(1000);
        String receiver = "addr_test1qpreceiver";
        PlutusData redeemer = BigIntPlutusData.of(19);

        ScriptTx tx = new ScriptTx()
                .withdraw(rewardAddr, amount, redeemer, receiver);

        String yaml = TxPlan.from(tx).toYaml();
        assertThat(yaml).contains("type: stake_withdrawal");
        assertThat(yaml).contains("reward_address: " + rewardAddr);
        assertThat(yaml).contains("receiver: " + receiver);
        assertThat(yaml).contains("redeemer_hex:");

        ScriptTx restored = (ScriptTx) TxPlan.getTxs(yaml).get(0);
        assertThat(restored.getIntentions()).isNotEmpty();
        assertThat(restored.getIntentions().stream().anyMatch(i -> "stake_withdrawal".equals(i.getType()))).isTrue();
    }
}

