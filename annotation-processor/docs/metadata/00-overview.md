# Metadata Annotation Processor — Overview

The metadata annotation processor is a compile-time code generator that bridges the gap between Java domain classes and Cardano transaction metadata. Annotate your POJOs or records with `@MetadataType`, build the project, and the processor generates a `{ClassName}MetadataConverter` class that handles lossless round-trip serialization to and from the on-chain metadata format — no manual CBOR encoding required.

## Why Use It?

Cardano transaction metadata is stored as nested maps of integers, strings, bytes, and lists (CBOR). Writing the serialization and deserialization code by hand is tedious, error-prone, and hard to keep in sync as your model evolves. The annotation processor:

- **Eliminates boilerplate** — converter classes are generated automatically at compile time.
- **Stays in sync** — regenerated on every build, so the converter always matches your model.
- **Validates at compile time** — unsupported types, missing constructors, and conflicting options are caught before runtime.
- **Handles the hard parts** — negative `BigInteger` sign handling, string chunking, map key narrowing, nested type recursion, and composite structures are all built in.

## What It Generates

For each `@MetadataType`-annotated class, the processor emits one converter class:

```
com/example/
├── TokenInfo.java                          # Your source class
└── TokenInfoMetadataConverter.java         # Generated converter
```

The converter implements `MetadataConverter<T>` with two core methods:

| Method | Direction | Signature |
|--------|-----------|-----------|
| `toMetadataMap` | Java &rarr; chain | `MetadataMap toMetadataMap(T obj)` |
| `fromMetadataMap` | Chain &rarr; Java | `T fromMetadataMap(MetadataMap map)` |

When a label is specified (`@MetadataType(label = 721)`), the converter also implements `LabeledMetadataConverter<T>`, adding `toMetadata` / `fromMetadata` methods that wrap/unwrap the map under the given label key.

## Feature Highlights

| Feature | Description |
|---------|-------------|
| **Scalars** | `String`, `BigInteger`, `int`/`long`/`short`/`byte`, `boolean`, `BigDecimal`, `double`, `float`, `char`, `byte[]`, `URI`, `URL`, `UUID`, `Currency`, `Locale`, `Instant`, `LocalDate`, `LocalDateTime`, `Date` |
| **Collections** | `List<T>`, `Set<T>`, `SortedSet<T>` with scalar, enum, or nested element types |
| **Maps** | `Map<K, V>` with `String`, `Integer`, `Long`, `BigInteger`, or `byte[]` keys |
| **Composites** | `List<List<T>>`, `List<Map<K,V>>`, `Map<K, List<T>>`, `Map<K, Map<K2,V2>>` |
| **Optional** | `Optional<T>` with scalar, enum, or nested inner type |
| **Enums** | Stored as their `name()` string, reconstructed via `valueOf` |
| **Nested types** | `@MetadataType`-annotated fields are recursively serialized via their own converter |
| **Polymorphic types** | `@MetadataDiscriminator` on interfaces/abstract classes dispatches to concrete subtypes |
| **Custom adapters** | `@MetadataEncoder` / `@MetadataDecoder` for full control over serialization |
| **Encoding control** | `@MetadataField(enc = STRING)` forces string encoding; `STRING_HEX` / `STRING_BASE64` for `byte[]` |
| **Required fields** | `@MetadataField(required = true)` throws on missing keys during deserialization |
| **Default values** | `@MetadataField(defaultValue = "...")` provides fallbacks |
| **Records** | Java records are fully supported, including canonical constructor deserialization |
| **Lombok** | `@Data`, `@Getter`/`@Setter`, `@NoArgsConstructor` are detected automatically |
| **Inheritance** | Fields from superclasses are included; child fields shadow parent fields |
| **Labels** | `@MetadataType(label = N)` generates top-level `Metadata` wrapping methods |

## How It Works

```
  ┌──────────────────┐     javac annotation       ┌──────────────────────────┐
  │  @MetadataType   │ ──── processing phase ────► │ TokenInfoMetadata        │
  │  TokenInfo.java  │                             │ Converter.java           │
  └──────────────────┘                             └──────────────────────────┘
          │                                                    │
          │  your code                                         │  generated code
          ▼                                                    ▼
  TokenInfo obj = ...                              converter.toMetadataMap(obj)
                                                   converter.fromMetadataMap(map)
```

The processor reads your annotated class at compile time, inspects each field's type, resolves accessor methods (getters/setters, record components, Lombok, or public fields), and emits a converter class that uses the CCL `MetadataMap` / `MetadataList` API to build the on-chain representation.

## Quick Example

```java
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

@MetadataType(label = 42)
public record TokenInfo(
        @MetadataField(key = "policy_id", required = true) String policyId,
        @MetadataField(key = "asset_name", required = true) String assetName,
        int decimals
) {}
```

After building, use the generated converter:

```java
var converter = new TokenInfoMetadataConverter();

// Serialize
Metadata metadata = converter.toMetadata(tokenInfo);
tx.attachMetadata(metadata);

// Deserialize
TokenInfo restored = converter.fromMetadata(metadata);
```

## Documentation Guide

| Document | What You'll Learn |
|----------|-------------------|
| [Getting Started](01-getting-started.md) | Dependencies, first annotated class, build, and end-to-end example |
| [Annotations Reference](02-annotations-reference.md) | Full details on `@MetadataType`, `@MetadataField`, `@MetadataIgnore`, and polymorphic annotations |
| [Generated Converters](03-generated-converters.md) | How to use `MetadataConverter` and `LabeledMetadataConverter` |
| [Supported Types](04-supported-types.md) | Complete type mapping table with on-chain representations |
| [Class Support](05-class-support.md) | Records, POJOs, Lombok, inheritance, and accessor resolution |
| [Advanced Topics](06-advanced-topics.md) | Custom adapters, polymorphic types, composites, and real-world examples |
