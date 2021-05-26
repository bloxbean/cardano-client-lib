package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.backend.model.Utxo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseTest {

    protected ObjectMapper objectMapper = new ObjectMapper();
    protected String utxoJsonFile;
    protected String protocolParamJsonFile;

    protected List<Utxo> loadUtxos(String key) throws IOException {
        TypeReference<HashMap<String, List<Utxo>>> typeRef
                = new TypeReference<HashMap<String, List<Utxo>>>() {};
        Map<String, List<Utxo>> map = objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(utxoJsonFile), typeRef);
        return map.getOrDefault(key, Collections.emptyList());
    }

    protected Object loadObjectFromJson(String key, Class clazz) throws IOException {
        return objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(protocolParamJsonFile), clazz);
    }
}
