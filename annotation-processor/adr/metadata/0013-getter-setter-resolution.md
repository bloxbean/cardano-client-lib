# ADR metadata/0013: Getter/Setter Resolution & Field Access Strategy

- **Status**: Accepted
- **Date**: 2026-03-12
- **Deciders**: Cardano Client Lib maintainers

## Context

The generated `toMetadataMap` and `fromMetadataMap` methods need to read and write fields on
the annotated POJO. Java POJOs follow different access patterns: some use getter/setter methods,
some rely on Lombok to generate them, and some expose fields directly as `public`.

The processor must resolve the correct access strategy at compile time and emit appropriate
code (`obj.getFoo()` vs `obj.foo`). Boolean fields add complexity because idiomatic Java uses
`isFoo()` in addition to `getFoo()`.

## Decision

Implement a **three-tier fallback** for both getter and setter resolution in
`MetadataAnnotationProcessor.extractFields()`:

### Getter resolution (`findGetter`)

1. **Explicit getter** — scan the class for a public, zero-parameter method matching
   `getFoo()` whose return type matches the field type. For `boolean`/`Boolean` fields,
   also accept `isFoo()`.
2. **Lombok fallback** — if no explicit getter is found but `detectLombok()` returned `true`,
   assume Lombok will generate `getFoo()` and use that name.
3. **Direct field access** — if the field is `public` (or package-private with no modifiers),
   use `obj.fieldName` directly. Otherwise emit a WARNING and **skip the field**.

```java
ExecutableElement getter = findGetter(typeElement, ve);
if (getter != null) {
    getterName = getter.getSimpleName().toString();
} else if (hasLombok) {
    getterName = "get" + capitalize(fieldName);
} else if (!isDirectlyAccessible(ve)) {
    messager.printMessage(Diagnostic.Kind.WARNING,
        "No getter found for field '" + fieldName + "' and field is not public. "
        + "Field will be skipped.", ve);
    continue;
}
```

### Setter resolution (`findSetter`)

1. **Explicit setter** — scan for a public, single-parameter, void method matching
   `setFoo(FieldType)` where the parameter type exactly matches the field type.
2. **Lombok fallback** — assume `setFoo()` will be generated.
3. **Direct field access** — same as getter. Skip with WARNING if not accessible.

### Boolean `isFoo()` convention

```java
String getterMethodName = "get" + capitalize(fieldName);
String isGetterMethodName = "is" + capitalize(fieldName);
boolean isBooleanType = fieldTypeName.equals("boolean")
    || fieldTypeName.equals("java.lang.Boolean");

boolean nameMatches = methodName.equals(getterMethodName)
    || (isBooleanType && methodName.equals(isGetterMethodName));
```

Both `isFoo()` and `getFoo()` are accepted for boolean fields. The first match wins
(declaration order in the class).

### Generated code forms

| Resolution | `toMetadataMap` | `fromMetadataMap` |
|---|---|---|
| Getter/setter found | `obj.getFoo()` | `obj.setFoo(value)` |
| Direct field access | `obj.foo` | `obj.foo = value` |

When a getter is found but no setter (or vice versa), the missing accessor side falls
through to direct field access if the field is public, or the field is skipped entirely.

## Alternatives considered

### 1. Always require getter/setter (rejected)

Would force users to write boilerplate even for simple `public` field POJOs. Direct field
access is a common Java pattern, especially for DTOs.

### 2. Generate reflection-based access (rejected)

Reflection bypasses access modifiers but introduces runtime overhead, breaks GraalVM native
image compatibility, and obscures errors. Compile-time resolution is safer and faster.

### 3. Support `@Getter`/`@Setter` on individual fields (not chosen)

Lombok allows per-field `@Getter`/`@Setter`. The current implementation only detects
class-level Lombok annotations (`@Data`, `@Getter`+`@Setter`). Per-field detection would
add complexity for a rare use case; the class-level check covers >95% of Lombok usage.

### 4. Fail with ERROR on missing accessor (rejected)

Similar to ADR 0012's reasoning: another APT may generate the accessor. A WARNING is
less disruptive while still providing a clear diagnostic.

## Trade-offs

### Positive
- Supports idiomatic Java POJOs (Lombok, manual getters, public fields) without configuration.
- Boolean `isFoo()` convention handled automatically.
- Fields are skipped gracefully with a WARNING rather than failing the build.

### Negative / Limitations
- **No per-field Lombok detection**: `@Getter` on a single field is not detected unless the
  class also has a class-level annotation.
- **Declaration-order winner**: if both `isFoo()` and `getFoo()` exist on a boolean field,
  the first one encountered in source order wins. This is deterministic but not configurable.
- **No fluent setter support**: setters must return `void`. Builder-style `setFoo()` that
  returns `this` are not matched.

## Consequences

- `MetadataAnnotationProcessor` contains `findGetter()`, `findSetter()`, and
  `isDirectlyAccessible()` methods for the three-tier resolution.
- `MetadataFieldInfo` stores resolved `getterName` and `setterName` (null when using
  direct field access).
- `MetadataConverterGenerator.buildGetExpression()` emits `obj.getterName()` when
  `getterName != null`, otherwise `obj.fieldName`.
- `MetadataFieldAccessor` emits `obj.setterName(value)` when `setterName != null`,
  otherwise direct assignment.

## Related

- ADR metadata/0001: Annotation Processor Core Design — defines opt-out field inclusion
- ADR metadata/0012: No-Arg Constructor Validation — Lombok detection reused here
- `MetadataAnnotationProcessor.findGetter()` — getter resolution
- `MetadataAnnotationProcessor.findSetter()` — setter resolution
- `MetadataAnnotationProcessor.isDirectlyAccessible()` — public field fallback
- `MetadataConverterGenerator.buildGetExpression()` — code emission
- `MetadataFieldAccessor` — setter code emission
