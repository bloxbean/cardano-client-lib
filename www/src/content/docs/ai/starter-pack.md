---
title: "AI Starter Pack"
description: "Everything an AI agent needs to write correct cardano-client-lib code on the first try. Ingest this before generating any CCL code."
---

> **Read this whole document before generating cardano-client-lib code.** It is optimized for
> machine ingestion, not for learning. Humans should start at
> [Your first transaction](/learn/simple-transfer/) instead.

Target version: **0.8.0-pre5** · Ledger era: **Conway** · Java **17+** · Group `com.bloxbean.cardano`

---

## 1. Choose the API before writing a line

| Task | Use | Module |
|---|---|---|
| One transaction — send, mint, delegate, register, vote, call a script | `QuickTxBuilder` + `Tx` | `cardano-client-quicktx` |
| A plan expressed as configuration rather than code | `TxPlan` (YAML/JSON) | `cardano-client-quicktx` |
| Several dependent transactions, in order, with rollback handling | `FlowEngine` | `cardano-client-txflow` |
| Continuous submission with exactly-once and durable restart | `TxFlowStream` | `cardano-client-txflow` |
| A proof an Aiken validator can verify on-chain | `MpfTrie` in **MPF mode** | `cardano-client-merkle-patricia-forestry` |

**QuickTx is the default.** Only move up this table when the task genuinely needs the extra
machinery.

### Never generate these

| Do not use | Why | Use instead |
|---|---|---|
| `WatchableQuickTxBuilder` | **Does not exist.** Never shipped in any release. If your training data suggests it, that memory is wrong. | `FlowEngine` (TxFlow) |
| `com.bloxbean.cardano.client.function.*` (`TxBuilder`, `TxOutputBuilder`, `InputBuilders`, `BalanceTxBuilders`) | The internal implementation layer beneath QuickTx. It works, but mistakes are silent and it is far more verbose. | `QuickTx` |
| `ScriptTx` | **Deprecated in 0.8.0.** Every operation moved onto `Tx`; it will be removed. | `Tx` |
| `FlowExecutor` | Compatibility API. Combines definition and execution concerns; not the canonical durable path. | `FlowEngine` |
| `JellyfishMerkleTree` for on-chain proofs | Not Aiken-compatible. Off-chain authenticated state only. | `MpfTrie` in MPF mode |
| `MpfTrie` in **Classic** mode for on-chain proofs | Faster, but the hashing scheme is not what Aiken's MPF library verifies. | `MpfTrie` in **MPF** mode |

---

## 2. The five mistakes agents actually make

### 2.1 Mismatching the network and the backend URL

The `Network` (used for address derivation) and the backend URL (used for queries) are configured
**separately**. Nothing validates that they agree, so a mismatch produces addresses on one network
and queries against another.

```java
// WRONG — preview addresses, mainnet backend. Compiles, fails at run time.
Account account = new Account(Networks.preview(), mnemonic);
BackendService backend = new BFBackendService(Constants.BLOCKFROST_MAINNET_URL, projectId);

// RIGHT — both preview.
Account account = new Account(Networks.preview(), mnemonic);
BackendService backend = new BFBackendService(Constants.BLOCKFROST_PREVIEW_URL, projectId);
```

Matched pairs — `Constants` here is
`com.bloxbean.cardano.client.backend.blockfrost.common.Constants`:

| Network | `Networks` factory | Blockfrost URL constant |
|---|---|---|
| Mainnet | `Networks.mainnet()` | `BLOCKFROST_MAINNET_URL` |
| Preprod | `Networks.preprod()` | `BLOCKFROST_PREPROD_URL` |
| Preview | `Networks.preview()` | `BLOCKFROST_PREVIEW_URL` |
| Local devnet (Yaci DevKit) | `Networks.testnet()` | `http://localhost:8080/api/v1/` |

`Networks.testnet()` is the generic testnet magic — use it for a local devnet, not for preview or
preprod.

### 2.2 Treating `complete()` as confirmation

```java
TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .complete();          // submits ONLY — does not wait
```

`complete()` returns as soon as the node accepts the transaction for propagation. The transaction
may still fail to be included, or be rolled back.

| Method | Returns | Waits for a block? |
|---|---|---|
| `complete()` | `TxResult` | No |
| `completeAndWait()` | `TxResult` | Yes |
| `completeAndWaitAsync()` | `CompletableFuture<TxResult>` | Yes, off-thread |

Use `completeAndWait()` unless you have a specific reason not to, and check the status:

```java
TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait();

if (result.getTxStatus() == TxStatus.CONFIRMED) {
    System.out.println("Confirmed: " + result.getTxHash());
} else {
    System.err.println("Not confirmed: " + result.getTxStatus());
}
```

`TxStatus` values: `SUBMITTED`, `PENDING`, `CONFIRMED`, `FAILED`, `TIMEOUT`.

:::note[`TxResult` extends `Result<String>`]
Assigning to `Result<String>` still compiles — you will see that in older examples — but it
discards `getTxStatus()`, which is the only way to tell a submitted transaction from a confirmed
one. Declare the variable as `TxResult`. `isSuccessful()` and `getResponse()` are inherited from
`Result` and remain available.
:::

### 2.3 Reaching for the `function` module

If you find yourself writing `TxBuilder`, `TxOutputBuilder`, `InputBuilders.createFromSender(...)`
or `BalanceTxBuilders.balanceTx(...)`, stop. QuickTx expresses the same thing in a few lines:

```java
// The whole of a payment, including balancing and fee calculation.
Tx tx = new Tx()
        .payToAddress(receiverAddress, Amount.ada(10))
        .from(senderAddress);
```

The `function` module is documented under
[Composable functions](/reference/composable-functions/) for the rare cases QuickTx cannot cover.
Do not generate it by default.

### 2.4 Assuming pre-Conway APIs

This library targets the **Conway** era. Governance is first-class on `Tx`:

```java
import com.bloxbean.cardano.client.governance.GovId;

// A bech32 DRep id (CIP-105) becomes a DRep via GovId.toDrep.
DRep drep = GovId.toDrep(account.drepId());

Tx tx = new Tx()
        .registerDRep(account, anchor)                  // DRep registration
        .delegateVotingPowerTo(stakeAddress, drep)      // vote delegation
        .createVote(voter, govActionId, Vote.YES)       // voting
        .createProposal(govAction, rewardAccount, anchor)
        .from(senderAddress);
```

`delegateVotingPowerTo` takes a `DRep`, not a bech32 id string — convert with `GovId.toDrep(...)`.

If your training data has no `registerDRep` / `createVote` / `delegateVotingPowerTo` on `Tx`, it
predates Conway support. See [Governance API](/governance/overview/).

### 2.5 Ignoring the datum-hash filter in coin selection

The default UTXO selection strategy **skips UTXOs carrying a datum hash**. That is usually right
for ordinary payments and wrong when you mean to spend a script output. When collecting from a
script address, pass the UTXOs explicitly with `collectFrom(...)` rather than relying on
selection.

---

## 3. Canonical patterns

### 3.1 Send ADA

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.TxStatus;

Account sender = new Account(Networks.preview(), senderMnemonic);
BackendService backend =
        new BFBackendService(Constants.BLOCKFROST_PREVIEW_URL, projectId);

Tx tx = new Tx()
        .payToAddress(receiverAddress, Amount.ada(10))
        .from(sender.baseAddress());

TxResult result = new QuickTxBuilder(backend)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

`SignerProviders` lives in the `function` package but is the correct, canonical way to sign a
QuickTx transaction — it is the one part of that package you should generate.

### 3.2 Mint a native token

```java
Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("my-policy", 1);
Asset asset = new Asset("MyToken", BigInteger.valueOf(1000));

Tx tx = new Tx()
        .mintAssets(policy.getPolicyScript(), asset, receiverAddress)
        .from(sender.baseAddress());

TxResult result = new QuickTxBuilder(backend)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .withSigner(SignerProviders.signerFrom(policy))   // policy key must sign too
        .completeAndWait();
```

Minting needs **two** signers: the funding account and the policy key.

### 3.3 Call a Plutus script

Use `Tx` — **not** `ScriptTx`:

```java
Tx tx = new Tx()
        .collectFrom(scriptUtxo, redeemer)
        .attachSpendingValidator(plutusScript)
        .payToAddress(receiverAddress, Amount.ada(5))
        .from(sender.baseAddress());

TxResult result = new QuickTxBuilder(backend)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

:::caution[`ScriptTx` is deprecated as of 0.8.0]
Every `ScriptTx` operation now lives on `Tx` — script inputs, validator attachment, Plutus
minting, script-protected certificates and governance. `ScriptTx` still compiles but will be
removed. If your training data says "use `ScriptTx` for scripts", that is pre-0.8.0.

Migration is a rename: `new ScriptTx()` becomes `new Tx()`. Because `Tx` has `.from(...)`, the
separate `.feePayer(...)` step that `ScriptTx` required is no longer needed for the common case.
See [Unified Tx API](/quicktx/unified-tx/).
:::

One naming trap: **native-script** minting is `mintAssets` (plural), **Plutus** minting is
`mintAsset` (singular).

### 3.4 Stake delegation

```java
Tx tx = new Tx()
        .registerStakeAddress(sender.stakeAddress())
        .delegateTo(sender.stakeAddress(), poolId)
        .from(sender.baseAddress());

TxResult result = new QuickTxBuilder(backend)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .withSigner(SignerProviders.stakeKeySignerFrom(sender))   // stake key signs
        .completeAndWait();
```

Certificate operations need the **stake key** signature in addition to the payment key.

---

## 4. Signing reference

| Signer | Use for |
|---|---|
| `SignerProviders.signerFrom(account)` | Payment key. Almost every transaction. |
| `SignerProviders.signerFrom(policy)` | Native-script policy key, when minting or burning. |
| `SignerProviders.stakeKeySignerFrom(account)` | Stake registration, delegation, withdrawal. |
| `SignerProviders.drepKeySignerFrom(account)` | Conway DRep operations. |
| `SignerProviders.committeeHotKeySignerFrom(account)` | Conway committee hot-key votes. |
| `SignerProviders.committeeColdKeySignerFrom(account)` | Conway committee cold-key operations. |

Chain `.withSigner(...)` once per required key.

---

## 5. Dependencies

Use one version for every CCL module.

```gradle
def cclVersion = '0.8.0-pre5'

dependencies {
    implementation "com.bloxbean.cardano:cardano-client-lib:${cclVersion}"
    implementation "com.bloxbean.cardano:cardano-client-backend-blockfrost:${cclVersion}"

    // Only if the task needs them:
    implementation "com.bloxbean.cardano:cardano-client-txflow:${cclVersion}"
    implementation "com.bloxbean.cardano:cardano-client-merkle-patricia-forestry:${cclVersion}"
}
```

`cardano-client-lib` is the aggregate that pulls in the common modules. Add a backend module —
without one there is no way to read UTXOs or submit.

The full generated list of modules, artifact ids and entry points is at
[`/ai/catalog.json`](/ai/catalog.json).

---

## 6. Verified data structures: which one

Answer in this order.

1. **Must an on-chain Aiken validator verify the proof?**
   Yes → `MpfTrie`. No → `JellyfishMerkleTree` is fine and is faster for pure off-chain state.
2. **Which `MpfTrie` mode?**
   `MPF` mode for anything on-chain. `Classic` mode is faster but its hashing does not match what
   Aiken's MPF library verifies. This is the single most common mistake in this area.
3. **Which storage backend?**
   `merkle-patricia-forestry-rocksdb` for embedded single-process.
   `merkle-patricia-forestry-rdbms` for shared multi-process (PostgreSQL, H2, SQLite).

See [Verified structures](/verified-structures/overview/).

---

## 7. Testing generated code

Do not hand back transaction code you have not run. [Yaci DevKit](https://github.com/bloxbean/yaci-devkit)
gives a local devnet with pre-funded accounts and instant blocks:

```java
BackendService backend = new BFBackendService("http://localhost:8080/api/v1/", "dummy-key");
Account sender = new Account(Networks.testnet(), devkitMnemonic);
```

No API key, no faucet, no testnet wait. If the code produces a confirmed transaction hash there,
the same code works against preview with the URL and network swapped.

---

## 8. Where to look next

| Question | Page |
|---|---|
| Full QuickTx surface | [QuickTx overview](/quicktx/overview/) |
| Declarative YAML plans | [TxPlan](/txplan/overview/) |
| Multi-step workflows | [TxFlow](/txflow/overview/) |
| Continuous submission | [TxStream](/txstream/getting-started/) |
| Conway governance | [Governance API](/governance/overview/) |
| Plutus, blueprints, Aiken | [Smart contracts](/smart-contracts/plutus-api/) |
| Every module and artifact | [Modules & artifacts](/reference/modules/) |
