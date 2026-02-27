# Plan: Metadata Annotation Processor (Prototype)

## Status: COMPLETED ✅

## Context
Add a Java Annotation Processor that generates serializer/deserializer code for Cardano metadata,
based on annotating Java classes with `@MetadataType`. This is a prototype experiment within the
existing `annotation-processor` module (new package). It follows the same patterns as the existing
`@Constr` / `ConstrAnnotationProcessor` but targets `MetadataMap` instead of `ConstrPlutusData`.

User confirmed scope: MVP with primitive types only (String, BigInteger, Long, Integer, byte[]).

---

## New Package Location (in `annotation-processor` module)
- Annotations: `com.bloxbean.cardano.client.metadata.annotation`
- Processor:   `com.bloxbean.cardano.client.metadata.annotation.processor`

---

## Files Created

### 1. Annotations ✅

**`annotation-processor/src/main/java/com/bloxbean/cardano/client/metadata/annotation/MetadataType.java`**
- `@Target(ElementType.TYPE)`, `@Retention(RetentionPolicy.SOURCE)`
- Marks a class for metadata serialization code generation.

**`annotation-processor/src/main/java/com/bloxbean/cardano/client/metadata/annotation/MetadataField.java`**
- `@Target(ElementType.FIELD)`, `@Retention(RetentionPolicy.SOURCE)`
- Has `String key() default ""` to override field name as metadata key.

**`annotation-processor/src/main/java/com/bloxbean/cardano/client/metadata/annotation/MetadataIgnore.java`**
- `@Target(ElementType.FIELD)`, `@Retention(RetentionPolicy.SOURCE)`
- Excludes a field from converter generation.

---

### 2. Processor Classes ✅

**`annotation-processor/src/main/java/com/bloxbean/cardano/client/metadata/annotation/processor/MetadataFieldInfo.java`**
- Simple Lombok `@Data` DTO holding: `javaFieldName`, `metadataKey`, `javaTypeName`, `getterName`, `setterName`

**`annotation-processor/src/main/java/com/bloxbean/cardano/client/metadata/annotation/processor/MetadataConverterGenerator.java`**
- Uses JavaPoet to generate `{ClassName}MetadataConverter`
- Generates `toMetadataMap(ClassName obj) -> MetadataMap`
- Generates `fromMetadataMap(MetadataMap map) -> ClassName`
- Reuses `GENERATED_CODE` constant from `Constant` class
- Handles null checks for all reference types (not for primitives `int`/`long`)

**`annotation-processor/src/main/java/com/bloxbean/cardano/client/metadata/annotation/processor/MetadataAnnotationProcessor.java`**
- Extends `AbstractProcessor`, annotated with `@AutoService(Processor.class)`
- Processes `@MetadataType` annotated classes
- Detects Lombok via raw class reflection (same pattern as `ClassDefinitionGenerator`)
- Extracts fields, resolves getters/setters, detects @MetadataIgnore/@MetadataField
- Delegates code generation to `MetadataConverterGenerator`
- Writes generated file via `JavaFileUtil.createJavaFile()`

---

### 3. Build Changes ✅

**`annotation-processor/build.gradle`** – added:
```gradle
api project(':metadata')
```

---

### 4. Service Registration ✅

**`annotation-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor`**
Appended:
```
com.bloxbean.cardano.client.metadata.annotation.processor.MetadataAnnotationProcessor
```

---

### 5. Tests ✅

**`annotation-processor/src/test/resources/SampleOrder.java`**
- Test input class in `package com.test;`
- Has: `String recipient`, `BigInteger amount`, `Long timestamp`, `Integer quantity`
- Has: `@MetadataField(key = "ref_id") String referenceId`
- Has: `@MetadataIgnore String internalId`
- Explicit getters and setters for all fields

**`annotation-processor/src/test/java/.../metadata/MetadataAnnotationProcessorTest.java`**
- Uses `google.testing.compile` to compile `SampleOrder.java`
- Asserts compilation succeeds
- Asserts generated `com.test.SampleOrderMetadataConverter` contains `toMetadataMap` and `fromMetadataMap`

---

## Implementation Notes

### Key Decisions Made During Implementation

1. **No ClassDefinitionGenerator reuse**: The existing `ClassDefinitionGenerator` is tightly coupled to `@Constr`. Created a simpler inline field extraction in `MetadataAnnotationProcessor`.

2. **Lombok detection**: Uses raw `Class` types (unchecked) like the existing code to avoid generic type issues with `Element.getAnnotation()`.

3. **Generated file package**: Converter is generated in the **same package** as the annotated class (not a `.converter` subpackage). Simpler for MVP.

4. **Type mapping**:
   | Java Type    | Stored as         | Read back as                    |
   |--------------|-------------------|---------------------------------|
   | String       | String            | instanceof String               |
   | BigInteger   | BigInteger        | instanceof BigInteger           |
   | Long         | BigInteger.valueOf| instanceof BigInteger → .longValue() |
   | long         | BigInteger.valueOf| instanceof BigInteger → .longValue() |
   | Integer      | BigInteger.valueOf| instanceof BigInteger → .intValue()  |
   | int          | BigInteger.valueOf| instanceof BigInteger → .intValue()  |
   | byte[]       | byte[]            | instanceof byte[]               |

5. **Null checks**: Added for all reference types; skipped for primitives (`int`/`long`).

### Generated Code Sample (SampleOrderMetadataConverter)

```java
public class SampleOrderMetadataConverter {
  public MetadataMap toMetadataMap(SampleOrder sampleOrder) {
    MetadataMap map = MetadataBuilder.createMap();
    if (sampleOrder.getRecipient() != null) {
      map.put("recipient", sampleOrder.getRecipient());
    }
    if (sampleOrder.getAmount() != null) {
      map.put("amount", sampleOrder.getAmount());
    }
    if (sampleOrder.getTimestamp() != null) {
      map.put("timestamp", BigInteger.valueOf(sampleOrder.getTimestamp()));
    }
    if (sampleOrder.getQuantity() != null) {
      map.put("quantity", BigInteger.valueOf((long) sampleOrder.getQuantity()));
    }
    if (sampleOrder.getReferenceId() != null) {
      map.put("ref_id", sampleOrder.getReferenceId());
    }
    return map;
  }

  public SampleOrder fromMetadataMap(MetadataMap map) {
    SampleOrder obj = new SampleOrder();
    Object v;
    v = map.get("recipient");
    if (v instanceof String) { obj.setRecipient((String) v); }
    v = map.get("amount");
    if (v instanceof BigInteger) { obj.setAmount((BigInteger) v); }
    v = map.get("timestamp");
    if (v instanceof BigInteger) { obj.setTimestamp(((BigInteger) v).longValue()); }
    v = map.get("quantity");
    if (v instanceof BigInteger) { obj.setQuantity(((BigInteger) v).intValue()); }
    v = map.get("ref_id");
    if (v instanceof String) { obj.setReferenceId((String) v); }
    return obj;
  }
}
```
