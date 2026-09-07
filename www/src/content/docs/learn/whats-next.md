---
title: "What's next"
description: "You've finished the guided path. Here's how to choose what to read next based on what you're actually building."
---

You can now build, sign and submit every common kind of Cardano transaction: payments, native
tokens, NFTs, multi-sig, Plutus script calls and staking certificates. That covers most of what an
application needs.

What follows is not a reading list to work through in order. Pick the row that matches the problem
you actually have.

## Choose by what you're building

| What you're building | Go to |
|---|---|
| More transaction shapes — governance, pool registration, reference scripts | [QuickTx overview](/quicktx/overview/) |
| Transactions defined as config rather than code | [TxPlan](/txplan/getting-started/) |
| Several transactions that depend on each other's outputs | [TxFlow](/txflow/overview/) |
| A service submitting transactions continuously | [TxStream](/txstream/getting-started/) |
| DRep registration, voting, proposals | [Governance API](/governance/overview/) |
| Type-safe datums and redeemers from a CIP-57 blueprint | [Blueprint code generation](/smart-contracts/blueprint-codegen/) |
| Proofs an Aiken validator can verify on-chain | [Verified structures](/verified-structures/overview/) |
| Wallet integration (CIP-30) | [CIP-30](/standards/cip30/) |
| An AI agent writing CCL code for you | [Using CCL with AI](/ai/) |

## The three preview APIs, and when each earns its place

TxPlan, TxFlow and TxStream solve genuinely different problems. The most common mistake is
reaching for the largest one first.

**Start with plain QuickTx.** One `Tx`, one `completeAndWait()`. It handles far more than people
expect — including multi-asset outputs, certificates, governance and script spending. Move up only
when you hit a specific wall.

| You hit this wall | Because | Use |
|---|---|---|
| "The transaction shape is fixed, but the values change per run — and operators want to review them" | The decision belongs in config, not code | **[TxPlan](/txplan/getting-started/)** |
| "Transaction B needs to spend an output that transaction A creates" | UTXO chaining, ordering and rollback handling are genuinely hard to do by hand | **[TxFlow](/txflow/overview/)** |
| "I'm submitting a continuous stream and must never double-submit, even across a restart" | Exactly-once delivery and durable recovery need real machinery | **[TxStream](/txstream/getting-started/)** |

They compose: a TxFlow step can execute a TxPlan, and TxStream runs on the TxFlow engine.

:::note[These APIs are preview]
They work and are documented in full, but their surfaces may shift before 0.8.0 final. Pin an
exact version if you build on them now.
:::

## Test against a local devnet

The single biggest speed-up when learning this library is not reading — it is a fast feedback
loop. [Yaci DevKit](https://github.com/bloxbean/yaci-devkit) runs a local Cardano devnet with
pre-funded accounts and near-instant blocks:

```java
BackendService backend = new BFBackendService("http://localhost:8080/api/v1/", "dummy-key");
Account sender = new Account(Networks.testnet(), devkitMnemonic);
```

No API key, no faucet, no waiting on epoch boundaries. Because you can advance epochs in seconds,
it is the only practical way to test a full staking or governance lifecycle. The same code runs
against preview with the URL and network swapped.

## Understanding what the library did

When a transaction does something you did not expect, inspect it before it goes out:

```java
TxResult result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .withTxInspector(txn -> System.out.println(JsonUtil.getPrettyJson(txn)))
        .completeAndWait();
```

`withTxInspector` hands you the fully built transaction — inputs chosen, fee calculated, change
output added — just before submission. It is the fastest way to answer "why is the fee that
number" or "which UTXOs did it pick".

## Reference material

- [Modules & artifacts](/reference/modules/) — every published module and what it is for
- [Backend services](/reference/backend-services/) — Blockfrost, Koios, Ogmios/Kupo
- [Coin selection](/reference/coin-selection/) — UTXO selection strategies and their trade-offs
- [Composable functions](/reference/composable-functions/) — the layer beneath QuickTx, for the
  rare case QuickTx cannot express what you need

## Where to get help

- [GitHub Discussions](https://github.com/bloxbean/cardano-client-lib/discussions) — questions
- [GitHub Issues](https://github.com/bloxbean/cardano-client-lib/issues) — bugs and feature requests
- [cardano-client-examples](https://github.com/bloxbean/cardano-client-examples) — runnable samples
- [Showcase](/project/showcase/) — projects built with this library
