# TxStream Getting Started

TxStream is the continuous-submission API on top of TxFlow. For a script, CLI, or
small application, `FlowRuntime` is the shortest safe front door: it owns one normal
`FlowEngine`, its executors, and every stream opened through it. Server applications
can construct `FlowEngine` and `TxFlowStream` directly when they need explicit
resource ownership.

The example assumes you already have a `BackendService`, a funded sender `Account`,
and a receiver address. It creates no lane, executor, or signer-registry object:

```java
try (FlowRuntime runtime = FlowRuntime.builder(backend)
        .account("account://sender", sender)
        .build();
     TxFlowStream stream = runtime.open("payouts")) {
    TxPlan plan = TxPlan.from(new Tx()
                    .payToAddress(receiver, Amount.ada(2))
                    .fromRef("account://sender"))
            .withSigner("account://sender");

    TxStreamItemResult result = stream.submit("order-0042", plan)
            .awaitConfirmed(Duration.ofMinutes(5));
}
```

This success path is exercised against a real Yaci DevKit chain by
`TxStreamGettingStartedIntegrationTest` on Java 17 and Java 21.

## Why the account reference appears twice

A portable transaction plan names resources without embedding private keys or other
runtime secrets. `fromRef("account://sender")` selects the funding resource;
`withSigner("account://sender")` declares who authorizes the transaction. Those are
separate contracts even though they refer to the same account in a simple payment.
Scripts, policies, multisignature plans, and fee delegation can make them different,
so TxFlow does not infer one from the other.

`FlowRuntime.Builder.account(ref, account)` registers the runtime object behind that
portable name. Use a `wallet://` reference with `wallet(ref, wallet)`. Advanced
applications can provide a custom signer registry directly to `FlowEngine` instead.

Within one stream, do not mix `from(address)` and `fromRef("account://...")` for the
same wallet. Default lanes use syntactic identity, so those forms occupy different
lanes even if they eventually resolve to the same wallet. That weakens ordering and
throughput clarity; the engine remains the final contention boundary.

## Stable item IDs are the idempotency boundary

The `submit(String, TxPlan)` overload uses its item ID as the idempotency key. Reuse
the same business identifier when delivering the same intent again:

```text
stream.submit("order-0042", plan); // retry/redelivery attaches to the same intent
```

Do not generate a new ID for every retry:

```text
stream.submit("order-" + UUID.randomUUID(), plan); // defeats idempotency
```

Use `TxWorkItem` when the caller-visible item ID and business idempotency key must be
different, or when you need metadata, templates, bindings, or an explicit lane.

## Handle outcomes without blind resubmission

`awaitConfirmed(timeout)` is the beginner default. It returns only `CONFIRMED` and
throws typed exceptions for failure, cancellation, uncertainty, interruption, or
timeout. The timeout bounds the caller's receipt wait; it does not replace the
engine's confirmation timeout.

Always catch uncertainty first. A transaction in `RECOVERY_REQUIRED` may already be
on chain. Do not submit a replacement transaction:

```java
try {
    return stream.submit("order-0042", plan)
            .awaitConfirmed(Duration.ofMinutes(5));
} catch (TxStreamUncertainException uncertain) {
    // DO NOT RESUBMIT: reconcile the known item/transaction until resolved.
    return stream.awaitResolution(uncertain.itemId(),
            Duration.ofMinutes(5), Duration.ofSeconds(5));
} catch (TxStreamCancelledException cancelled) {
    throw cancelled;
} catch (TxStreamFailedException failed) {
    throw failed;
}
```

`awaitResolution` makes its network polling explicit. It repeatedly reconciles the
known item until it leaves `RECOVERY_REQUIRED` or the total timeout expires. It
returns only a confirmed result and throws the same typed failed/cancelled outcomes
when reconciliation becomes conclusive.

Callers that intentionally branch over every settled state can use
`awaitSettled()`; asynchronous integrations can use `completion()`. Both can expose
`RECOVERY_REQUIRED`, and `TxStreamItemResult.isUncertain()` is the direct predicate.

Validation or authoritative registration failures are rejections, not failed work.
Blocking `submit` throws the typed cause; `trySubmit` returns `REJECTED`. A rejected
item has no receipt, counter contribution, or retained state and can be corrected and
retried under the same stable item ID.

## Effective defaults

These defaults are normative until a future programmatic configuration snapshot is
introduced:

| Concern | Default | Operational meaning |
|---|---|---|
| Planner | `perItem()` | One single-step flow and true per-item idempotency |
| Lane policy | `byFundingSource()` | Syntactic `addr:` or `ref:` lanes; one in flight per funding identity |
| State store | In-memory, non-durable | Restart loses stream planning and projection state |
| Source | Direct submission | No external source starts automatically |
| `maxInFlight` | 16 | Up to 16 distinct ready lanes, never 16 transactions from one lane |
| `maxBufferSize` | 1,000 | Accepted buffered-item bound before backpressure |
| `maxRetainedSettledItems` | 10,000 | In-memory settled receipt/status retention bound |
| Window | None | Immediate per-item planning; no timer |
| Reconciliation | Off | No periodic repair observer |
| Ownership | Off | No active/standby election unless configured |
| Maintenance | None on direct stream builder | Timed/durable features require an explicit scheduler; `FlowRuntime` supplies its owned scheduler |
| Confirmation | Poll every 2 seconds; 60-second engine execution timeout | The receipt timeout is a separate caller budget |

## Opt in to pipelined windows

The default `perItem()` planner is the safest starting point. When several
transactions from one funding source should form one dependent flow, close them
as a window and put the chaining choice on that planner:

```java
TxFlowStream stream = TxFlowStream.builder("payouts", engine)
        .planner(TxStreamPlanner.perWindow(ChainingMode.PIPELINED))
        .window(WindowPolicy.count(4))
        .open();
```

`perWindow()` and `perWindow(ChainingMode.SEQUENTIAL)` retain the existing
confirmation-between-steps behavior. `PIPELINED` submits the generated steps
as a deterministic dependency chain without waiting for confirmation between
them, so an early transaction failure can also invalidate later transactions
that spend its expected outputs. It does not alter per-item, batching,
custom-planner, or registered-template flows.
`ChainingMode.BATCH` is intentionally rejected for this API.

Per-window identity covers the exact member set, not each item independently.
If a source can redeliver individual items, retain `perItem()` or deduplicate
upstream; a redelivered item in a differently composed window is a new flow and
can produce a second payment.

## Where to go next

- Direct `FlowEngine` construction is the production/server path when the
  application must size and own executors, stores, and maintenance resources. Closing
  a direct engine or stream never transfers ownership of caller-supplied executors.
- Different funding sources form independent default lanes. Explicit and partitioned
  lanes, windows, batching, and planner-local pipelining are advanced throughput
  controls; read [TxStream internals](TXSTREAM_INTERNALS.md) before changing their
  idempotency or UTxO assumptions.
- Graceful `close()` drains accepted work without a default timeout.
  `close(Duration)` bounds that wait; `abort(...)` is the explicit cancellation path.
- Durable production guidance is deliberately incomplete until
  [ADR 0006](../adr/0006-txstream-durable-registration-and-hydration.md) ships its
  registration-matching and store-only hydration requirements. Do not infer restart
  safety from the in-memory beginner profile. The current durable boundaries are
  described in [Durable runtime](DURABLE_RUNTIME.md).
