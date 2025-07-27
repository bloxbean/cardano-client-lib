---
description: Advanced PlutusData annotation system for automatic POJO conversion with code generation
sidebar_label: Annotation System
sidebar_position: 3
---

# PlutusData Annotation System

The Cardano Client Library provides a powerful annotation-based system for automatic conversion between Java POJOs and PlutusData. This system includes both runtime conversion and compile-time code generation for optimal performance.

:::tip Prerequisites
Before using annotations, ensure you understand the basic [PlutusData Types](./plutus-data-types.md) and their serialization patterns.
:::

## Overview

The annotation system provides two levels of PlutusData integration:

1. **Runtime Conversion** - Using `DefaultPlutusObjectConverter` with annotations
2. **Compile-time Generation** - Annotation processor that generates optimized converter classes

Both approaches use the same annotations but offer different trade-offs between convenience and performance.

## Core Annotations

### @Constr - Constructor Mapping

The `@Constr` annotation maps Java classes to `ConstrPlutusData` with specified constructor alternatives.

```java
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;

@Constr(alternative = 0)
public class Person {
    @PlutusField(order = 0)
    private String name;
    
    @PlutusField(order = 1) 
    private String publicKey;
    
    @PlutusField(order = 2)
    private int age;
    
    // Constructors, getters, and setters
    public Person() {}
    
    public Person(String name, String publicKey, int age) {
        this.name = name;
        this.publicKey = publicKey;
        this.age = age;
    }
    
    // Standard getters and setters...
}
```

**Key Properties:**
- `alternative` - Constructor alternative number (default: 0)
- Maps to `ConstrPlutusData(alternative, [field1, field2, ...])`
- Fields are ordered by `@PlutusField(order = n)` annotation

### @PlutusField - Field Mapping

Controls how individual fields are converted to PlutusData.

```java
@Constr(alternative = 1)
public class TokenInfo {
    @PlutusField(order = 0)
    private String policyId;
    
    @PlutusField(order = 1)
    private String assetName;
    
    @PlutusField(order = 2, type = FieldType.BYTES)
    private String metadata; // Force bytes encoding
    
    @PlutusField(order = 3)
    private Optional<Long> amount; // Becomes Some/None constructor
    
    // Fields without @PlutusField are ignored in code generation
    private transient String tempData;
}
```

**Field Properties:**
- `order` - Position in constructor fields (required)
- `type` - Override default type conversion (optional)

**Supported FieldType Options:**
- `FieldType.DEFAULT` - Automatic type detection
- `FieldType.BYTES` - Force BytesPlutusData encoding
- `FieldType.INTEGER` - Force BigIntPlutusData encoding

### @PlutusIgnore - Field Exclusion

Explicitly excludes fields from PlutusData conversion (redundant for code generation, but useful for documentation).

```java
@Constr(alternative = 0)
public class UserAccount {
    @PlutusField(order = 0)
    private String accountId;
    
    @PlutusField(order = 1)
    private BigInteger balance;
    
    @PlutusIgnore
    private String password; // Never serialized
    
    @PlutusIgnore
    private Date lastLogin; // Never serialized
}
```

## Runtime Conversion

Use `DefaultPlutusObjectConverter` for flexible runtime conversion:

```java
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.impl.DefaultPlutusObjectConverter;

// Create converter
PlutusObjectConverter converter = new DefaultPlutusObjectConverter();

// Create POJO
Person person = new Person("Alice", "6dcf4915b05a1358d86e87d352f2fa7392fa6c092b337af705b577822d06d17e", 25);

// Convert to PlutusData
ConstrPlutusData plutusData = (ConstrPlutusData) converter.toPlutusData(person);

// Convert back to POJO
Person restored = converter.toJavaObject(plutusData, Person.class);

// Verify roundtrip
assert person.getName().equals(restored.getName());
assert person.getAge() == restored.getAge();
```

### Advanced Runtime Patterns

```java
// Handle complex nested structures
@Constr(alternative = 0)
public class Order {
    @PlutusField(order = 0)
    private String orderId;
    
    @PlutusField(order = 1)
    private Person customer;
    
    @PlutusField(order = 2)
    private List<TokenInfo> items;
    
    @PlutusField(order = 3)
    private Map<String, String> metadata;
}

// Runtime conversion handles nesting automatically
Order order = new Order(
    "ORD-001",
    new Person("Bob", "abc123...", 30),
    List.of(new TokenInfo("policy1", "token1", "meta", Optional.of(100L))),
    Map.of("priority", "high", "source", "web")
);

PlutusData orderData = converter.toPlutusData(order);
```

## Compile-time Code Generation

For production applications requiring optimal performance, use the annotation processor to generate dedicated converter classes.

### Setup Dependencies

Add the annotation processor to your build configuration:

**Maven:**
```xml
<dependencies>
    <!-- Core library -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
        <version>${cardano.client.version}</version>
    </dependency>
    
    <!-- Annotation processor -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-annotation-processor</artifactId>
        <version>${cardano.client.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>11</source>
                <target>11</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.bloxbean.cardano</groupId>
                        <artifactId>cardano-client-annotation-processor</artifactId>
                        <version>${cardano.client.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Gradle:**
```gradle
dependencies {
    implementation "com.bloxbean.cardano:cardano-client-lib:${cardanoClientVersion}"
    annotationProcessor "com.bloxbean.cardano:cardano-client-annotation-processor:${cardanoClientVersion}"
    compileOnly "com.bloxbean.cardano:cardano-client-annotation-processor:${cardanoClientVersion}"
}
```

### Generated Converter Usage

The annotation processor generates optimized converter classes for each `@Constr` annotated class:

```java
// Original annotated class
@Constr(alternative = 0)
public class Person {
    @PlutusField(order = 0)
    private String name;
    
    @PlutusField(order = 1)
    private String publicKey;
    
    @PlutusField(order = 2)
    private int age;
    
    // Standard constructors and accessors...
}

// Generated PersonConverter class (created automatically)
public class PersonConverter {
    public ConstrPlutusData toPlutusData(Person person) {
        // Optimized conversion implementation
    }
    
    public Person fromPlutusData(ConstrPlutusData plutusData) {
        // Optimized deserialization implementation
    }
    
    public String serializeToHex(Person person) {
        return toPlutusData(person).serializeToHex();
    }
    
    public Person deserialize(String hex) {
        PlutusData data = PlutusData.deserialize(HexUtil.decodeHexString(hex));
        return fromPlutusData((ConstrPlutusData) data);
    }
}
```

### Using Generated Converters

```java
// Use the generated converter
PersonConverter converter = new PersonConverter();

// Create object
Person person = new Person("Alice", "6dcf4915b05a1358d86e87d352f2fa7392fa6c092b337af705b577822d06d17e", 25);

// Convert to PlutusData (optimized)
ConstrPlutusData plutusData = converter.toPlutusData(person);

// Serialize directly to hex
String hexData = converter.serializeToHex(person);

// Deserialize from hex
Person restored = converter.deserialize(hexData);

// Convert back from PlutusData
Person fromPlutus = converter.fromPlutusData(plutusData);
```

## Advanced Patterns

### Sum Types and Sealed Classes

Model algebraic data types with multiple alternatives:

```java
// Base marker interface
public sealed interface PaymentMethod permits CreditCard, BankTransfer, Cryptocurrency {
}

@Constr(alternative = 0)
public final class CreditCard implements PaymentMethod {
    @PlutusField(order = 0)
    private String cardNumber;
    
    @PlutusField(order = 1)
    private String expiryDate;
    
    // Constructor and accessors...
}

@Constr(alternative = 1)
public final class BankTransfer implements PaymentMethod {
    @PlutusField(order = 0)
    private String accountNumber;
    
    @PlutusField(order = 1)
    private String routingNumber;
    
    // Constructor and accessors...
}

@Constr(alternative = 2)
public final class Cryptocurrency implements PaymentMethod {
    @PlutusField(order = 0)
    private String walletAddress;
    
    @PlutusField(order = 1)
    private String currencyType;
    
    // Constructor and accessors...
}

// Usage with runtime converter
PaymentMethod payment = new CreditCard("1234-5678-9012-3456", "12/25");
PlutusData paymentData = converter.toPlutusData(payment);
// Results in: ConstrPlutusData(0, ["1234-5678-9012-3456", "12/25"])
```

### Optional and Nullable Fields

Handle Optional and nullable values elegantly:

```java
@Constr(alternative = 0)
public class UserProfile {
    @PlutusField(order = 0)
    private String userId;
    
    @PlutusField(order = 1)
    private Optional<String> email; // Some/None pattern
    
    @PlutusField(order = 2)
    private Optional<Integer> age;
    
    @PlutusField(order = 3)
    private List<String> tags; // Empty list if no tags
}

// Example usage
UserProfile profile = new UserProfile(
    "user123",
    Optional.of("user@example.com"), // Some("user@example.com")
    Optional.empty(),                // None
    List.of("premium", "verified")
);

// Converts to:
// ConstrPlutusData(0, [
//   BytesPlutusData("user123"),
//   ConstrPlutusData(0, [BytesPlutusData("user@example.com")]), // Some
//   ConstrPlutusData(1, []),                                    // None
//   ListPlutusData([BytesPlutusData("premium"), BytesPlutusData("verified")])
// ])
```

### Custom Field Conversion

Override default conversion behavior:

```java
@Constr(alternative = 0)
public class TimestampedData {
    @PlutusField(order = 0)
    private String data;
    
    @PlutusField(order = 1, type = FieldType.INTEGER)
    private LocalDateTime timestamp; // Custom conversion needed
    
    // Custom converter for LocalDateTime
    public static class TimestampConverter extends DefaultPlutusObjectConverter {
        @Override
        public PlutusData toPlutusData(Object obj) {
            if (obj instanceof LocalDateTime) {
                LocalDateTime dt = (LocalDateTime) obj;
                long epochSecond = dt.toEpochSecond(ZoneOffset.UTC);
                return BigIntPlutusData.of(epochSecond);
            }
            return super.toPlutusData(obj);
        }
        
        @Override
        public <T> T toJavaObject(PlutusData plutusData, Class<T> clazz) {
            if (clazz == LocalDateTime.class && plutusData instanceof BigIntPlutusData) {
                long epochSecond = ((BigIntPlutusData) plutusData).getValue().longValue();
                return clazz.cast(LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC));
            }
            return super.toJavaObject(plutusData, clazz);
        }
    }
}
```

### Versioning and Migration

Design classes for forward compatibility:

```java
@Constr(alternative = 0)
public class VersionedContract {
    @PlutusField(order = 0)
    private Integer version = 2; // Current version
    
    @PlutusField(order = 1)
    private String contractId;
    
    @PlutusField(order = 2)
    private Map<String, PlutusData> parameters; // Flexible parameters
    
    // Migration factory method
    public static VersionedContract fromPlutusData(ConstrPlutusData data) {
        List<PlutusData> fields = data.getData();
        int version = ((BigIntPlutusData) fields.get(0)).getValue().intValue();
        
        switch (version) {
            case 1:
                return migrateFromV1(fields);
            case 2:
                return parseV2(fields);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + version);
        }
    }
    
    private static VersionedContract migrateFromV1(List<PlutusData> fields) {
        // Handle v1 to v2 migration
        String contractId = new String(((BytesPlutusData) fields.get(1)).getValue());
        Map<String, PlutusData> params = new HashMap<>();
        // Set default values for new fields
        params.put("feature_flags", ListPlutusData.of());
        
        VersionedContract contract = new VersionedContract();
        contract.version = 2;
        contract.contractId = contractId;
        contract.parameters = params;
        return contract;
    }
}
```

## Performance Considerations

### Code Generation vs Runtime Conversion

**Generated Converters (Recommended for Production):**
- ✅ Zero reflection overhead
- ✅ Compile-time type safety
- ✅ Optimized serialization paths
- ✅ Minimal runtime dependencies
- ❌ Less flexible (fixed at compile time)

**Runtime Conversion (Good for Development):**
- ✅ Maximum flexibility
- ✅ Dynamic type handling
- ✅ No build setup required
- ❌ Reflection overhead
- ❌ Runtime type discovery

### Memory and CPU Optimization

```java
// For frequently converted objects, consider caching
public class CachedPersonConverter {
    private final PersonConverter converter = new PersonConverter();
    private final Map<Person, ConstrPlutusData> cache = new ConcurrentHashMap<>();
    
    public ConstrPlutusData toPlutusDataCached(Person person) {
        return cache.computeIfAbsent(person, converter::toPlutusData);
    }
}

// For batch operations, reuse converter instances
PersonConverter converter = new PersonConverter();
List<ConstrPlutusData> batch = people.stream()
    .map(converter::toPlutusData)
    .collect(Collectors.toList());
```

## Best Practices

### 1. Design for Immutability

```java
@Constr(alternative = 0)
public final class ImmutableToken {
    @PlutusField(order = 0)
    private final String policyId;
    
    @PlutusField(order = 1)
    private final String assetName;
    
    @PlutusField(order = 2)
    private final Long amount;
    
    public ImmutableToken(String policyId, String assetName, Long amount) {
        this.policyId = Objects.requireNonNull(policyId);
        this.assetName = Objects.requireNonNull(assetName);
        this.amount = Objects.requireNonNull(amount);
    }
    
    // Only getters, no setters
    public String getPolicyId() { return policyId; }
    public String getAssetName() { return assetName; }
    public Long getAmount() { return amount; }
}
```

### 2. Validate Data Integrity

```java
@Constr(alternative = 0)
public class ValidatedAddress {
    @PlutusField(order = 0)
    private String bech32Address;
    
    public ValidatedAddress(String bech32Address) {
        if (!isValidBech32(bech32Address)) {
            throw new IllegalArgumentException("Invalid bech32 address: " + bech32Address);
        }
        this.bech32Address = bech32Address;
    }
    
    private boolean isValidBech32(String address) {
        // Validation logic
        return address != null && address.startsWith("addr");
    }
}
```

### 3. Document Constructor Alternatives

```java
/**
 * Payment status with different states:
 * - Alternative 0: Pending (no additional data)
 * - Alternative 1: Completed (with transaction hash)
 * - Alternative 2: Failed (with error message)
 * - Alternative 3: Cancelled (with cancellation reason)
 */
public sealed interface PaymentStatus permits Pending, Completed, Failed, Cancelled {
}

@Constr(alternative = 0)
public record Pending() implements PaymentStatus {}

@Constr(alternative = 1)
public record Completed(@PlutusField(order = 0) String txHash) implements PaymentStatus {}

@Constr(alternative = 2)
public record Failed(@PlutusField(order = 0) String errorMessage) implements PaymentStatus {}

@Constr(alternative = 3)
public record Cancelled(@PlutusField(order = 0) String reason) implements PaymentStatus {}
```

This annotation system provides a powerful and flexible way to work with PlutusData while maintaining type safety and performance. Choose the approach that best fits your application's requirements: runtime conversion for flexibility or code generation for optimal performance.
