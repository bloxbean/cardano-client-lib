package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Converter for transforming YAML strings to Metadata objects.
 * This class extends JsonNodeToMetadataConverter to reuse the JsonNode processing logic,
 * since YAML can be parsed into the same JsonNode structure as JSON.
 */
public class YamlNoSchemaToMetadataConverter extends JsonNodeToMetadataConverter {
    
    private static final YAMLMapper yamlMapper = new YAMLMapper();

    /**
     * Convert a valid YAML string to Metadata
     * @param yaml YAML string to convert
     * @return Metadata object
     * @throws JsonProcessingException if YAML parsing fails
     */
    public static Metadata yamlToCborMetadata(String yaml) throws JsonProcessingException {
        JsonNode jsonNode = yamlMapper.readTree(yaml);
        return parseJsonNode(jsonNode);
    }

    /**
     * Creates a Metadata object from a YAML string body with a specific label
     * @param label the metadata label
     * @param yamlBody the YAML content
     * @return Metadata object
     * @throws JsonProcessingException if YAML parsing fails
     */
    public static Metadata yamlBodyToCborMetadata(java.math.BigInteger label, String yamlBody) throws JsonProcessingException {
        JsonNode jsonNode = yamlMapper.readTree(yamlBody);
        
        if (jsonNode instanceof ObjectNode) {
            CBORMetadataMap metadataMap = parseObjectNode((ObjectNode) jsonNode);
            return new com.bloxbean.cardano.client.metadata.cbor.CBORMetadata().put(label, metadataMap);
        } else if (jsonNode.isArray()) {
            CBORMetadataList metadataList = parseArrayNode((ArrayNode) jsonNode);
            return new com.bloxbean.cardano.client.metadata.cbor.CBORMetadata().put(label, metadataList);
        } else {
            throw new IllegalArgumentException("Invalid YAML format. Should be an object or an array");
        }
    }

    /**
     * Parses a YAML string to create a MetadataMap instance
     * @param yamlBody YAML string representing a map/object
     * @return MetadataMap object
     * @throws JsonProcessingException if YAML parsing fails
     * @throws IllegalArgumentException if YAML doesn't represent an object
     */
    public static CBORMetadataMap yamlToMetadataMap(String yamlBody) throws JsonProcessingException {
        JsonNode jsonNode = yamlMapper.readTree(yamlBody);
        if (jsonNode instanceof ObjectNode) {
            return parseObjectNode((ObjectNode) jsonNode);
        } else {
            throw new IllegalArgumentException("YAML content must be an object/map for MetadataMap conversion");
        }
    }

    /**
     * Parses a YAML string to create a MetadataList instance
     * @param yamlBody YAML string representing an array/list
     * @return MetadataList object
     * @throws JsonProcessingException if YAML parsing fails
     * @throws IllegalArgumentException if YAML doesn't represent an array
     */
    public static CBORMetadataList yamlToMetadataList(String yamlBody) throws JsonProcessingException {
        JsonNode jsonNode = yamlMapper.readTree(yamlBody);
        if (jsonNode instanceof ArrayNode) {
            return parseArrayNode((ArrayNode) jsonNode);
        } else {
            throw new IllegalArgumentException("YAML content must be an array/list for MetadataList conversion");
        }
    }
}