# ADR 0023: Custom Type Adapters, Encoder/Decoder, and Adapter Resolver

## Status
Accepted

## Context
The processor supports a fixed set of types (scalars, enums, nested `@MetadataType`, collections, maps, optionals, polymorphic). Users need to serialize types the processor doesn't know about (e.g., `Instant`, `Duration`, `ZonedDateTime`, domain-specific value objects). Without an extension mechanism, users must either wrap these in supported types or manually build metadata maps.

Additionally, some adapters need **runtime state** (e.g., a `NetworkType` configuration, a Spring-managed bean, or database-derived conversion parameters). A no-arg constructor requirement prevents these use cases.

## Decision

### MetadataTypeAdapter Interface
```java
public interface MetadataTypeAdapter<T> {
    Object toMetadata(T value);
    T fromMetadata(Object metadata);
}
```
Located in the `metadata` module alongside other annotations, available at both compile time and runtime.

### @MetadataField(adapter = ...) — Bidirectional Adapter
```java
@MetadataField(adapter = EpochSecondsAdapter.class)
private Instant timestamp;
```

Uses one class for both serialization and deserialization. The adapter class must implement `MetadataTypeAdapter<T>`.

### @MetadataEncoder / @MetadataDecoder — One-Directional Adapters
For fine-grained control, use separate annotations for serialization and deserialization:

```java
// Encoder-only: serialization uses encoder, deserialization falls back to built-in
@MetadataEncoder(SlotToEpochEncoder.class)
@MetadataField(key = "epoch")
private long slot;

// Decoder-only: serialization uses built-in, deserialization uses decoder
@MetadataDecoder(EpochToSlotDecoder.class)
@MetadataField(key = "slot")
private long slot;

// Both: separate classes for each direction
@MetadataEncoder(SlotToEpochEncoder.class)
@MetadataDecoder(EpochToSlotDecoder.class)
@MetadataField(key = "epoch")
private long slot;
```

Both annotations reference a `Class<? extends MetadataTypeAdapter<?>>`. Only the relevant method is called:
- `@MetadataEncoder` → calls `toMetadata()`
- `@MetadataDecoder` → calls `fromMetadata()`

**Mutual exclusivity**: `@MetadataEncoder`/`@MetadataDecoder` cannot coexist with `@MetadataField(adapter = ...)` on the same field. The processor reports a compile-time error.

### NoAdapter Sentinel
The default value for `@MetadataField(adapter = ...)` uses a `NoAdapter` inner class:
```java
Class<? extends MetadataTypeAdapter<?>> adapter() default NoAdapter.class;

final class NoAdapter implements MetadataTypeAdapter<Void> {
    private NoAdapter() {}
    // throws UnsupportedOperationException
}
```
Sentinel detection uses `Types.isSameType()` for type-safe comparison.

### Adapter Detection
In `MetadataTypeDetector`:
1. `detectAdapter()` reads `@MetadataField(adapter = ...)` via `AnnotationMirror` API
2. `detectEncoderDecoder()` reads `@MetadataEncoder` and `@MetadataDecoder` on the field
3. Both validate the class implements `MetadataTypeAdapter` via erasure + `isAssignable()`
4. `detectEncoderDecoder()` checks mutual exclusivity with `@MetadataField(adapter = ...)`

In `MetadataFieldExtractor`, adapter detection runs **before** normal type detection — adapter/encoder/decoder fields bypass all built-in type detection entirely.

### MetadataAdapterResolver — Dependency Injection

```java
public interface MetadataAdapterResolver {
    <T> T resolve(Class<T> adapterClass);
}
```

The resolver is the integration point for DI frameworks. A Spring implementation:
```java
MetadataAdapterResolver resolver = ctx::getBean;
```

`DefaultAdapterResolver` is the built-in implementation that uses `cls.getDeclaredConstructor().newInstance()`.

### Code Generation — Instance Fields and Dual Constructors

Each distinct adapter/encoder/decoder class gets a `private final` instance field:
```java
private final EpochSecondsAdapter _epochSecondsAdapter;
```

The generated converter has two constructors (only when adapter/encoder/decoder fields exist):
```java
// No-arg: backward compatible, uses DefaultAdapterResolver
public EventMetadataConverter() {
    this(DefaultAdapterResolver.INSTANCE);
}

// Resolver: adapter instances obtained from the resolver
public EventMetadataConverter(MetadataAdapterResolver resolver) {
    _epochSecondsAdapter = resolver.resolve(EpochSecondsAdapter.class);
}
```

When no adapter/encoder/decoder fields exist, no constructors are generated (backward compatible).

Field names are derived from the adapter FQN: lowercase first letter of the simple class name, prefixed with `_`.

### Code Generation — Serialization

Adapter and encoder fields use a `_putAdapted()` instance helper:
```java
if (event.getTimestamp() != null) {
    _putAdapted(map, "timestamp", _epochSecondsAdapter.toMetadata(event.getTimestamp()));
}
```

The `_putAdapted` helper dispatches at runtime based on the return type:
```java
private void _putAdapted(MetadataMap map, String key, Object value) {
    if (value instanceof String s)           map.put(key, s);
    else if (value instanceof BigInteger bi) _putBigInt(map, key, bi);
    else if (value instanceof byte[] ba)     map.put(key, ba);
    else if (value instanceof MetadataMap m) map.put(key, m);
    else if (value instanceof MetadataList l)map.put(key, l);
    else throw new IllegalArgumentException("Unsupported adapter result type: " + value.getClass());
}
```

### Code Generation — Deserialization

Adapter and decoder fields use the cast pattern:
```java
v = map.get("timestamp");
if (v != null) {
    obj.setTimestamp((java.time.Instant) _epochSecondsAdapter.fromMetadata(v));
}
```

### Encoder-only / Decoder-only Fallback

When only `@MetadataEncoder` is present, deserialization falls back to built-in type handling for the field's Java type. When only `@MetadataDecoder` is present, serialization falls back to built-in type handling.

### Dispatch Priority
Adapter is the **first** check in both `emitToMapPut()` and `emitFromMapGet()`, followed by encoder/decoder, then all built-in handling.

### Validation Rules
- Adapter must implement `MetadataTypeAdapter` — enforced by annotation type constraint
- `adapter` + `defaultValue` — compile-time ERROR (mutually exclusive)
- `adapter` + `enc` — compile-time WARNING (enc is ignored)
- `@MetadataEncoder`/`@MetadataDecoder` + `@MetadataField(adapter = ...)` — compile-time ERROR
- Encoder/decoder classes must implement `MetadataTypeAdapter` — enforced by annotation type constraint and runtime check

## Consequences
- Adapters **no longer require** a public no-arg constructor when a custom `MetadataAdapterResolver` is provided
- The no-arg converter constructor uses `DefaultAdapterResolver`, which calls `newInstance()` — preserving backward compatibility
- Adapter `toMetadata()` must return one of: String, BigInteger, byte[], MetadataMap, or MetadataList
- Adapters work with both POJOs and records
- Adapters on collection elements (`List<@Adapted>`) or map values are not supported — only direct field adapters
- The adapter class must be on the annotation processor's classpath at compile time
- Instance adapter fields are initialized once per converter instance (adapters should be thread-safe if the converter is shared)
- Encoder-only fields fall back to built-in deserialization; decoder-only fields fall back to built-in serialization
- Converters without any adapter/encoder/decoder fields remain constructor-less (no behavioral change)

## Related
- ADR metadata/0004: @MetadataField(enc=…) Type Override Mechanism
- ADR metadata/0011: Date and Time Type Support
- `MetadataConverterGeneratorTest$CustomAdapter` / `EncoderDecoder` — unit tests
- `SampleEncoderDecoderMetadataConverterTest` — codegen round-trip tests
