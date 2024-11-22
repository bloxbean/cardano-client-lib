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
        String drepId = DRepId.fromVerificationKeyBytes(vkBytes);
        assertThat(drepId).isEqualTo("drep1vlmeg4vu96detgkf5srw427363svqemth2xde2e2xp5ywfsx2gn");
    }

    @Test
    void fromKeyHash() {
        String drepId = DRepId.fromKeyHash("74984fae4ca1715fa1f8759f9d871015ac87f449a85dea6cf9956da1");
        assertThat(drepId).isEqualTo("drep1wjvyltjv59c4lg0cwk0empcszkkg0azf4pw75m8ej4k6zuqfvt5");
    }

    @Test
    void fromKeyHashBytes() {
        String drepId = DRepId.fromKeyHash(HexUtil.decodeHexString("74984fae4ca1715fa1f8759f9d871015ac87f449a85dea6cf9956da1"));
        assertThat(drepId).isEqualTo("drep1wjvyltjv59c4lg0cwk0empcszkkg0azf4pw75m8ej4k6zuqfvt5");
    }

    @Test
    void fromScriptHash() {
        String drepId = DRepId.fromScriptHash("41868c2b4e5289022a3a1f6f47f86823bc605c609d2c47a2db58e04a");
        assertThat(drepId).isEqualTo("drep_script1gxrgc26w22ysy236rah507rgyw7xqhrqn5ky0gkmtrsy553rrq7");
    }

    @Test
    void fromScriptHashBytes() {
        String drepId = DRepId.fromScriptHash(HexUtil.decodeHexString("41868c2b4e5289022a3a1f6f47f86823bc605c609d2c47a2db58e04a"));
        assertThat(drepId).isEqualTo("drep_script1gxrgc26w22ysy236rah507rgyw7xqhrqn5ky0gkmtrsy553rrq7");
    }

    @Test
    public void testDRepScriptId1() {
        String scriptHash = "d0657126dbf0c135a7224d91ca068f5bf769af6d1f1df0bce5170ec5";
        String expectedDRepId = "drep_script16pjhzfkm7rqntfezfkgu5p50t0mkntmdruwlp089zu8v29l95rg";

        String drepId = DRepId.fromScriptHash(scriptHash);
        assertThat(drepId).isEqualTo(expectedDRepId);
    }

    @Test
    public void testDRepScriptId2() {
        String scriptHash = "ae5acf0511255d647c84b3184a2d522bf5f6c5b76b989f49bd383bdd";
        String expectedDRepId = "drep_script14edv7pg3y4wkglyykvvy5t2j906ld3dhdwvf7jda8qaa63d5kf4";

        String drepId = DRepId.fromScriptHash(scriptHash);
        assertThat(drepId).isEqualTo(expectedDRepId);
    }

    //TODO: Implement this
    @Test
    void toDrep() {

    }
}
