# ADR 0002: Blueprint Code Generation Pipeline

- Status: Accepted
- Date: 2026-02-26
- Owners: Cardano Client Lib maintainers
- Related: ADR-0001 (Processor Architecture), ADR-0005 (Definition Keys), ADR-0008 (Schema Classification), CIP-57

## Context

Once the blueprint JSON is loaded and references are resolved (ADR-0001), the processor must transform schema definitions into Java source files. This involves two distinct concerns:

1. **Definition processing** — each named schema definition becomes a model class (POJO) and a converter class (serialization/deserialization).
2. **Validator processing** — each validator becomes a wrapper class with compiled script access, address derivation, and optionally custom interface implementations.

These two phases have different inputs, outputs, and class structures, but share naming strategies and package resolution logic.

## Decision

Implement a two-phase pipeline with clearly separated responsibilities:

### Phase 1: Definition Processing (`FieldSpecProcessor`)

For each definition in the blueprint:

```
Definition Key + BlueprintSchema
        │
        ▼
  DatumModelFactory.create()
  → DatumModel (name, namespace, schema, classification)
        │
        ▼
  SchemaClassifier.classify()
  → SchemaClassification: ENUM | INTERFACE | CLASS
        │
  ┌─────┼─────────────────┐
  │     │                 │
ENUM  INTERFACE         CLASS
  │     │                 │
  ▼     ▼                 ▼
Java  Interface +      @Data class +
enum  @Constr impl     ConverterCodeGenerator
```

**Key classes:**
- `FieldSpecProcessor` — orchestrates definition processing; checks `SharedTypeLookup`, creates `DatumModel`, dispatches by classification.
- `DatumModelFactory` — creates `DatumModel` from namespace + schema; uses `NamingStrategy` for class name derivation and `SchemaClassifier` for classification.
- `DatumModel` — immutable value object: `namespace`, `name`, `schema`, `classificationResult`.
- `ClassDefinitionGenerator` — reads fields from `@Constr`-annotated types, detects `FieldType` flags (`rawDataType`, `dataType`), produces `ClassDefinition`.
- `ConverterCodeGenerator` — generates converter classes with `toPlutusData()`, `fromPlutusData()`, `serialize()`, `deserialize()` methods; all converters extend `BasePlutusDataConverter`.

### Phase 2: Validator Processing (`ValidatorProcessor`)

For each validator in the blueprint:

```
Validator (title, compiledCode, hash, parameters, datum, redeemer)
        │
        ▼
  Calculate validator name + namespace
  Package: {base}.{firstToken}
        │
        ▼
  Process inline schemas (redeemer, datum, parameters)
  → delegates to FieldSpecProcessor.createDatumClass()
        │
        ▼
  Generate validator wrapper class:
  ┌────────────────────────────────────┐
  │  public class {Name}Validator      │
  │    static final String TITLE       │
  │    static final String COMPILED_CODE│
  │    static final String HASH        │
  │    Network network                 │
  │    getScriptAddress()              │
  │    getPlutusScript()               │
  │    // + @ExtendWith interfaces     │
  └────────────────────────────────────┘
```

**Key classes:**
- `ValidatorProcessor` — processes validators; calculates name from title (splits on `.`, joins non-first tokens with `_`), resolves package, generates wrapper `TypeSpec`.
- `PackageResolver` — determines output packages:
  - Models: `{base}.{namespace}.model` (lowercased, sanitized)
  - Validators: `{base}.{firstToken}` (first segment of validator title before `.`)

### Field Processing Pipeline

Within each definition, individual fields are processed by `DataTypeProcessUtil`, which dispatches to specialized `DataTypeProcessor` implementations based on `BlueprintDatatype` (see ADR-0015). Each processor returns `FieldSpec` objects that are assembled into the model class.

### Shared Type Detection

Before generating a class for a definition, `FieldSpecProcessor` checks `SharedTypeLookup` (see ADR-0003). If a shared type is found, no model class is generated — instead, a converter is generated via `SharedTypeConverterGenerator` to bridge between the shared type and on-chain representation.

## Rationale

1. **Separation of concerns**: Definitions and validators have fundamentally different structures. Separate processors keep each focused.
2. **Schema classification**: The three-way classification (ENUM/INTERFACE/CLASS) enables generating idiomatic Java for each pattern — enums for discriminated unions without data, interfaces for sum types, classes for product types.
3. **Shared type reuse**: Checking `SharedTypeLookup` first prevents generating redundant classes when existing types (e.g., from `plutus-aiken` module) already model the same data.
4. **Consistent naming**: Both phases share `PackageResolver` and `NamingStrategy` (see ADR-0014), ensuring consistent package and class naming.

## Consequences

### Positive
- Clean separation between definition and validator processing.
- Each schema classification maps to the most idiomatic Java construct.
- Shared types are detected early, avoiding redundant generation.
- Inline schemas in validators are handled by delegating to the same `FieldSpecProcessor` used for definitions.

### Negative
- Two-phase ordering means validators cannot reference definitions that haven't been processed yet.
  - **Mitigation**: Definitions are always processed first, so all types are available when validators are processed.
- The pipeline assumes CIP-57 structure. Non-conforming blueprints will fail at the parsing stage.

## References

- `FieldSpecProcessor` — definition processing orchestrator
- `ValidatorProcessor` — validator wrapper generator
- `DatumModelFactory`, `DatumModel` — definition model abstraction
- `ClassDefinitionGenerator`, `ConverterCodeGenerator` — class and converter generation
- `PackageResolver` — package name resolution
- ADR-0001: Processor Architecture (overall flow)
- ADR-0005: Definition Keys as Source of Truth
- ADR-0008: Schema Classification Strategy
- ADR-0015: DataType Processor Strategy Pattern
