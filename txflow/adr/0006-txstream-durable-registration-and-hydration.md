# ADR 0006: Durable TxStream Registration, Hydration, and Recovery

**Status**: Proposed

**ADR Document Version**: 1.0.0

**Date**: 2026-08-23

**Last Updated**: 2026-08-23

**Review State**: Initial review draft (review round 1)

**Target Release**: A TxStream preview release after this ADR is accepted

**Modules**: `txflow`, `txflow-extensions:txflow-store-rdbms`, durable-store documentation

**Related ADRs**: [ADR 0003: Relational Durable Store Extension for TxFlow](0003-relational-durable-store-extension.md), [ADR 0004: TxFlowStream v2](0004-txstream-on-flow-engine.md), [ADR 0005: Progressive TxStream API](0005-txstream-progressive-api.md)

**Supersedes**: None. This ADR extracts and expands the durable-correctness workstream first drafted as ADR 0005 Decision 11 and Phases B2–B3.

## ADR Version History

The ADR document version is independent of the library release version.

| ADR version | Date | Author | Review state | Summary |
|-------------|------|--------|--------------|---------|
| 1.0.0 | 2026-08-23 | Bloxbean / CCL maintainers with Codex review | Initial review draft | Defines atomic register/match/conflict semantics, partial-registration safety requirements, a coherent stored-item view, shared hydration and store-only reconciliation, store migration, and an implementation/conformance plan. |

Versioning follows ADR 0005: patch for clarifications, minor for proposed contract or phase changes, and major for incompatible replacement of an accepted decision. Every review round adds a version-history and review-record row.

## Review Record

| Review round | ADR version | Date | Reviewer | Outcome | Resolution summary |
|--------------|-------------|------|----------|---------|--------------------|
| 1 | 1.0.0 | 2026-08-23 | Pending maintainer, store-implementer, and external review | Open | Initial extraction from ADR 0005 following external feedback that durable crash consistency should not block acceptance of the DX API. |

## Executive Summary

A durable stream must answer two questions after its in-memory `ItemState` is gone:

1. is a submission the same registered intent and therefore an attachment, or different content reusing an identity and therefore a conflict?
2. can the stored registration, binding, plan, projection, and engine snapshot be reconstructed into the same honest result as the live path?

The current SPI cannot answer the first atomically: `registerItem(...)` reports only duplicate failure and `getItem(...)` returns a projection rather than the authoritative registration fingerprint. The current runtime cannot answer the second for every path: restart re-attach has reconstruction logic, but eviction attachment and store-only `reconcile(itemId)` do not share it.

This ADR proposes:

- a linearizable `registerOrMatch(...)` result carrying the authoritative stored registration;
- an explicit store capability and preview migration instead of an unsafe lookup-then-register fallback;
- a coherent stored-item view containing the authoritative and denormalized records needed for hydration;
- one internal hydrator used by restart re-attach, post-eviction attachment, store-only status/reconciliation, and partial-registration handling;
- fail-closed rules for incomplete crash states, with the exact pre-binding recovery policy kept as a blocking review question;
- common contract tests for durable in-memory, H2, and PostgreSQL stores.

ADR 0005 may proceed independently. Until this ADR is accepted and implemented, durable user documentation remains explicitly incomplete and must not promise post-eviction attachment or store-only repair.

## Context and Current Failure Modes

### Authority split

The engine execution store owns execution truth. The stream state store owns planning facts that the engine never sees:

- item identity and idempotency claim;
- content fingerprint and accepted lane;
- item-to-execution/step binding;
- portable planned request needed before engine start;
- denormalized item projection and its CAS sequence.

Registration, binding, and planned-request writes are authoritative and fail closed. Projections are repairable views of engine truth, but their sequence ordering and final-state immutability are correctness constraints.

### Failure 1: same-content redelivery after live eviction

The live path compares a redelivery with the retained `ItemState`. After retention evicts that state, the durable registration and projection can still exist. A new submission reaches `registerItem(...)`, receives only “duplicate,” and cannot distinguish:

- identical fingerprint: attach to the authoritative stored item;
- different fingerprint: typed identity conflict;
- incomplete registration from a crash: recover, wait, or classify corruption.

A pre-read followed by registration leaves a race between processes and is not an acceptable durable contract.

### Failure 2: store-only uncertainty cannot reconcile

`getItemStatus(itemId)` can return a stored projection when no live state exists, but `reconcile(itemId)` returns empty because snapshot reconciliation requires a live `ItemState`. A durable `RECOVERY_REQUIRED` row can therefore remain uncertain even after the engine store has a final authoritative snapshot.

### Failure 3: partial registration crash windows

The current write sequence can leave a registration/projection before a binding or persisted plan. Restart logic currently treats an unresolvable accepted row as abandoned and projects `CANCELLED`, but the registration remains authoritative. It is not yet specified whether a later same-content source redelivery may resume it, attach to cancellation, or conflicts. Multi-item window planning makes blind re-planning especially dangerous because grouping is part of execution identity.

This is the hardest decision in this ADR and remains blocking. No implementation may silently choose a behavior per store.

## Goals

- Make same-content durable redelivery attach and different-content reuse conflict after eviction or restart.
- Make the registration decision atomic across threads, processes, and supported databases.
- Reconstruct stored state through one invariant-preserving path.
- Allow store-only `RECOVERY_REQUIRED` items to converge to engine truth.
- Preserve transaction hashes, mapping, projection ordering, and final-state immutability.
- Define partial-write behavior that never blindly starts, replaces, or resubmits uncertain work.
- Give custom stores a source-compatible, fail-fast preview migration path.
- Keep ADR 0005's beginner and non-durable API independent of this workstream.

## Non-Goals

- Changing engine execution-store authority or idempotency semantics.
- Providing distributed UTXO reservation beyond existing ownership and engine fencing.
- Making projection writes authoritative.
- Persisting secret signer material or sensitive bindings.
- Rebuilding or resubmitting a known transaction as a reconciliation action.
- Redesigning windowing, batching, planner identity, or retention policy.
- Moving store packages or removing JavaBean accessors.

## Decision Principles

1. **Linearizable identity:** one stored registration is authoritative for a stream/item identity.
2. **Fail closed:** an incomplete or unreadable authoritative state is never treated as fresh work.
3. **Engine truth wins:** hydration may fast-forward projections from engine snapshots but cannot invent an execution outcome.
4. **No resurrection:** a final stored projection never moves backward or becomes accepted work again.
5. **One reconstruction path:** live eviction, restart, and store-only reads use the same mapping and projection rules.
6. **No secret persistence:** recoverability cannot be purchased by storing runtime secret values.
7. **Observable degradation:** unsupported SPI capabilities and corrupt/partial states fail with typed diagnostics.

## Decision 1: Atomic Registration Outcome

Add a result-returning store operation conceptually shaped as:

```java
TxStreamRegistrationResult registerOrMatch(TxStreamItemRecord candidate);
```

with:

```java
enum Kind {
    REGISTERED,
    MATCHED,
    CONFLICT
}
```

The result always carries the authoritative stored `TxStreamItemRecord`; for `REGISTERED` it may be the normalized record written by the store. The operation is linearizable for the store's stream scope and item id:

- absent identity: atomically persist the candidate and return `REGISTERED`;
- existing identity with the same versioned fingerprint and identity fields: return `MATCHED` without mutation;
- existing identity with different content/claim/lane identity: return `CONFLICT` without mutation;
- concurrent candidates cannot both become authoritative;
- `acceptedAt` is stored metadata, not part of semantic equality;
- a store error throws a typed registration failure and never reports a synthetic outcome.

The runtime maps `MATCHED` only after inspecting the stored-item state under Decisions 2–4. It is not automatically a successful attachment. `CONFLICT` becomes the existing typed content/identity conflict and never exposes a receipt for the candidate.

Database implementations use a unique key plus insert-or-read/compare in one transaction. A select followed by an independent insert is insufficient. Isolation need only guarantee the stated per-identity linearization, not serialize unrelated items.

## Decision 2: Capability-Gated Preview SPI Migration

The existing `void registerItem(...)` cannot implement the new semantics safely because it neither returns the stored fingerprint nor guarantees a coherent duplicate read. It remains deprecated for one preview release for source compatibility, but new runtime code does not fall back to it.

Add a capability such as:

```java
default boolean supportsAtomicRegistrationMatching() {
    return false;
}
```

and a default `registerOrMatch(...)` that throws `TXSTREAM_STORE_ATOMIC_REGISTRATION_UNSUPPORTED`. All shipped stores implement the new method and return `true`. A durable stream fails at `build()` when its store lacks the capability, before it accepts work. Whether non-durable custom stores may use a strictly process-local compatibility adapter is an open API-shape question; durable mode never does.

Migration documentation includes:

- semantic equality fields and fingerprint versioning;
- transaction/isolation examples for relational stores;
- concurrency and failure conformance fixtures;
- the release in which deprecated `registerItem(...)` will be removed.

## Decision 3: Coherent Stored-Item View

Hydration requires more than `getItem(...)`. The store exposes, directly or through a composed internal adapter, one coherent immutable view conceptually containing:

```java
record TxStreamStoredItem(
        TxStreamItemRecord registration,
        TxStreamItemResult projection,
        long projectionSequence,
        TxStreamBinding binding,
        TxStreamPlannedRecord planned) {
}
```

`binding` and `planned` may be absent according to a precisely classified crash state. Registration is required. Projection absence is either a recognized pre-projection crash state or typed corruption; it is never replaced with guessed success.

The store read must be self-consistent enough that immutable registration/binding facts are not paired with an older incompatible planned record. An RDBMS implementation uses one transaction/snapshot or validates record versions. A high-water sequence read remains mandatory so a hydrated authoritative repair writes at a value greater than the durable projection.

Exact public method shape—`loadItem(itemId)`, separate authoritative accessors assembled by a core adapter, or a smaller recovery-specific SPI—is a blocking review question. The conformance behavior, not the record spelling, is normative.

## Decision 4: One Shared Hydrator

Extract one package-private hydration component used by:

- same-content `MATCHED` submission after live-state eviction;
- restart re-attachment;
- store-only `getItemStatus` read-through when engine truth can advance the view;
- store-only `reconcile(itemId)`;
- partial-registration classification/recovery.

The hydrator restores or validates:

- stream id, item id, idempotency claim, fingerprint, and lane;
- execution id, step id, shared-execution membership, and whole-flow/template mapping;
- latest projection, transaction hash, event cursor where available, and projection sequence;
- item promise semantics: an already settled stored result produces a normally completed receipt;
- live claim indexes and lane membership only when the item truly re-enters live scheduling;
- cumulative counters: hydration/attachment does not create a second acceptance;
- final-state immutability and authoritative snapshot fast-forward rules.

Hydration itself performs no blind submission. Re-dispatch of a persisted but engine-absent planned execution remains the explicit ADR 0004 restart protocol and uses its deterministic execution id. Snapshot-based repair uses the same status mapping as live projection, including submitted-but-undecided `RECOVERY_REQUIRED` behavior.

Only newly applied advancing projections emit `onItemUpdated`; repeated reads and reconciliation are idempotent. Attachment callbacks, if any, must be distinct from `onItemAccepted` and cannot inflate accepted statistics.

## Decision 5: Store-Only Reconciliation

For a stored item without live `ItemState`, `reconcile(itemId)` loads/hydrates the stored view and consults the authoritative engine snapshot when an execution binding exists:

- final stored status returns unchanged without network submission;
- final engine/member truth fast-forwards to `CONFIRMED`, `FAILED`, or `CANCELLED` through the normal projection CAS path;
- a submitted but undecided transaction remains `RECOVERY_REQUIRED` with its known hash;
- absent engine truth never becomes failure merely because a lookup is empty;
- snapshot/read/store failures retain the latest honest projection and surface/log a typed diagnostic according to the existing reconcile API contract;
- unknown item remains empty/typed unknown as selected by the public API review.

The repaired projection uses a sequence strictly greater than the loaded durable high-water mark. A stale concurrent writer cannot overwrite it. Repeated reconciliation after repair is a no-op and emits no duplicate listener event.

## Decision 6: Partial Registration Is a First-Class State

The runtime must classify at least these durable shapes:

| Stored shape | Meaning | Allowed direction |
|--------------|---------|-------------------|
| registration + final projection | settled authoritative item | attach/read only; never re-plan |
| registration + binding + planned request | recoverable planned/started item | resolve engine snapshot or deterministic restart protocol |
| registration + non-final projection + binding, missing plan | potentially started but not reconstructable | fail closed/typed corruption unless engine snapshot proves a safe final projection |
| registration + non-final projection, no binding/plan | accepted before planning/bind | apply the reviewed pre-binding recovery policy; never pretend it ran |
| registration without projection | earliest crash window or corruption | apply a store-version-aware reviewed policy; never guess success |

The exact policy for the last two rows is intentionally unresolved in version 1.0.0. Review must choose one consistent strategy, including multi-item windows and source redelivery:

1. persist enough non-secret accepted payload before reporting acceptance so restart can resume deterministically;
2. retain a recoverable pre-binding marker and allow same-fingerprint source redelivery to resume under ownership/fencing, without resurrecting a final projection;
3. atomically tombstone/prune the incomplete registration so a later redelivery is fresh, proving no engine start was possible;
4. reject as typed store corruption requiring operator action.

Continuing today's “project `CANCELLED` but retain an unreplaceable registration” behavior is not accepted unless review demonstrates how documented idempotent redelivery can subsequently run the intent without violating final-state immutability.

## Decision 7: Documentation and Operational Boundaries

Until all blocking questions are resolved and required phases ship:

- ADR 0005 beginner docs use the non-durable in-memory default;
- durable docs state which restart/attachment cases are implemented and which are incomplete;
- `isDurable()` alone must not imply atomic matching capability;
- operators receive codes/actions for unsupported store, conflict, corrupt partial state, and uncertain snapshot;
- no documentation recommends blind resubmission of `RECOVERY_REQUIRED` work.

## Compatibility and Migration

This is a preview SPI change with a source-compatible transition where practical:

- shipped in-memory, durable-in-memory, H2, and PostgreSQL implementations migrate together;
- old custom stores continue to compile through default methods but durable stream construction fails fast until they implement the capability;
- existing persisted rows require no destructive rewrite if current columns already contain registration, binding, plan, projection, and sequence; migrations may add version/state columns or indexes;
- schema migration is forward-only, transactional where supported, and tested against populated pre-change fixtures;
- no stored fingerprint is recomputed with a new algorithm without explicit version handling.

Release notes must distinguish API source compatibility from durable behavioral capability.

## Implementation Plan

### Phase D0: Baseline and crash-window inventory

**Work**

- Pin current live, eviction, restart, abandoned-ghost, and store-only behavior in tests.
- Enumerate every write and crash boundary from registration through engine start.
- Record current schemas, uniqueness constraints, projection sequence behavior, and fingerprint versions.

**Exit criteria**

- Each partial shape in Decision 6 has a reproducible fixture.
- Existing final-state, hash-preservation, and re-attach suites are green.
- No schema/API edit lands before the chosen partial-state policy is documented in a new ADR version.

### Phase D1: Atomic store contract

**Work**

- Finalize result/method naming and equality fields.
- Add capability gating and deprecate `registerItem(...)`.
- Implement atomic outcomes in in-memory, durable-in-memory, H2, and PostgreSQL stores.
- Publish a reusable store conformance fixture.

**Verification**

- absent registration returns `REGISTERED` exactly once;
- N concurrent identical candidates yield one registration and only matches thereafter;
- concurrent different candidates yield one authority and conflicts for losers;
- no conflict mutates stored fingerprint, claim, lane, or accepted time;
- transaction rollback/failure never reports registration success;
- unsupported durable stores fail at build before accepting work.

### Phase D2: Acceptance integration and eviction attachment

**Work**

- Route new submissions through `registerOrMatch(...)`.
- Map conflict to honest rejection and match to stored-state classification.
- Attach settled and safely reconstructable matching items without acceptance counter/listener duplication.
- Keep live-map fast paths while proving semantic equivalence with stored matching.

**Verification**

- same-content after eviction attaches to the original outcome/identity;
- different-content after eviction conflicts;
- corrected rejected work can retry while an accepted identity cannot be replaced;
- matching final items return settled receipts;
- cross-instance races pass against H2 and PostgreSQL.

### Phase D3: Stored-item view and shared hydration

**Work**

- Implement the reviewed coherent-read SPI/adapter.
- Extract hydration from restart-specific logic.
- Route restart, stored match, store-only status, and reconcile through it.
- Preserve projection sequence domination, mapping, hash, and listener rules.

**Verification**

- every engine snapshot state maps identically through live and hydrated paths;
- final projections never regress;
- repaired projection dominates concurrent/stale CAS writes;
- settled hydration completes receipts without incrementing acceptance/failure totals;
- repeated hydrate/get/reconcile calls are idempotent;
- shared-flow member and whole-flow template mappings remain correct.

### Phase D4: Partial-registration policy

**Work**

- Implement the strategy accepted for every Decision 6 shape.
- Add failpoints after each authoritative write and before/after engine start.
- Verify ownership/fencing and deterministic identity for any resumable path.
- Add typed diagnostics and operator actions for non-resumable corruption.

**Verification**

- kill/restart at every failpoint never duplicates an engine execution;
- no incomplete row is silently reported confirmed, failed, or freshly accepted;
- source redelivery behavior is deterministic for per-item, per-window, batching, and template work;
- cleanup/tombstone operations cannot delete a possibly started execution binding;
- no secret value is introduced into durable records.

### Phase D5: RDBMS migration and documentation

**Work**

- Ship schema migrations and populated-database upgrade tests.
- Run the common suite against durable in-memory, H2, and PostgreSQL.
- Document custom-store migration, failure codes, metrics/logs, and recovery actions.
- Remove “incomplete” durable warnings only for guarantees proven by the shipped phases.

**Verification**

- upgrade and rollback policy is documented;
- PostgreSQL concurrent-process tests prove atomic authority;
- restart/kill tests prove hydration and partial-state behavior;
- ADR 0005 and package/reference documentation describe exactly the shipped contract.

## Verification Matrix

| Area | Contract/unit | Integration | Required evidence |
|------|---------------|-------------|-------------------|
| Registration | outcome/equality/concurrency fixture | H2 + PostgreSQL multi-connection | one authoritative registration |
| Eviction | retained-store/live-map tests | RDBMS restart/retention | same attaches, different conflicts |
| Hydration | mapping, receipt, counters, CAS | restart with engine snapshots | live and stored paths converge |
| Store-only repair | every status and failure | H2/PostgreSQL uncertain-to-final | no resubmission; hash retained |
| Partial writes | failpoint matrix | process kill/restart | no duplicate, resurrection, or false terminal result |
| Migration | old/new SPI and schema fixtures | populated database upgrade | fail-fast custom-store path and preserved rows |

All core verification runs on Java 17. Database tests cover the supported H2 and PostgreSQL versions used by CI.

## Alternatives Considered

### Pre-read registration then call the existing method

Rejected. It has a lookup/register race and cannot establish one authority across processes.

### Treat every durable duplicate as conflict

Rejected. It preserves safety but breaks idempotent redelivery after retention/restart and makes durable mode less capable than the live map.

### Treat every durable duplicate as attachment

Rejected. Different content could inherit another intent's execution and result.

### Put registration identity in the engine store

Rejected. Item/planner identity is stream-owned metadata; moving it would couple engine execution to every stream planner and source model.

### Duplicate reconstruction logic in each caller

Rejected. Restart, eviction, and read-through would drift on mapping, sequence, listener, and final-state rules.

### Automatically resubmit uncertain or incomplete work

Rejected. A known or possibly submitted transaction may exist. Reconciliation reads engine truth; only the explicit deterministic restart protocol may start an engine-absent persisted plan.

## Consequences

### Positive

- Durable idempotency works after in-memory retention and restart.
- Store-only uncertainty can converge without recreating the original process state.
- One conformance contract covers core and relational implementations.
- Crash states become named, testable, and operable rather than implicit edge cases.
- ADR 0005 can be reviewed on DX merits without weakening durability.

### Negative and costs

- The preview store SPI gains result/capability/coherent-read surface.
- Custom durable stores must migrate before they can build a durable stream.
- Relational implementations need concurrency, migration, and crash-window tests.
- Hydration centralization touches complex runtime code and requires careful regression coverage.
- The chosen partial-registration policy may require additional persisted state.

## Open Questions

1. **Atomic API shape (blocking):** Replace `registerItem`, supplement it, or add a result-returning overload with a different name?
2. **Semantic equality (blocking):** Is matching defined by versioned fingerprint alone, or must claim key and lane be compared independently for clearer corruption diagnostics?
3. **Coherent read shape (blocking):** One `loadItem` aggregate, separate immutable accessors plus validation, or a recovery cursor/transaction abstraction?
4. **Pre-binding crash recovery (blocking):** Which Decision 6 strategy works for per-item and multi-item planners without resurrection or silent loss?
5. **Missing projection (blocking):** Recoverable earliest crash state, prunable registration, or typed corruption?
6. **Non-durable custom stores (non-blocking):** May a process-local adapter implement register/match with locking, or should every store implement the new SPI?
7. **Unknown store-only item (non-blocking):** Preserve `Optional.empty()` or align with a typed item-unknown result in a later public API review?

## Review Checklist

Reviewers should explicitly confirm or challenge:

- linearizability and equality semantics;
- RDBMS isolation/upsert feasibility;
- whether the coherent view contains enough and only necessary state;
- sequence domination and final-state immutability;
- shared-flow/template hydration correctness;
- listener/counter behavior for attachment and repair;
- every partial-registration strategy under source redelivery and ownership failover;
- custom-store migration and deprecation duration;
- whether any documentation overstates durability before all required phases ship.

## Acceptance Criteria

This ADR is ready to become `Accepted` when:

- all blocking open questions have recorded decisions;
- the selected store API is implementable atomically in memory, H2, and PostgreSQL;
- the partial-state table has one unambiguous action for every row;
- custom-store and schema migration paths are documented;
- ADR 0004 authority/fencing and ADR 0005 uncertainty contracts remain intact.

Implementation is complete when:

- identical post-eviction/restart submissions attach and different content conflicts in all shipped stores;
- concurrency tests establish one authoritative registration;
- restart, attachment, status read, and reconcile use one hydrator;
- store-only `RECOVERY_REQUIRED` converges through engine truth without resubmission;
- hash, mapping, sequence, counters, listeners, and final-state invariants pass the common suite;
- every authoritative-write failpoint has a deterministic, documented recovery result;
- H2/PostgreSQL upgrade and process-kill tests pass;
- durable documentation no longer claims more than the implemented contract.

This ADR makes durable TxStream recovery independently reviewable, testable, and safe while allowing the progressive API work to advance on its own schedule.
