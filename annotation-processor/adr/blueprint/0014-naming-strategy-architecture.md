# ADR 0014: Naming Strategy Architecture

- Status: Accepted
- Date: 2026-02-26
- Owners: Cardano Client Lib maintainers
- Related: ADR-0005 (Definition Keys as Source of Truth), ADR-0006 (Generic Type Syntax Handling), ADR-0013 (JSON Pointer Handling), CIP-57

## Context

CIP-57 blueprint definition keys and schema titles contain identifiers from multiple Aiken compiler versions and potentially other smart contract languages. These identifiers use conventions that are incompatible with Java naming rules:

- **Dollar-sign delimiters**: `Interval$Int`, `List$Option$Credential` (Aiken v1.0.x)
- **Underscore delimiters**: `my_custom_type` (snake_case from Aiken)
- **Angle-bracket generics**: `Option<cardano/address/Credential>` (Aiken v1.1.x)
- **Forward-slash module paths**: `aiken/crypto/Hash` (escaped as `aiken~1crypto~1Hash` in JSON Pointer)
- **JSON Pointer escapes**: `~0`, `~1` (see ADR-0013)

A single naming strategy must produce valid, readable Java identifiers from all of these patterns.

## Decision

Define a `NamingStrategy` interface and provide `DefaultNamingStrategy` as the standard implementation, with a multi-step preprocessing pipeline.

### `NamingStrategy` Interface

```java
public interface NamingStrategy {
    String toClassName(String value);           // PascalCase class name
    String toCamelCase(String value);           // lowerCamelCase field/method name
    String firstUpperCase(String value);
    String firstLowerCase(String value);
    String sanitizeIdentifier(String value);    // Remove/replace invalid chars
    String toPackageNameFormat(String pkgName); // Lowercase, strip special chars
}
```

### `DefaultNamingStrategy` Preprocessing Pipeline

The `toCamelCase()` method (and by extension `toClassName()`) applies transformations in a specific order:

```
Input: "aiken~1crypto~1Hash<Int,Bool>"
         │
         ▼
  1. JSON Pointer Unescape
     "aiken/crypto/Hash<Int,Bool>"
         │
         ▼
  2. Sanitize Angle Brackets
     List<Int>       → "ListOfInt"
     Tuple<Int,Int>  → "TupleOfIntAndInt"
     Hash<Int,Bool>  → "HashOfIntAndBool"
         │
         ▼
  3. Sanitize Forward Slashes
     "aiken/crypto/HashOfIntAndBool"
     → "AikenCryptoHashOfIntAndBool"
     (capitalize each segment)
         │
         ▼
  4. Convert Delimiters
     $ preserved as-is (each $-part processed)
     _, -, space → capitalize next char
         │
         ▼
  5. Finalize Identifier
     Remove remaining invalid chars
     Prefix _ if starts with digit
```

### CIP-57 Pattern Examples

| Input | After Pipeline | As Class Name |
|-------|---------------|---------------|
| `Interval` | `interval` | `Interval` |
| `Interval$Int` | `interval$Int` | `Interval$Int` |
| `my_custom_type` | `myCustomType` | `MyCustomType` |
| `aiken/crypto/Hash` | `aikenCryptoHash` | `AikenCryptoHash` |
| `Option<Credential>` | `optionOfCredential` | `OptionOfCredential` |
| `Tuple<Int,String>` | `tupleOfIntAndString` | `TupleOfIntAndString` |
| `types~1order~1Action` | `typesOrderAction` | `TypesOrderAction` |

### Package Name Format

`toPackageNameFormat()` converts to all lowercase and strips characters invalid in Java package names:
```java
return pkg.toLowerCase()
    .replace("-", "").replace("_", "")
    .replace("/", "").replace("~", "")
    .replace("<", "").replace(">", "")
    .replace(",", "").replace("$", "");
```

### Keyword Escaping

`firstLowerCase()` appends a `_` suffix when the lowercased result is a Java reserved keyword:

```java
public String firstLowerCase(String value) {
    String result = value.substring(0, 1).toLowerCase() + value.substring(1);
    if (SourceVersion.isKeyword(result)) {
        result = result + "_";
    }
    return result;
}
```

This prevents generating invalid Java identifiers from schema titles that happen to match keywords:

| Input | `firstLowerCase()` Output |
|-------|--------------------------|
| `Enum` | `enum_` |
| `Class` | `class_` |
| `Default` | `default_` |
| `Action` | `action` (no change) |

Detection uses `javax.lang.model.SourceVersion.isKeyword()`, which covers all Java reserved words and contextual keywords.

### Usage Across the Processor

`DefaultNamingStrategy` is instantiated by:
- `FieldSpecProcessor` — class and field name derivation for definitions
- `ValidatorProcessor` — validator class naming
- `DatumModelFactory` — class name from schema title
- `DataTypeProcessUtil` — alternative name resolution
- `SchemaClassifier` — class name for classification decisions

## Rationale

1. **Pipeline ordering** prevents transformation conflicts. JSON Pointer unescaping must happen first (before `/` is used as a delimiter). Angle brackets must be converted before forward slashes are processed.
2. **Language neutrality**: The strategy handles patterns from Aiken v1.0.x (`$`), v1.1.x (`<>`), and arbitrary module paths (`/`) without hardcoding version-specific logic.
3. **Interface abstraction** allows alternative naming strategies (e.g., for other smart contract languages) without modifying processor code.
4. **`$` preservation**: Dollar signs are valid in Java identifiers and are preserved to maintain compatibility with Aiken v1.0.x naming.

## Consequences

### Positive
- Single, consistent naming logic used across all code generation phases.
- Handles all known CIP-57 identifier patterns from Aiken v1.0.x and v1.1.x.
- Interface abstraction enables future naming strategy alternatives.
- Pipeline approach makes each transformation step independently testable.

### Negative
- The multi-step pipeline can produce long class names for deeply nested generics (e.g., `MapOfIntAndListOfString`).
  - **Mitigation**: Deeply nested generics are uncommon in practice. The verbose name is still valid and unambiguous.
- The `$` preservation means generated class names may look unusual to Java developers unfamiliar with Aiken conventions.

## References

- `NamingStrategy` — naming strategy interface
- `DefaultNamingStrategy` — standard implementation with preprocessing pipeline
- `JsonPointerUtil` — JSON Pointer escape handling (step 1 of pipeline)
- ADR-0005: Definition Keys as Source of Truth (naming inputs)
- ADR-0006: Generic Type Syntax Handling (`$` and `<>` syntax)
- ADR-0013: JSON Pointer Handling (unescape logic)
