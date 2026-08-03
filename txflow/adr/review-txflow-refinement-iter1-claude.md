# Review: TxFlow Refinement Iteration 1 — Branch & Readiness Review

- **Branch**: `feat/txflow_refinement_iter1` (merge base `a7e8e106`)
- **Date**: 2026-07-17
- **Reviewer**: Claude (multi-agent review: ADR compliance, portable contract/codec, execution engine/durable runtime, RDBMS store extension, QuickTx changes, documentation)
- **Scope**: 6 commits (`34a7f45a`..`76cb0991`), 236 files, ~32k insertions, plus the uncommitted working-tree changes (docs restructure, `TxFlowCodec` portable-semantics validation)
- **References**: ADR 0001, ADR 0002 (v2.6.5), ADR 0003, quicktx ADRs (policy-references-for-minting, script-registry-for-attachment-references, unified-tx-and-deposit-resolution)

## Verdict

High-quality engineering with a sound architecture, close to preview-ready. **Two blockers** (one in the uncommitted working-tree diff) and a cluster of state-classification and codec-strictness bugs should be fixed before the durable runtime becomes the recommended path. The durability core — fenced write-ahead journaling before submission, conservative recovery, epoch-fenced leases — is correct where it matters most.

### Verification performed

| Suite | Result |
|---|---|
| `:txflow:test` | 542 tests, 0 failures, 1 skipped |
| `:quicktx:test` | 350 tests, 0 failures |
| `:txflow-extensions:txflow-store-rdbms:test` | 59 tests, 0 failures |
| `:txflow-extensions:txflow-store-rdbms:integrationTest` | 39 tests, 0 failures — includes 29 PostgreSQL contract tests against a real Testcontainers PostgreSQL and the H2 child-JVM kill/restart tests |

The RDBMS integration suite had never been executed in this worktree before this review; it now runs green.

---

## 1. Blockers

### BLK-1 — Uncommitted `TxFlowCodec` change breaks `FlowEngine.start()` for Java-authored flows

`validatePortableSemantics` (`txflow/.../codec/TxFlowCodec.java:313`) now rejects `StepDependency`, step-level retry policies, and TxPlan variables in the portable writer. But `TxFlowCompiler.compile()` explicitly supports those constructs — it copies them into the compiled plan (`compile/TxFlowCompiler.java:93,111`) and then computes the plan fingerprint by calling this same writer (`TxFlowCompiler.java:116`). The `FlowEncodingException` is swallowed by `catch (Exception)` and surfaces as `TXFLOW_COMPILATION_FAILED` with the migration advice "or write the legacy schema" — nonsensical when the caller was trying to *execute*, not serialize.

**Impact**: any Java-built definition using `dependsOn(...)`, `withRetryPolicy(...)`, or a TxPlan with variables can no longer be compiled or started via the new engine. Lines 93 and 111 of `TxFlowCompiler` become dead code.

**Context**: the instinct is right. Before this change those fields were silently dropped from both the portable output *and* the fingerprint, so two semantically different plans fingerprinted identically and `FlowEngine` idempotency dedup could match the wrong execution — arguably a worse bug.

**Fix**: make the rejection a deliberate, first-class compiler diagnostic (like the existing `TXFLOW_NON_PORTABLE_FACTORY`, `TxFlowCompiler.java:106-108`) with an execution-appropriate message, or decouple fingerprinting from the portable writer. Add compiler tests for definitions carrying these constructs — none exist today, which is why the suite stayed green.

**Do not commit the working-tree diff as-is.**

### BLK-2 — Uncertain-submission reconciliation failure lands as terminal `FAILED` (non-retryable) instead of `RECOVERY_REQUIRED`

`FlowExecutor.reconcileUncertainSubmission()` (`exec/FlowExecutor.java:1946-1972`): both failure branches wrap the error in a plain `FlowExecutionException`, never `ReconciliationUncertainException`. `FlowEngine.run()` (`exec/FlowEngine.java:612-668`) derives `RECOVERY_REQUIRED` only from `FlowStoreException`/`ReconciliationUncertainException`/pause-rollback causes; `UncertainSubmissionException` is checked nowhere in the engine.

**Scenario**: submission throws `ApiRuntimeException` (network blip) → `UncertainSubmissionException` → the reconciler's `chainDataSupplier.getTransactionInfo()` also throws → step fails → engine durably persists `FAILED, retryable=false` while the transaction may still land on-chain. A caller re-running the business operation as a new execution can double-submit the intent.

**Related**:
- `FlowRecoveryCoordinator.recover` (`recovery/FlowRecoveryCoordinator.java:97-106`) handles the identical situation correctly (`RECOVERY_REQUIRED`) — live path and recovery path diverge.
- `StepRunner.run()` (`exec/StepRunner.java:61`) returns the raw uncertain failure when policy declines `RECONCILE_THEN_RETRY` — also lands as plain `FAILED`.
- BATCH phase-2 submission exceptions bypass uncertain-submission reconciliation entirely (no `hasSubmissionApiFailure` handling on that path).

**Fix**: throw/wrap `ReconciliationUncertainException(txHash, cause)` from `reconcileUncertainSubmission`'s catch and from the resubmission-rejected branch (rejection of identical bytes — e.g. `BadInputsUTxO` — is often evidence the original landed). Wire BATCH phase 2 into the same handling.

---

## 2. High-priority findings

### H-1 — `UnknownFieldPolicy.REJECT` not enforced inside `spec.execution` (codec)

`Spec.execution` is bound as raw `JsonNode` (`TxFlowCodec.java:661`), so Jackson `FAIL_ON_UNKNOWN_PROPERTIES` never inspects that subtree and `collectUnknownFields` runs only under `WARN` (`:176-178`). Probe-confirmed: under `FlowParseOptions.serverDefaults()` (REJECT), `execution: {mode: SEQUENTIAL, bogus_setting: 42}` and the typo `confirmation: {min_confirmatoins: 3}` parse with **zero diagnostics**; the typo silently yields default `minConfirmations=10`. Contradicts the `FlowParseOptions` javadoc and lets typos silently change confirmation/rollback/retry semantics.

### H-2 — Silent scalar coercion turns garbage into dangerous values (codec)

`parseExecution` uses `asInt()`/`asDouble()`/`asLong()` without type checks:
- `min_confirmations: notanumber` → **0 confirmations** (`TxFlowCodec.java:392`), probe-confirmed.
- Same pattern: `jitter` → 0.0 (`:440`), `max_recovery_cycles` (`:422`), `resubmit_safety_margin` (`:447`), `minimum_consistent_absence_observations` (`:427-428`).
- Inconsistent null handling: `mode` uses `hasNonNull`, confirmation/retry fields use `has()`, so `min_confirmations: null` also becomes 0.

Durations are safe (they route through `DurationCodec`, which errors), making the numeric holes easy to miss.

### H-3 — Cancelled execution can end `RECOVERY_REQUIRED` instead of `CANCELLED` (engine)

`FlowEngine.run()` (`FlowEngine.java:578-594`): when `SpendingResourceCoordinator.acquire()` returns a cancelled acquisition (zero locks held), the engine still acquires durable resource leases for every identity before checking `cancelled.get()`. If another process holds a lease, `TXFLOW_RESOURCE_LEASE_CONFLICT` is thrown and classified as `recoveryRequired=true` (`:654`) — durably persisting `RECOVERY_REQUIRED` for a flow the user cancelled that submitted nothing. The acquisition's `cancelled()` flag is never consulted. Move the cancellation check before resource-lease acquisition.

### H-4 — Cross-process lease contention misclassified as "recovery required" (engine)

Same catch path as H-3, non-cancelled case: a legitimate `TXFLOW_RESOURCE_LEASE_CONFLICT`/`TXFLOW_LEASE_CONFLICT` on acquire is a busy/contention condition, but every `FlowStoreException` maps uniformly to `RECOVERY_REQUIRED` (`FlowEngine.java:651-674`). In-process contention correctly maps to `TXFLOW_RESOURCE_BUSY` (`:659-660`). Carve the lease-conflict codes out of the recovery classification.

### H-5 — `FlowResult` integrity inconsistent across chaining modes (engine)

- **BATCH** marks steps successful at *build* time (`FlowExecutor.java:2367-2370`). If phase-2 submission fails, the terminal `FlowResult` contains "successful" steps that never left the process; the engine's `anyMatch(FlowStepResult::isSuccessful)` heuristic (`FlowEngine.java:613-617`) then persists `PARTIALLY_COMPLETED` for a flow with zero on-chain effects.
- **SEQUENTIAL/PIPELINED** generic catch blocks (`FlowExecutor.java:1007-1016`, `:1330-1339`) build a *fresh* `FlowResult.Builder` (also resetting `startedAt`), discarding already-confirmed step results — the inverse error; `PARTIALLY_COMPLETED` becomes undetectable. BATCH's catch (`:2518-2525`) correctly reuses the builder.

The ~150-line restart/cleanup scaffolding triplicated across the three `doExecute*` methods is where both bugs crept in; it is the next dedup target.

### H-6 — PostgreSQL sub-microsecond timestamps can trigger false `TXFLOW_STORE_CORRUPT` (rdbms)

`RdbmsFlowExecutionStore.sameDatabaseTimestamp` (`RdbmsFlowExecutionStore.java:673-677`) cross-checks payload vs column instants by **truncating** both to microseconds; values are written at full nano precision (`:493,:515,:579`) and pgjdbc **rounds half-up** to microseconds. An instant with sub-microsecond remainder ≥500ns stores as `...457` while the payload truncates to `...456` — every subsequent read of that execution throws `TXFLOW_STORE_CORRUPT` permanently. H2 truncates (covered by `RdbmsCorruptionTest`), but no PostgreSQL test uses sub-microsecond instants, so the suite cannot catch it. Fix: truncate instants to micros before encoding/binding (or round consistently on both sides); add a PG test with `plusNanos(789)`.

### H-7 — Documentation: compile-breaking sample (docs)

`docs/content/preview/txflow/retry-execution-results.mdx:107-108,125-126` documents `FlowExecutor.withConfirmationTimeout(Duration)` / `withCheckInterval(Duration)` — neither exists (verified: `FlowExecutor` exposes exactly 11 public `with*` methods). Correct API: `withConfirmationConfig(ConfirmationConfig.builder().timeout(...).checkInterval(...).build())`. Pre-existing on master, but this branch rewrote the page and should fix it.

---

## 3. Medium findings

### M-1 — H2 first-run migration not crash-atomic; bricks startup permanently (rdbms)

H2 DDL implicitly commits, so a JVM crash between the first `CREATE TABLE` and `insertHistory` (`RdbmsSchemaManager.java:218-234`, script execution `:395-401`) leaves `txflow_*` tables with no history row. The next start hits `!historyExists && containsTxFlowObjects` (`:167-171`) → `TXFLOW_SCHEMA_INCOMPATIBLE` forever, without manual `DROP`s. Fail-closed but a permanent self-inflicted outage for the out-of-the-box embedded profile. PostgreSQL DDL is transactional; only H2 affected. No test covers interrupted migration.

### M-2 — Writer emits documents that violate its own published schema (codec)

Settings with only a legacy `rollbackStrategy` serialize as `action: NOTIFY` (`TxFlowCodec.java:471-475`); the published schema enum (`txflow/src/main/resources/schema/txflow-v1alpha1.schema.json:70`) has no `NOTIFY`. Only this codec's own parser accepts the output; any conformant external validator rejects a document this library produced. Probe-confirmed.

### M-3 — Remaining silent drops and non-canonical round-trip; fingerprint blind spots (codec)

The portable writer still silently drops: `RetryPolicy.retryOnTimeout`/`retryOnNetworkError` (writes 5 of 7 fields, `TxFlowCodec.java:477-484`; `retryOnNetworkError=false` round-trips as `true`), legacy `ConfirmationConfig` fields including `requiredAuthoritativeAbsences` (`:455-460`), and flow-level `description` (probe-confirmed; step description *is* preserved). Probe-confirmed `write→parse→write` is not stable (strategy-only rollback expands to a 6-field policy on the second write); the test `parsesAndCanonicallyRoundTripsPortableYamlAndJson` never compares serializations. All of these are also fingerprint blind spots: `CompiledTxFlow.getFingerprint()` is sha256 of this writer's output, so plans differing only in these fields fingerprint identically and idempotency matching treats different requests as the same execution.

### M-4 — Portable `preset:` silently ignored except `testnet` (codec)

`TxFlowCodec.java:381-390` special-cases only `"testnet"`; the schema allows any string and `warnUnknown` whitelists `preset`. Probe-confirmed: `preset: devnet` parses cleanly and yields 10/5s/30m instead of devnet's 3/1s/5m — no diagnostic. Legacy path supports `defaults|devnet|testnet|quick`.

### M-5 — Store envelope check defeats codec's v1 backward-compat read path (rdbms)

`verifyPayloadEnvelope` (`RdbmsFlowExecutionStore.java:607-613`) requires `data_version == FlowStoreCodec.CURRENT_FORMAT_VERSION` exactly, while `FlowStoreCodec.readEnvelope` documents that bumping the version must never remove the v1 read path. When v2 ships, all v1 rows fail with `TXFLOW_STORE_CODEC_UNSUPPORTED_VERSION` even though the codec can decode them. Delegate the supported-version set to the codec.

### M-6 — `PAUSE_FOR_RECOVERY` degrades to auto-rebuild on the legacy executor (codec/engine seam)

Parsing maps any non-FAIL/non-NOTIFY action — including `PAUSE_FOR_RECOVERY` — to `RollbackStrategy.REBUILD_FROM_FAILED` (`TxFlowCodec.java:404-407`), and the legacy strategy wins on the legacy executor. A document asking to pause for human recovery automatically rebuilds — a semantics inversion. `NOTIFY_ONLY` is the safer legacy analog.

### M-7 — `DurableLeaseGuard` renewal chain can die silently (engine)

`scheduleRenewal()` (`exec/DurableLeaseGuard.java:91-104`): if the maintenance executor rejects at fire time (e.g. shut down), the `RejectedExecutionException` lands on the JDK shared delayer thread and is swallowed — `renewalFailure` never set, `hasFailed()`/`checkHealthy()` stay green, lease silently expires. Fencing saves correctness (next fenced append fails `TXFLOW_LEASE_EXPIRED`) but detection is late and the surfaced error misleading. `catch (RuntimeException)` also misses `Error`. Wrap the dispatched task so any dispatch/execution failure lands in `renewalFailure`. Note: `DurableLeaseGuardTest` currently has a single test.

### M-8 — Malformed input misdiagnosed as "Document is not a TxFlow" (codec)

`parse()` gates on `detect()` (`TxFlowCodec.java:124`), which swallows all exceptions to `UNKNOWN` (`:95-97`). Probe-confirmed: malformed YAML, multi-document input, and duplicate JSON keys all produce only `TXFLOW_DOCUMENT_KIND` with no line/column — the `TXFLOW_PARSE_ERROR`-with-location path (`:532-542`) and the multi-document message (`:556`) are unreachable from `parse()` for these inputs. Related: `detect()` hardcodes `FlowParseOptions.serverDefaults()` (`:88`), ignoring caller options (limits mismatch + every document parsed twice).

---

## 4. Low / minor findings

**Codec**
- WARN-mode known-fields list whitelists step-level `retry` (`TxFlowCodec.java:607`) though `PortableStep` never binds it — under WARN/IGNORE a step retry block is silently dropped with no warning.
- `containsLegacyExpression` regex lacks `DOTALL` (`:524`) — `${...}` after a newline in a multi-line string escapes detection (probe-confirmed). Converse check `source.contains("${{")` (`:135`) scans comments → possible false positives.
- `write()` legacy path throws raw `IllegalStateException` from `FlowDocument.fromFlow` (`FlowDocument.java:308-324`) despite the documented `FlowEncodingException` contract.
- Compiled fingerprint uses `writerWithDefaultPrettyPrinter()` with no embedded format-version marker (`TxFlowCompiler.java:116-119`) — a Jackson upgrade could silently invalidate stored fingerprints (contrast `ExecutionRequestFingerprinter`, which embeds `format`/`version`).
- Legacy path has no `maxSteps` cap; legacy `variables` uses `HashMap` → byte-unstable output ordering.
- No equals/hashCode on model classes (`TxFlow`, `FlowStep`, `TransactionTemplate`, ...) — structural round-trip assertions impossible, which is how M-3 survived.

**Engine**
- `DurableExecutionPersistence.onRolledBack` (`:119-147`) ignores the rolled-back `transactionHash` and always resolves the *latest* attempt — a late rollback signal for attempt N can mark attempt N+1 `ROLLED_BACK` in the journal (audit accuracy only).
- `FlowEngine.createHandle()` performs `store.createOrGet` and task dispatch inside `synchronized(activeExecutions)` (nested in `synchronized(idempotencyClaims)` for keyed requests) (`FlowEngine.java:375-434`, `:202-225`) — a slow store serializes all `start()` calls; with a caller-runs executor the whole flow runs under both monitors. No lock cycle (throughput hazard, not deadlock).
- Non-durable idempotency claims are never evicted; at `maxInMemoryIdempotencyClaims` (10k) new keyed work is rejected with `TXFLOW_IDEMPOTENCY_CAPACITY_EXCEEDED` — no TTL; long-lived non-durable engines hard-stop.
- Durable mode journals a fenced append per confirmation-depth change per step (`DurableExecutionPersistence.onConfirmationDepth`) — one store round-trip per block per pending tx; consider thresholding.
- `FlowEngine.run()` builds its executor facade without `withScheduler(...)` (`FlowEngine.java:600-604`) — engine tests cannot virtualize legacy polling time.
- Dead code: `RollbackCoordinator.hasActualConsumer`; `FlowHorizonMonitor.verify(List,List,...)` overload is test-only.
- Each `doExecute*` mints an internal UUID executionId distinct from `request.getExecutionId()` — confusing logs.
- Pre-existing (unchanged on this branch): user `txInspector` never fires in BATCH mode (`buildStepOnly` registers before `buildAndSign()`, which doesn't invoke inspectors).

**RDBMS store**
- `inTransaction` catches `RuntimeException` but not `Error` (`RdbmsFlowExecutionStore.java:854-909`, `RdbmsSchemaManager.java:523-576`) — OOM leaks an un-rolled-back connection.
- Global lease-epoch singleton row (`SELECT ... FOR UPDATE`, `:679-705`) serializes all lease traffic store-wide (deliberate; documented in code).
- H2 driver is a `runtime` dependency of the published artifact — PostgreSQL-only consumers pull H2 transitively (CVE surface); consider optional/feature variant.
- Anchor connection pins one pool connection forever in DataSource mode with H2 dialect (`Builder.build()`, `:1168-1173`).
- Isolation level assumed, never set/validated; H2 lock timeouts (`HYT00`/50200) not classified retryable in `mapSqlFailure` (`:968-986`); `readEvents` takes `FOR UPDATE` (reader/appender serialization); naive `;`-split in `splitStatements` fragile for future migrations; `MAX_PAYLOAD_CHARACTERS` measures chars against a byte constant (codec re-checks bytes, still fail-closed); `DriverManagerDataSource.setLogWriter/setLoginTimeout` mutate JVM-global state.

**QuickTx** (no blocking defects)
- `resolvePolicyRef`/`resolveScript` mutate the caller's intent during build — cross-thread `Tx` reuse could race (javadoc note suffices).
- `DefaultScriptRegistry.addScript` doesn't trim refs while the builder trims before resolve — whitespace-registered refs can never resolve.
- `UtxoRefDeserializer` silently ignores unknown fields; future `UtxoRef` fields must be added manually (add a warning comment).
- 3-arg `compose(TxPlan, SignerRegistry, ScriptRegistry)` overload untested directly.
- `inputRef`/`utxoRef`/`flowOutputRef` `$defs` in the quicktx schema are defined but unreferenced — `flow_output` shape not schema-enforced (known, tracked in issue #636). No JSON-schema validator anywhere in the build — schema drift vs `TransactionDocument` undetected in CI.

**Docs** (otherwise release-ready: coordinates, versions, `_meta.js`, links, and defaults all verified consistent)
- `durable-runtime.mdx:274` and `txflow/README.md:272` write `FlowExecutionPolicy.requireValidityInterval(true)` in prose as if static; it is a `Builder` method.
- `txflow/README.md` uses `${cclVersion}` in Gradle snippets without defining it.

---

## 5. What is done well (verified)

1. **Write-ahead journaling is correct.** `onPrepared`/`onSubmitting` perform fenced appends of the SIGNED/SUBMITTING attempt (full signed CBOR, SHA-256, tx hash, spent inputs, validity bounds) *before* the network call in all three chaining modes (via the tx inspector, which `QuickTxBuilder.complete()` invokes after `buildAndSign()` and before submission). A crash cannot cause submission of *different* bytes — at worst hash-idempotent resubmission of identical bytes. Persistence failure aborts before I/O (fail-closed). Covered by `signedBytesAndHashAreFencedAndPersistedBeforeSubmittingState`.
2. **Conservative recovery philosophy**: observe-by-hash first; resubmit only digest- and recomputed-hash-verified identical bytes; refuse past the validity window minus `resubmitSafetyMargin`; rollback-from-absence requires backend-declared `AUTHORITATIVE_ABSENCE` observed the configured number of times — ambiguity becomes `RECOVERY_REQUIRED`, never automatic rebuild.
3. **Delegation, not duplication**: `FlowEngine` builds a fresh per-execution legacy `FlowExecutor`, keeping one implementation of the three chaining modes and four rollback strategies while neutralizing the mutable-executor problem.
4. **RDBMS store quality**: zero SQL-injection surface (all identifiers compile-time literals, all values prepared-statement-bound); one JDBC transaction per operation with explicit commit/rollback-*uncertainty* semantics (fault-injection tested); atomic lease acquisition via epoch-singleton lock (no check-then-act; epoch monotonicity guaranteed); sorted resource-lock ordering; exceptionally deep schema validation (catalog matching, collation, index usability, precision, checksummed migrations, newer-schema rejection, PG advisory-lock migration serialization); sanitized SQLExceptions; genuine child-JVM `destroyForcibly()` restart test.
5. **Fencing/split-brain**: epoch-checked `requireCurrent` + expiry validation means a usurped runner's appends fail `TXFLOW_STALE_FENCE`; since every submission is preceded by a fenced append, a stale runner cannot journal-then-submit a *new* transaction after takeover.
6. **Codec architecture**: stable diagnostic-code catalog mechanically enforced complete by test; hardened YAML loading (Jackson YAMLFactory — no arbitrary object instantiation; duplicate-key rejection at both layers; alias/nesting/size limits); thorough defensive copying; `ExecutionRequestFingerprinter` is a model canonical fingerprint (versioned, sorted, secret-free).
7. **QuickTx refs**: clean end-to-end design (mutual exclusion, hash verification on both resolution paths, signer dedup via `resolvedSignerRefKeys`, re-entrant builds, portable round-trip preserving refs without derived script material); deliberate backward compatibility (default interface method, additive overloads, sealed hierarchy on the Java 17 baseline); exceptional negative-path test coverage. ADRs match the implementation precisely.
8. **`FlowStoreTextPolicy` / `boundedClaimKey`**: NUL rejection (PG `text`), UTF-8 *byte* limits, surrogate validation; internal claim keys hashed into a versioned domain-tagged space so they cannot alias or consume user key limits (tested).

---

## 6. Readiness assessment

### Scope reality vs. ADR framing

**"Durable execution" today means durable audit + safe attempt-level reconciliation, not resumable orchestration.** After a crash, `start()` on a non-terminal stored execution returns a completed `RECOVERY_REQUIRED` handle (`FlowEngine.handleForStoredSnapshot`) and `recover()` reconciles exactly one attempt, leaving the snapshot `RUNNING` with no runner attached. No code path resumes the *remaining steps* of a durable execution under its original executionId. Legitimate milestone boundary — but docs should state it as prominently as ADR 0002 implies the opposite.

### State-classification trust

BLK-2, H-3, H-4, H-5 share one theme: terminal states (`FAILED`, `RECOVERY_REQUIRED`, `PARTIALLY_COMPLETED`, `CANCELLED`) do not yet reliably mean what operators will assume. For a durability product this is the credibility surface; treat the cluster as one pre-release workstream.

### ADR bookkeeping to reconcile

- ADR 0002 is marked **Implemented** (v2.6.5) while carrying 7 open questions, including default validity-window values; its "Resolved Decisions" note still says resolutions "remain revisable while the ADR is Proposed".
- The `txflow.config` package move (Decision 16) is contingent on the unresolved binary-compatibility open question; the ADR's own gap note admits shipped `FlowExecutionSettings` still imports config types from `txflow.exec`, contrary to ADR 0001.
- ADR 0001 (canonical `context.chaining_mode`, `RollbackStrategy` vocabulary) vs ADR 0002 (canonical `execution.mode`, `RollbackPolicy`) describe different canonical YAML for overlapping settings — the legacy decoder and portable schema coexist by design, but the ADRs should cross-reference this explicitly.
- Accepted residual risk, correctly documented, not a defect: fencing cannot prevent a partitioned stale worker that already holds signed bytes from submitting to Cardano (identical bytes only — hash-idempotent).

### Test coverage: strong overall, targeted gaps

Existing coverage is genuinely good (990 green including a reusable `FlowExecutionStoreContract` exercised against in-memory, H2, and real PostgreSQL; golden store fixtures; fault-injection JDBC tests; executor-rejection durability; sensitive-binding redaction). Priority gaps:

1. `DurableLeaseGuardTest` has one test — renewal failure surfacing, `fence()` after failed renewal, `close()` idempotency all untested (M-7).
2. No engine-level split-brain test (lease expiry mid-flow → second engine claims → first engine's late fenced append → `RECOVERY_REQUIRED`, no duplicate new submission).
3. No test drives `reconcileUncertainSubmission` observation failure / identical-resubmission rejection and asserts the resulting engine state (BLK-2).
4. No compiler tests for legacy-construct definitions (BLK-1's blindness).
5. No canonical-stability test (`write(parse(write(f))) == write(f)`) and no schema-conformance validation of writer output — either would have caught M-2/M-3 mechanically. No JSON-schema validator in the build at all.
6. BATCH: no phase-2 submission-failure test, no `PARTIALLY_COMPLETED` misreport test, no inspector-fires test.
7. `FlowParseOptions` limits entirely untested (alias bombs, nesting depth, `maxDocumentBytes`, `maxSteps`).
8. PG timestamp precision (H-6); interrupted H2 migration (M-1); non-UTC JVM timezone round-trip.

### Documentation

Near release-ready: 60+ API references verified against source; artifact coordinates/versions consistent with `gradle.properties` (0.8.0-pre5-SNAPSHOT); `_meta.js` 1:1 with pages; no cross-page contradictions (defaults verified against code); legacy vs new-engine pages cleanly separated with compatibility banners; honest about limitations (schema breadth caveat with tracked issue, single-tx-per-step). One hard error (H-7), two prose nits.

---

## 7. Recommended order of attack

1. **BLK-1**: rework the uncommitted codec validation as a compiler diagnostic (or decouple the fingerprint from the portable writer); add compiler tests for `dependsOn`/step-retry/TxPlan-variable inputs. Do not commit the current diff as-is.
2. **BLK-2**: map uncertain-submission reconciliation failures to `ReconciliationUncertainException` → `RECOVERY_REQUIRED` on the live path (incl. `StepRunner` policy-declined branch and BATCH phase 2).
3. **H-3/H-4**: check cancellation before durable resource-lease acquisition; classify lease-conflict store codes as busy, not recovery.
4. **H-5**: align `FlowResult` builder handling across the three modes so `PARTIALLY_COMPLETED` means "some transaction actually confirmed".
5. **H-1/H-2/M-4/M-2**: codec strictness — enforce REJECT inside `spec.execution`, strict numeric parsing, honor or reject all `preset` values, stop emitting `NOTIFY`.
6. **H-6/M-1**: normalize timestamps to microseconds before persisting; make H2 first-run migration adoptable/atomic; add the two missing tests.
7. **H-7 + doc nits**; state the attempt-level-recovery scope prominently in DURABLE_RUNTIME.md and durable-runtime.mdx.
8. ADR housekeeping: reconcile Implemented status vs open questions; resolve the `txflow.config` package-move contingency before the contract is declared stable.
9. Test-gap backlog (§6) as follow-up issues; consider a CI schema-conformance test for both published schemas.

---

# Re-review after fix pass — 2026-07-18

A remediation pass (applied by Codex, uncommitted at re-review time; ~4,800 insertions across 62 files) was verified finding-by-finding by a second multi-agent review, including empirical probes against the review's original failure scenarios.

**Verdict: both blockers and every High and Medium finding are fixed, correctly and with targeted tests. No correctness regressions were found in the fix diff.** Remaining open items are the Low/deliberate tail plus a handful of new Low observations below.

### Verification performed (re-run)

| Suite | Result |
|---|---|
| `:txflow:test` | 654 tests, 0 failures (up from 542) |
| `:quicktx:test` | 351 tests, 0 failures |
| `:txflow-extensions:txflow-store-rdbms:test` | 68 tests, 0 failures |
| `:txflow-extensions:txflow-store-rdbms:integrationTest` | 40 tests, 0 failures — includes the new PostgreSQL sub-microsecond timestamp test against a real container |

## Finding-by-finding verdicts

| Finding | Verdict | How it was fixed |
|---|---|---|
| BLK-1 | **FIXED** | The review's recommended option (a): a shared `PortableFlowValidator` runs in both the compiler (up front) and the portable writer; `dependsOn`/step-retry/TxPlan-variables get first-class diagnostics (`TXFLOW_NON_PORTABLE_DEPENDENCY`/`_STEP_RETRY`/`_TXPLAN_VARIABLES`) with execution-appropriate migration hints. Fingerprint now version-prefixed (`txflow-compiled:v1\n`, `TxFlowCompiler.java:47`). New 58-test `TxFlowCompilerTest` covers every legacy construct and asserts `TXFLOW_COMPILATION_FAILED` is absent. |
| BLK-2 | **FIXED** | All four branches (reconciler chain-query failure, identical-resubmission rejection, StepRunner policy-declined, BATCH phase 2 — which now converts `ApiRuntimeException` → `UncertainSubmissionException` and reconciles) produce `ReconciliationUncertainException` → engine persists `RECOVERY_REQUIRED`, retryable, attempt left `SUBMITTING`. Engine-state test asserts handle + stored snapshot. |
| H-1 | **FIXED** | `collectUnknownFields` now runs under REJECT with ERROR severity and covers `execution` + all subtrees + step outputs. Both original probes (`bogus_setting`, `min_confirmatoins`) now error. |
| H-2 | **FIXED** | New `validateExecutionShape` pass: integral checks, finite-number jitter, string-typed durations pre-parsed via `DurationCodec`; `hasNonNull` uniform; no `asInt()` coercion remains. |
| H-3 | **FIXED** | `completeCancellationIfRequested` consults `cancelled.get() \|\| acquisition.cancelled()` before each per-identity durable lease acquisition; test asserts terminal `CANCELLED` + `never().acquireResourceLease(...)`. |
| H-4 | **FIXED** | `TXFLOW_LEASE_CONFLICT`/`TXFLOW_RESOURCE_LEASE_CONFLICT` → `FAILED`/`TXFLOW_RESOURCE_BUSY`/retryable; verified both stores throw these codes only at acquire time, so the carve-out cannot swallow a mid-flight fence loss. |
| H-5 | **FIXED** | `FlowStepResult` rework (`submissionPendingAt`, `failureAfterSubmission(At)`, `cancelledAt`) + `observableStepResults` projection: confirmed→successful, submitted-unconfirmed→`IN_PROGRESS`, build-only→absent; all three modes' generic catches reuse the accumulated builder; rolled-back attempts re-projected to FAILED. `PARTIALLY_COMPLETED` now means "some transaction actually confirmed". |
| H-6 | **FIXED** | Timestamps truncated to microseconds before encoding *and* binding at every persist site; lease expiries rounded *up* so positive durations stay in the future; normalized values returned to callers. New PG test with `.123456789Z`/rollover instants runs green against real PostgreSQL. |
| H-7 | **FIXED** | Sample rewritten with `ConfirmationConfig.builder()`; phantom methods gone repo-wide. |
| M-1 | **FIXED** | H2+MIGRATE-only repair path: adopts an interrupted migration only when tables form an exact creation-order prefix, history matches spec exactly, and every table is empty — all checks precede any DROP; history insert moved last so the commit marker is atomic. 4 new tests incl. rejection paths. |
| M-2 | **FIXED** | `NOTIFY` can no longer be emitted; strategy-only settings rejected at write (`TXFLOW_NON_PORTABLE_ROLLBACK_STRATEGY`); schema enum mechanically cross-checked against `RollbackAction` by test. |
| M-3 | **FIXED** | Non-default `retryOnTimeout`/`retryOnNetworkError` and legacy confirmation fields rejected; flow `description` now encoded (schema updated); canonical `write→parse→write` string equality asserted in tests. |
| M-4 | **FIXED** | All four presets honored with values validated against `ConfirmationConfig`; unknown preset → `TXFLOW_CONFIRMATION_PRESET_UNSUPPORTED`; schema tightened to the enum; presets copied portable-fields-only so they can't smuggle legacy settings. |
| M-5 | **FIXED** | Envelope check delegates to `FlowStoreCodec.supportsFormatVersion(...)`; column version cross-checked against inner envelope (`TXFLOW_STORE_CODEC_VERSION_MISMATCH` → `TXFLOW_STORE_CORRUPT` with cause). |
| M-6 | **FIXED** | Codec no longer synthesizes a legacy strategy; executor maps `PAUSE_FOR_RECOVERY → FAIL_IMMEDIATELY`, `WAIT_FOR_REINCLUSION → NOTIFY_ONLY`; engine elevates rollback under `PAUSE_FOR_RECOVERY` to `RECOVERY_REQUIRED`. |
| M-7 | **FIXED** | Renewal dispatch wrapped in a recording executor — scheduling-time, dispatch-time (`RejectedExecutionException` on the JDK delayer), and execution-time failures all land in `renewalFailure` (now `AtomicReference<Throwable>`, catches `Error`); engine surfaces `TXFLOW_LEASE_RENEWAL_FAILED` → `RECOVERY_REQUIRED` and skips the fenceless terminal persist. The previously missing **engine-level split-brain test** was also added. |
| M-8 | **FIXED** | `parse()` classifies the parsed tree instead of gating on `detect()`; malformed YAML/duplicate keys → located `TXFLOW_PARSE_ERROR`; multi-doc → correct message (no line/col — minor); double-parse gone. |

**§4 low items fixed along the way**: step-`retry` whitelist, DOTALL/tree-based expression detection, legacy `write()` exception contract, fingerprint version marker, `Error` caught in all `inTransaction` variants (tested), H2 lock-timeout (`HYT00`/50200) now retryable via new dialect hook, `onRolledBack` now matches attempts by (stepId, txHash) with attempt-granular invalidation, BATCH `txInspector` now fires (exactly once, post-sign, tested), QuickTx registry trimming (+test) and concurrency javadoc, both docs nits, `${cclVersion}` defined, Spring doc artifact-id corrected.

**ADR housekeeping done**: ADR 0002 → v2.6.6 — open questions reclassified as non-blocking future design questions, stale "while Proposed" wording removed, `txflow.config` package move recorded and verified in code (forwarding classes retained; the ADR-0001 violation is gone), historical API sketches relabeled with shipped-contract summaries, and a prominent "Implemented recovery boundary" note states that `recover()` reconciles one attempt and whole-flow continuation is future work (mirrored in DURABLE_RUNTIME.md and durable-runtime.mdx). ADR 0003 gained an implementation-refinement section documenting the four store fixes.

## Still open (all Low or deliberate)

- **Codec**: legacy-path `maxSteps` cap; legacy `variables` `HashMap` ordering; model `equals`/`hashCode` (mitigated by the new canonical string-equality round-trip tests).
- **Engine**: `createHandle` monitor held across store I/O; idempotency-claim eviction/TTL; per-confirmation-depth fenced journal appends; `withScheduler` missing on the engine's executor facade (new engine tests use real-time latches); dead code (`hasActualConsumer`, test-only overloads); duplicate internal/journal execution IDs in logs.
- **RDBMS** (three were already classified deliberate): lease-epoch singleton serialization; H2 `runtimeOnly` dep (now comment-documented); anchor-connection pinning in DataSource+H2 mode; isolation level assumed not set; `readEvents` `FOR UPDATE`; naive `splitStatements`; `MAX_PAYLOAD_CHARACTERS` chars-vs-bytes (fail-closed via codec); `DriverManagerDataSource` JVM-global mutation.
- **QuickTx**: `UtxoRefDeserializer` maintenance-warning comment; direct test for the 3-arg `compose` overload.

## New observations from the fix diff (all Low/Info)

1. **Empty `execution:`/`confirmation:` stanzas now rejected** (`TXFLOW_FIELD_TYPE "Expected object"`) — schema-consistent strictness, but previously-valid documents fail; worth a release-note line.
2. **Numeric range validation still absent**: `min_confirmations: -1`, `max_attempts: 0`, `jitter: 5.0` pass codec validation though the schema forbids them — the new shape-validation layer is exactly where bounds belong.
3. `StepRunner.uncertainFailure` uses wall-clock `Clock.systemUTC()` and empty output list, inconsistent with `FlowExecutor.uncertainSubmissionFailure` (scheduler time, captured outputs).
4. Latent code-selection quirk in the engine catch: a lease-*conflict* code thrown during renewal would label a `RECOVERY_REQUIRED` outcome `TXFLOW_RESOURCE_BUSY` — unreachable with both current stores; add a comment or reorder.
5. Cancellation during retained-step verification reports the retained prefix successful from last-known state (optimistic; acceptable in a `CANCELLED` result).
6. Durable resource contention persists terminal `FAILED` with `retryable=true` under an executionId whose durable claim will refuse a retry — the flag invites a retry that must use a new executionId (same substance as pre-fix, but the flag now overpromises).
7. `TXFLOW_FIELD_VALUE` reused for "required field missing"; multi-document parse error lacks line/col; new engine/store diagnostic codes not yet cataloged in docs.
8. RDBMS diagnosability nits: interrupted-H2 repair reports "contains durable TxFlow rows" for an old-version history state; H2+VALIDATE on interrupted migration reports `TXFLOW_SCHEMA_INCOMPATIBLE` rather than a more actionable code.

**Intentional behavior changes to the legacy (non-durable) `FlowExecutor` path** (consequences of the H-5/BLK-2 fixes — release-note material): submitted-but-unconfirmed steps now report `IN_PROGRESS`/not-successful and build-only steps are omitted from terminal results; SEQUENTIAL `onStepCompleted` listeners fire after confirmation rather than after submission; `CANCELLED` results carry a `CancellationException` with typed per-step entries; BATCH `txInspector` fires for the first time; `ConfirmationTracker` cancellation errors are now `CancellationException`.

## Remaining test gaps (follow-up)

- Positive engine assertion of `PARTIALLY_COMPLETED` for a confirmed-prefix failure.
- Engine-state (durable) tests for the resubmission-rejected and policy-declined uncertain branches (they emit the same exception type the covered branch proves is mapped).
- A kill-based (rather than state-simulated) interrupted-H2-migration test.
- Schema numeric-range enforcement (observation 2) plus a validator-based conformance test of writer output.

## Updated verdict

With both blockers and the full High/Medium set fixed and verified — including empirical re-probes of the original failure scenarios and a green run of the full suite (1,113 tests: 654 + 351 + 68 + 40) — this branch is in **merge-ready shape from a correctness standpoint**. The open items above are quality-of-life and hardening follow-ups, none release-blocking. The §6 readiness caveats that remain relevant are now honestly documented in the ADRs and docs (attempt-level recovery boundary, accepted partitioned-worker residual risk).

---

# Appendix A — TxFlowStream MVP review (2026-07-18, branch `feat/txflowstream`)

Scope: package `com.bloxbean.cardano.client.txflow.stream` (commit `5421a846`, ~2,650 lines, 29 files) reviewed on the merged branch (`feat/txflow_refinement_iter1` merged into `feat/txflowstream`). This appendix is the checked-in record of the stream findings referenced by ADR 0004.

## A.1 Build status

The merged branch **did not compile**: `TxFlowStream.java` imported `txflow.exec.RollbackStrategy`, hard-moved to `txflow.config` by the refinement with no forwarding class (3 compile errors). Fixed on-branch by updating the import (plus the deprecated `exec.ConfirmationConfig` imports in `TxFlowStream.java` and `TxFlowStreamIntegrationTest.java`). Stream unit tests pass after the fix.

## A.2 Confirmed findings

1. **Terminal-state overwrite.** Any exception in `executeWindow` after some items confirmed (throwing listener/store in `recordBatch`, or a later flow of a multi-flow plan failing) routes ALL window entries through `failEntries` (`DefaultTxFlowStream.java:423-425`, `:490-499`), rewriting CONFIRMED→FAILED in the state store while receipts (future completes only once) still say CONFIRMED; `failedCount` counts on-chain-confirmed items.
2. **Worker fragility; no callback isolation, no logging.** A `RuntimeException` from a listener/state-store callback in the main path is caught by the loop (`:336-341`), stranding receipts / corrupting projections; the same callbacks throwing inside the catch-path handlers (`failWindow`/`failEntries`) escape and kill the worker, as does any `Error`. Either way the stream wedges: queue fills, `submit()` blocks, `drain()`/`close()` hang. `prepareEntry` (`:294-295`) can record ACCEPTED then abort on a throwing listener — a ghost item stuck non-terminal.
3. **`trySubmit` blocks.** It shares `submitMonitor` with `submit()`, which calls blocking `queue.put` while holding the monitor (`:199-215`, `:226`) — with a full buffer, `trySubmit` blocks indefinitely instead of returning `FULL`.
4. **`drain()` race.** Between `queue.poll` and `currentWindowSize` update (`:309-316`) an item exists only in the worker-local window; `isDrained()` (`:564-566`) evaluates true and `drain()` returns while an accepted item is unprocessed.
5. **Duplicate ids unguarded.** Duplicate `itemId` in one window collapses in the `entriesByItemId` HashMap (`:556-562`) — the losing receipt never completes (hangs `await()` forever). Two FLOW_STEP items with the same step id fail the whole window via `TxFlow` duplicate-step validation — batch poisoning by one item.
6. **No failure isolation within a window; no PARTIAL batch status.** One bad item fails every item in the window; a 9-of-10-confirmed batch records FAILED (`TxStreamBatchStatus` has no partial value).
7. **Post-merge semantic mismatches with refined txflow.** (a) `IN_PROGRESS` (submitted-unconfirmed) step results are projected to terminal FAILED and the transaction hash is dropped from the item record (`:461-485`) — unreconcilable against the chain; (b) `CANCELLED` flow results become FAILED (`FlowResult.getStatus()` never read; `TxStreamItemStatus.CANCELLED`/`cancelledCount` unreachable); (c) items are marked SUBMITTED en masse before `runner.execute` starts (`:409`, `:432-447`); (d) sticky `flush()` on an empty stream (`:323`); (e) interrupted-submit marks FAILED without counting it.
8. **Advanced features are stubs.** `UtxoReservationPolicy` is stored and never read (`:574-577` — `@SuppressWarnings("unused")` getter); "serial by funding scope" is just the single worker thread, with no funding-scope tracking. `TxWorkItem.idempotencyKey` is carried but consumed by nothing — source redelivery produces duplicate on-chain transactions.

## A.3 Done well

Clean SPI decomposition (`TxWorkSource`/`TxWorkSink`, `TxStreamPlanner`, `TxStreamStateStore`, `FlowExecutionRunner` test seam); immutable result snapshots; receipt future completed only on terminal status; bounded `ArrayBlockingQueue` backpressure; correct single-writer volatile discipline; sound windowing math (no off-by-one, time windows can't starve); honest MVP documentation (explicitly disclaims tx-count reduction).

## A.4 Test coverage

4 unit tests, all happy-path against a fake runner emitting only all-COMPLETED/all-FAILED results — none of the findings above are covered. The single integration test uses two independently funded senders, so same-funding-scope contention (the one claim "serial UTXO coordination" makes) is never exercised.
