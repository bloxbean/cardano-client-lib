# ADR 0018: Converter Code Generator Decomposition

- Status: Accepted
- Date: 2026-03-06
- Owners: Cardano Client Lib maintainers
- Related: ADR-0002 (Code Generation Pipeline), ADR-0015 (DataType Processor Strategy), ADR-0011 (Multi-Item Tuple Support)

## Context

`ConverterCodeGenerator` was a 1,813-line monolithic class responsible for generating `toPlutusData()` and `fromPlutusData()` converter code for all schema classifications (CLASS, INTERFACE, ENUM) and all 13 field types (INTEGER, BYTES, STRING, BOOL, LIST, MAP, PLUTUSDATA, OPTIONAL, PAIR, TRIPLE, QUARTET, QUINTET, CONSTRUCTOR).

Problems with the monolithic design:

1. **Giant switch statement**: A 100+ line type switch in `generateToPlutusDataMethod()` dispatched across all field types with no abstraction.
2. **Tuple code duplication**: Pair, Triple, Quartet, and Quintet each had ~5 nearly identical methods (20+ methods total), differing only in arity and accessor names.
3. **No isolation**: Adding or modifying support for a single type required editing the monolithic class, risking regressions in unrelated types.
4. **Untestable in isolation**: Individual type serialization/deserialization logic could only be tested through end-to-end generated converter output.
5. **Mixed concerns**: Class converter, interface variant converter, and enum converter logic were interleaved in a single file.

This is the same class of problem that ADR-0015 solved for `FieldSpecProcessor` — a monolithic dispatcher that needed the Strategy pattern.

## Decision

Decompose `ConverterCodeGenerator` into a strategy-based architecture with per-type generators, classification-specific builders, and shared infrastructure. The result is 15 focused classes replacing the single monolithic class.

### Package Structure

```
converter/
├── FieldCodeGenerator.java          (strategy interface)
├── FieldCodeGeneratorRegistry.java  (EnumMap dispatch + recursive composition)
├── ClassConverterBuilder.java       (CLASS classification)
├── InterfaceConverterBuilder.java   (INTERFACE classification)
├── EnumConverterBuilder.java        (ENUM classification)
├── SerDeMethodBuilder.java          (shared serialize/deserialize wrappers)
├── FieldAccessor.java               (field access encapsulation)
├── TupleInfo.java                   (arity parameterization enum)
└── type/
    ├── ElementCodeGenerator.java    (functional interface for nested dispatch)
    ├── TupleCodeGenerator.java      (arity-agnostic tuple generation)
    ├── IntegerFieldCodeGen.java
    ├── BytesFieldCodeGen.java
    ├── StringFieldCodeGen.java
    ├── BoolFieldCodeGen.java
    ├── PlutusDataFieldCodeGen.java
    ├── ConstructorFieldCodeGen.java
    ├── ListFieldCodeGen.java        (composite — holds registry reference)
    ├── MapFieldCodeGen.java         (composite — holds registry reference)
    ├── OptionalFieldCodeGen.java    (composite — holds registry reference)
    └── TupleFieldCodeGen.java       (parameterized by TupleInfo)
```

### `FieldCodeGenerator` Strategy Interface

Defines two levels of code generation — top-level (for object fields) and nested (for elements inside collections and tuples):

```java
public interface FieldCodeGenerator {
    // Top-level: generates statements for a field within a class
    CodeBlock generateSerialization(Field field, FieldAccessor accessor);
    CodeBlock generateDeserialization(Field field);

    // Nested: generates expressions for elements inside collections/tuples
    CodeBlock toPlutusDataExpression(FieldType fieldType, String expression);
    CodeBlock fromPlutusDataExpression(FieldType fieldType, String plutusDataVar);
    CodeBlock generateNestedSerialization(FieldType fieldType, String varName,
                                          String accessorExpr);
    CodeBlock generateNestedDeserialization(FieldType fieldType, String varName,
                                            String plutusDataExpr);
}
```

The two-level distinction is key: top-level methods handle field access, null checks, and statement context; nested methods produce expressions that can be composed inside list iteration, map entry processing, or tuple element handling.

### `FieldCodeGeneratorRegistry`

`EnumMap<Type, FieldCodeGenerator>` for O(1) dispatch, mirroring the pattern from ADR-0015's `DataTypeProcessUtil`:

```
┌───────────────────────────────────────────────────────┐
│  FieldCodeGeneratorRegistry                           │
│                                                       │
│  EnumMap<Type, FieldCodeGenerator>                    │
│  ┌─────────────┬────────────────────────────────────┐ │
│  │ INTEGER     │ IntegerFieldCodeGen                │ │
│  │ BYTES       │ BytesFieldCodeGen                  │ │
│  │ STRING      │ StringFieldCodeGen                 │ │
│  │ BOOL        │ BoolFieldCodeGen                   │ │
│  │ PLUTUSDATA  │ PlutusDataFieldCodeGen             │ │
│  │ CONSTRUCTOR │ ConstructorFieldCodeGen            │ │
│  │ LIST        │ ListFieldCodeGen(this)             │ │
│  │ MAP         │ MapFieldCodeGen(this)              │ │
│  │ OPTIONAL    │ OptionalFieldCodeGen(this)         │ │
│  │ PAIR        │ TupleFieldCodeGen(PAIR, this)      │ │
│  │ TRIPLE      │ TupleFieldCodeGen(TRIPLE, this)    │ │
│  │ QUARTET     │ TupleFieldCodeGen(QUARTET, this)   │ │
│  │ QUINTET     │ TupleFieldCodeGen(QUINTET, this)   │ │
│  └─────────────┴────────────────────────────────────┘ │
│                                                       │
│  dispatchNestedSerialization(FieldType, ...)           │
│  dispatchNestedDeserialization(FieldType, ...)         │
└───────────────────────────────────────────────────────┘
```

**13 registrations**: 6 simple generators + 3 composite generators (LIST, MAP, OPTIONAL receive a registry reference for recursive dispatch) + 4 tuple generators (PAIR through QUINTET, each parameterized by `TupleInfo`).

The registry also exposes `dispatchNestedSerialization()` and `dispatchNestedDeserialization()` methods that composite generators call to recursively process their element types.

### Three Converter Builders

Each builder handles one `SchemaClassification` and produces a complete `TypeSpec`:

| Builder | Classification | Responsibility |
|---------|---------------|----------------|
| `ClassConverterBuilder` | CLASS | Iterates `ClassDefinition.getFields()`, delegates each field to the registry, assembles `toPlutusData()` and `fromPlutusData()` methods |
| `InterfaceConverterBuilder` | INTERFACE | Generates `instanceof` dispatch for variant types, delegates constructor-level field processing to the registry |
| `EnumConverterBuilder` | ENUM | Maps enum constants to `ConstrPlutusData` alternatives by index |

All three share `SerDeMethodBuilder` for generating the four wrapper methods (`serialize()`, `serializeToHex()`, `deserialize()`, `deserializeFromHex()`) that are identical across classifications.

### `TupleInfo` Enum

Unifies Pair/Triple/Quartet/Quintet by parameterizing arity, eliminating the copy-pasted methods from the monolithic class:

```java
public enum TupleInfo {
    PAIR(2,    Type.PAIR,    Pair.class,    new String[]{"getFirst", "getSecond"}),
    TRIPLE(3,  Type.TRIPLE,  Triple.class,  new String[]{"getFirst", "getSecond", "getThird"}),
    QUARTET(4, Type.QUARTET, Quartet.class, new String[]{"getFirst", "getSecond", "getThird", "getFourth"}),
    QUINTET(5, Type.QUINTET, Quintet.class, new String[]{"getFirst", "getSecond", "getThird", "getFourth", "getFifth"});

    final int arity;
    final Type type;
    final Class<?> tupleClass;
    final String[] accessors;
}
```

### `TupleCodeGenerator` and `ElementCodeGenerator`

`TupleCodeGenerator` generates serialization/deserialization code for any arity by looping over `TupleInfo.accessors`:

```java
public class TupleCodeGenerator {
    CodeBlock generateSerialization(TupleInfo info, FieldType fieldType,
                                    String varName, ElementCodeGenerator elementGen);
    CodeBlock generateDeserialization(TupleInfo info, FieldType fieldType,
                                      String varName, ElementCodeGenerator elementGen);
}
```

`ElementCodeGenerator` is a `@FunctionalInterface` that the registry provides as a callback:

```java
@FunctionalInterface
public interface ElementCodeGenerator {
    CodeBlock generate(FieldType elementType, String varName, String accessorExpr);
}
```

This decouples tuple structure (arity, accessor names) from element processing (type-specific serialization), allowing `TupleCodeGenerator` to be completely arity-agnostic.

### `FieldAccessor`

Encapsulates the difference between direct field access and getter-based access:

```java
public class FieldAccessor {
    String fieldName;
    String accessExpression;    // "obj.fieldName" or "obj.getFieldName()"
    CodeBlock nullCheck;        // null-guard statement (if applicable)
}
```

Previously, `fieldOrGetterName()` and `nullCheckStatement()` were utility methods scattered in the monolithic class.

### `SerDeMethodBuilder`

Extracts the four wrapper methods shared by all converter types:

```java
public class SerDeMethodBuilder {
    MethodSpec buildSerialize(ClassName modelClass);       // → byte[]
    MethodSpec buildSerializeToHex(ClassName modelClass);  // → String (hex)
    MethodSpec buildDeserialize(ClassName modelClass);     // byte[] → T
    MethodSpec buildDeserializeFromHex(ClassName modelClass); // String → T
}
```

These methods are structurally identical regardless of whether the converter handles a CLASS, INTERFACE, or ENUM — they simply delegate to `toPlutusData()`/`fromPlutusData()` with CBOR encoding/decoding.

### `ConverterCodeGenerator` Facade

The original class is reduced to a ~40-line facade that delegates to the three builders:

```java
public class ConverterCodeGenerator {
    private final ClassConverterBuilder classBuilder;
    private final InterfaceConverterBuilder interfaceBuilder;
    private final EnumConverterBuilder enumBuilder;

    public TypeSpec generate(ClassDefinition classDef) {
        return classBuilder.build(classDef);
    }

    public TypeSpec generateInterfaceConverter(ClassDefinition classDef,
                                               List<ClassDefinition> constructors) {
        return interfaceBuilder.build(classDef, constructors);
    }

    public Optional<TypeSpec> generateEnumConverter(ClassDefinition classDef) {
        return enumBuilder.build(classDef);
    }
}
```

The public API is unchanged — callers (`FieldSpecProcessor`, `ValidatorProcessor`) do not need modification.

### Per-Type Generator Implementations

**Simple generators** (no recursive dispatch needed):

| Generator | Type | Core Logic |
|-----------|------|-----------|
| `IntegerFieldCodeGen` | INTEGER | `BigIntPlutusData.of(value)` / `BigIntPlutusData.getValue()` with int/long/BigInteger variants |
| `BytesFieldCodeGen` | BYTES | `BytesPlutusData.of(value)` / `BytesPlutusData.getValue()` |
| `StringFieldCodeGen` | STRING | `BytesPlutusData.of(value.getBytes())` / `new String(bytes)` |
| `BoolFieldCodeGen` | BOOL | `BigIntPlutusData.of(value ? 1 : 0)` / `equals(BigInteger.ONE)` |
| `PlutusDataFieldCodeGen` | PLUTUSDATA | Direct assignment (no conversion needed) |
| `ConstructorFieldCodeGen` | CONSTRUCTOR | Delegates to generated `*Converter.toPlutusData()`/`fromPlutusData()` |

**Composite generators** (hold registry reference for recursive element dispatch):

| Generator | Type | Recursive Elements |
|-----------|------|--------------------|
| `ListFieldCodeGen` | LIST | Element type dispatched through registry |
| `MapFieldCodeGen` | MAP | Key and value types dispatched through registry |
| `OptionalFieldCodeGen` | OPTIONAL | Inner type dispatched through registry |

**Parameterized generator**:

| Generator | Types | Parameterization |
|-----------|-------|------------------|
| `TupleFieldCodeGen` | PAIR, TRIPLE, QUARTET, QUINTET | `TupleInfo` enum + `TupleCodeGenerator` for arity-agnostic loops |

### Architecture Diagram

```
ConverterCodeGenerator (facade, ~40 lines)
    │
    ├── ClassConverterBuilder
    │     ├── FieldCodeGeneratorRegistry.get(field.type)  → per-field CodeBlock
    │     └── SerDeMethodBuilder                          → wrapper methods
    │
    ├── InterfaceConverterBuilder
    │     ├── FieldCodeGeneratorRegistry.get(field.type)  → per-variant field CodeBlock
    │     └── SerDeMethodBuilder                          → wrapper methods
    │
    └── EnumConverterBuilder
          └── SerDeMethodBuilder                          → wrapper methods

FieldCodeGeneratorRegistry
    │
    ├── IntegerFieldCodeGen ──────────────────────── (self-contained)
    ├── BytesFieldCodeGen ────────────────────────── (self-contained)
    ├── StringFieldCodeGen ───────────────────────── (self-contained)
    ├── BoolFieldCodeGen ─────────────────────────── (self-contained)
    ├── PlutusDataFieldCodeGen ───────────────────── (self-contained)
    ├── ConstructorFieldCodeGen ──────────────────── (self-contained)
    │
    ├── ListFieldCodeGen ─── registry.dispatchNested*() ──► recursive
    ├── MapFieldCodeGen ──── registry.dispatchNested*() ──► recursive
    ├── OptionalFieldCodeGen registry.dispatchNested*() ──► recursive
    │
    └── TupleFieldCodeGen(PAIR)    ┐
        TupleFieldCodeGen(TRIPLE)  ├── TupleCodeGenerator + ElementCodeGenerator
        TupleFieldCodeGen(QUARTET) │   (arity-agnostic loop over TupleInfo.accessors)
        TupleFieldCodeGen(QUINTET) ┘
```

## Consequences

### Positive

- **Open/Closed Principle**: Adding a new field type requires implementing `FieldCodeGenerator` and registering it — no modification to existing generators or builders.
- **Eliminated tuple duplication**: 20+ copy-pasted Pair/Triple/Quartet/Quintet methods replaced by a single `TupleCodeGenerator` parameterized by `TupleInfo`.
- **Individual testability**: Each generator can be unit tested in isolation by constructing a `Field`/`FieldType` and asserting the generated `CodeBlock` output.
- **Reduced cognitive load**: `ConverterCodeGenerator` reduced from 1,813 lines to ~40 lines. Each generator is typically <80 lines.
- **Recursive composition**: Composite generators (LIST, MAP, OPTIONAL) delegate element processing through the registry, making nesting depth-agnostic.
- **Stable public API**: The `ConverterCodeGenerator` facade preserves the existing `generate()`, `generateInterfaceConverter()`, and `generateEnumConverter()` signatures — no changes needed in callers.

### Negative

- **More files to navigate**: 15 classes instead of 1.
  - **Mitigation**: Consistent naming convention (`*FieldCodeGen` for type generators), clear package structure (`converter/` for infrastructure, `converter/type/` for per-type implementations).
- **Indirection through registry dispatch**: Following code generation flow requires understanding the registry lookup pattern.
  - **Mitigation**: This is the same `EnumMap` dispatch pattern used by ADR-0015's `DataTypeProcessUtil`, so developers familiar with the codebase already understand it.
- **Two-level generation concept** (top-level vs nested) adds a learning curve.
  - **Mitigation**: The distinction maps directly to the difference between "generate code for a field in a class" vs "generate an expression for an element inside a collection" — the separation is inherent in the problem domain.

## Testing

18 unit test files cover the decomposed architecture:
- One test per type generator (e.g., `IntegerFieldCodeGenTest`, `ListFieldCodeGenTest`)
- `TupleCodeGeneratorTest` for arity-agnostic tuple logic
- `FieldCodeGeneratorRegistryTest` for dispatch correctness
- `ClassConverterBuilderTest`, `InterfaceConverterBuilderTest`, `EnumConverterBuilderTest`
- `TestFixtures` class provides shared `Field`, `FieldType`, and `ClassDefinition` test data

Tests verify generated `CodeBlock` patterns rather than compiled output, enabling fast, focused unit testing without running the full annotation processing pipeline.

## References

- `ConverterCodeGenerator` — facade delegating to builders
- `FieldCodeGenerator` — strategy interface for per-type code generation
- `FieldCodeGeneratorRegistry` — `EnumMap`-based dispatch with recursive composition
- `ClassConverterBuilder`, `InterfaceConverterBuilder`, `EnumConverterBuilder` — per-classification builders
- `SerDeMethodBuilder` — shared wrapper method generation
- `TupleInfo` — arity parameterization enum
- `TupleCodeGenerator` — arity-agnostic tuple code generation
- `ElementCodeGenerator` — functional interface for nested element dispatch
- `FieldAccessor` — field access pattern encapsulation
- 10 type generators in `converter/type/` package
- ADR-0002: Code Generation Pipeline
- ADR-0015: DataType Processor Strategy Pattern (same `EnumMap` dispatch pattern)
- ADR-0011: Multi-Item Tuple Support (tuple types unified by `TupleInfo`)
