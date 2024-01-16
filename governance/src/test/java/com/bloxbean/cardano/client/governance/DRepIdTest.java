package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DRepIdTest {

    @Test
    void fromVerificationKey() {
        VerificationKey verificationKey = new VerificationKey("5820fbed0bbb5ec02297af05bc76979ef3228787d3db095092f67d867f4d5ce79944");
        String drepId = DRepId.fromVerificationKey(verificationKey);
        assertThat(drepId).isEqualTo("drep1vlmeg4vu96detgkf5srw427363svqemth2xde2e2xp5ywfsx2gn");
    }

    @Test
    @SneakyThrows
    void fromVerificationKeyBytes() {
        byte[] vkBytes = HexUtil.decodeHexString("fbed0bbb5ec02297af05bc76979ef3228787d3db095092f67d867f4d5ce79944");
        VerificationKey verificationKey = VerificationKey.create(vkBytes);
        String drepId = DRepId.fromVerificationKey(verificationKey);
        assertThat(drepId).isEqualTo("drep1vlmeg4vu96detgkf5srw427363svqemth2xde2e2xp5ywfsx2gn");
    }

    //TODO: Implement this
    @Test
    void toDrep() {

    }
}
