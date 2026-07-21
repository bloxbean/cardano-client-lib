# TxFlowStream v2 — Implementation Review & Readiness Report

- **Date**: 2026-07-20
- **Branch**: `feat/txflowstream` (uncommitted working tree)
- **Scope**: the complete ADR 0004 implementation — 14 slices (1A–1C, 2a–2d, 3a–3d) across `txflow` (package `com.bloxbean.cardano.client.txflow.stream`, 53 files / ~12.5k main lines), engine prerequisites P1–P5 in `txflow.exec`/`txflow.store`, and the relational stream store in `txflow-extensions/txflow-store-rdbms`
- **Method**: every slice went implement → adversarial review → fix → verify; this report adds the two capstone reviews no slice could perform — a holistic API/DX + maintainability review, and a cross-feature composition bug hunt with empirical probes — plus live-devnet validation
- **Related**: ADR 0004 (`0004-txstream-on-flow-engine.md`, Accepted), `review-txflow-refinement-iter1-claude.md` (the engine/refinement review this builds on), `.claude/tasks/txstream-v2-implementation.md` (full per-slice notes, findings, limitations, progress log)

## Verdict

**The correctness core is genuinely production-grade and has been validated end-to-end on a live devnet. The API is feature-complete against ADR 0004. What stands between here and a release is not correctness — it is the last 10%: two cross-feature composition bugs (fix in flight), a front door heavier than the ADR's own north star, one advertised UTXO lever not yet reachable through built-ins, and zero user-facing documentation.** All of these are cheap now (the API is unreleased) and expensive after the first release.

## 1. What was verified

| Suite | Result |
|---|---|
| `:txflow:test` stream package (22 classes, scoped) | 316+ tests, 0 failures (grew to ~330 with capstone probes) |
| `:txflow:test` engine/store/codec surface (P1–P5, FlowEngine, contracts) | e.g. FlowEngineTest 42, projection-reads 10, store contract 17+10, codec 31 — 0 failures |
| `:txflow-extensions:txflow-store-rdbms:test` (H2 + schema matrix) | ~101, 0 failures |
| `:txflow-extensions:txflow-store-rdbms:integrationTest` (real PostgreSQL 17.6 via Testcontainers + H2 kill/restart) | ~62, 0 failures |
| `:quicktx:test` (after the signer-scope fix) | 351, 0 failures |
| **Live Yaci DevKit devnet** — `TxFlowStreamIntegrationTest` | **5/5 on-chain**: serial one-lane, concurrent two-lane, perWindow shared flow, batching (two payments merged into ONE real transaction), templated parameterized invocations |

Environment note: the machine OOMs on a single full-suite run; verification switched to scoped per-class runs (`--no-daemon --max-workers=1`), which are equivalent in coverage and reliable. The timing-architecture guard (no owned threads/timers/wall-clock in txflow main) passes throughout.

## 2. What the review process caught (and why that builds confidence)

Every slice's adversarial review found real defects before they could ship; each was fixed with a regression test. The funds- and truth-relevant ones:

| Round | Finding (severity) | Resolution |
|---|---|---|
| 1A | Two unbounded-hang races (stranded item on systemic failure; attach to a failed registration); dishonest conflict on redelivered non-portable items; missing funding-scope enforcement | Structural rescues; payload-error fingerprints; mechanical lane pinning |
| 1B | Eviction clobbering a live successor's claim-key index; post-abort lost wakeup; non-total dispatch seam | Gated eviction; `schedulePump` on abort path; totalized seam (`TXSTREAM_EXECUTION_UNOBSERVABLE`) |
| 1C | Post-stop window straggler never settles (HIGH); **MATCHED stored `PARTIALLY_COMPLETED` shared flow permanently marks on-chain-CONFIRMED members FAILED** (HIGH); live claim-key collision | Straggler rescue; member-evidence projection; typed plan rejection |
| 2b | Re-attach fast-forward never reached the durable store (confirmed item re-attached forever) (HIGH); key-reuse guard lost on restart | Sequence-domination seeding; guard rebuild from durable records |
| 2c | Failed bootstrap could dispatch onto unfunded lanes; **config/lane-order drift silently re-splits the funding wallet** (funds) | `bootstrapSatisfied` dispatch gate; persisted fingerprint + `TXSTREAM_BOOTSTRAP_CONFIG_DRIFT` fail-fast |
| 2d | No funds bug — batching proven safe-by-construction; denylist hardened into a positive round-trip guard | Loud re-batch double-pay contract |
| 3a | **Multi-step template `PARTIALLY_COMPLETED → FAILED` misreport** (HIGH, the 1C bug's un-hunted sibling); silent template-definition drift | Whole-flow-aware snapshot projection; `TXSTREAM_TEMPLATE_DRIFT` fail-fast |
| 3b | Stats gauge going negative; phase starvation | Symmetric seed accounting; phase alternation |
| 3c | "Never stalled by an observer" claim overreached (blocking `onNext` stalls a lane — inherent to no-owned-threads) | Honest §2.2 scoping + `completePromise`-before-listener blast-radius reduction |
| 3d | No double-dispatch/epoch bug found; reclaim memoized-reattach gap; ownership fail-open on non-supporting stores | Reattach reset on step-down; `supportsOwnership()` build-time check |
| Devnet | **Portable/YAML signer refs with omitted scope silently dropped** (`QuickTxBuilder.compose` null-scope guard) — templated txs failed "No signers found" | Default omitted scope to `PAYMENT`, matching the Java API's own default (quicktx fix, 351 tests green) |

The pattern to note: three separate reviews caught the *same class* of bug (a confirmed on-chain transaction being reported FAILED) in three different code paths. That class is now pinned by regression tests in all three places, and it is the single most important invariant the API promises.

## 3. Capstone findings (the two reviews no slice could do)

### 3a. Cross-feature composition bugs — 2 HIGH, **both fixed and verified** (probes 8/8 green)

Both were collisions between slices that were individually correct; both are pinned by probe tests (`TxFlowStreamCompositionProbeTest`) encoding the exact interleavings, which failed before the fix and pass after:

1. **Ownership fence during window planning strands items** — a step-down while the planner was mid-`plan()` left executions in lane queues the ownership-gated pump would never dispatch: promises never settled, `drain()`/`close()` hung, and a later reclaim could have dispatched executions the new owner already reaped as CANCELLED. **Fixed**: loss-of-ownership is treated exactly like abort in the planning/dispatch pipeline (`runPlanning` entry + post-enqueue rescue, `dispatchTemplate` rescue, `rescueWindowStraggler` typing), all settling `TXSTREAM_OWNERSHIP_LOST`; the invariant is stated in `stepDownFenced`'s javadoc: *after step-down and pump quiescence, no unsettled non-in-flight item exists anywhere* — which also structurally proves the reclaim corollary (standby lane queues are provably empty). The entry check additionally fixed a latent hang (ownership `close()` flushing an open window after lease release).
2. **Fenced step-down permanently killed the Flow ingestion adapter** — a STANDBY stream's `accept` returned the same terminal CLOSED as a dead stream, so the adapter tore down, silently dropped held items, and completed `terminated()` normally. **Fixed**: a distinct `EmitResult.Status.PAUSED` disposition for ownership-standby (only for a live stream in STANDBY; closed/aborted/unhealthy stay CLOSED); `FlowWorkSource` parks PAUSED items like FULL (no teardown, no drop, capacity invariant preserved, no busy-spin); `openForWork()` calls `source.resume()` on every (re)activation so a reclaim resumes ingestion. Doc-only decision recorded: the reconciliation observer deliberately keeps running read-only on a STANDBY (CAS-arbitrated, benign).

Six other suspect compositions were empirically verified clean (standby×source, template×partitioned, template×batching lane-sharing, abort×held-items, cancelExecution×batch derivation, cross-feature stats coherence), and several surprising-but-correct behaviors are now documented (standby keeps running read-only reconciliation; `confirmed > accepted` is legitimate after re-attach).

### 3b. API/DX against the north star ("simple idiomatic APIs, powerful advanced stuff, full power of UTXO")

**P1 — recommend fixing before release (breaking changes are free now):**
1. **The required lane policy contradicts ADR design principle 1** ("lanes … are opt-in layers, never prerequisites"). Default to `LanePolicy.byFundingAddress()` — zero config, correct single-wallet serialization, free multi-wallet parallelism — and the beginner never learns what a lane is.
2. **Beginner ceremony is ~25 lines / 13 concepts** (ADR bar: "a wallet, a backend, submit(...)"). Add `FlowEngine.builder(BackendService)` (the legacy `FlowExecutor` it replaces had exactly this) and default the stream's `executor`/`maintenanceExecutor` from the engine's. Target: ~8 lines / 6 concepts.
3. **The third UTXO lever (intra-lane pipelining) is not reachable through any built-in planner** — `perWindow()`/`batching()` build SEQUENTIAL flows, so a 50-item window confirms serially. ADR Decision 2 explicitly promises PIPELINED chaining within a lane. Add a chaining option to the built-ins (the plumbing exists; `PlannedExecution` already carries a full `TxFlow`).
4. **Error-code catalog**: ~50 `TXSTREAM_*` codes exist only as scattered string literals. A public constants class + a table in `package-info`.
5. **Move the 8 store-SPI types to a `stream.store` subpackage** (44 public types → ~36 in the main package).

**P2 — coherence polish:** unify `getX()` vs `x()` accessors in the result family; add `onStreamAborted` to the listener; decide the `EmitResult.OK`-with-already-FAILED-receipt wart deliberately; state the (actually coherent) Result/Report/Outcome/Status suffix taxonomy in `package-info`.

**Positives worth recording:** internals correctly package-private (nothing leaked, nothing needed is hidden); `trySubmit`/`EmitResult` is a genuinely well-designed non-throwing mirror; the build-time invariants with teaching messages are the best in the codebase; honest states are implemented, not just documented.

### 3c. Maintainability

`EngineTxFlowStream.java` (4,952 lines) is a **well-organized monolith crossing into god-class territory**: clean section banners and load-bearing invariant comments, but eight locks with one recorded ordering constraint, six interacting state machines, and a visible accretion pattern. Recommended extractions (package-private, no API impact, in safety order): (1) the ownership machine (~330 lines, one gate), (2) reattach + bootstrap (~600 lines, start-time-only), (3) snapshot-status derivation (stateless, easiest to unit-test). Do **not** extract the lane dispatcher — it is the concurrency heart and its invariants are documented in place. Housekeeping: the literal NUL byte forcing `grep -a` on stream files is a recurring tooling tax worth removing.

### 3d. Documentation

- **ADR sync**: three divergences — the flagship Decision 1 example doesn't compile (missing required executor + scheduler), the pipelining claim is unimplemented in built-ins (finding P1.3), and the delivery plan still frames shipped iterations as future.
- **User docs: none exist.** `docs/content/preview/txflow/` (13 pages) and `txflow/README.md` have zero stream mentions. Every funds-critical warning (re-batch double-pay, partitioned config drift, new-item-id-after-step-down) lives only in javadoc. Must-write before release: stream getting-started; durability & exactly-once guide (dedup scopes, eviction guard window, re-attach, ownership); throughput guide (lanes/windows/batching/partitioned + the config-stability warning promoted out of javadoc); error-code reference; planner SPI guide.

## 4. Readiness assessment by dimension

| Dimension | Rating | Basis |
|---|---|---|
| Correctness (single instance) | **Strong** | 7 adversarial rounds + regression tests; honest-states invariant pinned in 3 code paths; devnet-validated |
| Funds safety | **Strong** | Batching proven lossless; bootstrap double-split fail-fast; engine claim + P3 backstop make double-submit structurally impossible; the residual risks are loudly documented contracts (re-batch, config drift) |
| Durability / crash recovery | **Strong** | Write-ahead binding, sequence-dominated projection convergence, re-attach proven against shared stores + real PostgreSQL + H2 kill/restart |
| Multi-instance HA (3d) | **Strong** | Epoch fencing atomic/monotonic on all 3 stores; fence honestly an optimization over the engine's real guarantee; both composition bugs fixed + probe-pinned |
| Throughput | **Good, one gap** | Lanes + batching real and devnet-proven; intra-lane pipelining not yet exposed through built-ins (P1.3) |
| API / DX | **Good core, heavy front door** | P1.1/P1.2 are the gap between shipped and the stated north star |
| Operability | **Adequate** | Typed codes + stats + listeners exist; needs the error catalog and ops docs; unbounded lane maps documented for high-cardinality use |
| Docs | **Not ready** | Excellent javadoc; zero user docs |
| Maintainability | **Adequate, plan the extractions** | One 4,952-line class; extractions identified and safe post-hoc |

## 5. Recommended pre-release punch list (in order)

1. ~~Land the two composition-bug fixes~~ **DONE** — 8/8 probe tests green; ownership/FlowAdapter/WindowPolicy suites verified unregressed.
2. P1.1 + P1.2: default lane policy + beginner construction tier (drops the front door to the ADR's own bar).
3. P1.3: pipelined chaining option on `perWindow()`/`batching()` (or honestly soften ADR Decision 2).
4. P1.4 + P1.5: error-code catalog; store-SPI subpackage move.
5. ~~Docs: getting-started + durability guide + throughput guide (the funds-critical warnings must leave javadoc-only status).~~ **DONE** — `txstream-getting-started.mdx`, `txstream-durability.mdx`, `txstream-throughput.mdx` in `docs/content/preview/txflow/` (+ `_meta.js`/`overview.mdx` registration + `txflow/README.md` section); every Java sample compile-verified against the shipped API; the re-batch double-pay, partitioned config-drift, and new-item-id-after-step-down warnings are now user-facing. Error-code reference and planner SPI guide remain follow-ups.
6. ADR sync pass (fix the flagship example; update the delivery-plan framing).
7. P2 polish + the three named extractions from `EngineTxFlowStream` (can trail the release).
8. Release labeling: ship as **preview/experimental** alongside txflow's `0.8.0-pre*` line; the honest caveat is that scale/soak testing on a long-running devnet (thousands of items, hours of uptime, real failover under load) has not been done — the devnet run validated the scenarios, not sustained load.

## 6. Where this API is genuinely useful (support positioning)

High-throughput payouts/disbursements (lanes for parallelism, batching for fees, idempotency against redelivery); queue-fed "outbox for Cardano" services (message id as idempotency key → effectively-once on-chain, durable across restarts, Flow adapter for reactive pipelines); reliable one-shot submission that must survive a crash; bulk templated operations (register once, stream parameterized invocations); HA active/standby deployments. **Not** the right tool for: one-off transactions (plain QuickTx), on-chain-atomic multi-party logic (contracts), or latency-critical paths (confirmation latency dominates — this is a throughput/reliability tool).

One-line guidance: *reach for the tx stream when you're submitting many transactions over time and care that each lands exactly once and survives failures; reach for QuickTx when you're building a single transaction.*
