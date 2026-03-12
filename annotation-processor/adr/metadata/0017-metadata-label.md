# ADR 0017: Metadata Label Annotation

## Status
Accepted

## Context
Cardano transaction metadata uses numeric labels (e.g., 721 for CIP-25 NFTs, 20 for CIP-20 messages) as top-level keys. Users had to manually wrap MetadataMap in a Metadata object with the correct label, which was error-prone and repetitive.

## Decision
Add an optional `label` attribute to `@MetadataType`:

```java
@MetadataType(label = 721)
class NftMetadata { ... }
```

When `label >= 0`, the generated converter includes two additional methods:

```java
public Metadata toMetadata(NftMetadata obj) {
    Metadata metadata = MetadataBuilder.createMetadata();
    metadata.put(BigInteger.valueOf(721L), toMetadataMap(obj));
    return metadata;
}

public NftMetadata fromMetadata(Metadata metadata) {
    Object raw = metadata.get(BigInteger.valueOf(721L));
    if (raw instanceof MetadataMap) {
        return fromMetadataMap((MetadataMap) raw);
    }
    throw new IllegalArgumentException("Expected MetadataMap at label 721");
}
```

### Design Choices
- **Default value `-1`** — no label, only `toMetadataMap`/`fromMetadataMap` generated (backward compatible)
- **`long` type** — matches Cardano metadata label range; `BigInteger.valueOf(long)` used in generated code
- **`toMetadataMap` unchanged** — the label methods compose on top, not replace
- **`fromMetadata` throws** — if the label is missing or not a MetadataMap, throws `IllegalArgumentException` with descriptive message

## Consequences
- Zero-cost for existing code — default `-1` means no new methods generated
- Common CIP patterns (721, 20, etc.) become one-annotation declarations
- The generated converter is self-documenting about which label it uses
