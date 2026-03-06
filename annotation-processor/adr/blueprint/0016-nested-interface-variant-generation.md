# ADR 0016: anyOf Interface Variants as Top-Level Classes with Prefixed Names

- Status: Accepted
- Date: 2026-03-06
- Owners: Cardano Client Lib maintainers
- Related: ADR-0002 (Blueprint Code Generation Pipeline), ADR-0008 (Schema Classification Strategy)

## Context

When the schema classifier (ADR-0008) identifies an `INTERFACE` classification — an `anyOf` schema with more than one variant — the processor generates a Java interface and one class per variant. The variants must avoid naming collisions: both `Credential` and `PaymentCredential` can have a `VerificationKey` variant in the same package.

An earlier approach nested variants as static inner classes (e.g., `Credential.VerificationKey`). While this solved collisions, it created downstream complexity:

1. **Converter FQN resolution**: `ClassDefinitionGenerator` needed enclosing-element inspection to prefix converter names (detecting that `VerificationKey` is inside `Credential` to produce `CredentialVerificationKeyConverter`).
2. **Nested `ClassName` construction**: `buildVariantTypeSpec()` used `ClassName.get(pkg, interfaceClassName, variantName)` for nested references, and all downstream code (`ConverterCodeGenerator`, `DataImplGenerator`) needed `ClassName.bestGuess()` to parse dotted notation.
3. **Converter placement complexity**: Placing converters for nested classes required special handling in the code generation pipeline.

A simpler approach achieves the same collision avoidance: generate variants as **top-level classes** with the interface name prepended to the variant name.

## Decision

Generate each `anyOf` variant as a **top-level class** with a prefixed name (`InterfaceName` + `VariantName`). The interface itself is written as a standalone file with no inner types.

### Generated Structure

```
Credential.java
  public interface Credential {}

CredentialVerificationKey.java
  public abstract class CredentialVerificationKey
      implements Data<CredentialVerificationKey>, Credential { ... }

CredentialScript.java
  public abstract class CredentialScript
      implements Data<CredentialScript>, Credential { ... }
```

Multiple files are emitted: one for the interface and one per variant.

### Key Implementation

#### `FieldSpecProcessor` — Interface and Variant Generation

- `buildInterfaceTypeSpecBuilder(String interfaceName)` — creates the interface `TypeSpec.Builder` with `@Constr` annotation and public modifier.
- `createDatumClass(DatumModel)` INTERFACE branch:
  1. Writes the interface file first (standalone, no inner types).
  2. Iterates over variants, calling `buildVariantTypeSpec()` for each.
  3. Writes each variant as a separate file, registered with `GeneratedTypesRegistry`.

- `buildVariantTypeSpec(String ns, String interfaceName, BlueprintSchema schema)`:
  - Constructs the prefixed name: `nameStrategy.toClassName(interfaceName) + nameStrategy.toClassName(schema.getTitle())`.
  - Uses `ClassName.get(pkg, prefixedName)` — a top-level reference, no nesting.
  - Modifiers: `PUBLIC, ABSTRACT` (no `STATIC` since it's top-level).
  - Parameterizes `Data<PrefixedName>` (e.g., `Data<CredentialVerificationKey>`).
  - Still implements the parent interface (e.g., `Credential`).
  - Applies `@Constr(alternative = X)` annotation with the correct constructor index.
  - Processes fields directly from the variant schema (no recursive `collectAllFields`).

#### `ClassDefinitionGenerator` — Simplified

No enclosing-element inspection is needed. Since the class is already named `CredentialVerificationKey`, the converter name `CredentialVerificationKeyConverter` and impl name `CredentialVerificationKeyImpl` are derived naturally:

```java
String prefix = className;  // Already "CredentialVerificationKey"
```

`getConverterClassFromField()` continues to use `String.join("", fieldClass.simpleNames())` which works for top-level classes (single-element list).

### What Does NOT Change

- **`SchemaClassifier`** — still classifies `anyOf > 1` as INTERFACE.
- **`ConstrAnnotationProcessor`** — interface detection via `getInterfaces()` still works (variants still implement the interface).
- **`ConverterCodeGenerator` / `InterfaceConverterBuilder`** — dispatch converter generation unchanged.
- **`DataImplGenerator`** — works with any class name.
- **Converter location** — always `pkg.converter.XConverter` (standard path).

## Rationale

1. **Simplicity**: No enclosing-element detection, no nested `ClassName` construction, no special converter placement logic. The prefixed name carries all necessary information.
2. **Collision avoidance**: `CredentialVerificationKey` and `PaymentCredentialVerificationKey` are distinct names — same guarantee as nesting, without the complexity.
3. **Predictable converter names**: `CredentialVerificationKeyConverter` is derived from the class name alone, with no need to inspect the type hierarchy.
4. **Reduced downstream complexity**: Eliminates the need for `ClassName.bestGuess()` to parse dotted nested-class notation in converter and impl generators.

## Consequences

### Positive
- Naming collisions between same-named variants of different interfaces are eliminated.
- Converter and impl class generation is simpler — no enclosing-element inspection needed.
- Each variant is a self-contained file, easy to navigate in IDEs.
- Downstream code (converter generators, impl generators) works with standard top-level class references.

### Negative
- More generated files (one per variant instead of one per interface).
  - **Mitigation**: IDE file trees and package structure keep them organized.
- Variant class names are longer (e.g., `MarketDatumSpotDatum` instead of `SpotDatum`).
  - **Mitigation**: These names are generated and rarely typed manually. The prefix provides useful context.
- The short variant name (e.g., `VerificationKey`) is no longer directly visible as a class name.
  - **Mitigation**: The interface still exists as a marker, and the prefix clearly indicates the relationship.

## References

- `FieldSpecProcessor.buildInterfaceTypeSpecBuilder()`, `buildVariantTypeSpec()` — interface and variant generation
- `ClassDefinitionGenerator` — simplified name prefixing (no enclosing-element detection)
- ADR-0002: Blueprint Code Generation Pipeline (INTERFACE classification flow)
- ADR-0008: Schema Classification Strategy (anyOf classification)
