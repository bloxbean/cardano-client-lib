# ADR 0013: JSON Pointer (RFC 6901) Handling

- Status: Accepted
- Date: 2026-02-26
- Owners: Cardano Client Lib maintainers
- Related: ADR-0005 (Definition Keys as Source of Truth), ADR-0014 (Naming Strategy Architecture), CIP-57

## Context

CIP-57 blueprint definition keys and `$ref` pointers use JSON Pointer syntax (RFC 6901) for references like `#/definitions/types~1order~1Action`. The `~` character is the escape character in JSON Pointer:

- `~0` represents a literal `~`
- `~1` represents a literal `/`

The Aiken compiler uses module paths containing `/` (e.g., `aiken/crypto/Hash`), which get escaped to `aiken~1crypto~1Hash` in definition keys. Correct handling of these escapes is critical — the unescape/escape order matters, and getting it wrong produces silently incorrect results.

## Decision

Centralize JSON Pointer escape handling in `JsonPointerUtil` with order-sensitive implementations.

### `JsonPointerUtil`

**`unescape(String value)`** — decode JSON Pointer escapes:
```java
public static String unescape(String value) {
    return value.replace("~1", "/")   // Step 1: ~1 → /
                .replace("~0", "~");  // Step 2: ~0 → ~
}
```

**`escape(String value)`** — encode for JSON Pointer:
```java
public static String escape(String value) {
    return value.replace("~", "~0")   // Step 1: ~ → ~0
                .replace("/", "~1");  // Step 2: / → ~1
}
```

### Why Order Matters

**Unescape order** — `~1` before `~0`:

| Input | Correct (`~1` first) | Wrong (`~0` first) |
|-------|----------------------|---------------------|
| `types~1order` | `types/order` | `types~1order` (unchanged, then `~1`→`/` = `types/order`) |
| `~01` | `~1` (literal tilde + "1") | First `~0`→`~`, then `~1`→`/` = `/` (WRONG) |
| `a~0b~1c` | `a~b/c` (correct) | `a~b~1c` → `a~b/c` (happens to work) |

The critical case is `~01`: it represents a literal `~` followed by `1` (not a `/`). Processing `~0` first would convert `~0` to `~`, leaving `~1`, which the second step incorrectly converts to `/`.

**Escape order** — `~` before `/`:

| Input | Correct (`~` first) | Wrong (`/` first) |
|-------|---------------------|---------------------|
| `a/b` | `a~1b` | `a~1b` (same) |
| `a~b` | `a~0b` | `a~0b` (same) |
| `a~/b` | `a~0~1b` (correct) | First `/`→`~1`, then `~`→`~0` = `a~0~01b` (WRONG) |

### Usage in `BlueprintUtil`

`JsonPointerUtil.unescape()` is called in 5+ `BlueprintUtil` methods:

| Method | Purpose | Example |
|--------|---------|---------|
| `getNamespaceFromReferenceKey()` | Extract package namespace from definition key | `types~1order~1Action` → `types.order` |
| `getNamespaceFromReference()` | Strip `#/definitions/` prefix, then extract namespace | `#/definitions/types~1order~1Action` → `types.order` |
| `normalizedReference()` | Normalize `$ref` to clean definition key | `#/definitions/types~1order~1Action` → `types/order/Action` |
| `getClassNameFromReferenceKey()` | Extract class name (last segment) from key | `types~1order~1Action` → `Action` |

These methods are used throughout the processor pipeline:
- `BlueprintAnnotationProcessor.process()` — namespace extraction for definition processing.
- `FieldSpecProcessor.resolveClassNameFromRef()` — class name resolution from `$ref`.
- `DataTypeProcessUtil.determineAlternativeName()` — fallback name from `$ref` when title is absent.

## Consequences

### Positive
- Centralized, tested implementation prevents order-sensitive bugs from being duplicated across the codebase.
- `BlueprintUtil` methods compose cleanly on top of `JsonPointerUtil` without reimplementing escaping.
- Handles edge cases (embedded tildes, multi-level paths) correctly per RFC 6901.

### Negative
- Developers must use `JsonPointerUtil` rather than writing inline replacements. A misplaced `replace("~0", "~")` in new code would reintroduce the order bug.
  - **Mitigation**: The utility is well-named and discoverable. Code review should flag any inline JSON Pointer handling.

## References

- `JsonPointerUtil` — centralized escape/unescape utility
- `BlueprintUtil` — consumer of `JsonPointerUtil` for namespace, class name, and reference resolution
- RFC 6901: JavaScript Object Notation (JSON) Pointer
- ADR-0005: Definition Keys as Source of Truth
- ADR-0014: Naming Strategy Architecture (uses unescaped values)
