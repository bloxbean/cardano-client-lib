---
title: "Metadata API"
description: "Transaction metadata creation, management, and serialization"
sidebar_position: 4
---

# Metadata API

The Metadata API provides comprehensive functionality for creating, managing, and serializing transaction metadata in the Cardano blockchain. It supports both CBOR and JSON formats, enabling you to attach structured data to transactions with type safety and validation.

## Key Features

- **CBOR Serialization**: Native CBOR format support for Cardano metadata
- **JSON Conversion**: Convert between JSON and CBOR metadata formats
- **Structured Data**: Support for maps, lists, and nested data structures
- **Type Safety**: Strong typing for different metadata value types
- **Validation**: Built-in validation for metadata structure and size limits
- **Builder Pattern**: Convenient builder pattern for metadata construction

## Core Classes

### Metadata Interface
The main interface for all metadata operations, providing methods for data manipulation and serialization.

### MetadataBuilder
Utility class for creating and converting metadata objects between different formats.

### MetadataMap
Interface for map-type metadata structures with key-value pairs.

### MetadataList
Interface for list-type metadata structures containing ordered elements.

**Key Methods:**
- `MetadataBuilder.createMetadata()` - Create new metadata instance
- `put()` - Add key-value pairs to metadata
- `serialize()` - Convert to CBOR bytes
- `toJson()` - Convert to JSON format

## Usage Examples

### Creating Basic Metadata

Create simple metadata with key-value pairs:

```java
// Create empty metadata
Metadata metadata = MetadataBuilder.createMetadata();

// Add simple key-value pairs
metadata.put(1L, "Hello Cardano");
metadata.put(2L, BigInteger.valueOf(100));
metadata.put(3L, "metadata".getBytes());

// Add boolean value (as integer)
metadata.put(4L, BigInteger.valueOf(1)); // true
metadata.put(5L, BigInteger.valueOf(0)); // false

System.out.println("Basic metadata created with 5 entries");
```

### Creating Structured Metadata

Create complex metadata with nested structures:

```java
// Create metadata with nested maps
Metadata structuredMetadata = MetadataBuilder.createMetadata();

// Create nested map
MetadataMap nestedMap = MetadataBuilder.createMap();
nestedMap.put("name", "Cardano Transaction");
nestedMap.put("version", BigInteger.valueOf(1));
nestedMap.put("timestamp", BigInteger.valueOf(System.currentTimeMillis()));

// Create list of values
MetadataList valuesList = MetadataBuilder.createList();
valuesList.add("value1");
valuesList.add("value2");
valuesList.add(BigInteger.valueOf(42));

// Add to main metadata
structuredMetadata.put(100L, nestedMap);
structuredMetadata.put(200L, valuesList);
structuredMetadata.put(300L, "Simple string value");

System.out.println("Structured metadata created");
```

### Metadata Serialization

Convert metadata to different formats:

```java
Metadata metadata = MetadataBuilder.createMetadata();
metadata.put(674L, "Simple message");
metadata.put(675L, BigInteger.valueOf(12345));

// Serialize to CBOR bytes
byte[] cborBytes = metadata.serialize();
System.out.println("CBOR size: " + cborBytes.length + " bytes");

// Convert to JSON string
String jsonString = metadata.toJson();
System.out.println("JSON: " + jsonString);

// Convert to hex string for inspection
String hexString = HexUtil.encodeHexString(cborBytes);
System.out.println("CBOR Hex: " + hexString);
```

### JSON to Metadata Conversion

Convert JSON metadata to CBOR format:

```java
// JSON metadata string
String jsonMetadata = """
{
    "674": {
        "msg": ["Hello", "Cardano", "World"]
    },
    "675": {
        "field1": "value1",
        "field2": 42,
        "field3": true
    }
}
""";

// Convert JSON to Metadata
Metadata metadata = MetadataBuilder.createMetadataFromJson(jsonMetadata);

// Serialize to CBOR
byte[] cborBytes = metadata.serialize();
System.out.println("Converted JSON to CBOR: " + cborBytes.length + " bytes");
```

## Advanced Usage

### CIP-20 Metadata (Message Metadata)

Create CIP-20 compliant metadata for messages:

```java
public class CIP20MetadataBuilder {
    
    public static Metadata createMessageMetadata(String message) {
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // CIP-20 uses label 674 for messages
        MetadataMap messageMap = MetadataBuilder.createMap();
        messageMap.put("msg", message);
        
        metadata.put(674L, messageMap);
        return metadata;
    }
    
    public static Metadata createMessageWithSender(String message, String sender) {
        Metadata metadata = MetadataBuilder.createMetadata();
        
        MetadataMap messageMap = MetadataBuilder.createMap();
        messageMap.put("msg", message);
        messageMap.put("sender", sender);
        messageMap.put("timestamp", BigInteger.valueOf(System.currentTimeMillis()));
        
        metadata.put(674L, messageMap);
        return metadata;
    }
}

// Usage
Metadata simpleMessage = CIP20MetadataBuilder.createMessageMetadata("Hello from Cardano!");
Metadata messageWithSender = CIP20MetadataBuilder.createMessageWithSender(
    "Transaction completed", "CardanoApp"
);
```

### Custom Metadata Structures

Create application-specific metadata structures:

```java
public class ApplicationMetadata {
    
    public static Metadata createTransactionMetadata(String appName, String version, 
                                                   Map<String, Object> customData) {
        Metadata metadata = MetadataBuilder.createMetadata();
        
        // Application info (label 1000)
        MetadataMap appInfo = MetadataBuilder.createMap();
        appInfo.put("name", appName);
        appInfo.put("version", version);
        appInfo.put("timestamp", BigInteger.valueOf(System.currentTimeMillis()));
        
        metadata.put(1000L, appInfo);
        
        // Custom data (label 1001)
        if (customData != null && !customData.isEmpty()) {
            MetadataMap customMap = MetadataBuilder.createMap();
            
            for (Map.Entry<String, Object> entry : customData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof String) {
                    customMap.put(key, (String) value);
                } else if (value instanceof Number) {
                    customMap.put(key, BigInteger.valueOf(((Number) value).longValue()));
                } else if (value instanceof byte[]) {
                    customMap.put(key, (byte[]) value);
                }
            }
            
            metadata.put(1001L, customMap);
        }
        
        return metadata;
    }
}

// Usage
Map<String, Object> customData = new HashMap<>();
customData.put("transactionId", "tx_12345");
customData.put("amount", 1000000);
customData.put("description", "Payment for services");

Metadata appMetadata = ApplicationMetadata.createTransactionMetadata(
    "MyCardanoApp", "1.0.0", customData
);
```

The Metadata API provides full support for standard metadata formats while allowing custom metadata structures for application-specific needs.