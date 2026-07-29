# ADR 0001: TxFlow Flow-Level Execution Context in YAML

**Status**: Proposed
**Date**: 2026-06-17
**Issue**: https://github.com/bloxbean/cardano-client-lib/issues/630
**Modules**: `txflow`, `quicktx`

## Context

TxFlow YAML currently describes flow identity, variables, steps, dependencies, per-step retry policy, and each step's transaction plan. It does not describe execution behavior for the flow itself.

The runtime execution settings live only on `FlowExecutor` fluent methods:

- `withChainingMode(ChainingMode)`
- `withConfirmationConfig(ConfirmationConfig)`
- `withRollbackStrategy(RollbackStrategy)`
- `withDefaultRetryPolicy(RetryPolicy)`

This means a flow loaded with `TxFlow.fromYaml(...)` cannot declare that it should run in `BATCH` mode, use a devnet confirmation preset, or rebuild the whole flow after rollback. Those settings are also lost when a flow is serialized with `TxFlow.toYaml()`.

TxPlan already has a top-level `context:` block for transaction composition settings such as fee payer, collateral payer, signer refs, validity range, and deposit behavior. TxFlow has a per-step `context:` that maps to this transaction context, but there is no flow-level context for executor behavior. This causes downstream YAML tooling, such as Yaci DevKit scenario files, to carry custom directives outside the TxFlow schema and strip them before calling `TxFlow.fromYaml(...)`.

## Scope

In scope:

- a top-level TxFlow execution `context:` section;
- a public `FlowExecutionSettings` model carried by `TxFlow`;
- YAML round-trip support for execution settings;
- executor precedence and per-execution effective settings;
- strict parsing and validation for execution settings.

Out of scope:

- changing the TxPlan transaction context schema;
- implementing minting `policy_ref` (covered by `quicktx/adr/policy-references-for-minting.md`);
- adding a mandatory YAML schema discriminator;
- implementing a generic extension namespace.

Follow-up:

- public YAML format detection helpers;
- an explicit `extensions:` map for layered tools if more extension points are needed after flow-level context lands;
- a Plutus script-reference design for minting and validator attachment.

## Decision

Add an optional top-level TxFlow `context:` section as a sibling of `flow:`. This section is the flow execution context. It is distinct from the per-step `context:`, which remains the transaction composition context for a step.

```yaml
version: "1.0"
context:
  chaining_mode: BATCH
  confirmation: devnet
  rollback_strategy: REBUILD_ENTIRE_FLOW
  retry:
    max_attempts: 3
    backoff: exponential
    initial_delay: 1s
    max_delay: 30s
flow:
  id: fund-and-forward
  steps:
    - step:
        id: fund
        tx:
          # transaction content
        context:
          signers:
            - ref: account://acc0
              scope: payment
```

The flow-level `context:` will be carried on the `TxFlow` domain model through a new immutable domain object named `FlowExecutionSettings`. Do not call the new type `TxFlowContext`, and do not reuse `txflow.exec.FlowExecutionContext`; that class tracks step outputs and execution state during a run.

The domain model must preserve whether a setting was explicitly provided in YAML. Convenience getters may expose effective defaults, but the executor needs presence information to apply precedence correctly.

`FlowExecutionSettings` should not introduce a model-to-`txflow.exec` package dependency. The implementation should either move public execution config types such as `ConfirmationConfig` and `RollbackStrategy` into the public `txflow` API package during the preview window, or introduce base-package setting types that are mapped to executor internals at the boundary.

## Context Fields

The initial schema should support these fields:

| YAML field | Java target | Default when absent |
|------------|-------------|---------------------|
| `chaining_mode` | `ChainingMode` | `SEQUENTIAL` |
| `confirmation` | `ConfirmationConfig` preset or inline config | no deep confirmation tracking |
| `rollback_strategy` | `RollbackStrategy` | `FAIL_IMMEDIATELY` |
| `retry` | `RetryPolicy` default for steps | no default retry |

`chaining_mode` and `rollback_strategy` should parse enum names case-insensitively. Serialization should emit canonical enum names.

`confirmation` may be either a preset string or an object:

```yaml
context:
  confirmation: devnet
```

```yaml
context:
  confirmation:
    preset: devnet
    min_confirmations: 3
    check_interval: 1s
    timeout: 5m
    max_rollback_retries: 3
    wait_for_backend_after_rollback: true
    post_rollback_wait_attempts: 30
    post_rollback_utxo_sync_delay: 3s
```

Preset names are `defaults`, `devnet`, `testnet`, and `quick`. Inline fields override the preset when both are present. A confirmation object without a `preset` starts from `ConfirmationConfig.defaults()` and then applies the inline fields; it does not imply the `devnet` or `quick` preset. Duration fields should use the same compact format as TxFlow retry YAML (`ms`, `s`, `m`) and may later add ISO-8601 duration support if needed.

`max_rollback_retries` belongs to `ConfirmationConfig`, so the schema must place it under the `confirmation` object. Do not accept a root-level `context.max_rollback_retries` alias; the flow-level context schema is new, and accepting a non-round-tripping alias would create needless long-term asymmetry.

YAML DTOs must use boxed nullable fields such as `Integer` and `Boolean`, not primitives. Preset merging and precedence depend on distinguishing absent values from explicit values such as `false` or `0`.

## Execution Precedence

For each execution setting:

1. An explicit `FlowExecutor.with...(...)` call wins.
2. A value from `TxFlow.context` is used when the executor did not explicitly configure that setting.
3. The existing library default is used when neither executor nor flow provides a value.

For retry policy specifically, step-level retry stays strongest for the step:

1. `FlowStep.retry`
2. explicit `FlowExecutor.withDefaultRetryPolicy(...)`
3. `TxFlow.context.retry`
4. no retry

This precedence requires the executor to distinguish "explicitly set to the default value" from "not set". `FlowExecutor` currently initializes fields such as `chainingMode` and `rollbackStrategy` to defaults, and its setters preserve public null-to-default behavior (`withChainingMode(null)` means `SEQUENTIAL`; `withRollbackStrategy(null)` means `FAIL_IMMEDIATELY`). Therefore the implementation must add explicit override tracking flags rather than changing those configured fields to nullable values.

`confirmation` precedence is whole-object precedence. An explicit `FlowExecutor.withConfirmationConfig(...)` wins over the whole YAML confirmation object. There is no cross-source field merge such as "executor check interval plus YAML timeout". Field-level merging happens only inside YAML when a `confirmation.preset` is combined with inline confirmation fields.

## Executor Implementation Constraint

Flow context must not be applied by mutating shared `FlowExecutor` fields before running a flow.

`FlowExecutor` can run async flows and may be reused across flows. Mutating executor fields to apply one flow's YAML context would cause cross-flow races when two flows with different contexts execute concurrently.

Implementation should compute an immutable per-execution effective settings object, for example `EffectiveFlowExecutionSettings`, and pass it through validation, chaining-mode dispatch, retry selection, confirmation tracking, and rollback handling.

`withConfirmationConfig(...)` currently has an eager side effect: it creates a `ConfirmationTracker` and stores it on the executor instance. That shared tracker is part of the concurrency hazard. The refactor should remove the shared `confirmationTracker` as the authoritative runtime tracker, or leave it only as a compatibility detail that is not used by execution. Confirmation trackers should be created from the effective per-execution confirmation config and scoped to that execution.

## Validation Rules

- A non-default `rollback_strategy` still requires confirmation tracking. If neither the executor nor the flow context provides `confirmation`, fail with a clear message before execution.
- This validation must run against the computed effective settings, not the executor's shared fields. The existing programmatic behavior already fails for `withRollbackStrategy(REBUILD_...)` without confirmation tracking; the refactor must preserve the same rule for both Java and YAML paths.
- `confirmation` absent means keep current simple confirmation behavior, not `ConfirmationConfig.defaults()`.
- `confirmation: defaults` explicitly enables deep confirmation tracking with `ConfirmationConfig.defaults()`.
- A bare `confirmation:` with no value, `confirmation: null`, or an empty `confirmation: {}` object is invalid. Use absence for simple confirmation behavior or `confirmation: defaults` for explicit default deep tracking.
- Invalid enum names or invalid duration values must fail fast for execution-context fields.
- The same `retry:` shape must not be strict at flow level but lenient at step level. This implementation migrates step retry parsing to the same strict enum, duration, and bounds validation used by flow-level retry.
- Dependency `strategy:` parsing should also fail fast for unknown values. A misspelled dependency strategy can silently change UTXO-selection semantics, so it should not default to `ALL`.
- Existing TxFlow YAML without `context:` remains valid.

Compatibility note: TxFlow YAML that previously relied on lenient fallback for typoed step retry values or dependency strategies will now fail during parsing. Fix the YAML to use valid enum names instead of relying on logged defaults.

## YAML Compatibility And Extensions

The current `FlowDocument` and `TransactionDocument` root classes use Jackson's default unknown-property behavior. Many intent classes ignore unknown fields, but root-level or flow-level unknown fields fail parsing.

Do not globally disable unknown-property failures for all YAML. That would hide misspelled transaction fields such as `chianing_mode` or `fee_pyayer_ref`.

This ADR does not add a generic extension mechanism. The new top-level execution context addresses the immediate Yaci DevKit use case that motivated custom directives. If future tooling still needs extension data, prefer one explicit `extensions:` map at relevant scopes instead of supporting both magic `x_*` prefixes and maps.

## Related Gaps From Issue 630

### Policy References For Minting

`DefaultSignerRegistry.addPolicy("policy://...", policy)` can provide policy signing, but minting YAML cannot use that reference as the source of minting policy material.

Current state:

- `MintingIntent` requires a runtime `Script` or `script_hex` plus `script_type`.
- `ScriptMintingIntent` carries a `policyId`, assets, redeemer, and optional receiver/datum.
- `SignerBinding` exposes `signerFor(...)`, `asWallet()`, and `preferredAddress()`, but does not expose a `Policy`, `Script`, or policy id.

This should be handled as a follow-up QuickTx design item, not folded into the TxFlow execution-context change. A likely approach is a backward-compatible default method on `SignerBinding`, such as `Optional<Policy> asPolicy()`, or a separate policy resolver interface. Then `policy_ref` can be added to minting intents and resolved during composition.

This follow-up is now documented separately in `quicktx/adr/policy-references-for-minting.md`.

### YAML Format Detection

Consumers currently infer TxPlan vs TxFlow by checking for `transaction:` or `flow:`. Add public helpers rather than requiring every consumer to duplicate this logic:

- `TxFlow.isTxFlowYaml(String yaml)`
- `TxPlan.isTxPlanYaml(String yaml)`
- or a shared `YamlDocumentType.detect(String yaml)`

A mandatory discriminator field is not part of this ADR because it would require a broader schema-version decision.

### Documentation Drift

While reviewing the issue, the TxFlow guide was found to mention APIs that are not present in the current code, including `TxFlow.Builder.withVersion(...)`, `FlowExecutor.withConfirmationTimeout(...)`, and `FlowExecutor.withCheckInterval(...)`. Documentation should be updated during implementation so YAML examples, builder APIs, and executor options match.

The new YAML `confirmation:` block is the declarative replacement for the phantom `withConfirmationTimeout(...)` and `withCheckInterval(...)` examples. Do not add those executor methods only to satisfy stale documentation unless there is a separate API need.

## Alternatives Considered

### Put Execution Context Under `flow:`

```yaml
flow:
  id: example
  context:
    chaining_mode: BATCH
```

This avoids a second top-level `context`, but it diverges from TxPlan's top-level context pattern and makes execution metadata look like part of the flow definition rather than document-level runtime semantics.

Decision: reject. Use top-level `context:` for consistency with TxPlan.

### Use `execution:` Instead Of `context:`

```yaml
execution:
  chaining_mode: BATCH
```

This is less ambiguous, but it creates a new naming convention and weakens the TxPlan/TxFlow parallel. The ambiguity is manageable because the scopes differ: document-level `context` means execution context; step-level `context` means transaction context.

Decision: reject for now. Use `context:` and document the scope difference clearly.

### Apply Flow Context By Mutating `FlowExecutor`

This is the smallest implementation mechanically, but it is unsafe for concurrent async flows and shared executors.

Decision: reject. Use per-execution effective settings.

## Implementation Plan

1. Add a TxFlow execution-context model.
   - Add `FlowExecutionSettings` in the public TxFlow model/API package.
   - Avoid model dependencies on `txflow.exec` types; resolve package ownership for `ConfirmationConfig` and `RollbackStrategy` before wiring the model.
   - Add builder methods on `TxFlow.Builder` such as `withContext(...)`, `withChainingMode(...)`, and optional focused helpers if they match local style.
   - Preserve explicit presence of settings.

2. Extend `FlowDocument`.
   - Add top-level `context`.
   - Add YAML DTOs for confirmation, retry, and enum values using boxed nullable fields.
   - Convert domain context to and from YAML.
   - Round-trip context in `TxFlow.toYaml()` and `TxFlow.fromYaml()`.
   - Reject root-level `context.max_rollback_retries`; only accept the canonical nested confirmation field.
   - Define `confirmation:`, `confirmation: null`, and `confirmation: {}` as invalid.

3. Refactor `FlowExecutor` effective settings.
   - Track explicit executor overrides for chaining mode, confirmation config, rollback strategy, and default retry policy.
   - Build immutable effective settings for each `execute`, `executeSync`, `resume`, and `resumeSync` invocation.
   - Validate effective settings before execution.
   - Use effective settings for mode dispatch, retry fallback, confirmation timeout/check interval, rollback retry limit, post-rollback waits, and confirmation tracker creation.
   - Move confirmation tracker creation to the per-execution settings path; the shared executor tracker must not drive execution.

4. Add tests.
   - YAML parse of top-level `context.chaining_mode`.
   - YAML round-trip of all context fields.
   - Stable `toYaml()` ordering with top-level `context` as a sibling of `flow`.
   - Case-insensitive enum parsing.
   - `confirmation` preset and inline configuration parsing.
   - `confirmation` absent vs `confirmation: defaults` vs invalid empty confirmation.
   - Executor precedence: explicit executor setting overrides YAML context.
   - Whole-object confirmation precedence with no executor/YAML field merge.
   - Flow context controls execution when executor setting is absent.
   - Step retry overrides flow default retry.
   - Strict parsing for flow retry, step retry, and dependency strategy.
   - Non-default rollback strategy without confirmation fails before execution.
   - Concurrent async flows with different contexts do not contaminate each other.

5. Update docs.
   - Add TxFlow YAML examples with flow-level `context:`.
   - Explain top-level execution context vs per-step transaction context.
   - Document precedence.
   - Remove or fix stale API references found during review.

6. Open follow-up work.
   - QuickTx `policy_ref` for minting intents (see `quicktx/adr/policy-references-for-minting.md`).
   - Public YAML format detection helper.
   - Optional `extensions:` map support for layered tools if needed after this schema change.

## Consequences

Positive:

- TxFlow YAML becomes self-contained for execution behavior.
- `BATCH` and `PIPELINED` flows can be expressed declaratively.
- Yaci DevKit and similar tools no longer need custom top-level directives for TxFlow execution settings.
- Round-tripping a YAML-loaded flow preserves its execution intent.

Tradeoffs:

- `context:` now has different meanings at different scopes. Documentation and examples must be explicit.
- `FlowExecutor` needs a scoped settings refactor rather than a simple parser change.
- Confirmation tracking and rollback settings become part of YAML, so validation errors must be precise and early.

Out of scope:

- Changing the TxPlan transaction context schema.
- Implementing minting `policy_ref`.
- Adding a mandatory schema discriminator.
- Adding a generic extension namespace.
