# ADR 0022: Polymorphic Nested Types via @MetadataDiscriminator

## Status
Accepted

## Context
ADR 0015 added support for nested `@MetadataType` fields, but only for concrete types. Users needed to serialize fields declared as interfaces or abstract classes where the actual type varies at runtime (e.g., a `Media` field that could be `ImageMedia` or `AudioMedia`). Without discriminator-based dispatch, the processor could not determine which converter to use during deserialization.

## Decision
Introduce `@MetadataDiscriminator` and `@MetadataSubtype` annotations for polymorphic nested type support. The parent type (interface or abstract class) declares the discriminator key and the set of concrete subtypes. Each subtype must be annotated with `@MetadataType`.

### Annotation API
```java
@MetadataDiscriminator(key = "type", subtypes = {
    @MetadataSubtype(value = "image", type = ImageMedia.class),
    @MetadataSubtype(value = "audio", type = AudioMedia.class)
})
public interface Media {}

@MetadataType
public class ImageMedia implements Media {
    private String url;
    private int width;
    private int height;
}

@MetadataType
public class AudioMedia implements Media {
    private String url;
    private int duration;
}

@MetadataType
public class Post {
    private String title;
    private Media media;  // Polymorphic field
}
```

### Detection
- `MetadataFieldExtractor` checks for `@MetadataDiscriminator` on the field's type element **before** checking for `@MetadataType` (polymorphic takes priority over plain nested)
- `extractSubtypeEntries()` uses the `AnnotationMirror` API to safely extract `Class<?>` values at compile time (avoids `MirroredTypeException`)
- Each subtype is validated: must resolve to a `TypeElement` and must carry `@MetadataType`
- At least one subtype is required (empty subtypes list is a compile-time ERROR)

### MetadataFieldInfo Extensions
```java
private boolean polymorphicType;
private String discriminatorKey;
private List<PolymorphicSubtypeInfo> subtypes;

public record PolymorphicSubtypeInfo(
    String discriminatorValue,
    String converterFqn,
    String javaTypeFqn
) {}
```

### Code Generation — Serialization
Uses `instanceof` checks against each concrete subtype, delegates to the subtype's converter, and injects the discriminator key into the resulting map:

```java
if (post.getMedia() instanceof ImageMedia) {
    MetadataMap _polyMap = new ImageMediaMetadataConverter().toMetadataMap((ImageMedia) post.getMedia());
    _polyMap.put("type", "image");
    map.put("media", _polyMap);
} else if (post.getMedia() instanceof AudioMedia) {
    MetadataMap _polyMap = new AudioMediaMetadataConverter().toMetadataMap((AudioMedia) post.getMedia());
    _polyMap.put("type", "audio");
    map.put("media", _polyMap);
}
```

### Code Generation — Deserialization
Reads the discriminator value from the metadata map and dispatches to the correct converter:

```java
if (v instanceof MetadataMap) {
    MetadataMap _polyMap = (MetadataMap) v;
    Object _disc = _polyMap.get("type");
    if ("image".equals(_disc)) {
        obj.setMedia(new ImageMediaMetadataConverter().fromMetadataMap(_polyMap));
    } else if ("audio".equals(_disc)) {
        obj.setMedia(new AudioMediaMetadataConverter().fromMetadataMap(_polyMap));
    }
}
```

### Dispatch Priority
In both `emitToMapPut()` and `emitFromMapGet()`, polymorphic fields are checked before plain nested types:
1. Adapter (ADR 0023)
2. Map
3. Collection
4. **Polymorphic** ← before nested
5. Nested
6. Scalar / enum / etc.

### Design Notes
- The parent interface/abstract class carries `@MetadataDiscriminator` but does NOT need `@MetadataType`
- The discriminator key-value pair is stored inside each subtype's metadata map (not as a wrapper)
- Unknown discriminator values during deserialization silently leave the field null (no exception)
- Polymorphic fields work with both POJOs and records

## Consequences
- Each concrete subtype must have its own `@MetadataType` annotation and generated converter
- The discriminator key is reserved — subtype fields should not collide with it
- Adding new subtypes requires updating the `@MetadataDiscriminator` annotation on the parent type
- Subtype order in the annotation determines `instanceof` check order during serialization
- Polymorphic fields in collections (`List<Media>`) are not supported in this version — only direct fields
