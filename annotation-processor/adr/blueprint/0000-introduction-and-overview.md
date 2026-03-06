# ADR 0000: Introduction and Overview

- Status: Accepted
- Date: 2026-03-06
- Owners: Cardano Client Lib maintainers

## Purpose

This document is the entry point for the Blueprint Annotation Processor ADR series (0001–0018). It explains what the processor does, how the ADRs relate to each other, and provides a recommended reading order for newcomers.

## What is the Blueprint Annotation Processor?

Cardano smart contracts (written in Aiken, PlutusTx, Helios, etc.) produce a **CIP-57 blueprint** — a JSON file describing validators, their parameters, datums, and redeemers with full schema definitions.

The Blueprint Annotation Processor is a compile-time Java annotation processor (JSR 269) that reads a CIP-57 blueprint file and **automatically generates** all necessary Java classes:

- **Model classes** (POJOs) for datums and redeemers
- **Converter classes** for PlutusData serialization/deserialization
- **Validator wrappers** with compiled script access and address derivation

### Usage

```java
@Blueprint(fileInResources = "plutus.json", packageName = "com.example")
interface MyContract {}
```

At compile time, this single annotation triggers the processor to parse the blueprint and generate all required classes into the specified package.

## How to Read These ADRs

### Recommended Reading Order

**Start here** — understand the overall architecture:

1. **[ADR-0001](0001-annotation-processor-architecture.md)** — Processor entry point, module dependencies, processing flow
2. **[ADR-0002](0002-blueprint-code-generation-pipeline.md)** — Two-phase pipeline (definitions then validators)

**Then** — learn how blueprint schemas are analyzed:

3. **[ADR-0005](0005-definition-keys-as-source-of-truth.md)** — How class names are derived from definition keys
4. **[ADR-0013](0013-json-pointer-rfc6901-handling.md)** — How definition key escaping works (RFC 6901)
5. **[ADR-0014](0014-naming-strategy-architecture.md)** — How blueprint identifiers become Java names
6. **[ADR-0006](0006-generic-type-syntax-handling.md)** — How generic types are parsed across Aiken versions
7. **[ADR-0008](0008-schema-classification-strategy.md)** — The 7-type classification system
8. **[ADR-0007](0007-opaque-plutusdata-detection.md)** — How opaque/unstructured schemas are detected
9. **[ADR-0015](0015-datatype-processor-strategy-pattern.md)** — Per-datatype processing dispatch

**Then** — understand shared type reuse:

10. **[ADR-0003](0003-shared-blueprint-type-registry.md)** — The shared type registry SPI
11. **[ADR-0017](0017-aiken-stdlib-version-hints-spi.md)** — Version-aware type lookup for Aiken stdlib
12. **[ADR-0009](0009-shared-type-converter-architecture.md)** — Converter generation for shared types
13. **[ADR-0012](0012-rawdata-interface-and-fieldtype-flags.md)** — Encoding markers (`Data<T>`, `RawData`)

**Then** — code generation specifics:

14. **[ADR-0011](0011-multi-item-tuple-support.md)** — Pair through Quintet tuple mapping
15. **[ADR-0018](0018-converter-code-generator-decomposition.md)** — Strategy-based converter code generation
16. **[ADR-0016](0016-nested-interface-variant-generation.md)** — anyOf variants as top-level classes with prefixed names
17. **[ADR-0010](0010-validator-name-collision-resolution.md)** — Validator name disambiguation

**Finally** — testing:

18. **[ADR-0004](0004-blueprint-naming-with-aiken-version.md)** — Test file naming conventions

## Architecture Overview

```
@Blueprint(fileInResources = "plutus.json", packageName = "com.example")
interface MyContract {}
        │
        ▼
BlueprintAnnotationProcessor.process()               [ADR-0001]
        │
        ├─ PlutusBlueprintLoader.load()
        │    Parse JSON, resolve $ref references       [ADR-0013]
        │    Detect Option types, circular refs
        │
        ├─ Phase 1: DEFINITIONS                        [ADR-0002]
        │    For each definition key:
        │    │
        │    ├─ Parse definition key                    [ADR-0005, ADR-0006]
        │    │    Extract class name, namespace,
        │    │    handle generics and RFC 6901 escaping
        │    │
        │    ├─ Check SharedTypeLookup                  [ADR-0003, ADR-0017]
        │    │    ├─ Found → SharedTypeConverterGenerator [ADR-0009]
        │    │    │           Generate converter only
        │    │    └─ Not found → continue generation
        │    │
        │    ├─ NamingStrategy.toClassName()             [ADR-0014]
        │    │    Blueprint identifier → Java name
        │    │
        │    ├─ SchemaClassifier.classify()              [ADR-0008]
        │    │    ├─ ALIAS    → skip (primitive)
        │    │    ├─ OPTION   → skip (OptionDataTypeProcessor)
        │    │    ├─ PAIR_ALIAS → skip (PairDataTypeProcessor)
        │    │    ├─ ENUM     → generate Java enum
        │    │    ├─ INTERFACE → generate interface       [ADR-0016]
        │    │    │              + top-level variant classes
        │    │    └─ CLASS    → generate @Data class
        │    │
        │    ├─ Opaque PlutusData check                  [ADR-0007]
        │    │    If abstract → map to PlutusData.class
        │    │
        │    ├─ DataTypeProcessor dispatch                [ADR-0015]
        │    │    Per-field type resolution:
        │    │    bytes, integer, string, bool, list,
        │    │    map, option, pair, constructor, null
        │    │    │
        │    │    ├─ Tuple items → Pair/Triple/Quartet    [ADR-0011]
        │    │    └─ RawData/Data<T> flags                [ADR-0012]
        │    │
        │    └─ ConverterCodeGenerator (facade)           [ADR-0018]
        │         ├─ ClassConverterBuilder    (CLASS)
        │         ├─ InterfaceConverterBuilder (INTERFACE)
        │         └─ EnumConverterBuilder     (ENUM)
        │         Each delegates per-field generation to
        │         FieldCodeGeneratorRegistry (13 type generators)
        │
        └─ Phase 2: VALIDATORS                          [ADR-0002]
             For each validator:
             │
             ├─ Calculate validator name                  [ADR-0010]
             │    Resolve collisions (skip-first-token)
             │
             ├─ Process inline schemas (datum, redeemer)
             │    → delegates to Phase 1 logic
             │
             └─ Generate validator wrapper class
                  TITLE, COMPILED_CODE, HASH,
                  getScriptAddress(), getPlutusScript()
```

## ADR Index by Category

### Foundation & Pipeline

| ADR | Title | Summary |
|-----|-------|---------|
| [0001](0001-annotation-processor-architecture.md) | Annotation Processor Architecture | Entry point, module dependencies, `@Blueprint` annotation, `PlutusBlueprintLoader`, `GeneratedTypesRegistry` |
| [0002](0002-blueprint-code-generation-pipeline.md) | Code Generation Pipeline | Two-phase pipeline: `FieldSpecProcessor` (definitions) → `ValidatorProcessor` (validators), `DatumModelFactory`, `PackageResolver` |
| [0014](0014-naming-strategy-architecture.md) | Naming Strategy Architecture | `DefaultNamingStrategy` 5-step pipeline converting blueprint identifiers to Java names, keyword escaping |
| [0013](0013-json-pointer-rfc6901-handling.md) | JSON Pointer (RFC 6901) Handling | `JsonPointerUtil` for definition key escaping/unescaping (`~0` = `~`, `~1` = `/`) |

### Schema Analysis & Type Resolution

| ADR | Title | Summary |
|-----|-------|---------|
| [0005](0005-definition-keys-as-source-of-truth.md) | Definition Keys as Source of Truth | Class names derived from definition keys (not titles), CIP-57 compliance |
| [0006](0006-generic-type-syntax-handling.md) | Generic Type Syntax Handling | Parsing `Option<T>`, `Pair<K,V>` across Aiken versions; 9 built-in generic containers |
| [0007](0007-opaque-plutusdata-detection.md) | Opaque PlutusData Detection | Three-layer defense for detecting unstructured schemas → `PlutusData.class` |
| [0008](0008-schema-classification-strategy.md) | Schema Classification Strategy | `SchemaClassifier` with 7 types: ALIAS, OPTION, PAIR_ALIAS, ENUM, INTERFACE, CLASS, UNKNOWN |
| [0015](0015-datatype-processor-strategy-pattern.md) | DataType Processor Strategy | `DataTypeProcessor` interface with 10 implementations, `EnumMap`-based dispatch |

### Shared Type Reuse

| ADR | Title | Summary |
|-----|-------|---------|
| [0003](0003-shared-blueprint-type-registry.md) | Shared Blueprint Type Registry | `BlueprintTypeRegistry` SPI, `SchemaSignatureBuilder`, structural matching, `AikenBlueprintTypeRegistry` |
| [0017](0017-aiken-stdlib-version-hints-spi.md) | Aiken Stdlib Version Hints | `AnnotationHintDescriptor` SPI, `@AikenStdlib` annotation, versioned buckets (V1/V2/V3) |
| [0009](0009-shared-type-converter-architecture.md) | Shared Type Converter Architecture | `SharedTypeConverterGenerator`, `SharedTypeKind` (CONSTRUCTOR/BYTES/PAIR) |
| [0012](0012-rawdata-interface-and-fieldtype-flags.md) | RawData Interface and FieldType Flags | `Data<T>` / `RawData` marker interfaces, `FieldType` encoding flags |

### Code Generation

| ADR | Title | Summary |
|-----|-------|---------|
| [0011](0011-multi-item-tuple-support.md) | Multi-Item Tuple Support | `SchemaTypeResolver` maps list schemas to Pair/Triple/Quartet/Quintet (2–5 items) |
| [0018](0018-converter-code-generator-decomposition.md) | Converter Code Generator Decomposition | `FieldCodeGenerator` strategy interface, `FieldCodeGeneratorRegistry` dispatch, `TupleInfo`-parameterized tuple generation |
| [0016](0016-nested-interface-variant-generation.md) | Interface Variant Generation | anyOf variants as top-level classes with prefixed names (e.g., `CredentialVerificationKey`) |
| [0010](0010-validator-name-collision-resolution.md) | Validator Name Collision Resolution | `ValidatorProcessor.calculateValidatorName()`, skip-first-token strategy |

### Testing Conventions

| ADR | Title | Summary |
|-----|-------|---------|
| [0004](0004-blueprint-naming-with-aiken-version.md) | Blueprint Test File Naming | `<Name>_aiken_v<version>.json` convention for multi-version test coverage |

## Dependency Graph

This shows which ADRs reference each other (arrow = "references"):

```
                   ┌──────────────────────────────────────┐
                   │          ADR-0001                     │
                   │  Processor Architecture               │
                   └──────┬────────────┬──────────────────┘
                          │            │
                          ▼            ▼
               ┌──────────────┐  ┌──────────────┐
               │  ADR-0002    │  │  ADR-0003    │
               │  Pipeline    │  │  Type Registry│
               └──┬──┬──┬──┬─┘  └──┬──┬──┬─────┘
                  │  │  │  │       │  │  │
      ┌───────────┘  │  │  │       │  │  └──────────┐
      ▼              │  │  │       │  ▼              ▼
┌──────────┐         │  │  │       │ ┌──────────┐ ┌──────────┐
│ ADR-0005 │         │  │  │       │ │ ADR-0009 │ │ ADR-0017 │
│ Def Keys │         │  │  │       │ │ Converters│ │ Versions │
└────┬─────┘         │  │  │       │ └──────────┘ └──────────┘
     │               │  │  │       │
     ▼               │  │  │       ▼
┌──────────┐         │  │  │  ┌──────────┐
│ ADR-0006 │         │  │  │  │ ADR-0012 │
│ Generics │         │  │  │  │ RawData  │
└──────────┘         │  │  │  └──────────┘
                     │  │  │
      ┌──────────────┘  │  └───────────────┐
      ▼                 ▼                  ▼
┌──────────┐     ┌──────────┐       ┌──────────┐
│ ADR-0008 │     │ ADR-0015 │       │ ADR-0016 │
│ Classify │     │ DataType │       │ Variants │
└──────────┘     │ Strategy │       └──────────┘
                 └────┬─────┘
                      │
                      ▼
                 ┌──────────┐
                 │ ADR-0011 │
                 │ Tuples   │
                 └──────────┘

  ADR-0002 ──► ADR-0018 ◄── ADR-0015
  Pipeline     Converter     DataType
               Decomp.       Strategy
                  │
                  ▼
              ADR-0011
              Tuples

  ADR-0005 ──► ADR-0013 ──► ADR-0014
  Def Keys     JSON Ptr     Naming

  ADR-0007 ──► ADR-0004
  Opaque       Test Files

  ADR-0010 ──► ADR-0004
  Validators   Test Files
```

### Cross-Reference Matrix

| ADR | References |
|-----|-----------|
| 0001 | 0002, 0003 |
| 0002 | 0001, 0005, 0008, 0015, 0016 |
| 0003 | 0009, 0017 |
| 0004 | — |
| 0005 | 0003, 0004 |
| 0006 | 0005 |
| 0007 | 0004 |
| 0008 | — |
| 0009 | 0003, 0017 |
| 0010 | 0004 |
| 0011 | 0006, 0015 |
| 0012 | 0003, 0009 |
| 0013 | 0005, 0014 |
| 0014 | 0005, 0006, 0013 |
| 0015 | 0002, 0011 |
| 0016 | 0002, 0008 |
| 0017 | 0003, 0009 |
| 0018 | 0002, 0015, 0011 |

## Key Concepts Glossary

| Term | Definition |
|------|-----------|
| **Blueprint** | A CIP-57 JSON file describing a smart contract's validators, datums, redeemers, and parameters with full schema definitions |
| **Definition Key** | A JSON Pointer path (e.g., `cardano/transaction/OutputReference`) that uniquely identifies a schema in the blueprint's `definitions` section |
| **SchemaSignature** | A canonical JSON string computed from a schema's structure, used as a lookup key in the shared type registry |
| **SchemaClassification** | One of 7 categories (ALIAS, OPTION, PAIR_ALIAS, ENUM, INTERFACE, CLASS, UNKNOWN) that determines how a schema is generated |
| **DatumModel** | An immutable value object carrying a schema's namespace, class name, schema reference, and classification result |
| **SharedTypeLookup** | The interface through which the processor queries the shared type registry before generating a class |
| **RegisteredType** | A value object (`packageName` + `simpleName`) returned by the registry when a schema matches a known shared type |
| **SharedTypeKind** | The encoding category of a shared type: CONSTRUCTOR (structured), BYTES (raw byte array), or PAIR (two-element tuple) |
| **FieldType Flags** | Boolean markers (`rawDataType`, `dataType`) on generated fields indicating whether a type uses `RawData` or `Data<T>` encoding |
| **NamingStrategy** | A pipeline that converts blueprint identifiers (snake_case, generic syntax, escaped characters) into valid Java class/field names |
| **DataTypeProcessor** | A strategy interface with 10 implementations, one per CIP-57 data type, dispatched via an `EnumMap` |
| **GeneratedTypesRegistry** | An in-memory set (`pkg:className`) that prevents the processor from generating duplicate class files |
| **AnnotationHintDescriptor** | An SPI that allows type registries to declare which annotations the processor should read from the `@Blueprint`-annotated interface |
| **FieldCodeGenerator** | A strategy interface for per-field-type converter code generation, with top-level (field) and nested (element) methods |
| **FieldCodeGeneratorRegistry** | An `EnumMap<Type, FieldCodeGenerator>` providing O(1) dispatch and recursive composition for converter code generation |
| **TupleInfo** | An enum parameterizing Pair/Triple/Quartet/Quintet by arity, class reference, and accessor names to eliminate tuple code duplication |

## References

- [CIP-57: Plutus Contract Blueprints](https://cips.cardano.org/cip/CIP-0057)
- [RFC 6901: JSON Pointer](https://datatracker.ietf.org/doc/html/rfc6901)
- [JavaPoet](https://github.com/square/javapoet) — Code generation library used by the processor
- [JSR 269](https://jcp.org/en/jsr/detail?id=269) — Java Annotation Processing API
