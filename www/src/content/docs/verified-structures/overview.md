---
title: "Overview"
description: "Merkle Patricia Forestry and Jellyfish Merkle Tree: authenticated data structures for Cardano applications."
---

> **Preview feature**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

A verified data structure stores key-value data off chain and reduces all of it to one hash, the *root*. Anyone who holds the root can check a *proof* that a key has a given value, or has no value, without having the data.

The usual Cardano pattern is to keep the data in your application, store the root in a datum, and let a validator check proofs on chain.

## Which one do I need?

| | [MpfTrie](/verified-structures/mpf/) | [Jellyfish Merkle Tree](/verified-structures/jmt/) |
|---|---|---|
| **Choose it for** | Proofs checked on chain by an Aiken validator | Versioned off-chain state, with proofs checked off chain |
| **On-chain verifier** | Aiken's `merkle-patricia-forestry` library | None |
| **Deleting a key** | Supported | Not implemented |
| **Reading old versions** | Record versions with a storage backend | Built in |
| **Writes** | One key at a time, or grouped in a session | Batches, each with a version number |

If your validator needs to check a proof, use MPF.

## Dependencies

RocksDB is the recommended storage. PostgreSQL, H2 and SQLite are alternatives, through the `-rdbms` modules. The storage modules bring in their core module.

```gradle
def cclVersion = '<version>' // the latest preview

dependencies {
    // MPF
    implementation "com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rocksdb:$cclVersion"

    // JMT
    implementation "com.bloxbean.cardano:cardano-client-jellyfish-merkle-rocksdb:$cclVersion"
}
```

SQL storage never creates its tables by itself. Call `RdbmsMpfSchema.createIfNotExists(dbConfig)` for MPF or `RdbmsJmtSchemaInstaller.createIfNotExists(dbConfig)` for JMT once, or apply the bundled DDL from `/ddl/{mpf,jmt}/{postgres,h2,sqlite}/schema.sql` with your migration tool. See the [MPF](https://github.com/bloxbean/cardano-client-lib/tree/master/verified-structures/merkle-patricia-forestry-rdbms) and [JMT](https://github.com/bloxbean/cardano-client-lib/tree/master/verified-structures/jellyfish-merkle-rdbms) module READMEs.

## Next steps

- [MpfTrie](/verified-structures/mpf/): store data and generate proofs for Aiken.
- [Jellyfish Merkle Tree](/verified-structures/jmt/): versioned state and proofs.
- [MPF versions and garbage collection](/verified-structures/multi-version-gc/) and the [MPF production guide](/verified-structures/production-guide/).
