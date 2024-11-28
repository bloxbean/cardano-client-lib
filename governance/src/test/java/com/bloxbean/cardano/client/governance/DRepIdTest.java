package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.governance.cip105.DRepId;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DRepIdTest {

    @Test
    void fromVerificationKey() throws CborSerializationException {
        //https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/test-vector-2.md
        VerificationKey verificationKey = VerificationKey.create(HexUtil.decodeHexString("70344fe0329bbacbb33921e945daed181bd66889333eb73f3bb10ad8e4669976"));
        String drepId = DRepId.fromVerificationKey(verificationKey);
        assertThat(drepId).isEqualTo("drep_vkh1rmf3ftma8lu0e5eqculttpfy6a6v5wrn8msqa09gr0tr590rpdl");
    }

    @Test
    @SneakyThrows
    void fromVerificationKeyBytes() {
        //https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/test-vector-4.md
        byte[] vkBytes = HexUtil.decodeHexString("ab5d2187f2f4419421b0457f7ac8ab0d4b4ec0802af5de21dde64f603248a381");
        String drepId = DRepId.fromVerificationKeyBytes(vkBytes);
        assertThat(drepId).isEqualTo("drep_vkh1cx359uxlhq4e8j3wddqxht9sfqp004t2n8v0jk5q4zmv2chvj7w");
    }

    @Test
    void fromKeyHash() {
        //https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/test-vector-4.md
        String drepId = DRepId.fromKeyHash("c1a342f0dfb82b93ca2e6b406bacb04802f7d56a99d8f95a80a8b6c5");
        assertThat(drepId).isEqualTo("drep_vkh1cx359uxlhq4e8j3wddqxht9sfqp004t2n8v0jk5q4zmv2chvj7w");
    }

    @Test
    void fromKeyHashBytes() {
        String drepId = DRepId.fromKeyHash(HexUtil.decodeHexString("74984fae4ca1715fa1f8759f9d871015ac87f449a85dea6cf9956da1"));
        assertThat(drepId).isEqualTo("drep_vkh1wjvyltjv59c4lg0cwk0empcszkkg0azf4pw75m8ej4k6z68j3zw");
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
