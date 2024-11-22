## Governance Module (cardano-client-governance)

- CIP 105 [Conway era Key Chains for HD Wallets](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0105)

### New Credentials

1. drep_credential
2. committee_cold_credential
3. committee_hot_credential

### New Keys

1. DRep Keys
2. Constitutional Committee Cold Keys
3. Constitutional Committee Hot Keys

**DRep Keys :** DReps to be identified on-chain, in DRep registrations, retirements, votes, and in vote delegations from ada holders. 

**Constitutional Committee Cold Keys :** Constitutional committee members can be recognized by their cold credentials
within update committee governance actions, authorize hot credential certificate and resign cold key certificates.

**Constitutional Committee Hot Keys :** Constitutional committee hot credential can be observed within the authorize hot key certificate and votes.

All the above keys can be generated from public key digests or script hashes.

### DRep Keys

Role = 3

```shell
m / 1852' / 1815' / account' / 3 / address_index
```

A maximum of one set of DRep keys should be associated with one wallet account, which can be achieved by setting
address_index=0.

#### DRep ID
A DRep ID (drep_credential) can be generated from the Ed25519 public DRep key (without chaincode) by creating a blake2b-224
hash digest of the key. As this is key-based credential it should be marked as entry 0 in a credential array.

### Constitutional Committee Cold Keys

Role = 4

```shell
m / 1852' / 1815' / account' / 4 / address_index
```

A maximum of one set of constitutional committee cold keys should be associated with one wallet account, which can be 
achieved by setting address_index=0.

#### Constitutional Committee Cold Credential

A constitutional committee cold credential (committee_cold_credential) can be generated from the Ed25519 public constitutional committee 
cold key (without chaincode) by creating a blake2b-224 hash digest of the key. As this is key-based credential it should 
be marked as entry 0 in a credential array.

### Constitutional Committee Hot Keys

Role = 5

```shell
m / 1852' / 1815' / account' / 5 / address_index
```

A maximum of one set of constitutional committee hot keys should be associated with one wallet account, which can be achieved 
by setting address_index=0.

#### Constitutional Committee Hot Credential

A constitutional committee hot credential (committee_hot_credential) can be generated from the Ed25519 public constitutional
committee hot key (without chaincode) by creating a blake2b-224 hash digest of the key. As this is key-based credential 
it should be marked as entry 0 in a credential array.


### Bech32 Encoding

#### DRep Keys

DRep keys and DRep IDs should be encoded in Bech32 with the following prefixes:

| Prefix        | Meaning                                                 | Contents                                                          |
| ------------- | --------------------------------------------------------| ----------------------------------------------------------------- |
| `drep_sk`     | CIP-1852’s DRep signing key                             | Ed25519 private key                                               |
| `drep_vk`     | CIP-1852’s DRep verification key                        | Ed25519 public key                                                |
| `drep_xsk`    | CIP-1852’s DRep extended signing key                    | Ed25519-bip32 extended private key                                |
| `drep_xvk`    | CIP-1852’s DRep extended verification key               | Ed25519 public key with chain code                                |
| `drep`        | Delegate representative verification key hash (DRep ID) | blake2b\_224 digest of a delegate representative verification key |
| `drep_script` | Delegate representative script hash (DRep ID)        | blake2b\_224 digest of a serialized delegate representative script |

#### Constitutional Committee Cold Keys

Constitutional cold keys and credential should be encoded in Bech32 with the following prefixes:

| Prefix           | Meaning                                                               | Contents                                                                 |
| ---------------- | --------------------------------------------------------------------- |--------------------------------------------------------------------------|
| `cc_cold_sk`     | CIP-1852’s constitutional committee cold signing key                  | Ed25519 private key                                                      |
| `cc_cold_vk`     | CIP-1852’s constitutional committee verification signing key          | Ed25519 private key                                                      |
| `cc_cold_xsk`    | CIP-1852’s constitutional committee cold extended signing key         | Ed25519-bip32 extended private key                                       |
| `cc_cold_xvk`    | CIP-1852’s constitutional committee extended verification signing key | Ed25519 public key with chain code                                       |
| `cc_cold`        | Constitutional committee cold verification key hash (cold credential) | blake2b\_224 digest of a constitutional committee cold verification key  |
| `cc_cold_script` | Constitutional committee cold script hash (cold credential)           | blake2b\_224 digest of a serialized constitutional committee cold script |

#### Constitutional Committee Hot Keys

Constitutional hot keys and credential should be encoded in Bech32 with the following prefixes:

| Prefix          | Meaning                                                               | Contents                                                              |
| --------------- | --------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `cc_hot_sk`     | CIP-1852’s constitutional committee hot signing key                   | Ed25519 private key                                                   |
| `cc_hot_vk`     | CIP-1852’s constitutional committee verification signing key          | Ed25519 private key                                                   |
| `cc_hot_xsk`    | CIP-1852’s constitutional committee hot extended signing key          | Ed25519-bip32 extended private key                                    |
| `cc_hot_xvk`    | CIP-1852’s constitutional committee extended verification signing key | Ed25519 public key with chain code                                    |
| `cc_hot`        | Constitutional committee hot verification key hash (hot credential)   | blake2b\_224 digest of a constitutional committee hot verification key |
| `cc_hot_script` | Constitutional committee hot script hash (hot credential)             | blake2b\_224 digest of a serialized constitutional committee hot script |
