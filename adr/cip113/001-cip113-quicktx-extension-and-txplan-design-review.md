# ADR-CIP113-001: Programmable Token QuickTx Extension, CIP-113 Protocol, and TxPlan Compatibility

**Date**: 2026-08-30

**Revision**: 4 — typed extension intents and extension-owned codecs

**Last updated**: 2026-09-02

**Status**: Accepted / Implemented (Experimental)

**Scope**: `programmable-token`, `quicktx`, `function`

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

CIP-113 should be implemented through a **Programmable Token QuickTx extension** using semantic,
serializable intents and a generic build-lifecycle SPI. The `quicktx` module owns only generic
extension contracts and registries. One top-level `programmable-token` module initially owns both
the protocol-neutral public API and the CIP-113 implementation, separated by Java package. There
must be no dependency from `quicktx` to `programmable-token` or CIP-113.

True TxPlan compatibility is part of the target design. A plan must preserve Programmable Token
operations through YAML serialization and reconstruct an ordinary `Tx` containing registered
semantic intents. It must explicitly identify the technical protocol, initially `cip-113`, and must
not serialize live backend objects, resolved UTxOs, scripts, registry caches, or mutable
`ProgrammableTokenTx` state.

This ADR makes the following foundational choices:

- the primary Programmable Token API uses explicit `transfer`, `mint`, `burn`, `thirdPartyTransfer`,
  `registerToken`, and `updateRegistry` intents rather than implicit `payToAddress` routing;
- the domain-facing types are `ProgrammableTokenTx`, `ProgrammableTokenService`, and
  `ProgrammableTokenExtension`; CIP-113 names remain on protocol-specific deployments, codecs,
  registry models, redeemers, and materializers;
- CIP-113 is the default Java protocol for the initial release, but every serialized TxPlan records
  `protocol: cip-113` explicitly;
- register-and-mint uses a Programmable Token-owned named policy reference, not QuickTx's existing
  `PolicyRef`;
- QuickTx owns a bounded post-balance re-finalization, script re-evaluation, and re-balance loop
  for extensions whose redeemer data embeds ledger indexes;
- TxPlan gains an explicit top-level `extensions` section, document-local namespace aliases such as
  `pt`, qualified intent names such as `pt:transfer`, and an external intent-codec registry;
- the exact deployment identifies the validator surface. `contract_version`, when emitted, is
  verified deployment metadata rather than a user-selected `profile`.

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
- a finalization phase before every script-cost evaluation;
- bounded re-finalization and re-evaluation when balancing changes resolved indexes;
- versioned deployment data while the CIP and reference implementation remain subject to change.

These requirements justify a protocol extension, but not tight coupling from QuickTx to CIP-113.

---

## 3. Assessment of PR #653

### 3.1 Strengths to preserve

- Separate CIP-113 implementation boundary that can move under a neutral Programmable Token module.
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

**Target fix**: serialize Programmable Token intent values and reconstruct an ordinary `Tx`
containing those intents. Execution behavior comes from `ProgrammableTokenExtension` and its
selected CIP-113 protocol adapter, not from the Java subtype.

### 4.3 P0/P1: Inline datum handling has two different severities

The current CIP-113 reference implementation permits `NoDatum` or a bounded inline datum and forbids
datum hashes and reference scripts on seizable outputs. Its third-party path requires paired outputs
to preserve the input address, datum, and reference script exactly.

#### P0 — third-party paired outputs lose inline datum

The PR recreates third-party continuing outputs without preserving an inline datum. This is a hard
on-chain failure for any holder UTxO carrying an inline datum, including a valid UTxO created by
other tooling. Method-level documentation says datum/reference script are preserved, while the
output construction emits neither.

**Required fix**: preserve inline datum byte-for-byte in paired third-party outputs and reject a
datum hash or reference script with a role-specific error before input selection.

#### P1 — mint rejects supported inline datum

The PR also rejects every non-null programmable mint output datum. The reference implementation
recommends no datum as the normal substandard default, but deliberately permits issuer-authorized
inline datums for cases such as CIP-68 metadata. This is a feature gap rather than an unconditional
transaction failure.

**Required fix**: distinguish inline datum from datum hash, allow inline datum when supported by the
selected deployment and substandard, and version the behavior if a deployment targets an older
contract surface.

### 4.4 P0: Asset names are round-tripped through UTF-8

Third-party output construction decodes the asset-name half of a unit to `String` and constructs a
new amount from that string. Cardano native asset names are arbitrary bytes, not necessarily UTF-8.
Invalid or non-canonical UTF-8 sequences can be changed by decode/re-encode. A second corruption path
exists for a name that is valid UTF-8 but begins with `0x`: `new Asset(name, ...)` interprets that
prefix as a hex-encoded asset name rather than literal bytes.

**Required fix**: retain the original unit and change only the quantity:

```java
Amount.asset(held.getUnit(), remainingQuantity)
```

Add regression coverage using non-text, invalid-UTF-8, and valid UTF-8 names beginning with `0x`.

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

In the current implementation that stale absence routes to the ordinary `Tx` path. Because the
programmable tokens are held at the smart-wallet address rather than the ordinary `from` address,
the typical symptom is insufficient funds or unresolved minting policy—not a successful rules
bypass. It remains a correctness and usability bug, but should not be described as silent movement.

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

New integration-test code uses `System.out.println`. The repository's
[AGENTS.md](../../AGENTS.md) explicitly requires SLF4J and prohibits `System.out.println`. Replace
console writes with a test logger or structured assertion descriptions. The module's
`showStandardStreams` setting controls test-output visibility; it does not override the source-style
rule.

---

## 5. Architectural Decision

### 5.1 Module and dependency direction

```text
application
    |
    +--> quicktx -------------------------------------------+
    |      QuickTxBuilder, Tx, TxPlan                       |
    |      generic extension SPI and registries             |
    |                                                       |
    +--> programmable-token --------------------------------+
           depends on quicktx
           protocol-neutral public API and semantic intents
           ProgrammableTokenExtension
           ProgrammableTokenService
           cip113 package: default protocol implementation

quicktx MUST NOT depend on programmable-token or cip113
```

For the initial implementation, use one Gradle module and artifact:

```text
:programmable-token
cardano-client-programmable-token
```

Keep protocol-neutral types under `com.bloxbean.cardano.client.programmabletoken` and CIP-113 types
under `com.bloxbean.cardano.client.programmabletoken.cip113`. Root domain packages must not import
the CIP-113 package; an architecture test should enforce that direction. If another protocol is
implemented, these packages can move mechanically into `programmable-token-api`,
`programmable-token-cip113`, and a new adapter artifact without changing Java package names. The
original `cardano-client-programmable-token` coordinate can remain as the stable API or convenience
artifact so ordinary consumers do not need an artifact migration.

### 5.2 Stable domain identity versus technical protocol

The public API models the Programmable Token domain. A protocol strategy describes how those domain
operations map to a particular technical specification and deployed validator surface.

| Concept | Initial value | Ownership |
|---|---|---|
| Extension id | `programmable-token` | Stable library-defined identity |
| TxPlan namespace | `pt` | Document-local configurable alias |
| Protocol id | `cip-113` | Technical specification/materializer identity |
| Contract version | `0.5.0-alpha.2` for the reviewed deployment | Verified deployment metadata |
| Extension schema version | `1` | Serialized Programmable Token intent schema |
| Deployment | Preview bootstrap transaction or another exact reference | Concrete on-chain validator instance |

Use the term `protocol`, not `dialect`, in the public API and TxPlan. CIP-113 defines contract
semantics and wire formats, not merely syntax. A future compatible revision can retain protocol id
`cip-113` with a new contract version. A different specification can register another protocol id.
This protocol concept must not be confused with CIP-113 substandards, which remain token-specific
issuance, transfer, and third-party logic within the CIP-113 framework.

### 5.3 Two capabilities, one extension

A QuickTx extension has two related responsibilities:

1. **Plan codec registration**: register externally defined semantic intent types.
2. **Build lifecycle participation**: aggregate and resolve those intents at the correct transaction
   lifecycle points.

A codec-only registry is insufficient for CIP-113 because canonical indexes must be finalized after
all intents have contributed to the transaction, before each script-cost evaluation, and again if a
balance pass changes index-sensitive content.

Implemented generic SPI shape:

```java
public interface QuickTxExtension {
    String id();

    Set<String> operations();

    Map<String, Class<? extends ExtensionIntent>> intentTypes();

    TxBuildExtension newBuildExtension(ExtensionMetadata metadata);
}

public interface ExtensionIntent extends TxIntent {
    String getExtensionId();

    String getOperation();
}

public interface TxBuildExtension {
    default void prepare(ExtensionBuildContext context) {}

    default void beforeScriptEvaluation(
            ExtensionBuildContext context, Transaction transaction) {}

    default BalanceFinalization afterBalance(
            ExtensionBuildContext context, Transaction transaction) { ... }

    default void verify(ExtensionBuildContext context, Transaction transaction) {}
}
```

`ProgrammableTokenExtension` implements this SPI and delegates protocol-specific materialization to
the selected `ProgrammableTokenProtocol`. `afterBalance` may update index-bearing redeemer data and
return `refinalized()`, which tells QuickTx to recompute script data, evaluate, and balance again.

`ExtensionIntent` is deliberately a semantic marker, not a generic payload envelope. Extension
modules register concrete operation classes with the codec. The codec uses an instance-scoped
Jackson mapper and canonical `(extension id, operation)` type ids, so it does not mutate the global
QuickTx mapper and does not require higher-level intent classes in `TxIntent@JsonSubTypes`.

### 5.4 Registration and default scope

Extension registration must be per codec and per builder, not global mutable state:

```java
ProgrammableTokenExtension programmableToken = ProgrammableTokenExtension.builder(backendService)
        .protocol(Cip113Protocol.create())
        .deployment(Cip113Deployments.PREVIEW)
        .build();

TxPlanCodec codec = TxPlanCodec.builder()
        .withExtension("pt", programmableToken)
        .build();

TxPlan plan = codec.fromYaml(yaml);

new QuickTxBuilder(backendService)
        .withExtension(programmableToken)
        .compose(plan)
        .complete();
```

The Java convenience builder may omit `protocol(...)` when the supplied deployment descriptor
declares its protocol, as `Cip113Deployments.PREVIEW` does. This provides a CIP-113 default without a
global mutable choice or a dependency from neutral API packages to concrete CIP-113 classes.
`TxPlanCodec` defaults the document namespace to `pt` when authoring. Canonical serialization always
writes the resolved protocol and extension metadata, so a persisted plan never depends on a future
library default.

Reasons to avoid a global registry include deterministic tests, safe coexistence of multiple
deployments or protocols, no classloader leakage, and predictable application-server behavior.
`ServiceLoader` may provide codec discovery, but runtime services and deployment selection remain
explicitly scoped.

### 5.5 Unknown extensions fail closed

The plan codec and builder must reject:

- an undeclared or duplicate namespace alias;
- an unregistered extension id or operation during YAML parsing;
- a registered codec with no corresponding execution extension;
- a requested operation unsupported by the selected protocol, deployment, or CCL implementation;
- a deployment reference that the runtime cannot resolve;
- incompatible extension schema, protocol, or verified contract versions.

Unknown fields may follow existing forward-compatibility rules, but unknown semantic operations must
never be ignored or routed through an ordinary native-token path.

### 5.6 QuickTx owns index stability

CIP-113 redeemers contain ledger indexes. QuickTx evaluates scripts before balancing, while balancing
may append or reorder inputs and outputs. Therefore a pre-evaluation hook plus a post-balance size
check is not a sufficient contract.

The target design is a bounded QuickTx stabilization loop:

1. extensions finalize index-bearing data against the provisional transaction;
2. QuickTx computes script data, evaluates scripts, and balances;
3. QuickTx and extensions compare the balanced transaction with the provisional content-and-order
   snapshot;
4. if an index-sensitive change occurred, extensions re-finalize embedded data and QuickTx repeats
   evaluation and balancing;
5. QuickTx completes only when the snapshot is stable, and fails with an actionable error if the
   configured iteration bound is reached.

The snapshot must compare ordered content, not only list sizes. It includes at least input
references, output identity/value/datum/reference script, withdrawal credentials, mint policies,
reference inputs, and the ledger targets represented inside extension redeemers.

PR #653's fee-ceiling pre-funding may remain as a transitional optimization for the experimental
CIP-113 implementation, but it is not the generic extension contract. The SPI must remain correct
when balancing legitimately adds an input or change output.

---

## 6. Programmable Token Semantic Intent Model

### 6.1 Proposed intent operations

`pt` is the default document-local namespace. It is not part of the canonical in-memory identity.

| YAML type | Canonical operation | Purpose | Role-specific data |
|---|---|---|---|
| `pt:transfer` | `transfer` | Owner-authorized programmable transfer | transfer redeemer |
| `pt:mint` | `mint` | Mint programmable assets | issuance redeemer, optional inline datum |
| `pt:burn` | `burn` | Spend and burn programmable assets | transfer redeemer + issuance redeemer |
| `pt:third_party_transfer` | `third_party_transfer` | Seize, claw back, or forced transfer | holder + third-party redeemer |
| `pt:register` | `register` | Register a programmable policy | named result + typed logic credentials/state |
| `pt:update_registry` | `update_registry` | Update protocol registry state | token reference + authorization |
| `pt:unfrack` | `unfrack` | Holder-driven UTxO restructuring, when supported | protocol authorization data |

Internally, the codec resolves `pt:transfer` to a structured identity such as
`IntentType("programmable-token", "transfer")`. Changing the plan alias from `pt` to `tokens` must
not change semantic equality or runtime dispatch.

Concrete intents live in `com.bloxbean.cardano.client.programmabletoken.intent` and follow core
QuickTx naming conventions: `ProgrammableTransferIntent`, `ProgrammableMintIntent`,
`ProgrammableBurnIntent`, `ProgrammableRegisterIntent`,
`ProgrammableThirdPartyTransferIntent`, `ProgrammableRegistryUpdateIntent`, and
`ProgrammableUnfrackIntent`. Their fields use domain types such as `Amount`, `PlutusData`,
`ProgrammableTokenPolicyRef`, typed credential/registry values, and binary-safe programmable-token
asset values. No semantic intent or nested domain value uses a string-keyed payload map. The
CIP-113 build extension consumes these concrete types and contains no generic payload-key extraction.

### 6.2 Java authoring facade

`ProgrammableTokenTx` is the protocol-neutral authoring facade. Its methods only add typed intents;
it must not hold backend services, registry caches, resolved UTxOs, script objects, protocol
materializers, or executable `Runnable` declarations.

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

Deserialized plans create an ordinary `Tx` containing Programmable Token intents. The Java authoring
subtype is not required during replay. The registered extension recognizes the canonical extension
and operation ids, then delegates to the selected protocol materializer.

This avoids adding Programmable Token or CIP-113 subtypes to `quicktx` serializers and ensures plans
are driven by data, not concrete transaction classes.

### 6.4 Register-and-mint references

`registeredPolicyId()` is mutable build output and cannot safely connect declarative operations.
Registration should publish a named domain reference that later intents resolve during the same
build:

```java
new ProgrammableTokenTx()
        .registerToken("usd", spec, registrationRedeemer)
        .mint(ProgrammableTokenPolicyRef.named("usd"), receiver, assets,
                issuanceRedeemer, null);
```

Use `ProgrammableTokenPolicyRef`, not QuickTx's existing `PolicyRef`. The existing type is backed by
`SignerRegistry`, represents a native minting policy, and resolves at compose time. The new reference
represents the semantic result of a Programmable Token registration and is resolved by the selected
protocol during the plan-wide build. CIP-113 currently derives that policy id from its issuance
template, but that derivation detail does not belong in the domain reference.

### 6.5 Protocol capabilities

The domain intent set may be broader than a particular protocol, deployment, or current CCL
implementation. Validate capabilities before chain access:

```java
enum ProgrammableTokenCapability {
    TRANSFER,
    MINT,
    BURN,
    THIRD_PARTY_TRANSFER,
    REGISTER,
    UPDATE_REGISTRY,
    UNFRACK,
    INLINE_DATUM,
    GLOBAL_STATE
}
```

Effective support is the intersection of protocol semantics, the resolved deployment, the CCL
adapter implementation, and token-specific authorization/state. Failures must identify which layer
does not support the requested operation. They must not silently degrade to an ordinary `Tx` intent.

---

## 7. TxPlan Design

### 7.1 Plan contents

A plan stores:

- semantic Programmable Token intent fields;
- variables and named references;
- extension id, document namespace, and extension schema version;
- explicit protocol id;
- an exact or resolvable deployment reference;
- optional verified `contract_version` metadata;
- structured Plutus data using the existing `PlutusDataYamlUtil` representation.

A plan does not store:

- `BackendService`, service implementations, or protocol materializer classes;
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
  pt:
    extension: programmable-token
    schema_version: "1"
    protocol: cip-113
    contract_version: "0.5.0-alpha.2"
    deployment:
      network: preview
      bootstrap_tx: a432339cbd7318222c8c51ed4fb52ee4c68f676037622aa7361dd45d897324a4

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
        - type: "pt:transfer"
          policy_id: ${policy_id}
          receiver: ${receiver}
          amounts:
            - unit: ${policy_id}4d79546f6b656e
              quantity: 100
          transfer_redeemer:
            constructor: 0
            fields: []
```

The key `pt` is the document-local namespace alias; the stable extension identity is the
`programmable-token` value. A user may choose another valid alias, but every qualified intent must
refer to a declared alias. This requires an intentional change to `TransactionDocument`,
`TxPlan.toYaml/fromYaml`, validation, and schema-version handling.

`contract_version` is not a CIP version, CCL version, or user-selected dialect/profile. It identifies
the validator and datum/redeemer compatibility surface associated with the exact deployment. The
deployment resolver verifies it by matching the bootstrap reference and resolved validator hashes to
a pinned descriptor; the chain does not itself publish this semantic version string. It may be
omitted from hand-authored input when an exact deployment reference determines it, but canonical
serialization should emit the resolved value when known.

### 7.3 Codec extensibility

The current static `TxIntent@JsonSubTypes` list cannot be the only registration mechanism because
`quicktx` cannot import higher-level module classes.

The codec registry uses canonical extension and operation ids:

```java
Map<String, Class<? extends ExtensionIntent>> intentTypes() {
    return Map.of(
            "transfer", ProgrammableTransferIntent.class,
            "mint", ProgrammableMintIntent.class,
            "burn", ProgrammableBurnIntent.class);
}
```

The document codec resolves the alias before looking up the canonical pair and registers those
classes only on its private mapper copy. Only explicitly registered extension/operation pairs may
instantiate classes. Decoding produces the typed intent directly; there is no intermediate
`Map<String, Object>` extension payload.

### 7.4 Namespace and default rules

- `pt` is the default namespace used by Java-to-YAML authoring.
- Namespace aliases are codec/document concerns; `QuickTxBuilder` dispatches canonical ids.
- Aliases must match a restricted identifier grammar and must not collide with each other or reserved
  core namespaces.
- Core QuickTx intents remain unqualified for backward compatibility.
- A hand-authored plan must declare every namespace it uses.
- The Java API may infer the pinned CIP-113 adapter from a protocol-bearing deployment descriptor,
  but canonical YAML always writes `protocol: cip-113`.
- A library upgrade must never reinterpret an already serialized plan using a different default.

### 7.5 Variable resolution

Every Programmable Token intent must implement `resolveVariables(...)` for:

- addresses;
- policy ids and named policy references;
- asset units and quantities;
- registry credentials/global-state policy ids where the selected protocol uses them;
- structured redeemers and inline datums.

Extension metadata and deployment variables must resolve before protocol capability validation and
chain access.

### 7.6 Round-trip contract

For every serializable Programmable Token operation:

```text
Java intent -> YAML -> TxPlan -> YAML -> TxPlan
```

must preserve semantic equality. Namespace aliases are presentation metadata; canonical extension
and operation identities determine intent equality. Runtime-resolved data may differ between builds
because chain state can change; the declared operation, protocol, and deployment constraint must not.

---

## 8. Build Lifecycle

### 8.1 Required phases

```text
1. Parse / author
   - create semantic intents only

2. Resolve variables
   - no chain access

3. Validate declarations
   - extension id, protocol, deployment, and effective capabilities
   - conflicts, required fields, role-specific redeemers

4. Extension preparation
   - resolve deployment snapshot
   - load a fresh registry view
   - aggregate all Programmable Token intents for the selected CIP-113 protocol instance
   - select PLB and funding inputs without overlap
   - resolve co-resident policy proofs
   - add outputs, withdrawals, scripts, and reference inputs

5. Apply materialized core intentions
   - construct the complete transaction shape
   - extension semantic intents intentionally have a no-op `apply()` because `prepare()` already
     interpreted them as an aggregate

6. Before script evaluation
   - compute canonical indexes
   - finalize CIP-113 redeemers
   - declare required signers
   - snapshot ordered index-sensitive transaction content

7. Balance
   - allow QuickTx to add required funding inputs and change outputs

8. Stabilize after balance
   - compare actual ordered content, not only collection sizes
   - if index-sensitive content changed, re-finalize embedded redeemer data
   - recompute script data/hash, re-evaluate, and rebalance
   - repeat to a small explicit bound; fail if the transaction does not converge

9. Final verification
   - verify index-sensitive content, redeemer bindings, balance, and fee are stable
   - prohibit untracked mutation after the stable evaluation/balance pass
```

### 8.2 Mapping to existing QuickTx seams

The first implementation should adapt existing lifecycle seams instead of creating a parallel build
engine:

| Required capability | Existing seam | Decision |
|---|---|---|
| Plan-wide preparation before per-`Tx` completion | `AbstractTx.complete()` is package-private and per transaction | Keep `complete()` internal. Add a builder-level extension preparation phase before the builder iterates through `txList` and calls `complete()`. It receives all semantic intents and may materialize ordinary intents. |
| Pre-evaluation finalization | `AbstractTx.preTxEvaluation()` is protected and per transaction; `TxContext.preBalanceTx(TxBuilder)` is public but a single slot | Keep the protected per-`Tx` hook for compatibility. Generalize the builder context to an ordered list of pre-evaluation participants and invoke extension finalizers after reference-script resolution. The existing user transformer remains supported as one ordered participant. |
| Negative-output handling | `QuickTxBuilder` skips `preTxEvaluation` while an output coin is negative | Preserve the provisional skip, but the stabilization pass must invoke extension finalization before every actual evaluation once provisional output values are resolved. |
| Post-balance processing | `TxContext.postBalanceTx(TxBuilder)` and `AbstractTx.postBalanceTx` | Keep legacy hooks, add ordered extension finalizers, and run them inside the stabilization boundary. A reported index-sensitive change triggers re-evaluation and rebalancing rather than continuing to completion. |
| Stabilization ownership | `verifyAndAdjustRedeemerIndexes` updates redeemer pointer indexes only | QuickTx owns the bounded loop. The existing adjustment remains a core step, while extensions update indexes embedded inside redeemer data. |

This promotes builder-level, ordered participation—not the package-private or protected `AbstractTx`
methods themselves—as the public experimental SPI. It also replaces the single-transformer
assumption without exposing CIP-113 classes from `quicktx`.

### 8.3 Aggregation scope

The extension must aggregate Programmable Token intents across every transaction fragment passed to
one `compose(...)` call and group them by configured protocol/deployment instance. Resolving each
`ProgrammableTokenTx` independently can select the same UTxOs, duplicate withdrawals, or compute
conflicting change. The initial CIP-113 materializer consumes its complete group in one pass.

At minimum, aggregation keys include:

- owner/holder smart wallet;
- policy id;
- action role;
- logic credential/reward account;
- global-state policy;
- funding address and already reserved UTxOs.

### 8.3.1 Extension-intent `apply()` contract

Core `TxIntent` implementations are normally directly replayable through `apply()` and/or
`outputBuilder()`. Extension semantic intents are different: they are declarative inputs to an
extension planner. They are recorded during authoring, perform no chain I/O, and are collected by
the registered build-local extension during `prepare()`. Because a protocol may need cross-intent
resolution, transaction-wide aggregation, and shared input selection, extension authors should not
place materialization in `ExtensionIntent.apply()`. The marker provides an intentional no-op
implementation only because extension intents participate in the existing `TxIntent` collection.

QuickTx validates extension ownership, operation support, and the registered concrete intent class
before calling any build extension. Build extensions obtain an extension-scoped intent view from
`ExtensionBuildContext`, so an intent cannot be silently consumed by the wrong extension.

### 8.4 Input reservation

The materializer must maintain one build-local set of reserved input references. Selection for later
policies or fragments excludes previously selected PLB and ADA inputs. This addresses the PR's known
multi-policy contention and makes composition deterministic.

### 8.5 Registry snapshot

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
ProgrammableTokenService programmableTokens = ProgrammableTokenService.create(
        backendService,
        Cip113Protocol.create(),
        Cip113Deployments.PREVIEW);

QuickTxBuilder builder = new QuickTxBuilder(backendService)
        .withExtension(programmableTokens.extension());
```

If a generic extension method is not introduced immediately, this is acceptable as an interim API:

```java
new ProgrammableQuickTxBuilder(backendService, Cip113Deployments.PREVIEW)
```

It is still preferable to wrapping the backend in a new subtype.

PR #653 also exposes a plain-builder route through
`new QuickTxBuilder(backendService).compose(service.tx())`, so the specialized builder is not the
only entry point. The architectural concern still applies: `service.tx()` performs deployment and
chain wiring while constructing the transaction object, earlier than the proposed plan-wide build
lifecycle. The extension model preserves the plain `QuickTxBuilder` path while deferring chain work
until all semantic intents are known.

### 9.2 Public read service

The primary service is domain-named and contains only operations that can be represented honestly
across Programmable Token protocols:

```java
interface ProgrammableTokenService {
    ProgrammableTokenProtocolDescriptor protocol();

    ProgrammableTokenCapabilities capabilities();

    Result<List<Amount>> getBalance(Address owner);

    Result<List<Amount>> getProgrammableBalance(Address owner);

    Result<Boolean> isProgrammable(String policyId);

    ProgrammableTokenExtension extension();
}
```

Address derivation may remain here only if the domain contract can define it across supported
protocols. Otherwise it is exposed as a capability or through the protocol service.

### 9.3 CIP-113 protocol service

CIP-113 registry, issuance-template, coordination-UTxO, and deployment-resolution concepts must not
leak into the generic service merely because CIP-113 is initially the default. Expose an advanced
protocol-specific facade in the same artifact:

```java
interface Cip113ProtocolService {
    Cip113Deployment deployment();

    Result<Cip113Deployment> resolveDeployment();

    Address smartWalletAddress(Address owner);

    Result<RegistryNode> getRegistryNode(String policyId);

    Result<List<RegistryNode>> getRegistry();

    Result<String> derivePolicyId(Credential mintingLogic);
}
```

`ProgrammableTokenService` may expose this through a typed protocol-access mechanism or a CIP-113
factory may return both facades. Do not add generic methods that assume every future protocol uses a
registry, smart-wallet address, issuance template, or coordination UTxO.

Low-level script suppliers and resolved protocol UTxOs belong in a package-private runtime contract
or an explicitly advanced customization interface.

### 9.4 Indexer extension point

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
perform exact lookups without replacing the domain service or protocol materializer.

---

## 10. Protocol, Deployment, and Schema Versioning

CIP-113 remains under active development and its reference implementation can change independently
of CCL. A single unversioned `Cip113Deployment` plus hand-written codecs risks applying the wrong
rules to a deployment with a different datum/redeemer surface.

Keep these version axes independent:

| Axis | Example | Purpose |
|---|---|---|
| Extension schema | `1` | Programmable Token TxPlan field compatibility |
| Protocol | `cip-113` | Selects the technical materializer and codec family |
| Contract version | `0.5.0-alpha.2` | Identifies the deployed validator/datum/redeemer surface |
| Deployment | Preview bootstrap transaction | Identifies exact on-chain scripts and protocol state |

Do not call the contract version a `profile`; the term is ambiguous. `contract_version` is verified
against the pinned deployment descriptor and resolved hashes, and is never a free-form switch that
selects behavior by itself.

Each CIP-113 deployment descriptor should carry:

- deployment identifier;
- network;
- bootstrap transaction hash;
- protocol id and contract version;
- codec version;
- known validator/script hashes after resolution;
- feature flags or capabilities where contract versions differ.

Example:

```java
enum Cip113Capability {
    STANDALONE_THIRD_PARTY_DELEGATE,
    REGISTRY_NODE_UPDATE,
    MAX_INLINE_DATUM_BOUND
}
```

Generic features such as `INLINE_DATUM`, `GLOBAL_STATE`, and operations such as `BURN` belong in
`ProgrammableTokenCapability`; CIP-113-only validator distinctions remain in `Cip113Capability`.
Transaction intents validate the effective domain capabilities against the selected protocol and
resolved deployment before any inputs are selected.

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

- No direct QuickTx-to-Programmable Token or CIP-113 dependency.
- Reusable extension model for future protocol/CIP modules.
- Stable domain API even if the default technical specification changes.
- Short, collision-safe, configurable TxPlan intent namespaces.
- Real TxPlan YAML compatibility.
- One resolution pass removes fluent-order bugs.
- Explicit role semantics improve safety and error messages.
- Ordinary `Tx` replay works without reconstructing a specialized subtype.
- Multi-fragment composition can reserve inputs and deduplicate withdrawals globally.
- Backend provider implementations remain unchanged.

### Costs

- QuickTx needs a small, carefully designed extension SPI and codec registry.
- The TxPlan codec can no longer rely exclusively on a static annotation subtype list.
- A bounded finalization/stabilization lifecycle becomes public extension surface and must be
  carefully constrained.
- PR #653 transaction-building code requires decomposition into semantic intent and runtime
  materializer layers.
- The initial single module requires package-dependency discipline so a future artifact split remains
  mechanical.
- Extension schema, protocol, contract, and deployment versioning require explicit compatibility
  rules.

### Risks

- An overly broad lifecycle SPI could expose QuickTx internals and become difficult to evolve.
- Extension ordering can become ambiguous if multiple extensions mutate the same transaction.
- A mutable Java default could reinterpret plans unless serialization always records the protocol.
- User-configurable aliases can collide or become confused with canonical extension identity.
- YAML polymorphism must remain safe; only explicitly registered types may be instantiated.
- Multiple extensions may contend for inputs or lifecycle phases.

### Mitigations

- Keep lifecycle phases minimal and transaction-oriented.
- Define deterministic extension order and reject duplicate extension ids.
- Use explicit registration, not class-name-based polymorphic deserialization.
- Resolve aliases to structured canonical identities and validate duplicate/reserved namespaces.
- Derive any Java default from a pinned protocol-bearing deployment descriptor and always serialize
  the resolved protocol and deployment constraint.
- Enforce that protocol-neutral packages do not import the CIP-113 package.
- Provide a build-local input reservation service shared by all extensions.
- Add compatibility and composition tests before considering the SPI stable.

---

## 13. Implementation Plan

### Phase 0: Correctness stabilization in the current `cip113` prototype

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
- Add the top-level `TransactionDocument.extensions` metadata section and version validation.
- Prototype the bounded post-balance stabilization loop against the existing QuickTx lifecycle seams.

**Exit gate**: approved SPI design plus a trivial test extension proving external intent
serialization and lifecycle invocation without any `cip113` dependency in `quicktx`.

### Phase 2: Programmable Token module and semantic intents

Goal: replace `Runnable` declarations with immutable or value-like semantic intentions.

- Introduce the top-level `programmable-token` Gradle module and artifact.
- Update `settings.gradle` and the existing `cip` aggregate dependency without adding a reverse
  dependency from `quicktx`.
- Move public domain types under `com.bloxbean.cardano.client.programmabletoken`.
- Move protocol-specific types under `com.bloxbean.cardano.client.programmabletoken.cip113`.
- Define `ProgrammableTokenExtension`, `ProgrammableTokenProtocol`, protocol descriptors, and
  effective capability validation.
- Implement transfer, mint, burn, third-party, register, and update intents.
- Implement structured redeemer/datum serialization.
- Implement variable resolution and semantic validation.
- Convert `ProgrammableTokenTx` into an authoring facade that only appends intents.
- Remove eager registry lookup and materialization from fluent verbs.
- Introduce explicit role-specific authorization objects.
- Introduce `ProgrammableTokenPolicyRef` for named register-and-mint dependencies.
- Enforce the neutral-package-to-CIP113 dependency rule with an architecture test.

**Exit gate**: Java-authored operations are order-independent and every supported intent round-trips
through YAML using canonical extension/operation identity independent of the chosen namespace alias.

### Phase 3: CIP-113 runtime materializer

Goal: execute all semantic intents in one coherent build pass.

- Resolve and version-check deployment.
- Verify the deployment-resolved `contract_version` and effective capabilities.
- Load one registry snapshot per build.
- Aggregate operations across composed transaction fragments.
- Reserve PLB and ADA inputs globally.
- Resolve all registered and covering-node proofs.
- Add scripts, withdrawals, outputs, and reference inputs.
- Finalize canonical indexes before script-cost evaluation.
- Re-finalize embedded index data and repeat evaluation/balancing when ordered content changes.
- Verify full index-sensitive content and ordering after balancing, not only collection sizes.

**Exit gate**: equivalent in-memory and YAML plans build equivalent transaction semantics against the
same chain snapshot.

### Phase 4: Public API cleanup

Goal: present one simple, stable integration path.

- Replace `ProgrammableBackendService` with `ProgrammableTokenService` composition.
- Keep registry, deployment-resolution, and issuance-template operations on an advanced
  `Cip113ProtocolService`, not the protocol-neutral service.
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
- Produce release notes identifying the extension schema, default protocol, supported CIP-113
  contract/deployment versions, and known gaps.

### Implementation outcome

All phases were implemented on `cip113_review_planning`. The existing `RegistryLookup` remains the
narrow indexer extension point instead of adding a second overlapping `Cip113ChainDataProvider`
type; scanning and fake-backed implementations exercise the same contract. The initial API remains
experimental, as documented in `programmable-token/RELEASE_NOTES.md`. Qualification includes the
full multi-module build and the 16-step Yaci DevKit suite, including two-transfer aggregation,
register-and-mint, burn, multi-input third-party transfer, mixed withdrawal ordering, published
reference scripts, and registry update.

---

## 14. Review Plan

### 14.1 Review sequence

1. **Architecture review**
   - Approve the single top-level `programmable-token` module, package dependency direction, and
     generic QuickTx extension SPI.
   - Approve the Programmable Token domain/CIP-113 protocol separation.
   - Confirm the recommendation that extension support is initially experimental.

2. **QuickTx lifecycle review**
   - Confirm the mapping from existing QuickTx seams to the preparation, pre-evaluation, and
     post-balance hooks.
   - Approve QuickTx ownership of the bounded stabilization loop.
   - Verify hooks cannot invalidate fee calculation or transaction balance unexpectedly.
   - Define multiple-extension ordering and failure behavior.

3. **TxPlan schema review**
   - Approve the top-level extension map, document-local namespace aliases, and canonical
     extension/operation identity.
   - Approve codec registration and safe polymorphic deserialization.
   - Approve independent schema, protocol, contract-version, and deployment-reference rules.

4. **Programmable Token domain API review**
   - Approve explicit transfer/mint/burn/third-party verbs.
   - Approve role-specific redeemer types.
   - Approve `ProgrammableTokenPolicyRef` for register-and-mint.
   - Confirm that CIP-113 registry/deployment details remain on the protocol-specific facade.

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

### 14.3 Recommended defaults for approval

These are decisions proposed by this ADR, not unresolved alternatives. Reviewers should approve a
default or record a replacement before implementation begins.

| Topic | Recommended default |
|---|---|
| Introduce the generic SPI now? | Yes. Implement the minimum codec and lifecycle surface needed by CIP-113, but mark it experimental until qualification and at least one additional extension use case validate its shape. CIP-113 remains serializable through that SPI. |
| Module layout | Use one top-level `programmable-token` Gradle module and `cardano-client-programmable-token` artifact for now. Separate neutral and CIP-113 Java packages so a future artifact split is mechanical. |
| Public identity | Use Programmable Token names for the domain facade and extension. Keep CIP-113 names for protocol-specific deployments, registry models, codecs, redeemers, and materializers. |
| Registration scope | Register runtime participants per `QuickTxBuilder` and codecs per `TxPlanCodec`. Both consume the same immutable extension descriptor. Do not use `TxContext` or a process-global environment as the initial ownership scope. |
| Lifecycle shape | Provide an ordered finalization/stabilization pipeline, not only `beforeScriptEvaluation`. QuickTx owns re-evaluation and rebalancing. |
| Multiple-extension ordering | Sort first by fixed lifecycle phase, then explicit registration order, then extension id as a deterministic diagnostic tie-breaker. Reject duplicate extension ids. |
| Input reservation | Provide one generic build-local reservation service shared by core QuickTx and all extensions. |
| Plan metadata | Add a top-level, versioned `extensions` section to `TransactionDocument`. The map key is a document-local alias; its value records stable extension id, protocol, schema, and deployment metadata. |
| Namespace | Default to `pt`, allow a user-provided document alias, and serialize types as quoted qualified names such as `"pt:transfer"`. Resolve aliases before runtime dispatch. |
| Protocol default | A Java caller may omit the protocol when the configured deployment descriptor declares `cip-113`. Persisted plans always record `protocol: cip-113`; a library upgrade cannot reinterpret them. |
| Register-and-mint reference | Introduce `ProgrammableTokenPolicyRef`; do not reuse the native-policy `PolicyRef`. |
| First supported contract surface | Pin the exact Preview deployment, validator hashes, reference implementation commit, and blueprint version used for qualification. The candidate reviewed here has contract version `0.5.0-alpha.2`; it becomes supported only after those artifacts are captured in a deployment descriptor and pass the test matrix. |
| Version terminology | Keep extension `schema_version`, protocol id, `contract_version`, and deployment reference separate. Do not use the ambiguous term `profile`. |
| Compatibility status | Mark the generic SPI, Programmable Token API, and CIP-113 protocol API experimental. Version serialized extension schemas independently; promise no stable Java binary compatibility until CIP-113 and the SPI complete qualification. |
| Implicit payment routing | Remove it from the primary API. Use explicit Programmable Token verbs; any temporary compatibility route must be deprecated, exhaustive across overloads, and fail closed. |

### 14.4 Review artifacts

Before implementation approval, prepare:

- a small extension SPI Java prototype;
- one external sample intent that round-trips through TxPlan;
- a package/module dependency diagram and architecture rule;
- lifecycle sequence diagram showing ordinary intents and extension hooks;
- proposed Programmable Token Java API examples for every supported operation;
- proposed YAML examples for transfer, register-and-mint, burn, and third-party transfer;
- schema/protocol/contract/deployment compatibility table;
- test plan with named test classes and ownership.

### 14.5 Acceptance gates

The architecture is approved when:

- `quicktx` has no compile/runtime dependency on `programmable-token` or CIP-113;
- protocol-neutral packages have no dependency on the CIP-113 package;
- external intent types can be registered without editing `TxIntent@JsonSubTypes`;
- extension registration is immutable or safely scoped, not global mutable state;
- a YAML plan reconstructs equivalent semantic intents;
- namespace aliases resolve to canonical extension/operation ids and collisions fail closed;
- canonical YAML records the resolved protocol and deployment constraint even when Java used a
  default;
- the runtime extension sees all composed Programmable Token intents before selecting inputs;
- index-sensitive data is finalized before each script evaluation;
- balancing changes trigger bounded re-finalization, re-evaluation, and rebalancing until stable;
- the final guard compares ordered transaction content and detects same-size reorderings;
- unknown/missing extensions fail with actionable messages;
- one builder can execute both ordinary and Programmable Token intents in the same plan;
- multiple deployments or protocol instances can coexist in one JVM without registry or namespace
  collision.

---

## 15. Verification and Test Plan

### 15.1 Unit tests

| Area | Required cases |
|---|---|
| Declaration ordering | Permute `from`, multiple transfers, and authorization declarations; assert equivalent semantics |
| Multiple payments | Add a second same-policy payment before and after authorization |
| Asset names | Empty, UTF-8, valid UTF-8 beginning with `0x`, non-UTF-8, zero byte, and 32-byte names |
| Inline datum | Mint with allowed inline datum; reject datum hash; preserve datum on third-party continuation |
| Burn authorization | Different transfer and issuance scripts/redeemers; shared script with compatible redeemer |
| Incidental policies | Registered and unregistered co-resident policies in selected PLB inputs |
| Registry freshness | Register/update followed by read/mint without manual invalidation |
| Manual wiring | Remove API or verify complete dependency validation and execution |
| Error semantics | Distinguish absent global state from backend failure |
| ADA buffer | Null, non-lovelace, zero, negative, and valid positive lovelace |
| Stabilization | Balance appends an input/change output; extension re-finalizes embedded indexes; evaluation and balance converge |
| Stability guard | Reorder index-sensitive entries without changing list sizes; assert the content/order snapshot rejects or re-finalizes |
| Non-convergence | Extension alternates index-sensitive content; assert the bounded loop fails with an actionable error |
| Protocol default | Java omission with a CIP-113 deployment descriptor resolves to pinned CIP-113; canonical YAML explicitly emits `protocol: cip-113` |
| Contract version | Exact deployment resolves and verifies `contract_version`; mismatch fails before input selection |
| Package boundary | Protocol-neutral packages do not import the CIP-113 package |

### 15.2 TxPlan tests

- Java intent to YAML to plan semantic equality for every Programmable Token intent.
- YAML to plan to YAML canonical round trip.
- Default `pt` and a custom alias both resolve to the same canonical semantic intents.
- Duplicate, undeclared, invalid, and reserved namespace aliases fail loudly.
- `pt:transfer` without a declared `pt` extension fails loudly.
- Canonical serialization emits explicit extension id, protocol, schema version, deployment, and
  resolved contract version when known.
- Variables in addresses, units, quantities, credentials, redeemers, and deployment references.
- Unknown extension id and unknown intent type fail loudly.
- Registered codec without runtime extension fails before chain access.
- Runtime extension with incompatible schema, protocol, contract version, or deployment fails during
  validation.
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
- Stabilization converges for bounded, deterministic balance mutations and rejects oscillation.

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
./gradlew :programmable-token:test
./gradlew :programmable-token:integrationTest --tests '*Cip113EndToEndIT*'
./gradlew clean build
```

---

## 16. Suggested PR Breakdown

Avoid combining the generic QuickTx SPI, all Programmable Token intent migration, and CIP-113
protocol correctness fixes in one unreviewable change.

1. **PR A — CIP-113 correctness fixes and regression tests**
2. **PR B — Generic QuickTx codec registry, lifecycle SPI, and stabilization prototype**
3. **PR C — Programmable Token module, domain API, semantic intents, namespace, and protocol model**
4. **PR D — CIP-113 protocol adapter, runtime materializer, and composition support**
5. **PR E — TxPlan YAML schema, named policy references, and round-trip tests**
6. **PR F — Public API cleanup, documentation, and experimental/beta qualification**

Each PR should keep `./gradlew clean build` green and include focused tests for its new contract.

---

## 17. Decision Checklist

- [x] Approve QuickTx extension SPI direction.
- [x] Approve no `quicktx -> programmable-token` or `quicktx -> cip113` dependency.
- [x] Approve one top-level `programmable-token` module with neutral and CIP-113 package boundaries.
- [x] Approve Programmable Token public names and CIP-113 protocol-specific names.
- [x] Approve semantic Programmable Token intents as the TxPlan representation.
- [x] Approve extension-owned concrete intent classes rather than a generic map payload envelope.
- [x] Document extension semantic intents as aggregate-planned declarations with intentional
  no-op `apply()` behavior.
- [x] Approve build-time aggregation and single materialization pass.
- [x] Approve explicit role-specific operation APIs and redeemers.
- [x] Approve removal of implicit `payToAddress` routing from the primary API.
- [x] Approve per-builder/per-codec registration and deterministic lifecycle ordering.
- [x] Approve top-level `TransactionDocument.extensions`, default `pt` namespace, and qualified
  intent names.
- [x] Approve explicit protocol metadata and deployment-verified `contract_version`; reject
  `profile` terminology.
- [x] Approve `ProgrammableTokenPolicyRef` for register-and-mint.
- [x] Approve QuickTx-owned bounded stabilization with content/order snapshots.
- [x] Pin the first supported CIP-113 deployment/reference version.
- [x] Define experimental/beta compatibility guarantees.
- [x] Complete P0 regression tests.
- [x] Complete TxPlan round-trip and Java/YAML equivalence tests.
- [x] Complete devnet qualification.

---

## 18. References

- [CCL PR #653](https://github.com/bloxbean/cardano-client-lib/pull/653)
- [CIP-113 proposal PR #444](https://github.com/cardano-foundation/CIPs/pull/444)
- [CIP-113 reference implementation](https://github.com/cardano-foundation/cip113-programmable-tokens)
- [CIP-143: Interoperable Programmable Tokens](https://cips.cardano.org/cip/CIP-0143)
- [Current output-shape rules](https://github.com/cardano-foundation/cip113-programmable-tokens/blob/dba98d9e54d7c46c28980e7d4b2aae532f907594/lib/assets.ak#L208-L283)
- [Current third-party paired-output rules](https://github.com/cardano-foundation/cip113-programmable-tokens/blob/dba98d9e54d7c46c28980e7d4b2aae532f907594/validators/programmable_logic/third_party.ak#L188-L195)
- [Repository contribution guidelines](../../AGENTS.md)
