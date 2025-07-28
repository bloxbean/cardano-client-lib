---
description: Comprehensive guide to the metadata module including Metadata, MetadataBuilder, MetadataMap, MetadataList, CBOR handling, and JSON conversion utilities
sidebar_label: Metadata Module
sidebar_position: 2
---

# Metadata Module

The metadata module provides a comprehensive, type-safe system for creating, manipulating, and converting Cardano transaction metadata. It supports CBOR serialization, JSON conversion, and various data structures for flexible metadata handling.

:::tip Prerequisites
Understanding of [Transaction Building](../../quicktx/index.md) and basic CBOR concepts is recommended.
:::

## Overview

The metadata system consists of several key components:

- **Metadata Interface** - Core metadata functionality with CBOR support
- **MetadataBuilder** - Factory and utility methods for metadata creation
- **MetadataMap** - Key-value map structures for metadata
- **MetadataList** - Array-like structures for metadata
- **JSON Conversion** - Bidirectional JSON-metadata conversion
- **CBOR Integration** - Native CBOR serialization and deserialization

## Metadata Interface

The `Metadata` interface provides the core functionality for handling transaction metadata with automatic CBOR serialization and hash calculation.

### Core Methods

```java
import com.bloxbean.cardano.client.metadata.*;
import java.math.BigInteger;

public class MetadataBasicsExample {
    
    public void demonstrateBasicMetadata() {
        // Create new metadata instance
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // Add different types of data
        metadata.put(BigInteger.valueOf(1), "Hello Cardano");
        metadata.put(BigInteger.valueOf(2), BigInteger.valueOf(12345));
        metadata.put(BigInteger.valueOf(3), "World".getBytes());
        
        // Convenience method for long keys (auto-converted to BigInteger)
        metadata.put(4L, "Using long key");
        
        // Negative integers require special handling
        metadata.putNegative(BigInteger.valueOf(5), BigInteger.valueOf(-100));
        
        // Retrieve values
        Object value1 = metadata.get(BigInteger.valueOf(1));
        System.out.println("Value 1: " + value1); // Output: Hello Cardano
        
        // Get all keys
        java.util.List<BigInteger> keys = metadata.keys();
        System.out.println("Keys: " + keys);
        
        // Calculate metadata hash
        String hash = metadata.getMetadataHash();
        System.out.println("Metadata hash: " + hash);
        
        // Serialize to CBOR bytes
        byte[] cborBytes = metadata.serialize();
        System.out.println("CBOR size: " + cborBytes.length + " bytes");
    }
}
```

### String Length Handling

Cardano metadata has a 64-byte limit for individual strings. The library automatically handles this by splitting longer strings.

```java
public class StringHandlingExample {
    
    public void demonstrateStringHandling() {
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // Short string - stored as single value
        metadata.put(1L, "Short text");
        
        // Long string - automatically split into chunks
        String longText = "This is a very long string that exceeds the 64-byte limit " +
                         "for individual metadata strings and will be automatically " +
                         "split into multiple chunks by the library";
        
        metadata.put(2L, longText);
        
        // When retrieved, chunks are automatically recombined
        Object retrievedText = metadata.get(BigInteger.valueOf(2));
        System.out.println("Retrieved text: " + retrievedText);
        
        // The splitting is transparent to the user
        System.out.println("Original equals retrieved: " + longText.equals(retrievedText));
    }
}
```

### Metadata Merging

```java
public class MetadataMergingExample {
    
    public void demonstrateMetadataMerging() {
        // Create first metadata object
        Metadata metadata1 = MetadataBuilder.createMetadata();
        metadata1.put(1L, "First metadata");
        metadata1.put(2L, BigInteger.valueOf(100));
        
        // Create second metadata object
        Metadata metadata2 = MetadataBuilder.createMetadata();
        metadata2.put(3L, "Second metadata");
        metadata2.put(4L, BigInteger.valueOf(200));
        
        // Merge metadata2 into metadata1
        Metadata merged = metadata1.merge(metadata2);
        
        // Verify merged content
        System.out.println("Merged keys: " + merged.keys());
        System.out.println("Value from first: " + merged.get(BigInteger.valueOf(1)));
        System.out.println("Value from second: " + merged.get(BigInteger.valueOf(3)));
        
        // Handle key conflicts (second metadata overwrites first)
        metadata1.put(5L, "Original value");
        metadata2.put(5L, "Overwriting value");
        
        Metadata conflictMerged = metadata1.merge(metadata2);
        System.out.println("Conflict result: " + conflictMerged.get(BigInteger.valueOf(5)));
        // Output: Overwriting value
    }
}
```

## MetadataBuilder

The `MetadataBuilder` class provides factory methods and utilities for creating and converting metadata objects.

### Creation Methods

```java
public class MetadataBuilderExample {
    
    public void demonstrateCreation() {
        // Create empty metadata
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // Create empty metadata map
        MetadataMap map = MetadataBuilder.createMap();
        
        // Create empty metadata list
        MetadataList list = MetadataBuilder.createList();
        
        // Build complex nested structure
        buildComplexMetadata();
    }
    
    private void buildComplexMetadata() {
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // Create a map for user information
        MetadataMap userInfo = MetadataBuilder.createMap();
        userInfo.put("name", "Alice");
        userInfo.put("age", BigInteger.valueOf(30));
        userInfo.put("city", "New York");
        
        // Create a list of hobbies
        MetadataList hobbies = MetadataBuilder.createList();
        hobbies.add("reading");
        hobbies.add("swimming");
        hobbies.add("coding");
        
        // Add nested structures to main metadata
        metadata.put(1L, userInfo);
        metadata.put(2L, hobbies);
        metadata.put(3L, "Simple string value");
        
        System.out.println("Complex metadata created with nested structures");
    }
}
```

### CBOR Serialization and Deserialization

```java
public class CborHandlingExample {
    
    public void demonstrateCborOperations() {
        // Create metadata
        Metadata original = MetadataBuilder.createMetadata();
        original.put(1L, "Test data");
        original.put(2L, BigInteger.valueOf(42));
        
        try {
            // Serialize to CBOR bytes
            byte[] cborBytes = original.serialize();
            System.out.println("Serialized to " + cborBytes.length + " bytes");
            
            // Deserialize back to metadata
            Metadata deserialized = MetadataBuilder.deserialize(cborBytes);
            
            // Verify data integrity
            System.out.println("Original hash: " + original.getMetadataHash());
            System.out.println("Deserialized hash: " + deserialized.getMetadataHash());
            
            // Compare values
            Object value1 = deserialized.get(BigInteger.valueOf(1));
            Object value2 = deserialized.get(BigInteger.valueOf(2));
            
            System.out.println("Value 1: " + value1);
            System.out.println("Value 2: " + value2);
            
        } catch (Exception e) {
            System.err.println("CBOR operation failed: " + e.getMessage());
        }
    }
    
    public void handleCborErrors() {
        // Invalid CBOR data
        byte[] invalidCbor = {0x01, 0x02, 0x03}; // Not valid CBOR
        
        try {
            Metadata metadata = MetadataBuilder.deserialize(invalidCbor);
        } catch (Exception e) {
            System.err.println("Expected error: " + e.getMessage());
            // Handle deserialization error appropriately
        }
    }
}
```

## JSON Conversion

The metadata module provides comprehensive JSON conversion capabilities with automatic type detection and preservation.

### Basic JSON Operations

```java
public class JsonConversionExample {
    
    public void demonstrateJsonConversion() {
        // Create metadata with various data types
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(1L, "Hello World");
        metadata.put(2L, BigInteger.valueOf(12345));
        metadata.put(3L, "0x48656c6c6f".getBytes()); // Hex bytes
        
        try {
            // Convert to JSON
            String json = MetadataBuilder.toJson(metadata);
            System.out.println("Metadata as JSON:");
            System.out.println(json);
            
            // Convert back from JSON
            Metadata fromJson = MetadataBuilder.metadataFromJson(json);
            
            // Verify round-trip conversion
            String backToJson = MetadataBuilder.toJson(fromJson);
            System.out.println("Round-trip successful: " + json.equals(backToJson));
            
        } catch (Exception e) {
            System.err.println("JSON conversion error: " + e.getMessage());
        }
    }
    
    public void demonstrateComplexJsonConversion() {
        // Create complex nested structure
        String complexJson = """
            {
                "1": {
                    "name": "Alice",
                    "details": {
                        "age": 30,
                        "location": "New York"
                    }
                },
                "2": ["item1", "item2", "item3"],
                "3": "0x48656c6c6f",
                "4": 12345
            }
            """;
        
        try {
            // Parse complex JSON
            Metadata metadata = MetadataBuilder.metadataFromJson(complexJson);
            
            // Access nested data
            Object userInfo = metadata.get(BigInteger.valueOf(1));
            Object itemList = metadata.get(BigInteger.valueOf(2));
            Object hexData = metadata.get(BigInteger.valueOf(3));
            
            System.out.println("User info: " + userInfo);
            System.out.println("Item list: " + itemList);
            System.out.println("Hex data: " + hexData);
            
        } catch (Exception e) {
            System.err.println("Complex JSON parsing error: " + e.getMessage());
        }
    }
}
```

### JSON Schema-less Conversion

```java
public class SchemalessJsonExample {
    
    public void demonstrateSchemalessConversion() {
        // JSON without predefined schema
        String dynamicJson = """
            {
                "transaction_id": "abc123",
                "amount": 1000000,
                "recipient": "addr1...",
                "metadata": {
                    "purpose": "payment",
                    "tags": ["urgent", "verified"]
                }
            }
            """;
        
        try {
            // Convert with specific label
            Metadata metadata = MetadataBuilder.metadataFromJsonBody(
                BigInteger.valueOf(674), // CIP-20 label for arbitrary JSON
                dynamicJson
            );
            
            // The JSON is stored under the specified label
            Object jsonData = metadata.get(BigInteger.valueOf(674));
            System.out.println("Stored JSON data: " + jsonData);
            
            // Convert back to JSON
            String retrievedJson = MetadataBuilder.toJson(metadata);
            System.out.println("Retrieved JSON: " + retrievedJson);
            
        } catch (Exception e) {
            System.err.println("Schema-less conversion error: " + e.getMessage());
        }
    }
}
```

## MetadataMap

The `MetadataMap` interface provides key-value storage with support for multiple key types and nested structures.

### Basic Map Operations

```java
public class MetadataMapExample {
    
    public void demonstrateMapOperations() {
        MetadataMap map = MetadataBuilder.createMap();
        
        // Different key types
        map.put("string_key", "String value");
        map.put(BigInteger.valueOf(1), "Integer key value");
        map.put("number".getBytes(), "Byte array key value");
        
        // Different value types
        map.put("text", "Simple text");
        map.put("number", BigInteger.valueOf(42));
        map.put("bytes", "Hello".getBytes());
        
        // Nested structures
        MetadataList nestedList = MetadataBuilder.createList();
        nestedList.add("item1");
        nestedList.add("item2");
        map.put("nested_list", nestedList);
        
        MetadataMap nestedMap = MetadataBuilder.createMap();
        nestedMap.put("inner_key", "inner_value");
        map.put("nested_map", nestedMap);
        
        // Retrieve values
        Object stringValue = map.get("string_key");
        Object intValue = map.get(BigInteger.valueOf(1));
        Object nestedValue = map.get("nested_list");
        
        System.out.println("String value: " + stringValue);
        System.out.println("Integer key value: " + intValue);
        System.out.println("Nested list: " + nestedValue);
        
        // Get all keys
        java.util.List<Object> keys = map.keys();
        System.out.println("All keys: " + keys);
    }
}
```

### Advanced Map Usage

```java
public class AdvancedMapExample {
    
    public void demonstrateAdvancedMapUsage() {
        MetadataMap userProfile = MetadataBuilder.createMap();
        
        // User basic information
        userProfile.put("id", BigInteger.valueOf(12345));
        userProfile.put("username", "alice");
        userProfile.put("email", "alice@example.com");
        
        // Preferences as nested map
        MetadataMap preferences = MetadataBuilder.createMap();
        preferences.put("theme", "dark");
        preferences.put("language", "en");
        preferences.put("notifications", BigInteger.valueOf(1)); // 1 = enabled
        userProfile.put("preferences", preferences);
        
        // Activity history as list
        MetadataList activities = MetadataBuilder.createList();
        activities.add("login");
        activities.add("transaction");
        activities.add("logout");
        userProfile.put("recent_activities", activities);
        
        // Handle negative values
        userProfile.putNegative("balance_change", BigInteger.valueOf(-50));
        
        // Convert to JSON for storage or transmission
        try {
            String json = userProfile.toJson();
            System.out.println("User profile JSON: " + json);
        } catch (Exception e) {
            System.err.println("JSON conversion failed: " + e.getMessage());
        }
        
        // Remove entries
        userProfile.remove("email"); // Remove by string key
        userProfile.remove(BigInteger.valueOf(12345)); // Remove by BigInteger key
    }
}
```

## MetadataList

The `MetadataList` interface provides array-like functionality with support for various data types and nested structures.

### Basic List Operations

```java
public class MetadataListExample {
    
    public void demonstrateListOperations() {
        MetadataList list = MetadataBuilder.createList();
        
        // Add different types
        list.add("First item");
        list.add(BigInteger.valueOf(42));
        list.add("Hello".getBytes());
        
        // Add negative number
        list.addNegative(BigInteger.valueOf(-100));
        
        // Add multiple strings at once
        String[] fruits = {"apple", "banana", "orange"};
        list.addAll(fruits);
        
        // Add nested structures
        MetadataMap nestedMap = MetadataBuilder.createMap();
        nestedMap.put("key", "value");
        list.add(nestedMap);
        
        MetadataList nestedList = MetadataBuilder.createList();
        nestedList.add("nested item");
        list.add(nestedList);
        
        // Access by index
        Object firstItem = list.getValueAt(0);
        Object secondItem = list.getValueAt(1);
        
        System.out.println("First item: " + firstItem);
        System.out.println("Second item: " + secondItem);
        System.out.println("List size: " + list.size());
        
        // Replace item at index
        list.replaceAt(0, "Replaced first item");
        
        // Remove items
        list.removeItem("banana"); // Remove by value
        list.removeItemAt(2); // Remove by index
        
        System.out.println("Final size: " + list.size());
    }
}
```

### List Manipulation

```java
public class ListManipulationExample {
    
    public void demonstrateListManipulation() {
        MetadataList transactionLog = MetadataBuilder.createList();
        
        // Build transaction log entries
        for (int i = 1; i <= 5; i++) {
            MetadataMap logEntry = MetadataBuilder.createMap();
            logEntry.put("tx_id", "tx_" + i);
            logEntry.put("amount", BigInteger.valueOf(i * 1000000)); // ADA in lovelace
            logEntry.put("timestamp", BigInteger.valueOf(System.currentTimeMillis()));
            
            transactionLog.add(logEntry);
        }
        
        System.out.println("Transaction log created with " + transactionLog.size() + " entries");
        
        // Process each entry
        for (int i = 0; i < transactionLog.size(); i++) {
            Object entry = transactionLog.getValueAt(i);
            System.out.println("Entry " + i + ": " + entry);
        }
        
        // Update specific entry
        MetadataMap updatedEntry = MetadataBuilder.createMap();
        updatedEntry.put("tx_id", "tx_3_updated");
        updatedEntry.put("amount", BigInteger.valueOf(5000000));
        updatedEntry.put("timestamp", BigInteger.valueOf(System.currentTimeMillis()));
        updatedEntry.put("status", "confirmed");
        
        transactionLog.replaceAt(2, updatedEntry); // Update third entry (index 2)
        
        // Convert to JSON for inspection
        try {
            String json = transactionLog.toJson();
            System.out.println("Transaction log JSON: " + json);
        } catch (Exception e) {
            System.err.println("JSON conversion failed: " + e.getMessage());
        }
    }
}
```

## Transaction Integration

### Adding Metadata to Transactions

```java
public class TransactionMetadataExample {
    
    public void demonstrateTransactionMetadata() {
        // Create transaction metadata
        Metadata txMetadata = MetadataBuilder.createMetadata();
        
        // Add transaction description
        txMetadata.put(1L, "Payment for services");
        
        // Add structured data
        MetadataMap paymentDetails = MetadataBuilder.createMap();
        paymentDetails.put("invoice_id", "INV-2024-001");
        paymentDetails.put("service", "Web Development");
        paymentDetails.put("hours", BigInteger.valueOf(40));
        paymentDetails.put("rate", BigInteger.valueOf(50)); // USD per hour
        
        txMetadata.put(2L, paymentDetails);
        
        // Add tags
        MetadataList tags = MetadataBuilder.createList();
        tags.add("business");
        tags.add("web-dev");
        tags.add("contractor");
        
        txMetadata.put(3L, tags);
        
        // Use in transaction building (using QuickTx)
        buildTransactionWithMetadata(txMetadata);
    }
    
    private void buildTransactionWithMetadata(Metadata metadata) {
        try {
            // Example transaction building with metadata
            /*
            Tx paymentTx = new Tx()
                .payTo(receiverAddress, Amount.ada(100))
                .attachMetadata(metadata)  // Attach our metadata
                .from(senderAddress);
            
            Result<String> result = QuickTxBuilder.create(backendService)
                .compose(paymentTx)
                .withSigner(SignerProviders.signerFrom(senderAccount))
                .completeAndSubmit();
            */
            
            System.out.println("Transaction would be built with metadata:");
            System.out.println("Metadata hash: " + metadata.getMetadataHash());
            System.out.println("Metadata size: " + metadata.serialize().length + " bytes");
            
        } catch (Exception e) {
            System.err.println("Transaction building failed: " + e.getMessage());
        }
    }
}
```

### CIP-20 Message Standard

```java
public class CIP20MessageExample {
    
    public void demonstrateCIP20Messages() {
        // CIP-20 uses label 674 for arbitrary JSON messages
        final BigInteger CIP20_LABEL = BigInteger.valueOf(674);
        
        // Create a CIP-20 compliant message
        String messageJson = """
            {
                "msg": [
                    "Hello from Cardano!",
                    "This is a CIP-20 message"
                ]
            }
            """;
        
        try {
            // Create metadata with CIP-20 structure
            Metadata cip20Metadata = MetadataBuilder.metadataFromJsonBody(CIP20_LABEL, messageJson);
            
            // Verify the structure
            Object message = cip20Metadata.get(CIP20_LABEL);
            System.out.println("CIP-20 message: " + message);
            
            // Convert back to JSON
            String retrievedJson = MetadataBuilder.toJson(cip20Metadata);
            System.out.println("Retrieved JSON: " + retrievedJson);
            
            // Use in transaction
            /*
            Tx messageTx = new Tx()
                .payTo(receiverAddress, Amount.ada(1)) // Minimal ADA for message
                .attachMetadata(cip20Metadata)
                .from(senderAddress);
            */
            
        } catch (Exception e) {
            System.err.println("CIP-20 message creation failed: " + e.getMessage());
        }
    }
    
    public void createStandardizedMessage(String message, String sender) {
        MetadataMap messageMap = MetadataBuilder.createMap();
        
        // Standard message structure
        MetadataList messageContent = MetadataBuilder.createList();
        messageContent.add(message);
        
        messageMap.put("msg", messageContent);
        messageMap.put("sender", sender);
        messageMap.put("timestamp", BigInteger.valueOf(System.currentTimeMillis()));
        
        // Create metadata with CIP-20 label
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(674L, messageMap);
        
        System.out.println("Standardized message created:");
        System.out.println("Hash: " + metadata.getMetadataHash());
    }
}
```

## Error Handling

### Exception Types and Handling

```java
public class MetadataErrorHandlingExample {
    
    public void demonstrateErrorHandling() {
        // Handle serialization errors
        handleSerializationErrors();
        
        // Handle JSON conversion errors
        handleJsonErrors();
        
        // Handle CBOR deserialization errors
        handleCborErrors();
    }
    
    private void handleSerializationErrors() {
        try {
            Metadata metadata = MetadataBuilder.createMetadata();
            // This would normally work
            byte[] serialized = metadata.serialize();
            
        } catch (RuntimeException e) {
            System.err.println("Serialization error: " + e.getMessage());
            // Log error and provide fallback
        }
    }
    
    private void handleJsonErrors() {
        String invalidJson = "{ invalid json }";
        
        try {
            Metadata metadata = MetadataBuilder.metadataFromJson(invalidJson);
            
        } catch (Exception e) {
            System.err.println("JSON parsing error: " + e.getMessage());
            
            // Provide default metadata or re-throw with context
            throw new RuntimeException("Failed to parse transaction metadata", e);
        }
    }
    
    private void handleCborErrors() {
        byte[] invalidCbor = {0x01, 0x02, 0x03}; // Invalid CBOR
        
        try {
            Metadata metadata = MetadataBuilder.deserialize(invalidCbor);
            
        } catch (Exception e) {
            System.err.println("CBOR deserialization error: " + e.getMessage());
            // Return empty metadata or handle appropriately
        }
    }
    
    // Safe metadata operations with validation
    public Optional<Metadata> createMetadataSafely(Map<String, Object> data) {
        try {
            Metadata metadata = MetadataBuilder.createMetadata();
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                try {
                    // Parse key as long, fallback to string
                    long key = Long.parseLong(entry.getKey());
                    metadata.put(key, entry.getValue());
                } catch (NumberFormatException e) {
                    // Use string key in a map
                    MetadataMap stringMap = MetadataBuilder.createMap();
                    stringMap.put(entry.getKey(), entry.getValue());
                    metadata.put(999L, stringMap); // Use special key for string keys
                }
            }
            
            return Optional.of(metadata);
            
        } catch (Exception e) {
            System.err.println("Safe metadata creation failed: " + e.getMessage());
            return Optional.empty();
        }
    }
}
```

## Best Practices

### Efficient Metadata Design

```java
public class MetadataBestPractices {
    
    // Keep metadata concise to minimize transaction size
    public Metadata createEfficientMetadata(String purpose, Map<String, String> data) {
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // Use short, meaningful labels
        metadata.put(1L, purpose);
        
        // Group related data
        MetadataMap dataMap = MetadataBuilder.createMap();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            // Use abbreviated keys to save space
            String shortKey = abbreviateKey(entry.getKey());
            dataMap.put(shortKey, entry.getValue());
        }
        
        metadata.put(2L, dataMap);
        
        return metadata;
    }
    
    private String abbreviateKey(String fullKey) {
        // Create abbreviations for common keys
        return switch (fullKey.toLowerCase()) {
            case "transaction_id" -> "tx_id";
            case "timestamp" -> "ts";
            case "amount" -> "amt";
            case "recipient" -> "to";
            case "sender" -> "from";
            default -> fullKey.length() > 10 ? fullKey.substring(0, 10) : fullKey;
        };
    }
    
    // Validate metadata size before transaction
    public boolean validateMetadataSize(Metadata metadata) {
        try {
            byte[] serialized = metadata.serialize();
            
            // Cardano has practical limits on metadata size
            final int MAX_METADATA_SIZE = 16 * 1024; // 16KB reasonable limit
            
            if (serialized.length > MAX_METADATA_SIZE) {
                System.err.println("Metadata too large: " + serialized.length + " bytes");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Metadata validation failed: " + e.getMessage());
            return false;
        }
    }
    
    // Standardized metadata structure for applications
    public Metadata createApplicationMetadata(String appName, String version, 
                                            String action, Object payload) {
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // Application identification
        MetadataMap appInfo = MetadataBuilder.createMap();
        appInfo.put("name", appName);
        appInfo.put("version", version);
        metadata.put(1L, appInfo);
        
        // Action information
        metadata.put(2L, action);
        
        // Payload (if not too large)
        if (payload != null) {
            metadata.put(3L, payload);
        }
        
        // Timestamp
        metadata.put(4L, BigInteger.valueOf(System.currentTimeMillis()));
        
        return metadata;
    }
}
```

### Performance Optimization

```java
public class MetadataPerformance {
    
    // Cache frequently used metadata structures
    private static final Map<String, Metadata> METADATA_CACHE = new ConcurrentHashMap<>();
    
    public Metadata getCachedMetadata(String key, Supplier<Metadata> supplier) {
        return METADATA_CACHE.computeIfAbsent(key, k -> supplier.get());
    }
    
    // Batch metadata operations
    public List<Metadata> processMetadataBatch(List<Map<String, Object>> dataList) {
        return dataList.parallelStream()
            .map(this::createMetadataFromMap)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    private Optional<Metadata> createMetadataFromMap(Map<String, Object> data) {
        try {
            Metadata metadata = MetadataBuilder.createMetadata();
            
            // Process entries efficiently
            data.entrySet().parallelStream().forEach(entry -> {
                try {
                    long key = Long.parseLong(entry.getKey());
                    synchronized (metadata) {
                        metadata.put(key, entry.getValue());
                    }
                } catch (Exception e) {
                    // Skip invalid entries
                }
            });
            
            return Optional.of(metadata);
            
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    // Memory-efficient large metadata handling
    public void processLargeMetadata(Metadata metadata, Consumer<Object> processor) {
        // Process metadata keys in chunks to avoid memory issues
        List<BigInteger> keys = metadata.keys();
        int chunkSize = 100;
        
        for (int i = 0; i < keys.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, keys.size());
            List<BigInteger> chunk = keys.subList(i, end);
            
            chunk.forEach(key -> {
                Object value = metadata.get(key);
                processor.accept(value);
            });
            
            // Allow garbage collection between chunks
            System.gc();
        }
    }
}
```

The metadata module provides a comprehensive, type-safe, and efficient system for handling Cardano transaction metadata. Use these patterns and best practices to create robust applications that leverage metadata for enhanced transaction functionality.