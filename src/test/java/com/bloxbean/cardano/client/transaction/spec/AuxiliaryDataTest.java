package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class AuxiliaryDataTest {

    String dataFile = "json-metadata.json";

    @Test
    public void getAuxiliaryDataHash_whenMetadataIsSet() throws IOException {
        JsonNode json = loadJsonMetadata("json-4");
        Metadata metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json.toString());

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        byte[] auxHashBytes = auxiliaryData.getAuxiliaryDataHash();
        String auxHash = HexUtil.encodeHexString(auxHashBytes);

        assertThat(auxHash).isEqualTo("79906018ae50cbed76c2f89382f504036ff43b935ee8488eb51884f9fd4f13cd");
    }

    @Test
    public void getAuxiliaryDataHash_whenPlutusScript() {
        PlutusScript plutusScript = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .plutusScripts(Arrays.asList(plutusScript))
                .build();

        byte[] auxHashBytes = auxiliaryData.getAuxiliaryDataHash();
        String auxHash = HexUtil.encodeHexString(auxHashBytes);

        assertThat(auxHash).isEqualTo("bcd5c19833c5b47b0d5684e4e9a7feb1d2b0951eecd03915e1082eab2d028888");
    }

    @Test
    public void getAuxiliaryDataHash_whenPlutusScriptAndMetadata() throws CborSerializationException, CborException {
        PlutusScript plutusScript = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        CBORMetadata cborMetadata = new CBORMetadata();
        CBORMetadataMap metadataMap = new CBORMetadataMap();
        CBORMetadataList metadataList = new CBORMetadataList();
        metadataList.add("First contract call from cardano-client-lib : A client library for Cardano");
        metadataMap.put("msg", metadataList);
        cborMetadata.put(new BigInteger("674"), metadataList);

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(cborMetadata)
                .plutusScripts(Arrays.asList(plutusScript))
                .build();

        System.out.println(HexUtil.encodeHexString(CborSerializationUtil.serialize(auxiliaryData.serialize())));
        byte[] auxHashBytes = auxiliaryData.getAuxiliaryDataHash();
        String auxHash = HexUtil.encodeHexString(auxHashBytes);

        assertThat(auxHash).isEqualTo("f9c8cfaf79eea2b4ba97e87e295a527a0586567f7e036f5e5bee877a481d3c5c");
    }

    @Test
    public void serializeDeserialize() throws CborSerializationException, CborDeserializationException {
        CBORMetadata metadata = new CBORMetadata();
        CBORMetadataMap map = new CBORMetadataMap();
        map.put("key1", "value1");
        map.put(BigInteger.valueOf(1001), "bigValue");
        map.put(new byte[] {1,2}, "byteValue");

        CBORMetadataList list = new CBORMetadataList();
        list.add("listValue1");
        list.add(BigInteger.valueOf(2));

        metadata.put(BigInteger.valueOf(11), map);
        metadata.put(BigInteger.valueOf(22), list);

        //Native script
        ScriptPubkey scriptPubkey1 = ScriptPubkey.createWithNewKey()._1;
        ScriptPubkey scriptPubkey2 = ScriptPubkey.createWithNewKey()._1;

        PlutusScript plutusScript1 = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();

        PlutusScript plutusScript2 = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        AuxiliaryData auxData = AuxiliaryData.builder()
                .metadata(metadata)
                .nativeScripts(Arrays.asList(scriptPubkey1, scriptPubkey2))
                .plutusScripts(Arrays.asList(plutusScript1, plutusScript2))
                .build();

        DataItem dataItem = auxData.serialize();

        AuxiliaryData deAuxData = AuxiliaryData.deserialize((Map)dataItem);
        CBORMetadata deMetadata = (CBORMetadata) deAuxData.getMetadata();
        Map deMap = (Map) deMetadata.getData().get(new UnsignedInteger(11));
        Array deArray = (Array) deMetadata.getData().get(new UnsignedInteger(22));

        //asserts
        assertThat(deAuxData.getNativeScripts()).hasSize(2);
        assertThat(deAuxData.getPlutusScripts()).hasSize(2);

        assertThat(deMap.get(new UnicodeString("key1"))).isEqualTo(new UnicodeString("value1"));

        assertThat(deAuxData.getNativeScripts()).containsExactlyElementsOf(Arrays.asList(scriptPubkey1, scriptPubkey2));
        assertThat(deAuxData.getPlutusScripts()).containsExactlyElementsOf(Arrays.asList(plutusScript1, plutusScript2));
    }

    @Test
    public void serializeDeserialize_noAuxData() throws CborSerializationException, CborDeserializationException {
        CBORMetadata metadata = new CBORMetadata();
        CBORMetadataMap map = new CBORMetadataMap();
        map.put("key1", "value1");
        map.put(BigInteger.valueOf(1001), "bigValue");
        map.put(new byte[] {1,2}, "byteValue");

        CBORMetadataList list = new CBORMetadataList();
        list.add("listValue1");
        list.add(BigInteger.valueOf(2));

        metadata.put(BigInteger.valueOf(11), map);
        metadata.put(BigInteger.valueOf(22), list);

        //Native script
        ScriptPubkey scriptPubkey1 = ScriptPubkey.createWithNewKey()._1;
        ScriptPubkey scriptPubkey2 = ScriptPubkey.createWithNewKey()._1;

        PlutusScript plutusScript1 = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();

        PlutusScript plutusScript2 = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        AuxiliaryData auxData = AuxiliaryData.builder()
                .metadata(metadata)
                .nativeScripts(Arrays.asList(scriptPubkey1, scriptPubkey2))
                .plutusScripts(Arrays.asList(plutusScript1, plutusScript2))
                .build();

        DataItem dataItem = auxData.serialize();

        AuxiliaryData deAuxData = AuxiliaryData.deserialize((Map)dataItem);
        CBORMetadata deMetadata = (CBORMetadata) deAuxData.getMetadata();
        Map deMap = (Map) deMetadata.getData().get(new UnsignedInteger(11));
        Array deArray = (Array) deMetadata.getData().get(new UnsignedInteger(22));

        //asserts
        assertThat(deAuxData.getNativeScripts()).hasSize(2);
        assertThat(deAuxData.getPlutusScripts()).hasSize(2);

        assertThat(deMap.get(new UnicodeString("key1"))).isEqualTo(new UnicodeString("value1"));

        assertThat(deAuxData.getNativeScripts()).containsExactlyElementsOf(Arrays.asList(scriptPubkey1, scriptPubkey2));
        assertThat(deAuxData.getPlutusScripts()).containsExactlyElementsOf(Arrays.asList(plutusScript1, plutusScript2));
    }

    private JsonNode loadJsonMetadata(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(this.getClass().getClassLoader().getResourceAsStream(dataFile));
        ObjectNode root = (ObjectNode) rootNode;

        return root.get(key);
    }
}
