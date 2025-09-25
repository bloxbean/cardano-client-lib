# Crypto API

The Crypto API provides cryptographic utilities and functions for the Cardano Client Library, including key generation, signing, hashing, and mnemonic management. These utilities support various cryptographic standards like BIP32, BIP39, and CIP1852.

## Overview

The Crypto API offers comprehensive cryptographic functionality:

- **Key Generation**: Generate Ed25519 keys and key pairs
- **Mnemonic Management**: Generate and validate BIP39 mnemonic phrases
- **Key Derivation**: Hierarchical deterministic key derivation (BIP32/CIP1852)
- **Digital Signatures**: Sign and verify messages using Ed25519
- **Hash Functions**: Blake2b hashing utilities
- **Encoding/Decoding**: Bech32 and hex encoding utilities

## Key Generation

### Generate Random Key Pairs

Generate new Ed25519 key pairs for signing:

```java
// Generate a new key pair
Keys keyPair = KeyGenUtil.generateKey();
SecretKey privateKey = keyPair.getSkey();
VerificationKey publicKey = keyPair.getVkey();

// Get key bytes
byte[] privateKeyBytes = privateKey.getBytes();
byte[] publicKeyBytes = publicKey.getBytes();
```

### Derive Public Key from Private Key

Derive the public key from an existing private key:

```java
SecretKey privateKey = SecretKey.create(privateKeyBytes);
VerificationKey publicKey = KeyGenUtil.getPublicKeyFromPrivateKey(privateKey);
```

### Generate Key Hash

Generate a Blake2b hash from a verification key:

```java
VerificationKey publicKey = keyPair.getVkey();
String keyHash = KeyGenUtil.getKeyHash(publicKey);
```

## Mnemonic Management

### Generate New Mnemonic Phrases

Generate BIP39 mnemonic phrases for wallet creation:

```java
// Generate 24-word mnemonic (default)
String mnemonic24 = MnemonicUtil.generateNew(Words.TWENTY_FOUR);

// Generate 15-word mnemonic
String mnemonic15 = MnemonicUtil.generateNew(Words.FIFTEEN);

// Generate 12-word mnemonic
String mnemonic12 = MnemonicUtil.generateNew(Words.TWELVE);
```

### Validate Mnemonic Phrases

Validate existing mnemonic phrases:

```java
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

try {
    MnemonicUtil.validateMnemonic(mnemonic);
    System.out.println("Mnemonic is valid");
} catch (AddressRuntimeException e) {
    System.err.println("Invalid mnemonic: " + e.getMessage());
}
```

### Convert Mnemonic to Entropy

Convert mnemonic phrases to entropy bytes:

```java
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

try {
    byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonic);
    String entropyHex = HexUtil.encodeHexString(entropy);
} catch (MnemonicException e) {
    System.err.println("Mnemonic conversion failed: " + e.getMessage());
}
```

### Convert Mnemonic to Seed

Generate seed from mnemonic with optional passphrase:

```java
List`<String>` mnemonicWords = Arrays.asList(mnemonic.split(" "));

// Without passphrase
byte[] seed = MnemonicCode.toSeed(mnemonicWords, "");

// With passphrase
byte[] seedWithPassphrase = MnemonicCode.toSeed(mnemonicWords, "mypassphrase");
```

## Hierarchical Deterministic Key Derivation

### CIP1852 Key Derivation

Generate keys following the CIP1852 standard for Cardano:

```java
CIP1852 cip1852 = new CIP1852();
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

// Create derivation path for account 0, address 0
DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPath(0);

// Get key pair from mnemonic
HdKeyPair keyPair = cip1852.getKeyPairFromMnemonic(mnemonic, derivationPath);

// Get root key pair
HdKeyPair rootKeyPair = cip1852.getRootKeyPairFromMnemonic(mnemonic);
```

### Manual Key Derivation

Use the HdKeyGenerator for custom key derivation:

```java
HdKeyGenerator keyGenerator = new HdKeyGenerator();

// Generate root key pair from entropy
byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonic);
HdKeyPair rootKeyPair = keyGenerator.getRootKeyPairFromEntropy(entropy);

// Derive account key pair
DerivationPath accountPath = DerivationPath.createAccountDerivationPath(0);
HdKeyPair accountKeyPair = keyGenerator.getAccountKeyPairFromSecretKey(
    rootKeyPair.getPrivateKey().getKeyData(), accountPath);
```

## Digital Signatures

### Sign Messages

Sign messages using Ed25519 private keys:

```java
SigningProvider signingProvider = new EdDSASigningProvider();

byte[] message = "Hello, Cardano!".getBytes();
byte[] privateKeyBytes = privateKey.getBytes();

// Sign message
byte[] signature = signingProvider.sign(message, privateKeyBytes);

// Sign with extended private key (64 bytes)
byte[] extendedPrivateKey = new byte[64]; // Your extended private key
byte[] extendedSignature = signingProvider.signExtended(message, extendedPrivateKey);
```

### Verify Signatures

Verify Ed25519 signatures:

```java
SigningProvider signingProvider = new EdDSASigningProvider();

byte[] message = "Hello, Cardano!".getBytes();
byte[] signature = /* signature bytes */;
byte[] publicKeyBytes = publicKey.getBytes();

// Verify signature
boolean isValid = signingProvider.verify(signature, message, publicKeyBytes);
if (isValid) {
    System.out.println("Signature is valid");
} else {
    System.out.println("Signature is invalid");
}
```

## Hash Functions

### Blake2b Hashing

Use Blake2b hash functions for various purposes:

```java
byte[] input = "Hello, Cardano!".getBytes();

// Blake2b-160 (20 bytes)
byte[] hash160 = Blake2bUtil.blake2bHash160(input);

// Blake2b-224 (28 bytes) - commonly used for key hashes
byte[] hash224 = Blake2bUtil.blake2bHash224(input);

// Blake2b-256 (32 bytes)
byte[] hash256 = Blake2bUtil.blake2bHash256(input);

// Convert to hex
String hash160Hex = HexUtil.encodeHexString(hash160);
String hash224Hex = HexUtil.encodeHexString(hash224);
String hash256Hex = HexUtil.encodeHexString(hash256);
```

## Encoding and Decoding

### Bech32 Encoding

Encode and decode Bech32 strings:

```java
// Encode bytes to Bech32
byte[] keyBytes = publicKey.getBytes();
String bech32Key = Bech32.encode("addr_vk", keyBytes);

// Decode Bech32 string
Bech32.Bech32Data decoded = Bech32.decode(bech32Key);
String hrp = decoded.hrp; // Human readable part
byte[] data = decoded.data; // Decoded bytes
```

### Hex Encoding

Convert between bytes and hex strings:

```java
byte[] bytes = {0x01, 0x02, 0x03, 0x04};

// Encode to hex string
String hexString = HexUtil.encodeHexString(bytes);

// Decode from hex string
byte[] decodedBytes = HexUtil.decodeHexString(hexString);
```

## Advanced Cryptographic Operations

### Custom Signing Provider

Implement custom signing logic:

```java
public class CustomSigningProvider implements SigningProvider {
    
    @Override
    public byte[] sign(byte[] message, byte[] privateKey) {
        // Implement custom signing logic
        Ed25519PrivateKeyParameters keyParams = new Ed25519PrivateKeyParameters(privateKey, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, keyParams);
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }
    
    @Override
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        // Implement custom verification logic
        Ed25519PublicKeyParameters keyParams = new Ed25519PublicKeyParameters(publicKey, 0);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, keyParams);
        verifier.update(message, 0, message.length);
        return verifier.verifySignature(signature);
    }
}
```

### Configure Crypto Provider

Configure the global crypto provider:

```java
// Set custom signing provider
CryptoConfiguration.INSTANCE.setSigningProvider(new CustomSigningProvider());

// Get current signing provider
SigningProvider currentProvider = CryptoConfiguration.INSTANCE.getSigningProvider();
```

## Derivation Path Utilities

### Create Standard Derivation Paths

Create CIP1852 compliant derivation paths:

```java
// External address derivation path: m/1852'/1815'/0'/0/0
DerivationPath externalPath = DerivationPath.createExternalAddressDerivationPath(0);

// Internal address derivation path: m/1852'/1815'/0'/1/0  
DerivationPath internalPath = DerivationPath.createInternalAddressDerivationPath(0);

// Stake address derivation path: m/1852'/1815'/0'/2/0
DerivationPath stakePath = DerivationPath.createStakeAddressDerivationPath();

// Account derivation path: m/1852'/1815'/0'
DerivationPath accountPath = DerivationPath.createAccountDerivationPath(0);
```

### Custom Derivation Paths

Create custom derivation paths:

```java
DerivationPath customPath = new DerivationPath()
    .purpose(new Purpose(1852, true))    // m/1852'
    .coinType(new CoinType(1815, true))  // m/1852'/1815'
    .account(new Account(0, true))       // m/1852'/1815'/0'
    .role(new Role(0, false))           // m/1852'/1815'/0'/0
    .index(new Index(5, false));        // m/1852'/1815'/0'/0/5
```

## Error Handling

Handle cryptographic errors properly:

```java
try {
    // Key generation
    Keys keyPair = KeyGenUtil.generateKey();
} catch (CborSerializationException e) {
    System.err.println("Key generation failed: " + e.getMessage());
}

try {
    // Mnemonic validation
    MnemonicUtil.validateMnemonic(invalidMnemonic);
} catch (AddressRuntimeException e) {
    System.err.println("Invalid mnemonic: " + e.getMessage());
}

try {
    // Key derivation
    CIP1852 cip1852 = new CIP1852();
    HdKeyPair keyPair = cip1852.getKeyPairFromMnemonic(mnemonic, derivationPath);
} catch (CryptoException e) {
    System.err.println("Key derivation failed: " + e.getMessage());
}
```

## Best Practices

### Secure Key Storage

```java
// Generate keys securely
SecureRandom secureRandom = new SecureRandom();
byte[] randomBytes = new byte[32];
secureRandom.nextBytes(randomBytes);

// Clear sensitive data after use
Arrays.fill(privateKeyBytes, (byte) 0);
Arrays.fill(mnemonicBytes, (byte) 0);
```

### Mnemonic Handling

```java
// Always validate mnemonics
public boolean isValidMnemonic(String mnemonic) {
    try {
        MnemonicUtil.validateMnemonic(mnemonic);
        return true;
    } catch (AddressRuntimeException e) {
        return false;
    }
}

// Use secure random for mnemonic generation
String secureMnemonic = MnemonicUtil.generateNew(Words.TWENTY_FOUR);
```

### Performance Considerations

```java
// Cache signing provider for repeated operations
private static final SigningProvider SIGNING_PROVIDER = new EdDSASigningProvider();

// Reuse key objects when possible
private static final CIP1852 CIP1852_INSTANCE = new CIP1852();
```

The Crypto API provides all the essential cryptographic building blocks needed for secure Cardano application development, following industry standards and best practices.
