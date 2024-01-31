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

    @Test
    void fromKeyHash() {
        String drepId = DRepId.fromKeyHash("74984fae4ca1715fa1f8759f9d871015ac87f449a85dea6cf9956da1");
        assertThat(drepId).isEqualTo("drep1wjvyltjv59c4lg0cwk0empcszkkg0azf4pw75m8ej4k6zuqfvt5");
    }

    @Test
    void fromScriptHash() {
        String drepId = DRepId.fromScriptHash("41868c2b4e5289022a3a1f6f47f86823bc605c609d2c47a2db58e04a");
        assertThat(drepId).isEqualTo("drep_script1gxrgc26w22ysy236rah507rgyw7xqhrqn5ky0gkmtrsy553rrq7");
    }

    //TODO: Implement this
    @Test
    void toDrep() {

    }
}
