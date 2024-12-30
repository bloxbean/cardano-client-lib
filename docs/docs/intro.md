---
sidebar_position: 1
---

# Introduction

A client library for Cardano in Java. This library simplifies the interaction with Cardano blockchain from a Java application.

:::info

**Latest Stable Version**: [0.6.3](https://github.com/bloxbean/cardano-client-lib/releases/tag/v0.6.3)

:::

### Recent Posts
- [Introducing QuickTx API to build transactions](https://satran004.medium.com/introducing-new-quicktx-api-in-cardano-client-lib-0-5-0-beta1-5beb491282ce)
- [Composable functions to build transactions](https://medium.com/coinmonks/cardano-client-lib-new-composable-functions-to-build-transaction-in-java-part-i-be3a8b4da835)
- [Demo:- Test Aiken Smart Contract Using Java Offchain Code with Yaci DevKit](https://youtu.be/PTnSc85t0Nk?si=44uK6KFrTIH3m06A)

### Old Posts
**Note:** Some of the APIs mentioned in the below posts are deprecated. Please refer to the latest documentation for the latest APIs.

- [Cardano-client-lib : A Java Library to interact with Cardano - Part I](https://medium.com/p/83fba0fee537) 
- [Cardano-client-lib: Transaction with Metadata in Java - Part II](https://medium.com/p/fa34f403b90e) 
- [Cardano-client-lib: Minting a new Native Token in Java - Part III](https://medium.com/p/1a94a21cfeeb) 

### Examples

[Cardano-client-lib examples repository](https://github.com/bloxbean/cardano-client-examples/tree/main/src/test/java/com/bloxbean/cardano/client/example)

[JavaDoc](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html)

[Documentation](https://cardano-client.dev/)

### Features

#### Address Generation

- Address Generation (Base Address, Enterprise Address)
- Generate Address from Mnemonic phase

#### Transaction Serialization & Signing
- API to build Payment transaction (ADA & Native Tokens)
- CBOR serialization of transaction
- Transaction signing

#### QuickTx API
- Build and submit transaction with simple declarative style API
- Supports
    - Payment transaction
    - Token Minting and token transfer
    - Plutus smart contract call
    - Token minting with Plutus contract
    - Staking operations
    - Governance transactions (Preview)
  
#### Composable Functions
- Composable functions to build transaction
- Supports
    - Payment transaction
    - Token Minting and token transfer
    - Plutus smart contract call
    - Token minting with Plutus contract
  
#### High Level api (Deprecated)
- To build and submit
    -  Payment transaction
    - Token Minting and token transfer transaction

[Examples with Composable Functions](https://github.com/bloxbean/cardano-client-examples/tree/main/src/test/java/com/bloxbean/cardano/client/example/function)

#### CIP Implementations
- [CIP20 - Transaction Message/Comment metada](https://cips.cardano.org/cips/cip20/)
- [CIP25 - NFT Metadata Standard](https://cips.cardano.org/cips/cip25/)
- [CIP8  - Message Signing](https://cips.cardano.org/cips/cip8/)
- [CIP30  - dApp signData & verify](https://cips.cardano.org/cips/cip30/)
- [CIP27  - CNFT Community Royalties Standard](https://cips.cardano.org/cips/cip27/)
- [CIP68  - Datum Metadata Standard](https://cips.cardano.org/cips/cip68/)

#### Metadata Builder
- Helper to build Metadata
- Converter to convert JSON (No Schema) to Metadata format

#### Token Minting
- Token Minting transaction builder
- Native script (ScriptAll, ScriptAny, ScriptAtLeast, ScriptPubKey, RequireTimeAfter, RequireTimeBefore)
- Policy Id generation

#### Backend Integration
The library also provides integration with Cardano node through different backend services.
Out of box, the library currently supports integration with following providers through the Backend api.

- [Blockfrost](https://blockfrost.io)
    - **Module :** cardano-client-backend-blockfrost [README](https://github.com/bloxbean/cardano-client-lib/blob/master/backend-modules/blockfrost/README.md)
    - **Status :** Stable
- [Koios](https://www.koios.rest/)
    - **Module :** cardano-client-backend-koios [README](https://github.com/bloxbean/cardano-client-lib/blob/master/backend-modules/koios/README.md)
    - **Status :** Stable
- [Ogmios](https://ogmios.dev/)
    - **Module :** cardano-client-backend-koios [README](https://github.com/bloxbean/cardano-client-lib/blob/master/backend-modules/ogmios/README.md)
    - **Status :** Stable
    - **Supported Apis :** submitTransaction, evaluateTx, Kupo support (UtxoService)

**Following Backend apis are currently available**
- TransactionService (Submit transaction, Get transaction, Evaluate ExUnits for Script Txn)
- AddressService (Get address details)
- UtxoService (Get utxos for an address)
- AssetService
- BlockService
- NetworkInfoService
- EpochService
- MetadataService
