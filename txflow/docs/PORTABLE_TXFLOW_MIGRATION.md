# Portable TxFlow migration

Portable TxFlow separates a reusable definition from each execution request. New documents use
`api_version: txflow.cardano-client.dev/v1alpha1`, `kind: TxFlow`, metadata, and `spec`.

Key changes from the preview `version: "1.0"` format:

- Put one shared QuickTx transaction (`tx` plus optional `context`) under each step's `transaction`.
- Declare runtime inputs in `spec.parameters` and supply values through `FlowBindings`; portable
  expressions use `${{ inputs.name }}` and are bound on parsed nodes, never raw YAML text.
- Use `needs` only for ordering. Name a producer output and consume it with `flow_output` when a
  later transaction must spend that exact output.
- Replace embedded keys and scripts with server-owned logical resource references.
- Import `FlowExecutionSettings`, `ConfirmationConfig`, and `RollbackStrategy` from
  `com.bloxbean.cardano.client.txflow.config`. The former settings and confirmation classes remain
  as deprecated forwarding subclasses; Java enums cannot be forwarded, so `RollbackStrategy` was
  hard-moved during the pre-release window.
- Parse with `TxFlowCodec`, compile with `TxFlowCompiler`, then execute a `FlowExecutionRequest`
  through an immutable `FlowEngine`.
- Retry classification is conservative for untyped failures: known pre-submission `IOException`
  and timeout failures remain retryable according to policy, but an unknown `RuntimeException`
  fails because the legacy adapter cannot prove whether submission occurred. Unknown or accepted
  submission outcomes with a known transaction hash are reconciled before any retry.

Durable/server deployments should also follow [the store, fencing, recovery, and executor
contract](DURABLE_RUNTIME.md).

The codec still reads the preview format and emits `TXFLOW_LEGACY_FORMAT`. Encoding fails instead
of silently discarding Java factories, predicate filters, or additional transactions.

For Java 21 virtual threads, create the executor in the application and pass it to the engine or
legacy facade. TxFlow never owns or closes it.
