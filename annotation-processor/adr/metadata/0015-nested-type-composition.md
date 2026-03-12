# ADR 0015: Nested @MetadataType Composition

## Status
Accepted

## Context
The metadata annotation processor only supported flat POJOs with scalar types, enums, collections, and Optional. Real-world Cardano metadata is hierarchical (e.g., CIP-25 NFTs with nested address objects, orders with customer details). Users had to manually serialize nested objects.

## Decision
A POJO field whose type is another `@MetadataType`-annotated class is automatically recognized and delegates to that type's generated converter. This applies to:

1. **Scalar nested fields** — serialized as a nested `MetadataMap`
2. **Collection elements** (`List<T>`, `Set<T>`, `SortedSet<T>`) — each element serialized via its converter
3. **Optional fields** (`Optional<T>`) — present values serialized via converter, absent omitted

### Detection
During `extractFields()`, the processor checks `typeElement.getAnnotation(MetadataType.class)` on the field's type. For collection/Optional element types, it resolves the `TypeElement` via `getTypeElement()` and checks similarly.

### Code Generation
A new `NestedTypeCodeGen` class handles all nested type contexts:
- `emitSerializeToMap()` — `map.put(key, new XConverter().toMetadataMap(value))`
- `emitSerializeToList()` — `_list.add(new XConverter().toMetadataMap(_el))`
- `emitDeserializeScalar()` — `if (v instanceof MetadataMap) { obj.setX(new XConverter().fromMetadataMap((MetadataMap) v)); }`
- `emitDeserializeElement()` — similar for collection elements
- `emitDeserializeOptional()` — present/absent branches

### MetadataFieldInfo Extensions
- `nestedType` (boolean) — field type is `@MetadataType`
- `elementNestedType` (boolean) — collection/Optional element type is `@MetadataType`
- `nestedConverterFqn` (String) — fully qualified converter class name

## Consequences
- Enables hierarchical metadata structures without manual serialization
- Nested converters are instantiated per call (`new XConverter()`) — stateless, so no caching needed
- Circular references would cause infinite recursion; not guarded (matches Java serialization convention)
- `CollectionCodeGen` and `OptionalCodeGen` updated with nested dispatch branches
