# TxStream preview qualification

Scope: the progressive API in PR #649, evaluated on Java 17 and Java 21.

## Supported preview profile

Use `FlowRuntime` for bounded process-local jobs, `perItem()` for independently
redelivered intents, and stable item IDs. Handle timeout and `RECOVERY_REQUIRED`
without submitting replacement payments. The managed runtime's idempotency claim
capacity is a lifetime limit shared by its streams, distinct from receipt retention.
It defaults to 10,000 and can be increased for a bounded job.

Acceptance snapshots portable transaction plans. Caller mutation after acceptance
cannot change queued work; concurrent mutation during submission is unsupported.

Backend-based engines check the preceding lane transaction's output visibility
before starting the next execution. The default polling budget is 60 seconds with
a 2-second interval. Failure to observe indexing fails only the new execution,
before engine start, with `TXSTREAM_BACKEND_NOT_READY`. Provider calls require
their own I/O timeouts. This is not a cross-endpoint consistency guarantee or
coordination with external spenders.

## Validation

The following passed locally on Java 17 and Java 21:

```text
./gradlew :txflow:test -PskipSigning
./gradlew :txflow:integrationTest --tests '*TxStreamGettingStartedIntegrationTest' -PskipSigning
```

The RDBMS unit suite also passed on Java 21. The added regressions exercise:

- Snapshot isolation for both TxPlan and FlowStep submissions, including redelivery.
- Stored confirmed/failed/cancelled results through `awaitResolution`.
- Known store-only uncertainty timing out instead of being reported unknown.
- Delayed backend indexing holding the next execution without resubmission.
- Indexing timeout preserving the previous result and refusing premature execution.
- Four consecutive same-lane DevKit payments through the managed runtime without
  the soak application's submission gate.

## Separate durability qualification

The durable follow-up implements atomic registration matching and shared read-only
hydration for shipped stores. H2/PostgreSQL contract tests exercise concurrent
identical registration; runtime tests cover eviction, restart, and store-only repair.
Registrations without a persisted plan remain recovery-required and are never
silently replanned. See ADR 0007 for the implemented subset and upgrade behavior.
Transparent queue recovery across every crash boundary remains outside this preview.

Before promoting durable operation beyond experimental support, require H2 and
PostgreSQL contract tests, restart/redelivery beyond live retention, registration
commit ambiguity, shared-flow and template hydration, and explicit handling of
accepted-but-unplanned work. Run sustained provider-specific soak tests with
duplicate/missing-payment reconciliation and report application-gated and ordinary
SDK paths separately. Local DevKit success does not replace that qualification.

Backend visibility retries release the worker and in-flight slot between probes.
`FlowRuntime` supplies the retry scheduler. Direct stream builders inherit the
engine's maintenance executor when it implements `ScheduledExecutorService`;
an explicit stream `maintenanceExecutor(...)` takes precedence. When neither
provides a scheduler, an unsuccessful probe fails before engine start. A plain
`Executor` cannot schedule retries. Plain `close()` keeps retries active while
it drains and cancels remaining timers afterward; abort cancels them immediately.
Parked executions retain their buffer permits, so `maxBufferSize` includes them.
The pending hashes are process-local and reset
on restart; authoritative FAILED/CANCELLED repair clears the affected item's hold.
A visibility timeout fails every member of the waiting execution. Their IDs remain
registered. Because this particular error proves the engine never started, the
caller may retry that work under new IDs; this does not apply to RECOVERY_REQUIRED.
Custom backend-based engines used by TxStream enable visibility checks by default;
custom output services must support historical output lookup or explicitly disable
`backendVisibilityChecks`. Ordinary engine executions do not invoke this stream gate.
## Combined-branch qualification, 2026-09-06

Java 21: 1,064 TxFlow unit tests, 114 RDBMS unit tests, 73 RDBMS integration
tests (including PostgreSQL and abrupt H2 restart), and the soak reconciler unit
test passed with no skips. Java 17 TxFlow/RDBMS unit suites and the RDBMS
integration suite also passed during this change.

A two-minute local DevKit smoke run used two lanes, `perItem()`, 0.5 items/second,
four recipients, and `--utxo-gate=false`. The SDK indexing check remained enabled.
All 60 submitted items confirmed; there were no failures, cancellations, unresolved
items, orphan leases, or duplicate/missing payments. Expected and actual payout
value both equalled 73,280,000 lovelace. The exact reconciliation output is saved in
[the smoke report](qualification/2026-09-06-txstream-devkit-smoke.txt).

This was a short smoke run with chaos disabled. It does not establish long-term
heap stability, public-network throughput, or failover-under-load qualification.


## Follow-up review qualification

After the reader/writer projection race was reproduced, the review corrections
were validated on Java 21: 1,070 TxFlow unit tests, 118 RDBMS unit tests and
76 H2/PostgreSQL integration tests passed, with no failures or skips. Java 17
TxFlow/RDBMS unit and integration suites also passed during the correction.

New regressions cover RUNNING and absent snapshots during foreign reads and
receipt attachment, preserving the owner's later confirmation; targeted plan
lookup; bounded scanning past abandoned rows; exact-version and concurrent
operator acknowledgement; and a projection-only insert winning the registration
race. The dispatch regressions cover progress on another lane under maxInFlight=1,
FAILED/CANCELLED repair wakeup and stale visibility timers.
