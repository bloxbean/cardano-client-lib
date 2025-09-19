---
title: "Address API"
description: "Address generation, validation, and conversion utilities"
sidebar_position: 3
---

# Address API

The Address API provides comprehensive functionality for working with Cardano addresses. It supports address creation, validation, conversion, and extraction of address components. The API handles both Shelley addresses (current) and Byron addresses (legacy) with full support for different address types and networks.

## Key Features

- **Address Creation**: Create addresses from various inputs (strings, bytes, credentials)
- **Address Validation**: Validate address format and network compatibility
- **Address Conversion**: Convert between different address formats and representations
- **Credential Extraction**: Extract payment and delegation credentials from addresses
- **Type Support**: Support for all Cardano address types (base, enterprise, pointer, reward)

## Core Classes

### Address Class
The main class for Shelley address operations, supporting Bech32 encoded addresses and credential extraction.

### AddressProvider Class
Utility class for generating addresses from accounts, keys, and scripts.

### AddressUtil Class
Utility class for address validation, conversion, and manipulation operations.

**Key Methods:**
- `AddressUtil.isValidAddress(String address)` - Validate address format
- `AddressUtil.addressToBytes(String address)` - Convert address to bytes
- `AddressUtil.getBech32Address(Address address)` - Get Bech32 representation

**Address Class Methods:**
- `Address(String address)` - Create from Bech32 string
- `toBech32()` - Convert to Bech32 format
- `getPaymentCredential()` - Extract payment credential
- `getDelegationCredential()` - Extract delegation credential

## Usage Examples

### Creating Addresses

Create addresses from different input formats:

```java
// Create address from Bech32 string
String addressString = "addr_test1qpx4kmt032z8fwjj88vyvksrrvs724mt9h503ha8yh2ck2vrdwlz2qrlwssdm08085p5qldeyhh274lxhaetustth7psmplwg6";
Address address = new Address(addressString);

// Create address from byte array
byte[] addressBytes = address.getBytes();
Address addressFromBytes = new Address(addressBytes);

// Create address with custom prefix
Address customAddress = new Address("addr_test", addressBytes);
```

### Address Generation from Accounts

Generate addresses from account credentials:

```java
// Generate addresses from account
Account account = new Account(Networks.testnet());

// Get base address (payment + staking)
String baseAddress = account.baseAddress();
System.out.println("Base Address: " + baseAddress);

// Get enterprise address (payment only)
String enterpriseAddress = account.enterpriseAddress();
System.out.println("Enterprise Address: " + enterpriseAddress);

// Get stake address (for delegation)
String stakeAddress = account.stakeAddress();
System.out.println("Stake Address: " + stakeAddress);
```

### Address Validation

Validate address format and network compatibility:

```java
// Basic address validation
String addressToValidate = "addr_test1qpx4kmt032z8fwjj88vyvksrrvs724mt9h503ha8yh2ck2vrdwlz2qrlwssdm08085p5qldeyhh274lxhaetustth7psmplwg6";

boolean isValid = AddressUtil.isValidAddress(addressToValidate);
if (isValid) {
    System.out.println("Address is valid");
} else {
    System.out.println("Address is invalid");
}

// Network-specific validation
public boolean isTestnetAddress(String address) {
    try {
        Address addr = new Address(address);
        return addr.getPrefix().startsWith("addr_test") || 
               addr.getPrefix().startsWith("stake_test");
    } catch (Exception e) {
        return false;
    }
}

// Validate address type
public boolean isBaseAddress(String address) {
    try {
        Address addr = new Address(address);
        return addr.getAddressType() == AddressType.Base;
    } catch (Exception e) {
        return false;
    }
}
```

### Address Conversion

Convert addresses between different formats:

```java
// Convert address to bytes
String addressString = "addr_test1qpx4kmt032z8fwjj88vyvksrrvs724mt9h503ha8yh2ck2vrdwlz2qrlwssdm08085p5qldeyhh274lxhaetustth7psmplwg6";
byte[] addressBytes = AddressUtil.addressToBytes(addressString);

// Convert bytes back to address
Address reconstructedAddress = new Address(addressBytes);
String reconstructedString = reconstructedAddress.toBech32();

System.out.println("Original: " + addressString);
System.out.println("Reconstructed: " + reconstructedString);
System.out.println("Match: " + addressString.equals(reconstructedString));
```

### Credential Extraction

Extract payment and delegation credentials from addresses:

```java
Address address = new Address("addr_test1qpx4kmt032z8fwjj88vyvksrrvs724mt9h503ha8yh2ck2vrdwlz2qrlwssdm08085p5qldeyhh274lxhaetustth7psmplwg6");

// Extract payment credential
Optional<Credential> paymentCredential = address.getPaymentCredential();
if (paymentCredential.isPresent()) {
    Credential payment = paymentCredential.get();
    System.out.println("Payment Credential Type: " + payment.getType());
    System.out.println("Payment Credential Hash: " + payment.getBytes());
}

// Extract delegation credential
Optional<Credential> delegationCredential = address.getDelegationCredential();
if (delegationCredential.isPresent()) {
    Credential delegation = delegationCredential.get();
    System.out.println("Delegation Credential Type: " + delegation.getType());
    System.out.println("Delegation Credential Hash: " + delegation.getBytes());
}
```

### Address Information

Get detailed information about addresses:

```java
Address address = new Address("addr_test1qpx4kmt032z8fwjj88vyvksrrvs724mt9h503ha8yh2ck2vrdwlz2qrlwssdm08085p5qldeyhh274lxhaetustth7psmplwg6");

// Get address type
AddressType addressType = address.getAddressType();
System.out.println("Address Type: " + addressType);

// Get network
Network network = address.getNetwork();
System.out.println("Network: " + network);

// Get prefix
String prefix = address.getPrefix();
System.out.println("Prefix: " + prefix);

// Get raw bytes
byte[] bytes = address.getBytes();
System.out.println("Address Length: " + bytes.length + " bytes");
```

## Advanced Usage

### Script Addresses

Work with script-based addresses:

```java
// Generate key pair for script
Keys keyPair = KeyGenUtil.generateKey();
VerificationKey verificationKey = keyPair.getVkey();

// Create script pubkey
ScriptPubkey scriptPubkey = ScriptPubkey.create(verificationKey);

// Generate script address
Address scriptAddress = AddressProvider.getEntAddress(scriptPubkey, Networks.testnet());
System.out.println("Script Address: " + scriptAddress.getAddress());

// Verify it's a script address
Optional<Credential> paymentCredential = scriptAddress.getPaymentCredential();
if (paymentCredential.isPresent() && 
    paymentCredential.get().getType() == CredentialType.Script) {
    System.out.println("Successfully created script address");
}
```

### Multi-Signature Addresses

Create addresses for multi-signature scenarios:

```java
// Generate multiple key pairs
Keys key1 = KeyGenUtil.generateKey();
Keys key2 = KeyGenUtil.generateKey();
Keys key3 = KeyGenUtil.generateKey();

// Create script pubkeys
ScriptPubkey scriptPubkey1 = ScriptPubkey.create(key1.getVkey());
ScriptPubkey scriptPubkey2 = ScriptPubkey.create(key2.getVkey());
ScriptPubkey scriptPubkey3 = ScriptPubkey.create(key3.getVkey());

// Create multi-sig script (2 of 3)
ScriptAtLeast multiSigScript = ScriptAtLeast.create(2)
    .addScript(scriptPubkey1)
    .addScript(scriptPubkey2)
    .addScript(scriptPubkey3);

// Generate multi-sig address
Address multiSigAddress = AddressProvider.getEntAddress(multiSigScript, Networks.testnet());
System.out.println("Multi-Sig Address: " + multiSigAddress.getAddress());
```

### Address Utilities

Utility functions for common address operations:

```java
public class AddressHelper {
    
    // Check if address belongs to specific network
    public static boolean isMainnetAddress(String address) {
        try {
            Address addr = new Address(address);
            return addr.getNetwork() == Networks.mainnet();
        } catch (Exception e) {
            return false;
        }
    }
    
    // Extract address hash for comparison
    public static String getAddressHash(String address) {
        try {
            Address addr = new Address(address);
            Optional<Credential> credential = addr.getPaymentCredential();
            if (credential.isPresent()) {
                return HexUtil.encodeHexString(credential.get().getBytes());
            }
        } catch (Exception e) {
            // Handle error
        }
        return null;
    }
    
    // Compare addresses ignoring network differences
    public static boolean isSameAddressHash(String addr1, String addr2) {
        String hash1 = getAddressHash(addr1);
        String hash2 = getAddressHash(addr2);
        return hash1 != null && hash1.equals(hash2);
    }
}
```

## Best Practices

### Address Validation

```java
// Always validate addresses before use
public boolean safeAddressOperation(String addressString) {
    if (!AddressUtil.isValidAddress(addressString)) {
        System.err.println("Invalid address format");
        return false;
    }
    
    try {
        Address address = new Address(addressString);
        // Perform operations with validated address
        return true;
    } catch (Exception e) {
        System.err.println("Address processing failed: " + e.getMessage());
        return false;
    }
}
```

### Network Consistency

```java
// Ensure address matches expected network
public boolean validateNetworkConsistency(String address, Network expectedNetwork) {
    try {
        Address addr = new Address(address);
        return addr.getNetwork().equals(expectedNetwork);
    } catch (Exception e) {
        return false;
    }
}
```

### Secure Address Handling

```java
// Handle sensitive address data securely
public void processAddressSecurely(String addressString) {
    try {
        Address address = new Address(addressString);
        byte[] addressBytes = address.getBytes();
        
        // Process address bytes
        processBytes(addressBytes);
        
    } catch (Exception e) {
        System.err.println("Secure address processing failed: " + e.getMessage());
    }
}
```

## Address Types Reference

| Address Type | Prefix | Use Case | Components |
|--------------|--------|----------|------------|
| **Base** | `addr` / `addr_test` | Payment + Staking | Payment credential + Delegation credential |
| **Enterprise** | `addr` / `addr_test` | Payment only | Payment credential only |
| **Pointer** | `addr` / `addr_test` | Payment + Pointer to stake | Payment credential + Stake pointer |
| **Reward** | `stake` / `stake_test` | Staking rewards | Delegation credential only |
| **Byron** | `Ae2` / `37` | Legacy addresses | Legacy format (deprecated) |

The Address API provides comprehensive support for all these address types with consistent interfaces and robust error handling.