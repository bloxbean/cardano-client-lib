# ADR 0003: Shared Blueprint Type Registry

- Status: Accepted
- Date: 2025-10-02
- Updated: 2026-02-25
- Owners: Cardano Client Lib maintainers

## Context

Most blueprint files repeat the same schema fragments for common ledger concepts such as:

- Addresses, credentials, and hashes from the Aiken standard library.
- Basic CIP-57 primitives (byte arrays, integers, options, lists).
- Reusable tuples/pairs that recur across contracts.

Today, every project that uses the annotation processor gets a fresh copy of these model classes. This leads to:

- Duplicate classes emitted per project (and per blueprint) even though the semantics are identical.
- Larger generated footprints and harder diffing when only contract-specific models should change.
- No way for consumers to override a schema with their own audited domain type.

We want a solution that keeps code generation language-agnostic: Aiken is the dominant source today, but PlutusTx or Helios can emit the same schemas. Any deduplication scheme needs to recognise shared shapes without hard-coding a particular compiler.

## Decision

Introduce a “Shared Blueprint Type Registry” that sits between schema analysis and type generation. The registry will:

1. Provide built-in registrations for well-known CIP-57 schemas (initially seeded with Aiken standard-library types).
2. Allow consumers to register additional mappings, or override defaults, without modifying the generator.
3. Fall back to existing generation when a schema is unknown or intentionally customised.

Core ideas:

- **Schema signatures**: We describe a schema by a structural signature (datatype, nested fields, `$ref` targets, titles, indices) and normalise it to a canonical JSON string. Equal signatures imply identical structure, regardless of the compiled language that emitted the blueprint.
- **Registry API**: Define a `BlueprintTypeRegistry` SPI (service-provider interface) that can look up a `RegisteredType` (a decoupled value object with `packageName` and `simpleName`) for a given schema signature. The annotation processor consults the registry before generating classes.
- **Layered sources**: Ship defaults in the `plutus-aiken` module and discover user extensions via `META-INF/services` and processor options. Later sources override earlier ones.

With this in place, blueprints reusing standard schemas will lean on shared classes housed in the `plutus` module. Projects can still opt out by disabling the registry or providing their own mapping.

## Goals

- Reduce redundant generated classes for well-known schemas.
- Preserve language neutrality: detection is structural, not Aiken-key based.
- Allow downstream projects to plug in or override mappings with minimal ceremony.
- Maintain backward compatibility (existing generated code keeps working when registry is disabled).

## Non-Goals

- Automatic migration of already generated sources; this is opt-in via registry order.
- Changing the serialized blueprint JSON format.
- Enforcing any specific class naming inside consumer projects.

## Proposed Architecture

### 1. Schema Signature Builder

- Implemented in `plutus` module (`SchemaSignatureBuilder`) so both runtime consumers and the processor can use it.
- For a `BlueprintSchema`, computes a canonical form that includes:
  - `dataType`, `title`, `description`, `comment` (optional), constructor index.
  - `enum`, length constraints.
  - Normalised references (strip `#/definitions/`, resolve `~1`).
  - Recursively computed signatures for `fields`, `anyOf`/`allOf`/`oneOf`/`notOf`, `items`, `keys`, `values`, `left/right`.
  - Deterministic ordering via Jackson `ORDER_MAP_ENTRIES_BY_KEYS` to avoid spurious differences.
  - Identity tracking for recursive/circular schemas.
- Serialised to a **canonical JSON string** (not a hash) that serves as the registry key via `SchemaSignature.of(json)`.

### 2. `BlueprintTypeRegistry` SPI

```java
public interface BlueprintTypeRegistry {
    Optional<RegisteredType> lookup(SchemaSignature signature, BlueprintSchema schema, LookupContext context);
}
```

- Returns `Optional<RegisteredType>` — a decoupled value object (`packageName` + `simpleName` + `canonicalName()`) rather than JavaPoet-specific `TypeName`. This keeps the SPI independent of code-generation libraries.
- `LookupContext` exposes `namespace` (e.g., `"types.order"`) and `blueprintName` for contextual decisions.
- Provide a base implementation that uses a simple `Map<SchemaSignature, RegisteredType>` for static registrations.

### 3. Default registry module

- Implemented in the `plutus-aiken` module with shared classes in package `com.bloxbean.cardano.client.plutus.aiken.blueprint.std`.
- `AikenBlueprintTypeRegistry` maps canonical signatures for ledger-primitive schemas to the shared classes.
- Exported via `META-INF/services/com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry` so the processor discovers it automatically.

### 4. Processor integration

- Extend `FieldSpecProcessor` / `DataTypeProcessUtil` pipeline:
  1. Before generating a class for a schema, compute its signature and query registries.
  2. If a registry returns a `TypeName`, register it in `GeneratedTypesRegistry` (so we do not produce duplicates) and reuse the type.
  3. Otherwise, proceed with current generation.
- Support override ordering: user registries take precedence, followed by defaults, then generation.

### 5. Consumer extensibility

- Provide three integration points:
  1. **Service loader**: Users add their own `BlueprintTypeRegistry` implementation to `META-INF/services/...BlueprintTypeRegistry` on the annotation processor classpath.
  2. **Runtime registration**: `BlueprintTypeRegistryExtensions.registerByTitle(title, packageName, simpleName)` allows programmatic registration. Title-based lookups are consulted **before** signature-based lookups in `ServiceLoaderSharedTypeLookup`, using a thread-safe `ConcurrentHashMap`.
  3. **Processor option to disable**: `-Acardano.registry.enable=false` disables the registry entirely (returns a no-op lookup that always yields empty). The default is `true` (enabled).
- **Deferred**: The `-Acardano.registry=config.json` option for file-based bespoke mappings is not yet implemented; runtime registration via `BlueprintTypeRegistryExtensions` covers the same use case for now.

## Default Registrations (Actual)

The `AikenBlueprintTypeRegistry` ships 11 registrations, grouped by `SharedTypeKind`:

### Constructor-based types (`SharedTypeKind.CONSTRUCTOR`)
- `Address` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address`
- `Credential` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Credential`
- `ReferencedCredential` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ReferencedCredential`

### Bytes-wrapper types (`SharedTypeKind.BYTES`)
- `VerificationKey` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKey`
- `Script` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Script`
- `Signature` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Signature`
- `VerificationKeyHash` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash`
- `ScriptHash` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ScriptHash`
- `DataHash` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.DataHash`
- `Hash` → `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Hash`

### Pair type (`SharedTypeKind.PAIR`)
- `Pair` (two-ByteArray tuple) → `com.bloxbean.cardano.client.plutus.blueprint.type.Pair`

These were harvested from canonical schemas in the Aiken standard library and CIP-57 examples. Additional types (e.g., `TransactionInput`, `Value`) may be added in future releases as demand arises.

## Alternatives Considered

1. **Key-based mapping (Aiken-only)**: Simple but tightly coupled to Aiken naming; fails for other compilers.
2. **Do nothing**: Keeps redundancy and offers no reuse path.
3. **Global opt-in via annotation attribute**: Would still require consumers to wire their own registry; the chosen SPI approach subsumes this.

## Consequences

Positive:
- Smaller generated source sets when blueprints use shared schemas.
- Easier auditing of shared ledger types (single authoritative implementation).
- Extensible mechanism for future language/tooling integrations.

Neutral/Negative:
- Additional abstraction and discovery logic in the processor.
- Potential confusion if a schema almost matches but not exactly (mitigated by documentation and diagnostics).
- Need to distribute and version the shared-type module alongside the processor.

## Implementation Notes

- Signature collisions: since signatures are full canonical JSON strings (not hashes), collisions are structurally impossible — identical signatures mean identical schemas.
- Diagnostics: surface `Messager` warnings when a registry mapping is applied (debug mode) or when a registry rejects a schema that looks similar (future enhancement).
- Testing:
  - Unit tests for signature builder covering primitives, lists, variants, referencing, tuples.
  - Tests for default registry lookups (using blueprint fixtures similar to Aftermarket).
  - Integration tests that ensure generated sources reference shared classes when defaults are enabled and fall back when disabled.
- Scope: registry discovery is used during annotation processing; exposing it at runtime is future work.

## Rollout Plan & Tasks

- [x] Implement schema signature builder and unit tests in `plutus` module.
- [x] Create default registry module with shared ledger classes and service loader wiring.
- [x] Integrate registry discovery into the annotation processor behind a feature flag (`-Acardano.registry.enable=true`).
- [x] Document opt-in/override workflow for consumers.
- [x] Expose extension API (`registerSchema`) so applications can add custom mappings via subclassed registries.
- [x] Reevaluate enabling the registry by default after baking period (now enabled unless opt-out).

## Open Questions

- **Resolved**: How to version shared classes to avoid breaking consumers when schemas evolve? → Shared classes are versioned with the `plutus-aiken` module. Backward-compatible constructors are maintained.
- **Resolved**: Should the registry operate at the validator namespace level? → Global registry with `LookupContext` providing namespace hints. No conflicts observed so far.
- **Resolved**: Runtime exposure of the registry SPI? → Deferred; the implementation remains compile-time only. `BlueprintTypeRegistryExtensions.registerByTitle()` provides a runtime registration mechanism for test and advanced use cases.
- **Deferred**: File-based configuration (`-Acardano.registry=config.json`) is not implemented. Runtime registration via `BlueprintTypeRegistryExtensions` covers the same use case with less ceremony.
