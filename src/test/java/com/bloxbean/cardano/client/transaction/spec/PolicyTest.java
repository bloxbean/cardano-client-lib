package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.PolicyUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PolicyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void PolicyScriptPubKeySerializationTest() throws CborSerializationException, JsonProcessingException {
        Keys keys = KeyGenUtil.generateKey();
        VerificationKey vkey = keys.getVkey();
        SecretKey skey = keys.getSkey();
        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
        Policy policy1 = new Policy(scriptPubkey, Arrays.asList(skey));
        String jsonString = objectMapper.writeValueAsString(policy1);
        Policy policy2 = objectMapper.readValue(jsonString,Policy.class);
        assertEquals(policy1,policy2);
    }

    @Test
    void PolicyScriptAtLeastSerializationTest() throws CborSerializationException, JsonProcessingException {
        Policy policy1 = PolicyUtil.createMultiSigScriptAtLeastPolicy("PolicyScriptAtLeast2OutOf3",3,2);
        String jsonString = objectMapper.writeValueAsString(policy1);
        Policy policy2 = objectMapper.readValue(jsonString,Policy.class);
        assertEquals(policy1,policy2);
    }

    @Test
    void PolicyScriptAllSerializationTest() throws CborSerializationException, JsonProcessingException {
        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("MultiSigPolicy",3);
        String jsonString = objectMapper.writeValueAsString(policy1);
        Policy policy2 = objectMapper.readValue(jsonString,Policy.class);
        assertEquals(policy1,policy2);
    }
}
