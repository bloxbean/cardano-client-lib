package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyUtilTest {
    private ObjectMapper objectMapper = new ObjectMapper();

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
