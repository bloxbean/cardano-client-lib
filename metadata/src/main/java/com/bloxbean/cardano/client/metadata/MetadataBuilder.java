package com.bloxbean.cardano.client.metadata;

import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigInteger;

/**
 * The {@code MetadataBuilder} class provides a collection of static methods for creating
 * and manipulating metadata objects in various formats such as CBOR and JSON.
 * It offers functionality to create, deserialize, and convert metadata
 * between JSON and CBOR representations.
 *
 * <p> This class serves as a utility for handling metadata operations, abstracting
 * the complexity of different metadata types and conversion processes behind
 * simple method calls. It includes methods for:
 * <ul>
 *   <li>Creating different types of metadata objects like {@code Metadata}, {@code MetadataMap}, {@code MetadataList}.</li>
 *   <li>Deserialization of CBOR byte arrays to Metadata objects.</li>
 *   <li>Conversion of JSON strings to Metadata and vice-versa.</li>
 * </ul>
 *
 * <p> Example usage:
 * <pre>
 * {@code
 * Metadata metadata = MetadataBuilder.createMetadata();
 * MetadataMap metadataMap = MetadataBuilder.createMap();
 * MetadataList metadataList = MetadataBuilder.createList();
 * Metadata fromJson = MetadataBuilder.metadataFromJson(jsonString);
 * String jsonString = MetadataBuilder.toJson(metadata);
 * }
 * </pre>
 */
public class MetadataBuilder {

    /**
     * Create Metadata object
     *
     * @return Metadata
     */
    public static Metadata createMetadata() {
        return new CBORMetadata();
    }

    /**
     * Create MetadataMap object
     *
     * @return MetadataMap
     */
    public static MetadataMap createMap() {
        return new CBORMetadataMap();
    }

    /**
     * Create MetadataList object
     *
     * @return MetadataList
     */
    public static MetadataList createList() {
        return new CBORMetadataList();
    }

    /**
     * Deserialize cbor bytes to Metadata object
     *
     * @param cborBytes
     * @return Metadata
     */
    public static Metadata deserialize(byte[] cborBytes) {
        return CBORMetadata.deserialize(cborBytes);
    }

    /**
     * Converts a JSON string to a Metadata object.
     *
     * @param json the JSON string to be converted to a Metadata object
     * @return a Metadata object generated from the provided JSON string
     * @throws IllegalArgumentException if the JSON format is invalid
     */
    public static Metadata metadataFromJson(String json) {
        try {
            return JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid json format for metadta", e);
        }
    }

    /**
     * Creates a Metadata object from a JSON string.
     *
     * @param label    a BigInteger representing the label associated with the metadata
     * @param jsonBody a JSON string representing the metadata content
     * @return a Metadata object containing the parsed data
     * @throws IllegalArgumentException if the JSON format is invalid or not an object/array
     */
    public static Metadata metadataFromJsonBody(BigInteger label, String jsonBody) {
        JsonNode jsonNode;
        try {
            jsonNode = JsonUtil.parseJson(jsonBody);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid json format");
        }

        if (jsonNode instanceof ObjectNode) {
            var metadataMap = JsonNoSchemaToMetadataConverter.parseObjectNode((ObjectNode) jsonNode);
            return new CBORMetadata().put(label, metadataMap);
        } else if (jsonNode.isArray()) {
            var metadataList = JsonNoSchemaToMetadataConverter.parseArrayNode((ArrayNode) jsonNode);
            return new CBORMetadata().put(label, metadataList);
        } else {
            throw new IllegalArgumentException("Invalid json format. Should be an object or an array");
        }
    }

    /**
     * Parses a JSON string to create a MetadataMap instance.
     *
     * @param jsonBody the JSON string to be converted to a MetadataMap object
     * @return a MetadataMap object generated from the provided JSON object
     * @throws IllegalArgumentException if the JSON format is invalid
     */
    public static MetadataMap metadataMapFromJsonBody(String jsonBody) {
        try {
            return JsonNoSchemaToMetadataConverter.parseObjectNode((ObjectNode) JsonUtil.parseJson(jsonBody));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not parse json body to MetadataMap", e);
        }
    }

    /**
     * Converts a JSON string into a MetadataList instance.
     *
     * @param jsonBody the JSON string to be converted to a MetadataList object
     * @return a MetadataList object generated from the provided JSON array
     * @throws IllegalArgumentException if the JSON format is invalid or cannot be parsed
     */
    public static MetadataList metadataListFromJsonBody(String jsonBody) {
        try {
            return JsonNoSchemaToMetadataConverter.parseArrayNode((ArrayNode) JsonUtil.parseJson(jsonBody));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not parse json body to MetadataMap", e);
        }
    }

    /**
     * Converts a Metadata object to its JSON representation.
     *
     * @param metadata the Metadata object to be converted
     * @return a JsonNode representing the JSON equivalent of the given Metadata
     * @throws IllegalArgumentException if the serialization results in an invalid JSON format
     */
    public static String toJson(Metadata metadata) {
        try {
            return MetadataToJsonNoSchemaConverter.cborBytesToJson(metadata.serialize());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid json format");
        }
    }

    /**
     * Converts the provided CBOR byte array to a JSON string.
     *
     * @param serializedCbor A byte array serialized CBOR data to be converted to JSON.
     * @return A string containing the JSON representation of the given CBOR data.
     * @throws IllegalArgumentException if the CBOR data cannot be converted to a valid JSON format.
     */
    public static String toJson(byte[] serializedCbor) {
        try {
            return MetadataToJsonNoSchemaConverter.cborBytesToJson(serializedCbor);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid json format");
        }
    }
}
