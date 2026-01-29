---
title: "Plutus API"
description: "Smart contract interaction with PlutusData, scripts, and blueprints"
sidebar_position: 5
---

# Plutus API

The Plutus API provides comprehensive functionality for interacting with Plutus smart contracts on the Cardano blockchain. It includes support for Plutus scripts (V1, V2, V3) and data serialization with type safety.

## Key Features

- **Plutus Script Support**: Complete support for V1, V2, and V3 script versions
- **Data Serialization**: Convert between Java objects and PlutusData
- **Type Safety**: Strong typing for Plutus data structures

## Core Classes

### PlutusScript Classes
- `PlutusV1Script`: Plutus V1 script implementation
- `PlutusV2Script`: Plutus V2 script implementation  
- `PlutusV3Script`: Plutus V3 script implementation

### PlutusData Classes
- `PlutusData`: Base interface for all Plutus data types
- `BigIntPlutusData`: Integer data representation
- `BytesPlutusData`: Byte string data representation
- `ListPlutusData`: List data representation
- `MapPlutusData`: Map data representation


## Usage Examples

### Creating Plutus Scripts

Create different versions of Plutus scripts:

```java
// Create Plutus V2 Script (most common)
PlutusV2Script v2Script = PlutusV2Script.builder()
        .type("PlutusScriptV2")
        .cborHex("590a4d590a4a0100003333...")
        .build();

// Create Plutus V3 Script (latest)
PlutusV3Script v3Script = PlutusV3Script.builder()
        .type("PlutusScriptV3")
        .cborHex("590b5e590b5b0100003333...")
        .build();

System.out.println("Scripts created successfully");
```

### Working with PlutusData

Create and manipulate PlutusData structures:

```java
// Create integer data
PlutusData intData = BigIntPlutusData.of(BigInteger.valueOf(42));

// Create string data (as bytes)
PlutusData stringData = BytesPlutusData.of("Hello Cardano".getBytes());

// Create list data
List<PlutusData> dataList = Arrays.asList(intData, stringData);
PlutusData listData = ListPlutusData.of(dataList);

// Create map data
Map<PlutusData, PlutusData> dataMap = new HashMap<>();
dataMap.put(BytesPlutusData.of("key".getBytes()), intData);
PlutusData mapData = MapPlutusData.of(dataMap);

System.out.println("PlutusData structures created");
```


## Advanced Usage


### Script Validation

Validate and work with Plutus scripts:

```java
public class ScriptValidator {
    
    public static boolean isValidScript(PlutusScript script) {
        try {
            // Check script type
            String scriptType = script.getType();
            if (!Arrays.asList("PlutusScriptV1", "PlutusScriptV2", "PlutusScriptV3")
                    .contains(scriptType)) {
                return false;
            }
            
            // Check CBOR hex
            String cborHex = script.getCborHex();
            if (cborHex == null || cborHex.isEmpty()) {
                return false;
            }
            
            // Validate hex format
            HexUtil.decodeHexString(cborHex);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Script validation failed: " + e.getMessage());
            return false;
        }
    }
    
    public static void analyzeScript(PlutusScript script) {
        System.out.println("Script Type: " + script.getType());
        System.out.println("CBOR Size: " + script.getCborHex().length() / 2 + " bytes");
        System.out.println("Script Hash: " + script.getScriptHash());
    }
}
```

## Best Practices

### Type Safety

```java
// Use type-safe PlutusData creation
public PlutusData createTypeSafePlutusData() {
    // Prefer specific types over generic PlutusData
    PlutusData intData = BigIntPlutusData.of(BigInteger.valueOf(100));
    PlutusData bytesData = BytesPlutusData.of("data".getBytes());
    
    // Validate data before use
    if (intData instanceof BigIntPlutusData) {
        BigInteger value = ((BigIntPlutusData) intData).getValue();
        System.out.println("Integer value: " + value);
    }
    
    return intData;
}
```

## Plutus Version Comparison

| Feature | Plutus V1 | Plutus V2 | Plutus V3 |
|---------|-----------|-----------|-----------|
| **Reference Inputs** | ❌ | ✅ | ✅ |
| **Inline Datums** | ❌ | ✅ | ✅ |
| **Reference Scripts** | ❌ | ✅ | ✅ |
| **Collateral Outputs** | ❌ | ✅ | ✅ |
| **New Primitives** | ❌ | ❌ | ✅ |
| **Performance** | Basic | Improved | Optimized |

Choose the appropriate Plutus version based on your smart contract requirements and the features you need.