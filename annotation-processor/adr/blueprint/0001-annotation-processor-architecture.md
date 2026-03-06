# ADR 0001: Blueprint Annotation Processor Architecture

- Status: Accepted
- Date: 2026-02-26
- Owners: Cardano Client Lib maintainers
- Related: CIP-57, ADR-0002 (Code Generation Pipeline)

## Context

Cardano smart contracts (Plutus/Aiken) produce a CIP-57 blueprint — a JSON file describing validators, their parameters, datums, and redeemers with full schema definitions. To interact with these contracts from Java, developers need model classes, serialization converters, and validator wrappers that match the blueprint.

Manually writing these classes is tedious, error-prone, and must be repeated whenever the contract changes. Java's annotation processing API (`javax.annotation.processing`) provides a compile-time code generation mechanism that can automate this entirely.

## Decision

Implement a JSR 269 annotation processor that reads a CIP-57 blueprint JSON file at compile time and generates all necessary Java classes via JavaPoet.

### Core Components

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `@Blueprint` | `plutus` module | Marks a class and specifies the blueprint file path and base package |
| `@ExtendWith` | `plutus` module | Optionally specifies interfaces for generated validator classes to implement |
| `BlueprintAnnotationProcessor` | `annotation-processor` module | Entry point; discovered via `@AutoService(Processor.class)` |
| `PlutusBlueprintLoader` | `plutus` module | Parses blueprint JSON into `PlutusContractBlueprint` model, resolves `$ref` references |
| `GeneratedTypesRegistry` | `annotation-processor` module | Tracks generated classes (`pkg:className`) to prevent duplicate file creation |
| `SourceWriter` | `annotation-processor` module | Writes JavaPoet `TypeSpec` to `ProcessingEnvironment.Filer` |
| `ErrorReporter` | `annotation-processor` module | Wraps `Messager` with `error()`, `warn()`, `note()` convenience methods |

### Processing Flow

```
@Blueprint(file = "plutus.json", packageName = "com.example")
interface MyContract {}
                │
                ▼
  BlueprintAnnotationProcessor.process()
                │
    ┌───────────┴───────────┐
    │  Load & Parse JSON    │
    │  PlutusBlueprintLoader│
    │  → resolveReferences()│
    └───────────┬───────────┘
                │
    ┌───────────┴───────────────────┐
    │  Phase 1: Definitions         │
    │  For each definition key:     │
    │    resolveDefinitionKey()     │
    │    → FieldSpecProcessor       │
    │      .createDatumClass()      │
    │  Output: model + converter    │
    ├───────────────────────────────┤
    │  Phase 2: Validators          │
    │  For each validator:          │
    │    ValidatorProcessor         │
    │      .processValidator()      │
    │  Output: validator wrapper    │
    └───────────────────────────────┘
```

### Module Dependencies

- `plutus` module — contains annotations (`@Blueprint`, `@ExtendWith`, `@Constr`), blueprint model classes (`PlutusContractBlueprint`, `BlueprintSchema`), and `PlutusBlueprintLoader`.
- `annotation-processor` module — depends on `plutus`; contains the processor, code generators, and all support classes.
- `plutus-aiken` module — provides `SharedTypeLookup` for resolving shared/registered types (see ADR-0003).

### Blueprint File Resolution

The processor locates the blueprint file specified in `@Blueprint` by:
1. Trying `StandardLocation.CLASS_PATH` via `processingEnv.getFiler().getResource()`
2. Falling back to `StandardLocation.CLASS_OUTPUT`

This supports both resource-directory placement and build-output placement.

### Reference Resolution

`PlutusBlueprintLoader.resolveReferences()` performs recursive `$ref` resolution with circular-reference detection via `IdentityHashMap`. It traverses all schema fields (`fields`, `anyOf`, `items`, `keys`, `values`, `left`, `right`) and populates `schema.setRefSchema()` for each resolved reference. It also detects `Option$`/`Option<` keys and sets `dataType = option`.

## Consequences

### Positive
- Zero boilerplate: developers annotate one interface and get all generated classes at compile time.
- Type-safe: generated converters enforce correct serialization/deserialization at compile time.
- IDE-friendly: generated sources appear in build output and are indexable.
- Extensible: `@ExtendWith` allows validator wrappers to implement custom interfaces.

### Negative
- Compile-time dependency on the blueprint JSON file — if the file is missing, compilation fails with a processor error.
- Debugging annotation processor issues requires understanding the JavaPoet + `ProcessingEnvironment` model.
  - **Mitigation**: `ErrorReporter` provides clear error messages with element context.

## References

- `BlueprintAnnotationProcessor` — main processor entry point
- `PlutusBlueprintLoader` — JSON parsing and `$ref` resolution
- `GeneratedTypesRegistry` — duplicate prevention registry
- ADR-0002: Code Generation Pipeline (details on `FieldSpecProcessor` and `ValidatorProcessor`)
- ADR-0003: Shared Blueprint Type Registry
- CIP-57: Plutus Blueprint specification
