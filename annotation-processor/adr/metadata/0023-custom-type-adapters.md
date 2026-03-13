# ADR 0023: Custom Type Adapters via @MetadataField(adapter = ...)

## Status
Accepted

## Context
The processor supports a fixed set of types (scalars, enums, nested `@MetadataType`, collections, maps, optionals, polymorphic). Users need to serialize types the processor doesn't know about (e.g., `Instant`, `Duration`, `ZonedDateTime`, domain-specific value objects). Without an extension mechanism, users must either wrap these in supported types or manually build metadata maps.

## Decision
Introduce `MetadataTypeAdapter<T>` interface and an `adapter` attribute on `@MetadataField` that lets users plug in custom serialization logic.

### MetadataTypeAdapter Interface
```java
public interface MetadataTypeAdapter<T> {
    Object toMetadata(T value);
    T fromMetadata(Object metadata);
}
```

Located in the `metadata` module alongside other annotations, available at both compile time and runtime.

### Annotation API
```java
public class EpochSecondsAdapter implements MetadataTypeAdapter<Instant> {
    @Override
    public Object toMetadata(Instant value) {
        return BigInteger.valueOf(value.getEpochSecond());
    }
    @Override
    public Instant fromMetadata(Object metadata) {
        return Instant.ofEpochSecond(((BigInteger) metadata).longValue());
    }
}

@MetadataType
public class Event {
    private String name;

    @MetadataField(adapter = EpochSecondsAdapter.class)
    private Instant timestamp;
}
```

### NoAdapter Sentinel
The default value uses a `NoAdapter` inner class instead of `void.class`:
```java
Class<? extends MetadataTypeAdapter<?>> adapter() default NoAdapter.class;

final class NoAdapter implements MetadataTypeAdapter<Void> {
    private NoAdapter() {}
    // throws UnsupportedOperationException
}
```

This enables the type-safe constraint `Class<? extends MetadataTypeAdapter<?>>` — the compiler rejects non-adapter classes at the source level. Sentinel detection uses `Types.isSameType()` for type-safe comparison.

### Adapter Detection
In `MetadataFieldExtractor`, a single-pass `detectAdapter()` method:
1. Reads `@MetadataField` via `AnnotationMirror` API (avoids `MirroredTypeException`)
2. Checks if adapter is the `NoAdapter` sentinel using `isSameType()`
3. Validates adapter implements `MetadataTypeAdapter` via erasure + `isAssignable()`
4. Extracts key, enc, required, defaultValue in the same pass
5. Returns `AdapterDetectionResult` or `null` (no adapter)

Adapter detection runs **before** `detectFieldType()` — fields with adapters bypass all built-in type detection entirely. This means adapters work with any Java type, even types the processor has never seen.

### Code Generation — Static Fields
Each distinct adapter class gets a `private static final` field in the generated converter:
```java
private static final EpochSecondsAdapter _epochSecondsAdapter = new EpochSecondsAdapter();
```

Field names are derived from the adapter FQN: lowercase first letter of the simple class name, prefixed with `_`.

### Code Generation — Serialization
Adapter fields use a `_putAdapted()` helper because `MetadataMap` has no generic `put(String, Object)` overload:
```java
if (event.getTimestamp() != null) {
    _putAdapted(map, "timestamp", _epochSecondsAdapter.toMetadata(event.getTimestamp()));
}
```

The `_putAdapted` helper dispatches at runtime based on the adapter's return type:
```java
private static void _putAdapted(MetadataMap map, String key, Object value) {
    if (value == null) return;
    if (value instanceof String s)           map.put(key, s);
    else if (value instanceof BigInteger bi) map.put(key, bi);
    else if (value instanceof byte[] ba)     map.put(key, ba);
    else if (value instanceof MetadataMap m) map.put(key, m);
    else if (value instanceof MetadataList l)map.put(key, l);
    else throw new IllegalArgumentException("Unsupported adapter result type: " + value.getClass());
}
```

### Code Generation — Deserialization
Uses FQN cast to avoid import issues in generated code:
```java
v = map.get("timestamp");
if (v != null) {
    obj.setTimestamp((java.time.Instant) _epochSecondsAdapter.fromMetadata(v));
}
```

### Dispatch Priority
Adapter is the **first** check in both `emitToMapPut()` and `emitFromMapGet()`, overriding all built-in handling.

### Validation Rules
- Adapter must implement `MetadataTypeAdapter` — compile-time ERROR if not (enforced by annotation type constraint)
- `adapter` + `defaultValue` — compile-time ERROR (mutually exclusive)
- `adapter` + `enc` — compile-time WARNING (enc is ignored when adapter is specified)
- `isScalar()` returns `false` for adapter fields to prevent built-in scalar handling

## Consequences
- Adapters must have a public no-arg constructor (instantiated via `new`)
- Adapter `toMetadata()` must return one of: String, BigInteger, byte[], MetadataMap, or MetadataList
- Adapters work with both POJOs and records
- Adapters on collection elements (`List<@Adapted>`) or map values are not supported — only direct field adapters
- The adapter class must be on the annotation processor's classpath at compile time
- Static adapter fields are shared across all calls (adapters should be stateless or thread-safe)
