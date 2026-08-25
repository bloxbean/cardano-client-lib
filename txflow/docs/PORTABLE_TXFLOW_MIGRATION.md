# Portable TxFlow migration

Portable TxFlow separates a reusable definition from each execution request. New documents use
`api_version: txflow.cardano-client.dev/v1alpha1`, `kind: TxFlow`, metadata, and `spec`.

Key changes from the preview `version: "1.0"` format:

- Put one shared QuickTx transaction (`tx` plus optional `context`) under each step's `transaction`.
- Declare runtime inputs in `spec.parameters` and supply values through `FlowBindings`; portable
  expressions use `${{ inputs.name }}` and are bound on parsed nodes, never raw YAML text.
- Use `needs` only for ordering. Name a producer output and consume it with `flow_output` when a
  later transaction must spend that exact output. Use `funding_from` when ordinary
  address-based coin selection may use any pending output from an earlier step
  that belongs to the requested funding address.
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

Compatibility notes for the v1alpha1 refinement:

- Compiled definition fingerprints include an explicit `txflow-compiled:v1` domain marker. Any
  fingerprint produced by an earlier preview build changes once; do not rewrite an existing
  durable execution to force a match. Finish or reconcile it with the build that created it, or
  treat the upgraded definition as a deliberately new execution identity.
- Portable parsing now rejects unknown execution fields, wrong scalar types, invalid presets, and
  malformed values that older preview builds ignored or coerced. Empty `execution:` and
  `confirmation:` stanzas are also rejected; omit an unused stanza or provide an object with the
  intended settings.
- `metadata.description` is an additive v1alpha1 field. Older external validators using the prior
  schema with `additionalProperties: false` may need the updated bundled schema before accepting a
  document that emits it.
- `ConfirmationConfig.devnet()` and `quick()` contain legacy backend-wait behavior. Express their
  portable confirmation values with `preset` or explicit min/check/timeout fields; backend restart
  and index-sync behavior remains host/test infrastructure, not document authority.
- The legacy `FlowExecutor` facade now reports submitted-but-unconfirmed steps as `IN_PROGRESS`
  and omits build-only steps from terminal results. `onStepCompleted` fires after confirmation,
  cancellation results carry a `CancellationException`, and the batch-mode `txInspector` callback
  is invoked consistently with the other chaining modes.

The codec still reads the preview format and emits `TXFLOW_LEGACY_FORMAT`. Portable encoding fails
instead of silently discarding Java factories, any legacy `StepDependency` selection, step-level
retry overrides, legacy flow/`TxPlan` variables, additional transactions, legacy
`RollbackStrategy`, non-default legacy confirmation compatibility fields, or non-default retry
filter flags. Migrate dependencies to `needs` plus named `flow_output` references, values to
parameters plus `FlowBindings`, and rollback behavior to `RollbackPolicy`.

For Java 21 virtual threads, create the executor in the application and pass it to the engine or
legacy facade. TxFlow never owns or closes it.
