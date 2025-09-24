---
title: "CIP30 API"
description: "dApp Connector and Data Signing implementation"
sidebar_position: 6
---

# CIP30 API

CIP30 (Cardano Improvement Proposal 30) defines a standard for dApp-wallet communication on the Cardano blockchain. The Cardano Client Library provides APIs for data signing and signature verification according to the CIP30 specification.

## Key Features

- **Data Signing**: Sign arbitrary data using CIP30's signData() method
- **Signature Verification**: Verify CIP30 data signatures
- **COSE Integration**: Built on CIP8 COSE signature standard
- **Address Validation**: Validate addresses in signatures
- **Standard Compliance**: Full CIP30 specification support

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip30
- **Dependencies**: cip8, core

## Usage Examples

### Signing Data with CIP30

```java
// Create account for signing
Account signerAccount = Account.createFromMnemonic(Networks.testnet(), "your mnemonic words");

// Get address bytes
byte[] addressBytes = signerAccount.baseAddress().getBytes();

// Create payload to sign
String message = "Hello from dApp!";
byte[] payload = message.getBytes(StandardCharsets.UTF_8);

// Sign data using CIP30DataSigner
DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(
    addressBytes, 
    payload, 
    signerAccount
);

// Get signature as hex string
String signatureHex = dataSignature.signature();
String keyHex = dataSignature.key();

System.out.println("Signature: " + signatureHex);
System.out.println("Key: " + keyHex);
```

### Verifying CIP30 Signatures

```java
// Create DataSignature from hex strings
DataSignature dataSignature = new DataSignature(signatureHex, keyHex);

// Verify the signature
boolean isValid = CIP30DataSigner.INSTANCE.verifyDataSignature(
    addressBytes,
    payload,
    dataSignature
);

if (isValid) {
    System.out.println("Signature is valid");
} else {
    System.out.println("Signature verification failed");
}
```

### Working with DataSignature Objects

```java
// Create DataSignature
DataSignature dataSignature = new DataSignature(signatureHex, keyHex);

// Get address from signature
byte[] addressFromSignature = dataSignature.address();

// Get curve identifier
Integer crv = dataSignature.crv();

// Get algorithm identifier
Integer alg = dataSignature.alg();

// Verify address matches
boolean addressMatches = Arrays.equals(addressBytes, addressFromSignature);
```

### Signing with Hash Payload

```java
// Sign with payload hashing (for large payloads)
DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(
    addressBytes,
    largePayload,
    signerAccount,
    true // hashPayload = true
);
```

### UTXO Management (CIP30 UtxoSupplier)

```java
// Create CIP30 UTXO supplier
CIP30UtxoSupplier utxoSupplier = new CIP30UtxoSupplier();

// Set UTXO data
List<Utxo> utxos = getUtxosFromWallet();
utxoSupplier.setUtxos(utxos);

// Use in transaction building
Tx tx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(10))
    .from(senderAddress)
    .withUtxoSupplier(utxoSupplier);
```

## API Reference

### CIP30DataSigner Class

Main class for CIP30 data signing and verification.

#### Methods

##### signData(byte[] addressBytes, byte[] payload, Account signer)
Signs data using CIP30's signData() method.

```java
public DataSignature signData(
    @NonNull byte[] addressBytes, 
    @NonNull byte[] payload, 
    @NonNull Account signer
) throws DataSignError
```

##### signData(byte[] addressBytes, byte[] payload, Account signer, boolean hashPayload)
Signs data with optional payload hashing.

```java
public DataSignature signData(
    @NonNull byte[] addressBytes,
    @NonNull byte[] payload,
    @NonNull Account signer,
    boolean hashPayload
) throws DataSignError
```

##### verifyDataSignature(byte[] addressBytes, byte[] payload, DataSignature dataSignature)
Verifies a CIP30 data signature.

```java
public boolean verifyDataSignature(
    byte[] addressBytes,
    byte[] payload,
    DataSignature dataSignature
)
```

### DataSignature Class

Represents a CIP30 data signature.

#### Constructor
```java
// Create from signature and key hex strings
public DataSignature(@NonNull String signature, @NonNull String key)
```

#### Methods
```java
// Set signature hex string
public DataSignature signature(@NonNull String signature)

// Set key hex string
public DataSignature key(@NonNull String key)

// Get address bytes from signature
public byte[] address()

// Get curve identifier
public Integer crv()

// Get algorithm identifier
public Integer alg()
```

### CIP30UtxoSupplier Class

UTXO supplier implementation for CIP30 wallets.

#### Methods
```java
// Set UTXOs
public void setUtxos(List<Utxo> utxos)

// Get UTXOs for address
public List<Utxo> getUtxos(String address)
```

## CIP30 Specification Details

### Metadata Label
CIP30 uses metadata label **674** for data signing metadata.

### Signature Format
CIP30 signatures follow COSE Sign1 format with:
- **Address Header**: Address bytes in unprotected headers (-100)
- **Content Type**: Cardano message content type (-1000)
- **Algorithm**: EdDSA algorithm (14)
- **Curve**: Ed25519 curve (-8)

### Data Signing Process
1. **Payload Preparation**: Convert data to bytes (UTF-8 for text)
2. **Header Creation**: Create COSE headers with address and content type
3. **Signature Generation**: Sign using EdDSA with Ed25519 curve
4. **Verification**: Verify signature against public key

### Constants
The API uses the following constants defined in `CIP30Constant`:

- **ADDRESS_KEY**: Header key for address (-100)
- **CONTENT_TYPE**: Content type for Cardano messages (-1000)
- **ALGORITHM_ID**: EdDSA algorithm identifier (14)
- **CURVE_ID**: Ed25519 curve identifier (-8)

## Best Practices

1. **Validate addresses**: Always verify that signature addresses match expected addresses
2. **Handle errors**: Use try-catch blocks for DataSignError exceptions
3. **Hash large payloads**: Use payload hashing for large data to improve performance
4. **Verify signatures**: Always verify signatures before processing signed data
5. **Use proper encoding**: Ensure payload is properly encoded (UTF-8 for text)

## Integration with Other Standards

### With CIP8 (Message Signing)
CIP30 builds on CIP8's COSE signature standard:

```java
// CIP30 uses CIP8 COSE signatures internally
DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(
    addressBytes, 
    payload, 
    signerAccount
);

// The signature contains CIP8 COSE Sign1 data
String signatureHex = dataSignature.signature();
COSESign1 coseSign1 = COSESign1.deserialize(HexUtil.decodeHexString(signatureHex));
```

### With dApp Wallets
```java
// Typical dApp integration pattern
public class DAppConnector {
    private CIP30DataSigner signer = CIP30DataSigner.INSTANCE;
    
    public String requestSignature(String message, Account userAccount) {
        try {
            byte[] addressBytes = userAccount.baseAddress().getBytes();
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            
            DataSignature signature = signer.signData(addressBytes, payload, userAccount);
            return signature.signature();
        } catch (DataSignError e) {
            throw new RuntimeException("Failed to sign data", e);
        }
    }
}
```

For more information about CIP30, refer to the [official CIP30 specification](https://cips.cardano.org/cips/cip30/).
