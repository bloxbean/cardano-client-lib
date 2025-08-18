package com.bloxbean.cardano.client.watcher.visualizer.json;

import com.bloxbean.cardano.client.watcher.visualizer.model.ChainVisualizationModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for serializing and deserializing ChainVisualizationModel to/from JSON.
 * 
 * This class provides methods for converting the abstract visualization model
 * to JSON format for external tool integration, storage, or transmission.
 */
public class JsonSerializer {
    
    private static final ObjectMapper OBJECT_MAPPER;
    
    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * Serialize a ChainVisualizationModel to JSON string.
     * 
     * @param model the model to serialize
     * @return JSON string representation
     * @throws JsonSerializationException if serialization fails
     */
    public static String serialize(ChainVisualizationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize chain visualization model", e);
        }
    }
    
    /**
     * Serialize a ChainVisualizationModel to pretty-printed JSON string.
     * 
     * @param model the model to serialize
     * @return pretty-printed JSON string
     * @throws JsonSerializationException if serialization fails
     */
    public static String serializePretty(ChainVisualizationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize chain visualization model", e);
        }
    }
    
    /**
     * Deserialize JSON string to ChainVisualizationModel.
     * 
     * @param json the JSON string to deserialize
     * @return deserialized model
     * @throws JsonDeserializationException if deserialization fails
     */
    public static ChainVisualizationModel deserialize(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, ChainVisualizationModel.class);
        } catch (JsonProcessingException e) {
            throw new JsonDeserializationException("Failed to deserialize chain visualization model", e);
        }
    }
    
    /**
     * Deserialize JSON from InputStream to ChainVisualizationModel.
     * 
     * @param inputStream the input stream containing JSON data
     * @return deserialized model
     * @throws JsonDeserializationException if deserialization fails
     */
    public static ChainVisualizationModel deserialize(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        
        try {
            return OBJECT_MAPPER.readValue(inputStream, ChainVisualizationModel.class);
        } catch (IOException e) {
            throw new JsonDeserializationException("Failed to deserialize chain visualization model from stream", e);
        }
    }
    
    /**
     * Validate that a JSON string can be deserialized without errors.
     * 
     * @param json the JSON string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            OBJECT_MAPPER.readValue(json, ChainVisualizationModel.class);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
    
    /**
     * Get the ObjectMapper instance used for serialization.
     * This can be useful for custom configuration or advanced usage.
     * 
     * @return the ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
    
    /**
     * Serialize model to compact JSON (single line, no formatting)
     * 
     * @param model the model to serialize
     * @return compact JSON string
     * @throws JsonSerializationException if serialization fails
     */
    public static String serializeCompact(ChainVisualizationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        
        try {
            ObjectMapper compactMapper = new ObjectMapper();
            compactMapper.registerModule(new JavaTimeModule());
            compactMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            compactMapper.disable(SerializationFeature.INDENT_OUTPUT);
            return compactMapper.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize chain visualization model compactly", e);
        }
    }
    
    /**
     * Exception thrown when JSON serialization fails
     */
    public static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown when JSON deserialization fails
     */
    public static class JsonDeserializationException extends RuntimeException {
        public JsonDeserializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}