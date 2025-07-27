---
description: Comprehensive guide to PlutusData types, serialization, and POJO conversion patterns
sidebar_label: PlutusData Types
sidebar_position: 1
---

# PlutusData Types and Serialization

PlutusData is the core data representation system used in Cardano smart contracts. It provides a standardized way to encode and decode data for Datum and Redeemer values in Plutus scripts. This guide covers all PlutusData types, serialization patterns, and conversion utilities available in the Cardano Client Library.

## Overview

PlutusData supports five fundamental types that can represent any data structure:
- **BigIntPlutusData** - Arbitrary precision integers
- **BytesPlutusData** - Byte arrays and UTF-8 strings  
- **ConstrPlutusData** - Algebraic data types with constructor alternatives
- **ListPlutusData** - Homogeneous collections
- **MapPlutusData** - Key-value mappings

All PlutusData types are serialized to CBOR (Concise Binary Object Representation) format for on-chain storage and transmission.

## Core PlutusData Interface

The base `PlutusData` interface provides essential serialization and utility methods:

```java
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

// Serialize to CBOR hex string
String hex = plutusData.serializeToHex();

// Calculate datum hash (Blake2b-256)
String datumHash = plutusData.getDatumHash();

// Deserialize from CBOR bytes
PlutusData restored = PlutusData.deserialize(bytes);

// Get CBOR diagnostic string (debugging)
String diagnostic = plutusData.getCborDiagnostic();
```

## BigIntPlutusData

Represents arbitrary precision integers using Java's `BigInteger` class.

### Basic Usage

```java
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import java.math.BigInteger;

// Create from int
PlutusData smallInt = BigIntPlutusData.of(42);

// Create from long
PlutusData longValue = BigIntPlutusData.of(1234567890123456789L);

// Create from BigInteger
BigInteger huge = new BigInteger("12345678901234567890123456789");
PlutusData bigInt = BigIntPlutusData.of(huge);

// Create negative values
PlutusData negative = BigIntPlutusData.of(-1000);
```

### Optimized Serialization

BigIntPlutusData uses optimized CBOR encoding:
- **Small values** (≤64 bits): Direct CBOR integer encoding
- **Large values** (>64 bits): CBOR tags 2 (positive) and 3 (negative) with byte string
- **Very large values** (>64 bytes): Automatic chunking

```java
// Small integer - efficient encoding
PlutusData small = BigIntPlutusData.of(100);
System.out.println("Small: " + small.serializeToHex()); // "1864"

// Large integer - tagged encoding  
BigInteger large = new BigInteger("18446744073709551616"); // 2^64
PlutusData bigValue = BigIntPlutusData.of(large);
System.out.println("Large: " + bigValue.serializeToHex()); // "c249010000000000000000"
```

### Working with Values

```java
// Extract the BigInteger value
BigIntPlutusData bigIntData = (BigIntPlutusData) plutusData;
BigInteger value = bigIntData.getValue();

// Arithmetic operations
PlutusData sum = BigIntPlutusData.of(
    ((BigIntPlutusData) data1).getValue()
        .add(((BigIntPlutusData) data2).getValue())
);
```

## BytesPlutusData

Handles byte arrays and UTF-8 strings with automatic chunking for large data.

### String Handling

```java
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;

// Create from UTF-8 string
PlutusData text = BytesPlutusData.of("Hello, Cardano!");

// Create from byte array
byte[] bytes = {0x01, 0x02, 0x03, 0x04};
PlutusData bytesData = BytesPlutusData.of(bytes);

// Create from hex string
PlutusData hexData = BytesPlutusData.of("deadbeef", true); // true = interpret as hex

// Extract value
BytesPlutusData bytesPlutus = (BytesPlutusData) plutusData;
byte[] extractedBytes = bytesPlutus.getValue();
String extractedString = new String(extractedBytes, StandardCharsets.UTF_8);
```

### Automatic Chunking

For byte arrays exceeding 64 bytes, BytesPlutusData automatically uses chunked encoding:

```java
// Large byte array - will be chunked automatically
byte[] largeData = new byte[1000];
Arrays.fill(largeData, (byte) 0xFF);
PlutusData chunked = BytesPlutusData.of(largeData);

// Chunked encoding is transparent to the user
byte[] restored = ((BytesPlutusData) chunked).getValue();
assert Arrays.equals(largeData, restored);
```

### CBOR Type Selection

BytesPlutusData intelligently chooses between CBOR byte string and text string:

```java
// UTF-8 text → CBOR text string (major type 3)
PlutusData text = BytesPlutusData.of("Hello");

// Binary data → CBOR byte string (major type 2)  
PlutusData binary = BytesPlutusData.of(new byte[]{0x00, 0xFF});
```

## ConstrPlutusData

Represents algebraic data types with constructor alternatives. Essential for modeling complex data structures and Haskell-style data types.

### Basic Constructor Usage

```java
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;

// Simple constructor with no fields
PlutusData unit = ConstrPlutusData.of(0); // Alternative 0, no fields

// Constructor with single field
PlutusData some = ConstrPlutusData.of(0, BigIntPlutusData.of(42));

// Constructor with multiple fields
PlutusData person = ConstrPlutusData.of(1,
    BytesPlutusData.of("Alice"),           // name
    BigIntPlutusData.of(30),               // age
    BytesPlutusData.of("alice@email.com")  // email
);
```

### Smart Encoding

ConstrPlutusData uses efficient CBOR encoding based on alternative number:

```java
// Compact encoding for alternatives 0-6 (tags 121-127)
PlutusData compact = ConstrPlutusData.of(3, someFields);

// Compact encoding for alternatives 7-127 (tags 1280-1400)  
PlutusData mediumCompact = ConstrPlutusData.of(50, someFields);

// General encoding for alternatives >127 (tag 102)
PlutusData general = ConstrPlutusData.of(200, someFields);
```

### Modeling Optional Values

```java
// Optional type: None = alternative 1, Some = alternative 0
public class OptionalValue {
    public static PlutusData none() {
        return ConstrPlutusData.of(1); // No fields
    }
    
    public static PlutusData some(PlutusData value) {
        return ConstrPlutusData.of(0, value); // One field
    }
}

// Usage
PlutusData noValue = OptionalValue.none();
PlutusData hasValue = OptionalValue.some(BigIntPlutusData.of(100));
```

### Modeling Sum Types

```java
// Result type: Success = 0, Error = 1
public static PlutusData success(PlutusData result) {
    return ConstrPlutusData.of(0, result);
}

public static PlutusData error(String message) {
    return ConstrPlutusData.of(1, BytesPlutusData.of(message));
}

// Color enumeration
public static PlutusData red() { return ConstrPlutusData.of(0); }
public static PlutusData green() { return ConstrPlutusData.of(1); }
public static PlutusData blue() { return ConstrPlutusData.of(2); }
public static PlutusData rgb(int r, int g, int b) {
    return ConstrPlutusData.of(3,
        BigIntPlutusData.of(r),
        BigIntPlutusData.of(g), 
        BigIntPlutusData.of(b)
    );
}
```

### Working with Constructor Data

```java
ConstrPlutusData constrData = (ConstrPlutusData) plutusData;

// Get alternative number
int alternative = constrData.getAlternative();

// Get fields
List<PlutusData> fields = constrData.getData();

// Pattern matching example
switch (alternative) {
    case 0: // Success case
        PlutusData result = fields.get(0);
        break;
    case 1: // Error case  
        String errorMsg = new String(((BytesPlutusData) fields.get(0)).getValue());
        break;
}
```

## ListPlutusData

Represents homogeneous collections of PlutusData elements.

### Basic List Operations

```java
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import java.util.Arrays;

// Create from varargs
PlutusData numbers = ListPlutusData.of(
    BigIntPlutusData.of(1),
    BigIntPlutusData.of(2),
    BigIntPlutusData.of(3)
);

// Create from List
List<PlutusData> items = Arrays.asList(
    BytesPlutusData.of("apple"),
    BytesPlutusData.of("banana"),
    BytesPlutusData.of("cherry")
);
PlutusData fruits = ListPlutusData.of(items);

// Empty list
PlutusData emptyList = ListPlutusData.of();
```

### Nested Lists

```java
// List of lists (matrix)
PlutusData matrix = ListPlutusData.of(
    ListPlutusData.of(BigIntPlutusData.of(1), BigIntPlutusData.of(2)),
    ListPlutusData.of(BigIntPlutusData.of(3), BigIntPlutusData.of(4))
);

// Mixed content (though not type-safe)
PlutusData mixed = ListPlutusData.of(
    BigIntPlutusData.of(42),
    BytesPlutusData.of("text"),
    ConstrPlutusData.of(0, BigIntPlutusData.of(100))
);
```

### Working with Lists

```java
ListPlutusData listData = (ListPlutusData) plutusData;

// Get all elements
List<PlutusData> elements = listData.getData();

// Iterate over elements
for (PlutusData element : elements) {
    if (element instanceof BigIntPlutusData) {
        BigInteger value = ((BigIntPlutusData) element).getValue();
        System.out.println("Number: " + value);
    }
}

// Size check
int size = elements.size();
boolean isEmpty = elements.isEmpty();
```

### Chunked Encoding

For very large lists, ListPlutusData supports chunked encoding:

```java
// Large list - may use chunked encoding automatically
List<PlutusData> largeList = new ArrayList<>();
for (int i = 0; i < 10000; i++) {
    largeList.add(BigIntPlutusData.of(i));
}
PlutusData chunkedList = ListPlutusData.of(largeList);
```

## MapPlutusData

Represents key-value mappings where both keys and values are PlutusData.

### Basic Map Operations

```java
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;

// Create new map
MapPlutusData map = new MapPlutusData();

// Add key-value pairs
map.put(BytesPlutusData.of("name"), BytesPlutusData.of("Alice"));
map.put(BytesPlutusData.of("age"), BigIntPlutusData.of(30));
map.put(BigIntPlutusData.of(42), BytesPlutusData.of("answer"));

// Create from Map
Map<PlutusData, PlutusData> dataMap = new LinkedHashMap<>();
dataMap.put(BytesPlutusData.of("key1"), BigIntPlutusData.of(100));
dataMap.put(BytesPlutusData.of("key2"), BytesPlutusData.of("value2"));
PlutusData mapData = MapPlutusData.of(dataMap);
```

### Complex Key Types

```java
// Use constructor data as keys
PlutusData personKey = ConstrPlutusData.of(0,
    BytesPlutusData.of("Alice"),
    BigIntPlutusData.of(30)
);

// Use lists as keys
PlutusData coordinates = ListPlutusData.of(
    BigIntPlutusData.of(10),
    BigIntPlutusData.of(20)
);

MapPlutusData complexMap = new MapPlutusData();
complexMap.put(personKey, BytesPlutusData.of("Person record"));
complexMap.put(coordinates, BytesPlutusData.of("Location data"));
```

### Working with Maps

```java
MapPlutusData mapData = (MapPlutusData) plutusData;

// Get underlying map (preserves insertion order)
Map<PlutusData, PlutusData> map = mapData.getData();

// Lookup by key
PlutusData nameKey = BytesPlutusData.of("name");
PlutusData nameValue = map.get(nameKey);

// Iterate over entries
for (Map.Entry<PlutusData, PlutusData> entry : map.entrySet()) {
    PlutusData key = entry.getKey();
    PlutusData value = entry.getValue();
    // Process key-value pair
}

// Check if key exists
boolean hasAge = map.containsKey(BytesPlutusData.of("age"));
```

### Nested Maps and Complex Structures

```java
// Map containing other complex types
MapPlutusData userProfile = new MapPlutusData();

// Personal info as a constructor
PlutusData personalInfo = ConstrPlutusData.of(0,
    BytesPlutusData.of("Alice"),
    BigIntPlutusData.of(30),
    BytesPlutusData.of("Engineer")
);

// Preferences as a list
PlutusData preferences = ListPlutusData.of(
    BytesPlutusData.of("dark_mode"),
    BytesPlutusData.of("email_notifications")
);

// Settings as nested map
MapPlutusData settings = new MapPlutusData();
settings.put(BytesPlutusData.of("theme"), BytesPlutusData.of("dark"));
settings.put(BytesPlutusData.of("language"), BytesPlutusData.of("en"));

userProfile.put(BytesPlutusData.of("personal"), personalInfo);
userProfile.put(BytesPlutusData.of("preferences"), preferences);
userProfile.put(BytesPlutusData.of("settings"), settings);
```

## POJO to PlutusData Conversion

The library provides automatic conversion between Java POJOs and PlutusData using the `PlutusObjectConverter` interface.

### Default Converter

```java
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.impl.DefaultPlutusObjectConverter;

PlutusObjectConverter converter = new DefaultPlutusObjectConverter();

// Convert POJO to PlutusData
MyObject obj = new MyObject();
PlutusData plutusData = converter.toPlutusData(obj);

// Convert PlutusData back to POJO
MyObject restored = converter.toJavaObject(plutusData, MyObject.class);
```

### Supported Java Types

The default converter handles these types automatically:

```java
// Primitive types → BigIntPlutusData
Integer intValue = 42;
Long longValue = 1234567890L;
BigInteger bigValue = new BigInteger("999999999999999999");

// Strings and byte arrays → BytesPlutusData
String text = "Hello World";
byte[] bytes = {0x01, 0x02, 0x03};

// Collections → ListPlutusData
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
Set<String> tags = Set.of("tag1", "tag2", "tag3");

// Maps → MapPlutusData
Map<String, Integer> scores = Map.of("Alice", 100, "Bob", 95);

// Optional → ConstrPlutusData (0 for Some, 1 for None)
Optional<String> present = Optional.of("value");
Optional<String> absent = Optional.empty();

// Convert all
PlutusData intData = converter.toPlutusData(intValue);
PlutusData textData = converter.toPlutusData(text);
PlutusData listData = converter.toPlutusData(numbers);
PlutusData mapData = converter.toPlutusData(scores);
PlutusData optData = converter.toPlutusData(present);
```

### Custom POJO Conversion

For custom classes, use the annotation system:

```java
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
@Constr(alternative = 0) // Maps to ConstrPlutusData with alternative 0
public class Person {
    @PlutusField(order = 0) // Field order in constructor
    private String name;
    
    @PlutusField(order = 1)
    private Integer age;
    
    @PlutusField(order = 2)
    private List<String> hobbies;
    
    // This field will be ignored
    private transient String tempData;
}

// Usage
Person alice = Person.builder()
    .name("Alice")
    .age(30)
    .hobbies(Arrays.asList("reading", "coding"))
    .build();

PlutusData personData = converter.toPlutusData(alice);
// Results in: ConstrPlutusData(alternative=0, [
//   BytesPlutusData("Alice"),
//   BigIntPlutusData(30),
//   ListPlutusData([BytesPlutusData("reading"), BytesPlutusData("coding")])
// ])

Person restored = converter.toJavaObject(personData, Person.class);
```

### Handling Enums

```java
public enum Color {
    RED, GREEN, BLUE, RGB
}

@Data
@Constr(alternative = 0)
public class ColorData {
    @PlutusField
    private Color simpleColor; // Converted to BigIntPlutusData (ordinal)
}

// Or use explicit enum mapping
@Data
public class ColorChoice {
    @PlutusField
    private Color color;
}

// Configure converter for enum handling
PlutusObjectConverter converter = new DefaultPlutusObjectConverter() {
    @Override
    public PlutusData toPlutusData(Object obj) {
        if (obj instanceof Color) {
            Color color = (Color) obj;
            return BigIntPlutusData.of(color.ordinal());
        }
        return super.toPlutusData(obj);
    }
};
```

### Advanced POJO Patterns

```java
// Sealed class pattern using different alternatives
@Constr(alternative = 0)
public class Success {
    @PlutusField
    private String result;
}

@Constr(alternative = 1) 
public class Error {
    @PlutusField
    private String message;
    
    @PlutusField
    private Integer code;
}

// Recursive data structures
@Data
@Constr(alternative = 0)
public class TreeNode {
    @PlutusField
    private String value;
    
    @PlutusField
    private List<TreeNode> children; // Recursive reference
}
```

## Serialization and JSON Support

### CBOR Serialization

All PlutusData types support CBOR serialization:

```java
// Serialize to CBOR bytes
byte[] cborBytes = plutusData.serialize();

// Serialize to hex string
String hexString = plutusData.serializeToHex();

// Deserialize from CBOR
PlutusData restored = PlutusData.deserialize(cborBytes);

// Calculate datum hash (Blake2b-256 of CBOR)
String datumHash = plutusData.getDatumHash();
```

### JSON Conversion

Convert PlutusData to/from JSON following Cardano CLI schema:

```java
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;

// Convert to JSON
String json = PlutusDataJsonConverter.toJson(plutusData);

// Convert from JSON  
PlutusData fromJson = PlutusDataJsonConverter.fromJson(json);

// Example JSON output
{
  "constructor": 0,
  "fields": [
    {"bytes": "416c696365"},  // "Alice" in hex
    {"int": 30},
    {
      "list": [
        {"bytes": "72656164696e67"},  // "reading" in hex
        {"bytes": "636f64696e67"}     // "coding" in hex
      ]
    }
  ]
}
```

### Pretty Printing

For human-readable output:

```java
import com.bloxbean.cardano.client.plutus.util.PlutusDataPrettyPrinter;

// Pretty print with UTF-8 conversion for readable strings
String readable = PlutusDataPrettyPrinter.toJson(plutusData);

// Example output - converts hex to UTF-8 when possible
{
  "constructor": 0,
  "fields": [
    {"bytes": "Alice"},     // Converted from hex to UTF-8
    {"int": 30},
    {
      "list": [
        {"bytes": "reading"}, // Converted from hex to UTF-8
        {"bytes": "coding"}   // Converted from hex to UTF-8
      ]
    }
  ]
}
```

## Performance Considerations

### Memory Efficiency

- **Chunked Encoding**: Large byte arrays and lists automatically use chunked encoding
- **Lazy Deserialization**: PlutusData objects are deserialized on-demand
- **Immutable Objects**: All PlutusData types are immutable, enabling safe sharing

### Serialization Optimization

```java
// For repeated serialization, cache CBOR bytes
class CachedPlutusData {
    private final PlutusData data;
    private byte[] cachedCbor;
    
    public byte[] serialize() {
        if (cachedCbor == null) {
            cachedCbor = data.serialize();
        }
        return cachedCbor.clone(); // Return copy for safety
    }
}

// For large datasets, consider streaming
public void processLargeDataset(List<PlutusData> dataset) {
    for (PlutusData item : dataset) {
        // Process items one at a time to avoid memory issues
        String hex = item.serializeToHex();
        // Process hex...
    }
}
```

### Type-Safe Patterns

```java
// Use sealed interfaces for type safety
public sealed interface Result permits Success, Error {
    PlutusData toPlutusData();
}

public record Success(String value) implements Result {
    public PlutusData toPlutusData() {
        return ConstrPlutusData.of(0, BytesPlutusData.of(value));
    }
}

public record Error(String message) implements Result {
    public PlutusData toPlutusData() {
        return ConstrPlutusData.of(1, BytesPlutusData.of(message));
    }
}
```

## Common Patterns and Best Practices

### 1. Validation and Error Handling

```java
public static PlutusData validateAndConvert(Object obj) {
    if (obj == null) {
        throw new IllegalArgumentException("Cannot convert null to PlutusData");
    }
    
    try {
        PlutusObjectConverter converter = new DefaultPlutusObjectConverter();
        return converter.toPlutusData(obj);
    } catch (Exception e) {
        throw new RuntimeException("Failed to convert to PlutusData: " + e.getMessage(), e);
    }
}
```

### 2. Builder Pattern for Complex Data

```java
public class ComplexDataBuilder {
    private final MapPlutusData data = new MapPlutusData();
    
    public ComplexDataBuilder addPerson(String name, int age) {
        PlutusData person = ConstrPlutusData.of(0,
            BytesPlutusData.of(name),
            BigIntPlutusData.of(age)
        );
        data.put(BytesPlutusData.of("person"), person);
        return this;
    }
    
    public ComplexDataBuilder addSettings(Map<String, String> settings) {
        MapPlutusData settingsMap = new MapPlutusData();
        settings.forEach((k, v) -> 
            settingsMap.put(BytesPlutusData.of(k), BytesPlutusData.of(v))
        );
        data.put(BytesPlutusData.of("settings"), settingsMap);
        return this;
    }
    
    public PlutusData build() {
        return data;
    }
}

// Usage
PlutusData complex = new ComplexDataBuilder()
    .addPerson("Alice", 30)
    .addSettings(Map.of("theme", "dark", "lang", "en"))
    .build();
```

### 3. Version-Safe Serialization

```java
@Data
@Constr(alternative = 0)
public class VersionedData {
    @PlutusField(order = 0)
    private Integer version = 1; // Always include version
    
    @PlutusField(order = 1)
    private String data;
    
    // Migration helper
    public static VersionedData fromPlutusData(PlutusData plutusData) {
        // Handle version migration logic
        ConstrPlutusData constr = (ConstrPlutusData) plutusData;
        List<PlutusData> fields = constr.getData();
        
        int version = ((BigIntPlutusData) fields.get(0)).getValue().intValue();
        switch (version) {
            case 1:
                return parseV1(fields);
            case 2:
                return parseV2(fields);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + version);
        }
    }
}
```

This comprehensive guide covers all aspects of working with PlutusData in the Cardano Client Library. The type system is designed to be both powerful and user-friendly, supporting everything from simple value encoding to complex algebraic data types with automatic POJO conversion.