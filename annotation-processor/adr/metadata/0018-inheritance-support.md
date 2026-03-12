# ADR 0018: Inheritance Support

## Status
Accepted

## Context
Java POJOs commonly use inheritance for shared fields (e.g., `BaseMetadata` with `version` and `author` fields). The processor only looked at fields declared directly on the annotated class, ignoring superclass fields.

## Decision
Walk the class hierarchy bottom-up, collecting fields from each superclass until `java.lang.Object`. Superclasses do NOT need `@MetadataType` — any concrete class in the hierarchy contributes its fields.

### Field Resolution
- `extractFields()` refactored into a hierarchy walker that calls `extractFieldsForType()` per class
- A `Set<String> seenFieldNames` tracks already-seen field names
- **Shadowing**: if a child declares a field with the same name as a parent field, the child's field wins (parent's is skipped)

### Accessor Resolution
- `findGetter()` and `findSetter()` now use `processingEnv.getElementUtils().getAllMembers(leafTypeElement)` instead of `typeElement.getEnclosedElements()`
- This ensures inherited public methods (getters/setters from parent classes) are found when resolving accessors for parent fields
- The `leafTypeElement` (the annotated class) is passed through, so accessor search always starts from the most-derived class

### Generated Code
The converter for a child class includes fields from all ancestors:
```java
// For SampleChildMetadata extends SampleBaseMetadata
public MetadataMap toMetadataMap(SampleChildMetadata obj) {
    // child fields: name, description
    // parent fields: version, author
    map.put("name", obj.getName());
    map.put("description", obj.getDescription());
    map.put("version", obj.getVersion());
    map.put("author", obj.getAuthor());
    return map;
}
```

## Consequences
- Parent classes need public getters/setters (or Lombok) — same requirement as direct fields
- No-arg constructor validation only checks the annotated (leaf) class
- Deep hierarchies work naturally (A extends B extends C)
- `@MetadataIgnore` on parent fields is respected
- Static fields in parent classes are skipped (same as direct fields)
