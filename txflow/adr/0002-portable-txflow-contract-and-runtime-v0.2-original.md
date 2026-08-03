# ADR 0002: Portable TxFlow Contract, Compilation, Execution, and Recovery

**Status**: Proposed

**ADR Document Version**: 0.2.0

**Date**: 2026-07-10

**Last Updated**: 2026-07-10

**Review State**: Architecture review update

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

### Versioning Rules For This ADR

- Increment the patch version for clarifications, corrections, examples, and editorial changes that do not change a proposed decision.
- Increment the minor version when a proposed API, schema element, compatibility rule, or implementation phase changes while the ADR remains `Proposed`.
- Increment the major version when an accepted decision is replaced with an incompatible decision.
- Add a row to the version history for every version change. Do not replace earlier history entries.
- Record unresolved reviewer disagreements in the Open Questions section until the review reaches a decision.
- Suggested status progression is `Proposed` -> `Accepted` -> `Implementing` -> `Implemented`. Use `Rejected` or `Superseded` where appropriate.

## Executive Summary

TxFlow already provides a useful Cardano-specific runtime for ordered multi-transaction workflows. It supports transaction chaining, three execution modes, confirmation tracking, rollback handling, retry policies, listeners, asynchronous handles, a registry, state-store abstractions, Java-first transaction factories, and YAML-backed `TxPlan` steps.

ADR 0001 adds flow-level execution settings to YAML and correctly introduces per-execution effective settings. That work should be retained. The next architectural step is to make TxFlow a stable contract that can be authored outside Java, sent to a server, validated without side effects, constrained by server policy, executed repeatedly, observed through portable events, and safely recovered after process failure.

The current design is not yet sufficient for that use case because:

- `depends_on` makes previous outputs available to coin selection but does not guarantee their consumption;
- several Java models serialize to YAML with silent semantic loss;
- variables are substituted into raw YAML text rather than bound as typed values;
- flow-definition identity and execution identity are conflated;
- parsing, validation, binding, capability resolution, and execution are not separate stages;
- portable result, event, error, and lifecycle models are incomplete;
- persistence captures some transitions but does not provide a complete recovery protocol;
- retries classify failures using message text and do not model uncertain submission outcomes;
- YAML-requested behavior is not evaluated through a server policy abstraction;
- QuickTx script references cannot currently be supplied through `FlowExecutor` even though QuickTx has a `ScriptRegistry` integration point.

This ADR proposes the following direction:

1. Keep TxFlow focused on deterministic transaction orchestration rather than becoming a general workflow language.
2. Define a versioned, portable TxFlow document envelope with a published JSON Schema.
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
- Support reusable flow definitions with distinct concurrent executions.
- Make dependency and prior-output semantics explicit.
- Support signer, policy, and script references without embedding secrets or script material in YAML.
- Provide deterministic diagnostics with stable codes and document paths.
- Provide portable result and event models suitable for JSON or YAML transport.
- Support crash recovery and transaction reconciliation through CCL-defined primitives.
- Preserve existing preview users through a documented compatibility and migration path.
- Use Java 17 as the required Java runtime.

## Non-Goals

- Implement an HTTP server, REST API, message queue consumer, database, or distributed scheduler in CCL.
- Turn TxFlow into a general-purpose workflow language with loops, arbitrary scripts, or unrestricted expressions.
- Guarantee atomic execution of multiple Cardano transactions. Partial on-chain success is an inherent possibility and must be represented explicitly.
- Store private keys, secrets, or credentials inside a TxFlow document or execution snapshot.
- Hide Cardano transaction concepts from authors. The portable model should simplify orchestration without pretending that transaction inputs, outputs, confirmation, rollback, and signing do not exist.
- Remove the current TxFlow APIs in the same release that introduces the new APIs.

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

## Gap Summary

| ID | Priority | Gap | Existing consequence | Target outcome |
|----|----------|-----|----------------------|----------------|
| GAP-01 | Critical | Dependency availability is mistaken for dependency consumption | A step may declare `depends_on` but consume unrelated base UTXOs | Separate ordering from explicit previous-output references |
| GAP-02 | Critical | YAML serialization can silently lose transaction semantics | Java factories, filters, and multi-transaction plans do not round-trip | Serialization is lossless or fails |
| GAP-03 | Critical | YAML is not a fully versioned public contract | Version validation is not part of normal parsing; no discriminator or schema | Versioned envelope, format detection, JSON Schema, conformance fixtures |
| GAP-04 | Critical | Raw text variable substitution | Types and document structure can change during substitution | Typed parameters and model/node-level binding |
| GAP-05 | Critical | Definition ID is also used as run ID | Reusable/concurrent executions and durable correlation are ambiguous | Separate definition and execution identities |
| GAP-06 | Critical | Result state is too coarse | Built/submitted steps can appear completed before confirmation | Portable step-attempt lifecycle and structured results |
| GAP-07 | Critical | Persistence is not a complete recovery protocol | Crash windows and documented recovery APIs do not match implementation | Versioned snapshots/journal, reconciliation, and resume APIs |
| GAP-08 | High | Resource resolution is incomplete | TxFlow cannot supply the QuickTx `ScriptRegistry` path | Unified resource catalog/resolver and preflight |
| GAP-09 | High | Executor and `TxPlan` contain shared mutable state | Reuse across executors or concurrent runs can contaminate execution | Immutable engine and compiled plan; run-scoped state |
| GAP-10 | High | Retry classification is message-based | Permanent failures may retry and uncertain submissions may rebuild | Typed failure categories and reconciliation-aware retry |
| GAP-11 | High | YAML execution settings are not constrained through policy | A submitted document can request excessive retries, waits, or unsafe modes | Authoritative `FlowExecutionPolicy` with effective-settings output |
| GAP-12 | High | Validation is graph-focused and late | Empty plans, unresolved resources, and invalid transaction content can fail during execution | Multi-stage compiler with structured diagnostics |
| GAP-13 | Medium | `context` is overloaded at two scopes | Non-Java authors must infer two unrelated meanings | Canonical `execution` and `transaction.context` scopes |
| GAP-14 | Medium | Documentation describes APIs that do not exist | Users cannot rely on the guide as a contract | Generated/reference documentation and compile-tested examples |
| GAP-15 | Medium | Registry and cancellation are process-local and incomplete | Cancelled/exceptional executions can be difficult to reconcile | Execution-aware cancellation token and portable terminal events |
| GAP-16 | Medium | Current model resembles a DAG but executes an ordered list | Users may assume parallel scheduling or general graph behavior | Explicitly define an ordered transaction graph for the first portable version |
| GAP-17 | Critical | Rollback monitoring and rebuild semantics are incomplete | Detection stops outside the active wait, sequential restart can repeat confirmed business actions, rollback can surface as timeout, and shallow in-block transactions can be skipped as confirmed | Flow-scoped monitoring, typed rollback outcomes, persisted reconciliation, invalidated-closure rebuild, and strict cross-mode tests |

### Evidence In The Current Implementation

- GAP-01: [`FlowUtxoSupplier.resolvePendingUtxosForAddress`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowUtxoSupplier.java#L120) combines dependency outputs with base UTXOs and logs/continues when a required dependency cannot be resolved. [`findPendingUtxo`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowUtxoSupplier.java#L168) searches all dependency outputs without applying the declared selection strategy.
- GAP-02: [`FlowDocument.fromFlow`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L274) omits Java transaction factories, and [`convertTxPlanToStepContent`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L385) reads only the first transaction entry. `DependencyEntry.filter` is declared but is not converted to or from `StepDependency`.
- GAP-03: [`FlowDocument.fromYaml`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L768) does not call [`validateVersion`](../src/main/java/com/bloxbean/cardano/client/txflow/yaml/FlowDocument.java#L793).
- GAP-04: [`VariableResolver.resolve`](../../quicktx/src/main/java/com/bloxbean/cardano/client/quicktx/serialization/VariableResolver.java#L30) performs regular-expression replacement on the full YAML string.
- GAP-05: [`FlowExecutor.executeSync`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L659) keys active execution protection by `flow.getId()`, and persistence helpers use the same value as the state key.
- GAP-06: [`FlowStepResult`](../src/main/java/com/bloxbean/cardano/client/txflow/result/FlowStepResult.java#L19) represents success using a boolean and `FlowStatus.COMPLETED`, while pipelined and batch execution create successful step results before deep confirmation.
- GAP-07: [`FlowStateStore`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/store/FlowStateStore.java#L27) describes recovery-oriented storage, while the recovery example in [`DESIGN_AND_USAGE.md`](../docs/DESIGN_AND_USAGE.md#L697) calls a non-existent `resumeTracking` method.
- GAP-08: QuickTx provides [`compose(TxPlan, SignerRegistry, ScriptRegistry)`](../../quicktx/src/main/java/com/bloxbean/cardano/client/quicktx/QuickTxBuilder.java#L258), but TxFlow execution currently calls the signer-only overload.
- GAP-09: [`FlowExecutor.executeStepSequential`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L1621) adds missing flow variables directly to the step's mutable `TxPlan`.
- GAP-10: [`RetryPolicy.isRetryable`](../src/main/java/com/bloxbean/cardano/client/txflow/RetryPolicy.java#L162) checks error-message substrings and retries unknown exceptions by default.
- GAP-12: [`TxFlow.validate`](../src/main/java/com/bloxbean/cardano/client/txflow/TxFlow.java#L101) validates graph relationships but does not implement the transaction-definition validation stated in its Javadoc.
- GAP-15: [`FlowHandle.cancel`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowHandle.java#L192) cancels the result future, which is not itself the task submitted to the executor.
- GAP-17: [`ConfirmationTracker.waitForConfirmation`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/ConfirmationTracker.java#L212) monitors one transaction only until that blocking call returns. [`FlowExecutor.doExecuteSequential`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L704) starts again at step zero after `REBUILD_ENTIRE_FLOW`, while [`findStillConfirmedSteps`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L1143) treats block presence as confirmed without enforcing the configured depth. [`waitForConfirmationWithTracking`](../src/main/java/com/bloxbean/cardano/client/txflow/exec/FlowExecutor.java#L1276) returns an empty value for some rollback strategies, which the sequential caller converts to `ConfirmationTimeoutException`.
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
api_version: ccl.bloxbean.com/txflow/v2alpha1
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

The new schema will use a flat step list:

```yaml
steps:
  - id: first
    transaction: ...
  - id: second
    needs: [first]
    transaction: ...
```

The redundant `- step:` wrapper remains supported only by the version 1 compatibility decoder.

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
String canonicalYaml = codec.write(flow, FlowFormat.YAML);
String canonicalJson = codec.write(flow, FlowFormat.JSON);
```

Proposed supporting types:

```java
enum FlowDocumentType { TX_FLOW, TX_PLAN, UNKNOWN }
enum FlowFormat { YAML, JSON }

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
- `TxFlow.toYaml()` writes version 1 while the flow originated from version 1, unless the caller explicitly migrates it.
- `TxFlowCodec.write(..., V2)` is the only canonical version 2 writer.
- Unsupported versions, duplicate keys, multiple YAML documents, and invalid document kinds fail before model construction.
- Publish `txflow-v2alpha1.schema.json` as a module resource and release artifact.

### Decision 2: Separate Definition Identity From Execution Identity

#### Existing Java API

```java
TxFlow flow = TxFlow.builder("monthly-distribution").build();
FlowHandle handle = executor.execute(flow);
```

`flow.getId()` is used by active execution tracking, the registry, results, and the state store.

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

- Legacy execution APIs derive an execution ID internally.
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

`TxFlow.validate()` remains for lightweight graph validation but is no longer presented as complete executable validation.

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

The current implementation extracts the variable map and replaces `${...}` in the entire YAML string before deserializing the document.

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

If sealed public interfaces are considered too restrictive for extension, use a normal interface plus registered reference types.

#### Compatibility

- Version 1 `depends_on` remains supported as an ordering dependency plus legacy pending-UTXO visibility.
- The compiler emits `TXFLOW_LEGACY_IMPLICIT_INPUT` when version 1 dependency behavior is used.
- Version 2 never interprets `needs` as input consumption.
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

The current serializer logs that the factory cannot be serialized and emits a step without transaction content.

A `FlowStep` may also hold a `TxPlan` containing multiple transactions, while the current step serializer reads only the first transaction from the serialized plan. Java `Predicate<Utxo>` filters cannot be serialized. Conversely, YAML without `tx` can produce an empty but non-null `TxPlan`.

#### Proposed Java API

```java
FlowPortabilityResult portability = codec.checkPortable(flow);

if (!portability.isPortable()) {
    // diagnostics include step and reason
}

String yaml = codec.write(flow, FlowFormat.YAML); // throws FlowEncodingException on loss
```

The existing convenience API changes from silent omission to failure:

```java
flow.toYaml(); // fails if any step is not portable
```

For intentional Java-only flows:

```java
FlowExecutor.create(backendService).executeSync(javaOnlyFlow); // remains supported
codec.write(javaOnlyFlow, FlowFormat.YAML);                    // rejected
```

#### Required Invariants

- A portable flow step contains exactly one transaction plan.
- A Java transaction factory is executable but not portable.
- Every selector and retry/confirmation setting has a serialized representation.
- Encoding never catches an exception, logs it, and continues with partial content.
- `decode(encode(flow))` preserves compiled execution semantics.
- Property-based and golden-fixture tests enforce semantic round trips.

### Decision 7: Make The Definition And Compiled Plan Immutable

#### Existing API

`TxFlow` wraps its top-level collections, but `TxPlan` and nested values remain mutable. During execution, flow variables can be added directly to a step's `TxPlan`.

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

No execution path mutates the source `TxFlow` or its `TxPlan`. Existing mutable `TxPlan` APIs remain available to construct plans, but compilation takes a defensive snapshot.

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

TxFlow currently calls only the signer-registry overload.

#### Proposed Java API

Short-term compatibility addition:

```java
FlowExecutor executor = FlowExecutor.create(backendService)
        .withSignerRegistry(signerRegistry)
        .withScriptRegistry(scriptRegistry);
```

Preferred unified API:

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

The canonical version 2 name is `execution`, and the canonical field is `mode`. The version 1 `context.chaining_mode` shape remains supported by the compatibility decoder.

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

Documentation currently shows `executor.resumeTracking(snapshot)`, but such an API does not exist. The current snapshot lacks a definition fingerprint, execution ID, attempt history, prepared signed transaction, and output/spent-input data needed for robust recovery. Persistence failures are logged and ignored by the executor.

#### Proposed Java API

```java
interface FlowExecutionStore {
    FlowExecutionSnapshot create(FlowExecutionSnapshot initial);

    AppendResult append(
            FlowExecutionId executionId,
            long expectedRevision,
            List<FlowEvent> events);

    Optional<FlowExecutionSnapshot> load(FlowExecutionId executionId);

    List<FlowExecutionRef> findRecoverable(RecoveryQuery query);

    Optional<ExecutionLease> tryAcquireLease(
            FlowExecutionId executionId,
            String owner,
            Duration leaseDuration);

    void releaseLease(ExecutionLease lease);
}
```

The snapshot includes:

```java
record FlowExecutionSnapshot(
        FlowExecutionId executionId,
        FlowDefinitionRef definition,
        long revision,
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
        byte[] signedCbor,
        List<Utxo> expectedOutputs,
        List<TransactionInput> spentInputs,
        Instant preparedAt) {}
```

Non-sensitive bindings may be stored directly. Sensitive bindings are persisted as references to an application-controlled secure value store, together with a fingerprint and redacted display value. CCL defines the `secureValueRef` field and a recovery-time resolver interface but does not implement a secret store. A snapshot must retain enough binding information to compile or resume unbuilt later steps; redaction alone is not sufficient for recovery.

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
7. If absent and submission was never attempted, submit the prepared transaction.
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

The server-oriented default is `PAUSE_FOR_RECOVERY` or `FAIL_EXECUTION`, not unconditional warn-and-continue.

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

The current implementation classifies several errors using message substrings and retries unknown exceptions by default.

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

`maxAttempts` is explicitly defined as total attempts including the initial attempt. A separate `maxRetries` name is not introduced unless compatibility review decides the current name is too ambiguous.

#### Compatibility

- `RetryPolicy.isRetryable(Throwable)` remains as a legacy adapter.
- Internal CCL failures are mapped to stable categories before policy evaluation.
- Unknown failures default to `FAIL` in server mode and may retain the current behavior in legacy mode.

### Decision 13: Treat Rollback As A Persisted Reconciliation Process

Rollback is not a normal step retry. It is a change in the observed ledger status of one or more submitted transaction attempts. The engine must pause forward progress, record the observation, reconcile all affected attempts, and only then decide whether to wait, fail, resubmit identical signed bytes, or rebuild part of the flow.

#### Verification Status At ADR Version 0.2.0

The focused audit used Java 17 and covered the current rollback/retry source paths plus these unit-test groups:

- `RetryPolicyTest`;
- `ConfirmationTrackerTest`;
- `FlowExecutorTest`;
- `FlowExecutorResumeTest`;
- `RollbackExceptionTest`.

Those focused tests pass. The Yaci DevKit rollback suite was inspected but was not executed as part of this ADR update because it requires a running local DevKit and mutates that DevKit by taking snapshots and rolling back its chain. Existing integration scenarios are not treated as final acceptance evidence because some allow more than one outcome when the rollback races confirmation, and some accept a rebuild failure caused by UTXO availability.

#### Current Rollback And Retry Findings

| ID | Severity | Current behavior | Risk | Required correction |
|----|----------|------------------|------|---------------------|
| RB-01 | Critical | Sequential `REBUILD_ENTIRE_FLOW` creates a new context and starts again at step zero | An earlier transaction that remains on chain can be rebuilt and the business action can be duplicated | Reconcile every prior attempt and retain the still-valid prefix before any rebuild |
| RB-02 | Critical | Tracking is a blocking wait for one transaction and stops when the target status is returned | A rollback of an earlier flow transaction may not be observed while a later step is running | Maintain a run-scoped monitor set until the configured monitoring horizon |
| RB-03 | Critical | `FAIL_IMMEDIATELY` and exhausted `NOTIFY_ONLY` return an empty optional | Rollback is reported as confirmation timeout and rollback persistence hooks are bypassed | Return a typed rollback outcome and persist it before strategy evaluation |
| RB-04 | Critical | Retry wraps build, sign, submit, and confirm as one operation | An accepted submission with a lost response can cause rebuild/resubmission and duplicate intent | Persist the prepared hash and reconcile an unknown submission outcome before retry |
| RB-05 | High | Pipelined restart treats transaction presence plus block height as confirmed | A shallow transaction can be skipped without satisfying `minConfirmations` | Recompute depth against a recorded chain point and enforce effective confirmation policy |
| RB-06 | High | Rollback invalidation is inferred mainly from declared step dependencies | Ordering-only dependencies and actual UTXO consumption are conflated | Compute invalidation from prepared transaction inputs and explicit flow-output references |
| RB-07 | High | Backend query exceptions and absence have an imprecise model | Provider lag or outage can be mistaken for a ledger decision | Represent `UNKNOWN` separately; unknown observations never trigger rebuild |
| RB-08 | High | Generic retry classification is message-based and defaults unknown failures to retryable | Permanent or wrapped failures may be retried; JVM errors can be misclassified | Use phase-aware typed categories and default unknown failures to fail in server mode |
| RB-09 | Medium | Confirmation and rollback paths use blocking sleep | Cancellation and high-concurrency server execution are less responsive | Use a scheduler/clock abstraction with cancellation-aware waits |
| RB-10 | Medium | Exponential delay can overflow and has no jitter | Extreme YAML values can produce invalid delays or synchronized retry bursts | Validate bounds, saturate arithmetic, and apply policy-capped jitter |

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

This version 1 shape remains readable during the compatibility window. The backend-restart fields are test-environment concerns and must not be carried into the canonical portable rollback contract.

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

| Existing `RollbackStrategy` | Version 2 effective behavior | Compatibility notes |
|-------------------------------------|------------------------------|---------------------|
| `FAIL_IMMEDIATELY` | `FAIL` | Produces rollback-specific state/error, never confirmation timeout |
| `NOTIFY_ONLY` | `WAIT_FOR_REINCLUSION` | Existing listener notification remains; waiting is bounded by reinclusion and execution timeouts |
| `REBUILD_FROM_FAILED` | `RECONCILE_AND_REBUILD + AFFECTED_STEP` | Compiler/runtime upgrades scope to invalidated closure if any prepared or submitted descendant consumes the attempt |
| `REBUILD_ENTIRE_FLOW` | `RECONCILE_AND_REBUILD + INVALIDATED_CLOSURE` | The definition is re-evaluated, but still-valid attempts are retained and are never blindly executed again |

`RollbackStrategy` remains available as a legacy adapter. New documentation uses `RollbackPolicy`. The semantic correction for `REBUILD_ENTIRE_FLOW` is intentional: “entire flow” means reconcile the entire flow, not resubmit every prior business action.

#### Required Safety Invariants

1. No build, sign, or submission may occur while an execution is in `RECONCILING`.
2. An `UNKNOWN` observation never proves rollback, absence, or permission to rebuild.
3. A locally computed transaction hash and signed CBOR are persisted before the first submission call.
4. An uncertain submission is reconciled by hash before any rebuild or resubmission.
5. If identical signed CBOR remains valid, resubmitting those same bytes is preferred over rebuilding because it preserves the hash and transaction identity.
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

1. The engine persists the signed CBOR and hashes for `txA` and `txB` before submission.
2. Both become `IN_BLOCK`. The monitor observes `txA` disappear while the flow is still active.
3. The engine persists `TRANSACTION_ROLLED_BACK(txA)`, pauses submissions, and reconciles both hashes.
4. If `txA` is re-included with the same hash and `txB` remains valid or is also re-included, no transaction is rebuilt.
5. If both remain absent but the signed bytes are still valid, the engine resubmits the identical CBOR in dependency order. The hashes remain `txA` and `txB`.
6. If `txA` can no longer be submitted because its input was consumed elsewhere, the engine marks its attempt superseded, rebuilds `fund-staging` as `txA2`, then rebuilds `forward-payment` as `txB2` because it consumes the changed output.
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

#### Rollback Implementation Workstream

This workstream is ordered so immediate safety corrections can land before the complete durable engine:

**RB Phase 0 — Contract And Deterministic Harness**

1. Approve the terms, compatibility mapping, monitoring horizon, state transitions, and safety invariants in this section.
2. Add a deterministic fake chain/clock/scheduler capable of inclusion, depth increase, authoritative absence, re-inclusion, backend uncertainty, and process interruption.
3. Convert rollback tests from race-dependent timing assertions to scripted observations with one required outcome.

Exit criteria: every strategy has deterministic tests and no test accepts both success and failure for the same script.

**RB Phase 1 — Current API Safety Corrections**

1. Replace empty optional rollback paths with a typed internal outcome.
2. Ensure `FAIL_IMMEDIATELY` persists and returns rollback, never timeout.
3. Remove duplicate sequential confirmation waits and persist `SUBMITTED` immediately after submission.
4. Verify configured confirmation depth before retaining/skipping a transaction.
5. Reconcile and retain the valid sequential prefix instead of restarting at step zero.
6. Validate retry and rollback limits, use saturating delay arithmetic, add jitter, and make waits cancellation-aware.
7. Do not retry `Error`; inspect the typed cause chain and default unknown server-mode failures to fail.

Exit criteria: the current facade cannot blindly repeat a confirmed prefix, mislabel rollback as timeout, or rebuild on an unknown backend observation.

**RB Phase 2 — Flow-Scoped Monitor And Invalidated Closure**

1. Add a run-scoped rollback coordinator that monitors all eligible attempts through flow termination.
2. Introduce chain-point-aware `TransactionObservation` and backend reconciliation adapters.
3. Persist actual spent inputs and produced outputs for every prepared attempt.
4. Compute invalidation from transaction data and explicit flow-output references.
5. Implement consistent behavior for sequential, pipelined, and batch modes.

Exit criteria: rollback of an earlier transaction is detected while a later step is active, and only the invalidated closure is resubmitted/rebuilt.

**RB Phase 3 — Durable Recovery**

1. Persist signed CBOR/hash before submission and `SUBMITTING` before the backend call.
2. Persist every observation, decision, attempt replacement, and recovery counter with revision checks.
3. Implement process restart at every rollback/retry boundary.
4. Add execution leases and ensure only one recovery coordinator mutates an execution.
5. Implement `PAUSE_FOR_RECOVERY` and external-signature recovery.

Exit criteria: a process stop at any lifecycle boundary cannot cause blind resubmission, lost rollback history, or two concurrent rebuilders.

**RB Phase 4 — Portable Contract And Compatibility**

1. Add `RollbackPolicy` to the Java model, YAML/JSON schema, compiler, policy evaluator, result, and event codecs.
2. Add the version 1 strategy adapter and migration diagnostics.
3. Remove DevKit backend-restart fields from canonical version 2 YAML; retain them only as test-harness/runtime adapter configuration.
4. Publish rollback and retry conformance fixtures for non-Java clients.

Exit criteria: Java and YAML requests compile to the same effective rollback policy and portable events/results round-trip without semantic loss.

**RB Phase 5 — Strict Integration Verification**

1. Run the full deterministic matrix on every Java 17 build.
2. Run isolated Yaci DevKit tests for all strategies and modes with explicit snapshot setup/cleanup.
3. Test shallow and deeper rollback, same-hash re-inclusion, changed transaction rebuild, backend restart, indexer lag, cancellation, and recovery exhaustion.
4. Fail tests when the intended rollback was not observed; never accept “transaction confirmed before rollback” as coverage of a rollback case.
5. Record exact preconditions, chain points, transaction hashes, and event sequences to make failures reproducible.

Exit criteria: the strict matrix passes on Java 17, no test has permissive rollback outcomes, and integration results match persisted event histories.

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

The fluent setters mutate the shared executor. ADR 0001 snapshots execution settings, but listener, registries, inspector, state store, and executor remain shared mutable fields.

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

Cancellation sets `CANCEL_REQUESTED`, signals a run-scoped token, interrupts an owned task when safe, persists the request, and eventually records `CANCELLED` or the actual terminal state. It never claims that an already submitted transaction was undone.

#### Compatibility

- `FlowExecutor` remains available and delegates to `FlowEngine` where practical.
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

Move public configuration types to a public model/configuration package during the preview window:

```java
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
```

Executor-internal runtime types remain under `txflow.exec`. Deprecated forwarding types or factory methods can ease migration if binary compatibility requirements demand them.

### Decision 17: Make Documentation And Conformance Artifacts Part Of The Contract

#### Existing Documentation API Examples

The current guide mentions methods such as:

```java
TxFlow.Builder.withVersion(...)
FlowExecutor.withConfirmationTimeout(...)
FlowExecutor.withCheckInterval(...)
FlowExecutor.resumeTracking(...)
FlowStep.Builder.dependsOnChange(...)
```

These methods are not present in the current code. The guide also lists `SelectionStrategy.CHANGE`, which is not present.

#### Proposed Documentation And Tooling API

Ship and test:

- versioned JSON Schemas;
- a complete YAML reference;
- minimal, payment, minting, script, chained-output, rollback, and recovery examples;
- a version 1 to version 2 migration guide;
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
ccl txflow migrate --from v1 --to v2alpha1 flow.yaml
ccl txflow canonicalize flow.yaml
```

The CLI itself is not approved by this ADR; only the reusable library primitives are approved.

## End-To-End Portable Example

This example shows a reusable definition authored by a non-Java developer. The first transaction funds a staging account. The second transaction explicitly consumes the named output of the first transaction and pays a runtime-provided beneficiary.

### 1. TxFlow Definition YAML

```yaml
api_version: ccl.bloxbean.com/txflow/v2alpha1
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
- requested retries and confirmation timeouts are within server policy;
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
com.bloxbean.cardano.client.txflow.model
    TxFlow
    FlowStep
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

com.bloxbean.cardano.client.txflow.store
    FlowExecutionStore
    FlowExecutionSnapshot
    StepExecutionSnapshot
    ExecutionLease

com.bloxbean.cardano.client.txflow.recovery
    FlowRecoveryRequest
    FlowRecoveryResult
```

A later ADR may propose separate `txflow-model`, `txflow-codec`, and `txflow-runtime` Gradle modules if dependency weight or non-runtime tooling justifies it.

## Compatibility And Migration Strategy

### Version 1 Documents

- Continue accepting the current `version: "1.0"` format.
- Apply current flow-level `context` semantics from ADR 0001.
- Decode `flow.variables` as definition defaults.
- Decode `depends_on` using legacy pending-UTXO visibility.
- Emit warnings for implicit input semantics, unused `filter`, empty transaction content, and other ambiguous constructs.
- Reject constructs that currently cause silent loss when writing.

### Version 2 Alpha Documents

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

### Deprecation Timing

No removal schedule is decided in this ADR. Removal requires:

- at least one release with both APIs available;
- a published migration guide;
- compatibility fixtures for version 1 YAML;
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
- cap amounts, fees, deposits, execution duration, retries, confirmation depth, and polling rates;
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

### Phase 0: Review And Contract Freeze

Deliverables:

1. Review and accept or revise this ADR.
2. Confirm that TxFlow remains an ordered transaction graph rather than a general workflow engine.
3. Decide the exact version 2 alpha namespace and naming convention.
4. Decide whether `FlowEngine` is a new public facade or the next form of `FlowExecutor`.
5. Decide package migration and binary compatibility expectations.
6. Mark version 1 YAML as preview or stable explicitly.
7. Review and approve the rollback monitoring horizon, compatibility mapping, reconciliation invariants, and strict acceptance matrix in Decision 13.

Exit criteria:

- no unresolved critical naming or compatibility question;
- ADR version history updated with review changes;
- initial diagnostic-code namespace agreed.

### Phase 1: Correctness Hardening Of Current APIs

Deliverables:

1. Call version validation from the normal decoder.
2. Reject unsupported versions, duplicate keys, multiple documents, and malformed roots.
3. Reject `confirmation: null` and other presence-sensitive invalid values consistently.
4. Require each portable flow step to have exactly one transaction.
5. Reject serialization of Java factories, Java predicates, and multi-transaction step plans.
6. Stop catching encoding failures and continuing with partial YAML.
7. Enforce required dependencies rather than logging and continuing.
8. Remove the unused YAML `filter` field or implement it with `UtxoFilterSpec`.
9. Add `FlowExecutor.withScriptRegistry(...)` and pass both registries to QuickTx.
10. Correct documentation APIs, `SelectionStrategy.CHANGE`, Java version, duplicated examples, and recovery claims.
11. Complete RB Phase 1: return typed rollback outcomes, remove duplicate confirmation waits, verify confirmation depth, retain valid prefixes, and harden retry limits/cancellation.

Tests:

- zero/multiple transaction step tests;
- Java factory encoding rejection;
- predicate/filter encoding rejection;
- version and null-presence tests;
- required dependency failure tests;
- script-reference TxFlow integration tests;
- semantic round-trip tests;
- deterministic rollback tests for the current facade with one required outcome per scenario.

### Phase 2: Codec, Schema, Diagnostics, And Typed Parameters

Deliverables:

1. Add `TxFlowCodec`, parse options, result, and diagnostics.
2. Publish versioned JSON Schema resources.
3. Add document-type detection.
4. Add version 2 alpha DTOs and compatibility decoder.
5. Add `ParameterSpec`, `FlowBindings`, and typed model-level binding.
6. Add parser and model resource limits.
7. Add canonical YAML and JSON writers.
8. Add `RollbackPolicy` DTOs, schema constraints, version 1 strategy mapping, and rollback/retry conformance fixtures.

Tests:

- schema conformance fixtures;
- line/column/document-path diagnostics;
- type-preserving binding;
- interpolation restrictions;
- parser resource-limit tests;
- fuzz and malformed YAML tests;
- version 1 compatibility fixtures.

### Phase 3: Explicit Output References And Compiler

Deliverables:

1. Add `needs` scheduling dependencies.
2. Add named output bindings and declarative selectors.
3. Add `FlowOutputRef` support in transaction input/reference intents.
4. Add `TxFlowCompiler`, compilation request/result, and immutable compiled flow.
5. Validate transaction cardinality, intent content, graph order, references, and mode compatibility.
6. Add definition fingerprinting.
7. Ensure compilation snapshots mutable `TxPlan` input.

Tests:

- `needs` without consumption;
- explicit previous-output consumption;
- selector zero/one/many behavior;
- pipelined and batch resolution from locally built outputs;
- compiler determinism and fingerprint stability;
- Java/YAML compiled-plan equivalence.

### Phase 4: Resource Catalog, Policy, And Preflight

Deliverables:

1. Add unified resource descriptors and resolution.
2. Add signer, policy, script, address, and external-data resolver adapters.
3. Add capability preflight.
4. Add `FlowExecutionPolicy` and requested-to-effective settings evaluation.
5. Add optional backend-aware preflight/dry-run.
6. Add network, amount, action, resource-prefix, and duration policies.
7. Add policy limits for rollback actions, monitoring horizon, recovery cycles, observation threshold, and reinclusion window.

Tests:

- missing and unauthorized resources;
- capability mismatch;
- network mismatch;
- policy rejection and capping;
- redaction of sensitive data;
- policy behavior across all execution modes.

### Phase 5: Immutable Engine And Portable Lifecycle

Deliverables:

1. Add immutable `FlowEngine`.
2. Add `FlowExecutionRequest`, execution ID, idempotency key, and correlation metadata.
3. Add rich execution, step-attempt, result, error, and event models.
4. Add event sequencing.
5. Add run-scoped cancellation.
6. Adapt current `FlowExecutor`, `FlowHandle`, `FlowListener`, and result APIs.
7. Replace message-based internal retry classification with typed categories.
8. Add the run-scoped rollback coordinator, `TransactionObservation`, invalidated-closure planning, and consistent cross-mode semantics from RB Phase 2.

Tests:

- same definition with concurrent execution IDs;
- idempotency-key collision behavior;
- immutable configuration under concurrency;
- cancellation before build, during build, after submit, and during confirmation;
- partial-success result semantics;
- ordered event-sequence tests;
- retry decisions for every lifecycle phase;
- earlier-attempt rollback detection while a later step is active;
- strict sequential, pipelined, and batch rollback behavior.

### Phase 6: Durable State And Recovery

Deliverables:

1. Add versioned `FlowExecutionSnapshot` and `FlowExecutionStore`.
2. Add revision-based appends and optional execution leases.
3. Persist prepared signed transaction/hash before submission.
4. Persist all lifecycle transitions including `IN_BLOCK`.
5. Add recovery reconciliation and resume APIs.
6. Add persistence-failure policy.
7. Provide an in-memory reference store implementing the complete contract.
8. Document database implementation requirements without adding a database dependency.
9. Persist rollback/re-inclusion observations and decisions before callbacks or resumed submissions.
10. Implement the durable rollback recovery and lease behavior from RB Phase 3.

Tests:

- simulated process stop at every lifecycle boundary;
- accepted-but-not-recorded submission;
- mempool/in-block/confirmed/rolled-back recovery;
- concurrent recovery lease contention;
- revision conflict handling;
- definition fingerprint mismatch;
- recovery after policy or resource changes;
- no blind rebuild when a prepared hash may have been submitted;
- process interruption at every rollback reconciliation boundary;
- same-hash re-inclusion and invalidated-closure rebuild histories.

### Phase 7: Documentation And Adoption

Deliverables:

1. Complete YAML reference and end-to-end guides.
2. Version 1 to version 2 migration guide.
3. Compile-tested Java examples.
4. Non-Java author examples for payment, minting, scripts, certificates, and governance where supported by TxPlan.
5. Diagnostic-code catalog.
6. Server integration reference architecture without an actual server implementation.
7. Release and deprecation notes.

## Test And Verification Strategy

The existing unit and integration tests remain valuable. New testing should add these layers:

1. **Codec tests**: schema, malformed inputs, canonical output, resource limits, duplicate keys.
2. **Semantic round trips**: Java -> YAML -> Java and YAML -> Java -> YAML -> Java compare compiled plans, not formatting.
3. **Cross-version fixtures**: immutable version 1 fixtures and version 2 alpha fixtures.
4. **Compiler tests**: all diagnostics, parameter binding, resources, policies, and modes.
5. **Property tests**: generated definitions within supported bounds.
6. **Concurrency tests**: shared engine, different settings, resources, and definitions.
7. **Lifecycle tests**: every transition and partial-success state.
8. **Recovery tests**: crash or interruption between every pair of durable transitions.
9. **Devnet integration tests**: sequential, pipelined, batch, rollback, restart, and confirmation behavior using Yaci DevKit.
10. **Compatibility tests**: current public APIs delegate correctly and emit expected warnings.
11. **Strict rollback matrix**: execute every scenario in Decision 13 with deterministic observations, exact event/state assertions, and no permissive alternate outcomes.
12. **Retry safety tests**: uncertain submission, wrapped permanent errors, backend uncertainty, saturating backoff, jitter bounds, and cancellation during scheduled delay.

All builds and tests for this work use Java 17.

## Acceptance Criteria

This ADR is considered implemented only when:

- every accepted portable definition either round-trips semantically or fails encoding with a diagnostic;
- a Java-only transaction factory is never silently emitted as incomplete YAML;
- a step cannot silently discard extra transactions from a `TxPlan`;
- `needs` and explicit prior-output consumption have distinct semantics;
- required output references fail deterministically when unresolved;
- runtime values are typed and never substituted into raw YAML structure;
- versioned JSON Schema and conformance fixtures ship with the module;
- all parser/compiler errors have stable codes and document paths;
- the same definition can execute concurrently using distinct execution IDs;
- policy constrains all YAML-requested execution settings and transaction capabilities;
- signer, policy, and script references support preflight validation;
- results distinguish built, submitted, in-block, confirmed, failed, rolled-back, and cancelled attempts;
- prepared transaction hashes are durably recorded before submission;
- recovery reconciles uncertain submission before rebuilding;
- rollback is represented by a typed state/error and is never converted to confirmation timeout;
- rollback monitoring covers all eligible attempts through the effective horizon;
- an unknown backend observation never authorizes resubmission or rebuild;
- valid confirmed prefixes are retained and only the invalidated dependency closure is rebuilt;
- same-hash re-inclusion retains the existing attempt and records its new inclusion history;
- rollback recovery, ordinary retry, backend observation, and reinclusion budgets remain independent;
- strict deterministic and Yaci rollback matrices pass without accepting alternate non-rollback outcomes;
- persistence transitions use revisions or equivalent optimistic concurrency;
- cancellation is persisted and does not claim to reverse submitted transactions;
- version 1 compatibility behavior is covered by immutable fixtures;
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
- Existing Java-first flows remain supported.

### Costs And Tradeoffs

- The public surface grows substantially.
- A compatibility decoder and adapters must be maintained.
- QuickTx input-reference models need an extension for flow-output references.
- Deep immutability requires defensive copying or new immutable models around mutable `TxPlan` types.
- Durable recovery increases state-model and test complexity.
- A version 2 alpha schema must be stabilized through real consumer feedback before a stable version is declared.
- Policy cannot guarantee that preflight remains valid after chain state changes.

### Risks

- Attempting all phases in one change would create a high-review-risk rewrite.
- Prematurely declaring version 2 stable could freeze weak naming or incomplete transaction intent coverage.
- Maintaining two APIs indefinitely would increase complexity; deprecation must eventually be resolved.
- A generic expression language could accidentally turn TxFlow into an unsafe workflow engine. This ADR intentionally limits expressions to typed references and string interpolation.

## Alternatives Considered

### Keep The Current YAML And Only Add More Fields

Rejected as the long-term direction. It does not solve identity, typed binding, compilation, output-reference semantics, structured errors, policy, or recovery.

### Treat `depends_on` As Guaranteed Consumption Without Changing The Model

Rejected. The underlying transaction may use coin selection, explicit inputs, script inputs, or reference inputs. Ordering and transaction input binding must be represented separately.

### Embed Transaction Hash Variables From Earlier Steps

Example:

```yaml
tx_hash: ${steps.fund.tx_hash}
output_index: 0
```

Rejected as the primary API. It exposes low-level dynamic variables, remains index-heavy, and does not provide selector cardinality or input-kind semantics. A typed `FlowOutputRef` can compile to a transaction hash and index internally.

### Make The Server Interpret TxFlow Without New CCL APIs

Rejected. Each server would duplicate format detection, binding, policy, diagnostics, resource resolution, lifecycle state, and recovery behavior. Those semantics belong with the CCL transaction engine.

### Turn TxFlow Into A General Workflow Engine

Rejected. General workflow engines already exist, and unrestricted conditions, scripting, and loops would complicate security and determinism. TxFlow should orchestrate a bounded ordered graph of Cardano transactions.

### Replace YAML With JSON Only

Rejected. YAML is an explicit usability requirement. JSON should be supported as an equivalent transport and schema-validation target, not as a replacement.

### Store Only Transaction Hashes For Recovery

Rejected. A hash alone cannot always reconstruct expected outputs, spent inputs, signed transaction bytes, attempt policy, or whether submission was attempted. The prepared transaction record is required for robust reconciliation.

### Continue With Mutable `FlowExecutor` Only

Rejected for the server-facing API. Per-execution settings solved one race, but shared mutation remains possible for registries, listeners, inspectors, and state stores. An immutable engine makes configuration ownership clear.

## Open Questions For Review

1. Should the canonical schema namespace be `ccl.bloxbean.com/txflow/v2alpha1`, `bloxbean.com/txflow/v1alpha1`, or another form?
2. Should the next schema be called `v2alpha1` because a `version: "1.0"` format exists, or `v1alpha1` because the current document was never declared stable?
3. Should `FlowEngine` be a new facade, or should `FlowExecutor` itself move to an immutable builder API?
4. Should `TxFlow` remain the definition model name, or should a new `FlowDefinition` type be introduced with `TxFlow` as a compatibility facade?
5. Should public config types move to `txflow.config`, or remain in the base `txflow` package?
6. Should `TxInputRef` be a sealed interface, an extensible interface, or a single tagged value type?
7. Is named-output selection best modeled at `FlowStep`, on individual output-producing QuickTx intents, or both?
8. Which parts of `UtxoFilterSpec` are safe and stable enough to expose in the TxFlow schema?
9. Should policy capping produce warnings and continue, or should any difference between requested and effective settings require explicit caller acknowledgement?
10. What is the required binary compatibility policy during the current pre-release series?
11. Should signed transaction CBOR always be stored for recovery, or should the store contract allow an encrypted/external blob reference?
12. Which backend states can be portably distinguished across Blockfrost, Koios, Yaci, and custom suppliers?
13. Should idempotency be defined only within one definition version, or across compatible versions with the same fingerprint?
14. Should independent steps ever execute concurrently in a future version, or should ordering remain fully explicit?
15. Which current version 1 ambiguities should be warnings versus immediate errors?
16. Should `UNTIL_FLOW_TERMINAL` be mandatory in server mode, or may server policy allow the legacy `UNTIL_STEP_CONFIRMED` horizon with a warning?
17. Should authoritative backends be allowed to detect rollback from one absence observation, or should the portable minimum always be two consistent observations?
18. When `WAIT_FOR_REINCLUSION` expires, should the default be `RECOVERY_REQUIRED` or a terminal rollback failure?
19. Is the compatibility interpretation of `REBUILD_ENTIRE_FLOW`—reconcile the whole flow but rebuild only the invalidated closure—acceptable, or should the legacy name be deprecated immediately to avoid ambiguity?
20. Which backend adapters can distinguish mempool absence from chain absence strongly enough to support identical-CBOR resubmission without an additional provider-specific capability?

## Review Checklist

Reviewers should explicitly confirm or request changes for:

- [ ] TxFlow scope as an ordered Cardano transaction graph
- [ ] versioned document envelope and schema naming
- [ ] definition/execution identity separation
- [ ] typed parameter and binding model
- [ ] `needs` versus explicit flow-output references
- [ ] lossless portability rules
- [ ] compiler and diagnostics API
- [ ] resource catalog and capability model
- [ ] execution policy ownership and capping behavior
- [ ] immutable `FlowEngine` direction
- [ ] lifecycle, result, event, and error models
- [ ] durable store, lease, and recovery model
- [ ] retry and uncertain-submission semantics
- [ ] rollback terms, monitoring horizon, and backend-uncertainty boundary
- [ ] rollback compatibility mapping and invalidated-closure rebuild semantics
- [ ] rollback portable policy, observation, event, state, and error APIs
- [ ] strict rollback/retry acceptance matrix and Yaci DevKit verification plan
- [ ] package ownership changes
- [ ] version 1 compatibility and deprecation strategy
- [ ] phased implementation plan
- [ ] Java 17 requirement
