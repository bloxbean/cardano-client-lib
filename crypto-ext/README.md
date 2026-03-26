# Crypto Extensions (cardano-client-crypto-ext)

> **Experimental:** This module is currently experimental and primarily created to be used in
> [Yaci DevKit](https://github.com/bloxbean/yaci-devkit) devnet mode. APIs may change without notice.

Provides pure-Java implementations of VRF (Verifiable Random Function) and KES (Key Evolving Signature)
cryptographic primitives used in Cardano's Ouroboros Praos consensus protocol.

## Features

### VRF (Verifiable Random Function)
- ECVRF-ED25519-SHA512-Elligator2 implementation per IETF draft-irtf-cfrg-vrf-06
- Two independent implementations: BouncyCastle-based (`BcVrfProver`/`BcVrfVerifier`) and i2p-based (`EcVrfVerifier`)
- Cardano-specific VRF input construction (`CardanoVrfInput`) for both Praos and TPraos
- Leader eligibility check (`CardanoLeaderCheck`) combining VRF verification with Praos threshold comparison

### KES (Key Evolving Signature)
- Sum6 composition KES scheme (depth 6, 64 periods) matching Cardano node behavior
- Operational certificate (`OpCert`) parsing from TextEnvelope JSON and raw CBOR
- Sign and verify with forward-secure key evolution

### Utilities
- `BlockProducerKeys` - Load VRF skey, KES skey, and operational certificate from cardano-node file formats
- `CryptoExtConfiguration` - Pluggable provider configuration for VRF and KES implementations

## Usage

It is recommended to access VRF and KES implementations through `CryptoExtConfiguration` rather than
instantiating them directly. This way, if the underlying implementation changes in a future release,
your code will continue to work without modification.

```java
// Load block producer keys
BlockProducerKeys keys = BlockProducerKeys.load(
        Path.of("delegate1.vrf.skey"),
        Path.of("delegate1.kes.skey"),
        Path.of("opcert1.cert")
);

// Get VRF prover and verifier through CryptoExtConfiguration
VrfProver prover = CryptoExtConfiguration.INSTANCE.getVrfProver();
VrfVerifier verifier = CryptoExtConfiguration.INSTANCE.getVrfVerifier();

byte[] alpha = CardanoVrfInput.mkInputVrf(slot, epochNonce);
byte[] proof = prover.prove(keys.getVrfSkey(), alpha);

byte[] vrfVkey = Arrays.copyOfRange(keys.getVrfSkey(), 32, 64);
VrfResult result = verifier.verify(vrfVkey, proof, alpha);

// Leader eligibility check
byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(result.getOutput());
boolean eligible = CardanoLeaderCheck.checkLeaderValue(leaderValue, sigma, activeSlotCoeff);

// Get KES signer and verifier through CryptoExtConfiguration
KesSigner signer = CryptoExtConfiguration.INSTANCE.getKesSigner();
KesVerifier kesVerifier = CryptoExtConfiguration.INSTANCE.getKesVerifier();

byte[] signature = signer.sign(keys.getKesSkey(), headerBodyHash, kesPeriod);
byte[] kesVk = keys.getOpCert().getKesVkey();
boolean valid = kesVerifier.verify(signature, headerBodyHash, kesVk, kesPeriod);
```

**Dependencies:** crypto, common
