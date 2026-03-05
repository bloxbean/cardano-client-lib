# ADR 0016: anyOf Interface Variants as Nested Static Inner Classes

- Status: Accepted
- Date: 2026-03-04
- Owners: Cardano Client Lib maintainers
- Related: ADR-0002 (Blueprint Code Generation Pipeline), ADR-0008 (Schema Classification Strategy), PR #595

## Context

When the schema classifier (ADR-0008) identifies an `INTERFACE` classification — an `anyOf` schema with more than one variant — the processor generates a Java interface and one class per variant. Prior to this change, each variant was emitted as a **top-level class** that implemented the interface.

This caused two problems:

1. **Naming collisions**: Multiple interfaces could share variant names. For example, both `Credential` and `PaymentCredential` could have a `VerificationKey` variant. As top-level classes, these would collide in the same package.
2. **Semantic incorrectness**: A variant like `VerificationKey` belongs to its parent interface. Placing it at the top level loses that scoping relationship.

## Decision

Generate each `anyOf` variant as a **`static` nested inner class** of its parent interface. This scopes variants naturally (e.g., `Credential.VerificationKey` vs `PaymentCredential.VerificationKey`) and eliminates naming collisions without artificial prefixes.

### Generated Structure

```
Before (top-level):                    After (nested):
  Credential.java (interface)            Credential.java
  VerificationKey.java (class)             public interface Credential {
  Script.java (class)                        public static abstract class VerificationKey
                                                 implements Data<Credential.VerificationKey>, Credential { ... }
                                             public static abstract class Script
                                                 implements Data<Credential.Script>, Credential { ... }
                                           }
```

A single `.java` file is emitted per interface, containing the interface declaration and all nested variant classes.

### Key Implementation

#### `FieldSpecProcessor` — Interface and Variant Generation

- `buildInterfaceTypeSpecBuilder(String interfaceName)` — creates the interface `TypeSpec.Builder` with `@Constr` annotation and public modifier.
- `buildVariantTypeSpec(String ns, String interfaceName, BlueprintSchema schema)` — creates each variant as a `static abstract class` nested inside the interface:
  - Uses `ClassName.get(pkg, interfaceClassName, className)` to construct the nested class reference.
  - Adds both `Data<InterfaceName.VariantName>` and the parent interface as superinterfaces.
  - Applies `@Constr(alternative = X)` annotation with the correct constructor index.
  - Processes fields directly from the variant schema (no recursive `collectAllFields`).
- Variants are added to the interface builder via `interfaceBuilder.addType(variantTypeSpec)`.
- The interface file is written once, containing all nested variants.

#### `ClassDefinitionGenerator` — Nested Class Detection

Detects nested classes by inspecting the enclosing element:

```java
Element enclosing = typeElement.getEnclosingElement();
if (enclosing != null && enclosing.getKind().isInterface()) {
    String enclosingName = ((TypeElement) enclosing).getSimpleName().toString();
    prefix = enclosingName + className;  // e.g., "CredentialVerificationKey"
}
```

Prefixes converter and impl class names to avoid collisions:
- `Credential.VerificationKey` → converter: `CredentialVerificationKeyConverter`, impl: `CredentialVerificationKeyImpl`
- `Credential.Script` → converter: `CredentialScriptConverter`, impl: `CredentialScriptImpl`

`getConverterClassFromField()` uses `String.join("", fieldClass.simpleNames())` to flatten nested class names for converter lookup.

#### `ConverterCodeGenerator` — Nested Class Resolution

Uses `ClassName.bestGuess(objType)` to correctly resolve nested class references:

```java
// objType = "com.example.Credential.VerificationKey"
// ClassName.bestGuess() parses dotted notation into correct nested ClassName
ClassName constrTypeName = ClassName.bestGuess(constructor.getObjType());
```

Interface converter `toPlutusData()` dispatches to the correct variant converter via `instanceof` checks against nested class types.

#### `DataImplGenerator` — Impl Class Generation

Similarly uses `ClassName.bestGuess(classDef.getObjType())` to construct the correct nested class reference for impl generation.

#### `BlueprintAnnotationProcessor`

The `buildVariantInterfaceMap()` pre-scan was removed — it is no longer needed since variant scoping is handled naturally by nesting within the parent interface.

## Rationale

1. **Natural scoping**: Java's nested class mechanism directly models the "variant belongs to interface" relationship. `Credential.VerificationKey` is unambiguous.
2. **No artificial prefixes**: Previous approaches would prefix variant names (e.g., `CredentialVerificationKey`) to avoid collisions, losing the clean name. Nesting preserves the short name while avoiding collisions.
3. **Single file per interface**: All variants for an interface live in one file, making it easy to see the complete sum type at a glance.
4. **`ClassName.bestGuess()` support**: JavaPoet's `ClassName.bestGuess()` naturally handles dotted names as nested classes, making the implementation straightforward.

## Consequences

### Positive
- Naming collisions between same-named variants of different interfaces are eliminated.
- Generated code is semantically correct — variants are scoped to their parent interface.
- Converter and impl class names are unambiguous via enclosing-name prefixing.
- Reduced number of generated files (one per interface instead of one per variant).

### Negative
- Converter/impl class names become longer for nested variants (e.g., `CredentialVerificationKeyConverter`).
  - **Mitigation**: These names are generated and rarely typed manually.
- All variant classes must be processed together when the interface is generated, requiring all variant schemas to be available at interface processing time.
  - **Mitigation**: CIP-57 blueprint structure guarantees all variants are present in the `anyOf` array.

## References

- `FieldSpecProcessor.buildInterfaceTypeSpecBuilder()`, `buildVariantTypeSpec()` — interface and variant generation
- `ClassDefinitionGenerator` — nested class detection and name prefixing
- `ConverterCodeGenerator` — `ClassName.bestGuess()` for nested class resolution
- `DataImplGenerator` — impl generation with nested class support
- ADR-0002: Blueprint Code Generation Pipeline (INTERFACE classification flow)
- ADR-0008: Schema Classification Strategy (anyOf classification)
