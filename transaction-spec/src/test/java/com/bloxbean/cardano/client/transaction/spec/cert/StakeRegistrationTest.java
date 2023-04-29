package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StakeRegistrationTest {

    @Test
    void stakeRegistration() throws Exception {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");

        StakeCredential stakeCredential = StakeCredential.fromKey(verificationKey.getBytes());
        StakeRegistration stakeDeregistration = new StakeRegistration(stakeCredential);

        assertThat(stakeDeregistration.getCborHex()).isEqualTo("82008200581c75eceaf1013f030e6e03ea6cfa1ebca88ffb60b3e6fe7bb8325af363");
    }

    @Test
    void testSerializeDeserialize() throws Exception {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");

        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromKey(verificationKey.getBytes()));
        byte[] serBytes = CborSerializationUtil.serialize(stakeRegistration.serialize());

        //deserialize
        List<DataItem> dataItemList = CborDecoder.decode(serBytes);
        Array certArray = (Array) dataItemList.get(0);
        StakeRegistration deSerStakeRegistration = (StakeRegistration) Certificate.deserialize(certArray);

        assertThat(deSerStakeRegistration.getType()).isEqualTo(CertificateType.STAKE_REGISTRATION);
        assertThat(deSerStakeRegistration.getStakeCredential()).isEqualTo(stakeRegistration.getStakeCredential());
    }

}
