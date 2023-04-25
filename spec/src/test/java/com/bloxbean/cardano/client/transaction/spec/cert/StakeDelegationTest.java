package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StakeDelegationTest {

    @Test
    void serialize_hexPoolId() throws CborSerializationException {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");
        StakeCredential stakeCredential = StakeCredential.fromKey(verificationKey.getBytes());

        StakePoolId stakePoolId = StakePoolId.fromHexPoolId("3921f4441153e5936910de57cb1982dfbaa781a57ba1ff97b3fd869e");

        StakeDelegation stakeDelegation = new StakeDelegation(stakeCredential, stakePoolId);
        String cborHex = stakeDelegation.getCborHex();

        assertThat(cborHex).isEqualTo("83028200581c75eceaf1013f030e6e03ea6cfa1ebca88ffb60b3e6fe7bb8325af363581c3921f4441153e5936910de57cb1982dfbaa781a57ba1ff97b3fd869e");
    }

    @Test
    void serialize_bech32PoolId() throws CborSerializationException {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");
        StakeCredential stakeCredential = StakeCredential.fromKey(verificationKey.getBytes());

        StakePoolId stakePoolId = StakePoolId.fromBech32PoolId("pool18yslg3q320jex6gsmetukxvzm7a20qd90wsll9anlkrfua38flr");

        StakeDelegation stakeDelegation = new StakeDelegation(stakeCredential, stakePoolId);
        String cborHex = stakeDelegation.getCborHex();

        assertThat(cborHex).isEqualTo("83028200581c75eceaf1013f030e6e03ea6cfa1ebca88ffb60b3e6fe7bb8325af363581c3921f4441153e5936910de57cb1982dfbaa781a57ba1ff97b3fd869e");
    }
}
