# ADR 0003: Shared Blueprint Type Registry

- Status: Proposed
- Date: 2025-10-02
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

- **Schema signatures**: We will describe a schema by a structural signature (datatype, nested fields, `$ref` targets, titles, indices) and normalise it to a stable string key. Equal signatures imply identical structure, regardless of the compiled language that emitted the blueprint.
- **Registry API**: Define a `BlueprintTypeRegistry` SPI (service-provider interface) that can look up a Java type (`TypeName`) for a given schema signature. The annotation processor consults the registry before generating classes.
- **Layered sources**: Ship defaults in `plutus-blueprint-std` (or similar) and discover user extensions via `META-INF/services`, processor options, or configuration files. Later sources override earlier ones.

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

- Implement in `plutus` module (shared with loader) so both runtime consumers and the processor can use it.
- For a `BlueprintSchema`, compute a canonical form that includes:
  - `dataType`, `title`, `comment` (optional), constructor index.
  - Normalised references (strip `#/definitions/`, resolve `~1`).
  - Recursively computed signatures for `fields`, `anyOf`/`allOf`/`oneOf`, `items`, `keys`, `values`, `left/right`.
  - Deterministic ordering (e.g., by field order) to avoid spurious differences.
- Serialise to a string or lightweight hash (e.g., SHA-256) that serves as the registry key.

### 2. `BlueprintTypeRegistry` SPI

```java
public interface BlueprintTypeRegistry {
    Optional<TypeName> lookup(SchemaSignature signature, BlueprintSchema schema, LookupContext context);
}
```

- `LookupContext` will expose namespace, package resolver, annotation options, etc.
- Provide a base implementation that uses a simple `Map<String, TypeName>` for static registrations.

### 3. Default registry module

- Add `plutus-blueprint-std` (package name TBD) with classes representing ledger primitives (`Address`, `Credential`, `VerificationKeyHash`, etc.).
- Populate an `AikenBlueprintTypeRegistry` mapping canonical signatures for those schemas to the shared classes.
- Export via `META-INF/services/com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry` so the processor discovers it automatically.

### 4. Processor integration

- Extend `FieldSpecProcessor` / `DataTypeProcessUtil` pipeline:
  1. Before generating a class for a schema, compute its signature and query registries.
  2. If a registry returns a `TypeName`, register it in `GeneratedTypesRegistry` (so we do not produce duplicates) and reuse the type.
  3. Otherwise, proceed with current generation.
- Support override ordering: user registries take precedence, followed by defaults, then generation.

### 5. Consumer extensibility

- Provide two integration points:
  1. **Service loader**: Users add their own implementation to `META-INF/services/...BlueprintTypeRegistry` on the annotation processor classpath.
  2. **Processor option**: Support an annotation-processing option (e.g., `-Acardano.registry=config.json`) that lists bespoke mappings; the processor loads and registers them at build time.
- Document how to disable defaults (e.g., `-Acardano.registry.disableDefaults=true`) for teams that want full control.

## Default Registrations (Seed List)

Start with schemas that recur in current blueprints and align with the Aiken stdlib but are also general Cardano ledger concepts:

- `Address`, `Credential`, `Referenced Credential`, `VerificationKeyHash`, `ScriptHash`.
- `TransactionInput`, `Value`, `PolicyId`, `AssetName`, `StakeCredential`.
- Generic `ByteArray`, `Int`, `String`, `Pair`, `java.util.Optional<T>`, `List<ByteArray>` (already represented but we can point them to existing core classes where sensible).

We will harvest canonical schemas from the Aiken stdlib repository and CIP-57 examples, compute their signatures once, and ship them with the registry.

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

- Signature collisions: detect and warn when two different schemas hash to the same signature string (extremely unlikely with canonical string keys, but worth guarding).
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

- How to version shared classes to avoid breaking consumers when schemas evolve? (Proposal: semantic version the registry module and maintain backward-compatible constructors.)
- Should the registry operate at the validator namespace level (so different contracts can map the same schema to different classes)? Initial plan is global; reevaluate if conflicts appear.
- Runtime exposure of the registry SPI is deferred; the initial implementation remains compile-time only to keep scope tight.
