# ADR 0006: Generic Type Syntax Handling

- Status: Accepted
- Date: 2026-02-25
- Owners: Cardano Client Lib maintainers
- Related: ADR-0005 (Definition Keys as Source of Truth), CIP-57

## Context

The Aiken compiler has evolved its blueprint output format across major versions, changing how generic type instantiations are represented in definition keys:

- **Aiken v1.0.x** uses a dollar-sign (`$`) delimiter: `Interval$Int`, `List$Option$Credential`
- **Aiken v1.1.x** uses angle-bracket syntax: `Option<cardano/address/Credential>`, `List<Int>`, `Interval<Int>`

The annotation processor must handle both syntaxes correctly, distinguishing between:
1. **Built-in containers** (e.g., `List`, `Option`) — mapped to Java standard types, no class generation needed.
2. **Domain-specific generics** (e.g., `Interval<Int>`) — require generated Java classes.

Without this distinction, the processor would either generate redundant classes for built-in containers or fail to generate necessary classes for domain-specific types.

## Decision

Implement a two-phase resolution strategy in `BlueprintAnnotationProcessor.resolveDefinitionKeyForClassGeneration()`:

1. **Extract base type**: Find the first `$` or `<` delimiter and take everything before it.
2. **Classify**: Check the simple name (last segment after `/`) against a built-in container list. Skip containers; return the base type for domain-specific generics.

### Built-in Container List (9 types)

These types are skipped during class generation because they map to Java standard types or are handled by specialized `DataTypeProcessor` implementations:

| Container | Reason for Skipping |
|-----------|-------------------|
| `List` | Maps to `java.util.List<T>` |
| `Option` | Handled by `OptionDataTypeProcessor` → `java.util.Optional<T>` |
| `Optional` | Same as Option |
| `Tuple` | Maps to `PlutusData` or specialized tuple handling |
| `Pair` | Handled by `PairDataTypeProcessor` |
| `Map` | Maps to `java.util.Map<K,V>` |
| `Dict` | Aiken's dictionary type, same as Map |
| `Data` | Opaque `PlutusData` (see ADR-0007) |
| `Redeemer` | Opaque `PlutusData` (see ADR-0007) |

Implemented in `BlueprintUtil.isBuiltInGenericContainer(String simpleName)`.

### Resolution Examples

| Input Definition Key | Base Type | Simple Name | Category | Return Value |
|---------------------|-----------|-------------|----------|-------------|
| `List$Int` | `List` | `List` | Built-in container | `null` (skip) |
| `Option<Credential>` | `Option` | `Option` | Built-in container | `null` (skip) |
| `Tuple$Int_String` | `Tuple` | `Tuple` | Built-in container | `null` (skip) |
| `Data` | `Data` | `Data` | Built-in abstract | `null` (skip) |
| `Interval$Int` | `Interval` | `Interval` | Domain-specific | `"Interval"` |
| `aiken/interval/IntervalBound<Int>` | `aiken/interval/IntervalBound` | `IntervalBound` | Domain-specific | `"aiken/interval/IntervalBound"` |
| `cardano/transaction/ValidityRange` | (no generic) | `ValidityRange` | Non-generic | `"cardano/transaction/ValidityRange"` |

## Rationale

1. **Backward compatibility**: Older blueprints (Aiken v1.0.x) using `$` syntax continue to work without changes.
2. **Forward compatibility**: Newer blueprints (Aiken v1.1.x) using angle-bracket syntax are handled by the same code path.
3. **Language neutrality**: The detection is structural (delimiter-based), not tied to a specific Aiken version.
4. **Real-world impact**: SundaeSwap V2 blueprints use `Interval$Int` — without this logic, either no `Interval` class would be generated or a redundant `List` class would be emitted.

## Implementation

### Key Methods

**`BlueprintAnnotationProcessor.resolveDefinitionKeyForClassGeneration(String definitionKey)`**
- Detects generic instantiation by checking for `<`, `>`, or `$` characters.
- Extracts base type by finding first `$` then first `<` delimiter.
- Extracts simple name (last segment after `/`) for container detection.
- Returns `null` for built-in containers; returns base type for domain-specific generics; returns key as-is for non-generic types.

**`BlueprintUtil.isBuiltInGenericContainer(String simpleName)`**
- Simple string equality check against the 9 known container names.
- Called with the simple name (not the full namespaced path).

### Processing Flow

```
Definition Key: "aiken/interval/IntervalBound<Int>"
  │
  ├─ Contains '<' → generic instantiation detected
  │
  ├─ Extract base type: "aiken/interval/IntervalBound"
  │
  ├─ Extract simple name: "IntervalBound"
  │
  ├─ isBuiltInGenericContainer("IntervalBound") → false
  │
  └─ Return: "aiken/interval/IntervalBound" → generate class
```

## Consequences

### Positive
- Single code path handles both Aiken generic syntaxes uniformly.
- No redundant class generation for built-in containers.
- Domain-specific generics like `Interval` get proper typed classes.

### Negative
- The built-in container list is hardcoded. If Aiken introduces new container types, the list must be updated manually.
  - **Mitigation**: The list has been stable across Aiken v1.0.x through v1.1.x. New containers are rare.

## References

- `BlueprintAnnotationProcessor.resolveDefinitionKeyForClassGeneration()` — main resolution logic
- `BlueprintUtil.isBuiltInGenericContainer()` — container detection
- ADR-0005: Definition keys as source of truth for class names
- ADR-0007: Opaque PlutusData detection (covers `Data` and `Redeemer` skipping)
