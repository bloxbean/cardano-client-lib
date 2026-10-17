# ADR 0008: Generic QuickTx Extension Support in TxFlow

**Status**: Proposed

**ADR Document Version**: 1.0.0

**Date**: 2026-10-17

**Last Updated**: 2026-10-17

**Review State**: Initial review draft (review round 1)

**Target Release**: To be decided

**Modules**: `txflow`, `quicktx` (one additive codec API); `programmable-token` only for the CIP-113 conformance follow-up (M1b)

**Issue**: [#700](https://github.com/bloxbean/cardano-client-lib/issues/700). Follow-up to [#657](https://github.com/bloxbean/cardano-client-lib/pull/657).

**Related ADRs**: [ADR 0002: Portable TxFlow Contract](0002-portable-txflow-contract-and-runtime.md) (transaction schema ownership, lossless portability, fingerprinting), [ADR 0004: TxFlowStream on FlowEngine](0004-txstream-on-flow-engine.md), [CIP-113 QuickTx extension review](../../adr/cip113/001-cip113-quicktx-extension-and-txplan-design-review.md)

**Supersedes**: None

**Risk**: Medium. The public API changes are additive and opt-in, but the design touches plan isolation, compiled-definition fingerprints, the portable schema and the trust boundary between an untrusted flow document and the extensions the operator registers.

All code references are to `origin/master` at `dfd8540d`.

## Revision History

- **r1** (2026-10-17): Initial proposal.

## 1. Context

### 1.1 What QuickTx already provides

QuickTx has a generic, experimental extension SPI. A TxFlow design can be generic because QuickTx already is:

| Piece | Location | What it does |
|---|---|---|
| `QuickTxExtension` | `quicktx/.../extension/QuickTxExtension.java` | Declares `id()`, `schemaVersion()`, `operations()`, `intentTypes()` (operation → intent class), `metadata()`, `validateMetadata(...)` and `newBuildExtension(metadata)`. |
| `ExtensionIntent` | `quicktx/.../extension/ExtensionIntent.java` | Typed declarative intent. Its type id is `<extension-id>:<operation>` in memory and `<namespace>:<operation>` in a document. It is planned in aggregate by the build participant, not applied on its own. |
| `ExtensionMetadata` | `quicktx/.../extension/ExtensionMetadata.java` | Immutable, serializable binding: `extension`, `schema_version`, `protocol`, `contract_version`, `deployment`. Map keys are sorted, so equal metadata serializes identically. |
| `TxBuildExtension` + `ExtensionBuildContext` | `quicktx/.../extension/` | Per-build participant hooks (`prepare`, `beforeScriptEvaluation`, `afterBalance`, `verify`). The context exposes the build's `UtxoSupplier`. |
| `QuickTxBuilder.withExtension` | `QuickTxBuilder.java:98-108` | Builder-scoped registration, keyed by extension id. `compose(TxPlan)` checks every declared namespace against the registered extensions and calls `validateMetadata` (`:225-241`). |
| `TxPlanCodec` | `quicktx/.../serialization/TxPlanCodec.java` | Instance-scoped codec that reads and writes extension-aware TxPlan YAML. It registers intent subtypes on a private mapper copy (`:49-53`) and is **keyed by document namespace** (`Builder.withExtension(namespace, extension)`, `:280-288`). |

Core `TxPlan.toYaml()` / `TxPlan.from(yaml)` refuse extension plans with a message that says to use a `TxPlanCodec` (`TxPlan.java:447-460`, `:566-600`).

### 1.2 Why TxFlow cannot run extension steps today

Each TxFlow plan copy and each TxFlow-created builder uses the core, extension-unaware path:

| Concern | Sites |
|---|---|
| Plan copies through core YAML | `FlowExecutionContext.snapshotPlan` (`exec/FlowExecutionContext.java:60-63`), `TxPlanExecutionMaterializer.materialize` (`exec/TxPlanExecutionMaterializer.java:53`), `TxFlowCompiler` (`compile/TxFlowCompiler.java:116`), `EngineTxFlowStream` (`stream/EngineTxFlowStream.java:1570`, `:1903`) |
| Canonical write for the compiled fingerprint and portable output | `TxFlowCodec.transactionNode` (`codec/TxFlowCodec.java:351-362`), which the compiler calls through `TxFlowCodec.standard()` (`compile/TxFlowCompiler.java:122`) |
| Builders without extensions | `FlowExecutor` creates `new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)` at `exec/FlowExecutor.java:2211` (sequential), `:2321` (pipelined) and `:2878` (batch build) |
| Portable template materialization | `QuickTxTemplateMaterializer.materialize` decodes with a strict core-only mapper and core `TxPlan.from(...)` (`compile/QuickTxTemplateMaterializer.java:63-87`) |
| Portable schema | `spec` allows only `network`, `parameters`, `execution`, `steps` (`TxFlowCodec.java:912-914`; `txflow-v1alpha1.schema.json`, `spec.additionalProperties: false`) |
| Fail-fast added by #657 | `TxFlow.validate()` (`TxFlow.java:135-139`) and `PortableFlowValidator` `TXFLOW_NON_PORTABLE_EXTENSION` (`codec/PortableFlowValidator.java:73-76`) |

Some paths already work generically and need no change:

- **Parameter binding.** The compiler binds the template as a JSON tree before materializing it (`TxFlowCompiler.java:100-101`), so the binding never sees intent types.
- **Resource preflight.** It scans any `ref` / `*_ref` field in the tree (`TxFlowCompiler.java:353-393`), whatever intent the field sits in.
- **Execution policy.** `FlowExecutionPolicy.evaluateTransaction` reads only `context` (`config/FlowExecutionPolicy.java:197-217`).
- **Stream batching.** It merges only items whose intents are all pure `PaymentIntent`s (`stream/BuiltInPlanners.java:225-240`), so an item that holds an extension intent is never merged.
- **Pipelined output capture.** Outputs are captured from the built transaction, not from the intents.
- **Template parsing.** A portable step's `transaction` is kept as a raw tree (`TxFlowCodec.java:235`). Only compilation interprets it.

### 1.3 The question this ADR answers

> Can TxFlow support QuickTx extensions generically, so that a new extension works without TxFlow changes?

**Yes.** TxFlow only has to carry the extensions the operator registers to the places that need them: the builder, the codec and the validator. It never needs to know an extension's id, intent classes or operations. A new extension then works in TxFlow once it is registered.

There are three limits, each handled explicitly below:

1. **Registration stays explicit.** Extensions are constructed with operator configuration (a protocol, a deployment, a backend), so TxFlow cannot discover or build them from a document. See D1 and alternative A1.
2. **Correctness under PIPELINED depends on the extension.** It must read the chain through the build's `UtxoSupplier`. This becomes a documented SPI contract (D9). CIP-113 does not meet it yet (§11).
3. **Two features need SPI hooks that do not exist yet:** `flow_output` references inside extension-intent fields, and spending-resource identities for inputs an extension chooses at build time. Both are rejected or documented as limits until a real extension needs them (D8, OQ4, OQ5).

## 2. Goals

- G1. Any `QuickTxExtension` registered on a `FlowExecutor`, `FlowEngine` or `FlowRuntime` works in Java `withTxPlan` steps, with no extension-specific code in `txflow`.
- G2. Portable v1alpha1 YAML can declare extension bindings and use extension intents in step transactions, with the same lossless and fingerprint guarantees as core intents.
- G3. A flow that needs a missing or mismatched extension is rejected before any step is submitted, at every entry point.
- G4. Flows that use no extension behave exactly as before: same fingerprints, same YAML, same plan copies.
- G5. TxFlowStream can accept extension items (last milestone; it may stay unsupported at first).

## 3. Non-Goals

- Discovering extensions automatically (ServiceLoader or similar), or constructing one from document metadata.
- `flow_output` references inside extension-intent fields (OQ4).
- Spending-resource leases for inputs an extension selects during `prepare` (OQ5).
- Extension support in the legacy (pre-v1alpha1) `FlowDocument` YAML format. It keeps failing, now with a clear diagnostic.
- Promoting the experimental QuickTx extension SPI to stable.
- Fixing CIP-113's chain reads (tracked as M1b, separate PR).

## 4. Threat Model and Trust Assumptions

- **Untrusted:** the flow definition (portable YAML/JSON, or a Java `TxFlow` received from a caller), including its `spec.extensions` metadata and every intent field. ADR 0002 already treats the definition as untrusted input under server policy.
- **Trusted:** the extension instances the operator registers on the engine, and their configured deployment, protocol and backend. They are code the operator chose to load.
- **Trust boundary:** document metadata may **select** a registered extension (by `extension` id) and **assert** a binding (`schema_version`, `protocol`, `deployment`). It may never construct, replace or reconfigure an extension. Every asserted binding is checked against the registered instance through `QuickTxExtension.validateMetadata`. CIP-113, for example, rejects a `bootstrap_tx` or network that differs from its configured deployment (`cip113/Cip113Protocol.java:62-87`).
- **Asset at risk:** a document that steers an extension to an attacker-chosen deployment (scripts, registry), or that silently drops an intent field, so that the signed transaction differs from what policy reviewed and the fingerprint covers.
- **Out of scope:** malicious extension code. A registered extension runs with the same trust as any other library the operator loads.

## 5. Decisions

### D1. Engine-scoped extension registration

Add additive, repeatable registration to every TxFlow entry point:

```java
// Illustrative only
FlowExecutor.create(...).withExtension(QuickTxExtension extension);
FlowEngine.builder(backend).extension(QuickTxExtension extension);
FlowRuntime.builder(backend).extension(QuickTxExtension extension);   // delegates to FlowEngine.Builder
TxFlowCodec.withExtensions(Collection<QuickTxExtension> extensions);  // extension-aware codec
```

- Registrations are keyed by `extension.id()`. Registering a second, different instance with the same id fails, as in `QuickTxBuilder.withExtension` (`QuickTxBuilder.java:106-108`). Registering the same instance again does nothing.
- The set is frozen when the engine or executor is built. `FlowEngine` passes it to the `FlowExecutor` it creates (next to `withScriptRegistry`, `FlowEngine.java:779-782`), to its compiler and to the streams it opens.
- `TxFlowCodec.standard()` stays extension-free. It rejects extension steps with a diagnostic and never throws a Jackson exception.

*Alternatives:* ServiceLoader discovery (A1); a per-step extension list (A2).

### D2. One internal carrier; no extension-specific code in `txflow`

An internal, immutable `FlowExtensions` value, built from the D1 registrations, is the only extension state TxFlow consults. It provides:

- `applyTo(QuickTxBuilder)`: calls `withExtension` for each registered extension. It is applied at the three builder sites (`FlowExecutor.java:2211`, `:2321`, `:2878`) for **TxPlan steps only** (see OQ3 for `withTxContext` factory steps).
- `codec()`: a single id-bound `TxPlanCodec` (D3).
- `check(...)`: the requirement check of D5.

`txflow` main code must not reference a concrete extension, intent class or operation name. It sees only the `QuickTxExtension`, `ExtensionIntent` and `ExtensionMetadata` interfaces.

### D3. Id-bound `TxPlanCodec` registration (QuickTx, additive)

Today a `TxPlanCodec` is keyed by **document namespace** (`TxPlanCodec.java:170-185`, `:280-288`). TxFlow cannot know which alias (`pt`, `token`, …) an author chose, and aliases are local to the document. Add:

```java
// Illustrative only
TxPlanCodec.builder().withExtension(QuickTxExtension extension)   // bound by extension id
```

When a document declares namespace `ns` with `extension: X`, the codec resolves `ns` to the extension registered under id `X`. It then applies exactly today's checks: id match, `schema_version` match, `validateMetadata`, and an operation → `intentTypes()` lookup. The namespace-bound overload is unchanged. A codec that has both kinds of binding resolves the explicit namespace binding first.

*Alternative:* build a namespace-bound codec per plan inside TxFlow (A3).

### D4. Copy and serialize extension plans through the codec

At each plan-copy and write site listed in §1.2:

- If `plan.hasExtensions()` is false, keep the existing core path unchanged. This is what preserves I2.
- Otherwise copy with `codec.fromYaml(codec.toYaml(plan))`. Snapshots taken by `FlowExecutionContext.snapshotPlan` store the codec YAML. `TxFlowCodec.transactionNode` writes the step's extension bindings and its namespaced intents.

The codec already deep-copies `ExtensionMetadata` and validates intents on both read and write (`TxPlanCodec.java:60-132`). A copy is therefore isolated per attempt, exactly as core plans are today (ADR 0002, transaction schema rule 7).

### D5. Requirement check moves to where the registrations are

- `TxFlow.validate()` has no engine context, so it stops rejecting extension steps (`TxFlow.java:135-139`). It keeps its structural checks.
- `FlowExtensions.check(flow)` runs before any step is built, at every place that executes or compiles a flow: `FlowExecutor` (where `flow.validate()` runs today, `FlowExecutor.java:750`, `:794`, `:857`, `:2077`), `TxFlowCompiler.compile`, and stream submission. For each step it verifies:
  1. every declared namespace's `extension` id is registered;
  2. `validateMetadata` accepts the declared metadata;
  3. every `ExtensionIntent` belongs to a declared and registered extension, has the registered concrete type for its operation, and passes `validate()`.
- `PortableFlowValidator` stops emitting `TXFLOW_NON_PORTABLE_EXTENSION` once M2 can write extension steps portably. Until then the code stays, with its message pointing at engine registration.

New stable diagnostic codes (added to `txflow-diagnostic-codes.json`):

| Code | Meaning |
|---|---|
| `TXFLOW_EXTENSION_UNREGISTERED` | A step needs an extension id the engine or codec does not have. |
| `TXFLOW_EXTENSION_METADATA_INVALID` | `validateMetadata` rejected the declared binding. |
| `TXFLOW_EXTENSION_INTENT_INVALID` | Unknown operation, wrong concrete type, or `validate()` failed. |
| `TXFLOW_EXTENSION_BINDING_CONFLICT` | Flow-level and step-level bindings disagree (D6). |
| `TXFLOW_EXTENSION_INTENT_LOSSY` | The round-trip guard failed (D7). |
| `TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED` | A `flow_output` reference appears inside an extension intent (D8). |

### D6. Portable v1alpha1: flow-level `spec.extensions`

Add an optional `spec.extensions` map: namespace → `ExtensionMetadata`. It has the same shape and namespace rules as the TxPlan `extensions:` block (`[a-z][a-z0-9_-]{0,31}`, the same reserved names, one namespace per extension id). A step's `transaction.tx.intents[]` may then use `type: <namespace>:<operation>`.

```yaml
# Illustrative only
api_version: txflow.cardano-client.dev/v1alpha1
kind: TxFlow
metadata: { name: register-mint-transfer }
spec:
  extensions:
    pt:
      extension: programmable-token
      schema_version: "1"
      protocol: cip113
      deployment: { bootstrap_tx: "<64 hex>", network: "0" }
  steps:
    - id: register
      transaction:
        tx:
          from_ref: wallet://issuer
          intents:
            - type: pt:register
              # extension-defined fields
    - id: transfer
      needs: [register]
      transaction:
        tx:
          from_ref: wallet://issuer
          intents:
            - type: pt:transfer
              receiver: ${receiver}
              # extension-defined fields
```

- **Why flow-level.** ADR 0002 forbids nesting standalone TxPlan envelope fields inside a step (transaction schema rule 2). The binding describes the runtime contract of the whole flow. One binding per flow also gives one canonical place for the fingerprint.
- **Compilation.** For a step whose `tx.intents` contains a namespaced type:
  1. Remove the extension intents. Run the existing strict core materializer on the rest, unchanged.
  2. Decode each extension intent through the engine codec, wrapping it in a one-transaction document whose `extensions:` block holds only the flow-level bindings the step uses.
  3. Re-insert the extension intents in their authored positions.

  Namespaced types are allowed only in `tx.intents`, the only array `TxPlanCodec` rewrites (`TxPlanCodec.java:262-275`). Anywhere else they are `TXFLOW_EXTENSION_INTENT_INVALID`.
- **Writing Java steps.** A Java `withTxPlan` extension step is written portably by hoisting its plan's `getExtensions()` into `spec.extensions`. If two steps bind the same namespace to different metadata, or the same extension id to different namespaces, the write fails with `TXFLOW_EXTENSION_BINDING_CONFLICT`.
- **Schema.** `txflow-v1alpha1.schema.json` gains `spec.extensions`. The shared QuickTx intent schema already allows any `type` string (`quicktx-transaction-v1alpha1.schema.json:57`). See OQ1 and OQ2 for the field name and versioning.

### D7. Generic losslessness guard for unknown intent schemas

ADR 0002 requires lossless portability (Decision 6): the compiled fingerprint must cover everything that is executed. TxFlow cannot know an extension's field schema, so it proves "no silent loss" generically:

- Extension intents are decoded with unknown properties failing. The codec mapper is a copy of the default `YamlSerializer` mapper, which does not disable `FAIL_ON_UNKNOWN_PROPERTIES` (`YamlSerializer.java:21-31`). An implementation must keep it that way for the TxFlow codec even if QuickTx relaxes it later.
- After decoding, each extension intent is written back through the codec and compared with its bound input subtree (JSON value equality, after the namespace rewrite). A difference is reported as `TXFLOW_EXTENSION_INTENT_LOSSY`, pointing at that intent's document path.

This makes "round-trip lossless" part of the extension contract (D9, C2). CIP-113 already round-trips byte-for-byte (`programmable-token/src/test/.../ProgrammableTokenApiTest.java:135-161`).

### D8. Bindings, policy and resources

- Parameter binding, policy evaluation and `*_ref` resource preflight apply to extension intents unchanged (§1.2).
- A `flow_output` pointer anywhere inside an extension intent is rejected (`TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED`). `TxPlanExecutionMaterializer` resolves `FlowOutputRef` only in the three core intent types it knows (`TxPlanExecutionMaterializer.java:59-72`), so accepting the pointer would leave it unresolved. A generic hook is OQ4.
- Inputs an extension selects during `prepare` (for example protocol UTxOs) are not known when the flow compiles, so they are not covered by spending-resource leases. This is documented as a limit (R3, OQ5).

### D9. Extension contract for TxFlow compatibility (documentation, no new methods)

An extension works under TxFlow without TxFlow changes if it meets these conditions. They are added to the `quicktx.extension` package documentation:

- **C1.** Every chain read made while building (`prepare`, `afterBalance`, `verify`) goes through `ExtensionBuildContext.getUtxoSupplier()`. Under TxFlow that is the flow-aware supplier, which sees outputs of earlier pipelined steps. A private backend supplier sees only confirmed state.
- **C2.** Intents are pure data, and a decode-then-encode round trip through `TxPlanCodec` reproduces the authored tree (D7).
- **C3.** `validateMetadata` and `ExtensionIntent.validate()` are pure: no chain or network I/O. They run during compilation and validation.
- **C4.** `newBuildExtension` returns a fresh participant for each build. This is already required by `TxBuildExtension`'s Javadoc.

CIP-113 meets C2–C4 but not C1: registry nodes are read through the service's own `DefaultUtxoSupplier` (`cip113/tx/DefaultCip113ProtocolService.java:79`, `:99-100`; `cip113/tx/Cip113BuildExtension.java:98`). That is milestone M1b.

### D10. TxFlowStream

A stream opened on an engine shares the engine's `FlowExtensions`:

- Submission runs the D5 check.
- Persisted portable payloads are written and read with the extension-aware `TxFlowCodec` (`EngineTxFlowStream.java:339`, `:1585`, `:3467`), so recovery decodes them with the same bindings.
- `enforceLaneFundingScope` copies through D4 (`:1903`).

Batching needs no change, since extension items are never merged (§1.2). I9 locks that in with a test. Until M3 the stream keeps rejecting extension items with `TXSTREAM_NON_PORTABLE_ITEM`.

## 6. Invariants

Each invariant names the decisions it constrains and must have at least one test that would fail if it were broken.

- **I1. Fail before submission** (D5). A flow that needs an unregistered extension, has invalid metadata or holds an invalid extension intent is rejected before any step is built or submitted. This holds for `FlowExecutor` (sync and async), `FlowEngine`, `FlowRuntime` and TxFlowStream.
- **I2. Extension-free flows unchanged** (D4, D6). For flows with no extension, compiled fingerprints, portable YAML/JSON output and plan snapshots are byte-identical to `dfd8540d`. A golden test over the existing conformance fixtures enforces this.
- **I3. No extension-specific code** (D2). `txflow` main sources import no concrete `ExtensionIntent` or `QuickTxExtension` and no `programmable-token` type, and the `txflow` build has no dependency on `programmable-token`. A source-scan test enforces this.
- **I4. Copies preserve extensions** (D3, D4). For any extension plan, a TxFlow copy has equal `getExtensions()` and equal codec YAML, and shares no mutable intent instance with its source.
- **I5. Builders carry exactly the registered set** (D1, D2). Every `QuickTxBuilder` that TxFlow creates for a TxPlan step has the registered extensions, no others, and no duplicates.
- **I6. Documents never configure extensions** (D1, D5, §4). Document metadata is only validated against registered instances. A document cannot cause an extension to be constructed, and a metadata mismatch (id, `schema_version`, `validateMetadata`) is rejected.
- **I7. Lossless extension intents** (D6, D7). An unknown field, or any field that does not round-trip, in an extension intent is rejected at compile time with a path-precise diagnostic.
- **I8. Fingerprint covers extensions** (D6). Changing any `spec.extensions` field (including a nested `deployment` value), or any field of an extension intent, changes the compiled fingerprint. Reordering `deployment` map keys does not.
- **I9. No batching of extension items** (D10). A planner never merges an item that holds an `ExtensionIntent`.
- **I10. Pipelined visibility under C1** (D9). An extension that reads through `ExtensionBuildContext.getUtxoSupplier()` sees outputs of earlier PIPELINED steps of the same flow.

## 7. Compatibility

- **Java API.** Additive only (D1, D3). No signature changes.
- **Behaviour.** `TxFlow.validate()` no longer reports extension steps. Executing such a flow without registering the extension fails in the executor before submission (I1) instead of in `validate()`. It still fails before any submission.
- **Schema.** `spec.extensions` is an additive optional field (OQ2). Older readers reject it under the default unknown-field policy. Under `WARN` or `IGNORE` they drop it, and the namespaced intent then fails strict materialization. Either way they fail closed.
- **Fingerprints.** Unchanged for extension-free flows (I2). Extension flows could not compile before, so they have no prior fingerprint.
- **Experimental SPI.** The QuickTx extension SPI is experimental (`extension/package-info.java`). TxFlow's extension entry points are marked experimental too.

## 8. Implementation Milestones

Each milestone is one PR.

### M1. Java `withTxPlan` steps on FlowExecutor, FlowEngine and FlowRuntime

- **Entry gate:** ADR accepted.
- **Scope:** D1, D2, D3, D4 (executor, compiler and snapshot sites), D5.
- **Exit:**
  - Unit tests for I1–I6 and I10, written against a **test-only fake extension** in `txflow` test sources. It is a trivial `QuickTxExtension` with one intent that reads a UTxO through the context supplier and adds a payment via `addPreparedIntent`. This proves the design is generic without CIP-113.
  - QuickTx unit tests for id-bound codec resolution.
  - Yaci DevKit IT: CIP-113 register → mint → transfer as a 3-step flow in **SEQUENTIAL** mode.
  - Full `:quicktx:test` and `:txflow:test` pass on Java 17.

### M1b. CIP-113 C1 conformance (programmable-token, separate PR)

- **Entry gate:** M1 merged.
- **Scope:** read registry nodes and the coordination UTxO through the build's `UtxoSupplier`.
- **Exit:** the M1 IT also passes in **PIPELINED** mode, and a unit test shows a pending registry node is visible.

### M2. Portable YAML

- **Entry gate:** M1 merged; OQ1 and OQ2 decided.
- **Scope:** D6, D7, D8; the schema update; `PortableFlowValidator` cleanup.
- **Exit:**
  - Tests for I7 and I8.
  - Conformance fixtures: valid extension flows; an unknown field; a non-round-tripping field; a binding conflict; an unregistered extension; a `flow_output` inside an extension intent.
  - The I2 golden test still passes.
  - DevKit IT that runs the M1 flow from YAML.

### M3. TxFlowStream

- **Entry gate:** M2 merged.
- **Scope:** D10.
- **Exit:** tests for I9, I1 (stream entry), and recovery that decodes a persisted extension payload after restart.

## 9. Verification Strategy

- **Genericity:** the fake extension (M1) is the main evidence. If TxFlow needed knowledge of a particular extension, the fake extension's tests would fail. I3's source scan guards against regressions.
- **Independent reference for CIP-113:** the standalone TxPlan DevKit ITs (`Cip113TxPlanResourceEndToEndIT`, `Cip113EndToEndIT`) are the baseline. A TxFlow step must produce a transaction with the same intent-level outcome (assets, receivers, registry change) as the equivalent standalone TxPlan.
- **Negative cases:**
  - an unregistered extension;
  - a wrong `schema_version`;
  - a mismatched `deployment` (I6);
  - an unknown intent field and a lossy field (I7);
  - a namespaced type in `inputs`/`scripts`;
  - conflicting bindings;
  - a `flow_output` inside an extension intent;
  - a duplicate registration with different instances.

  Each must fail before submission, with its stable code.
- **Golden values** for I2 are produced from `dfd8540d` **before** any change, not from the code under test.

## 10. Alternatives Considered

- **A1. ServiceLoader discovery, or building extensions from document metadata.** Rejected. Extensions need operator configuration (a protocol service, a deployment, a backend) that a document cannot safely supply. Building one from document metadata would let an untrusted document choose a deployment, which breaks I6.
- **A2. A per-step extension list (`FlowStep.withExtension`).** Rejected. It puts runtime objects into the definition, which can then no longer be serialized or fingerprinted. It also duplicates engine configuration in every step.
- **A3. A namespace-bound codec per plan, assembled inside TxFlow.** Workable, but it rebuilds a mapper per plan, and every TxFlow call site has to repeat the alias → id mapping. D3 is a small, general QuickTx addition that other hosts can reuse too.
- **A4. Deep-copy plans without serialization (clone intents).** Rejected for now. Core plans are already copied through YAML, and the codec is the one component that knows the extension intent types. A cloning SPI would add a second contract that every extension would have to meet.
- **A5. Keep the #657 fail-fast and support only `withTxContext` factories.** Rejected. Factories cannot be made portable, are not accepted by TxFlowStream, and do not meet G1 or G2.

## 11. Consequences and Risks

**Positive**
- A new extension needs no TxFlow change. Registering it is enough.
- One codec path covers copying, writing and fingerprinting.
- The trust boundary is explicit and tested.

**Negative and risks**
- **R1.** TxFlow's correctness under PIPELINED depends on each extension meeting C1. A non-conforming extension fails with "insufficient" or "not found" errors from the extension, not with a TxFlow diagnostic. Mitigation: documentation, plus a reusable conformance test kit (follow-up).
- **R2.** The round-trip guard (D7) may reject an extension that normalizes its input, for example an extension that writes a canonical form different from the authored one. That is intended (ADR 0002 Decision 6), but it constrains extension authors.
- **R3.** Inputs an extension selects (for example a shared CIP-113 registry node) can contend across concurrent executions without lease protection. Ledger validation still catches a double spend, but the losing execution fails rather than waiting.
- **R4.** The experimental QuickTx SPI may change. TxFlow's extension surface is experimental too, so it can follow.

## 12. Open Questions

- **OQ1. Name of the flow-level block.** ADR 0002 says portable documents reject unknown fields "except within a documented `extensions` map" (`0002-…md:2485`). That sentence could be read as reserving `extensions` for something else.
  - (a) `spec.extensions`: the same name and shape as TxPlan's block.
  - (b) `spec.quicktx_extensions`.

  **Lean: (a).** It is the documented map that sentence anticipated, and authors already know it from TxPlan.
- **OQ2. Schema versioning.**
  - (a) An additive field in v1alpha1.
  - (b) A new `v1alpha2`.

  **Lean: (a).** The version is alpha, the change is additive, and older readers fail closed (§7).
- **OQ3. `withTxContext` factory steps.** If the engine pre-registers extensions on factory builders, a factory that registers its own instance with the same id would now throw "Extension already registered" (`QuickTxBuilder.java:106-108`).
  - (a) Apply the registrations to TxPlan steps only, so factories behave as today.
  - (b) Apply them everywhere and document the change.
  - (c) Relax `QuickTxBuilder.withExtension` to accept an instance with an equal id and metadata.

  **Lean: (a).** There is no behaviour change, and factories are the legacy path.
- **OQ4. `flow_output` inside extension intents.** A future optional SPI could let an intent expose and accept resolved `UtxoRef`s. **Lean:** defer until an extension needs it. D8 rejects these references until then.
- **OQ5. Spending identities for inputs an extension selects.** A future optional SPI method (for example `spendingIdentities(intent, metadata)`) would let the compiler lease protocol UTxOs. **Lean:** defer; document R3.
- **OQ6. Fingerprint scope.** Should the fingerprint include the registered extension's implementation version (for example its jar version)? **Lean: no.** `schema_version` and the metadata define the contract. The implementation version is operational data for the journal, not part of the definition's identity.

## 13. Related Findings (Out of Scope)

- CIP-113 reads registry nodes outside the build supplier (`DefaultCip113ProtocolService.java:79`, `:99-100`; `Cip113BuildExtension.java:98`). This is tracked as M1b and in issue #700.
- The legacy `FlowDocument` YAML path copies plans through core YAML (`yaml/FlowDocument.java:428`, `:748`). It will keep rejecting extension plans, now with `TXFLOW_EXTENSION_UNREGISTERED` instead of a Jackson error.
- `TxFlow.fromYaml()` parses with `TxFlowCodec.standard()` (`TxFlow.java:277`), and `TxFlow.toYaml()` writes through the legacy `FlowDocument` (`TxFlow.java:265-266`). Both stay extension-free. Callers who need extensions use `TxFlowCodec.withExtensions(...)`.
