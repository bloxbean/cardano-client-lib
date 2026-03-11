# ADR 0016: anyOf Interface Variants in Sub-Packages

- Status: Accepted
- Date: 2026-03-10
- Owners: Cardano Client Lib maintainers
- Related: ADR-0002 (Blueprint Code Generation Pipeline), ADR-0008 (Schema Classification Strategy)

## Context

When the schema classifier (ADR-0008) identifies an `INTERFACE` classification — an `anyOf` schema with more than one variant — the processor generates a Java interface and one class per variant. The variants must avoid naming collisions: both `Credential` and `PaymentCredential` can have a `VerificationKey` variant.

Earlier approaches tried:
1. **Nested inner classes** (e.g., `Credential.VerificationKey`) — created downstream complexity in converter FQN resolution and `ClassName` construction.
2. **Top-level prefixed names** (e.g., `CredentialVerificationKey`) — simpler, but produced long concatenated names that are harder to read.

A cleaner approach: place variants in a **sub-package** named after the interface, keeping short, unprefixed variant names.

## Decision

Generate each `anyOf` variant in a **sub-package** named after the interface. The interface itself stays in the root model package. Sub-package names use the existing `toPackageNameFormat()` convention (lowercase, no special chars) for consistency with Aiken module path handling.

### Generated Structure

```
com.example.model/
  Credential.java
    public interface Credential {}

com.example.model.credential/
  VerificationKey.java
    public abstract class VerificationKey
        implements Data<VerificationKey>, Credential { ... }

  Script.java
    public abstract class Script
        implements Data<Script>, Credential { ... }
```

For multi-word interfaces, PascalCase is lowercased (same convention as Aiken module paths):
```
com.example.model/
  PaymentCredential.java

com.example.model.paymentcredential/
  VerificationKey.java
  Script.java
```

### Sub-Package Naming

Reuses the existing `NamingStrategy.toPackageNameFormat()` — lowercase, stripped of special characters:
- `Credential` → `credential`
- `PaymentCredential` → `paymentcredential`
- `GlobalStateSpendAction` → `globalstatespendaction`
- `MarketDatum` → `marketdatum`

This is the same convention used for Aiken module paths (`global_state` → `globalstate`), ensuring a single consistent package naming rule across the codebase.

### Key Implementation

#### `FieldSpecProcessor` — Interface and Variant Generation

- `buildInterfaceTypeSpecBuilder(String interfaceName)` — creates the interface `TypeSpec.Builder` with `@Constr` annotation and public modifier. Interface stays in root package.
- `createDatumClass(DatumModel)` INTERFACE branch:
  1. Writes the interface file in the root model package.
  2. Computes variant sub-package: `pkg + "." + nameStrategy.toPackageNameFormat(className)`.
  3. Iterates over variants, calling `buildVariantTypeSpec()` for each.
  4. Writes each variant to the sub-package, registered with `GeneratedTypesRegistry`.

- `buildVariantTypeSpec(String ns, String interfaceName, BlueprintSchema schema)`:
  - Uses unprefixed variant name: `nameStrategy.toClassName(schema.getTitle())`.
  - Computes sub-package: `pkg + "." + nameStrategy.toPackageNameFormat(interfaceClassName)`.
  - Uses `ClassName.get(variantPkg, className)` for `Data<T>` parameterization.
  - References parent interface via `ClassName.get(pkg, interfaceClassName)` (root package).
  - Modifiers: `PUBLIC, ABSTRACT` (top-level class).
  - Applies `@Constr(alternative = X)` annotation.

#### Converter Resolution — Automatic

`ConstrAnnotationProcessor` reads the package from `TypeElement` at compile time. Since variants now live in sub-packages, converter packages auto-resolve:
- Variant `com.example.model.credential.VerificationKey` → converter in `com.example.model.credential.converter`
- Interface converter stays at `com.example.model.converter.CredentialConverter`

### What Does NOT Change

- **`SchemaClassifier`** — still classifies `anyOf > 1` as INTERFACE.
- **`ConstrAnnotationProcessor`** — interface detection via `getInterfaces()` still works.
- **`ConverterCodeGenerator` / `InterfaceConverterBuilder`** — dispatch converter generation unchanged.
- **`DataImplGenerator`** — works with any package/class combination.
- **`getInnerDatumClass()`** — field references point to the interface type (root package), unchanged.
- **`NamingStrategy`** — no new methods; reuses existing `toPackageNameFormat()`.

## Rationale

1. **Readable names**: `credential.VerificationKey` is clearer than `CredentialVerificationKey`.
2. **Collision avoidance**: `credential.VerificationKey` and `paymentcredential.VerificationKey` are in different packages.
3. **IDE navigation**: Sub-packages group variants logically under their interface.
4. **Automatic converter resolution**: No special handling — `ConstrAnnotationProcessor` derives converter package from the variant's `TypeElement`.
5. **Consistent conventions**: Uses the same `toPackageNameFormat()` as Aiken module path conversion — one rule for all package naming.

## Consequences

### Positive
- Shorter, more readable variant class names.
- Logical grouping in IDE file trees and package explorers.
- Naming collisions eliminated via package scoping.
- No changes needed to converter generation pipeline.
- Unified package naming convention across the codebase.

### Negative
- More packages generated (one sub-package per interface).
  - **Mitigation**: IDE package trees handle this naturally.
- Import paths are slightly longer (e.g., `import com.example.model.credential.VerificationKey`).
  - **Mitigation**: IDEs auto-manage imports.

## References

- `NamingStrategy.toPackageNameFormat()` — unified package name formatting
- `FieldSpecProcessor.buildVariantTypeSpec()`, `createDatumClass()` — variant sub-package generation
- ADR-0002: Blueprint Code Generation Pipeline (INTERFACE classification flow)
- ADR-0008: Schema Classification Strategy (anyOf classification)
