---
sidebar_position: 1
---

# Introduction

A client library for Cardano in Java. This library simplifies the interaction with Cardano blockchain from a Java application.

**Latest Stable Version**: [0.3.0](https://github.com/bloxbean/cardano-client-lib/releases/tag/v0.3.0) (Compatible with Babbage Era)

**Posts**
- [Cardano-client-lib : A Java Library to interact with Cardano - Part I](https://medium.com/p/83fba0fee537)
- [Cardano-client-lib: Transaction with Metadata in Java - Part II](https://medium.com/p/fa34f403b90e)
- [Cardano-client-lib: Minting a new Native Token in Java - Part III](https://medium.com/p/1a94a21cfeeb)
- [Composable functions to build transactions](https://medium.com/coinmonks/cardano-client-lib-new-composable-functions-to-build-transaction-in-java-part-i-be3a8b4da835)

**Examples**

[Cardano-client-lib examples repository](https://github.com/bloxbean/cardano-client-examples/)

[JavaDoc](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-lib/0.3.0/index.html)

[Documentation](https://cardano-client.bloxbean.com/)

**Features**

#### Address Generation

- Address Generation (Base Address, Enterprise Address)
- Generate Address from Mnemonic phase

#### Transaction Serialization & Signing
- API to build Payment transaction (ADA & Native Tokens)
- CBOR serialization of transaction
- Transaction signing

#### High Level api
- To build and submit
    -  Payment transaction
    - Token Minting and token transfer transaction

#### Composable Functions
- To build and submit
    - Payment transaction
    - Token Minting and token transfer
    - Plutus smart contract call
    - Token minting with Plutus contract

[Examples with Composable Functions](https://github.com/bloxbean/cardano-client-examples/tree/main/src/test/java/com/bloxbean/cardano/client/example/function)

#### CIP Implementations
- [CIP20 - Transaction Message/Comment metada](https://cips.cardano.org/cips/cip20/)
- [CIP25 - NFT Metadata Standard](https://cips.cardano.org/cips/cip25/)
- [CIP8  - Message Signing](https://cips.cardano.org/cips/cip8/)
- [CIP30  - dApp signData & verify](https://cips.cardano.org/cips/cip30/)

#### Metadata Builder
- Helper to build Metadata
- Converter to conver JSON (No Schema) to Metadata format

#### Token Minting
- Token Minting transaction builder
- Native script (ScriptAll, ScriptAny, ScriptAtLeast, ScriptPubKey, RequireTimeAfter, RequireTimeBefore)
- Policy Id generation

#### Backend Integration
The library also provides integration with Cardano node through different backend services.
Out of box, the library currently supports integration with following providers through the Backend api.

- [Blockfrost](https://blockfrost.io)
    - **Module :** cardano-client-backend-blockfrost [README](backend-modules/blockfrost/README.md)
    - **Status :** Stable
- [Koios](https://www.koios.rest/)
    - **Module :** cardano-client-backend-koios [README](backend-modules/koios/README.md)
    - **Status :** Beta
- [Ogmios](https://ogmios.dev/)
    - **Module :** cardano-client-backend-koios [README](backend-modules/ogmios/README.md)
    - **Status :** Beta
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
