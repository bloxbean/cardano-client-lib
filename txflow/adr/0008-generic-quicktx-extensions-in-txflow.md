# ADR 0008: Generic QuickTx Extension Support in TxFlow

**Status**: Accepted

**ADR Document Version**: 1.3.0

**Date**: 2026-10-17

**Last Updated**: 2026-10-18

**Review State**: Design approved at r3 (`1be2134`); implementation approved at `cfc2fb19`

**Target Release**: To be decided

**Modules**: `txflow`, `quicktx` (one additive codec API); `programmable-token` only for the CIP-113 conformance follow-up (M1b)

**Issue**: [#700](https://github.com/bloxbean/cardano-client-lib/issues/700). Follow-up to [#657](https://github.com/bloxbean/cardano-client-lib/pull/657).

**Related ADRs**: [ADR 0002: Portable TxFlow Contract](0002-portable-txflow-contract-and-runtime.md) (transaction schema ownership, lossless portability, fingerprinting), [ADR 0004: TxFlowStream on FlowEngine](0004-txstream-on-flow-engine.md), [CIP-113 QuickTx extension review](../../adr/cip113/001-cip113-quicktx-extension-and-txplan-design-review.md)

**Supersedes**: None

**Risk**: Medium. The public API changes are additive and opt-in, but the design touches plan isolation, compiled-definition fingerprints, the portable schema and the trust boundary between an untrusted flow document and the extensions the operator registers.

All code references are to `origin/master` at `dfd8540d`.

## Revision History

- **r1** (2026-10-17): Initial proposal.
- **r2** (2026-10-18; responds to the review of `9037614`): F1 (and the Aster comment) → M1 now includes the extension-aware portability check, the canonical writer, the `spec.extensions` parser field and schema (new D6a); D5, M1 and M2 rescoped; OQ1 and OQ2 become M1 entry gates. F2 → new D4a: TxFlow copies and decodes extension plans literally and never runs TxPlan variable expansion on them; D4, D6 and D8 use it; new I11; the example now uses `${{ inputs.receiver }}`. F3 → new D9a: the visibility contract separates address scans (`funding_from`, legacy `depends_on`) from exact lookups; C1 and I10 narrowed; the example declares `funding_from`; new I12; M1b exit criteria updated. F4 → D6a: every binding must be used by at least one extension intent, otherwise `TXFLOW_EXTENSION_BINDING_UNUSED`; new I13; fixtures added.
- **r3** (2026-10-18; responds to the review of `7b2ba7e`): F2 completion → D4a now also states that the portable path (FlowEngine, FlowRuntime, the compiler and the canonical writer) keeps rejecting any plan with a non-empty `getVariables()` (`TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES`), extension or core alike. Only the FlowExecutor path accepts retained variables as inert data. I11 is split by path, and the M1 exit criteria include an already-bound plan. F5 → D9a, C1 and §1.3 now limit spent-output filtering and the fail-closed claim to address scans. The exact-lookup gap is recorded as R6 and a follow-up. The "Unsupported" bullet matches the table and I12. §9 separates the missing-`funding_from` case, a runtime build failure, from the fails-before-submission cases.
- **r4** (2026-10-18; after the implementation review of #703–#706, approved at `cfc2fb19`): D10 gains the multi-item planner rule (F10). Implementation notes:
  - I5 applies registrations only to builders of plans that use extensions.
  - The preflight checks `schema_version` independently of the extension's `validateMetadata` hook (F6).
  - Number-like strings are quoted only in extension plans and in the YAML of flows with extension bindings, so extension-free output stays byte-identical (I2, F7).
  - The D7 guard matches extension intents by order, because core input and script intents move arrays on write (F8).
  - D8 also scans Java-plan extension intents in their written form (F9).
  - TxFlow builders now get a `ScriptSupplier`, and an optional per-step `txEvaluator` whose UTxO view resolves funding-step outputs exactly.
  - CIP-113 also reads the coordination UTxO and global state through the build supplier (F11).
  - The legacy `FlowDocument` rejects extension plans with a plain message.

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
2. **Correctness under PIPELINED depends on the extension and on the flow's edges.** The extension must read the chain through the build's `UtxoSupplier` (D9, C1). That supplier exposes another step's pending outputs only along declared edges: `funding_from` for address scans, and legacy `depends_on` or a core `flow_output` for exact lookups (D9a). `needs` gives ordering only. Only address scans filter outputs spent by earlier steps (R6). CIP-113 does not meet C1 yet (§13).
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
- Otherwise copy with the codec's **literal** copy (D4a), never with `codec.fromYaml(codec.toYaml(plan))`. Snapshots taken by `FlowExecutionContext.snapshotPlan` store the codec YAML. `TxFlowCodec.transactionNode` writes the step's extension bindings and its namespaced intents.

The codec deep-copies `ExtensionMetadata` and validates intents on both read and write (`TxPlanCodec.java:60-132`). A copy is therefore isolated per attempt, exactly as core plans are today (ADR 0002, transaction schema rule 7).

### D4a. Literal copy and decode boundary (added in r2, F2)

`TxPlanCodec.fromYaml(yaml, variables)` is the standalone *authoring* decoder. It runs `VariableResolver.resolveTree` over every document field (`TxPlanCodec.java:99-114`) and keeps the variables on the plan (`:124`). Applying it to an already-resolved plan reinterprets literal text: a value `"literal ${other} stays"` becomes `"literal REINTERPRETED stays"` when the plan also carries `other`, and fails with `Variable not found` when it does not (reproduced by the r1 review).

TxFlow therefore never runs TxPlan variable expansion on an extension plan. QuickTx gets one additive codec entry point, used by every TxFlow site:

```java
// Illustrative only
TxPlan TxPlanCodec.copy(TxPlan plan);          // write, then read the tree literally
TxPlan TxPlanCodec.readLiteral(String yaml);   // read an already-bound document literally
```

- A literal read runs the same steps as `fromYaml` (binding validation, namespace rewrite, typed decode, intent validation), except that it skips `resolveTree`. It also hands the core reconstruction a document without `variables`, as `fromYaml` already does (`:119-123`).
- `copy` keeps the source plan's `variables` map on the copy as inert data, so that `getVariables()` is equal on both. It never applies them.
- **Binding happens once, when the plan is authored.** A Java-built plan holds final values: standalone QuickTx never expands variables in an in-memory plan, and `compose(plan)` does not read `getVariables()`. A plan decoded with `codec.fromYaml(yaml, vars)` was bound by that call. TxFlow runs an extension plan with exactly the values standalone `QuickTxBuilder.compose(plan)` would use.
- **Retained variables on the portable path (r3).** `PortableFlowValidator` already rejects any step plan with a non-empty `getVariables()` (`TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES`, `PortableFlowValidator.java:78-82`). That check stays as it is and applies to extension plans exactly as to core plans. The portable form has no TxPlan `variables` (ADR 0002, transaction schema rule 2), and dropping them silently would break I4. So FlowEngine, FlowRuntime, the compiler and the canonical writer reject a plan decoded with `codec.fromYaml(yaml, vars)` until the author clears the inert map (`plan.setVariables(Map.of())`, public at `TxPlan.java:63-66`). Because they are inert, clearing them changes no executed value. The FlowExecutor path is not portable and does not run this check, so it accepts the plan and keeps the variables as inert data. Behaviour for extension-free plans is unchanged.
- **Legacy flow variables** (`TxFlow.addVariable`) are not merged into extension plans. `TxPlanExecutionMaterializer` keeps merging them into core plans as today (`:54-58`), which preserves I2. Extension plans cannot come from the legacy YAML format (§13), so no legacy document depends on that merge.
- **Portable steps** (M2) are bound by TxFlow's `${{ … }}` binder on the JSON tree (`TxFlowCompiler.java:100-101`), and the bound tree is decoded with `readLiteral`. A bound value that contains `${other}` stays literal.
- A core field of an extension plan that holds literal `${…}` text, and that the core reconstruction must parse (for example `from` or `change_address`), still fails with the codec's existing "kept literal and this field cannot hold it" error (`TxPlanCodec.java:139-151`). That fails closed and is never reinterpreted. It is the same limit core plans have today.

### D5. Requirement check moves to where the registrations are

- `TxFlow.validate()` has no engine context, so it stops rejecting extension steps (`TxFlow.java:135-139`). It keeps its structural checks.
- `FlowExtensions.check(flow)` runs before any step is built, at every place that executes or compiles a flow: `FlowExecutor` (where `flow.validate()` runs today, `FlowExecutor.java:750`, `:794`, `:857`, `:2077`), `TxFlowCompiler.compile`, and stream submission. For each step it verifies:
  1. every declared namespace's `extension` id is registered;
  2. `validateMetadata` accepts the declared metadata;
  3. every `ExtensionIntent` belongs to a declared and registered extension, has the registered concrete type for its operation, and passes `validate()`.
- `PortableFlowValidator` takes the registered extensions as input (none for `TxFlowCodec.standard()`). It reports an extension step as `TXFLOW_EXTENSION_UNREGISTERED` only when a required extension is missing, and stops emitting `TXFLOW_NON_PORTABLE_EXTENSION` in **M1** (F1). The compiler (`TxFlowCompiler.java:76-81`) and the canonical writer (`TxFlowCodec.java:343`) both call it with the engine's registrations, so neither the portability check nor the fingerprint is bypassed. Its other checks are unchanged. In particular, `TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES` still applies to extension plans (D4a, r3).
- The legacy code `TXFLOW_NON_PORTABLE_EXTENSION` stays in `txflow-diagnostic-codes.json` for compatibility, but is no longer emitted.

New stable diagnostic codes (added to `txflow-diagnostic-codes.json`):

| Code | Meaning |
|---|---|
| `TXFLOW_EXTENSION_UNREGISTERED` | A step needs an extension id the engine or codec does not have. |
| `TXFLOW_EXTENSION_METADATA_INVALID` | `validateMetadata` rejected the declared binding. |
| `TXFLOW_EXTENSION_INTENT_INVALID` | Unknown operation, wrong concrete type, or `validate()` failed. |
| `TXFLOW_EXTENSION_BINDING_CONFLICT` | Flow-level and step-level bindings disagree (D6). |
| `TXFLOW_EXTENSION_BINDING_UNUSED` | A declared binding is used by no extension intent (D6a). |
| `TXFLOW_EXTENSION_TEMPLATE_UNSUPPORTED` | M1 only: a portable template uses an extension intent before M2 lands (D6a). Retired in M2. |
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
      funding_from: [register]   # D9a: lets the build see register's pending outputs by address
      transaction:
        tx:
          from_ref: wallet://issuer
          intents:
            - type: pt:transfer
              receiver: '${{ inputs.receiver }}'
              # extension-defined fields
```

(`spec.parameters.receiver` with `type: address` is declared but omitted above for brevity.)

- **Why flow-level.** ADR 0002 forbids nesting standalone TxPlan envelope fields inside a step (transaction schema rule 2). The binding describes the runtime contract of the whole flow. One binding per flow also gives one canonical place for the fingerprint.
- **Compilation.** For a step whose `tx.intents` contains a namespaced type:
  1. Remove the extension intents. Run the existing strict core materializer on the rest, unchanged.
  2. Decode each extension intent with the engine codec's `readLiteral` (D4a), wrapping it in a one-transaction document whose `extensions:` block holds the flow-level bindings the step uses.
  3. Re-insert the extension intents in their authored positions.

  Namespaced types are allowed only in `tx.intents`, the only array `TxPlanCodec` rewrites (`TxPlanCodec.java:262-275`). Anywhere else they are `TXFLOW_EXTENSION_INTENT_INVALID`.
- **Writing Java steps.** A Java `withTxPlan` extension step is written portably by hoisting its plan's `getExtensions()` into `spec.extensions`. If two steps bind the same namespace to different metadata, or the same extension id to different namespaces, the write fails with `TXFLOW_EXTENSION_BINDING_CONFLICT`.
- **Schema.** `txflow-v1alpha1.schema.json` gains `spec.extensions`. The shared QuickTx intent schema already allows any `type` string (`quicktx-transaction-v1alpha1.schema.json:57`). See OQ1 and OQ2 for the field name and versioning.

### D6a. Where bindings live, and unused bindings (added in r2, F1 and F4)

- **Model.** `TxFlow` gains a read-only `extensions` map (namespace → `ExtensionMetadata`). The portable parser fills it from `spec.extensions`. Java authors normally leave it empty and bind on the plan (`extension.configure(plan)`).
- **Canonical form.** The writer emits `spec.extensions` as the flow-level map merged with every step plan's `getExtensions()`. Conflicts are `TXFLOW_EXTENSION_BINDING_CONFLICT`. Steps are written with namespaced intent types. The compiled fingerprint uses this form, so the bindings are part of it (I8).
- **Unused bindings are rejected.** Every namespace in the merged map must be used by at least one extension intent of some step, otherwise `TXFLOW_EXTENSION_BINDING_UNUSED`. This applies to a core-only flow that declares a binding, to an extra binding next to a used one, and to a Java plan bound with `configure(plan)` but holding no intent of that extension. The check runs before any registry lookup, so an unused binding is reported as unused whether or not it is registered or valid. Rejecting, rather than retaining, keeps the canonical form a function of what executes: two documents that differ only in an unused binding cannot share a fingerprint, because neither compiles.
- **Milestones (F1).** The writer, the parser field, the unused check and the schema change all land in **M1**, because `FlowEngine` compilation fingerprints through the writer. M2 adds only template materialization (D6 compilation, D7, D8). Between M1 and M2, a parsed YAML step that uses a namespaced intent compiles to `TXFLOW_EXTENSION_TEMPLATE_UNSUPPORTED`. It never reaches a Jackson exception, and the code is retired in M2.

### D7. Generic losslessness guard for unknown intent schemas

ADR 0002 requires lossless portability (Decision 6): the compiled fingerprint must cover everything that is executed. TxFlow cannot know an extension's field schema, so it proves "no silent loss" generically:

- Extension intents are decoded with unknown properties failing. The codec mapper is a copy of the default `YamlSerializer` mapper, which does not disable `FAIL_ON_UNKNOWN_PROPERTIES` (`YamlSerializer.java:21-31`). An implementation must keep it that way for the TxFlow codec even if QuickTx relaxes it later.
- After decoding, each extension intent is written back through the codec and compared with its bound input subtree (JSON value equality, after the namespace rewrite). A difference is reported as `TXFLOW_EXTENSION_INTENT_LOSSY`, pointing at that intent's document path.

This makes "round-trip lossless" part of the extension contract (D9, C2). CIP-113 already round-trips byte-for-byte (`programmable-token/src/test/.../ProgrammableTokenApiTest.java:135-161`).

### D8. Bindings, policy and resources

- Parameter binding, policy evaluation and `*_ref` resource preflight apply to extension intents unchanged (§1.2). The bound tree is decoded literally (D4a).
- A `flow_output` pointer anywhere inside an extension intent is rejected (`TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED`). `TxPlanExecutionMaterializer` resolves `FlowOutputRef` only in the three core intent types it knows (`TxPlanExecutionMaterializer.java:59-72`), so accepting the pointer would leave it unresolved. A generic hook is OQ4.
- Inputs an extension selects during `prepare` (for example protocol UTxOs) are not known when the flow compiles, so they are not covered by spending-resource leases. This is documented as a limit (R3, OQ5).

### D9. Extension contract for TxFlow compatibility (documentation, no new methods)

An extension works under TxFlow without TxFlow changes if it meets these conditions. They are added to the `quicktx.extension` package documentation:

- **C1.** Every chain read made while building (`prepare`, `afterBalance`, `verify`) goes through `ExtensionBuildContext.getUtxoSupplier()`. Under TxFlow that is the flow-aware supplier. Its **address scans** hide outputs that earlier flow steps spent, and it exposes pending outputs only as D9a describes. Its exact lookups do not filter spent outputs (D9a, R6). A private backend supplier does neither, so its address scans can build on state that an earlier pipelined step already spent.
- **C2.** Intents are pure data, and a decode-then-encode round trip through `TxPlanCodec` reproduces the authored tree (D7).
- **C3.** `validateMetadata` and `ExtensionIntent.validate()` are pure: no chain or network I/O. They run during compilation and validation.
- **C4.** `newBuildExtension` returns a fresh participant for each build. This is already required by `TxBuildExtension`'s Javadoc.

CIP-113 meets C2–C4 but not C1: registry nodes are read through the service's own `DefaultUtxoSupplier` (`cip113/tx/DefaultCip113ProtocolService.java:79`, `:99-100`; `cip113/tx/Cip113BuildExtension.java:98`). That is milestone M1b.

### D9a. Visibility contract for pending outputs (added in r2, F3)

TxFlow keeps the visibility rules it has today. It adds no extension-specific or global visibility. The flow-aware supplier is built in `FlowExecutor.createUtxoSupplier` (`FlowExecutor.java:2397-2411`) and behaves as `FlowUtxoSupplier` does today:

| Read | Pending outputs of an earlier step S are visible when the current step declares | Code |
|---|---|---|
| Address scan (`getAll`, `getPage`) | `funding_from: [S]` (`fundsFrom(S)`), or a legacy `depends_on` on S; only outputs at the scanned address | `FlowUtxoSupplier.java:143-191` |
| Exact lookup (`getTxOutput`) | a legacy `depends_on` on S, or a **core** `flow_output` reference to S in the same plan | `FlowUtxoSupplier.java:95-104`, `:201-235` |
| Either | `needs: [S]` alone gives **no** visibility; it is ordering only | `FlowExecutor.java:2406-2410` |

**Address scans** filter out outputs spent by earlier flow steps (`filterOutSpentUtxos`, `FlowUtxoSupplier.java:75-123`). **Exact lookups do not.** `getTxOutput` returns a backend hit as is, and its pending fallback does not filter either (`:95-104`, `:201-235`). In the pipelined window, an exact lookup of a confirmed output that an earlier step has already spent still returns it (R6).

Consequences for extensions:

- **Supported pattern.** An extension finds protocol state by an **address scan** (for example the CIP-113 registry scan, `RegistryLookup.Scanning`). A step that must see protocol outputs created by an earlier pipelined step S declares `funding_from: [S]`. That is the existing meaning of `funding_from`: the step may select and consume S's outputs at the addresses it scans. An extension that consumes a protocol output S created therefore falls within the authority the author already granted.
- **Without the edge, an address scan fails closed.** If S spent a confirmed protocol output and replaced it, the scan filters out the old output as spent and does not see the new one. The extension reports "not found" instead of building on stale state. Under SEQUENTIAL mode, S is confirmed before the next step builds, so no edge is needed.
- **Exact lookups.** A pending output of S can be read exactly only if the step declares a legacy dependency on S or holds a **core** `flow_output` reference to S (table above, I12). An extension intent cannot declare its own exact-reference edge, because extension-owned `flow_output` pointers are rejected (D8, OQ4). Exact lookups of confirmed outputs, such as the CIP-113 deployment or bootstrap references, work as today. They are **not** spent-filtered, so an extension must not rely on an exact lookup to decide whether protocol state is still live (R6). Protocol state that flow steps may spend should be read by address scan.
- **Extension-free flows are unaffected.** Nothing in D9a changes how the supplier is constructed.

### D10. TxFlowStream

A stream opened on an engine shares the engine's `FlowExtensions`:

- Submission runs the D5 check.
- Persisted portable payloads are written and read with the extension-aware `TxFlowCodec` (`EngineTxFlowStream.java:339`, `:1585`, `:3467`), so recovery decodes them with the same bindings.
- `enforceLaneFundingScope` copies through D4 (`:1903`).

Batching needs no change, since extension items are never merged (§1.2). I9 locks that in with a test. Until M3 the stream keeps rejecting extension items with `TXSTREAM_NON_PORTABLE_ITEM`.

**Multi-item planners (added in r4).** A planner that puts several items in one flow must emit a flow whose bindings merge under D6a. Two items can each be valid and still conflict: the same extension under two namespaces, or one namespace bound to different metadata. The built-in `perWindow` planner therefore splits a lane group, in claim-key order, into consecutive runs whose bindings merge, and emits one flow per run. The lane still runs one execution at a time. A window without conflicting bindings stays one flow, so its identities are unchanged. Custom planners are responsible for emitting valid flows. If they do not, the engine rejects the conflicting flow with `TXFLOW_EXTENSION_BINDING_CONFLICT` before any submission.

## 6. Invariants

Each invariant names the decisions it constrains and must have at least one test that would fail if it were broken.

- **I1. Fail before submission** (D5). A flow that needs an unregistered extension, has invalid metadata or holds an invalid extension intent is rejected before any step is built or submitted. This holds for `FlowExecutor` (sync and async), `FlowEngine`, `FlowRuntime` and TxFlowStream.
- **I2. Extension-free flows unchanged** (D4, D6). For flows with no extension, compiled fingerprints, portable YAML/JSON output and plan snapshots are byte-identical to `dfd8540d`. A golden test over the existing conformance fixtures enforces this.
- **I3. No extension-specific code** (D2). `txflow` main sources import no concrete `ExtensionIntent` or `QuickTxExtension` and no `programmable-token` type, and the `txflow` build has no dependency on `programmable-token`. A source-scan test enforces this.
- **I4. Copies preserve extensions** (D3, D4, D4a). For any extension plan, a TxFlow copy has equal `getExtensions()`, equal `getVariables()` and equal codec YAML, and shares no mutable intent instance with its source.
- **I5. Builders carry exactly the registered set** (D1, D2). Every `QuickTxBuilder` that TxFlow creates for a TxPlan step has the registered extensions, no others, and no duplicates.
- **I6. Documents never configure extensions** (D1, D5, §4). Document metadata is only validated against registered instances. A document cannot cause an extension to be constructed, and a metadata mismatch (id, `schema_version`, `validateMetadata`) is rejected.
- **I7. Lossless extension intents** (D6, D7). An unknown field, or any field that does not round-trip, in an extension intent is rejected at compile time with a path-precise diagnostic.
- **I8. Fingerprint covers extensions** (D6, D6a). Changing any `spec.extensions` field (including a nested `deployment` value), or any field of an extension intent, changes the compiled fingerprint. Reordering `deployment` map keys does not. This holds for Java steps (bindings hoisted from plans) in M1 and for portable steps in M2.
- **I9. No batching of extension items** (D10). A planner never merges an item that holds an `ExtensionIntent`.
- **I10. Pipelined visibility under C1** (D9, D9a). An extension that reads through `ExtensionBuildContext.getUtxoSupplier()` finds, by address scan, a pending output of an earlier PIPELINED step S at the scanned address **if and only if** the step declares `funding_from: [S]` or a legacy dependency on S. With `needs: [S]` alone it does not find it.
- **I11. No reinterpretation** (D4a). A literal `${other}` value in an extension intent is never re-expanded:
  - **FlowExecutor path:** it survives every copy, snapshot and retry unchanged, with a plan variable `other` present and without one.
  - **Portable path (FlowEngine, FlowRuntime, compiler, writer):** an already-bound plan that still has `variables` is rejected with `TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES` before submission. Once its variables are cleared, the same plan compiles and runs with the literal value unchanged. A portable binding value containing `${other}` stays literal (M2).
  - Legacy flow variables never reach an extension plan.
- **I12. Exact pending lookups stay scoped** (D9a). An extension's `getTxOutput` on a pending output of S returns empty unless the step declares a legacy dependency on S or holds a core `flow_output` reference to S. D9a widens nothing.
- **I13. No unused bindings** (D6a). A flow whose merged bindings include a namespace that no extension intent uses is rejected with `TXFLOW_EXTENSION_BINDING_UNUSED` at parse, compile and write, whether or not that binding is registered or valid.

## 7. Compatibility

- **Java API.** Additive only (D1, D3). No signature changes.
- **Behaviour.** `TxFlow.validate()` no longer reports extension steps. Executing such a flow without registering the extension fails in the executor before submission (I1) instead of in `validate()`. It still fails before any submission.
- **Diagnostics.** `TXFLOW_NON_PORTABLE_EXTENSION` is no longer emitted (D5). Callers that matched on it see `TXFLOW_EXTENSION_UNREGISTERED` instead.
- **Schema.** `spec.extensions` is an additive optional field (OQ2). Older readers reject it under the default unknown-field policy. Under `WARN` or `IGNORE` they drop it, and the namespaced intent then fails strict materialization. Either way they fail closed.
- **Fingerprints.** Unchanged for extension-free flows (I2). Extension flows could not compile before, so they have no prior fingerprint.
- **Experimental SPI.** The QuickTx extension SPI is experimental (`extension/package-info.java`). TxFlow's extension entry points are marked experimental too.

## 8. Implementation Milestones

Each milestone is one PR.

### M1. Java `withTxPlan` steps on FlowExecutor, FlowEngine and FlowRuntime

- **Entry gate:** ADR accepted, with OQ1 and OQ2 decided. M1 fixes the canonical `spec.extensions` form that the fingerprint uses.
- **Scope:**
  - D1, D2, D3, D4, D4a (`copy`, `readLiteral`) and D5, including the extension-aware `PortableFlowValidator`.
  - D6a: the `TxFlow.extensions` field, the canonical writer, the parser field, the unused check and the schema change.
  - D9a: documentation only.
- **Exit:**
  - Unit tests for I1–I6, I8 (Java steps), I10–I13, written against a **test-only fake extension** in `txflow` test sources. It is a trivial `QuickTxExtension` with one intent that scans an address through the context supplier and adds a payment via `addPreparedIntent`. This proves the design is generic without CIP-113. They cover:
    - **I10:** pending visible with `fundsFrom(S)`, absent with `needs(S)` only.
    - **I11:** literal `${other}` with and without a variable `other`, across a retry, on the FlowExecutor path. On FlowEngine, an **already-bound** plan (`codec.fromYaml(yaml, vars)`) with retained variables is rejected with `TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES` and not with any extension code; after `setVariables(Map.of())` it compiles, and the literal survives.
    - **I13:** a core-only flow with a binding, and a used-plus-unused pair.
  - A `FlowEngine` and `FlowRuntime` test that compiles, fingerprints and runs a Java extension flow (F1).
  - QuickTx unit tests for id-bound codec resolution, `copy` and `readLiteral`.
  - Yaci DevKit IT: CIP-113 register → mint → transfer as a 3-step flow in **SEQUENTIAL** mode.
  - The I2 golden test passes, and the full `:quicktx:test` and `:txflow:test` suites pass on Java 17.

### M1b. CIP-113 C1 conformance (programmable-token, separate PR)

- **Entry gate:** M1 merged.
- **Scope:** read registry nodes and the coordination UTxO through the build's `UtxoSupplier`.
- **Exit:** the M1 IT also passes in **PIPELINED** mode, with `fundsFrom` edges from the register step (D9a). A unit test shows that a pending registry node is visible with `fundsFrom(register)` and that the build fails closed (registry node not found) with `needs(register)` only.

### M2. Portable YAML

- **Entry gate:** M1 merged.
- **Scope:** D6 compilation of templates (strict core plus `readLiteral` for extension intents), D7, D8; retire `TXFLOW_EXTENSION_TEMPLATE_UNSUPPORTED`.
- **Exit:**
  - Tests for I7, I8 (portable steps) and I11 (a binding value containing `${other}`).
  - Conformance fixtures:
    - a valid extension flow;
    - an unknown field;
    - a non-round-tripping field;
    - a binding conflict;
    - an unregistered extension;
    - a `flow_output` inside an extension intent;
    - a core-only flow with a binding, and a used-plus-unused binding map (I13), each with round-trip and fingerprint assertions.
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
  - a duplicate registration with different instances;
  - an unused binding, registered and unregistered (I13);
  - an already-bound plan with retained variables on FlowEngine (I11).

  Each must fail before submission, with its stable code.
- **Runtime negative case (not a pre-submission diagnostic):** a pipelined extension step that reads a pending protocol output without a `funding_from` edge (I10, R5). The build fails with the extension's own error, such as "registry node not found", when that step builds. The producer step may already have been submitted. The test asserts that this step builds nothing, not that it gets a stable TxFlow code.
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
- **R5.** Flow authors must declare `funding_from` to let a pipelined extension step see protocol outputs created by an earlier step (D9a). Forgetting it fails closed with an extension error, not a TxFlow diagnostic. Mitigation: document the edge in the extension's TxFlow guide.
- **R6.** `FlowUtxoSupplier.getTxOutput` does not filter outputs spent by earlier flow steps (`FlowUtxoSupplier.java:95-104`). This is existing behaviour and affects core lookups too. An extension that reads protocol state by exact lookup in the pipelined window can see an output an earlier step already spent. The ledger then rejects the double spend at submission, so no funds are at risk, but the step fails late. Mitigation: D9a tells extensions to read spendable protocol state by address scan. Follow-up (out of scope): spent-filter exact lookups in `FlowUtxoSupplier`, under its own change and with its own tests, because it changes core behaviour.
- **R2.** The round-trip guard (D7) may reject an extension that normalizes its input, for example an extension that writes a canonical form different from the authored one. That is intended (ADR 0002 Decision 6), but it constrains extension authors.
- **R3.** Inputs an extension selects (for example a shared CIP-113 registry node) can contend across concurrent executions without lease protection. Ledger validation still catches a double spend, but the losing execution fails rather than waiting.
- **R4.** The experimental QuickTx SPI may change. TxFlow's extension surface is experimental too, so it can follow.

## 12. Open Questions

- **OQ1. Name of the flow-level block.** ADR 0002 says portable documents reject unknown fields "except within a documented `extensions` map" (`0002-…md:2485`). That sentence could be read as reserving `extensions` for something else.
  - (a) `spec.extensions`: the same name and shape as TxPlan's block.
  - (b) `spec.quicktx_extensions`.

  **Lean: (a).** It is the documented map that sentence anticipated, and authors already know it from TxPlan. **M1 entry gate (r2):** accepting this ADR without an objection adopts the lean.
- **OQ2. Schema versioning.**
  - (a) An additive field in v1alpha1.
  - (b) A new `v1alpha2`.

  **Lean: (a).** The version is alpha, the change is additive, and older readers fail closed (§7). **M1 entry gate (r2):** accepting this ADR without an objection adopts the lean.
- **OQ3. `withTxContext` factory steps.** If the engine pre-registers extensions on factory builders, a factory that registers its own instance with the same id would now throw "Extension already registered" (`QuickTxBuilder.java:106-108`).
  - (a) Apply the registrations to TxPlan steps only, so factories behave as today.
  - (b) Apply them everywhere and document the change.
  - (c) Relax `QuickTxBuilder.withExtension` to accept an instance with an equal id and metadata.

  **Lean: (a).** There is no behaviour change, and factories are the legacy path.
- **OQ4. `flow_output` inside extension intents.** A future optional SPI could let an intent expose and accept resolved `UtxoRef`s. **Lean:** defer until an extension needs it. D8 rejects these references until then.
- **OQ5. Spending identities for inputs an extension selects.** A future optional SPI method (for example `spendingIdentities(intent, metadata)`) would let the compiler lease protocol UTxOs. **Lean:** defer; document R3.
- **OQ6. Fingerprint scope.** Should the fingerprint include the registered extension's implementation version (for example its jar version)? **Lean: no.** `schema_version` and the metadata define the contract. The implementation version is operational data for the journal, not part of the definition's identity.

## 13. Related Findings (Out of Scope)

- `FlowUtxoSupplier.getTxOutput` does not filter spent outputs (R6). Follow-up issue to be filed; not changed here.
- CIP-113 reads registry nodes outside the build supplier (`DefaultCip113ProtocolService.java:79`, `:99-100`; `Cip113BuildExtension.java:98`). This is tracked as M1b and in issue #700.
- The legacy `FlowDocument` YAML path copies plans through core YAML (`yaml/FlowDocument.java:428`, `:748`). It will keep rejecting extension plans, now with `TXFLOW_EXTENSION_UNREGISTERED` instead of a Jackson error.
- `TxFlow.fromYaml()` parses with `TxFlowCodec.standard()` (`TxFlow.java:277`), and `TxFlow.toYaml()` writes through the legacy `FlowDocument` (`TxFlow.java:265-266`). Both stay extension-free. Callers who need extensions use `TxFlowCodec.withExtensions(...)`.
