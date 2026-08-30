# ADR-CIP113-001: CIP-113 QuickTx Extension and TxPlan Compatibility

**Date**: 2026-08-30

**Status**: Proposed / In Review

**Scope**: `cip/cip113`, `quicktx`, `function`

**Reviewed change**: [PR #653](https://github.com/bloxbean/cardano-client-lib/pull/653), commit `6fc80337136c6c09c4537d001d98305f739d1c41`

**Decision owners**: Cardano Client Lib maintainers

---

## 1. Executive Summary

PR #653 is a strong CIP-113 prototype. It contains valuable protocol work: deployment resolution,
smart-wallet derivation, registry lookup, policy derivation, datum/redeemer codecs, applied-script
resolution, reference-script reuse, and a resettable devnet end-to-end suite.

The proposed public API resembles QuickTx, but its current mechanics are not aligned with the
QuickTx intent architecture:

- operations are stored as `Runnable` declarations rather than semantic `TxIntent` values;
- operations may materialize before the fluent declaration is complete, making call order observable;
- `compose(...)` performs deployment resolution, registry reads, UTxO selection, and script lookup;
- `ProgrammableTokenTx` depends on subclass hooks that are lost during TxPlan deserialization;
- `compose(TxPlan)` supports only an in-memory plan and is not YAML round-trip compatible;
- a protocol-specific `BackendService` subtype and `QuickTxBuilder` subtype create parallel API stacks.

### Decision

CIP-113 should be implemented as a **QuickTx extension** using semantic, serializable intents and a
generic build-lifecycle SPI. The `quicktx` module owns only extension contracts and registries. The
`cip113` module owns all CIP-113 intent types, codecs, runtime services, protocol rules, and lifecycle
implementation. There must be no dependency from `quicktx` to `cip113`.

True TxPlan compatibility is part of the target design. A plan must preserve CIP-113 operations
through YAML serialization and reconstruct an ordinary `Tx` containing registered CIP-113 intents.
It must not serialize live backend objects, resolved UTxOs, scripts, registry caches, or mutable
`ProgrammableTokenTx` state.

PR #653 should not establish its current classes as stable public API until the correctness blockers
and extension design in this ADR are addressed. If merged incrementally, the module and APIs should
be explicitly marked experimental.

---

## 2. Context and Architectural Forces

### 2.1 QuickTx vision

QuickTx is intended to provide:

1. **Declarative authoring**: fluent verbs record transaction intentions.
2. **Deferred resolution**: chain reads and UTxO selection happen during build, not declaration.
3. **Order-independent declarations**: equivalent sets of intentions produce equivalent transaction
   semantics regardless of fluent call ordering, except where ordering is explicitly part of the API.
4. **Composition**: multiple transaction fragments can be composed into one final transaction.
5. **TxPlan portability**: semantic intentions can be serialized, variable-resolved, reviewed,
   reconstructed, and executed later.
6. **Provider independence**: domain protocols consume backend capabilities without changing the
   identity or type of the backend.
7. **Fail-closed behavior**: an unavailable registry or unresolved policy must not silently route a
   programmable token through an ordinary native-token path.

### 2.2 CIP-113 requirements

CIP-113 transactions need more than ordinary payment and mint intents:

- registry membership and covering-node resolution;
- smart-wallet address derivation;
- coordinated selection of PLB inputs and ordinary ADA inputs;
- protocol, registry, global-state, and script reference inputs;
- role-specific withdraw-zero invocations;
- canonical input, reference-input, withdrawal, mint-policy, and output indexes;
- a finalization phase before script-cost evaluation;
- verification that balancing did not invalidate resolved indexes;
- versioned deployment data while the CIP and reference implementation remain subject to change.

These requirements justify a protocol extension, but not tight coupling from QuickTx to CIP-113.

---

## 3. Assessment of PR #653

### 3.1 Strengths to preserve

- Separate `cip:cip113` module boundary.
- Automatic smart-wallet and policy-id derivation.
- Registry-selected substandard credentials rather than caller-selected logic scripts.
- Support for applied scripts and published reference scripts.
- Deployment resolution from a bootstrap transaction.
- Pure domain models and ledger-ordering helpers.
- Blueprint agreement tests for manually maintained codecs.
- Reset-and-redeploy devnet integration tests that assert resulting on-chain state.
- Explicit guards for late index changes and missing protocol components.
- Separation of read-side functionality from transaction authoring as a general concept.

### 3.2 Merge recommendation

The protocol implementation is worth retaining, but the current public transaction API should be
reworked before it is treated as stable. Sections 4.1 through 4.10 are correctness, compatibility,
or lifecycle blockers. Sections 4.11 through 4.13 are API-quality follow-ups. Sections 5 and 6
define the recommended replacement architecture.

---

## 4. Review Findings

### 4.1 P0: Eager per-policy materialization makes fluent order observable

`payToAddress(...)` and `withRedeemer(...)` both call `materialiseIfReady(...)`. Once a policy is
materialized, later declarations for that policy are ignored by input selection and programmable
change because `materialised.contains(policy)` returns immediately.

Example:

```java
new ProgrammableTokenTx()
        .from(sender)
        .payToAddress(alice, amount1)
        .withRedeemer(policyId, redeemer)
        .payToAddress(bob, amount2);
```

The first payment is materialized when `withRedeemer(...)` runs. The second payment output is added,
but its quantity is absent from the previously completed input selection and change calculation.

This can produce insufficient inputs, excess programmable change, or a late validator failure. It
also contradicts the module documentation that verbs only record and that all resolution happens in
one pass.

**Required direction**: record typed operations only. Aggregate and materialize all CIP-113
operations once, after the declaration set is closed and before script evaluation.

### 4.2 P0: Current TxPlan support is in-memory only and silently loses semantics

`ProgrammableQuickTxBuilder.compose(TxPlan)` wires live `ProgrammableTokenTx` instances already held
inside an in-memory plan. This does not provide TxPlan serialization compatibility.

Current failure mode:

1. `ProgrammableTokenTx` extends `Tx`.
2. `TxPlan.toYaml()` accepts it through `tx instanceof Tx`.
3. An unwired programmable transaction holds its operations in `List<Runnable> declarations`, not in
   `getIntentions()`.
4. The serializer therefore emits the ordinary `from` state but loses programmable operations.
5. `TxPlan.from(...)` always creates `new Tx()`.
6. Even a previously materialized plan loses the programmable subtype's pre/post-evaluation index
   hooks and mutable protocol state after deserialization.

This is more dangerous than an unsupported-operation error because the resulting YAML can look
valid while representing a different transaction.

**Required immediate fix**: until semantic intents exist, reject YAML serialization of
`ProgrammableTokenTx` and document `compose(TxPlan)` as in-memory-only.

**Target fix**: serialize CIP-113 intent values and reconstruct an ordinary `Tx` containing those
intents. Execution behavior comes from a registered CIP-113 extension, not from the Java subtype.

### 4.3 P0: Legal inline datum behavior is not preserved

The current CIP-113 reference implementation permits `NoDatum` or a bounded inline datum and forbids
datum hashes and reference scripts on seizable outputs. Its third-party path requires paired outputs
to preserve the input address, datum, and reference script exactly.

The PR currently:

- rejects every non-null programmable mint output datum;
- recreates third-party continuing outputs without preserving an inline datum;
- documents contradictory expectations: method-level documentation says datum/reference script are
  preserved, while the output construction emits neither.

This makes legitimate inline-datum tokens, including CIP-68-related shapes allowed by the current
reference implementation, impossible to mint or seize correctly.

**Required fix**:

- distinguish inline datum from datum hash;
- allow supported inline datum shapes according to the selected deployment version;
- preserve inline datum byte-for-byte in paired third-party outputs;
- reject unsupported datum hashes and reference scripts early with role-specific errors;
- version this behavior if a deployment intentionally targets an older contract surface.

### 4.4 P0: Asset names are round-tripped through UTF-8

Third-party output construction decodes the asset-name half of a unit to `String` and constructs a
new amount from that string. Cardano native asset names are arbitrary bytes, not necessarily UTF-8.
Invalid or non-canonical UTF-8 sequences can be changed by decode/re-encode.

**Required fix**: retain the original unit and change only the quantity:

```java
Amount.asset(held.getUnit(), remainingQuantity)
```

Add regression coverage using non-text and invalid-UTF-8 asset-name bytes.

### 4.5 P0: A reference input is added after intention application

`buildProofs(...)` discovers an incidental unregistered policy during `preTxEvaluation`, calls
`readFrom(coveringNode)`, and immediately searches the current transaction for that reference input.
At this point `readFrom(...)` only appends a new intention; the already-built transaction is not
updated. Index resolution therefore fails for a PLB input containing an incidental unregistered
policy.

**Required fix**: inspect every selected PLB input and resolve proofs for all co-resident policies
during the extension preparation/materialization phase, before reference-input intents are applied.

### 4.6 P1: Inherited `Tx` overloads bypass programmable routing

Only two simple `payToAddress` overloads are overridden. Richer inherited methods call the protected
six-argument payment implementation directly and bypass the programmable policy routing path. The
subtype also needs a growing list of covariant-return overrides because `Tx` is declared as
`AbstractTx<Tx>`.

This means the subclass cannot enforce a coherent programmable-token contract across the full `Tx`
surface.

**Required direction**: prefer explicit CIP-113 verbs that add semantic intents. Do not rely on
overriding a subset of ordinary `Tx` verbs to infer protocol behavior from chain state.

### 4.7 P1: Registry cache invalidation is part of application correctness

`RegistryLookup.Scanning` caches indefinitely. After registration or update, the integration test
manually calls `registryLookup().invalidate()` so later steps see the mutation. An ordinary
register-wait-mint flow using the same service can otherwise continue treating the policy as absent.

Manual cache invalidation is too low-level for the default service and is easy to omit.

**Required fix**:

- guarantee refresh-on-miss for membership queries where absence affects routing;
- invalidate or refresh after confirmed registry mutations;
- consider bounded/height-aware snapshots rather than an unqualified permanent cache;
- do not expose cache invalidation as a normal application workflow.

### 4.8 P1: Manual constructor never becomes wired

The public manual constructor assigns deployment, registry, and UTxO supplier but does not set
`wired = true`. Subsequent declarations remain queued and the post-balance unwired guard rejects the
transaction.

**Required fix if the constructor remains**: validate all dependencies and set the state consistently.
The preferred extension architecture removes the need for this constructor from the public API.

### 4.9 P1: Burn conflates transfer and issuance redeemers

A burn invokes both the token's transfer logic and minting logic. These are independent credentials
and may use different validators and redeemer schemas. The current API supplies one `PlutusData` and
passes it to both roles.

**Required API**: make role-specific authorization explicit, for example:

```java
burn(policyId, assets,
        BurnAuthorization.builder()
                .transferRedeemer(transferRedeemer)
                .issuanceRedeemer(issuanceRedeemer)
                .build());
```

Equivalent role-specific fields are required in the TxPlan intent.

### 4.10 P1: `compose(...)` performs backend work

`ProgrammableQuickTxBuilder.compose(...)` calls `wire(...)`. Wiring resolves the deployment, creates
the registry, reads coordination/template UTxOs, executes declarations, performs registry queries,
selects inputs, and resolves scripts. Therefore compose can perform network I/O and fail before
`build()` or `complete()`.

This violates the expected QuickTx boundary between declaration/composition and execution. It also
makes reusable plans sensitive to the chain state at compose time instead of build time.

**Required fix**: extension registration at compose time must be side-effect free. Runtime resolution
starts in the build preparation phase.

### 4.11 P2: Public service surface mixes results, exceptions, and null

The read API states that chain operations follow backend `Result` conventions, but required and
optional UTxO lookups expose nullable `Utxo` values, while lazy resolution may throw
`Cip113Exception`. Global-state lookup swallows backend failures and returns null, conflating absence
with provider failure.

**Recommendation**:

- expose `Result<Utxo>` for required chain data;
- expose `Result<Optional<Utxo>>` for genuinely optional data;
- keep coordination/template/global-state wiring package-private where possible;
- choose one error model for public read methods and document build-time exception translation.

### 4.12 P2: `withAdaBuffer(Amount)` accepts invalid units

`withAdaBuffer` reads only `buffer.getQuantity()` and does not verify that the unit is lovelace or
that the input is non-null and positive.

**Recommendation**: accept `BigInteger lovelace` or validate the `Amount` unit and quantity.

### 4.13 P2: Repository logging convention

New integration-test code uses `System.out.println`. Repository guidelines require SLF4J. Replace
console writes with a test logger or structured assertion descriptions.

---

## 5. Architectural Decision

### 5.1 Dependency direction

```text
application
    |
    +--> quicktx ------------------------------+
    |      QuickTxBuilder                      |
    |      Tx / TxPlan                         |
    |      extension SPI and registries        |
    |                                          |
    +--> cip113 -------------------------------+
           depends on quicktx
           Cip113Extension
           CIP-113 TxIntent implementations
           Cip113Runtime / chain-data provider
           deployment and protocol models

quicktx MUST NOT depend on cip113
```

### 5.2 Two capabilities, one extension

A QuickTx extension has two related responsibilities:

1. **Plan codec registration**: register externally defined semantic intent types.
2. **Build lifecycle participation**: aggregate and resolve those intents at the correct transaction
   lifecycle points.

A codec-only registry is insufficient for CIP-113 because canonical indexes must be finalized after
all intents have contributed to the transaction and before script-cost evaluation.

Illustrative SPI:

```java
public interface QuickTxExtension {
    String id();

    void registerIntentTypes(TxIntentTypeRegistry registry);

    TxBuildExtension buildExtension();
}

public interface TxBuildExtension {
    default void validate(ExtensionPlanContext context) {}

    default TxBuilder prepare(ExtensionBuildContext context) {
        return TxBuilder.noOp();
    }

    default TxBuilder beforeScriptEvaluation(ExtensionBuildContext context) {
        return TxBuilder.noOp();
    }

    default TxBuilder afterBalanceVerify(ExtensionBuildContext context) {
        return TxBuilder.noOp();
    }
}
```

Names are illustrative. The final SPI should use existing `TxBuilder` and context conventions.

### 5.3 Registration scope

Extension registration must be per codec and per builder, not global mutable state:

```java
Cip113Extension cip113 = Cip113Extension.create(
        backendService,
        Cip113Deployments.PREVIEW);

TxPlanCodec codec = TxPlanCodec.builder()
        .withExtension(cip113)
        .build();

TxPlan plan = codec.fromYaml(yaml);

new QuickTxBuilder(backendService)
        .withExtension(cip113)
        .compose(plan)
        .complete();
```

Reasons to avoid a global registry:

- deterministic tests;
- no cross-application classloader leakage;
- safe use of multiple CIP-113 deployments in one process;
- explicit runtime configuration;
- predictable behavior in application servers and plugins.

`ServiceLoader` may provide codec discovery, but runtime services and deployment selection remain
explicit.

### 5.4 Unknown extensions fail closed

The plan codec and builder must reject:

- an unregistered intent type during YAML parsing;
- a registered intent type with no corresponding execution extension;
- a plan deployment reference that the runtime cannot resolve;
- incompatible extension schema or protocol versions.

Unknown fields may follow existing forward-compatibility rules, but unknown semantic operation types
must never be ignored.

---

## 6. CIP-113 Semantic Intent Model

### 6.1 Proposed intent types

| Intent type | Purpose | Role-specific data |
|---|---|---|
| `cip113_transfer` | Owner-authorized programmable transfer | transfer redeemer |
| `cip113_mint` | Mint registered programmable assets | issuance redeemer, optional inline datum |
| `cip113_burn` | Spend and burn programmable assets | transfer redeemer + issuance redeemer |
| `cip113_third_party_transfer` | Seize, claw back, or forced transfer | holder + third-party redeemer |
| `cip113_register` | Insert a registry node | registration id + node spec + issuance redeemer |
| `cip113_update_registry` | Update mutable registry fields | policy reference + issuance redeemer |
| `cip113_unfrack` | Holder-driven UTxO restructuring, when implemented | unfracking redeemer |

The intent names should be namespaced so independently developed extensions do not collide.

### 6.2 Java authoring facade

`ProgrammableTokenTx` may remain as a convenience facade, but its methods only add typed intents. It
must not hold backend services, registry caches, resolved UTxOs, script objects, or executable
`Runnable` declarations.

Preferred explicit API:

```java
new ProgrammableTokenTx()
        .from(owner)
        .transfer(receiver, amount, transferRedeemer)
        .transfer(secondReceiver, secondAmount, transferRedeemer);
```

Mint and burn should be explicit rather than inferred exclusively from sign:

```java
new ProgrammableTokenTx()
        .from(issuer)
        .mint(policyId, receiver, assets, issuanceRedeemer, inlineDatum);

new ProgrammableTokenTx()
        .from(owner)
        .burn(policyId, assets,
                BurnAuthorization.of(transferRedeemer, issuanceRedeemer));
```

This avoids ambiguous `withRedeemer(policy, data)` semantics and prevents accidental use of one
redeemer for multiple protocol roles.

### 6.3 Compatibility with ordinary `Tx`

Deserialized CIP-113 plans should create an ordinary `Tx` containing CIP-113 intents. The Java
authoring subtype is not required during replay. The registered extension identifies its intents and
executes them.

This avoids adding `cip113` subtypes to `quicktx` serializers and ensures plans are driven by data,
not by concrete transaction classes.

### 6.4 Register-and-mint references

`registeredPolicyId()` is mutable build output and cannot safely connect declarative operations.
Registration should publish a named policy reference that later intents resolve during the same
build:

```java
new ProgrammableTokenTx()
        .registerToken("usd", spec, registrationRedeemer)
        .mint(PolicyRef.ref("cip113://usd"), receiver, assets, issuanceRedeemer, null);
```

The design should reuse the existing `PolicyRef` resolution mechanism if its ownership and lifecycle
fit. Otherwise add a CIP-specific named reference without changing existing `PolicyRef` semantics.

---

## 7. TxPlan Design

### 7.1 Plan contents

A plan stores:

- semantic CIP-113 intent fields;
- variables and references;
- a CIP-113 extension schema version;
- a deployment reference, such as a named network deployment or bootstrap transaction hash;
- structured Plutus data using the existing `PlutusDataYamlUtil` representation.

A plan does not store:

- `BackendService` or service implementation classes;
- resolved registry/coordination/global-state UTxOs;
- fetched or cached scripts;
- protocol parameters;
- runtime lambdas, suppliers, or `Runnable` declarations;
- final ledger indexes;
- transaction-local mutable materialization state.

### 7.2 Illustrative YAML

```yaml
version: "1.0"
extensions:
  cip113:
    schema_version: "1"
    deployment: preview

variables:
  owner: addr_test1...
  receiver: addr_test1...
  policy_id: 0123...

context:
  fee_payer: ${owner}

transaction:
  - tx:
      from: ${owner}
      intents:
        - type: cip113_transfer
          policy_id: ${policy_id}
          receiver: ${receiver}
          amounts:
            - unit: ${policy_id}4d79546f6b656e
              quantity: 100
          transfer_redeemer:
            constructor: 0
            fields: []
```

The exact extension metadata location should be decided with the broader TxPlan schema owner. If
generic plan-level extensions are not desired, deployment can be an intent field, at the cost of
repetition.

### 7.3 Codec extensibility

The current static `TxIntent@JsonSubTypes` list cannot be the only registration mechanism because
`quicktx` cannot import higher-level module classes.

The codec registry should support:

```java
registry.register(
        "cip113_transfer",
        Cip113TransferIntent.class,
        new Cip113TransferIntentCodec());
```

Jackson `NamedType` registration is a possible implementation detail, but it must be applied to the
codec's mapper before deserialization. The existing process-wide static mapper should not be mutated
after concurrent use begins.

### 7.4 Variable resolution

Every CIP-113 intent must implement `resolveVariables(...)` for:

- addresses;
- policy ids and named policy references;
- asset units and quantities;
- deployment reference when represented in the intent;
- registry credentials/global-state policy ids;
- structured redeemers and inline datums.

Resolution must occur before semantic validation and chain access.

### 7.5 Round-trip contract

For every serializable CIP-113 operation:

```text
Java intent -> YAML -> TxPlan -> YAML -> TxPlan
```

must preserve semantic equality. Runtime-resolved data may differ between builds because chain state
can change; the declared operation must not.

---

## 8. Build Lifecycle

### 8.1 Required phases

```text
1. Parse / author
   - create semantic intents only

2. Resolve variables
   - no chain access

3. Validate declarations
   - conflicts, required fields, deployment reference, role-specific redeemers

4. Extension preparation
   - resolve deployment snapshot
   - load a fresh registry view
   - aggregate all CIP-113 intents across composed Tx fragments
   - select PLB and funding inputs without overlap
   - resolve co-resident policy proofs
   - add outputs, withdrawals, scripts, and reference inputs

5. Apply ordinary and extension intentions
   - construct the complete transaction shape

6. Before script evaluation
   - compute canonical indexes
   - finalize CIP-113 redeemers
   - declare required signers
   - snapshot index-sensitive transaction shape

7. Balance
   - no unexpected input or output mutation

8. After balance verification
   - verify index-sensitive shape and redeemer bindings
   - fail rather than rewrite data after evaluation/fee calculation
```

### 8.2 Aggregation scope

The extension must aggregate CIP-113 intents across every transaction fragment passed to one
`compose(...)` call. Resolving each `ProgrammableTokenTx` independently can select the same UTxOs,
duplicate withdrawals, or compute conflicting change.

At minimum, aggregation keys include:

- owner/holder smart wallet;
- policy id;
- action role;
- logic credential/reward account;
- global-state policy;
- funding address and already reserved UTxOs.

### 8.3 Input reservation

The materializer must maintain one build-local set of reserved input references. Selection for later
policies or fragments excludes previously selected PLB and ADA inputs. This addresses the PR's known
multi-policy contention and makes composition deterministic.

### 8.4 Registry snapshot

All membership and covering-node decisions within one build must use a coherent registry snapshot.
The snapshot should expose its source or observed chain point where the backend supports it.

Absence-dependent operations should refresh on cache miss or use an explicitly fresh snapshot.
Registration and update operations must invalidate the shared cached view after confirmation, without
requiring application code to call `invalidate()`.

---

## 9. Service and API Boundaries

### 9.1 Do not subtype `BackendService`

`ProgrammableBackendService` turns a protocol facade into a new service-locator type and requires a
decorator that forwards every inherited backend getter. This creates maintenance and binary-compatibility
cost whenever `BackendService` evolves.

Preferred composition:

```java
Cip113Service cip113 = Cip113Service.create(
        backendService,
        Cip113Deployments.PREVIEW);

QuickTxBuilder builder = new QuickTxBuilder(backendService)
        .withExtension(cip113.extension());
```

If a generic extension method is not introduced immediately, this is acceptable as an interim API:

```java
new Cip113QuickTxBuilder(backendService, Cip113Deployments.PREVIEW)
```

It is still preferable to wrapping the backend in a new subtype.

### 9.2 Public read service

The public service should focus on application use cases:

```java
interface Cip113Service {
    Cip113Deployment deployment();

    Result<Cip113Deployment> resolveDeployment();

    Address smartWalletAddress(Address owner);

    Result<List<Amount>> getBalance(Address owner);

    Result<List<Amount>> getProgrammableBalance(Address owner);

    Result<Boolean> isProgrammable(String policyId);

    Result<RegistryNode> getRegistryNode(String policyId);

    Result<List<RegistryNode>> getRegistry();

    Result<String> derivePolicyId(Credential mintingLogic);

    QuickTxExtension extension();
}
```

Low-level coordination/template/global-state/script supplier access belongs in a package-private
runtime contract or an advanced customization interface.

### 9.3 Indexer extension point

Preserve an indexer-oriented data provider, but make it explicit and narrow:

```java
interface Cip113ChainDataProvider {
    Result<RegistrySnapshot> registrySnapshot(Cip113Deployment deployment);

    Result<Optional<Utxo>> findGlobalState(String policyId);

    Result<ResolvedDeployment> resolveDeployment(Cip113Deployment deployment);

    Result<Optional<PlutusScript>> findScript(String scriptHash);
}
```

The default implementation may scan through generic backend services. An indexer implementation can
perform exact lookups without replacing the whole `Cip113Service`.

---

## 10. Deployment and Schema Versioning

CIP-113 remains under active development and its reference implementation can change independently
of CCL. A single unversioned `Cip113Deployment` plus hand-written codecs risks applying the wrong
rules to a deployment with a different datum/redeemer surface.

Each deployment descriptor should carry:

- deployment identifier;
- network;
- bootstrap transaction hash;
- protocol/API surface version;
- codec version;
- known validator/script hashes after resolution;
- feature flags or capabilities where contract versions differ.

Example:

```java
enum Cip113Capability {
    INLINE_DATUM_OUTPUTS,
    STANDALONE_THIRD_PARTY_DELEGATE,
    REGISTRY_NODE_UPDATE,
    GLOBAL_STATE,
    MAX_INLINE_DATUM_BOUND
}
```

Transaction intents should validate required capabilities against the selected deployment before any
inputs are selected.

---

## 11. Alternatives Considered

### 11.1 Keep the current specialized backend and builder

**Rejected as the target architecture.** It is easy to demonstrate but creates one backend wrapper
and builder subtype per protocol, performs work during compose, and does not solve serialization.

### 11.2 Add CIP-113 classes directly to `TxIntent@JsonSubTypes`

**Rejected.** This creates a dependency from `quicktx` to `cip113` or forces CIP-113 types into the
wrong module. Future CIP integrations would expand the same central list.

### 11.3 Serialize `ProgrammableTokenTx` as a transaction subtype

**Rejected.** It couples plan schema to Java inheritance, requires subtype factories in QuickTx,
and still serializes mutable runtime state poorly. TxPlan should serialize semantic intents.

### 11.4 Make CIP-113 plans unsupported

**Acceptable only as a short-term safeguard.** CIP-113 is a strong TxPlan use case, and designing
semantic intents now prevents another rewrite later. Full YAML support may be staged while the CIP
is experimental, but the public API should not block it.

### 11.5 Add only an intent codec registry

**Insufficient.** CIP-113 needs transaction-wide preparation and pre-evaluation finalization. Codec
registration without lifecycle participation would reconstruct data but could not safely build it.

---

## 12. Consequences

### Positive

- No direct QuickTx-to-CIP113 dependency.
- Reusable extension model for future protocol/CIP modules.
- Real TxPlan YAML compatibility.
- One resolution pass removes fluent-order bugs.
- Explicit role semantics improve safety and error messages.
- Ordinary `Tx` replay works without reconstructing a specialized subtype.
- Multi-fragment composition can reserve inputs and deduplicate withdrawals globally.
- Backend provider implementations remain unchanged.

### Costs

- QuickTx needs a small, carefully designed extension SPI and codec registry.
- The TxPlan codec can no longer rely exclusively on a static annotation subtype list.
- A pre-script-evaluation lifecycle hook becomes public extension surface and must be stable.
- PR #653 transaction-building code requires decomposition into semantic intent and runtime
  materializer layers.
- Extension schema and deployment versioning require explicit compatibility rules.

### Risks

- An overly broad lifecycle SPI could expose QuickTx internals and become difficult to evolve.
- Extension ordering can become ambiguous if multiple extensions mutate the same transaction.
- YAML polymorphism must remain safe; only explicitly registered types may be instantiated.
- Multiple extensions may contend for inputs or lifecycle phases.

### Mitigations

- Keep lifecycle phases minimal and transaction-oriented.
- Define deterministic extension order and reject duplicate extension ids.
- Use explicit registration, not class-name-based polymorphic deserialization.
- Provide a build-local input reservation service shared by all extensions.
- Add compatibility and composition tests before considering the SPI stable.

---

## 13. Implementation Plan

### Phase 0: Correctness stabilization in `cip113`

Goal: prevent known invalid transaction shapes independent of the final SPI.

- Fix same-policy multi-operation order dependence or temporarily reject declarations after
  materialization.
- Preserve inline datums on paired outputs.
- Preserve arbitrary asset-name bytes.
- Move incidental-policy covering-node resolution before transaction assembly.
- Fix the manual constructor or remove it.
- Split burn transfer/issuance redeemers.
- Remove application-required registry cache invalidation.
- Validate `withAdaBuffer` input.
- Add targeted regression tests from Section 15.

**Exit gate**: all P0 correctness tests pass and no test depends on a particular fluent declaration
order unless explicitly documented by the API.

### Phase 1: QuickTx extension SPI design

Goal: introduce the minimum generic surface needed by CIP-113 without protocol coupling.

- Define `QuickTxExtension` identity and registration semantics.
- Define per-codec intent type registration.
- Define build lifecycle phases and contexts.
- Define deterministic ordering for multiple extensions.
- Define unknown/missing extension failures.
- Define build-local input reservation access.
- Decide whether extension metadata belongs in `TxPlan.context` or a top-level `extensions` section.

**Exit gate**: approved SPI design plus a trivial test extension proving external intent
serialization and lifecycle invocation without any `cip113` dependency in `quicktx`.

### Phase 2: CIP-113 semantic intents

Goal: replace `Runnable` declarations with immutable or value-like semantic intentions.

- Implement transfer, mint, burn, third-party, register, and update intents.
- Implement structured redeemer/datum serialization.
- Implement variable resolution and semantic validation.
- Convert `ProgrammableTokenTx` into an authoring facade that only appends intents.
- Remove eager registry lookup and materialization from fluent verbs.
- Introduce explicit role-specific authorization objects.

**Exit gate**: Java-authored operations are order-independent and every supported intent round-trips
through YAML.

### Phase 3: CIP-113 runtime materializer

Goal: execute all semantic intents in one coherent build pass.

- Resolve and version-check deployment.
- Load one registry snapshot per build.
- Aggregate operations across composed transaction fragments.
- Reserve PLB and ADA inputs globally.
- Resolve all registered and covering-node proofs.
- Add scripts, withdrawals, outputs, and reference inputs.
- Finalize canonical indexes before script-cost evaluation.
- Verify index-sensitive transaction shape after balancing.

**Exit gate**: equivalent in-memory and YAML plans build equivalent transaction semantics against the
same chain snapshot.

### Phase 4: Public API cleanup

Goal: present one simple, stable integration path.

- Replace `ProgrammableBackendService` with `Cip113Service` composition.
- Decide whether `ProgrammableQuickTxBuilder` remains as a deprecated/interim convenience.
- Hide low-level wiring APIs not required by application callers.
- Mark version-sensitive APIs experimental until CIP-113 stabilizes.
- Update README examples for Java and YAML authoring.

**Exit gate**: one recommended happy path, one advanced customization path, and no manual cache or
deployment initialization ordering requirement.

### Phase 5: Qualification and release decision

- Run unit, property, devnet integration, plan round-trip, and composition tests.
- Test at least one indexer-backed `Cip113ChainDataProvider` implementation or fake.
- Verify behavior against the targeted reference implementation tag/deployment.
- Review API binary compatibility and experimental annotations.
- Produce release notes identifying supported CIP-113 deployment versions and known gaps.

---

## 14. Review Plan

### 14.1 Review sequence

1. **Architecture review**
   - Approve dependency direction and the need for a generic extension SPI.
   - Decide whether extension support is a stable QuickTx feature or initially experimental.

2. **QuickTx lifecycle review**
   - Confirm the exact preparation, pre-evaluation, and post-balance hooks.
   - Verify hooks cannot invalidate fee calculation or transaction balance unexpectedly.
   - Define multiple-extension ordering and failure behavior.

3. **TxPlan schema review**
   - Approve intent namespace and extension metadata location.
   - Approve codec registration and safe polymorphic deserialization.
   - Approve schema-version and deployment-reference rules.

4. **CIP-113 domain API review**
   - Approve explicit transfer/mint/burn/third-party verbs.
   - Approve role-specific redeemer types.
   - Approve register-and-mint policy references.
   - Decide which low-level runtime APIs remain public.

5. **Protocol conformance review**
   - Pin the supported CIP-113 reference tag or deployment.
   - Compare every public operation with current validator invariants.
   - Verify inline datum, global state, registry update, and third-party paired-output behavior.

6. **Implementation review**
   - Review semantic intents independently from runtime materialization.
   - Review cache freshness and chain snapshot assumptions.
   - Review multi-policy and multi-fragment input reservation.

7. **Qualification review**
   - Evaluate the full test matrix and devnet evidence.
   - Decide experimental, beta, or stable API status.

### 14.2 Required reviewers and perspectives

- QuickTx/TxPlan maintainer: lifecycle and serialization compatibility.
- CIP-113 protocol reviewer: validator and deployment conformance.
- Backend/indexer maintainer: query capabilities, cache freshness, and provider neutrality.
- Transaction balancing/redeemer-index reviewer: ordering and fee lifecycle.
- API reviewer: Java fluency, null/error semantics, and backward compatibility.
- Security reviewer: fail-closed routing, datum preservation, and registry proof handling.

### 14.3 Review questions requiring explicit decisions

1. Is the generic extension SPI justified now, or should CIP-113 remain experimental and
   non-serializable until a second extension use case exists?
2. Should extensions be registered on `QuickTxBuilder`, `TxContext`, `TxPlanCodec`, or a shared
   immutable `QuickTxEnvironment` used by all three?
3. Is `beforeScriptEvaluation` sufficient, or should QuickTx expose a more general transaction
   finalizer pipeline?
4. How is deterministic ordering defined when multiple extensions use the same lifecycle phase?
5. Should extensions share a generic input reservation service?
6. Where should plan-level extension metadata live?
7. Should `PolicyRef` be reused for register-and-mint, or should CIP-113 introduce its own named
   reference?
8. What deployment/tag is the first supported contract surface?
9. Which APIs are experimental, and what compatibility guarantee applies before CIP-113 is final?
10. Should implicit `payToAddress` routing be removed entirely or retained as a guarded convenience?

### 14.4 Review artifacts

Before implementation approval, prepare:

- a small extension SPI Java prototype;
- one external sample intent that round-trips through TxPlan;
- lifecycle sequence diagram showing ordinary intents and extension hooks;
- proposed CIP-113 Java API examples for every supported operation;
- proposed YAML examples for transfer, register-and-mint, burn, and third-party transfer;
- protocol-version compatibility table;
- test plan with named test classes and ownership.

### 14.5 Acceptance gates

The architecture is approved when:

- `quicktx` has no compile/runtime dependency on `cip113`;
- external intent types can be registered without editing `TxIntent@JsonSubTypes`;
- extension registration is immutable or safely scoped, not global mutable state;
- a YAML plan reconstructs equivalent semantic intents;
- the runtime extension sees all composed CIP-113 intents before selecting inputs;
- all index-sensitive mutation finishes before script evaluation;
- balancing changes are verified after the fact;
- unknown/missing extensions fail with actionable messages;
- one builder can execute both ordinary and CIP-113 intents in the same plan;
- multiple deployments can coexist in one JVM without registry collision.

---

## 15. Verification and Test Plan

### 15.1 Unit tests

| Area | Required cases |
|---|---|
| Declaration ordering | Permute `from`, multiple transfers, and authorization declarations; assert equivalent semantics |
| Multiple payments | Add a second same-policy payment before and after authorization |
| Asset names | Empty, UTF-8, non-UTF-8, zero byte, and 32-byte names |
| Inline datum | Mint with allowed inline datum; reject datum hash; preserve datum on third-party continuation |
| Burn authorization | Different transfer and issuance scripts/redeemers; shared script with compatible redeemer |
| Incidental policies | Registered and unregistered co-resident policies in selected PLB inputs |
| Registry freshness | Register/update followed by read/mint without manual invalidation |
| Manual wiring | Remove API or verify complete dependency validation and execution |
| Error semantics | Distinguish absent global state from backend failure |
| ADA buffer | Null, non-lovelace, zero, negative, and valid positive lovelace |

### 15.2 TxPlan tests

- Java intent to YAML to plan semantic equality for every CIP-113 intent.
- YAML to plan to YAML canonical round trip.
- Variables in addresses, units, quantities, credentials, redeemers, and deployment references.
- Unknown extension id and unknown intent type fail loudly.
- Registered codec without runtime extension fails before chain access.
- Runtime extension with incompatible schema/deployment version fails during validation.
- An unwired Java authoring facade serializes its semantic intents without composing first.
- Deserialization produces an ordinary `Tx` that executes successfully with the registered extension.
- Plans remain reusable across sequential builds and reject unsafe concurrent reuse where necessary.

### 15.3 Composition tests

- Ordinary `Tx` plus CIP-113 transfer.
- Two CIP-113 fragments for the same owner and policy.
- Two policies sharing one PLB input.
- Two policies sharing one logic credential.
- Multiple owners with distinct smart wallets.
- Register plus mint via named policy reference.
- Registry update combined with forbidden mint/burn is rejected regardless of declaration order.
- Mixed key and script withdrawals preserve ledger ordering.
- Global-state and published-script reference inputs deduplicate correctly.
- Input reservation prevents ADA and PLB UTxO reuse across fragments.

### 15.4 Property tests

- Declaration permutations produce the same normalized intent set.
- Input/reference-input ordering produces correct indexes for randomized transaction ids and indexes.
- Withdrawal ordering covers mixed credential kinds and randomized hashes.
- Asset unit reconstruction is byte-preserving for arbitrary asset-name bytes.
- Registry covering-node lookup holds across randomized sorted registries.
- Post-balance shape verification detects any randomized index-sensitive mutation.

### 15.5 Devnet integration tests

- Transfer to multiple recipients with authorization declared in different positions.
- Mint with inline datum, followed by owner transfer and third-party action preserving the datum.
- Burn using distinct transfer and issuance substandard scripts.
- Register, wait, then mint using the same long-lived service without cache invalidation.
- Register-and-mint from a YAML plan using a named policy reference.
- Execute the same semantic operation from Java and YAML and compare resulting on-chain state.
- Multi-policy co-resident UTxO handling, including an unregistered incidental policy.
- Graceful errors for missing global-state UTxO and backend lookup failure.

### 15.6 Build commands

```bash
./gradlew :quicktx:test
./gradlew :cip:cip113:test
./gradlew :cip:cip113:integrationTest --tests '*Cip113EndToEndIT*'
./gradlew clean build
```

---

## 16. Suggested PR Breakdown

Avoid combining the generic QuickTx SPI, all CIP-113 intent migration, and protocol correctness fixes
in one unreviewable change.

1. **PR A — CIP-113 correctness fixes and regression tests**
2. **PR B — Generic QuickTx intent codec registry**
3. **PR C — Generic QuickTx build lifecycle extension SPI**
4. **PR D — CIP-113 semantic intents and Java authoring facade**
5. **PR E — CIP-113 runtime materializer and composition support**
6. **PR F — TxPlan YAML schema, named policy references, and round-trip tests**
7. **PR G — Public API cleanup, documentation, and experimental/beta qualification**

Each PR should keep `./gradlew clean build` green and include focused tests for its new contract.

---

## 17. Decision Checklist

- [ ] Approve QuickTx extension SPI direction.
- [ ] Approve no `quicktx -> cip113` dependency.
- [ ] Approve semantic CIP-113 intents as the TxPlan representation.
- [ ] Approve build-time aggregation and single materialization pass.
- [ ] Approve explicit role-specific operation APIs and redeemers.
- [ ] Decide whether implicit `payToAddress` routing remains.
- [ ] Decide extension registration scope and lifecycle ordering.
- [ ] Decide plan extension metadata location and schema versioning.
- [ ] Decide policy reference mechanism for register-and-mint.
- [ ] Pin the first supported CIP-113 deployment/reference version.
- [ ] Define experimental/beta compatibility guarantees.
- [ ] Complete P0 regression tests.
- [ ] Complete TxPlan round-trip and Java/YAML equivalence tests.
- [ ] Complete devnet qualification.

---

## 18. References

- [CCL PR #653](https://github.com/bloxbean/cardano-client-lib/pull/653)
- [CIP-113 proposal PR #444](https://github.com/cardano-foundation/CIPs/pull/444)
- [CIP-113 reference implementation](https://github.com/cardano-foundation/cip113-programmable-tokens)
- [Current output-shape rules](https://github.com/cardano-foundation/cip113-programmable-tokens/blob/dba98d9e54d7c46c28980e7d4b2aae532f907594/lib/assets.ak#L208-L283)
- [Current third-party paired-output rules](https://github.com/cardano-foundation/cip113-programmable-tokens/blob/dba98d9e54d7c46c28980e7d4b2aae532f907594/validators/programmable_logic/third_party.ak#L188-L195)
- [ADR-010: QuickTx Intent Refactoring Beta Readiness Review](../010-quicktx-beta-readiness-review.md)
- [ADR-013: Unified Tx API](../013-unified-tx-api.md)
