# TxStream API Review and Readiness Report

> **Historical review snapshot (2026-08-21).** ADR 0005 implemented the
> progressive API findings recorded here. Use
> [TXSTREAM_DESIGN.md](TXSTREAM_DESIGN.md), the public getting-started guide,
> and [ADR 0005](../../adr/0005-txstream-progressive-api.md) for current behavior.

- **Date:** 2026-08-21
- **Scope:** `com.bloxbean.cardano.client.txflow.stream` (`TxFlowStream` / TxStream), engine prerequisites in `txflow.exec` / `txflow.store`, and the relational adapter in `txflow-extensions/txflow-store-rdbms`
- **Related:** [TXSTREAM_DESIGN.md](TXSTREAM_DESIGN.md) (how the system works), [TXSTREAM_INTERNALS.md](../TXSTREAM_INTERNALS.md) (maintainer invariants), [TXSTREAM_API_DX.md](TXSTREAM_API_DX.md) (historical proposal), ADR [0004](../../adr/0004-txstream-on-flow-engine.md), earlier review `../../adr/review-txstream-v2-readiness-claude.md`

This report is a product and implementation review of the shipped TxStream API: feedback, remaining edge cases, and a quality ranking. It does not propose a redesign. For the architecture itself, read the design document first.

---

## Verdict

TxStream is a **preview-ready, correctness-first streaming runtime** on `FlowEngine`. The invariant work — honest states, write-ahead binding, lane scheduling, ownership fencing — is well above typical SDK quality. The public front door is still too heavy for the ADR's own north star ("a wallet, a backend, `submit(...)`"), and a few durable-path contracts do not match what the API promises.

**Overall: 8.0 / 10 (A−).** Ship as **preview / experimental** with the current `0.8.0-pre*` line. Do **not** call this GA.

| Question | Answer |
|---|---|
| Can a careful team run payouts, outbox, or templated ops on preview? | **Yes**, with `perItem()`, a durable store, and treating `RECOVERY_REQUIRED` as "check the hash, do not blindly retry." |
| Is the API frozen? | **No.** Default lane + inherited executor + pipelining option + error catalog + store subpackage should land before 1.0. |
| Soak / scale? | Tooling exists (`txflow-soak`). Sustained multi-hour failover-under-load is an ops exercise, not a CI gate. |
| Release label | Keep **preview / experimental** on `0.8.0-pre*`. |

---

## 1. What was reviewed

| Area | What |
|---|---|
| Public API | `TxFlowStream`, `TxWorkItem`, `TxStreamReceipt`, `EmitResult`, planners, lane policies, windowing, listeners, cancel, ownership |
| Implementation | `EngineTxFlowStream` (~5,146 lines), `BuiltInPlanners`, in-memory stores, `FlowWorkSource` |
| Persistence | `TxStreamStateStore` SPI, `InMemoryDurableTxStreamStore`, `RdbmsTxStreamStateStore` |
| Tests | ~24 stream unit-test classes (composition probes, ownership, reattach, batching, templates, Flow adapter), Yaci ITs, RDBMS restart ITs, soak tooling |
| Docs | Getting-started / durability / throughput MDX, internals, ADR 0004 |

---

## 2. What is strong

These are the reasons the correctness core deserves a high score:

- **Honest states.** `SUBMITTED` is only projected after a `TRANSACTION_SUBMITTED` engine event. A known transaction hash is never dropped. An uncertain outcome is `RECOVERY_REQUIRED`, never a false `FAILED`.
- **Fail-closed planning writes, isolated observers.** Registration and binding failures reject work; listener exceptions cannot kill the dispatcher or wedge `drain()`.
- **Deterministic identities.** Claim-derived execution ids plus `StableIdFactory` make redelivery `MATCH` instead of double-spend.
- **Build-time invariants with teaching errors.** A durable stream store without a durable engine is rejected; a count-only window larger than the buffer is rejected; ownership without `supportsOwnership()` is rejected instead of wedging in `STANDBY`.
- **`trySubmit` / `EmitResult`.** Non-blocking mirror of `submit` with `OK`, `FULL`, `PAUSED`, `CONFLICT`, `REJECTED` is a genuinely well-designed backpressure surface.
- **Adversarial test culture.** Composition probes encode interleavings (ownership × mid-planning, standby × Flow adapter) that slice reviews cannot see. Several HIGH bugs were found this way and pinned by regression tests.
- **Batching is safe-by-construction.** Eligibility is a positive round-trip (`payToAddress` re-emit must equal the original `PaymentIntent`), not a field denylist.

---

## 3. API readiness

**Ready for preview users who will read the durability and throughput guides.** Not ready as a beginner API. The getting-started page already admits the construction ceremony is heavier than intended.

| ADR bar | What ships |
|---|---|
| "A wallet, a backend, `submit(...)`" | Engine construction still needs four suppliers, a signer registry, and an executor. The stream still **requires** a lane policy and its **own** executor. |
| Lanes are opt-in | `TxFlowStream.Builder.build()` throws if `lanePolicy == null`. |
| Default `LanePolicy.byFundingAddress()` | The policy exists but is not the default. |
| Inherit engine executor | Not implemented (`executor must be supplied`). |
| Intra-lane pipelining via built-ins | Not implemented. `perWindow()` / `batching()` emit `TxFlow`s with empty settings, which default to `ChainingMode.SEQUENTIAL`. A lane is one *confirmed* transaction at a time. |

Ceremony today is still on the order of 13 concepts (engine, four suppliers, two executors, lane, stream id, item, plan, idempotency key, start, receipt). That is a **product** gap, not a correctness gap.

### Surface issues that will be expensive after 1.0

1. **~50 `TXSTREAM_*` codes are string literals.** No public constants class, no catalog. Callers match `"TXSTREAM_OWNERSHIP_LOST"`.
2. **Store SPI types live in the main package** (`TxStreamStateStore`, `TxStreamItemRecord`, `TxStreamBinding`, `TxStreamPlannedRecord`, `TxStreamStoreCodec`, both in-memory stores). The public package is crowded (~50 types).
3. **Accessor style is mixed.** `TxStreamReceipt.itemId()` / `executionId()` vs `TxStreamItemResult.getItemId()` / `getExecutionId()`.
4. **`EmitResult.OK` with an already-`FAILED` receipt** (eager validation). Documented; still a trap: `isAccepted() == true` does not mean the item will run.
5. **`TxStreamEventListener` has no `onStreamAborted`.** Close, drain, and ownership are covered; abort is not.
6. **`TxStreamStateStore` javadoc still says "Iteration 1A ships in-memory only; durable arrives with iteration 2"** — durable in-memory and RDBMS already shipped.
7. **Templates cannot use `byFundingAddress()` / `partitioned()`** (`TXSTREAM_LANE_REQUIRED`). A hole if templated payouts are the intended iteration-3a story.

---

## 4. Edge cases and issues

### 4.1 Bugs / contract mismatches

**Durable redelivery after live-map eviction becomes a conflict, not an attach.** This is the most important remaining correctness issue.

Durable stores treat `evictItem` as a no-op (the documented retention lift). The stream still drops the item from the live map and the claim-key index at `maxRetainedSettledItems` (default 10,000). `accept()` only attach-or-conflicts against the **live map**. A later `registerItem` of the same id hits `TxStreamDuplicateItemException` and is reported as **CONFLICT**, even when the fingerprint is identical.

That contradicts `submit()`'s javadoc ("after eviction, an identical resubmit matches the stored execution") and the claim that a durable store lifts the retention cap. In-memory eviction behaves as documented (fresh accept → engine `MATCH`). Durable production streams that run past 10k settled items will start **rejecting legitimate redelivery**.

Fix: on live-map miss, read the store record and attach-or-conflict from the stored fingerprint before `registerItem`.

**`getItemStatus` vs `reconcile` after eviction / store-only rows.** ADR Decision 4 says `getItemStatus` on `RECOVERY_REQUIRED` is a read-through repair. For a store-only row (durable hit, live miss) it returns the stored snapshot **without** consulting the engine, and `reconcile()` returns empty. Reattach usually hydrates non-terminal items, so this is a hole more than a common path — but it is a hole in the advertised contract.

**`receipt.completion()` is not the last word.** The promise completes on `RECOVERY_REQUIRED`. A later repair advances the live projection and does **not** re-complete the future. Callers who `join()` and treat anything other than `CONFIRMED` as "retry with a new item id" can double-pay. This is designed, and it is the sharpest **user** footgun. `close()` / `drain()` can return with items still `RECOVERY_REQUIRED`.

### 4.2 Funds footguns (documented, still load-bearing)

| Case | What happens | Risk |
|---|---|---|
| **`batching()` + source redelivery of a subset** | New member set → new claim → **second on-chain payment** | Highest. Docs are excellent; the API still lets you do it. |
| **Partitioned config drift** | Durable: `TXSTREAM_BOOTSTRAP_CONFIG_DRIFT` fail-fast. Non-durable: silent re-split of the funding wallet | Funds |
| **Ownership step-down** | Queued work is `CANCELLED` / `TXSTREAM_OWNERSHIP_LOST`. Recover with a **new item id** | Ops. Same id is a duplicate. |
| **`perWindow()` "exactly-once"** | Flow-level only. One item in a new window runs again | Duplicate txs if the source redelivers subsets |
| **Non-durable crash** | ACCEPTED-but-unbound items are `TXSTREAM_ABANDONED` | Bounded loss; redelivery is the answer |
| **P3 still not landed** | Two processes spending the same wallet without stream ownership is out of scope | Claim poisoning via `TXFLOW_RESOURCE_BUSY` |

Batching still **drops** member-level context (extra signers, change, validity, metadata). That is documented and fail-closed if the merged transaction is unsignable.

### 4.3 Throughput / UTXO-model gaps

ADR Decision 2 promised three levers: parallel lanes, **intra-lane pipelining**, batching.

- **Lanes:** real — identity-keyed FIFO, alias-sharing, overlap validation, round-robin `maxInFlight`.
- **Batching:** real — payment-only, lossless-by-construction merge.
- **Pipelining: not exposed on built-ins.** `perItem()` is one in-flight execution per lane, waiting for **confirmation**, so single-lane throughput is about one transaction per confirmation latency, not per block. `perWindow()` is a multi-step `SEQUENTIAL` flow. Custom planners can set `ChainingMode.PIPELINED` on the `TxFlow`; beginners cannot.

Partitioned lanes are the supported scale path. The soak README's `rate ≤ lanes ÷ (2 × blockTime)` is the honest ops model.

### 4.4 Other edge cases

- **Payload held by reference.** Mutating a `TxPlan` after `submit` diverges fingerprint vs executed content. Easy to do with a reused `Tx` builder.
- **Blocking `onItemUpdated` stalls that lane.** No owned threads. The promise is completed *before* the listener, so `drain()` still unblocks.
- **`submit()` on a STANDBY throws `TXSTREAM_CLOSED`.** `trySubmit` returns `PAUSED`. Adapters must use `trySubmit`.
- **Lane resolver failures** (`TXSTREAM_LANE_UNRESOLVED`) are not retained; redelivery retries. Content lane errors are retained and attach forever.
- **Cancel of a shared-flow member is rejected**, not widened. Right, but `cancel()` returning `false` is easy to misread as "already done."
- **`EngineTxFlowStream` is 5,146 lines**, eight locks, several state machines. Invariants are commented in place; a new engineer should not change dispatch without [TXSTREAM_INTERNALS.md](TXSTREAM_INTERNALS.md).

---

## 5. Quality ranking

| Dimension | Score | Why |
|---|---|---|
| Correctness (single instance) | **9 / 10** | Transition table, terminal precedence, composition probes, hash preservation. Residual: durable attach-after-eviction. |
| Funds safety | **8.5 / 10** | Double-submit is structurally hard. Remaining risk is *documented contracts* (re-batch, partitioned drift, `RECOVERY_REQUIRED` retry). |
| Durability / HA | **8.5 / 10** | Two-phase bind, reattach, RDBMS, epoch-fenced ownership. Fence is an optimization over engine claims. Active/active lanes still future (needs P3). |
| Throughput design | **7 / 10** | Lanes + batching are real. Intra-lane pipelining is missing from built-ins. |
| API / DX | **6.5 / 10** | Progressive disclosure is inverted: you learn lanes and executors before you send ADA. |
| Maintainability | **6 / 10** | One well-commented god class. Extractions (ownership, reattach, snapshot status) are still the right next refactor. |
| Tests | **9 / 10** | Adversarial, deterministic, composition-aware. Soak exists but is operator-run, not CI. |
| Docs | **8 / 10** | Getting-started, durability, throughput, contracts tutorial, internals. Error-code catalog and planner SPI guide still missing. ADR examples still lag the builder. |
| **Overall** | **8.0 / 10** | **A−** as a preview runtime. **B** as a 1.0 public API. |

Relative to other Cardano client libraries' "submit many txs" layers, the **correctness thinking is top-tier**. Relative to this repo's own north star ("simple things are one line"), the **front door is the weak part**.

---

## 6. Pre-release punch list

Ordered by value while the API is still unreleased / preview:

1. **Fix durable attach-after-eviction** (store lookup + fingerprint attach). Treat as blocking for any long-running durable deployment.
2. Default `LanePolicy.byFundingAddress()`; inherit `executor` / `maintenanceExecutor` from the engine (or add `FlowEngine.builder(BackendService)`).
3. `perWindow()` / `batching()` chaining option (`PIPELINED`), or soften ADR Decision 2.
4. Public `TxStreamErrorCodes` + a table in `package-info` / docs.
5. Move store SPI to `stream.store`.
6. Make `getItemStatus` / `reconcile` honor store-only `RECOVERY_REQUIRED` rows.
7. Extract ownership + reattach out of `EngineTxFlowStream` (no API change).

---

## 7. Where this API is useful

High-throughput payouts and disbursements (lanes for parallelism, batching for fees, idempotency against redelivery); queue-fed "outbox for Cardano" services (message id as idempotency key → effectively-once on-chain, durable across restarts, Flow adapter for reactive pipelines); reliable one-shot submission that must survive a crash; bulk templated operations (register once, stream parameterized invocations); HA active/standby deployments.

**Not** the right tool for: one-off transactions (plain QuickTx), on-chain-atomic multi-party logic (contracts), or latency-critical paths (confirmation latency dominates — this is a throughput and reliability tool).

One-line guidance: *reach for the tx stream when you are submitting many transactions over time and care that each lands exactly once and survives failures; reach for QuickTx when you are building a single transaction.*
