---
title: "CIP30 API"
description: "dApp Connector and Data Signing implementation"
sidebar_position: 6
---

# CIP30 API

CIP30 (Cardano Improvement Proposal 30) defines a standard for dApp-wallet communication on the Cardano blockchain. The Cardano Client Library provides CIP30-compatible data signing and verification built on CIP8 COSE Sign1. It bundles the signing flow—header construction, Sig_Structure generation, signing, and verification—into a small surface so you can focus on wallet UX instead of COSE internals.

## Key Features

- **Data Signing**: `CIP30DataSigner.signData(...)`
- **Verification**: `CIP30DataSigner.verify(DataSignature)`
- **COSE Integration**: Uses CIP8 builders internally
- **UTXO Bridge**: `CIP30UtxoSupplier` for hex-encoded UTxOs

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip30
- **Dependencies**: cip8, core

## Usage Examples

### Signing Data with CIP30

Sign arbitrary payload bytes together with the user’s address (embedded in COSE headers) to prove address ownership.

```java
// Create account for signing
Account signerAccount = Account.createFromMnemonic(Networks.testnet(), "your mnemonic words");

byte[] addressBytes = signerAccount.baseAddress().getBytes();
byte[] payload = "Hello from dApp!".getBytes(StandardCharsets.UTF_8);

DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(
        addressBytes,
        payload,
        signerAccount);

String signatureHex = dataSignature.signature();
String keyHex = dataSignature.key();
```

### Verifying CIP30 Signatures

Verify both the signature and that the address in the protected header matches the public key used for signing.

```java
DataSignature dataSignature = new DataSignature(signatureHex, keyHex);

boolean isValid = CIP30DataSigner.INSTANCE.verify(dataSignature);
```

### Working with DataSignature Objects

Read back address, curve, and public key information from the COSE-encoded signature without manual parsing.

```java
DataSignature dataSignature = new DataSignature(signatureHex, keyHex);

byte[] addressFromSignature = dataSignature.address();
Integer crv = dataSignature.crv();
byte[] x = dataSignature.x();
```

### Signing with Hash Payload

Enable hashing for large payloads so only a Blake2b-224 hash is signed instead of the full data.

```java
DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(
        addressBytes,
        largePayload,
        signerAccount,
        true // hashPayload = true
);
```

### UTXO Management (CIP30UtxoSupplier)

Convert wallet-provided hex-encoded CBOR UTxOs into the library’s `UtxoSupplier` for transaction building.

```java
// Hex-encoded CBOR UTxOs from wallet getUtxos()
List<String> walletUtxos = fetchFromWallet();

CIP30UtxoSupplier utxoSupplier = new CIP30UtxoSupplier(walletUtxos);
List<Utxo> page = utxoSupplier.getPage(address, 20, 0, OrderEnum.asc);
```

## API Reference

### CIP30DataSigner

```java
// Sign data
public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull Account signer)
public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull Account signer, boolean hashPayload)
public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull byte[] pvtKey, @NonNull byte[] pubKey)
public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull byte[] pvtKey, @NonNull byte[] pubKey, boolean hashPayload)

// Verify signature
public boolean verify(@NonNull DataSignature dataSignature)
```

### DataSignature

```java
public DataSignature(@NonNull String signature, @NonNull String key)

public DataSignature signature(@NonNull String signature)
public DataSignature key(@NonNull String key)

public byte[] address() // address bytes from protected header
public Integer crv() // curve id from COSE_Key other header
public byte[] x() // public key bytes (x) from COSE_Key other header
```

### CIP30UtxoSupplier

```java
// Construct from hex-encoded CBOR UTxOs
public CIP30UtxoSupplier(List<String> hexEncodedCborUtxos)

public List<Utxo> getPage(String address, Integer pageSize, Integer page, OrderEnum order)
public Optional<Utxo> getTxOutput(String txHash, int outputIndex)
public List<Utxo> getAll(String paymentAddress)
```

### Constants (`CIP30Constant`)

- **ADDRESS_KEY**: "address" (stored in protected header other map)
- **ALG_EdDSA**: -8
- **CRV_Ed25519**: 6
- **OKP**: 1
- **X_KEY**: -2 (public key bytes)
- **CRV_KEY**: -1 (curve id)

## CIP30 Data Signing Flow

1. `CIP30DataSigner` builds a CIP8 COSE_Sign1 with the payload (optionally hashed) and address in headers.
2. Signature is created with `Configuration.INSTANCE.getSigningProvider()`.
3. `DataSignature` stores the hex-encoded COSE_Sign1 and COSE_Key.
4. `verify` recreates the SigStructure, checks the signature, and verifies the address against the key.

## Integration with Other Standards

- **CIP8**: All signing uses the CIP8 COSE builders.
- **CIP67/68/25**: Use normal transaction-building APIs for assets/metadata; CIP30 only covers data signing.

For more information about CIP30, refer to the [official CIP30 specification](https://cips.cardano.org/cips/cip30/).
