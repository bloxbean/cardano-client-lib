# Understanding Generated Converters

For every class annotated with `@MetadataType`, the annotation processor generates a converter class named `{ClassName}MetadataConverter`. This document explains the interfaces these converters implement and how to use them.

## MetadataConverter Interface

Every generated converter implements `MetadataConverter<T>`:

```java
public interface MetadataConverter<T> {
    MetadataMap toMetadataMap(T obj);
    T fromMetadataMap(MetadataMap map);
}
```

- **`toMetadataMap`** — converts a Java object to a `MetadataMap` (the on-chain key-value structure).
- **`fromMetadataMap`** — reconstructs a Java object from a `MetadataMap`.

### Example

```java
var converter = new TokenInfoMetadataConverter();

// Serialize
TokenInfo token = new TokenInfo("abc123", "MyToken", 6);
MetadataMap map = converter.toMetadataMap(token);

// Deserialize
TokenInfo restored = converter.fromMetadataMap(map);
```

## LabeledMetadataConverter Interface

When `@MetadataType(label = N)` specifies a non-negative label, the converter also implements `LabeledMetadataConverter<T>`:

```java
public interface LabeledMetadataConverter<T> extends MetadataConverter<T> {
    Metadata toMetadata(T obj);
    T fromMetadata(Metadata metadata);
}
```

- **`toMetadata`** — wraps the `MetadataMap` under the configured label, producing a `Metadata` object ready to attach to a transaction.
- **`fromMetadata`** — extracts the `MetadataMap` from the label key and deserializes it.

### Example

```java
// Cip25NftMetadata has @MetadataType(label = 721)
var converter = new Cip25NftMetadataMetadataConverter();

// Serialize to Metadata (wraps under label 721)
Cip25NftMetadata nft = new Cip25NftMetadata();
nft.setName("MyNFT");
nft.setImage("ipfs://Qm...");
Metadata metadata = converter.toMetadata(nft);

// Deserialize from Metadata
Cip25NftMetadata restored = converter.fromMetadata(metadata);
```

## Attaching Metadata to Transactions

Use the `toMetadata` method (for labeled types) or manually wrap a `MetadataMap` under a label, then attach it to a `Tx`:

```java
var converter = new Cip25NftMetadataMetadataConverter();
Metadata metadata = converter.toMetadata(nft);

Tx tx = new Tx()
        .payToAddress(receiverAddr, Amount.ada(1.5))
        .attachMetadata(metadata)
        .from(senderAddr);

Result<String> result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

## Retrieving and Deserializing Metadata from Chain

After a transaction is confirmed, you can retrieve its metadata from the chain and deserialize it:

```java
// Fetch CBOR metadata by transaction hash
var cborResult = backendService.getMetadataService()
        .getCBORMetadataByTxnHash(txHash);

// Find the entry for your label and decode it
for (MetadataCBORContent entry : cborResult.getValue()) {
    if ("721".equals(entry.getLabel())) {
        byte[] cborBytes = HexUtil.decodeHexString(entry.getCborMetadata());
        List<DataItem> items = CborDecoder.decode(cborBytes);
        MetadataMap chainMap = new CBORMetadataMap((co.nstant.in.cbor.model.Map) items.get(0));

        // Deserialize back to your Java object
        Cip25NftMetadata nft = converter.fromMetadataMap(chainMap);
    }
}
```

## Generic Programming via the Interface

The `MetadataConverter<T>` and `LabeledMetadataConverter<T>` interfaces enable generic code that works with any annotated metadata type:

```java
public <T> void submitMetadata(LabeledMetadataConverter<T> converter, T obj,
                                BackendService backend, Account account) {
    Metadata metadata = converter.toMetadata(obj);

    Tx tx = new Tx()
            .payToAddress(account.baseAddress(), Amount.ada(1.5))
            .attachMetadata(metadata)
            .from(account.baseAddress());

    new QuickTxBuilder(backend)
            .compose(tx)
            .withSigner(SignerProviders.signerFrom(account))
            .completeAndWait(System.out::println);
}
```

This lets you write reusable metadata submission logic without coupling to any specific model class.

## Next Steps

- [Supported Types](04-supported-types.md) — complete list of supported Java types and their on-chain representations
- [Class Support and Patterns](05-class-support.md) — records, POJOs, Lombok, and inheritance
- [Advanced Topics](06-advanced-topics.md) — custom adapters, polymorphic types, and real-world examples
