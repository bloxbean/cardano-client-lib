package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.crypto.VerificationKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StakePoolIdTest {

    @Test
    void fromOperatorVKey() {
        String vkeyCborHex = "582089ca49631d52a071630602eb7c37d97b49092d4ca9e830bdb39ec9dee13b58ea";
        VerificationKey operatorVKey = new VerificationKey(vkeyCborHex); //cold vkey
        StakePoolId stakePoolId = StakePoolId.fromColdVKey(operatorVKey);
        String bech32PoolId = stakePoolId.getBech32PoolId();

        assertThat(bech32PoolId).isEqualTo("pool1upg9ukst2jsw8katgzhfu4vth7v78n4htq2kgd7sjn0ryhsf2yl");
    }

    @Test
    void fromOperatorVKey_2() {
        String vkeyCborHex = "5820abc3a3aa28c0e50ee11626ec8c5e5cceb5534e31b03cf407b4a331ba9ea39f9b";
        VerificationKey operatorVKey = new VerificationKey(vkeyCborHex); //cold vkey
        StakePoolId stakePoolId = StakePoolId.fromColdVKey(operatorVKey);
        String bech32PoolId = stakePoolId.getBech32PoolId();

        assertThat(bech32PoolId).isEqualTo("pool15670hh8xa4pftdxgc5swrf068g7jd60g4qsuv9y4pk7kzu3vsya");
    }

    @Test
    void bech32IdFromHexId() {
        String hexId = "8ebb7e21acee66c3e5af6c765ea6ad31962e7b9160520aecd6f001a3";
        StakePoolId stakePoolId = StakePoolId.fromHexPoolId(hexId);
        assertThat(stakePoolId.getBech32PoolId()).isEqualTo("pool136ahugdvaenv8ed0d3m9af4dxxtzu7u3vpfq4mxk7qq6xuvd0ca");
    }
}
