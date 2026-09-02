# ADR-CIP113-002: Programmable Token Readiness Review and Remediation Plan

**Date**: 2026-09-02

**Status**: Proposed

**Scope**: `programmable-token`, `quicktx`

**Related ADR**: [ADR-CIP113-001](001-cip113-quicktx-extension-and-txplan-design-review.md)

**Decision owners**: Cardano Client Lib maintainers

---

## 1. Context

ADR-CIP113-001 established the Programmable Token domain API, typed extension intents, the generic
QuickTx extension lifecycle, TxPlan extension metadata, instance-scoped codecs, and a CIP-113
protocol adapter. Those foundations are now implemented and validated by unit tests and Yaci
DevKit end-to-end tests.

A production-readiness review found that the architecture should remain intact, but identified
several correctness, reuse, concurrency, validation, and test-harness gaps. This ADR records the
recommended decisions and an implementation plan. It is intentionally separate from
ADR-CIP113-001: the earlier ADR owns the architecture; this ADR owns hardening and release
readiness.

The implementation reviewed by this ADR is an experimental adapter for the vendored CIP-113
reference-contract snapshot `0.5.0-alpha.2`. It is not a claim of compatibility with every future
CIP-113 revision or deployment.

## 2. Decision Summary

Keep the current architecture:

- `quicktx` owns only the generic `QuickTxExtension`, `TxBuildExtension`, `ExtensionIntent`, build
  lifecycle, TxPlan metadata, and codec integration;
- `programmable-token` owns the domain facade, typed semantic intents, protocol abstraction, and
  CIP-113 adapter;
- authoring and TxPlan decoding produce typed, declarative intents and perform no chain I/O;
- the CIP-113 build extension aggregates all intents transaction-wide;
- named policy references remain deferred until build;
- pre-evaluation finalization, post-balance stabilization, and final verification remain mandatory;
- `contract_version` remains informational, while the configured deployment is the runtime
  compatibility anchor.

Before a beta designation, fix the transaction-correctness and lifecycle issues in Sections 3 and
4. Sections 5 through 7 are robustness, API-quality, maintainability, and release-gate work.

## 3. Transaction-Correctness Decisions

### 3.1 Restrict one third-party action to one policy

The current planner permits multiple policy ids for a single holder. The pinned CIP-113
third-party redeemer identifies one registry node, while the materializer has one framework
third-party withdrawal and resolves its redeemer from the first referenced registry node. A
multi-policy declaration therefore cannot authorize every policy correctly.

**Decision**: for the `0.5.0-alpha.2` dialect, one third-party transaction MUST contain exactly one
holder and one distinct programmable-token policy. Multiple transfer outputs for that holder and
policy remain supported and MUST be aggregated.

Validation MUST happen before UTxO selection or other backend-dependent materialization when the
policy ids are already available. The exception must identify the conflicting policy ids and state
that the operations must be split into separate transactions.

Batch third-party actions may be introduced only by a future dialect whose on-chain redeemer and
validator surface explicitly support multiple registry-node proofs. The generic Programmable Token
API must not imply that every dialect supports them.

### 3.2 Require consistent burn authorization per policy

There is one transfer-logic invocation and one issuance-logic invocation per applicable reward
credential. Multiple burn intents for the same policy cannot independently supply different
redeemers.

**Decision**: aggregate both the transfer redeemer and issuance redeemer per policy. Semantically
equal Plutus data is accepted; conflicting values fail before any burn is recorded. Do not use
last-write-wins maps for authorization data.

The comparison must use canonical Plutus-data equality, not Java object identity or source form.
HEX and structured YAML representations resolving to the same Plutus data are equal.

### 3.3 Preserve aggregate materialization

The fixes above MUST NOT turn each typed intent into an independently materialized transaction
fragment. Transfer and burn requirements must still be aggregated by policy before input selection,
programmable change calculation, withdrawal construction, and index finalization.

## 4. Build Lifecycle, Reuse, and Concurrency

### 4.1 Generated core intents are a build-local overlay

`TxBuildExtension.prepare()` currently appends generated core intents to the authored `Tx`. A
second build of the same `TxPlan` or `TxContext` generates and appends them again. That violates the
declarative model and ADR-CIP113-001's sequential plan-reuse guarantee.

**Decision**: extension preparation MUST NOT mutate an authored transaction's intent list.
`ExtensionBuildContext` will own build-local prepared intents associated with a source
`AbstractTx<?>`.

The preferred generic QuickTx seam is conceptually:

```java
extensionContext.addPreparedIntent(sourceTx, generatedCoreIntent);
List<TxIntent> prepared = extensionContext.preparedIntents(sourceTx);
txBuilder = txBuilder.andThen(sourceTx.complete(prepared));
```

Names may change to match existing conventions, but the semantics are fixed:

1. authored intents remain unchanged;
2. prepared intents exist for one `_build()` invocation only;
3. `AbstractTx.complete(...)` evaluates authored and prepared intents as one ordered set for output
   calculation, input construction, intent application, deposit resolution, and script detection;
4. prepared intents are visible only to the build that created them;
5. input reservations remain build-local;
6. a failed build leaves the original plan unchanged;
7. sequential builds start from the same semantic plan and fresh chain state.

QuickTx must retain a no-argument `complete()` path for existing code. The overlay-aware overload
should remain package-internal unless an external extension implementation demonstrably needs a
public method.

Tests must compare the authored intent count and serialized plan before and after successful and
failed builds. Building twice must not duplicate inputs, outputs, withdrawals, mints, witnesses, or
redeemers.

### 4.2 Define concurrent plan reuse separately

Sequential reuse is required. Concurrent use of the same mutable `TxPlan`, `Tx`, or
`QuickTxBuilder.TxContext` is not required and should not be implied. Document authoring models as
mutable and not thread-safe. Runtime extension descriptors and protocol services, however, are
expected to be reusable by independent builds and must be thread-safe.

### 4.3 Publish resolved deployment state atomically

The CIP-113 service currently has several independently mutable resolution fields and a boolean
`resolving` re-entry guard. A concurrent caller can observe resolution in progress, return, and
consume partially initialized state.

**Decision**: resolution produces one immutable `ResolvedCip113Deployment` state containing the
resolved deployment, coordination UTxO, issuance-template UTxO, script resolver/cache inputs, and
registry lookup prerequisites. Publish that state atomically only after complete validation.

Implementation may use a synchronized one-shot initializer or a shared `CompletableFuture`, but it
must provide these behaviors:

- concurrent first callers wait for the same resolution result;
- no caller observes partial state;
- a failure is delivered consistently to all waiting callers;
- retry behavior is explicit rather than accidental;
- re-entrant internal calls do not deadlock;
- registry and deployment-script caches are safe for independent concurrent builds.

Prefer immutable snapshots per build for registry contents. Avoid invalidating shared mutable
registry state as part of normal transaction planning.

### 4.4 Fail closed when resolving live protocol UTxOs

An in-place protocol upgrade can spend and recreate the coordination output. Falling back silently
to the bootstrap output when an asset query fails can therefore return a spent reference input.

**Decision**: backend errors while finding the live coordination UTxO fail resolution with a clear
exception/result. The bootstrap output may be used only if the service positively verifies that it
is still unspent and carries the expected NFT. Empty successful lookup results also fail with a
diagnostic naming the asset unit and bootstrap transaction.

Resolution failures must be logged through SLF4J with enough context for diagnosis and must not be
converted to an unrelated later script-evaluation error.

## 5. Protocol Capability and Validation Decisions

### 5.1 Verification-key logic credentials

The domain credential model can represent script and verification-key credentials. The current
transaction materializer can invoke script credentials but cannot construct the required signer and
redeemer-less withdrawal flow for transfer or third-party verification-key credentials.

**Decision for the initial dialect**: reject any registration or registry update that would create
a token requiring a verification-key operation the adapter cannot execute. Fail before submitting
the registry change, so this library cannot create a token that it cannot subsequently operate.

The empty verification-key sentinel used to forbid unfracking remains valid because it is data, not
an operation the current adapter attempts to invoke.

A later enhancement may support key credentials by adding the correct required signer,
redeemer-less withdrawal, signer-resolution API, and TxPlan representation. That work must have its
own capability flag and end-to-end tests.

### 5.2 Remove unsupported `unfrack` from schema version 1

The Java facade and operation registry currently expose `unfrack`, while the CIP-113 protocol
capabilities deliberately omit it and materialization always fails.

**Decision**: remove `pt:unfrack` and the Java `unfrack(...)` facade method from programmable-token
schema version `1` before release. Internal codec/model work may remain only if it is clearly marked
non-public and unused. Add the operation in a later schema version when implemented and tested.

Unsupported operations decoded from YAML must fail during codec/runtime binding, not after backend
access.

### 5.3 Validate asset names at the semantic boundary

`ProgrammableTokenAsset.name` is raw asset-name bytes encoded as hexadecimal.

**Decision**: intent validation requires:

- a non-null name;
- no `0x` prefix in the serialized domain value;
- even-length valid hexadecimal;
- at most 32 decoded bytes;
- a non-null quantity with the sign required by the semantic operation.

Canonical serialization emits lowercase hexadecimal. Errors must name the intent operation and
asset entry rather than surfacing from the lower-level `Asset` constructor.

### 5.4 Strengthen deployment metadata validation

`schema_version` is the serialized programmable-token intent schema. It remains mandatory and is
validated by the generic extension codec. `protocol` remains mandatory and identifies the selected
dialect.

`contract_version` remains optional informational provenance. It is neither a compatibility switch
nor a user-selected runtime profile while CIP-113 public deployment/version discovery is undefined.

For CIP-113 deployment metadata:

- when `bootstrap_tx` is present, it must be a valid transaction hash and match the configured
  service deployment;
- when `network` is present, it must be a valid network id and match the configured deployment;
- a metadata mismatch fails before chain I/O;
- omission means "bind to the explicitly configured runtime deployment" and must be documented;
- canonical serialization through a configured service should emit both values when known so a
  reviewed plan is pinned rather than ambient.

### 5.5 Make extension metadata immutable

Extension descriptors and TxPlan bindings must not share caller-mutable deployment maps.

**Decision**: replace mutable metadata exposure with immutable values or defensive copies at every
boundary. At minimum, normalize and copy the deployment map on construction, storage in `TxPlan`,
return from `getExtensions()`, and return from `QuickTxExtension.metadata()`.

Nested YAML-compatible lists and maps must also be copied or normalized; `Map.copyOf` alone is not
enough for recursively mutable values. Metadata equality and deterministic serialization should be
tested.

## 6. TxPlan and Java API Decisions

### 6.1 Resolve runtime variables structurally

The extension-aware codec currently parses YAML, serializes the tree back to YAML, substitutes
`${variable}` text across the whole document, and parses again. This loses native runtime types and
allows quotes, newlines, comments, or placeholder-shaped content inside a runtime value to alter
the document.

**Decision**: runtime variable resolution traverses the parsed `JsonNode` tree and replaces scalar
values without reinterpreting replacement text as YAML.

Required semantics:

- an exact scalar `${name}` is replaced by `mapper.valueToTree(value)`, preserving numbers,
  booleans, lists, maps, and null policy;
- a placeholder embedded in a larger string performs escaped string interpolation and remains a
  string;
- replacement values are not recursively treated as templates unless an explicit future feature
  defines that behavior;
- missing variables fail with a path-aware diagnostic;
- document defaults are combined with runtime variables, with runtime values winning;
- unresolved structured Plutus data continues through `PlutusDataValue.resolve(...)`, preserving
  its specialized integer/bytes rules;
- `${variables}` inside `_hex` values are decoded and validated after resolution.

This change belongs to QuickTx because core and extension intents share the same TxPlan runtime
variable model.

### 6.2 Preserve the explicit Programmable Token verbs

`transfer`, `mint`, `burn`, `thirdPartyTransfer`, `register`, and `updateRegistry` remain the public
domain API. Do not restore implicit `payToAddress` routing.

Inherited `Tx` methods return `Tx`, so mixing a core method in the middle of a
`ProgrammableTokenTx` chain can lose the specialized fluent return type at compile time.

**Decision for this PR**: do not add a large set of covariant forwarding overrides. Provide examples
that keep programmable-token verbs together or use a local `ProgrammableTokenTx` variable. Track a
future QuickTx-wide self-typed fluent-base improvement if this becomes a frequent usability issue.

### 6.3 Keep deterministic extension ordering simple

The implementation orders extensions by explicit numeric `order()` and then stable extension id.
ADR-CIP113-001 also mentioned registration order.

**Decision**: use numeric order followed by extension id. Do not make behavior depend on map or
registration order. Update ADR-CIP113-001's wording when this ADR is accepted. Reject duplicate
extension ids and conflicting namespace bindings as today.

## 7. Internal Simplification Plan

`Cip113TransactionMaterializer` is package-private, which correctly prevents its implementation
surface from becoming public API. It nevertheless combines copied fluent-routing behavior,
declaration replay, registry resolution, input selection, output construction, script attachment,
and index finalization in one large class.

Do not begin with a broad rewrite. First land the correctness and lifecycle changes with regression
tests. Then split responsibilities without changing public APIs or transaction output:

1. **`Cip113TransactionPlanner`** — aggregates typed operations, resolves policies and registry
   nodes, selects/reserves inputs, and produces an immutable transaction-wide plan.
2. **`Cip113CoreIntentEmitter`** — converts the immutable plan into build-local ordinary QuickTx
   intents and script requirements.
3. **`Cip113IndexFinalizer`** — owns ledger ordering, embedded index resolution, post-balance
   comparison, and final verification.
4. **`Cip113TransactionMaterializer`** — remove after callers and tests use the smaller components,
   or retain temporarily as a narrow coordinator.

As part of this cleanup:

- remove unused inherited fluent-routing overrides and legacy eager/declaration machinery;
- remove duplicate assignments and duplicate/misattached Javadocs;
- keep protocol codecs and ledger-ordering utilities pure;
- preserve datum, reference-script, and asset-name bytes exactly;
- keep registry snapshots and planned collections immutable where practical.

The refactor is complete only when before/after transaction-body and witness-set fixtures agree for
the supported scenarios.

## 8. Test Plan

### 8.1 Unit and component tests

Add tests for:

- multiple third-party transfers for one holder, one policy aggregate successfully;
- multiple policies in one third-party transaction fail before UTxO selection;
- burns for one policy accept semantically equal issuance redeemers;
- burns for one policy reject conflicting issuance redeemers;
- extension preparation does not change authored intent count or TxPlan YAML;
- one plan can be built twice without duplicate generated content;
- a failed build leaves the plan reusable;
- concurrent first resolution publishes one complete state;
- all concurrent callers receive the same resolution failure;
- backend failure cannot silently return a spent bootstrap coordination output;
- unsupported verification-key transfer/third-party credentials fail at registration/update;
- `pt:unfrack` is not advertised or decoded under schema version `1`;
- asset-name null, odd-length, non-hex, prefixed, and over-32-byte values fail early;
- valid empty and 32-byte asset names round-trip canonically;
- metadata values cannot be mutated through returned objects;
- deployment network and bootstrap mismatches fail before backend calls;
- extension ordering is numeric order then id;
- ordinary QuickTx intents and plans remain unaffected.

### 8.2 Variable-resolution tests

Test exact-node and embedded-string variables containing:

- quotes, colons, `#`, braces, and newlines;
- strings that themselves contain `${another_variable}`;
- numbers, booleans, lists, and maps;
- structured Plutus integers and bytes;
- CBOR HEX redeemers and data containing variables;
- missing variables with a useful document path.

Tests must parse unresolved templates directly and exercise each intent's
`resolveVariables(...)`; whole-document raw YAML substitution must not mask missing intent-level
resolution.

### 8.3 DevKit end-to-end tests

Retain the existing direct Java and resource-YAML deployment, registration, named-policy mint,
later mint, transfer, burn, third-party, global-state, ordering, and custom JuLC substandard tests.

Add or strengthen:

- a transfer to a different owner's smart wallet with before/after balance assertions;
- multiple transfer intents aggregated into that transaction;
- a custom datum and structured variable-bearing redeemer on the cross-owner transfer;
- negative build tests for multi-policy third-party and conflicting burn authorization where they
  can be exercised without submission;
- rebuilding the same decoded YAML plan against refreshed chain state when its operation remains
  valid.

DevKit availability may use a JUnit assumption. After availability and setup succeed, protocol,
deployment, derivation, build, submission, and state-verification failures must fail assertions, not
be converted into skipped tests.

The DevKit reset and top-up helper must throw on non-2xx responses. Test diagnostics should use
SLF4J with a compatible test runtime provider; callback output may be captured for assertion rather
than relying on `System.out`.

## 9. Compatibility Manifest

Because the CIP-113 reference contracts are evolving, add a small checked-in compatibility
manifest next to the vendored blueprint containing:

- protocol id (`cip-113`);
- informational reference-contract version;
- source repository and commit;
- blueprint hash;
- expected validator/script hashes used by codec-agreement tests;
- supported programmable-token schema version;
- implemented and deliberately unsupported capabilities.

Runtime compatibility continues to be anchored by the explicit deployment. The manifest is build
and review provenance, not automatic proof that an arbitrary public deployment matches.

A new reference-contract snapshot must update the manifest, regenerate checked-in artifacts through
the isolated Java 25 JuLC fixture when applicable, and pass blueprint agreement plus full DevKit
tests. Do not silently relabel the existing adapter as compatible with a newer contract surface.

## 10. Delivery Plan

### Phase 0 — Correctness guards

1. Reject multi-policy third-party transactions.
2. Aggregate and validate burn issuance redeemers.
3. Reject unsupported verification-key operational credentials before registry mutation.
4. Remove `unfrack` from the schema-v1/public capability surface.
5. Add focused regression tests.

These changes are small, fail closed, and should land before internal restructuring.

### Phase 1 — Reusable build-local intent overlay

1. Add prepared-intent storage to `ExtensionBuildContext`.
2. Add an internal `AbstractTx.complete(preparedIntents)` path.
3. Include prepared intents in all relevant completion calculations.
4. Change `Cip113BuildExtension` to contribute prepared intents instead of mutating `Tx`.
5. Add sequential reuse, failed-build reuse, composition, and ordinary-QuickTx regression tests.

This phase changes the generic SPI implementation and should receive focused QuickTx review.

### Phase 2 — Service and deployment hardening

1. Introduce atomic immutable resolved state.
2. Make caches and registry snapshots concurrency-safe.
3. Remove silent bootstrap fallback.
4. Validate network/bootstrap metadata fully.
5. Add concurrency and backend-failure tests.

### Phase 3 — TxPlan and model hardening

1. Implement structural runtime variable resolution.
2. Make extension metadata immutable and defensively copied.
3. Add asset-name validation and canonicalization.
4. Finalize deterministic extension-order documentation.
5. Add compatibility manifest and adversarial codec tests.

### Phase 4 — Internal simplification

1. Extract planner, emitter, and finalizer components.
2. Remove copied/unused fluent machinery.
3. Verify transaction fixtures and all integration scenarios remain equivalent.

### Phase 5 — Release gate

Run with Java 17:

```text
./gradlew :quicktx:test :programmable-token:test
./gradlew :quicktx:build :programmable-token:build
./gradlew :programmable-token:integrationTest
./gradlew clean build
```

The module is beta-ready only when:

- all Phase 0 through Phase 3 items are complete;
- no authored-plan mutation occurs during build;
- all unit and DevKit tests pass without protocol-related skips;
- public operations match advertised capabilities;
- the supported contract snapshot and limitations are documented;
- Java 17 remains the library build/runtime baseline;
- no QuickTx-to-programmable-token dependency is introduced.

Phase 4 may follow beta only if the materializer remains package-private and no known correctness or
maintainability risk is deferred with it.

## 11. Consequences

### Positive

- Invalid multi-policy and conflicting-authorization transactions fail early and clearly.
- TxPlan and Java-authored semantic transactions remain reusable across sequential builds.
- Application-scoped Programmable Token services can safely serve independent concurrent builds.
- Deployment errors do not degrade into obscure ledger failures.
- Schema-v1 advertises only operations the adapter can execute.
- Metadata and runtime variables become deterministic and safe at the serialization boundary.
- CIP-113-specific complexity remains isolated from QuickTx and the public domain facade.
- The large materializer can be simplified without destabilizing public APIs.

### Costs

- QuickTx needs a small internal completion seam for build-local prepared intents.
- Structural variable resolution changes shared TxPlan behavior and needs broad regression coverage.
- Atomic service initialization and immutable snapshots add implementation work.
- Some currently accepted declarations will fail earlier until key-credential and unfrack support is
  implemented.
- Contract snapshot upgrades require an explicit compatibility review and artifact regeneration.

## 12. Non-Goals

This ADR does not:

- redesign the generic extension SPI or its lifecycle phases;
- add a global extension registry;
- make QuickTx depend on Programmable Token or CIP-113;
- select a future CIP-113 public deployment;
- treat `contract_version` as a compatibility selector;
- implement a hypothetical CIP-256 or future Programmable Token dialect;
- implement multi-policy third-party redeemers without matching on-chain support;
- raise CCL's Java baseline from Java 17;
- stabilize internal CIP-113 planner/materializer classes as public API.

## 13. Review Checklist

- [ ] Confirm the one-policy third-party restriction for the pinned contract.
- [ ] Confirm early rejection versus implementation of verification-key operational credentials.
- [ ] Approve the build-local prepared-intent overlay design.
- [ ] Confirm sequential reuse is guaranteed and concurrent authoring-model reuse is not.
- [ ] Approve atomic immutable deployment resolution.
- [ ] Confirm bootstrap lookup failures must fail closed.
- [ ] Approve removal of `unfrack` from schema version 1.
- [ ] Confirm structural TxPlan variable-resolution semantics.
- [ ] Confirm deployment metadata omission and canonical-emission behavior.
- [ ] Confirm numeric-order-plus-id extension ordering.
- [ ] Approve the compatibility manifest fields.
- [ ] Agree that Phases 0 through 3 are required before beta.

## 14. References

- [ADR-CIP113-001](001-cip113-quicktx-extension-and-txplan-design-review.md)
- [CIP-113 specification](https://cips.cardano.org/cip/CIP-0113)
- [CIP-113 reference implementation](https://github.com/cardano-foundation/cip113-programmable-tokens)
- [CIP-113 contract-surface changes](https://github.com/cardano-foundation/cip113-programmable-tokens/blob/main/CONTRACT_SURFACE_CHANGES.md)

