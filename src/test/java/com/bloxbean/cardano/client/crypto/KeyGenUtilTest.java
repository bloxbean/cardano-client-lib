package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KeyGenUtilTest {

    @Test
    public void testGenerateKey() throws CborSerializationException {
        Keys keys = KeyGenUtil.generateKey();

        SecretKey skey = keys.getSkey();
        VerificationKey vkey = keys.getVkey();

        System.out.println(JsonUtil.getPrettyJson(skey));
        System.out.println(JsonUtil.getPrettyJson(vkey));

        assertThat(skey.getCborHex(), Matchers.notNullValue());
        assertThat(vkey.getCborHex(), Matchers.notNullValue());
        assertEquals(skey.getBytes().length, 32);
        assertEquals(vkey.getBytes().length, 32);
    }

    @Test
    public void testPublicKeyFromPrivateKey() throws CborSerializationException {
        String cborText = "582037abc7f86d3f53cbedd38835cd4dbcf0ff7d2bf5a5c12ec77c6851bf5295ae63";
        VerificationKey vkey = KeyGenUtil.getPublicKeyFromPrivateKey(new SecretKey(cborText));

        System.out.println(JsonUtil.getPrettyJson(vkey));

        assertThat(vkey.getCborHex(), Matchers.notNullValue());
        assertThat(vkey.getCborHex(), is("5820b7f0336ad5e86f92eb7872ea0e589aab388f1405c754949ab2082095fd8e2dfc"));
        assertEquals(vkey.getBytes().length, 32);
    }

    @Test
    public void testGetKeyHash() throws CborSerializationException {
        String cborText = "582037abc7f86d3f53cbedd38835cd4dbcf0ff7d2bf5a5c12ec77c6851bf5295ae63";
        VerificationKey vkey = KeyGenUtil.getPublicKeyFromPrivateKey(new SecretKey(cborText));

        System.out.println(JsonUtil.getPrettyJson(vkey));
        String keyHash = KeyGenUtil.getKeyHash(vkey);

        assertThat(keyHash, is("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884"));
    }

    @Test
    public void testGetScriptPubkey() throws CborSerializationException {
        String cborText = "582037abc7f86d3f53cbedd38835cd4dbcf0ff7d2bf5a5c12ec77c6851bf5295ae63";
        VerificationKey vkey = KeyGenUtil.getPublicKeyFromPrivateKey(new SecretKey(cborText));

        System.out.println(JsonUtil.getPrettyJson(vkey));
        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);

        System.out.println(JsonUtil.getPrettyJson(scriptPubkey));
        assertThat(scriptPubkey.getKeyHash(), is("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884"));
    }

    @Test
    public void testKeysSerializationDeserialization() throws CborSerializationException, JsonProcessingException {
        Tuple<ScriptPubkey,Keys> tuple = ScriptPubkey.createWithNewKey();
        Keys keys1 = tuple._2;

        ObjectMapper objectMapper = new ObjectMapper();
        String keysJson = objectMapper.writeValueAsString(keys1);

        Keys keys2 = objectMapper.readValue(keysJson,Keys.class);

        assertEquals(keys1,keys2);
    }
}
