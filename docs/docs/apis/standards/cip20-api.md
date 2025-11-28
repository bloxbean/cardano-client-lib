---
title: "CIP20 API"
description: "Transaction Message/Comment Metadata implementation"
sidebar_position: 4
---

# CIP20 API

CIP20 (Cardano Improvement Proposal 20) defines a standard for adding messages, comments, or memos to Cardano transactions using transaction metadata. This allows you to attach informational text, invoice numbers, or similar data to transactions on the Cardano blockchain. The library’s `MessageMetadata` wraps the CIP20 layout (label 674 with `msg` array) so you only worry about adding strings and serializing the CBOR when needed.

## Key Features

- **Standard Compliance**: Full CIP20 specification support
- **Message Metadata**: Add messages/comments to transactions
- **Validation**: Built-in validation for message length (max 64 characters)
- **Multiple Messages**: Support for multiple messages in a single transaction
- **Easy Integration**: Simple API for adding metadata to transactions

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip20
- **Dependencies**: metadata

## Usage Examples

### Creating Message Metadata

Build the `MessageMetadata` object and serialize to CBOR when you need raw bytes.

The following example shows how to create CIP20 compliant message metadata with multiple messages.

```java
// Create message metadata
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("Payment for services")
    .add("Invoice #12345")
    .add("Thank you for your business");

// Get metadata as CBOR bytes
byte[] cborBytes = messageMetadata.serialize();
String hexMetadata = HexUtil.encodeHexString(cborBytes);
```

### Adding Messages to Transactions

Attach the metadata directly in QuickTx so it is included in the auxiliary data of the transaction.

The following example shows how to add message metadata to a transaction using QuickTx.

```java
// Create message metadata
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("Payment for NFT purchase")
    .add("Transaction ID: " + System.currentTimeMillis());

// Create transaction with message metadata
Tx tx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(10))
    .from(senderAddress)
    .attachMetadata(messageMetadata);

// Build and sign transaction
Result<String> result = quickTxBuilder.compose(tx)
    .feePayer(senderAddress)
    .withSigner(SignerProviders.signerFrom(senderAccount))
    .completeAndWait(System.out::println);
```

### Working with Message Lists

Retrieve the stored messages to display or process them after deserialization.

The following example shows how to retrieve and work with messages from metadata.

```java
// Create message metadata
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("First message")
    .add("Second message")
    .add("Third message");

// Get all messages
List<String> messages = messageMetadata.getMessages();

// Process messages
for (String message : messages) {
    System.out.println("Message: " + message);
}

// Check message count
System.out.println("Total messages: " + messages.size());
```

### Integration with Other Metadata

Merge CIP20 messages with other metadata maps if you need to send multiple labels in one transaction.

The following example shows how to combine CIP20 message metadata with other transaction metadata.

```java
// Create CIP20 message metadata
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("Payment confirmation")
    .add("Order #789");

// Create additional custom metadata
CBORMetadata customMetadata = new CBORMetadata()
    .put(BigInteger.valueOf(100), "Custom field value")
    .put(BigInteger.valueOf(101), "Another custom value");

// Combine metadata (CIP20 uses label 674)
CBORMetadata combinedMetadata = (CBORMetadata) customMetadata.merge(messageMetadata);

// Use in transaction
Tx tx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(5))
    .from(senderAddress)
    .attachMetadata(combinedMetadata);
```

### Practical Use Cases

#### Invoice Tracking
```java
// Create invoice metadata
MessageMetadata invoiceMetadata = MessageMetadata.create()
    .add("Invoice #INV-2024-001")
    .add("Due: 2024-01-15")
    .add("Amount: 100 ADA");

Tx paymentTx = new Tx()
    .payToAddress(merchantAddress, Amount.ada(100))
    .from(customerAddress)
    .attachMetadata(invoiceMetadata);
```

#### Payment Confirmations
```java
// Create payment confirmation metadata
MessageMetadata confirmationMetadata = MessageMetadata.create()
    .add("Payment confirmed")
    .add("Ref: " + generatePaymentReference())
    .add("Thank you!");

Tx confirmationTx = new Tx()
    .payToAddress(recipientAddress, Amount.ada(50))
    .from(payerAddress)
    .attachMetadata(confirmationMetadata);
```

#### Service Requests
```java
// Create service request metadata
MessageMetadata serviceMetadata = MessageMetadata.create()
    .add("Service: Web Development")
    .add("Client: Acme Corp")
    .add("Priority: High");

Tx serviceTx = new Tx()
    .payToAddress(developerAddress, Amount.ada(25))
    .from(clientAddress)
    .attachMetadata(serviceMetadata);
```

## API Reference

### MessageMetadata Class

The main class for creating and managing CIP20 message metadata.

#### Constructor
```java
// Create new instance
public static MessageMetadata create()
```

#### Methods

##### add(String message)
Adds a message to the metadata. Each message must be:
- Non-null
- Maximum 64 characters in UTF-8 encoding

```java
public MessageMetadata add(String message)
```

**Parameters:**
- `message` - The message to add (max 64 characters)

**Returns:** The MessageMetadata instance for chaining

**Throws:** `IllegalArgumentException` if message is null or exceeds 64 characters

##### getMessages()
Retrieves all messages from the metadata.

```java
public List<String> getMessages()
```

**Returns:** List of all messages in the metadata

##### serialize()
Serializes the metadata to CBOR bytes.

```java
public byte[] serialize()
```

**Returns:** CBOR-encoded bytes of the metadata

## CIP20 Specification Details

### Metadata Label
CIP20 uses metadata label **674** for message metadata.

### Message Format
Messages are stored as an array of strings under the "msg" key:
```json
{
  "674": {
    "msg": [
      "First message",
      "Second message",
      "Third message"
    ]
  }
}
```

### Constraints
- Each message must be a maximum of 64 characters when encoded in UTF-8
- Messages cannot be null
- Multiple messages are supported in a single transaction

## Best Practices

1. **Keep messages concise** - The 64-character limit encourages brief, informative messages
2. **Use meaningful content** - Include relevant information like invoice numbers, references, or confirmations
3. **Validate input** - Always handle validation errors when adding messages
4. **Consider privacy** - Remember that metadata is publicly visible on the blockchain
5. **Combine with other standards** - CIP20 works well with CIP25 (NFT metadata) and other standards

## Integration Examples

### With CIP25 (NFT Metadata)
```java
// Create CIP25 NFT metadata
CIP25NFT nftMetadata = CIP25NFT.create()
    .name("My NFT")
    .description("A sample NFT");

// Create CIP20 message metadata
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("NFT purchase")
    .add("Collection: Art Gallery");

// Combine metadata
CBORMetadata combinedMetadata = (CBORMetadata) nftMetadata.merge(messageMetadata);
```

### With Custom Metadata
```java
// Create custom metadata
CBORMetadata customMetadata = new CBORMetadata()
    .put(BigInteger.valueOf(100), "Custom field");

// Create CIP20 message metadata
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("Transaction note");

// Combine and use in transaction
CBORMetadata finalMetadata = (CBORMetadata) customMetadata.merge(messageMetadata);
```

For more information about CIP20, refer to the [official CIP20 specification](https://cips.cardano.org/cips/cip20/).
