## Annotation Processor (cardano-client-annotation-processor)

Contains two annotation processors:

1. **Blueprint Processor** -- Generates Java classes from CIP-57 Plutus blueprint JSON files. See [docs/01-getting-started.md](docs/01-getting-started.md).
2. **Metadata Processor** -- Generates `MetadataConverter` classes for Java POJOs annotated with `@MetadataType`, enabling automatic serialization to/from Cardano transaction metadata (CBOR).

**Dependencies**

---

### Metadata Processor

Annotate a POJO with `@MetadataType` and the processor generates a `{ClassName}MetadataConverter` at compile time with `toMetadataMap()` and `fromMetadataMap()` methods. No runtime reflection.

#### Quick example

```java
@MetadataType(label = 721)
@Data // Lombok
public class NftMetadata {
    private String name;
    private String description;
    private BigInteger edition;
    private List<String> tags;
    private Map<String, BigInteger> attributes;
}
```

#### Supported field types

| Category | Types |
|---|---|
| **Integers** | `int`/`Integer`, `long`/`Long`, `short`/`Short`, `byte`/`Byte`, `BigInteger` |
| **Decimals** | `float`/`Float`, `double`/`Double`, `BigDecimal` (stored as text) |
| **Text** | `String` (auto-chunked at 64 UTF-8 bytes), `char`/`Character` |
| **Boolean** | `boolean`/`Boolean` (stored as integer 0/1) |
| **Binary** | `byte[]` (with optional `enc=STRING_HEX` or `enc=STRING_BASE64`) |
| **Enums** | Any Java enum (stored as name string) |
| **Temporal** | `Instant`, `LocalDate`, `LocalDateTime`, `java.util.Date` |
| **Identity** | `URI`, `URL`, `UUID`, `Currency`, `Locale` |
| **Collections** | `List<T>`, `Set<T>`, `SortedSet<T>`, `Optional<T>` |
| **Maps** | `Map<K, V>` where K is `String`, `Integer`, `Long`, or `BigInteger` |
| **Nested types** | Fields typed as another `@MetadataType` class |
| **Composite** | `List<List<T>>`, `Map<String, List<T>>`, `List<Map<String, V>>`, `Map<String, Map<String, V>>` |

#### Map key types

Maps support four key types:

```java
@MetadataType
public class Config {
    private Map<String, String> settings;         // String keys (most common)
    private Map<Integer, String> levelNames;      // Integer keys
    private Map<Long, BigInteger> timestamps;     // Long keys
    private Map<BigInteger, String> blockData;    // BigInteger keys
}
```

All integer-family keys are stored as Cardano integers on-chain. Other key types (e.g., `Map<Boolean, V>`) produce a compile-time error.

#### Composite types (one level of nesting)

The processor supports one level of collection/map nesting:

```java
@MetadataType
public class Report {
    private List<List<String>> matrix;                // nested list
    private Map<String, List<BigInteger>> scores;     // map to list
    private List<Map<String, String>> records;        // list of maps
    private Map<String, Map<String, String>> nested;  // map of maps
}
```

Deeper nesting (e.g., `List<List<List<T>>>`) is not supported.

#### Negative integer values

Negative `BigInteger`, `Integer`, and `Long` values are correctly serialized using CBOR NegativeInteger encoding. The generated converter uses sign-aware helpers (`putNegative()`/`addNegative()`) automatically -- no special handling is needed in user code.

#### Annotations

| Annotation | Purpose |
|---|---|
| `@MetadataType` | Marks a class for converter generation |
| `@MetadataType(label=N)` | Also generates `toMetadata()`/`fromMetadata()` with the given label |
| `@MetadataField(key="k")` | Overrides the metadata key name |
| `@MetadataField(enc=...)` | Overrides encoding (e.g., `STRING_HEX` for `byte[]`) |
| `@MetadataIgnore` | Excludes a field from serialization |

#### Inheritance

The processor walks the class hierarchy and includes fields from all superclasses. Subclass fields shadow parent fields with the same name. Superclasses do not need `@MetadataType`.

#### Design decisions

Architecture decisions are documented in [adr/metadata/](adr/metadata/).
