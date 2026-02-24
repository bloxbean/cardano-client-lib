# ADR metadata/0012: No-Arg Constructor Validation

- **Status**: Accepted
- **Date**: 2026-02-24
- **Deciders**: Cardano Client Lib maintainers

## Context

`fromMetadataMap` generates `new ClassName()` to instantiate the target POJO before
populating its fields. If the annotated class does not have a public no-arg constructor,
the generated code fails to compile — but with a cryptic message pointing at the generated
file rather than the user's POJO.

Common causes:
- A class defines only an all-args constructor (explicit or via `@lombok.AllArgsConstructor`).
- A class defines a parameterized constructor without also declaring a no-arg one.

Lombok's `@Data` and `@NoArgsConstructor` generate a no-arg constructor at compile time,
so it is not visible to the annotation processor during source processing.

## Decision

Emit a **compile-time WARNING** from `MetadataAnnotationProcessor` when no public no-arg
constructor is found on the annotated class and Lombok is not detected on the class.

If Lombok is present (`@Data`, or both `@Getter` and `@Setter`), the warning is suppressed
on the assumption that Lombok will generate the constructor.

### Detection

```java
private void validateNoArgConstructor(TypeElement typeElement, boolean hasLombok) {
    for (Element e : typeElement.getEnclosedElements()) {
        if (!(e instanceof ExecutableElement)) continue;
        ExecutableElement ee = (ExecutableElement) e;
        if (ee.getKind() == ElementKind.CONSTRUCTOR
                && ee.getParameters().isEmpty()
                && ee.getModifiers().contains(Modifier.PUBLIC)) {
            return; // found
        }
    }
    if (!hasLombok) {
        messager.printMessage(Diagnostic.Kind.WARNING, "...", typeElement);
    }
}
```

Called from `process()` immediately after `detectLombok()`, before `extractFields()`.

### Warning vs. error

A WARNING rather than an ERROR is chosen because:
- The missing constructor is often supplied by Lombok or another APT (e.g. MapStruct),
  which runs in the same or a subsequent compilation round.
- Failing with an ERROR would break builds that rely on multi-pass APT.
- The compiler error from the generated file is a sufficient hard stop; the WARNING
  provides an earlier, clearer diagnostic.

### Message

```
No public no-arg constructor found on 'ClassName'. The generated fromMetadataMap()
calls new ClassName(). Add a public no-arg constructor or use @lombok.NoArgsConstructor.
```

## Alternatives considered

### 1. Emit an ERROR (rejected)

Would block code generation entirely if the constructor is missing, even when another APT
(e.g. Lombok) is about to generate it. A WARNING followed by the compiler's own error on
the generated file is the correct two-stage signal.

### 2. Do nothing / rely on compiler error (rejected)

The compiler error appears on the generated file (e.g. `OrderMetadataConverter.java:15`)
rather than the user's POJO, making the root cause non-obvious. An early WARNING at the
POJO source location is significantly more actionable.

### 3. Check for `@AllArgsConstructor` and warn specifically (not chosen)

Detecting specific Lombok annotations would require knowing the full set of annotations
that may suppress the need for a no-arg constructor. The current approach (detect any
Lombok presence on the class) is simpler and errs on the side of fewer false-positive
warnings.

## Trade-offs

### Positive
- Early, actionable diagnostic at the user's POJO source location.
- Zero false positives for Lombok-annotated classes.
- No impact on code generation — warning only.

### Negative / Limitations
- **False negatives for non-Lombok APTs**: if another annotation processor generates a
  no-arg constructor, the warning is still emitted (since the constructor isn't yet visible).
  The warning is harmless — the build succeeds — but may cause noise.
- **Lombok detection is coarse**: only `@Data` and `@Getter`+`@Setter` are detected.
  `@NoArgsConstructor` alone (without `@Data`) also suppresses the warning because
  it generates a no-arg constructor, but it is not currently checked explicitly.
  Adding it would be a trivial future improvement.

## Consequences

- `MetadataAnnotationProcessor` gains a `validateNoArgConstructor(TypeElement, boolean)`
  method called once per annotated class during `process()`.
- No changes to `MetadataFieldInfo`, `MetadataConverterGenerator`, or generated code.

## Related

- ADR metadata/0001: Annotation Processor Core Design
- `MetadataAnnotationProcessor.validateNoArgConstructor()` — implementation
- `MetadataAnnotationProcessor.detectLombok()` — Lombok detection reused here
