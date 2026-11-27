---
title: "What's new in 0.8.0"
description: "New features in the Cardano Client Lib 0.8.0 preview releases."
---

> **Preview features**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

The 0.8.0 previews add several major features for building Cardano applications in Java. For the complete list of changes, see the [release notes](https://github.com/bloxbean/cardano-client-lib/releases).

## New features

### [Unified Tx API](/quicktx/unified-tx/)

`ScriptTx` is deprecated in the 0.8.0 previews (not in stable 0.7.x). All script operations, such as `collectFrom` with redeemers, Plutus minting, validator attachment, and script-protected staking and governance, are available directly on the `Tx` class. You no longer choose between `Tx` and `ScriptTx`, and you can mix script and non-script operations in one transaction.

### [TxFlow: multi-step transactions](/txflow/overview/)

TxFlow orchestrates multi-step Cardano transactions with automatic UTXO chaining, confirmation tracking, rollback detection and retries.

### [TxStream: many transactions, reliably](/txstream/getting-started/)

TxStream submits a continuous stream of transactions, each landing once, and durable stores carry that across restarts. It runs on the TxFlow engine, and it's experimental: see [known issues](/project/known-issues/).

### [TxPlan: YAML transactions](/txplan/overview/)

TxPlan defines Cardano transactions in YAML, with variables and context properties, and runs them through `QuickTxBuilder`. It's part of the `quicktx` module, so it needs no additional dependency.

### [Verified data structures](/verified-structures/overview/)

Merkle Patricia Forestry (MPF) and Jellyfish Merkle Tree (JMT), with RocksDB and SQL storage. MPF proofs can be verified on chain by Aiken's library.

## New in 0.8.0-pre7

- **[CIP-113 programmable tokens](/learn/cip113-programmable-tokens/):** tokens with on-chain transfer rules, through the new `cardano-client-programmable-token` module.
- **QuickTx extensions in TxFlow:** TxFlow and TxStream can run steps that use extension intents, such as programmable tokens.
- **MPF proofs:** proofs are now canonical and match the official JavaScript library. RocksDB garbage collection also received data-loss fixes. See [MpfTrie](/verified-structures/mpf/).
- **JMT:** absence proof fixes, bounded historical reads and a schema installer. See [Jellyfish Merkle Tree](/verified-structures/jmt/).
- **Nexus backend:** the new `cardano-client-backend-nexus` module.
- **TxPlan:** the `additional_signers_count` context property.
- **Lower-level parsing:** stack-safe CBOR and Plutus data codecs, plus `RawTx` and `RawBlock` views that keep a transaction's original bytes.
- **GraalVM:** reachability metadata now ships in the module jars.

## Gradle dependencies

```gradle
def cclVersion = '<version>' // the latest preview

dependencies {
    // Core library, including QuickTx with the Unified Tx API and TxPlan
    implementation "com.bloxbean.cardano:cardano-client-lib:$cclVersion"

    // TxFlow and TxStream
    implementation "com.bloxbean.cardano:cardano-client-txflow:$cclVersion"

    // Verified data structures on RocksDB
    implementation "com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rocksdb:$cclVersion"
    implementation "com.bloxbean.cardano:cardano-client-jellyfish-merkle-rocksdb:$cclVersion"

    // Backend provider (choose one)
    implementation "com.bloxbean.cardano:cardano-client-backend-blockfrost:$cclVersion"
}
```

## API stability

See [known issues](/project/known-issues/) for current gaps. These APIs are a preview for early adopters. Core concepts are stable, but method signatures, configuration options and defaults may change before the stable release.

If you find a problem or have a suggestion, please open an issue on [GitHub](https://github.com/bloxbean/cardano-client-lib/issues).
