---
title: "Known issues"
description: "Known gaps in TxFlow and TxStream in the latest preview, and how to work around them."
---

These gaps are in the [latest preview](/start-here/installation/#versions), and we plan to fix them in a coming release. Follow progress in [issue #738](https://github.com/bloxbean/cardano-client-lib/issues/738).

## TxStream

| Issue | What happens | What to do |
|---|---|---|
| Partitioned `bootstrap(true)` | `start()` fails with `TXSTREAM_BOOTSTRAP_FAILED`, because the bootstrap transaction has no signer | [Fund the lanes yourself](/txstream/throughput/) and use `bootstrap(false)` |
| `batching(...)` on an address lane | The batch fails with `No signers found`, and no funds move | Use a funding-ref lane (`ofFundingRef` or `from_ref`) |
| Secure references | Nothing resolves `withSecureBindingReference` values, so a durable stream can't deliver a secret to a template | Keep secrets in signer and resource registries |
| `awaitResolution` on `FlowRuntime` | It can't change a `RECOVERY_REQUIRED` answer, and ends in `TxStreamTimeoutException` | Look the transaction hash up on chain, or use an engine with a store |

## TxFlow engine

These also affect TxStream, which runs on the same engine.

| Issue | What happens | What to do |
|---|---|---|
| `engine.recover(...)` | It never finishes an execution, and on a finished one it moves the state back to `RUNNING`. A later replay of the same key returns `RECOVERY_REQUIRED` | Use the recovery result and an on-chain lookup as your answer. Don't call it on a finished execution |
| `spec.execution.validity` | It has no effect, so the transaction gets no validity interval from it | Set `valid_from_slot` and `valid_to_slot` in the transaction's `context` |
| Rollback actions with the default wiring | `DefaultChainDataSupplier` doesn't declare authoritative absence, so `RollbackAction` isn't applied, and a vanished transaction ends `RECOVERY_REQUIRED`. `FlowRuntime` can't supply another adapter | With `FlowEngine`, [opt in](/txflow/policies-results-observability/#submission-and-confirmation-are-separate) with `ObservationCapabilities.withAuthoritativeAbsence(...)` |
| `FlowExecutionState.ROLLED_BACK` | A run never produces it | Detect a rollback through `FlowErrorCategory.ROLLBACK` |
