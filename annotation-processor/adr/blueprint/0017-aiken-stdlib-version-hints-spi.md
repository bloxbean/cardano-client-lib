# ADR 0017: Aiken Stdlib Version Hints via AnnotationHintDescriptor SPI

- Status: Accepted
- Date: 2026-03-04
- Owners: Cardano Client Lib maintainers
- Related: ADR-0003 (Shared Blueprint Type Registry), ADR-0009 (Shared Type Converter Architecture), PR #597

## Context

The Aiken standard library has evolved across major versions (V1, V2, V3), and each version uses different schema shapes for the same ledger concepts:

- **V1** (stdlib ≥ 1.9.0, < 2.0.0): `VerificationKeyCredential` / `ScriptCredential` variant names, nested `TransactionId` wrapper in `OutputReference`, `$`-delimited generic refs.
- **V2** (stdlib ≥ 2.0.0, < 3.0.0): `VerificationKey` / `Script` variant names, flat `ByteArray` in `OutputReference`, bare hash refs.
- **V3** (stdlib ≥ 3.0.0): Same variant names as V2 but with namespaced refs (e.g., `cardano/address/PaymentCredential`).

The Shared Blueprint Type Registry (ADR-0003) needs version-aware lookup to map the correct schema signature to the correct shared type. However, the annotation processor module must not depend on `plutus-aiken` — the registry is discovered via `ServiceLoader`. This means the processor cannot reference `@AikenStdlib` or `AikenStdlibVersion` directly.

Additionally, the original `Pair` registry entry was a raw (non-parameterized) type. List schemas with two items should produce parameterized `Pair<T1, T2>` at the type resolution layer, avoiding incorrect raw Pair usage.

## Decision

### 1. `AnnotationHintDescriptor` SPI

Introduce a string-based descriptor that registries use to declare which annotations the processor should read:

```java
public class AnnotationHintDescriptor {
    private final String annotationFqn;  // e.g., "com...AikenStdlib"
    private final String elementName;    // e.g., "value"
    private final String hintKey;        // e.g., "aiken.stdlib.version"
    private final String defaultValue;   // e.g., "V3"
}
```

No class references are used — only fully-qualified name strings. This keeps the processor decoupled from any specific annotation library.

### 2. `BlueprintTypeRegistry.annotationHints()`

The `BlueprintTypeRegistry` SPI gains a default method:

```java
default List<AnnotationHintDescriptor> annotationHints() {
    return Collections.emptyList();
}
```

Registries declare which annotations they need the processor to read. Backward-compatible: existing registries return an empty list.

### 3. `ServiceLoaderSharedTypeLookup.resolveHints()`

The processor's `ServiceLoaderSharedTypeLookup` collects hint descriptors from all loaded registries and resolves them generically using annotation mirrors:

```java
public LookupContext resolveHints(TypeElement typeElement) {
    Map<String, String> hints = new HashMap<>();
    for (BlueprintTypeRegistry registry : registries) {
        for (AnnotationHintDescriptor desc : registry.annotationHints()) {
            hints.put(desc.hintKey(), extractAnnotationValue(typeElement, desc));
        }
    }
    return new LookupContext(null, null, hints);
}
```

`extractAnnotationValue()` inspects annotation mirrors on the `TypeElement`, handles enum values by stripping the package prefix, and falls back to the descriptor's default value when the annotation is absent.

### 4. `LookupContext` Carries Hints

`LookupContext` is extended with an immutable `hints` map:

```java
public class LookupContext {
    private final String namespace;
    private final String blueprintName;
    private final Map<String, String> hints;  // unmodifiable

    public Optional<String> hint(String key) { ... }

    public static final LookupContext EMPTY = new LookupContext(null, null, Map.of());
}
```

The context flows from `BlueprintAnnotationProcessor` through `FieldSpecProcessor`, `ValidatorProcessor`, and `DataTypeProcessUtil` to every registry lookup call.

### 5. `@AikenStdlib` Annotation and `AikenStdlibVersion` Enum

In the `plutus-aiken` module:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface AikenStdlib {
    AikenStdlibVersion value() default AikenStdlibVersion.V3;
}

public enum AikenStdlibVersion {
    V1,   // stdlib >= 1.9.0, < 2.0.0
    V2,   // stdlib >= 2.0.0, < 3.0.0
    V3;   // stdlib >= 3.0.0 (latest)
    public static final AikenStdlibVersion LATEST = V3;
}
```

Usage on a blueprint marker interface:

```java
@Blueprint(fileInResources = "blueprint.json", packageName = "my.generated")
@AikenStdlib(AikenStdlibVersion.V1)
public interface MyOldBlueprint {}
```

### 6. Versioned Buckets in `AikenBlueprintTypeRegistry`

The registry stores mappings in two tiers:

```
AikenBlueprintTypeRegistry
  ├── commonMappings: Map<SchemaSignature, RegisteredType>
  │     (version-independent: Pair, VerificationKey, Script, Signature,
  │      VerificationKeyHash, ScriptHash, DataHash, Hash, PolicyId, AssetName,
  │      IntervalBoundType)
  │
  └── versionedMappings: Map<String, Map<SchemaSignature, RegisteredType>>
        ├── "V1" → {Credential, ReferencedCredential, Address,
        │           OutputReferenceV1, IntervalBound, ValidityRange}
        ├── "V2" → {PaymentCredential (for "Credential" + "PaymentCredential" schemas),
        │           StakeCredential, Address, OutputReference}
        └── "V3" → {PaymentCredential (for "Credential" + "PaymentCredential" schemas),
                    Address, StakeCredential, OutputReference,
                    IntervalBound, ValidityRange}
```

Lookup logic:

```java
public Optional<RegisteredType> lookup(SchemaSignature signature,
                                       BlueprintSchema schema,
                                       LookupContext context) {
    // 1. Check common types (version-independent)
    RegisteredType common = commonMappings.get(signature);
    if (common != null) return Optional.of(common);

    // 2. Version-specific lookup
    String version = context.hint(HINT_STDLIB_VERSION)
                            .orElse(AikenStdlibVersion.LATEST.name());
    Map<SchemaSignature, RegisteredType> vMap = versionedMappings.get(version);
    if (vMap != null) {
        RegisteredType versioned = vMap.get(signature);
        if (versioned != null) return Optional.of(versioned);
    }
    return Optional.empty();
}
```

### 7. Cache Key Includes Hints

`ServiceLoaderSharedTypeLookup` includes context hints in the lookup cache key, preventing cross-version cache collisions (e.g., a V1 `Credential` schema cached separately from a V3 `Credential` schema).

### 8. Parameterized Pair via `SchemaTypeResolver`

List schemas with multiple items are now resolved to parameterized tuple types at the type resolution layer by `SchemaTypeResolver.resolveListType()`:

| Items | Resolved Type |
|-------|--------------|
| 1 | `List<T>` |
| 2 | `Pair<T1, T2>` |
| 3 | `Triple<T1, T2, T3>` |
| 4 | `Quartet<T1, T2, T3, T4>` |
| 5 | `Quintet<T1, T2, T3, T4, T5>` |

The `Pair` entry in `commonMappings` remains for backward compatibility with blueprints that match the two-ByteArray tuple signature. However, most list-with-2-items schemas now bypass the registry entirely and produce correctly parameterized `Pair<T1, T2>` via `SchemaTypeResolver`.

## Rationale

1. **Framework decoupling**: `AnnotationHintDescriptor` uses strings, not class references. The annotation processor never imports `@AikenStdlib` or `AikenStdlibVersion`.
2. **Extensibility**: Any future registry (e.g., PlutusTx, Helios) can declare its own hint annotations without modifying the processor.
3. **Version isolation**: Separate maps per version prevent V1 schemas from accidentally matching V3 shared types (or vice versa).
4. **Backward compatibility**: `annotationHints()` defaults to empty list; existing registries work without changes. `LookupContext.EMPTY` provides a no-hints fallback. Default version is `LATEST` (V3).
5. **Parameterized correctness**: `Pair<byte[], byte[]>` is type-safe; raw `Pair` lost generic information and caused incorrect `toPlutusData()` generation in some cases.

## Consequences

### Positive
- Version-aware registry lookup enables correct shared type matching across Aiken stdlib V1/V2/V3.
- The processor remains fully decoupled from Aiken-specific types.
- New versioned shared types (PaymentCredential, StakeCredential, OutputReference, IntervalBound, ValidityRange, PolicyId, AssetName) reduce generated code for standard ledger types.
- Parameterized Pair types produce correct serialization code.

### Negative
- Additional indirection through `AnnotationHintDescriptor` and hint maps adds complexity.
  - **Mitigation**: The indirection is minimal and well-encapsulated in `ServiceLoaderSharedTypeLookup`.
- Registries must maintain schema builders for each version they support, increasing maintenance surface.
  - **Mitigation**: Schema shapes change only with Aiken major releases; version buckets are clearly separated.
- Cache keys grow with each hint, increasing memory slightly.
  - **Mitigation**: Hint maps are small (typically 1 entry) and cached per blueprint, not per field.

## References

- `AnnotationHintDescriptor` — string-based SPI descriptor
- `BlueprintTypeRegistry.annotationHints()` — SPI extension point
- `ServiceLoaderSharedTypeLookup.resolveHints()` — generic annotation mirror resolution
- `LookupContext` — hint-carrying context
- `AikenBlueprintTypeRegistry` — versioned bucket implementation
- `AikenStdlib`, `AikenStdlibVersion` — Aiken-specific annotation and enum
- `SchemaTypeResolver.resolveListType()` — parameterized Pair/Triple/Quartet/Quintet resolution
- ADR-0003: Shared Blueprint Type Registry (registry foundation)
- ADR-0009: Shared Type Converter Architecture (converter generation)
