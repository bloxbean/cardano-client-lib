package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.crypto.VerificationKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StakeDeregistrationTest {

    @Test
    void from() throws Exception {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");

        StakeCredential stakeCredential = StakeCredential.fromKey(verificationKey.getBytes());
        StakeDeregistration stakeDeregistration = new StakeDeregistration(stakeCredential);

        assertThat(stakeDeregistration.getCborHex()).isEqualTo("82018200581c75eceaf1013f030e6e03ea6cfa1ebca88ffb60b3e6fe7bb8325af363");
    }

}
