# CIP-113 Programmable Tokens — QuickTx support

**Status: early spike. Not production-ready, and the standard it implements is not final.**

CIP-113 is an unmerged proposal ([CIPs#444](https://github.com/cardano-foundation/CIPs/pull/444));
its reference implementation is at blueprint version `0.5.0-alpha.2`. The CIP text and the
deployed contracts have already diverged in several places — this module targets the deployed
contracts. Expect breaking changes.

## What works today

| Operation | Status |
|---|---|
| Transfer (owner-authorised) | Implemented, exercised end to end on chain |
| Mint | Implemented, exercised end to end on chain |
| Token registration | Implemented, exercised end to end on chain |
| Registry / balance queries | Implemented |
| **Burn** | **Not implemented** — throws. Needs to spend the holder's smart-wallet UTxOs, not just mint a negative quantity |
| **Third-party (seizure, clawback)** | **Not implemented** — throws |
| **Registry-node update** | **Not implemented** — throws |
| **Unfracking** | **Not implemented** — no entry point |
| Key-credential logic scripts | **Not implemented** — throws; script credentials only |
| Multi-policy smart-wallet UTxOs | Partial — a UTxO holding several policies is split, but two policies selected in one transaction can contend for the same input |

## Layout

Everything lives in `cip:cip113`, split by package rather than by module.

| Package | What it holds |
|---|---|
| `…cip.cip113` | Domain types that touch no backend: deployment model, smart-wallet derivation, policy-id derivation, the datum/redeemer codecs, and the ledger orderings |
| `…cip.cip113.tx` | `ProgrammableTokenTx` (a specialized `Tx`), `ProgrammableTokenService` (the read side), and the registry lookup |

## Using it

```java
// A ProgrammableBackendService *is* a BackendService, so it goes anywhere one does.
ProgrammableBackendService backend =
        ProgrammableBackendService.wrap(backendService, Cip113Deployments.PREVIEW);

Result<String> result = new QuickTxBuilder(backend)
        .compose(new ProgrammableTokenTx(backend)
                 .from(senderAddress)
                 .payToAddress(receiverAddress, Amount.asset(policyId, "MyToken", 100))
                 .withRedeemer(policyId, myTransferRedeemer))
        .feePayer(senderAddress)
        .withSigner(SignerProviders.signerFrom(senderAccount))
        .completeAndWait();
```

That is the whole setup. `new ProgrammableTokenTx(backend)` wires itself from the backend —
registry lookup, UTxO supplier, script resolver, protocol parameters, global-state resolution and
the protocol's own reference UTxOs — and the deployment resolves itself on first use, so there is
no initialisation call and no ordering to get right. The read side
(`backend.getProgrammableTokenService()`: balances, registry, `isProgrammable`, policy-id
derivation) sits on the backend like every other CCL service, and building a transaction never
requires touching it. Nothing
about the *token* is assumed: which transfer, minting and third-party logic scripts run is read
from that token's registry node and resolved automatically, so no caller ever names a substandard
script. The one case that needs help is a substandard that has never been used on chain — no
backend can serve a script the chain has not revealed — and then it is registered once, on the
service rather than per transaction:

```java
api.scripts().register(myNeverYetUsedSubstandard);
```

It reuses `Tx`'s verbs — `from`, `payToAddress` — with their existing signatures. The one
addition is `withRedeemer(...)`, because a programmable token's rules need one and a plain payment
does not. Routing is inferred from the **token**: a registered policy takes the programmable
path, ADA and unregistered tokens are paid normally.

Two behaviours differ from a plain `Tx`:

- Paying a programmable amount to an address writes the output at that party's **smart wallet**,
  not the address passed. An address already carrying the base script credential is used as-is.
- Outputs hold one programmable policy each.

## What works, and what doesn't

| Operation | State |
|---|---|
| Deployment resolution (walks the bootstrap transaction) | implemented |
| Smart-wallet derivation | implemented, unit-tested against the CIP's published example |
| Policy-id derivation | implemented — pure hashing, no UPLC applier needed |
| Registry scan, covering-node search | implemented |
| Balance and registry queries | implemented |
| **Transfer** | implemented, **not yet verified on chain** |
| **Mint / burn** | implemented (burn = negative quantity), **not yet verified on chain** |
| **Register a new token** | implemented, **not yet verified on chain** |
| Third-party act (seize, clawback) | `TODO` stub |
| Update a registry node | `TODO` stub |
| Unfracking | out of scope |

Each stub throws with a message describing exactly what the implementation has to do.

## The Preview deployment

`Cip113Deployments.PREVIEW` is resolved from bootstrap transaction
`a432339cbd7318222c8c51ed4fb52ee4c68f676037622aa7361dd45d897324a4`, read from chain on
2026-08-26:

| | |
|---|---|
| coordination UTxO | `a432339c…#0` at `addr_test1wqgt2xufwszw8084m9njfn9pwq6yn4dysel0cc5ydlrc23s2qfa2v` |
| params policy | `ea423f5e7d078fb6c7d2505bee02b567eaece043e257fdd601cdaf59` |
| base script (PLB) | `698c48a630206282690774aebcfa9410895c09f85bc103b19f9888dc` |
| transfer delegate | `971606541dfdc9e411ba722880d783165f044cc541c17225f35d1e59` |
| third-party delegate | `8d2d24f8203f6049c3f36576c1628856b8012b3c10db36f7182233f4` |
| unfracking delegate | `d4be7708df51b14718d19888db5ad8e417eda138cf83f030bf7ab857` |
| upgrade authority | `4861aca31fe0581ff2a16d180f26ac2b4feeb71ca5fd2a86b7927bb5` |
| registry | `addr_test1wqr9pu02kzxggerr4ncrwrwu2zlqtkhzfsefepst2aazz5srqp5fw`, node policy `9aeda27e8b7e8c0077af9d6d8077b61d4e4a8b25368280ad26dc00c8` |
| issuance template | `a432339c…#2`, policy `36480294379b6196a91bd7ac82b6f36cedf38a8b098fe5a2e7f52c7a` |
| max inline datum | 2048 bytes |

Two things the live data confirmed:

- **The 7-field `RegistryNode` layout is real.** Seven nodes are registered, all constructor 0
  with the documented field order — including the origin node (empty `key`, all-`empty_vkey`
  credentials) and a tail node whose `next` is a **30-byte** `ff…` sentinel, wider than a
  28-byte policy id. Ordering code must not assume 28 bytes; `PolicyOrdering` does not.
- **The coordination UTxO is unspent at the bootstrap output**, so this deployment has not been
  upgraded in place. Resolving from chain reproduces the baked-in constants exactly, which a
  unit test now asserts.

## Running the integration test

```bash
yaci-devkit up

./gradlew :cip:cip113:integrationTest --tests '*Cip113EndToEndIT*'
```

That is the whole setup. No API key, no funded account, no environment variables, no Gradle
properties. The suite resets the devnet, funds the standard DevKit account
(`test test … sauce`), and deploys the CIP-113 protocol itself. Without a devnet listening on
`http://localhost:8080/api/v1/` it skips with a message saying so.

### The chain is reset on every run

`DevNet.reset()` runs in `@BeforeAll` and step 0 redeploys the protocol, so every run starts from
an empty chain. That is deliberate: it removes the whole class of failures where a test passes or
fails because of what a *previous* run left behind — accumulated supply, a half-registered
credential, a registry node from an abandoned attempt.

The consequence is that the suite runs **as a unit**. A single step run with a `--tests` method
filter resets the chain and then finds nothing deployed.

### Scripts are evaluated locally

The suite uses `AikenTransactionEvaluator` rather than the backend's evaluate endpoint. A remote
evaluator that cannot build an evaluation context returns an empty `ScriptFailures` map — no script
name, no reason — which is worse than useless when debugging. Aiken's evaluator runs the validators
in-process and names the one that failed.

### What step 0 deploys

All twelve validators, in one transaction, reimplemented from the reference deployment in
`cip113-programmable-tokens-platform`. Every validator is parameterized and several are
parameterized by another's hash, so the order is a dependency chain: the one-shot
`protocol_params_mint` seeds off a UTxO reference, every delegate hangs off the resulting params
policy, and `registry_spend` must precede `registry_mint` because the latter takes its credential
as a parameter.

It also writes the **origin registry node** (empty key, 30-byte sentinel `next`, all credentials
the empty-vkey sentinel), the **coordination datum**, the **issuance template**, and registers the
reward accounts of the three delegates — which are re-parameterized on every deployment, so their
withdraw-zero would otherwise be invalid.

### The always-true script

Compiled from the Aiken project `cip113/alwaystrue` (Aiken `v1.1.23+8949565`, Plutus V3 — the
same compiler the CIP-113 reference implementation uses). Its blueprint is checked in at
`src/it/resources/blueprint/alwaystrue/plutus.json`.

| | |
|---|---|
| validator | `alwaystrue.placeholder.withdraw` |
| script hash | `4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10` |
| reward address (Preview) | `stake_test17p9tymy4q2gxwxzlwzw3gqcqen93tv9jpw7k9flf4ghzuyqfm7lsm` |

It exposes a `withdraw` handler that always succeeds — exactly the shape CIP-113's three
substandard roles need. One script serves all three (issuance, transfer, third-party), which the
standard permits: a node's three logic fields are independent credentials and nothing requires
them to differ, and the reference implementation's own `dummy` substandard reuses one script
across roles.

A note on the CBOR wrapping, since getting it wrong makes every derived address wrong: Aiken
emits `compiledCode` already wrapped in one CBOR byte string, CCL's `PlutusBlueprintUtil` wraps
it once more for the script's `cborHex`, and the script hash covers the single-wrapped form —
`blake2b_224(0x03 ‖ compiledCode)`. `AlwaysTrueScriptHashTest` pins that offline, and
`AlwaysTrueScripts.scriptHash()` re-checks it at runtime and throws if it ever drifts.

**It authorises everything.** Never point a real token at it.

> **Blockfrost's `active` flag is not a registration check.** It tracks whether a stake
> credential is *delegating to a pool*. A script credential registered purely so its
> withdraw-zero is valid never delegates, so it reads `active: false` forever even though Koios
> reports `status: registered` with a 2 ADA deposit against the same address.
>
> The registration signal is whether the **lookup itself succeeds**: a backend only knows a stake
> account once it has been registered on chain. Gating on `active` reports a registered account as
> missing and sends the caller off to register it again — which then fails, either with
> `StakeKeyRegisteredDELEG` or, once a prior identical attempt has landed, with
> `"All inputs are spent"` (input selection is deterministic, so a re-run rebuilds the same
> transaction byte for byte). Step 6 treats all of those as "already usable".

**Step 5 matters more than it looks.** A withdrawal is only valid against a *registered* stake
credential — even a zero one — so every substandard script needs its reward account registered
before it can ever be invoked via withdraw-zero. That is a hard prerequisite for registering a
token, for minting, and for any transfer whose transfer logic is that script. Registering a
*script* stake credential needs no script witness (registration is permissionless; only
delegation and deregistration make the script run), so it is a plain `Tx` with a certificate and
no validator attached. It costs the 2 ADA key deposit, refunded on deregistration. The step is
idempotent, so re-running the suite is safe.

The read-only steps re-derive the table above from chain, so they double as a check that the
baked-in constants are still correct — if the deployment is ever upgraded in place, step 1 will
show it.

## Known gaps worth knowing before you dig in

- **Withdrawal ordering is a hypothesis.** `LedgerOrdering.sortedWithdrawals` puts script
  credentials before key credentials, which is what cardano-ledger's `Credential` `Ord` implies
  and what the reference implementation's own off-chain guide does — but it has not been
  confirmed against a live transaction. CCL's own `WithdrawalUtil` sorts by hash alone; if the
  hypothesis holds, that is an upstream bug.
- **Scripts must be supplied by the caller.** Spending from the base script needs the base
  script and the transfer delegate available. Pass them as reference-script UTxOs via
  `readFrom(...)`, or attach them with `withScripts(...)`. Discovering published script
  references from the deployment is a TODO.
- **Global state is not auto-resolved.** When a registry node declares a `global_state_cs`, its
  UTxO must currently be added by hand with `readFrom(...)`. The builder logs a warning.
- **Datum and redeemer codecs are hand-written.** They should be generated from a vendored
  `plutus.json` via `@Blueprint`, which would turn a shape change into a compile error instead
  of silently invalid CBOR.
- **The build targets Java 17 but the Gradle daemon may launch on an older JDK.** A
  `.java-version` pinning 11 is enough to do it, and the failure surfaces as a dependency
  resolution error — *"looking for a library compatible with JVM runtime version 11"* — which reads
  like a dependency problem rather than a JDK one. The root `build.gradle` now requests a Java 17
  toolchain, so Gradle finds an installed 17 and uses it whatever the launcher is. If you would
  rather not carry that change, `JAVA_HOME=$(/usr/libexec/java_home -v 17)` in front of the command
  does the same job.
