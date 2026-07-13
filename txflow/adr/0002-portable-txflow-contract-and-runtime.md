# ADR 0002: Portable TxFlow Contract, Compilation, Execution, and Recovery

**Status**: Implemented

**ADR Document Version**: 2.6.4

**Date**: 2026-07-10

**Last Updated**: 2026-07-13

**Review State**: Implemented; implementation review and re-review remediated and verified on Java 17 with Yaci DevKit

**Target Release**: To be decided

**Modules**: `txflow`, `quicktx`

**Related ADRs**: [ADR 0001: TxFlow Flow-Level Execution Context in YAML](0001-flow-level-execution-context.md), [QuickTx policy references for minting](../../quicktx/adr/policy-references-for-minting.md), [QuickTx script registry for attachment references](../../quicktx/adr/script-registry-for-attachment-references.md)

**Supersedes**: None

## ADR Version History

The ADR document version is independent from the TxFlow YAML schema version discussed later in this document.

| ADR version | Date | Author | Review state | Summary |
|-------------|------|--------|--------------|---------|
| 0.1.0 | 2026-07-10 | Bloxbean / CCL maintainers | Initial draft | Captures the current TxFlow gaps and proposes a portable definition, compilation, execution, policy, state, and recovery architecture. |
| 0.2.0 | 2026-07-10 | Bloxbean / CCL maintainers | Architecture review update | Adds the focused rollback/retry implementation audit, normative rollback semantics, proposed rollback policy APIs, reconciliation algorithm, compatibility mapping, implementation workstream, and strict Java 17 verification matrix. |
| 2.0.0 | 2026-07-12 | Independent design review (maintainer-commissioned) | Revised proposal | Independently verifies every 0.2.0 evidence claim against source (all confirmed; several corrected or found worse than stated). Adds five newly found defects. Adds Decisions 18–21: concurrent-execution UTXO contention, transaction validity-interval policy, `FlowExecutor` internal decomposition, and a deterministic clock/chain test seam. Renames the portable schema namespace to `bloxbean.com/txflow/v1alpha1` (form later revised at 2.1.0). Resolves 15 of 20 open questions. Replaces the interleaved eight-phase plan with three independently mergeable tracks (A: correctness hardening, B: portable contract, C: durable runtime). |
| 2.1.0 | 2026-07-12 | Independent design review (maintainer-commissioned) | Second review round | Incorporates the Codex review of 2.0.0: same-definition concurrency deferred to Track C1 (duplicate guard retained through Track A; no public API changes in Track A); rollback-from-absence requires chain-point-aware authoritative `ABSENT` observations — accumulated ambiguous empties never count, and exhausted uncertain reconciliation yields `RECOVERY_REQUIRED`, not rebuild; uncertain-submission handling defined for absent/unknown hash lookups (identical-bytes resubmission or typed failure, never rebuild); idempotency scope extended to a canonical execution-request fingerprint with a typed conflict; Decision 18 gains canonical resource identity, coverage rules, deterministic lock ordering, and lease renewal/expiry/fencing; Decision 19 softened to record-when-present plus policy-required option, nullable slots, and preflight-time absolute-slot validation (network defaults stay open); schema identifier changed to group/version form `txflow.bloxbean.com/v1alpha1` (host later changed at 2.5.0); `FlowWriteOptions` (format + schema version) replaces the format-only writer; sealed-interface rationale corrected; enum/package-move forwarding limits documented; NEW-02 reworded (definition mutation; the stale-value path requires shared plans or future bindings); cross-track dependencies documented and durations marked illustrative; versioning-rule text restored, with the 2.0.0 renumbering recorded as a maintainer decision. |
| 2.2.0 | 2026-07-12 | Independent design review (maintainer-commissioned) | Third review round | Incorporates the Codex review of 2.1.0. Absence authority becomes an adapter/configuration declaration: a current tip does not prove transaction-index consistency, so empty lookups are `UNKNOWN` unless authority is declared (one additive `ConfirmationConfig` flag is Track A's only public API change; auto-rebuild from undeclared absence becomes breaking-by-honesty change #6). `FlowExecutionStore` gains atomic idempotency claims (`createOrGet`) and token-based lease fencing (revision CAS alone cannot fence a stale owner); spending-resource leases become a store primitive. Signed payloads are modeled as inline-or-external (`SignedPayload`) with defensive copies. Journal compaction gains a `compactedThroughSequence` watermark with typed reconnect behavior. Policy capping is split: numeric ceilings warn-and-continue, semantic replacements (mode, rollback action, horizon) require rejection or explicit acknowledgement. Consistency fixes: A4.1 no longer claims results expose the execution ID in Track A; B2 and the resolution table align with the 2.1.0 idempotency semantics; the package tree keeps `TxFlow`/`FlowStep` in their existing package; GAP-09 wording aligned with the reworded NEW-02. |
| 2.3.0 | 2026-07-12 | Independent design review (maintainer-commissioned) | Fourth review round | Incorporates the Codex review of 2.2.0. Absence authority moves from `ConfirmationConfig` to a backend-adapter capability (`TransactionObservationCapabilities` SPI plus a server-side wrapper) — flow configuration, execution settings, and YAML can never declare a backend's transaction index authoritative. `FlowExecutionStore` completes its own semantics: execution/resource lease renewal and release, a sequence-cursor `readEvents` API returning typed `EVENTS_COMPACTED`, resource-lease epochs validated on writes, and `compactedThroughSequence` on the snapshot record. Decision 18 acknowledges that fencing protects state writes but cannot stop a partitioned stale worker from submitting to Cardano — race-free cross-process spending needs the deferred reservation/coordinator ADR. `InlineCbor` shows its defensive-copy constructor/accessor; `SignedPayloadResolver` added with mandatory hash verification during recovery; the leftover 2.0.0 idempotency-collision row and the Decision 7 stale-variable wording aligned; metadata formatting fixed. |
| 2.4.0 | 2026-07-12 | Independent design review (maintainer-commissioned) | Fifth review round | Final API corrections from the Codex review of 2.3.0, after which the reviewer considers the ADR ready for maintainer acceptance. `InlineCbor` compact constructor made `public` (a nested record in an interface is implicitly public; Java 17 rejects a stricter canonical constructor) with null check. Payload verification moved into CCL: the engine — never the resolver implementation — verifies the recorded `sha256` and recomputes the Cardano transaction hash against the recorded `transactionHash` before resubmission, for external and inline payloads alike. `append` takes a composite `MutationFence` (execution-lease fence plus optional `ResourceLeaseFence`) so both epochs are actually validatable, matching the 2.3.0 text. Governance note generalized to cover the 2.1.x–2.4.x review-round revisions. |
| 2.5.0 | 2026-07-12 | Bloxbean / CCL maintainers | Maintainer revision | Schema group host changed from `txflow.bloxbean.com` to `txflow.cardano-client.dev`: the identifier is now product-scoped (cardano-client-lib) rather than organization-scoped, keeping the schema identity stable independent of organizational branding. The group remains a purely symbolic DNS name under the maintainer's control; nothing is fetched from it. |
| 2.6.0 | 2026-07-13 | Bloxbean / CCL maintainers | Accepted | Clarifies that a portable TxFlow step embeds exactly one transaction from the shared QuickTx/TxPlan transaction contract rather than defining a second transaction language. Pins schema ownership, version compatibility, flow-specific input references, and defensive compilation rules. The maintainer accepts the ADR and authorizes implementation in Track A → B → C order. |
| 2.6.1 | 2026-07-13 | Bloxbean / CCL maintainers | Implemented | Records completion of Tracks A, B, and C: deterministic correctness hardening, the portable QuickTx-owned contract/compiler/policy surface, and the executor-neutral durable runtime/recovery primitives. Verification used Java 17 and the external Yaci DevKit, including the complete TxFlow unit suites, strict integration suite, and focused portable same-hash recovery coverage. |
| 2.6.2 | 2026-07-13 | Bloxbean / CCL maintainers | Corrective implementation review | Reopens implementation after an adversarial review found that the green test matrix did not cover several accepted invariants, including sequential non-confirmed fall-through, depth-safe prefix reconciliation, recovery-required mapping, lease-renewal ordering, and the Decision 20 decomposition. Records that the ADR remains accepted but is not considered implemented until the reviewed gaps are corrected and the full Java 17 unit and Yaci matrices pass again. |
| 2.6.3 | 2026-07-13 | Bloxbean / CCL maintainers | Implemented after corrective review | Records independent classification and remediation of the implementation-review findings, including strict non-permissive rollback tests and the additional NOTIFY_ONLY tracking correction. Final Java 17 verification passes QuickTx 350/350, TxFlow 474 passing plus one Java 21-only skip, and the complete 63/63 external-Yaci integration matrix; the strict rollback class passes 11/11 and the bounded scalability scenario completes 100/100 flows (200 transactions). Remaining items are non-blocking performance/style follow-ups: per-depth durable appends, safe per-attempt TxPlan reparsing, and low-severity boilerplate cleanup. |
| 2.6.4 | 2026-07-13 | Bloxbean / CCL maintainers | Implemented after re-review | Fixes the two re-review regressions introduced by fresh/resume consolidation: resume no longer replaces the legacy state snapshot or emits a duplicate flow-start callback, while all fresh paths retain their start behavior. Adds strict external-Yaci coverage for the default non-authoritative backend adapter: ambiguous post-inclusion absence emits a suspected-rollback event, ends in a bounded typed reconciliation-uncertain outcome, and never rebuilds. Clarifies conservative unknown-failure retry compatibility and locks negative portable durations at their owning-policy boundaries. Final Java 17 verification passes QuickTx 350/350, TxFlow 480 passing plus one Java 21-only skip, and 64/64 external-Yaci tests; the strict rollback class now passes 12/12. |

### Versioning Rules For This ADR

- Increment the patch version for clarifications, corrections, examples, and editorial changes that do not change a proposed decision.
- Increment the minor version when a proposed API, schema element, compatibility rule, or implementation phase changes while the ADR remains `Proposed`.
- Increment the major version when an accepted decision is replaced with an incompatible decision.
- Add a row to the version history for every version change. Do not replace earlier history entries.
- Record unresolved reviewer disagreements in the Open Questions section until the review reaches a decision.
- Suggested status progression is `Proposed` -> `Accepted` -> `Implementing` -> `Implemented`. Use `Rejected` or `Superseded` where appropriate.

**Governance note (2.1.0)**: under the rules above, the 0.2.0 → 2.0.0 change would ordinarily have been 0.3.0, since the ADR is still `Proposed` and no accepted decision was replaced. The renumbering to 2.0.0 was an explicit maintainer decision to designate the revised proposal as the second major draft of this document; it is recorded here as a rule exception rather than justified by rewording the rules. Versions after 2.0.0 follow the original rules — hence the 2.1.x–2.5.x minor revisions for the subsequent review rounds and maintainer adjustments. Decisions marked "Resolved" in this document are review-proposed resolutions approved by the project maintainer during the 2.0.0/2.1.0 review rounds; they remain revisable while the ADR is `Proposed`.

## Terminology Used In This Document

- **Legacy format**: the current YAML shape (`version: "1.0"`, top-level `context:` and `flow:` keys). It was never declared stable and is treated as a preview format.
- **Portable format**: the new versioned document contract introduced by this ADR, identified by `api_version: txflow.cardano-client.dev/v1alpha1`. It is named `v1alpha1`, not `v2alpha1`, because the legacy format was never a published stable contract; the portable format is the first version intended as one.
- **Definition**: a reusable, immutable TxFlow document or Java model.
- **Execution**: one run of a definition, with its own identity, bindings, effective settings, and state.
- **Attempt**: one build/sign/submit lifecycle of one step's transaction within an execution.

## Executive Summary

TxFlow already provides a useful Cardano-specific runtime for ordered multi-transaction workflows. It supports transaction chaining, three execution modes, confirmation tracking, rollback handling, retry policies, listeners, asynchronous handles, a registry, state-store abstractions, Java-first transaction factories, and YAML-backed `TxPlan` steps.

ADR 0001 adds flow-level execution settings to YAML and correctly introduces per-execution effective settings. That work should be retained. The next architectural step is to make TxFlow a stable contract that can be authored outside Java, sent to a server, validated without side effects, constrained by server policy, executed repeatedly, observed through portable events, and safely recovered after process failure.

Version 2.0.0 of this ADR is based on an independent line-by-line verification of every evidence claim in version 0.2.0 (see "Verification Status"). All claims were confirmed; several were found to be worse in the code than originally stated, and five additional defects were found. The current design is not yet sufficient for the target use case because:

- `depends_on` makes previous outputs available to coin selection but does not guarantee their consumption;
- several Java models serialize to YAML with silent semantic loss — including a FILTER dependency that silently degrades to match-all on read-back;
- variables are substituted into raw YAML text rather than bound as typed values, and execution permanently mutates the shared `TxPlan` by writing variable values into it;
- flow-definition identity and execution identity are conflated (no execution-ID concept exists anywhere in the module);
- parsing, validation, binding, capability resolution, and execution are not separate stages;
- portable result, event, error, and lifecycle models are incomplete;
- persistence captures some transitions but does not provide a complete recovery protocol, and every persistence failure is silently swallowed;
- retries classify failures using message text, retry unknown exceptions by default, and can crash with a negative computed backoff delay;
- rollback under `FAIL_IMMEDIATELY` or exhausted `NOTIFY_ONLY` is reported as a confirmation timeout and leaves the transaction stranded at `SUBMITTED` in the state store;
- YAML-requested behavior is not evaluated through a server policy abstraction;
- QuickTx script references cannot currently be supplied through `FlowExecutor` even though QuickTx has a `ScriptRegistry` integration point;
- concurrent executions spending from the same logical account have no UTXO-contention protection;
- `FlowExecutor` is a 2,986-line class with six execution-path variants, and no decomposition plan existed for building the new runtime on top of it.

This ADR proposes the following direction:

1. Keep TxFlow focused on deterministic transaction orchestration rather than becoming a general workflow language.
2. Define a versioned, portable TxFlow document envelope (`v1alpha1`) with a published JSON Schema.
3. Separate a reusable `TxFlow` definition from a `FlowExecutionRequest` and unique execution identity.
4. Introduce explicit parse, bind, compile, policy, and execute stages.
5. Separate scheduling dependencies from explicit references to previous transaction outputs.
6. Require lossless serialization and fail on non-portable Java constructs.
7. Make runtime inputs typed and bind them at the parsed-node/model level.
8. Introduce unified resource resolution and preflight capability validation.
9. Introduce portable execution states, results, events, and structured errors.
10. Define rollback as a persisted reconciliation process that monitors all relevant attempts, preserves still-valid work, and never blindly resubmits an uncertain transaction.
11. Define a versioned, optimistic-concurrency state-store protocol and a recovery/reconciliation API.
12. Make the execution engine immutable after construction and all execution state run-scoped.
13. Preserve the existing APIs during a migration window through compatibility adapters and deprecation rather than an immediate breaking removal.
14. Serialize concurrent executions that draw on the same logical spending resource by default (Decision 18).
15. Record transaction validity intervals per attempt — and let policy require them — so identical-CBOR resubmission has a defined safety window (Decision 19).
16. Decompose `FlowExecutor` internals behind the existing facade before building the durable runtime on them (Decision 20).
17. Ship a deterministic clock/scheduler/chain seam as a first-class primitive so every time-dependent behavior is testable without races (Decision 21).

Delivery is re-cut into three separately mergeable tracks, ordered by dependency (see the Implementation Plan for the explicit cross-track dependencies):

- **Track A — Correctness and safety hardening** of the existing API. No new public types. Fixes every defect that is exploitable by current users today.
- **Track B — Portable contract**: codec, envelope, typed parameters, explicit data flow, compiler, resources, policy. Additive.
- **Track C — Durable runtime**: engine, portable lifecycle, durable store, recovery, flow-scoped rollback reconciliation.

## Context

The intended deployment model is broader than an in-process Java application:

1. A Java or non-Java developer authors a TxFlow document.
2. The document is sent to an application server.
3. The server parses and validates it without submitting a transaction.
4. Runtime values are supplied separately from the reusable definition.
5. Logical references such as `account://treasury`, `policy://rewards`, and `script://vesting` are resolved by server-controlled registries.
6. Server policy constrains what the flow is allowed to do.
7. The flow is compiled into an immutable execution plan.
8. The server starts an execution with a unique execution ID and optional idempotency key.
9. Execution state and transaction attempts are persisted.
10. A caller can observe structured events and retrieve a portable result.
11. If the process stops, another process can reconcile and resume the execution without blindly duplicating transactions.

The server implementation, transport protocol, authentication, database technology, and user interface are outside the scope of CCL. However, CCL should provide the model, codec, compiler, policy, engine, lifecycle, event, persistence, and recovery primitives required to implement such a server consistently.

## Goals

- Make TxFlow YAML understandable and usable without Java knowledge.
- Ensure that accepted YAML has the same semantics as the corresponding Java model.
- Detect errors before transaction submission whenever possible.
- Prevent untrusted YAML from controlling server resources without limits.
- Support reusable flow definitions with distinct concurrent executions — including a defined answer for UTXO contention between them.
- Make dependency and prior-output semantics explicit.
- Support signer, policy, and script references without embedding secrets or script material in YAML.
- Provide deterministic diagnostics with stable codes and document paths.
- Provide portable result and event models suitable for JSON or YAML transport.
- Support crash recovery and transaction reconciliation through CCL-defined primitives.
- Make every time-dependent behavior deterministically testable.
- Preserve existing preview users through a documented compatibility and migration path.
- Use Java 17 as the required Java runtime.

## Non-Goals

- Implement an HTTP server, REST API, message queue consumer, database, or distributed scheduler in CCL.
- Turn TxFlow into a general-purpose workflow language with loops, arbitrary scripts, or unrestricted expressions.
- Guarantee atomic execution of multiple Cardano transactions. Partial on-chain success is an inherent possibility and must be represented explicitly.
- Store private keys, secrets, or credentials inside a TxFlow document or execution snapshot.
- Hide Cardano transaction concepts from authors. The portable model should simplify orchestration without pretending that transaction inputs, outputs, confirmation, rollback, and signing do not exist.
- Remove the current TxFlow APIs in the same release that introduces the new APIs.
- Implement a full distributed UTXO reservation service. Decision 18 defines the minimum viable in-process contention answer and names the full protocol as future work.

## Design Principles

1. **Portable first**: every construct in the portable definition has a stable serialized representation.
2. **No silent semantic loss**: unsupported serialization and compilation fail with actionable diagnostics.
3. **Definition is not execution**: a reusable definition is immutable; each execution has its own identity, bindings, policy result, and state.
4. **Explicit data flow**: ordering and use of prior outputs are modeled separately.
5. **Compile before side effects**: parsing, structural validation, binding, reference preflight, and policy evaluation occur before submission.
6. **Server policy is authoritative**: YAML execution settings are requests and defaults, not permission.
7. **Reconcile before retry**: uncertain transaction submission is resolved using a known transaction hash before rebuilding or resubmitting.
8. **State transitions are first-class**: results and persistence distinguish built, signed, submitted, in-block, confirmed, failed, rolled-back, and cancelled states.
9. **Compatibility is explicit**: legacy behavior is parsed through a compatibility layer and produces warnings where semantics cannot be guaranteed.
10. **Java 17 baseline**: public implementations and tests target Java 17.
11. **Determinism first**: no production code path calls `Thread.sleep`, `Instant.now`, or polls a backend without going through an injectable clock/scheduler/observation seam, so every rollback, retry, and confirmation behavior has a scripted deterministic test with exactly one required outcome.
12. **One spender, one lane**: concurrent executions that draw on the same logical spending resource are serialized by default; opting into concurrent spending is an explicit, policy-visible choice.

## Current Architecture

The current public entry points are centered on these types:

```java
TxFlow flow = TxFlow.builder("fund-and-forward")
        .addVariable("amount", 5_000_000L)
        .addStep(step)
        .build();

String yaml = flow.toYaml();
TxFlow parsed = TxFlow.fromYaml(yaml);

FlowExecutor executor = FlowExecutor.create(backendService)
        .withSignerRegistry(signerRegistry)
        .withChainingMode(ChainingMode.SEQUENTIAL)
        .withConfirmationConfig(ConfirmationConfig.defaults());

FlowResult result = executor.executeSync(parsed);
FlowHandle handle = executor.execute(parsed);
```

The corresponding current YAML shape is:

```yaml
version: "1.0"
context:
  chaining_mode: SEQUENTIAL
  confirmation: defaults
flow:
  id: fund-and-forward
  variables:
    amount: 5000000
  steps:
    - step:
        id: fund
        tx:
          from_ref: account://treasury
          intents:
            - type: payment
              address: ${staging_address}
              amounts:
                - unit: lovelace
                  quantity: ${amount}
        context:
          signers:
            - ref: account://treasury
              scope: payment
```

ADR 0001 correctly adds `FlowExecutionSettings` and computes effective settings per execution. The implementation also adds strict parsing for several execution fields. This ADR extends that work rather than replacing it.

## Verification Status (ADR 2.0.0)

Every evidence claim in ADR 0.2.0 was independently re-verified against the source on branch `feat/txflow_refinement_iter1` (2026-07-12) by four parallel audit passes covering (a) executor/identity, (b) YAML/serialization, (c) rollback/retry, and (d) store/docs/QuickTx integration. Results:

- **All gap claims (GAP-01 … GAP-17) and rollback findings (RB-01 … RB-10) are confirmed in substance.** No claim was fabricated or materially wrong in direction.
- **Corrections** (claims that were imprecise): GAP-01, GAP-15, RB-07, RB-08, RB-09. The corrected wording is incorporated in the Gap Summary and findings tables below.
- **Newly found defects** (worse than 0.2.0 stated): NEW-01 … NEW-05 below.

### Newly Found Defects

| ID | Severity | Defect | Evidence |
|----|----------|--------|----------|
| NEW-01 | Critical | A `FILTER` dependency round-trips through YAML to **match-all**: the `Predicate<Utxo>` is dropped on write, and on read-back `applyFilter` runs with a null predicate that matches every UTXO — silent semantic corruption, not merely an unused field | `FlowDocument.fromFlow` writes `strategy: filter` without the predicate; `StepDependency.applyFilter` (StepDependency.java:86-102) matches all when predicate is null |
| NEW-02 | High | **Definition mutation during execution** (reworded 2.1.0): all three chaining modes write flow variables directly into the shared `TxPlan` (`plan.addVariable`, guarded by `containsKey`), permanently mutating the definition. Through the current public API a given `TxFlow` instance always carries the same variables, so the stale-value hazard is reproducible when a `TxPlan`/`FlowStep` is shared across flows with different variables, under concurrent execution over the mutable nested plan, and for any future per-execution bindings API; the per-run-copy fix stands regardless | FlowExecutor.java:1638 (sequential), :1736 (pipelined), :2178 (batch) |
| NEW-03 | High | Rolled-back transactions are **stranded at `SUBMITTED`** in the state store under `FAIL_IMMEDIATELY` and exhausted `NOTIFY_ONLY`: `persistTransactionRolledBack` is reachable only via the `RollbackException` path, which these strategies never take | FlowExecutor.java:2324 reachable only from `onRollbackDetected` in `catch (RollbackException)` blocks |
| NEW-04 | High | Exponential backoff **crashes instead of failing typed**: `1L << (attempt-1)` wraps, `Math.min` does not saturate negatives, and `Thread.sleep(negative)` throws `IllegalArgumentException` out of the retry loop for large `maxAttempts`/`initialDelay` configurations | RetryPolicy.java:133, :139 |
| NEW-05 | Medium | `FlowUtxoSupplier.findPendingUtxo` swallows resolution failures with an **empty catch block** (no logging), in addition to bypassing the declared selection strategy | FlowUtxoSupplier.java:178-180 |

## Gap Summary

The Verification column records the 2.0.0 audit outcome: ✓ confirmed as stated, ✓± confirmed with correction (see Evidence notes), + confirmed and found worse than stated. GAP-18 … GAP-21 are new in 2.0.0.

| ID | Priority | Verification | Gap | Existing consequence | Target outcome |
|----|----------|--------------|-----|----------------------|----------------|
| GAP-01 | Critical | ✓± | Dependency availability is mistaken for dependency consumption | A step may declare `depends_on` but consume unrelated base UTXOs | Separate ordering from explicit previous-output references |
| GAP-02 | Critical | + | YAML serialization can silently lose transaction semantics | Java factories, filters, and multi-transaction plans do not round-trip; FILTER degrades to match-all (NEW-01) | Serialization is lossless or fails |
| GAP-03 | Critical | ✓ | YAML is not a fully versioned public contract | `validateVersion` has zero callers (dead code); no kind discriminator, schema, duplicate-key rejection, or parser resource limits | Versioned envelope, format detection, JSON Schema, conformance fixtures |
| GAP-04 | Critical | ✓ | Raw text variable substitution | Types and document structure can change during substitution; values can inject YAML | Typed parameters and model/node-level binding |
| GAP-05 | Critical | ✓ | Definition ID is also used as run ID | No execution-ID concept exists anywhere; the state store overwrites the previous run's state | Separate definition and execution identities |
| GAP-06 | Critical | ✓ | Result state is too coarse | Built/submitted steps can appear completed before confirmation | Portable step-attempt lifecycle and structured results |
| GAP-07 | Critical | ✓ | Persistence is not a complete recovery protocol | `resumeTracking` in docs does not exist; the existing `resume(flow, prevResult)` is disconnected from `FlowStateStore`; snapshots lack fingerprint/execution ID/attempts/CBOR/inputs; all persistence failures swallowed | Versioned snapshots/journal, reconciliation, and resume APIs |
| GAP-08 | High | ✓ | Resource resolution is incomplete | The 3-arg `compose(plan, signers, scripts)` exists in QuickTx but `FlowExecutor` calls the 2-arg overload at all three sites | Unified resource catalog/resolver and preflight |
| GAP-09 | High | + | Executor and `TxPlan` contain shared mutable state | TxPlan mutation in three paths permanently mutates the shared definition (NEW-02); `signerRegistry`, `listener`, `txInspector`, `flowStateStore`, `flowRegistry` are read live mid-flight (settings are snapshotted per ADR 0001) | Immutable engine and compiled plan; run-scoped state |
| GAP-10 | High | ✓ | Retry classification is message-based | Permanent failures may retry and uncertain submissions may rebuild; no typed error model exists anywhere in the module | Typed failure categories and reconciliation-aware retry |
| GAP-11 | High | ✓ | YAML execution settings are not constrained through policy | A submitted document can request excessive retries, waits, or unsafe modes | Authoritative `FlowExecutionPolicy` with effective-settings output |
| GAP-12 | High | ✓ | Validation is graph-focused and late | `TxFlow.validate()` Javadoc claims transaction-definition validation it does not perform | Multi-stage compiler with structured diagnostics |
| GAP-13 | Medium | ✓ | `context` is overloaded at two scopes | The same YAML key binds `ExecutionContext` at flow scope and `TxContext` at step scope | Canonical `execution` and `transaction.context` scopes |
| GAP-14 | Medium | ✓ | Documentation describes APIs that do not exist | Six phantom APIs (`resumeTracking`, `withVersion`, `withConfirmationTimeout`, `withCheckInterval`, `dependsOnChange`, `SelectionStrategy.CHANGE`) | Generated/reference documentation and compile-tested examples |
| GAP-15 | Medium | ✓± | Registry and cancellation are process-local and incomplete | `cancel()` cancels the result future, not the task; cooperative cancellation IS honored at step boundaries but never mid-step, and sleeps wake only on real interrupt | Execution-aware cancellation token and portable terminal events |
| GAP-16 | Medium | ✓ | Current model resembles a DAG but executes an ordered list | Insertion order, DFS for cycle detection only, no topological sort, zero parallelism | Explicitly define an ordered transaction graph for the first portable version |
| GAP-17 | Critical | ✓ | Rollback monitoring and rebuild semantics are incomplete | Detection stops outside the active wait, sequential restart repeats confirmed business actions, rollback surfaces as timeout, shallow in-block transactions are skipped as confirmed | Flow-scoped monitoring, typed rollback outcomes, persisted reconciliation, invalidated-closure rebuild, and strict cross-mode tests |
| GAP-18 | Critical | new | Concurrent executions have no UTXO-contention protection | Two concurrent executions spending from the same `account://treasury` select overlapping UTXOs and race to double-spend; one fails non-deterministically after submission | Per-resource execution serialization by default (Decision 18) |
| GAP-19 | High | new | Transaction validity intervals are not managed per attempt | "Resubmit identical signed CBOR" (rollback recovery) has no defined safety window; an expired transaction cannot be distinguished from a still-valid one without re-deriving TTL | Deliberate validity-interval policy recorded per attempt (Decision 19) |
| GAP-20 | High | new | `FlowExecutor` is a 2,986-line class with six execution-path variants | Every rollback/recovery improvement multiplies across `doExecute{Sequential,Pipelined,Batch}` × fresh/resume; the durable runtime cannot be built on it safely | Internal decomposition behind the existing facade (Decision 20) |
| GAP-21 | High | new | Time and chain observation are hard-wired (`Thread.sleep`, direct backend polls) | Rollback/retry tests are race-dependent; some integration scenarios accept both success and failure as passing | Injectable clock/scheduler/observation seam plus scripted fake chain (Decision 21) |

### Evidence In The Current Implementation

Line references are against branch `feat/txflow_refinement_iter1` at verification time.

- GAP-01: [`FlowUtxoSupplier.resolvePendingUtxosForAddress`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowUtxoSupplier.java#L120) merges dependency outputs with base UTXOs and logs-and-continues when a required dependency cannot be resolved (never throws). **Correction to 0.2.0**: this address path DOES apply the declared selection strategy via `StepDependency.resolveUtxos`; it is [`findPendingUtxo`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowUtxoSupplier.java#L168) (the txHash path) that bypasses strategy/index/filter entirely and swallows exceptions in an empty catch (NEW-05). The two paths are mutually inconsistent, and neither guarantees consumption.
- GAP-02: [`FlowDocument.fromFlow`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L274) omits Java transaction factories at `log.debug` level (effectively silent) while still emitting the step shell; [`convertTxPlanToStepContent`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L385) reads only `transaction.get(0)` of a multi-transaction plan; `DependencyEntry.filter` is declared but never converted in either direction (and the predicate loss produces NEW-01); YAML without `tx` produces an empty but non-null `TxPlan`. Additionally, a parsed templated flow re-serializes with resolved literals, so a second round trip is not idempotent.
- GAP-03: [`FlowDocument.fromYaml`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L768) never calls [`validateVersion`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L793); a repo-wide search finds zero callers. The mapper is a plain `ObjectMapper(new YAMLFactory())` with output-formatting options only — no `STRICT_DUPLICATE_DETECTION`, no `LoaderOptions` limits.
- GAP-04: [`VariableResolver.resolve`](../../quicktx/src/main/java/com/bloxbean/cardano/client/quicktx/serialization/VariableResolver.java#L30) performs regular-expression replacement on the full YAML string before deserialization. A node-aware resolver exists only for PlutusData.
- GAP-05: [`FlowExecutor.executeSync`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L659) keys active execution protection by `flow.getId()` (line 669; async path line 608); the registry (line 617) and every persistence helper use the same value. `InMemoryFlowStateStore.saveFlowState` overwrites the prior run's entry. A search for any execution/run identifier concept returns nothing. Nuance: the in-memory `FlowExecutionContext` itself is created per run and is thread-safe — the collision is specifically at the guard, registry, and store layers.
- GAP-06: [`FlowStepResult`](../src/main/java/com/bloxbean/cardano/client/txflow/result/FlowStepResult.java#L19) represents success using a boolean and `FlowStatus.COMPLETED`, while pipelined and batch execution create successful step results before deep confirmation (see RB-05).
- GAP-07: [`FlowStateStore`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/store/FlowStateStore.java#L27) describes recovery-oriented storage, while the recovery example in [`DESIGN_AND_USAGE.md`](../docs/DESIGN_AND_USAGE.md#L697) calls a non-existent `resumeTracking` method. `FlowExecutor.resume(TxFlow, FlowResult)` (line 594; sync variant line 549) does exist but consumes a prior in-memory `FlowResult`, not a snapshot — the persistence store and the resume path are disconnected. `FlowStateSnapshot`/`StepStateSnapshot` lack definition fingerprint, execution ID, attempt history, signed CBOR, and spent-input/produced-output data. All five persistence helpers (`FlowExecutor.java:2246-2349`) catch, warn-log, and swallow exceptions; `IN_BLOCK` and depth progression are never persisted.
- GAP-08: QuickTx provides [`compose(TxPlan, SignerRegistry, ScriptRegistry)`](../../quicktx/src/main/java/com/bloxbean/cardano/client/quicktx/QuickTxBuilder.java#L258), but `FlowExecutor` calls the signer-only overload at all three composition sites (lines 1645, 1743, 2184). Script refs in flow steps therefore throw at build time (`"script_ref/script_hash set but no ScriptRegistry or ScriptSupplier configured"`).
- GAP-09: [`FlowExecutor.executeStepSequential`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L1621) adds missing flow variables directly to the step's mutable `TxPlan` — and the same mutation exists in the pipelined (line 1736) and batch (line 2178) paths (NEW-02). The fluent `with*` setters mutate `volatile` shared fields; non-snapshotted fields (`signerRegistry`, `listener`, `txInspector`, `flowStateStore`, `flowRegistry`) are read live by in-flight executions.
- GAP-10: [`RetryPolicy.isRetryable`](../src/main/java/com/bloxbean/cardano/client/txflow/RetryPolicy.java#L162) checks error-message substrings and retries unknown exceptions by default (`return true` fall-through). See RB-08 for the `Error`-guard ordering correction.
- GAP-12: [`TxFlow.validate`](../src/main/java/com/bloxbean/cardano/client/txflow/TxFlow.java#L101) validates duplicate IDs, dangling references, cycles, and ordering — nothing else; its Javadoc claims "each step has valid transaction definition".
- GAP-14: all six phantom APIs confirmed absent. ADR 0001 already directs that three of them (`withVersion`, `withConfirmationTimeout`, `withCheckInterval`) must not be added merely to satisfy stale documentation.
- GAP-15: [`FlowHandle.cancel`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowHandle.java#L192) cancels the manually created result future, which never interrupts the running task (submitted via `Executor.execute`, no task future retained). **Correction to 0.2.0**: a cooperative path exists — `hooks.isCancelled()` is checked at six step-boundary points and in the retry wait, so an async flow stops at the next boundary; it is mid-step interruption that is missing.
- GAP-16: steps execute in insertion order via `for (int i = 0; i < totalSteps; i++)` in all six execution paths; the dependency graph is used only for cycle detection; no parallel scheduling exists.
- GAP-17: [`ConfirmationTracker.waitForConfirmation`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/ConfirmationTracker.java#L212) monitors one transaction only until that blocking call returns, and `stopTracking(hash)` removes confirmed steps from detection entirely. [`FlowExecutor.doExecuteSequential`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L704) starts again at step zero after `REBUILD_ENTIRE_FLOW` (`findStillConfirmedSteps` is called only from the pipelined/batch/resume paths — never sequential). [`findStillConfirmedSteps`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L1143) treats block presence as confirmed without enforcing the configured depth. [`waitForConfirmationWithTracking`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L1276) returns an empty value for some rollback strategies, which the sequential caller converts to `ConfirmationTimeoutException` — and which bypasses `persistTransactionRolledBack` (NEW-03).
- GAP-18: no locking, reservation, or serialization exists between executions; `FlowUtxoSupplier` filters only against the *same run's* spent inputs (`FlowExecutionContext.getAllSpentInputs`), so two concurrent runs see and select the same base UTXOs.
- GAP-19: no code path sets or records a validity interval for orchestration purposes; `TxContext.validFrom/validTo` exist in QuickTx but TxFlow neither defaults them nor persists them per attempt.
- GAP-20: `FlowExecutor.java` is 2,986 lines: supplier wiring, ~15 mutable config fields, effective-settings resolution, six execution-path variants (`doExecuteSequential` :704, `doExecutePipelined` :956, `doExecuteBatch` :1929, plus `...WithResume` at :2417/:2575/:2760), per-step retry, confirmation waiting, rollback handling, UTXO capture, hooks abstraction, resume verification, and persistence helpers — one class.
- GAP-21: blocking `Thread.sleep` at ConfirmationTracker.java:255 and FlowExecutor.java:1243, 1250, 1398, 1460, 1606; no clock or scheduler abstraction; rollback integration scenarios exist that accept more than one outcome.
- Package ownership: [`FlowExecutionSettings`](../src/main/java/com/bloxbean/cardano/client/txflow/FlowExecutionSettings.java#L1) imports public configuration types from `txflow.exec`, contrary to the package-boundary direction in ADR 0001.

## Detailed Findings And Decisions

### Decision 1: Define A Versioned Portable Document Envelope

#### Existing YAML API

```yaml
version: "1.0"
context: ...
flow:
  id: example
  steps:
    - step:
        id: step-1
        tx: ...
```

The current `FlowDocument.validateVersion(String)` helper is not called by the standard `TxFlow.fromYaml(String)` path. A missing version is treated like the default, and there is no mandatory document kind. Consumers must inspect fields such as `flow` and `transaction` to distinguish TxFlow from TxPlan.

#### Proposed YAML API

```yaml
api_version: txflow.cardano-client.dev/v1alpha1
kind: TxFlow
metadata:
  name: fund-and-forward
  version: "1.0.0"
  annotations:
    owner: treasury-team
spec:
  network: preview
  parameters: ...
  execution: ...
  steps: ...
```

`api_version` versions the serialized schema. `metadata.version` versions the user's flow definition. Neither value is the ADR document version.

**Resolved (2.0.0; form revised 2.1.0; host revised 2.5.0)**: the schema identifier is `txflow.cardano-client.dev/v1alpha1`, following the Kubernetes `group/version` convention — the group is a DNS name (`txflow.cardano-client.dev`) and the version is the path segment. The group is product-scoped (cardano-client-lib's domain) rather than organization-scoped so the schema identity survives organizational rebranding; it is a symbolic identifier under the maintainer's control, and nothing is ever fetched from it. The 0.2.0 draft proposed `ccl.bloxbean.com/txflow/v2alpha1`; `v1alpha1` was chosen because the legacy format was never declared stable, so the portable format is the *first* versioned public contract.

The portable schema uses a flat step list:

```yaml
steps:
  - id: first
    transaction: ...
  - id: second
    needs: [first]
    transaction: ...
```

The redundant `- step:` wrapper remains supported only by the legacy-format compatibility decoder.

#### Transaction Schema Ownership

The transaction inside a portable TxFlow step is owned by the QuickTx/TxPlan contract. TxFlow does not define a second transaction-intent language and must not copy or fork the QuickTx transaction model.

Normative rules:

1. `spec.steps[].transaction` embeds exactly one QuickTx transaction definition. It is the single-transaction subset of a standalone `TxPlan`, consisting of `tx` plus its transaction `context`.
2. The standalone `TxPlan` envelope fields (`version`, `variables`, and the `transaction` list) are not nested inside a TxFlow step. TxFlow owns definition metadata, parameters, bindings, scheduling, outputs, execution policy, and lifecycle.
3. The `tx` and `context` fields use the same Java model, serialized names, intent discriminators, validation rules, and execution semantics as the corresponding QuickTx/TxPlan fields.
4. QuickTx owns the reusable transaction JSON Schema definitions. The TxFlow schema references or mechanically incorporates those shared definitions; independently maintained duplicate schema definitions are not permitted.
5. Each TxFlow schema version declares the QuickTx transaction-schema versions it accepts. An unsupported combination fails during parsing or compilation with a stable diagnostic; it is never interpreted using best-effort field matching.
6. Flow-aware input references such as `flow_output` are additions to the shared QuickTx input-reference model. Their syntax is declared by QuickTx, while resolution against named step outputs is performed by the TxFlow compiler/runtime.
7. Compilation creates an immutable defensive snapshot and materializes a fresh, exactly-one-transaction `TxPlan` (or equivalent internal QuickTx plan) for each execution. Neither compilation nor execution mutates the source `TxFlow`, embedded transaction definition, or a caller-supplied `TxPlan`.
8. A Java `FlowStep` containing a multi-transaction `TxPlan` remains executable only through explicitly supported legacy Java behavior. It is not portable and the portable writer rejects it with a diagnostic rather than selecting or discarding entries.

For `txflow.cardano-client.dev/v1alpha1`, the initial compatible QuickTx transaction schema is the current unified `Tx` YAML contract represented by `TransactionDocument.TxContent` and `TransactionDocument.TxContext`. Before the first artifact containing the portable schema is released, that QuickTx schema receives its own stable schema identifier and shared JSON Schema definitions; B1 treats this as part of publishing the TxFlow schema, not as a separate optional task.

#### Existing Java API

```java
TxFlow flow = TxFlow.fromYaml(yaml);
String yaml = flow.toYaml();
FlowDocument.validateVersion(yaml);
```

#### Proposed Java API

```java
TxFlowCodec codec = TxFlowCodec.standard();

FlowDocumentType type = codec.detect(source);
FlowParseResult parseResult = codec.parse(
        source,
        FlowParseOptions.serverDefaults()
);

if (parseResult.hasErrors()) {
    List<FlowDiagnostic> diagnostics = parseResult.getDiagnostics();
}

TxFlow flow = parseResult.requireFlow();
String canonicalYaml = codec.write(flow,
        FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1ALPHA1));
String canonicalJson = codec.write(flow,
        FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
```

Proposed supporting types:

```java
enum FlowDocumentType { TX_FLOW, TX_PLAN, UNKNOWN }
enum FlowFormat { YAML, JSON }
enum FlowSchemaVersion { LEGACY, V1ALPHA1 }

// 2.1.0: format alone cannot select a schema version; the writer takes both.
record FlowWriteOptions(FlowFormat format, FlowSchemaVersion schemaVersion) {}

final class FlowParseOptions {
    int maxDocumentBytes;
    int maxAliases;
    int maxNestingDepth;
    int maxSteps;
    UnknownFieldPolicy unknownFieldPolicy;
    Set<String> supportedApiVersions;
}

final class FlowDiagnostic {
    String code;
    DiagnosticSeverity severity;
    String message;
    String documentPath;
    Integer line;
    Integer column;
    String stepId;
}
```

#### Compatibility

- `TxFlow.fromYaml(String)` remains as a convenience adapter and delegates to `TxFlowCodec` with legacy-compatible options.
- `TxFlow.toYaml()` writes the legacy format while the flow originated from the legacy format, unless the caller explicitly migrates it.
- `TxFlowCodec.write(flow, FlowWriteOptions)` — with an explicit schema version — is the only canonical portable-format writer; content the selected schema version cannot represent fails with diagnostics (2.1.0: `FlowWriteOptions` replaces the format-only overload, which could not select a schema version).
- Unsupported versions, duplicate keys, multiple YAML documents, and invalid document kinds fail before model construction.
- Publish `txflow-v1alpha1.schema.json` as a module resource and release artifact.

### Decision 2: Separate Definition Identity From Execution Identity

#### Existing Java API

```java
TxFlow flow = TxFlow.builder("monthly-distribution").build();
FlowHandle handle = executor.execute(flow);
```

`flow.getId()` is used by active execution tracking, the registry, results, and the state store. No per-run identifier exists anywhere in the module.

#### Proposed Java API

```java
TxFlow definition = TxFlow.builder("monthly-distribution")
        .withDefinitionVersion("1.2.0")
        .build();

FlowExecutionRequest request = FlowExecutionRequest.builder()
        .definition(definition)
        .executionId(FlowExecutionId.random())
        .idempotencyKey("customer-42:2026-07")
        .bindings(bindings)
        .correlationId("invoice-run-8842")
        .requestedSettings(requestedSettings)
        .build();

FlowExecutionHandle handle = engine.start(request);
```

Proposed identity types:

```java
record FlowDefinitionRef(String id, String version, String fingerprint) {}
record FlowExecutionId(String value) {}
```

**Resolved (2.0.0; idempotency scope revised 2.1.0)**: `TxFlow` remains the definition model name; no `FlowDefinition` rename. Idempotency uniqueness is scoped by (tenant/principal namespace where the embedding server supplies one, idempotency key). A request matches an existing execution only when BOTH the definition fingerprint AND a canonical execution-request fingerprint — the semantically significant request fields (bindings, requested settings, network), canonically serialized and hashed — are equal; a match returns the existing execution's handle/result. The same key with a different definition or request fingerprint is a typed `TXFLOW_IDEMPOTENCY_CONFLICT` error: a reused key never silently returns an earlier execution that was started with different bindings or settings.

#### Proposed YAML API

The YAML document contains reusable definition identity only:

```yaml
metadata:
  name: monthly-distribution
  version: "1.2.0"
```

Execution identity and idempotency belong to the execution request, not the reusable YAML definition. A transport may express that request as JSON or YAML, but it is a separate model:

```yaml
execution_id: 01JZZY3P9J3R0Q6NGS30TKS8NF
definition:
  name: monthly-distribution
  version: "1.2.0"
idempotency_key: customer-42:2026-07
bindings:
  beneficiary: addr_test1...
  amount: 5000000
```

#### Compatibility

- Legacy execution APIs derive an execution ID internally. **Revised 2.1.0**: Track A threads this internal execution ID through the execution context, results, and logs for correlation only; the same-definition duplicate guard and the id-keyed registry/state-store behavior are retained until Track C1, where execution-ID keying lands together with Decision 18 spending-resource serialization. Concurrent same-definition executions are never enabled before contention control exists.
- `FlowResult.getFlowId()` remains available but is deprecated in favor of `getDefinitionRef()` and `getExecutionId()`.
- Existing per-executor duplicate-flow-ID protection is replaced by execution-ID and idempotency-key protection.

### Decision 3: Introduce Explicit Parse, Bind, Compile, And Execute Stages

#### Existing Java API

```java
TxFlow flow = TxFlow.fromYaml(yaml);
FlowResult result = executor.executeSync(flow);
```

Some structural validation occurs during Jackson mapping, some graph validation occurs in `TxFlow.validate()`, resource resolution occurs during QuickTx composition, and transaction validation can occur during building.

#### Proposed Java API

```java
FlowParseResult parsed = codec.parse(yaml, parseOptions);

FlowCompilationResult compilation = compiler.compile(
        FlowCompilationRequest.builder()
                .definition(parsed.requireFlow())
                .bindings(bindings)
                .resources(resourceCatalog)
                .policy(executionPolicy)
                .build()
);

if (compilation.hasErrors()) {
    return compilation.getDiagnostics();
}

CompiledTxFlow compiled = compilation.requireCompiledFlow();
FlowExecutionHandle handle = engine.start(
        FlowExecutionRequest.builder()
                .compiledFlow(compiled)
                .executionId(FlowExecutionId.random())
                .build()
);
```

Compilation performs, in order:

1. schema and document validation;
2. step and dependency validation;
3. typed parameter binding;
4. transaction-plan cardinality and intent validation;
5. logical-reference syntax and capability preflight;
6. output-binding and flow-output-reference validation;
7. execution-mode compatibility validation;
8. network validation;
9. server policy evaluation and effective-settings calculation;
10. immutable compiled-plan creation.

Compilation does not sign or submit transactions. Backend-dependent checks such as current protocol parameters or UTXO availability belong to an optional preflight/dry-run phase:

```java
FlowPreflightResult preflight = engine.preflight(compiled, PreflightOptions.defaults());
```

#### Proposed Validation API

```java
FlowValidationResult validation = compiler.validate(
        FlowValidationRequest.of(definition, bindings, resources, policy)
);
```

`TxFlow.validate()` remains for lightweight graph validation but is no longer presented as complete executable validation; its Javadoc is corrected in Track A.

### Decision 4: Replace Raw YAML Variables With Typed Parameters And Bindings

#### Existing YAML API

```yaml
flow:
  variables:
    amount: 5000000
  steps:
    - step:
        tx:
          intents:
            - type: payment
              address: ${receiver}
              amounts:
                - unit: lovelace
                  quantity: ${amount}
```

The current implementation extracts the variable map and replaces `${...}` in the entire YAML string before deserializing the document. Because substitution is destructive, a parsed templated flow re-serializes with resolved literals, so round trips are not idempotent.

#### Proposed YAML API

```yaml
spec:
  parameters:
    beneficiary:
      type: address
      required: true
    amount:
      type: integer
      default: 5000000
      minimum: 1000000
      maximum: 100000000
    memo:
      type: string
      required: false
      max_length: 64

  steps:
    - id: pay
      transaction:
        tx:
          intents:
            - type: payment
              address: ${{ inputs.beneficiary }}
              amounts:
                - unit: lovelace
                  quantity: ${{ inputs.amount }}
```

When a scalar consists only of a parameter expression, binding preserves the parameter's native type. Interpolation inside a larger string is allowed only for string-compatible parameter types:

```yaml
description: "Payment for ${{ inputs.memo }}"
```

Expressions are not allowed in YAML property names, tags, type discriminators, or arbitrary executable code.

**Syntax isolation rule (2.0.0)**: the two expression syntaxes never coexist in one document. `${x}` is recognized only by the legacy-format decoder; `${{ inputs.x }}` only by the portable-format decoder. A `${{ ... }}` token in a legacy document, or a `${...}` token in a portable document, is a hard parse error with a migration diagnostic — never silently passed through or double-substituted.

#### Existing Java API

```java
TxFlow.builder("flow")
        .addVariable("amount", 5_000_000L)
        .build();
```

#### Proposed Java API

```java
TxFlow flow = TxFlow.builder("flow")
        .addParameter(ParameterSpec.integer("amount")
                .required()
                .minimum(1_000_000L)
                .maximum(100_000_000L)
                .build())
        .addParameter(ParameterSpec.address("beneficiary").required().build())
        .build();

FlowBindings bindings = FlowBindings.builder()
        .put("amount", 5_000_000L)
        .put("beneficiary", "addr_test1...")
        .build();
```

Legacy `variables` are decoded as definition-local defaults. A migration warning advises authors to move externally supplied values to `parameters` and `FlowBindings`.

Sensitive parameters are never stored in the definition and are redacted from diagnostics, events, and snapshots:

```java
ParameterSpec.string("externalToken").sensitive().required().build();
```

### Decision 5: Separate Scheduling Dependencies From Prior-Output References

#### Existing YAML API

```yaml
depends_on:
  - from_step: fund
    strategy: all
```

This currently influences the `UtxoSupplier` seen by QuickTx. It does not prove that the selected outputs were used by the transaction.

#### Existing Java API

```java
FlowStep.builder("forward")
        .dependsOn("fund")
        .dependsOnIndex("fund", 0)
        .dependsOn(StepDependency.filter("fund", predicate))
        .withTxPlan(plan)
        .build();
```

#### Proposed YAML API

Scheduling dependency:

```yaml
needs: [fund]
```

Named output binding on the producing step:

```yaml
outputs:
  staging_funds:
    select:
      output_index: 0
    expect: exactly_one
```

Explicit consumption by a normal input intent:

```yaml
inputs:
  - type: collect_from
    refs:
      - flow_output:
          step: fund
          output: staging_funds
```

Explicit use as a reference input:

```yaml
inputs:
  - type: reference_input
    ref:
      flow_output:
        step: deploy-script
        output: script_reference
```

Output selectors use the existing QuickTx declarative UTXO filter model where possible. Index selection is supported, but address, asset, datum, and reference-script selectors are preferred for long-lived definitions.

**Resolved (2.0.0)**: named output bindings live on `FlowStep` (not on individual intents) for the first portable version; intent-level binding may be added later without breaking the step-level model.

#### Proposed Java API

```java
FlowStep fund = FlowStep.builder("fund")
        .withTxPlan(fundPlan)
        .bindOutput("staging_funds",
                FlowOutputSelector.atIndex(0).expectExactlyOne())
        .build();

FlowStep forward = FlowStep.builder("forward")
        .needs("fund")
        .withTxPlan(forwardPlanUsing(
                TxInputRef.flowOutput("fund", "staging_funds")))
        .build();
```

Proposed input-reference API in QuickTx:

```java
sealed interface TxInputRef permits OnChainUtxoRef, FlowOutputRef {}

record OnChainUtxoRef(String txHash, int outputIndex) implements TxInputRef {}
record FlowOutputRef(String stepId, String outputName) implements TxInputRef {}
```

**Resolved (2.0.0; rationale corrected 2.1.0)**: `TxInputRef` starts sealed. The 2.0.0 rationale ("unsealing later is source-compatible") was wrong: consumers using exhaustive sealed-type switches stop compiling when the hierarchy is widened or unsealed. Sealed is still chosen for the pre-release series because compiler-checked exhaustiveness surfaces unhandled reference kinds early; the accepted cost is that widening the hierarchy is a source-breaking change — tolerable before a stable release, to be re-evaluated at stabilization.

#### Compatibility

- Legacy `depends_on` remains supported as an ordering dependency plus legacy pending-UTXO visibility.
- The compiler emits `TXFLOW_LEGACY_IMPLICIT_INPUT` when legacy dependency behavior is used.
- The portable format never interprets `needs` as input consumption.
- A required flow-output reference that resolves to zero outputs fails the step before transaction construction.
- An `exactly_one` selector that resolves to multiple outputs fails rather than silently selecting one.

### Decision 6: Require Lossless Portability

#### Existing Java API And Behavior

```java
FlowStep javaOnly = FlowStep.builder("step")
        .withTxContext(builder -> builder.compose(tx).withSigner(signer))
        .build();

String yaml = TxFlow.builder("flow")
        .addStep(javaOnly)
        .build()
        .toYaml();
```

The current serializer logs (at debug level) that the factory cannot be serialized and emits a step without transaction content.

A `FlowStep` may also hold a `TxPlan` containing multiple transactions, while the current step serializer reads only the first transaction from the serialized plan. Java `Predicate<Utxo>` filters cannot be serialized — and on read-back a FILTER dependency silently becomes match-all (NEW-01), which is a behavioral corruption, not just data loss. Conversely, YAML without `tx` can produce an empty but non-null `TxPlan`.

#### Proposed Java API

```java
FlowPortabilityResult portability = codec.checkPortable(flow);

if (!portability.isPortable()) {
    // diagnostics include step and reason
}

String yaml = codec.write(flow, writeOptions); // throws FlowEncodingException on loss
```

The existing convenience API changes from silent omission to failure:

```java
flow.toYaml(); // fails if any step is not portable
```

For intentional Java-only flows:

```java
FlowExecutor.create(backendService).executeSync(javaOnlyFlow); // remains supported
codec.write(javaOnlyFlow, writeOptions);                       // rejected
```

#### Required Invariants

- A portable flow step contains exactly one transaction plan.
- A Java transaction factory is executable but not portable.
- Every selector and retry/confirmation setting has a serialized representation.
- Encoding never catches an exception, logs it, and continues with partial content.
- A predicate-based FILTER dependency is rejected by the portable writer; it never degrades to match-all.
- `decode(encode(flow))` preserves compiled execution semantics, and a second round trip is idempotent.
- Property-based and golden-fixture tests enforce semantic round trips.

### Decision 7: Make The Definition And Compiled Plan Immutable

#### Existing API

`TxFlow` wraps its top-level collections, but `TxPlan` and nested values remain mutable. During execution, flow variables are added directly to a step's `TxPlan` in all three chaining modes, permanently mutating the shared definition; the stale-value hazard materializes when a plan is shared across flows with different variables, under concurrent execution, or once per-execution bindings exist (NEW-02).

#### Proposed API

```java
TxFlow definition = TxFlow.builder("flow")
        .addStep(step)
        .build(); // deeply immutable definition

CompiledTxFlow compiled = compiler.compile(request).requireCompiledFlow();
```

The compiler creates run-independent immutable templates. Runtime binding produces run-scoped immutable step plans or copies:

```java
CompiledStepPlan boundStep = compiled.bindStep(stepId, executionContext);
```

No execution path mutates the source `TxFlow` or its `TxPlan`. Existing mutable `TxPlan` APIs remain available to construct plans, but compilation takes a defensive snapshot. **Track A ships the minimum fix early**: the current executor binds flow variables into a per-run copy of the plan instead of mutating the shared instance (item A4), independent of the compiler work.

### Decision 8: Add Unified Resource Resolution And Capability Preflight

#### Existing Java API

```java
FlowExecutor executor = FlowExecutor.create(backendService)
        .withSignerRegistry(signerRegistry);
```

QuickTx now supports:

```java
quickTxBuilder.compose(plan, signerRegistry, scriptRegistry);
```

TxFlow currently calls only the signer-registry overload at all three composition sites.

#### Proposed Java API

Short-term compatibility addition (Track A):

```java
FlowExecutor executor = FlowExecutor.create(backendService)
        .withSignerRegistry(signerRegistry)
        .withScriptRegistry(scriptRegistry);
```

Preferred unified API (Track B):

```java
FlowResourceCatalog resources = FlowResourceCatalog.builder()
        .signers(signerRegistry)
        .scripts(scriptRegistry)
        .addresses(addressResolver)
        .externalData(externalDataResolver)
        .build();

FlowEngine engine = FlowEngine.builder()
        .services(flowServices)
        .resources(resources)
        .build();
```

Resource capabilities are inspectable without exposing secrets:

```java
Optional<ResourceDescriptor> describe(ResourceRef ref);
ResolvedResource resolve(ResourceRef ref, ResolutionContext context);
```

Example descriptors:

```java
record ResourceDescriptor(
        ResourceRef ref,
        Set<ResourceCapability> capabilities,
        Optional<String> network,
        Map<String, String> publicMetadata) {}
```

Compilation checks that referenced resources exist and provide capabilities such as `PAYMENT_SIGN`, `STAKE_SIGN`, `POLICY_SIGN`, `SCRIPT_ATTACH`, or `ADDRESS_SOURCE`. Actual private material is resolved only for the execution that needs it.

### Decision 9: Treat YAML Execution Settings As Requests Evaluated By Policy

#### Existing YAML API

```yaml
context:
  chaining_mode: BATCH
  confirmation: quick
  rollback_strategy: REBUILD_ENTIRE_FLOW
  retry:
    max_attempts: 1000
```

ADR 0001 defines precedence between executor configuration, flow configuration, and defaults. That precedence is correct for trusted applications but does not express authorization or resource limits.

#### Existing Java API

```java
FlowExecutor.create(backendService)
        .withChainingMode(ChainingMode.SEQUENTIAL)
        .withDefaultRetryPolicy(policy)
        .withConfirmationConfig(config)
        .withRollbackStrategy(strategy);
```

#### Proposed Java API

```java
FlowExecutionPolicy policy = FlowExecutionPolicy.builder()
        .allowNetworks(Set.of(Network.PREVIEW))
        .allowChainingModes(Set.of(ChainingMode.SEQUENTIAL, ChainingMode.PIPELINED))
        .maxSteps(20)
        .maxRetryAttempts(5)
        .allowRollbackActions(Set.of(
                RollbackAction.FAIL,
                RollbackAction.WAIT_FOR_REINCLUSION,
                RollbackAction.RECONCILE_AND_REBUILD,
                RollbackAction.PAUSE_FOR_RECOVERY))
        .maxRollbackRecoveryCycles(3)
        .minimumRollbackObservations(2)
        .maxConfirmationTimeout(Duration.ofMinutes(20))
        .maxExecutionDuration(Duration.ofHours(1))
        .maxLovelacePerTransaction(100_000_000L)
        .allowResourcePrefixes(Set.of("account://customer/", "script://approved/"))
        .build();

PolicyEvaluationResult evaluation = policy.evaluate(
        definition,
        requestedSettings,
        bindings,
        resourceDescriptors
);

EffectiveFlowExecutionSettings effective = evaluation.requireEffectiveSettings();
```

Policy evaluation may reject, cap, or replace requested settings. The result records both requested and effective values for auditability.

**Resolved (2.0.0; semantic-override rule added 2.2.0)**: numeric safety ceilings (retry counts, timeouts, amounts, durations, recovery cycles) are capped with the requested-vs-effective difference recorded and a warning diagnostic, then execution continues. Semantic replacements — execution mode, rollback action, monitoring horizon — are not silent caps: policy either rejects the request or requires explicit caller acknowledgement (`FlowExecutionRequest.acknowledgeSemanticOverrides(true)`) before substituting, because a flow authored for `PIPELINED`/`RECONCILE_AND_REBUILD` can carry different business assumptions than the substituted behavior. A `strictSettings()` policy flag remains available for deployments that must reject any difference at all.

#### Proposed YAML API

```yaml
spec:
  execution:
    mode: PIPELINED
    confirmation:
      preset: testnet
      min_confirmations: 6
    rollback:
      action: RECONCILE_AND_REBUILD
      monitoring_horizon: UNTIL_FLOW_TERMINAL
      rebuild_scope: INVALIDATED_CLOSURE
      max_recovery_cycles: 3
      reinclusion_window: 2m
    retry:
      max_attempts: 3
      backoff: exponential
      initial_delay: 1s
      max_delay: 30s
```

The canonical portable-format name is `execution`, and the canonical field is `mode`. The legacy `context.chaining_mode` shape remains supported by the compatibility decoder.

### Decision 10: Introduce Portable Lifecycle, Result, Event, And Error Models

#### Existing Java API

```java
FlowResult result = executor.executeSync(flow);
Throwable error = result.getError();
FlowStepResult step = result.getStepResult("fund").orElseThrow();
boolean successful = step.isSuccessful();
```

The existing result model cannot precisely distinguish a transaction that was built, submitted, included, deeply confirmed, or later rolled back.

#### Proposed Java API

```java
FlowExecutionResult result = handle.await();

FlowExecutionId executionId = result.getExecutionId();
FlowExecutionStatus status = result.getStatus();
List<StepExecutionResult> steps = result.getSteps();
List<FlowError> errors = result.getErrors();
```

Proposed lifecycle states:

```java
enum FlowExecutionStatus {
    ACCEPTED,
    COMPILING,
    READY,
    RUNNING,
    RECONCILING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED,
    RECOVERY_REQUIRED
}

enum StepAttemptStatus {
    PENDING,
    BUILDING,
    BUILT,
    SIGNING,
    SIGNED,
    SUBMITTING,
    SUBMITTED,
    IN_BLOCK,
    CONFIRMED,
    ROLLED_BACK,
    SUPERSEDED,
    FAILED,
    CANCELLED,
    SKIPPED
}
```

Portable error:

```java
record FlowError(
        String code,
        FlowErrorCategory category,
        FlowErrorPhase phase,
        String message,
        boolean retryable,
        String stepId,
        Integer attempt,
        String transactionHash,
        Map<String, Object> details) {}
```

Java-only diagnostic access may retain the original cause without including it in portable serialization:

```java
Optional<Throwable> result.getInternalCause();
```

Portable event envelope:

```java
record FlowEvent(
        FlowExecutionId executionId,
        long sequence,
        Instant timestamp,
        FlowEventType type,
        String stepId,
        Integer attempt,
        Map<String, Object> data) {}
```

The monotonically increasing event sequence supports durable consumers, reconnecting clients, and audit logs.

#### Compatibility

- `FlowListener` remains supported through an adapter from `FlowEvent`.
- `FlowResult` and `FlowStepResult` remain available for legacy execution methods.
- New engine methods return the richer execution result and handle types.

### Decision 11: Define Durable State And Recovery As A CCL Protocol

#### Existing Java API

```java
interface FlowStateStore {
    void saveFlowState(FlowStateSnapshot snapshot);
    List<FlowStateSnapshot> loadPendingFlows();
    void updateTransactionState(
            String flowId,
            String stepId,
            String txHash,
            TransactionStateDetails details);
    void markFlowComplete(String flowId, FlowStatus status);
}
```

Documentation currently shows `executor.resumeTracking(snapshot)`, but such an API does not exist. The API that does exist — `FlowExecutor.resume(TxFlow, FlowResult)` — consumes a previous in-memory `FlowResult`, not a persisted snapshot, so the persistence store and the resume path are disconnected. The current snapshot lacks a definition fingerprint, execution ID, attempt history, prepared signed transaction, and output/spent-input data needed for robust recovery. Persistence failures are logged and ignored by the executor, and `IN_BLOCK`/depth transitions are never persisted at all.

#### Proposed Java API

```java
interface FlowExecutionStore {
    // 2.2.0: atomic create-or-return/conflict on the idempotency claim.
    // A plain create() lets two servers race the same claim into duplicate executions.
    StartExecutionResult createOrGet(
            IdempotencyClaim claim,
            FlowExecutionSnapshot initial);

    // 2.2.0: every mutation carries a fence; the store rejects any write whose
    // fence predates the current lease epoch. Revision CAS alone cannot fence a
    // stale owner whose lease expired before new writes advanced the revision.
    // 2.4.0: the fence is a composite MutationFence so a write presents the
    // execution-lease epoch and, when spending resources are claimed, the
    // resource-lease epoch — the store validates both and binds every lease
    // and event to the target execution (and resource leases to its owner).
    AppendResult append(
            FlowExecutionId executionId,
            long expectedRevision,
            MutationFence fence,
            List<FlowEvent> events);

    // 2.3.0: durable consumers read by sequence cursor; a cursor below
    // the compaction watermark yields a typed EVENTS_COMPACTED result telling
    // the consumer to re-baseline from the snapshot.
    EventReadResult readEvents(
            FlowExecutionId executionId,
            long afterSequence,
            int limit);

    Optional<FlowExecutionSnapshot> load(FlowExecutionId executionId);

    List<FlowExecutionRef> findRecoverable(RecoveryQuery query);

    Optional<ExecutionLease> tryAcquireLease(
            FlowExecutionId executionId,
            String owner,
            Duration leaseDuration);

    // 2.2.0: spending-resource leases (Decision 18) are a store primitive.
    Optional<ResourceLease> tryAcquireResources(
            Set<SpendingResourceId> resources,
            String owner,
            Duration leaseDuration);

    // 2.3.0: the renewal APIs required by the "renewed by the active owner"
    // semantics; renewal preserves the lease epoch, re-acquisition mints a new one.
    ExecutionLease renewLease(ExecutionLease lease, Duration duration);
    ResourceLease renewResources(ResourceLease lease, Duration duration);

    void releaseLease(ExecutionLease lease);
    void releaseResources(ResourceLease lease);
}

// 2.3.0: events since a cursor, or a typed compaction signal.
record EventReadResult(
        List<FlowEvent> events,
        long compactedThroughSequence,
        boolean eventsCompacted) {}   // true → cursor predates the watermark; re-baseline

record IdempotencyClaim(
        String namespace,            // tenant/principal scope, empty if unused
        String idempotencyKey,
        String definitionFingerprint,
        String requestFingerprint) {}

// Returned by createOrGet: CREATED, MATCHED (equal fingerprints — existing
// snapshot returned), or CONFLICT (same key, different fingerprints).
enum StartOutcome { CREATED, MATCHED, CONFLICT }

record StartExecutionResult(
        StartOutcome outcome,
        FlowExecutionSnapshot snapshot) {}

// Canonical resolved spending-resource identity (Decision 18).
record SpendingResourceId(String canonicalId) {}

// Monotonic epoch minted per lease acquisition; validated on every mutation.
record LeaseFence(FlowExecutionId executionId, long epoch) {}

// 2.4.0: composite mutation fence — resourceLease is null for executions
// holding no spending-resource lease.
record MutationFence(LeaseFence executionLease, ResourceLeaseFence resourceLease) {}

record ResourceLeaseFence(String leaseId, long epoch, Set<SpendingResourceId> resources) {}
```

The snapshot includes:

```java
record FlowExecutionSnapshot(
        FlowExecutionId executionId,
        FlowDefinitionRef definition,
        long revision,
        long compactedThroughSequence,   // 2.3.0: journal-compaction watermark
        FlowExecutionStatus status,
        Map<String, PersistedBinding> bindings,
        EffectiveFlowExecutionSettings effectiveSettings,
        List<StepExecutionSnapshot> steps,
        Instant createdAt,
        Instant updatedAt) {}

record PersistedBinding(
        String parameterName,
        String parameterType,
        Object nonSensitiveValue,
        String secureValueRef,
        String valueFingerprint,
        String redactedDisplay) {}

record PreparedTransaction(
        String transactionHash,
        SignedPayload signedPayload,
        List<Utxo> expectedOutputs,
        List<TransactionInput> spentInputs,
        Long validFromSlot,   // nullable: legacy / intentionally unbounded transactions
        Long validToSlot,     // nullable: legacy / intentionally unbounded transactions
        Instant preparedAt) {}

// 2.2.0: inline-or-reference payload; InlineCbor takes defensive copies of
// the byte array on construction and access.
sealed interface SignedPayload permits SignedPayload.InlineCbor, SignedPayload.ExternalRef {
    // Nested records in an interface are implicitly public, so the compact
    // constructor must be public — Java 17 rejects a stricter one.
    record InlineCbor(byte[] bytes) implements SignedPayload {
        public InlineCbor {
            Objects.requireNonNull(bytes, "bytes");
            bytes = bytes.clone();                                   // copy in
        }
        @Override public byte[] bytes() { return bytes.clone(); }    // copy out
    }
    record ExternalRef(String ref, String sha256) implements SignedPayload {}
}

// 2.3.0/2.4.0: recovery loads external payloads through this CCL primitive.
// The resolver only loads bytes; verification is CCL's responsibility — the
// engine verifies the recorded sha256 AND recomputes the Cardano transaction
// hash against the recorded transactionHash before any resubmission.
interface SignedPayloadResolver {
    byte[] resolve(SignedPayload.ExternalRef ref);
}
```

`PreparedTransaction` records the validity interval when the built transaction carries one — the fields are nullable so intentionally unbounded legacy transactions remain representable — so recovery can decide whether identical-CBOR resubmission is still possible without re-deriving TTL (Decision 19).

Non-sensitive bindings may be stored directly. Sensitive bindings are persisted as references to an application-controlled secure value store, together with a fingerprint and redacted display value. CCL defines the `secureValueRef` field and a recovery-time resolver interface but does not implement a secret store. A snapshot must retain enough binding information to compile or resume unbuilt later steps; redaction alone is not sufficient for recovery.

**Resolved (2.0.0; payload modeled explicitly 2.2.0)**: signed transaction bytes are stored inline by default via `SignedPayload.InlineCbor` (defensive copies on construction and access); `SignedPayload.ExternalRef` carries a reference plus content hash for deployments with payload-size or key-management constraints. Stores must support inline payloads; external-reference support is optional and declared by the implementation. Recovery resolves external references through the `SignedPayloadResolver` primitive; verification is CCL's job, not the resolver implementation's — before any resubmission the engine verifies the recorded `sha256` against the loaded bytes and recomputes the Cardano transaction hash from the payload, requiring it to equal the recorded `transactionHash` (revised 2.4.0). The recomputed-hash check applies to inline payloads as well.

```java
interface SecureBindingResolver {
    Object resolve(String secureValueRef, ResolutionContext context);
}
```

Before submission, the engine persists `SIGNED` with the locally computed transaction hash and signed CBOR. It then persists `SUBMITTING`, calls the backend, and persists `SUBMITTED`. If the process stops after the backend accepted the transaction but before `SUBMITTED` is stored, recovery queries the known hash before deciding what to do.

Recovery API:

```java
FlowRecoveryResult recovery = engine.recover(
        FlowRecoveryRequest.builder()
                .executionId(executionId)
                .definitionResolver(definitionResolver)
                .build()
);
```

Recovery reconciliation rules:

1. Verify the definition fingerprint.
2. Acquire an execution lease or fail without mutation.
3. For each non-terminal attempt with a prepared hash, query chain/backend state.
4. If confirmed, reconstruct outputs and advance state.
5. If in block, resume confirmation tracking.
6. If submitted or possibly in mempool, continue tracking before rebuilding.
7. If absent and submission was never attempted, submit the prepared transaction (within its validity interval).
8. If absent after a recorded uncertain submission, apply configured reconciliation timeout before retry.
9. Rebuild only when policy and retry classification allow it.
10. Append recovery decisions as events.

#### Persistence Failure Policy

```java
enum PersistenceFailurePolicy {
    FAIL_EXECUTION,
    PAUSE_FOR_RECOVERY,
    WARN_AND_CONTINUE
}
```

The server-oriented default is `PAUSE_FOR_RECOVERY` or `FAIL_EXECUTION`, not unconditional warn-and-continue. The legacy `FlowExecutor` facade keeps `WARN_AND_CONTINUE` for compatibility.

#### Event Journal Growth

The append-based journal is unbounded by nature. The store contract defines a compaction rule so implementations do not invent their own: once an execution reaches a terminal state, an implementation may replace its event history with the terminal snapshot plus attempt summaries, provided attempt histories (including rolled-back and superseded attempts) are preserved in the snapshot. Retention beyond that is an application decision.

Compaction is observable (2.2.0): the snapshot records an inclusive `compactedThroughSequence` watermark. A consumer reconnecting with an exclusive cursor strictly below the watermark receives a typed `EVENTS_COMPACTED` response and must re-baseline from the snapshot; a cursor equal to the watermark safely requests the first retained event after it. Events are never silently missing for a durable consumer.

### Decision 12: Use Typed, Phase-Aware Retry Classification

#### Existing Java API

```java
RetryPolicy policy = RetryPolicy.builder()
        .maxAttempts(3)
        .retryOnTimeout(true)
        .retryOnNetworkError(true)
        .build();

boolean retry = policy.isRetryable(error);
```

The current implementation classifies several errors using message substrings and retries unknown exceptions by default (`return true` fall-through). **Corrections to 0.2.0 established during verification**: an `instanceof Error` guard exists, but it is positioned *after* the timeout/network substring checks, so an `Error` whose message contains "timeout", "connection", "network", "socket", "reset", or "refused" is retried; and only `ConfirmationTimeoutException` has its cause chain walked — other wrapped root causes are invisible to classification. Additionally, backoff arithmetic can go negative and crash the retry loop with `IllegalArgumentException` (NEW-04).

#### Proposed Java API

```java
RetryDecision decision = retryPolicy.evaluate(
        RetryContext.builder()
                .phase(FlowErrorPhase.SUBMIT)
                .category(FlowErrorCategory.NETWORK)
                .attempt(2)
                .transactionHash(preparedHash)
                .submissionOutcome(SubmissionOutcome.UNKNOWN)
                .build()
);

switch (decision.getAction()) {
    case RETRY_SAME_TRANSACTION:
    case RECONCILE_THEN_RETRY:
    case REBUILD_STEP:
    case FAIL:
}
```

Retry policy adds jitter and optional backend `retryAfter` support:

```java
RetryPolicy.builder()
        .maxAttempts(3)
        .backoffStrategy(BackoffStrategy.EXPONENTIAL)
        .jitter(0.20)
        .initialDelay(Duration.ofSeconds(1))
        .maxDelay(Duration.ofSeconds(30))
        .retryableCategories(Set.of(
                FlowErrorCategory.NETWORK,
                FlowErrorCategory.BACKEND_UNAVAILABLE))
        .build();
```

`maxAttempts` is explicitly defined as total attempts including the initial attempt. A separate `maxRetries` name is not introduced.

Required arithmetic and classification invariants (shipped in Track A ahead of the typed API):

- the `Error` guard is evaluated before any message heuristics, and cause chains are unwrapped before classification;
- backoff arithmetic saturates (`Math.multiplyExact` with clamp) — a computed delay is always within `[0, maxDelay]`;
- retry and rollback limits are validated at build time;
- jitter (default 0.20) is applied within policy caps.

#### Compatibility

- `RetryPolicy.isRetryable(Throwable)` remains as a legacy adapter.
- Internal CCL failures are mapped to stable categories before policy evaluation.
- Unknown failures default to `FAIL` in server mode and may retain the current behavior in legacy mode.

### Decision 13: Treat Rollback As A Persisted Reconciliation Process

Rollback is not a normal step retry. It is a change in the observed ledger status of one or more submitted transaction attempts. The engine must pause forward progress, record the observation, reconcile all affected attempts, and only then decide whether to wait, fail, resubmit identical signed bytes, or rebuild part of the flow.

#### Verification Status At ADR Version 2.0.0

The 0.2.0 audit covered the rollback/retry source paths plus `RetryPolicyTest`, `ConfirmationTrackerTest`, `FlowExecutorTest`, `FlowExecutorResumeTest`, and `RollbackExceptionTest` (all passing on Java 17). The 2.0.0 verification re-audited every RB finding independently; the corrected verdicts are recorded in the findings table below. The Yaci DevKit rollback suite was inspected but not executed (requires a running local DevKit and mutates it via snapshots). Existing integration scenarios are not treated as final acceptance evidence because some allow more than one outcome when the rollback races confirmation, and some accept a rebuild failure caused by UTXO availability.

#### Current Rollback And Retry Findings

Verification column: ✓ confirmed as stated at 0.2.0; ✓± confirmed with the noted correction.

| ID | Severity | Verification | Current behavior | Risk | Required correction |
|----|----------|--------------|------------------|------|---------------------|
| RB-01 | Critical | ✓ | Sequential `REBUILD_ENTIRE_FLOW` creates a new context and starts again at step zero; `findStillConfirmedSteps` is never invoked on the sequential paths | An earlier transaction that remains on chain can be rebuilt and the business action can be duplicated | Reconcile every prior attempt and retain the still-valid prefix before any rebuild |
| RB-02 | Critical | ✓ | Tracking is a blocking wait for one transaction and stops when the target status is returned; `stopTracking` removes confirmed steps from detection entirely | A rollback of an earlier flow transaction may not be observed while a later step is running | Maintain a run-scoped monitor set until the configured monitoring horizon |
| RB-03 | Critical | ✓ | `FAIL_IMMEDIATELY` and exhausted `NOTIFY_ONLY` return an empty optional; the listener callback fires but `persistTransactionRolledBack` never does (NEW-03) | Rollback is reported as confirmation timeout, and the state store keeps the transaction at `SUBMITTED` | Return a typed rollback outcome and persist it before strategy evaluation |
| RB-04 | Critical | ✓ | Retry wraps build, sign, submit, and confirm as one operation; the only guard is the message-substring non-retryable list | An accepted submission with a lost response can cause rebuild/resubmission and duplicate intent | Persist the prepared hash and reconcile an unknown submission outcome before retry |
| RB-05 | High | ✓ | Pipelined restart treats transaction presence plus block height as confirmed; the no-tracker fallback hardcodes depth 0 | A shallow transaction can be skipped without satisfying `minConfirmations` | Recompute depth against a recorded chain point and enforce effective confirmation policy |
| RB-06 | High | ✓ | Rollback invalidation is inferred from declared step dependencies; spent inputs are captured but used only for build-time UTXO filtering, never for invalidation | Ordering-only dependencies and actual UTXO consumption are conflated | Compute invalidation from prepared transaction inputs and explicit flow-output references |
| RB-07 | High | ✓± | Thrown backend exceptions are handled safely (keep polling, end as timeout). However `Optional.empty()` from `getTransactionInfo` after a previously seen block height is **immediately** treated as rollback — indexer lag is conflated with reorg. No `UNKNOWN` state exists | Provider lag or eventual consistency can be mistaken for a ledger decision | Represent `UNKNOWN` separately; require N consistent absence observations; unknown observations never trigger rebuild |
| RB-08 | High | ✓± | Message-based classification with a default-retryable fall-through. The `instanceof Error` guard exists but sits after the substring checks, so an `Error` with a network-looking message is retried; cause chains (except `ConfirmationTimeoutException`) are not unwrapped | Permanent or wrapped failures may be retried; JVM errors can be misclassified | Evaluate the `Error` guard first, unwrap causes, use phase-aware typed categories, and default unknown failures to fail in server mode |
| RB-09 | Medium | ✓± | Confirmation and rollback paths use blocking sleep. Loops are cooperatively cancellation-aware *between* iterations, but sleeps wake only on real interrupt, so cancellation latency is up to one full interval (30s worst case in retry backoff) | Cancellation and high-concurrency server execution are less responsive | Use a scheduler/clock abstraction with cancellation-aware waits (Decision 21) |
| RB-10 | Medium | ✓ | `1L << (attempt-1)` wraps; `Math.min` does not saturate a negative delay; `Thread.sleep(negative)` throws `IllegalArgumentException` out of the retry loop; no jitter exists anywhere in the module | Extreme YAML values produce an unclassified crash, and synchronized retry bursts are possible | Validate bounds, saturate arithmetic, and apply policy-capped jitter |

#### Terms And Boundaries

- **Rollback detected** means an attempt previously observed in a block is authoritatively absent at a later compatible chain point, or the attempt is observed under a different block identity. A changed block identity represents rollback followed by re-inclusion even when an intermediate absence was not sampled.
- **Re-inclusion** means the same transaction hash appears in a new block after its earlier inclusion was invalidated. Re-inclusion does not create a new attempt.
- **Backend uncertainty** means the provider cannot make an authoritative observation because it is unavailable, behind the required chain point, or returned an error. It is not a rollback.
- **Confirmed** means the attempt reached the effective configured confirmation depth. It is practical finality for the execution policy, not absolute Cardano finality.
- **Invalidated closure** contains the rolled-back attempt and every submitted or prepared descendant whose actual inputs or explicit output references depend on an invalidated output. A `needs` ordering edge alone does not imply UTXO invalidation.
- **Recovery cycle** is one persisted rollback detection followed by reconciliation and a decision. It is separate from a step retry attempt and a submission-observation retry.
- **Monitoring horizon** defines how long CCL owns rollback detection. The server-oriented default is through flow termination. Monitoring after a terminal result belongs to a watcher or a later execution-reopen operation and is outside the synchronous execution contract.

#### Existing Java API

~~~java
ConfirmationConfig confirmation = ConfirmationConfig.builder()
        .minConfirmations(6)
        .maxRollbackRetries(3)
        .waitForBackendAfterRollback(true)
        .postRollbackWaitAttempts(30)
        .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
        .build();

FlowExecutor executor = FlowExecutor.create(backendService)
        .withConfirmationConfig(confirmation)
        .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
        .withDefaultRetryPolicy(RetryPolicy.defaults());
~~~

The existing API combines confirmation depth, rollback recovery budget, DevKit backend restart behavior, and UTXO-index synchronization in `ConfirmationConfig`. The four enum values combine detection response and rebuild scope. Generic step retry has no phase or known-submission context.

#### Existing YAML API

~~~yaml
context:
  confirmation:
    min_confirmations: 6
    max_rollback_retries: 3
    wait_for_backend_after_rollback: true
    post_rollback_wait_attempts: 30
    post_rollback_utxo_sync_delay: 3s
  rollback_strategy: REBUILD_ENTIRE_FLOW
  retry:
    max_attempts: 3
~~~

This legacy shape remains readable during the compatibility window. The backend-restart fields are test-environment concerns and must not be carried into the canonical portable rollback contract.

#### Proposed Java API

The portable API separates rollback detection/action from ordinary retry:

~~~java
RollbackPolicy rollbackPolicy = RollbackPolicy.builder()
        .action(RollbackAction.RECONCILE_AND_REBUILD)
        .monitoringHorizon(RollbackMonitoringHorizon.UNTIL_FLOW_TERMINAL)
        .rebuildScope(RollbackRebuildScope.INVALIDATED_CLOSURE)
        .maxRecoveryCycles(3)
        .reinclusionWindow(Duration.ofMinutes(2))
        .minimumConsistentAbsenceObservations(2)
        .build();

RetryPolicy retryPolicy = RetryPolicy.builder()
        .maxAttempts(3)
        .retryableCategories(Set.of(
                FlowErrorCategory.NETWORK,
                FlowErrorCategory.BACKEND_UNAVAILABLE))
        .build();

FlowEngine engine = FlowEngine.builder()
        .services(flowServices)
        .rollbackPolicy(rollbackPolicy)
        .defaultRetryPolicy(retryPolicy)
        .stateStore(flowExecutionStore)
        .build();
~~~

Proposed public types:

~~~java
enum RollbackAction {
    FAIL,
    WAIT_FOR_REINCLUSION,
    RECONCILE_AND_REBUILD,
    PAUSE_FOR_RECOVERY
}

enum RollbackMonitoringHorizon {
    UNTIL_STEP_CONFIRMED,
    UNTIL_FLOW_TERMINAL
}

enum RollbackRebuildScope {
    AFFECTED_STEP,
    INVALIDATED_CLOSURE
}

enum TransactionPresence {
    MEMPOOL,
    IN_BLOCK,
    CONFIRMED,
    ABSENT,
    UNKNOWN
}

record ChainPoint(long slot, long blockHeight, String blockHash) {}

record TransactionObservation(
        String transactionHash,
        TransactionPresence presence,
        ChainPoint observedAt,
        ChainPoint inclusionPoint,
        int confirmationDepth,
        Instant observedTime,
        FlowError observationError) {}

interface TransactionReconciler {
    TransactionObservation observe(
            String transactionHash,
            ReconciliationContext context);
}

// 2.3.0: capabilities are declared by the backend adapter — never by flow
// configuration, execution settings, or YAML.
interface TransactionObservationCapabilities {
    boolean supportsAuthoritativeAbsence();
    boolean supportsMempoolObservation();
}
~~~

`TransactionReconciler` is a CCL service primitive implemented by backend adapters. It must distinguish authoritative absence from unknown status. A backend without mempool support may return `ABSENT` or `UNKNOWN` according to its guarantees; it must not pretend to have observed `MEMPOOL`.

The engine records a typed decision rather than throwing an unstructured internal exception:

~~~java
record RollbackContext(
        FlowExecutionId executionId,
        String stepId,
        int attempt,
        TransactionObservation previousObservation,
        TransactionObservation currentObservation,
        Set<String> invalidatedStepIds,
        int recoveryCycle,
        EffectiveFlowExecutionSettings settings) {}

record RollbackDecision(
        RollbackAction action,
        RollbackRebuildScope rebuildScope,
        String reasonCode,
        Duration nextObservationDelay) {}
~~~

For a server request, YAML supplies requested values, server policy produces the effective `RollbackPolicy`, and the persisted snapshot records both.

**Resolved (2.0.0)**:

- The default and server-mode monitoring horizon is `UNTIL_FLOW_TERMINAL`; server policy may allow the legacy `UNTIL_STEP_CONFIRMED` horizon with a warning diagnostic.
- Rollback from absence requires observations classified as **authoritative `ABSENT`** (revised 2.2.0): an absence observation is authoritative only when the backend adapter explicitly declares the `AUTHORITATIVE_ABSENCE` capability for its transaction index AND the observation's chain point is at or beyond the attempt's recorded inclusion point. The chain-point check is necessary but not sufficient — a backend's tip endpoint may be current while its transaction index lags (replicas, separate sync pipelines), so only the capability declaration asserts index consistency. Adapters without the capability produce `UNKNOWN` for empty lookups; `UNKNOWN` never counts toward rollback no matter how many accumulate, and when reconciliation of `UNKNOWN` observations exhausts its budget the outcome is `RECOVERY_REQUIRED` (or a typed failure per policy) — never automatic rebuild. Authority is owned by the backend adapter/deployment (`TransactionObservationCapabilities`, or the server-side wrapper for known-consistent deployments) and is never expressible through flow configuration, execution settings, or YAML (2.3.0). The portable default is two consecutive authoritative absences; server policy may lower this to one.
- When `WAIT_FOR_REINCLUSION` expires, the default outcome is `RECOVERY_REQUIRED` (recoverable), not a terminal rollback failure; policy may harden this to terminal failure.
- The compatibility reinterpretation of `REBUILD_ENTIRE_FLOW` (reconcile the whole flow, rebuild only the invalidated closure) is accepted, and the legacy name is deprecated in documentation immediately to remove the ambiguity.

#### Proposed YAML API

~~~yaml
spec:
  execution:
    confirmation:
      preset: testnet
      min_confirmations: 6
      timeout: 10m
      check_interval: 3s
    rollback:
      action: RECONCILE_AND_REBUILD
      monitoring_horizon: UNTIL_FLOW_TERMINAL
      rebuild_scope: INVALIDATED_CLOSURE
      max_recovery_cycles: 3
      reinclusion_window: 2m
      minimum_consistent_absence_observations: 2
    retry:
      max_attempts: 3
      retryable_categories:
        - NETWORK
        - BACKEND_UNAVAILABLE
      backoff: exponential
      jitter: 0.20
      initial_delay: 1s
      max_delay: 30s
~~~

The schema restricts numeric and duration values. Server policy may lower recovery cycles, increase the minimum rollback-observation threshold, replace automatic rebuild with `PAUSE_FOR_RECOVERY`, or reject the request. YAML cannot request the DevKit-specific node restart procedure.

#### Compatibility Mapping

| Existing `RollbackStrategy` | Portable effective behavior | Compatibility notes |
|-------------------------------------|------------------------------|---------------------|
| `FAIL_IMMEDIATELY` | `FAIL` | Produces rollback-specific state/error, never confirmation timeout |
| `NOTIFY_ONLY` | `WAIT_FOR_REINCLUSION` | Existing listener notification remains; waiting is bounded by reinclusion and execution timeouts |
| `REBUILD_FROM_FAILED` | `RECONCILE_AND_REBUILD + AFFECTED_STEP` | Compiler/runtime upgrades scope to invalidated closure if any prepared or submitted descendant consumes the attempt |
| `REBUILD_ENTIRE_FLOW` | `RECONCILE_AND_REBUILD + INVALIDATED_CLOSURE` | The definition is re-evaluated, but still-valid attempts are retained and are never blindly executed again |

`RollbackStrategy` remains available as a legacy adapter. New documentation uses `RollbackPolicy`. The semantic correction for `REBUILD_ENTIRE_FLOW` is intentional: "entire flow" means reconcile the entire flow, not resubmit every prior business action.

#### Required Safety Invariants

1. No build, sign, or submission may occur while an execution is in `RECONCILING`.
2. An `UNKNOWN` observation never proves rollback, absence, or permission to rebuild.
3. A locally computed transaction hash and signed CBOR are persisted before the first submission call.
4. An uncertain submission is reconciled by hash before any rebuild or resubmission.
5. If identical signed CBOR remains valid — its recorded validity interval (when present) is open and its inputs are unspent — resubmitting those same bytes is preferred over rebuilding because it preserves the hash and transaction identity.
6. Rebuild is allowed only after the prior attempt is authoritatively absent and the prepared transaction cannot safely be reused because of expiry, invalid inputs, policy change, or an equivalent typed reason.
7. A transaction satisfying the effective confirmation policy is retained unless reconciliation proves that its inclusion was rolled back.
8. Rebuild scope is based on actual spent inputs and explicit output references, not ordering edges alone.
9. Every rollback observation, policy decision, state transition, retained attempt, superseded attempt, and new attempt is durable before forward execution resumes.
10. Rollback recovery cycles, transaction retry attempts, backend observation retries, and reinclusion timeouts have independent counters.
11. A rollback produces `FlowErrorCategory.ROLLBACK` and a rollback-specific diagnostic code; it is never represented as a generic timeout.
12. Terminal results retain all attempt histories, including rolled-back and superseded attempts, rather than replacing them with the latest hash.

#### Reconciliation Algorithm

1. Maintain a run-scoped monitor set for all submitted attempts that remain inside the effective monitoring horizon.
2. Observe transaction presence together with the backend chain point.
3. When rollback criteria are met, atomically append `TRANSACTION_ROLLED_BACK` and transition the execution to `RECONCILING` before invoking user callbacks.
4. Stop scheduling new submissions and acquire or renew the execution lease.
5. Reconcile every prepared, submitted, in-block, or confirmed attempt in the execution, not only the transaction that triggered detection.
6. Wait for the configured re-inclusion window when the action permits it. If the same hash reappears, record `TRANSACTION_REINCLUDED`, update its inclusion point, and resume depth tracking without creating a new attempt.
7. If it remains absent, compute the invalidated closure from persisted spent inputs, produced outputs, explicit flow-output references, and currently observed chain state.
8. Apply the effective rollback action:
   - `FAIL` records a rollback-specific failure and preserves partial-success information;
   - `WAIT_FOR_REINCLUSION` remains in recovery until re-inclusion or timeout, then returns `RECOVERY_REQUIRED` or fails according to server policy;
   - `RECONCILE_AND_REBUILD` retains valid attempts, tries identical signed-byte resubmission where safe, and otherwise creates new attempts only for the invalidated closure;
   - `PAUSE_FOR_RECOVERY` persists `RECOVERY_REQUIRED` without additional chain mutation.
9. Persist the decision and new attempt plan, then transition back to `RUNNING`.
10. Resume monitoring for retained, re-included, and rebuilt attempts until the monitoring horizon closes.

~~~mermaid
flowchart TD
    A["Observe all monitored attempts"] --> B{"Authoritative rollback?"}
    B -->|No| A
    B -->|Unknown backend state| C["Retry observation; do not rebuild"]
    C --> A
    B -->|Yes| D["Persist rollback and enter RECONCILING"]
    D --> E["Pause new submissions and reconcile every attempt"]
    E --> F{"Same hash re-included?"}
    F -->|Yes| G["Persist re-inclusion and resume depth tracking"]
    F -->|No| H["Compute invalidated dependency closure"]
    H --> I{"Effective rollback action"}
    I -->|Fail| J["Return typed rollback failure or partial result"]
    I -->|Wait| K["Wait within bounded re-inclusion window"]
    I -->|Pause| L["Persist RECOVERY_REQUIRED"]
    I -->|Rebuild| M["Reuse signed bytes or rebuild invalidated attempts"]
    M --> N["Persist recovery plan before resuming"]
    G --> A
    K --> E
    N --> A
~~~

#### Execution-Mode Semantics

| Mode | Required rollback behavior |
|------|----------------------------|
| `SEQUENTIAL` | Continue monitoring the confirmed prefix through flow termination. On rollback, retain every still-valid prefix attempt and rebuild only the invalidated closure. Never restart blindly at step zero. |
| `PIPELINED` | Pause additional submission, reconcile all submitted hashes, recompute confirmation depth, and rebuild the invalidated closure in topological order. A transaction is not skippable merely because it has a block height. |
| `BATCH` | Reconcile every prepared and submitted transaction. Retain valid on-chain attempts, mark invalid prepared descendants superseded, and obtain new signatures if rebuilding changes transaction bodies. |

If external signing or approval is required, automatic rebuild may become impossible. The engine then returns `RECOVERY_REQUIRED` with a structured request for new signatures rather than failing ambiguously or reusing invalid witnesses.

#### Rollback And Retry Interaction

| Condition | Classification | Permitted action |
|-----------|----------------|------------------|
| Build fails before a hash exists | Build failure | Typed retry may rebuild if the category and policy allow |
| Signing fails | Sign failure | Usually fail or request external action; never classify as rollback |
| Submission definitely rejected | Submit failure | Retry or rebuild only according to the typed rejection category |
| Submission outcome unknown and prepared hash exists | Uncertain submission | Reconcile the same hash; do not rebuild |
| Transaction is in a block but below target depth | Confirmation in progress | Continue monitoring |
| Previously included transaction is authoritatively absent | Rollback | Invoke rollback policy, not generic retry |
| Backend observation is unavailable or behind | Backend uncertainty | Retry observation with capped backoff; do not declare rollback |
| Rolled-back transaction reappears with the same hash | Re-inclusion | Continue the same attempt with a new inclusion history |
| Prepared transaction expired or consumes invalid inputs after rollback | Invalid prepared attempt | Mark it superseded and rebuild only if rollback policy permits |

Generic retry and rollback recovery share the scheduler and cancellation token, but they do not share counters or erase each other's history.

#### Portable State, Events, And Errors

The lifecycle model adds or uses these execution and attempt states:

- execution: `RECONCILING`, `RECOVERY_REQUIRED`, `PARTIALLY_COMPLETED`, and the existing terminal states;
- attempt: `SUBMITTING`, `SUBMITTED`, `IN_BLOCK`, `CONFIRMED`, `ROLLED_BACK`, `SUPERSEDED`, and terminal failure/cancellation states.

Required portable events include:

- `TRANSACTION_ROLLED_BACK`;
- `TRANSACTION_REINCLUDED`;
- `ROLLBACK_RECOVERY_STARTED`;
- `ROLLBACK_DECISION_RECORDED`;
- `ATTEMPT_RETAINED`;
- `ATTEMPT_SUPERSEDED`;
- `ROLLBACK_RECOVERY_COMPLETED`;
- `RECOVERY_REQUIRED`.

Each rollback event includes execution ID, step ID, attempt number, transaction hash, old and new observations, observed chain point, effective strategy, recovery cycle, and monotonically increasing event sequence. Suggested stable error codes are `TXFLOW_ROLLBACK_DETECTED`, `TXFLOW_ROLLBACK_REINCLUSION_TIMEOUT`, `TXFLOW_ROLLBACK_RECOVERY_EXHAUSTED`, and `TXFLOW_ROLLBACK_RECONCILIATION_UNKNOWN`.

Callbacks are projections of persisted events. Persistence occurs first so a listener exception or process stop cannot erase the rollback fact.

#### Worked End-To-End Rollback Scenario

Apply this execution policy to the `fund-and-forward` definition in the end-to-end example:

~~~yaml
spec:
  execution:
    mode: PIPELINED
    confirmation:
      min_confirmations: 3
      timeout: 10m
      check_interval: 3s
    rollback:
      action: RECONCILE_AND_REBUILD
      monitoring_horizon: UNTIL_FLOW_TERMINAL
      rebuild_scope: INVALIDATED_CLOSURE
      max_recovery_cycles: 3
      reinclusion_window: 2m
      minimum_consistent_absence_observations: 2
~~~

Assume `fund-staging` produced transaction `txA` and `forward-payment` produced `txB`, which spends `txA#0`:

1. The engine persists the signed CBOR, hashes, and validity intervals for `txA` and `txB` before submission.
2. Both become `IN_BLOCK`. The monitor observes `txA` disappear while the flow is still active.
3. The engine persists `TRANSACTION_ROLLED_BACK(txA)`, pauses submissions, and reconciles both hashes.
4. If `txA` is re-included with the same hash and `txB` remains valid or is also re-included, no transaction is rebuilt.
5. If both remain absent but the signed bytes are still valid (validity interval open, inputs unspent), the engine resubmits the identical CBOR in dependency order. The hashes remain `txA` and `txB`.
6. If `txA` can no longer be submitted because its input was consumed elsewhere or its validity interval closed, the engine marks its attempt superseded, rebuilds `fund-staging` as `txA2`, then rebuilds `forward-payment` as `txB2` because it consumes the changed output.
7. A preceding independent confirmed step is retained and is not executed again.
8. The final result exposes both attempt histories and the recovery events. If the recovery budget is exhausted, the result is a typed rollback failure or `RECOVERY_REQUIRED` with accurate partial-success state.

The same recovery can be resumed after a process stop:

~~~java
FlowRecoveryResult recovery = engine.recover(
        FlowRecoveryRequest.builder()
                .executionId(executionId)
                .definitionResolver(definitionResolver)
                .build()
);

if (recovery.status() == FlowExecutionStatus.RECOVERY_REQUIRED) {
    recovery.diagnostics().forEach(diagnostic ->
            log.warn("{}: {}", diagnostic.code(), diagnostic.message()));
}
~~~

#### Strict Rollback Acceptance Matrix

| Scenario | Mode/action | Required assertion |
|----------|-------------|--------------------|
| In-block transaction disappears | All modes / `FAIL` | Typed rollback failure, rollback event persisted before terminal result, no timeout error |
| Same hash returns in a new block | All modes / `WAIT_FOR_REINCLUSION` | Same attempt retained, new inclusion history recorded, no rebuild |
| Rolled-back independent step with no submitted consumers | Sequential / affected step | Only that step may be reused or rebuilt; earlier valid steps retain hashes |
| Rolled-back producer with submitted consumer | All modes / invalidated closure | Producer and actual consumers reconciled; unrelated and ordering-only steps retained |
| Earlier confirmed prefix remains on chain | Sequential / rebuild | Prefix is not built, signed, or submitted again |
| Shallow transaction remains in block | Pipelined and batch / restart | It is monitored to effective depth, not treated as already confirmed |
| Backend returns errors after prior inclusion | All modes | State becomes observation/reconciliation retry; no rollback or rebuild until authoritative evidence |
| Backend returns empty after prior inclusion (indexer lag) | All modes | An empty lookup at a chain point behind the recorded inclusion is UNKNOWN and never counts toward rollback; rollback requires the configured threshold of authoritative absences; exhausted UNKNOWN reconciliation yields RECOVERY_REQUIRED, never rebuild |
| Submission response is lost | All modes | Prepared hash is reconciled; no new transaction body is built while outcome is unknown |
| Prepared transaction remains valid after rollback | All modes / rebuild | Identical signed CBOR is resubmitted before considering a rebuild |
| Prepared transaction expired or input changed | All modes / rebuild | Old attempt becomes superseded; new attempt and affected descendants receive new hashes |
| Process stops after rollback event | All modes | Recovery resumes from persisted reconciliation state without duplicate event/action |
| Two workers recover the same execution | All modes | One lease winner; loser performs no mutation or submission |
| Recovery cycles exhausted | All modes | Typed exhausted error or `RECOVERY_REQUIRED` with full partial-success and attempt history |
| Cancellation during backoff/reconciliation | All modes | Prompt cancellation, no later scheduled submission, post-submit uncertainty remains recoverable |

### Decision 14: Introduce An Immutable `FlowEngine`

#### Existing Java API

```java
FlowExecutor executor = FlowExecutor.create(backendService)
        .withSignerRegistry(signerRegistry)
        .withListener(listener)
        .withExecutor(threadExecutor)
        .withStateStore(stateStore);
```

The fluent setters mutate the shared executor. ADR 0001 snapshots execution settings, but listener, registries, inspector, state store, and executor remain shared mutable fields read live by in-flight executions.

#### Proposed Java API

```java
FlowEngine engine = FlowEngine.builder()
        .services(FlowServices.from(backendService))
        .resources(resourceCatalog)
        .executionPolicy(executionPolicy)
        .stateStore(flowExecutionStore)
        .eventSink(eventSink)
        .taskExecutor(taskExecutor)
        .persistenceFailurePolicy(PersistenceFailurePolicy.PAUSE_FOR_RECOVERY)
        .build();
```

`FlowEngine` is immutable and thread-safe after `build()`. Every execution creates a run-scoped context containing effective settings, resource-resolution scope, listener/event sink, confirmation tracker, cancellation token, and state revision.

**Resolved (2.0.0)**: `FlowEngine` is a new public facade. `FlowExecutor` is not evolved in place; it becomes a thin delegating adapter over the engine in the same release that introduces `FlowEngine`, and is deprecated in a later, separately decided release. Evolving the 2,986-line class in place was rejected because its six execution-path variants make behavior-preserving refactoring unverifiable without the facade split (see Decision 20).

#### Proposed Execution API

```java
FlowExecutionHandle handle = engine.start(request);
FlowExecutionResult result = engine.executeSync(request);
FlowPreflightResult preflight = engine.preflight(compiledFlow, options);
FlowRecoveryResult recovery = engine.recover(recoveryRequest);
```

#### Cancellation API

```java
CancellationResult cancellation = handle.requestCancel("user request");
```

Cancellation sets `CANCEL_REQUESTED`, signals a run-scoped token, interrupts an owned task when safe, persists the request, and eventually records `CANCELLED` or the actual terminal state. It never claims that an already submitted transaction was undone. Waits are scheduled through the Decision 21 scheduler, so cancellation takes effect without waiting out a sleep interval.

#### Compatibility

- `FlowExecutor` remains available and delegates to `FlowEngine`.
- Existing executor setters remain supported during the migration window.
- New documentation recommends `FlowEngine` for server-side and concurrent applications.

### Decision 15: Define Execution Modes And Compatibility Explicitly

TxFlow remains an ordered transaction graph in the first portable version. Steps appear in deterministic topological order. `needs` may reference only an earlier step. Independent-step parallel scheduling, branches, loops, and arbitrary conditions are not introduced by this ADR.

#### Existing Java API

```java
executor.withChainingMode(ChainingMode.SEQUENTIAL);
executor.withChainingMode(ChainingMode.PIPELINED);
executor.withChainingMode(ChainingMode.BATCH);
```

#### Proposed Java API

The enum remains, but compilation produces compatibility diagnostics:

```java
ModeCompatibilityResult result = modeValidator.validate(
        compiledFlow,
        ChainingMode.BATCH
);
```

Examples of validation:

- `BATCH` rejects a step requiring an on-chain query whose value is available only after an earlier transaction confirms.
- `PIPELINED` and `BATCH` require every prior-output reference to be derivable from a locally built transaction.
- `REBUILD_FROM_FAILED` is normalized to documented full-flow behavior where pipelined dependencies require it.
- A flow with external signing or approval gates is rejected from `BATCH` unless all signatures can be obtained before submission.
- The compiled plan records partial-success semantics and the rollback strategy actually used.

#### Proposed YAML API

```yaml
execution:
  mode: BATCH
```

The compiler, not the parser, decides whether the selected mode is compatible with the complete flow.

### Decision 16: Correct Public Package Ownership

ADR 0001 states that the public flow model should not depend on executor-internal packages. The current `FlowExecutionSettings` imports `ConfirmationConfig` and `RollbackStrategy` from `com.bloxbean.cardano.client.txflow.exec`.

#### Existing API

```java
import com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.RollbackStrategy;

FlowExecutionSettings settings = FlowExecutionSettings.builder()
        .confirmationConfig(config)
        .rollbackStrategy(strategy)
        .build();
```

#### Proposed API

**Resolved (2.0.0; forwarding limits documented 2.1.0)**: public configuration types move to `txflow.config` during the pre-release window — the only time the move is cheap. Honest limits: Java enums cannot have forwarding aliases, so `RollbackStrategy` and other enums are hard-moved — a source- and binary-breaking pre-release change called out explicitly in release notes; deprecated forwarding is possible only for classes/interfaces and is used where practical. If the binary-compatibility open question resolves to "compatibility required for the current series", the move is cancelled outright rather than half-done.

```java
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
```

Executor-internal runtime types remain under `txflow.exec`.

### Decision 17: Make Documentation And Conformance Artifacts Part Of The Contract

#### Existing Documentation API Examples

The current guide mentions methods that are not present in the code: `TxFlow.Builder.withVersion(...)`, `FlowExecutor.withConfirmationTimeout(...)`, `FlowExecutor.withCheckInterval(...)`, `FlowExecutor.resumeTracking(...)`, `FlowStep.Builder.dependsOnChange(...)`, and `SelectionStrategy.CHANGE` (verified absent, all six). ADR 0001 already directs that the first three must not be added merely to satisfy stale documentation.

#### Proposed Documentation And Tooling API

Ship and test:

- versioned JSON Schemas;
- a complete YAML reference;
- minimal, payment, minting, script, chained-output, rollback, and recovery examples;
- a legacy-to-portable migration guide;
- a public validation entry point;
- an optional CLI in a later ADR;
- compile-tested Java snippets;
- golden YAML/JSON fixtures shared by tests and documentation;
- a machine-readable diagnostic-code catalog.

Suggested validation API for build tools and servers:

```java
FlowValidationResult result = TxFlowValidator.standard().validate(yaml);
```

An optional future CLI can be layered over the same API:

```text
ccl txflow validate flow.yaml
ccl txflow migrate --to v1alpha1 flow.yaml
ccl txflow canonicalize flow.yaml
```

The CLI itself is not approved by this ADR; only the reusable library primitives are approved.

### Decision 18 (new in 2.0.0): Concurrent Executions And UTXO Contention

Decision 2 makes concurrent executions of one definition a first-class capability. That immediately creates a resource conflict this ADR must answer: two concurrent executions spending from the same logical account (for example `account://treasury`) will observe the same base UTXO set, select overlapping inputs, and race — one execution fails non-deterministically at submission or, worse, during confirmation. Idempotency keys do not prevent this; they deduplicate *requests*, not *inputs*. Today `FlowUtxoSupplier` excludes only the *same run's* spent inputs, so nothing protects concurrent runs from each other.

#### Decision

1. **Spending-resource serialization by default.** The engine derives the set of logical spending resources for an execution from the compiled plan (each step's `from`/`from_ref` sources and fee payers). Executions whose spending-resource sets intersect are serialized by the engine: the second execution waits (bounded by policy `maxQueueWait`) or is rejected with `TXFLOW_RESOURCE_BUSY`, according to policy. Executions with disjoint spending resources run fully concurrently.
2. **Opt-out is explicit and policy-visible.** `FlowExecutionRequest.allowConcurrentSpending(true)` disables serialization for that execution. The policy may forbid the opt-out. The effective choice is recorded in the execution snapshot for audit.
3. **Scope is per-engine (in-process).** Cross-process coordination is delegated to the `FlowExecutionStore` lease primitive at the granularity of a spending resource; a store that cannot provide resource leases documents that multi-process deployments must serialize externally.
4. **Full UTXO reservation is future work.** A reservation protocol (per-UTXO claims with TTL) is intentionally out of scope; it requires backend cooperation to be race-free and is named as a candidate for a later ADR.

#### Resource Identity And Lock Semantics (2.1.0)

- **Canonical identity**: contention is computed over canonical *resolved* spending resources — resolved account/base addresses, or a script address plus the relevant credential — never raw alias strings. `account://treasury` and a second alias resolving to the same account must collide.
- **Coverage**: the derived resource set includes each step's `from` sources, fee payers, collateral providers, explicitly referenced UTXOs (mapped to their owning resource), and script-controlled sources named by the plan.
- **Opaque steps**: a Java-factory step's spending set is unknowable before execution. Such executions either declare their spending resources explicitly on the step or are treated conservatively — serialized against all executions of the same engine; policy chooses which.
- **Dynamically bound sources**: resources that depend on bindings are resolved at compile/bind time, before the execution enters the contention set.
- **Multi-resource acquisition**: locks are acquired in a deterministic global order (sorted canonical IDs), all-or-nothing with bounded wait, to prevent deadlock between executions with overlapping resource sets.
- **Lease safety**: cross-process resource leases (via the store's `tryAcquireResources`) carry expiry and are renewed by the active owner. Fencing is token-based (revised 2.2.0/2.4.0): each lease acquisition mints a monotonically increasing epoch, every mutation carries a composite `MutationFence` (execution-lease fence plus, when spending resources are claimed, a `ResourceLeaseFence`), and the store rejects any write whose execution- or resource-lease epoch predates the current one. The complete fence and appended events must name the mutation's target execution, and each resource lease must belong to the active execution-lease owner. Revision checks alone cannot fence a stale owner when no new writes have advanced the revision since its lease expired.

```java
FlowExecutionPolicy.builder()
        .spendingContention(SpendingContentionPolicy.SERIALIZE) // SERIALIZE | REJECT | ALLOW
        .maxQueueWait(Duration.ofMinutes(2))
        .build();
```

#### Consequences

Serialization is conservative — two executions that would have selected disjoint UTXOs from the same account are still queued. That is the correct default for a transaction orchestrator: a queued execution is delayed; a double-spend race is a failed business action. Applications that pre-partition funds across accounts get concurrency without the risk.

**Fencing limits (2.3.0)**: a store fencing token rejects stale *state writes*; it cannot prevent a partitioned stale worker that already holds signed bytes from *submitting to Cardano*, because the chain does not consult CCL's leases. Resource leases plus a pre-submit lease check shrink this window but do not close it. Truly race-free cross-process spending requires the deferred UTXO-reservation/coordinator design named in item 4 — until then, multi-process deployments must accept this residual risk or serialize spending externally.

### Decision 19 (new in 2.0.0): Deliberate Transaction Validity-Interval Policy

Rollback recovery (Decision 13, invariant 5) prefers resubmitting identical signed CBOR. That preference is only meaningful within the transaction's validity interval — and today TxFlow neither sets nor records validity intervals for orchestration purposes, so recovery cannot know whether a prepared transaction is still submittable.

#### Decision

Revised 2.1.0: recording and policy control replace the earlier universal mandate; concrete default values remain an open question.

1. The validity interval (`validFromSlot`, `validToSlot`) is always recorded in `PreparedTransaction` (Decision 11) at signing time when the built transaction carries one; the fields are nullable so intentionally unbounded legacy transactions remain representable and valid.
2. Server policy may require an interval (`requireValidityInterval()`) and may cap the window length. An engine-level `defaultValidityWindow` is available as opt-in configuration for definitions that do not set one; it is not mandated, and network-appropriate default values remain an open question.
3. Recovery and rollback reconciliation classify a prepared attempt with a recorded `validToSlot` as reusable only while the current chain tip plus a safety margin (`resubmitSafetyMargin`) stays below `validToSlot`; past that, the attempt is superseded and rebuild rules apply. An attempt without a recorded interval is governed by input validity alone.
4. Absolute-slot validation is chain-dependent and therefore happens at preflight and runtime, not at compile time; compile time checks only internal consistency. The reinclusion-window-versus-validity-window comparison (a `reinclusion_window` longer than the validity window makes re-inclusion of an expired transaction impossible) is emitted as a preflight warning.
5. YAML may set per-flow or per-step validity preferences; server policy may cap or require them:

```yaml
spec:
  execution:
    validity:
      window: 2h
      resubmit_safety_margin: 60
```

### Decision 20 (new in 2.0.0): Decompose `FlowExecutor` Internals Behind The Existing Facade

`FlowExecutor` is a single 2,986-line class containing six execution-path variants (`doExecuteSequential`, `doExecutePipelined`, `doExecuteBatch`, each with a `...WithResume` twin), per-step retry, confirmation waiting, rollback handling, UTXO capture, persistence helpers, and a private hooks abstraction. Every rollback correction in this ADR would otherwise have to be applied six times, and the durable runtime (Track C) cannot be built on this structure safely. ADR 0.2.0 proposed a large new public surface but no internal restructuring plan; this decision supplies it.

#### Decision

Extract four internal collaborators, package-private under `txflow.exec`, with `FlowExecutor` (and later `FlowEngine`) as facades over them:

```text
StepRunner            — build/sign/submit/confirm exactly one attempt of one step;
                        owns retry-with-reconciliation for that attempt
ChainingStrategy      — SEQUENTIAL / PIPELINED / BATCH scheduling over StepRunner;
                        collapses the six fresh/resume variants to three strategies
                        with a resume entry point
RollbackCoordinator   — run-scoped monitor set, observation evaluation,
                        reconciliation algorithm, invalidated-closure planning
PersistencePort       — every durable transition in one place; enforces the
                        persistence-failure policy instead of scattered try/catch-warn
```

Rules:

- The decomposition is behavior-locked: Track A's deterministic tests are written against the *current* facade first, and the extraction must keep them green.
- No public API changes result from this decision alone; it is internal.
- New rollback/recovery behavior (Track C) is implemented in the collaborators, never as further branches inside facade methods.

### Decision 21 (new in 2.0.0): Deterministic Time, Scheduling, And Chain Observation

The 0.2.0 draft required a "deterministic fake chain/clock/scheduler" as a test-harness item inside the rollback workstream. Verification showed this is not merely a test concern: production code paths call `Thread.sleep` directly at six sites, poll backends inline, and have no time seam — which is why rollback tests are race-dependent and some integration scenarios accept two outcomes. This decision promotes the seam to a first-class primitive delivered before any rollback behavior change.

#### Decision

1. Introduce `FlowScheduler` (internal SPI): `sleep(Duration, CancellationToken)`, `now()`, `schedule(...)`. The production implementation wraps the system clock and interruptible waits; the test implementation is a virtual clock.
2. Introduce a scripted `FakeChainBackend` test fixture capable of expressing: inclusion, depth growth, authoritative absence, `Optional.empty()` lag, thrown backend errors, re-inclusion, and process interruption points — as an ordered script with exactly one required outcome per scenario.
3. All confirmation, retry, rollback, and reinclusion waits go through `FlowScheduler`. Direct `Thread.sleep`/`Instant.now` calls in `txflow` production code are removed and prevented by an ArchUnit (or equivalent) test.
4. Every scenario in the Strict Rollback Acceptance Matrix has a scripted deterministic test. A test that accepts more than one outcome for the same script is a build failure by convention.

This is the first implementation deliverable of the entire plan (Track A, item A0), because every subsequent correctness fix is verified through it.

## End-To-End Portable Example

This example shows a reusable definition authored by a non-Java developer. The first transaction funds a staging account. The second transaction explicitly consumes the named output of the first transaction and pays a runtime-provided beneficiary.

### 1. TxFlow Definition YAML

```yaml
api_version: txflow.cardano-client.dev/v1alpha1
kind: TxFlow
metadata:
  name: fund-and-forward
  version: "1.0.0"
  annotations:
    owner: payments-team
    purpose: chained-payment-example

spec:
  network: preview

  parameters:
    beneficiary:
      type: address
      required: true
    fund_lovelace:
      type: integer
      default: 8000000
      minimum: 3000000
      maximum: 20000000
    forward_lovelace:
      type: integer
      default: 5000000
      minimum: 1000000
      maximum: 15000000

  execution:
    mode: PIPELINED
    confirmation:
      preset: testnet
      min_confirmations: 3
      timeout: 10m
      check_interval: 3s
    rollback:
      action: RECONCILE_AND_REBUILD
      monitoring_horizon: UNTIL_FLOW_TERMINAL
      rebuild_scope: INVALIDATED_CLOSURE
      max_recovery_cycles: 3
      reinclusion_window: 2m
      minimum_consistent_absence_observations: 2
    retry:
      max_attempts: 3
      backoff: exponential
      initial_delay: 1s
      max_delay: 20s

  steps:
    - id: fund-staging
      description: Fund the staging account
      transaction:
        tx:
          from_ref: account://treasury
          intents:
            - type: payment
              address_ref: account://staging
              amounts:
                - unit: lovelace
                  quantity: ${{ inputs.fund_lovelace }}
        context:
          fee_payer_ref: account://treasury
          signers:
            - ref: account://treasury
              scope: payment
      outputs:
        staging-funds:
          select:
            output_index: 0
          expect: exactly_one

    - id: forward-payment
      description: Consume the staging output and pay the beneficiary
      needs: [fund-staging]
      transaction:
        tx:
          from_ref: account://staging
          inputs:
            - type: collect_from
              refs:
                - flow_output:
                    step: fund-staging
                    output: staging-funds
          intents:
            - type: payment
              address: ${{ inputs.beneficiary }}
              amounts:
                - unit: lovelace
                  quantity: ${{ inputs.forward_lovelace }}
        context:
          fee_payer_ref: account://staging
          signers:
            - ref: account://staging
              scope: payment
```

Notes:

- `account://treasury` and `account://staging` are logical server-side references. No private key is present in the document.
- `needs` controls order only.
- `flow_output` explicitly binds the second transaction input to the named output of the first transaction.
- The compiler verifies `forward_lovelace <= fund_lovelace` only if such a cross-parameter constraint is added to the policy or parameter model; otherwise final transaction balancing still determines feasibility.
- The server may reject `PIPELINED` or replace it with `SEQUENTIAL` through policy.

### 2. Server Resource Configuration

```java
SignerRegistry signerRegistry = new DefaultSignerRegistry()
        .addAccount("account://treasury", treasuryAccount)
        .addAccount("account://staging", stagingAccount);

ScriptRegistry scriptRegistry = new DefaultScriptRegistry();

FlowResourceCatalog resources = FlowResourceCatalog.builder()
        .signers(signerRegistry)
        .scripts(scriptRegistry)
        .build();
```

### 3. Server Policy

```java
FlowExecutionPolicy policy = FlowExecutionPolicy.builder()
        .allowNetworks(Set.of(Network.PREVIEW))
        .allowChainingModes(Set.of(
                ChainingMode.SEQUENTIAL,
                ChainingMode.PIPELINED))
        .maxSteps(10)
        .maxRetryAttempts(3)
        .maxConfirmationTimeout(Duration.ofMinutes(15))
        .maxExecutionDuration(Duration.ofHours(1))
        .maxLovelacePerTransaction(25_000_000L)
        .allowResourcePrefixes(Set.of("account://treasury", "account://staging"))
        .spendingContention(SpendingContentionPolicy.SERIALIZE)
        .build();
```

### 4. Parse Without Side Effects

```java
TxFlowCodec codec = TxFlowCodec.standard();

FlowParseResult parsed = codec.parse(
        yaml,
        FlowParseOptions.serverDefaults()
);

if (parsed.hasErrors()) {
    parsed.getDiagnostics().forEach(diagnostic ->
            log.warn("{} {} at {}:{} path={}",
                    diagnostic.getCode(),
                    diagnostic.getMessage(),
                    diagnostic.getLine(),
                    diagnostic.getColumn(),
                    diagnostic.getDocumentPath()));
    throw new IllegalArgumentException("Invalid TxFlow document");
}

TxFlow definition = parsed.requireFlow();
```

### 5. Bind Runtime Inputs And Compile

```java
FlowBindings bindings = FlowBindings.builder()
        .put("beneficiary", "addr_test1...")
        .put("forward_lovelace", 5_000_000L)
        .build();

FlowCompilationResult compilation = TxFlowCompiler.standard().compile(
        FlowCompilationRequest.builder()
                .definition(definition)
                .bindings(bindings)
                .resources(resources)
                .policy(policy)
                .build()
);

if (compilation.hasErrors()) {
    throw new FlowCompilationException(compilation.getDiagnostics());
}

CompiledTxFlow compiled = compilation.requireCompiledFlow();
EffectiveFlowExecutionSettings effectiveSettings = compiled.getEffectiveSettings();
```

Compilation confirms that:

- all required parameters are present and correctly typed;
- parameter values satisfy constraints;
- the network is allowed;
- each step contains exactly one transaction plan;
- step IDs and `needs` references are valid;
- `staging-funds` exists and is referenced by a later step;
- referenced accounts exist and can provide payment signatures and addresses;
- `PIPELINED` can resolve the previous output from the locally built first transaction;
- requested retries, confirmation timeouts, and validity windows are within server policy;
- all transaction intents are structurally valid.

### 6. Optional Preflight

```java
FlowPreflightResult preflight = engine.preflight(
        compiled,
        PreflightOptions.builder()
                .checkProtocolParameters(true)
                .checkInitialFunds(true)
                .build()
);

if (!preflight.isExecutable()) {
    throw new FlowPreflightException(preflight.getDiagnostics());
}
```

Preflight may become stale because chain state changes. It improves diagnostics but does not replace execution-time validation.

### 7. Start A Distinct Execution

```java
FlowExecutionId executionId = FlowExecutionId.random();

FlowExecutionRequest request = FlowExecutionRequest.builder()
        .compiledFlow(compiled)
        .executionId(executionId)
        .idempotencyKey("customer-42:invoice-8842")
        .correlationId("invoice-8842")
        .build();

FlowExecutionHandle handle = engine.start(request);
```

### 8. Observe Portable Events

```java
handle.events().subscribe(event -> {
    log.info("execution={} sequence={} type={} step={} attempt={}",
            event.executionId(),
            event.sequence(),
            event.type(),
            event.stepId(),
            event.attempt());
});
```

Representative events:

```text
FLOW_ACCEPTED
FLOW_COMPILED
FLOW_STARTED
STEP_BUILDING               fund-staging attempt=1
TRANSACTION_SIGNED          fund-staging attempt=1 txHash=abc...
TRANSACTION_SUBMITTING      fund-staging attempt=1 txHash=abc...
TRANSACTION_SUBMITTED       fund-staging attempt=1 txHash=abc...
OUTPUT_BOUND                fund-staging output=staging-funds -> abc...#0
STEP_BUILDING               forward-payment attempt=1
TRANSACTION_SIGNED          forward-payment attempt=1 txHash=def...
TRANSACTION_SUBMITTED       forward-payment attempt=1 txHash=def...
TRANSACTION_IN_BLOCK        fund-staging attempt=1
TRANSACTION_IN_BLOCK        forward-payment attempt=1
TRANSACTION_CONFIRMED       fund-staging attempt=1
TRANSACTION_CONFIRMED       forward-payment attempt=1
FLOW_COMPLETED
```

### 9. Retrieve A Portable Result

```java
FlowExecutionResult result = handle.await();

if (result.getStatus() == FlowExecutionStatus.COMPLETED) {
    result.getSteps().forEach(step ->
            log.info("{} -> {}", step.getStepId(), step.getTransactionHash()));
} else {
    result.getErrors().forEach(error ->
            log.error("{} {}", error.code(), error.message()));
}
```

Example JSON representation:

```json
{
  "execution_id": "01JZZY3P9J3R0Q6NGS30TKS8NF",
  "definition": {
    "id": "fund-and-forward",
    "version": "1.0.0",
    "fingerprint": "sha256:..."
  },
  "status": "COMPLETED",
  "steps": [
    {
      "step_id": "fund-staging",
      "attempt": 1,
      "status": "CONFIRMED",
      "transaction_hash": "abc...",
      "outputs": {
        "staging-funds": "abc...#0"
      }
    },
    {
      "step_id": "forward-payment",
      "attempt": 1,
      "status": "CONFIRMED",
      "transaction_hash": "def..."
    }
  ],
  "errors": []
}
```

### 10. Recover After A Process Restart

```java
List<FlowExecutionRef> recoverable = executionStore.findRecoverable(
        RecoveryQuery.defaults()
);

for (FlowExecutionRef ref : recoverable) {
    FlowRecoveryResult recovery = engine.recover(
            FlowRecoveryRequest.builder()
                    .executionId(ref.executionId())
                    .definitionResolver(definitionRepository::resolve)
                    .build()
    );

    log.info("Recovery {} -> {}", ref.executionId(), recovery.getAction());
}
```

If the last stored state is `SUBMITTING` with a known signed transaction hash, recovery queries the hash. It does not immediately rebuild the step.

## Proposed Module And Package Layout

This ADR does not require a Gradle-module split in the first implementation. The following package boundaries should be established even if all types remain in `txflow` initially:

```text
com.bloxbean.cardano.client.txflow          (existing types keep this package)
    TxFlow
    FlowStep

com.bloxbean.cardano.client.txflow.model    (new types only)
    ParameterSpec
    FlowBindings
    FlowOutputRef
    FlowOutputSelector

com.bloxbean.cardano.client.txflow.codec
    TxFlowCodec
    FlowParseOptions
    FlowParseResult
    FlowDiagnostic

com.bloxbean.cardano.client.txflow.compile
    TxFlowCompiler
    FlowCompilationRequest
    FlowCompilationResult
    CompiledTxFlow

com.bloxbean.cardano.client.txflow.config
    FlowExecutionSettings
    ConfirmationConfig
    RollbackStrategy
    FlowExecutionPolicy

com.bloxbean.cardano.client.txflow.resource
    FlowResourceCatalog
    ResourceRef
    ResourceDescriptor
    ResourceCapability

com.bloxbean.cardano.client.txflow.exec
    FlowEngine
    FlowExecutionRequest
    FlowExecutionHandle
    FlowExecutionResult
    FlowEvent
    FlowError
    (internal: StepRunner, ChainingStrategy, RollbackCoordinator,
     PersistencePort, FlowScheduler)

com.bloxbean.cardano.client.txflow.store
    FlowExecutionStore
    FlowExecutionSnapshot
    StepExecutionSnapshot
    ExecutionLease

com.bloxbean.cardano.client.txflow.recovery
    FlowRecoveryRequest
    FlowRecoveryResult
```

Existing public types (`TxFlow`, `FlowStep`, and the current result types) keep their present packages for the current series; the `.model` grouping above applies to newly introduced types, and relocation of existing types is deferred to a major release with an explicit compatibility plan (clarified 2.1.0 — Java has no type aliases, so class moves cannot be made transparent). The `txflow.config` move in Decision 16 is the one deliberate pre-release exception.

A later ADR may propose separate `txflow-model`, `txflow-codec`, and `txflow-runtime` Gradle modules if dependency weight or non-runtime tooling justifies it.

## Compatibility And Migration Strategy

### Legacy Documents

- Continue accepting the current `version: "1.0"` format.
- Apply current flow-level `context` semantics from ADR 0001.
- Decode `flow.variables` as definition defaults.
- Decode `depends_on` using legacy pending-UTXO visibility.
- Emit warnings for implicit input semantics, unused `filter`, empty transaction content, and other ambiguous constructs.
- Reject constructs that currently cause silent loss when writing (Track A makes the legacy writer fail on loss).

### Portable (v1alpha1) Documents

- Require `api_version` and `kind`.
- Use `spec.execution`, flat steps, typed parameters, `needs`, named outputs, and explicit flow-output references.
- Reject unknown fields by default except within a documented `extensions` map.
- Reject Java-only transaction factories because they cannot originate from a portable document.

### Existing Java API

| Existing API | Migration target | Compatibility action |
|--------------|------------------|----------------------|
| `TxFlow.fromYaml(yaml)` | `TxFlowCodec.parse(...)` | Keep as legacy convenience delegate |
| `flow.toYaml()` | `TxFlowCodec.write(...)` | Keep, but fail on semantic loss |
| `TxFlow.addVariable(...)` | `addParameter(...)` plus `FlowBindings` | Keep for definition-local legacy defaults |
| `FlowStep.dependsOn(...)` | `needs(...)` plus explicit `FlowOutputRef` | Keep legacy semantics and warn in portable compilation |
| `StepDependency.filter(...Predicate...)` | `FlowOutputSelector` / `UtxoFilterSpec` | Keep for Java-only flows; reject portable encoding |
| `FlowExecutor.create(...).with...` | immutable `FlowEngine.builder()` | Keep through adapter/deprecation window |
| `executor.execute(flow)` | `engine.start(FlowExecutionRequest)` | Generate execution ID for legacy call |
| `executor.resume(flow, previousResult)` | `engine.recover(FlowRecoveryRequest)` | Retain current best-effort result resume separately |
| `FlowStateStore` | `FlowExecutionStore` | Provide adapter where semantics permit |
| `FlowListener` | `FlowEventSink` / event stream | Provide event-to-listener adapter |
| `FlowResult` | `FlowExecutionResult` | Provide legacy projection |
| `withSignerRegistry(...)` | `FlowResourceCatalog` | Keep and add short-term `withScriptRegistry(...)` |

### Behavior Changes Shipped Without A New API (Track A)

These are deliberate breaking-by-honesty changes to the existing API, approved as part of this ADR because the previous behavior was silent data loss or misreporting:

1. `flow.toYaml()` throws on non-portable content instead of silently emitting incomplete YAML.
2. Rollback under `FAIL_IMMEDIATELY` (and exhausted `NOTIFY_ONLY`) produces a rollback-typed failure and persisted `ROLLED_BACK` state instead of `ConfirmationTimeoutException`.
3. `fromYaml` validates the version and rejects duplicate keys and multiple documents.
4. A required dependency that cannot be resolved fails the step instead of logging and continuing.
5. Sequential rollback restart reconciles the confirmed prefix instead of re-running from step zero.
6. Automatic rollback rebuild from transaction absence requires backend-adapter-declared absence authority (`TransactionObservationCapabilities`); without it, a suspected rollback produces listener notification plus a typed reconciliation-uncertain failure instead of an automatic rebuild (2.2.0; ownership moved to the adapter at 2.3.0).

### Deprecation Timing

No removal schedule is decided in this ADR. Removal requires:

- at least one release with both APIs available;
- a published migration guide;
- compatibility fixtures for legacy YAML;
- explicit release notes;
- a separate accepted deprecation/removal decision.

## Security Considerations

Server-facing use requires defenses beyond structural YAML parsing:

- enforce maximum input bytes, nesting, aliases, collection sizes, steps, intents, and metadata entries;
- reject duplicate YAML keys and multiple documents;
- prohibit arbitrary type tags and object polymorphism outside registered discriminators;
- bind runtime values after parsing, never by editing raw YAML text;
- redact sensitive bindings and resource-resolution details;
- ensure logical references are authorized for the caller/tenant;
- validate Cardano network consistency for addresses and resource descriptors;
- cap amounts, fees, deposits, execution duration, retries, confirmation depth, validity windows, and polling rates;
- allow or deny minting, certificates, governance actions, script execution, and metadata through policy;
- validate output destinations and asset policies when an application requires allowlists;
- avoid embedding private keys, seed phrases, bearer tokens, or full secret resolver results in snapshots;
- make `txInspector`-like hooks observational or explicitly capable of vetoing, and prevent accidental secret logging;
- audit requested settings, effective policy decisions, resource references, transaction hashes, and recovery actions.

## Observability Requirements

The event and state models should make the following available without parsing log text:

- execution ID, definition ID/version/fingerprint, correlation ID, and idempotency key hash;
- requested and effective execution settings;
- step ID and attempt number;
- build, sign, submit, inclusion, confirmation, rollback, cancellation, and recovery timestamps;
- transaction hash as soon as it is locally known;
- confirmation depth and block information;
- retry decision and next delay;
- rollback strategy actually applied;
- spending-resource serialization decisions (queued, rejected, opted out);
- diagnostic and error codes;
- event sequence and snapshot revision;
- policy rejection or cap decisions;
- resource reference names without secret contents.

CCL should expose events and leave metrics/tracing library integration to adapters. A future OpenTelemetry adapter can consume `FlowEvent` without adding OpenTelemetry dependencies to the core model.

## Failure And Partial-Success Semantics

Multiple Cardano transactions are not atomic. The portable result must distinguish:

- no transaction submitted;
- some transactions submitted but not found on chain;
- some transactions in block;
- a confirmed prefix followed by failure;
- independent confirmed steps with another failed step;
- rollback after earlier inclusion;
- cancellation requested after submission;
- recovery required because backend state is uncertain.

`PARTIALLY_COMPLETED` means at least one transaction reached an irreversible-for-the-configured-policy success state while the overall flow did not complete. It does not imply ledger atomicity or finality beyond the configured confirmation policy.

## Implementation Plan

Version 2.0.0 replaces the interleaved eight-phase plan of 0.2.0 with three tracks. Each track is separately mergeable and independently valuable, and a later track never blocks an earlier one from shipping. The rationale: the defects in Track A are exploitable by current users today and none of them require the portable contract — entangling them with codec/compiler/engine work would delay safety fixes behind naming decisions.

Cross-track dependencies (made explicit at 2.1.0 — the tracks are ordered, not independent):

- Track A depends on nothing and ships alone.
- Track B depends on Track A's serialization honesty (A3) for its compatibility decoder, and its compiler assumes Track A behavior.
- Track C depends on Track A's deterministic harness (A0) and behavior-locked tests (the Decision 20 extraction gate), and on Track B for the compiler, execution identity, bindings, and resource catalog — C1/C2 consume B2/B3 types.

Mapping from the 0.2.0 plan: old Phase 1 + RB Phases 0–1 → Track A; old Phases 2–4 → Track B; old Phases 5–6 + RB Phases 2–3 → Track C; old Phase 7 and RB Phases 4–5 are distributed into B3/C3 and the exit criteria.

Sequencing (no durations — the team estimates each track when it is scheduled):

```text
Track A (correctness, existing API)   A0 → A1 → A2 → A3 ∥ A4
Track B (portable contract, additive) B1 → B2 → B3          after A3
Track C (durable runtime)             C1 → C2 → C3          after A0/A tests and B2/B3
```

Every task lands with `./gradlew :txflow:test` green plus its listed new tests. All builds use Java 17.

### Track A — Correctness And Safety Hardening (existing API, no new public types)

**A0. Deterministic test foundation** (Decision 21; prerequisite for every later item)

1. A0.1 Extract the `FlowScheduler` clock/scheduler seam; route all six `Thread.sleep` sites and time reads through it.
2. A0.2 Build the scripted `FakeChainBackend` fixture (inclusion, depth growth, authoritative absence, empty-result lag, thrown errors, re-inclusion, interruption points).
3. A0.3 Rewrite the rollback/confirmation tests as scripted observations with exactly one required outcome per scenario; add the no-direct-sleep architecture test.

Exit: every current rollback strategy has a deterministic test; no test accepts two outcomes for one script.

**A1. Rollback safety on the current API** (fixes RB-01, RB-03, RB-05, RB-07, NEW-03)

1. A1.1 Replace the empty-`Optional` confirmation result with an internal typed outcome (CONFIRMED / ROLLED_BACK / TIMEOUT / CANCELLED).
2. A1.2 `FAIL_IMMEDIATELY` and exhausted `NOTIFY_ONLY` produce a rollback-typed failure and call `persistTransactionRolledBack` — never `ConfirmationTimeoutException`.
3. A1.3 Sequential rollback restart reconciles via the (depth-enforcing) still-confirmed check and pre-populates the valid prefix; never re-runs step zero blindly.
4. A1.4 Enforce `minConfirmations` in `findStillConfirmedSteps` and the no-tracker fallback (record chain tip, compute depth).
5. A1.5 Treat every empty transaction lookup as `UNKNOWN` unless the *backend adapter* declares absence authority (revised 2.3.0). Track A introduces a minimal SPI — `TransactionObservationCapabilities` with `supportsAuthoritativeAbsence()` and `supportsMempoolObservation()` — which a backend supplier implements, plus a server-side wrapper for deployments that know their backend's index is consistent (`ObservationCapabilities.withAuthoritativeAbsence(supplier)`). Authority is deliberately NOT expressible through `ConfirmationConfig`, `FlowExecutionSettings`, or YAML: those are flow/request-scoped, and a flow author must not be able to vouch for a backend's transaction-index consistency. Even with adapter authority, an absence counts only when the backend tip is at or beyond the recorded inclusion height at observation time, and N consecutive qualifying absences (default 2) are required before declaring rollback — the tip check filters lag but cannot by itself prove index consistency. Without adapter authority, or on ambiguous empties, the tracker keeps polling (listeners may still be notified of a *suspected* rollback) and, when the reconciliation budget is exhausted, produces a typed reconciliation-uncertain failure; automatic rebuild never triggers from undeclared or ambiguous absence. The deterministic fake chain (A0) and the Yaci DevKit adapter implement the capability directly, so rollback-rebuild scenarios remain fully exercised. The SPI and wrapper are Track A's only public additions.

Exit: the strict-matrix rows for timeout-vs-rollback, prefix retention, shallow-skip, and indexer lag pass deterministically on the current facade.

**A2. Retry hardening** (fixes RB-04 partial, RB-08, RB-10, NEW-04)

1. A2.1 Reorder classification: `Error` guard first, unwrap cause chains, message heuristics as fallback only.
2. A2.2 Saturating backoff arithmetic, build-time bounds validation, jitter (default 0.20).
3. A2.3 Known-hash pre-check in `executeStepWithRetry`: if a hash was computed and submission was attempted, query the hash before any retry decision. Found → switch to confirmation tracking. Not found or lookup failed → the outcome is uncertain, and a negative lookup does not prove the transaction was not accepted (it may sit in a mempool): resubmit the identical signed bytes where safe (idempotent — same hash), otherwise fail with a typed uncertain-submission error; never rebuild a new transaction body while the outcome is unknown (revised 2.1.0). (The full reconciliation protocol is Track C; this is the cheap majority that prevents duplicate submissions.)

Exit: wrapped permanent errors are not retried; extreme configs produce typed failures, not crashes; a lost submission response never causes an unchecked rebuild.

**A3. Serialization honesty** (fixes GAP-02, GAP-03 minimum, NEW-01)

1. A3.1 `toYaml()` throws on non-portable content (factory, multi-transaction plan, predicate filter); the FILTER-to-match-all degradation becomes impossible.
2. A3.2 `fromYaml()` calls `validateVersion`; enable strict duplicate-key detection; set SnakeYAML `LoaderOptions` limits; reject multiple documents.
3. A3.3 Remove (or reject with a diagnostic) the dead `DependencyEntry.filter` YAML field.
4. A3.4 Round-trip property tests, including second-round-trip idempotence for templated flows.

**A4. Execution hygiene** (fixes GAP-05 interim, GAP-08, GAP-09, NEW-02, NEW-05, GAP-14)

1. A4.1 Internal execution ID per run, threaded through the execution context and logs for correlation — `FlowResult` is unchanged in Track A, since exposing the ID on results would be an additive public API change; it surfaces on the new result types in Track C (revised 2.2.0). The same-definition duplicate guard and the id-keyed registry/state-store behavior are deliberately retained: re-keying by execution ID moves to Track C1, where it lands together with Decision 18 spending-resource serialization, so concurrent same-definition executions are never enabled before contention control exists. Track A's only public additions are the `TransactionObservationCapabilities` SPI and its wrapper (A1.5).
2. A4.2 Bind flow variables into a per-run copy of the step's `TxPlan`; the shared plan is never mutated.
3. A4.3 Add `FlowExecutor.withScriptRegistry(...)`; call the 3-arg `compose(...)` at all three sites; add a script-reference flow integration test.
4. A4.4 Fix `FlowUtxoSupplier.findPendingUtxo`: apply the declared selection strategy; replace the empty catch with logging (and failure for required dependencies).
5. A4.5 Documentation sweep: remove the six phantom APIs; document the real `resume(flow, previousResult)` semantics; correct `TxFlow.validate()` Javadoc.

**Track A exit criteria**: full txflow unit suite green; deterministic rollback matrix green; no scenario in which a confirmed transaction is rebuilt, a rollback is reported as timeout, a lost submission response causes an unchecked duplicate submission, or serialization silently loses semantics.

### Track B — Portable Contract (additive public API)

**B1. Codec, envelope, typed parameters** (Decisions 1, 4)

1. `TxFlowCodec`, `FlowParseOptions/Result`, `FlowDiagnostic` with stable codes; `v1alpha1` envelope; document-type detection.
2. `ParameterSpec`, `FlowBindings`, node-level typed binding; syntax isolation between `${x}` (legacy) and `${{ inputs.x }}` (portable).
3. Published JSON Schema resource + conformance fixtures; legacy compatibility decoder with migration warnings.
4. Canonical YAML and JSON writers.

Tests: schema conformance, line/column diagnostics, type-preserving binding, interpolation restrictions, parser resource limits, fuzz/malformed inputs, legacy fixtures.

**B2. Explicit data flow, compiler, identity** (Decisions 2, 3, 5, 7, 15, 16)

1. `needs`, named `outputs`, declarative selectors, `FlowOutputRef` in QuickTx input/reference intents (`TxInputRef` sealed).
2. `TxFlowCompiler` with the ten-stage validation pipeline producing immutable, fingerprinted `CompiledTxFlow`.
3. Public `FlowExecutionRequest` with execution ID and idempotency key — (namespace, key) uniqueness requiring equal definition and canonical request fingerprints to match, typed conflict otherwise, per Decision 2 (aligned 2.2.0).
4. Config types move to `txflow.config` with deprecated forwarders.

Tests: needs-without-consumption, explicit consumption, selector cardinality, pipelined/batch local-output resolution, compiler determinism and fingerprint stability, Java/YAML compiled-plan equivalence.

**B3. Resources and policy** (Decisions 8, 9)

1. `FlowResourceCatalog`, descriptors, capability preflight.
2. `FlowExecutionPolicy` with requested-to-effective evaluation, warning-on-cap, `strictSettings()` flag; rollback-policy limits; validity-window caps (Decision 19).
3. Optional backend-aware preflight/dry-run.

Tests: missing/unauthorized resources, capability mismatch, network mismatch, policy rejection and capping, sensitive-data redaction, all execution modes.

### Track C — Durable Server Runtime

**C1. Engine and internal decomposition** (Decisions 10, 14, 20)

1. Extract `StepRunner`, `ChainingStrategy`, `RollbackCoordinator`, `PersistencePort` behind the existing facade, behavior-locked by Track A's deterministic tests.
2. Immutable `FlowEngine`; portable lifecycle enums, `FlowExecutionResult`, `FlowError` with typed categories (replacing message heuristics as the primary classifier), sequenced `FlowEvent` stream.
3. Run-scoped cancellation token honored inside waits via `FlowScheduler`; `FlowExecutor`/`FlowListener`/`FlowResult` adapters.
4. Execution-ID keying of the active-run guard, registry, and store together with spending-resource serialization (Decision 18): concurrent same-definition executions are enabled here and only here, replacing the legacy duplicate-flow-ID guard retained through Track A (revised 2.1.0).

Tests: concurrent execution IDs on one definition, idempotency collisions, immutable configuration under concurrency, cancellation at every phase, partial-success semantics, ordered event sequences, retry decisions per phase, spending-contention queue/reject/opt-out behavior.

**C2. Durable state, recovery, flow-scoped rollback** (Decisions 11, 13, 19; RB Phases 2–3 of 0.2.0)

1. `FlowExecutionStore` with revisioned snapshots, fenced event append, atomic idempotency claims (`createOrGet`), and execution/resource leases; in-memory reference implementation; database implementation requirements documented without a database dependency.
2. Persist signed CBOR/hash/validity interval before submission; `SUBMITTING` → backend call → `SUBMITTED`; persist `IN_BLOCK` and depth transitions; persistence-failure policy (engine default `PAUSE_FOR_RECOVERY`; legacy facade keeps warn-and-continue).
3. Flow-scoped monitor set through the effective horizon; invalidated closure from persisted spent inputs and explicit refs; identical-CBOR resubmission gated by the recorded validity interval; `engine.recover(...)` implementing the reconciliation rules.
4. Journal compaction rule for terminal executions.

Tests: simulated process stop at every durable boundary; accepted-but-unrecorded submission; mempool/in-block/confirmed/rolled-back recovery; lease contention; revision conflicts; stale lease-fence rejection; concurrent idempotency-claim races; fingerprint mismatch; recovery after policy/resource changes; no blind rebuild while a prepared hash may have been submitted; same-hash re-inclusion and closure-rebuild histories; expired-validity supersession.

**C3. Conformance and adoption** (Decision 17; RB Phases 4–5 of 0.2.0)

1. Golden fixtures (legacy + v1alpha1) shared by tests and docs; rollback/retry conformance fixtures for non-Java clients.
2. Migration guide, diagnostic-code catalog, compile-tested examples, server integration reference architecture.
3. Full Yaci DevKit strict integration matrix: all strategies and modes, shallow and deep rollback, same-hash re-inclusion, changed-body rebuild, backend restart, indexer lag, cancellation, recovery exhaustion — with explicit snapshot setup/cleanup, and failing (never skipping) when the intended rollback was not observed.

## Test And Verification Strategy

The existing unit and integration tests remain valuable. New testing adds these layers:

1. **Deterministic harness first** (Decision 21): virtual clock + scripted chain; one required outcome per script; architecture test bans direct sleeps/clock reads.
2. **Codec tests**: schema, malformed inputs, canonical output, resource limits, duplicate keys.
3. **Semantic round trips**: Java -> YAML -> Java and YAML -> Java -> YAML -> Java compare compiled plans, not formatting; second round trip idempotent.
4. **Cross-version fixtures**: immutable legacy fixtures and v1alpha1 fixtures.
5. **Compiler tests**: all diagnostics, parameter binding, resources, policies, and modes.
6. **Property tests**: generated definitions within supported bounds.
7. **Concurrency tests**: shared engine, different settings, resources, definitions; spending-contention serialization.
8. **Lifecycle tests**: every transition and partial-success state.
9. **Recovery tests**: crash or interruption between every pair of durable transitions.
10. **Devnet integration tests**: sequential, pipelined, batch, rollback, restart, and confirmation behavior using Yaci DevKit.
11. **Compatibility tests**: current public APIs delegate correctly and emit expected warnings.
12. **Strict rollback matrix**: every scenario in Decision 13 with deterministic observations, exact event/state assertions, and no permissive alternate outcomes.
13. **Retry safety tests**: uncertain submission, wrapped permanent errors, backend uncertainty, saturating backoff, jitter bounds, and cancellation during scheduled delay.

All builds and tests for this work use Java 17.

## Acceptance Criteria

This ADR is considered implemented only when:

- every accepted portable definition either round-trips semantically or fails encoding with a diagnostic;
- a Java-only transaction factory is never silently emitted as incomplete YAML;
- a step cannot silently discard extra transactions from a `TxPlan`;
- a FILTER dependency can never silently degrade to match-all;
- `needs` and explicit prior-output consumption have distinct semantics;
- required output references fail deterministically when unresolved;
- runtime values are typed and never substituted into raw YAML structure, and no execution mutates a shared `TxPlan`;
- versioned JSON Schema and conformance fixtures ship with the module;
- all parser/compiler errors have stable codes and document paths;
- the same definition can execute concurrently using distinct execution IDs, and intersecting spending resources are serialized or rejected per policy;
- policy constrains all YAML-requested execution settings and transaction capabilities;
- signer, policy, and script references support preflight validation, and script references execute through `FlowExecutor`/`FlowEngine`;
- results distinguish built, submitted, in-block, confirmed, failed, rolled-back, superseded, and cancelled attempts;
- prepared transaction hashes, signed CBOR, and validity intervals are durably recorded before submission;
- recovery reconciles uncertain submission before rebuilding;
- rollback is represented by a typed state/error and is never converted to confirmation timeout, and rolled-back state is always persisted;
- rollback monitoring covers all eligible attempts through the effective horizon;
- an unknown backend observation never authorizes resubmission or rebuild; rollback from absence requires adapter-declared absence authority plus the configured threshold of chain-point-qualified absence observations, and exhausted uncertain reconciliation ends in `RECOVERY_REQUIRED`, not rebuild;
- valid confirmed prefixes are retained and only the invalidated dependency closure is rebuilt;
- same-hash re-inclusion retains the existing attempt and records its new inclusion history;
- rollback recovery, ordinary retry, backend observation, and reinclusion budgets remain independent;
- strict deterministic and Yaci rollback matrices pass without accepting alternate non-rollback outcomes;
- persistence transitions use revisions plus lease-epoch fencing, and idempotency claims are atomic at the store (`createOrGet`);
- cancellation is persisted, takes effect without waiting out sleep intervals, and does not claim to reverse submitted transactions;
- legacy compatibility behavior is covered by immutable fixtures;
- public documentation contains no non-existent APIs;
- the complete txflow unit suite and relevant Java 17 integration suites pass.

## Consequences

### Positive

- Non-Java authors receive a documented and machine-validatable contract.
- Server implementers can validate, authorize, compile, execute, observe, and recover flows using common CCL primitives.
- Prior-output consumption becomes explicit and auditable.
- Runtime configuration and authorization are separated.
- Execution results accurately represent Cardano's partial-success and rollback realities.
- Crash recovery becomes deterministic enough for production orchestration.
- Concurrent executions have a defined, auditable contention answer instead of a silent double-spend race.
- Every safety fix in Track A benefits current users without waiting for the new APIs.
- Existing Java-first flows remain supported.

### Costs And Tradeoffs

- The public surface grows substantially.
- A compatibility decoder and adapters must be maintained.
- QuickTx input-reference models need an extension for flow-output references.
- Deep immutability requires defensive copying or new immutable models around mutable `TxPlan` types.
- Durable recovery increases state-model and test complexity.
- Spending-resource serialization is conservative and can queue executions that would not actually have conflicted.
- The v1alpha1 schema must be stabilized through real consumer feedback before a stable version is declared.
- Policy cannot guarantee that preflight remains valid after chain state changes.

### Risks

- Attempting all tracks in one change would create a high-review-risk rewrite; the track structure exists precisely to prevent this.
- Prematurely declaring v1alpha1 stable could freeze weak naming or incomplete transaction intent coverage.
- Maintaining two APIs indefinitely would increase complexity; deprecation must eventually be resolved.
- A generic expression language could accidentally turn TxFlow into an unsafe workflow engine. This ADR intentionally limits expressions to typed references and string interpolation.
- The Decision 20 decomposition is a refactor of safety-critical code; it is gated on the deterministic test suite specifically to control this risk.

## Alternatives Considered

### Keep The Current YAML And Only Add More Fields

Rejected as the long-term direction. It does not solve identity, typed binding, compilation, output-reference semantics, structured errors, policy, or recovery.

### Treat `depends_on` As Guaranteed Consumption Without Changing The Model

Rejected. The underlying transaction may use coin selection, explicit inputs, script inputs, or reference inputs. Ordering and transaction input binding must be represented separately.

### Embed Transaction Hash Variables From Earlier Steps

Rejected as the primary API. It exposes low-level dynamic variables, remains index-heavy, and does not provide selector cardinality or input-kind semantics. A typed `FlowOutputRef` can compile to a transaction hash and index internally.

### Make The Server Interpret TxFlow Without New CCL APIs

Rejected. Each server would duplicate format detection, binding, policy, diagnostics, resource resolution, lifecycle state, and recovery behavior. Those semantics belong with the CCL transaction engine.

### Turn TxFlow Into A General Workflow Engine

Rejected. General workflow engines already exist, and unrestricted conditions, scripting, and loops would complicate security and determinism. TxFlow should orchestrate a bounded ordered graph of Cardano transactions.

### Replace YAML With JSON Only

Rejected. YAML is an explicit usability requirement. JSON should be supported as an equivalent transport and schema-validation target, not as a replacement.

### Store Only Transaction Hashes For Recovery

Rejected. A hash alone cannot always reconstruct expected outputs, spent inputs, signed transaction bytes, validity intervals, attempt policy, or whether submission was attempted. The prepared transaction record is required for robust reconciliation.

### Continue With Mutable `FlowExecutor` Only

Rejected for the server-facing API. Per-execution settings solved one race, but shared mutation remains possible for registries, listeners, inspectors, and state stores. An immutable engine makes configuration ownership clear.

### Name The Portable Schema v2alpha1 (0.2.0 Proposal)

Rejected. The legacy format was never declared stable, so the portable format is the first versioned public contract; naming it v2 would imply a stable v1 existed and inflate the perceived migration burden.

### Fix Correctness Only Inside The New Engine

Rejected. Current users hit the Track A defects today through the existing API; safety fixes must not be gated on adoption of a new API surface.

## Resolved Decisions (Formerly Open Questions)

Resolutions proposed by the independent review and approved by the project maintainer (2.0.0 review; 2.1.0 revisions noted). Each is recorded inline at the owning decision; all remain revisable while the ADR is `Proposed`.

| # | Question (0.2.0) | Resolution |
|---|------------------|------------|
| 1–2 | Schema namespace and version name | `txflow.cardano-client.dev/v1alpha1` — Kubernetes group/version form, product-scoped host (Decision 1; form revised 2.1.0, host revised 2.5.0) |
| 3 | `FlowEngine` vs evolved `FlowExecutor` | New `FlowEngine` facade; `FlowExecutor` becomes a delegating adapter (Decision 14) |
| 4 | `TxFlow` vs new `FlowDefinition` name | Keep `TxFlow` (Decision 2) |
| 5 | Config package location | Move to `txflow.config` now (Decision 16) |
| 6 | `TxInputRef` sealed or open | Sealed for the pre-release series; widening/unsealing is source-breaking for exhaustive switches and accepted pre-release (Decision 5; rationale corrected 2.1.0) |
| 7 | Output binding location | On `FlowStep`; intent-level may follow (Decision 5) |
| 9 | Policy capping behavior | Numeric ceilings: record + warn + continue; semantic replacements (mode, rollback action, horizon): reject or require explicit caller acknowledgement; `strictSettings()` rejects any difference (Decision 9; revised 2.2.0) |
| 11 | Signed CBOR storage | Inline by default; external/encrypted blob reference permitted (Decision 11) |
| 13 | Idempotency scope | (namespace, key) unique; a match requires equal definition AND canonical execution-request fingerprints — any mismatch is a typed conflict (Decision 2; revised 2.1.0) |
| 16 | Monitoring horizon in server mode | `UNTIL_FLOW_TERMINAL` default; legacy horizon allowed with warning (Decision 13) |
| 17 | Absence observations minimum | Authoritative absence requires the adapter-declared `AUTHORITATIVE_ABSENCE` capability AND a chain point at/beyond the recorded inclusion; two consecutive such observations by default (policy may lower to one); ambiguous empties never count, and exhausted uncertainty ends in `RECOVERY_REQUIRED` (Decision 13; revised 2.2.0) |
| 18 | `WAIT_FOR_REINCLUSION` expiry default | `RECOVERY_REQUIRED`; policy may harden to terminal failure (Decision 13) |
| 19 | `REBUILD_ENTIRE_FLOW` reinterpretation | Accepted; legacy name deprecated in documentation immediately (Decision 13) |
| — | Idempotency collision behavior (new) | Atomic (namespace, key) claim at the store: equal definition AND execution-request fingerprints → return the existing handle/result; any mismatch → typed `TXFLOW_IDEMPOTENCY_CONFLICT` (Decision 2; revised 2.1.0–2.2.0) |
| — | Concurrent spending (new) | Serialize by default, explicit policy-visible opt-out (Decision 18) |

## Open Questions For Review

1. Which parts of `UtxoFilterSpec` are safe and stable enough to expose in the TxFlow output-selector schema? (Proposed starting subset: address, asset unit, minimum amount, datum-hash presence.)
2. What is the required binary compatibility policy during the current pre-release series?
3. Which backend states can be portably distinguished across Blockfrost, Koios, Ogmios/Yaci, and custom suppliers — specifically, which adapters can declare `AUTHORITATIVE_ABSENCE`, and which can distinguish mempool absence from chain absence strongly enough to support identical-CBOR resubmission?
4. Should independent steps ever execute concurrently in a future version, or should ordering remain fully explicit?
5. Which current legacy-format ambiguities should be warnings versus immediate errors in the compatibility decoder?
6. Decision 18 defers per-UTXO reservation. Is per-engine spending-resource serialization plus store-level resource leases sufficient for the first server deployments, or must the reservation ADR land in the same release train?
7. What are the right engine defaults for `defaultValidityWindow` and `resubmitSafetyMargin` (Decision 19) across mainnet and testnets with different slot characteristics?

## Review Checklist

Reviewers should explicitly confirm or request changes for:

- [ ] TxFlow scope as an ordered Cardano transaction graph
- [ ] verification-status section, corrected findings, and newly found defects (NEW-01 … NEW-05)
- [ ] versioned document envelope and `v1alpha1` naming
- [ ] definition/execution identity separation and idempotency semantics
- [ ] typed parameter and binding model, including syntax isolation
- [ ] `needs` versus explicit flow-output references
- [ ] lossless portability rules (including FILTER rejection)
- [ ] compiler and diagnostics API
- [ ] resource catalog and capability model
- [ ] execution policy ownership and capping behavior
- [ ] immutable `FlowEngine` direction and `FlowExecutor` adapter plan
- [ ] lifecycle, result, event, and error models
- [ ] durable store, lease, journal-compaction, and recovery model
- [ ] retry and uncertain-submission semantics
- [ ] rollback terms, monitoring horizon, and backend-uncertainty boundary
- [ ] rollback compatibility mapping and invalidated-closure rebuild semantics
- [ ] rollback portable policy, observation, event, state, and error APIs
- [ ] strict rollback/retry acceptance matrix and Yaci DevKit verification plan
- [ ] concurrent-execution spending contention (Decision 18)
- [ ] validity-interval policy (Decision 19)
- [ ] `FlowExecutor` decomposition plan (Decision 20)
- [ ] deterministic time/chain seam (Decision 21)
- [ ] Track A breaking-by-honesty behavior changes
- [ ] package ownership changes
- [ ] legacy compatibility and deprecation strategy
- [ ] three-track implementation plan and sequencing
- [ ] Java 17 requirement
