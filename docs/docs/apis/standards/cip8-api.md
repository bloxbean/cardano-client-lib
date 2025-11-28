---
title: "CIP8 API"
description: "Message Signing with COSE Signatures implementation"
sidebar_position: 3
---

# CIP8 API

CIP8 (Cardano Improvement Proposal 8) defines a standard for message signing using COSE (CBOR Object Signing and Encryption) signatures. The Cardano Client Library exposes builders and models for COSE Sign1 and COSE Sign so you can create and verify signatures that follow the specification. The focus is on providing small, composable pieces: you assemble headers, build a `SigStructure`, sign with your own signing provider, and serialize/deserialise with the COSE models.

## Key Features

- **COSE Sign1 and Sign** builders
- **Payload hashing** toggle (Blake2b-224)
- **Header helpers** for protected and unprotected maps
- **COSE key utilities** for crv/x headers and serialization

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip8
- **Dependencies**: common, crypto

## Usage Examples

### Creating COSE Sign1 Signatures

```java
// Protected headers (algorithm + optional content type or key id)
HeaderMap protectedHeaders = new HeaderMap()
        .algorithmId(14) // EdDSA
        .contentType(-1000); // Optional content type

// Unprotected headers for custom values
HeaderMap unprotectedHeaders = new HeaderMap()
        .addOtherHeader(-100, "Some header value");

Headers headers = new Headers()
        ._protected(new ProtectedHeaderMap(protectedHeaders))
        .unprotected(unprotectedHeaders);

byte[] payload = "Hello World".getBytes(StandardCharsets.UTF_8);

COSESign1Builder builder = new COSESign1Builder(headers, payload, false)
        .hashed(true); // Blake2b-224 hashing of payload

SigStructure sigStructure = builder.makeDataToSign();
byte[] signature = signingProvider.sign(sigStructure.serializeAsBytes(), privateKeyBytes);

COSESign1 coseSign1 = builder.build(signature);
byte[] serialized = coseSign1.serializeAsBytes();
String hexSignature = HexUtil.encodeHexString(serialized);
```

### Creating COSE Sign Signatures (Multiple Signers)

```java
Headers headers = new Headers()
        ._protected(new ProtectedHeaderMap(new HeaderMap().algorithmId(14)))
        .unprotected(new HeaderMap());

byte[] payload = "Multi-signer message".getBytes(StandardCharsets.UTF_8);

COSESignBuilder builder = new COSESignBuilder(headers, payload, false)
        .hashed(true);

SigStructure sigStructure = builder.makeDataToSign();
List<COSESignature> signatures = new ArrayList<>();
for (byte[] pvtKey : signerPrivateKeys) {
    byte[] sig = signingProvider.sign(sigStructure.serializeAsBytes(), pvtKey);
    signatures.add(new COSESignature().signature(sig));
}

COSESign coseSign = builder.build(signatures);
byte[] serialized = coseSign.serializeAsBytes();
```

### Signature Verification

`COSESign1` exposes `signedData()` to reconstruct the SigStructure for verification.

```java
COSESign1 coseSign1 = COSESign1.deserialize(signatureBytes);
SigStructure signedData = coseSign1.signedData();

boolean isValid = signingProvider.verify(
        coseSign1.signature(),
        signedData.serializeAsBytes(),
        publicKeyBytes);

byte[] payload = coseSign1.payload();
```

### Working with Headers

```java
Headers headers = coseSign1.headers();
HeaderMap protectedHeaders = headers._protected().getAsHeaderMap();
HeaderMap unprotectedHeaders = headers.unprotected();

Object algorithmId = protectedHeaders.algorithmId();
Object contentType = protectedHeaders.contentType();
byte[] keyId = protectedHeaders.keyId();
byte[] customValue = unprotectedHeaders.otherHeaderAsBytes(-100);
```

### Creating COSE Keys

`COSEKey` stores standard fields plus arbitrary other headers (e.g., crv, x).

```java
COSEKey coseKey = new COSEKey()
        .keyType(1) // Octet Key Pair
        .algorithmId(14) // EdDSA
        .addOtherHeader(-1, new UnsignedInteger(6)) // crv (Ed25519)
        .addOtherHeader(-2, new ByteString(publicKeyBytes));

byte[] keyBytes = coseKey.serializeAsBytes();
COSEKey deserializedKey = COSEKey.deserialize(keyBytes);
byte[] extractedPublicKey = deserializedKey.otherHeaderAsBytes(-2);
```

## API Reference

### Core Classes

#### COSESign1Builder
Builder class for creating COSE Sign1 signatures.

```java
// Constructor
COSESign1Builder(Headers headers, byte[] payload, boolean isPayloadExternal)

// Methods
SigStructure makeDataToSign() // Create signature structure
COSESign1 build(byte[] signature) // Build final signature
COSESign1Builder hashed(boolean hashed) // Enable Blake2b-224 hashing
```

#### COSESignBuilder
Builder class for creating COSE Sign signatures with multiple signers.

```java
// Constructor
COSESignBuilder(Headers headers, byte[] payload, boolean isPayloadExternal)

// Methods
SigStructure makeDataToSign() // Create signature structure
COSESign build(List<COSESignature> signatures) // Build final signature
COSESignBuilder hashed(boolean hashed) // Enable Blake2b-224 hashing
```

#### Headers
Manages protected and unprotected headers.

```java
Headers() // use fluent setters

// Methods
Headers _protected(ProtectedHeaderMap protectedHeaders)
Headers unprotected(HeaderMap unprotectedHeaders)
HeaderMap _protected().getAsHeaderMap() // Decode protected map
HeaderMap unprotected() // Get unprotected headers
Headers copy() // Create a copy
```

#### HeaderMap
Manages individual header maps.

```java
// Methods
HeaderMap algorithmId(long or String algorithmId)
HeaderMap contentType(long or String contentType)
HeaderMap keyId(byte[] keyId)
HeaderMap addOtherHeader(long key, Object value) // Custom header
Object algorithmId()
Object contentType()
byte[] keyId()
byte[] otherHeaderAsBytes(long key)
long otherHeaderAsLong(long key)
String otherHeaderAsString(long key)
```

#### COSEKey
Represents a COSE key map.

```java
COSEKey keyType(long or String)
COSEKey algorithmId(long or String)
COSEKey addOtherHeader(long key, DataItem value)
COSEKey addOtherHeader(long key, long/String/BigInteger/byte[] value)
byte[] serializeAsBytes()
COSEKey deserialize(byte[] bytes)
byte[] otherHeaderAsBytes(long key)
long otherHeaderAsLong(long key)
String otherHeaderAsString(long key)
```

## CIP8 Specification Details

### COSE Sign1 Structure
Components describe exactly what is signed and carried in the CBOR array:
- **Protected Headers**: Algorithm ID, content type, key ID
- **Unprotected Headers**: Custom headers
- **Payload**: Message data (optionally hashed with Blake2b-224)
- **Signature**: EdDSA signature over the structure

### Signature Process
1. **Header Creation**: Set protected/unprotected headers with algorithm, optional content type, and custom keys.
2. **Payload Preparation**: Optionally hash payload with Blake2b-224 (`hashed(true)`).
3. **Structure Building**: `makeDataToSign()` yields the Sig_Structure the signer must sign.
4. **Signing**: Sign the serialized Sig_Structure using EdDSA with your signing provider.
5. **Serialization**: Build `COSESign1` and encode via `serializeAsBytes()`.

### Algorithm Identifiers

- **14**: EdDSA (Edwards Curve Digital Signature Algorithm)
- **1**: Octet Key Pair key type

## Advanced CIP8 Features

### COSE Encryption

Encryption helpers are available as low-level containers:

- `COSEEncrypt` holds headers, ciphertext, and recipients.
- `PasswordEncryption` wraps a `COSEEncrypt0` instance (tag 16).
- `PubKeyEncryption` wraps a `COSEEncrypt` instance (tag 96).

You construct the underlying `COSEEncrypt`/`COSEEncrypt0` yourself and then wrap it if you need the tagged form.

## Integration with Other CIPs

- **CIP30**: Uses CIP8 for dApp wallet data signing
- **CIP25**: Can include CIP8 signatures in NFT metadata
- **CIP68**: Can include CIP8 signatures in datum metadata

For more information about CIP8, refer to the [official CIP8 specification](https://cips.cardano.org/cips/cip8/).
