---
description: Address Api
sidebar_label: Address Api
sidebar_position: 1
---

# Overview

This document provides various address related apis in Cardano Client Lib.

## Address

The `Address` class represents a Shelley address in the Cardano blockchain. It provides various methods to create, and retrieve
information from a Shelley address. This class supports addresses encoded in Bech32 format and provides utilities for extracting address
credentials, such as payment and delegation credentials.

**Note:** For Byron addresses, use the `ByronAddress` class

### Key Features

- **Address Creation**: Create an `Address` instance from a Bech32 encoded string, a byte array, or by providing an address prefix and bytes.
- **Address Encoding**: Convert the address to a Bech32 encoded string.
- **Credential Extraction**: Retrieve payment and delegation credentials from the address, including their types (PubKeyHash or ScriptHash).
- **Address Type and Network Information**: Determine the address type and network associated with the address.

### Public Constructors

- **`Address(String prefix, byte[] bytes)`**  
  Creates an `Address` instance from the given prefix and byte array.

- **`Address(String address)`**  
  Creates an `Address` instance from a Bech32 encoded address.

- **`Address(byte[] addressBytes)`**  
  Creates an `Address` instance from a byte array representing the address.

### Public Methods

#### Address Encoding and Prefix

- **`String toBech32()`**  
  Returns the Bech32 encoded address.

- **`String getPrefix()`**  
  Returns the prefix of the address, which indicates the type and network.

- **`String getAddress()`**  
  Returns the Bech32 encoded address. This is a convenience method that calls `toBech32()`.

#### Address Type and Network

- **`AddressType getAddressType()`**  
  Returns the `AddressType`, which indicates whether the address is a base, pointer, enterprise, or reward address.

- **`Network getNetwork()`**  
  Returns the `Network` associated with the address, such as Mainnet or Testnet.

#### Credential Extraction

- **`Optional<byte[]> getDelegationCredentialHash()`**  
  Retrieves the StakeKeyHash or ScriptHash from the delegation part of the address. For pointer addresses, it returns the delegationPointerHash.

- **`Optional<byte[]> getPaymentCredentialHash()`**  
  Retrieves the payment key hash or script hash from the address.

- **`Optional<Credential> getDelegationCredential()`**  
  Retrieves the delegation credential from the address, if present.

- **`Optional<Credential> getPaymentCredential()`**  
  Retrieves the payment credential from the address, if present.

#### Address Part Identification

- **`boolean isPubKeyHashInPaymentPart()`**  
  Checks if the payment part of the address is a PubKeyHash. Returns `true` if it is, otherwise `false`.

- **`boolean isScriptHashInPaymentPart()`**  
  Checks if the payment part of the address is a ScriptHash. Returns `true` if it is, otherwise `false`.

- **`boolean isStakeKeyHashInDelegationPart()`**  
  Checks if the delegation part of the address is a StakeKeyHash. Returns `true` if it is, otherwise `false`.

- **`boolean isScriptHashInDelegationPart()`**  
  Checks if the delegation part of the address is a ScriptHash. Returns `true` if it is, otherwise `false`.

### Usage Example

#### Creating an Address from a Bech32 Encoded String

```java
String bech32Address = "addr1q..."; // Valid Bech32 address (base address, stake address etc.)
Address address = new Address(bech32Address);
```

#### Retrieving Address Information

```java
AddressType addressType = address.getAddressType();
Network network = address.getNetwork();
String bech32EncodedAddress = address.toBech32();
```

#### Extracting Credentials

```java
Optional<byte[]> paymentHash = address.getPaymentCredentialHash();
Optional<byte[]> delegationHash = address.getDelegationCredentialHash();
```

## AddressProvider

The `AddressProvider` class is a utility class that generates various types of Shelley addresses for the Cardano blockchain. This class supports generating base, pointer, enterprise, and reward addresses using different combinations of payment and delegation credentials, including public keys and scripts.

### Key Features

- **Base Address Generation**: Create base addresses from combinations of payment keys, scripts, and delegation keys or scripts.
- **Pointer Address Generation**: Generate pointer addresses using payment keys or scripts and delegation pointers.
- **Enterprise Address Generation**: Create enterprise addresses from payment keys or scripts.
- **Reward Address Generation**: Generate reward addresses using delegation keys or scripts.
- **Address Verification**: Verify if a given address matches a public key.
- **Stake Address Extraction**: Extract the stake address from a base address.
- **Stake Address Generation from Extended Public Key**: Generate stake addresses from extended account public keys.

### Public Methods

#### Base Address Generation

- **`getBaseAddress(HdPublicKey paymentKey, HdPublicKey delegationKey, Network networkInfo)`**  
  Returns a base address using a payment key and a delegation key.

- **`getBaseAddress(Script paymentScript, HdPublicKey delegationKey, Network networkInfo)`**  
  Returns a base address using a payment script and a delegation key.

- **`getBaseAddress(HdPublicKey paymentKey, Script delegationScript, Network networkInfo)`**  
  Returns a base address using a payment key and a delegation script.

- **`getBaseAddress(Script paymentScript, Script delegationScript, Network networkInfo)`**  
  Returns a base address using a payment script and a delegation script.

- **`getBaseAddress(Credential paymentCredential, Credential delegationCredential, Network networkInfo)`**  
  Returns a base address using payment and delegation credentials, which can be either a key or a script.

#### Pointer Address Generation

- **`getPointerAddress(HdPublicKey paymentKey, Pointer delegationPointer, Network networkInfo)`**  
  Returns a pointer address using a payment key and a delegation pointer.

- **`getPointerAddress(Script paymentScript, Pointer delegationPointer, Network networkInfo)`**  
  Returns a pointer address using a payment script and a delegation pointer.

- **`getPointerAddress(Credential paymentCredential, Pointer delegationPointer, Network networkInfo)`**  
  Returns a pointer address using a payment credential (key or script) and a delegation pointer.

#### Enterprise Address Generation

- **`getEntAddress(HdPublicKey paymentKey, Network networkInfo)`**  
  Returns an enterprise address using a payment key.

- **`getEntAddress(Script paymentScript, Network networkInfo)`**  
  Returns an enterprise address using a payment script.

- **`getEntAddress(Credential paymentCredential, Network networkInfo)`**  
  Returns an enterprise address using a payment credential (key or script).

#### Reward Address Generation

- **`getRewardAddress(HdPublicKey delegationKey, Network networkInfo)`**  
  Returns a reward address using a delegation key.

- **`getRewardAddress(Script delegationScript, Network networkInfo)`**  
  Returns a reward address using a delegation script.

- **`getRewardAddress(Credential stakeCredential, Network networkInfo)`**  
  Returns a reward address using a stake credential (key or script).

#### Address Verification

- **`verifyAddress(Address address, byte[] publicKey)`**  
  Verifies the provided address against a public key by reconstructing the address from the public key and comparing it with the provided address.

#### Stake Address Extraction and Generation

- **`getStakeAddress(Address address)`**  
  Extracts the stake address from a base address.

- **`getStakeAddressFromAccountPublicKey(String accountPubKey, Network network)`**  
  Generates a stake address from a CIP-1852 extended account public key (Bech32 encoded with prefix "acct_xvk" or "xpub").

- **`getStakeAddressFromAccountPublicKey(byte[] accountPubKeyBytes, Network network)`**  
  Generates a stake address from a CIP-1852 extended account public key (Ed25519 public key with chain code).

#### Credential Extraction

- **`getDelegationCredentialHash(Address address)`**  
  Extracts the delegation credential hash (StakeKeyHash or ScriptHash) from the delegation part of a Shelley address.

- **`getPaymentCredentialHash(Address address)`**  
  Extracts the payment credential hash (payment key hash or script hash) from a Shelley address.

- **`getDelegationCredential(Address address)`**  
  Retrieves the delegation credential (key or script) from a Shelley address.

- **`getPaymentCredential(Address address)`**  
  Retrieves the payment credential (key or script) from a Shelley address.

#### Address Type Identification

- **`isPubKeyHashInPaymentPart(Address address)`**  
  Checks if the payment part of a Shelley address is a PubKeyHash.

- **`isScriptHashInPaymentPart(Address address)`**  
  Checks if the payment part of a Shelley address is a ScriptHash.

- **`isStakeKeyHashInDelegationPart(Address address)`**  
  Checks if the delegation part of a Shelley address is a StakeKeyHash.

- **`isScriptHashInDelegationPart(Address address)`**  
  Checks if the delegation part of a Shelley address is a ScriptHash.

## AddressUtil

The `AddressUtil` class provides utility methods for working with both Shelley and Byron addresses on the Cardano blockchain.
It includes methods for validating addresses, converting addresses to and from byte arrays, and handling different address formats, including Bech32 and Base58.

### Key Features

- **Address Validation**: Check if a given Shelley or Byron address is valid.
- **Address Conversion**: Convert Shelley or Byron addresses to byte arrays and vice versa.
- **Byron Address Handling**: Specific methods to convert Byron address bytes to a Base58 encoded address string.

### Public Methods

#### Address Validation

- **`boolean isValidAddress(String addr)`**  
  Checks whether a given Shelley or Byron era address is valid. Returns `true` if the address is valid, otherwise returns `false`.

  #### Example Usage:

  ```java
  boolean isValid = AddressUtil.isValidAddress("addr1..."); // Replace with an actual address
  ```

#### Address Conversion

- **`byte[] addressToBytes(String address) throws AddressExcepion`**  
  Converts a Shelley or Byron address to a byte array.

    - **Parameters:**
        - `address`: The Shelley or Byron address as a `String`.
    - **Returns:** A byte array representing the address.
    - **Throws:** `AddressExcepion` if the address is invalid or conversion fails.

  ##### Example Usage:

  ```java
  byte[] addressBytes = AddressUtil.addressToBytes("addr1...");
  ```

- **`String bytesToBase58Address(byte[] bytes) throws AddressExcepion`**  
  Converts a byte array to a Base58 encoded Byron address string. Only valid for Byron addresses.

    - **Parameters:**
        - `bytes`: The byte array representing a Byron address.
    - **Returns:** A `String` representing the Base58 encoded Byron address.
    - **Throws:** `AddressExcepion` if the address type is not Byron.

  ##### Example Usage:

  ```java
  String base58Address = AddressUtil.bytesToBase58Address(byronAddressBytes);
  ```

- **`String bytesToAddress(byte[] bytes) throws AddressExcepion`**  
  Converts a byte array to either a Shelley (Bech32 encoded) or Byron (Base58 encoded) address string, depending on the address type.

    - **Parameters:**
        - `bytes`: The byte array representing the address.
    - **Returns:** A `String` representing the Shelley or Byron address.
    - **Throws:** `AddressExcepion` if the conversion fails.

  ##### Example Usage:

  ```java
  String address = AddressUtil.bytesToAddress(addressBytes);
  ```

### Usage Example

#### Validating an Address

```java
String address = "addr1...";
boolean isValid = AddressUtil.isValidAddress(address);
System.out.println("Is the address valid? " + isValid);
```

#### Converting an Address to Bytes

```java
String shelleyAddress = "addr1...";
byte[] addressBytes = AddressUtil.addressToBytes(shelleyAddress);
```

#### Converting Bytes to an Address

```java
byte[] addressBytes = ...; // Some byte array representing an address
String address = AddressUtil.bytesToAddress(addressBytes);
```

#### Converting Byron Address Bytes to a Base58 Address String

```java
byte[] byronAddressBytes = ...; // Some byte array representing a Byron address
String base58Address = AddressUtil.bytesToBase58Address(byronAddressBytes);
```

### Exception Handling

The `AddressUtil` methods throw `AddressExcepion` if the input is invalid or the conversion process encounters an issue. This ensures that errors in address handling are caught and managed appropriately.

