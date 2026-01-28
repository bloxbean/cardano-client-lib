# Cardano Client Library - Architecture Overview

A comprehensive Java library for interacting with the Cardano blockchain, providing high-level APIs for transaction building, account management, smart contract integration, and blockchain data access.

## Project Overview

**Latest Stable Version**: 0.6.4  
**Latest Beta Version**: 0.7.0-beta3  
**Group ID**: com.bloxbean.cardano

The cardano-client-lib is designed with a modular architecture that enables developers to include only the components they need while maintaining clean separation of concerns and standard compliance.

## Core Architecture Principles

### 1. Modular Design
- **Fine-grained modules**: Each module has a specific responsibility and minimal dependencies
- **Optional dependencies**: Add only what you need for your use case
- **Clean interfaces**: Well-defined APIs between modules

### 2. Plugin Architecture
- **Backend abstraction**: Unified interface for different blockchain data providers
- **Supplier patterns**: Pluggable implementations for protocol parameters, UTXOs, and transaction processing
- **Strategy patterns**: Configurable algorithms for UTXO selection

### 3. Standard Compliance
- **CIP Standards**: Comprehensive implementation of Cardano Improvement Proposals
- **Era Support**: Handles Byron, Shelley, and Conway era features
- **CBOR Serialization**: Complete transaction serialization following Cardano specifications

### 4. Type Safety
- **Strong typing**: Extensive use of type-safe APIs
- **Compile-time validation**: Annotation processors for PlutusData conversion
- **Error handling**: Structured error types and validation

## Module Architecture

### Foundation Layer

#### common
**Purpose**: Core utilities and constants  
**Dependencies**: None  
**Key Features**:
- ADA/Lovelace conversion utilities
- CBOR serialization support
- Hex/JSON utilities
- Bech32 encoding/decoding

#### common-spec
**Purpose**: Common CBOR specifications  
**Dependencies**: None  
**Key Features**:
- Era and network identification
- Mathematical types (Rational, UnitInterval)
- Base script interfaces

### Cryptographic Layer

#### crypto
**Purpose**: Cryptographic operations and key management  
**Dependencies**: common  
**Key Features**:
- BIP32/BIP39/CIP1852 implementation
- EdDSA signing provider
- Multi-language mnemonic support
- Blake2b hashing utilities

#### address
**Purpose**: Address generation and management  
**Dependencies**: common, crypto, common-spec  
**Key Features**:
- All Cardano address types (Base, Enterprise, Pointer, Stake, Byron)
- Network-aware address generation
- Automatic address type detection

### Transaction Layer

#### transaction-spec
**Purpose**: Low-level transaction serialization  
**Dependencies**: common, crypto, address, metadata  
**Key Features**:
- Complete CBOR serialization/deserialization
- Conway era governance support
- Native script composition
- Value and asset representation

#### metadata
**Purpose**: Transaction metadata handling  
**Dependencies**: common, crypto  
**Key Features**:
- JSON to/from metadata conversion
- Structured metadata building
- No-schema metadata support

### Smart Contract Layer

#### plutus
**Purpose**: Plutus smart contract integration  
**Dependencies**: common, crypto, common-spec  
**Key Features**:
- PlutusData hierarchy implementation
- Multi-version Plutus script support (V1/V2/V3)
- Aiken blueprint integration
- Type-safe PlutusData conversion

#### annotation-processor
**Purpose**: Compile-time code generation  
**Dependencies**: plutus  
**Key Features**:
- Automatic PlutusData converter generation
- Blueprint-driven code generation
- Compile-time validation

### UTXO Management Layer

#### coinselection
**Purpose**: UTXO selection algorithms  
**Dependencies**: common, core-api  
**Key Features**:
- Multiple selection strategies (Default, LargestFirst, RandomImprove)
- Datum-aware selection
- Configurable selection limits

#### core-api
**Purpose**: Core APIs and interfaces  
**Dependencies**: common, transaction-spec  
**Key Features**:
- UtxoSupplier, TransactionProcessor interfaces
- ProtocolParamsSupplier abstraction
- Core model classes

### Business Logic Layer

#### core
**Purpose**: Account management and transaction building  
**Dependencies**: common, crypto, address, transaction-spec, core-api, plutus, coinselection  
**Key Features**:
- Account creation and management
- Multi-signature support
- Fee calculation service
- Transaction building utilities

### High-Level APIs

#### function
**Purpose**: Composable transaction functions  
**Dependencies**: core, hd-wallet  
**Key Features**:
- Functional composition of transaction builders
- Script execution cost evaluation
- Automatic fee calculation

#### quicktx
**Purpose**: High-level transaction API  
**Dependencies**: core, function, backend, hd-wallet  
**Key Features**:
- Fluent API for transaction building
- Automatic UTXO selection and balancing
- Integrated verification system

### Standard Implementations

#### cip (CIP Standards)
- **cip8**: Message signing (CIP-8)
- **cip20**: Transaction message metadata (CIP-20)
- **cip25**: NFT metadata standard (CIP-25)
- **cip27**: Community royalties (CIP-27)
- **cip30**: dApp-Wallet bridge (CIP-30)
- **cip67**: Asset name labeling (CIP-67)
- **cip68**: Datum metadata (CIP-68)

#### governance
**Purpose**: Conway era governance support  
**Dependencies**: common, crypto  
**Key Features**:
- CIP-105 HD wallet governance key derivation
- DRep and Committee key management
- Governance identifier handling

#### hd-wallet
**Purpose**: Hierarchical deterministic wallet  
**Dependencies**: core  
**Key Features**:
- BIP44/CIP1852 wallet implementation
- Automatic address discovery
- Gap limit scanning

### Backend Layer

#### backend
**Purpose**: Backend service abstractions  
**Dependencies**: core-api, transaction-spec  
**Key Services**:
- UtxoService, TransactionService
- AddressService, AssetService
- BlockService, EpochService
- NetworkInfoService, MetadataService

#### Backend Implementations

##### blockfrost
**Provider**: Blockfrost API  
**Type**: RESTful API service  
**Features**: 
- Production-ready with rate limiting
- Multiple network support
- Comprehensive coverage

##### koios
**Provider**: Koios API  
**Type**: Community-driven decentralized API  
**Features**: 
- Free public access
- No API key required
- Decentralized infrastructure

##### ogmios
**Provider**: Ogmios (cardano-node bridge)  
**Type**: Direct node connection  
**Features**: 
- Minimal overhead
- Transaction evaluation
- Protocol parameter access

### Supplier Modules

#### supplier/ogmios-supplier
**Purpose**: Ogmios-specific implementations  
**Provides**: ProtocolParamsSupplier, TransactionProcessor

#### supplier/kupo-supplier
**Purpose**: Kupo indexer integration  
**Provides**: UtxoSupplier

#### supplier/utxorpc-supplier
**Purpose**: UTxO RPC integration  
**Status**: Under development

## Key Architectural Patterns

### 1. Builder Pattern
Extensive use throughout the library for complex object construction:
- TransactionBuilder for transaction assembly
- MetadataBuilder for metadata construction
- QuickTxBuilder for high-level transaction creation

### 2. Strategy Pattern
Pluggable algorithms and implementations:
- UtxoSelectionStrategy for UTXO selection
- SigningProvider for cryptographic operations
- Backend implementations for blockchain access

### 3. Factory Pattern
Multiple creation methods for flexible instantiation:
- Account creation from mnemonic, keys, or scratch
- Address generation for different types and networks
- Service creation from backend implementations

### 4. Functional Composition
Composable functions for transaction building:
```java
TxBuilder txBuilder = output1.outputBuilder()
    .and(output2.outputBuilder())
    .buildInputs(createFromSender(senderAddress))
    .andThen(balanceTx(senderAddress, 1));
```

### 5. Interface Segregation
Clean separation between:
- Core functionality and backend implementations
- High-level APIs and low-level serialization
- Different service responsibilities

## Integration Flows

### 1. Account Creation and Management
```
Mnemonic → Root Key → Account Key → Payment/Stake/Governance Keys → Addresses
```

### 2. Transaction Building Flow
```
Transaction Request → UTXO Selection → Transaction Assembly → Fee Calculation → Signing → Serialization
```

### 3. Backend Integration
```
BackendService → Specific Implementation (Blockfrost/Koios/Ogmios) → Cardano Network
```

### 4. Smart Contract Execution
```
PlutusScript + Redeemer → Script Evaluation → Transaction Assembly → Submission
```

## Dependency Hierarchy

```
Application Layer:     quicktx, function
Business Logic:        core, hd-wallet, governance
Smart Contracts:       plutus, annotation-processor
Standards:             cip modules
Transaction:           transaction-spec, metadata
Cryptography:          crypto, address
Backend:               backend, backend-modules, supplier
Foundation:            common, common-spec, core-api, coinselection
```

## Usage Recommendations

### For Simple Applications
Use the top-level `cardano-client-lib` module with one backend:
```xml
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-lib</artifactId>
    <version>0.6.4</version>
</dependency>
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-blockfrost</artifactId>
    <version>0.6.4</version>
</dependency>
```

### For Fine-Grained Control
Include only needed modules:
```xml
<!-- Core functionality -->
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-core</artifactId>
    <version>0.6.4</version>
</dependency>
<!-- Backend -->
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-koios</artifactId>
    <version>0.6.4</version>
</dependency>
<!-- High-level API -->
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-quicktx</artifactId>
    <version>0.6.4</version>
</dependency>
```

## API Evolution

### Current APIs
- **QuickTx API**: Recommended for new applications
- **Composable Functions**: For advanced transaction building
- **Core API**: Deprecated but maintained for backward compatibility

### Migration Path
The library provides a clear migration path from legacy APIs to modern approaches while maintaining backward compatibility.

## Summary

The cardano-client-lib architecture demonstrates excellent software engineering practices with its modular design, clean abstractions, and comprehensive standard compliance. The library enables developers to interact with Cardano blockchain efficiently while providing flexibility to choose the appropriate level of abstraction and backend implementation for their specific needs.