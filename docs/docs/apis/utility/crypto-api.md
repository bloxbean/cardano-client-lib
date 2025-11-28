# Crypto API

The Crypto API provides key generation, signing, hashing, encoding helpers, and CIP-1852 HD derivation utilities used across the Cardano Client Library.

## Overview

- Key generation: Ed25519 keys and key pairs
- Mnemonic management: BIP39 generation and validation
- HD derivation: CIP-1852 paths for Cardano
- Sign/verify: Ed25519/BIP32-Ed25519 signing
- Hashing: Blake2b helpers
- Encoding: Bech32 and hex utilities

## Key Generation

```java
Keys keyPair = KeyGenUtil.generateKey();
SecretKey privateKey = keyPair.getSkey();
VerificationKey publicKey = keyPair.getVkey();

byte[] keyHashBytes = Blake2bUtil.blake2bHash224(publicKey.getBytes());
String keyHashHex = HexUtil.encodeHexString(keyHashBytes);

// Convenience: 28-byte key hash as hex
String keyHash = KeyGenUtil.getKeyHash(publicKey);
```

## Mnemonic Management

```java
// Generate mnemonic
String mnemonic24 = MnemonicUtil.generateNew(Words.TWENTY_FOUR);

// Validate mnemonic
MnemonicUtil.validateMnemonic(mnemonic24);

// Convert to entropy and seed
byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonic24);
byte[] seed = MnemonicCode.toSeed(Arrays.asList(mnemonic24.split(" ")), "");
```

Derive directly from entropy (e.g., when you already have raw bytes):

```java
byte[] entropyBytes = ...;
HdKeyPair rootFromEntropy = new HdKeyGenerator().getRootKeyPairFromEntropy(entropyBytes);
```

## CIP-1852 HD Derivation

Common derivation helpers (no `createAccountDerivationPath` factory exists):

- External address: `DerivationPath.createExternalAddressDerivationPath(index)`
- Internal address: `DerivationPath.createInternalAddressDerivationPath(index)`
- Stake address: `DerivationPath.createStakeAddressDerivationPath()`
- Account-specific variants: `createExternalAddressDerivationPathForAccount`, `createInternalAddressDerivationPathForAccount`, `createStakeAddressDerivationPathForAccount`
- Other roles: `createDRepKeyDerivationPathForAccount`, `createCommitteeColdKeyDerivationPathForAccount`, `createCommitteeHotKeyDerivationPathForAccount`

```java
CIP1852 cip1852 = new CIP1852();
String mnemonic = "...";

DerivationPath externalPath = DerivationPath.createExternalAddressDerivationPath(0);
HdKeyPair keyPair = cip1852.getKeyPairFromMnemonic(mnemonic, externalPath);

HdKeyPair rootKeyPair = cip1852.getRootKeyPairFromMnemonic(mnemonic);
```

### Advanced derivation options

```java
// Specify derivation type and passphrase
HdKeyPair ledgerPair = cip1852.getKeyPairFromMnemonic(
        mnemonic,
        "optional-passphrase",
        externalPath,
        Bip32Type.LEDGER);

HdKeyPair trezorRoot = cip1852.getRootKeyPairFromMnemonic(mnemonic, Bip32Type.TREZOR);
```

### Deriving from existing keys

```java
byte[] accountXprv = ...; // account private key (xprv)
DerivationPath rolePath = DerivationPath.createExternalAddressDerivationPathForAccount(0);

// Get child key pair from account key
HdKeyPair fromAccount = cip1852.getKeyPairFromAccountKey(accountXprv, rolePath);

byte[] accountXpub = ...; // account public key
HdPublicKey paymentPub = cip1852.getPublicKeyFromAccountPubKey(accountXpub, 0, 0); // role 0, index 0

byte[] rootKey = ...; // root private key bytes
HdKeyPair fromRoot = cip1852.getKeyPairFromRootKey(rootKey, rolePath);
```

## Signing and Verification

```java
SigningProvider signingProvider = new EdDSASigningProvider();

byte[] message = "Hello, Cardano!".getBytes();
byte[] sk = privateKey.getBytes();       // 32 bytes
byte[] xsk = new byte[64];               // extended private key if available

byte[] sig1 = signingProvider.sign(message, sk);
byte[] sig2 = signingProvider.signExtended(message, xsk); // 64-byte extended key

// Legacy overload (with explicit public key) remains for compatibility
byte[] sig3 = signingProvider.signExtended(message, xsk, publicKey.getBytes());

boolean isValid = signingProvider.verify(sig1, message, publicKey.getBytes());
```

Configure a custom provider:

```java
CryptoConfiguration.INSTANCE.setSigningProvider(new CustomSigningProvider());
SigningProvider provider = CryptoConfiguration.INSTANCE.getSigningProvider();
```

## Hash Functions

```java
byte[] input = "Hello, Cardano!".getBytes();

byte[] hash160 = Blake2bUtil.blake2bHash160(input);
byte[] hash224 = Blake2bUtil.blake2bHash224(input);
byte[] hash256 = Blake2bUtil.blake2bHash256(input);

String hex160 = HexUtil.encodeHexString(hash160);
```

## Encoding and Decoding

```java
// Bech32
String bech32Key = Bech32.encode("addr_vk", publicKey.getBytes());
Bech32.Bech32Data decoded = Bech32.decode(bech32Key);

// Hex
String hex = HexUtil.encodeHexString(new byte[]{0x01, 0x02});
byte[] bytes = HexUtil.decodeHexString(hex);
```

## Error Handling

Handle failures explicitly, e.g., invalid mnemonics or derivation errors:

```java
try {
    MnemonicUtil.validateMnemonic(mnemonic);
} catch (AddressRuntimeException e) {
    // invalid mnemonic
}

try {
    HdKeyPair keyPair = cip1852.getKeyPairFromMnemonic(mnemonic, externalPath);
} catch (CryptoException e) {
    // derivation failed
}
```

## Best Practices

- Never log or persist raw private keys or mnemonics; clear sensitive byte arrays after use.
- Validate mnemonics before derivation.
- Reuse signing providers for performance in hot paths.
- Pick the correct derivation helper for the role (external/internal/stake/drep/committee).
