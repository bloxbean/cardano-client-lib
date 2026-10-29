---
title: "Why Cardano Client Lib"
description: "A Java library for building, signing and submitting Cardano transactions — what it covers, who it is for, and where to start."
---

Cardano Client Lib (CCL) is a Java library for interacting with the Cardano blockchain. It builds,
signs and submits transactions — from a simple ADA payment to a Plutus script call, a governance
vote, or a multi-step workflow where one transaction spends what the last one produced.

It is a **library, not a framework**. There is no server to run and no lifecycle to adopt: you
call it from whatever you already have — a Spring service, a CLI, a test, a Lambda.

```java
Tx tx = new Tx()
        .payToAddress(receiverAddress, Amount.ada(10))
        .from(sender.baseAddress());

TxResult result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

That is a complete, confirmed payment. UTXO selection, balancing, fee calculation, change and
confirmation tracking are handled.

## What it covers

| Area | What you can do |
|---|---|
| **Transactions** | Payments, multi-asset outputs, metadata, validity intervals, custom fee payers and collateral |
| **Native tokens & NFTs** | Minting and burning with native-script or Plutus policies, CIP-25 and CIP-68 metadata |
| **Smart contracts** | Spend from and mint with Plutus V1–V3 validators, reference scripts and inputs, CIP-57 blueprint code generation, Aiken and Scalus interop |
| **Staking** | Registration, delegation, reward withdrawal, deregistration, script-based stake credentials |
| **Governance** | Full Conway era — DRep registration and voting, vote delegation, proposals, committee and treasury actions |
| **Addresses & keys** | BIP39 mnemonics, CIP-1852 derivation, HD wallets, base/enterprise/pointer/reward addresses |
| **Standards** | CIP-8, 20, 25, 27, 30, 67, 68 |
| **Backends** | Blockfrost, Koios, Ogmios/Kupo, Nexus — or your own via `UtxoSupplier` |

Beyond a single transaction, three preview APIs handle the harder coordination problems:
[TxPlan](/txplan/getting-started/) for transactions defined as configuration,
[TxFlow](/txflow/your-first-flow/) for multi-step workflows with UTXO chaining and rollback
handling, and [TxStream](/txstream/getting-started/) for continuous submission with exactly-once
delivery.

## Where to start

| You are | Start at |
|---|---|
| New to the library | [Installation](/start-here/installation/), then [Your first transaction](/learn/simple-transfer/) |
| New to Cardano's UTXO model | [Core concepts](/start-here/core-concepts/) |
| Looking for a specific API | [Key APIs at a glance](/start-here/key-apis/) |
| Chaining several transactions | [TxFlow: your first flow](/txflow/your-first-flow/) |
| An AI agent, or using one | [AI Starter Pack](/ai/starter-pack/) |

The [guided path](/learn/simple-transfer/) runs from a first payment through tokens, NFTs,
multi-sig, Plutus scripts and staking. It is worth following in order if you are new — each step
builds on the one before.

## Versions

- **Latest stable:** [0.7.1](https://github.com/bloxbean/cardano-client-lib/releases/tag/v0.7.1)
- **Current preview:** 0.8.0-pre5 — see [what's new](/project/whats-new/) for the Unified Tx API,
  TxFlow, TxPlan and verified data structures

Java 17 or later is required.

## Reading and watching

- [Introducing the QuickTx API](https://satran004.medium.com/introducing-new-quicktx-api-in-cardano-client-lib-0-5-0-beta1-5beb491282ce)
- [Composable functions to build transactions](https://medium.com/coinmonks/cardano-client-lib-new-composable-functions-to-build-transaction-in-java-part-i-be3a8b4da835)
- [Testing an Aiken contract from Java with Yaci DevKit](https://youtu.be/PTnSc85t0Nk?si=44uK6KFrTIH3m06A)
- [Example projects](https://github.com/bloxbean/cardano-client-examples) — runnable samples
- [Showcase](/project/showcase/) — projects built with CCL
