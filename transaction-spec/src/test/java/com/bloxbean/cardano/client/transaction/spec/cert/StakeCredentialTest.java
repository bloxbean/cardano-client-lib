package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StakeCredentialTest {

    @Test
    public void fromKey_verificationKey() throws Exception {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");

        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromKey(verificationKey));
        assertThat(stakeRegistration.getCborHex()).isEqualTo("82008200581c75eceaf1013f030e6e03ea6cfa1ebca88ffb60b3e6fe7bb8325af363");
    }

    @Test
    public void fromKey_bytes() throws Exception {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");

        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromKey(verificationKey.getBytes()));
        assertThat(stakeRegistration.getCborHex()).isEqualTo("82008200581c75eceaf1013f030e6e03ea6cfa1ebca88ffb60b3e6fe7bb8325af363");
    }

    @Test
    void fromKeyHash() throws Exception {
        VerificationKey verificationKey = new VerificationKey("582054d685d1c4bcf38eceb69b8c8af28653e0ec92fd6c0c6a40af384960f494b036");

        System.out.println(HexUtil.encodeHexString(Blake2bUtil.blake2bHash224(verificationKey.getBytes())));
        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromKeyHash(Blake2bUtil.blake2bHash224(verificationKey.getBytes())));

        assertThat(stakeRegistration.getCborHex()).isEqualTo("82008200581c75eceaf1013f030e6e03ea6cfa1ebca88ffb60b3e6fe7bb8325af363");
    }

    @Test
    void fromScript() throws Exception {
        ScriptPubkey scriptPubkey = new ScriptPubkey("d6af304dd3eb1cdd0a26d3d3a07de6b0ac13fce999e1856242889e6a");
        StakeCredential stakeCredential = StakeCredential.fromScript(scriptPubkey);

        StakeRegistration stakeRegistration = new StakeRegistration(stakeCredential);
        assertThat(stakeRegistration.getCborHex()).isEqualTo("82008201581c6de614f5aeb3f0b23671ac2dcb526e2c7da7435e6743d153ce364069");
    }

    @Test
    void fromScriptHash() throws Exception {
        StakeCredential stakeCredential = StakeCredential.fromScriptHash(HexUtil.decodeHexString("6de614f5aeb3f0b23671ac2dcb526e2c7da7435e6743d153ce364069"));

        StakeRegistration stakeRegistration = new StakeRegistration(stakeCredential);
        assertThat(stakeRegistration.getCborHex()).isEqualTo("82008201581c6de614f5aeb3f0b23671ac2dcb526e2c7da7435e6743d153ce364069");
    }

}
