---
title: "CIP8 API"
description: "Message Signing with COSE Signatures implementation"
sidebar_position: 3
---

# CIP8 API

CIP8 (Cardano Improvement Proposal 8) defines a standard for message signing using COSE (CBOR Object Signing and Encryption) signatures. The Cardano Client Library provides APIs to create, sign, and verify messages according to the CIP8 specification.

## Key Features

- **COSE Signatures**: Full COSE Sign1 and COSE Sign support
- **Message Signing**: Sign arbitrary messages with private keys
- **Signature Verification**: Verify signatures against public keys
- **Header Management**: Flexible protected and unprotected header handling
- **Standard Compliance**: Full CIP8 specification support

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip8
- **Dependencies**: common, crypto

## Usage Examples

### Creating COSE Sign1 Signatures

The following example shows how to create a COSE Sign1 signature for a message using the CIP8 API.

```java
// Create headers for the signature
HeaderMap protectedHeaders = new HeaderMap()
    .algorithmId(14) // EdDSA algorithm
    .contentType(-1000); // Cardano message content type

HeaderMap unprotectedHeaders = new HeaderMap()
    .addOtherHeader(-100, "Some header value");

Headers headers = new Headers(protectedHeaders, unprotectedHeaders);

// Create the message payload
String message = "Hello World";
byte[] payload = message.getBytes(StandardCharsets.UTF_8);

// Create COSE Sign1 builder
COSESign1Builder builder = new COSESign1Builder(headers, payload, false);
builder.hashPayload(); // Hash the payload with Blake2b-224

// Create signature structure for signing
SigStructure sigStructure = builder.makeDataToSign();

// Sign with private key
byte[] signature = privateKey.sign(sigStructure.toBytes());

// Build the final COSE Sign1 object
COSESign1 coseSign1 = builder.build(signature);

// Serialize to bytes for transmission
byte[] serialized = coseSign1.toBytes();
String hexSignature = HexUtil.encodeHexString(serialized);
```

### Creating COSE Sign Signatures (Multiple Signers)

The following example shows how to create a COSE Sign signature with multiple signers.

```java
// Create headers for the signature
HeaderMap protectedHeaders = new HeaderMap()
    .algorithmId(14) // EdDSA algorithm
    .contentType(-1000); // Cardano message content type

Headers headers = new Headers(protectedHeaders, new HeaderMap());

// Create the message payload
String message = "Multi-signer message";
byte[] payload = message.getBytes(StandardCharsets.UTF_8);

// Create COSE Sign builder
COSESignBuilder builder = new COSESignBuilder(headers, payload, false);
builder.hashPayload(); // Hash the payload with Blake2b-224

// Create signature structure for signing
SigStructure sigStructure = builder.makeDataToSign();

// Sign with multiple private keys
List<COSESignature> signatures = new ArrayList<>();
for (PrivateKey privateKey : privateKeys) {
    byte[] signature = privateKey.sign(sigStructure.toBytes());
    COSESignature coseSignature = new COSESignature()
        .signature(signature);
    signatures.add(coseSignature);
}

// Build the final COSE Sign object
COSESign coseSign = builder.build(signatures);

// Serialize to bytes for transmission
byte[] serialized = coseSign.toBytes();
```

### Signature Verification

The following example shows how to verify a COSE signature.

```java
// Deserialize the signature from bytes
COSESign1 coseSign1 = COSESign1.deserialize(signatureBytes);

// Verify the signature
boolean isValid = COSEUtil.verifyCoseSign1(coseSign1, publicKey);

if (isValid) {
    System.out.println("Signature is valid");
    
    // Extract the payload
    byte[] payload = coseSign1.payload();
    String message = new String(payload, StandardCharsets.UTF_8);
    System.out.println("Message: " + message);
} else {
    System.out.println("Signature verification failed");
}
```

### Working with Headers

The following example shows how to work with protected and unprotected headers.

```java
// Create protected headers
HeaderMap protectedHeaders = new HeaderMap()
    .algorithmId(14) // EdDSA algorithm
    .contentType(-1000) // Cardano message content type
    .keyId("key-123"); // Key identifier

// Create unprotected headers
HeaderMap unprotectedHeaders = new HeaderMap()
    .addOtherHeader(-100, "Custom header value")
    .addOtherHeader(-101, "Another custom value");

// Combine into Headers object
Headers headers = new Headers(protectedHeaders, unprotectedHeaders);

// Access header values
Integer algorithmId = headers._protected().algorithmId();
Integer contentType = headers._protected().contentType();
String keyId = headers._protected().keyId();

// Access unprotected header values
Object customValue = headers.unprotected().getOtherHeader(-100);
```

### Creating COSE Keys

The following example shows how to create and work with COSE keys.

```java
// Create a COSE key from a public key
COSEKey coseKey = new COSEKey()
    .keyType(1) // Octet Key Pair
    .algorithm(14) // EdDSA
    .crv(-8) // Ed25519 curve
    .x(publicKeyBytes); // Public key bytes

// Serialize the key
byte[] keyBytes = coseKey.toBytes();
String keyHex = HexUtil.encodeHexString(keyBytes);

// Deserialize a COSE key
COSEKey deserializedKey = COSEKey.deserialize(HexUtil.decodeHexString(keyHex));

// Extract public key bytes
byte[] extractedPublicKey = deserializedKey.x();
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
COSESign1Builder hashPayload() // Hash payload with Blake2b-224
```

#### COSESignBuilder
Builder class for creating COSE Sign signatures with multiple signers.

```java
// Constructor
COSESignBuilder(Headers headers, byte[] payload, boolean isPayloadExternal)

// Methods
SigStructure makeDataToSign() // Create signature structure
COSESign build(List<COSESignature> signatures) // Build final signature
COSESignBuilder hashPayload() // Hash payload with Blake2b-224
```

#### Headers
Manages protected and unprotected headers.

```java
// Constructor
Headers(HeaderMap protectedHeaders, HeaderMap unprotectedHeaders)

// Methods
HeaderMap _protected() // Get protected headers
HeaderMap unprotected() // Get unprotected headers
Headers copy() // Create a copy
```

#### HeaderMap
Manages individual header maps.

```java
// Methods
HeaderMap algorithmId(Integer algorithmId) // Set algorithm ID
HeaderMap contentType(Integer contentType) // Set content type
HeaderMap keyId(String keyId) // Set key ID
HeaderMap addOtherHeader(Integer key, Object value) // Add custom header
Integer algorithmId() // Get algorithm ID
Integer contentType() // Get content type
String keyId() // Get key ID
Object getOtherHeader(Integer key) // Get custom header
```

#### COSEUtil
Utility class for signature verification.

```java
// Static methods
boolean verifyCoseSign1(COSESign1 coseSign1, PublicKey publicKey)
boolean verifyCoseSign(COSESign coseSign, List<PublicKey> publicKeys)
```

## CIP8 Specification Details

### COSE Sign1 Structure
CIP8 uses COSE Sign1 format with the following structure:
- **Protected Headers**: Algorithm ID, content type, key ID
- **Unprotected Headers**: Custom headers, address information
- **Payload**: Message data (optionally hashed with Blake2b-224)
- **Signature**: EdDSA signature over the structure

### Signature Process
1. **Header Creation**: Create protected and unprotected headers
2. **Payload Preparation**: Optionally hash payload with Blake2b-224
3. **Structure Building**: Create COSE Sign1 structure
4. **Signing**: Sign using EdDSA with Ed25519 curve
5. **Serialization**: Convert to CBOR bytes

### Algorithm Identifiers
The following algorithm identifiers are commonly used:

- **14**: EdDSA (Edwards Curve Digital Signature Algorithm)
- **-8**: Ed25519 curve identifier
- **1**: Octet Key Pair key type
- **-1000**: Cardano message content type

### Hash Algorithm
- **Blake2b-224**: Used for payload hashing (224-bit output)
- **Purpose**: Reduce payload size for large messages

## Best Practices

1. **Always hash payloads** for large messages using `hashPayload()` method
2. **Use appropriate algorithm IDs** based on your key type (EdDSA = 14 for Ed25519)
3. **Include key identifiers** in protected headers for key management
4. **Validate signatures** before processing signed messages
5. **Handle serialization errors** gracefully when deserializing signatures

## Advanced CIP8 Features

### COSE Encryption

CIP8 also provides COSE encryption capabilities:

#### COSEEncrypt
```java
// Create COSE encryption object
COSEEncrypt coseEncrypt = new COSEEncrypt()
    .headers(headers)
    .ciphertext(encryptedData)
    .recipients(recipients);
```

#### Password-Based Encryption
```java
// Create password-based encryption
PasswordEncryption passwordEncryption = new PasswordEncryption()
    .password("your-password")
    .salt(saltBytes);
```

#### Public Key Encryption
```java
// Create public key encryption
PubKeyEncryption pubKeyEncryption = new PubKeyEncryption()
    .publicKey(publicKeyBytes)
    .algorithm(algorithmId);
```

### COSE Recipients

```java
// Create COSE recipient
COSERecipient recipient = new COSERecipient()
    .headers(recipientHeaders)
    .encryptedKey(encryptedKeyBytes);
```

## Integration with Other CIPs

CIP8 is commonly used with other Cardano standards:

- **CIP30**: Uses CIP8 for dApp wallet data signing
- **CIP25**: Can include CIP8 signatures in NFT metadata
- **CIP68**: Can include CIP8 signatures in datum metadata

For more information about CIP8, refer to the [official CIP8 specification](https://cips.cardano.org/cips/cip8/).
