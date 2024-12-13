package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class AuxiliaryDataTest {

    String dataFile = "json-metadata.json";

    @Test
    public void getAuxiliaryDataHash_checkKeysOrder_whenMetadataIsSet() throws Exception {
        JsonNode json = loadJsonMetadata("json-1");
        Metadata metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json.toString());

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        byte[] auxHashBytes = auxiliaryData.getAuxiliaryDataHash();
        String auxHash = HexUtil.encodeHexString(auxHashBytes);
        String serializedHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(auxiliaryData.serialize()));

        Map metadataMap =  (Map)CborSerializationUtil.deserialize(HexUtil.decodeHexString(serializedHex));
        Collection<DataItem> metadataLabels = metadataMap.getKeys();

        assertThat(metadataLabels).containsExactly(new UnsignedInteger(945845007538436815L), new UnsignedInteger(1302243434517352162L), new UnsignedInteger(1351859328329939190L),
                new UnsignedInteger(7274669146951118819L), new UnsignedInteger(7505166164059511819L), new UnsignedInteger(new BigInteger("17802948329108123211")));
        assertThat(auxHash).isEqualTo("47a7d2a804b63b12818b1fc4bf710a966d20aa25105f315f92af673dfec435db");
    }

    @Test
    public void getAuxiliaryDataHash_whenPlutusV1Script() {
        PlutusV1Script plutusScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .plutusV1Scripts(Arrays.asList(plutusScript))
                .build();

        byte[] auxHashBytes = auxiliaryData.getAuxiliaryDataHash();
        String auxHash = HexUtil.encodeHexString(auxHashBytes);

        assertThat(auxHash).isEqualTo("7f0a2910e344e22e60ee9fa985820e9b5136977c15ddd6a38d134b1dc4f3e150");
    }

    @Test
    public void getAuxiliaryDataHash_whenPlutusV2Script() {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .plutusV2Scripts(Arrays.asList(plutusScript))
                .build();

        byte[] auxHashBytes = auxiliaryData.getAuxiliaryDataHash();
        String auxHash = HexUtil.encodeHexString(auxHashBytes);

        assertThat(auxHash).isEqualTo("bbe94949bec1152ab70f5d4689b9c6242fd4591dbc32ca84ad7140246128263e");
    }


    @Test
    public void getAuxiliaryDataHash_whenPlutusScriptAndMetadata() throws CborSerializationException, CborException {
        PlutusV1Script plutusScript = PlutusV1Script.builder()
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
                .plutusV1Scripts(Arrays.asList(plutusScript))
                .build();

        System.out.println(HexUtil.encodeHexString(CborSerializationUtil.serialize(auxiliaryData.serialize())));
        byte[] auxHashBytes = auxiliaryData.getAuxiliaryDataHash();
        String auxHash = HexUtil.encodeHexString(auxHashBytes);

        assertThat(auxHash).isEqualTo("9754d68a3d8bdc75150c415eba8a0b0623e10db17ee4a7a33883efc05f9cdc0b");
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

        PlutusV1Script plutusScript1 = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();

        PlutusV1Script plutusScript2 = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        AuxiliaryData auxData = AuxiliaryData.builder()
                .metadata(metadata)
                .nativeScripts(Arrays.asList(scriptPubkey1, scriptPubkey2))
                .plutusV1Scripts(Arrays.asList(plutusScript1, plutusScript2))
                .build();

        DataItem dataItem = auxData.serialize();

        AuxiliaryData deAuxData = AuxiliaryData.deserialize((Map)dataItem);
        CBORMetadata deMetadata = (CBORMetadata) deAuxData.getMetadata();
        Map deMap = (Map) deMetadata.getData().get(new UnsignedInteger(11));
        Array deArray = (Array) deMetadata.getData().get(new UnsignedInteger(22));

        //asserts
        assertThat(deAuxData.getNativeScripts()).hasSize(2);
        assertThat(deAuxData.getPlutusV1Scripts()).hasSize(2);

        assertThat(deMap.get(new UnicodeString("key1"))).isEqualTo(new UnicodeString("value1"));

        assertThat(deAuxData.getNativeScripts()).containsExactlyElementsOf(Arrays.asList(scriptPubkey1, scriptPubkey2));
        assertThat(deAuxData.getPlutusV1Scripts()).containsExactlyElementsOf(Arrays.asList(plutusScript1, plutusScript2));
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

        PlutusV1Script plutusScript1 = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();

        PlutusV1Script plutusScript2 = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        AuxiliaryData auxData = AuxiliaryData.builder()
                .metadata(metadata)
                .nativeScripts(Arrays.asList(scriptPubkey1, scriptPubkey2))
                .plutusV1Scripts(Arrays.asList(plutusScript1, plutusScript2))
                .build();

        DataItem dataItem = auxData.serialize();

        AuxiliaryData deAuxData = AuxiliaryData.deserialize((Map)dataItem);
        CBORMetadata deMetadata = (CBORMetadata) deAuxData.getMetadata();
        Map deMap = (Map) deMetadata.getData().get(new UnsignedInteger(11));
        Array deArray = (Array) deMetadata.getData().get(new UnsignedInteger(22));

        //asserts
        assertThat(deAuxData.getNativeScripts()).hasSize(2);
        assertThat(deAuxData.getPlutusV1Scripts()).hasSize(2);

        assertThat(deMap.get(new UnicodeString("key1"))).isEqualTo(new UnicodeString("value1"));

        assertThat(deAuxData.getNativeScripts()).containsExactlyElementsOf(Arrays.asList(scriptPubkey1, scriptPubkey2));
        assertThat(deAuxData.getPlutusV1Scripts()).containsExactlyElementsOf(Arrays.asList(plutusScript1, plutusScript2));
    }

    private JsonNode loadJsonMetadata(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(this.getClass().getClassLoader().getResourceAsStream(dataFile));
        ObjectNode root = (ObjectNode) rootNode;

        return root.get(key);
    }
}
