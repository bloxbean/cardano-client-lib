# ADR 0005: Use Definition Keys as Source of Truth for Class Names

- **Status**: Accepted
- **Date**: 2026-02-10
- **Deciders**: Cardano Client Lib maintainers
- **Related**: CIP-57 Plutus Contract Blueprints specification

## Context

The CIP-57 Plutus Contract Blueprint specification defines two naming mechanisms for schema definitions:

1. **Definition keys** (`#/definitions/<key>`): Technical identifiers that form the schema's structural path, used in JSON references (`$ref`)
2. **Title fields** (`schema.title`): Optional human-readable labels for UI display and documentation

Per CIP-57, the title field "MAY be used to provide a human-friendly name for documentation purposes" (i.e., it's optional decoration).

### The Problem

The annotation processor had **inconsistent behavior**:
- **Namespace extraction**: Used definition keys (correct)
- **Class name resolution**: Preferred title fields over definition keys (incorrect)

This created three issues:

1. **CIP-57 violation**: Using optional title fields as the primary source for structural naming violates the specification's intent
2. **Unpredictable code generation**: Developers cannot reliably predict class names from definition keys alone
3. **Namespace extraction bug**: Generic types incorrectly extracted namespace from type parameters instead of base types
   - Example: `"Option<cardano/address/Credential>"` → extracted `"cardano.address"` instead of `""` (empty)

### Real-World Evidence

Analysis of 45+ production blueprints (Aiken v1.0.21-alpha through v1.1.17) showed:
- **44/45 blueprints**: Title matches definition key's last segment
- **1/45 blueprints**: Title differs (abstract PlutusData type, skipped during generation)

**Conclusion**: Title preference provides no practical benefit while violating CIP-57 and introducing complexity.

## Decision

**Use definition keys as the authoritative source for class names and namespace extraction**, treating titles as optional fallback only.

### Scope

**Changes apply to:**
- ✅ Top-level datum/redeemer class names
- ✅ Interface names for schemas with multiple constructors
- ✅ Package namespace resolution

**Does NOT change:**
- ❌ Field names within classes (continue using `schema.title`)
- ❌ Validator parameter names (continue using `parameter.title`)

### Resolution Strategy

1. **Class names**: Prefer definition key (extract last segment), fallback to title if key is null/empty
2. **Namespace extraction**: Extract from base type only (strip generic parameters before checking for module paths)

### Example

```json
"cardano/transaction/OutputReference": {
  "title": "OutputReference",
  "fields": [
    {"title": "transaction_id", "$ref": "#/definitions/ByteArray"},
    {"title": "output_index", "$ref": "#/definitions/Int"}
  ]
}
```

Generated code:
```java
package cardano.transaction.model;  // namespace from definition key

public class OutputReference {      // class name from definition key
    private ByteArray transactionId; // field name from title (UNCHANGED)
    private Integer outputIndex;     // field name from title (UNCHANGED)
}
```

## Rationale

1. **CIP-57 compliance**: Definition keys are technical identifiers per specification; titles are optional decoration
2. **Predictability**: Developers can derive class names directly from blueprint structure
3. **Consistency**: Single source of truth for namespace and class name extraction
4. **Correctness**: Fixes generic type namespace extraction bug (base type vs. type parameters)
5. **No breaking changes**: 44/45 blueprints already have matching titles/keys; generated code remains identical

### Why Field Names Are Unchanged

Field names and parameter names represent the contract's API. Changing them would break compatibility with on-chain contracts. Only top-level class organization (packages/classes) can be safely standardized.

## Alternatives Considered

### 1. Keep Title-First Approach
**Rejected**: Violates CIP-57, unpredictable behavior, no practical benefit (44/45 blueprints already match).

### 2. Make Preference Configurable
**Rejected**: Adds complexity, multiple code paths, unclear defaults. Single source of truth is simpler and more maintainable.

### 3. Extract Namespace from Type Parameters (Status Quo)
**Rejected**: This was the bug. Violates CIP-57 and causes incorrect namespace resolution for generic types like `Option<T>`.

## Consequences

### Positive

- ✅ Full CIP-57 compliance for code generation
- ✅ Predictable, consistent class naming
- ✅ Simpler logic with single source of truth
- ✅ Correct generic type handling
- ✅ No breaking changes (all existing blueprints work identically)

### Neutral

- Title fields remain valid for field names, parameter names, and documentation
- Rare edge case: If future blueprints use verbose definition keys, class names may be longer (acceptable tradeoff)

### Negative

- Potential confusion if developers expect title fields to control class names
  - **Mitigation**: Clear documentation, consistent behavior across all blueprints

## Validation

- **Unit tests**: All 219 annotation-processor tests pass
  - Updated 18 tests validating namespace extraction
  - Added 4 new tests for generic types with module paths
  - Updated 4 tests for class name resolution
- **Integration tests**: Validated against 45+ production blueprints (SundaeSwap V2/V3, CIP-113, Gift Cardano, etc.)
- **Compatibility**: Generated code identical for 44/45 blueprints; 1/45 is abstract type (skipped)

## Implementation

Modified two key methods:
1. `BlueprintUtil.getNamespaceFromReferenceKey()`: Extract from base type (strip generics first)
2. `FieldSpecProcessor.resolveTitleFromDefinitionKey()`: Prefer definition key, fallback to title

See commit cf953ed1 for implementation details.

## References

- [CIP-57 Plutus Contract Blueprints](https://cips.cardano.org/cip/CIP-0057)
- [JSON Pointer (RFC 6901)](https://datatracker.ietf.org/doc/html/rfc6901) — Definition key escaping rules
- ADR-0003: Shared Blueprint Type Registry
- ADR-0004: Blueprint Test File Naming with Aiken Version
